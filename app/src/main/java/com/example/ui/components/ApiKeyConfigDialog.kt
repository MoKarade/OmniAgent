package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.GeminiDualEngineClient
import com.example.ui.theme.ActionEmerald
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.OmniCyan
import com.example.ui.theme.SecurityCrimson
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThoughtAmber

@Composable
fun ApiKeyConfigDialog(
    onDismiss: () -> Unit,
    onKeySaved: () -> Unit
) {
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf(GeminiDualEngineClient.getCustomApiKey()) }
    var status by remember { mutableStateOf(GeminiDualEngineClient.getDiagnosticStatus()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, OmniCyan, RoundedCornerShape(20.dp))
                .testTag("api_key_config_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = ThoughtAmber.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = ThoughtAmber,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Configuration Clé API Gemini",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Double moteur : Firebase AI Logic + Clé API Directe",
                            style = MaterialTheme.typography.labelSmall,
                            color = OmniCyan
                        )
                    }
                }

                // Diagnostic Status Box
                Surface(
                    color = ObsidianBackground,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "ÉTAT ACTUEL DES CLÉS :",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ActionEmerald
                        )
                        Text(
                            text = "• Source active : ${status.activeSourceLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        Text(
                            text = "• Clé interne App : ${status.customKeyPreview}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = if (status.hasCustomInAppKey) ActionEmerald else TextSecondary
                        )
                        Text(
                            text = "• Secrets AI Studio (GEMINI_API_KEY) : ${status.buildConfigKeyPreview}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = if (status.hasBuildConfigSecretKey) ActionEmerald else ThoughtAmber
                        )
                        Text(
                            text = "• Clé Firebase (google-services.json) : ${status.firebaseGoogleApiKeyPreview}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = OmniCyan
                        )
                    }
                }

                Text(
                    text = "Si le panneau Secrets d'AI Studio n'injecte pas votre clé ou si la clé Android auto-générée par Firebase est restreinte sur votre projet Cloud, collez directement votre clé Gemini (commençant par AIza...) ci-dessous. Elle prendra immédiatement le relais :",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Clé API Gemini (AIzaSy...)") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OmniCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedLabelColor = OmniCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_api_key_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            GeminiDualEngineClient.saveCustomApiKey(context, keyInput)
                            status = GeminiDualEngineClient.getDiagnosticStatus()
                            onKeySaved()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ActionEmerald,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_custom_api_key_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enregistrer la clé", fontWeight = FontWeight.Bold)
                    }

                    if (status.hasCustomInAppKey) {
                        OutlinedButton(
                            onClick = {
                                GeminiDualEngineClient.clearCustomApiKey(context)
                                keyInput = ""
                                status = GeminiDualEngineClient.getDiagnosticStatus()
                            },
                            modifier = Modifier.testTag("clear_custom_api_key_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Effacer",
                                tint = SecurityCrimson,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fermer")
                }
            }
        }
    }
}
