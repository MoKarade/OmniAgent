package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.McpCategory
import com.example.model.McpRecord
import com.example.ui.theme.ActionEmerald
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.McpViolet
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OmniCyan
import com.example.ui.theme.SecurityCrimson
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.OmniAgentUiState
import com.example.viewmodel.OmniAgentViewModel

@Composable
fun McpVaultScreen(
    uiState: OmniAgentUiState,
    viewModel: OmniAgentViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    var selectedCategory by remember { mutableStateOf<McpCategory?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var newSubtitle by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf(McpCategory.CONTACTS) }

    val filteredRecords = remember(uiState.mcpRecords, selectedCategory) {
        if (selectedCategory == null) uiState.mcpRecords
        else uiState.mcpRecords.filter { it.category == selectedCategory }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, McpViolet.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = McpViolet
                        )
                        Text(
                            text = stringResource(R.string.section_mcp_vault),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Conformément à la Section 2.B des Instructions Système, l'agent interroge EN PRIORITÉ ces outils MCP locaux via query_personal_data(category, query) avant d'ouvrir manuellement les applications Contacts, Calendrier, Notes ou Messages.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // Live MCP Queries Executed in Current Session
        if (uiState.retrievedMcpContext.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ActionEmerald.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Journal des Requêtes MCP Actives (${uiState.retrievedMcpContext.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = ActionEmerald
                        )
                        uiState.retrievedMcpContext.forEach { entry ->
                            Text(
                                text = "• $entry",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Add new MCP personal data record
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Ajouter une Donnée Personnelle au Serveur MCP Local",
                        style = MaterialTheme.typography.titleSmall,
                        color = OmniCyan
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        McpCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = newCategory == cat,
                                onClick = { newCategory = cat },
                                label = { Text(cat.displayName) },
                                modifier = Modifier.testTag("new_mcp_cat_${cat.mcpKey}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Titre / Nom du contact ou événement") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mcp_new_title_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newSubtitle,
                        onValueChange = { newSubtitle = it },
                        label = { Text("Sous-titre (ex: Numéro, Date/Heure)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mcp_new_subtitle_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newContent,
                        onValueChange = { newContent = it },
                        label = { Text("Contenu détaillé retourné par query_personal_data") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mcp_new_content_input"),
                        minLines = 2
                    )

                    Button(
                        onClick = {
                            viewModel.addMcpRecord(newCategory, newTitle, newSubtitle, newContent)
                            newTitle = ""
                            newSubtitle = ""
                            newContent = ""
                        },
                        enabled = newTitle.isNotBlank() && newContent.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = McpViolet,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.testTag("mcp_add_record_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enregistrer dans MCP (${newCategory.displayName})")
                    }
                }
            }
        }

        // Category Filter Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("Tout (${uiState.mcpRecords.size})") },
                    modifier = Modifier.testTag("filter_mcp_all")
                )
                McpCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.displayName) },
                        modifier = Modifier.testTag("filter_mcp_${cat.mcpKey}")
                    )
                }
            }
        }

        items(
            items = filteredRecords,
            key = { it.id }
        ) { record ->
            McpRecordItemCard(
                record = record,
                onDelete = { viewModel.deleteMcpRecord(record.id) }
            )
        }
    }
}

@Composable
private fun McpRecordItemCard(
    record: McpRecord,
    onDelete: () -> Unit
) {
    val categoryIcon = when (record.category) {
        McpCategory.CONTACTS -> Icons.Default.Contacts
        McpCategory.CALENDAR -> Icons.Default.CalendarMonth
        McpCategory.NOTES -> Icons.Default.Description
        McpCategory.MESSAGES -> Icons.AutoMirrored.Filled.Message
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (record.containsUntrustedExternalText) SecurityCrimson else BorderSubtle,
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("mcp_record_card_${record.id}"),
        colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = McpViolet,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = record.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = "${record.category.displayName} • ${record.subtitle}",
                            style = MaterialTheme.typography.labelSmall,
                            color = OmniCyan
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_mcp_${record.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Supprimer l'entrée MCP",
                        tint = TextSecondary
                    )
                }
            }

            if (record.containsUntrustedExternalText) {
                Surface(
                    color = SecurityCrimson.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = SecurityCrimson,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Contient une charge d'injection adversariale (traitée comme donnée passive)",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecurityCrimson
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = record.content,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
    }
}
