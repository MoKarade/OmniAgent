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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppAccessPermission
import com.example.model.AutonomousAgentInstance
import com.example.model.GlobalAccessPolicyMode
import com.example.model.ScopedAccessToken
import com.example.model.SystemServiceType
import com.example.security.ChainedSecurityAuditBlock
import com.example.security.SoniaToolType
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

private fun permissionAgentEmoji(agentId: String): String = when (agentId) {
    "agent_alpha" -> "🤖"
    "agent_beta" -> "💬"
    "agent_gamma" -> "🏦"
    else -> "✨"
}

private fun permissionAgentShortName(name: String): String = when {
    name.contains("Alpha", ignoreCase = true) -> "Alpha"
    name.contains("Beta", ignoreCase = true) -> "Beta"
    name.contains("Gamma", ignoreCase = true) -> "Gamma"
    else -> name.take(10)
}

private fun appEmojiForPackage(pkg: String): String = when {
    pkg.contains("nexuslauncher", ignoreCase = true) -> "🏠"
    pkg.contains("messaging", ignoreCase = true) -> "💬"
    pkg.contains("neobank", ignoreCase = true) || pkg.contains("bank", ignoreCase = true) -> "🏦"
    pkg.contains("keep", ignoreCase = true) || pkg.contains("note", ignoreCase = true) -> "📝"
    pkg.contains("settings", ignoreCase = true) -> "🛡️"
    pkg.contains("calendar", ignoreCase = true) -> "📅"
    pkg.contains("gm", ignoreCase = true) || pkg.contains("mail", ignoreCase = true) -> "✉️"
    pkg.contains("chrome", ignoreCase = true) -> "🌐"
    else -> "📱"
}

/**
 * Tab 2: "Autorisations" — Dark Google minimalist style with Maximum Security Center.
 * All advanced controls (Zero-Trust Security Vault + AES-256 + SHA-256 Audit Chain + PIN Lock,
 * 10 Sonia Tools permissions, System Services, and per-app granular toggles) are tucked behind clicks.
 */
