package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.GeminiDualEngineClient
import com.example.data.LiaMemoryAndHistoryStore
import com.example.data.OmniFirestoreRepository
import com.example.security.configureAppCheck
import com.example.ui.auth.AuthScreen
import com.example.ui.components.ApiKeyConfigDialog
import com.example.ui.screens.LiaSettingsScreen
import com.example.ui.screens.OperatorConsoleScreen
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.GoogleBlueContainer
import com.example.ui.theme.GoogleTextPrimary
import com.example.ui.theme.GoogleTextSecondary
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.OmniAgentViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

enum class OmniDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CONSOLE("console", R.string.nav_console, Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Capture any unexpected thread exceptions to logcat for clear debugging
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("OmniAgentCrash", "FATAL crash on thread ${thread.name}", throwable)
        }

        // STEP 1: Mandatory Firebase App Check initialization with launch intent before UI or ViewModels
        runCatching { configureAppCheck(this, intent) }

        // STEP 2: Initialize Dual-Engine Gemini API Key Client, LIA Persistent Memory Store, Google Cloud Workspace Client, Dynamic App Monitor & Usage Service
        runCatching { GeminiDualEngineClient.init(this) }
        runCatching { LiaMemoryAndHistoryStore.init(this) }
        runCatching { com.example.data.LiaGoogleCloudWorkspaceClient.init(this) }
        runCatching { com.example.data.LiaUniversalAppsAndGoogleHub.initAndRegisterDynamicPackageMonitor(this) }
        runCatching { com.example.service.LiaMcpConnectorRegistry.refreshConnectors(this) }
        runCatching { com.example.service.LiaAppUsageTrackerService.startTracking(this) }

        // STEP 3: Enable Edge-to-Edge and render UI
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OmniRootAuthHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Hide the system-wide overlay while inside the app so only the in-app bubble is shown
        runCatching { com.example.service.SoniaOverlayBubbleService.hideOverlay(this) }
        runCatching { com.example.data.LiaUniversalAppsAndGoogleHub.refreshAllInstalledPhoneApps(this) }
        runCatching { com.example.service.LiaMcpConnectorRegistry.refreshConnectors(this) }
        runCatching { com.example.service.LiaAppUsageTrackerService.startTracking(this) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (com.example.service.SoniaOverlayBubbleService.canDrawOverlays(this)) {
            runCatching { com.example.service.SoniaOverlayBubbleService.startSideBubble(this) }
        }
    }

    override fun onStop() {
        super.onStop()
        if (com.example.service.SoniaOverlayBubbleService.canDrawOverlays(this)) {
            runCatching { com.example.service.SoniaOverlayBubbleService.startSideBubble(this) }
        }
    }
}

@Composable
fun OmniRootAuthHost() {
    val context = LocalContext.current
    val auth = remember { runCatching { FirebaseAuth.getInstance() }.getOrNull() }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(auth?.currentUser) }
    var showAuthModal by rememberSaveable { mutableStateOf(false) }
    var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(auth) {
        val currentAuth = auth ?: return@DisposableEffect onDispose {}
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        currentAuth.addAuthStateListener(listener)
        onDispose {
            currentAuth.removeAuthStateListener(listener)
        }
    }

    val firestoreRepository = remember(context) {
        runCatching {
            val databaseId = runCatching { context.getString(R.string.firestore_database_id) }.getOrNull().orEmpty().trim()
            val firestore = if (databaseId.isNotBlank() && databaseId != "(default)") {
                runCatching { FirebaseFirestore.getInstance(databaseId) }.getOrElse { FirebaseFirestore.getInstance() }
            } else {
                FirebaseFirestore.getInstance()
            }
            OmniFirestoreRepository(firestore)
        }.getOrNull()
    }

    val agentViewModel: OmniAgentViewModel = viewModel(
        key = currentUser?.uid ?: "local_operator_session",
        factory = viewModelFactory {
            initializer {
                OmniAgentViewModel(
                    firestoreRepository = if (currentUser != null) firestoreRepository else null,
                    initialUserId = currentUser?.uid,
                    initialUserEmail = currentUser?.email
                )
            }
        }
    )

    LaunchedEffect(currentUser?.uid) {
        agentViewModel.bindAuthenticatedUser(
            userId = currentUser?.uid,
            userEmail = currentUser?.email,
            repository = if (currentUser != null) firestoreRepository else null
        )
    }

    if (showApiKeyDialog) {
        ApiKeyConfigDialog(
            onDismiss = { showApiKeyDialog = false },
            onKeySaved = { agentViewModel.dismissError() }
        )
    }

    if (showAuthModal) {
        AuthScreen(
            onSignInSuccess = {
                currentUser = auth?.currentUser
                showAuthModal = false
            },
            onContinueLocalSession = {
                showAuthModal = false
            }
        )
    } else {
        OmniAgentApp(
            agentViewModel = agentViewModel,
            currentUser = currentUser,
            onOpenApiKeyDialog = { showApiKeyDialog = true },
            onOpenAuthScreen = { showAuthModal = true },
            onSignOut = {
                runCatching { auth?.signOut() }
                currentUser = null
            }
        )
    }
}

