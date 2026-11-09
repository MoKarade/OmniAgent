package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiDualEngineClient
import com.example.data.LiaAssistantBrain
import com.example.data.LiaMemoryAndHistoryStore
import com.example.data.OmniFirestoreRepository
import com.example.model.AgentOperationalStatus
import com.example.model.AtomicAction
import com.example.model.CognitivePipelineStage
import com.example.model.CognitiveStepResult
import com.example.model.DynamicPromptProfile
import com.example.model.FastPathDispatchResult
import com.example.model.FriendlyMissionSuggestion
import com.example.model.GlobalAccessPolicyMode
import com.example.model.LiveCognitiveTelemetry
import com.example.model.McpCategory
import com.example.model.McpRecord
import com.example.model.SecurityPosture
import com.example.model.SystemInstructionsProvider
import com.example.model.currentTimeFormatted
import com.example.security.SoniaAuditBlock
import com.example.security.SoniaSecurityKernel
import com.example.service.AppActionExecutor
import com.example.service.LiaMcpConnectorRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PendingHitlRequest(
    val stepNumber: Int,
    val question: String,
    val stagedObservation: String,
    val stagedThought: String,
    val stagedSubgoal: String,
    val stagedFollowUpAction: AtomicAction
)

data class OmniAgentUiState(
    val userMission: String = "Bonjour LIA, quels sont mes prochains rendez-vous ?",
    val submittedMission: String = "Bonjour LIA, quels sont mes prochains rendez-vous ?",
    val status: AgentOperationalStatus = AgentOperationalStatus.IDLE,
    val globalAccessPolicy: GlobalAccessPolicyMode = GlobalAccessPolicyMode.FULL_AUTONOMOUS_ACCESS,
    val allowedMcpCategories: Set<McpCategory> = McpCategory.entries.toSet(),
    val mcpRecords: List<McpRecord> = SystemInstructionsProvider.defaultMcpVault(),
    val retrievedMcpContext: List<McpRecord> = emptyList(),
    val cognitiveHistory: List<CognitiveStepResult> = emptyList(),
    val liveTelemetry: LiveCognitiveTelemetry = LiveCognitiveTelemetry(),
    val isThinkingStep: Boolean = false,
    val pendingHitlRequest: PendingHitlRequest? = null,
    val missionCompletedSummary: String? = null,
    val errorMessage: String? = null,
    val authenticatedUserId: String? = null,
    val authenticatedUserEmail: String? = null,
    val isCloudSynced: Boolean = false,
    // Floating Bubble State
    val isFloatingBubbleMode: Boolean = false,
    val isBubbleMinimizedToEdge: Boolean = false,
    val isBubbleDockedRight: Boolean = true,
    // Security & Audit Vault State
    val auditBlockchain: List<SoniaAuditBlock> = SoniaSecurityKernel.buildGenesisChain(),
    val dlpBlockedCount: Int = 0,
    val isVaultPinLocked: Boolean = false,
    val isUserPinConfigured: Boolean = false,
    val isHardwareKeystoreActive: Boolean = SoniaSecurityKernel.isHardwareKeystoreReady(),
    // Voice TTS State
    val isVoiceTtsEnabled: Boolean = false,
    val lastSpokenText: String = "Bonjour, je suis LIA, votre assistante personnelle Android.",
    // Dynamic Prompt & Fast-Path Telemetry
    val systemInstructionsText: String = SystemInstructionsProvider.MASTER_SYSTEM_PROMPT,
    val activePromptProfile: DynamicPromptProfile = SystemInstructionsProvider.buildDynamicPromptProfile(
        mission = "Bonjour LIA, quels sont mes prochains rendez-vous ?",
        allowedCategories = McpCategory.entries.toSet()
    ),
    val lastFastPathDispatch: FastPathDispatchResult? = null
)

