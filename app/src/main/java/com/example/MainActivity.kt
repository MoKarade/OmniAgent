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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.example.ui.screens.AgentFleetAndHistoryScreen
import com.example.ui.screens.AppAccessControlScreen
import com.example.ui.screens.OperatorConsoleScreen
import com.example.ui.theme.ActionEmerald
import com.example.ui.theme.GoogleBackground
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.GoogleBlueContainer
import com.example.ui.theme.GoogleSurface
import com.example.ui.theme.GoogleSurfaceVariant
import com.example.ui.theme.GoogleTextPrimary
import com.example.ui.theme.GoogleTextSecondary
import com.example.ui.theme.GoogleYellow
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
    APP_ACCESS("app_access", R.string.nav_app_access, Icons.Filled.Shield, Icons.Outlined.Shield),
    AGENTS_TASKS("agents_tasks", R.string.nav_agents_tasks, Icons.Filled.SmartToy, Icons.Outlined.SmartToy)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory Firebase App Check initialization with launch intent before UI or ViewModels
        configureAppCheck(this, intent)

        // STEP 2: Initialize Dual-Engine Gemini API Key Client, LIA Persistent Memory Store, Google Cloud Workspace Client, Dynamic App Monitor & Usage Service
        GeminiDualEngineClient.init(this)
        LiaMemoryAndHistoryStore.init(this)
        com.example.data.LiaGoogleCloudWorkspaceClient.init(this)
        com.example.data.LiaUniversalAppsAndGoogleHub.initAndRegisterDynamicPackageMonitor(this)
        com.example.service.LiaMcpConnectorRegistry.refreshConnectors(this)
        com.example.service.LiaAppUsageTrackerService.startTracking(this)

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
        com.example.service.SoniaOverlayBubbleService.hideOverlay(this)
        com.example.data.LiaUniversalAppsAndGoogleHub.refreshAllInstalledPhoneApps(this)
        com.example.service.LiaMcpConnectorRegistry.refreshConnectors(this)
        com.example.service.LiaAppUsageTrackerService.startTracking(this)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (com.example.service.SoniaOverlayBubbleService.canDrawOverlays(this)) {
            com.example.service.SoniaOverlayBubbleService.startSideBubble(this)
        }
    }

    override fun onStop() {
        super.onStop()
        if (com.example.service.SoniaOverlayBubbleService.canDrawOverlays(this)) {
            com.example.service.SoniaOverlayBubbleService.startSideBubble(this)
        }
    }
}

