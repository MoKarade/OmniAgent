package com.example.data

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.model.currentTimeFormatted
import com.example.security.SafeContactResolver
import com.example.security.SoniaSecurityKernel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class GoogleCloudEmailItem(
    val id: String,
    val subject: String,
    val from: String,
    val snippet: String,
    val dateFormatted: String
)

data class GoogleCloudCalendarEventItem(
    val id: String,
    val title: String,
    val startFormatted: String,
    val locationOrDescription: String,
    val source: String // "Google Calendar API" or "Android Calendar Provider"
)

data class GoogleCloudDriveFileItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val webViewLink: String,
    val modifiedTime: String
)

data class GoogleCloudContactItem(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val emailAddress: String,
    val source: String // "Google People API" or "Android Contacts"
)

data class GoogleWorkspaceAuthState(
    val isSignedIn: Boolean = false,
    val isAuthenticating: Boolean = false,
    val isSyncingWorkspace: Boolean = false,
    val userUid: String = "",
    val userEmail: String = "",
    val userDisplayName: String,
    val userPhotoUrl: String = "",
    val idTokenPresent: Boolean = false,
    val hasCloudAccessToken: Boolean = false,
    val authorizedScopes: List<String> = listOf(
        "https://www.googleapis.com/auth/gmail.readonly",
        "https://www.googleapis.com/auth/gmail.send",
        "https://www.googleapis.com/auth/calendar",
        "https://www.googleapis.com/auth/drive.readonly",
        "https://www.googleapis.com/auth/contacts.readonly",
        "https://www.googleapis.com/auth/tasks",
        "https://www.googleapis.com/auth/documents.readonly",
        "https://www.googleapis.com/auth/spreadsheets.readonly"
    ),
    val recentEmails: List<GoogleCloudEmailItem> = emptyList(),
    val upcomingEvents: List<GoogleCloudCalendarEventItem> = emptyList(),
    val recentDriveFiles: List<GoogleCloudDriveFileItem> = emptyList(),
    val syncedContacts: List<GoogleCloudContactItem> = emptyList(),
    val lastSyncSummary: String = "Prêt à synchroniser Gmail, Google Agenda, Drive et Contacts.",
    val lastSyncTimestamp: String = "",
    val authErrorMessage: String? = null
) {
    constructor(
        isSignedIn: Boolean = false,
        isAuthenticating: Boolean = false,
        isSyncingWorkspace: Boolean = false,
        userUid: String = "",
        userEmail: String = "",
        userDisplayName: String = "",
        userPhotoUrl: String = "",
        idTokenPresent: Boolean = false,
        hasCloudAccessToken: Boolean = false,
        lastSyncSummary: String = "Prêt à synchroniser Gmail, Google Agenda, Drive et Contacts.",
        authErrorMessage: String? = null
    ) : this(
        isSignedIn = isSignedIn,
        isAuthenticating = isAuthenticating,
        isSyncingWorkspace = isSyncingWorkspace,
        userUid = userUid,
        userEmail = userEmail,
        userDisplayName = userDisplayName,
        userPhotoUrl = userPhotoUrl,
        idTokenPresent = idTokenPresent,
        hasCloudAccessToken = hasCloudAccessToken,
        authorizedScopes = listOf(
            "https://www.googleapis.com/auth/gmail.readonly",
            "https://www.googleapis.com/auth/gmail.send",
            "https://www.googleapis.com/auth/calendar",
            "https://www.googleapis.com/auth/drive.readonly",
            "https://www.googleapis.com/auth/contacts.readonly",
            "https://www.googleapis.com/auth/tasks",
            "https://www.googleapis.com/auth/documents.readonly",
            "https://www.googleapis.com/auth/spreadsheets.readonly"
        ),
        lastSyncSummary = lastSyncSummary,
        authErrorMessage = authErrorMessage
    )
}

/**
 * Gestionnaire unifié Google Sign-In (Jetpack Credential Manager) + APIs Google Cloud Workspace
 * (Gmail API, Google Calendar API, Google Drive API, Google People/Contacts API) avec passerelle
 * vers les ContentProviders Android natifs (`CalendarContract`, `ContactsContract`, `mailto:`)
 * et indexation automatique dans la base Room RAG de LIA.
 */
object LiaGoogleCloudWorkspaceClient {
    private const val TAG = "LiaGoogleCloudWorkspace"
    private const val PREFS_NAME = "lia_google_workspace_auth_prefs"
    private const val KEY_OAUTH_ACCESS_TOKEN = "oauth_access_token"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_display_name"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(GoogleWorkspaceAuthState(userDisplayName = ""))
    val state: StateFlow<GoogleWorkspaceAuthState> = _state.asStateFlow()

    @Volatile
    private var cachedAccessToken: String = ""

