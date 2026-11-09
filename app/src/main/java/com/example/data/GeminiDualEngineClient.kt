package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "GeminiDualEngine"
private const val PREFS_NAME = "omniagent_api_key_prefs"
private const val KEY_CUSTOM_GEMINI_API_KEY = "custom_gemini_api_key"

data class ApiKeyDiagnosticStatus(
    val hasCustomInAppKey: Boolean,
    val customKeyPreview: String,
    val hasBuildConfigSecretKey: Boolean,
    val buildConfigKeyPreview: String,
    val firebaseGoogleApiKeyPreview: String,
    val activeSourceLabel: String
)

object GeminiDualEngineClient {

    @Volatile
    private var cachedCustomKey: String = ""

    @Volatile
    private var cachedFirebaseKey: String = ""

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cachedCustomKey = prefs.getString(KEY_CUSTOM_GEMINI_API_KEY, "")?.trim().orEmpty()
        val resId = context.resources.getIdentifier("google_api_key", "string", context.packageName)
        if (resId != 0) {
            cachedFirebaseKey = context.getString(resId).trim()
        }
    }

    fun saveCustomApiKey(context: Context, apiKey: String) {
        val cleaned = apiKey.trim()
        cachedCustomKey = cleaned
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_GEMINI_API_KEY, cleaned)
            .apply()
    }

    fun clearCustomApiKey(context: Context) {
        cachedCustomKey = ""
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CUSTOM_GEMINI_API_KEY)
            .apply()
    }

    fun getCustomApiKey(): String = cachedCustomKey

    fun hasValidCustomApiKey(): Boolean =
        cachedCustomKey.isNotBlank() && cachedCustomKey.startsWith("AIza")

    fun hasValidBuildConfigKey(): Boolean {
        val key = runCatching { BuildConfig.GEMINI_API_KEY.trim() }.getOrDefault("")
        return key.isNotBlank() &&
            key != "MY_GEMINI_API_KEY" &&
            key != "PLACEHOLDER" &&
            !key.startsWith("YOUR_")
    }

    fun resolveBestApiKey(): String? {
        if (cachedCustomKey.isNotBlank() && cachedCustomKey != "MY_GEMINI_API_KEY") {
            return cachedCustomKey
        }
        val buildConfigKey = runCatching { BuildConfig.GEMINI_API_KEY.trim() }.getOrDefault("")
        if (buildConfigKey.isNotBlank() &&
            buildConfigKey != "MY_GEMINI_API_KEY" &&
            buildConfigKey != "PLACEHOLDER"
        ) {
            return buildConfigKey
        }
        if (cachedFirebaseKey.isNotBlank()) {
            return cachedFirebaseKey
        }
        return null
    }

    fun getDiagnosticStatus(): ApiKeyDiagnosticStatus {
        val buildKey = runCatching { BuildConfig.GEMINI_API_KEY.trim() }.getOrDefault("")
        val validBuild = hasValidBuildConfigKey()
        val validCustom = cachedCustomKey.isNotBlank()

        val activeSource = when {
            validCustom -> "Clé API saisie dans l'application (${maskKey(cachedCustomKey)})"
            validBuild -> "Panneau Secrets AI Studio (${maskKey(buildKey)})"
            cachedFirebaseKey.isNotBlank() -> "Firebase AI Logic (${maskKey(cachedFirebaseKey)})"
            else -> "Moteur Hybride Rapide Sonia"
        }

        return ApiKeyDiagnosticStatus(
            hasCustomInAppKey = validCustom,
            customKeyPreview = if (validCustom) maskKey(cachedCustomKey) else "Non configurée",
            hasBuildConfigSecretKey = validBuild,
            buildConfigKeyPreview = if (validBuild) maskKey(buildKey) else "Non injectée (MY_GEMINI_API_KEY)",
            firebaseGoogleApiKeyPreview = if (cachedFirebaseKey.isNotBlank()) maskKey(cachedFirebaseKey) else "Absente",
            activeSourceLabel = activeSource
        )
    }

    private fun maskKey(key: String): String {
        if (key.length <= 10) return "••••••••"
        return "${key.take(6)}••••${key.takeLast(4)}"
    }

    /**
     * Ultra-fast REST call prioritizing low-latency Flash models with tight timeouts.
     */
    suspend fun generateJsonWithRestFallback(
        primaryModel: String,
        systemInstruction: String?,
        prompt: String,
        bitmap: Bitmap? = null
    ): String = withContext(Dispatchers.IO) {
        val candidateKeys = buildList {
            if (cachedCustomKey.isNotBlank()) add(cachedCustomKey)
            val buildKey = runCatching { BuildConfig.GEMINI_API_KEY.trim() }.getOrDefault("")
            if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY" && !contains(buildKey)) {
                add(buildKey)
            }
            if (cachedFirebaseKey.isNotBlank() && !contains(cachedFirebaseKey)) {
                add(cachedFirebaseKey)
            }
        }

        if (candidateKeys.isEmpty()) {
            throw IllegalStateException("Aucune clé API configurée.")
        }

        // Prioritize ultra-fast Flash models first for < 2s latency
        val candidateModels = buildList {
            add("gemini-3.8-flash")
            if (!contains(primaryModel)) add(primaryModel)
            if (!contains("gemini-3.5-flash-lite")) add("gemini-3.5-flash-lite")
        }

        val base64Image = bitmap?.let { bmp ->
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 60, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }

        var lastError: Exception? = null

        for (apiKey in candidateKeys) {
            for (modelName in candidateModels) {
                try {
                    val result = executeSingleHttpGenerateContent(
                        apiKey = apiKey,
                        modelName = modelName,
                        systemInstruction = systemInstruction,
                        prompt = prompt,
                        base64Jpeg = base64Image
                    )
                    if (result.isNotBlank()) {
                        return@withContext result
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fast REST attempt failed for model=$modelName: ${e.message}")
                    lastError = e
                    val msg = e.message.orEmpty()
                    if (msg.contains("API_KEY_INVALID") ||
                        msg.contains("API key not valid") ||
                        msg.contains("API_KEY_ANDROID_APP_BLOCKED") ||
                        msg.contains("PERMISSION_DENIED")
                    ) {
                        break
                    }
                }
            }
        }

        throw lastError ?: IllegalStateException("Échec de l'appel Gemini REST.")
    }

    private fun executeSingleHttpGenerateContent(
        apiKey: String,
        modelName: String,
        systemInstruction: String?,
        prompt: String,
        base64Jpeg: String?
    ): String {
        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4_000
            readTimeout = 6_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        val payload = JSONObject().apply {
            if (!systemInstruction.isNullOrBlank()) {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemInstruction))
                    )
                )
            }
            val partsArray = JSONArray()
            if (!base64Jpeg.isNullOrBlank()) {
                partsArray.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", "image/jpeg")
                            .put("data", base64Jpeg)
                    )
                )
            }
            partsArray.put(JSONObject().put("text", prompt))

            put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", partsArray)
                )
            )
            put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("temperature", 0.15)
            )
        }

        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val errBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                throw IllegalStateException("HTTP $code ($modelName): $errBody")
            }

            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
                ?: throw IllegalStateException("Aucun candidat renvoyé par $modelName")
            if (candidates.length() == 0) {
                throw IllegalStateException("Liste de candidats vide ($modelName)")
            }
            val contentObj = candidates.getJSONObject(0).optJSONObject("content")
                ?: throw IllegalStateException("Contenu absent dans la réponse ($modelName)")
            val parts = contentObj.optJSONArray("parts")
                ?: throw IllegalStateException("Parts absentes dans la réponse ($modelName)")

            val textBuilder = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (!part.optBoolean("thought", false)) {
                    textBuilder.append(part.optString("text", ""))
                }
            }
            return textBuilder.toString().trim()
        } finally {
            conn.disconnect()
        }
    }
}
