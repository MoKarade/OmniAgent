package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.window.Dialog
import com.example.model.AgentOperationalStatus
import com.example.model.AutonomousAgentInstance
import com.example.model.ScopedAccessToken
import com.example.ui.theme.ActionEmerald
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.McpViolet
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.OmniCyan
import com.example.ui.theme.SecurityCrimson
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThoughtAmber

/**
 * Visual, low-text Centralized Agent Manager UI Component:
 * - Instant switching between active agent instances using visual avatar cards
 * - Displays real-time operational status (Idle, Thinking, Executing, Error)
 * - 1-tap access to Scoped Token Permissions & Parallel Fleet execution
 */
@Composable
fun CentralizedAgentManagerBar(
    agents: List<AutonomousAgentInstance>,
    activeAgentId: String,
    agentTokens: Map<String, ScopedAccessToken>,
    isFriendlyMode: Boolean,
    isBusy: Boolean,
    onSelectAgent: (String) -> Unit,
    onCreateAgent: (name: String, role: String, model: String, mission: String) -> Unit,
    onRunAllAgentsParallel: () -> Unit,
    onToggleFriendlyMode: () -> Unit,
    onOpenAgentPermissions: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showQuickAddDialog by remember { mutableStateOf(false) }
    val activeAgent = agents.find { it.id == activeAgentId } ?: agents.first()
    val activeToken = agentTokens[activeAgent.id]

    if (showQuickAddDialog) {
        QuickSpawnAgentModal(
            onDismiss = { showQuickAddDialog = false },
            onConfirm = { name, role, model, mission ->
                onCreateAgent(name, role, model, mission)
                showQuickAddDialog = false
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OmniCyan.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .testTag("centralized_agent_manager_bar"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Minimal Header: Title + Parallel Action + Mode Toggle Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = OmniCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Assistants",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = OmniCyan.copy(alpha = 0.16f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "${agents.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = OmniCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Token / Access shortcut pill
                    if (activeToken != null) {
                        Surface(
                            onClick = { onOpenAgentPermissions(activeAgent.id) },
                            color = if (activeToken.isRevoked) {
                                SecurityCrimson.copy(alpha = 0.18f)
                            } else {
                                McpViolet.copy(alpha = 0.18f)
                            },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                if (activeToken.isRevoked) SecurityCrimson else McpViolet.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.testTag("active_agent_token_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = "Permissions",
                                    tint = if (activeToken.isRevoked) SecurityCrimson else McpViolet,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (activeToken.isRevoked) {
                                        "Bloqué"
                                    } else {
                                        "${activeToken.allowedControlPackages.size} apps"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Parallel execution icon button
                    Surface(
                        onClick = { if (!isBusy) onRunAllAgentsParallel() },
                        color = ActionEmerald.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, ActionEmerald.copy(alpha = 0.6f)),
                        modifier = Modifier.testTag("central_manager_parallel_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Lancer tous",
                                tint = ActionEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Tous",
                                style = MaterialTheme.typography.labelSmall,
                                color = ActionEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Simple vs Expert icon toggle
                    IconButton(
                        onClick = onToggleFriendlyMode,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("toggle_friendly_mode_chip")
                    ) {
                        Icon(
                            imageVector = if (isFriendlyMode) Icons.Default.AutoAwesome else Icons.Default.Code,
                            contentDescription = "Changer de vue",
                            tint = if (isFriendlyMode) OmniCyan else ThoughtAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Visual Horizontal Agent Switcher Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                agents.forEach { agent ->
                    VisualAgentTile(
                        agent = agent,
                        isSelected = agent.id == activeAgentId,
                        onClick = { onSelectAgent(agent.id) }
                    )
                }

                // Add Agent circular/tile button
                Surface(
                    onClick = { showQuickAddDialog = true },
                    color = ObsidianSurfaceVariant,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .height(76.dp)
                        .width(68.dp)
                        .testTag("central_manager_add_agent_button")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Nouvel assistant",
                            tint = OmniCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Créer",
                            style = MaterialTheme.typography.labelSmall,
                            color = OmniCyan
                        )
                    }
                }
            }

            // Active progress bar only when working
            if (activeAgent.status == AgentOperationalStatus.OBSERVING ||
                activeAgent.status == AgentOperationalStatus.THINKING ||
                activeAgent.status == AgentOperationalStatus.EXECUTING
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = statusAccentColor(activeAgent.status),
                    trackColor = ObsidianSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VisualAgentTile(
    agent: AutonomousAgentInstance,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = statusAccentColor(agent.status),
        label = "agentTileColor"
    )
    val shortName = when {
        agent.id == "agent_alpha" -> "Alpha"
        agent.id == "agent_beta" -> "Beta"
        agent.id == "agent_gamma" -> "Gamma"
        else -> agent.name.take(10)
    }
    val avatarIcon: ImageVector = when (agent.id) {
        "agent_alpha" -> Icons.Default.SmartToy
        "agent_beta" -> Icons.Default.MarkChatRead
        "agent_gamma" -> Icons.Default.AccountBalanceWallet
        else -> Icons.Default.AutoAwesome
    }

    Surface(
        color = if (isSelected) OmniCyan.copy(alpha = 0.16f) else ObsidianSurfaceVariant,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) OmniCyan else BorderSubtle
        ),
        modifier = Modifier
            .height(76.dp)
            .width(142.dp)
            .clickable(onClick = onClick)
            .testTag("central_agent_instance_${agent.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Avatar with live status dot
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    color = statusColor.copy(alpha = 0.2f),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = avatarIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                        .border(1.5.dp, ObsidianSurface, CircleShape)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = shortName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    color = statusColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = agent.status.standardizedCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadgePill(
    status: AgentOperationalStatus,
    isFriendlyMode: Boolean
) {
    val color = statusAccentColor(status)
    val icon = when (status) {
        AgentOperationalStatus.IDLE, AgentOperationalStatus.COMPLETED -> Icons.Default.CheckCircle
        AgentOperationalStatus.OBSERVING, AgentOperationalStatus.THINKING -> Icons.Default.Psychology
        AgentOperationalStatus.EXECUTING -> Icons.Default.PlayArrow
        AgentOperationalStatus.WAITING_HITL -> Icons.Default.Security
        AgentOperationalStatus.PAUSED_SECURITY, AgentOperationalStatus.ERROR -> Icons.Default.ErrorOutline
    }
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                text = if (isFriendlyMode) status.frenchLabel else status.standardizedCode,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                fontFamily = JetBrainsMonoFontFamily
            )
        }
    }
}

private fun statusAccentColor(status: AgentOperationalStatus): Color = when (status) {
    AgentOperationalStatus.IDLE -> OmniCyan
    AgentOperationalStatus.OBSERVING, AgentOperationalStatus.THINKING -> ThoughtAmber
    AgentOperationalStatus.EXECUTING, AgentOperationalStatus.COMPLETED -> ActionEmerald
    AgentOperationalStatus.WAITING_HITL -> ThoughtAmber
    AgentOperationalStatus.PAUSED_SECURITY, AgentOperationalStatus.ERROR -> SecurityCrimson
}

@Composable
private fun QuickSpawnAgentModal(
    onDismiss: () -> Unit,
    onConfirm: (name: String, role: String, model: String, mission: String) -> Unit
) {
    var name by remember { mutableStateOf("Assistant Perso") }
    var role by remember { mutableStateOf("Polyvalent") }
    var selectedModel by remember { mutableStateOf("gemini-4-argon") }
    var mission by remember {
        mutableStateOf("Vérifie mes prochains rendez-vous dans l'agenda.")
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, OmniCyan, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Nouvel Assistant",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OmniCyan,
                        unfocusedBorderColor = BorderSubtle
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("gemini-4-argon", "gemini-3.8-flash", "gemini-3.1-flash-lite").forEach { model ->
                        FilterChip(
                            selected = selectedModel == model,
                            onClick = { selectedModel = model },
                            label = { Text(model.removePrefix("gemini-"), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = mission,
                    onValueChange = { mission = it },
                    label = { Text("Mission par défaut") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OmniCyan,
                        unfocusedBorderColor = BorderSubtle
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Annuler")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && mission.isNotBlank()) {
                                onConfirm(name.trim(), role.trim(), selectedModel, mission.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OmniCyan,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Créer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
