package com.example.service

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.example.data.LiaAppLaunchAction
import com.example.data.LiaMemoryAndHistoryStore
import com.example.data.LiaUniversalAppsAndGoogleHub
import com.example.model.currentTimeFormatted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Paramètre d'un outil dynamique MCP (Model Context Protocol), présenté en langage naturel.
 */
data class McpToolParameter(
    val name: String,
    val labelFr: String,
    val placeholderFr: String,
    val required: Boolean = true
)

/**
 * Outil dynamique généré pour une application installée ou un service Google via l'architecture MCP.
 */
data class McpDynamicTool(
    val toolId: String,
    val connectorId: String,
    val appName: String,
    val packageName: String,
    val titleFr: String,
    val naturalDescriptionFr: String,
    val emoji: String,
    val sampleNaturalPhrase: String,
    val parameters: List<McpToolParameter> = emptyList()
)

/**
 * Connecteur MCP représentant une application détectée sur l'appareil ou un service Google
 * avec ses outils dynamiques prêts à être appelés par LIA en langage naturel.
 */
data class McpAppConnector(
    val connectorId: String,
    val appName: String,
    val packageName: String,
    val iconEmoji: String,
    val categoryFr: String,
    val isInstalledOnDevice: Boolean,
    val statusLabelFr: String,
    val naturalSummaryFr: String,
    val accentHex: Long,
    val tools: List<McpDynamicTool>
)

/**
 * Résultat d'exécution d'un outil dynamique MCP par LIA.
 */
data class McpToolExecutionOutcome(
    val connector: McpAppConnector,
    val tool: McpDynamicTool,
    val executed: Boolean,
    val naturalReplyFr: String,
    val badgeFr: String,
    val appLaunchAction: LiaAppLaunchAction? = null,
    val targetVirtualScreenId: String? = null,
    val quickFollowUps: List<String> = emptyList(),
    val timestamp: String = currentTimeFormatted()
)

data class McpConnectorRegistryState(
    val connectors: List<McpAppConnector> = emptyList(),
    val lastExecution: McpToolExecutionOutcome? = null,
    val totalDynamicToolsCount: Int = 0,
    val lastRefreshedAt: String = ""
)

/**
 * Architecture de Connecteurs MCP (Model Context Protocol) de LIA :
 * - Détecte dynamiquement les applications installées sur le téléphone (WhatsApp, Messenger, Google Keep,
 *   Gmail, Google Agenda, Maps, YouTube, Spotify, Drive, Telegram, Steam, etc.) ainsi que toute nouvelle
 *   application installée par l'utilisateur.
 * - Génère pour chaque application des outils dynamiques (« tools ») adaptés à ses capacités natives
 *   (Intents Android, Deep-Links, ContentProviders, partage direct, recherche interne).
 * - Permet à LIA d'interagir nativement avec ces applications à partir du langage naturel sans aucun jargon.
 */
object LiaMcpConnectorRegistry {

    private val _state = MutableStateFlow(McpConnectorRegistryState())
    val state: StateFlow<McpConnectorRegistryState> = _state.asStateFlow()

    /**
     * Rafraîchit la liste complète des connecteurs MCP et génère les outils dynamiques
     * pour chaque application installée détectée sur le téléphone.
     */
    fun refreshConnectors(context: Context? = LiaMemoryAndHistoryStore.getApplicationContextOrNull()): List<McpAppConnector> {
        val installedApps = if (context != null) {
            LiaUniversalAppsAndGoogleHub.refreshAllInstalledPhoneApps(context)
        } else {
            LiaUniversalAppsAndGoogleHub.state.value.installedPhoneApps
        }
        val installedPackages = installedApps.map { it.packageName.lowercase() }.toSet()

        fun isPkgInstalled(pkg: String): Boolean {
            if (installedPackages.contains(pkg.lowercase())) return true
            if (context == null) return true
            return runCatching {
                context.packageManager.getLaunchIntentForPackage(pkg) != null
            }.getOrDefault(false)
        }

        val coreConnectors = buildCoreNativeConnectors(::isPkgInstalled)
        val corePackages = coreConnectors.map { it.packageName.lowercase() }.toSet()

        // Génère dynamiquement un connecteur MCP + 3 outils natifs pour CHAQUE autre application installée sur l'appareil
        val dynamicInstalledConnectors = installedApps
            .filter { it.packageName.lowercase() !in corePackages }
            .map { appItem ->
                val cleanId = appItem.packageName.lowercase().replace('.', '_')
                val connectorId = "mcp.dynamic.$cleanId"
                McpAppConnector(
                    connectorId = connectorId,
                    appName = appItem.appLabel,
                    packageName = appItem.packageName,
                    iconEmoji = appItem.emoji,
                    categoryFr = if (appItem.isGoogleApp) "Services Google" else "Applications installées",
                    isInstalledOnDevice = true,
                    statusLabelFr = if (appItem.isNewlyInstalledAfterBoot) "Nouveau • Connecté" else "Connecté",
                    naturalSummaryFr = "Ouverture directe, recherche et partage vers ${appItem.appLabel}",
                    accentHex = if (appItem.isGoogleApp) 0xFF8AB4F8 else 0xFF81C995,
                    tools = listOf(
                        McpDynamicTool(
                            toolId = "$connectorId.open",
                            connectorId = connectorId,
                            appName = appItem.appLabel,
                            packageName = appItem.packageName,
                            titleFr = "Ouvrir ${appItem.appLabel}",
                            naturalDescriptionFr = "Lance immédiatement ${appItem.appLabel}",
                            emoji = "🚀",
                            sampleNaturalPhrase = "Ouvre ${appItem.appLabel}"
                        ),
                        McpDynamicTool(
                            toolId = "$connectorId.search",
                            connectorId = connectorId,
                            appName = appItem.appLabel,
                            packageName = appItem.packageName,
                            titleFr = "Rechercher dans ${appItem.appLabel}",
                            naturalDescriptionFr = "Lance une recherche directe dans ${appItem.appLabel}",
                            emoji = "🔎",
                            sampleNaturalPhrase = "Cherche dans ${appItem.appLabel}",
                            parameters = listOf(
                                McpToolParameter("query", "Votre recherche", "Que cherchez-vous ?")
                            )
                        ),
                        McpDynamicTool(
                            toolId = "$connectorId.share",
                            connectorId = connectorId,
                            appName = appItem.appLabel,
                            packageName = appItem.packageName,
                            titleFr = "Envoyer du texte vers ${appItem.appLabel}",
                            naturalDescriptionFr = "Partage directement une note ou un texte dans ${appItem.appLabel}",
                            emoji = "📤",
                            sampleNaturalPhrase = "Partage ce texte dans ${appItem.appLabel}",
                            parameters = listOf(
                                McpToolParameter("text", "Contenu à envoyer", "Écrivez votre texte…")
                            )
                        )
                    )
                )
            }

        val allConnectors = coreConnectors + dynamicInstalledConnectors
        val totalTools = allConnectors.sumOf { it.tools.size }
        _state.update {
            it.copy(
                connectors = allConnectors,
                totalDynamicToolsCount = totalTools,
                lastRefreshedAt = currentTimeFormatted()
            )
        }
        return allConnectors
    }

