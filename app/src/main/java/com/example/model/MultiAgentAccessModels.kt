package com.example.model

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class AgentOperationalStatus(
    val standardizedCode: String,
    val frenchLabel: String,
    val friendlyTitle: String,
    val subtitle: String
) {
    IDLE(
        standardizedCode = "Idle",
        frenchLabel = "En Veille",
        friendlyTitle = "Prêt à vous aider",
        subtitle = "En attente de votre demande"
    ),
    OBSERVING(
        standardizedCode = "Thinking",
        frenchLabel = "Observation Écran",
        friendlyTitle = "Regarde votre écran...",
        subtitle = "Analyse visuelle de l'application ouverte"
    ),
    THINKING(
        standardizedCode = "Thinking",
        frenchLabel = "Raisonnement",
        friendlyTitle = "Réfléchit à la meilleure action...",
        subtitle = "Synthèse cognitive en cours"
    ),
    EXECUTING(
        standardizedCode = "Executing",
        frenchLabel = "Exécution Action",
        friendlyTitle = "Agit sur votre téléphone...",
        subtitle = "Exécution sécurisée de l'étape"
    ),
    WAITING_HITL(
        standardizedCode = "Waiting Approval",
        frenchLabel = "Attente Accord (HITL)",
        friendlyTitle = "Attend votre confirmation",
        subtitle = "Action sensible bloquée jusqu'à votre accord"
    ),
    PAUSED_SECURITY(
        standardizedCode = "Error",
        frenchLabel = "Bloqué Zero-Trust",
        friendlyTitle = "Accès refusé par vos réglages",
        subtitle = "Jeton d'accès ou permission non accordée"
    ),
    COMPLETED(
        standardizedCode = "Idle",
        frenchLabel = "Mission Accomplie",
        friendlyTitle = "Tâche terminée avec succès !",
        subtitle = "Objectif atteint et vérifié"
    ),
    ERROR(
        standardizedCode = "Error",
        frenchLabel = "Erreur",
        friendlyTitle = "Action interrompue",
        subtitle = "Une erreur ou un blocage est survenu"
    )
}

enum class SystemServiceType(
    val scopeKey: String,
    val friendlyTitle: String,
    val friendlyDescription: String,
    val technicalName: String
) {
    SCREEN_CAPTURE_SOM(
        scopeKey = "sys:screen_capture",
        friendlyTitle = "Vision de l'écran",
        friendlyDescription = "Permet à l'assistant de voir ce qui est affiché à l'écran pour vous guider.",
        technicalName = "MediaProjection & OmniParser SoM v2"
    ),
    ACCESSIBILITY_GUI(
        scopeKey = "sys:accessibility_gui",
        friendlyTitle = "Clics, saisie de texte & défilement",
        friendlyDescription = "Permet à l'assistant d'appuyer sur les boutons et d'écrire à votre place.",
        technicalName = "AccessibilityService Gesture & Input"
    ),
    APP_LAUNCHER_NAVIGATION(
        scopeKey = "sys:app_launcher",
        friendlyTitle = "Ouverture d'applications & Navigation",
        friendlyDescription = "Permet de lancer une application ou de revenir à l'écran d'accueil.",
        technicalName = "PackageManager Intent & Global Navigation"
    ),
    MCP_PERSONAL_VAULT(
        scopeKey = "sys:mcp_vault",
        friendlyTitle = "Carnet d'adresses, Agenda & Notes",
        friendlyDescription = "Accès rapide à vos contacts, rendez-vous et notes personnelles.",
        technicalName = "Local MCP Personal Data Bridge"
    ),
    NOTIFICATIONS_BAR(
        scopeKey = "sys:notifications",
        friendlyTitle = "Lecture des notifications",
        friendlyDescription = "Permet de lire les alertes et nouveaux messages reçus en haut de l'écran.",
        technicalName = "NotificationListenerService"
    ),
    CLIPBOARD_GUARD(
        scopeKey = "sys:clipboard",
        friendlyTitle = "Presse-papiers (Copier / Coller)",
        friendlyDescription = "Permet de copier un code ou un texte d'une application vers une autre.",
        technicalName = "ClipboardManager Secure Bridge"
    )
}

