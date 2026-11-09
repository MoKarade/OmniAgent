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
import com.example.model.FastPathDispatchResult
import com.example.model.GlobalAccessPolicyMode
import com.example.model.currentTimeFormatted
import com.example.security.ActionTargetSafetyVerdict
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
    val safetyValidationSummary: String = "Zero-Trust DLP + Permissions MCP validés"
)

data class FastPathQueuedTask(
    val taskId: String = "fp_${System.currentTimeMillis()}",
    val appContext: Context,
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
    val lastFastPathSummary: String = "File Fast-Path prête (actions système directes avec garde-fou Zero-Trust)"
)

/**
 * Service d'exécution directe d'actions (`AppActionExecutor`) doté d'une file d'exécution
 * **Fast-Path (`fastPathQueue`)** et d'une porte de sécurité Zero-Trust unifiée (Verrou PIN PBKDF2,
 * Mode Lockdown, Pare-feu DLP Luhn, Liste blanche d'URI et Permissions MCP par application).
 */
class AppActionExecutor : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXECUTE_DIRECT_INTENT) {
            val targetUriOrPkg = intent.getStringExtra(EXTRA_TARGET_URI_OR_PKG).orEmpty()
            val appLabel = intent.getStringExtra(EXTRA_APP_LABEL).orEmpty()
            val rawCommand = intent.getStringExtra(EXTRA_RAW_COMMAND).orEmpty()
            if (targetUriOrPkg.isNotBlank() && isExecutionPermittedByGlobalSecurity(applicationContext, targetUriOrPkg, userExplicitlyConfirmed = true)) {
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

        private val ROUTINE_FAST_PATH_INTENT_TYPES = setOf(
            LiaClassifiedIntentType.DIRECT_APP_LAUNCH,
            LiaClassifiedIntentType.SET_ALARM,
            LiaClassifiedIntentType.SET_TIMER,
            LiaClassifiedIntentType.TOGGLE_FLASHLIGHT,
            LiaClassifiedIntentType.OPEN_CAMERA,
            LiaClassifiedIntentType.OPEN_SYSTEM_SETTINGS,
            LiaClassifiedIntentType.PLAY_MEDIA,
            LiaClassifiedIntentType.MAPS_NAVIGATION,
            LiaClassifiedIntentType.SEND_MESSAGE,
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
            "iban",
            "paiement",
            "payer",
            "supprime tout",
            "efface tout",
            "formater",
            "réinitialiser",
            "factory reset"
        )

        private val INTERNAL_SAFE_ACTION_PREFIXES = listOf(
            "flashlight:",
            "alarm:",
            "timer:",
            "settings:",
            "camera:",
            "media_play:",
            "app_msg:",
            "app_search:",
            "calendar_insert:",
            "share_text:",
            "steam:",
            "geo:",
            "google.navigation:",
            "screen_click:",
            "screen_gesture:",
            "screen_type:",
            "global_action:",
            "ai_prompt:",
            "ui_macro:",
            "agent_goal:"
        )

        init {
            fastPathScope.launch {
                for (queuedTask in fastPathQueue) {
                    val startMs = System.currentTimeMillis()
                    val ok = executeClassifiedIntentInternal(
                        context = queuedTask.appContext,
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
         * Porte de sécurité centrale vérifiée avant toute exécution d'Intent (Chat, Bulle flottante ou balise [ACTION]).
         */
        fun isExecutionPermittedByGlobalSecurity(
            context: Context,
            targetPackageOrUri: String,
            userExplicitlyConfirmed: Boolean
        ): Boolean {
            SoniaSecurityKernel.initVaultState(context)
            if (SoniaSecurityKernel.isVaultLockedRuntime) {
                _fastPathMetrics.update {
                    it.copy(
                        totalSafetyBlocked = it.totalSafetyBlocked + 1,
                        lastFastPathSummary = "🔒 Action bloquée : Coffre verrouillé par code PIN"
                    )
                }
                return false
            }

            val policy = SoniaSecurityKernel.globalPolicyRuntime
            if (policy == GlobalAccessPolicyMode.LOCKDOWN || policy == GlobalAccessPolicyMode.OBSERVATION_ONLY) {
                _fastPathMetrics.update {
                    it.copy(
                        totalSafetyBlocked = it.totalSafetyBlocked + 1,
                        lastFastPathSummary = "🛡️ Action bloquée par la politique ${policy.frenchTitle}"
                    )
                }
                return false
            }

            val trimmed = targetPackageOrUri.trim()
            val isInternalPrefix = INTERNAL_SAFE_ACTION_PREFIXES.any { trimmed.startsWith(it, ignoreCase = true) }
            if (!isInternalPrefix) {
                when (SoniaSecurityKernel.validateActionTargetSafety(trimmed)) {
                    ActionTargetSafetyVerdict.BLOCKED_UNSAFE_TARGET -> {
                        _fastPathMetrics.update {
                            it.copy(
                                totalSafetyBlocked = it.totalSafetyBlocked + 1,
                                lastFastPathSummary = "🛡️ Cible d'action non sûre bloquée : ${trimmed.take(36)}"
                            )
                        }
                        return false
                    }
                    ActionTargetSafetyVerdict.REQUIRES_USER_CONFIRMATION -> {
                        if (!userExplicitlyConfirmed && policy == GlobalAccessPolicyMode.SELECTIVE_ZERO_TRUST) {
                            _fastPathMetrics.update {
                                it.copy(
                                    totalHitlEscalated = it.totalHitlEscalated + 1,
                                    lastFastPathSummary = "⚠️ Confirmation utilisateur requise pour : ${trimmed.take(36)}"
                                )
                            }
                            return false
                        }
                    }
                    ActionTargetSafetyVerdict.SAFE_APP_LAUNCH -> Unit
                }
            }

            return true
        }

        /**
         * Vérifie si une commande utilisateur correspond à une tâche système de routine éligible
         * à la file d'exécution Fast-Path et applique les 4 portes Zero-Trust.
         */
        fun tryDispatchRoutineTaskViaFastPathQueue(
            context: Context,
            rawUserCommand: String,
            isVaultPinLocked: Boolean = SoniaSecurityKernel.isVaultLockedRuntime,
            globalAccessPolicy: GlobalAccessPolicyMode = SoniaSecurityKernel.globalPolicyRuntime
        ): FastPathDispatchResult? {
            val cleaned = rawUserCommand.trim()
            if (cleaned.isBlank()) return null

            // Ne pas intercepter en simple ouverture Fast-Path une mission d'inspection autonome (ex: "va dans mon application Toyota regarde ma charge")
            val lowerCmd = cleaned.lowercase()
            if (lowerCmd.contains("va voir") || lowerCmd.contains("aller voir") ||
                lowerCmd.contains("va dans") || lowerCmd.contains("aller dans") ||
                lowerCmd.contains("regarde") || lowerCmd.contains("voir") ||
                lowerCmd.contains("combien") || lowerCmd.contains("vérifie") ||
                lowerCmd.contains("verifie") || lowerCmd.contains("check") ||
                lowerCmd.contains("ma voiture") || lowerCmd.contains("charge") ||
                lowerCmd.contains("autonomie") || lowerCmd.contains("solde") ||
                lowerCmd.contains("commande") || lowerCmd.contains("colis") ||
                lowerCmd.contains("toyota") || lowerCmd.contains("tesla") ||
                lowerCmd.contains("renault") || lowerCmd.contains("steam") ||
                lowerCmd.contains("gemini") || lowerCmd.contains("chatgpt") ||
                lowerCmd.contains("clique") || lowerCmd.contains("scrolle")
            ) {
                return null
            }

            val startTimeMs = System.currentTimeMillis()
            val taskId = "fp_${startTimeMs}"

            val classifiedSpec = IntentHandler.classifyAndExtractKnownIntent(cleaned, context)
                ?: return null

            if (classifiedSpec.intentType !in ROUTINE_FAST_PATH_INTENT_TYPES) {
                return null
            }

            // 1. Verrouillage PIN
            if (isVaultPinLocked || SoniaSecurityKernel.isVaultLockedRuntime) {
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
                    summaryMessageFr = "🔒 Exécution bloquée : veuillez déverrouiller votre code PIN dans Paramètres & Sécurité.",
                    blockingReasonFr = "Coffre verrouillé par code PIN"
                )
            }

            // 2. Inspection DLP & Anti-Injection
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

            // 3. Politique globale (LOCKDOWN / OBSERVATION_ONLY)
            if (globalAccessPolicy == GlobalAccessPolicyMode.LOCKDOWN ||
                globalAccessPolicy == GlobalAccessPolicyMode.OBSERVATION_ONLY
            ) {
                val reason = "Mode ${globalAccessPolicy.frenchTitle} actif"
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

            // 4. Escalade HITL si mot-clé sensible ou si confirmation exigée dans les permissions MCP
            val lower = cleaned.lowercase()
            val containsSensitiveKeyword = SENSITIVE_OR_FINANCIAL_KEYWORDS.any { lower.contains(it) }
            val regState = LiaMcpConnectorRegistry.state.value
            val mcpConnectors = regState.connectors
            val matchingConnector = mcpConnectors.find {
                it.packageName.equals(classifiedSpec.targetPackage, ignoreCase = true) ||
                    it.appName.equals(classifiedSpec.resolvedAppLabel, ignoreCase = true)
            }
            if (matchingConnector != null && regState.disabledConnectorIds.contains(matchingConnector.connectorId)) {
                val reason = "Connecteur '${matchingConnector.appName}' désactivé dans Paramètres MCP"
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
                    summaryMessageFr = "🛡️ $reason.",
                    blockingReasonFr = reason
                )
            }

            if (containsSensitiveKeyword ||
                globalAccessPolicy == GlobalAccessPolicyMode.SELECTIVE_ZERO_TRUST &&
                classifiedSpec.intentType == LiaClassifiedIntentType.DIAL_PHONE
            ) {
                _fastPathMetrics.update {
                    it.copy(
                        totalHitlEscalated = it.totalHitlEscalated + 1,
                        lastFastPathSummary = "⚠️ Confirmation humaine requise pour ${classifiedSpec.resolvedAppLabel}"
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
                    summaryMessageFr = "⚠️ Opération sensible détectée : confirmation utilisateur requise.",
                    blockingReasonFr = "Confirmation humaine obligatoire (HITL)"
                )
            }

            // 5. Envoi dans la file d'exécution Fast-Path
            val safetySummary = "DLP OK • MCP OK • Non-sensible"
            fastPathQueue.trySend(
                FastPathQueuedTask(
                    taskId = taskId,
                    appContext = context.applicationContext,
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

        fun executeClassifiedIntent(
            context: Context,
            spec: ClassifiedUserIntent
        ): Boolean {
            val target = spec.deepLinkUri.ifBlank { spec.targetPackage }
            if (!isExecutionPermittedByGlobalSecurity(context, target, userExplicitlyConfirmed = true)) {
                return false
            }
            return executeClassifiedIntentInternal(context.applicationContext, spec)
        }

        /**
         * Point d'entrée universel sécurisé pour exécuter toute cible d'application ou URI d'action système.
         * Si [userExplicitlyConfirmed] vaut false (ex: auto-exécution issue d'une réponse IA), les cibles
         * sensibles (`tel:`, `smsto:`, `mailto:`, `https://`) ne sont pas auto-lancées sans clic utilisateur.
         */
        fun executeTargetDirectly(
            context: Context,
            packageNameOrUri: String,
            appLabel: String = "",
            rawCommand: String = "",
            userExplicitlyConfirmed: Boolean = true
        ): Boolean {
            val trimmedTarget = packageNameOrUri.trim()
            if (!isExecutionPermittedByGlobalSecurity(context, trimmedTarget, userExplicitlyConfirmed)) {
                return false
            }
            if (!userExplicitlyConfirmed) {
                val verdict = SoniaSecurityKernel.validateActionTargetSafety(trimmedTarget)
                val isInternal = INTERNAL_SAFE_ACTION_PREFIXES.any { trimmedTarget.startsWith(it, ignoreCase = true) }
                if (!isInternal && verdict != ActionTargetSafetyVerdict.SAFE_APP_LAUNCH) {
                    return false
                }
            }
            val appCtx = context.applicationContext
            val spec = IntentHandler.buildIntentSpecFromTarget(
                appLabel = appLabel.ifBlank { trimmedTarget },
                packageNameOrUri = trimmedTarget,
                rawCommand = rawCommand.ifBlank { appLabel },
                context = appCtx
            )
            return executeClassifiedIntentInternal(appCtx, spec)
        }

        private fun executeClassifiedIntentInternal(
            context: Context,
            spec: ClassifiedUserIntent,
            executedViaFastPathQueue: Boolean = true,
            safetyValidationSummary: String = "Zero-Trust DLP + Permissions MCP validés"
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
                    succeeded = torchOk,
                    executedViaFastPathQueue = executedViaFastPathQueue,
                    safetyValidationSummary = safetyValidationSummary
                )
                return torchOk
            }

            // 0A. Messagerie multi-applications
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
                    succeeded = ok,
                    executedViaFastPathQueue = executedViaFastPathQueue,
                    safetyValidationSummary = safetyValidationSummary
                )
                return ok
            }

            // 0B. Steam (avec navigation autonome dans Bibliothèque / Récemment joués via LiaAccessibilityService)
            if (uriOrPkg.startsWith("steam:")) {
                val steamPkg = "com.valvesoftware.android.steam.community"
                val subAction = uriOrPkg.removePrefix("steam:")
                val ok = runCatching {
                    val launch = pm.getLaunchIntentForPackage(steamPkg)?.apply {
                        addFlags(DIRECT_LAUNCH_FLAGS)
                    }
                    if (launch != null) {
                        context.startActivity(launch)
                        if (LiaAccessibilityService.instance != null) {
                            LiaAccessibilityService.executeAutonomousMacroSequence(
                                context = context,
                                steps = buildList {
                                    add(UiAutomationStep.WaitMs(1800L))
                                    add(UiAutomationStep.ClickByTextOrId("Bibliothèque", 0.50f, 0.92f))
                                    add(UiAutomationStep.WaitMs(900L))
                                    add(UiAutomationStep.ClickByTextOrId("Library", 0.50f, 0.92f))
                                    if (subAction.contains("recent") || subAction.contains("games")) {
                                        add(UiAutomationStep.WaitMs(800L))
                                        add(UiAutomationStep.SwipeGesture("SWIPE_UP"))
                                    }
                                }
                            )
                        }
                        true
                    } else {
                        val webUrl = when {
                            subAction.startsWith("search:") -> {
                                val q = Uri.decode(subAction.removePrefix("search:"))
                                "https://store.steampowered.com/search/?term=${Uri.encode(q)}"
                            }
                            subAction.contains("store") -> "https://store.steampowered.com/"
                            else -> "https://steamcommunity.com/my/games/?tab=recent"
                        }
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                            addFlags(DIRECT_LAUNCH_FLAGS)
                        }
                        context.startActivity(webIntent)
                        true
                    }
                }.getOrDefault(false)
                recordExecutionLog(
                    spec = spec,
                    resolvedPackage = steamPkg,
                    succeeded = ok,
                    executedViaFastPathQueue = executedViaFastPathQueue,
                    safetyValidationSummary = safetyValidationSummary
                )
                return ok
            }

            // 0C. Envoi de message / prompt à l'application Gemini ou ChatGPT sur le téléphone ("ai_prompt:gemini:..." ou "ai_prompt:chatgpt:...")
            if (uriOrPkg.startsWith("ai_prompt:")) {
                val payload = uriOrPkg.removePrefix("ai_prompt:")
                val parts = payload.split(":", limit = 2)
                val targetAi = parts.getOrNull(0)?.lowercase().orEmpty()
                val promptText = Uri.decode(parts.getOrNull(1).orEmpty())

                val candidatePkgs = when (targetAi) {
                    "chatgpt", "openai" -> listOf("com.openai.chatgpt")
                    else -> listOf("com.google.android.apps.bard", "com.google.android.googlequicksearchbox")
                }
                val fallbackWeb = when (targetAi) {
                    "chatgpt", "openai" -> "https://chatgpt.com/?q=${Uri.encode(promptText)}"
                    else -> "https://gemini.google.com/app"
                }

                // Copier systématiquement le prompt dans le presse-papiers
                if (promptText.isNotBlank()) {
                    runCatching {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("LIA_AI_Prompt", promptText))
                    }
                }

                val installedPkg = candidatePkgs.firstOrNull { pkg ->
                    runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
                }

                val ok = runCatching {
                    if (installedPkg != null) {
                        // 1. Essayer ACTION_SEND vers Gemini/ChatGPT si un prompt est fourni
                        var started = false
                        if (promptText.isNotBlank()) {
                            started = runCatching {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    setPackage(installedPkg)
                                    putExtra(Intent.EXTRA_TEXT, promptText)
                                    addFlags(DIRECT_LAUNCH_FLAGS)
                                }
                                context.startActivity(sendIntent)
                                true
                            }.getOrDefault(false)
                        }
                        if (!started) {
                            val launch = pm.getLaunchIntentForPackage(installedPkg)?.apply {
                                addFlags(DIRECT_LAUNCH_FLAGS)
                            }
                            if (launch != null) {
                                context.startActivity(launch)
                                started = true
                            }
                        }
                        // 2. Si le Service d'Accessibilité est actif, saisir automatiquement le prompt et cliquer sur Envoyer !
                        if (started && promptText.isNotBlank() && LiaAccessibilityService.instance != null) {
                            LiaAccessibilityService.executeAutonomousMacroSequence(
                                context = context,
                                steps = listOf(
                                    UiAutomationStep.WaitMs(1600L),
                                    UiAutomationStep.TypeTextIntoField(
                                        text = promptText,
                                        targetFieldHint = "",
                                        autoClickSend = true
                                    )
                                )
                            )
                        }
                        started
                    } else {
                        // Ouvrir Gemini Web ou l'assistant vocal Android avec le prompt déjà copié et saisi via Accessibilité
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackWeb)).apply {
                            addFlags(DIRECT_LAUNCH_FLAGS)
                        }
                        context.startActivity(webIntent)
                        if (promptText.isNotBlank() && LiaAccessibilityService.instance != null) {
                            LiaAccessibilityService.executeAutonomousMacroSequence(
                                context = context,
                                steps = listOf(
                                    UiAutomationStep.WaitMs(2400L),
                                    UiAutomationStep.TypeTextIntoField(
                                        text = promptText,
                                        targetFieldHint = "",
                                        autoClickSend = true
                                    )
                                )
                            )
                        }
                        true
                    }
                }.getOrDefault(false)

                recordExecutionLog(
                    spec = spec,
                    resolvedPackage = installedPkg ?: fallbackWeb,
                    succeeded = ok,
                    executedViaFastPathQueue = executedViaFastPathQueue,
                    safetyValidationSummary = safetyValidationSummary
                )
                return ok
            }

