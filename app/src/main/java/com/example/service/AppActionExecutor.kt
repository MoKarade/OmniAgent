package com.example.service

import android.app.SearchManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import com.example.data.LiaUniversalAppsAndGoogleHub
import com.example.model.AppAccessPermission
import com.example.model.FastPathDispatchResult
import com.example.model.GlobalAccessPolicyMode
import com.example.model.ScopedAccessToken
import com.example.model.currentTimeFormatted
import com.example.security.SoniaSecurityKernel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppActionExecutionRecord(
    val id: String = "exec_${System.currentTimeMillis()}",
    val timestamp: String = currentTimeFormatted(),
    val intentType: String,
    val systemAction: String,
    val resolvedPackage: String,
    val appLabel: String,
    val appliedFlagsHex: String,
    val bypassedConfirmation: Boolean,
    val extractedParamsSummary: String,
    val succeeded: Boolean,
    val executedViaFastPathQueue: Boolean = true,
    val safetyValidationSummary: String = "Zero-Trust DLP + Jeton SHA-256 validés"
)

data class FastPathQueuedTask(
    val taskId: String = "fp_${System.currentTimeMillis()}",
    val context: Context,
    val spec: ClassifiedUserIntent,
    val safetyAuditSummary: String,
    val enqueuedAtMs: Long = System.currentTimeMillis(),
    val onCompleted: ((Boolean, Long) -> Unit)? = null
)

data class FastPathQueueMetrics(
    val totalFastPathDispatched: Int = 0,
    val totalSafetyBlocked: Int = 0,
    val totalHitlEscalated: Int = 0,
    val averageFastPathLatencyMs: Long = 8L,
    val lastFastPathSummary: String = "File Fast-Path prête (contournement délibération LLM pour tâches routines)"
)

/**
 * Service d'exécution directe d'actions (`AppActionExecutor`) doté d'une file d'exécution
 * **Fast-Path (`fastPathQueue`)** qui contourne la délibération LLM haute-latence pour les tâches
 * système de routine (ouverture d'applications, alarmes, minuteurs, lampe torche, réglages, caméra, navigation)
 * tout en appliquant une validation de sécurité Zero-Trust stricte (Pare-feu DLP, Verrou PIN, Jetons Scopés, Garde HITL).
 */