data class ScopedAccessToken(
    val tokenId: String,
    val agentId: String,
    val agentName: String,
    val allowedScreenPackages: Set<String>,
    val allowedControlPackages: Set<String>,
    val allowedSystemServices: Set<SystemServiceType>,
    val issuedAtFormatted: String = currentTimeFormatted(),
    val expiresAtFormatted: String = "Session Active (24h)",
    val isRevoked: Boolean = false
) {
    val activeScopes: List<String>
        get() {
            if (isRevoked) return emptyList()
            val serviceScopes = allowedSystemServices.map { it.scopeKey }
            val appScopes = (allowedScreenPackages + allowedControlPackages).sorted().mapNotNull { pkg ->
                val canRead = allowedScreenPackages.contains(pkg)
                val canWrite = allowedControlPackages.contains(pkg)
                when {
                    canRead && canWrite -> "app:$pkg:read_write"
                    canRead -> "app:$pkg:read_only"
                    canWrite -> "app:$pkg:write"
                    else -> null
                }
            }
            return serviceScopes + appScopes
        }

    val sha256Signature: String
        get() {
            val payload = "$tokenId|$agentId|revoked=$isRevoked|${activeScopes.joinToString(",")}"
            return try {
                val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
                digest.joinToString("") { "%02x".format(it) }.take(24)
            } catch (_: Exception) {
                payload.hashCode().toUInt().toString(16)
            }
        }

    fun hasSystemServiceScope(service: SystemServiceType): Boolean =
        !isRevoked && allowedSystemServices.contains(service)

    fun canReadApp(packageName: String): Boolean =
        !isRevoked &&
            allowedSystemServices.contains(SystemServiceType.SCREEN_CAPTURE_SOM) &&
            allowedScreenPackages.contains(packageName)

    fun canControlApp(packageName: String): Boolean =
        !isRevoked &&
            allowedSystemServices.contains(SystemServiceType.ACCESSIBILITY_GUI) &&
            allowedControlPackages.contains(packageName)
}

data class FriendlyMissionSuggestion(
    val id: String,
    val emojiTitle: String,
    val subtitle: String,
    val userPrompt: String,
    val targetScreenId: String,
    val recommendedAgentId: String
)

data class AutonomousAgentInstance(
    val id: String,
    val name: String,
    val roleBadge: String,
    val assignedModel: String = "gemini-4-argon",
    val status: AgentOperationalStatus = AgentOperationalStatus.IDLE,
    val userMission: String,
    val currentScreenId: String = "home_launcher",
    val stepsCompleted: Int = 0,
    val lastActionSummary: String = "Aucune action exécutée",
    val lastUpdatedFormatted: String = currentTimeFormatted(),
    val activeTargetOverlay: TargetedSomOverlay? = null,
    val liveTelemetry: LiveCognitiveTelemetry = LiveCognitiveTelemetry(),
    val cognitiveHistory: List<CognitiveStepResult> = emptyList(),
    val isAutoPilotRunning: Boolean = false
)

enum class TaskExecutionStatus(val frenchLabel: String) {
    IN_PROGRESS("En cours"),
    SUCCESS("Succès"),
    WAITING_HITL("Attente Accord"),
    BLOCKED_ACCESS("Bloqué (Accès)"),
    FAILED("Échec")
}

data class TaskHistoryEntry(
    val id: String,
    val agentId: String,
    val agentName: String,
    val missionPrompt: String,
    val status: TaskExecutionStatus,
    val stepsCount: Int,
    val timestampFormatted: String,
    val durationMs: Long,
    val appsAccessed: List<String>,
    val mcpCategoriesUsed: List<String>,
    val securityPosture: SecurityPosture,
    val summary: String,
    val actionTrace: List<String>
)