    fun init(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawStoredToken = prefs.getString(KEY_OAUTH_ACCESS_TOKEN, "")?.trim().orEmpty()
        cachedAccessToken = if (rawStoredToken.isNotBlank()) {
            val decrypted = SoniaSecurityKernel.decryptSensitivePayload(rawStoredToken)
            // SEC-02: Auto-migrate legacy plaintext OAuth token to AES-256-GCM ciphertext
            if (!rawStoredToken.startsWith("ENCv1:") && decrypted.isNotBlank()) {
                val encrypted = SoniaSecurityKernel.encryptSensitivePayload(decrypted)
                if (encrypted.startsWith("ENCv1:")) {
                    prefs.edit().putString(KEY_OAUTH_ACCESS_TOKEN, encrypted).apply()
                }
            }
            decrypted
        } else {
            ""
        }
        val savedEmail = prefs.getString(KEY_USER_EMAIL, "")?.trim().orEmpty()
        val savedName = prefs.getString(KEY_USER_NAME, "")?.trim().orEmpty()

        val fbUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        val effectiveEmail = fbUser?.email ?: savedEmail
        val effectiveName = fbUser?.displayName ?: savedName

        _state.update {
            it.copy(
                isSignedIn = fbUser != null || cachedAccessToken.isNotBlank() || effectiveEmail.isNotBlank(),
                userUid = fbUser?.uid.orEmpty(),
                userEmail = effectiveEmail,
                userDisplayName = effectiveName,
                userPhotoUrl = fbUser?.photoUrl?.toString().orEmpty(),
                idTokenPresent = fbUser != null,
                hasCloudAccessToken = cachedAccessToken.isNotBlank()
            )
        }
    }

    fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun resolveWebClientId(context: Context): String {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId != 0) context.getString(resId).trim() else ""
    }

    /**
     * Sauvegarde un jeton d'accès OAuth2 Bearer Google Cloud chiffré via AndroidKeyStore AES-256-GCM (`SEC-02`)
     * et lance immédiatement la synchronisation des 4 APIs Google Workspace (Gmail, Calendar, Drive, Contacts).
     */
    fun updateOAuthAccessToken(context: Context, accessToken: String, optionalEmail: String = "") {
        val cleanToken = accessToken.trim().removePrefix("Bearer ").trim()
        cachedAccessToken = cleanToken
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (cleanToken.isBlank()) {
                remove(KEY_OAUTH_ACCESS_TOKEN)
            } else {
                putString(KEY_OAUTH_ACCESS_TOKEN, SoniaSecurityKernel.encryptSensitivePayload(cleanToken))
            }
            if (optionalEmail.isNotBlank()) {
                putString(KEY_USER_EMAIL, optionalEmail.trim())
            }
        }.apply()

        _state.update {
            it.copy(
                isSignedIn = it.isSignedIn || cleanToken.isNotBlank(),
                userEmail = if (optionalEmail.isNotBlank()) optionalEmail.trim() else it.userEmail,
                hasCloudAccessToken = cleanToken.isNotBlank(),
                authErrorMessage = null,
                lastSyncSummary = if (cleanToken.isNotBlank()) {
                    "Jeton OAuth2 Google Cloud chiffré (AES-256-GCM). Synchronisation des APIs en cours…"
                } else {
                    it.lastSyncSummary
                }
            )
        }

        if (cleanToken.isNotBlank()) {
            syncAllWorkspaceData(context)
        }
    }

    /**
     * Flux 1 : Connexion automatique silencieuse au démarrage via Jetpack Credential Manager (`GetGoogleIdOption`).
     */
    suspend fun attemptSilentGoogleSignIn(context: Context): Boolean {
        val activity = findActivity(context) ?: return false
        val webClientId = resolveWebClientId(context)
        if (webClientId.isBlank()) return false

        return try {
            val credentialManager = CredentialManager.create(activity)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(activity, request)
            val cred = result.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdCred = GoogleIdTokenCredential.createFrom(cred.data)
                completeCredentialSignIn(context, googleIdCred)
                true
            } else {
                false
            }
        } catch (_: NoCredentialException) {
            false
        } catch (e: Exception) {
            Log.d(TAG, "Silent CredentialManager sign-in skipped: ${e.message}")
            false
        }
    }

    /**
     * Flux 2 : Connexion interactive 1-clic via Jetpack Credential Manager (`GetSignInWithGoogleOption`).
     * Authentifie l'utilisateur auprès de Google & Firebase Auth, tente de récupérer un jeton OAuth2
     * pour les scopes Gmail, Calendar, Drive et Contacts, et synchronise les données dans Room.
     */
    suspend fun signInInteractiveWithCredentialManager(
        context: Context,
        onSuccess: () -> Unit = {}
    ): Boolean {
        val activity = findActivity(context)
        if (activity == null) {
            _state.update {
                it.copy(
                    isAuthenticating = false,
                    authErrorMessage = "Impossible d'accéder à l'Activity Android pour Credential Manager."
                )
            }
            return false
        }
        val webClientId = resolveWebClientId(context)
        if (webClientId.isBlank()) {
            _state.update {
                it.copy(
                    isAuthenticating = false,
                    authErrorMessage = "default_web_client_id introuvable dans google-services.json."
                )
            }
            return false
        }

        _state.update { it.copy(isAuthenticating = true, authErrorMessage = null) }
        return try {
            val credentialManager = CredentialManager.create(activity)
            val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = credentialManager.getCredential(activity, request)
            val cred = result.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(cred.data)
                completeCredentialSignIn(context, googleIdTokenCredential)
                onSuccess()
                true
            } else {
                _state.update {
                    it.copy(
                        isAuthenticating = false,
                        authErrorMessage = "Type d'identifiant non pris en charge par Google Sign-In."
                    )
                }
                false
            }
        } catch (_: GetCredentialCancellationException) {
            _state.update {
                it.copy(
                    isAuthenticating = false,
                    authErrorMessage = "Connexion Google annulée par l'utilisateur."
                )
            }
            false
        } catch (_: NoCredentialException) {
            _state.update {
                it.copy(
                    isAuthenticating = false,
                    authErrorMessage = "Aucun compte Google configuré sur cet émulateur Android. Ajoutez un compte Google dans les Paramètres Android ou collez un jeton OAuth2 Google Cloud ci-dessous pour autoriser Gmail, Agenda, Drive et Contacts."
                )
            }
            false
        } catch (e: GetCredentialException) {
            Log.e(TAG, "CredentialManager error", e)
            _state.update {
                it.copy(
                    isAuthenticating = false,
                    authErrorMessage = "Erreur Credential Manager : ${e.localizedMessage ?: e.message}"
                )
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In error", e)
            _state.update {
                it.copy(
                    isAuthenticating = false,
                    authErrorMessage = "Erreur d'authentification Google : ${e.localizedMessage ?: e.message}"
                )
            }
            false
        }
    }

    private suspend fun completeCredentialSignIn(
        context: Context,
        googleIdCred: GoogleIdTokenCredential
    ) {
        val firebaseCred = GoogleAuthProvider.getCredential(googleIdCred.idToken, null)
        val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCred).await()
        val fbUser = authResult.user

        val email = googleIdCred.id.ifBlank { fbUser?.email.orEmpty() }
        val displayName = googleIdCred.displayName ?: fbUser?.displayName ?: email.substringBefore("@")
        val photoUrl = googleIdCred.profilePictureUri?.toString() ?: fbUser?.photoUrl?.toString().orEmpty()

        // Try acquiring an OAuth2 Workspace token via GoogleAuthUtil if available on device
        val acquiredOAuthToken = withContext(Dispatchers.IO) {
            tryAcquireWorkspaceAccessTokenViaPlayServices(context, email)
        }
        if (acquiredOAuthToken.isNotBlank()) {
            cachedAccessToken = acquiredOAuthToken
        }

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_NAME, displayName)
            if (cachedAccessToken.isNotBlank()) {
                putString(KEY_OAUTH_ACCESS_TOKEN, SoniaSecurityKernel.encryptSensitivePayload(cachedAccessToken))
            }
        }.apply()

        // Save user's identity in Room so LIA knows who is signed in
        if (email.isNotBlank()) {
            LiaMemoryAndHistoryStore.upsertSouvenir(
                type = "FAIT",
                subjectKey = "compte_google_connecte",
                title = "Compte Google connecté : $displayName",
                detailValue = "Connecté via Google Sign-In (Credential Manager) avec l'adresse $email. Accès autorisé à Gmail, Google Agenda, Google Drive et Contacts.",
                searchTags = "google,gmail,agenda,drive,contacts,email,$email,${displayName.lowercase()}"
            )
        }

        _state.update {
            it.copy(
                isSignedIn = true,
                isAuthenticating = false,
                userUid = fbUser?.uid.orEmpty(),
                userEmail = email,
                userDisplayName = displayName,
                userPhotoUrl = photoUrl,
                idTokenPresent = true,
                hasCloudAccessToken = cachedAccessToken.isNotBlank(),
                authErrorMessage = null,
                lastSyncSummary = "Connecté en tant que $displayName ($email). Synchronisation Workspace en cours…"
            )
        }

        syncAllWorkspaceData(context)
    }

    /**
     * Tente d'obtenir un jeton OAuth2 multi-scopes (Gmail, Calendar, Drive, Contacts)
     * via Google Play Services `GoogleAuthUtil.getToken` si disponible sur l'appareil.
     */
    private fun tryAcquireWorkspaceAccessTokenViaPlayServices(context: Context, email: String): String {
        if (email.isBlank()) return ""
        return try {
            val authUtilClass = Class.forName("com.google.android.gms.auth.GoogleAuthUtil")
            val accountClass = Class.forName("android.accounts.Account")
            val accountConstructor = accountClass.getConstructor(String::class.java, String::class.java)
            val googleAccount = accountConstructor.newInstance(email, "com.google")
            val scopesString = "oauth2:" + listOf(
                "https://www.googleapis.com/auth/gmail.readonly",
                "https://www.googleapis.com/auth/gmail.send",
                "https://www.googleapis.com/auth/calendar",
                "https://www.googleapis.com/auth/drive.readonly",
                "https://www.googleapis.com/auth/contacts.readonly"
            ).joinToString(" ")
            val getTokenMethod = authUtilClass.getMethod(
                "getToken",
                Context::class.java,
                accountClass,
                String::class.java
            )
            (getTokenMethod.invoke(null, context.applicationContext, googleAccount, scopesString) as? String)
                ?.trim()
                .orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    fun signOut(context: Context) {
        scope.launch {
            runCatching {
                val credentialManager = CredentialManager.create(context.applicationContext)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            }
            runCatching {
                FirebaseAuth.getInstance().signOut()
            }
            cachedAccessToken = ""
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()

            _state.update {
                GoogleWorkspaceAuthState(
                    lastSyncSummary = "Déconnecté de Google Cloud Workspace."
                )
            }
        }
    }

    /**
     * Synchronise simultanément (en parallèle via `async` / `coroutineScope`, `PERF-02`)
     * Gmail, Google Calendar, Google Drive et Google Contacts via les APIs Google Cloud REST
     * (si un jeton OAuth2 est actif) et les ContentProviders Android (`CalendarContract`, `ContactsContract`),
     * puis indexe les résultats dans la base Room RAG de LIA.
     */
    fun syncAllWorkspaceData(context: Context) {
        val appCtx = context.applicationContext
        scope.launch {
            _state.update {
                it.copy(
                    isSyncingWorkspace = true,
                    authErrorMessage = null,
                    lastSyncSummary = "Synchronisation parallèle de Gmail, Agenda, Drive et Contacts…"
                )
            }

            val token = cachedAccessToken
            val (emails, events, driveFiles, contacts) = coroutineScope {
                val emailsDeferred = async(Dispatchers.IO) { fetchGmailMessagesInternal(token) }
                val eventsDeferred = async(Dispatchers.IO) { fetchCalendarEventsInternal(appCtx, token) }
                val driveDeferred = async(Dispatchers.IO) { fetchDriveFilesInternal(token) }
                val contactsDeferred = async(Dispatchers.IO) { fetchContactsInternal(appCtx, token) }
                WorkspaceSyncBundle(
                    emails = emailsDeferred.await(),
                    events = eventsDeferred.await(),
                    driveFiles = driveDeferred.await(),
                    contacts = contactsDeferred.await()
                )
            }
            val now = currentTimeFormatted()

            val summaryParts = mutableListOf<String>()
            if (emails.isNotEmpty()) summaryParts.add("${emails.size} e-mails Gmail")
            if (events.isNotEmpty()) summaryParts.add("${events.size} événements Agenda")
            if (driveFiles.isNotEmpty()) summaryParts.add("${driveFiles.size} fichiers Drive")
            if (contacts.isNotEmpty()) summaryParts.add("${contacts.size} contacts")

            val summaryText = if (summaryParts.isNotEmpty()) {
                "Synchronisé à $now : ${summaryParts.joinToString(" • ")} indexés dans la mémoire RAG de LIA."
            } else if (cachedAccessToken.isBlank()) {
                "Connecté à $now. Intents Android Gmail/Agenda/Drive/Contacts prêts (ajoutez un jeton OAuth2 ou accordez les permissions Contacts/Agenda pour la lecture directe)."
            } else {
                "Synchronisation Google Cloud terminée à $now (aucun nouvel élément trouvé)."
            }

            _state.update {
                it.copy(
                    isSyncingWorkspace = false,
                    recentEmails = emails,
                    upcomingEvents = events,
                    recentDriveFiles = driveFiles,
                    syncedContacts = contacts,
                    lastSyncSummary = summaryText,
                    lastSyncTimestamp = now
                )
            }
        }
    }

    private data class WorkspaceSyncBundle(
        val emails: List<GoogleCloudEmailItem>,
        val events: List<GoogleCloudCalendarEventItem>,
        val driveFiles: List<GoogleCloudDriveFileItem>,
        val contacts: List<GoogleCloudContactItem>
    )

    // =========================================================================
    // 1. GMAIL CLOUD API (gmail.googleapis.com/gmail/v1) + Native Intent Bridge
    // =========================================================================

    private suspend fun fetchGmailMessagesInternal(accessToken: String): List<GoogleCloudEmailItem> =
        withContext(Dispatchers.IO) {
            if (accessToken.isBlank()) return@withContext emptyList()
            try {
                val listJson = executeGoogleGetRequest(
                    url = "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=5",
                    accessToken = accessToken
                ) ?: return@withContext emptyList()

                val messagesArray = JSONObject(listJson).optJSONArray("messages") ?: return@withContext emptyList()
                val msgIds = buildList {
                    for (i in 0 until messagesArray.length()) {
                        val msgId = messagesArray.optJSONObject(i)?.optString("id").orEmpty()
                        if (msgId.isNotBlank()) add(msgId)
                    }
                }
                if (msgIds.isEmpty()) return@withContext emptyList()

                // PERF-02: Fetch metadata for all message IDs concurrently using async + awaitAll
                val fetchedItems = coroutineScope {
                    msgIds.map { msgId ->
                        async(Dispatchers.IO) {
                            val detailJson = executeGoogleGetRequest(
                                url = "https://gmail.googleapis.com/gmail/v1/users/me/messages/$msgId?format=metadata&metadataHeaders=Subject&metadataHeaders=From&metadataHeaders=Date",
                                accessToken = accessToken
                            ) ?: return@async null

                            val detailObj = JSONObject(detailJson)
                            val snippet = detailObj.optString("snippet", "").trim()
                            val headers = detailObj.optJSONObject("payload")?.optJSONArray("headers") ?: JSONArray()
                            var subject = "(Sans objet)"
                            var from = "Expéditeur inconnu"
                            var dateStr = ""
                            for (h in 0 until headers.length()) {
                                val hObj = headers.optJSONObject(h) ?: continue
                                when (hObj.optString("name").lowercase()) {
                                    "subject" -> subject = hObj.optString("value", subject)
                                    "from" -> from = hObj.optString("value", from)
                                    "date" -> dateStr = hObj.optString("value", "").take(22)
                                }
                            }

                            GoogleCloudEmailItem(
                                id = msgId,
                                subject = subject,
                                from = from,
                                snippet = snippet,
                                dateFormatted = dateStr
                            )
                        }
                    }.awaitAll().filterNotNull()
                }

                fetchedItems.forEach { item ->
                    LiaMemoryAndHistoryStore.upsertSouvenir(
                        type = "FAIT",
                        subjectKey = "gmail_msg_${item.id.take(12)}",
                        title = "E-mail Gmail : ${item.subject}",
                        detailValue = "De : ${item.from} — Extrait : ${item.snippet}",
                        searchTags = "gmail,email,mail,${item.subject.lowercase()},${item.from.lowercase()}"
                    )
                }
                fetchedItems
            } catch (e: Exception) {
                Log.w(TAG, "Gmail API fetch warning: ${e.message}")
                emptyList()
            }
        }

    /**
     * Envoie un e-mail via l'API Google Cloud Gmail (`POST /gmail/v1/users/me/messages/send`)
     * si un jeton OAuth2 `gmail.send` est actif, ou ouvre automatiquement le compositeur Gmail Android
     * pré-rempli (`mailto:`) en fallback.
     */
    suspend fun sendOrComposeEmail(
        context: Context,
        toEmailOrName: String,
        subject: String,
        body: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanTarget = toEmailOrName.trim()
        val resolvedEmail = if (cleanTarget.contains("@")) {
            cleanTarget
        } else {
            _state.value.syncedContacts.firstOrNull {
                it.displayName.contains(cleanTarget, ignoreCase = true) && it.emailAddress.isNotBlank()
            }?.emailAddress.orEmpty()
        }

        if (cachedAccessToken.isNotBlank() && resolvedEmail.contains("@")) {
            try {
                val rfc2822 = buildString {
                    append("To: $resolvedEmail\r\n")
                    append("Subject: $subject\r\n")
                    append("Content-Type: text/plain; charset=\"UTF-8\"\r\n\r\n")
                    append(body)
                }
                val encodedRaw = Base64.encodeToString(
                    rfc2822.toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
                val payload = JSONObject().put("raw", encodedRaw).toString()
                val response = executeGooglePostRequest(
                    url = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send",
                    accessToken = cachedAccessToken,
                    jsonBody = payload
                )
                if (response != null) {
                    return@withContext true to "✅ E-mail envoyé directement via l'API Google Cloud Gmail à **$resolvedEmail** (Objet : *$subject*)."
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct Gmail API send failed, falling back to mailto intent: ${e.message}")
            }
        }

        // Fallback: Launch native Gmail composer with pre-filled recipient, subject, and body
        val mailtoUri = buildString {
            append("mailto:")
            if (resolvedEmail.isNotBlank()) append(Uri.encode(resolvedEmail))
            append("?subject=").append(URLEncoder.encode(subject, "UTF-8"))
            append("&body=").append(URLEncoder.encode(body, "UTF-8"))
        }
        val launched = withContext(Dispatchers.Main) {
            LiaUniversalAppsAndGoogleHub.launchGoogleOrUniversalTarget(context, mailtoUri, "Gmail Compose")
        }
        if (launched) {
            true to "📧 Compositeur **Gmail** ouvert avec votre message pré-rempli${if (resolvedEmail.isNotBlank()) " pour **$resolvedEmail**" else ""}."
        } else {
            false to "Impossible d'ouvrir Gmail sur cet appareil."
        }
    }

    // =========================================================================
    // 2. GOOGLE CALENDAR CLOUD API + Android CalendarContract Provider
    // =========================================================================

    private suspend fun fetchCalendarEventsInternal(
        context: Context,
        accessToken: String
    ): List<GoogleCloudCalendarEventItem> = withContext(Dispatchers.IO) {
        val events = mutableListOf<GoogleCloudCalendarEventItem>()

        // A. Query Google Calendar Cloud REST API if OAuth2 token is available
        if (accessToken.isNotBlank()) {
            try {
                val isoNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())
                val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=10&singleEvents=true&orderBy=startTime&timeMin=${URLEncoder.encode(isoNow, "UTF-8")}"
                val jsonStr = executeGoogleGetRequest(url, accessToken)
                if (jsonStr != null) {
                    val items = JSONObject(jsonStr).optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val ev = items.optJSONObject(i) ?: continue
                        val id = ev.optString("id", "cal_$i")
                        val summary = ev.optString("summary", "(Sans titre)")
                        val startObj = ev.optJSONObject("start")
                        val startTime = startObj?.optString("dateTime")?.ifBlank { startObj.optString("date") }.orEmpty()
                        val location = ev.optString("location", "").ifBlank { ev.optString("description", "") }

                        val item = GoogleCloudCalendarEventItem(
                            id = id,
                            title = summary,
                            startFormatted = startTime.replace("T", " ").take(16),
                            locationOrDescription = location.take(80),
                            source = "Google Calendar Cloud API"
                        )
                        events.add(item)

                        LiaMemoryAndHistoryStore.upsertSouvenir(
                            type = "AGENDA",
                            subjectKey = "gcal_${id.take(14)}",
                            title = "Agenda Google : $summary",
                            detailValue = "Prévu le ${item.startFormatted}${if (location.isNotBlank()) " ($location)" else ""}",
                            searchTags = "agenda,calendrier,calendar,rdv,rendez-vous,${summary.lowercase()}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Calendar Cloud API warning: ${e.message}")
            }
        }

        // B. Also query local Android CalendarContract.Events if READ_CALENDAR is granted
        val hasCalPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCalPerm) {
            runCatching {
                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.EVENT_LOCATION
                )
                val nowMs = System.currentTimeMillis()
                val cursor = context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    "${CalendarContract.Events.DTSTART} >= ?",
                    arrayOf(nowMs.toString()),
                    "${CalendarContract.Events.DTSTART} ASC"
                )
                cursor?.use { c ->
                    val idIdx = c.getColumnIndex(CalendarContract.Events._ID)
                    val titleIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
                    val startIdx = c.getColumnIndex(CalendarContract.Events.DTSTART)
                    val locIdx = c.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                    val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
                    var count = 0
                    while (c.moveToNext() && count < 8) {
                        val id = if (idIdx >= 0) c.getLong(idIdx).toString() else "$count"
                        val title = if (titleIdx >= 0) c.getString(titleIdx)?.trim().orEmpty() else ""
                        if (title.isBlank()) continue
                        val dtStart = if (startIdx >= 0) c.getLong(startIdx) else nowMs
                        val loc = if (locIdx >= 0) c.getString(locIdx)?.trim().orEmpty() else ""
                        val formattedDate = fmt.format(Date(dtStart))

                        if (events.none { it.title.equals(title, ignoreCase = true) }) {
                            val item = GoogleCloudCalendarEventItem(
                                id = "local_$id",
                                title = title,
                                startFormatted = formattedDate,
                                locationOrDescription = loc,
                                source = "Android Calendar Provider"
                            )
                            events.add(item)
                            LiaMemoryAndHistoryStore.upsertSouvenir(
                                type = "AGENDA",
                                subjectKey = "local_cal_$id",
                                title = "Agenda : $title",
                                detailValue = "Prévu le $formattedDate${if (loc.isNotBlank()) " à $loc" else ""}",
                                searchTags = "agenda,calendrier,rdv,${title.lowercase()}"
                            )
                            count++
                        }
                    }
                }
            }
        }
        events
    }

    /**
     * Crée un événement dans Google Agenda via l'API Cloud REST, le ContentProvider `CalendarContract`
     * (si WRITE_CALENDAR est accordé) ou l'Intent système Google Agenda.
     */
    suspend fun createCalendarEvent(
        context: Context,
        title: String,
        description: String = "Créé par LIA Assistant",
        startOffsetMinutes: Int = 60
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().ifBlank { "Rendez-vous LIA" }
        val startMs = System.currentTimeMillis() + startOffsetMinutes * 60_000L
        val endMs = startMs + 3_600_000L

        // 1. Try Google Calendar Cloud REST API if OAuth2 token is present
        if (cachedAccessToken.isNotBlank()) {
            try {
                val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val bodyJson = JSONObject().apply {
                    put("summary", cleanTitle)
                    put("description", description)
                    put("start", JSONObject().put("dateTime", isoFmt.format(Date(startMs))))
                    put("end", JSONObject().put("dateTime", isoFmt.format(Date(endMs))))
                }.toString()

                val response = executeGooglePostRequest(
                    url = "https://www.googleapis.com/calendar/v3/calendars/primary/events",
                    accessToken = cachedAccessToken,
                    jsonBody = bodyJson
                )
                if (response != null) {
                    LiaMemoryAndHistoryStore.upsertSouvenir(
                        type = "AGENDA",
                        subjectKey = "gcal_created_${System.currentTimeMillis()}",
                        title = "Agenda Google : $cleanTitle",
                        detailValue = "Créé via Google Calendar Cloud API",
                        searchTags = "agenda,calendrier,rdv,${cleanTitle.lowercase()}"
                    )
                    syncAllWorkspaceData(context)
                    return@withContext true to "📅 Événement **« $cleanTitle »** ajouté directement dans **Google Agenda Cloud API** et indexé dans votre mémoire Room."
                }
            } catch (e: Exception) {
                Log.w(TAG, "Calendar Cloud API insert fallback: ${e.message}")
            }
        }

        // 2. Try direct CalendarContract insert if WRITE_CALENDAR & READ_CALENDAR are granted
        val canWriteCal = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        if (canWriteCal) {
            runCatching {
                val calCursor = context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(CalendarContract.Calendars._ID),
                    null,
                    null,
                    null
                )
                var calId = 1L
                calCursor?.use { c ->
                    if (c.moveToFirst()) {
                        calId = c.getLong(0)
                    }
                }
                val values = ContentValues().apply {
                    put(CalendarContract.Events.DTSTART, startMs)
                    put(CalendarContract.Events.DTEND, endMs)
                    put(CalendarContract.Events.TITLE, cleanTitle)
                    put(CalendarContract.Events.DESCRIPTION, description)
                    put(CalendarContract.Events.CALENDAR_ID, calId)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                }
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                if (uri != null) {
                    LiaMemoryAndHistoryStore.upsertSouvenir(
                        type = "AGENDA",
                        subjectKey = "cal_created_${System.currentTimeMillis()}",
                        title = "Agenda : $cleanTitle",
                        detailValue = "Enregistré dans le calendrier Android",
                        searchTags = "agenda,calendrier,rdv,${cleanTitle.lowercase()}"
                    )
                    syncAllWorkspaceData(context)
                    return@withContext true to "📅 Événement **« $cleanTitle »** enregistré directement dans votre **Google Agenda** et mémorisé dans Room."
                }
            }
        }

        // 3. Fallback: Launch Google Calendar insert intent
        val launched = withContext(Dispatchers.Main) {
            LiaUniversalAppsAndGoogleHub.launchGoogleOrUniversalTarget(
                context,
                "calendar_insert:$cleanTitle",
                cleanTitle
            )
        }
        LiaMemoryAndHistoryStore.upsertSouvenir(
            type = "AGENDA",
            subjectKey = "cal_intent_${System.currentTimeMillis()}",
            title = "Agenda : $cleanTitle",
            detailValue = "Ouvert dans Google Agenda",
            searchTags = "agenda,calendrier,rdv,${cleanTitle.lowercase()}"
        )
        launched to "📅 **Google Agenda** ouvert avec l'événement **« $cleanTitle »** pré-rempli et ajouté à votre mémoire Room."
    }

    // =========================================================================
    // 3. GOOGLE DRIVE CLOUD API (www.googleapis.com/drive/v3/files)
    // =========================================================================

    private suspend fun fetchDriveFilesInternal(accessToken: String): List<GoogleCloudDriveFileItem> =
        withContext(Dispatchers.IO) {
            if (accessToken.isBlank()) return@withContext emptyList()
            val files = mutableListOf<GoogleCloudDriveFileItem>()
            try {
                val url = "https://www.googleapis.com/drive/v3/files?pageSize=10&orderBy=modifiedTime%20desc&fields=files(id,name,mimeType,modifiedTime,webViewLink)"
                val jsonStr = executeGoogleGetRequest(url, accessToken) ?: return@withContext emptyList()
                val filesArray = JSONObject(jsonStr).optJSONArray("files") ?: return@withContext emptyList()
                for (i in 0 until filesArray.length()) {
                    val f = filesArray.optJSONObject(i) ?: continue
                    val id = f.optString("id")
                    val name = f.optString("name")
                    if (name.isBlank()) continue
                    val mime = f.optString("mimeType", "application/octet-stream")
                    val link = f.optString("webViewLink", "https://drive.google.com/file/d/$id/view")
                    val modTime = f.optString("modifiedTime", "").replace("T", " ").take(16)

                    val item = GoogleCloudDriveFileItem(
                        id = id,
                        name = name,
                        mimeType = mime,
                        webViewLink = link,
                        modifiedTime = modTime
                    )
                    files.add(item)

                    LiaMemoryAndHistoryStore.upsertSouvenir(
                        type = "FAIT",
                        subjectKey = "gdrive_${id.take(12)}",
                        title = "Fichier Google Drive : $name",
                        detailValue = "Modifié le $modTime — Lien : $link",
                        searchTags = "drive,fichier,document,cloud,${name.lowercase()}"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Drive Cloud API warning: ${e.message}")
            }
            files
        }

    // =========================================================================
    // 4. GOOGLE CONTACTS (People API v1) + Android ContactsContract
    // =========================================================================

    private suspend fun fetchContactsInternal(
        context: Context,
        accessToken: String
    ): List<GoogleCloudContactItem> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<GoogleCloudContactItem>()

        // A. Google People Cloud REST API (`people.googleapis.com/v1/people/me/connections`)
        if (accessToken.isNotBlank()) {
            try {
                val url = "https://people.googleapis.com/v1/people/me/connections?personFields=names,emailAddresses,phoneNumbers&pageSize=50"
                val jsonStr = executeGoogleGetRequest(url, accessToken)
                if (jsonStr != null) {
                    val connections = JSONObject(jsonStr).optJSONArray("connections") ?: JSONArray()
                    for (i in 0 until connections.length()) {
                        val person = connections.optJSONObject(i) ?: continue
                        val resName = person.optString("resourceName", "person_$i")
                        val names = person.optJSONArray("names")
                        val displayName = names?.optJSONObject(0)?.optString("displayName")?.trim().orEmpty()
                        if (displayName.isBlank()) continue

                        val phones = person.optJSONArray("phoneNumbers")
                        val phone = phones?.optJSONObject(0)?.optString("value")?.trim().orEmpty()

                        val emails = person.optJSONArray("emailAddresses")
                        val email = emails?.optJSONObject(0)?.optString("value")?.trim().orEmpty()

                        val item = GoogleCloudContactItem(
                            id = resName,
                            displayName = displayName,
                            phoneNumber = phone,
                            emailAddress = email,
                            source = "Google People Cloud API"
                        )
                        contacts.add(item)

                        if (phone.isNotBlank()) {
                            val cleanKey = displayName.lowercase().replace(Regex("[^a-z0-9]"), "_")
                            LiaMemoryAndHistoryStore.upsertUserFact(
                                key = "contact_$cleanKey",
                                label = "Contact $displayName",
                                value = phone
                            )
                        }
                        LiaMemoryAndHistoryStore.upsertSouvenir(
                            type = "CONTACT",
                            subjectKey = "gcontact_${displayName.lowercase().replace(" ", "_")}",
                            title = "Contact Google : $displayName",
                            detailValue = buildString {
                                if (phone.isNotBlank()) append("Tél : $phone ")
                                if (email.isNotBlank()) append("E-mail : $email")
                            }.trim().ifBlank { "Contact Google synchronisé" },
                            searchTags = "contact,google,telephone,email,${displayName.lowercase()},$phone,$email"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "People Cloud API warning: ${e.message}")
            }
        }

        // B. Local Android ContactsContract sync via SafeContactResolver
        val deviceContacts = SafeContactResolver.readRealAndroidContacts(context)
        if (deviceContacts.isNotEmpty()) {
            SafeContactResolver.syncAndroidContactsIntoRoom(context)
            for ((devName, devPhone) in deviceContacts) {
                if (contacts.none { it.displayName.equals(devName, ignoreCase = true) }) {
                    contacts.add(
                        GoogleCloudContactItem(
                            id = "android_${devName.lowercase()}",
                            displayName = devName,
                            phoneNumber = devPhone,
                            emailAddress = "",
                            source = "Android Contacts Provider"
                        )
                    )
                }
            }
        }

        contacts
    }

    // =========================================================================
    // Low-level HTTPS Helpers for Google Cloud REST APIs
    // =========================================================================

    private fun executeGoogleGetRequest(url: String, accessToken: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                handleHttpAuthFailureIfNeeded(code, url)
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google GET request failed for ${url.substringBefore("?")}: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun executeGooglePostRequest(url: String, accessToken: String, jsonBody: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }
            val code = conn.responseCode
            if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                handleHttpAuthFailureIfNeeded(code, url)
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google POST request failed for ${url.substringBefore("?")}: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun handleHttpAuthFailureIfNeeded(statusCode: Int, url: String) {
        val endpoint = url.substringBefore("?")
        Log.w(TAG, "Google Workspace API returned HTTP $statusCode for $endpoint")
        if (statusCode == 401 || statusCode == 403) {
            _state.update {
                it.copy(
                    authErrorMessage = "Jeton OAuth2 Google Cloud expiré ou insuffisant (HTTP $statusCode). Reconnectez-vous ou mettez à jour votre jeton."
                )
            }
        }
    }
}