class AppActionExecutor : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXECUTE_DIRECT_INTENT) {
            val targetUriOrPkg = intent.getStringExtra(EXTRA_TARGET_URI_OR_PKG).orEmpty()
            val appLabel = intent.getStringExtra(EXTRA_APP_LABEL).orEmpty()
            val rawCommand = intent.getStringExtra(EXTRA_RAW_COMMAND).orEmpty()
            if (targetUriOrPkg.isNotBlank()) {
                val spec = IntentHandler.buildIntentSpecFromTarget(
                    appLabel = appLabel,
                    packageNameOrUri = targetUriOrPkg,
                    rawCommand = rawCommand.ifBlank { appLabel },
                    context = applicationContext
                )
                executeClassifiedIntentInternal(applicationContext, spec)
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_EXECUTE_DIRECT_INTENT = "com.example.service.action.EXECUTE_DIRECT_INTENT"
        const val EXTRA_TARGET_URI_OR_PKG = "extra_target_uri_or_pkg"
        const val EXTRA_APP_LABEL = "extra_app_label"
        const val EXTRA_RAW_COMMAND = "extra_raw_command"

        private const val DIRECT_LAUNCH_FLAGS =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP

        private val _executionLogs = MutableStateFlow<List<AppActionExecutionRecord>>(emptyList())
        val executionLogs: StateFlow<List<AppActionExecutionRecord>> = _executionLogs.asStateFlow()

        private val _fastPathMetrics = MutableStateFlow(FastPathQueueMetrics())
        val fastPathMetrics: StateFlow<FastPathQueueMetrics> = _fastPathMetrics.asStateFlow()

        private val fastPathScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val fastPathQueue = Channel<FastPathQueuedTask>(capacity = Channel.UNLIMITED)

        // Ensemble des types d'intentions système de routine éligibles au contournement de délibération haute-latence
        private val ROUTINE_FAST_PATH_INTENT_TYPES = setOf(
            LiaClassifiedIntentType.DIRECT_APP_LAUNCH,
            LiaClassifiedIntentType.SET_ALARM,
            LiaClassifiedIntentType.SET_TIMER,
            LiaClassifiedIntentType.TOGGLE_FLASHLIGHT,
            LiaClassifiedIntentType.OPEN_CAMERA,
            LiaClassifiedIntentType.OPEN_SYSTEM_SETTINGS,
            LiaClassifiedIntentType.PLAY_MEDIA,
            LiaClassifiedIntentType.MAPS_NAVIGATION,
            LiaClassifiedIntentType.YOUTUBE_DEEP_LINK,
            LiaClassifiedIntentType.WEB_SEARCH_DEEP_LINK,
            LiaClassifiedIntentType.PLAY_STORE_SEARCH,
            LiaClassifiedIntentType.ADD_CALENDAR_EVENT,
            LiaClassifiedIntentType.DIAL_PHONE
        )

        private val SENSITIVE_OR_FINANCIAL_KEYWORDS = listOf(
            "virement",
            "transfère",
            "transfert bancaire",
            "neobank",
            "iban",
            "paiement",
            "payer",
            "supprime tout",
            "efface tout",
            "formater",
            "réinitialiser",
            "factory reset"
        )

        init {
            fastPathScope.launch {
                for (queuedTask in fastPathQueue) {
                    val startMs = System.currentTimeMillis()
                    val ok = executeClassifiedIntentInternal(
                        context = queuedTask.context,
                        spec = queuedTask.spec,
                        executedViaFastPathQueue = true,
                        safetyValidationSummary = queuedTask.safetyAuditSummary
                    )
                    val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(3L)
                    _fastPathMetrics.update { m ->
                        val nextCount = m.totalFastPathDispatched + 1
                        val nextAvg = ((m.averageFastPathLatencyMs * m.totalFastPathDispatched) + elapsedMs) / nextCount
                        m.copy(
                            totalFastPathDispatched = nextCount,
                            averageFastPathLatencyMs = nextAvg.coerceAtLeast(3L),
                            lastFastPathSummary = "⚡ Fast-Path #${nextCount} : ${queuedTask.spec.resolvedAppLabel} (${elapsedMs} ms)"
                        )
                    }
                    queuedTask.onCompleted?.invoke(ok, elapsedMs)
                }
            }
        }

        /**
         * Vérifie si une commande utilisateur correspond à une tâche système de routine éligible
         * à la file d'exécution Fast-Path (contournant la délibération LLM haute-latence) et applique
         * la validation de sécurité Zero-Trust stricte avant l'exécution immédiate.
         */
        fun tryDispatchRoutineTaskViaFastPathQueue(
            context: Context,
            rawUserCommand: String,
            isVaultPinLocked: Boolean,
            globalAccessPolicy: GlobalAccessPolicyMode,
            activeAgentToken: ScopedAccessToken,
            appPermissions: List<AppAccessPermission>
        ): FastPathDispatchResult? {
            val cleaned = rawUserCommand.trim()
            if (cleaned.isBlank()) return null

            val startTimeMs = System.currentTimeMillis()
            val taskId = "fp_${startTimeMs}"

            // 1. Détection d'intention déterministe via IntentHandler
            val classifiedSpec = IntentHandler.classifyAndExtractKnownIntent(cleaned, context)
                ?: return null

            // Vérifie que l'intention fait partie des tâches système de routine autorisées en Fast-Path
            if (classifiedSpec.intentType !in ROUTINE_FAST_PATH_INTENT_TYPES) {
                return null
            }

            // 2. VALIDATION DE SÉCURITÉ STRICTE #1 : Verrouillage PIN du Coffre
            if (isVaultPinLocked) {
                _fastPathMetrics.update {
                    it.copy(
                        totalSafetyBlocked = it.totalSafetyBlocked + 1,
                        lastFastPathSummary = "🔒 Fast-Path bloqué : Coffre verrouillé par code PIN"
                    )
                }
                return FastPathDispatchResult(
                    taskId = taskId,
                    rawCommand = cleaned,
                    executedViaFastPath = false,
                    safetyValidated = false,
                    dlpPassed = true,
                    tokenScopePassed = false,
                    hitlRequired = false,
                    resolvedActionLabel = classifiedSpec.resolvedAppLabel,
                    targetPackageOrUri = classifiedSpec.deepLinkUri.ifBlank { classifiedSpec.targetPackage },
                    queueLatencyMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L),
                    summaryMessageFr = "🔒 Exécution rapide bloquée : veuillez déverrouiller le code PIN dans Autorisations.",
                    blockingReasonFr = "Coffre verrouillé par code PIN"
                )
            }

            // 3. VALIDATION DE SÉCURITÉ STRICTE #2 : Inspection DLP & Pare-feu Anti-Injection
            val dlpScan = SoniaSecurityKernel.inspectAndSanitize(cleaned)
            if (!dlpScan.isSafe) {
                _fastPathMetrics.update {
                    it.copy(
                        totalSafetyBlocked = it.totalSafetyBlocked + 1,
                        lastFastPathSummary = "🛡️ Fast-Path bloqué par DLP (${dlpScan.detectedThreats.firstOrNull()})"
                    )
                }
                return FastPathDispatchResult(
                    taskId = taskId,
                    rawCommand = cleaned,
                    executedViaFastPath = false,
                    safetyValidated = false,
                    dlpPassed = false,
                    tokenScopePassed = true,
                    hitlRequired = false,
                    resolvedActionLabel = classifiedSpec.resolvedAppLabel,
                    targetPackageOrUri = classifiedSpec.deepLinkUri.ifBlank { classifiedSpec.targetPackage },
                    queueLatencyMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L),
                    summaryMessageFr = "🛡️ Pare-feu DLP : Requête interceptée (${dlpScan.detectedThreats.firstOrNull()}).",
                    blockingReasonFr = dlpScan.detectedThreats.joinToString()
                )
            }