enum class GlobalAccessPolicyMode(
    val frenchTitle: String,
    val description: String
) {
    FULL_AUTONOMOUS_ACCESS(
        "Accès Complet (Tout Voir & Contrôler)",
        "Les assistants voient tous les écrans et interagissent avec toutes les applications autorisées (votre accord reste demandé pour les opérations sensibles)."
    ),
    SELECTIVE_ZERO_TRUST(
        "Personnalisé par Agent & par Application",
        "Chaque assistant dispose de son propre jeton d'accès (Scoped Token) limitant les applications et services système qu'il peut utiliser."
    ),
    OBSERVATION_ONLY(
        "Mode Lecture Seule (Voir sans toucher)",
        "Les assistants peuvent voir et résumer vos écrans, mais aucun clic ni saisie automatique n'est autorisé."
    ),
    LOCKDOWN(
        "Coupe-Circuit Total (Tout Bloquer)",
        "Révocation immédiate : aucun assistant ne peut lire l'écran ni interagir avec vos applications."
    )
}

enum class AppRiskClassification(val frenchLabel: String) {
    STANDARD("Standard"),
    PERSONAL_DATA("Vie Privée"),
    FINANCIAL_CRITICAL("Banque & Paiement"),
    SYSTEM_CORE("Système")
}

data class AppAccessPermission(
    val packageName: String,
    val appName: String,
    val categoryLabel: String,
    val isRealDeviceApp: Boolean = false,
    val linkedScreenId: String,
    val canSeeScreen: Boolean = true,
    val canInteractGui: Boolean = true,
    val requireHitlForActions: Boolean = false,
    val riskClassification: AppRiskClassification = AppRiskClassification.STANDARD,
    val accessCount: Int = 0,
    val lastAccessStatus: String = "Prêt • Accès autorisé"
)

data class SecurityAccessAuditEvent(
    val id: String,
    val timestampFormatted: String,
    val agentId: String,
    val agentName: String,
    val appName: String,
    val packageName: String,
    val operation: String,
    val granted: Boolean,
    val reason: String,
    val tokenBadge: String = "oat_live_verified"
)

fun currentTimeFormatted(): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
    return sdf.format(Date())
}

object MultiAgentDefaults {

    fun friendlySuggestions(): List<FriendlyMissionSuggestion> = listOf(
        FriendlyMissionSuggestion(
            id = "friendly_sms",
            emojiTitle = "Préparer un SMS",
            subtitle = "Vérifie le contact avec Safe Contact Resolver avant l'envoi",
            userPrompt = "Écris un SMS",
            targetScreenId = "messages_inbox",
            recommendedAgentId = "agent_alpha"
        ),
        FriendlyMissionSuggestion(
            id = "friendly_notes",
            emojiTitle = "Prendre une note",
            subtitle = "Enregistre une note ou un souvenir personnel",
            userPrompt = "Ouvre Notes",
            targetScreenId = "notes_editor",
            recommendedAgentId = "agent_beta"
        ),
        FriendlyMissionSuggestion(
            id = "friendly_vpn",
            emojiTitle = "Vérifier les paramètres réseau",
            subtitle = "Inspecte l'état de la connexion et de la sécurité",
            userPrompt = "Ouvre Paramètres",
            targetScreenId = "settings_vpn",
            recommendedAgentId = "agent_alpha"
        )
    )

    fun defaultAgents(): List<AutonomousAgentInstance> = listOf(
        AutonomousAgentInstance(
            id = "agent_alpha",
            name = "LIA • Alpha (Manager)",
            roleBadge = "Planificateur Stratégique & Manager-RAG",
            assignedModel = "gemini-3.8-flash",
            status = AgentOperationalStatus.IDLE,
            userMission = "",
            currentScreenId = "home_launcher",
            stepsCompleted = 0,
            lastActionSummary = "Maintient l'agenda des sous-objectifs abstraits (sans images brutes)"
        ),
        AutonomousAgentInstance(
            id = "agent_beta",
            name = "LIA • Beta (Operator)",
            roleBadge = "Décideur • AppFunctions, OmniParser v2 & Operator-RAG",
            assignedModel = "gemini-3.8-flash",
            status = AgentOperationalStatus.IDLE,
            userMission = "",
            currentScreenId = "messages_inbox",
            stepsCompleted = 0,
            lastActionSummary = "Voie Symbolique (@AppFunction/MCP) & Voie Visuelle Set-of-Mark"
        ),
        AutonomousAgentInstance(
            id = "agent_gamma",
            name = "LIA • Gamma (Reflector)",
            roleBadge = "Réflexion Multi-Échelle (Action/Trajectory/Global) & HITL",
            assignedModel = "gemini-3.8-flash",
            status = AgentOperationalStatus.IDLE,
            userMission = "",
            currentScreenId = "home_launcher",
            stepsCompleted = 0,
            lastActionSummary = "Vérification Three-Tier MobileUse & Anti-Lethal Trifecta actifs"
        )
    )