    private fun buildCoreNativeConnectors(isInstalled: (String) -> Boolean): List<McpAppConnector> {
        return listOf(
            // 1. MESSENGER
            McpAppConnector(
                connectorId = "mcp.messenger",
                appName = "Messenger",
                packageName = "com.facebook.orca",
                iconEmoji = "💙",
                categoryFr = "Messagerie",
                isInstalledOnDevice = isInstalled("com.facebook.orca"),
                statusLabelFr = "Connecté",
                naturalSummaryFr = "Ouvrir une discussion, écrire à un proche ou partager un message",
                accentHex = 0xFF4285F4,
                tools = listOf(
                    McpDynamicTool(
                        toolId = "messenger.open_thread",
                        connectorId = "mcp.messenger",
                        appName = "Messenger",
                        packageName = "com.facebook.orca",
                        titleFr = "Ouvrir une discussion",
                        naturalDescriptionFr = "Ouvre directement vos discussions Messenger ou un contact précis",
                        emoji = "💬",
                        sampleNaturalPhrase = "Ouvre une conversation sur Messenger",
                        parameters = listOf(
                            McpToolParameter("contact", "Contact (optionnel)", "Ex : Marie, Thomas…", required = false)
                        )
                    ),
                    McpDynamicTool(
                        toolId = "messenger.send_message",
                        connectorId = "mcp.messenger",
                        appName = "Messenger",
                        packageName = "com.facebook.orca",
                        titleFr = "Envoyer un message Messenger",
                        naturalDescriptionFr = "Prépare et ouvre Messenger avec votre message prêt à envoyer",
                        emoji = "✉️",
                        sampleNaturalPhrase = "Envoie Salut tu es dispo sur Messenger",
                        parameters = listOf(
                            McpToolParameter("contact", "Destinataire", "Ex : Marie"),
                            McpToolParameter("message", "Message", "Ex : J'arrive dans 10 minutes")
                        )
                    )
                )
            ),

            // 2. WHATSAPP
            McpAppConnector(
                connectorId = "mcp.whatsapp",
                appName = "WhatsApp",
                packageName = "com.whatsapp",
                iconEmoji = "🟢",
                categoryFr = "Messagerie",
                isInstalledOnDevice = isInstalled("com.whatsapp"),
                statusLabelFr = "Connecté",
                naturalSummaryFr = "Discussions instantanées et messages directs avec vos contacts",
                accentHex = 0xFF81C995,
                tools = listOf(
                    McpDynamicTool(
                        toolId = "whatsapp.send_message",
                        connectorId = "mcp.whatsapp",
                        appName = "WhatsApp",
                        packageName = "com.whatsapp",
                        titleFr = "Envoyer un WhatsApp",
                        naturalDescriptionFr = "Ouvre la discussion WhatsApp du contact avec votre message",
                        emoji = "🟢",
                        sampleNaturalPhrase = "Envoie Bonjour sur WhatsApp",
                        parameters = listOf(
                            McpToolParameter("contact", "Contact", "Ex : Maman, Thomas…"),
                            McpToolParameter("message", "Message", "Ex : On se voit ce soir ?")
                        )
                    ),
                    McpDynamicTool(
                        toolId = "whatsapp.open_conversation",
                        connectorId = "mcp.whatsapp",
                        appName = "WhatsApp",
                        packageName = "com.whatsapp",
                        titleFr = "Ouvrir une discussion WhatsApp",
                        naturalDescriptionFr = "Accède directement aux discussions WhatsApp",
                        emoji = "💬",
                        sampleNaturalPhrase = "Ouvre une conversation sur WhatsApp",
                        parameters = listOf(
                            McpToolParameter("contact", "Contact (optionnel)", "Nom du contact", required = false)
                        )
                    )
                )
            ),

            // 3. GOOGLE KEEP
            McpAppConnector(
                connectorId = "mcp.keep",
                appName = "Google Keep",
                packageName = "com.google.android.keep",
                iconEmoji = "💡",
                categoryFr = "Notes & Idées",
                isInstalledOnDevice = isInstalled("com.google.android.keep"),
                statusLabelFr = "Synchronisé",
                naturalSummaryFr = "Créer des notes rapides, listes de courses et retrouver vos idées",
                accentHex = 0xFFFDD663,
                tools = listOf(
                    McpDynamicTool(
                        toolId = "keep.create_note",
                        connectorId = "mcp.keep",
                        appName = "Google Keep",
                        packageName = "com.google.android.keep",
                        titleFr = "Créer une note dans Keep",
                        naturalDescriptionFr = "Enregistre une nouvelle note dans Google Keep et dans la mémoire de LIA",
                        emoji = "📝",
                        sampleNaturalPhrase = "Crée une note dans Keep : Acheter du café et des fruits",
                        parameters = listOf(
                            McpToolParameter("content", "Contenu de la note", "Ex : Acheter du café, appeler le médecin…")
                        )
                    ),
                    McpDynamicTool(
                        toolId = "keep.search_notes",
                        connectorId = "mcp.keep",
                        appName = "Google Keep",
                        packageName = "com.google.android.keep",
                        titleFr = "Retrouver mes notes Keep",
                        naturalDescriptionFr = "Cherche instantanément dans vos notes et listes enregistrées",
                        emoji = "🔎",
                        sampleNaturalPhrase = "Retrouve mes notes dans Google Keep",
                        parameters = listOf(
                            McpToolParameter("query", "Mot-clé (optionnel)", "Ex : courses, code, idée…", required = false)
                        )
                    ),
                    McpDynamicTool(
                        toolId = "keep.create_checklist",
                        connectorId = "mcp.keep",
                        appName = "Google Keep",
                        packageName = "com.google.android.keep",
                        titleFr = "Nouvelle liste à cocher",
                        naturalDescriptionFr = "Crée une liste de choses à faire ou de courses dans Keep",
                        emoji = "☑️",
                        sampleNaturalPhrase = "Crée une liste dans Keep : Pain, Lait, Oeufs",
                        parameters = listOf(
                            McpToolParameter("items", "Éléments de la liste", "Ex : Pain, lait, fruits")
                        )
                    )
                )
            ),

            // 4. GOOGLE AGENDA (CALENDAR)
            McpAppConnector(
                connectorId = "mcp.calendar",
                appName = "Google Agenda",
                packageName = "com.google.android.calendar",
                iconEmoji = "📅",
                categoryFr = "Services Google",
                isInstalledOnDevice = isInstalled("com.google.android.calendar"),
                statusLabelFr = "Synchronisé",
                naturalSummaryFr = "Consulter votre planning et ajouter des rendez-vous en une phrase",
                accentHex = 0xFF8AB4F8,
                tools = listOf(
                    McpDynamicTool(
                        toolId = "calendar.create_event",
                        connectorId = "mcp.calendar",
                        appName = "Google Agenda",
                        packageName = "com.google.android.calendar",
                        titleFr = "Ajouter un rendez-vous",
                        naturalDescriptionFr = "Crée un événement dans Google Agenda",
                        emoji = "➕",
                        sampleNaturalPhrase = "Ajoute à Google Agenda : Dentiste demain à 14h",
                        parameters = listOf(
                            McpToolParameter("title", "Titre et horaire", "Ex : Dîner avec Julie demain à 20h")
                        )
                    ),
                    McpDynamicTool(
                        toolId = "calendar.view_schedule",
                        connectorId = "mcp.calendar",
                        appName = "Google Agenda",
                        packageName = "com.google.android.calendar",
                        titleFr = "Voir mes prochains RDV",
                        naturalDescriptionFr = "Lit vos prochains événements inscrits à l'agenda",
                        emoji = "📅",
                        sampleNaturalPhrase = "Quels sont mes prochains rendez-vous dans mon agenda ?"
                    )
                )
            ),

            // 5. GMAIL
            McpAppConnector(
                connectorId = "mcp.gmail",
                appName = "Gmail",
                packageName = "com.google.android.gm",
                iconEmoji = "📧",
                categoryFr = "Services Google",
                isInstalledOnDevice = isInstalled("com.google.android.gm"),
                statusLabelFr = "Synchronisé",
                naturalSummaryFr = "Rédiger un e-mail pré-rempli ou ouvrir votre boîte de réception",
                accentHex = 0xFFF28B82,
                tools = listOf(
                    McpDynamicTool(
                        toolId = "gmail.compose_email",
                        connectorId = "mcp.gmail",
                        appName = "Gmail",
                        packageName = "com.google.android.gm",
                        titleFr = "Écrire un e-mail",
                        naturalDescriptionFr = "Prépare un e-mail directement dans Gmail",
                        emoji = "✍️",
                        sampleNaturalPhrase = "Envoie un mail sur Gmail : Compte-rendu de réunion",
                        parameters = listOf(
                            McpToolParameter("subject", "Sujet ou message", "Ex : Confirmation de rendez-vous")
                        )
                    ),
                    McpDynamicTool(
                        toolId = "gmail.open_inbox",
                        connectorId = "mcp.gmail",
                        appName = "Gmail",
                        packageName = "com.google.android.gm",
                        titleFr = "Ouvrir ma boîte Gmail",
                        naturalDescriptionFr = "Ouvre directement vos e-mails récents",
                        emoji = "📬",
                        sampleNaturalPhrase = "Ouvre Gmail"
                    )
                )
            ),

            // 6. GOOGLE MAPS
            McpAppConnector(
                connectorId = "mcp.maps",
                appName = "Google Maps",
                packageName = "com.google.android.apps.maps",
                iconEmoji = "🗺️",
                categoryFr = "Services Google",
                isInstalledOnDevice = isInstalled("com.google.android.apps.maps"),
                statusLabelFr = "Prêt",
                naturalSummaryFr = "Lancer un itinéraire GPS ou chercher un lieu à proximité",
                accentHex = 0xFF81C995,
                tools = listOf(
                    McpDynamicTool(
                        toolId = "maps.navigate_to",
                        connectorId = "mcp.maps",
                        appName = "Google Maps",
                        packageName = "com.google.android.apps.maps",
                        titleFr = "Lancer un itinéraire",
                        naturalDescriptionFr = "Démarre le guidage GPS vers la destination",
                        emoji = "🧭",
                        sampleNaturalPhrase = "Itinéraire vers la gare sur Google Maps",
                        parameters = listOf(
                            McpToolParameter("destination", "Destination", "Ex : Gare de Lyon, Pharmacie…")
                        )
                    ),
                    McpDynamicTool(
                        toolId = "maps.search_place",
                        connectorId = "mcp.maps",
                        appName = "Google Maps",
                        packageName = "com.google.android.apps.maps",
                        titleFr = "Chercher un lieu autour de moi",
                        naturalDescriptionFr = "Trouve des restaurants, commerces ou adresses sur Maps",
                        emoji = "📍",
                        sampleNaturalPhrase = "Cherche un restaurant italien sur Google Maps",
                        parameters = listOf(
                            McpToolParameter("query", "Lieu recherché", "Ex : Boulangerie, Cinéma…")
                        )
                    )
                )
            ),

            // 7. YOUTUBE & MUSIQUE (SPOTIFY)
            McpAppConnector(
                connectorId = "mcp.youtube",
                appName = "YouTube",
                packageName = "com.google.android.youtube",
                iconEmoji = "▶️",
                categoryFr = "Médias & Musique",
                isInstalledOnDevice = isInstalled("com.google.android.youtube"),
                statusLabelFr = "Prêt",
                naturalSummaryFr = "Rechercher et lancer une vidéo ou de la musique sur YouTube",
                accentHex = 0xFFF28B82,
                tools = listOf(
                    McpDynamicTool(
                        toolId = "youtube.search_video",
                        connectorId = "mcp.youtube",
                        appName = "YouTube",
                        packageName = "com.google.android.youtube",
                        titleFr = "Chercher une vidéo",
                        naturalDescriptionFr = "Lance la recherche vidéo directement dans YouTube",
                        emoji = "🎬",
                        sampleNaturalPhrase = "Cherche musique lo-fi sur YouTube",
                        parameters = listOf(
                            McpToolParameter("query", "Vidéo ou artiste", "Ex : Musique relaxante, tutoriel…")
                        )
                    )
                )
            ),

            McpAppConnector(
                connectorId = "mcp.spotify",
                appName = "Spotify",
                packageName = "com.spotify.music",
                iconEmoji = "🎵",
                categoryFr = "Médias & Musique",
                isInstalledOnDevice = isInstalled("com.spotify.music"),
                statusLabelFr = "Prêt",
                naturalSummaryFr = "Écouter un artiste, un titre ou une ambiance musicale",
                accentHex = 0xFF81C995,
                tools = listOf(
                    McpDynamicTool(
                        toolId = "spotify.play_music",
                        connectorId = "mcp.spotify",
                        appName = "Spotify",
                        packageName = "com.spotify.music",
                        titleFr = "Lancer de la musique",
                        naturalDescriptionFr = "Joue immédiatement un titre ou un style musical",
                        emoji = "🎧",
                        sampleNaturalPhrase = "Joue du jazz sur Spotify",
                        parameters = listOf(
                            McpToolParameter("query", "Titre, artiste ou style", "Ex : Daft Punk, Jazz, Focus…")
                        )
                    )
                )
            ),

            // 8. GOOGLE DRIVE
            McpAppConnector(
                connectorId = "mcp.drive",
                appName = "Google Drive",
                packageName = "com.google.android.apps.docs",
                iconEmoji = "📁",
                categoryFr = "Services Google",
                isInstalledOnDevice = isInstalled("com.google.android.apps.docs"),
                statusLabelFr = "Synchronisé",
                naturalSummaryFr = "Retrouver vos documents, PDF et feuilles de calcul",
                accentHex = 0xFF8AB4F8,
                tools = listOf(
                    McpDynamicTool(
                        toolId = "drive.search_files",
                        connectorId = "mcp.drive",
                        appName = "Google Drive",
                        packageName = "com.google.android.apps.docs",
                        titleFr = "Chercher mes fichiers Drive",
                        naturalDescriptionFr = "Retrouve vos fichiers et documents dans Drive",
                        emoji = "📂",
                        sampleNaturalPhrase = "Cherche mes fichiers dans Google Drive",
                        parameters = listOf(
                            McpToolParameter("query", "Nom du document (optionnel)", "Ex : Facture, CV…", required = false)
                        )
                    )
                )
            ),

            // 9. TELEGRAM
            McpAppConnector(
                connectorId = "mcp.telegram",
                appName = "Telegram",
                packageName = "org.telegram.messenger",
                iconEmoji = "✈️",
                categoryFr = "Messagerie",
                isInstalledOnDevice = isInstalled("org.telegram.messenger"),
                statusLabelFr = "Connecté",
                naturalSummaryFr = "Ouvrir un canal ou envoyer un message sur Telegram",
                accentHex = 0xFF8AB4F8,
                tools = listOf(
                    McpDynamicTool(
                        toolId = "telegram.open_chat",
                        connectorId = "mcp.telegram",
                        appName = "Telegram",
                        packageName = "org.telegram.messenger",
                        titleFr = "Ouvrir une discussion Telegram",
                        naturalDescriptionFr = "Lance Telegram directement sur vos échanges",
                        emoji = "✈️",
                        sampleNaturalPhrase = "Ouvre une conversation sur Telegram"
                    )
                )
            )
        )
    }