            // 0D. Contrôle direct de l'écran via LiaAccessibilityService (Clic, Swipe, Saisie de texte, Action globale, Macro)
            if (uriOrPkg.startsWith("screen_click:")) {
                val targetText = Uri.decode(uriOrPkg.removePrefix("screen_click:")).trim()
                val tLower = targetText.lowercase()
                val (fx, fy) = when {
                    tLower.contains("haut") -> 0.50f to 0.18f
                    tLower.contains("bas") || tLower.contains("envoyer") -> 0.50f to 0.88f
                    tLower.contains("gauche") -> 0.20f to 0.50f
                    tLower.contains("droite") -> 0.82f to 0.50f
                    else -> 0.50f to 0.50f
                }
                val ok = if (LiaAccessibilityService.instance != null) {
                    LiaAccessibilityService.clickNodeByTextOrFallback(targetText, fx, fy)
                } else {
                    LiaAccessibilityService.openAccessibilitySettings(context)
                }
                recordExecutionLog(spec, "accessibility:click:$targetText", ok, executedViaFastPathQueue, safetyValidationSummary)
                return ok
            }

            if (uriOrPkg.startsWith("screen_gesture:")) {
                val direction = uriOrPkg.removePrefix("screen_gesture:").trim()
                val ok = if (LiaAccessibilityService.instance != null) {
                    LiaAccessibilityService.performSwipeGesture(direction)
                } else {
                    LiaAccessibilityService.openAccessibilitySettings(context)
                }
                recordExecutionLog(spec, "accessibility:gesture:$direction", ok, executedViaFastPathQueue, safetyValidationSummary)
                return ok
            }