    fun defaultAgentTokens(
        agents: List<AutonomousAgentInstance> = defaultAgents(),
        permissions: List<AppAccessPermission> = defaultAppPermissions()
    ): Map<String, ScopedAccessToken> {
        val allPackages = permissions.map { it.packageName }.toSet()
        val nonFinancialPackages = permissions
            .filter { it.riskClassification != AppRiskClassification.FINANCIAL_CRITICAL }
            .map { it.packageName }
            .toSet()

        return agents.associate { agent ->
            val shortHash = UUID.nameUUIDFromBytes(agent.id.toByteArray()).toString().take(6)
            val isBeta = agent.id == "agent_beta"
            val allowedRead = allPackages
            val allowedWrite = if (isBeta) nonFinancialPackages else allPackages
            val services = if (isBeta) {
                setOf(
                    SystemServiceType.SCREEN_CAPTURE_SOM,
                    SystemServiceType.ACCESSIBILITY_GUI,
                    SystemServiceType.MCP_PERSONAL_VAULT,
                    SystemServiceType.NOTIFICATIONS_BAR
                )
            } else {
                SystemServiceType.entries.toSet()
            }

            agent.id to ScopedAccessToken(
                tokenId = "oat_live_${agent.id}_$shortHash",
                agentId = agent.id,
                agentName = agent.name,
                allowedScreenPackages = allowedRead,
                allowedControlPackages = allowedWrite,
                allowedSystemServices = services,
                issuedAtFormatted = currentTimeFormatted(),
                expiresAtFormatted = "Actif • Portée vérifiée",
                isRevoked = false
            )
        }
    }

    // Clean start: no pre-populated fake task history ("Synchronise les coordonnées de Camille...")
    fun defaultTaskHistory(): List<TaskHistoryEntry> = emptyList()

    fun defaultAppPermissions(): List<AppAccessPermission> = listOf(
        AppAccessPermission(
            packageName = "com.google.android.apps.nexuslauncher",
            appName = "Écran d'Accueil (Launcher)",
            categoryLabel = "Système & Navigation",
            isRealDeviceApp = false,
            linkedScreenId = "home_launcher",
            canSeeScreen = true,
            canInteractGui = true,
            requireHitlForActions = false,
            riskClassification = AppRiskClassification.SYSTEM_CORE
        ),
        AppAccessPermission(
            packageName = "com.google.android.apps.messaging",
            appName = "Messages & SMS",
            categoryLabel = "Communication Privée",
            isRealDeviceApp = false,
            linkedScreenId = "messages_inbox",
            canSeeScreen = true,
            canInteractGui = true,
            requireHitlForActions = false,
            riskClassification = AppRiskClassification.PERSONAL_DATA
        ),
        AppAccessPermission(
            packageName = "com.google.android.keep",
            appName = "Notes Personnelles",
            categoryLabel = "Productivité & Coffre",
            isRealDeviceApp = false,
            linkedScreenId = "notes_editor",
            canSeeScreen = true,
            canInteractGui = true,
            requireHitlForActions = false,
            riskClassification = AppRiskClassification.PERSONAL_DATA
        ),
        AppAccessPermission(
            packageName = "com.android.settings",
            appName = "Paramètres Système",
            categoryLabel = "Configuration Téléphone",
            isRealDeviceApp = false,
            linkedScreenId = "settings_vpn",
            canSeeScreen = true,
            canInteractGui = true,
            requireHitlForActions = false,
            riskClassification = AppRiskClassification.SYSTEM_CORE
        ),
        AppAccessPermission(
            packageName = "com.google.android.calendar",
            appName = "Agenda & Événements",
            categoryLabel = "Organisation",
            isRealDeviceApp = false,
            linkedScreenId = "calendar_agenda",
            canSeeScreen = true,
            canInteractGui = true,
            requireHitlForActions = false,
            riskClassification = AppRiskClassification.PERSONAL_DATA
        ),
        AppAccessPermission(
            packageName = "com.google.android.gm",
            appName = "Gmail (E-mails)",
            categoryLabel = "Messagerie Cloud",
            isRealDeviceApp = false,
            linkedScreenId = "gmail_client",
            canSeeScreen = true,
            canInteractGui = true,
            requireHitlForActions = false,
            riskClassification = AppRiskClassification.PERSONAL_DATA
        ),
        AppAccessPermission(
            packageName = "com.android.chrome",
            appName = "Google Chrome (Internet)",
            categoryLabel = "Navigateur Web",
            isRealDeviceApp = false,
            linkedScreenId = "chrome_browser",
            canSeeScreen = true,
            canInteractGui = true,
            requireHitlForActions = false,
            riskClassification = AppRiskClassification.STANDARD
        )
    )