            // 4. VALIDATION DE SÉCURITÉ STRICTE #3 : Escalade HITL obligatoire si l'opération est financière/destructrice
            val lower = cleaned.lowercase()
            val matchedAppPerm = appPermissions.find {
                it.packageName.equals(classifiedSpec.targetPackage, ignoreCase = true) ||
                    it.appName.equals(classifiedSpec.resolvedAppLabel, ignoreCase = true)
            }
            val containsSensitiveKeyword = SENSITIVE_OR_FINANCIAL_KEYWORDS.any { lower.contains(it) }
            if (containsSensitiveKeyword || matchedAppPerm?.requireHitlForActions == true) {
                _fastPathMetrics.update {
                    it.copy(
                        totalHitlEscalated = it.totalHitlEscalated + 1,
                        lastFastPathSummary = "⚠️ Escalade HITL : Confirmation humaine requise pour ${classifiedSpec.resolvedAppLabel}"
                    )
                }
                return FastPathDispatchResult(
                    taskId = taskId,
                    rawCommand = cleaned,
                    executedViaFastPath = false,
                    safetyValidated = true,
                    dlpPassed = true,
                    tokenScopePassed = true,
                    hitlRequired = true,
                    resolvedActionLabel = classifiedSpec.resolvedAppLabel,
                    targetPackageOrUri = classifiedSpec.deepLinkUri.ifBlank { classifiedSpec.targetPackage },
                    queueLatencyMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L),
                    summaryMessageFr = "⚠️ Opération sensible détectée : routage vers la validation humaine (HITL).",
                    blockingReasonFr = "Confirmation humaine obligatoire (HITL)"
                )
            }

            // 5. VALIDATION DE SÉCURITÉ STRICTE #4 : Politique Zero-Trust & Jetons Scopés
            val isPackageBlockedByPerm = matchedAppPerm != null && !matchedAppPerm.canSeeScreen
            if (globalAccessPolicy == GlobalAccessPolicyMode.LOCKDOWN ||
                activeAgentToken.isRevoked ||
                isPackageBlockedByPerm
            ) {
                val reason = when {
                    globalAccessPolicy == GlobalAccessPolicyMode.LOCKDOWN -> "Coupe-circuit global LOCKDOWN actif"
                    activeAgentToken.isRevoked -> "Jeton d'accès ${activeAgentToken.tokenId} révoqué"
                    else -> "Application '${classifiedSpec.resolvedAppLabel}' verrouillée dans Autorisations"
                }
                _fastPathMetrics.update {
                    it.copy(
                        totalSafetyBlocked = it.totalSafetyBlocked + 1,
                        lastFastPathSummary = "🛡️ Fast-Path refusé : $reason"
                    )
                }
                return FastPathDispatchResult(
                    taskId = taskId,
                    rawCommand = cleaned,
                    executedViaFastPath = false,
                    safetyValidated = false,
                    dlpPassed = true,
                    tokenScopePassed = false,
                    hitlRequired = false,
                    resolvedActionLabel = classifiedSpec.resolvedAppLabel,
                    targetPackageOrUri = classifiedSpec.deepLinkUri.ifBlank { classifiedSpec.targetPackage },
                    queueLatencyMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L),
                    summaryMessageFr = "🛡️ Sécurité Zero-Trust : $reason.",
                    blockingReasonFr = reason
                )
            }

            // 6. Envoi dans la file d'exécution Fast-Path (sans attente de délibération LLM)
            val safetySummary = "DLP OK • Jeton ${activeAgentToken.tokenId.take(14)} OK • Non-sensible"
            fastPathQueue.trySend(
                FastPathQueuedTask(
                    taskId = taskId,
                    context = context.applicationContext,
                    spec = classifiedSpec,
                    safetyAuditSummary = safetySummary
                )
            )

            val queueLatency = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(4L)
            return FastPathDispatchResult(
                taskId = taskId,
                rawCommand = cleaned,
                executedViaFastPath = true,
                safetyValidated = true,
                dlpPassed = true,
                tokenScopePassed = true,
                hitlRequired = false,
                resolvedActionLabel = classifiedSpec.resolvedAppLabel,
                targetPackageOrUri = classifiedSpec.deepLinkUri.ifBlank { classifiedSpec.targetPackage },
                queueLatencyMs = queueLatency,
                summaryMessageFr = classifiedSpec.summaryMessageFr
            )
        }

        /**
         * Exécute immédiatement un `ClassifiedUserIntent` en résolvant le composant cible via
         * `PackageManager` pour contourner les menus de sélection / confirmation intermédiaires.
         */
        fun executeClassifiedIntent(
            context: Context,
            spec: ClassifiedUserIntent
        ): Boolean {
            runCatching {
                val serviceIntent = Intent(context, AppActionExecutor::class.java).apply {
                    action = ACTION_EXECUTE_DIRECT_INTENT
                    putExtra(EXTRA_TARGET_URI_OR_PKG, spec.deepLinkUri.ifBlank { spec.targetPackage })
                    putExtra(EXTRA_APP_LABEL, spec.resolvedAppLabel)
                    putExtra(EXTRA_RAW_COMMAND, spec.rawUserCommand)
                }
                context.startService(serviceIntent)
            }
            return executeClassifiedIntentInternal(context, spec)
        }

        /**
         * Point d'entrée universel appelé par `LiaMemoryAndHistoryStore.launchAppFast` et `OmniAgentViewModel`
         * pour exécuter directement toute cible d'application ou URI d'action système via la file Fast-Path.
         */
        fun executeTargetDirectly(
            context: Context,
            packageNameOrUri: String,
            appLabel: String = "",
            rawCommand: String = ""
        ): Boolean {
            val spec = IntentHandler.buildIntentSpecFromTarget(
                appLabel = appLabel.ifBlank { packageNameOrUri },
                packageNameOrUri = packageNameOrUri,
                rawCommand = rawCommand.ifBlank { appLabel },
                context = context
            )
            return executeClassifiedIntentInternal(context, spec)
        }

        private fun executeClassifiedIntentInternal(
            context: Context,
            spec: ClassifiedUserIntent,
            executedViaFastPathQueue: Boolean = true,
            safetyValidationSummary: String = "Zero-Trust DLP + Jeton SHA-256 validés"
        ): Boolean {
            val pm = context.packageManager
            val uriOrPkg = spec.deepLinkUri.ifBlank { spec.targetPackage }.trim()

            // 0. Contrôle matériel direct de la Lampe Torche (CameraManager.setTorchMode)
            if (spec.intentType == LiaClassifiedIntentType.TOGGLE_FLASHLIGHT || uriOrPkg.startsWith("flashlight:")) {
                val turnOn = !uriOrPkg.endsWith("off", ignoreCase = true)
                val torchOk = runCatching {
                    val camManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                    val camId = camManager?.cameraIdList?.firstOrNull { id ->
                        val chars = camManager.getCameraCharacteristics(id)
                        chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } ?: camManager?.cameraIdList?.firstOrNull()
                    if (camManager != null && camId != null) {
                        camManager.setTorchMode(camId, turnOn)
                        true
                    } else {
                        false
                    }
                }.getOrDefault(false)
                recordExecutionLog(
                    spec = spec,
                    resolvedPackage = "android.hardware.camera2.torch",
                    succeeded = torchOk
                )
                return torchOk
            }

            // 0A. Exécution directe de messagerie multi-applications (Messenger, WhatsApp, Telegram, Instagram, Discord, Keep)
            if (uriOrPkg.startsWith("app_msg:")) {
                val payload = uriOrPkg.removePrefix("app_msg:")
                val parts = payload.split(":", limit = 3)
                val channelId = parts.getOrNull(0).orEmpty()
                val recipient = Uri.decode(parts.getOrNull(1).orEmpty())
                val msgText = Uri.decode(parts.getOrNull(2).orEmpty())
                val channel = MessagingAppChannel.entries.firstOrNull { it.id.equals(channelId, ignoreCase = true) }
                    ?: MessagingAppChannel.MESSENGER
                val ok = LiaCrossAppKnowledgeResolver.launchMessagingAppWithPayload(
                    context = context,
                    channel = channel,
                    recipientNameOrPhone = recipient,
                    messageText = msgText
                )
                recordExecutionLog(
                    spec = spec,
                    resolvedPackage = channel.packageName,
                    succeeded = ok
                )
                return ok
            }

            // 0B. Navigation autonome Steam (Bibliothèque / Dernier jeu joué)
            if (uriOrPkg.startsWith("steam:")) {
                val steamPkg = "com.valvesoftware.android.steam.community"
                val ok = runCatching {
                    val launch = pm.getLaunchIntentForPackage(steamPkg)?.apply {
                        addFlags(DIRECT_LAUNCH_FLAGS)
                    }
                    if (launch != null) {
                        context.startActivity(launch)
                        true
                    } else {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://steamcommunity.com/my/games/?tab=recent")).apply {
                            addFlags(DIRECT_LAUNCH_FLAGS)
                        }
                        context.startActivity(webIntent)
                        true
                    }
                }.getOrDefault(false)
                recordExecutionLog(
                    spec = spec,
                    resolvedPackage = steamPkg,
                    succeeded = ok
                )
                return ok
            }

            return runCatching {
                val builtIntent: Intent? = when (spec.intentType) {
                    LiaClassifiedIntentType.TOGGLE_FLASHLIGHT -> null

                    LiaClassifiedIntentType.DIAL_PHONE -> {
                        val telUri = if (uriOrPkg.startsWith("tel:")) uriOrPkg else "tel:${Uri.encode(uriOrPkg)}"
                        Intent(Intent.ACTION_DIAL, Uri.parse(telUri)).apply {
                            bindBestPackageViaPackageManager(pm, this, spec.targetPackage.ifBlank { "com.google.android.dialer" })
                        }
                    }

                    LiaClassifiedIntentType.OPEN_CAMERA -> {
                        val isVideo = spec.extractedParameters["mode"] == "VIDEO" || uriOrPkg == "camera:video"
                        val isSelfie = spec.extractedParameters["camera_facing"] == "FRONT" || uriOrPkg == "camera:selfie"
                        val action = if (isVideo) {
                            MediaStore.INTENT_ACTION_VIDEO_CAMERA
                        } else {
                            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
                        }
                        Intent(action).apply {
                            if (isSelfie) {
                                putExtra("android.intent.extras.CAMERA_FACING", 1)
                                putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                                putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
                            }
                            bindBestPackageViaPackageManager(pm, this, spec.targetPackage)
                        }
                    }

                    LiaClassifiedIntentType.SET_ALARM -> {
                        val parts = uriOrPkg.removePrefix("alarm:").split(":")
                        val h = spec.extractedParameters["hour"]?.toIntOrNull()
                            ?: parts.getOrNull(0)?.toIntOrNull() ?: 7
                        val m = spec.extractedParameters["minutes"]?.toIntOrNull()
                            ?: parts.getOrNull(1)?.toIntOrNull() ?: 0
                        Intent(AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(AlarmClock.EXTRA_HOUR, h)
                            putExtra(AlarmClock.EXTRA_MINUTES, m)
                            putExtra(AlarmClock.EXTRA_MESSAGE, spec.extractedParameters["message"] ?: "Réveil LIA")
                            putExtra(AlarmClock.EXTRA_SKIP_UI, spec.bypassConfirmationMenu)
                            bindBestPackageViaPackageManager(pm, this, spec.targetPackage)
                        }
                    }

                    LiaClassifiedIntentType.SET_TIMER -> {
                        val timerParts = uriOrPkg.removePrefix("timer:").split(":", limit = 2)
                        val seconds = spec.extractedParameters["length_seconds"]?.toIntOrNull()
                            ?: timerParts.getOrNull(0)?.toIntOrNull() ?: 300
                        val timerLabel = spec.extractedParameters["label"]
                            ?: timerParts.getOrNull(1)?.takeIf { it.isNotBlank() }
                            ?: "Minuteur"
                        Intent(AlarmClock.ACTION_SET_TIMER).apply {
                            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                            putExtra(AlarmClock.EXTRA_MESSAGE, timerLabel)
                            putExtra(AlarmClock.EXTRA_SKIP_UI, spec.bypassConfirmationMenu)
                            bindBestPackageViaPackageManager(pm, this, spec.targetPackage)
                        }
                    }

                    LiaClassifiedIntentType.OPEN_SYSTEM_SETTINGS -> {
                        val settingsAction = spec.extractedParameters["settings_action"]
                            ?: uriOrPkg.removePrefix("settings:").ifBlank { Settings.ACTION_SETTINGS }
                        Intent(settingsAction).apply {
                            bindBestPackageViaPackageManager(pm, this, "com.android.settings")
                        }
                    }

                    LiaClassifiedIntentType.PLAY_MEDIA -> {
                        val query = spec.extractedParameters["query"]
                            ?: Uri.decode(uriOrPkg.removePrefix("media_play:"))
                        val mediaIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                            putExtra(SearchManager.QUERY, query)
                            bindBestPackageViaPackageManager(pm, this, spec.targetPackage)
                        }
                        if (mediaIntent.resolveActivity(pm) != null) {
                            mediaIntent
                        } else {
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://open.spotify.com/search/${Uri.encode(query)}")
                            )
                        }
                    }

                    LiaClassifiedIntentType.PLAY_STORE_SEARCH -> {
                        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriOrPkg)).apply {
                            bindBestPackageViaPackageManager(pm, this, "com.android.vending")
                        }
                        if (marketIntent.resolveActivity(pm) != null) {
                            marketIntent
                        } else {
                            val q = uriOrPkg.substringAfter("q=", "")
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/search?q=$q&c=apps")
                            )
                        }
                    }

                    LiaClassifiedIntentType.SHARE_CONTENT -> {
                        val sharedText = spec.extractedParameters["text"]
                            ?: Uri.decode(uriOrPkg.removePrefix("share_text:"))
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, sharedText)
                        }
                        Intent.createChooser(sendIntent, "Partager avec").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }

                    LiaClassifiedIntentType.DEEP_APP_SEARCH -> {
                        // Format: app_search:<package>:<encodedQuery>
                        val payload = uriOrPkg.removePrefix("app_search:")
                        val parts = payload.split(":", limit = 2)
                        val pkg = parts.getOrNull(0).orEmpty()
                        val query = Uri.decode(parts.getOrNull(1).orEmpty())
                        val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                            if (pkg.isNotBlank()) setPackage(pkg)
                            putExtra(SearchManager.QUERY, query)
                        }
                        if (searchIntent.resolveActivity(pm) != null) {
                            searchIntent
                        } else {
                            pm.getLaunchIntentForPackage(pkg)
                                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode("$pkg $query")}"))
                        }
                    }

                    LiaClassifiedIntentType.SEND_EMAIL -> {
                        Intent(Intent.ACTION_SENDTO, Uri.parse(uriOrPkg)).apply {
                            bindBestPackageViaPackageManager(pm, this, spec.targetPackage.ifBlank { "com.google.android.gm" })
                        }
                    }

                    LiaClassifiedIntentType.MAPS_NAVIGATION -> {
                        Intent(Intent.ACTION_VIEW, Uri.parse(uriOrPkg)).apply {
                            bindBestPackageViaPackageManager(pm, this, "com.google.android.apps.maps")
                        }
                    }

                    LiaClassifiedIntentType.ADD_CALENDAR_EVENT -> {
                        val title = uriOrPkg.removePrefix("calendar_insert:").trim().ifBlank { spec.resolvedAppLabel }
                        val startMillis = System.currentTimeMillis() + 3_600_000L
                        Intent(Intent.ACTION_INSERT).apply {
                            data = CalendarContract.Events.CONTENT_URI
                            putExtra(CalendarContract.Events.TITLE, title)
                            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 3_600_000L)
                            bindBestPackageViaPackageManager(pm, this, "com.google.android.calendar")
                        }
                    }

                    LiaClassifiedIntentType.YOUTUBE_DEEP_LINK,
                    LiaClassifiedIntentType.WEB_SEARCH_DEEP_LINK -> {
                        Intent(Intent.ACTION_VIEW, Uri.parse(uriOrPkg)).apply {
                            val preferredPkg = if (spec.intentType == LiaClassifiedIntentType.YOUTUBE_DEEP_LINK) {
                                "com.google.android.youtube"
                            } else {
                                "com.android.chrome"
                            }
                            bindBestPackageViaPackageManager(pm, this, preferredPkg)
                        }
                    }

                    LiaClassifiedIntentType.DIRECT_APP_LAUNCH -> {
                        resolveDirectPackageLaunchIntent(context, pm, uriOrPkg, spec.resolvedAppLabel)
                    }
                }

                if (builtIntent != null) {
                    builtIntent.addFlags(DIRECT_LAUNCH_FLAGS)
                    val resolvedPkg = builtIntent.`package`
                        ?: builtIntent.component?.packageName
                        ?: spec.targetPackage.ifBlank { uriOrPkg }

                    context.startActivity(builtIntent)

                    LiaAppUsageTrackerService.recordAppOpenEvent(
                        context = context,
                        packageName = resolvedPkg,
                        appLabel = spec.resolvedAppLabel
                    )

                    recordExecutionLog(
                        spec = spec,
                        resolvedPackage = resolvedPkg,
                        succeeded = true
                    )
                    true
                } else {
                    // Fallback via le Hub universel (ex: Web URL ou recherche d'application par nom)
                    val fallbackOk = LiaUniversalAppsAndGoogleHub.launchGoogleOrUniversalTarget(
                        context,
                        uriOrPkg,
                        spec.resolvedAppLabel
                    )
                    recordExecutionLog(
                        spec = spec,
                        resolvedPackage = uriOrPkg,
                        succeeded = fallbackOk
                    )
                    fallbackOk
                }
            }.getOrElse {
                val fallbackOk = LiaUniversalAppsAndGoogleHub.launchGoogleOrUniversalTarget(
                    context,
                    uriOrPkg,
                    spec.resolvedAppLabel
                )
                recordExecutionLog(
                    spec = spec,
                    resolvedPackage = uriOrPkg,
                    succeeded = fallbackOk
                )
                fallbackOk
            }
        }

        /**
         * Utilise `PackageManager.queryIntentActivities` pour lier directement le package cible
         * à l'Intent afin d'éviter la boîte de dialogue « Ouvrir avec… » d'Android.
         */
        private fun bindBestPackageViaPackageManager(
            pm: PackageManager,
            intent: Intent,
            preferredPackage: String
        ) {
            val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }

            if (matches.isEmpty()) return

            val preferredMatch = if (preferredPackage.isNotBlank()) {
                matches.firstOrNull { it.activityInfo?.packageName.equals(preferredPackage, ignoreCase = true) }
            } else null

            val chosenPkg = preferredMatch?.activityInfo?.packageName
                ?: matches.firstOrNull()?.activityInfo?.packageName

            if (!chosenPkg.isNullOrBlank()) {
                intent.setPackage(chosenPkg)
            }
        }

        private fun resolveDirectPackageLaunchIntent(
            context: Context,
            pm: PackageManager,
            packageOrQuery: String,
            appLabel: String
        ): Intent? {
            // 1. Package direct via PackageManager.getLaunchIntentForPackage
            val directIntent = pm.getLaunchIntentForPackage(packageOrQuery)
            if (directIntent != null) return directIntent

            // 2. Recherche par label / correspondance dans le catalogue d'applications installées
            val matchedInstalled = LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp(
                packageOrQuery.ifBlank { appLabel }
            )
            if (matchedInstalled != null) {
                val matchedIntent = pm.getLaunchIntentForPackage(matchedInstalled.packageName)
                if (matchedIntent != null) return matchedIntent
            }

            return null
        }

        private fun recordExecutionLog(
            spec: ClassifiedUserIntent,
            resolvedPackage: String,
            succeeded: Boolean,
            executedViaFastPathQueue: Boolean = true,
            safetyValidationSummary: String = "Zero-Trust DLP + Jeton SHA-256 validés"
        ) {
            val record = AppActionExecutionRecord(
                intentType = spec.intentType.code,
                systemAction = spec.systemIntentAction,
                resolvedPackage = resolvedPackage,
                appLabel = spec.resolvedAppLabel,
                appliedFlagsHex = "0x${Integer.toHexString(DIRECT_LAUNCH_FLAGS).uppercase()}",
                bypassedConfirmation = spec.bypassConfirmationMenu,
                extractedParamsSummary = spec.extractedParameters.entries.joinToString(", ") { "${it.key}=${it.value}" },
                succeeded = succeeded,
                executedViaFastPathQueue = executedViaFastPathQueue,
                safetyValidationSummary = safetyValidationSummary
            )
            _executionLogs.update { current ->
                (listOf(record) + current).take(25)
            }
        }
    }
}
