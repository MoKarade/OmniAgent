package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LiaAssistantBrain
import com.example.data.LiaMemoryAndHistoryStore
import com.example.model.GlobalAccessPolicyMode
import com.example.model.McpCategory
import com.example.model.SecurityPosture
import com.example.service.LiaMcpConnectorRegistry
import com.example.service.LiaReasoningDepthMode
import com.example.service.LiaResponseToneStyle
import com.example.service.McpAppConnector
import com.example.service.McpDebugLogEntry
import com.example.service.McpDebugLogLevel
import com.example.ui.components.GlassNeumorphicCard
import com.example.ui.components.GlassNeumorphicPill
import com.example.ui.theme.ActionEmerald
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.GoogleBlueContainer
import com.example.ui.theme.GoogleGreen
import com.example.ui.theme.GoogleGreenContainer
import com.example.ui.theme.GooglePurple
import com.example.ui.theme.GooglePurpleContainer
import com.example.ui.theme.GoogleRed
import com.example.ui.theme.GoogleRedContainer
import com.example.ui.theme.GoogleTextMuted
import com.example.ui.theme.GoogleTextPrimary
import com.example.ui.theme.GoogleTextSecondary
import com.example.ui.theme.GoogleYellow
import com.example.ui.theme.GoogleYellowContainer
import com.example.viewmodel.OmniAgentUiState
import com.example.viewmodel.OmniAgentViewModel

enum class LiaSettingsSection(
    val id: String,
    val titleFr: String,
    val subtitleFr: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    MCP_PERMISSIONS(
        id = "mcp_permissions",
        titleFr = "Outils MCP",
        subtitleFr = "Permissions par application et par action",
        icon = Icons.Default.Extension,
        accentColor = GoogleBlue
    ),
    SECURITY_VAULT(
        id = "security_vault",
        titleFr = "Sécurité & PIN",
        subtitleFr = "PIN PBKDF2, AndroidKeyStore AES-256-GCM & Pare-feu Zero-Trust",
        icon = Icons.Default.Shield,
        accentColor = ActionEmerald
    ),
    AI_PREFERENCES(
        id = "ai_preferences",
        titleFr = "Préférences IA",
        subtitleFr = "Comportement, voix, mémoire et connexions",
        icon = Icons.Default.Tune,
        accentColor = GoogleGreen
    ),
    DEBUG_LOGS(
        id = "debug_logs",
        titleFr = "Journaux",
        subtitleFr = "Traces d'exécution, mémoire et diagnostics",
        icon = Icons.Default.BugReport,
        accentColor = GooglePurple
    )
}

/**
 * Section 'Paramètres & Sécurité' isolée de l'interface principale.
 * Accessible via une icône discrète dans la barre supérieure afin de préserver le design minimaliste
 * de la console de discussion tout en offrant un contrôle complet sur :
 * 1. Les permissions des connecteurs et outils dynamiques MCP (par application et par outil)
 * 2. Le coffre-fort de sécurité Zero-Trust (Code PIN PBKDF2-HMAC-SHA256, AndroidKeyStore AES-256-GCM, Politique globale et Chaîne d'audit SHA-256)
 * 3. Les préférences de l'IA (niveau de réflexion Gemini 2026, ton, voix TTS, mémoire continue, clé API et bulle système)
 * 4. Les journaux de débogage en temps réel (MCP, Fast-Path, RAG, Raisonnement IA, Sécurité)
 */
