package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.architecture.LiaNeuroSymbolicEngine
import com.example.architecture.NpuQuantizationMode
import com.example.architecture.ReflectionGranularityTier
import com.example.architecture.TriAgentRole
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
import com.example.ui.theme.GoogleSurface
import com.example.ui.theme.GoogleSurfaceVariant
import com.example.ui.theme.GoogleTextMuted
import com.example.ui.theme.GoogleTextPrimary
import com.example.ui.theme.GoogleTextSecondary
import com.example.ui.theme.GoogleYellow
import com.example.ui.theme.GoogleYellowContainer

/**
 * Console interactive d'Architecture Système Neuro-Symbolique LIA.
 * Expose et permet de piloter en temps réel les 10 sections de la spécification d'ingénierie :
 * 1. Double Voie Neuro-Symbolique (AppFunctions & Android MCP vs OmniParser v2 + Ferret-UI)
 * 2. Topologie Tri-Agent (Manager, Operator, Reflector) & Réflexion Multi-Échelle (MobileUse)
 * 3. Mémoire Virtuelle MemGPT, Graphe d'Entités Mem0 & Auto-Évolution (Tips & Shortcuts)
 * 4. Accélération NPU (LPDDR5X 62 Go/s, INT4 AWQ / BitNet b1.58, mzCache KV) & Moshi/Mimi 160ms
 * 5. Sécurité Lethal Trifecta, Visual Sanitization (MobileInject), DMA (11 Hooks) & Matrice Complète
 */
