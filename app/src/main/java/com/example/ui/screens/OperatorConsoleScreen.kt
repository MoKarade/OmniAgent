package com.example.ui.screens

import android.Manifest
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.LiaChatMessage
import com.example.data.LiaConversationThread
import com.example.data.LiaGoogleCloudWorkspaceClient
import com.example.data.LiaLearnedShortcut
import com.example.data.LiaMemoryAndHistoryStore
import com.example.data.LiaUniversalAppsAndGoogleHub
import com.example.data.LiaUserFact
import com.example.model.AgentOperationalStatus
import com.example.model.CognitivePipelineStage
import com.example.model.CognitiveStepResult
import com.example.model.FriendlyMissionSuggestion
import com.example.model.MultiAgentDefaults
import com.example.model.VirtualDeviceScreen
import com.example.security.SoniaToolType
import com.example.ui.components.LiaArchitectureBlueprintDialog
import com.example.ui.components.LiaConversationHistoryDialog
import com.example.ui.components.LiaPersonalDatabaseAndLearningDialog
import com.example.ui.components.LiaSafeSmsConfirmationModal
import com.example.ui.components.LiaUniversalAppsAndGoogleDialog
import com.example.ui.components.PendingSafeSmsConfirmation
import com.example.ui.components.SoniaArtifactCard
import com.example.ui.theme.GoogleBackground
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.GoogleBlueContainer
import com.example.ui.theme.GoogleBlueVivid
import com.example.ui.theme.GoogleBorderSubtle
import com.example.ui.theme.GoogleGreen
import com.example.ui.theme.GoogleGreenContainer
import com.example.ui.theme.GooglePurple
import com.example.ui.theme.GooglePurpleContainer
import com.example.ui.theme.GoogleRed
import com.example.ui.theme.GoogleRedContainer
import com.example.ui.theme.GoogleSurface
import com.example.ui.theme.GoogleSurfaceVariant
import com.example.ui.theme.GoogleTextMuted
import com.example.ui.theme.GoogleTextPrimary
import com.example.ui.theme.GoogleTextSecondary
import com.example.ui.theme.GoogleYellow
import com.example.ui.theme.GoogleYellowContainer
import com.example.viewmodel.OmniAgentUiState
import com.example.viewmodel.OmniAgentViewModel
import com.example.viewmodel.PendingHitlRequest
import kotlinx.coroutines.launch
import java.util.Locale

private data class QuickVisualPrompt(
    val emoji: String,
    val shortTitle: String,
    val suggestion: FriendlyMissionSuggestion?,
    val toolType: SoniaToolType?,
    val directPrompt: String? = null,
    val accentColor: Color
)

private fun agentEmojiFor(agentId: String): String = when (agentId) {
    "agent_alpha" -> "🤖"
    "agent_beta" -> "💬"
    "agent_gamma" -> "🏦"
    else -> "✨"
}

private fun shortAgentName(name: String): String = when {
    name.contains("Alpha", ignoreCase = true) -> "Alpha"
    name.contains("Beta", ignoreCase = true) -> "Beta"
    name.contains("Gamma", ignoreCase = true) -> "Gamma"
    else -> name.take(10)
}

/**
 * Tab 1: Ultra-minimalist Dark Google / Gemini style Chat Window for LIA.
 * Syncs live with the outside floating bubble (`LiaMemoryAndHistoryStore`), preserves full multi-turn
 * chat history, supports direct SMS sending, personal database ("Mes Infos"), and fast app learning.
 */
