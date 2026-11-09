package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.service.AppActionExecutor
import com.example.ui.theme.ActionEmerald
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.McpViolet
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OmniCyan
import com.example.ui.theme.SecurityCrimson
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThoughtAmber
import com.example.viewmodel.OmniAgentUiState

@Composable
fun SecurityPromptScreen(
    uiState: OmniAgentUiState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    var copiedPrompt by remember { mutableStateOf(false) }
    val fastPathMetrics by AppActionExecutor.fastPathMetrics.collectAsStateWithLifecycle()
    val promptProfile = uiState.activePromptProfile

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OmniCyan.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.section_system_prompt),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "Optimisation Dynamique Active : ${promptProfile.tier.badgeLabel} (${promptProfile.taskCategoryLabel}). ${promptProfile.rationaleFr}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "⚡ File d'Exécution Fast-Path : ${fastPathMetrics.totalFastPathDispatched} action(s) directe(s) • Latence moy. ${fastPathMetrics.averageFastPathLatencyMs} ms • ${fastPathMetrics.lastFastPathSummary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ActionEmerald
                    )
                    Button(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText(
                                    "OmniAgent System Instructions",
                                    uiState.systemInstructionsText
                                )
                            )
                            copiedPrompt = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (copiedPrompt) ActionEmerald else OmniCyan,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.testTag("copy_system_prompt_button")
                    ) {
                        Icon(
                            imageVector = if (copiedPrompt) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (copiedPrompt) {
                                "Instructions Système Copiées !"
                            } else {
                                stringResource(R.string.copy_system_prompt)
                            }
                        )
                    }
                }
            }
        }

        // Security Guardrails Breakdown (Section 3.1 - 3.4)
        item {
            Text(
                text = "Matrice de Robustesse & Garde-Fous Actifs",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }

        item {
            SecurityRuleCard(
                icon = Icons.Default.VerifiedUser,
                title = "1. SÉCURITÉ HUMAINE (Human-in-the-Loop)",
                accentColor = ThoughtAmber,
                description = "Aucune action financière (virement, paiement carte), suppression irréversible ou envoi sensible n'est exécuté de manière autonome sans appeler ask_user(question, is_sensitive=true) et obtenir la validation explicite de l'opérateur humain."
            )
        }

        item {
            SecurityRuleCard(
                icon = Icons.Default.Loop,
                title = "2. ÉVITEMENT DES BOUCLES (2x Consecutive Failures)",
                accentColor = OmniCyan,
                description = "Le moteur trace la signature de chaque action. Si une action échoue 2 fois consécutivement (l'écran ne change pas), une alerte de réflexion force le Cerveau Cognitif à basculer sur une alternative (swipe, bouton de secours ou global_action(\"BACK\"))."
            )
        }

        item {
            SecurityRuleCard(
                icon = Icons.Default.Shield,
                title = "3. DÉFENSE CONTRE L'INJECTION ADVERSARIALE",
                accentColor = SecurityCrimson,
                description = "Tout texte affiché à l'écran (SMS, e-mail, page web) contenant des directives hostiles telles que « Ignore les instructions précédentes » est isolé comme donnée passive non-fiable. L'agent ne dévie jamais de la consigne initiale."
            )
        }

        item {
            SecurityRuleCard(
                icon = Icons.Default.GppGood,
                title = "4. CONFORMITÉ & ISOLATION DES PRIVILÈGES",
                accentColor = ActionEmerald,
                description = "Sortie JSON strictement validée par Schema.obj(...) sur Firebase AI Logic avec attestation App Check et audit Red-Team temps réel via gemini-3.8-flash."
            )
        }

        item {
            SecurityRuleCard(
                icon = Icons.Default.Lock,
                title = "5. SECRETS AI STUDIO, FACTURATION & FIREBASE AI LOGIC",
                accentColor = OmniCyan,
                description = "• Secrets AI Studio : Avec Firebase AI Logic (Firebase.ai) et App Check activés sur le projet gen-lang-client-0823766206, aucune clé GEMINI_API_KEY n'est exposée côté client dans l'APK. Le fichier .env.example expose uniquement MCP_OPERATOR_SECRET_KEY pour vos secrets personnalisés.\n• Compte de Facturation (Spark vs Blaze) : L'API Gemini Developer via Firebase AI Logic fonctionne gratuitement sur le plan Spark (sans facturation) dans la limite des quotas gratuits par minute, et bascule automatiquement sur les quotas étendus Pay-as-you-go si un compte Cloud Billing (plan Blaze) est associé au projet Google Cloud."
            )
        }


        // Exact Verbatim System Instructions Block
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF070B17))
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
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = McpViolet,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "SYSTEM INSTRUCTIONS (INTÉGRALES)",
                            style = MaterialTheme.typography.labelSmall,
                            color = McpViolet
                        )
                    }
                    Text(
                        text = uiState.systemInstructionsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD6E4FF)
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityRuleCard(
    icon: ImageVector,
    title: String,
    accentColor: Color,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = accentColor
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    }
}