@Composable
fun LiaSettingsScreen(
    uiState: OmniAgentUiState,
    viewModel: OmniAgentViewModel,
    isUserSignedIn: Boolean,
    userEmail: String?,
    onBackToAssistant: () -> Unit,
    onOpenApiKeyDialog: () -> Unit,
    onOpenAuthScreen: () -> Unit,
    onSignOut: () -> Unit,
    onTriggerSystemBubble: () -> Unit
) {
    val mcpState by LiaMcpConnectorRegistry.state.collectAsStateWithLifecycle()
    val liaMemory by LiaMemoryAndHistoryStore.state.collectAsStateWithLifecycle()
    var activeSection by rememberSaveable { mutableStateOf(LiaSettingsSection.MCP_PERMISSIONS) }

    val ragSummaryText = remember(liaMemory.lastRagOutcome, liaMemory.souvenirs.size) {
        val matches = liaMemory.lastRagOutcome?.retrievedMatches.orEmpty()
        if (matches.isNotEmpty()) {
            matches.take(3).joinToString(" • ") { "${it.souvenir.title} (${it.similarityPercent}%)" }
        } else {
            "${liaMemory.souvenirs.size} souvenirs personnels indexés en base locale"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("lia_isolated_settings_screen")
    ) {
        // En-tête épuré de la section Paramètres + sélecteur de sous-section Material 3 semi-transparent
        GlassNeumorphicCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp),
            cornerRadius = 24.dp,
            accentColor = activeSection.accentColor
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF232836).copy(alpha = 0.85f),
                            modifier = Modifier
                                .size(38.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                        ) {
                            IconButton(
                                onClick = onBackToAssistant,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("settings_back_to_assistant_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Retour à l'assistant",
                                    tint = GoogleTextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Paramètres & Sécurité",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GoogleTextPrimary
                            )
                            Text(
                                text = activeSection.subtitleFr,
                                style = MaterialTheme.typography.bodySmall,
                                color = GoogleTextSecondary
                            )
                        }
                    }

                    // Compteur synthétique des outils MCP actifs
                    val totalTools = mcpState.connectors.sumOf { it.tools.size }
                    val enabledTools = mcpState.connectors.sumOf { connector ->
                        if (!mcpState.isConnectorAllowed(connector.connectorId)) {
                            0
                        } else {
                            connector.tools.count { mcpState.isToolAllowed(connector.connectorId, it.toolId) }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = GoogleBlueContainer.copy(alpha = 0.65f),
                        modifier = Modifier.border(1.dp, GoogleBlue.copy(alpha = 0.4f), RoundedCornerShape(50))
                    ) {
                        Text(
                            text = "$enabledTools/$totalTools outils actifs",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = GoogleBlue,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Barre d'onglets interne Material 3 semi-transparente
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiaSettingsSection.entries.forEach { section ->
                        val selected = activeSection == section
                        val tabBg by animateColorAsState(
                            targetValue = if (selected) {
                                section.accentColor.copy(alpha = 0.22f)
                            } else {
                                Color(0xFF1F2432).copy(alpha = 0.68f)
                            },
                            animationSpec = tween(200),
                            label = "settingsTabBg"
                        )
                        val tabBorder by animateColorAsState(
                            targetValue = if (selected) {
                                section.accentColor.copy(alpha = 0.68f)
                            } else {
                                Color.White.copy(alpha = 0.10f)
                            },
                            animationSpec = tween(200),
                            label = "settingsTabBorder"
                        )

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = tabBg,
                            tonalElevation = if (selected) 4.dp else 0.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, tabBorder, RoundedCornerShape(16.dp))
                                .clickable { activeSection = section }
                                .testTag("settings_tab_${section.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = section.icon,
                                    contentDescription = section.titleFr,
                                    tint = if (selected) section.accentColor else GoogleTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(
                                    text = section.titleFr,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) GoogleTextPrimary else GoogleTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Contenu animé de la sous-section sélectionnée
        AnimatedContent(
            targetState = activeSection,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                val slideIn = slideInHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) { width -> if (forward) width / 8 else -width / 8 }
                val slideOut = slideOutHorizontally(
                    animationSpec = tween(170)
                ) { width -> if (forward) -width / 10 else width / 10 }
                (fadeIn(tween(220)) + slideIn).togetherWith(fadeOut(tween(160)) + slideOut)
            },
            label = "SettingsSubSectionTransition",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { section ->
            when (section) {
                LiaSettingsSection.MCP_PERMISSIONS -> McpPermissionsSectionContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    connectors = mcpState.connectors,
                    disabledConnectorIds = mcpState.disabledConnectorIds,
                    disabledToolIds = mcpState.disabledToolIds,
                    confirmationRequiredToolIds = mcpState.confirmationRequiredToolIds
                )

                LiaSettingsSection.SECURITY_VAULT -> SecurityVaultSectionContent(
                    uiState = uiState,
                    viewModel = viewModel
                )

                LiaSettingsSection.DEBUG_LOGS -> DebugLogsSectionContent(
                    uiState = uiState,
                    mcpDebugLogs = mcpState.debugLogs,
                    lastRagDiagnosticSummary = ragSummaryText
                )

                LiaSettingsSection.AI_PREFERENCES -> AiPreferencesSectionContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    aiPreferences = mcpState.aiPreferences,
                    souvenirsCount = liaMemory.souvenirs.size + liaMemory.longTermMemories.size,
                    learnedShortcutsCount = liaMemory.learnedShortcuts.size,
                    isUserSignedIn = isUserSignedIn,
                    userEmail = userEmail,
                    onOpenApiKeyDialog = onOpenApiKeyDialog,
                    onOpenAuthScreen = onOpenAuthScreen,
                    onSignOut = onSignOut,
                    onTriggerSystemBubble = onTriggerSystemBubble
                )
            }
        }
    }
}

