package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.LiaMemorySnapshot
import com.example.model.PromptComplexityTier
import com.example.ui.theme.GoogleBackground
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
import com.example.viewmodel.OmniAgentUiState

/**
 * Carte « Compréhension & Mémoire en Direct » en design Glassmorphic / Soft-Neumorphic.
 * Sans aucun jargon technique : montre simplement comment LIA comprend votre demande,
 * mobilise vos souvenirs personnels et prépare l'action correspondante.
 */
@Composable
fun LiaLiveThoughtAndRagDiagnosticCard(
    uiState: OmniAgentUiState,
    liaMemory: LiaMemorySnapshot,
    onTriggerCommand: (String) -> Unit,
    onClose: () -> Unit
) {
    val profile = uiState.activePromptProfile
    val telemetry = uiState.liveTelemetry
    val ragOutcome = liaMemory.lastRagOutcome
    val latestStep = uiState.cognitiveHistory.lastOrNull()
    var customTeachTrigger by remember { mutableStateOf("") }
    var customTeachAction by remember { mutableStateOf("") }

    val tierColor = when (profile.tier) {
        PromptComplexityTier.BRIEF_DIRECT -> GoogleGreen
        PromptComplexityTier.CONTEXTUAL_SYNTHESIS -> GoogleBlue
        PromptComplexityTier.DEEP_CHAIN_OF_THOUGHT -> GooglePurple
    }
    val naturalModeLabel = when (profile.tier) {
        PromptComplexityTier.BRIEF_DIRECT -> "Réponse instantanée"
        PromptComplexityTier.CONTEXTUAL_SYNTHESIS -> "Contexte personnalisé"
        PromptComplexityTier.DEEP_CHAIN_OF_THOUGHT -> "Réflexion approfondie"
    }

    GlassNeumorphicCard(
        cornerRadius = 24.dp,
        accentColor = tierColor,
        isElevated = true,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_diagnostic_thought_rag_card")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. En-tête épuré et humain
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
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(tierColor.copy(alpha = 0.18f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Compréhension LIA",
                            tint = tierColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Comment LIA vous comprend",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = GoogleTextPrimary
                        )
                        Text(
                            text = "Lien en direct entre votre demande, vos souvenirs et l'action",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoogleTextSecondary
                        )
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

            // 2. Pastilles de statut naturelles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = tierColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "✨ $naturalModeLabel",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tierColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = GoogleGreenContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = if (profile.eligibleForFastPath) "⚡ Action directe rapide" else "💡 Analyse du contexte",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoogleGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = GooglePurpleContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "💭 ${liaMemory.souvenirs.size} souvenirs • ${liaMemory.learnedShortcuts.size} habitudes",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GooglePurple,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // 3. Fil conducteur visuel : Votre demande ➔ Réflexion ➔ Action
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF121620).copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DiagnosticPipelineStepRow(
                    stepNumber = "1",
                    badgeLabel = "Votre demande",
                    badgeColor = GoogleBlue,
                    detailText = uiState.submittedMission.ifBlank {
                        liaMemory.activeMessages.lastOrNull { it.role == "user" }?.text
                            ?: "Dites ou écrivez simplement ce que vous souhaitez…"
                    },
                    subDetailText = "Contexte actif : LIA Assistant Android"
                )

                val latestLiaMsg = liaMemory.activeMessages.lastOrNull { it.role == "lia" }
                val cleanThought = latestStep?.thought?.takeIf { it.isNotBlank() }
                    ?.replace("Prompt Direct", "Compréhension immédiate")
                    ?.replace("Synthèse Contextuelle", "Association avec vos souvenirs")
                    ?.replace("Chaîne de Pensée CoT", "Réflexion étape par étape")
                    ?: telemetry.liveThought.ifBlank { "LIA analyse votre intention et vos habitudes." }

                DiagnosticPipelineStepRow(
                    stepNumber = "2",
                    badgeLabel = "Réflexion de LIA",
                    badgeColor = GooglePurple,
                    detailText = cleanThought,
                    subDetailText = "Domaine : ${profile.taskCategoryLabel}"
                )

                val actionSummary = latestStep?.subgoal?.takeIf { it.isNotBlank() }
                    ?: telemetry.liveSubgoal.ifBlank {
                        latestLiaMsg?.learnedNote
                            ?: latestLiaMsg?.appLaunchAction?.let { "Ouverture de ${it.appLabel}" }
                            ?: "Prête à agir sur vos applications ou votre téléphone"
                    }

                DiagnosticPipelineStepRow(
                    stepNumber = "3",
                    badgeLabel = "Action réalisée",
                    badgeColor = GoogleGreen,
                    detailText = actionSummary,
                    subDetailText = latestLiaMsg?.learnedNote ?: "Exécution fluide et sécurisée ✓"
                )
            }

            // 4. Souvenirs mobilisés et apprentissage rapide
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF121620).copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val matches = ragOutcome?.retrievedMatches.orEmpty()
                Text(
                    text = if (matches.isNotEmpty()) {
                        "💭 Souvenirs utilisés pour vous répondre (${matches.size})"
                    } else {
                        "💭 Votre mémoire personnelle (${liaMemory.souvenirs.size} souvenirs mémorisés)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = GooglePurple
                )

                if (matches.isEmpty()) {
                    Text(
                        text = "LIA garde en mémoire vos préférences, vos contacts et vos notes pour personnaliser chaque réponse.",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoogleTextSecondary
                    )
                } else {
                    matches.take(3).forEach { hit ->
                        val simFraction = (hit.similarityPercent / 100f).coerceIn(0.10f, 1f)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(GoogleSurfaceVariant.copy(alpha = 0.45f))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = hit.souvenir.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = GoogleTextPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Pertinence ${hit.similarityPercent}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GoogleGreen
                                )
                            }
                            Text(
                                text = hit.souvenir.detailValue,
                                style = MaterialTheme.typography.labelSmall,
                                color = GoogleTextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            LinearProgressIndicator(
                                progress = { simFraction },
                                color = GoogleGreen,
                                trackColor = GoogleSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                        }
                    }
                }

                // Habitudes apprises en 1 geste
                if (liaMemory.learnedShortcuts.isNotEmpty()) {
                    Text(
                        text = "⚡ Vos habitudes favorites :",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = GoogleGreen,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        liaMemory.learnedShortcuts.take(5).forEach { sc ->
                            GlassNeumorphicPill(
                                text = sc.triggerPhrase.take(24),
                                emoji = "⚡",
                                selected = false,
                                accentColor = GoogleGreen,
                                onClick = { onTriggerCommand(sc.triggerPhrase) }
                            )
                        }
                    }
                }

                // Enseigner une habitude ou une information à LIA
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GoogleSurfaceVariant.copy(alpha = 0.35f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "➕ Apprendre une habitude ou une information à LIA :",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoogleTextPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customTeachTrigger,
                            onValueChange = { customTeachTrigger = it },
                            placeholder = {
                                Text(
                                    "Quand je dis… (ex: mode détente)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            textStyle = MaterialTheme.typography.labelSmall.copy(color = GoogleTextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GooglePurple,
                                unfocusedBorderColor = GoogleBorderSubtle
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("diagnostic_teach_trigger_input")
                        )
                        OutlinedTextField(
                            value = customTeachAction,
                            onValueChange = { customTeachAction = it },
                            placeholder = {
                                Text(
                                    "Fais… (ex: ouvre Spotify)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            textStyle = MaterialTheme.typography.labelSmall.copy(color = GoogleTextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoogleGreen,
                                unfocusedBorderColor = GoogleBorderSubtle
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("diagnostic_teach_action_input")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassNeumorphicPill(
                            text = "Créer cette habitude",
                            emoji = "✨",
                            selected = true,
                            accentColor = GooglePurple,
                            onClick = {
                                val trig = customTeachTrigger.trim().ifBlank { "mode discussion" }
                                val act = customTeachAction.trim().ifBlank { "ouvre une conversation sur Messenger" }
                                onTriggerCommand("Quand je dis $trig, fais $act")
                                customTeachTrigger = ""
                                customTeachAction = ""
                            },
                            modifier = Modifier.testTag("diagnostic_save_rule_btn")
                        )
                        GlassNeumorphicPill(
                            text = "Retenir cette info",
                            emoji = "💭",
                            selected = true,
                            accentColor = GoogleGreen,
                            onClick = {
                                val key = customTeachTrigger.trim().ifBlank { "préférence" }
                                val value = customTeachAction.trim().ifBlank { "Messenger par défaut" }
                                onTriggerCommand("Apprends que $key : $value")
                                customTeachTrigger = ""
                                customTeachAction = ""
                            },
                            modifier = Modifier.testTag("diagnostic_save_fact_btn")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticPipelineStepRow(
    stepNumber: String,
    badgeLabel: String,
    badgeColor: Color,
    detailText: String,
    subDetailText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(badgeColor.copy(alpha = 0.2f))
        ) {
            Text(
                text = stepNumber,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = badgeLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
            Text(
                text = detailText,
                style = MaterialTheme.typography.bodySmall,
                color = GoogleTextPrimary
            )
            Text(
                text = subDetailText,
                style = MaterialTheme.typography.labelSmall,
                color = GoogleTextMuted
            )
        }
    }
}

/**
 * Télécommande tactile Glassmorphic « Contrôle de l'Écran & Actions Directes » :
 * Permet de piloter l'écran (cliquer, faire défiler, écrire, revenir en arrière)
 * ou de lancer une conversation Messenger / WhatsApp / Keep en 1 geste.
 */
@Composable
fun LiaFullScreenControlAndActionsDeck(
    onExecuteCommand: (String) -> Unit,
    onClose: () -> Unit
) {
    var customScreenOrContactTarget by remember { mutableStateOf("") }

    val screenGestures = listOf(
        "👆 Toucher au centre" to "Clique au milieu de l'écran",
        "⬆️ Toucher en haut" to "Clique en haut de l'écran",
        "⬇️ Toucher Envoyer" to "Clique sur Envoyer",
        "⏬ Défiler vers le bas" to "Scrolle vers le bas",
        "⏫ Défiler vers le haut" to "Scrolle vers le haut",
        "⬅️ Glisser à gauche" to "Balaye à gauche",
        "➡️ Glisser à droite" to "Balaye à droite",
        "⌨️ Écrire Bonjour" to "Tape le texte Bonjour dans le champ",
        "🔙 Retour" to "Bouton retour",
        "🏠 Accueil" to "Écran d'accueil",
        "🔔 Notifications" to "Ouvre les notifications"
    )

    val messagingAndLearningActions = listOf(
        "💙 Discussion Messenger" to "Ouvre une conversation sur Messenger",
        "🟢 Discussion WhatsApp" to "Ouvre une conversation sur WhatsApp",
        "💡 Nouvelle note Keep" to "Crée une note dans Keep : Idée importante",
        "✈️ Discussion Telegram" to "Ouvre une conversation sur Telegram",
        "🎮 Dernier jeu Steam" to "Retrouve le dernier jeu auquel j'ai joué dans Steam",
        "🔦 Lampe torche" to "Allume la lampe torche",
        "⚙️ Réglages rapides" to "Réglages rapides"
    )

    GlassNeumorphicCard(
        cornerRadius = 24.dp,
        accentColor = GoogleBlue,
        isElevated = true,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("full_screen_control_deck_card")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(GoogleBlue.copy(alpha = 0.18f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Contrôle de l'écran",
                            tint = GoogleBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Contrôle intuitif de l'écran",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = GoogleTextPrimary
                        )
                        Text(
                            text = "Gestes tactiles, saisie directe et messagerie rapide",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoogleTextSecondary
                        )
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

            // Barre directe : Bouton à toucher, texte à écrire ou contact Messenger
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF11151E).copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customScreenOrContactTarget,
                    onValueChange = { customScreenOrContactTarget = it },
                    placeholder = {
                        Text(
                            "Nom d'un bouton, texte à écrire ou prénom d'un contact…",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = GoogleTextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoogleBlue,
                        unfocusedBorderColor = GoogleBorderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_screen_or_contact_input")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassNeumorphicPill(
                        text = "Toucher cet élément",
                        emoji = "👆",
                        selected = true,
                        accentColor = GoogleBlue,
                        onClick = {
                            val target = customScreenOrContactTarget.trim().ifBlank { "le centre de l'écran" }
                            onExecuteCommand("Clique sur $target")
                            customScreenOrContactTarget = ""
                        },
                        modifier = Modifier.testTag("custom_click_target_btn")
                    )
                    GlassNeumorphicPill(
                        text = "Écrire ce texte",
                        emoji = "⌨️",
                        selected = true,
                        accentColor = GoogleGreen,
                        onClick = {
                            val textToType = customScreenOrContactTarget.trim().ifBlank { "Salut, tu es disponible ?" }
                            onExecuteCommand("Tape le texte $textToType dans le champ")
                            customScreenOrContactTarget = ""
                        },
                        modifier = Modifier.testTag("custom_type_text_btn")
                    )
                    GlassNeumorphicPill(
                        text = "Discuter sur Messenger",
                        emoji = "💙",
                        selected = true,
                        accentColor = GooglePurple,
                        onClick = {
                            val contact = customScreenOrContactTarget.trim()
                            val cmd = if (contact.isNotBlank()) {
                                "Ouvre une conversation avec $contact sur Messenger"
                            } else {
                                "Ouvre une conversation sur Messenger"
                            }
                            onExecuteCommand(cmd)
                            customScreenOrContactTarget = ""
                        },
                        modifier = Modifier.testTag("custom_open_messenger_contact_btn")
                    )
                }
            }

            Text(
                text = "Gestes sur l'écran :",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = GoogleTextSecondary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                screenGestures.forEachIndexed { idx, (label, cmd) ->
                    GlassNeumorphicPill(
                        text = label,
                        selected = false,
                        accentColor = GoogleBlue,
                        onClick = { onExecuteCommand(cmd) },
                        modifier = Modifier.testTag("screen_gesture_chip_$idx")
                    )
                }
            }

            Text(
                text = "Accès rapides aux applications :",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = GoogleTextSecondary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                messagingAndLearningActions.forEachIndexed { idx, (label, cmd) ->
                    GlassNeumorphicPill(
                        text = label,
                        selected = label.contains("Messenger"),
                        accentColor = if (label.contains("Messenger")) GoogleBlue else GoogleGreen,
                        onClick = { onExecuteCommand(cmd) },
                        modifier = Modifier.testTag("messaging_learning_chip_$idx")
                    )
                }
            }
        }
    }
}