@Composable
fun OmniRootAuthHost() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
    var showAuthModal by rememberSaveable { mutableStateOf(false) }
    var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val firestoreRepository = remember(context) {
        val databaseId = context.getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(databaseId)
        OmniFirestoreRepository(firestore)
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
                currentUser = auth.currentUser
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
                auth.signOut()
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
    var showMoreMenu by remember { mutableStateOf(false) }

    // Retour fluide vers l'assistant depuis les vues secondaires
    BackHandler(enabled = currentTab != OmniDestination.CONSOLE) {
        currentTab = OmniDestination.CONSOLE
    }

    // Flou dynamique d'arrière-plan lorsque le menu d'options est ouvert
    val mainContentBlur by animateDpAsState(
        targetValue = if (showMoreMenu) 10.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "mainContentBlur"
    )

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

                        // Droite : Navigation Material 3 semi-transparente avec transitions fluides + Menu
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OmniDestination.entries.forEach { dest ->
                                val selected = currentTab == dest
                                val consumerLabel = when (dest) {
                                    OmniDestination.CONSOLE -> "Assistant"
                                    OmniDestination.APP_ACCESS -> "Confidentialité"
                                    OmniDestination.AGENTS_TASKS -> "Espace avancé"
                                }
                                val pillBgColor by animateColorAsState(
                                    targetValue = if (selected) {
                                        GoogleBlueContainer.copy(alpha = 0.82f)
                                    } else {
                                        Color(0xFF222734).copy(alpha = 0.55f)
                                    },
                                    animationSpec = tween(220),
                                    label = "navPillBg"
                                )
                                val pillBorderColor by animateColorAsState(
                                    targetValue = if (selected) {
                                        GoogleBlue.copy(alpha = 0.65f)
                                    } else {
                                        Color.White.copy(alpha = 0.10f)
                                    },
                                    animationSpec = tween(220),
                                    label = "navPillBorder"
                                )

                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = pillBgColor,
                                    tonalElevation = if (selected) 4.dp else 0.dp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .border(1.dp, pillBorderColor, RoundedCornerShape(50))
                                        .clickable { currentTab = dest }
                                        .animateContentSize(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                        .testTag("nav_tab_${dest.route}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = if (selected) 12.dp else 9.dp,
                                            vertical = 7.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = consumerLabel,
                                            tint = if (selected) GoogleBlue else GoogleTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        if (selected) {
                                            Text(
                                                text = consumerLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = GoogleBlue
                                            )
                                        }
                                    }
                                }
                            }

                            // Menu d'options & Compte
                            Box {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF222734).copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                                ) {
                                    IconButton(
                                        onClick = { showMoreMenu = true },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("top_bar_more_options_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Paramètres",
                                            tint = GoogleTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    modifier = Modifier
                                        .background(Color(0xFF1A1E29).copy(alpha = 0.96f))
                                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (currentUser != null) "Compte Google connecté" else "Connecter Google",
                                                color = GoogleTextPrimary
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (currentUser != null) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                                contentDescription = null,
                                                tint = if (currentUser != null) ActionEmerald else GoogleBlue
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            if (currentUser != null) onSignOut() else onOpenAuthScreen()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (com.example.service.SoniaOverlayBubbleService.canDrawOverlays(context)) {
                                                    "Bulle flottante (Hors de l'app)"
                                                } else {
                                                    "Activer la bulle flottante"
                                                },
                                                color = GoogleTextPrimary
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoogleBlue)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            triggerSystemWideBubble()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Confidentialité & Accès", color = GoogleTextPrimary) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Shield, contentDescription = null, tint = ActionEmerald)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            currentTab = OmniDestination.APP_ACCESS
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Espace avancé & Système", color = GoogleTextPrimary) },
                                        leadingIcon = {
                                            Icon(Icons.Default.SmartToy, contentDescription = null, tint = GoogleBlue)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            currentTab = OmniDestination.AGENTS_TASKS
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Clé API Gemini", color = GoogleTextPrimary) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Key, contentDescription = null, tint = GoogleYellow)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onOpenApiKeyDialog()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            // Corps principal avec flou dynamique et transitions de navigation fluides (AnimatedContent)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .blur(mainContentBlur)
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
                            onOpenAccessPermissions = { agentId ->
                                agentViewModel.selectPermissionTargetAgent(agentId)
                                currentTab = OmniDestination.APP_ACCESS
                            },
                            onOpenGoogleSignIn = onOpenAuthScreen
                        )
                        OmniDestination.APP_ACCESS -> AppAccessControlScreen(
                            uiState = uiState,
                            viewModel = agentViewModel,
                            onInspectAppInConsole = { screenId ->
                                agentViewModel.selectScreenManually(screenId)
                                currentTab = OmniDestination.CONSOLE
                            }
                        )
                        OmniDestination.AGENTS_TASKS -> AgentFleetAndHistoryScreen(
                            uiState = uiState,
                            viewModel = agentViewModel,
                            onSwitchToConsole = { currentTab = OmniDestination.CONSOLE },
                            onOpenAgentPermissions = { agentId ->
                                agentViewModel.selectPermissionTargetAgent(agentId)
                                currentTab = OmniDestination.APP_ACCESS
                            }
                        )
                    }
                }
            }
        }
    }
}