@Composable
fun LiaArchitectureBlueprintDialog(
    onDismiss: () -> Unit
) {
    val archState by LiaNeuroSymbolicEngine.state.collectAsStateWithLifecycle()
    val liveTriplets = LiaNeuroSymbolicEngine.buildLiveMem0Triplets()
    val liveMemGpt = LiaNeuroSymbolicEngine.computeLiveMemGptStats()

    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    var newTipApp by rememberSaveable { mutableStateOf("") }
    var newTipRule by rememberSaveable { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = GoogleSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, GoogleBlue.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                            text = "🧬 Architecture Neuro-Symbolique LIA",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoogleTextPrimary
                        )
                        Text(
                            text = "AppFunctions • OmniParser v2 • Tri-Agent • MemGPT/Mem0 • NPU AWQ • Moshi",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoogleBlue
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = GoogleTextSecondary)
                    }
                }

                // 5 Section Tabs covering all 10 chapters of the PDF
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        0 to "1. Double Voie & Vision",
                        1 to "2. Tri-Agent & Réflexion",
                        2 to "3. MemGPT, Mem0 & Tips",
                        3 to "4. NPU, BitNet & Moshi",
                        4 to "5. Sécurité, DMA & Matrice"
                    )
                    tabs.forEach { (idx, label) ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selectedSection == idx) GoogleBlueContainer else GoogleSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { selectedSection = idx }
                                .testTag("arch_tab_$idx")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedSection == idx) GoogleBlue else GoogleTextSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 390.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedSection) {
                        // TAB 0: Section 1, 2 & 3 (Double Voie Neuro-Symbolique, AppFunctions, Ferret-UI & OmniParser v2)
                        0 -> {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GoogleSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "⚡ Voie 1 : Symbolique Directe (OS-Native)",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = GoogleGreen
                                        )
                                        Text(
                                            text = "Privilégie @AppFunction (androidx.appfunctions) & Android MCP en < 8 ms avant tout fallback visuel.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GoogleTextSecondary
                                        )
                                    }
                                    Switch(
                                        checked = archState.preferSymbolicAppFunctions,
                                        onCheckedChange = { LiaNeuroSymbolicEngine.toggleSymbolicAppFunctionsPath(it) }
                                    )
                                }
                            }

                            Text(
                                text = "Registre déclaratif @AppFunction (KSP & AppFunctionContext) :",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GoogleTextPrimary
                            )

                            archState.declaredAppFunctions.forEach { fn ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = GoogleBackground,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, GoogleBorderSubtle, RoundedCornerShape(12.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "@AppFunction(\"${fn.name}\")",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = GoogleBlue
                                            )
                                            Text(
                                                text = "${fn.deterministicLatencyMs} ms",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = GoogleGreen
                                            )
                                        }
                                        Text(
                                            text = fn.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GoogleTextPrimary
                                        )
                                        Text(
                                            text = "Schéma : ${fn.inputSchema}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = GoogleTextMuted
                                        )
                                    }
                                }
                            }

                            // Section 3: Ferret-UI Any-Resolution + OmniParser v2 + Test-Time Scaling
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GoogleSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "👁️ Voie 2 : Perception Visuelle Ferret-UI & OmniParser v2",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = GooglePurple
                                    )
                                    Text(
                                        text = "• Partitionnement dynamique (${archState.viewportState.rawResolution}) :\n" +
                                            "  - ${archState.viewportState.ferretUiTopTile}\n" +
                                            "  - ${archState.viewportState.ferretUiBottomTile}\n" +
                                            "  - ${archState.viewportState.globalThumbnail}\n" +
                                            "• Pipeline : ${archState.viewportState.omniParserDetector} + Set-of-Mark (${archState.viewportState.setOfMarkAccuracyBenchmark})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GoogleTextPrimary
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Visual Test-Time Scaling (2 captures @ 100ms + zoom ROI)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GoogleYellow,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = archState.viewportState.visualTestTimeScalingActive,
                                            onCheckedChange = { LiaNeuroSymbolicEngine.toggleVisualTestTimeScaling(it) }
                                        )
                                    }
                                }
                            }
                        }

                        // TAB 1: Section 4 (Topologie Tri-Agent Mobile-Agent-v2/E & Three-Tier Reflection MobileUse)
                        1 -> {
                            Text(
                                text = "Découplage Fonctionnel Tri-Agent (Mobile-Agent-v2 / Mobile-Agent-E) :",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GoogleTextPrimary
                            )

                            TriAgentRole.entries.forEach { role ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = GoogleSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = role.codeName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = GoogleBlue
                                        )
                                        Text(
                                            text = role.responsibility,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GoogleTextPrimary
                                        )
                                        Text(
                                            text = "🔗 ${role.ragSource}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GoogleGreen
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Réflexion Multi-Échelle à 3 Niveaux (MobileUse Three-Tier Reflection) :",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GoogleTextPrimary
                            )

                            ReflectionGranularityTier.entries.forEach { tier ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = GoogleBackground,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, GoogleBorderSubtle, RoundedCornerShape(12.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = tier.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = GooglePurple
                                            )
                                            Text(
                                                text = tier.scaleLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GoogleYellow
                                            )
                                        }
                                        Text(
                                            text = tier.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GoogleTextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // TAB 2: Section 5 & 6 (MemGPT RAM/Disk, Graphe Mem0, Auto-Évolution Tips & Proactivité Act2Intention)
                        2 -> {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GoogleSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "🧠 Hiérarchie Mémoire Virtuelle MemGPT (RAM / Disk)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = GoogleBlue
                                    )
                                    Text(
                                        text = "• Contexte Actif (RAM) : ${liveMemGpt.activeRamTokensUsed} / ${liveMemGpt.activeRamTokenLimit} tokens (Tampon FIFO : ${liveMemGpt.fifoQueueMessagesCount} messages)\n" +
                                            "• Recall Storage (Room) : ${liveMemGpt.recallStorageEntriesCount} messages archivés\n" +
                                            "• Archival Storage (Room) : ${liveMemGpt.archivalStorageVectorsCount} entrées consolidées\n" +
                                            "• Gain mesuré : -${liveMemGpt.p95LatencyReductionPercent}% latence p95 • -${liveMemGpt.tokenSavingsPercent}% tokens",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GoogleTextPrimary
                                    )
                                }
                            }

                            Text(
                                text = "🕸️ Graphe de Connaissances Dynamique Mem0 (Sujet ➔ Prédicat ➔ Objet) :",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GoogleTextPrimary
                            )

                            if (liveTriplets.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = GoogleBackground,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Graphe Mem0 vierge (100% propre). Ajoutez un souvenir ou un contact pour générer automatiquement les triplets Sujet ➔ Prédicat ➔ Objet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GoogleTextMuted,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            } else {
                                liveTriplets.forEach { triplet ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = GoogleBackground,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, GoogleBorderSubtle, RoundedCornerShape(12.dp))
                                    ) {
                                        Text(
                                            text = "${triplet.subject}  ➔  [${triplet.predicate}]  ➔  ${triplet.objectTarget}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = GoogleGreen,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "💡 Auto-Évolution Mobile-Agent-E : Conseils Heuristiques (Tips) :",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GoogleTextPrimary
                            )

                            if (archState.heuristicTips.isEmpty()) {
                                Text(
                                    text = "Aucun conseil heuristique pré-rempli. Vous pouvez ajouter une règle d'interface ci-dessous.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoogleTextMuted
                                )
                            } else {
                                archState.heuristicTips.forEach { tip ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = GoogleSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "App : ${tip.targetAppOrDomain}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = GoogleYellow
                                                )
                                                Text(
                                                    text = tip.heuristicRule,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = GoogleTextPrimary
                                                )
                                            }
                                            IconButton(
                                                onClick = { LiaNeuroSymbolicEngine.deleteHeuristicTip(tip.id) },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = GoogleRed, modifier = Modifier.size(15.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newTipApp,
                                    onValueChange = { newTipApp = it },
                                    placeholder = { Text("App cible") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.38f)
                                )
                                OutlinedTextField(
                                    value = newTipRule,
                                    onValueChange = { newTipRule = it },
                                    placeholder = { Text("Règle heuristique (Tip)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.62f)
                                )
                                IconButton(
                                    onClick = {
                                        if (newTipApp.isNotBlank() && newTipRule.isNotBlank()) {
                                            LiaNeuroSymbolicEngine.addHeuristicTip(newTipApp, newTipRule)
                                            newTipApp = ""
                                            newTipRule = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(GoogleBlueVivid)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Ajouter Tip", tint = Color.White)
                                }
                            }
                        }

                        // TAB 3: Section 7 & 8 (NPU Bandwidth, INT4 AWQ / BitNet b1.58, mzCache KV & Moshi/Mimi 160ms)
                        3 -> {
                            val npu = archState.npuTelemetry
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GoogleSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = "⚡ Loi de Bande Passante NPU (${npu.socName})",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = GoogleGreen
                                    )
                                    Text(
                                        text = "• Bande passante LPDDR5X : ${npu.memoryBandwidthGbPerSec} Go/s (Intensité = 1 op/octet)\n" +
                                            "• Débit effectif en Decode : ${npu.effectiveDecodeTops} TOPS sur ${npu.theoreticalTops} TOPS théoriques\n" +
                                            "• Phase Prefill : ${npu.prefillTokensPerSec} tokens/s • Phase Decode : ${npu.activeQuantization.decodeSpeedTokensPerSec} tokens/s\n" +
                                            "• Empreinte RAM : ${npu.activeQuantization.modelRamFootprintGb} Go • Dissipation : ${npu.activeQuantization.thermalWatts} W (< 3,5 W)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GoogleTextPrimary
                                    )
                                }
                            }

                            Text(
                                text = "Format de Compression & Quantification Bas-Bit :",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GoogleTextPrimary
                            )

                            NpuQuantizationMode.entries.forEach { mode ->
                                val selected = npu.activeQuantization == mode
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) GoogleBlueContainer else GoogleBackground,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            1.dp,
                                            if (selected) GoogleBlue else GoogleBorderSubtle,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { LiaNeuroSymbolicEngine.selectNpuQuantizationMode(mode) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "${mode.label} (${mode.decodeSpeedTokensPerSec} tok/s • ${mode.thermalWatts} W)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) GoogleBlue else GoogleTextPrimary
                                        )
                                        Text(
                                            text = mode.precisionDetail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GoogleTextSecondary
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GoogleSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "📦 Ordonnancement Mémoire Contigu NPU & RAG Embarqué",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = GooglePurple
                                    )
                                    npu.contiguousAllocationOrder.forEach { line ->
                                        Text(text = line, style = MaterialTheme.typography.bodySmall, color = GoogleTextPrimary)
                                    }
                                    Text(
                                        text = "• RAG : Fenêtres ${npu.ragChunkWindowChars} chars (overlap ${npu.ragChunkOverlapChars}) • ${npu.vectorEngineSpec}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoogleGreen
                                    )
                                }
                            }

                            // Section 8: Moshi & Mimi Codec 160ms
                            val moshi = archState.moshiVoiceState
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GoogleSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🎙️ Pipeline Vocal Full-Duplex Moshi & Mimi",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = GoogleYellow
                                        )
                                        Switch(
                                            checked = moshi.isFullDuplexEnabled,
                                            onCheckedChange = { LiaNeuroSymbolicEngine.toggleMoshiFullDuplex(it) }
                                        )
                                    }
                                    Text(
                                        text = "• ${moshi.neuralCodecName} : Trame ${moshi.frameSizeMs} ms + Délai acoustique ${moshi.acousticDelayMs} ms = ${moshi.totalTheoreticalLatencyMs} ms (< 200 ms)\n" +
                                            "• ${moshi.architectureCoupling} (Interruption mid-sentence active)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GoogleTextPrimary
                                    )
                                }
                            }
                        }

                        // TAB 4: Section 9 & 10 (Lethal Trifecta, Visual Sanitization, DMA 11 Hooks & Matrice de Spécification)
                        4 -> {
                            val sec = archState.securityAndDma
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GoogleGreenContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "🛡️ Neutralisation du « Lethal Trifecta » & Défense MobileInject",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = GoogleGreen
                                    )
                                    Text(
                                        text = "1. Isolation des Canaux d'Entrée : Tout contenu externe est étiqueté comme donnée passive non exécutable.\n" +
                                            "2. Garde-fou Human-in-the-Loop : Confirmation obligatoire avant tout envoi de SMS, action financière ou suppression.\n" +
                                            "3. Visual Sanitization (MobileInject) : Filtrage spatial des filigranes adversariaux.\n" +
                                            "4. Conformité DMA (UE) : 11 points d'accès système Android (AppFunctions, AppSearch, Computer Control).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GoogleTextPrimary
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Assainissement Visuel (Visual Sanitization anti-MobileInject)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = GoogleGreen,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = sec.visualSanitizationMobileInjectActive,
                                            onCheckedChange = { LiaNeuroSymbolicEngine.toggleVisualSanitization(it) }
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "📊 Matrice de Spécification & Métriques (AndroidWorld / MobileWorld) :",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GoogleTextPrimary
                            )

                            Text(
                                text = "• Succès AndroidWorld : ${sec.androidWorldTargetSuccessPercent}% (Objectif > 65% vs 30,6% Gen-1)\n" +
                                    "• Boucle visuelle : ${sec.visualLoopLatencyMs} ms (< 800 ms) • Thermique : ${sec.continuousThermalWatts} W (< 3,5 W) • Vocal : ${sec.voiceEndToEndLatencyMs} ms (< 200 ms)",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoogleBlue
                            )

                            archState.specificationMatrix.forEach { row ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = GoogleBackground,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, GoogleBorderSubtle, RoundedCornerShape(12.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${row.domain} • ${row.componentName}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = GoogleBlue
                                            )
                                            Text(
                                                text = "✓ Actif",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = GoogleGreen
                                            )
                                        }
                                        Text(
                                            text = row.technologyStack,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = GooglePurple
                                        )
                                        Text(
                                            text = row.operationalRole,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GoogleTextSecondary
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
