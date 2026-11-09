package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.security.SoniaSecurityKernel
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

/**
 * Client Gemini 2026 sécurisé :
 * - Modèles officiels : `gemini-3.8-flash` (défaut rapide), `gemini-4-argon` (raisonnement approfondi),
 *   `gemini-3.5-flash-lite` (ultra-basse latence), `gemini-embedding-2` (embeddings RAG en ligne).
 * - Stockage chiffré AES-256-GCM de la clé API via `SoniaSecurityKernel`.
 * - En-tête HTTP `x-goog-api-key` (jamais dans l'URL `?key=`) et backoff exponentiel sur erreurs transitoires.
 */
object GeminiDualEngineClient {

    const val MODEL_FLASH_DEFAULT = "gemini-3.8-flash"
    const val MODEL_PRO_REASONING = "gemini-4-argon"
    const val MODEL_FLASH_LITE = "gemini-3.5-flash-lite"
    const val MODEL_EMBEDDING = "gemini-embedding-2"

    @Volatile
    private var cachedCustomKey: String = ""

    @Volatile
    private var cachedFirebaseKey: String = ""

    @Volatile
    private var lastDiagnosticErrorMessage: String = ""

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawStored = prefs.getString(KEY_CUSTOM_GEMINI_API_KEY, "")?.trim().orEmpty()
        if (rawStored.isNotBlank()) {
            val decrypted = SoniaSecurityKernel.decryptSensitivePayload(rawStored)?.trim().orEmpty()
            cachedCustomKey = decrypted
            // Migration transparente vers le format chiffré AES-256-GCM si la clé était en clair
            if (!rawStored.startsWith("ENCv1:") && decrypted.isNotBlank()) {
                val encrypted = SoniaSecurityKernel.encryptSensitivePayload(decrypted)
                if (!encrypted.isNullOrBlank()) {
                    prefs.edit().putString(KEY_CUSTOM_GEMINI_API_KEY, encrypted).apply()
                }
            }
        } else {
            cachedCustomKey = ""
        }

        val resId = context.resources.getIdentifier("google_api_key", "string", context.packageName)
        if (resId != 0) {
            cachedFirebaseKey = context.getString(resId).trim()
        }
    }

    fun saveCustomApiKey(context: Context, apiKey: String) {
        val cleaned = apiKey.trim()
        cachedCustomKey = cleaned
        val toStore = SoniaSecurityKernel.encryptSensitivePayload(cleaned) ?: cleaned
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_GEMINI_API_KEY, toStore)
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

    fun getLastDiagnosticError(): String = lastDiagnosticErrorMessage

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
            validCustom -> "Clé API chiffrée AES-256-GCM (${maskKey(cachedCustomKey)})"
            validBuild -> "Panneau Secrets AI Studio (${maskKey(buildKey)})"
            cachedFirebaseKey.isNotBlank() -> "Firebase AI Logic (${maskKey(cachedFirebaseKey)})"
            else -> "Mode Hors-Ligne Local LIA"
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
     * Appel REST Gemini avec en-tête `x-goog-api-key`, cascade de modèles (`gemini-3.8-flash`,
     * `gemini-4-argon`, `gemini-3.5-flash-lite`) et backoff exponentiel sur erreurs transitoires.
     */
    suspend fun generateJsonWithRestFallback(
        primaryModel: String = MODEL_FLASH_DEFAULT,
        systemInstruction: String?,
        prompt: String,
        bitmap: Bitmap? = null,
        responseMimeType: String = "application/json"
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
            lastDiagnosticErrorMessage = "Aucune clé API Gemini configurée."
            throw IllegalStateException(lastDiagnosticErrorMessage)
        }

        val candidateModels = buildList {
            if (primaryModel.isNotBlank()) add(primaryModel)
            if (!contains(MODEL_FLASH_DEFAULT)) add(MODEL_FLASH_DEFAULT)
            if (!contains(MODEL_FLASH_LITE)) add(MODEL_FLASH_LITE)
        }

        val base64Image = bitmap?.let { bmp ->
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 60, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }

        var lastError: Exception? = null

        for (apiKey in candidateKeys) {
            for ((attemptIndex, modelName) in candidateModels.withIndex()) {
                if (attemptIndex > 0) {
                    delay(220L * attemptIndex)
                }
                try {
                    val result = executeSingleHttpGenerateContent(
                        apiKey = apiKey,
                        modelName = modelName,
                        systemInstruction = systemInstruction,
                        prompt = prompt,
                        base64Jpeg = base64Image,
                        responseMimeType = responseMimeType
                    )
                    if (result.isNotBlank()) {
                        lastDiagnosticErrorMessage = ""
                        return@withContext result
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "REST attempt failed for model=$modelName: ${e.message}")
                    lastError = e
                    val msg = e.message.orEmpty()
                    lastDiagnosticErrorMessage = msg.take(180)
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

    /**
     * Calcule un vecteur d'embedding sémantique en ligne via `gemini-embedding-2` (dim=64 tronqué et normalisé L2)
     * pour enrichir le moteur RAG hybride lorsque la connexion est disponible.
     */
    suspend fun embedTextWithGeminiEmbedding2(
        text: String,
        targetDimensions: Int = 64
    ): FloatArray? = withContext(Dispatchers.IO) {
        val apiKey = resolveBestApiKey() ?: return@withContext null
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@withContext null

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_EMBEDDING:embedContent"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3_000
            readTimeout = 4_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        val payload = JSONObject().apply {
            put("model", "models/$MODEL_EMBEDDING")
            put(
                "content",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", trimmed.take(1500)))
                )
            )
            put("outputDimensionality", targetDimensions)
        }

        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }
            if (conn.responseCode !in 200..299) {
                return@withContext null
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val valuesJson = JSONObject(body).optJSONObject("embedding")?.optJSONArray("values")
                ?: return@withContext null
            val dim = minOf(targetDimensions, valuesJson.length())
            if (dim <= 0) return@withContext null
            val vec = FloatArray(targetDimensions)
            var normSq = 0f
            for (i in 0 until dim) {
                val v = valuesJson.optDouble(i, 0.0).toFloat()
                vec[i] = v
                normSq += v * v
            }
            val norm = kotlin.math.sqrt(normSq)
            if (norm > 1e-6f) {
                for (i in 0 until targetDimensions) {
                    vec[i] /= norm
                }
            }
            vec
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun executeSingleHttpGenerateContent(
        apiKey: String,
        modelName: String,
        systemInstruction: String?,
        prompt: String,
        base64Jpeg: String?,
        responseMimeType: String
    ): String {
        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
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
                    .put("responseMimeType", responseMimeType)
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