    fun extraBuiltInScreens(): Map<String, VirtualDeviceScreen> = mapOf(
        "calendar_agenda" to VirtualDeviceScreen(
            screenId = "calendar_agenda",
            appName = "Agenda",
            packageName = "com.google.android.calendar",
            screenTitle = "Agenda • Vos événements",
            statusBanner = "Synchronisé avec LIA",
            elements = listOf(
                SomElement(22, "Aucun événement pré-rempli", SomElementType.TEXT_BLOCK, 0.06f, 0.10f, 0.94f, 0.34f, "Votre agenda est vierge."),
                SomElement(24, "Champ Nouvel Événement", SomElementType.TEXT_FIELD, 0.06f, 0.42f, 0.94f, 0.60f, "Titre de l'événement..."),
                SomElement(25, "Bouton Planifier l'événement", SomElementType.BUTTON, 0.06f, 0.68f, 0.94f, 0.84f, "Enregistrer dans l'Agenda")
            )
        ),
        "gmail_client" to VirtualDeviceScreen(
            screenId = "gmail_client",
            appName = "Gmail",
            packageName = "com.google.android.gm",
            screenTitle = "Gmail • Boîte de Réception",
            statusBanner = "Boîte de réception propre",
            elements = listOf(
                SomElement(26, "Boîte de réception", SomElementType.TEXT_BLOCK, 0.06f, 0.10f, 0.94f, 0.36f, "Aucun message non lu."),
                SomElement(27, "Champ Nouveau Message", SomElementType.TEXT_FIELD, 0.06f, 0.40f, 0.94f, 0.64f, ""),
                SomElement(28, "Bouton Envoyer E-mail", SomElementType.DANGER_ACTION, 0.06f, 0.70f, 0.94f, 0.86f, "Envoyer E-mail", isSensitiveFinancialOrDestructive = true)
            )
        ),
        "chrome_browser" to VirtualDeviceScreen(
            screenId = "chrome_browser",
            appName = "Chrome",
            packageName = "com.android.chrome",
            screenTitle = "Chrome • Navigation Web",
            statusBanner = "Navigation prête",
            elements = listOf(
                SomElement(29, "Barre d'adresse URL", SomElementType.TEXT_FIELD, 0.06f, 0.08f, 0.94f, 0.20f, "https://www.google.fr"),
                SomElement(30, "Page d'accueil", SomElementType.TEXT_BLOCK, 0.06f, 0.24f, 0.94f, 0.62f, "Recherche Google prête."),
                SomElement(31, "Bouton Actualiser", SomElementType.BUTTON, 0.06f, 0.68f, 0.94f, 0.85f, "Rafraîchir la page")
            )
        )
    )

