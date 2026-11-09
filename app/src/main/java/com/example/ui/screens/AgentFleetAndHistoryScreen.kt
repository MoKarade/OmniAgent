package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AgentOperationalStatus
import com.example.model.AutonomousAgentInstance
import com.example.model.ScopedAccessToken
import com.example.model.SystemServiceType
import com.example.model.TaskExecutionStatus
import com.example.model.TaskHistoryEntry
import com.example.ui.theme.GoogleBackground
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.GoogleBlueContainer
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

private fun hubAgentEmoji(agentId: String): String = when (agentId) {
    "agent_alpha" -> "🧭"
    "agent_beta" -> "⚡"
    "agent_gamma" -> "🛡️"
    else -> "✨"
}

private fun hubAgentShortName(name: String): String = when {
    name.contains("Alpha", ignoreCase = true) -> "LIA • Alpha (Manager)"
    name.contains("Beta", ignoreCase = true) -> "LIA • Beta (Operator)"
    name.contains("Gamma", ignoreCase = true) -> "LIA • Gamma (Reflector)"
    else -> name.take(22)
}

/**
 * Tab 3: "Agents" — Topologie Cognitive Tri-Agent (Mobile-Agent-v2 / Mobile-Agent-E)
 * & Réflexion Multi-Échelle à 3 niveaux (MobileUse : Action / Trajectory / Global).
 */
@Composable
fun AgentFleetAndHistoryScreen(
    uiState: OmniAgentUiState,
    viewModel: OmniAgentViewModel,
    onSwitchToConsole: () -> Unit,
    onOpenAgentPermissions: (String) -> Unit = {}
) {
    var showArchitectureDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GoogleBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Card: Neuro-Symbolic Hybrid & Tri-Agent Topology
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = GoogleSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GoogleBlue.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                    .clickable { showArchitectureDialog = true }
                    .testTag("open_neuro_symbolic_blueprint_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🧬 Topologie Tri-Agent & Neuro-Symbolique",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = GoogleBlue
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = GoogleBlueContainer
                        ) {
                            Text(
                                text = "Inspecter (10 Modules)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = GoogleBlue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = "• Manager (Alpha) : Planification globale & Manager-RAG (sans images brutes)\n" +
                            "• Operator (Beta) : AppFunctions (<8ms), OmniParser v2 SoM & Operator-RAG\n" +
                            "• Reflector (Gamma) : Réflexion MobileUse à 3 échelles (Action / Trajectory / Global)",
                        style = MaterialTheme.typography.bodySmall,
                        color = GoogleTextSecondary
                    )
                }
            }
        }

        items(
            items = uiState.agents,
            key = { it.id }
        ) { agent ->
            val isSelected = agent.id == uiState.activeAgentId
            val agentHistory = uiState.taskHistory.filter { it.agentId == agent.id }
            val token = uiState.agentScopedTokens[agent.id] ?: uiState.activeAgentToken

            VisualGoogleAgentCard(
                agent = agent,
                token = token,
                isSelected = isSelected,
                agentHistory = agentHistory,
                onSelectAndChat = {
                    viewModel.selectActiveAgent(agent.id)
                    onSwitchToConsole()
                },
                onRunStep = {
                    viewModel.selectActiveAgent(agent.id)
                    viewModel.executeStepForSpecificAgent(agent.id)
                    onSwitchToConsole()
                },
                onRunGeneralAction = { actionPrompt ->
                    viewModel.selectActiveAgent(agent.id)
                    viewModel.executeStepForSpecificAgent(agent.id, actionPrompt)
                    onSwitchToConsole()
                },
                onOpenPermissions = {
                    onOpenAgentPermissions(agent.id)
                },
                onReplayTask = { entry ->
                    viewModel.rerunHistoricalTask(entry)
                    onSwitchToConsole()
                }
            )
        }
    }

    if (showArchitectureDialog) {
        com.example.ui.components.LiaArchitectureBlueprintDialog(
            onDismiss = { showArchitectureDialog = false }
        )
    }
}