    /**
     * Analyse une phrase en langage naturel pour détecter si elle correspond à l'utilisation
     * d'un connecteur d'application (ex: créer une note dans Keep, envoyer sur WhatsApp/Messenger,
     * chercher un lieu sur Maps, ajouter à l'agenda) et exécute l'outil dynamique correspondant.
     */
    fun tryMatchAndExecuteNaturalIntent(
        context: Context?,
        rawUserText: String
    ): McpToolExecutionOutcome? {
        val cleaned = rawUserText.trim()
        val lower = cleaned.lowercase()
        if (cleaned.isBlank()) return null

        val connectors = if (_state.value.connectors.isEmpty()) {
            refreshConnectors(context)
        } else {
            _state.value.connectors
        }

        // 1. Création de note ou de liste dans Google Keep
        val isKeepWriteIntent = (lower.contains("keep") || lower.contains("google keep")) &&
            (lower.startsWith("crée") || lower.startsWith("cree") ||
                lower.startsWith("ajoute") || lower.startsWith("note") ||
                lower.startsWith("écris") || lower.startsWith("ecris") ||
                lower.startsWith("enregistre") || lower.contains("liste")) &&
            !lower.startsWith("retrouve") && !lower.startsWith("cherche") && !lower.startsWith("lis")

        if (isKeepWriteIntent) {
            val keepConnector = connectors.firstOrNull { it.connectorId == "mcp.keep" } ?: return null
            val isChecklist = lower.contains("liste")
            val tool = keepConnector.tools.firstOrNull {
                it.toolId == if (isChecklist) "keep.create_checklist" else "keep.create_note"
            } ?: keepConnector.tools.first()

            val extractedContent = cleaned
                .replace(Regex("(?i)^(crée|cree|ajoute|enregistre|écris|ecris|mets)\\s+(une\\s+)?(nouvelle\\s+)?(note|liste|idée|idee)(\\s+de\\s+courses)?\\s+(dans|sur)\\s+(google\\s+)?keep\\s*[:,-]?\\s*"), "")
                .replace(Regex("(?i)^note\\s+(dans|sur)\\s+(google\\s+)?keep\\s*[:,-]?\\s*"), "")
                .trim()
                .ifBlank { cleaned }

            return executeTool(
                context = context,
                toolId = tool.toolId,
                arguments = mapOf("content" to extractedContent, "items" to extractedContent)
            )
        }

        // 2. Recherche ou partage vers une application installée détectée dynamiquement
        val shareRegex = Regex("(?i)^(?:partage|envoie\\s+ce\\s+texte)\\s+(.+?)\\s+(?:dans|sur|vers)\\s+([a-zA-Z0-9À-ÿ\\s._-]+)$")
        val shareMatch = shareRegex.find(cleaned)
        if (shareMatch != null) {
            val contentToShare = shareMatch.groupValues[1].trim()
            val targetAppName = shareMatch.groupValues[2].trim()
            val matchedConnector = connectors.firstOrNull {
                it.appName.equals(targetAppName, ignoreCase = true) ||
                    it.appName.lowercase().contains(targetAppName.lowercase())
            }
            if (matchedConnector != null) {
                val shareTool = matchedConnector.tools.firstOrNull { it.toolId.endsWith(".share") }
                    ?: matchedConnector.tools.first()
                return executeTool(
                    context = context,
                    toolId = shareTool.toolId,
                    arguments = mapOf("text" to contentToShare, "message" to contentToShare)
                )
            }
        }

        return null
    }