class OmniAgentViewModel(
    private var firestoreRepository: OmniFirestoreRepository? = null,
    initialUserId: String? = null,
    initialUserEmail: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OmniAgentUiState(
            authenticatedUserId = initialUserId,
            authenticatedUserEmail = initialUserEmail,
            isCloudSynced = initialUserId != null && firestoreRepository != null
        )
    )
    val uiState: StateFlow<OmniAgentUiState> = _uiState.asStateFlow()

    private var cloudSyncJob: Job? = null

    init {
        refreshMcpRetrieval()
        GeminiDualEngineClient.applicationContextRef?.let { refreshSecurityState(it) }
        if (initialUserId != null && firestoreRepository != null) {
            bindAuthenticatedUser(initialUserId, initialUserEmail, firestoreRepository)
        }
    }

    /**
     * Synchronizes the UI state with the hardware keystore status and user PIN lock state.
     */
    fun refreshSecurityState(context: Context) {
        val pinConfigured = SoniaSecurityKernel.isUserPinConfigured(context)
        val hardwareReady = SoniaSecurityKernel.isHardwareKeystoreReady()
        _uiState.update { state ->
            state.copy(
                isUserPinConfigured = pinConfigured,
                isVaultPinLocked = SoniaSecurityKernel.isVaultLockedRuntime,
                globalAccessPolicy = SoniaSecurityKernel.globalPolicyRuntime,
                isHardwareKeystoreActive = hardwareReady
            )
        }
    }

    /**
     * Binds or unbinds an authenticated Firebase user and starts real-time Firestore listeners.
     */
    fun bindAuthenticatedUser(
        userId: String?,
        userEmail: String?,
        repository: OmniFirestoreRepository?
    ) {
        cloudSyncJob?.cancel()
        firestoreRepository = repository

        if (userId == null || repository == null) {
            _uiState.update {
                it.copy(
                    authenticatedUserId = null,
                    authenticatedUserEmail = null,
                    isCloudSynced = false
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                authenticatedUserId = userId,
                authenticatedUserEmail = userEmail,
                isCloudSynced = true
            )
        }

        cloudSyncJob = viewModelScope.launch {
            runCatching {
                repository.upsertUserSessionProfile(
                    userId = userId,
                    email = userEmail,
                    displayName = userEmail?.substringBefore('@')
                )
                repository.seedDefaultMcpVaultIfEmpty(
                    userId = userId,
                    defaults = SystemInstructionsProvider.defaultMcpVault()
                )
            }

            launch {
                repository.observeMcpRecords(userId).collect { remoteRecords ->
                    if (remoteRecords.isNotEmpty()) {
                        _uiState.update { it.copy(mcpRecords = remoteRecords) }
                        refreshMcpRetrieval()
                    }
                }
            }

            launch {
                repository.observeCognitiveLogs(userId).collect { remoteSteps ->
                    if (remoteSteps.isNotEmpty() && _uiState.value.cognitiveHistory.isEmpty()) {
                        _uiState.update { it.copy(cognitiveHistory = remoteSteps) }
                    }
                }
            }
        }
    }

    fun updateMissionText(newMission: String) {
        _uiState.update { current ->
            val dynamicProfile = SystemInstructionsProvider.buildDynamicPromptProfile(
                mission = newMission,
                allowedCategories = current.allowedMcpCategories
            )
            current.copy(
                userMission = newMission,
                errorMessage = null,
                activePromptProfile = dynamicProfile,
                systemInstructionsText = dynamicProfile.compiledSystemPrompt
            )
        }
        refreshMcpRetrieval()
    }

    fun applyFriendlySuggestion(suggestion: FriendlyMissionSuggestion) {
        _uiState.update { current ->
            val dynamicProfile = SystemInstructionsProvider.buildDynamicPromptProfile(
                mission = suggestion.missionPrompt,
                allowedCategories = current.allowedMcpCategories
            )
            current.copy(
                userMission = suggestion.missionPrompt,
                submittedMission = suggestion.missionPrompt,
                missionCompletedSummary = null,
                errorMessage = null,
                activePromptProfile = dynamicProfile,
                systemInstructionsText = dynamicProfile.compiledSystemPrompt
            )
        }
        refreshMcpRetrieval()
    }

    // =========================================================================
    // FLOATING BUBBLE CONTROLS
    // =========================================================================

    fun toggleFloatingBubbleMode() {
        _uiState.update {
            val nextMode = !it.isFloatingBubbleMode
            it.copy(
                isFloatingBubbleMode = nextMode,
                isBubbleMinimizedToEdge = false
            )
        }
    }

    fun setBubbleMinimizedToEdge(minimized: Boolean) {
        _uiState.update { it.copy(isBubbleMinimizedToEdge = minimized) }
    }

    fun toggleBubbleDockSide() {
        _uiState.update { it.copy(isBubbleDockedRight = !it.isBubbleDockedRight) }
    }

    // =========================================================================
    // ZERO-TRUST SECURITY KERNEL, USER PIN & GLOBAL POLICY
    // =========================================================================

    fun setGlobalAccessPolicy(mode: GlobalAccessPolicyMode) {
        SoniaSecurityKernel.updateGlobalPolicy(mode)
        _uiState.update { state ->
            val prevHash = state.auditBlockchain.firstOrNull()?.currHash ?: "0000000000000000"
            val auditBlock = SoniaSecurityKernel.nextMonotonicAuditBlock(
                actor = "Utilisateur",
                operation = "GLOBAL_POLICY_CHANGE",
                detail = "Politique globale basculée vers ${mode.title} (${mode.name})",
                prevHash = prevHash,
                status = if (mode == GlobalAccessPolicyMode.LOCKDOWN) SecurityPosture.BLOCKED else SecurityPosture.NOMINAL
            )
            state.copy(
                globalAccessPolicy = mode,
                auditBlockchain = (listOf(auditBlock) + state.auditBlockchain).take(40)
            )
        }
    }

    /**
     * Creates or updates the user's personal PIN with PBKDF2-HMAC-SHA256 (12,000 iterations + random salt).
     */
    fun configureOrUpdateUserPin(context: Context, newPin: String): Boolean {
        val success = SoniaSecurityKernel.setOrUpdateUserPin(context, newPin)
        if (success) {
            _uiState.update { state ->
                val prevHash = state.auditBlockchain.firstOrNull()?.currHash ?: "0000000000000000"
                val block = SoniaSecurityKernel.nextMonotonicAuditBlock(
                    actor = "Utilisateur",
                    operation = "VAULT_PIN_CONFIGURED",
                    detail = "Code PIN personnel configuré avec hachage PBKDF2-HMAC-SHA256 salé",
                    prevHash = prevHash,
                    status = SecurityPosture.NOMINAL
                )
                state.copy(
                    isUserPinConfigured = true,
                    isVaultPinLocked = false,
                    errorMessage = null,
                    auditBlockchain = (listOf(block) + state.auditBlockchain).take(40)
                )
            }
        }
        return success
    }

    /**
     * Verifies the user's personal PIN against the PBKDF2 hash stored on device.
     */
    fun verifySecurityPin(context: Context, pin: String): Boolean {
        val unlocked = SoniaSecurityKernel.verifyUserPin(context, pin)
        _uiState.update { state ->
            val prevHash = state.auditBlockchain.firstOrNull()?.currHash ?: "0000000000000000"
            val block = SoniaSecurityKernel.nextMonotonicAuditBlock(
                actor = "Utilisateur",
                operation = if (unlocked) "VAULT_UNLOCK" else "VAULT_AUTH_FAIL",
                detail = if (unlocked) "Déverrouillage PIN PBKDF2 réussi" else "Tentative PIN invalide bloquée",
                prevHash = prevHash,
                status = if (unlocked) SecurityPosture.NOMINAL else SecurityPosture.ELEVATED_WARNING
            )
            state.copy(
                isVaultPinLocked = !unlocked,
                auditBlockchain = (listOf(block) + state.auditBlockchain).take(40)
            )
        }
        return unlocked
    }

    fun setVaultPinLocked(context: Context, locked: Boolean) {
        if (locked) {
            if (!SoniaSecurityKernel.isUserPinConfigured(context)) {
                _uiState.update {
                    it.copy(errorMessage = "Configurez d'abord votre code PIN personnel dans l'onglet Sécurité & PIN.")
                }
                return
            }
            SoniaSecurityKernel.lockVault()
        } else {
            SoniaSecurityKernel.isVaultLockedRuntime = false
        }
        _uiState.update {
            it.copy(
                isVaultPinLocked = SoniaSecurityKernel.isVaultLockedRuntime,
                isUserPinConfigured = SoniaSecurityKernel.isUserPinConfigured(context)
            )
        }
    }

    fun clearUserSecurityPin(context: Context) {
        SoniaSecurityKernel.clearUserPin(context)
        _uiState.update { state ->
            val prevHash = state.auditBlockchain.firstOrNull()?.currHash ?: "0000000000000000"
            val block = SoniaSecurityKernel.nextMonotonicAuditBlock(
                actor = "Utilisateur",
                operation = "VAULT_PIN_REMOVED",
                detail = "Code PIN personnel supprimé par l'utilisateur",
                prevHash = prevHash,
                status = SecurityPosture.NOMINAL
            )
            state.copy(
                isUserPinConfigured = false,
                isVaultPinLocked = false,
                auditBlockchain = (listOf(block) + state.auditBlockchain).take(40)
            )
        }
    }

    fun toggleVoiceTts() {
        _uiState.update { it.copy(isVoiceTtsEnabled = !it.isVoiceTtsEnabled) }
    }

    fun submitVoiceCommand(spokenText: String) {
        val cleaned = spokenText.trim()
        if (cleaned.isBlank()) return
        updateMissionText(cleaned)
        executeSingleCognitiveStep()
    }

    fun reportVoiceInputError(message: String) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                liveTelemetry = LiveCognitiveTelemetry(
                    stage = CognitivePipelineStage.IDLE,
                    liveObservation = "Microphone vocal en attente",
                    liveThought = message,
                    liveSubgoal = "Appuyez sur 🎤 ou écrivez votre demande."
                )
            )
        }
    }

    fun toggleMcpCategoryAccess(category: McpCategory) {
        _uiState.update { state ->
            val updated = state.allowedMcpCategories.toMutableSet()
            if (updated.contains(category)) {
                updated.remove(category)
            } else {
                updated.add(category)
            }
            val dynamicProfile = SystemInstructionsProvider.buildDynamicPromptProfile(
                mission = state.userMission,
                allowedCategories = updated
            )
            state.copy(
                allowedMcpCategories = updated,
                activePromptProfile = dynamicProfile,
                systemInstructionsText = dynamicProfile.compiledSystemPrompt
            )
        }
        refreshMcpRetrieval()
    }

    private fun refreshMcpRetrieval() {
        _uiState.update { state ->
            val queryTerms = state.userMission
                .lowercase()
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length > 2 }

            val matching = state.mcpRecords.filter { record ->
                state.allowedMcpCategories.contains(record.category) &&
                    (queryTerms.isEmpty() || queryTerms.any { term ->
                        record.title.lowercase().contains(term) ||
                            record.snippet.lowercase().contains(term) ||
                            record.category.displayName.lowercase().contains(term)
                    })
            }.ifEmpty {
                state.mcpRecords.filter { state.allowedMcpCategories.contains(it.category) }.take(2)
            }

            state.copy(retrievedMcpContext = matching)
        }
    }

    // =========================================================================
    // COGNITIVE EXECUTION STEP (DLP -> FAST-PATH -> LIA BRAIN)
    // =========================================================================

    fun executeSingleCognitiveStep() {
        val stateSnapshot = _uiState.value
        if (stateSnapshot.isThinkingStep || stateSnapshot.pendingHitlRequest != null) return

        // 0. Check Zero-Trust PIN Lock & Global Lockdown Policy
        if (SoniaSecurityKernel.isVaultLockedRuntime || stateSnapshot.isVaultPinLocked) {
            _uiState.update {
                it.copy(
                    status = AgentOperationalStatus.SECURITY_BLOCKED,
                    errorMessage = "🔒 LIA est verrouillée par code PIN. Déverrouillez l'accès dans Paramètres > Sécurité & PIN."
                )
            }
            return
        }

        if (stateSnapshot.globalAccessPolicy == GlobalAccessPolicyMode.LOCKDOWN ||
            SoniaSecurityKernel.globalPolicyRuntime == GlobalAccessPolicyMode.LOCKDOWN
        ) {
            _uiState.update {
                it.copy(
                    status = AgentOperationalStatus.SECURITY_BLOCKED,
                    errorMessage = "🛑 Mode Verrouillage Total actif. Autorisez l'accès dans Paramètres > Sécurité & PIN."
                )
            }
            return
        }

        val mission = stateSnapshot.userMission.trim()
        if (mission.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Veuillez saisir ou dicter une demande pour LIA.") }
            return
        }

        // 1. DLP & Anti-Prompt-Injection Inspection
        val dlpReport = SoniaSecurityKernel.inspectAndSanitize(mission)
        if (!dlpReport.isSafe) {
            val prevHash = stateSnapshot.auditBlockchain.firstOrNull()?.currHash ?: "0000000000000000"
            val block = SoniaSecurityKernel.nextMonotonicAuditBlock(
                actor = "Pare-feu DLP",
                operation = "DLP_BLOCK_INJECTION",
                detail = "Menace bloquée : ${dlpReport.detectedThreats.firstOrNull()}",
                prevHash = prevHash,
                status = SecurityPosture.BLOCKED
            )
            _uiState.update {
                it.copy(
                    status = AgentOperationalStatus.SECURITY_BLOCKED,
                    dlpBlockedCount = it.dlpBlockedCount + 1,
                    auditBlockchain = (listOf(block) + it.auditBlockchain).take(40),
                    errorMessage = "🛡️ Pare-feu LIA : Requête bloquée (${dlpReport.detectedThreats.firstOrNull()})."
                )
            }
            return
        }

        val dynamicProfile = SystemInstructionsProvider.buildDynamicPromptProfile(
            mission = dlpReport.sanitizedText,
            allowedCategories = stateSnapshot.allowedMcpCategories
        )

        // Clear the user input field once submitted
        _uiState.update {
            it.copy(
                userMission = "",
                submittedMission = dlpReport.sanitizedText,
                activePromptProfile = dynamicProfile,
                systemInstructionsText = dynamicProfile.compiledSystemPrompt,
                errorMessage = null
            )
        }

        // 2. Try Fast-Path Routine Queue if enabled in AI Preferences
        val appCtx = GeminiDualEngineClient.applicationContextRef
        val fastPathAllowed = LiaMcpConnectorRegistry.currentAiPreferences.fastPathEnabled &&
            stateSnapshot.globalAccessPolicy == GlobalAccessPolicyMode.FULL_AUTONOMOUS_ACCESS

        if (fastPathAllowed && appCtx != null) {
            val fastPathResult = AppActionExecutor.tryDispatchRoutineTaskViaFastPathQueue(
                rawMission = dlpReport.sanitizedText,
                context = appCtx,
                promptProfile = dynamicProfile
            )
            if (fastPathResult != null) {
                LiaMemoryAndHistoryStore.appendExchange(
                    userText = dlpReport.sanitizedText,
                    liaReply = fastPathResult.summaryMessageFr,
                    source = "app",
                    learnedNote = "⚡ Exécution directe Fast-Path (${fastPathResult.queueLatencyMs} ms)",
                    appLaunchAction = com.example.data.LiaAppLaunchAction(
                        packageName = fastPathResult.targetPackageOrUri,
                        appLabel = fastPathResult.resolvedActionLabel,
                        isInstantShortcut = true,
                        latencyMs = fastPathResult.queueLatencyMs
                    )
                )
                val nextIndex = _uiState.value.cognitiveHistory.size + 1
                val fastStep = CognitiveStepResult(
                    stepIndex = nextIndex,
                    timestampFormatted = currentTimeFormatted(),
                    screenTitle = fastPathResult.resolvedActionLabel,
                    modelUsed = "LIA Fast-Path Queue (${dynamicProfile.tier.badgeLabel})",
                    observation = "Commande directe reconnue (${fastPathResult.taskCategoryLabel})",
                    thought = "Exécution déterministe immédiate sans appel réseau LLM.",
                    subgoal = fastPathResult.resolvedActionLabel,
                    action = AtomicAction(
                        tool = "fast_path_launch",
                        packageName = fastPathResult.targetPackageOrUri,
                        summary = fastPathResult.summaryMessageFr
                    ),
                    mcpContextUsed = emptyList(),
                    securityStatus = SecurityPosture.NOMINAL,
                    executionOutcome = "✓ Exécuté en ${fastPathResult.queueLatencyMs} ms",
                    durationMs = fastPathResult.queueLatencyMs
                )
                val prevHash = _uiState.value.auditBlockchain.firstOrNull()?.currHash ?: "0000000000000000"
                val auditBlock = SoniaSecurityKernel.nextMonotonicAuditBlock(
                    actor = "Fast-Path",
                    operation = "FAST_PATH_DISPATCH",
                    detail = "${fastPathResult.resolvedActionLabel} (${fastPathResult.targetPackageOrUri})",
                    prevHash = prevHash,
                    status = SecurityPosture.NOMINAL
                )
                _uiState.update { state ->
                    state.copy(
                        status = AgentOperationalStatus.COMPLETED,
                        isThinkingStep = false,
                        lastFastPathDispatch = fastPathResult,
                        missionCompletedSummary = fastPathResult.summaryMessageFr,
                        lastSpokenText = fastPathResult.summaryMessageFr,
                        cognitiveHistory = state.cognitiveHistory + fastStep,
                        auditBlockchain = (listOf(auditBlock) + state.auditBlockchain).take(40)
                    )
                }
                return
            }
        }

        // 3. Full LIA Assistant Brain Execution (Local Intents / MCP / Hybrid RAG / Gemini 2026)
        viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    isThinkingStep = true,
                    status = AgentOperationalStatus.THINKING,
                    liveTelemetry = LiveCognitiveTelemetry(
                        stage = CognitivePipelineStage.THINKING,
                        activeModel = "Gemini 3.8 Flash / Gemini 4 Argon",
                        liveObservation = "Analyse de votre demande : « ${dlpReport.sanitizedText.take(60)} »",
                        liveThought = "Mobilisation de vos souvenirs RAG et des connecteurs MCP…",
                        liveSubgoal = dynamicProfile.taskCategoryLabel
                    )
                )
            }

            val outcome = LiaAssistantBrain.processUserMessage(
                rawUserText = dlpReport.sanitizedText,
                source = "app"
            )

            // SEC-01: Do NOT auto-launch sensitive URIs (tel:, smsto:, mailto:, https://) without explicit user tap
            var autoLaunched = false
            val launchAction = outcome.appLaunchAction
            if (launchAction != null &&
                appCtx != null &&
                _uiState.value.globalAccessPolicy == GlobalAccessPolicyMode.FULL_AUTONOMOUS_ACCESS
            ) {
                autoLaunched = AppActionExecutor.executeTargetDirectly(
                    context = appCtx,
                    targetPackageOrUri = launchAction.packageName,
                    label = launchAction.appLabel,
                    userExplicitlyConfirmed = false
                )
            }

            val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(12L)
            val nextIndex = _uiState.value.cognitiveHistory.size + 1
            val cleanSummary = outcome.replyText
                .replace("**", "")
                .replace("`", "")
                .trim()

            val stepResult = CognitiveStepResult(
                stepIndex = nextIndex,
                timestampFormatted = currentTimeFormatted(),
                screenTitle = launchAction?.appLabel ?: "Chat LIA",
                modelUsed = "LIA Brain (${dynamicProfile.tier.badgeLabel})",
                observation = dlpReport.sanitizedText,
                thought = outcome.learnedNote ?: "Réponse synthétisée avec contexte personnel et outils MCP.",
                subgoal = launchAction?.let { "Action : ${it.appLabel}" } ?: "Réponse directe à l'utilisateur",
                action = AtomicAction(
                    tool = if (launchAction != null) "open_app" else "chat_reply",
                    packageName = launchAction?.packageName,
                    summary = cleanSummary.take(240)
                ),
                mcpContextUsed = _uiState.value.retrievedMcpContext.map { it.title },
                securityStatus = SecurityPosture.NOMINAL,
                executionOutcome = when {
                    autoLaunched -> "✓ Application ouverte (${launchAction?.appLabel})"
                    launchAction != null -> "🔒 Prêt à ouvrir sur confirmation (${launchAction.appLabel})"
                    else -> "✓ Réponse générée en $elapsedMs ms"
                },
                durationMs = elapsedMs
            )

            val prevHash = _uiState.value.auditBlockchain.firstOrNull()?.currHash ?: "0000000000000000"
            val auditBlock = SoniaSecurityKernel.nextMonotonicAuditBlock(
                actor = "LIA Brain",
                operation = if (launchAction != null) "ACTION_${launchAction.packageName.take(24)}" else "CHAT_RESPONSE",
                detail = cleanSummary.take(90),
                prevHash = prevHash,
                status = SecurityPosture.NOMINAL
            )

            val spokenClean = cleanSummary
                .replace(Regex("[*`_#•✨🔍⚡💭☀️🚗🌙📱🛡️✓⚠️]"), "")
                .trim()

            _uiState.update { state ->
                state.copy(
                    isThinkingStep = false,
                    status = AgentOperationalStatus.COMPLETED,
                    cognitiveHistory = state.cognitiveHistory + stepResult,
                    missionCompletedSummary = cleanSummary,
                    lastSpokenText = spokenClean.ifBlank { state.lastSpokenText },
                    auditBlockchain = (listOf(auditBlock) + state.auditBlockchain).take(40),
                    liveTelemetry = LiveCognitiveTelemetry(
                        stage = CognitivePipelineStage.COMPLETED,
                        activeModel = stepResult.modelUsed,
                        liveObservation = stepResult.observation,
                        liveThought = stepResult.thought,
                        liveSubgoal = stepResult.executionOutcome,
                        elapsedMs = elapsedMs
                    )
                )
            }

            // Persist step to Firestore if cloud sync is active
            val uid = _uiState.value.authenticatedUserId
            val repo = firestoreRepository
            if (uid != null && repo != null) {
                runCatching { repo.appendCognitiveStep(uid, stepResult) }
            }
        }
    }

    fun resolveHitlConfirmation(approved: Boolean) {
        val pending = _uiState.value.pendingHitlRequest ?: return
        val prevHash = _uiState.value.auditBlockchain.firstOrNull()?.currHash ?: "0000000000000000"
        val auditBlock = SoniaSecurityKernel.nextMonotonicAuditBlock(
            actor = "Utilisateur HITL",
            operation = if (approved) "HITL_APPROVED" else "HITL_REJECTED",
            detail = pending.question.take(80),
            prevHash = prevHash,
            status = if (approved) SecurityPosture.NOMINAL else SecurityPosture.BLOCKED
        )

        if (approved) {
            val pkg = pending.stagedFollowUpAction.packageName
            val appCtx = GeminiDualEngineClient.applicationContextRef
            if (!pkg.isNullOrBlank() && appCtx != null) {
                AppActionExecutor.executeTargetDirectly(
                    context = appCtx,
                    targetPackageOrUri = pkg,
                    label = pending.stagedSubgoal,
                    userExplicitlyConfirmed = true
                )
            }
        }

        _uiState.update { state ->
            state.copy(
                pendingHitlRequest = null,
                status = if (approved) AgentOperationalStatus.COMPLETED else AgentOperationalStatus.SECURITY_BLOCKED,
                auditBlockchain = (listOf(auditBlock) + state.auditBlockchain).take(40)
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun resetSession() {
        _uiState.update { state ->
            state.copy(
                status = AgentOperationalStatus.IDLE,
                cognitiveHistory = emptyList(),
                pendingHitlRequest = null,
                missionCompletedSummary = null,
                errorMessage = null,
                lastFastPathDispatch = null,
                liveTelemetry = LiveCognitiveTelemetry()
            )
        }
        val uid = _uiState.value.authenticatedUserId
        val repo = firestoreRepository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                runCatching { repo.clearCognitiveLogs(uid) }
            }
        }
    }
}