@Composable
private fun McpPermissionsSectionContent(
    uiState: OmniAgentUiState,
    viewModel: OmniAgentViewModel,
    connectors: List<McpAppConnector>,
    disabledConnectorIds: Set<String>,
    disabledToolIds: Set<String>,
    confirmationRequiredToolIds: Set<String>
) {
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("Toutes") }
    val expandedConnectors = remember { mutableStateMapOf<String, Boolean>() }

    val categories = remember(connectors) {
        listOf("Toutes") + connectors.map { it.categoryFr }.distinct()
    }

    val filteredConnectors = remember(connectors, searchQuery, selectedCategory) {
        connectors.filter { connector ->
            val matchesCategory = selectedCategory == "Toutes" || connector.categoryFr == selectedCategory
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                connector.appName.lowercase().contains(q) ||
                connector.packageName.lowercase().contains(q) ||
                connector.tools.any {
                    it.titleFr.lowercase().contains(q) || it.toolId.lowercase().contains(q)
                }
            matchesCategory && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Politique globale & actions rapides MCP
        item {
            GlassNeumorphicCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentColor = GoogleBlue
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gouvernance des Connecteurs MCP",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = GoogleTextPrimary
                            )
                            Text(
                                text = "Autorisez ou bloquez chaque outil dynamique par application installée.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GoogleTextSecondary
                            )
                        }

                        IconButton(
                            onClick = { LiaMcpConnectorRegistry.refreshConnectors(context) },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("mcp_refresh_connectors_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualiser les connecteurs",
                                tint = GoogleBlue
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassNeumorphicPill(
                            text = "Tout autoriser",
                            emoji = "✅",
                            selected = disabledConnectorIds.isEmpty() && disabledToolIds.isEmpty(),
                            accentColor = GoogleGreen,
                            onClick = {
                                LiaMcpConnectorRegistry.setAllMcpPermissions(context, allowAll = true)
                                viewModel.setGlobalAccessPolicy(GlobalAccessPolicyMode.FULL_AUTONOMOUS_ACCESS)
                            },
                            modifier = Modifier.testTag("mcp_allow_all_button")
                        )

                        GlassNeumorphicPill(
                            text = "Mode lecture seule",
                            emoji = "🛡️",
                            selected = disabledToolIds.isNotEmpty(),
                            accentColor = GoogleYellow,
                            onClick = {
                                LiaMcpConnectorRegistry.setAllMcpPermissions(context, allowAll = false)
                                viewModel.setGlobalAccessPolicy(GlobalAccessPolicyMode.OBSERVATION_ONLY)
                            },
                            modifier = Modifier.testTag("mcp_read_only_mode_button")
                        )
                    }

                    // Barre de recherche des outils et applications
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Rechercher une application ou un outil MCP (ex: WhatsApp, Keep, send…)",
                                style = MaterialTheme.typography.bodySmall,
                                color = GoogleTextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = GoogleTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoogleBlue.copy(alpha = 0.65f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = Color(0xFF151922).copy(alpha = 0.72f),
                            unfocusedContainerColor = Color(0xFF151922).copy(alpha = 0.52f),
                            focusedTextColor = GoogleTextPrimary,
                            unfocusedTextColor = GoogleTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mcp_permissions_search_input")
                    )

                    // Filtres par catégorie d'application
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { category ->
                            GlassNeumorphicPill(
                                text = category,
                                selected = selectedCategory == category,
                                accentColor = GoogleBlue,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }
                }
            }
        }

        // 2. Liste des connecteurs d'applications et leurs outils dynamiques MCP
        items(filteredConnectors, key = { it.connectorId }) { connector ->
            val connectorEnabled = !disabledConnectorIds.contains(connector.connectorId)
            val isExpanded = expandedConnectors[connector.connectorId] ?: (connector.connectorId in listOf(
                "mcp.messenger",
                "mcp.whatsapp",
                "mcp.keep",
                "mcp.calendar"
            ))
            val allowedToolsCount = if (!connectorEnabled) {
                0
            } else {
                connector.tools.count { !disabledToolIds.contains(it.toolId) }
            }
            val accentColor = Color(connector.accentHex)

            GlassNeumorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mcp_connector_card_${connector.connectorId}"),
                cornerRadius = 22.dp,
                accentColor = if (connectorEnabled) accentColor else GoogleRed
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedConnectors[connector.connectorId] = !isExpanded
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = accentColor.copy(alpha = 0.18f),
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(1.dp, accentColor.copy(alpha = 0.45f), CircleShape)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = connector.iconEmoji,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = connector.appName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = GoogleTextPrimary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (connectorEnabled) {
                                            GoogleGreenContainer.copy(alpha = 0.65f)
                                        } else {
                                            GoogleRedContainer.copy(alpha = 0.65f)
                                        }
                                    ) {
                                        Text(
                                            text = "$allowedToolsCount/${connector.tools.size} outils",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (connectorEnabled) GoogleGreen else GoogleRed,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${connector.categoryFr} • ${connector.packageName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoogleTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Switch(
                                checked = connectorEnabled,
                                onCheckedChange = { enabled ->
                                    LiaMcpConnectorRegistry.setConnectorPermission(
                                        context = context,
                                        connectorId = connector.connectorId,
                                        enabled = enabled
                                    )
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = GoogleBlue,
                                    uncheckedThumbColor = GoogleTextMuted,
                                    uncheckedTrackColor = Color(0xFF252A38)
                                ),
                                modifier = Modifier.testTag("mcp_connector_switch_${connector.connectorId}")
                            )
                            IconButton(
                                onClick = { expandedConnectors[connector.connectorId] = !isExpanded },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Afficher les outils",
                                    tint = GoogleTextSecondary
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            connector.tools.forEach { tool ->
                                val toolEnabled = connectorEnabled && !disabledToolIds.contains(tool.toolId)
                                val requiresConfirm = confirmationRequiredToolIds.contains(tool.toolId)

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF141822).copy(alpha = 0.72f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.dp,
                                            color = if (toolEnabled) {
                                                Color.White.copy(alpha = 0.10f)
                                            } else {
                                                GoogleRed.copy(alpha = 0.30f)
                                            },
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = tool.emoji,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = tool.titleFr,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (toolEnabled) GoogleTextPrimary else GoogleTextMuted
                                                    )
                                                }
                                                Text(
                                                    text = tool.naturalDescriptionFr,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = GoogleTextSecondary
                                                )
                                                Text(
                                                    text = "ID MCP : ${tool.toolId}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = GoogleTextMuted
                                                )
                                            }

                                            Switch(
                                                checked = toolEnabled,
                                                enabled = connectorEnabled,
                                                onCheckedChange = { allowed ->
                                                    LiaMcpConnectorRegistry.setToolPermission(
                                                        context = context,
                                                        toolId = tool.toolId,
                                                        enabled = allowed
                                                    )
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = ActionEmerald,
                                                    uncheckedThumbColor = GoogleTextMuted,
                                                    uncheckedTrackColor = Color(0xFF252A38)
                                                ),
                                                modifier = Modifier.testTag("mcp_tool_switch_${tool.toolId}")
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            // Option de confirmation avant exécution
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = if (requiresConfirm) {
                                                    GoogleYellowContainer.copy(alpha = 0.65f)
                                                } else {
                                                    Color(0xFF222736).copy(alpha = 0.7f)
                                                },
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .clickable {
                                                        LiaMcpConnectorRegistry.toggleToolConfirmationRequired(
                                                            context = context,
                                                            toolId = tool.toolId
                                                        )
                                                    }
                                            ) {
                                                Text(
                                                    text = if (requiresConfirm) {
                                                        "🔒 Confirmation requise"
                                                    } else {
                                                        "⚡ Exécution directe"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (requiresConfirm) GoogleYellow else GoogleTextSecondary,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }

                                            // Bouton de test direct de l'outil MCP
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = GoogleBlueContainer.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .clickable {
                                                        LiaAssistantBrain.executeDynamicMcpToolFromUi(tool, emptyMap())
                                                    }
                                                    .testTag("mcp_test_tool_${tool.toolId}")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = GoogleBlue,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "Tester",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = GoogleBlue
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
            }
        }

        // 3. Catégories de contexte MCP (Mémoire & Contexte partagé avec le modèle)
        item {
            GlassNeumorphicCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentColor = GooglePurple
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Sources de Contexte MCP Autorisées",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoogleTextPrimary
                    )
                    Text(
                        text = "Choisissez quelles catégories d'informations personnelles peuvent enrichir le contexte de LIA.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GoogleTextSecondary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        McpCategory.entries.forEach { category ->
                            val allowed = uiState.allowedMcpCategories.contains(category)
                            GlassNeumorphicPill(
                                text = category.displayName,
                                emoji = if (allowed) "✓" else "✕",
                                selected = allowed,
                                accentColor = GooglePurple,
                                onClick = { viewModel.toggleMcpCategoryAccess(category) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugLogsSectionContent(
    uiState: OmniAgentUiState,
    mcpDebugLogs: List<McpDebugLogEntry>,
    lastRagDiagnosticSummary: String?
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var selectedFilter by rememberSaveable { mutableStateOf("Tous") }
    var searchFilter by rememberSaveable { mutableStateOf("") }

    // Fusionne les journaux MCP temps réel, les étapes cognitives IA, le Fast-Path et l'audit de sécurité
    val combinedLogs = remember(
        mcpDebugLogs,
        uiState.cognitiveHistory,
        uiState.auditBlockchain,
        uiState.lastFastPathDispatch,
        lastRagDiagnosticSummary
    ) {
        val list = mutableListOf<McpDebugLogEntry>()
        list.addAll(mcpDebugLogs)

        uiState.lastFastPathDispatch?.let { fp ->
            list.add(
                McpDebugLogEntry(
                    id = "fp_${fp.taskId}_${fp.queueLatencyMs}",
                    timestamp = "Temps réel",
                    category = "FAST-PATH",
                    title = "Exécution rapide : ${fp.resolvedActionLabel}",
                    detail = "${fp.summaryMessageFr} • Cible : ${fp.targetPackageOrUri}",
                    toolOrPackageId = "dispatcher.fast_path",
                    latencyMs = fp.queueLatencyMs,
                    level = if (fp.safetyValidated) McpDebugLogLevel.SUCCESS else McpDebugLogLevel.WARNING
                )
            )
        }

        uiState.cognitiveHistory.forEach { step ->
            list.add(
                McpDebugLogEntry(
                    id = "cog_step_${step.stepIndex}_${step.durationMs}",
                    timestamp = "Étape #${step.stepIndex}",
                    category = "RAISONNEMENT-IA",
                    title = "Action ${step.action.tool} • ${step.subgoal.ifBlank { step.observation.take(45) }}",
                    detail = "Pensée : ${step.thought.take(160)} | Résultat : ${step.executionOutcome}",
                    toolOrPackageId = step.modelUsed,
                    latencyMs = step.durationMs,
                    level = McpDebugLogLevel.INFO
                )
            )
        }

        uiState.auditBlockchain.take(25).forEach { block ->
            list.add(
                McpDebugLogEntry(
                    id = "sec_${block.index}_${block.blockHash}",
                    timestamp = block.timestampFormatted,
                    category = "SÉCURITÉ",
                    title = "${block.agentName} • ${block.toolName}",
                    detail = "${block.actionSummary} (SHA-256 : ${block.blockHash})",
                    toolOrPackageId = "sonia.security.kernel",
                    latencyMs = 2L,
                    level = if (block.granted) McpDebugLogLevel.INFO else McpDebugLogLevel.ERROR
                )
            )
        }
        list
    }

    val filteredLogs = remember(combinedLogs, selectedFilter, searchFilter) {
        combinedLogs.filter { entry ->
            val matchesCategory = when (selectedFilter) {
                "Outils MCP" -> entry.category.startsWith("MCP")
                "Raisonnement IA" -> entry.category == "RAISONNEMENT-IA" || entry.category == "IA-PREFS"
                "Fast-Path" -> entry.category == "FAST-PATH"
                "Sécurité" -> entry.category == "SÉCURITÉ" || entry.level == McpDebugLogLevel.WARNING
                else -> true
            }
            val q = searchFilter.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                entry.title.lowercase().contains(q) ||
                entry.detail.lowercase().contains(q) ||
                entry.toolOrPackageId.lowercase().contains(q)
            matchesCategory && matchesSearch
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Barre de contrôle des journaux de débogage
        item {
            GlassNeumorphicCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentColor = GooglePurple
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Journaux de Débogage & Télémétrie",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = GoogleTextPrimary
                            )
                            Text(
                                text = "${filteredLogs.size} événements enregistrés • Profil prompt : ${uiState.activePromptProfile.tier.badgeLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = GoogleTextSecondary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = {
                                    val exportText = filteredLogs.joinToString("\n") {
                                        "[${it.timestamp}] [${it.category}] ${it.title} (${it.latencyMs}ms) - ${it.detail}"
                                    }
                                    clipboard.setText(AnnotatedString(exportText))
                                    LiaMcpConnectorRegistry.recordDebugLog(
                                        category = "MCP",
                                        title = "Journaux copiés dans le presse-papiers",
                                        detail = "${filteredLogs.size} lignes exportées.",
                                        toolOrPackageId = "clipboard.export",
                                        latencyMs = 2L,
                                        level = McpDebugLogLevel.INFO
                                    )
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("debug_logs_copy_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copier les journaux",
                                    tint = GoogleBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { LiaMcpConnectorRegistry.clearDebugLogs() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("debug_logs_clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Vider les journaux",
                                    tint = GoogleRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Résumé RAG / Mémoire en direct
                    if (!lastRagDiagnosticSummary.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = GooglePurpleContainer.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GooglePurple.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        ) {
                            Text(
                                text = "🧠 Contexte RAG : $lastRagDiagnosticSummary",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoogleTextPrimary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Filtres par type de journal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Tous", "Outils MCP", "Raisonnement IA", "Fast-Path", "Sécurité").forEach { filterName ->
                            GlassNeumorphicPill(
                                text = filterName,
                                selected = selectedFilter == filterName,
                                accentColor = GooglePurple,
                                onClick = { selectedFilter = filterName }
                            )
                        }

                        GlassNeumorphicPill(
                            text = "Ping diagnostic MCP",
                            emoji = "⚡",
                            selected = false,
                            accentColor = GoogleBlue,
                            onClick = {
                                val count = LiaMcpConnectorRegistry.refreshConnectors(context).size
                                LiaMcpConnectorRegistry.recordDebugLog(
                                    category = "MCP-DIAGNOSTIC",
                                    title = "Vérification des connecteurs réussie",
                                    detail = "$count connecteurs d'applications vérifiés et synchronisés.",
                                    toolOrPackageId = "mcp.healthcheck",
                                    latencyMs = 6L,
                                    level = McpDebugLogLevel.SUCCESS
                                )
                            },
                            modifier = Modifier.testTag("debug_logs_ping_button")
                        )
                    }

                    OutlinedTextField(
                        value = searchFilter,
                        onValueChange = { searchFilter = it },
                        placeholder = {
                            Text(
                                text = "Filtrer les journaux (ex: keep, whatsapp, fast_path, bloqué…)",
                                style = MaterialTheme.typography.bodySmall,
                                color = GoogleTextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = GoogleTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GooglePurple.copy(alpha = 0.65f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = Color(0xFF151922).copy(alpha = 0.72f),
                            unfocusedContainerColor = Color(0xFF151922).copy(alpha = 0.52f),
                            focusedTextColor = GoogleTextPrimary,
                            unfocusedTextColor = GoogleTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("debug_logs_search_input")
                    )
                }
            }
        }

        if (filteredLogs.isEmpty()) {
            item {
                GlassNeumorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Aucun journal pour ce filtre",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = GoogleTextPrimary
                        )
                        Text(
                            text = "Exécutez une action ou appuyez sur « Ping diagnostic MCP » pour générer une trace.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoogleTextSecondary
                        )
                    }
                }
            }
        } else {
            items(filteredLogs, key = { it.id }) { logEntry ->
                val badgeColor = when (logEntry.level) {
                    McpDebugLogLevel.SUCCESS -> GoogleGreen
                    McpDebugLogLevel.INFO -> GoogleBlue
                    McpDebugLogLevel.WARNING -> GoogleYellow
                    McpDebugLogLevel.ERROR -> GoogleRed
                }

                GlassNeumorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    accentColor = badgeColor
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = badgeColor.copy(alpha = 0.18f),
                                    modifier = Modifier.border(1.dp, badgeColor.copy(alpha = 0.45f), RoundedCornerShape(50))
                                ) {
                                    Text(
                                        text = "${logEntry.level.badgeEmoji} ${logEntry.category}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                if (logEntry.toolOrPackageId.isNotBlank()) {
                                    Text(
                                        text = logEntry.toolOrPackageId,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = GoogleTextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Text(
                                text = "${logEntry.latencyMs} ms • ${logEntry.timestamp}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = GoogleTextMuted
                            )
                        }

                        Text(
                            text = logEntry.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = GoogleTextPrimary
                        )

                        Text(
                            text = logEntry.detail,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = GoogleTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityVaultSectionContent(
    uiState: OmniAgentUiState,
    viewModel: OmniAgentViewModel
) {
    val context = LocalContext.current
    var pinInput by rememberSaveable { mutableStateOf("") }
    var pinFeedbackMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var pinFeedbackIsError by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("security_vault_section"),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Statut Matériel AndroidKeyStore AES-256-GCM & Pare-feu DLP Zero-Trust
        item {
            GlassNeumorphicCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentColor = ActionEmerald
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = ActionEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Noyau de Sécurité Zero-Trust",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoogleTextPrimary
                                )
                                Text(
                                    text = "Chiffrement matériel des clés API / OAuth2 & pare-feu anti-injection",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoogleTextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (uiState.isHardwareKeystoreActive) {
                                GoogleGreenContainer.copy(alpha = 0.65f)
                            } else {
                                GoogleYellowContainer.copy(alpha = 0.65f)
                            }
                        ) {
                            Text(
                                text = if (uiState.isHardwareKeystoreActive) "AES-256-GCM Actif" else "Keystore Standard",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isHardwareKeystoreActive) GoogleGreen else GoogleYellow,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassNeumorphicPill(
                            text = "Menaces DLP bloquées : ${uiState.dlpBlockedCount}",
                            emoji = "🛡️",
                            selected = uiState.dlpBlockedCount > 0,
                            accentColor = GoogleBlue,
                            onClick = {}
                        )
                        GlassNeumorphicPill(
                            text = if (uiState.isVaultPinLocked) "Accès Verrouillé" else "Accès Déverrouillé",
                            emoji = if (uiState.isVaultPinLocked) "🔒" else "🔓",
                            selected = uiState.isVaultPinLocked,
                            accentColor = if (uiState.isVaultPinLocked) GoogleRed else GoogleGreen,
                            onClick = {}
                        )
                    }
                }
            }
        }

        // 2. Politique Globale d'Exécution (Autonome / Observation / Verrouillage Total)
        item {
            GlassNeumorphicCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentColor = GoogleBlue
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Politique Globale d'Autorisation (Chat & Bulle Flottante)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoogleTextPrimary
                    )
                    Text(
                        text = "S'applique immédiatement à la fenêtre principale et à la bulle flottante système.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GoogleTextSecondary
                    )

                    GlobalAccessPolicyMode.entries.forEach { mode ->
                        val selected = uiState.globalAccessPolicy == mode
                        val modeColor = when (mode) {
                            GlobalAccessPolicyMode.FULL_AUTONOMOUS_ACCESS -> GoogleGreen
                            GlobalAccessPolicyMode.SELECTIVE_ZERO_TRUST -> GoogleBlue
                            GlobalAccessPolicyMode.OBSERVATION_ONLY -> GoogleYellow
                            GlobalAccessPolicyMode.LOCKDOWN -> GoogleRed
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) modeColor.copy(alpha = 0.18f) else Color(0xFF141822).copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (selected) modeColor.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { viewModel.setGlobalAccessPolicy(mode) }
                                .testTag("security_policy_${mode.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.frenchTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) modeColor else GoogleTextPrimary
                                    )
                                    Text(
                                        text = mode.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GoogleTextSecondary
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = modeColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Verrouillage par Code PIN Personnel (PBKDF2-HMAC-SHA256 salé — jamais affiché en clair)
        item {
            GlassNeumorphicCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentColor = if (uiState.isVaultPinLocked) GoogleRed else GooglePurple
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isVaultPinLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (uiState.isVaultPinLocked) GoogleRed else GooglePurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Code PIN Personnel (PBKDF2-HMAC-SHA256)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = GoogleTextPrimary
                            )
                            Text(
                                text = when {
                                    uiState.isVaultPinLocked -> "🔒 LIA est actuellement verrouillée. Entrez votre code PIN pour déverrouiller."
                                    uiState.isUserPinConfigured -> "✓ Code PIN personnel configuré (hachage salé 12 000 itérations)."
                                    else -> "Aucun code PIN défini. Créez un code PIN (4 à 12 caractères) pour protéger LIA."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = GoogleTextSecondary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 12) {
                                pinInput = it
                                pinFeedbackMessage = null
                            }
                        },
                        placeholder = {
                            Text(
                                text = if (uiState.isVaultPinLocked) {
                                    "Saisissez votre code PIN pour déverrouiller…"
                                } else if (uiState.isUserPinConfigured) {
                                    "Nouveau code PIN ou PIN actuel…"
                                } else {
                                    "Choisissez un code PIN (min. 4 chiffres)…"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = GoogleTextMuted
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GooglePurple.copy(alpha = 0.7f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                            focusedContainerColor = Color(0xFF151922).copy(alpha = 0.72f),
                            unfocusedContainerColor = Color(0xFF151922).copy(alpha = 0.52f),
                            focusedTextColor = GoogleTextPrimary,
                            unfocusedTextColor = GoogleTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("security_pin_input")
                    )

                    if (!pinFeedbackMessage.isNullOrBlank()) {
                        Text(
                            text = pinFeedbackMessage.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (pinFeedbackIsError) GoogleRed else GoogleGreen
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isVaultPinLocked) {
                            Button(
                                onClick = {
                                    val unlocked = viewModel.verifySecurityPin(context, pinInput)
                                    if (unlocked) {
                                        pinInput = ""
                                        pinFeedbackIsError = false
                                        pinFeedbackMessage = "✓ LIA déverrouillée avec succès."
                                    } else {
                                        pinFeedbackIsError = true
                                        pinFeedbackMessage = "✕ Code PIN incorrect."
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = GoogleGreen),
                                modifier = Modifier.testTag("security_unlock_pin_button")
                            ) {
                                Text("Déverrouiller LIA", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val ok = viewModel.configureOrUpdateUserPin(context, pinInput)
                                    if (ok) {
                                        pinInput = ""
                                        pinFeedbackIsError = false
                                        pinFeedbackMessage = "✓ Code PIN personnel enregistré (PBKDF2-HMAC-SHA256)."
                                    } else {
                                        pinFeedbackIsError = true
                                        pinFeedbackMessage = "⚠️ Le code PIN doit contenir entre 4 et 12 caractères."
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                                modifier = Modifier.testTag("security_save_pin_button")
                            ) {
                                Text(
                                    text = if (uiState.isUserPinConfigured) "Modifier le PIN" else "Enregistrer le PIN",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (uiState.isUserPinConfigured) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.setVaultPinLocked(context, true)
                                        pinInput = ""
                                        pinFeedbackIsError = false
                                        pinFeedbackMessage = "🔒 LIA est maintenant verrouillée."
                                    },
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("security_lock_vault_button")
                                ) {
                                    Text("Verrouiller maintenant", color = GoogleYellow, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.clearUserSecurityPin(context)
                                        pinInput = ""
                                        pinFeedbackIsError = false
                                        pinFeedbackMessage = "Code PIN supprimé."
                                    },
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("security_clear_pin_button")
                                ) {
                                    Text("Supprimer PIN", color = GoogleRed, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Chaîne d'Audit Cryptographique SHA-256 (Monotone & Thread-Safe)
        item {
            GlassNeumorphicCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentColor = GooglePurple
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Registre d'Audit SHA-256 (${uiState.auditBlockchain.size} blocs)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoogleTextPrimary
                    )
                    Text(
                        text = "Chaque décision de sécurité, déverrouillage PIN ou exécution d'action est chaînée cryptographiquement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GoogleTextSecondary
                    )

                    uiState.auditBlockchain.take(8).forEach { block ->
                        val statusColor = if (block.granted) GoogleGreen else GoogleRed
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF141822).copy(alpha = 0.72f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${block.index} • ${block.agentName} (${block.toolName})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                    Text(
                                        text = block.timestampFormatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = GoogleTextMuted
                                    )
                                }
                                Text(
                                    text = block.actionSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoogleTextPrimary
                                )
                                Text(
                                    text = "SHA-256 : ${block.previousHash.take(12)}… ➔ ${block.blockHash.take(12)}…",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = GoogleTextMuted
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
private fun AiPreferencesSectionContent(
    uiState: OmniAgentUiState,
    viewModel: OmniAgentViewModel,
    aiPreferences: com.example.service.LiaAiPreferences,
    souvenirsCount: Int,
    learnedShortcutsCount: Int,
    isUserSignedIn: Boolean,
    userEmail: String?,
    onOpenApiKeyDialog: () -> Unit,
    onOpenAuthScreen: () -> Unit,
    onSignOut: () -> Unit,
    onTriggerSystemBubble: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Niveau de réflexion & Optimisation dynamique du prompt
        item {
            GlassNeumorphicCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentColor = GoogleGreen
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = GoogleGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Calibrage Cognitif & Raisonnement (Gemini 2026)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = GoogleTextPrimary
                            )
                            Text(
                                text = "Bascule entre Gemini 3.5 Flash-Lite, Gemini 3.8 Flash et Gemini 4 Argon.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GoogleTextSecondary
                            )
                        }
                    }

                    LiaReasoningDepthMode.entries.forEach { mode ->
                        val selected = aiPreferences.reasoningDepthMode == mode
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) {
                                GoogleGreenContainer.copy(alpha = 0.55f)
                            } else {
                                Color(0xFF141822).copy(alpha = 0.65f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (selected) GoogleGreen.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    LiaMcpConnectorRegistry.updateAiPreferences(context) {
                                        it.copy(reasoningDepthMode = mode)
                                    }
                                }
                                .testTag("ai_pref_reasoning_${mode.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.labelFr,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) GoogleGreen else GoogleTextPrimary
                                    )
                                    Text(
                                        text = mode.descriptionFr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GoogleTextSecondary
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = GoogleGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Style & Ton des réponses en langage naturel
        item {
            GlassNeumorphicCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentColor = GoogleBlue
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Style de Conversation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoogleTextPrimary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiaResponseToneStyle.entries.forEach { tone ->
                            val selected = aiPreferences.responseToneStyle == tone
                            GlassNeumorphicPill(
                                text = tone.labelFr,
                                emoji = if (selected) "✨" else "•",
                                selected = selected,
                                accentColor = GoogleBlue,
                                onClick = {
                                    LiaMcpConnectorRegistry.updateAiPreferences(context) {
                                        it.copy(responseToneStyle = tone)
                                    }
                                },
                                modifier = Modifier.testTag("ai_pref_tone_${tone.name.lowercase()}")
                            )
                        }
                    }

                    Text(
                        text = aiPreferences.responseToneStyle.descriptionFr,
                        style = MaterialTheme.typography.bodySmall,
                        color = GoogleTextSecondary
                    )
                }
            }
        }

        // 3. Interrupteurs de comportement IA (Fast-Path, Mémoire RAG, Voix TTS, Sécurité SMS)
        item {
            GlassNeumorphicCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentColor = GooglePurple
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Capacités & Automatisation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoogleTextPrimary
                    )

                    AiPreferenceToggleRow(
                        title = "File d'exécution rapide (Fast-Path)",
                        subtitle = "Exécute instantanément l'ouverture d'applications et les réglages sans latence",
                        checked = aiPreferences.fastPathEnabled,
                        onCheckedChange = { enabled ->
                            LiaMcpConnectorRegistry.updateAiPreferences(context) {
                                it.copy(fastPathEnabled = enabled)
                            }
                        },
                        testTag = "ai_pref_fast_path_switch"
                    )

                    AiPreferenceToggleRow(
                        title = "Mémoire continue & Souvenirs ($souvenirsCount enregistrés)",
                        subtitle = "Retient vos préférences, contacts et habitudes ($learnedShortcutsCount raccourcis appris)",
                        checked = aiPreferences.autoMemoryRagEnabled,
                        onCheckedChange = { enabled ->
                            LiaMcpConnectorRegistry.updateAiPreferences(context) {
                                it.copy(autoMemoryRagEnabled = enabled)
                            }
                        },
                        testTag = "ai_pref_auto_memory_switch"
                    )

                    AiPreferenceToggleRow(
                        title = "Suggestions proactives sur l'accueil",
                        subtitle = "Affiche des propositions d'actions adaptées au contexte du moment",
                        checked = aiPreferences.proactiveSuggestionsEnabled,
                        onCheckedChange = { enabled ->
                            LiaMcpConnectorRegistry.updateAiPreferences(context) {
                                it.copy(proactiveSuggestionsEnabled = enabled)
                            }
                        },
                        testTag = "ai_pref_proactive_switch"
                    )

                    AiPreferenceToggleRow(
                        title = "Réponse vocale (Synthèse TTS)",
                        subtitle = "LIA lit ses réponses à haute voix en français",
                        checked = uiState.isVoiceTtsEnabled,
                        onCheckedChange = { viewModel.toggleVoiceTts() },
                        testTag = "ai_pref_voice_tts_switch"
                    )

                    AiPreferenceToggleRow(
                        title = "Confirmation avant envoi de SMS ou message",
                        subtitle = "Demande toujours validation avant d'expédier un message réel",
                        checked = aiPreferences.requireSmsConfirmation,
                        onCheckedChange = { enabled ->
                            LiaMcpConnectorRegistry.updateAiPreferences(context) {
                                it.copy(requireSmsConfirmation = enabled)
                            }
                        },
                        testTag = "ai_pref_sms_confirm_switch"
                    )

                    AiPreferenceToggleRow(
                        title = "Journalisation détaillée en temps réel",
                        subtitle = "Enregistre chaque étape MCP et RAG dans l'onglet Journaux",
                        checked = aiPreferences.captureVerboseDebugLogs,
                        onCheckedChange = { enabled ->
                            LiaMcpConnectorRegistry.updateAiPreferences(context) {
                                it.copy(captureVerboseDebugLogs = enabled)
                            }
                        },
                        testTag = "ai_pref_verbose_logs_switch"
                    )
                }
            }
        }

        // 4. Connexions, Clé API & Bulle Système
        item {
            GlassNeumorphicCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentColor = GoogleBlue
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Connexions & Système",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoogleTextPrimary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassNeumorphicPill(
                            text = "Clé API Gemini (AES-256-GCM)",
                            emoji = "🔑",
                            selected = false,
                            accentColor = GoogleYellow,
                            onClick = onOpenApiKeyDialog,
                            modifier = Modifier.testTag("settings_open_api_key_button")
                        )

                        GlassNeumorphicPill(
                            text = if (isUserSignedIn) {
                                "Google (${userEmail?.substringBefore('@') ?: "Connecté"})"
                            } else {
                                "Connecter Google"
                            },
                            emoji = if (isUserSignedIn) "☁️" else "🔗",
                            selected = isUserSignedIn,
                            accentColor = GoogleGreen,
                            onClick = { if (isUserSignedIn) onSignOut() else onOpenAuthScreen() },
                            modifier = Modifier.testTag("settings_google_account_button")
                        )

                        GlassNeumorphicPill(
                            text = "Bulle flottante",
                            emoji = "🫧",
                            selected = false,
                            accentColor = GoogleBlue,
                            onClick = onTriggerSystemBubble,
                            modifier = Modifier.testTag("settings_floating_bubble_button")
                        )

                        val isAccessActive = com.example.service.LiaAccessibilityService.isAccessibilityPermissionEnabled(context)
                        GlassNeumorphicPill(
                            text = if (isAccessActive) "Accessibilité LIA (Active ✓)" else "Activer Contrôle Total Écran",
                            emoji = "🎮",
                            selected = isAccessActive,
                            accentColor = ActionEmerald,
                            onClick = { com.example.service.LiaAccessibilityService.openAccessibilitySettings(context) },
                            modifier = Modifier.testTag("settings_accessibility_service_button")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiPreferenceToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF141822).copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = GoogleTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = GoogleTextSecondary
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = GoogleGreen,
                uncheckedThumbColor = GoogleTextMuted,
                uncheckedTrackColor = Color(0xFF252A38)
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