@Composable
fun OmniAgentApp(
    agentViewModel: OmniAgentViewModel = viewModel(),
    currentUser: FirebaseUser? = null,
    onOpenApiKeyDialog: () -> Unit = {},
    onOpenAuthScreen: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by agentViewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableStateOf(OmniDestination.CONSOLE) }

    LaunchedEffect(context) {
        agentViewModel.refreshSecurityState(context)
    }

    // Retour fluide vers l'assistant depuis les vues secondaires
    BackHandler(enabled = currentTab != OmniDestination.CONSOLE) {
        currentTab = OmniDestination.CONSOLE
    }

    val triggerSystemWideBubble: () -> Unit = {
        if (com.example.service.SoniaOverlayBubbleService.canDrawOverlays(context)) {
            com.example.service.SoniaOverlayBubbleService.startSideBubble(context)
            (context as? ComponentActivity)?.moveTaskToBack(true)
        } else {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    com.example.ui.components.LiaAuroraGlassBackground(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                // Barre supérieure flottante Material 3 semi-transparente (Glassmorphic)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF181C26).copy(alpha = 0.72f),
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    GoogleBlue.copy(alpha = 0.20f),
                                    Color.Black.copy(alpha = 0.45f)
                                )
                            ),
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Gauche : Identité LIA (appui pour revenir à l'accueil ou réduire en bulle flottante)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    if (currentTab != OmniDestination.CONSOLE) {
                                        currentTab = OmniDestination.CONSOLE
                                    } else {
                                        triggerSystemWideBubble()
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("top_bar_system_bubble_button")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = GoogleBlueContainer.copy(alpha = 0.85f),
                                tonalElevation = 4.dp,
                                modifier = Modifier
                                    .size(34.dp)
                                    .border(1.dp, GoogleBlue.copy(alpha = 0.45f), CircleShape)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = GoogleBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "LIA",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = GoogleTextPrimary
                                )
                                if (currentTab != OmniDestination.CONSOLE) {
                                    Text(
                                        text = "← Retour à l'assistant",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoogleBlue
                                    )
                                }
                            }
                        }

                        // Droite : Icône discrète 'Paramètres' isolant toute la configuration avancée
                        val isSettingsActive = currentTab != OmniDestination.CONSOLE
                        val settingsBgColor by animateColorAsState(
                            targetValue = if (isSettingsActive) {
                                GoogleBlueContainer.copy(alpha = 0.82f)
                            } else {
                                Color(0xFF222734).copy(alpha = 0.55f)
                            },
                            animationSpec = tween(220),
                            label = "settingsIconBg"
                        )
                        val settingsBorderColor by animateColorAsState(
                            targetValue = if (isSettingsActive) {
                                GoogleBlue.copy(alpha = 0.65f)
                            } else {
                                Color.White.copy(alpha = 0.12f)
                            },
                            animationSpec = tween(220),
                            label = "settingsIconBorder"
                        )

                        Surface(
                            shape = CircleShape,
                            color = settingsBgColor,
                            tonalElevation = if (isSettingsActive) 4.dp else 0.dp,
                            modifier = Modifier
                                .size(36.dp)
                                .border(1.dp, settingsBorderColor, CircleShape)
                        ) {
                            IconButton(
                                onClick = {
                                    currentTab = if (currentTab == OmniDestination.SETTINGS) {
                                        OmniDestination.CONSOLE
                                    } else {
                                        OmniDestination.SETTINGS
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("top_bar_settings_button")
                            ) {
                                Icon(
                                    imageVector = if (isSettingsActive) {
                                        Icons.Filled.Settings
                                    } else {
                                        Icons.Outlined.Settings
                                    },
                                    contentDescription = "Paramètres",
                                    tint = if (isSettingsActive) GoogleBlue else GoogleTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            // Corps principal avec transitions de navigation fluides (AnimatedContent)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        val forward = targetState.ordinal >= initialState.ordinal
                        val enterSlide = slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) { fullWidth -> if (forward) fullWidth / 7 else -fullWidth / 7 }
                        val exitSlide = slideOutHorizontally(
                            animationSpec = tween(durationMillis = 190)
                        ) { fullWidth -> if (forward) -fullWidth / 9 else fullWidth / 9 }

                        (fadeIn(animationSpec = tween(240)) + enterSlide + scaleIn(initialScale = 0.98f, animationSpec = tween(240)))
                            .togetherWith(fadeOut(animationSpec = tween(180)) + exitSlide)
                    },
                    label = "OmniMainTabTransition",
                    modifier = Modifier.fillMaxSize()
                ) { destination ->
                    when (destination) {
                        OmniDestination.CONSOLE -> OperatorConsoleScreen(
                            uiState = uiState,
                            viewModel = agentViewModel,
                            onOpenAccessPermissions = {
                                currentTab = OmniDestination.SETTINGS
                            },
                            onOpenGoogleSignIn = onOpenAuthScreen
                        )
                        OmniDestination.SETTINGS -> LiaSettingsScreen(
                            uiState = uiState,
                            viewModel = agentViewModel,
                            isUserSignedIn = currentUser != null,
                            userEmail = currentUser?.email,
                            onBackToAssistant = { currentTab = OmniDestination.CONSOLE },
                            onOpenApiKeyDialog = onOpenApiKeyDialog,
                            onOpenAuthScreen = onOpenAuthScreen,
                            onSignOut = onSignOut,
                            onTriggerSystemBubble = triggerSystemWideBubble
                        )
                    }
                }
            }
        }
    }
}