@Composable
fun AppAccessControlScreen(
    uiState: OmniAgentUiState,
    viewModel: OmniAgentViewModel,
    onInspectAppInConsole: (String) -> Unit
) {
    val selectedAgent = uiState.selectedPermissionAgent
    val selectedToken = uiState.selectedPermissionAgentToken
    val allowedTools = uiState.selectedAgentAllowedTools

    var showSecurityFortress by rememberSaveable { mutableStateOf(false) }
    var showToolsGovernance by rememberSaveable { mutableStateOf(false) }
    var showAdvancedServices by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GoogleBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 3 ULTRA-CLEAN MASTER PILLS (Tout / Lecture / Bloquer)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VisualMasterModeCard(
                    icon = Icons.Default.CheckCircle,
                    title = "Tout",
                    accentColor = GoogleGreen,
                    containerColor = GoogleGreenContainer,
                    isSelected = uiState.globalAccessPolicy == GlobalAccessPolicyMode.FULL_AUTONOMOUS_ACCESS,
                    onClick = { viewModel.setGlobalAccessPolicy(GlobalAccessPolicyMode.FULL_AUTONOMOUS_ACCESS) },
                    testTag = "grant_all_apps_button",
                    modifier = Modifier.weight(1f)
                )
                VisualMasterModeCard(
                    icon = Icons.Default.Visibility,
                    title = "Lecture",
                    accentColor = GoogleBlue,
                    containerColor = GoogleBlueContainer,
                    isSelected = uiState.globalAccessPolicy == GlobalAccessPolicyMode.OBSERVATION_ONLY,
                    onClick = { viewModel.setGlobalAccessPolicy(GlobalAccessPolicyMode.OBSERVATION_ONLY) },
                    testTag = "read_only_all_apps_button",
                    modifier = Modifier.weight(1f)
                )
                VisualMasterModeCard(
                    icon = Icons.Default.Block,
                    title = "Bloquer",
                    accentColor = GoogleRed,
                    containerColor = GoogleRedContainer,
                    isSelected = uiState.globalAccessPolicy == GlobalAccessPolicyMode.LOCKDOWN,
                    onClick = { viewModel.setGlobalAccessPolicy(GlobalAccessPolicyMode.LOCKDOWN) },
                    testTag = "revoke_all_apps_button",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. EXPANDABLE FORTRESS SECURITY CENTER (AES-256 Keystore • Pare-feu DLP • Verrou PIN • Chaîne SHA-256)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GoogleSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            1.dp,
                            if (uiState.isVaultPinLocked) GoogleRed else GoogleGreen.copy(alpha = 0.35f),
                            RoundedCornerShape(18.dp)
                        )
                        .clickable { showSecurityFortress = !showSecurityFortress }
                        .testTag("toggle_security_fortress_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isVaultPinLocked) Icons.Default.Lock else Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = if (uiState.isVaultPinLocked) GoogleRed else GoogleGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Bouclier Zero-Trust & Chiffrement AES-256",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GoogleTextPrimary
                                )
                                Text(
                                    text = if (uiState.isVaultPinLocked)
                                        "Verrou PIN actif • ${uiState.auditBlockchain.size} blocs SHA-256"
                                    else
                                        "Keystore Matériel OK • DLP (${uiState.dlpBlockedCount} menaces bloquées)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoogleTextSecondary
                                )
                            }
                        }
                        Icon(
                            imageVector = if (showSecurityFortress) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = GoogleTextSecondary
                        )
                    }
                }

                AnimatedVisibility(visible = showSecurityFortress) {
                    MaximumSecurityFortressPanel(
                        isPinLocked = uiState.isVaultPinLocked,
                        pinCodeHint = uiState.securityPinCode,
                        dlpBlockedCount = uiState.dlpBlockedCount,
                        auditBlocks = uiState.auditBlockchain,
                        onTogglePinLock = { viewModel.setVaultPinLocked(!uiState.isVaultPinLocked) },
                        onVerifyPin = viewModel::verifySecurityPin
                    )
                }
            }
        }

        // 3. EXPANDABLE 10 SONIA TOOLS GOVERNANCE GRID (Hidden behind click!)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GoogleSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { showToolsGovernance = !showToolsGovernance }
                        .testTag("toggle_tools_governance_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = GooglePurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Outils Sonia autorisés (${allowedTools.size}/${SoniaToolType.entries.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = GoogleTextPrimary
                            )
                        }
                        Icon(
                            imageVector = if (showToolsGovernance) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = GoogleTextSecondary
                        )
                    }
                }

                AnimatedVisibility(visible = showToolsGovernance) {
                    SoniaToolsPermissionGrid(
                        allowedTools = allowedTools,
                        onToggleTool = { tool ->
                            viewModel.toggleAgentToolPermission(selectedAgent.id, tool)
                        },
                        onSetAllTools = { allowAll ->
                            viewModel.setAllToolsForAgent(selectedAgent.id, allowAll)
                        }
                    )
                }
            }
        }

        // 4. COLLAPSIBLE ADVANCED SYSTEM SERVICES & TOKEN PILL (Hidden behind click!)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GoogleSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { showAdvancedServices = !showAdvancedServices }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = GoogleBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Services système & Jeton (${permissionAgentShortName(selectedAgent.name)})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = GoogleTextPrimary
                            )
                        }
                        Icon(
                            imageVector = if (showAdvancedServices) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = GoogleTextSecondary
                        )
                    }
                }

                AnimatedVisibility(visible = showAdvancedServices) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        VisualAgentAndTokenBar(
                            agents = uiState.agents,
                            selectedAgent = selectedAgent,
                            token = selectedToken,
                            onSelectAgent = viewModel::selectPermissionTargetAgent,
                            onRotateToken = { viewModel.rotateAgentAccessToken(selectedAgent.id) },
                            onToggleRevokeToken = {
                                viewModel.setAgentTokenRevoked(
                                    selectedAgent.id,
                                    !selectedToken.isRevoked
                                )
                            }
                        )
                        VisualSystemServicesGrid(
                            token = selectedToken,
                            onToggleService = { service ->
                                viewModel.toggleAgentSystemService(selectedAgent.id, service)
                            }
                        )
                    }
                }
            }
        }

        // 5. MINIMALIST APP ROWS (Click an app row to reveal its 3 granular toggles!)
        items(
            items = uiState.appPermissions,
            key = { it.packageName }
        ) { permission ->
            val agentCanSee = selectedToken.allowedScreenPackages.contains(permission.packageName)
            val agentCanControl = selectedToken.allowedControlPackages.contains(permission.packageName)

            VisualAppPermissionRow(
                permission = permission,
                agentCanSee = agentCanSee,
                agentCanControl = agentCanControl,
                onToggleSee = {
                    viewModel.toggleAgentAppScreenScope(selectedAgent.id, permission.packageName)
                },
                onToggleControl = {
                    viewModel.toggleAgentAppControlScope(selectedAgent.id, permission.packageName)
                },
                onToggleHitlShield = {
                    viewModel.updateAppPermission(
                        packageName = permission.packageName,
                        requireHitlForActions = !permission.requireHitlForActions
                    )
                },
                onOpenPreview = {
                    onInspectAppInConsole(permission.linkedScreenId)
                }
            )
        }
    }
}

