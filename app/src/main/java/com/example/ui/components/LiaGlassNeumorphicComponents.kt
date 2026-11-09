package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.service.LiaMcpConnectorRegistry
import com.example.service.McpAppConnector
import com.example.service.McpDynamicTool
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.GoogleBlueContainer
import com.example.ui.theme.GoogleBorderSubtle
import com.example.ui.theme.GoogleGreen
import com.example.ui.theme.GoogleGreenContainer
import com.example.ui.theme.GooglePurple
import com.example.ui.theme.GooglePurpleContainer
import com.example.ui.theme.GoogleSurface
import com.example.ui.theme.GoogleSurfaceVariant
import com.example.ui.theme.GoogleTextMuted
import com.example.ui.theme.GoogleTextPrimary
import com.example.ui.theme.GoogleTextSecondary
import com.example.ui.theme.GoogleYellow

/**
 * Fond d'écran atmosphérique Glassmorphism & Soft-Neumorphism avec flou d'arrière-plan (`Modifier.blur`) :
 * Associe une couche de halos lumineux floutés (saphir, améthyste, émeraude) et un voile Material 3
 * semi-transparent pour offrir une profondeur vitrée minimaliste et épurée.
 */
@Composable
fun LiaAuroraGlassBackground(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 48.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0F16))
    ) {
        // Couche d'arrière-plan floutée (halos atmosphériques + flou gaussien)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    // Halo saphir en haut à gauche
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF4285F4).copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.16f, h * 0.10f),
                            radius = w * 0.68f
                        ),
                        center = Offset(w * 0.16f, h * 0.10f),
                        radius = w * 0.68f
                    )
                    // Halo améthyste doux à droite
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFC58AF9).copy(alpha = 0.17f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.88f, h * 0.35f),
                            radius = w * 0.62f
                        ),
                        center = Offset(w * 0.88f, h * 0.35f),
                        radius = w * 0.62f
                    )
                    // Halo émeraude subtil vers le bas
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF81C995).copy(alpha = 0.13f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.28f, h * 0.86f),
                            radius = w * 0.65f
                        ),
                        center = Offset(w * 0.28f, h * 0.86f),
                        radius = w * 0.65f
                    )
                }
        )

        // Voile vitré Material 3 semi-transparent
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0E1117).copy(alpha = 0.52f),
            tonalElevation = 2.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                content = content
            )
        }
    }
}

/**
 * Carte hybride Glassmorphism + Soft-Neumorphism avec reflet spéculaire biseauté et transitions fluides.
 */
@Composable
fun GlassNeumorphicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    accentColor: Color = GoogleBlue,
    isElevated: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val glassFill = Brush.linearGradient(
        colors = if (isElevated) {
            listOf(
                Color(0xFF232834).copy(alpha = 0.90f),
                Color(0xFF181C24).copy(alpha = 0.84f)
            )
        } else {
            listOf(
                Color(0xFF1D222C).copy(alpha = 0.82f),
                Color(0xFF141820).copy(alpha = 0.76f)
            )
        }
    )
    val neumorphicBevelBorder = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.20f),
            accentColor.copy(alpha = 0.28f),
            Color.Black.copy(alpha = 0.45f)
        )
    )

    Column(
        modifier = modifier
            .clip(shape)
            .background(brush = glassFill, shape = shape)
            .border(width = 1.dp, brush = neumorphicBevelBorder, shape = shape)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .padding(contentPadding),
        content = content
    )
}

/**
 * Pastille (Pill) tactile Glassmorphic / Neumorphic avec bordure lumineuse douce.
 */
@Composable
fun GlassNeumorphicPill(
    text: String,
    emoji: String? = null,
    selected: Boolean = false,
    accentColor: Color = GoogleBlue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    val bgBrush = if (selected) {
        Brush.horizontalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.28f),
                accentColor.copy(alpha = 0.14f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF242936).copy(alpha = 0.82f),
                Color(0xFF181C26).copy(alpha = 0.76f)
            )
        )
    }
    val borderBrush = if (selected) {
        Brush.linearGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.80f),
                Color.White.copy(alpha = 0.25f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.04f)
            )
        )
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(brush = bgBrush, shape = shape)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!emoji.isNullOrBlank()) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) accentColor else GoogleTextPrimary,
            maxLines = 1
        )
    }
}