@Composable
fun OperatorConsoleScreen(
    uiState: OmniAgentUiState,
    viewModel: OmniAgentViewModel,
    onOpenAccessPermissions: (String) -> Unit = {},
    onOpenGoogleSignIn: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val liaMemory by LiaMemoryAndHistoryStore.state.collectAsStateWithLifecycle()
    val universalHub by LiaUniversalAppsAndGoogleHub.state.collectAsStateWithLifecycle()
    val workspaceAuth by LiaGoogleCloudWorkspaceClient.state.collectAsStateWithLifecycle()
    val mcpRegistry by com.example.service.LiaMcpConnectorRegistry.state.collectAsStateWithLifecycle()

    LaunchedEffect(context) {
        if (mcpRegistry.connectors.isEmpty()) {
            com.example.service.LiaMcpConnectorRegistry.refreshConnectors(context)
        }
    }

    var showPlusToolsTray by rememberSaveable { mutableStateOf(false) }
    var showPhoneScreenPreview by rememberSaveable { mutableStateOf(false) }
    var showLiveDiagnosticView by rememberSaveable { mutableStateOf(false) }
    var showFullScreenControlDeck by rememberSaveable { mutableStateOf(false) }
    var showMcpConnectorsModal by rememberSaveable { mutableStateOf(false) }
    var showHistoryDialog by rememberSaveable { mutableStateOf(false) }
    var showMemoryDatabaseDialog by rememberSaveable { mutableStateOf(false) }
    var showUniversalGoogleAppsDialog by rememberSaveable { mutableStateOf(false) }
    var showArchitectureBlueprintDialog by rememberSaveable { mutableStateOf(false) }
    var pendingSafeSmsModal by remember { mutableStateOf<PendingSafeSmsConfirmation?>(null) }

    // Hands-free SpeechRecognizer state (Commands + Voice Notes)
    var isHandsFreeModeEnabled by rememberSaveable { mutableStateOf(false) }
    var isVoiceNoteDictationMode by rememberSaveable { mutableStateOf(false) }
    var isSpeechRecognizerListening by remember { mutableStateOf(false) }
    var voiceRmsLevel by remember { mutableStateOf(0f) }
    var voiceAutoStartSignal by remember { mutableIntStateOf(0) }

    val workspacePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        LiaGoogleCloudWorkspaceClient.syncAllWorkspaceData(context)
    }

    val listState = rememberLazyListState()
    val activeAgent = uiState.activeAgent

    // Auto-scroll when a new message arrives in the active conversation
    LaunchedEffect(liaMemory.activeMessages.size, uiState.isThinkingStep) {
        val totalItems = liaMemory.activeMessages.size + uiState.artifacts.size + 1
        if (totalItems > 1) {
            listState.animateScrollToItem((totalItems - 1).coerceAtLeast(0))
        }
    }

    // Lazy Android TextToSpeech engine (initialized when Voice TTS or Hands-Free mode is enabled)
    val shouldEnableTts = uiState.isVoiceTtsEnabled || isHandsFreeModeEnabled
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context, shouldEnableTts) {
        var instance: TextToSpeech? = null
        if (shouldEnableTts) {
            instance = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    instance?.language = Locale.FRENCH
                    instance?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if (isHandsFreeModeEnabled) {
                                voiceAutoStartSignal += 1
                            }
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {}
                    })
                    if (uiState.lastSpokenText.isNotBlank()) {
                        instance?.speak(uiState.lastSpokenText, TextToSpeech.QUEUE_FLUSH, null, "lia_tts")
                    }
                }
            }
            ttsEngine = instance
        }
        onDispose {
            instance?.stop()
            instance?.shutdown()
            ttsEngine = null
        }
    }

    LaunchedEffect(uiState.lastSpokenText, shouldEnableTts) {
        if (shouldEnableTts && uiState.lastSpokenText.isNotBlank()) {
            ttsEngine?.speak(uiState.lastSpokenText, TextToSpeech.QUEUE_FLUSH, null, "lia_tts")
        }
    }

    val primaryToolsList = remember {
        listOf(
            SoniaToolType.CREATE_CHART,
            SoniaToolType.CREATE_CHECKLIST,
            SoniaToolType.GENERATE_IMAGE,
            SoniaToolType.CREATE_TABLE,
            SoniaToolType.CREATE_TIMELINE,
            SoniaToolType.CODE_CALCULATOR,
            SoniaToolType.VOICE_TTS
        )
    }

    val isAnyModalActive = showMcpConnectorsModal ||
        showHistoryDialog ||
        showMemoryDatabaseDialog ||
        showUniversalGoogleAppsDialog ||
        showArchitectureBlueprintDialog ||
        pendingSafeSmsModal != null

    val consoleBackgroundBlur by animateDpAsState(
        targetValue = if (isAnyModalActive) 12.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "console_modal_blur"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .blur(consoleBackgroundBlur)
    ) {
        // 1. COLLAPSIBLE PHONE SCREEN PREVIEW (Hidden by default — fluid spring transition)
        AnimatedVisibility(
            visible = showPhoneScreenPreview,
            enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut() + shrinkVertically()
        ) {
            VisualMiniPhonePreviewCard(
                screen = uiState.currentScreen,
                highlightedElementId = uiState.lastHighlightedElementId,
                onSelectScreen = viewModel::selectScreenManually,
                onTapElement = viewModel::triggerManualElementTap,
                onClose = { showPhoneScreenPreview = false }
            )
        }

        // 2. GLASSMORPHIC / SOFT-NEUMORPHIC NATURAL LANGUAGE STREAM
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // A. Minimalist Glassmorphic Welcome Header + Connected Apps (MCP) + Intuitive Pills
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Bonjour, que souhaitez-vous faire ?",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = GoogleTextPrimary
                        )
                        Text(
                            text = "Parlez ou écrivez naturellement — LIA agit sur vos applications et votre téléphone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoogleTextSecondary
                        )
                    }

                    // Barre de contrôles intuitifs Glassmorphic / Soft-Neumorphic (zéro jargon technique)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.example.ui.components.GlassNeumorphicPill(
                            text = "Applis connectées",
                            emoji = "✨",
                            selected = showMcpConnectorsModal,
                            accentColor = GoogleBlue,
                            onClick = { showMcpConnectorsModal = true },
                            modifier = Modifier.testTag("universal_google_apps_button")
                        )

                        com.example.ui.components.GlassNeumorphicPill(
                            text = "Contrôler l'écran",
                            emoji = "🎮",
                            selected = showFullScreenControlDeck,
                            accentColor = GoogleBlue,
                            onClick = { showFullScreenControlDeck = !showFullScreenControlDeck },
                            modifier = Modifier.testTag("toggle_screen_control_deck_button")
                        )

                        com.example.ui.components.GlassNeumorphicPill(
                            text = "Mes souvenirs",
                            emoji = "💭",
                            selected = showMemoryDatabaseDialog,
                            accentColor = GooglePurple,
                            onClick = { showMemoryDatabaseDialog = true },
                            modifier = Modifier.testTag("memory_database_button")
                        )

                        com.example.ui.components.GlassNeumorphicPill(
                            text = "Compréhension",
                            emoji = "💡",
                            selected = showLiveDiagnosticView,
                            accentColor = GooglePurple,
                            onClick = { showLiveDiagnosticView = !showLiveDiagnosticView },
                            modifier = Modifier.testTag("toggle_live_diagnostic_button")
                        )

                        com.example.ui.components.GlassNeumorphicPill(
                            text = "Discussions",
                            emoji = "🕘",
                            selected = showHistoryDialog,
                            accentColor = GoogleBlue,
                            onClick = { showHistoryDialog = true },
                            modifier = Modifier.testTag("history_threads_button")
                        )

                        com.example.ui.components.GlassNeumorphicPill(
                            text = "Nouvelle",
                            emoji = "➕",
                            selected = false,
                            accentColor = GoogleGreen,
                            onClick = {
                                LiaMemoryAndHistoryStore.startNewConversation()
                                viewModel.resetSession()
                            },
                            modifier = Modifier.testTag("new_conversation_button")
                        )
                    }

                    // BARRE DYNAMIQUE DES APPLICATIONS CONNECTÉES (Architecture MCP : WhatsApp, Messenger, Keep, Services Google, etc.)
                    if (mcpRegistry.connectors.isNotEmpty()) {
                        com.example.ui.components.LiaConnectedAppsNaturalBar(
                            connectors = mcpRegistry.connectors,
                            onExecuteNaturalCommand = { cmd ->
                                viewModel.updateMissionText(cmd)
                                viewModel.executeSingleCognitiveStep()
                            },
                            onExecuteMcpTool = { tool, args ->
                                com.example.data.LiaAssistantBrain.executeDynamicMcpToolFromUi(tool, args)
                            },
                            onOpenAllAppsSheet = { showMcpConnectorsModal = true }
                        )
                    }

                    // COMPRÉHENSION & MÉMOIRE EN DIRECT (Transition fluide)
                    AnimatedVisibility(
                        visible = showLiveDiagnosticView,
                        enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        com.example.ui.components.LiaLiveThoughtAndRagDiagnosticCard(
                            uiState = uiState,
                            liaMemory = liaMemory,
                            onTriggerCommand = { cmd ->
                                viewModel.updateMissionText(cmd)
                                viewModel.executeSingleCognitiveStep()
                            },
                            onClose = { showLiveDiagnosticView = false }
                        )
                    }

                    // TÉLÉCOMMANDE TACTILE : CONTRÔLE INTUITIF DE L'ÉCRAN & ACTIONS DIRECTES
                    AnimatedVisibility(
                        visible = showFullScreenControlDeck,
                        enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        com.example.ui.components.LiaFullScreenControlAndActionsDeck(
                            showPhoneScreenPreview = showPhoneScreenPreview,
                            onTogglePhonePreview = { showPhoneScreenPreview = !showPhoneScreenPreview },
                            onExecuteCommand = { cmd ->
                                viewModel.updateMissionText(cmd)
                                viewModel.executeSingleCognitiveStep()
                            },
                            onClose = { showFullScreenControlDeck = false }
                        )
                    }

                    // Suggestions personnalisées (« Pour vous »)
                    com.example.ui.components.LiaProactiveRagActionCardsSection(
                        cards = liaMemory.proactiveRagActionCards,
                        souvenirsCount = liaMemory.souvenirs.size + liaMemory.longTermMemories.size,
                        onExecuteCard = { card ->
                            viewModel.updateMissionText(card.commandPrompt)
                            viewModel.executeSingleCognitiveStep()
                        }
                    )

                    // Suggestions naturelles en 1 geste
                    val quickConsumerSuggestions = remember {
                        listOf(
                            "💙 Discussion Messenger" to "Ouvre une conversation sur Messenger",
                            "🟢 Discussion WhatsApp" to "Ouvre une conversation sur WhatsApp",
                            "💡 Créer une note Keep" to "Crée une note dans Keep : Acheter du café",
                            "📅 Mon prochain RDV" to "Quels sont mes prochains rendez-vous dans mon agenda ?",
                            "🎮 Contrôler l'écran" to "Contrôler l'écran",
                            "💬 Envoyer un SMS" to "Envoyer un message",
                            "📞 Appeler un contact" to "Appeler quelqu'un",
                            "🎮 Dernier jeu Steam" to "Retrouve le dernier jeu auquel j'ai joué dans Steam",
                            "⏱️ Mettre un minuteur" to "Mettre un minuteur",
                            "🔦 Lampe torche" to "Allume la lampe torche",
                            "⚙️ Réglages rapides" to "Réglages rapides",
                            "📁 Mes fichiers Drive" to "Cherche mes fichiers dans Google Drive",
                            "🧮 Calculer / Convertir" to "Calculer ou convertir",
                            "🌍 Traduire" to "Traduire un texte"
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        quickConsumerSuggestions.forEachIndexed { idx, (chipLabel, commandText) ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = GoogleSurface,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(1.dp, GoogleBorderSubtle, RoundedCornerShape(50))
                                    .clickable {
                                        viewModel.updateMissionText(commandText)
                                        viewModel.executeSingleCognitiveStep()
                                    }
                                    .testTag("consumer_quick_chip_$idx")
                            ) {
                                Text(
                                    text = chipLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = GoogleTextPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    // Clean Favorite Shortcuts Row (no developer percentages)
                    val rlActions = liaMemory.recommendedQuickActions
                    if (rlActions.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rlActions.take(5).forEach { rec ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = GoogleSurface,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .border(1.dp, GoogleBorderSubtle, RoundedCornerShape(50))
                                        .clickable {
                                            val launched = LiaMemoryAndHistoryStore.launchAppFast(
                                                context = context,
                                                packageName = rec.targetPackageOrScreen
                                            )
                                            if (!launched) {
                                                viewModel.updateMissionText(rec.triggerPhrase)
                                                viewModel.executeSingleCognitiveStep()
                                            }
                                        }
                                        .testTag("rl_quick_action_${rec.shortcutId}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(text = rec.emoji, style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            text = rec.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = GoogleTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // B. Rich Interactive LIA Tool Artifacts (Charts, Checklists, AI Images, Tables, Timelines, Calculators)
            items(
                items = uiState.artifacts,
                key = { it.id }
            ) { artifact ->
                SoniaArtifactCard(
                    artifact = artifact,
                    onToggleChecklistItem = viewModel::toggleChecklistItem,
                    onDismissArtifact = viewModel::dismissArtifact
                )
            }

            // C. Persistent Multi-Turn Conversation Messages (Synced live between Bubble & Main App!)
            items(
                items = liaMemory.activeMessages,
                key = { it.id }
            ) { msg ->
                if (msg.role == "user") {
                    LiaUserMessageBubble(msg = msg)
                } else {
                    LiaAssistantMessageBubble(
                        msg = msg,
                        onSendSmsDirect = { recipient, phone, body ->
                            pendingSafeSmsModal = PendingSafeSmsConfirmation(
                                messageId = msg.id,
                                recipientName = recipient,
                                phoneNumber = phone,
                                messageBody = body
                            )
                        },
                        onLaunchAppFast = { pkg ->
                            LiaMemoryAndHistoryStore.launchAppFast(
                                context = context,
                                packageName = pkg
                            )
                        },
                        onQuickReplyTap = { quickReply ->
                            viewModel.updateMissionText(quickReply)
                            viewModel.executeSingleCognitiveStep()
                        }
                    )
                }
            }

            // D. Optional Technical Phone Action Step Details (Only visible when user opens "Aperçu écran" in + menu)
            if (showPhoneScreenPreview) {
                val nonChatSteps = uiState.cognitiveHistory.filter { it.action.tool != "chat_reply" }
                items(
                    items = nonChatSteps,
                    key = { "step_${it.stepIndex}" }
                ) { step ->
                    SoniaDarkChatBubble(step = step)
                }
            }

            // E. Fast Live Indicator when LIA is acting
            if (uiState.isThinkingStep) {
                item {
                    LiveVisualThinkingBubble(stage = uiState.liveTelemetry.stage)
                }
            }

            // F. Security Confirmation Bubble (HITL)
            val hitl = uiState.pendingHitlRequest
            if (hitl != null) {
                item {
                    VisualHitlChatBubble(
                        request = hitl,
                        onApprove = { viewModel.resolveHitlConfirmation(approved = true) },
                        onReject = { viewModel.resolveHitlConfirmation(approved = false) }
                    )
                }
            }

            // G. Error / Security Alert Pill if any
            if (uiState.errorMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = GoogleRedContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.labelMedium,
                                color = GoogleRed,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = viewModel::dismissError,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fermer",
                                    tint = GoogleRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. HIDDEN SUPER-PALETTE DRAWER (Semi-transparent Material 3 Surface with fluid spring transition)
        AnimatedVisibility(
            visible = showPlusToolsTray,
            enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = Color(0xFF181C26).copy(alpha = 0.84f),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                GoogleBorderSubtle
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Row 1: All LIA Specialized Creation Tools
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        primaryToolsList.forEach { tool ->
                            val isAllowed = uiState.activeAgentAllowedTools.contains(tool)
                            val isVoiceActive = tool == SoniaToolType.VOICE_TTS && uiState.isVoiceTtsEnabled
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = when {
                                    isVoiceActive -> GoogleGreenContainer
                                    isAllowed -> GoogleSurfaceVariant
                                    else -> GoogleRedContainer.copy(alpha = 0.4f)
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        viewModel.executeSoniaTool(tool)
                                        if (tool != SoniaToolType.VOICE_TTS) {
                                            showPlusToolsTray = false
                                        }
                                    }
                                    .testTag("plus_tool_${tool.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(text = tool.emoji, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = tool.shortLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isAllowed) GoogleTextPrimary else GoogleRed
                                    )
                                }
                            }
                        }
                    }

                    // Row 2: Agent switcher pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        uiState.agents.forEach { agent ->
                            val isSelected = agent.id == uiState.activeAgentId
                            val statusColor = when (agent.status) {
                                AgentOperationalStatus.IDLE,
                                AgentOperationalStatus.COMPLETED -> GoogleGreen
                                AgentOperationalStatus.OBSERVING,
                                AgentOperationalStatus.THINKING -> GoogleYellow
                                AgentOperationalStatus.EXECUTING -> GoogleBlue
                                else -> GoogleRed
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) GoogleBlueContainer else GoogleSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { viewModel.selectActiveAgent(agent.id) }
                                    .testTag("chat_agent_pill_${agent.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(text = agentEmojiFor(agent.id), style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = shortAgentName(agent.name),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) GoogleBlue else GoogleTextPrimary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(statusColor)
                                    )
                                }
                            }
                        }
                    }

                    // Row 3: Quick Options & Advanced Settings (tucked neatly inside the + drawer)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mode Mains-Libres
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isHandsFreeModeEnabled) GoogleRedContainer else GoogleSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    isHandsFreeModeEnabled = !isHandsFreeModeEnabled
                                    if (isHandsFreeModeEnabled) {
                                        if (!uiState.isVoiceTtsEnabled) {
                                            viewModel.toggleVoiceTts()
                                        }
                                        voiceAutoStartSignal += 1
                                    }
                                    showPlusToolsTray = false
                                }
                                .testTag("hands_free_voice_mode_pill")
                        ) {
                            Text(
                                text = if (isHandsFreeModeEnabled) "🎙️ Mains-libres actif" else "🎙️ Mains-libres",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isHandsFreeModeEnabled) GoogleRed else GoogleTextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        // Dicter une Note
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isVoiceNoteDictationMode) GoogleYellowContainer else GoogleSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    isVoiceNoteDictationMode = !isVoiceNoteDictationMode
                                    if (isVoiceNoteDictationMode) {
                                        voiceAutoStartSignal += 1
                                    }
                                    showPlusToolsTray = false
                                }
                                .testTag("voice_note_dictation_pill")
                        ) {
                            Text(
                                text = if (isVoiceNoteDictationMode) "📝 Dictée active" else "📝 Note vocale",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isVoiceNoteDictationMode) GoogleYellow else GoogleTextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        // Compte Google
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (workspaceAuth.isSignedIn) GoogleGreenContainer else GoogleSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    showPlusToolsTray = false
                                    onOpenGoogleSignIn()
                                }
                                .testTag("header_google_signin_pill")
                        ) {
                            Text(
                                text = if (workspaceAuth.isSignedIn) "✓ Compte Google" else "🔑 Compte Google",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (workspaceAuth.isSignedIn) GoogleGreen else GoogleTextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        // Voir l'écran (Simulateur d'interactions)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (showPhoneScreenPreview) GoogleBlueContainer else GoogleSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    showPhoneScreenPreview = !showPhoneScreenPreview
                                    showPlusToolsTray = false
                                }
                                .testTag("toggle_phone_preview_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = "Écran",
                                    tint = GoogleBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (showPhoneScreenPreview) "Masquer l'aperçu" else "Aperçu écran",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GoogleTextPrimary
                                )
                            }
                        }

                        // Confidentialité & Sécurité
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = GoogleSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    showPlusToolsTray = false
                                    onOpenAccessPermissions(activeAgent.id)
                                }
                                .testTag("chat_open_permissions_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Confidentialité",
                                    tint = GoogleGreen,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Confidentialité",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GoogleTextPrimary
                                )
                            }
                        }

                        // Architecture système (Avancé)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = GoogleSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    showPlusToolsTray = false
                                    showArchitectureBlueprintDialog = true
                                }
                                .testTag("architecture_blueprint_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Système",
                                    tint = GoogleBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Système avancé",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GoogleTextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = GoogleSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    viewModel.resetSession()
                                    showPlusToolsTray = false
                                }
                                .testTag("reset_session_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Réinitialiser",
                                    tint = GoogleTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Réinitialiser",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GoogleTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3B. HANDS-FREE SPEECHRECOGNIZER & VOICE NOTE LIVE STUDIO BANNER
        AnimatedVisibility(
            visible = isSpeechRecognizerListening || isHandsFreeModeEnabled || isVoiceNoteDictationMode
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = GoogleSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .border(
                        width = 1.dp,
                        color = when {
                            isSpeechRecognizerListening -> GoogleRed
                            isVoiceNoteDictationMode -> GoogleYellow
                            else -> GoogleBlue
                        },
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size((10 + (voiceRmsLevel * 12)).dp)
                                .clip(CircleShape)
                                .background(if (isSpeechRecognizerListening) GoogleRed else GoogleBlue)
                        )
                        Column {
                            Text(
                                text = when {
                                    isVoiceNoteDictationMode && isSpeechRecognizerListening -> "📝 Dictée de Note Vocale en cours (SpeechRecognizer)…"
                                    isVoiceNoteDictationMode -> "📝 Mode Note Vocale actif — Parlez pour mémoriser dans Room"
                                    isHandsFreeModeEnabled && isSpeechRecognizerListening -> "🎙️ Écoute Mains-Libres active — Parlez à LIA…"
                                    isHandsFreeModeEnabled -> "🎙️ Mode Mains-Libres continu actif (Commandes & Notes)"
                                    else -> "🎙️ Reconnaissance vocale Android active…"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GoogleTextPrimary
                            )
                            if (uiState.userMission.isNotBlank()) {
                                Text(
                                    text = "« ${uiState.userMission} »",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoogleBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isVoiceNoteDictationMode) GoogleYellowContainer else GoogleSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { isVoiceNoteDictationMode = !isVoiceNoteDictationMode }
                        ) {
                            Text(
                                text = if (isVoiceNoteDictationMode) "📝 Note" else "⚡ Commande",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isVoiceNoteDictationMode) GoogleYellow else GoogleTextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = GoogleSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    isHandsFreeModeEnabled = false
                                    isVoiceNoteDictationMode = false
                                }
                        ) {
                            Text(
                                text = "Fermer",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoogleTextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. BOTTOM MINIMALIST GLASSMORPHIC / SOFT-NEUMORPHIC FLOATING INPUT DOCK
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF232836).copy(alpha = 0.92f),
                            Color(0xFF171B24).copy(alpha = 0.88f)
                        )
                    ),
                    shape = RoundedCornerShape(50)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            GoogleBlue.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.50f)
                        )
                    ),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // '+' Button that hides/reveals the Super-Palette
            IconButton(
                onClick = { showPlusToolsTray = !showPlusToolsTray },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (showPlusToolsTray) GoogleBlueContainer else GoogleSurfaceVariant)
                    .testTag("toggle_plus_options_button")
            ) {
                Icon(
                    imageVector = if (showPlusToolsTray) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "Outils LIA",
                    tint = if (showPlusToolsTray) GoogleBlue else GoogleTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Clean borderless TextField
            TextField(
                value = uiState.userMission,
                onValueChange = viewModel::updateMissionText,
                placeholder = {
                    Text(
                        text = if (isVoiceNoteDictationMode)
                            "Dictez une note vocale au micro…"
                        else
                            "Parler au micro ou écrire à LIA…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GoogleTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = GoogleTextPrimary,
                    unfocusedTextColor = GoogleTextPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("mission_input_field")
            )

            // Voice-to-Text Microphone Input Button (Android SpeechRecognizer for Commands & Notes)
            com.example.ui.components.SoniaVoiceInputButton(
                onPartialTranscript = { partial ->
                    val formattedPartial = if (isVoiceNoteDictationMode && !partial.lowercase().startsWith("note")) {
                        "Note vocale : $partial"
                    } else {
                        partial
                    }
                    viewModel.updateMissionText(formattedPartial)
                },
                onFinalTranscript = { finalSpoken ->
                    val finalCommand = if (isVoiceNoteDictationMode && !finalSpoken.lowercase().startsWith("note")) {
                        "Note vocale : $finalSpoken"
                    } else {
                        finalSpoken
                    }
                    viewModel.submitVoiceCommand(finalCommand)
                },
                onVoiceError = viewModel::reportVoiceInputError,
                onListeningStateChanged = { active -> isSpeechRecognizerListening = active },
                onRmsLevelChanged = { rms -> voiceRmsLevel = rms },
                autoStartSignal = voiceAutoStartSignal,
                buttonSize = 36.dp,
                iconSize = 18.dp,
                testTag = "chat_voice_mic_button"
            )

            // Voice TTS Quick Toggle Icon (Read Aloud)
            IconButton(
                onClick = viewModel::toggleVoiceTts,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isVoiceTtsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = "Voix",
                    tint = if (uiState.isVoiceTtsEnabled) GoogleGreen else GoogleTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Send / Execute Button
            IconButton(
                onClick = viewModel::executeSingleCognitiveStep,
                enabled = !uiState.isThinkingStep && uiState.pendingHitlRequest == null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (!uiState.isThinkingStep && uiState.pendingHitlRequest == null) GoogleBlueVivid
                        else GoogleSurfaceVariant
                    )
                    .testTag("step_agent_button")
            ) {
                if (uiState.isThinkingStep) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = GoogleBlue
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.btn_step_cycle),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // 5. CONVERSATION HISTORY DIALOG ("Historique")
    if (showHistoryDialog) {
        LiaConversationHistoryDialog(
            threads = liaMemory.threads,
            activeThreadId = liaMemory.currentThreadId,
            onSelectThread = { id ->
                LiaMemoryAndHistoryStore.selectConversation(id)
                showHistoryDialog = false
            },
            onNewConversation = {
                LiaMemoryAndHistoryStore.startNewConversation()
                viewModel.resetSession()
                showHistoryDialog = false
            },
            onDeleteThread = { id ->
                LiaMemoryAndHistoryStore.deleteConversation(id)
            },
            onDismiss = { showHistoryDialog = false }
        )
    }

    // 6. PERSONAL INFO DATABASE, SOUVENIRS (RAG) & RL APP USAGE HABITS DIALOG
    if (showMemoryDatabaseDialog) {
        LiaPersonalDatabaseAndLearningDialog(
            souvenirs = liaMemory.souvenirs,
            facts = liaMemory.userFacts,
            shortcuts = liaMemory.learnedShortcuts,
            longTermMemories = liaMemory.longTermMemories,
            appUsageHabits = liaMemory.appUsageHabits,
            lastRagOutcome = liaMemory.lastRagOutcome,
            onSaveSouvenir = { type, key, title, detail, tags ->
                LiaMemoryAndHistoryStore.upsertSouvenir(
                    type = type,
                    subjectKey = key,
                    title = title,
                    detailValue = detail,
                    searchTags = tags
                )
            },
            onDeleteSouvenir = { id ->
                LiaMemoryAndHistoryStore.deleteSouvenir(id)
            },
            onSaveFact = { key, label, value ->
                LiaMemoryAndHistoryStore.upsertUserFact(key, label, value)
            },
            onDeleteFact = { key ->
                LiaMemoryAndHistoryStore.deleteUserFact(key)
            },
            onLaunchShortcut = { shortcut ->
                LiaMemoryAndHistoryStore.launchAppFast(context, shortcut.targetPackageOrScreen)
            },
            onBoostShortcut = { shortcut ->
                LiaMemoryAndHistoryStore.recordLearnedShortcut(
                    triggerPhrase = shortcut.triggerPhrase,
                    actionTitle = shortcut.actionTitle,
                    targetPackageOrScreen = shortcut.targetPackageOrScreen,
                    stepsSaved = shortcut.stepsSaved + 1,
                    latencyMs = (shortcut.avgLatencyMs - 10L).coerceAtLeast(35L),
                    rewardSignal = 1.2f
                )
            },
            onLaunchInstalledApp = { pkg, label ->
                LiaMemoryAndHistoryStore.launchAppFast(context, pkg, label)
            },
            onDeleteLongTermMemory = { id ->
                LiaMemoryAndHistoryStore.deleteLongTermMemory(id)
            },
            onDismiss = { showMemoryDatabaseDialog = false }
        )
    }

    // 7. MANDATORY SAFE CONTACT RESOLVER MODAL BEFORE SENDING ANY SMS
    val activeSmsModal = pendingSafeSmsModal
    if (activeSmsModal != null) {
        LiaSafeSmsConfirmationModal(
            pending = activeSmsModal,
            userFacts = liaMemory.userFacts,
            onConfirmSend = { confirmedRecipient, confirmedPhone, confirmedBody ->
                LiaMemoryAndHistoryStore.sendSmsDirectly(
                    context = context,
                    messageId = activeSmsModal.messageId,
                    recipientName = confirmedRecipient,
                    phoneNumber = confirmedPhone,
                    messageBody = confirmedBody
                )
                pendingSafeSmsModal = null
            },
            onDismiss = { pendingSafeSmsModal = null }
        )
    }

    // 8. NEURO-SYMBOLIC ARCHITECTURE BLUEPRINT DIALOG (10 Sections from Engineering Spec)
    if (showArchitectureBlueprintDialog) {
        LiaArchitectureBlueprintDialog(
            onDismiss = { showArchitectureBlueprintDialog = false }
        )
    }

    // 9. UNIVERSAL GOOGLE WORKSPACE & ALL PHONE APPS DIALOG
    if (showUniversalGoogleAppsDialog) {
        LiaUniversalAppsAndGoogleDialog(
            hubState = universalHub,
            workspaceAuthState = workspaceAuth,
            onSignInWithCredentialManager = {
                coroutineScope.launch {
                    val ok = LiaGoogleCloudWorkspaceClient.signInInteractiveWithCredentialManager(context)
                    if (!ok) {
                        showUniversalGoogleAppsDialog = false
                        onOpenGoogleSignIn()
                    }
                }
            },
            onSyncGoogleWorkspaceApis = {
                workspacePermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR
                    )
                )
            },
            onLaunchGoogleSpec = { spec ->
                LiaMemoryAndHistoryStore.launchAppFast(
                    context = context,
                    packageName = spec.packageName,
                    appLabel = spec.title
                )
            },
            onTriggerPromptCommand = { promptCmd ->
                showUniversalGoogleAppsDialog = false
                viewModel.updateMissionText(promptCmd)
                viewModel.executeSingleCognitiveStep()
            },
            onLaunchPhoneApp = { pkg, label ->
                LiaMemoryAndHistoryStore.launchAppFast(
                    context = context,
                    packageName = pkg,
                    appLabel = label
                )
            },
            onRescanApps = {
                LiaUniversalAppsAndGoogleHub.refreshAllInstalledPhoneApps(context)
            },
            onDismiss = { showUniversalGoogleAppsDialog = false }
        )
    }

    // 10. GLASSMORPHIC DYNAMIC MCP CONNECTORS MODAL ("Applis connectées")
    if (showMcpConnectorsModal) {
        com.example.ui.components.LiaMcpConnectorsGlassModal(
            connectors = mcpRegistry.connectors,
            isGoogleSignedIn = workspaceAuth.isSignedIn,
            onSignInGoogle = {
                showMcpConnectorsModal = false
                showUniversalGoogleAppsDialog = true
            },
            onRefreshConnectors = {
                com.example.service.LiaMcpConnectorRegistry.refreshConnectors(context)
            },
            onExecuteMcpTool = { tool, args ->
                com.example.data.LiaAssistantBrain.executeDynamicMcpToolFromUi(tool, args)
            },
            onExecuteNaturalCommand = { cmd ->
                viewModel.updateMissionText(cmd)
                viewModel.executeSingleCognitiveStep()
            },
            onDismiss = { showMcpConnectorsModal = false }
        )
    }
}

@Composable
private fun LiaUserMessageBubble(
    msg: LiaChatMessage
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 24.dp,
        bottomEnd = 6.dp
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (msg.source == "bubble") {
                Text(
                    text = "💬 Écrit depuis la bulle LIA",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoogleBlue
                )
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(bubbleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF283246).copy(alpha = 0.90f),
                                Color(0xFF1D2434).copy(alpha = 0.85f)
                            )
                        ),
                        shape = bubbleShape
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f),
                                GoogleBlue.copy(alpha = 0.25f)
                            )
                        ),
                        shape = bubbleShape
                    )
            ) {
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoogleTextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                )
            }
        }
    }
}