    /**
     * Exécute un outil dynamique MCP identifié par son `toolId` avec ses paramètres naturels.
     */
    fun executeTool(
        context: Context? = LiaMemoryAndHistoryStore.getApplicationContextOrNull(),
        toolId: String,
        arguments: Map<String, String> = emptyMap()
    ): McpToolExecutionOutcome? {
        val connectors = if (_state.value.connectors.isEmpty()) {
            refreshConnectors(context)
        } else {
            _state.value.connectors
        }
        val connector = connectors.firstOrNull { c -> c.tools.any { it.toolId == toolId } } ?: return null
        val tool = connector.tools.first { it.toolId == toolId }

        val outcome: McpToolExecutionOutcome = when (toolId) {
            "keep.create_note", "keep.create_checklist" -> {
                val contentText = (arguments["content"] ?: arguments["items"] ?: "").trim()
                    .ifBlank { "Note rapide créée avec LIA" }
                val noteTitle = if (toolId == "keep.create_checklist") {
                    "Liste Keep : ${contentText.take(28)}"
                } else {
                    "Note Keep : ${contentText.take(28)}"
                }

                // Sauvegarde immédiate dans les souvenirs de LIA + ouverture/partage vers Google Keep
                LiaMemoryAndHistoryStore.upsertSouvenir(
                    type = "NOTE_VOCALE",
                    subjectKey = "keep_${System.currentTimeMillis()}",
                    title = noteTitle,
                    detailValue = contentText,
                    searchTags = "keep,note,liste,${contentText.lowercase().take(40)}"
                )

                if (context != null) {
                    launchKeepNoteIntent(context, noteTitle, contentText)
                }

                McpToolExecutionOutcome(
                    connector = connector,
                    tool = tool,
                    executed = true,
                    naturalReplyFr = "💡 C'est noté dans **Google Keep** :\n« **$contentText** »",
                    badgeFr = "💡 Synchronisé avec Google Keep",
                    appLaunchAction = LiaAppLaunchAction(
                        appLabel = "Google Keep",
                        packageName = "com.google.android.keep",
                        targetScreenId = "notes_editor"
                    ),
                    targetVirtualScreenId = "notes_editor",
                    quickFollowUps = listOf(
                        "🔎 Retrouve mes notes dans Google Keep",
                        "💡 Crée une note dans Keep : Idée projet",
                        "📅 Ajoute un rappel dans l'agenda"
                    )
                )
            }

            "keep.search_notes" -> {
                val query = arguments["query"].orEmpty().trim()
                val searchRes = LiaCrossAppKnowledgeResolver.searchAcrossAllPhoneAppsAndData(context, query)
                val summary = if (searchRes.matchedKeepAndNotes.isNotEmpty()) {
                    "💡 **Vos notes trouvées** :\n" + searchRes.matchedKeepAndNotes.take(5).joinToString("\n") { "• $it" }
                } else {
                    "💡 Aucune note correspondante trouvée. Vous pouvez dire : *« Crée une note dans Keep : ... »*."
                }
                McpToolExecutionOutcome(
                    connector = connector,
                    tool = tool,
                    executed = true,
                    naturalReplyFr = summary,
                    badgeFr = "💡 Notes Google Keep consultées",
                    appLaunchAction = LiaAppLaunchAction(
                        appLabel = "Google Keep",
                        packageName = "com.google.android.keep",
                        targetScreenId = "notes_editor"
                    ),
                    targetVirtualScreenId = "notes_editor",
                    quickFollowUps = listOf(
                        "💡 Crée une note dans Keep : Courses",
                        "🚀 Ouvre Google Keep"
                    )
                )
            }

            "messenger.open_thread", "messenger.send_message" -> {
                val contact = arguments["contact"].orEmpty().trim()
                val msg = arguments["message"].orEmpty().trim()
                val resolved = if (contact.isNotBlank()) {
                    LiaMemoryAndHistoryStore.verifyContactForSms(contact)
                } else null

                if (context != null) {
                    LiaCrossAppKnowledgeResolver.launchMessagingAppWithPayload(
                        context = context,
                        channel = MessagingAppChannel.MESSENGER,
                        recipientNameOrPhone = resolved?.phoneNumber?.takeIf { it.isNotBlank() } ?: contact,
                        messageText = msg
                    )
                }

                val reply = when {
                    contact.isNotBlank() && msg.isNotBlank() ->
                        "💙 J'ouvre votre discussion **Messenger** avec **$contact** et je prépare le message :\n« *$msg* »"
                    contact.isNotBlank() ->
                        "💙 J'ouvre votre conversation avec **$contact** sur **Messenger**."
                    else ->
                        "💙 J'ouvre vos discussions sur **Messenger**."
                }
                McpToolExecutionOutcome(
                    connector = connector,
                    tool = tool,
                    executed = true,
                    naturalReplyFr = reply,
                    badgeFr = "💙 Connecteur Messenger actif",
                    appLaunchAction = LiaAppLaunchAction(
                        appLabel = "Messenger",
                        packageName = "com.facebook.orca",
                        targetScreenId = "messenger_chat"
                    ),
                    targetVirtualScreenId = "messenger_chat",
                    quickFollowUps = listOf(
                        "⌨️ Saisir un message",
                        "👆 Clique sur Envoyer",
                        "🟢 Ouvrir WhatsApp"
                    )
                )
            }

            "whatsapp.send_message", "whatsapp.open_conversation" -> {
                val contact = arguments["contact"].orEmpty().trim()
                val msg = arguments["message"].orEmpty().trim()
                val resolved = if (contact.isNotBlank()) {
                    LiaMemoryAndHistoryStore.verifyContactForSms(contact)
                } else null

                if (context != null) {
                    LiaCrossAppKnowledgeResolver.launchMessagingAppWithPayload(
                        context = context,
                        channel = MessagingAppChannel.WHATSAPP,
                        recipientNameOrPhone = resolved?.phoneNumber?.takeIf { it.isNotBlank() } ?: contact,
                        messageText = msg
                    )
                }

                val reply = when {
                    contact.isNotBlank() && msg.isNotBlank() ->
                        "🟢 J'ouvre **WhatsApp** avec **$contact** et votre message prêt :\n« *$msg* »"
                    contact.isNotBlank() ->
                        "🟢 J'ouvre votre discussion **WhatsApp** avec **$contact**."
                    else ->
                        "🟢 J'ouvre vos discussions **WhatsApp**."
                }
                McpToolExecutionOutcome(
                    connector = connector,
                    tool = tool,
                    executed = true,
                    naturalReplyFr = reply,
                    badgeFr = "🟢 Connecteur WhatsApp actif",
                    appLaunchAction = LiaAppLaunchAction(
                        appLabel = "WhatsApp",
                        packageName = "com.whatsapp"
                    ),
                    quickFollowUps = listOf(
                        "💙 Ouvrir Messenger",
                        "📞 Appeler un contact"
                    )
                )
            }

            "calendar.create_event" -> {
                val title = arguments["title"].orEmpty().trim().ifBlank { "Rendez-vous" }
                if (context != null) {
                    LiaUniversalAppsAndGoogleHub.launchGoogleOrUniversalTarget(
                        context = context,
                        targetSpecOrPackage = "calendar_insert:$title",
                        optionalQuery = title
                    )
                }
                LiaMemoryAndHistoryStore.upsertSouvenir(
                    type = "EVENEMENT",
                    subjectKey = "rdv_${System.currentTimeMillis()}",
                    title = "Agenda : $title",
                    detailValue = title,
                    searchTags = "agenda,rdv,calendrier,${title.lowercase()}"
                )
                McpToolExecutionOutcome(
                    connector = connector,
                    tool = tool,
                    executed = true,
                    naturalReplyFr = "📅 J'ai préparé l'événement **« $title »** dans **Google Agenda** et je l'ai ajouté à vos souvenirs.",
                    badgeFr = "📅 Synchronisé avec Google Agenda",
                    appLaunchAction = LiaAppLaunchAction(
                        appLabel = "Google Agenda",
                        packageName = "com.google.android.calendar"
                    ),
                    quickFollowUps = listOf(
                        "📅 Quels sont mes prochains rendez-vous ?",
                        "⏰ Mettre un réveil demain"
                    )
                )
            }

            "calendar.view_schedule" -> {
                val searchRes = LiaCrossAppKnowledgeResolver.searchAcrossAllPhoneAppsAndData(context, "agenda")
                val summary = if (searchRes.matchedCalendarEvents.isNotEmpty()) {
                    "📅 **Vos prochains rendez-vous** :\n" + searchRes.matchedCalendarEvents.take(5).joinToString("\n") { "• $it" }
                } else {
                    "📅 Aucun événement à venir trouvé. Vous pouvez dire : *« Ajoute à Google Agenda : Dentiste demain à 14h »*."
                }
                McpToolExecutionOutcome(
                    connector = connector,
                    tool = tool,
                    executed = true,
                    naturalReplyFr = summary,
                    badgeFr = "📅 Google Agenda consulté",
                    appLaunchAction = LiaAppLaunchAction(
                        appLabel = "Google Agenda",
                        packageName = "com.google.android.calendar"
                    )
                )
            }

            "gmail.compose_email", "gmail.open_inbox" -> {
                val subject = arguments["subject"].orEmpty().trim()
                if (context != null) {
                    if (subject.isNotBlank()) {
                        val mailUri = "mailto:?subject=${Uri.encode(subject)}&body=${Uri.encode(subject)}"
                        LiaUniversalAppsAndGoogleHub.launchGoogleOrUniversalTarget(context, mailUri, subject)
                    } else {
                        LiaMemoryAndHistoryStore.launchAppFast(context, "com.google.android.gm", "Gmail")
                    }
                }
                McpToolExecutionOutcome(
                    connector = connector,
                    tool = tool,
                    executed = true,
                    naturalReplyFr = if (subject.isNotBlank()) {
                        "📧 J'ai ouvert **Gmail** avec votre brouillon d'e-mail : **« $subject »**."
                    } else {
                        "📧 J'ouvre votre boîte de réception **Gmail**."
                    },
                    badgeFr = "📧 Connecteur Gmail actif",
                    appLaunchAction = LiaAppLaunchAction(
                        appLabel = "Gmail",
                        packageName = "com.google.android.gm"
                    )
                )
            }

            "maps.navigate_to", "maps.search_place" -> {
                val dest = (arguments["destination"] ?: arguments["query"]).orEmpty().trim().ifBlank { "Autour de moi" }
                if (context != null) {
                    LiaUniversalAppsAndGoogleHub.launchGoogleOrUniversalTarget(
                        context = context,
                        targetSpecOrPackage = "geo:0,0?q=${Uri.encode(dest)}",
                        optionalQuery = dest
                    )
                }
                McpToolExecutionOutcome(
                    connector = connector,
                    tool = tool,
                    executed = true,
                    naturalReplyFr = "🗺️ J'ai lancé **Google Maps** pour : **« $dest »**.",
                    badgeFr = "🗺️ Navigation Google Maps lancée",
                    appLaunchAction = LiaAppLaunchAction(
                        appLabel = "Google Maps",
                        packageName = "com.google.android.apps.maps"
                    )
                )
            }

            "youtube.search_video" -> {
                val query = arguments["query"].orEmpty().trim().ifBlank { "Musique" }
                if (context != null) {
                    LiaUniversalAppsAndGoogleHub.launchGoogleOrUniversalTarget(
                        context = context,
                        targetSpecOrPackage = "https://www.youtube.com/results?search_query=${Uri.encode(query)}",
                        optionalQuery = query
                    )
                }
                McpToolExecutionOutcome(
                    connector = connector,
                    tool = tool,
                    executed = true,
                    naturalReplyFr = "▶️ Je lance la recherche **« $query »** directement dans **YouTube**.",
                    badgeFr = "▶️ Connecteur YouTube actif",
                    appLaunchAction = LiaAppLaunchAction(
                        appLabel = "YouTube",
                        packageName = "com.google.android.youtube"
                    )
                )
            }

            "spotify.play_music" -> {
                val query = arguments["query"].orEmpty().trim().ifBlank { "Playlist" }
                if (context != null) {
                    runCatching {
                        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                            putExtra(SearchManager.QUERY, query)
                            setPackage("com.spotify.music")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }.onFailure {
                        LiaMemoryAndHistoryStore.launchAppFast(context, "com.spotify.music", "Spotify")
                    }
                }
                McpToolExecutionOutcome(
                    connector = connector,
                    tool = tool,
                    executed = true,
                    naturalReplyFr = "🎵 Je lance **« $query »** sur **Spotify**.",
                    badgeFr = "🎵 Connecteur Spotify actif",
                    appLaunchAction = LiaAppLaunchAction(
                        appLabel = "Spotify",
                        packageName = "com.spotify.music"
                    )
                )
            }

            "drive.search_files" -> {
                val query = arguments["query"].orEmpty().trim()
                val searchRes = LiaCrossAppKnowledgeResolver.searchAcrossAllPhoneAppsAndData(context, query)
                val summary = if (searchRes.matchedDriveFiles.isNotEmpty()) {
                    "📁 **Vos fichiers Drive trouvés** :\n" + searchRes.matchedDriveFiles.take(5).joinToString("\n") { "• $it" }
                } else {
                    "📁 J'ouvre **Google Drive** pour accéder à vos documents."
                }
                if (context != null && query.isBlank()) {
                    LiaMemoryAndHistoryStore.launchAppFast(context, "com.google.android.apps.docs", "Google Drive")
                }
                McpToolExecutionOutcome(
                    connector = connector,
                    tool = tool,
                    executed = true,
                    naturalReplyFr = summary,
                    badgeFr = "📁 Connecteur Google Drive actif",
                    appLaunchAction = LiaAppLaunchAction(
                        appLabel = "Google Drive",
                        packageName = "com.google.android.apps.docs"
                    )
                )
            }

            else -> {
                // Exécution dynamique pour n'importe quelle application détectée sur le téléphone (.open, .search, .share)
                val pkg = connector.packageName
                val appLabel = connector.appName
                when {
                    toolId.endsWith(".search") -> {
                        val q = arguments["query"].orEmpty().trim()
                        if (context != null) {
                            launchSearchInApp(context, pkg, appLabel, q)
                        }
                        McpToolExecutionOutcome(
                            connector = connector,
                            tool = tool,
                            executed = true,
                            naturalReplyFr = if (q.isNotBlank()) {
                                "🔎 J'ai lancé la recherche **« $q »** dans **$appLabel**."
                            } else {
                                "🔎 J'ouvre **$appLabel** pour votre recherche."
                            },
                            badgeFr = "${connector.iconEmoji} Recherche dans $appLabel",
                            appLaunchAction = LiaAppLaunchAction(appLabel = appLabel, packageName = pkg)
                        )
                    }
                    toolId.endsWith(".share") -> {
                        val txt = (arguments["text"] ?: arguments["message"]).orEmpty().trim()
                        if (context != null) {
                            launchShareToApp(context, pkg, appLabel, txt)
                        }
                        McpToolExecutionOutcome(
                            connector = connector,
                            tool = tool,
                            executed = true,
                            naturalReplyFr = "📤 J'ai transmis votre contenu directement à **$appLabel**.",
                            badgeFr = "${connector.iconEmoji} Partagé vers $appLabel",
                            appLaunchAction = LiaAppLaunchAction(appLabel = appLabel, packageName = pkg)
                        )
                    }
                    else -> {
                        if (context != null) {
                            LiaMemoryAndHistoryStore.launchAppFast(context, pkg, appLabel)
                        }
                        McpToolExecutionOutcome(
                            connector = connector,
                            tool = tool,
                            executed = true,
                            naturalReplyFr = "${connector.iconEmoji} J'ouvre **$appLabel**.",
                            badgeFr = "${connector.iconEmoji} Connecteur $appLabel actif",
                            appLaunchAction = LiaAppLaunchAction(appLabel = appLabel, packageName = pkg)
                        )
                    }
                }
            }
        }

        _state.update { it.copy(lastExecution = outcome) }
        return outcome
    }

    private fun launchKeepNoteIntent(context: Context, title: String, contentText: String) {
        val launchedKeep = runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.google.android.keep")
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, contentText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        }.getOrDefault(false)

        if (launchedKeep) {
            LiaAppUsageTrackerService.recordAppOpenEvent(
                context = context,
                packageName = "com.google.android.keep",
                appLabel = "Google Keep"
            )
        }
    }

    private fun launchSearchInApp(context: Context, packageName: String, appLabel: String, query: String) {
        val launched = runCatching {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(packageName)
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        }.getOrDefault(false)

        if (!launched) {
            LiaMemoryAndHistoryStore.launchAppFast(context, packageName, appLabel)
        }
    }

    private fun launchShareToApp(context: Context, packageName: String, appLabel: String, text: String) {
        val launched = runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(packageName)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        }.getOrDefault(false)

        if (!launched) {
            LiaMemoryAndHistoryStore.launchAppFast(context, packageName, appLabel)
        }
    }
}