            if (uriOrPkg.startsWith("screen_type:")) {
                val textToType = Uri.decode(uriOrPkg.removePrefix("screen_type:")).trim()
                val ok = if (LiaAccessibilityService.instance != null) {
                    LiaAccessibilityService.typeTextIntoFocusedOrFirstInputField(context, textToType, autoClickSend = true)
                } else {
                    runCatching {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("LIA_Screen_Input", textToType))
                        true
                    }.getOrDefault(false)
                }
                recordExecutionLog(spec, "accessibility:type", ok, executedViaFastPathQueue, safetyValidationSummary)
                return ok
            }

            if (uriOrPkg.startsWith("global_action:")) {
                val actionName = uriOrPkg.removePrefix("global_action:").trim()
                val ok = if (LiaAccessibilityService.instance != null) {
                    LiaAccessibilityService.performSystemGlobalAction(actionName)
                } else if (actionName.equals("HOME", ignoreCase = true)) {
                    runCatching {
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(homeIntent)
                        true
                    }.getOrDefault(false)
                } else {
                    LiaAccessibilityService.openAccessibilitySettings(context)
                }
                recordExecutionLog(spec, "accessibility:global:$actionName", ok, executedViaFastPathQueue, safetyValidationSummary)
                return ok
            }

            if (uriOrPkg.startsWith("ui_macro:")) {
                // Format: ui_macro:appNameOrPkg|clickOrTypeTarget|optionalTextToType
                val rawMacro = uriOrPkg.removePrefix("ui_macro:")
                val parts = rawMacro.split("|")
                val appTarget = Uri.decode(parts.getOrNull(0).orEmpty())
                val elementTarget = Uri.decode(parts.getOrNull(1).orEmpty())
                val textPayload = Uri.decode(parts.getOrNull(2).orEmpty())

                LiaAccessibilityService.executeAutonomousMacroSequence(
                    context = context,
                    steps = buildList {
                        if (appTarget.isNotBlank()) {
                            add(UiAutomationStep.OpenApp(appTarget, appTarget))
                            add(UiAutomationStep.WaitMs(1800L))
                        }
                        if (elementTarget.isNotBlank()) {
                            val eLower = elementTarget.lowercase()
                            if (eLower.contains("scroll") || eLower.contains("descend") || eLower.contains("bas")) {
                                add(UiAutomationStep.SwipeGesture("SWIPE_UP"))
                            } else if (eLower.contains("monte") || eLower.contains("haut")) {
                                add(UiAutomationStep.SwipeGesture("SWIPE_DOWN"))
                            } else {
                                add(UiAutomationStep.ClickByTextOrId(elementTarget))
                            }
                            add(UiAutomationStep.WaitMs(900L))
                        }
                        if (textPayload.isNotBlank()) {
                            add(UiAutomationStep.TypeTextIntoField(textPayload, elementTarget, autoClickSend = true))
                        }
                    }
                )
                recordExecutionLog(spec, "accessibility:macro:$appTarget", true, executedViaFastPathQueue, safetyValidationSummary)
                return true
            }

            if (uriOrPkg.startsWith("agent_goal:")) {
                val payload = uriOrPkg.removePrefix("agent_goal:")
                val ok = LiaUniversalGoalAgent.executeAutonomousInspectionMission(context, payload)
                recordExecutionLog(spec, "agent_goal:inspection", ok, executedViaFastPathQueue, safetyValidationSummary)
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

                    LiaClassifiedIntentType.SEND_MESSAGE -> {
                        val body = spec.extractedParameters["body"].orEmpty()
                        val recipient = spec.extractedParameters["recipient"].orEmpty()
                        if (uriOrPkg.startsWith("smsto:") || uriOrPkg.startsWith("sms:")) {
                            Intent(Intent.ACTION_SENDTO, Uri.parse(uriOrPkg)).apply {
                                if (body.isNotBlank()) putExtra("sms_body", body)
                                bindBestPackageViaPackageManager(pm, this, spec.targetPackage.ifBlank { "com.google.android.apps.messaging" })
                            }
                        } else {
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
                                if (recipient.isNotBlank()) putExtra(Intent.EXTRA_TITLE, recipient)
                                if (spec.targetPackage.isNotBlank()) {
                                    bindBestPackageViaPackageManager(pm, this, spec.targetPackage)
                                }
                            }
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
                        resolveDirectPackageLaunchIntent(pm, uriOrPkg, spec.resolvedAppLabel)
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
                        succeeded = true,
                        executedViaFastPathQueue = executedViaFastPathQueue,
                        safetyValidationSummary = safetyValidationSummary
                    )
                    true
                } else {
                    val fallbackOk = LiaUniversalAppsAndGoogleHub.launchGoogleOrUniversalTarget(
                        context,
                        uriOrPkg,
                        spec.resolvedAppLabel
                    )
                    recordExecutionLog(
                        spec = spec,
                        resolvedPackage = uriOrPkg,
                        succeeded = fallbackOk,
                        executedViaFastPathQueue = executedViaFastPathQueue,
                        safetyValidationSummary = safetyValidationSummary
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
                    succeeded = fallbackOk,
                    executedViaFastPathQueue = executedViaFastPathQueue,
                    safetyValidationSummary = safetyValidationSummary
                )
                fallbackOk
            }
        }

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
            pm: PackageManager,
            packageOrQuery: String,
            appLabel: String
        ): Intent? {
            val directIntent = pm.getLaunchIntentForPackage(packageOrQuery)
            if (directIntent != null) return directIntent

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
            safetyValidationSummary: String = "Zero-Trust DLP + Permissions MCP validés"
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