    /**
     * Discovers all real installed launcher applications on the Android device/emulator
     * using the Play-compliant <queries> MAIN/LAUNCHER intent filter, and merges them with
     * the existing permission list and virtual screens.
     */
    fun discoverDeviceAppsAndScreens(
        context: Context,
        currentPermissions: List<AppAccessPermission>,
        currentScreens: Map<String, VirtualDeviceScreen>
    ): Pair<List<AppAccessPermission>, Map<String, VirtualDeviceScreen>> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolvedInfos = try {
            pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        } catch (_: Exception) {
            emptyList()
        }

        val mergedPermissions = currentPermissions.toMutableList()
        val mergedScreens = (currentScreens + extraBuiltInScreens()).toMutableMap()

        var dynamicElementBaseId = 40
        for (resolveInfo in resolvedInfos) {
            val pkg = resolveInfo.activityInfo?.packageName ?: continue
            if (pkg == context.packageName) continue
            if (mergedPermissions.any { it.packageName == pkg }) continue

            val label = try {
                resolveInfo.loadLabel(pm).toString().ifBlank { pkg.substringAfterLast('.') }
            } catch (_: Exception) {
                pkg.substringAfterLast('.')
            }

            val screenId = "device_app_${pkg.replace('.', '_')}"
            val isSensitiveApp = pkg.contains("bank", ignoreCase = true) ||
                pkg.contains("wallet", ignoreCase = true) ||
                pkg.contains("pay", ignoreCase = true)

            val permission = AppAccessPermission(
                packageName = pkg,
                appName = label,
                categoryLabel = "App installée sur cet appareil",
                isRealDeviceApp = true,
                linkedScreenId = screenId,
                canSeeScreen = true,
                canInteractGui = true,
                requireHitlForActions = isSensitiveApp,
                riskClassification = if (isSensitiveApp) {
                    AppRiskClassification.FINANCIAL_CRITICAL
                } else {
                    AppRiskClassification.STANDARD
                }
            )
            mergedPermissions.add(permission)

            if (!mergedScreens.containsKey(screenId)) {
                val baseId = dynamicElementBaseId
                dynamicElementBaseId += 4
                mergedScreens[screenId] = VirtualDeviceScreen(
                    screenId = screenId,
                    appName = label,
                    packageName = pkg,
                    screenTitle = "$label • Application de votre téléphone",
                    statusBanner = "Protégé par Jeton d'Accès • Package: $pkg",
                    elements = listOf(
                        SomElement(
                            id = baseId,
                            label = "Barre de recherche / Action dans $label",
                            type = SomElementType.TEXT_FIELD,
                            normLeft = 0.06f,
                            normTop = 0.09f,
                            normRight = 0.94f,
                            normBottom = 0.22f,
                            currentText = "Rechercher ou saisir dans $label..."
                        ),
                        SomElement(
                            id = baseId + 1,
                            label = "Vue principale de $label",
                            type = SomElementType.TEXT_BLOCK,
                            normLeft = 0.06f,
                            normTop = 0.26f,
                            normRight = 0.94f,
                            normBottom = 0.60f,
                            currentText = "Application Android installée ($pkg).\nVous pouvez autoriser ou bloquer chaque assistant pour cette application dans l'onglet Accès Apps."
                        ),
                        SomElement(
                            id = baseId + 2,
                            label = "Action Principale ($label)",
                            type = SomElementType.BUTTON,
                            normLeft = 0.06f,
                            normTop = 0.65f,
                            normRight = 0.94f,
                            normBottom = 0.78f,
                            currentText = "Interagir avec $label"
                        ),
                        SomElement(
                            id = baseId + 3,
                            label = "Retour Écran d'Accueil",
                            type = SomElementType.BUTTON,
                            normLeft = 0.06f,
                            normTop = 0.81f,
                            normRight = 0.94f,
                            normBottom = 0.91f,
                            currentText = "Retourner à l'accueil",
                            targetScreenId = "home_launcher"
                        )
                    )
                )
            }
        }

        return mergedPermissions to mergedScreens
    }
}