/**
 * Barre interactive d'applications connectées sur l'écran d'accueil :
 * Permet de choisir une application (WhatsApp, Messenger, Keep, Agenda, Gmail, Maps, Spotify, etc.)
 * et de déclencher ses actions naturelles en 1 geste avec une transition fluide.
 */
@Composable
fun LiaConnectedAppsNaturalBar(
    connectors: List<McpAppConnector>,
    onExecuteNaturalCommand: (String) -> Unit,
    onExecuteMcpTool: (McpDynamicTool, Map<String, String>) -> Unit,
    onOpenAllAppsSheet: () -> Unit
) {
    var selectedConnectorId by rememberSaveable { mutableStateOf<String?>(null) }
    var naturalInputText by rememberSaveable { mutableStateOf("") }

    val featuredConnectors = remember(connectors) {
        connectors.take(8)
    }
    val activeConnector = remember(connectors, selectedConnectorId) {
        connectors.firstOrNull { it.connectorId == selectedConnectorId }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Vos applications connectées",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = GoogleTextSecondary
            )
            Text(
                text = "Tout voir (${connectors.size}) ›",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = GoogleBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onOpenAllAppsSheet() }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .testTag("open_all_mcp_connectors_link")
            )
        }

        // Carrousel horizontal de pastilles Glassmorphic pour chaque application
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            featuredConnectors.forEach { connector ->
                val isSelected = connector.connectorId == selectedConnectorId
                val accent = Color(connector.accentHex)
                GlassNeumorphicPill(
                    text = connector.appName,
                    emoji = connector.iconEmoji,
                    selected = isSelected,
                    accentColor = accent,
                    onClick = {
                        selectedConnectorId = if (isSelected) null else connector.connectorId
                        naturalInputText = ""
                    },
                    modifier = Modifier.testTag("mcp_connector_pill_${connector.connectorId}")
                )
            }

            GlassNeumorphicPill(
                text = "Toutes les applis",
                emoji = "✨",
                selected = false,
                accentColor = GooglePurple,
                onClick = onOpenAllAppsSheet,
                modifier = Modifier.testTag("mcp_connector_pill_all")
            )
        }

        // Tiroir fluide d'actions naturelles pour l'application sélectionnée
        AnimatedVisibility(
            visible = activeConnector != null,
            enter = fadeIn() + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (activeConnector != null) {
                val accent = Color(activeConnector.accentHex)
                GlassNeumorphicCard(
                    cornerRadius = 22.dp,
                    accentColor = accent,
                    isElevated = true,
                    contentPadding = PaddingValues(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_mcp_connector_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = 0.18f))
                            ) {
                                Text(
                                    text = activeConnector.iconEmoji,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = activeConnector.appName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = GoogleTextPrimary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = GoogleGreenContainer.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = activeConnector.statusLabelFr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GoogleGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = activeConnector.naturalSummaryFr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoogleTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = { selectedConnectorId = null },
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

                    Spacer(modifier = Modifier.size(10.dp))

                    // Boutons d'actions dynamiques en langage naturel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        activeConnector.tools.forEach { tool ->
                            GlassNeumorphicPill(
                                text = tool.titleFr,
                                emoji = tool.emoji,
                                selected = false,
                                accentColor = accent,
                                onClick = {
                                    if (naturalInputText.isNotBlank()) {
                                        val paramMap = tool.parameters.associate {
                                            it.name to naturalInputText.trim()
                                        }
                                        onExecuteMcpTool(tool, paramMap)
                                        naturalInputText = ""
                                    } else {
                                        onExecuteNaturalCommand(tool.sampleNaturalPhrase)
                                    }
                                },
                                modifier = Modifier.testTag("mcp_tool_chip_${tool.toolId}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.size(10.dp))

                    // Champ naturel direct pour personnaliser l'action dans l'application choisie
                    val firstParamHint = activeConnector.tools
                        .flatMap { it.parameters }
                        .firstOrNull()?.placeholderFr
                        ?: "Que voulez-vous faire dans ${activeConnector.appName} ?"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = naturalInputText,
                            onValueChange = { naturalInputText = it },
                            placeholder = {
                                Text(
                                    text = firstParamHint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoogleTextMuted
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = GoogleTextPrimary),
                            shape = RoundedCornerShape(50),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                unfocusedBorderColor = GoogleBorderSubtle,
                                focusedContainerColor = Color(0xFF11151E),
                                unfocusedContainerColor = Color(0xFF11151E)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mcp_connector_natural_input")
                        )

                        IconButton(
                            onClick = {
                                val primaryTool = activeConnector.tools.firstOrNull()
                                if (primaryTool != null && naturalInputText.isNotBlank()) {
                                    val args = primaryTool.parameters.associate {
                                        it.name to naturalInputText.trim()
                                    } + mapOf(
                                        "content" to naturalInputText.trim(),
                                        "message" to naturalInputText.trim(),
                                        "query" to naturalInputText.trim(),
                                        "title" to naturalInputText.trim(),
                                        "destination" to naturalInputText.trim()
                                    )
                                    onExecuteMcpTool(primaryTool, args)
                                    naturalInputText = ""
                                } else if (primaryTool != null) {
                                    onExecuteNaturalCommand(primaryTool.sampleNaturalPhrase)
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accent)
                                .testTag("mcp_connector_execute_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Lancer l'action",
                                tint = Color(0xFF0B0E14),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fenêtre complète « Mes Applications & Services Connectés » en design Glassmorphic :
 * Affiche toutes les applications détectées sur l'appareil (WhatsApp, Messenger, Keep, Services Google,
 * et toutes les applications installées) avec leurs actions dynamiques en langage naturel.
 */
@Composable
fun LiaMcpConnectorsGlassModal(
    connectors: List<McpAppConnector>,
    isGoogleSignedIn: Boolean,
    onSignInGoogle: () -> Unit,
    onRefreshConnectors: () -> Unit,
    onExecuteMcpTool: (McpDynamicTool, Map<String, String>) -> Unit,
    onExecuteNaturalCommand: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("Toutes") }
    var expandedConnectorId by rememberSaveable { mutableStateOf<String?>(connectors.firstOrNull()?.connectorId) }
    var customInputByConnector by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val categories = remember {
        listOf("Toutes", "Messagerie", "Notes & Idées", "Services Google", "Médias & Musique", "Applications installées")
    }

    val filteredConnectors = remember(connectors, searchQuery, selectedCategory) {
        connectors.filter { c ->
            val matchesCat = selectedCategory == "Toutes" || c.categoryFr.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                c.appName.contains(searchQuery, ignoreCase = true) ||
                c.naturalSummaryFr.contains(searchQuery, ignoreCase = true) ||
                c.tools.any { it.titleFr.contains(searchQuery, ignoreCase = true) }
            matchesCat && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LiaAuroraGlassBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 20.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Applications & Services Connectés",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoogleTextPrimary
                        )
                        Text(
                            text = "${connectors.size} applications détectées • Actions directes en langage naturel",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoogleTextSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = onRefreshConnectors,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GoogleSurfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualiser les applications",
                                tint = GoogleBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GoogleSurfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = GoogleTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Barre de recherche fluide + Connexion Google
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = GoogleTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        placeholder = {
                            Text(
                                "Chercher WhatsApp, Keep, Messenger, Agenda…",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoogleTextMuted
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = GoogleTextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoogleBlue,
                            unfocusedBorderColor = GoogleBorderSubtle,
                            focusedContainerColor = Color(0xFF141822),
                            unfocusedContainerColor = Color(0xFF141822)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    GlassNeumorphicPill(
                        text = if (isGoogleSignedIn) "Google actif" else "Compte Google",
                        emoji = if (isGoogleSignedIn) "✓" else "🔑",
                        selected = isGoogleSignedIn,
                        accentColor = if (isGoogleSignedIn) GoogleGreen else GoogleBlue,
                        onClick = onSignInGoogle
                    )
                }

                // Filtres par catégorie
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        GlassNeumorphicPill(
                            text = cat,
                            selected = selectedCategory == cat,
                            accentColor = GoogleBlue,
                            onClick = { selectedCategory = cat }
                        )
                    }
                }

                // Liste des connecteurs d'applications
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(
                        items = filteredConnectors,
                        key = { it.connectorId }
                    ) { connector ->
                        val isExpanded = expandedConnectorId == connector.connectorId
                        val accent = Color(connector.accentHex)
                        val inputVal = customInputByConnector[connector.connectorId].orEmpty()

                        GlassNeumorphicCard(
                            cornerRadius = 22.dp,
                            accentColor = accent,
                            isElevated = isExpanded,
                            contentPadding = PaddingValues(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedConnectorId = if (isExpanded) null else connector.connectorId
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(accent.copy(alpha = 0.18f))
                                    ) {
                                        Text(
                                            text = connector.iconEmoji,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = connector.appName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = GoogleTextPrimary
                                        )
                                        Text(
                                            text = connector.naturalSummaryFr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GoogleTextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = GoogleGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${connector.tools.size} actions",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accent
                                    )
                                }
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        connector.tools.forEach { tool ->
                                            GlassNeumorphicPill(
                                                text = tool.titleFr,
                                                emoji = tool.emoji,
                                                selected = false,
                                                accentColor = accent,
                                                onClick = {
                                                    if (inputVal.isNotBlank()) {
                                                        val params = tool.parameters.associate {
                                                            it.name to inputVal.trim()
                                                        } + mapOf(
                                                            "content" to inputVal.trim(),
                                                            "message" to inputVal.trim(),
                                                            "query" to inputVal.trim(),
                                                            "title" to inputVal.trim()
                                                        )
                                                        onExecuteMcpTool(tool, params)
                                                        onDismiss()
                                                    } else {
                                                        onExecuteNaturalCommand(tool.sampleNaturalPhrase)
                                                        onDismiss()
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = inputVal,
                                            onValueChange = { newText ->
                                                customInputByConnector = customInputByConnector + (connector.connectorId to newText)
                                            },
                                            placeholder = {
                                                Text(
                                                    text = "Écrivez un message, une note ou une recherche pour ${connector.appName}…",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = GoogleTextMuted
                                                )
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(50),
                                            textStyle = MaterialTheme.typography.bodySmall.copy(color = GoogleTextPrimary),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = accent,
                                                unfocusedBorderColor = GoogleBorderSubtle,
                                                focusedContainerColor = Color(0xFF10141D),
                                                unfocusedContainerColor = Color(0xFF10141D)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )

                                        IconButton(
                                            onClick = {
                                                val primaryTool = connector.tools.firstOrNull()
                                                if (primaryTool != null) {
                                                    if (inputVal.isNotBlank()) {
                                                        val params = primaryTool.parameters.associate {
                                                            it.name to inputVal.trim()
                                                        } + mapOf(
                                                            "content" to inputVal.trim(),
                                                            "message" to inputVal.trim(),
                                                            "query" to inputVal.trim(),
                                                            "title" to inputVal.trim()
                                                        )
                                                        onExecuteMcpTool(primaryTool, params)
                                                    } else {
                                                        onExecuteNaturalCommand(primaryTool.sampleNaturalPhrase)
                                                    }
                                                    onDismiss()
                                                }
                                            },
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(accent)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Lancer",
                                                tint = Color(0xFF0D1017),
                                                modifier = Modifier.size(17.dp)
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
