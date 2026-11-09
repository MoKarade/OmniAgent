package com.example.ui.auth

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.LiaGoogleCloudWorkspaceClient
import com.example.ui.theme.ActionEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.OmniCyan
import com.example.ui.theme.SecurityCrimson
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThoughtAmber
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(
    onSignInSuccess: () -> Unit,
    onContinueLocalSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val workspaceAuth by LiaGoogleCloudWorkspaceClient.state.collectAsStateWithLifecycle()

    var oauthTokenInput by rememberSaveable { mutableStateOf("") }
    var emailInput by rememberSaveable { mutableStateOf(workspaceAuth.userEmail.ifBlank { "marc.richard4@gmail.com" }) }

    // Runtime permission launcher for Contacts + Calendar so local & cloud sync works immediately
    val workspacePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        LiaGoogleCloudWorkspaceClient.syncAllWorkspaceData(context)
    }

    // Flow 1: Silent / Automatic Sign-In with GetGoogleIdOption on launch
    LaunchedEffect(Unit) {
        LiaGoogleCloudWorkspaceClient.init(context)
        val signedInSilently = LiaGoogleCloudWorkspaceClient.attemptSilentGoogleSignIn(context)
        if (signedInSilently) {
            onSignInSuccess()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 580.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Auth & Google Cloud Workspace Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, OmniCyan.copy(alpha = 0.5f), RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = OmniCyan.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (workspaceAuth.isSignedIn) Icons.Default.CloudDone else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (workspaceAuth.isSignedIn) ActionEmerald else OmniCyan,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(26.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Google Sign-In & Credential Manager",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "OAuth Cloud : gen-lang-client-0311284453 • Gmail, Agenda, Drive, Contacts",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = ActionEmerald
                            )
                        }
                    }

                    Text(
                        text = "Connectez-vous en 1 clic via Jetpack Credential Manager (GetSignInWithGoogleOption) pour autoriser LIA à accéder à Gmail, Google Agenda, Google Drive et Google Contacts via les APIs Google Cloud et synchroniser votre mémoire RAG.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    // 4 Authorized Google Cloud APIs Status Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val services = listOf(
                            "📧 Gmail API" to "${workspaceAuth.recentEmails.size} mails",
                            "📅 Calendar API" to "${workspaceAuth.upcomingEvents.size} RDV",
                            "📁 Drive API" to "${workspaceAuth.recentDriveFiles.size} fichiers",
                            "👤 Contacts API" to "${workspaceAuth.syncedContacts.size} contacts"
                        )
                        services.forEach { (name, countLabel) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        1.dp,
                                        if (workspaceAuth.isSignedIn) ActionEmerald.copy(alpha = 0.5f) else OmniCyan.copy(alpha = 0.25f),
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = countLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ActionEmerald
                                    )
                                }
                            }
                        }
                    }

                    if (workspaceAuth.isSignedIn) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ActionEmerald.copy(alpha = 0.14f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ActionEmerald.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ActionEmerald
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Connecté : ${workspaceAuth.userDisplayName.ifBlank { workspaceAuth.userEmail.ifBlank { "Compte Google autorisé" } }}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = workspaceAuth.lastSyncSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    if (workspaceAuth.authErrorMessage != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SecurityCrimson, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = SecurityCrimson.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = SecurityCrimson
                                )
                                Text(
                                    text = workspaceAuth.authErrorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Primary 1-Tap Credential Manager Sign-In Button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                LiaGoogleCloudWorkspaceClient.signInInteractiveWithCredentialManager(
                                    context = context,
                                    onSuccess = onSignInSuccess
                                )
                            }
                        },
                        enabled = !workspaceAuth.isAuthenticating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OmniCyan,
                            contentColor = Color(0xFF001F24)
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_sign_in_button")
                    ) {
                        if (workspaceAuth.isAuthenticating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF001F24)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Authentification Credential Manager...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_google_sign_in),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Direct Fast Firebase Sign-In Button (Immediate Cloud Connection)
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                runCatching {
                                    com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously().await()
                                    onSignInSuccess()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ActionEmerald,
                            contentColor = Color(0xFF001F24)
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("firebase_fast_connect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connexion Directe Firebase (Session Cloud Immédiate)",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Sync Android Calendar & Contacts + Cloud Workspace Button
                    OutlinedButton(
                        onClick = {
                            workspacePermissionsLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.READ_CALENDAR,
                                    Manifest.permission.WRITE_CALENDAR
                                )
                            )
                        },
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sync_workspace_apis_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = OmniCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (workspaceAuth.isSyncingWorkspace)
                                "Synchronisation Gmail, Agenda, Drive, Contacts…"
                            else
                                "Synchroniser Gmail, Agenda, Drive & Contacts maintenant",
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = onContinueLocalSession,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("continue_local_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = ActionEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Retourner à la Console LIA",
                            color = Color.White
                        )
                    }
                }
            }

            // Optional Direct OAuth2 Bearer Token Card (for Emulator REST API testing without Play Store account)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ThoughtAmber.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = ThoughtAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Passerelle OAuth2 Google Cloud APIs (Optionnel sur Émulateur)",
                            style = MaterialTheme.typography.titleSmall,
                            color = ThoughtAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Sur un appareil physique, Credential Manager et Google Play Services fournissent automatiquement le jeton OAuth. Sur un émulateur sans compte Google Play, vous pouvez lier votre adresse et/ou un jeton OAuth2 Bearer pour interroger directement les APIs REST Gmail, Calendar, Drive et People.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Compte Google (ex: marc.richard4@gmail.com)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = oauthTokenInput,
                        onValueChange = { oauthTokenInput = it },
                        label = { Text("Jeton OAuth2 Bearer Google Cloud (optionnel : ya29.a0...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            LiaGoogleCloudWorkspaceClient.updateOAuthAccessToken(
                                context = context,
                                accessToken = oauthTokenInput,
                                optionalEmail = emailInput
                            )
                            onSignInSuccess()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ActionEmerald,
                            contentColor = Color(0xFF001F24)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_oauth_token_button")
                    ) {
                        Text(
                            text = "Activer & Synchroniser les APIs Google Cloud",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