@Composable
private fun MaximumSecurityFortressPanel(
    isPinLocked: Boolean,
    pinCodeHint: String,
    dlpBlockedCount: Int,
    auditBlocks: List<ChainedSecurityAuditBlock>,
    onTogglePinLock: () -> Unit,
    onVerifyPin: (String) -> Boolean
) {
    var pinInput by rememberSaveable { mutableStateOf("") }
    var pinError by rememberSaveable { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 3 Security Pillars Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecurityPillarChip(
                    emoji = "🔐",
                    title = "AES-256-GCM",
                    subtitle = "Keystore Actif",
                    color = GoogleGreen,
                    modifier = Modifier.weight(1f)
                )
                SecurityPillarChip(
                    emoji = "🛡️",
                    title = "Pare-feu DLP",
                    subtitle = "$dlpBlockedCount bloquées",
                    color = GoogleBlue,
                    modifier = Modifier.weight(1f)
                )
                SecurityPillarChip(
                    emoji = "⛓️",
                    title = "Audit SHA-256",
                    subtitle = "${auditBlocks.size} blocs",
                    color = GooglePurple,
                    modifier = Modifier.weight(1f)
                )
            }

            // PIN Vault Lock / Unlock Control
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isPinLocked) GoogleRedContainer else GoogleSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPinLocked) "🔒 Session verrouillée par PIN" else "🔓 Verrouillage par Code PIN",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isPinLocked) GoogleRed else GoogleTextPrimary
                            )
                            Text(
                                text = "Code PIN de sécurité : $pinCodeHint",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoogleTextSecondary
                            )
                        }

                        if (!isPinLocked) {
                            Button(
                                onClick = onTogglePinLock,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = GoogleRed)
                            ) {
                                Text("Verrouiller", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (isPinLocked) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = {
                                    pinInput = it
                                    pinError = false
                                },
                                placeholder = { Text("Code PIN ($pinCodeHint)", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val ok = onVerifyPin(pinInput)
                                    pinError = !ok
                                    if (ok) pinInput = ""
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = GoogleGreen)
                            ) {
                                Text("Déverrouiller", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (pinError) {
                            Text(
                                text = "Code PIN incorrect (essayez $pinCodeHint)",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoogleRed
                            )
                        }
                    }
                }
            }

            // Immutable SHA-256 Hash-Chained Audit Ledger (last 4 blocks)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Chaîne d'Audit Cryptographique SHA-256",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = GoogleTextSecondary
                )
                auditBlocks.take(4).forEach { block ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GoogleBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "#${block.index} • ${block.toolName} (${block.agentName})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (block.granted) GoogleGreen else GoogleRed
                                )
                                Text(
                                    text = block.actionSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoogleTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = block.blockHash.take(8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = GoogleBlue
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityPillarChip(
    emoji: String,
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GoogleSurfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(text = emoji, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = GoogleTextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SoniaToolsPermissionGrid(
    allowedTools: Set<SoniaToolType>,
    onToggleTool: (SoniaToolType) -> Unit,
    onSetAllTools: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Activer / couper chaque outil pour cet agent",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoogleTextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = GoogleGreenContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSetAllTools(true) }
                    ) {
                        Text(
                            text = "Tout activer",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = GoogleGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            SoniaToolType.entries.chunked(2).forEach { rowTools ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowTools.forEach { tool ->
                        val enabled = allowedTools.contains(tool)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (enabled) GoogleBlueContainer else GoogleSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onToggleTool(tool) }
                                .testTag("toggle_sonia_tool_${tool.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = tool.emoji, style = MaterialTheme.typography.bodyMedium)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tool.shortLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (enabled) GoogleBlue else GoogleTextMuted,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (enabled) "Autorisé" else "Bloqué",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = if (enabled) GoogleGreen else GoogleRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualAgentAndTokenBar(
    agents: List<AutonomousAgentInstance>,
    selectedAgent: AutonomousAgentInstance,
    token: ScopedAccessToken,
    onSelectAgent: (String) -> Unit,
    onRotateToken: () -> Unit,
    onToggleRevokeToken: () -> Unit
) {
    val isRevoked = token.isRevoked

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                agents.forEach { agent ->
                    val isSelected = agent.id == selectedAgent.id
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) GoogleBlueContainer else GoogleSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSelectAgent(agent.id) }
                            .testTag("select_permission_agent_${agent.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = permissionAgentEmoji(agent.id), style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = permissionAgentShortName(agent.name),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) GoogleBlue else GoogleTextPrimary
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isRevoked) GoogleRedContainer else GoogleGreenContainer,
                modifier = Modifier.fillMaxWidth()
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Jeton",
                            tint = if (isRevoked) GoogleRed else GoogleGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isRevoked) "Bloqué" else "Jeton OK",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isRevoked) GoogleRed else GoogleGreen
                        )
                        Text(
                            text = token.sha256Signature.take(8),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = GoogleTextSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = onRotateToken,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(GoogleSurface)
                                .testTag("rotate_token_${selectedAgent.id}")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Renouveler", tint = GoogleBlue, modifier = Modifier.size(15.dp))
                        }
                        IconButton(
                            onClick = onToggleRevokeToken,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(GoogleSurface)
                                .testTag("revoke_token_${selectedAgent.id}")
                        ) {
                            Icon(
                                imageVector = if (isRevoked) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = "Verrouiller",
                                tint = if (isRevoked) GoogleGreen else GoogleRed,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualMasterModeCard(
    icon: ImageVector,
    title: String,
    accentColor: Color,
    containerColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) containerColor else GoogleSurface,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else GoogleBorderSubtle,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "  $title",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) accentColor else GoogleTextPrimary,
                maxLines = 1
            )
        }
    }
}

private data class ServiceVisualMeta(
    val service: SystemServiceType,
    val emoji: String,
    val shortName: String
)

@Composable
private fun VisualSystemServicesGrid(
    token: ScopedAccessToken,
    onToggleService: (SystemServiceType) -> Unit
) {
    val serviceItems = listOf(
        ServiceVisualMeta(SystemServiceType.SCREEN_CAPTURE_SOM, "👁️", "Écran"),
        ServiceVisualMeta(SystemServiceType.ACCESSIBILITY_GUI, "👆", "Clics"),
        ServiceVisualMeta(SystemServiceType.APP_LAUNCHER_NAVIGATION, "🚀", "Apps"),
        ServiceVisualMeta(SystemServiceType.MCP_PERSONAL_VAULT, "📇", "Contacts"),
        ServiceVisualMeta(SystemServiceType.NOTIFICATIONS_BAR, "🔔", "Alertes"),
        ServiceVisualMeta(SystemServiceType.CLIPBOARD_GUARD, "📋", "Copier")
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            serviceItems.chunked(3).forEach { rowServices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowServices.forEach { item ->
                        val enabled = token.hasSystemServiceScope(item.service)

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (enabled) GoogleBlueContainer else GoogleSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onToggleService(item.service) }
                                .testTag("toggle_service_${item.service.scopeKey}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = item.emoji, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = item.shortName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (enabled) GoogleBlue else GoogleTextMuted,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
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
private fun VisualAppPermissionRow(
    permission: AppAccessPermission,
    agentCanSee: Boolean,
    agentCanControl: Boolean,
    onToggleSee: () -> Unit,
    onToggleControl: () -> Unit,
    onToggleHitlShield: () -> Unit,
    onOpenPreview: () -> Unit
) {
    // Granular options hidden behind a click on the app row!
    var expanded by rememberSaveable(permission.packageName) { mutableStateOf(false) }
    val effectiveSee = permission.canSeeScreen && agentCanSee
    val effectiveControl = effectiveSee && permission.canInteractGui && agentCanControl

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleSurface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("app_permission_card_${permission.packageName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (effectiveSee) GoogleBlueContainer else GoogleSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = appEmojiForPackage(permission.packageName),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Text(
                        text = permission.appName.substringBefore(" ("),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = GoogleTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Minimalist status dot + expand chevron
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    effectiveControl -> GoogleGreen
                                    effectiveSee -> GoogleBlue
                                    else -> GoogleRed
                                }
                            )
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = GoogleTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Hidden behind click: the 3 visual icon toggles (👁️ Voir | 👆 Agir | 🛡️ Accord)
            AnimatedVisibility(visible = expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VisualIconTogglePill(
                        icon = if (effectiveSee) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        label = "Voir",
                        enabled = effectiveSee,
                        activeColor = GoogleBlue,
                        activeContainer = GoogleBlueContainer,
                        onClick = onToggleSee,
                        testTag = "switch_see_${permission.packageName}"
                    )

                    VisualIconTogglePill(
                        icon = Icons.Default.TouchApp,
                        label = "Agir",
                        enabled = effectiveControl,
                        activeColor = GoogleGreen,
                        activeContainer = GoogleGreenContainer,
                        onClick = onToggleControl,
                        testTag = "switch_act_${permission.packageName}"
                    )

                    VisualIconTogglePill(
                        icon = Icons.Default.Shield,
                        label = "Accord",
                        enabled = permission.requireHitlForActions,
                        activeColor = GoogleYellow,
                        activeContainer = GoogleYellowContainer,
                        onClick = onToggleHitlShield,
                        testTag = "switch_hitl_${permission.packageName}"
                    )

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = GoogleSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onOpenPreview)
                    ) {
                        Text(
                            text = "Ouvrir",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoogleTextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualIconTogglePill(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    activeColor: Color,
    activeContainer: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (enabled) activeContainer else GoogleSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) activeColor else GoogleTextMuted,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) activeColor else GoogleTextMuted,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