@Composable
private fun VisualGoogleAgentCard(
    agent: AutonomousAgentInstance,
    token: ScopedAccessToken,
    isSelected: Boolean,
    agentHistory: List<TaskHistoryEntry>,
    onSelectAndChat: () -> Unit,
    onRunStep: () -> Unit,
    onRunGeneralAction: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    onReplayTask: (TaskHistoryEntry) -> Unit
) {
    // Details hidden behind click by default (active agent starts expanded)
    var expanded by rememberSaveable(agent.id) { mutableStateOf(isSelected) }

    val (statusColor, statusContainer, statusIcon) = when (agent.status) {
        AgentOperationalStatus.IDLE,
        AgentOperationalStatus.COMPLETED -> Triple(GoogleGreen, GoogleGreenContainer, Icons.Default.CheckCircle)
        AgentOperationalStatus.OBSERVING,
        AgentOperationalStatus.THINKING -> Triple(GoogleYellow, GoogleYellowContainer, Icons.Default.Psychology)
        AgentOperationalStatus.EXECUTING -> Triple(GoogleBlue, GoogleBlueContainer, Icons.Default.TouchApp)
        AgentOperationalStatus.WAITING_HITL,
        AgentOperationalStatus.PAUSED_SECURITY,
        AgentOperationalStatus.ERROR -> Triple(GoogleRed, GoogleRedContainer, Icons.Default.Warning)
    }

    val appEmoji = when (agent.currentScreenId) {
        "messages_inbox" -> "💬 SMS"
        "neobank_transfer" -> "🏦 Banque"
        "notes_editor" -> "📝 Notes"
        "settings_vpn" -> "🛡️ VPN"
        "calendar_agenda" -> "📅 Agenda"
        "gmail_client" -> "✉️ Gmail"
        "chrome_browser" -> "🌐 Chrome"
        else -> "🏠 Accueil"
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) GoogleBlue else GoogleBorderSubtle,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable { expanded = !expanded }
            .testTag("agent_card_${agent.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // MINIMALIST HEADER: Avatar + Name + Status Dot + Play/Chat Icons + Expand Chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) GoogleBlueContainer else GoogleSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = hubAgentEmoji(agent.id), style = MaterialTheme.typography.titleMedium)
                    }

                    Column {
                        Text(
                            text = hubAgentShortName(agent.name),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = GoogleTextPrimary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = agent.status.standardizedCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = GoogleTextSecondary
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onRunStep,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(GoogleGreenContainer)
                            .testTag("step_agent_${agent.id}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Exécuter", tint = GoogleGreen, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = onSelectAndChat,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(GoogleBlueContainer)
                            .testTag("focus_agent_${agent.id}")
                    ) {
                        Icon(Icons.Default.ChatBubble, contentDescription = "Chat", tint = GoogleBlue, modifier = Modifier.size(16.dp))
                    }

                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = GoogleTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // HIDDEN BEHIND CLICK: 1. Activity Flow + 2. Permissions + 3. Past Tasks
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 1. CE QU'IL FAIT (Visual 3-node strip)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = GoogleSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = appEmoji,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = GoogleTextPrimary
                            )
                            Text("➔", color = GoogleTextMuted)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(13.dp))
                                Text(
                                    text = agent.lastActionSummary.take(24),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text("➔", color = GoogleTextMuted)
                            Text(
                                text = "#${agent.stepsCompleted}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = GoogleBlue
                            )
                        }
                    }

                    // 2. SES AUTORISATIONS (Click to open Autorisations)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .clickable(onClick = onOpenPermissions),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tokenValid = !token.isRevoked
                        MiniPermissionBadge(
                            icon = Icons.Default.Key,
                            label = if (tokenValid) "Jeton" else "Bloqué",
                            enabled = tokenValid,
                            activeColor = if (tokenValid) GoogleGreen else GoogleRed,
                            activeContainer = if (tokenValid) GoogleGreenContainer else GoogleRedContainer
                        )
                        MiniPermissionBadge(
                            icon = Icons.Default.Visibility,
                            label = "Écran",
                            enabled = token.hasSystemServiceScope(SystemServiceType.SCREEN_CAPTURE_SOM),
                            activeColor = GoogleBlue,
                            activeContainer = GoogleBlueContainer
                        )
                        MiniPermissionBadge(
                            icon = Icons.Default.TouchApp,
                            label = "Clics",
                            enabled = token.hasSystemServiceScope(SystemServiceType.ACCESSIBILITY_GUI),
                            activeColor = GoogleGreen,
                            activeContainer = GoogleGreenContainer
                        )
                        MiniPermissionBadge(
                            icon = Icons.Default.Shield,
                            label = "${token.allowedScreenPackages.size} Apps",
                            enabled = token.allowedScreenPackages.isNotEmpty(),
                            activeColor = GooglePurple,
                            activeContainer = GooglePurpleContainer
                        )
                    }

                    // 2B. ACTIONS APPRISES DE VOS DEMANDES PASSÉES (RAG & RL)
                    val learnedCards = com.example.data.LiaMemoryAndHistoryStore.state.value.proactiveRagActionCards
                    if (learnedCards.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            learnedCards.forEach { card ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = GoogleBlueContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable { onRunGeneralAction(card.commandPrompt) }
                                ) {
                                    Text(
                                        text = "${card.emoji} ${card.buttonLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = GoogleBlue,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3. CE QU'IL A FAIT (Visual task history pills with ↻ replay)
                    if (agentHistory.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            agentHistory.take(4).forEach { entry ->
                                VisualHistoryBadge(
                                    entry = entry,
                                    onReplay = { onReplayTask(entry) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPermissionBadge(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    activeColor: Color,
    activeContainer: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (enabled) activeContainer else GoogleSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = label, tint = if (enabled) activeColor else GoogleTextMuted, modifier = Modifier.size(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) activeColor else GoogleTextMuted
            )
        }
    }
}

@Composable
private fun VisualHistoryBadge(
    entry: TaskHistoryEntry,
    onReplay: () -> Unit
) {
    val (color, container, statusIcon) = when (entry.status) {
        TaskExecutionStatus.SUCCESS -> Triple(GoogleGreen, GoogleGreenContainer, Icons.Default.CheckCircle)
        TaskExecutionStatus.WAITING_HITL -> Triple(GoogleYellow, GoogleYellowContainer, Icons.Default.Security)
        TaskExecutionStatus.BLOCKED_ACCESS,
        TaskExecutionStatus.FAILED -> Triple(GoogleRed, GoogleRedContainer, Icons.Default.Warning)
        TaskExecutionStatus.IN_PROGRESS -> Triple(GoogleBlue, GoogleBlueContainer, Icons.Default.Bolt)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onReplay)
            .testTag("rerun_task_${entry.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(statusIcon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Text(
                text = entry.missionPrompt.take(16) + "…",
                style = MaterialTheme.typography.labelSmall,
                color = GoogleTextPrimary,
                maxLines = 1
            )
            Icon(Icons.Default.Replay, contentDescription = "Relancer", tint = color, modifier = Modifier.size(12.dp))
        }
    }
}