@Composable
private fun LiaAssistantMessageBubble(
    msg: LiaChatMessage,
    onSendSmsDirect: (String, String, String) -> Unit,
    onLaunchAppFast: (String) -> Unit,
    onQuickReplyTap: (String) -> Unit
) {
    val cardShape = RoundedCornerShape(24.dp)
    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E2330).copy(alpha = 0.86f),
                        Color(0xFF151922).copy(alpha = 0.80f)
                    )
                ),
                shape = cardShape
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.20f),
                        GoogleBlue.copy(alpha = 0.30f),
                        Color.Black.copy(alpha = 0.45f)
                    )
                ),
                shape = cardShape
            )
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Learned Note Badge (if LIA learned a personal fact, passive souvenir, or shortcut during this turn)
            if (!msg.learnedNote.isNullOrBlank()) {
                val canUndoSouvenir = msg.learnedNote.startsWith("💭 Auto-appris") || msg.learnedNote.startsWith("🧠 Appris")
                Surface(
                    shape = RoundedCornerShape(50),
                    color = GooglePurpleContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = msg.learnedNote,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = GooglePurple,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (canUndoSouvenir) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = GoogleRedContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        LiaMemoryAndHistoryStore.undoLatestLearnedSouvenir(msg.id)
                                    }
                                    .testTag("undo_souvenir_${msg.id}")
                            ) {
                                Text(
                                    text = "✕ Annuler",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoogleRed,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            val formattedText = remember(msg.text) { formatLiaRichMessage(msg.text) }
            val clipboardManager = LocalClipboardManager.current
            var copiedFeedback by remember(msg.id) { mutableStateOf(false) }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "✨",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 1.dp)
                )
                Text(
                    text = formattedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoogleTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Subtle Consumer Footer (Timestamp + 1-tap Copy)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = msg.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = GoogleTextMuted
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (copiedFeedback) GoogleGreenContainer else GoogleSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(msg.text.replace("**", "").replace("`", "")))
                            copiedFeedback = true
                        }
                        .testTag("copy_lia_message_${msg.id}")
                ) {
                    Text(
                        text = if (copiedFeedback) "✓ Copié" else "Copier",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (copiedFeedback) GoogleGreen else GoogleTextSecondary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            // Clean Consumer SMS Preview Card (with 1-tap Cancel button so LIA never gets stuck!)
            val sms = msg.smsDraft
            if (sms != null) {
                var recipientInput by rememberSaveable(msg.id, sms.recipientName) { mutableStateOf(sms.recipientName) }
                var phoneInput by rememberSaveable(msg.id, sms.phoneNumber) { mutableStateOf(sms.phoneNumber) }
                var bodyInput by rememberSaveable(msg.id, sms.messageBody) { mutableStateOf(sms.messageBody) }

                val isPhoneCurrentlyValid = LiaMemoryAndHistoryStore.isPhoneNumberValid(phoneInput)

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GoogleBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (isPhoneCurrentlyValid) GoogleBlue.copy(alpha = 0.45f) else GoogleYellow.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(18.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💬 Message SMS",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = GoogleTextPrimary
                            )
                            if (sms.isSent) {
                                Text(
                                    text = "✓ Envoyé",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoogleGreen
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = GoogleRedContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            LiaMemoryAndHistoryStore.cancelSmsDraft(msg.id)
                                        }
                                        .testTag("cancel_sms_draft_button_${msg.id}")
                                ) {
                                    Text(
                                        text = "✕ Annuler",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = GoogleRed,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = recipientInput,
                                onValueChange = { newName ->
                                    recipientInput = newName
                                    val resolved = LiaMemoryAndHistoryStore.verifyContactForSms(newName)
                                    if (resolved.isVerified && resolved.phoneNumber.isNotBlank()) {
                                        phoneInput = resolved.phoneNumber
                                    }
                                },
                                label = { Text("Destinataire") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.45f)
                                    .testTag("sms_preview_recipient_${msg.id}")
                            )

                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                label = { Text("Numéro") },
                                placeholder = { Text("06...") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.55f)
                                    .testTag("sms_preview_phone_${msg.id}")
                            )
                        }

                        OutlinedTextField(
                            value = bodyInput,
                            onValueChange = { bodyInput = it },
                            label = { Text("Message") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sms_preview_body_${msg.id}")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val check = LiaMemoryAndHistoryStore.verifyContactForSms(
                                        phoneInput.ifBlank { recipientInput }
                                    )
                                    if (phoneInput.isBlank() && check.phoneNumber.isNotBlank()) {
                                        phoneInput = check.phoneNumber
                                    }
                                    if (recipientInput.isBlank() && check.recipientName.isNotBlank()) {
                                        recipientInput = check.recipientName
                                    }
                                    LiaMemoryAndHistoryStore.updateSmsDraftPreview(
                                        messageId = msg.id,
                                        updatedRecipient = recipientInput,
                                        updatedPhone = phoneInput,
                                        updatedBody = bodyInput
                                    )
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("verify_contact_sms_button_${msg.id}")
                            ) {
                                Text(
                                    text = "Vérifier",
                                    color = GoogleBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Button(
                                onClick = { onSendSmsDirect(recipientInput, phoneInput, bodyInput) },
                                enabled = isPhoneCurrentlyValid && bodyInput.isNotBlank(),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (sms.isSent) GoogleGreen else GoogleBlueVivid
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("direct_sms_send_button_${msg.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Envoyer SMS",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (sms.isSent) "Renvoyer" else "Envoyer",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Clean Consumer App Launch Button (no technical latency or RL jargon)
            val appAction = msg.appLaunchAction
            if (appAction != null) {
                Button(
                    onClick = { onLaunchAppFast(appAction.packageName) },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlueVivid),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fast_launch_button_${msg.id}")
                ) {
                    Text(
                        text = "Ouvrir ${appAction.appLabel}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 1-Tap Contextual Follow-Up Suggestion Chips (for general actions or next steps)
            if (msg.quickReplies.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    msg.quickReplies.forEachIndexed { idx, suggestion ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = GoogleBlueContainer.copy(alpha = 0.75f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, GoogleBlue.copy(alpha = 0.45f), RoundedCornerShape(50))
                                .clickable { onQuickReplyTap(suggestion) }
                                .testTag("lia_quick_reply_${msg.id}_$idx")
                        ) {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = GoogleBlue,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Transforme le texte Markdown de LIA (`**gras**`, `*italique*`, `` `code` ``) en un `AnnotatedString`
 * élégant, sans jamais afficher d'astérisques bruts à l'utilisateur.
 */
private fun formatLiaRichMessage(raw: String): AnnotatedString {
    val cleanLines = raw.replace("\r\n", "\n")
    return buildAnnotatedString {
        var i = 0
        val n = cleanLines.length
        while (i < n) {
            when {
                cleanLines.startsWith("**", i) -> {
                    val end = cleanLines.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                            append(cleanLines.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(cleanLines[i])
                        i++
                    }
                }
                cleanLines.startsWith("`", i) -> {
                    val end = cleanLines.indexOf("`", i + 1)
                    if (end != -1 && end - i < 80) {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = GoogleBlue)) {
                            append(cleanLines.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(cleanLines[i])
                        i++
                    }
                }
                cleanLines.startsWith("*", i) && (i == 0 || cleanLines[i - 1] != '*') -> {
                    val end = cleanLines.indexOf("*", i + 1)
                    if (end != -1 && end - i in 2..120 && !cleanLines.substring(i + 1, end).contains("\n")) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = GoogleTextSecondary)) {
                            append(cleanLines.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(cleanLines[i])
                        i++
                    }
                }
                else -> {
                    append(cleanLines[i])
                    i++
                }
            }
        }
    }
}



@Composable
private fun SoniaDarkChatBubble(
    step: CognitiveStepResult
) {
    val isPureChatReply = step.action.tool == "chat_reply"
    val isFinalStep = step.action.tool == "terminate" || isPureChatReply
    var showDetails by rememberSaveable(step.stepIndex) { mutableStateOf(false) }
    val targetBadge = step.action.elementId?.let { "#$it" } ?: "Écran"
    val mainText = if (isFinalStep && step.action.summary.isNotBlank()) {
        step.action.summary
    } else {
        step.subgoal
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFinalStep) GoogleSurfaceVariant else GoogleSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isFinalStep) GoogleBlue.copy(alpha = 0.4f) else GoogleBorderSubtle,
                shape = RoundedCornerShape(22.dp)
            )
            .let { mod ->
                if (isPureChatReply) mod else mod.clickable { showDetails = !showDetails }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = if (isPureChatReply) Alignment.Top else Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = if (isPureChatReply) Alignment.Top else Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isPureChatReply) {
                        Text(
                            text = "✨",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isFinalStep) GoogleGreen else GoogleBlue)
                        )
                    }
                    Text(
                        text = mainText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isPureChatReply) FontWeight.Normal else if (isFinalStep) FontWeight.SemiBold else FontWeight.Medium,
                        color = GoogleTextPrimary,
                        maxLines = if (isPureChatReply || isFinalStep || showDetails) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isPureChatReply) {
                    Icon(
                        imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Détails",
                        tint = GoogleTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Hidden behind click (only for phone actions): Visual Flow + Observation / Thought / Action
            if (!isPureChatReply) {
                AnimatedVisibility(visible = showDetails) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(GoogleBackground)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VisualFlowPill(Icons.Default.Visibility, targetBadge, GoogleBlue, GoogleBlueContainer)
                            Text("➔", style = MaterialTheme.typography.labelSmall, color = GoogleTextMuted)
                            VisualFlowPill(Icons.Default.Psychology, "LIA", GoogleYellow, GoogleYellowContainer)
                            Text("➔", style = MaterialTheme.typography.labelSmall, color = GoogleTextMuted)
                            VisualFlowPill(Icons.Default.TouchApp, step.action.tool, GoogleGreen, GoogleGreenContainer)
                        }
                        Text(
                            text = "👁️ ${step.observation}",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoogleTextSecondary
                        )
                        Text(
                            text = "🧠 ${step.thought}",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoogleTextSecondary
                        )
                        Text(
                            text = "⚡ ${step.action.toFormattedCall()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = GoogleBlue
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualMiniPhonePreviewCard(
    screen: VirtualDeviceScreen,
    highlightedElementId: Int?,
    onSelectScreen: (String) -> Unit,
    onTapElement: (com.example.model.SomElement) -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val screens = listOf(
                        "home_launcher" to "🏠 Accueil",
                        "messages_inbox" to "💬 SMS",
                        "neobank_transfer" to "🏦 Banque",
                        "notes_editor" to "📝 Notes",
                        "settings_vpn" to "🛡️ VPN"
                    )
                    screens.forEach { (id, label) ->
                        val selected = screen.screenId == id
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) GoogleBlueContainer else GoogleSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onSelectScreen(id) }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) GoogleBlue else GoogleTextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = GoogleTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                screen.elements.forEach { el ->
                    val isHighlighted = el.id == highlightedElementId
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isHighlighted) GoogleGreenContainer else GoogleSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onTapElement(el) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "#${el.id}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isHighlighted) GoogleGreen else GoogleBlue
                            )
                            Text(
                                text = el.label.take(18),
                                style = MaterialTheme.typography.labelSmall,
                                color = GoogleTextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualFlowPill(
    icon: ImageVector,
    label: String,
    color: Color,
    container: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = container
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LiveVisualThinkingBubble(
    stage: CognitivePipelineStage
) {
    val label = when (stage) {
        CognitivePipelineStage.OBSERVING -> "LIA regarde l'écran..."
        CognitivePipelineStage.THINKING -> "LIA réfléchit..."
        CognitivePipelineStage.PLANNING_SUBGOAL,
        CognitivePipelineStage.STAGING_ACTION,
        CognitivePipelineStage.EXECUTING_ACTION -> "LIA agit..."
        else -> "LIA..."
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = GoogleSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = GoogleBlue
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = GoogleBlue
            )
        }
    }
}

@Composable
private fun VisualHitlChatBubble(
    request: PendingHitlRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, GoogleRed, RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = GoogleRed,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = request.question,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = GoogleTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hitl_reject_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = GoogleRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refuser", color = GoogleRed, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onApprove,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleGreen),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hitl_approve_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Autoriser", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
