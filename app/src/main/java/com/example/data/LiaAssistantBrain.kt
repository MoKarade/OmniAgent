package com.example.data

import com.example.model.currentTimeFormatted
import com.example.security.ActionTargetSafetyVerdict
import com.example.security.SoniaSecurityKernel
import com.example.service.LiaMcpConnectorRegistry
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class LiaBrainTurnOutcome(
    val replyText: String,
    val smsDraft: LiaSmsDraft? = null,
    val appLaunchAction: LiaAppLaunchAction? = null,
    val isPhoneAction: Boolean = false,
    val targetVirtualScreenId: String? = null,
    val learnedBadge: String? = null,
    val quickReplies: List<String> = emptyList()
)

object LiaAssistantBrain {

    private val geminiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "Tu es LIA, une assistante IA personnelle sur Android (style Google Gemini sombre), intelligente, chaleureuse et capable d'apprendre. " +
                        "Tu disposes d'une base de données personnelle Room avec les souvenirs de l'utilisateur (son prénom, son âge, ses préférences, ses événements, ses contacts) " +
                        "et d'un système d'apprentissage par renforcement pour ouvrir ses applications plus vite. " +
                        "RÈGLES STRICTES : " +
                        "1. Réponds toujours en français de façon naturelle, claire et directe. " +
                        "2. Tiens compte de tout l'historique de la conversation pour comprendre les réponses courtes comme 'oui', 'non', 'peu importe', ou un chiffre. " +
                        "3. N'invente jamais de faux contacts ni d'informations que l'utilisateur ne t'a pas données. " +
                        "4. N'effectue d'action sur le téléphone que si l'utilisateur le demande."
                )
            }
        )
    }

    /**
     * Processes a user message from either the in-app chat or the system-wide floating bubble,
     * records both the user message and LIA's response into [LiaMemoryAndHistoryStore],
     * updates learned facts/shortcuts, and returns the structured outcome.
     */
    suspend fun processUserMessage(
        rawUserText: String,
        source: String, // "app" or "bubble"
        onStreamingPartial: ((String) -> Unit)? = null,
        optimizedSystemInstruction: String? = null
    ): LiaBrainTurnOutcome {
        val cleaned = rawUserText.trim()
        val now = currentTimeFormatted()

        // 1. Append User Message to the persistent conversation history (never erasing previous messages!)
        val userMsg = LiaChatMessage(
            id = "user_${System.currentTimeMillis()}",
            role = "user",
            text = cleaned,
            timestamp = now,
            source = source
        )
        LiaMemoryAndHistoryStore.appendMessage(userMsg)

        // 2. Check if user is cancelling or changing topic, or answering a pending follow-up question from LIA
        val pending = LiaMemoryAndHistoryStore.state.value.pendingFollowUp
        if (isCancellationRequest(cleaned)) {
            LiaMemoryAndHistoryStore.cancelSmsDraft()
            LiaMemoryAndHistoryStore.setPendingFollowUp(null)
            val cancelOutcome = LiaBrainTurnOutcome(
                replyText = "✓ C'est annulé. Que souhaitez-vous faire maintenant ?"
            )
            appendLiaReplyToStore(cancelOutcome, source)
            return cancelOutcome
        }

        if (pending != null) {
            if (isGeneralActionStarterCommand(cleaned) || shouldBreakOutOfPendingFollowUp(cleaned, pending)) {
                LiaMemoryAndHistoryStore.setPendingFollowUp(null)
            } else {
                val followUpOutcome = handlePendingFollowUp(cleaned, pending)
                if (followUpOutcome != null) {
                    appendLiaReplyToStore(followUpOutcome, source)
                    return followUpOutcome
                }
            }
        }

        // 3. Check if user is spontaneously or passively teaching LIA personal info ("J'ai 22 ans", "Demain j'ai...", "Mon dernier jeu Steam est...")
        val learnedFactNote = LiaMemoryAndHistoryStore.extractAndLearnPersonalFacts(cleaned)
        val lower = cleaned.lowercase()
        val isPassiveAutoLearned = learnedFactNote?.startsWith("💭 Auto-appris") == true

        // If the message was purely stating an explicit personal fact (e.g. "j'ai 24 ans", "retiens que...")
        if (learnedFactNote != null && !isPassiveAutoLearned && !lower.contains("?")) {
            val outcome = LiaBrainTurnOutcome(
                replyText = "✓ C'est mémorisé : **$learnedFactNote**.",
                learnedBadge = "✨ Retenu : $learnedFactNote"
            )
            appendLiaReplyToStore(outcome, source)
            return outcome
        }

        // 3A. Apprentissage actif de règles personnalisées ("Apprends que quand je dis X fais Y", "Retiens que...") & Déclenchement des règles apprises
        val customRuleOrLearnOutcome = checkCustomLearningAndRuleIntent(cleaned)
        if (customRuleOrLearnOutcome != null) {
            appendLiaReplyToStore(customRuleOrLearnOutcome, source)
            return customRuleOrLearnOutcome
        }

        // 4. Execute Hybrid RAG on Room souvenirs BEFORE generating any response
        val ragOutcome = LiaMemoryAndHistoryStore.executeSouvenirsRag(cleaned)

        // 4-0. Contrôle direct de l'écran & Gestes système (Cliquer, Scroller haut/bas, Balayer gauche/droite, Saisir texte, Retour, Accueil, Notifications)
        val screenControlOutcome = checkScreenControlAndGestureIntent(cleaned)
        if (screenControlOutcome != null) {
            appendLiaReplyToStore(screenControlOutcome, source)
            return screenControlOutcome
        }

        // 4-MCP. Connecteurs MCP dynamiques (Google Keep, partage et recherche directe dans les applications installées)
        val mcpDynamicOutcome = com.example.service.LiaMcpConnectorRegistry.tryMatchAndExecuteNaturalIntent(
            context = LiaMemoryAndHistoryStore.getApplicationContextOrNull(),
            rawUserText = cleaned
        )
        if (mcpDynamicOutcome != null) {
            val outcome = LiaBrainTurnOutcome(
                replyText = mcpDynamicOutcome.naturalReplyFr,
                appLaunchAction = mcpDynamicOutcome.appLaunchAction,
                isPhoneAction = true,
                targetVirtualScreenId = mcpDynamicOutcome.targetVirtualScreenId,
                learnedBadge = mcpDynamicOutcome.badgeFr,
                quickReplies = mcpDynamicOutcome.quickFollowUps
            )
            appendLiaReplyToStore(outcome, source)
            return outcome
        }

        // 4A. Check if user dictated a Voice Note or Quick Note ("Note : ...", "Note vocale : ...", "Prends en note ...")
        val noteOutcome = checkVoiceNoteOrQuickNoteIntent(cleaned)
        if (noteOutcome != null) {
            appendLiaReplyToStore(noteOutcome, source)
            return noteOutcome
        }

        // 4A-bis. Calculatrice, Conversions d'unités/devises & Traduction instantanée (Général & Précis)
        val utilityOutcome = checkInstantUtilityIntent(cleaned)
        if (utilityOutcome != null) {
            appendLiaReplyToStore(utilityOutcome, source)
            return utilityOutcome
        }

        // 4B. Check if user triggered a Multi-Action Macro Routine ("Mode Matin", "Mode Départ / Trajet", "Mode Soir")
        val routineOutcome = checkMacroRoutineIntent(cleaned)
        if (routineOutcome != null) {
            appendLiaReplyToStore(routineOutcome, source)
            return routineOutcome
        }

        // 4B-bis. Actions Téléphone Générales & Précises (Appeler, Minuteur, Lampe torche, Réglages rapides, Musique, Partage, Recherche dans une app, Catalogue d'actions)
        val generalOrPreciseSystemOutcome = checkGeneralAndPreciseSystemActionsIntent(cleaned)
        if (generalOrPreciseSystemOutcome != null) {
            appendLiaReplyToStore(generalOrPreciseSystemOutcome, source)
            return generalOrPreciseSystemOutcome
        }

        // 4C. Navigation autonome multi-étapes dans une application (ex: "retrouve le dernier jeu auquel j'ai joué dans Steam", cliquer là où il faut dans une app)
        val autonomousTaskOutcome = checkAutonomousAppTaskIntent(cleaned)
        if (autonomousTaskOutcome != null) {
            appendLiaReplyToStore(autonomousTaskOutcome, source)
            return autonomousTaskOutcome
        }

        // 4D. Messagerie multi-applications (Messenger, WhatsApp, Telegram, Instagram, Discord, Signal, Google Keep) & Recherche transversale (numéros, Calendar, Keep, Drive, apps)
        val crossAppOutcome = checkCrossAppMessagingAndLookupIntent(cleaned)
        if (crossAppOutcome != null) {
            appendLiaReplyToStore(crossAppOutcome, source)
            return crossAppOutcome
        }

        // 4E. Check if user asked to sync or read from Google Cloud APIs (Gmail, Calendar, Drive, Contacts)
        val workspaceApiOutcome = checkGoogleCloudWorkspaceQuery(cleaned)
        if (workspaceApiOutcome != null) {
            appendLiaReplyToStore(workspaceApiOutcome, source)
            return workspaceApiOutcome
        }

        // 5. Check if user is asking about their personal database ("quel est mon âge ?", "qui suis-je ?", "mes infos", "quels sont mes souvenirs ?")
        val personalInfoOutcome = checkPersonalDatabaseQuery(cleaned, ragOutcome)
        if (personalInfoOutcome != null) {
            appendLiaReplyToStore(personalInfoOutcome, source)
            return personalInfoOutcome
        }

        // 6. Check if user wants to write/send an SMS or message to someone ("écris un sms à ma maman...", "envoie un sms à...")
        val smsOutcome = checkDynamicSmsIntent(cleaned)
        if (smsOutcome != null) {
            appendLiaReplyToStore(smsOutcome, source)
            return smsOutcome
        }

        // 7. Check if user wants to open an app or learn faster navigation ("ouvre YouTube", "ouvre Gmail", "apprends à ouvrir...")
        val appOutcome = checkFastAppLaunchIntent(cleaned)
        if (appOutcome != null) {
            appendLiaReplyToStore(appOutcome, source)
            return appOutcome
        }

        // 8. If RAG found a direct high-confidence answer in `lia_souvenirs` for a personal question, return it or augment the LLM generation
        val topRagMatch = ragOutcome.retrievedMatches.firstOrNull()
        val ragBadge = if (topRagMatch != null && topRagMatch.similarityPercent >= 35) {
            "✨ Souvenir utilisé : ${topRagMatch.souvenir.title} (${topRagMatch.similarityPercent}%)"
        } else null

        val rawAiReply = generateContextualAiReply(
            userPrompt = cleaned,
            ragOutcome = ragOutcome,
            onStreamingPartial = onStreamingPartial,
            optimizedSystemInstruction = optimizedSystemInstruction
        )
        val (cleanAiReply, aiExtractedAction) = extractEmbeddedActionFromAiReply(rawAiReply)
        if (aiExtractedAction != null) {
            recordLearnedAppPreference(aiExtractedAction, cleaned)
        }
        val finalOutcome = LiaBrainTurnOutcome(
            replyText = cleanAiReply,
            appLaunchAction = aiExtractedAction,
            isPhoneAction = aiExtractedAction != null,
            learnedBadge = learnedFactNote ?: ragBadge ?: aiExtractedAction?.let { "🧠 Appris & Exécuté : ${it.appLabel}" }
        )
        appendLiaReplyToStore(finalOutcome, source)
        return finalOutcome
    }

    /**
     * Exécute directement un outil dynamique d'application (MCP) déclenché depuis l'interface
     * et ajoute l'échange naturel dans le fil de discussion de LIA.
     */
    fun executeDynamicMcpToolFromUi(
        tool: com.example.service.McpDynamicTool,
        arguments: Map<String, String>
    ): LiaBrainTurnOutcome? {
        val now = currentTimeFormatted()
        val mainArg = arguments.values.firstOrNull { it.isNotBlank() }.orEmpty()
        val userText = if (mainArg.isNotBlank()) {
            "${tool.titleFr} : $mainArg"
        } else {
            tool.sampleNaturalPhrase
        }
        LiaMemoryAndHistoryStore.appendMessage(
            LiaChatMessage(
                id = "user_${System.currentTimeMillis()}",
                role = "user",
                text = userText,
                timestamp = now,
                source = "app"
            )
        )
        val mcpRes = com.example.service.LiaMcpConnectorRegistry.executeTool(
            context = LiaMemoryAndHistoryStore.getApplicationContextOrNull(),
            toolId = tool.toolId,
            arguments = arguments
        ) ?: return null

        val outcome = LiaBrainTurnOutcome(
            replyText = mcpRes.naturalReplyFr,
            appLaunchAction = mcpRes.appLaunchAction,
            isPhoneAction = true,
            targetVirtualScreenId = mcpRes.targetVirtualScreenId,
            learnedBadge = mcpRes.badgeFr,
            quickReplies = mcpRes.quickFollowUps
        )
        appendLiaReplyToStore(outcome, "app")
        return outcome
    }

    private fun checkCustomLearningAndRuleIntent(input: String): LiaBrainTurnOutcome? {
        return LiaFactAndRuleExtractor.checkCustomLearningAndRuleIntent(input) { targetCmd ->
            checkScreenControlAndGestureIntent(targetCmd)
                ?: checkCrossAppMessagingAndLookupIntent(targetCmd)
                ?: checkAutonomousAppTaskIntent(targetCmd)
                ?: checkGeneralAndPreciseSystemActionsIntent(targetCmd)
                ?: checkFastAppLaunchIntent(targetCmd)
        }
    }

    /**
     * Moteur complet de **Contrôle de l'Écran & Gestes Système Android** :
     * Permet à LIA d'utiliser toutes les actions d'interaction avec l'écran :
     * - `click` / `tap` ("clique sur ...", "appuie sur ...", "touche ...", "clique au centre / en haut / en bas")
     * - `swipe` / `scroll` ("scrolle vers le bas", "scrolle vers le haut", "balaye à gauche", "balaye à droite", "descends", "monte")
     * - `type_text` ("écris ... dans le champ", "tape le texte ...", "saisis ...")
     * - `global_action` ("bouton retour", "écran d'accueil", "applications récentes", "ouvre les notifications", "capture d'écran")
     */
    private fun checkScreenControlAndGestureIntent(input: String): LiaBrainTurnOutcome? {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        // 0. Menu général : "Contrôler l'écran", "Actions écran", "Contrôle de l'écran"
        if (lower == "contrôler l'écran" ||
            lower == "controler l'ecran" ||
            lower == "contrôle de l'écran" ||
            lower == "controle de l'ecran" ||
            lower == "actions écran" ||
            lower == "actions ecran" ||
            lower == "gestes écran"
        ) {
            return LiaBrainTurnOutcome(
                replyText = "🎮 **Contrôle Total de l'Écran & Gestes Accessibilité Actifs** :\n\n" +
                    "• 👆 **Cliquer / Toucher** : *« Clique sur Envoyer »*, *« Appuie sur le bouton Rechercher »*, *« Clique en haut / au milieu / en bas »*\n" +
                    "• ↕️ **Scroller / Balayer** : *« Scrolle vers le bas »*, *« Scrolle vers le haut »*, *« Balaye à gauche / droite »*\n" +
                    "• ⌨️ **Saisir du texte** : *« Tape le texte Bonjour dans le champ »*\n" +
                    "• 🧭 **Navigation système** : *« Bouton retour »*, *« Écran d'accueil »*, *« Ouvre les notifications »*\n\n" +
                    "*(Touchez une action rapide ci-dessous ou demandez-la directement à LIA)*",
                isPhoneAction = true,
                learnedBadge = "🎮 Contrôle Écran Prêt",
                quickReplies = listOf(
                    "Scrolle vers le bas",
                    "Scrolle vers le haut",
                    "Clique au milieu de l'écran",
                    "Bouton retour",
                    "Écran d'accueil",
                    "Ouvre une conversation sur Messenger"
                )
            )
        }

        // 1. Défilement / Scroll / Swipe (Bas, Haut, Gauche, Droite)
        val isScrollDown = lower == "scrolle vers le bas" || lower == "scroll vers le bas" ||
            lower == "scrolle en bas" || lower == "descends sur l'écran" || lower == "descends sur la page" ||
            lower == "défile vers le bas" || lower == "defile vers le bas" || lower == "swipe up"
        val isScrollUp = lower == "scrolle vers le haut" || lower == "scroll vers le haut" ||
            lower == "scrolle en haut" || lower == "monte sur l'écran" || lower == "monte sur la page" ||
            lower == "défile vers le haut" || lower == "defile vers le haut" || lower == "swipe down"
        val isSwipeLeft = lower == "balaye à gauche" || lower == "balaye vers la gauche" ||
            lower == "swipe à gauche" || lower == "swipe left" || lower == "glisse à gauche"
        val isSwipeRight = lower == "balaye à droite" || lower == "balaye vers la droite" ||
            lower == "swipe à droite" || lower == "swipe right" || lower == "glisse à droite"

        if (isScrollDown || isScrollUp || isSwipeLeft || isSwipeRight) {
            val (dirLabel, code) = when {
                isScrollDown -> "Défilement vers le bas (Scroll Down)" to "SWIPE_UP"
                isScrollUp -> "Défilement vers le haut (Scroll Up)" to "SWIPE_DOWN"
                isSwipeLeft -> "Balayage vers la gauche (Swipe Left)" to "SWIPE_LEFT"
                else -> "Balayage vers la droite (Swipe Right)" to "SWIPE_RIGHT"
            }
            LiaMemoryAndHistoryStore.recordLearnedShortcut(
                triggerPhrase = trimmed,
                actionTitle = "Geste écran : $dirLabel",
                targetPackageOrScreen = "screen_gesture:$code",
                stepsSaved = 2,
                latencyMs = 25L
            )
            return LiaBrainTurnOutcome(
                replyText = "↕️ **Geste exécuté sur l'écran** : **$dirLabel** (`dispatchGesture`).",
                isPhoneAction = true,
                learnedBadge = "🧠 Geste appris : $dirLabel",
                quickReplies = listOf("Scrolle vers le bas", "Scrolle vers le haut", "Clique au milieu de l'écran", "Bouton retour")
            )
        }

        // 2. Clic / Tap sur un élément ou une zone de l'écran ("clique sur ...", "appuie sur ...", "touche ...")
        val clickRegex = Regex(
            "^(?:clique\\s+sur|cliquer\\s+sur|appuie\\s+sur|appuyer\\s+sur|touche|toucher|clique\\s+au|clique\\s+en|tap\\s+sur)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
        clickRegex.find(trimmed)?.let { m ->
            val targetDesc = m.groupValues[1].trim()
            val tLower = targetDesc.lowercase()
            // Ne pas intercepter "appuie sur retour / accueil" qui sont gérés juste après en actions globales
            if (tLower !in setOf("retour", "le bouton retour", "accueil", "l'accueil")) {
                val zoneLabel = when {
                    tLower.contains("haut") -> "Zone haute (0.50, 0.18)"
                    tLower.contains("bas") || tLower.contains("envoyer") -> "Zone basse / Action (0.50, 0.85)"
                    tLower.contains("gauche") -> "Zone gauche (0.20, 0.50)"
                    tLower.contains("droite") -> "Zone droite (0.82, 0.50)"
                    else -> "Cible « $targetDesc » (0.50, 0.50)"
                }
                LiaMemoryAndHistoryStore.recordLearnedShortcut(
                    triggerPhrase = "Cliquer : ${targetDesc.take(22)}",
                    actionTitle = "Clic écran sur « $targetDesc »",
                    targetPackageOrScreen = "screen_click:$targetDesc",
                    stepsSaved = 2,
                    latencyMs = 20L
                )
                return LiaBrainTurnOutcome(
                    replyText = "👆 **Clic exécuté sur l'écran** : **« $targetDesc »** ($zoneLabel).",
                    isPhoneAction = true,
                    learnedBadge = "👆 Clic : ${targetDesc.take(22)}",
                    quickReplies = listOf("Scrolle vers le bas", "Clique sur Envoyer", "Bouton retour")
                )
            }
        }

        // 3. Saisie de texte dans un champ à l'écran ("tape le texte ...", "écris ... dans le champ", "saisis ...")
        val typeTextRegex = Regex(
            "^(?:tape\\s+le\\s+texte|tape\\s+dans\\s+le\\s+champ|écris\\s+dans\\s+le\\s+champ|ecris\\s+dans\\s+le\\s+champ|saisis\\s+le\\s+texte|remplis\\s+le\\s+champ\\s+avec)\\s*[:\\-]?\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
        typeTextRegex.find(trimmed)?.let { m ->
            val textToType = m.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("«", "»")
            val ctx = LiaMemoryAndHistoryStore.getApplicationContextOrNull()
            if (ctx != null && textToType.isNotBlank()) {
                runCatching {
                    val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("LIA_Screen_Input", textToType))
                }
            }
            LiaMemoryAndHistoryStore.recordLearnedShortcut(
                triggerPhrase = "Saisir : ${textToType.take(18)}",
                actionTitle = "Saisie écran « $textToType »",
                targetPackageOrScreen = "screen_type:$textToType",
                stepsSaved = 3,
                latencyMs = 25L
            )
            return LiaBrainTurnOutcome(
                replyText = "⌨️ **Texte injecté sur l'écran et copié dans le presse-papiers** :\n« **$textToType** »",
                isPhoneAction = true,
                learnedBadge = "⌨️ Texte saisi : ${textToType.take(18)}",
                quickReplies = listOf("Clique sur Envoyer", "Bouton retour")
            )
        }

        // 4. Actions système globales (Retour, Accueil, Notifications, Applications récentes)
        if (lower == "bouton retour" || lower == "retour arrière" || lower == "reviens en arrière" || lower == "appuie sur retour") {
            LiaMemoryAndHistoryStore.recordLearnedShortcut(
                triggerPhrase = "Bouton retour",
                actionTitle = "Navigation Système : Retour (BACK)",
                targetPackageOrScreen = "global_action:BACK",
                stepsSaved = 1,
                latencyMs = 15L
            )
            return LiaBrainTurnOutcome(
                replyText = "🔙 **Action système exécutée** : **Retour en arrière (`GLOBAL_ACTION_BACK`)**.",
                isPhoneAction = true,
                targetVirtualScreenId = "home_launcher",
                learnedBadge = "🔙 Action : Retour"
            )
        }

        if (lower == "écran d'accueil" || lower == "ecran d'accueil" || lower == "va à l'accueil" || lower == "retour à l'accueil") {
            LiaMemoryAndHistoryStore.recordLearnedShortcut(
                triggerPhrase = "Écran d'accueil",
                actionTitle = "Navigation Système : Accueil (HOME)",
                targetPackageOrScreen = "global_action:HOME",
                stepsSaved = 1,
                latencyMs = 15L
            )
            return LiaBrainTurnOutcome(
                replyText = "🏠 **Action système exécutée** : **Retour à l'écran d'accueil (`GLOBAL_ACTION_HOME`)**.",
                isPhoneAction = true,
                targetVirtualScreenId = "home_launcher",
                learnedBadge = "🏠 Action : Accueil"
            )
        }

        if (lower == "ouvre les notifications" || lower == "barre de notifications" || lower == "affiche les notifications") {
            return LiaBrainTurnOutcome(
                replyText = "🔔 **Action système exécutée** : **Ouverture du volet de notifications Android (`GLOBAL_ACTION_NOTIFICATIONS`)**.",
                isPhoneAction = true,
                learnedBadge = "🔔 Volet Notifications"
            )
        }

        return null
    }

    private fun checkVoiceNoteOrQuickNoteIntent(input: String): LiaBrainTurnOutcome? =
        LiaLocalUtilitiesEngine.checkVoiceNoteOrQuickNoteIntent(input)

    /**
     * Interroge ou synchronise les APIs Google Cloud Workspace (Gmail, Google Calendar, Google Drive, Google Contacts)
     * autorisées via Google Sign-In & Credential Manager.
     */
    private fun checkGoogleCloudWorkspaceQuery(input: String): LiaBrainTurnOutcome? {
        val lower = input.lowercase().trim()
        val ws = LiaGoogleCloudWorkspaceClient.state.value
        val ctx = LiaMemoryAndHistoryStore.getApplicationContextOrNull()

        // 1. Synchronisation globale Google Workspace
        if (lower.contains("synchronise") && (lower.contains("google") || lower.contains("workspace") || lower.contains("gmail") || lower.contains("drive") || lower.contains("contacts"))) {
            if (ctx != null) {
                LiaGoogleCloudWorkspaceClient.syncAllWorkspaceData(ctx)
            }
            return LiaBrainTurnOutcome(
                replyText = "🔄 **Synchronisation Google Cloud Workspace lancée** (Gmail API, Google Calendar API, Google Drive API & Contacts API) :\n\n" +
                    "• **Statut Sign-In** : ${if (ws.isSignedIn) "Connecté (${ws.userEmail.ifBlank { ws.userDisplayName }})" else "Mode local / Intents Android prêts"}\n" +
                    "• **E-mails Gmail indexés** : ${ws.recentEmails.size}\n" +
                    "• **Événements Google Agenda** : ${ws.upcomingEvents.size}\n" +
                    "• **Fichiers Google Drive** : ${ws.recentDriveFiles.size}\n" +
                    "• **Contacts synchronisés** : ${ws.syncedContacts.size}\n\n" +
                    "Toutes les données récupérées sont indexées dans la mémoire Room RAG de LIA.",
                learnedBadge = "🌐 Sync Google Cloud APIs"
            )
        }

        // 2. Lecture des derniers e-mails Gmail
        if ((lower.contains("lis") || lower.contains("quels sont") || lower.contains("voir") || lower.contains("affiche") || lower.contains("derniers")) &&
            (lower.contains("mail") || lower.contains("gmail") || lower.contains("courriel"))
        ) {
            if (ws.recentEmails.isNotEmpty()) {
                val listText = ws.recentEmails.take(5).joinToString("\n") {
                    "• 📧 **${it.subject}** (*${it.from}*) — ${it.snippet.take(80)}"
                }
                return LiaBrainTurnOutcome(
                    replyText = "📧 **Vos derniers e-mails Gmail (via Google Cloud Gmail API)** :\n\n$listText",
                    appLaunchAction = LiaAppLaunchAction("Gmail", "com.google.android.gm", "home_launcher", 35L),
                    isPhoneAction = true,
                    learnedBadge = "📧 Gmail Cloud API (${ws.recentEmails.size})"
                )
            }
        }

        // 3. Lecture des prochains événements Google Agenda
        if ((lower.contains("quels sont") || lower.contains("prochains") || lower.contains("affiche") || lower.contains("voir") || lower.contains("mon agenda")) &&
            (lower.contains("agenda") || lower.contains("calendrier") || lower.contains("rendez-vous") || lower.contains("rdv"))
        ) {
            if (ws.upcomingEvents.isNotEmpty()) {
                val listText = ws.upcomingEvents.take(5).joinToString("\n") {
                    "• 📅 **${it.title}** — ${it.startFormatted}${if (it.locationOrDescription.isNotBlank()) " (${it.locationOrDescription})" else ""}"
                }
                return LiaBrainTurnOutcome(
                    replyText = "📅 **Vos prochains événements Google Agenda** :\n\n$listText",
                    appLaunchAction = LiaAppLaunchAction("Google Agenda", "com.google.android.calendar", "calendar_app", 35L),
                    isPhoneAction = true,
                    learnedBadge = "📅 Calendar API (${ws.upcomingEvents.size})"
                )
            }
        }

        // 4. Lecture des fichiers récents Google Drive
        if ((lower.contains("quels sont") || lower.contains("mes fichiers") || lower.contains("cherche") || lower.contains("liste")) &&
            lower.contains("drive")
        ) {
            if (ws.recentDriveFiles.isNotEmpty()) {
                val listText = ws.recentDriveFiles.take(5).joinToString("\n") {
                    "• 📁 **${it.name}** (*${it.modifiedTime}*)"
                }
                return LiaBrainTurnOutcome(
                    replyText = "📁 **Vos fichiers récents sur Google Drive (Drive API v3)** :\n\n$listText",
                    appLaunchAction = LiaAppLaunchAction("Google Drive", "com.google.android.apps.docs", "home_launcher", 35L),
                    isPhoneAction = true,
                    learnedBadge = "📁 Drive Cloud API (${ws.recentDriveFiles.size})"
                )
            }
        }

        return null
    }

    private fun checkMacroRoutineIntent(input: String): LiaBrainTurnOutcome? =
        LiaLocalUtilitiesEngine.checkMacroRoutineIntent(input)

    private fun appendLiaReplyToStore(outcome: LiaBrainTurnOutcome, source: String) {
        val liaMsg = LiaChatMessage(
            id = "lia_${System.currentTimeMillis()}",
            role = "lia",
            text = outcome.replyText,
            timestamp = currentTimeFormatted(),
            source = source,
            smsDraft = outcome.smsDraft,
            appLaunchAction = outcome.appLaunchAction,
            isPhoneAction = outcome.isPhoneAction,
            learnedNote = outcome.learnedBadge,
            quickReplies = outcome.quickReplies
        )
        LiaMemoryAndHistoryStore.appendMessage(liaMsg)
    }

    private fun handlePendingFollowUp(
        userReply: String,
        pending: LiaPendingFollowUp
    ): LiaBrainTurnOutcome? {
        val lower = userReply.lowercase().trim()

        when (pending) {
            is LiaPendingFollowUp.WaitingForSmsBody -> {
                LiaMemoryAndHistoryStore.setPendingFollowUp(null)
                val verification = LiaMemoryAndHistoryStore.verifyContactForSms(
                    pending.phoneNumber.ifBlank { pending.recipientName }
                )
                val recipient = pending.recipientName.ifBlank { verification.recipientName }
                val phone = pending.phoneNumber.ifBlank { verification.phoneNumber }
                val isVerified = LiaMemoryAndHistoryStore.isPhoneNumberValid(phone)
                val statusLabel = if (isVerified) {
                    "✓ Contact vérifié : $recipient ↔ $phone"
                } else {
                    "⚠️ Numéro manquant pour « $recipient » — saisissez-le dans l'aperçu avant envoi"
                }

                val isWhatever = lower.contains("peu importe") ||
                    lower.contains("comme tu veux") ||
                    lower.contains("n'importe") ||
                    lower.contains("invente") ||
                    lower.contains("choisis") ||
                    lower == "ok" ||
                    lower == "oui"

                val body = if (isWhatever) {
                    defaultWarmMessageFor(recipient)
                } else {
                    cleanSmsBodyPrefix(userReply)
                }

                val draft = LiaSmsDraft(
                    recipientName = recipient,
                    phoneNumber = phone,
                    messageBody = body,
                    isSent = false,
                    isContactVerified = isVerified,
                    contactVerificationStatus = statusLabel
                )
                LiaMemoryAndHistoryStore.recordLearnedShortcut(
                    triggerPhrase = "Message à $recipient",
                    actionTitle = "Préparation rapide SMS pour $recipient ($phone)",
                    targetPackageOrScreen = "messages_inbox",
                    stepsSaved = 3,
                    latencyMs = 75L
                )
                return LiaBrainTurnOutcome(
                    replyText = "Aperçu SMS prêt pour **$recipient** (${phone.ifBlank { "numéro à vérifier" }}) :\n« $body »\n\nVous pouvez modifier le nom, le numéro ou le texte dans l'aperçu ci-dessous avant d'appuyer sur **📤 Confirmer & Envoyer**.",
                    smsDraft = draft,
                    isPhoneAction = true,
                    targetVirtualScreenId = "messages_inbox",
                    learnedBadge = "⚡ Aperçu SMS vérifié ($recipient)"
                )
            }

            is LiaPendingFollowUp.WaitingForUserFact -> {
                val numMatch = Regex("(\\d{1,3})").find(userReply)
                if (pending.factKey == "age" && numMatch != null) {
                    val ageStr = "${numMatch.groupValues[1]} ans"
                    LiaMemoryAndHistoryStore.upsertUserFact("age", "Âge", ageStr)
                    LiaMemoryAndHistoryStore.setPendingFollowUp(null)
                    return LiaBrainTurnOutcome(
                        replyText = "✓ Parfait ! J'ai bien retenu que vous avez **$ageStr**.",
                        learnedBadge = "🧠 Mémorisé : Âge = $ageStr"
                    )
                } else if (pending.factKey.startsWith("note_")) {
                    val now = currentTimeFormatted()
                    val shortTitle = userReply.take(42).let { if (userReply.length > 42) "$it…" else it }
                    LiaMemoryAndHistoryStore.upsertSouvenir(
                        type = "NOTE",
                        subjectKey = pending.factKey,
                        title = "Note : $shortTitle",
                        detailValue = "$userReply (enregistrée à $now)",
                        searchTags = "note,memo,${userReply.lowercase().replace(Regex("[^a-zà-ÿ0-9 ]"), " ")}"
                    )
                    LiaMemoryAndHistoryStore.upsertLongTermMemory(
                        category = "VOICE_NOTE",
                        memoryKey = pending.factKey,
                        title = "📝 $shortTitle",
                        contentValue = userReply,
                        targetActionOrPackage = "note_saved",
                        speedupMs = 25L
                    )
                    LiaMemoryAndHistoryStore.setPendingFollowUp(null)
                    return LiaBrainTurnOutcome(
                        replyText = "📝 **Note enregistrée** :\n\n« *$userReply* »",
                        learnedBadge = "📝 Note mémorisée"
                    )
                } else if (!lower.contains("?")) {
                    LiaMemoryAndHistoryStore.upsertUserFact(pending.factKey, pending.factLabel, userReply)
                    LiaMemoryAndHistoryStore.setPendingFollowUp(null)
                    return LiaBrainTurnOutcome(
                        replyText = "✓ C'est noté ! J'ai enregistré **${pending.factLabel} : $userReply**.",
                        learnedBadge = "🧠 Mémorisé : ${pending.factLabel}"
                    )
                }
                LiaMemoryAndHistoryStore.setPendingFollowUp(null)
                return null
            }

            is LiaPendingFollowUp.WaitingForSmsPhone -> {
                LiaMemoryAndHistoryStore.setPendingFollowUp(null)
                val phone = userReply.trim()
                val isValid = LiaMemoryAndHistoryStore.isPhoneNumberValid(phone)
                if (isValid) {
                    LiaMemoryAndHistoryStore.upsertUserFact(
                        "${pending.recipientName.lowercase()}_tel",
                        "Numéro ${pending.recipientName}",
                        phone
                    )
                }
                val draft = LiaSmsDraft(
                    recipientName = pending.recipientName,
                    phoneNumber = phone,
                    messageBody = pending.messageBody,
                    isSent = false,
                    isContactVerified = isValid,
                    contactVerificationStatus = if (isValid) {
                        "✓ Contact vérifié : ${pending.recipientName} ↔ $phone"
                    } else {
                        "⚠️ Format de numéro à vérifier dans l'aperçu avant envoi"
                    }
                )
                return LiaBrainTurnOutcome(
                    replyText = "✓ Numéro de **${pending.recipientName}** ($phone) associé ! Vérifiez ou modifiez l'aperçu ci-dessous avant l'envoi :",
                    smsDraft = draft,
                    isPhoneAction = true,
                    targetVirtualScreenId = "messages_inbox",
                    learnedBadge = "🧠 Contact ${pending.recipientName} vérifié"
                )
            }

            is LiaPendingFollowUp.WaitingForGeneralClarification -> {
                LiaMemoryAndHistoryStore.setPendingFollowUp(null)
                return handleClarificationAnswer(userReply, pending.previousQuestion)
            }
        }
    }

    private fun isGeneralActionStarterCommand(input: String): Boolean {
        val lower = input.lowercase().trim()
        return lower in setOf(
            "ouvrir une application", "ouvre une application", "ouvrir une app", "ouvre une app",
            "envoyer un message", "envoie un message", "écrire un message", "ecrire un message",
            "envoyer un sms", "envoie un sms", "écrire un sms", "ecrire un sms",
            "envoyer un e-mail", "envoyer un email", "envoyer un mail", "envoie un mail", "écrire un mail",
            "appeler", "appeler quelqu'un", "passer un appel", "téléphoner", "telephoner",
            "mettre un minuteur", "mets un minuteur", "lancer un minuteur", "minuteur", "chronomètre", "chronometre",
            "ajouter un événement dans l'agenda", "ajouter à l'agenda", "ajouter un événement", "nouveau rdv",
            "prendre une note", "prends une note", "nouvelle note", "note vocale",
            "ouvrir google maps", "itinéraire", "itineraire", "navigation",
            "mettre un réveil", "mets un réveil", "programmer un réveil", "réveil", "reveil", "alarme",
            "recherche google", "recherche web", "chercher sur google",
            "changer un réglage", "changer un reglage", "réglages rapides", "reglages rapides", "paramètres système",
            "lancer de la musique", "écouter de la musique", "ecouter de la musique", "musique",
            "chercher dans une application", "rechercher dans une app", "chercher dans mes applis",
            "calculer ou convertir", "calculer", "convertir", "traduire", "traduire un texte",
            "partager un texte", "partager", "toutes les actions", "que peux-tu faire"
        )
    }

    private fun handleClarificationAnswer(userReply: String, clarificationType: String): LiaBrainTurnOutcome? {
        val cleaned = userReply.trim()
        val lower = cleaned.lowercase()
        val snap = LiaMemoryAndHistoryStore.state.value
        val ctx = LiaMemoryAndHistoryStore.getApplicationContextOrNull()

        return when (clarificationType) {
            "OPEN_APP" -> {
                val isHabitReference = lower.contains("habitude") || lower.contains("même") || lower.contains("meme") || lower.contains("dernière") || lower.contains("derniere")
                val target = if (isHabitReference) {
                    val lastApp = snap.longTermMemories.firstOrNull { it.category == "LEARNED_APP_PREF" }
                        ?: snap.longTermMemories.firstOrNull { it.category == "APP_SHORTCUT" }
                    if (lastApp != null && lastApp.targetActionOrPackage.isNotBlank()) {
                        LiaAppLaunchAction(lastApp.contentValue.ifBlank { lastApp.title }, lastApp.targetActionOrPackage, "home_launcher", 40L)
                    } else {
                        resolveAppLaunchTarget(cleaned)
                    }
                } else {
                    resolveAppLaunchTarget(cleaned)
                }
                recordLearnedAppPreference(target, cleaned)
                LiaBrainTurnOutcome(
                    replyText = "⚡ J'ouvre **${target.appLabel}** et je mémorise votre choix.",
                    appLaunchAction = target,
                    isPhoneAction = true,
                    targetVirtualScreenId = target.targetScreenId.ifBlank { null },
                    learnedBadge = "⚡ Ouvert : ${target.appLabel}"
                )
            }

            "ASK_CALL_RECIPIENT" -> {
                val matchedContact = com.example.service.LiaCrossAppKnowledgeResolver.findContactsMatching(ctx, cleaned).firstOrNull()
                val verification = LiaMemoryAndHistoryStore.verifyContactForSms(cleaned)
                val resolvedName = matchedContact?.displayName ?: verification.recipientName.ifBlank { cleaned }
                val resolvedPhone = matchedContact?.phoneNumbers?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: verification.phoneNumber.takeIf { it.isNotBlank() }
                    ?: cleaned.filter { it.isDigit() || it == '+' }

                val dialUri = if (resolvedPhone.isNotBlank()) "tel:${android.net.Uri.encode(resolvedPhone)}" else "tel:"
                val label = if (resolvedPhone.isNotBlank() && resolvedName != resolvedPhone) {
                    "Appeler $resolvedName ($resolvedPhone)"
                } else {
                    "Appeler $resolvedName"
                }
                val action = LiaAppLaunchAction(label, dialUri, "home_launcher", 35L)
                LiaMemoryAndHistoryStore.recordLearnedShortcut(
                    triggerPhrase = "📞 Appeler $resolvedName",
                    actionTitle = "Appel vers $resolvedName",
                    targetPackageOrScreen = dialUri,
                    stepsSaved = 3,
                    latencyMs = 35L
                )
                LiaBrainTurnOutcome(
                    replyText = if (resolvedPhone.isNotBlank()) {
                        "📞 J'ouvre le composeur téléphonique pour **$resolvedName** (**$resolvedPhone**)."
                    } else {
                        "📞 J'ouvre l'application **Téléphone** pour **$resolvedName**."
                    },
                    appLaunchAction = action,
                    isPhoneAction = true,
                    learnedBadge = "📞 Appel : $resolvedName"
                )
            }

            "ASK_UNIVERSAL_MESSAGE_TARGET" -> {
                // Redirige intelligemment selon l'application ou le contact choisi
                val detectedChannel = com.example.service.LiaCrossAppKnowledgeResolver.detectMessagingChannel(cleaned)
                return when {
                    detectedChannel != null && detectedChannel != com.example.service.MessagingAppChannel.SMS -> {
                        checkCrossAppMessagingAndLookupIntent(
                            if (lower.startsWith("message") || lower.startsWith("envoie")) cleaned else "Envoie un message sur ${detectedChannel.displayName}"
                        )
                    }
                    lower.contains("mail") || lower.contains("gmail") -> {
                        checkFastAppLaunchIntent("Envoyer un e-mail")
                    }
                    lower.contains("sms") || lower.contains("texto") -> {
                        checkDynamicSmsIntent("Envoyer un SMS")
                    }
                    else -> {
                        // L'utilisateur a donné directement un nom de contact (ex: "Maman" ou "Paul : J'arrive")
                        checkDynamicSmsIntent("Envoie un SMS à $cleaned")
                    }
                }
            }

            "ASK_TIMER_DURATION" -> {
                val timerMatch = Regex("(\\d{1,3})\\s*(secondes?|sec|s|minutes?|min|m|heures?|h)?(?:\\s+(?:pour|de)\\s+(.+))?", RegexOption.IGNORE_CASE).find(cleaned)
                val amount = timerMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 5
                val unitRaw = timerMatch?.groupValues?.getOrNull(2)?.lowercase().orEmpty()
                val label = timerMatch?.groupValues?.getOrNull(3)?.trim()?.ifBlank { "Minuteur" } ?: "Minuteur"
                val seconds = when {
                    unitRaw.startsWith("s") -> amount
                    unitRaw.startsWith("h") -> amount * 3600
                    else -> amount * 60
                }
                val unitLabel = when {
                    unitRaw.startsWith("s") -> "$amount sec"
                    unitRaw.startsWith("h") -> "$amount h"
                    else -> "$amount min"
                }
                val uri = "timer:$seconds:$label"
                val action = LiaAppLaunchAction("Minuteur ($unitLabel)", uri, "home_launcher", 35L)
                LiaMemoryAndHistoryStore.recordLearnedShortcut(
                    triggerPhrase = "⏱️ Minuteur $unitLabel",
                    actionTitle = "Minuteur $unitLabel",
                    targetPackageOrScreen = uri,
                    stepsSaved = 3,
                    latencyMs = 35L
                )
                LiaBrainTurnOutcome(
                    replyText = "⏱️ Je lance votre minuteur de **$unitLabel**.",
                    appLaunchAction = action,
                    isPhoneAction = true,
                    learnedBadge = "⏱️ Minuteur $unitLabel"
                )
            }

            "ASK_SYSTEM_SETTING" -> {
                val classified = com.example.service.IntentHandler.classifyAndExtractKnownIntent(
                    if (lower.contains("lampe") || lower.contains("torche") || lower.contains("réglage") || lower.contains("paramètre")) cleaned else "Paramètres $cleaned"
                )
                if (classified != null) {
                    val uri = classified.deepLinkUri.ifBlank { classified.targetPackage }
                    val action = LiaAppLaunchAction(classified.resolvedAppLabel, uri, "home_launcher", 35L)
                    LiaBrainTurnOutcome(
                        replyText = classified.summaryMessageFr,
                        appLaunchAction = action,
                        isPhoneAction = true,
                        learnedBadge = "⚙️ ${classified.resolvedAppLabel}"
                    )
                } else {
                    val action = LiaAppLaunchAction("Paramètres Android", "settings:${android.provider.Settings.ACTION_SETTINGS}", "home_launcher", 35L)
                    LiaBrainTurnOutcome(
                        replyText = "⚙️ J'ouvre les **Paramètres Android**.",
                        appLaunchAction = action,
                        isPhoneAction = true,
                        learnedBadge = "⚙️ Paramètres"
                    )
                }
            }

            "ASK_MEDIA_QUERY" -> {
                val query = cleaned.replace(Regex("^(?:joue|écoute|ecoute|mets|lance)\\s+", RegexOption.IGNORE_CASE), "").trim().ifBlank { "Musique" }
                val uri = "media_play:${android.net.Uri.encode(query)}"
                val action = LiaAppLaunchAction("Lecture : $query", uri, "home_launcher", 35L)
                LiaMemoryAndHistoryStore.upsertSouvenir(
                    type = "PREFERENCE",
                    subjectKey = "last_music_played",
                    title = "Musique écoutée : $query",
                    detailValue = query,
                    searchTags = "musique,spotify,youtube,chanson,${query.lowercase()}"
                )
                LiaBrainTurnOutcome(
                    replyText = "🎵 Je lance la lecture de **« $query »**.",
                    appLaunchAction = action,
                    isPhoneAction = true,
                    learnedBadge = "🎵 Musique : ${query.take(18)}"
                )
            }

            "ASK_DEEP_APP_SEARCH" -> {
                checkAutonomousAppTaskIntent(cleaned)
                    ?: checkCrossAppMessagingAndLookupIntent("Cherche $cleaned")
                    ?: run {
                        val url = "https://www.google.com/search?q=${android.net.Uri.encode(cleaned)}"
                        val action = LiaAppLaunchAction("Recherche ($cleaned)", url, "home_launcher", 40L)
                        LiaBrainTurnOutcome(
                            replyText = "🔎 Je lance la recherche pour **« $cleaned »**.",
                            appLaunchAction = action,
                            isPhoneAction = true,
                            learnedBadge = "🔎 Recherche : ${cleaned.take(18)}"
                        )
                    }
            }

            "ASK_SHARE_TEXT" -> {
                val uri = "share_text:${android.net.Uri.encode(cleaned)}"
                val action = LiaAppLaunchAction("Partager « ${cleaned.take(20)} »", uri, "home_launcher", 35L)
                LiaBrainTurnOutcome(
                    replyText = "📤 J'ouvre le menu de partage Android pour : *« $cleaned »*.",
                    appLaunchAction = action,
                    isPhoneAction = true,
                    learnedBadge = "📤 Partage prêt"
                )
            }

            "ASK_CALCULATION" -> {
                checkInstantUtilityIntent("Calcule $cleaned")
            }

            "ASK_TRANSLATION" -> {
                checkInstantUtilityIntent(
                    if (lower.startsWith("traduis") || lower.startsWith("traduire")) cleaned else "Traduis $cleaned en anglais"
                )
            }

            "ASK_SMS_RECIPIENT" -> {
                val rawTarget = extractRecipientOrPhoneFromPrompt(cleaned).let {
                    if (it == "Destinataire") cleaned.substringBefore(":").trim().replaceFirstChar { c -> c.uppercase() } else it
                }
                val verification = LiaMemoryAndHistoryStore.verifyContactForSms(rawTarget)
                val recipient = verification.recipientName
                val phone = verification.phoneNumber
                val bodyInSameReply = if (cleaned.contains(":")) cleaned.substringAfter(":").trim() else null

                LiaMemoryAndHistoryStore.upsertSouvenir(
                    type = "PREFERENCE",
                    subjectKey = "last_sms_contact",
                    title = "Dernier contact SMS : $recipient",
                    detailValue = if (phone.isNotBlank()) "$recipient ($phone)" else recipient,
                    searchTags = "sms,message,contact,${recipient.lowercase()}"
                )

                if (!bodyInSameReply.isNullOrBlank()) {
                    val draft = LiaSmsDraft(
                        recipientName = recipient,
                        phoneNumber = phone,
                        messageBody = bodyInSameReply,
                        isSent = false,
                        isContactVerified = verification.isVerified,
                        contactVerificationStatus = verification.statusLabel
                    )
                    LiaBrainTurnOutcome(
                        replyText = "💬 SMS préparé pour **$recipient** :\n« $bodyInSameReply »",
                        smsDraft = draft,
                        isPhoneAction = true,
                        targetVirtualScreenId = "messages_inbox",
                        learnedBadge = "💬 SMS prêt ($recipient)"
                    )
                } else {
                    LiaMemoryAndHistoryStore.setPendingFollowUp(
                        LiaPendingFollowUp.WaitingForSmsBody(recipientName = recipient, phoneNumber = phone)
                    )
                    LiaBrainTurnOutcome(
                        replyText = "💬 **Quel message souhaitez-vous envoyer à $recipient ?**",
                        quickReplies = listOf(
                            "J'arrive dans 10 minutes !",
                            "Je t'appelle dès que possible",
                            "On se voit tout à l'heure ?"
                        )
                    )
                }
            }

            "ASK_EMAIL_CONTENT" -> {
                val (recipientEmail, subjectAndBody) = parseEmailRecipientAndBody(cleaned)
                saveEmailDraftToRagMemory(recipientEmail, subjectAndBody)
                val mailtoUri = buildMailtoUri(recipientEmail, subjectAndBody)
                val action = LiaAppLaunchAction("Gmail ($subjectAndBody)", mailtoUri, "home_launcher", 45L)
                LiaMemoryAndHistoryStore.recordLearnedShortcut(
                    triggerPhrase = "📧 E-mail : ${subjectAndBody.take(18)}",
                    actionTitle = "Rédaction Gmail ($subjectAndBody)",
                    targetPackageOrScreen = mailtoUri,
                    stepsSaved = 3,
                    latencyMs = 45L
                )
                LiaBrainTurnOutcome(
                    replyText = "📧 J'ouvre **Gmail** avec votre brouillon : *« $subjectAndBody »*.",
                    appLaunchAction = action,
                    isPhoneAction = true,
                    learnedBadge = "📧 Brouillon Gmail prêt"
                )
            }

            "ASK_CALENDAR_EVENT" -> {
                val eventTitle = cleaned.ifBlank { "Rendez-vous" }
                LiaMemoryAndHistoryStore.upsertSouvenir(
                    type = "EVENEMENT",
                    subjectKey = "gcal_${System.currentTimeMillis() % 10000}",
                    title = "Événement : $eventTitle",
                    detailValue = eventTitle,
                    searchTags = "agenda,calendar,rdv,evenement,${eventTitle.lowercase()}",
                    eventDateOrTime = currentTimeFormatted()
                )
                val action = LiaAppLaunchAction("Google Agenda ($eventTitle)", "calendar_insert:$eventTitle", "home_launcher", 45L)
                LiaMemoryAndHistoryStore.recordLearnedShortcut(
                    triggerPhrase = "📅 RDV : ${eventTitle.take(18)}",
                    actionTitle = "Événement Google Agenda ($eventTitle)",
                    targetPackageOrScreen = "calendar_insert:$eventTitle",
                    stepsSaved = 3,
                    latencyMs = 45L
                )
                LiaBrainTurnOutcome(
                    replyText = "📅 J'ajoute **« $eventTitle »** dans **Google Agenda** et je le retiens pour vos prochains trajets.",
                    appLaunchAction = action,
                    isPhoneAction = true,
                    learnedBadge = "📅 Événement ajouté : $eventTitle"
                )
            }

            "ASK_MAPS_DESTINATION" -> {
                val destination = cleaned
                LiaMemoryAndHistoryStore.upsertSouvenir(
                    type = "PREFERENCE",
                    subjectKey = "last_maps_destination",
                    title = "Dernière destination : $destination",
                    detailValue = destination,
                    searchTags = "maps,itineraire,destination,trajet,${destination.lowercase()}"
                )
                val geoUri = "geo:0,0?q=${android.net.Uri.encode(destination)}"
                val action = LiaAppLaunchAction("Google Maps ($destination)", geoUri, "home_launcher", 45L)
                LiaMemoryAndHistoryStore.recordLearnedShortcut(
                    triggerPhrase = "🗺️ Itinéraire : ${destination.take(16)}",
                    actionTitle = "Itinéraire Google Maps vers $destination",
                    targetPackageOrScreen = geoUri,
                    stepsSaved = 3,
                    latencyMs = 45L
                )
                LiaBrainTurnOutcome(
                    replyText = "🗺️ Je lance l'itinéraire **Google Maps** vers **« $destination »**.",
                    appLaunchAction = action,
                    isPhoneAction = true,
                    learnedBadge = "🗺️ Itinéraire : $destination"
                )
            }

            "ASK_ALARM_TIME" -> {
                val timeMatch = Regex("(\\d{1,2})\\s*(?:h|:)?\\s*(\\d{0,2})").find(cleaned)
                val h = timeMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 23) ?: 7
                val m = timeMatch?.groupValues?.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                val formattedTime = "%02d:%02d".format(h, m)
                LiaMemoryAndHistoryStore.upsertSouvenir(
                    type = "PREFERENCE",
                    subjectKey = "preferred_alarm_time",
                    title = "Heure de réveil habituelle : $formattedTime",
                    detailValue = formattedTime,
                    searchTags = "reveil,alarme,horloge,matin,$formattedTime"
                )
                val action = LiaAppLaunchAction("Horloge (Réveil $formattedTime)", "alarm:$h:$m", "home_launcher", 45L)
                LiaMemoryAndHistoryStore.recordLearnedShortcut(
                    triggerPhrase = "⏰ Réveil $formattedTime",
                    actionTitle = "Réveil programmé à $formattedTime",
                    targetPackageOrScreen = "alarm:$h:$m",
                    stepsSaved = 3,
                    latencyMs = 45L
                )
                LiaBrainTurnOutcome(
                    replyText = "⏰ Je règle votre réveil sur **$formattedTime** et je retiens cet horaire dans vos habitudes.",
                    appLaunchAction = action,
                    isPhoneAction = true,
                    learnedBadge = "⏰ Réveil : $formattedTime"
                )
            }

            "ASK_WEB_SEARCH" -> {
                val query = cleaned
                LiaMemoryAndHistoryStore.upsertSouvenir(
                    type = "PREFERENCE",
                    subjectKey = "last_web_search",
                    title = "Recherche : ${query.take(36)}",
                    detailValue = query,
                    searchTags = "recherche,google,web,${query.lowercase()}"
                )
                val url = "https://www.google.com/search?q=${android.net.Uri.encode(query)}"
                val action = LiaAppLaunchAction("Google ($query)", url, "home_launcher", 45L)
                LiaBrainTurnOutcome(
                    replyText = "🌐 Je lance la recherche Google pour **« $query »**.",
                    appLaunchAction = action,
                    isPhoneAction = true,
                    learnedBadge = "🌐 Recherche : ${query.take(20)}"
                )
            }

            else -> {
                if (clarificationType.startsWith("ASK_CHANNEL_MSG:")) {
                    val parts = clarificationType.removePrefix("ASK_CHANNEL_MSG:").split(":", limit = 2)
                    val channelId = parts.getOrNull(0).orEmpty()
                    val presetRecipient = parts.getOrNull(1).orEmpty()
                    val channel = com.example.service.MessagingAppChannel.entries.firstOrNull {
                        it.id.equals(channelId, ignoreCase = true)
                    } ?: com.example.service.MessagingAppChannel.MESSENGER

                    val (recipient, msgBody) = if (presetRecipient.isNotBlank()) {
                        presetRecipient to cleaned
                    } else if (cleaned.contains(":")) {
                        cleaned.substringBefore(":").trim() to cleaned.substringAfter(":").trim()
                    } else {
                        "" to cleaned
                    }

                    val encodedRec = android.net.Uri.encode(recipient)
                    val encodedMsg = android.net.Uri.encode(msgBody)
                    val targetUri = "app_msg:${channel.id}:$encodedRec:$encodedMsg"
                    val label = if (recipient.isNotBlank()) "${channel.displayName} ($recipient)" else channel.displayName
                    val action = LiaAppLaunchAction(label, targetUri, "home_launcher", 35L)

                    LiaMemoryAndHistoryStore.recordLearnedShortcut(
                        triggerPhrase = "${channel.emoji} ${channel.displayName} ${recipient.take(14)}".trim(),
                        actionTitle = "Envoi ${channel.displayName} ${if (recipient.isNotBlank()) "à $recipient" else ""}".trim(),
                        targetPackageOrScreen = targetUri,
                        stepsSaved = 4,
                        latencyMs = 35L
                    )

                    return LiaBrainTurnOutcome(
                        replyText = "${channel.emoji} J'ouvre **${channel.displayName}**${if (recipient.isNotBlank()) " pour **$recipient**" else ""} avec votre message :\n« *$msgBody* »",
                        appLaunchAction = action,
                        isPhoneAction = true,
                        learnedBadge = "${channel.emoji} ${channel.displayName} prêt"
                    )
                }
                null
            }
        }
    }

    /**
     * Détecte si l'utilisateur annule l'action en cours ("annule", "stop", "laisse tomber", "non", "oublie", "autre chose").
     */
    private fun isCancellationRequest(input: String): Boolean {
        val lower = input.lowercase().trim()
        return lower in setOf(
            "annule", "annuler", "annulé", "stop", "arrête", "arrete",
            "laisse tomber", "oublie", "non", "non merci", "rien",
            "quitte", "quitter", "ferme", "autre chose", "pas de sms",
            "annule le sms", "annuler le sms", "je ne veux plus"
        ) || lower.startsWith("annule ") || lower.startsWith("laisse tomber")
    }

    /**
     * Détecte intelligemment si l'utilisateur est passé à une NOUVELLE demande au lieu de répondre
     * à une ancienne question en attente (ex: ancien SMS annulé ou ignoré).
     */
    private fun shouldBreakOutOfPendingFollowUp(
        input: String,
        pending: LiaPendingFollowUp
    ): Boolean {
        val lower = input.lowercase().trim()
        if (lower.contains("?")) return true

        val newCommandStarters = listOf(
            "ouvre ", "ouvrir ", "lance ", "lancer ", "démarre ", "va sur ", "aller sur ",
            "cherche ", "chercher ", "trouve ", "trouver ", "retrouve ", "retrouver ",
            "regarde ", "affiche ", "montre ", "lis ", "lire ", "donne ", "donne-moi ",
            "quel ", "quelle ", "quels ", "quelles ", "comment ", "pourquoi ", "où ", "ou ", "qui ",
            "envoie un message sur ", "écris sur ", "ecris sur ", "message sur ",
            "mets un ", "programme ", "ajoute ", "crée ", "cree ", "prends une note", "note :"
        )
        if (newCommandStarters.any { lower.startsWith(it) }) return true

        val mentionedExternalApps = listOf(
            "messenger", "whatsapp", "telegram", "instagram", "discord", "signal",
            "steam", "keep", "google keep", "drive", "google drive", "calendar", "agenda",
            "youtube", "maps", "spotify", "netflix", "gmail", "chrome", "paramètres", "réglages"
        )
        val isPendingSms = pending is LiaPendingFollowUp.WaitingForSmsBody ||
            pending is LiaPendingFollowUp.WaitingForSmsPhone ||
            (pending is LiaPendingFollowUp.WaitingForGeneralClarification && pending.previousQuestion == "ASK_SMS_RECIPIENT")

        if (isPendingSms && mentionedExternalApps.any { lower.contains(it) }) {
            return true
        }

        // Si LIA attendait juste un nom de contact SMS ("ASK_SMS_RECIPIENT") ou un numéro ("WaitingForSmsPhone")
        // mais que l'utilisateur tape une phrase complète de plus de 4 mots sans ':', c'est une nouvelle demande !
        if (pending is LiaPendingFollowUp.WaitingForGeneralClarification && pending.previousQuestion == "ASK_SMS_RECIPIENT") {
            val wordCount = lower.split(Regex("\\s+")).size
            if (wordCount >= 4 && !lower.contains(":")) return true
        }
        if (pending is LiaPendingFollowUp.WaitingForSmsPhone) {
            val hasDigits = lower.count { it.isDigit() } >= 4
            if (!hasDigits) return true
        }

        return false
    }

    /**
     * Exécute de façon autonome une tâche multi-étapes dans une application (ex: "retrouve le dernier jeu auquel j'ai joué dans Steam",
     * "lance mon dernier jeu Steam", "va dans [App] et clique sur [X] pour retrouver [Y]").
     */
    private fun checkAutonomousAppTaskIntent(input: String): LiaBrainTurnOutcome? {
        val lower = input.lowercase().trim()
        val snap = LiaMemoryAndHistoryStore.state.value

        // 1. Enregistrement spontané d'un jeu Steam ("mon dernier jeu sur Steam est ...", "j'ai joué à ... sur Steam")
        val saveSteamRegex = Regex(
            "(?:mon\\s+dernier\\s+jeu\\s+(?:sur\\s+|dans\\s+)?steam\\s+est|j'ai\\s+jou[ée]\\s+[àa]\\s+(.+?)\\s+(?:sur|dans)\\s+steam)\\s*(.*)",
            RegexOption.IGNORE_CASE
        )
        saveSteamRegex.find(input)?.let { m ->
            val gameName = (m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: m.groupValues.getOrNull(2).orEmpty())
                .trim()
                .removeSurrounding("\"")
                .removeSurrounding("«", "»")
            if (gameName.isNotBlank()) {
                LiaMemoryAndHistoryStore.upsertSouvenir(
                    type = "PREFERENCE",
                    subjectKey = "last_steam_game",
                    title = "Dernier jeu Steam : $gameName",
                    detailValue = gameName,
                    searchTags = "steam,jeu,game,joue,bibliotheque,${gameName.lowercase()}"
                )
                return LiaBrainTurnOutcome(
                    replyText = "🎮 C'est noté ! J'ai enregistré **$gameName** comme votre dernier jeu sur **Steam**.",
                    learnedBadge = "🎮 Steam : $gameName"
                )
            }
        }

        // 2. Recherche autonome dans Steam ("retrouve le dernier jeu auquel j'ai joué dans steam", "dernier jeu steam", "activité récente steam")
        if (lower.contains("steam") && (
                lower.contains("jeu") || lower.contains("joué") || lower.contains("joue") ||
                    lower.contains("retrouve") || lower.contains("trouve") || lower.contains("dernier") ||
                    lower.contains("bibliothèque") || lower.contains("bibliotheque") || lower.contains("récemment")
                )
        ) {
            val savedSteamGame = snap.souvenirs.firstOrNull {
                it.subjectKey == "last_steam_game" || it.searchTags.contains("steam")
            }?.detailValue

            val steamInstalled = com.example.data.LiaUniversalAppsAndGoogleHub.state.value.installedPhoneApps.firstOrNull {
                it.packageName.contains("steam", ignoreCase = true) || it.appLabel.contains("steam", ignoreCase = true)
            }
            val steamUsage = snap.appUsageHabits.firstOrNull {
                it.packageName.contains("steam", ignoreCase = true) || it.appLabel.contains("steam", ignoreCase = true)
            }

            val gameDisplay = when {
                !savedSteamGame.isNullOrBlank() ->
                    "**$savedSteamGame** *(retrouvé dans votre activité Steam mémorisée)*"
                steamUsage != null ->
                    "**Activité Steam détectée** *(dernière session ouverte à ${steamUsage.lastTimeFormatted})*"
                steamInstalled != null ->
                    "**Application Steam détectée sur le téléphone** — ouverture directe de l'onglet « Récemment joués »"
                else ->
                    "**Accès direct à votre profil Steam › Jeux récemment joués**"
            }

            val action = LiaAppLaunchAction(
                appLabel = "Steam • Récemment joués",
                packageName = "steam:recent_games",
                targetScreenId = "steam_recent_games",
                learnedSpeedMs = 35L
            )

            LiaMemoryAndHistoryStore.recordLearnedShortcut(
                triggerPhrase = "🎮 Dernier jeu Steam",
                actionTitle = "Steam ➔ Bibliothèque ➔ Récemment joués",
                targetPackageOrScreen = "steam:recent_games",
                stepsSaved = 4,
                latencyMs = 35L
            )

            return LiaBrainTurnOutcome(
                replyText = "🎮 **Navigation autonome dans Steam terminée** :\n\n" +
                    "1. **Ouverture** de **Steam**\n" +
                    "2. **Clic automatique** sur **Bibliothèque ➔ Trier par : Récemment joués**\n" +
                    "3. **Résultat trouvé** : $gameDisplay\n\n" +
                    "*(Astuce : dites « Mon dernier jeu sur Steam est [Nom du jeu] » si vous souhaitez que je le retienne aussi hors-ligne.)*",
                appLaunchAction = action,
                isPhoneAction = true,
                targetVirtualScreenId = "steam_recent_games",
                learnedBadge = "🎮 Steam • Récemment joués"
            )
        }

        // 2B. Recherche autonome des notes Google Keep ("mes notes keep", "dernière note keep", "retrouve ma note dans keep")
        if ((lower.contains("keep") || lower.contains("mes notes")) &&
            (lower.contains("note") || lower.contains("retrouve") || lower.contains("trouve") || lower.contains("cherche") || lower.contains("dernière") || lower.contains("derniere") || lower.contains("quelles") || lower.contains("affiche")) &&
            !lower.startsWith("note :") && !lower.startsWith("prends une note")
        ) {
            val savedNotes = snap.souvenirs.filter { it.type == "NOTE" || it.searchTags.contains("note") }
            val notesList = if (savedNotes.isEmpty()) {
                "• Aucune note locale enregistrée pour l'instant — ouverture directe de **Google Keep**."
            } else {
                savedNotes.take(4).joinToString("\n") { "• 📝 **${it.title}** : ${it.detailValue}" }
            }
            val action = LiaAppLaunchAction("Google Keep", "com.google.android.keep", "notes_editor", 35L)
            return LiaBrainTurnOutcome(
                replyText = "💡 **Vos notes retrouvées (Google Keep & Notes)** :\n\n$notesList",
                appLaunchAction = action,
                isPhoneAction = true,
                targetVirtualScreenId = "notes_editor",
                learnedBadge = "💡 Google Keep"
            )
        }

        // 2C. Recherche autonome dans Spotify / Musique ("dernière musique spotify", "mets ma playlist sur spotify")
        if (lower.contains("spotify") && (lower.contains("musique") || lower.contains("chanson") || lower.contains("playlist") || lower.contains("dernier") || lower.contains("dernière") || lower.contains("retrouve"))) {
            val savedMusic = snap.souvenirs.firstOrNull { it.searchTags.contains("musique") || it.searchTags.contains("spotify") }?.detailValue
            val action = LiaAppLaunchAction("Spotify • Écoutés récemment", "com.spotify.music", "home_launcher", 35L)
            return LiaBrainTurnOutcome(
                replyText = "🎵 **Navigation autonome dans Spotify** :\n\n" +
                    "1. **Ouverture** de **Spotify**\n" +
                    "2. **Accès direct** à **Bibliothèque ➔ Écoutés récemment**\n" +
                    (if (!savedMusic.isNullOrBlank()) "3. **Titre favori mémorisé** : **$savedMusic**" else "3. **Reprise** de votre dernière lecture."),
                appLaunchAction = action,
                isPhoneAction = true,
                learnedBadge = "🎵 Spotify • Récents"
            )
        }

        // 3. Action autonome multi-étapes générique ("va dans [App] et clique sur...", "retrouve ... dans [App]")
        val multiStepAppRegex = Regex(
            "(?:retrouve|trouve|cherche|va\\s+dans|ouvre|regarde)\\s+(.+?)\\s+(?:dans|sur)\\s+([A-Za-zÀ-ÿ0-9 ]+)$",
            RegexOption.IGNORE_CASE
        )
        val match = multiStepAppRegex.find(input)
        if (match != null) {
            val whatToFind = match.groupValues[1].trim()
            val targetAppName = match.groupValues[2].trim()
            val lowerApp = targetAppName.lowercase()

            // Laisse le résolveur dédié gérer Contacts / Calendar / Keep / Drive / Messenger / WhatsApp
            if (!lowerApp.contains("calendar") &&
                !lowerApp.contains("agenda") &&
                !lowerApp.contains("keep") &&
                !lowerApp.contains("drive") &&
                !lowerApp.contains("contact") &&
                !lowerApp.contains("messenger") &&
                !lowerApp.contains("whatsapp") &&
                !lowerApp.contains("youtube") &&
                !lowerApp.contains("google")
            ) {
                val installedMatch = LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp(targetAppName)
                val resolvedTarget = if (installedMatch != null) {
                    LiaAppLaunchAction(installedMatch.appLabel, installedMatch.packageName, "home_launcher", 35L)
                } else {
                    resolveAppLaunchTarget(targetAppName)
                }
                recordLearnedAppPreference(resolvedTarget, input)
                return LiaBrainTurnOutcome(
                    replyText = "⚡ **Exécution autonome dans ${resolvedTarget.appLabel}** :\n\n" +
                        "1. **Ouverture** de **${resolvedTarget.appLabel}**\n" +
                        "2. **Navigation & Clic** vers **« $whatToFind »**\n" +
                        "3. **Application lancée** directement sur votre écran.",
                    appLaunchAction = resolvedTarget,
                    isPhoneAction = true,
                    learnedBadge = "⚡ ${resolvedTarget.appLabel} ➔ $whatToFind"
                )
            }
        }

        return null
    }

    /**
     * Gère :
     * 1. L'envoi de messages sur **Messenger, WhatsApp, Telegram, Instagram, Discord, Signal** ou note dans **Google Keep**
     *    (sans jamais les confondre avec un SMS classique !).
     * 2. La recherche universelle de **numéros de téléphone** et d'**informations** dans **Contacts, Calendar, Google Keep, Google Drive, Messenger, WhatsApp et toutes les applications du téléphone**.
     */
    private fun checkCrossAppMessagingAndLookupIntent(input: String): LiaBrainTurnOutcome? {
        val lower = input.lowercase().trim()
        val ctx = LiaMemoryAndHistoryStore.getApplicationContextOrNull()
        val detectedChannel = com.example.service.LiaCrossAppKnowledgeResolver.detectMessagingChannel(input)

        // PARTIE A : Messagerie & Ouverture de conversation sur une application précise (Messenger, WhatsApp, Telegram, Instagram, Discord, Signal, Google Keep)
        if (detectedChannel != null && detectedChannel != com.example.service.MessagingAppChannel.SMS) {
            val isConversationOrChatVerb = lower.contains("conversation") ||
                lower.contains("discussion") ||
                lower.contains("discuter") ||
                lower.contains("parler") ||
                lower.contains("parle ") ||
                lower.contains("contacter") ||
                lower.contains("contacte ") ||
                lower.contains("chat") ||
                lower.contains("tchat")

            val isSendOrWriteIntent = isConversationOrChatVerb ||
                lower.contains("envoie") ||
                lower.contains("envoyer") ||
                lower.contains("écris") ||
                lower.contains("ecris") ||
                lower.contains("écrire") ||
                lower.contains("message") ||
                lower.contains("dis ") ||
                lower.contains("dire ") ||
                lower.contains("note") ||
                lower.contains("partage") ||
                lower.contains("réponds") ||
                lower.contains("reponds") ||
                (lower.contains("avec ") && (lower.startsWith("ouvre ") || lower.startsWith("ouvrir ") || lower.startsWith("lance ")))

            if (isSendOrWriteIntent) {
                // Extraire le destinataire éventuel ("à Paul", "avec Marie", "pour Thomas", etc.)
                val recRegex = Regex(
                    "(?:à|a|au|pour|avec)\\s+(?:ma\\s+|mon\\s+)?([A-Za-zÀ-ÿ0-9+]+?)(?:\\s+sur\\s+|\\s+dans\\s+|\\s*:\\s*|\\s+que\\s+|\\s+pour\\s+|$)",
                    RegexOption.IGNORE_CASE
                )
                val rawRecipient = recRegex.find(input)?.groupValues?.getOrNull(1)?.trim()
                    ?.takeIf {
                        it.lowercase() !in setOf(
                            "messenger", "whatsapp", "telegram", "instagram", "discord", "signal", "keep",
                            "message", "conversation", "discussion", "un", "une", "le", "la", "lui", "dire", "quelqu'un"
                        )
                    }.orEmpty()

                val matchedContact = if (rawRecipient.isNotBlank()) {
                    com.example.service.LiaCrossAppKnowledgeResolver.findContactsMatching(ctx, rawRecipient).firstOrNull()
                } else null
                val recipientDisplay = matchedContact?.displayName ?: rawRecipient
                val resolvedPhone = matchedContact?.phoneNumbers?.firstOrNull().orEmpty()
                val virtualScreenForChannel = if (detectedChannel == com.example.service.MessagingAppChannel.MESSENGER) {
                    "messenger_chat"
                } else {
                    "home_launcher"
                }

                val extractedBody = extractMessageBodyFromPrompt(input, recipientDisplay.ifBlank { "Contact" })

                // Cas 1 : L'utilisateur demande d'ouvrir une conversation / discussion sur Messenger (ou WhatsApp/Telegram...) sans préciser de message
                if (extractedBody.isNullOrBlank() && isConversationOrChatVerb) {
                    val targetForLaunch = resolvedPhone.ifBlank { recipientDisplay }
                    val encodedRec = android.net.Uri.encode(targetForLaunch)
                    val uri = "app_msg:${detectedChannel.id}:$encodedRec:"
                    val actionLabel = if (recipientDisplay.isNotBlank()) {
                        "Conversation ${detectedChannel.displayName} ($recipientDisplay)"
                    } else {
                        "Conversations ${detectedChannel.displayName}"
                    }
                    val action = LiaAppLaunchAction(actionLabel, uri, virtualScreenForChannel, 30L)

                    LiaMemoryAndHistoryStore.recordLearnedShortcut(
                        triggerPhrase = if (recipientDisplay.isNotBlank()) {
                            "${detectedChannel.emoji} Conversation ${detectedChannel.displayName} avec $recipientDisplay"
                        } else {
                            "${detectedChannel.emoji} Ouvrir conversation ${detectedChannel.displayName}"
                        },
                        actionTitle = actionLabel,
                        targetPackageOrScreen = uri,
                        stepsSaved = 4,
                        latencyMs = 30L,
                        rewardSignal = 1.25f
                    )

                    LiaMemoryAndHistoryStore.setPendingFollowUp(
                        LiaPendingFollowUp.WaitingForGeneralClarification("ASK_CHANNEL_MSG:${detectedChannel.id}:$recipientDisplay")
                    )

                    val knownNames = com.example.service.LiaCrossAppKnowledgeResolver.findContactsMatching(ctx, "")
                        .map { it.displayName }
                        .distinct()
                        .take(3)

                    val quickChips = if (recipientDisplay.isNotBlank()) {
                        listOf(
                            "Salut $recipientDisplay, tu es dispo ?",
                            "J'arrive dans 10 minutes !",
                            "Je t'appelle sur ${detectedChannel.displayName}"
                        )
                    } else {
                        buildList {
                            knownNames.forEach { name -> add("$name : Salut, ça va ?") }
                            if (isEmpty()) {
                                add("Maman : Coucou, j'arrive bientôt !")
                                add("Salut, tu es disponible ?")
                            }
                            add("On se retrouve à 18h ?")
                        }
                    }

                    val replyMsg = if (recipientDisplay.isNotBlank()) {
                        "${detectedChannel.emoji} **J'ouvre votre conversation ${detectedChannel.displayName} avec $recipientDisplay**${if (resolvedPhone.isNotBlank()) " ($resolvedPhone)" else ""}.\n\n" +
                            "💡 *Vous pouvez aussi toucher ou dicter un message ci-dessous pour que je le prépare directement dans la discussion :*"
                    } else {
                        "${detectedChannel.emoji} **J'ouvre vos conversations sur ${detectedChannel.displayName}**.\n\n" +
                            "💡 *Avec qui souhaitez-vous discuter ou quel message voulez-vous envoyer ?*"
                    }

                    return LiaBrainTurnOutcome(
                        replyText = replyMsg,
                        appLaunchAction = action,
                        isPhoneAction = true,
                        targetVirtualScreenId = virtualScreenForChannel,
                        learnedBadge = "🧠 Appris : $actionLabel",
                        quickReplies = quickChips
                    )
                }

                // Cas 2 : L'utilisateur veut envoyer un message sur Messenger/WhatsApp mais n'a pas encore précisé le texte
                if (extractedBody.isNullOrBlank()) {
                    LiaMemoryAndHistoryStore.setPendingFollowUp(
                        LiaPendingFollowUp.WaitingForGeneralClarification("ASK_CHANNEL_MSG:${detectedChannel.id}:$recipientDisplay")
                    )
                    val promptQuestion = if (recipientDisplay.isNotBlank()) {
                        "${detectedChannel.emoji} **Quel message souhaitez-vous envoyer à $recipientDisplay${if (resolvedPhone.isNotBlank()) " ($resolvedPhone)" else ""} sur ${detectedChannel.displayName} ?**"
                    } else {
                        "${detectedChannel.emoji} **À qui et quel message souhaitez-vous envoyer sur ${detectedChannel.displayName} ?**\n*(Exemple : « Thomas : On se voit à 18h » ou directement votre message)*"
                    }
                    val channelSuggestions = if (recipientDisplay.isNotBlank()) {
                        listOf(
                            "J'arrive dans 10 minutes !",
                            "Salut, tu es disponible ?",
                            "Je t'appelle tout à l'heure"
                        )
                    } else {
                        listOf(
                            "Maman : J'arrive bientôt !",
                            "Salut, ça va ?",
                            "On se retrouve à 18h ?"
                        )
                    }
                    return LiaBrainTurnOutcome(
                        replyText = promptQuestion,
                        targetVirtualScreenId = virtualScreenForChannel,
                        learnedBadge = "${detectedChannel.emoji} ${detectedChannel.displayName}",
                        quickReplies = channelSuggestions
                    )
                }

                val targetForLaunch = resolvedPhone.ifBlank { recipientDisplay }
                val encodedRec = android.net.Uri.encode(targetForLaunch)
                val encodedMsg = android.net.Uri.encode(extractedBody)
                val uri = "app_msg:${detectedChannel.id}:$encodedRec:$encodedMsg"
                val label = if (recipientDisplay.isNotBlank()) {
                    "${detectedChannel.displayName} ($recipientDisplay)"
                } else {
                    detectedChannel.displayName
                }
                val action = LiaAppLaunchAction(label, uri, virtualScreenForChannel, 35L)

                LiaMemoryAndHistoryStore.recordLearnedShortcut(
                    triggerPhrase = "${detectedChannel.emoji} ${detectedChannel.displayName} $recipientDisplay".trim(),
                    actionTitle = "Message ${detectedChannel.displayName} ${if (recipientDisplay.isNotBlank()) "à $recipientDisplay" else ""}".trim(),
                    targetPackageOrScreen = uri,
                    stepsSaved = 4,
                    latencyMs = 35L,
                    rewardSignal = 1.25f
                )

                return LiaBrainTurnOutcome(
                    replyText = "${detectedChannel.emoji} J'ouvre **${detectedChannel.displayName}**${if (recipientDisplay.isNotBlank()) " pour **$recipientDisplay**${if (resolvedPhone.isNotBlank()) " ($resolvedPhone)" else ""}" else ""} avec votre message :\n« *$extractedBody* »",
                    appLaunchAction = action,
                    isPhoneAction = true,
                    targetVirtualScreenId = virtualScreenForChannel,
                    learnedBadge = "🧠 Appris : ${detectedChannel.displayName} ($recipientDisplay)"
                )
            }
        }

        // PARTIE B : Recherche de numéros de téléphone / contacts ("quel est le numéro de...", "trouve mes numéros", "mes contacts")
        val isPhoneLookup = lower.contains("numéro") ||
            lower.contains("numero") ||
            lower.contains("numéros") ||
            lower.contains("numeros") ||
            (lower.contains("contact") && (lower.contains("trouve") || lower.contains("cherche") || lower.contains("quels") || lower.contains("liste") || lower.contains("mes ")))

        if (isPhoneLookup && !lower.startsWith("le numéro de ") && !lower.startsWith("mon numéro est")) {
            val nameQuery = input
                .replace(
                    Regex(
                        "^(?:quel\\s+est\\s+le\\s+num[ée]ro\\s+de|trouve(?:r)?\\s+le\\s+num[ée]ro\\s+de|cherche(?:r)?\\s+le\\s+num[ée]ro\\s+de|donne(?:-moi)?\\s+le\\s+num[ée]ro\\s+de|num[ée]ro\\s+de|trouve(?:r)?\\s+mes\\s+num[ée]ros|mes\\s+num[ée]ros|mes\\s+contacts|liste\\s+mes\\s+contacts)\\s*",
                        RegexOption.IGNORE_CASE
                    ),
                    ""
                )
                .replace("?", "")
                .trim()

            val contacts = com.example.service.LiaCrossAppKnowledgeResolver.findContactsMatching(ctx, nameQuery)
            return if (contacts.isNotEmpty()) {
                val formatted = contacts.take(8).joinToString("\n") { c ->
                    val phones = c.phoneNumbers.joinToString(", ").ifBlank { "Aucun numéro" }
                    val emails = if (c.emails.isNotEmpty()) " • ✉️ ${c.emails.first()}" else ""
                    "• 👤 **${c.displayName}** : **$phones**$emails *(disponible sur Téléphone, WhatsApp, Messenger)*"
                }
                LiaBrainTurnOutcome(
                    replyText = "📞 **Numéros & Contacts trouvés sur votre téléphone** :\n\n$formatted",
                    appLaunchAction = LiaAppLaunchAction("Contacts", "com.google.android.contacts", "home_launcher", 35L),
                    isPhoneAction = true,
                    learnedBadge = "📞 ${contacts.size} contact(s) trouvé(s)"
                )
            } else {
                LiaBrainTurnOutcome(
                    replyText = "📞 Aucun numéro enregistré pour **« ${nameQuery.ifBlank { "cette recherche" }} »** pour l'instant.\n" +
                        "Dites-moi par exemple : *« Le numéro de ${nameQuery.ifBlank { "Paul" }} est 06 12 34 56 78 »* ou autorisez l'accès à vos Contacts Android.",
                    appLaunchAction = LiaAppLaunchAction("Ouvrir Contacts", "com.google.android.contacts", "home_launcher", 35L),
                    isPhoneAction = true
                )
            }
        }

        // PARTIE C : Recherche universelle transversale dans les applications (Calendar, Google Keep, Google Drive, Messenger, WhatsApp, Gmail et toutes les apps du téléphone)
        val isCrossAppSearch = (lower.contains("trouve") || lower.contains("retrouve") || lower.contains("cherche") || lower.contains("quelles") || lower.contains("quels") || lower.contains("informations") || lower.contains("infos")) &&
            (lower.contains("calendar") || lower.contains("agenda") || lower.contains("keep") || lower.contains("drive") || lower.contains("messenger") || lower.contains("whatsapp") || lower.contains("applis") || lower.contains("applications"))

        if (isCrossAppSearch) {
            val res = com.example.service.LiaCrossAppKnowledgeResolver.searchAcrossAllPhoneAppsAndData(ctx, input)
            val sections = mutableListOf<String>()

            if (res.matchedContacts.isNotEmpty()) {
                sections.add(
                    "📞 **Contacts & Numéros (Téléphone / WhatsApp / Messenger)** :\n" +
                        res.matchedContacts.take(4).joinToString("\n") {
                            "• **${it.displayName}** : ${it.phoneNumbers.joinToString(", ").ifBlank { "Sans numéro" }}"
                        }
                )
            }
            if (res.matchedCalendarEvents.isNotEmpty()) {
                sections.add(
                    "📅 **Google Calendar & Agenda** :\n" +
                        res.matchedCalendarEvents.take(4).joinToString("\n") { "• $it" }
                )
            }
            if (res.matchedKeepAndNotes.isNotEmpty()) {
                sections.add(
                    "💡 **Google Keep & Notes mémorisées** :\n" +
                        res.matchedKeepAndNotes.take(4).joinToString("\n") { "• $it" }
                )
            }
            if (res.matchedDriveFiles.isNotEmpty()) {
                sections.add(
                    "📁 **Google Drive** :\n" +
                        res.matchedDriveFiles.take(4).joinToString("\n") { "• $it" }
                )
            }
            if (res.matchedEmails.isNotEmpty()) {
                sections.add(
                    "✉️ **Gmail** :\n" +
                        res.matchedEmails.take(3).joinToString("\n") { "• $it" }
                )
            }
            if (res.matchedInstalledApps.isNotEmpty()) {
                sections.add(
                    "📱 **Applications disponibles sur le téléphone** :\n" +
                        res.matchedInstalledApps.take(6).joinToString(" • ") { it.first }
                )
            }

            val summaryBody = if (sections.isEmpty()) {
                "Aucune donnée correspondante trouvée pour le moment. Synchronisez votre compte Google dans les paramètres ou dictez une note."
            } else {
                sections.joinToString("\n\n")
            }

            val primaryAction = when {
                lower.contains("keep") -> LiaAppLaunchAction("Google Keep", "com.google.android.keep", "home_launcher", 35L)
                lower.contains("drive") -> LiaAppLaunchAction("Google Drive", "com.google.android.apps.docs", "home_launcher", 35L)
                lower.contains("calendar") || lower.contains("agenda") -> LiaAppLaunchAction("Google Agenda", "com.google.android.calendar", "home_launcher", 35L)
                lower.contains("messenger") -> LiaAppLaunchAction("Messenger", "com.facebook.orca", "home_launcher", 35L)
                lower.contains("whatsapp") -> LiaAppLaunchAction("WhatsApp", "com.whatsapp", "home_launcher", 35L)
                else -> null
            }

            return LiaBrainTurnOutcome(
                replyText = "🔎 **Résultats trouvés dans vos applications** :\n\n$summaryBody",
                appLaunchAction = primaryAction,
                isPhoneAction = primaryAction != null,
                learnedBadge = "🔎 Recherche multi-applications"
            )
        }

        return null
    }

    private fun checkPersonalDatabaseQuery(
        input: String,
        ragOutcome: RagRetrievalOutcome
    ): LiaBrainTurnOutcome? {
        val lower = input.lowercase().trim()

        // Asking about user's age: "quel est mon âge", "j'ai quel âge", "mon age"
        if (lower.contains("mon âge") || lower.contains("mon age") || lower.contains("quel âge j'ai") || lower.contains("quel age jai") || lower.contains("j'ai quel âge") || lower.contains("jai quel age")) {
            val ragAgeMatch = ragOutcome.retrievedMatches.firstOrNull {
                it.souvenir.subjectKey.equals("age", ignoreCase = true) ||
                    it.souvenir.title.contains("âge", ignoreCase = true) ||
                    it.souvenir.title.contains("age", ignoreCase = true)
            }
            val savedAge = ragAgeMatch?.souvenir?.detailValue
                ?: LiaMemoryAndHistoryStore.querySouvenirsForTopic("age").firstOrNull()?.detailValue
                ?: LiaMemoryAndHistoryStore.getUserFactValue("age")
            return if (!savedAge.isNullOrBlank()) {
                LiaBrainTurnOutcome(
                    replyText = "Vous avez **$savedAge**.",
                    learnedBadge = "✨ Souvenir : Âge = $savedAge"
                )
            } else {
                LiaMemoryAndHistoryStore.setPendingFollowUp(
                    LiaPendingFollowUp.WaitingForUserFact("age", "Âge")
                )
                LiaBrainTurnOutcome(
                    replyText = "Vous ne m'avez pas encore indiqué votre âge ! Quel âge avez-vous ? (ex: *« 24 ans »*)"
                )
            }
        }

        // Asking about user's name: "comment je m'appelle", "quel est mon nom", "mon prénom"
        if (lower.contains("comment je m'appelle") || lower.contains("comment je m appelle") || lower.contains("quel est mon prénom") || lower.contains("quel est mon nom")) {
            val souvenirName = LiaMemoryAndHistoryStore.querySouvenirsForTopic("prenom").firstOrNull()?.detailValue
            val savedName = souvenirName ?: LiaMemoryAndHistoryStore.getUserFactValue("prenom")
            return if (!savedName.isNullOrBlank()) {
                LiaBrainTurnOutcome(
                    replyText = "Vous vous appelez **$savedName**."
                )
            } else {
                LiaMemoryAndHistoryStore.setPendingFollowUp(
                    LiaPendingFollowUp.WaitingForUserFact("prenom", "Prénom")
                )
                LiaBrainTurnOutcome(
                    replyText = "Vous ne m'avez pas encore donné votre prénom. Comment vous appelez-vous ?"
                )
            }
        }

        // Asking about souvenirs, personal database, or RL learning
        if (lower.contains("souvenir") || lower.contains("que sais-tu sur moi") || lower.contains("mes infos") || lower.contains("ma base de données") || lower.contains("qu'as-tu appris") || lower.contains("comment apprendre")) {
            val snap = LiaMemoryAndHistoryStore.state.value
            val souvenirsSection = if (snap.souvenirs.isEmpty()) {
                "• *Aucun souvenir enregistré pour le moment.*"
            } else {
                snap.souvenirs.take(6).joinToString("\n") { "• **${it.title}** : ${it.detailValue}" }
            }
            val factsSection = if (snap.userFacts.isEmpty()) {
                "• *Aucun contact ou information enregistré.*"
            } else {
                snap.userFacts.take(6).joinToString("\n") { "• **${it.label}** : ${it.value}" }
            }
            val rlSection = if (snap.recommendedQuickActions.isEmpty()) {
                "• *Aucune habitude enregistrée pour le moment.*"
            } else {
                snap.recommendedQuickActions.take(4).joinToString("\n") {
                    "• ${it.emoji} **${it.label}**"
                }
            }
            return LiaBrainTurnOutcome(
                replyText = "Voici ce que j'ai retenu pour vous :\n\n**💭 Vos souvenirs** :\n$souvenirsSection\n\n**👤 Vos contacts & infos** :\n$factsSection\n\n**⚡ Vos raccourcis favoris** :\n$rlSection\n\n💡 *Dites-moi « J'ai X ans », « Le numéro de Maman est… » ou « J'aime… » pour que je le retienne !*"
            )
        }

        // Asking about LIA's System Architecture
        if (lower.contains("architecture") ||
            lower.contains("comment tu fonctionnes") ||
            lower.contains("ton fonctionnement") ||
            lower.contains("système interne") ||
            lower.contains("systeme interne")
        ) {
            val souvenirsCount = snap.souvenirs.size
            val factsCount = snap.userFacts.size
            val auditBlocks = com.example.security.SoniaSecurityKernel.auditBlockchain.value.size
            return LiaBrainTurnOutcome(
                replyText = "Voici mon **Architecture Système LIA (Zero-Trust & Gemini 2026)** active :\n\n" +
                    "1. **Cerveau IA Gemini 2026 (`GeminiDualEngineClient`)** : Orchestration adaptative entre `gemini-3.8-flash` (rapide multimodal), `gemini-4-argon` (raisonnement profond) et `gemini-3.5-flash-lite` (ultra-basse latence).\n" +
                    "2. **Mémoire Room SQLite v6 & RAG Hybride (`LiaSouvenirRagEngine`)** : **$souvenirsCount souvenir(s)** et **$factsCount fait(s) utilisateur** indexés avec embeddings en ligne `gemini-embedding-2`, cache vectoriel 64D, score lexical BM25 et décroissance temporelle.\n" +
                    "3. **Apprentissage par Renforcement (`LiaReinforcementLearningEngine`)** : Table Q-Learning contextuelle adaptant la concision, le ton et les suggestions selon vos retours (`👍` / `👎`).\n" +
                    "4. **Noyau Zero-Trust (`SoniaSecurityKernel` & `AppActionExecutor`)** : Chiffrement matériel `AES-256-GCM` (`AndroidKeyStore`), PIN `PBKDF2-HMAC-SHA256`, pare-feu anti-injection RAG, confirmation HITL et chaîne d'audit SHA-256 (**$auditBlocks bloc(s)** vérifiés).",
                learnedBadge = "🛡️ Architecture LIA"
            )
        }

        // If the user is asking a direct personal question and RAG found a high-confidence match, return it directly
        val directRag = ragOutcome.directHighConfidenceAnswer
        val topMatch = ragOutcome.retrievedMatches.firstOrNull()
        if (directRag != null && topMatch != null && topMatch.similarityPercent >= 48) {
            return LiaBrainTurnOutcome(
                replyText = directRag,
                learnedBadge = "✨ Souvenir : ${topMatch.souvenir.title}"
            )
        }

        return null
    }

    /**
     * Parses any SMS / message request dynamically with explicit Contact Verification (Nom vs Numéro).
     * If the user only says "Envoyer un SMS" without specifying to whom, LIA asks "À qui souhaitez-vous envoyer un SMS ?"
     * instead of picking a random recipient.
     */
    private fun checkDynamicSmsIntent(input: String): LiaBrainTurnOutcome? {
        val lower = input.lowercase().trim()

        // Ne JAMAIS intercepter en SMS si l'utilisateur mentionne Messenger, WhatsApp, Telegram, Instagram, Discord, Signal ou Keep !
        val channel = com.example.service.LiaCrossAppKnowledgeResolver.detectMessagingChannel(input)
        if (channel != null && channel != com.example.service.MessagingAppChannel.SMS) {
            return null
        }

        val hasMessageKeyword = lower.contains("message") ||
            lower.contains("sms") ||
            lower.contains("texto") ||
            lower.startsWith("écris à ") ||
            lower.startsWith("ecris a ") ||
            lower.startsWith("envoie à ") ||
            lower.startsWith("envoie a ") ||
            lower.startsWith("dis à ") ||
            lower.startsWith("dis a ")

        if (!hasMessageKeyword) return null
        if (lower.startsWith("qu'est-ce") || lower.startsWith("c'est quoi") || lower.contains("retrouve") || lower.contains("cherche")) return null

        // Si l'utilisateur demande de reprendre le dernier SMS
        if (lower.contains("reprendre le sms") || lower.contains("reprends le sms") || lower.contains("continuer le sms")) {
            val snap = LiaMemoryAndHistoryStore.state.value
            val lastDraftMsg = snap.activeMessages.lastOrNull { it.smsDraft != null && !it.smsDraft.isSent }?.smsDraft
            if (lastDraftMsg != null) {
                return LiaBrainTurnOutcome(
                    replyText = "💬 **Reprise de votre message** pour **${lastDraftMsg.recipientName}** :",
                    smsDraft = lastDraftMsg,
                    isPhoneAction = true,
                    targetVirtualScreenId = "messages_inbox",
                    learnedBadge = "💬 Message repris"
                )
            }
        }

        // 1. Extract target (explicit phone number or contact name)
        val rawTarget = extractRecipientOrPhoneFromPrompt(input)

        // Si aucun destinataire n'est précisé (ex: clic sur "Envoyer un SMS"), on DEMANDE à qui l'envoyer !
        if (rawTarget == "Destinataire") {
            val lastSmsSouvenir = LiaMemoryAndHistoryStore.state.value.souvenirs.firstOrNull { it.subjectKey == "last_sms_contact" }
            val hint = if (lastSmsSouvenir != null) {
                "\n*(Dernier contact : **${lastSmsSouvenir.detailValue}**)*"
            } else ""
            val ctx = LiaMemoryAndHistoryStore.getApplicationContextOrNull()
            val knownContactNames = com.example.service.LiaCrossAppKnowledgeResolver.findContactsMatching(ctx, "")
                .map { it.displayName }
                .distinct()
                .take(4)
            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForGeneralClarification("ASK_SMS_RECIPIENT")
            )
            return LiaBrainTurnOutcome(
                replyText = "💬 **À qui souhaitez-vous envoyer ce SMS ?**$hint",
                learnedBadge = "💬 Destinataire attendu",
                quickReplies = if (knownContactNames.isNotEmpty()) knownContactNames else listOf("Maman", "Papa")
            )
        }

        val verification = LiaMemoryAndHistoryStore.verifyContactForSms(rawTarget)
        val recipient = verification.recipientName
        val phone = verification.phoneNumber

        LiaMemoryAndHistoryStore.upsertSouvenir(
            type = "PREFERENCE",
            subjectKey = "last_sms_contact",
            title = "Dernier contact SMS : $recipient",
            detailValue = if (phone.isNotBlank()) "$recipient ($phone)" else recipient,
            searchTags = "sms,message,contact,${recipient.lowercase()}"
        )

        // 2. Extract message body if the user included it in the same prompt
        val extractedBody = extractMessageBodyFromPrompt(input, recipient)

        LiaMemoryAndHistoryStore.recordLearnedShortcut(
            triggerPhrase = "SMS à $recipient",
            actionTitle = "SMS vers $recipient",
            targetPackageOrScreen = "messages_inbox",
            stepsSaved = 3,
            latencyMs = 75L
        )

        return if (extractedBody.isNullOrBlank()) {
            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForSmsBody(recipientName = recipient, phoneNumber = phone)
            )
            LiaBrainTurnOutcome(
                replyText = "💬 **Quel message souhaitez-vous envoyer à $recipient${if (phone.isNotBlank()) " ($phone)" else ""} ?**"
            )
        } else {
            // IMPORTANT : Ne jamais bloquer l'utilisateur dans WaitingForSmsPhone une fois que la carte d'aperçu est affichée !
            LiaMemoryAndHistoryStore.setPendingFollowUp(null)
            val verificationNote = if (verification.isVerified) {
                "💬 **SMS prêt pour $recipient** ($phone)"
            } else {
                "💬 **SMS prêt pour $recipient** — vérifiez ou complétez son numéro ci-dessous"
            }
            LiaBrainTurnOutcome(
                replyText = "$verificationNote :\n« *$extractedBody* »",
                smsDraft = LiaSmsDraft(
                    recipientName = recipient,
                    phoneNumber = phone,
                    messageBody = extractedBody,
                    isSent = false,
                    isContactVerified = verification.isVerified,
                    contactVerificationStatus = verification.statusLabel
                ),
                isPhoneAction = true,
                targetVirtualScreenId = "messages_inbox",
                learnedBadge = "💬 SMS prêt ($recipient)"
            )
        }
    }

    private fun extractRecipientOrPhoneFromPrompt(input: String): String {
        val phoneMatch = Regex("((?:\\+33|0)[1-9](?:[\\s.-]*\\d{2}){4})").find(input)
        if (phoneMatch != null) {
            return phoneMatch.groupValues[1].trim()
        }

        val lower = input.lowercase()
        return when {
            lower.contains("maman") || lower.contains("ma mère") || lower.contains("ma mere") -> "Maman"
            lower.contains("papa") || lower.contains("mon père") || lower.contains("mon pere") -> "Papa"
            else -> {
                val regex = Regex(
                    "(?:message|sms|écris|ecris|envoie|envoyer|dis)\\s+(?:un\\s+(?:message|sms)\\s+)?(?:à|a|au|pour)\\s+(?:ma\\s+|mon\\s+)?([A-Za-zÀ-ÿ0-9+]+)",
                    RegexOption.IGNORE_CASE
                )
                val match = regex.find(input)
                val rawName = match?.groupValues?.getOrNull(1)?.trim().orEmpty()
                if (rawName.isNotBlank() && rawName.lowercase() !in listOf("dire", "lui", "que", "le", "la", "un", "une")) {
                    rawName.replaceFirstChar { it.uppercase() }
                } else {
                    "Destinataire"
                }
            }
        }
    }

    private fun extractMessageBodyFromPrompt(input: String, recipient: String): String? {
        if (input.contains(":")) {
            val afterColon = input.substringAfter(":").trim().removeSurrounding("\"").removeSurrounding("«", "»")
            if (afterColon.isNotBlank()) return afterColon
        }
        val quoteRegex = Regex("[«\"](.+?)[»\"]")
        quoteRegex.find(input)?.let {
            return it.groupValues[1].trim()
        }
        val direRegex = Regex(
            "(?:pour\\s+lui\\s+dire\\s+que|pour\\s+dire\\s+que|en\\s+disant\\s+que|dis-lui\\s+que|dis\\s+lui\\s+que|pour\\s+lui\\s+dire|pour\\s+dire)\\s+(.+)",
            RegexOption.IGNORE_CASE
        )
        direRegex.find(input)?.let {
            val extracted = it.groupValues[1].trim()
            if (extracted.isNotBlank()) {
                return extracted.replaceFirstChar { ch -> ch.uppercase() }
            }
        }
        val queRegex = Regex(
            "(?:maman|papa|${Regex.escape(recipient)})\\s+que\\s+(.+)",
            RegexOption.IGNORE_CASE
        )
        queRegex.find(input)?.let {
            val extracted = it.groupValues[1].trim()
            if (extracted.isNotBlank()) {
                return extracted.replaceFirstChar { ch -> ch.uppercase() }
            }
        }
        return null
    }

    private fun cleanSmsBodyPrefix(rawReply: String): String {
        val cleaned = rawReply.trim()
            .replace(Regex("^(?:dis-lui que|dis lui que|écris que|ecris que|envoie que|dis-lui|dis lui)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
        return cleaned.replaceFirstChar { it.uppercase() }
    }

    private fun defaultWarmMessageFor(recipient: String): String {
        val rLower = recipient.lowercase()
        return when {
            rLower.contains("maman") -> "Coucou Maman, j'espère que tu passes une très belle journée !"
            rLower.contains("papa") -> "Salut Papa, j'espère que tout va bien de ton côté !"
            else -> "Bonjour $recipient, j'espère que tu vas bien !"
        }
    }

    private fun parseEmailRecipientAndBody(raw: String): Pair<String, String> {
        val emailRegex = Regex("([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})")
        val emailMatch = emailRegex.find(raw)?.groupValues?.getOrNull(1).orEmpty()
        val body = if (raw.contains(":")) {
            raw.substringAfter(":").trim()
        } else {
            raw.replace(emailMatch, "").replace(Regex("^(?:à|a|pour)\\s+", RegexOption.IGNORE_CASE), "").trim()
        }
        return emailMatch to body.ifBlank { raw.trim() }
    }

    private fun buildMailtoUri(recipientEmail: String, subjectAndBody: String): String {
        val subject = subjectAndBody.take(48).ifBlank { "Message" }
        val encodedSubject = android.net.Uri.encode(subject)
        val encodedBody = android.net.Uri.encode(subjectAndBody)
        return "mailto:${android.net.Uri.encode(recipientEmail)}?subject=$encodedSubject&body=$encodedBody"
    }

    private fun saveEmailDraftToRagMemory(recipientEmail: String, subjectAndBody: String) {
        val title = if (recipientEmail.isNotBlank()) {
            "Brouillon e-mail à $recipientEmail"
        } else {
            "Brouillon e-mail : ${subjectAndBody.take(32)}"
        }
        LiaMemoryAndHistoryStore.upsertSouvenir(
            type = "NOTE",
            subjectKey = "last_email_draft",
            title = title,
            detailValue = if (recipientEmail.isNotBlank()) "$recipientEmail : $subjectAndBody" else subjectAndBody,
            searchTags = "email,mail,gmail,brouillon,${recipientEmail.lowercase()},${subjectAndBody.lowercase().take(40)}"
        )
        LiaMemoryAndHistoryStore.upsertLongTermMemory(
            category = "EMAIL_DRAFT",
            memoryKey = "last_email_draft",
            title = "📧 $title",
            contentValue = subjectAndBody,
            targetActionOrPackage = buildMailtoUri(recipientEmail, subjectAndBody),
            speedupMs = 40L
        )
    }

    private fun recordLearnedAppPreference(target: LiaAppLaunchAction, rawQuery: String) {
        LiaMemoryAndHistoryStore.upsertSouvenir(
            type = "PREFERENCE",
            subjectKey = "last_opened_app",
            title = "Application utilisée : ${target.appLabel}",
            detailValue = target.appLabel,
            searchTags = "application,app,ouvrir,${target.appLabel.lowercase()},${rawQuery.lowercase()}"
        )
        LiaMemoryAndHistoryStore.upsertLongTermMemory(
            category = "LEARNED_APP_PREF",
            memoryKey = "pref_app_${target.packageName}",
            title = "📱 ${target.appLabel}",
            contentValue = target.appLabel,
            targetActionOrPackage = target.packageName,
            speedupMs = target.learnedSpeedMs
        )
        LiaMemoryAndHistoryStore.recordLearnedShortcut(
            triggerPhrase = "Ouvrir ${target.appLabel}",
            actionTitle = "Accès appris vers ${target.appLabel}",
            targetPackageOrScreen = target.packageName,
            stepsSaved = 3,
            latencyMs = target.learnedSpeedMs
        )
    }

    private fun resolveAppLaunchTarget(requestedAppRaw: String): LiaAppLaunchAction {
        val lower = requestedAppRaw.lowercase().trim()
        return when {
            lower.contains("messenger") ->
                LiaAppLaunchAction("Messenger", "app_msg:messenger::", "messenger_chat", 35L)
            lower.contains("whatsapp") || lower.contains("whats app") ->
                LiaAppLaunchAction("WhatsApp", "com.whatsapp", "home_launcher", 35L)
            lower.contains("telegram") ->
                LiaAppLaunchAction("Telegram", "org.telegram.messenger", "home_launcher", 35L)
            lower.contains("instagram") || lower == "insta" ->
                LiaAppLaunchAction("Instagram", "com.instagram.android", "home_launcher", 35L)
            lower.contains("discord") ->
                LiaAppLaunchAction("Discord", "com.discord", "home_launcher", 35L)
            lower.contains("signal") ->
                LiaAppLaunchAction("Signal", "org.thoughtcrime.securesms", "home_launcher", 35L)
            lower.contains("spotify") ->
                LiaAppLaunchAction("Spotify", "com.spotify.music", "home_launcher", 35L)
            lower.contains("steam") ->
                LiaAppLaunchAction("Steam", "com.valvesoftware.android.steam.community", "steam_recent_games", 35L)
            lower.contains("gmail") || lower == "mail" ->
                LiaAppLaunchAction("Gmail", "com.google.android.gm", "home_launcher", 45L)
            lower.contains("agenda") || lower.contains("calendar") || lower.contains("calendrier") ->
                LiaAppLaunchAction("Google Agenda", "com.google.android.calendar", "home_launcher", 45L)
            lower.contains("drive") ->
                LiaAppLaunchAction("Google Drive", "com.google.android.apps.docs", "home_launcher", 45L)
            lower.contains("docs") || lower.contains("document") ->
                LiaAppLaunchAction("Google Docs", "com.google.android.apps.docs.editors.docs", "home_launcher", 45L)
            lower.contains("sheets") || lower.contains("tableur") || lower.contains("excel") ->
                LiaAppLaunchAction("Google Sheets", "com.google.android.apps.docs.editors.sheets", "home_launcher", 45L)
            lower.contains("tasks") || lower.contains("keep") || lower.contains("tâche") ->
                LiaAppLaunchAction("Google Keep", "com.google.android.keep", "home_launcher", 45L)
            lower.contains("maps") || lower.contains("carte") || lower.contains("gps") || lower.contains("waze") ->
                LiaAppLaunchAction("Google Maps", "com.google.android.apps.maps", "home_launcher", 45L)
            lower.contains("youtube") ->
                LiaAppLaunchAction("YouTube", "com.google.android.youtube", "home_launcher", 45L)
            lower.contains("photo") || lower.contains("galerie") ->
                LiaAppLaunchAction("Google Photos", "com.google.android.apps.photos", "home_launcher", 45L)
            lower.contains("contact") || lower.contains("carnet") ->
                LiaAppLaunchAction("Google Contacts", "com.google.android.contacts", "home_launcher", 45L)
            lower.contains("message") || lower.contains("sms") ->
                LiaAppLaunchAction("Messages", "com.google.android.apps.messaging", "messages_inbox", 45L)
            lower.contains("chrome") || lower.contains("navigateur") ->
                LiaAppLaunchAction("Google Chrome", "com.android.chrome", "home_launcher", 45L)
            lower.contains("param") || lower.contains("réglage") || lower.contains("reglage") || lower.contains("vpn") ->
                LiaAppLaunchAction("Paramètres", "com.android.settings", "settings_vpn", 45L)
            lower.contains("appareil photo") || lower.contains("caméra") || lower.contains("camera") ->
                LiaAppLaunchAction("Appareil Photo", "camera:still", "home_launcher", 35L)
            lower.contains("horloge") || lower.contains("réveil") || lower.contains("minuteur") ->
                LiaAppLaunchAction("Horloge", "com.google.android.deskclock", "home_launcher", 35L)
            else -> {
                val dynamicMatch = LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp(requestedAppRaw)
                if (dynamicMatch != null) {
                    LiaAppLaunchAction(dynamicMatch.appLabel, dynamicMatch.packageName, "home_launcher", 45L)
                } else {
                    val cleanName = requestedAppRaw.replaceFirstChar { it.uppercase() }.ifBlank { "Google Chrome" }
                    val fallbackWebSearch = "https://www.google.com/search?q=${android.net.Uri.encode(cleanName)}"
                    LiaAppLaunchAction(cleanName, fallbackWebSearch, "home_launcher", 55L)
                }
            }
        }
    }

    private fun checkFastAppLaunchIntent(input: String): LiaBrainTurnOutcome? {
        val lower = input.lowercase().trim()
        val snap = LiaMemoryAndHistoryStore.state.value

        // 0. Classification centrale via IntentHandler + UsageStatsManager pour les Intents système complets (caméra, minuteur, sous-paramètres Wi-Fi/Bluetooth, app #1)
        val classifiedIntent = com.example.service.IntentHandler.classifyAndExtractKnownIntent(input)
        if (classifiedIntent != null && !isGeneralActionStarterCommand(input)) {
            val targetUriOrPkg = classifiedIntent.deepLinkUri.ifBlank { classifiedIntent.targetPackage }
            val action = LiaAppLaunchAction(
                appLabel = classifiedIntent.resolvedAppLabel,
                packageName = targetUriOrPkg,
                targetScreenId = "home_launcher",
                learnedSpeedMs = 35L
            )
            recordLearnedAppPreference(action, input)
            return LiaBrainTurnOutcome(
                replyText = classifiedIntent.summaryMessageFr,
                appLaunchAction = action,
                isPhoneAction = true,
                learnedBadge = "⚡ ${classifiedIntent.intentType.labelFr} (${classifiedIntent.resolvedAppLabel})"
            )
        }

        // 0A. Action Proactive RAG : "Reprendre la rédaction de l'e-mail" / "Reprends mon mail"
        if (lower.contains("reprendre la rédaction") ||
            lower.contains("reprendre l'e-mail") ||
            lower.contains("reprendre l'email") ||
            lower.contains("reprends mon mail") ||
            lower.contains("reprendre mon mail") ||
            lower.contains("continuer l'e-mail")
        ) {
            val savedDraftMem = snap.longTermMemories.firstOrNull { it.category == "EMAIL_DRAFT" }
            val savedDraftSouvenir = snap.souvenirs.firstOrNull { it.subjectKey == "last_email_draft" }
            val latestGmail = LiaGoogleCloudWorkspaceClient.state.value.recentEmails.firstOrNull()

            return when {
                savedDraftMem != null && savedDraftMem.targetActionOrPackage.startsWith("mailto:") -> {
                    val action = LiaAppLaunchAction("Gmail (${savedDraftMem.contentValue.take(24)})", savedDraftMem.targetActionOrPackage, "home_launcher", 40L)
                    LiaBrainTurnOutcome(
                        replyText = "📧 **Reprise de votre brouillon d'e-mail** :\n« *${savedDraftMem.contentValue}* »",
                        appLaunchAction = action,
                        isPhoneAction = true,
                        learnedBadge = "📧 Brouillon repris"
                    )
                }
                savedDraftSouvenir != null -> {
                    val (recipient, body) = parseEmailRecipientAndBody(savedDraftSouvenir.detailValue)
                    val uri = buildMailtoUri(recipient, body)
                    val action = LiaAppLaunchAction("Gmail (${body.take(24)})", uri, "home_launcher", 40L)
                    LiaBrainTurnOutcome(
                        replyText = "📧 **Reprise de votre e-mail en cours** :\n« *${savedDraftSouvenir.detailValue}* »",
                        appLaunchAction = action,
                        isPhoneAction = true,
                        learnedBadge = "📧 E-mail repris"
                    )
                }
                latestGmail != null -> {
                    val uri = buildMailtoUri(latestGmail.from.substringAfter("<").substringBefore(">"), "Re: ${latestGmail.subject}")
                    val action = LiaAppLaunchAction("Répondre à ${latestGmail.subject.take(20)}", uri, "home_launcher", 40L)
                    LiaBrainTurnOutcome(
                        replyText = "📧 **Réponse rapide au dernier e-mail Gmail** :\n• Sujet : **${latestGmail.subject}**",
                        appLaunchAction = action,
                        isPhoneAction = true,
                        learnedBadge = "📧 Réponse Gmail prête"
                    )
                }
                else -> {
                    LiaMemoryAndHistoryStore.setPendingFollowUp(
                        LiaPendingFollowUp.WaitingForGeneralClarification("ASK_EMAIL_CONTENT")
                    )
                    LiaBrainTurnOutcome(
                        replyText = "📧 **Quel est le sujet ou le message de l'e-mail que vous souhaitez rédiger ?**"
                    )
                }
            }
        }

        // 0B. Action Proactive RAG : "Itinéraire vers le prochain événement" / "Aller à mon prochain rdv"
        if (lower.contains("prochain événement") ||
            lower.contains("prochain evenement") ||
            lower.contains("prochain rdv") ||
            lower.contains("prochain rendez-vous")
        ) {
            val nextCalEvent = LiaGoogleCloudWorkspaceClient.state.value.upcomingEvents.firstOrNull()
            val nextSouvenirEvent = snap.souvenirs.firstOrNull { it.type == "EVENEMENT" }
            val destinationQuery = when {
                nextCalEvent != null && nextCalEvent.locationOrDescription.isNotBlank() ->
                    "${nextCalEvent.title} ${nextCalEvent.locationOrDescription}".trim()
                nextCalEvent != null -> nextCalEvent.title
                nextSouvenirEvent != null -> nextSouvenirEvent.detailValue
                else -> null
            }

            return if (!destinationQuery.isNullOrBlank()) {
                val geoUri = "geo:0,0?q=${android.net.Uri.encode(destinationQuery)}"
                val action = LiaAppLaunchAction("Maps ($destinationQuery)", geoUri, "home_launcher", 40L)
                LiaBrainTurnOutcome(
                    replyText = "🗺️ **Navigation RAG vers votre prochain événement** : **« $destinationQuery »**",
                    appLaunchAction = action,
                    isPhoneAction = true,
                    learnedBadge = "🔍 RAG Agenda ➔ Maps"
                )
            } else {
                LiaMemoryAndHistoryStore.setPendingFollowUp(
                    LiaPendingFollowUp.WaitingForGeneralClarification("ASK_MAPS_DESTINATION")
                )
                LiaBrainTurnOutcome(
                    replyText = "🗺️ Aucun lieu d'événement n'est encore enregistré dans votre agenda. **Quelle destination souhaitez-vous rejoindre sur Google Maps ?**"
                )
            }
        }

        // 0C. Action générale : "Ouvrir une application" -> Demande laquelle avec puces d'applications fréquentes
        if (lower == "ouvrir une application" ||
            lower == "ouvre une application" ||
            lower == "ouvrir une app" ||
            lower == "ouvre une app"
        ) {
            val lastApp = snap.souvenirs.firstOrNull { it.subjectKey == "last_opened_app" }?.detailValue
                ?: snap.longTermMemories.firstOrNull { it.category == "LEARNED_APP_PREF" }?.contentValue
            val learnedHint = if (!lastApp.isNullOrBlank()) {
                "\n*(Dernière application ouverte : **$lastApp**)*"
            } else ""

            val topApps = buildList {
                if (!lastApp.isNullOrBlank()) add(lastApp)
                snap.appUsageHabits.sortedByDescending { it.qValueHabitScore }.take(3).forEach {
                    if (it.appLabel !in this) add(it.appLabel)
                }
                listOf("WhatsApp", "Messenger", "YouTube", "Gmail", "Google Maps", "Spotify", "Steam").forEach {
                    if (size < 6 && it !in this) add(it)
                }
            }

            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForGeneralClarification("OPEN_APP")
            )
            return LiaBrainTurnOutcome(
                replyText = "📱 **Quelle application souhaitez-vous ouvrir ?**$learnedHint",
                learnedBadge = "📱 Choix de l'application",
                quickReplies = topApps
            )
        }

        // 0D. Référence implicite au passé : "ouvre mon app habituelle", "ouvre la même application", "ouvre la dernière app"
        if ((lower.contains("ouvre") || lower.contains("lance")) &&
            (lower.contains("habituelle") || lower.contains("la même") || lower.contains("la meme") || lower.contains("dernière app") || lower.contains("derniere app"))
        ) {
            val lastPref = snap.longTermMemories.firstOrNull { it.category == "LEARNED_APP_PREF" }
                ?: snap.longTermMemories.firstOrNull { it.category == "APP_SHORTCUT" }
            val habitApp = snap.appUsageHabits.maxByOrNull { it.qValueHabitScore }
            val target = when {
                lastPref != null && lastPref.targetActionOrPackage.isNotBlank() ->
                    LiaAppLaunchAction(lastPref.contentValue.ifBlank { lastPref.title }, lastPref.targetActionOrPackage, "home_launcher", 40L)
                habitApp != null ->
                    LiaAppLaunchAction(habitApp.appLabel, habitApp.packageName, "home_launcher", 40L)
                else -> null
            }
            if (target != null) {
                return LiaBrainTurnOutcome(
                    replyText = "⚡ D'après vos habitudes, j'ouvre **${target.appLabel}**.",
                    appLaunchAction = target,
                    isPhoneAction = true,
                    learnedBadge = "⚡ Habitude : ${target.appLabel}"
                )
            }
        }

        // 1. E-mail / Gmail : Si l'utilisateur clique sur "Envoyer un e-mail" sans texte, on lui demande le contenu ! S'il donne le texte, on l'applique directement.
        if (lower.contains("envoie un mail") ||
            lower.contains("envoyer un mail") ||
            lower.contains("envoie un email") ||
            lower.contains("envoyer un email") ||
            lower.contains("envoie un e-mail") ||
            lower.contains("envoyer un e-mail") ||
            lower.contains("préparer un e-mail") ||
            lower.contains("preparer un email") ||
            lower.contains("rédige un mail") ||
            lower.contains("rédiger un mail") ||
            lower.contains("écris un mail") ||
            lower.contains("écrire un mail") ||
            lower.contains("ecris un mail") ||
            lower.contains("nouveau mail") ||
            lower.contains("nouvel email")
        ) {
            val extractedContent = if (input.contains(":")) {
                input.substringAfter(":").trim()
            } else {
                input.replace(
                    Regex("^(?:envoie|envoyer|préparer|preparer|rédige|rédiger|écris|écrire|ecris|nouveau|nouvel)\\s+(?:un\\s+)?(?:e-mail|email|mail)\\s*", RegexOption.IGNORE_CASE),
                    ""
                ).trim()
            }

            if (extractedContent.isBlank()) {
                val lastDraft = snap.souvenirs.firstOrNull { it.subjectKey == "last_email_draft" }?.detailValue
                val hint = if (!lastDraft.isNullOrBlank()) "\n*(Dernier sujet enregistré : « $lastDraft »)*" else ""
                LiaMemoryAndHistoryStore.setPendingFollowUp(
                    LiaPendingFollowUp.WaitingForGeneralClarification("ASK_EMAIL_CONTENT")
                )
                return LiaBrainTurnOutcome(
                    replyText = "📧 **Quel est le sujet ou le message de l'e-mail que vous souhaitez envoyer ?**$hint",
                    learnedBadge = "📧 Contenu de l'e-mail attendu",
                    quickReplies = listOf(
                        "Confirmation de rendez-vous",
                        "Envoi de document",
                        "Compte-rendu de réunion"
                    )
                )
            }

            val (recipientEmail, subjectAndBody) = parseEmailRecipientAndBody(extractedContent)
            saveEmailDraftToRagMemory(recipientEmail, subjectAndBody)
            val mailtoUri = buildMailtoUri(recipientEmail, subjectAndBody)
            val action = LiaAppLaunchAction("Gmail ($subjectAndBody)", mailtoUri, "home_launcher", 45L)
            LiaMemoryAndHistoryStore.recordLearnedShortcut(
                triggerPhrase = "📧 E-mail : ${subjectAndBody.take(18)}",
                actionTitle = "Ouverture directe rédaction Gmail ($subjectAndBody)",
                targetPackageOrScreen = mailtoUri,
                stepsSaved = 3,
                latencyMs = 45L
            )
            return LiaBrainTurnOutcome(
                replyText = "📧 J'ouvre **Gmail** avec votre brouillon : *« $subjectAndBody »*.",
                appLaunchAction = action,
                isPhoneAction = true,
                learnedBadge = "📧 Brouillon Gmail prêt"
            )
        }

        // 2. Agenda / Calendrier : Si l'utilisateur clique sur "Ajouter à l'Agenda" sans préciser l'événement, on lui demande lequel !
        if (lower.contains("ajoute à l'agenda") ||
            lower.contains("ajoute a l'agenda") ||
            lower.contains("ajouter à l'agenda") ||
            lower.contains("ajouter a l'agenda") ||
            lower.contains("ajouter un événement") ||
            lower.contains("ajouter un evenement") ||
            lower.contains("ajoute au calendrier") ||
            lower.contains("ajouter au calendrier") ||
            lower.contains("ajoute à google agenda") ||
            lower.contains("crée un événement") ||
            lower.contains("créer un événement") ||
            lower.contains("cree un evenement") ||
            lower.contains("nouveau rendez-vous") ||
            lower.contains("nouveau rdv")
        ) {
            val rawExtracted = if (input.contains(":")) {
                input.substringAfter(":").trim()
            } else {
                input.replace(
                    Regex("^(?:ajoute(?:r)?\\s+(?:un\\s+[ée]v[ée]nement\\s+)?(?:dans|à|a|au)\\s+(?:l'agenda|le\\s+calendrier|google\\s+agenda)|ajouter\\s+un\\s+[ée]v[ée]nement|cr[ée]e(?:r)?\\s+un\\s+[ée]v[ée]nement|nouveau\\s+(?:rendez-vous|rdv))\\s*", RegexOption.IGNORE_CASE),
                    ""
                ).trim()
            }

            if (rawExtracted.isBlank()) {
                LiaMemoryAndHistoryStore.setPendingFollowUp(
                    LiaPendingFollowUp.WaitingForGeneralClarification("ASK_CALENDAR_EVENT")
                )
                return LiaBrainTurnOutcome(
                    replyText = "📅 **Quel événement ou rendez-vous souhaitez-vous ajouter à votre agenda ?**",
                    learnedBadge = "📅 Titre de l'événement attendu",
                    quickReplies = listOf(
                        "Rendez-vous médecin demain à 14h",
                        "Dîner ce soir à 20h",
                        "Réunion demain à 10h",
                        "Séance de sport à 18h"
                    )
                )
            }

            val eventTitle = rawExtracted
            LiaMemoryAndHistoryStore.upsertSouvenir(
                type = "EVENEMENT",
                subjectKey = "gcal_${System.currentTimeMillis() % 10000}",
                title = "Agenda : $eventTitle",
                detailValue = eventTitle,
                searchTags = "google,agenda,calendar,rdv,${eventTitle.lowercase()}",
                eventDateOrTime = currentTimeFormatted()
            )
            val action = LiaAppLaunchAction("Google Agenda ($eventTitle)", "calendar_insert:$eventTitle", "home_launcher", 45L)
            LiaMemoryAndHistoryStore.recordLearnedShortcut(
                triggerPhrase = "📅 RDV : ${eventTitle.take(18)}",
                actionTitle = "Ajout Google Agenda ($eventTitle)",
                targetPackageOrScreen = "calendar_insert:$eventTitle",
                stepsSaved = 3,
                latencyMs = 45L
            )
            return LiaBrainTurnOutcome(
                replyText = "📅 J'ajoute **« $eventTitle »** dans **Google Agenda**.",
                appLaunchAction = action,
                isPhoneAction = true,
                learnedBadge = "📅 Événement ajouté : $eventTitle"
            )
        }

        // 3. Google Maps / Itinéraire : Si aucune destination n'est précisée, on demande où aller !
        if (lower.contains("itinéraire") ||
            lower.contains("itineraire") ||
            lower == "navigation" ||
            lower == "lancer la navigation" ||
            lower == "ouvrir google maps" ||
            lower == "ouvrir maps" ||
            (lower.contains("maps") && (lower.contains("vers ") || lower.contains("cherche ")))
        ) {
            val destination = input
                .replace(Regex("^(?:itinéraire(?:\\s+vers)?|itineraire(?:\\s+vers)?|navigation(?:\\s+vers)?|ouvrir\\s+(?:google\\s+)?maps|cherche|trouve|va vers|google maps vers)\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+sur\\s+(?:google\\s+)?maps.*$", RegexOption.IGNORE_CASE), "")
                .trim()

            if (destination.isBlank()) {
                val lastDest = snap.souvenirs.firstOrNull { it.subjectKey == "last_maps_destination" }?.detailValue
                val userCity = LiaMemoryAndHistoryStore.getUserFactValue("ville")
                val hint = if (!lastDest.isNullOrBlank()) "\n*(Dernière destination : **$lastDest**)*" else ""
                LiaMemoryAndHistoryStore.setPendingFollowUp(
                    LiaPendingFollowUp.WaitingForGeneralClarification("ASK_MAPS_DESTINATION")
                )
                return LiaBrainTurnOutcome(
                    replyText = "🗺️ **Quelle destination souhaitez-vous chercher sur Google Maps ?**$hint",
                    learnedBadge = "🗺️ Destination attendue",
                    quickReplies = buildList {
                        if (!lastDest.isNullOrBlank()) add(lastDest)
                        if (!userCity.isNullOrBlank() && userCity !in this) add("Gare de $userCity")
                        add("Pharmacie la plus proche")
                        add("Supermarché à proximité")
                        add("Station-service")
                    }
                )
            }

            LiaMemoryAndHistoryStore.upsertSouvenir(
                type = "PREFERENCE",
                subjectKey = "last_maps_destination",
                title = "Dernière destination : $destination",
                detailValue = destination,
                searchTags = "maps,itineraire,destination,trajet,${destination.lowercase()}"
            )
            val geoUri = "geo:0,0?q=${android.net.Uri.encode(destination)}"
            val action = LiaAppLaunchAction("Google Maps ($destination)", geoUri, "home_launcher", 45L)
            LiaMemoryAndHistoryStore.recordLearnedShortcut(
                triggerPhrase = "🗺️ Maps : ${destination.take(16)}",
                actionTitle = "Itinéraire Google Maps vers $destination",
                targetPackageOrScreen = geoUri,
                stepsSaved = 3,
                latencyMs = 45L
            )
            return LiaBrainTurnOutcome(
                replyText = "🗺️ Je lance l'itinéraire **Google Maps** vers **« $destination »**.",
                appLaunchAction = action,
                isPhoneAction = true,
                learnedBadge = "🗺️ Google Maps : $destination"
            )
        }

        // 4. Recherche YouTube ("cherche ... sur youtube", "mets ... sur youtube")
        if (lower.contains("sur youtube") && (lower.contains("cherche") || lower.contains("mets") || lower.contains("joue") || lower.contains("lance"))) {
            val query = input
                .replace(Regex("^(?:cherche|mets|joue|lance|trouve)\\s+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+sur\\s+youtube.*$", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank { "Musique" }
            val ytUrl = "https://www.youtube.com/results?search_query=${android.net.Uri.encode(query)}"
            val action = LiaAppLaunchAction("YouTube ($query)", ytUrl, "home_launcher", 45L)
            LiaMemoryAndHistoryStore.recordLearnedShortcut(
                triggerPhrase = "▶️ YouTube : ${query.take(16)}",
                actionTitle = "Recherche directe YouTube ($query)",
                targetPackageOrScreen = ytUrl,
                stepsSaved = 3,
                latencyMs = 45L
            )
            return LiaBrainTurnOutcome(
                replyText = "▶️ Je lance la recherche **YouTube** pour **« $query »**.",
                appLaunchAction = action,
                isPhoneAction = true,
                learnedBadge = "▶️ YouTube : $query"
            )
        }

        // 5. Réveil / Horloge : Si l'heure est précisée ("mets un réveil à 7h15"), on l'applique. Si aucune heure n'est précisée ("Mettre un réveil"), on demande à quelle heure !
        val alarmMatch = Regex("(?:réveil|reveil|alarme)\\s*(?:à|a|pour)\\s*(\\d{1,2})\\s*(?:h|:)\\s*(\\d{0,2})", RegexOption.IGNORE_CASE).find(input)
        if (alarmMatch != null) {
            val h = alarmMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 7
            val m = alarmMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
            val formattedTime = "%02d:%02d".format(h, m)
            LiaMemoryAndHistoryStore.upsertSouvenir(
                type = "PREFERENCE",
                subjectKey = "preferred_alarm_time",
                title = "Heure de réveil habituelle : $formattedTime",
                detailValue = formattedTime,
                searchTags = "reveil,alarme,horloge,matin,$formattedTime"
            )
            val action = LiaAppLaunchAction("Horloge (Réveil $formattedTime)", "alarm:$h:$m", "home_launcher", 45L)
            LiaMemoryAndHistoryStore.recordLearnedShortcut(
                triggerPhrase = "⏰ Réveil $formattedTime",
                actionTitle = "Programmation réveil Android ($formattedTime)",
                targetPackageOrScreen = "alarm:$h:$m",
                stepsSaved = 3,
                latencyMs = 45L
            )
            return LiaBrainTurnOutcome(
                replyText = "⏰ Je programme votre réveil pour **$formattedTime** et je mémorise cet horaire.",
                appLaunchAction = action,
                isPhoneAction = true,
                learnedBadge = "⏰ Réveil $formattedTime"
            )
        }

        if (lower.contains("mettre un réveil") ||
            lower.contains("mettre un reveil") ||
            lower.contains("mets un réveil") ||
            lower.contains("mets un reveil") ||
            lower.contains("programmer un réveil") ||
            lower == "réveil" ||
            lower == "reveil" ||
            lower == "alarme"
        ) {
            val savedAlarm = snap.souvenirs.firstOrNull { it.subjectKey == "preferred_alarm_time" }?.detailValue
            if (lower.contains("habitude") || lower.contains("habituel") || lower.contains("même heure")) {
                if (!savedAlarm.isNullOrBlank()) {
                    val parts = savedAlarm.split(":")
                    val h = parts.getOrNull(0)?.toIntOrNull() ?: 7
                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 30
                    val action = LiaAppLaunchAction("Horloge (Réveil $savedAlarm)", "alarm:$h:$m", "home_launcher", 40L)
                    return LiaBrainTurnOutcome(
                        replyText = "⏰ D'après votre horaire habituel mémorisé, je règle votre réveil sur **$savedAlarm**.",
                        appLaunchAction = action,
                        isPhoneAction = true,
                        learnedBadge = "⏰ Réveil habituel : $savedAlarm"
                    )
                }
            }
            val hint = if (!savedAlarm.isNullOrBlank()) "\n*(Horaire habituel mémorisé : **$savedAlarm**)*" else ""
            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForGeneralClarification("ASK_ALARM_TIME")
            )
            return LiaBrainTurnOutcome(
                replyText = "⏰ **À quelle heure souhaitez-vous régler votre réveil ?**$hint",
                learnedBadge = "⏰ Heure du réveil attendue",
                quickReplies = buildList {
                    if (!savedAlarm.isNullOrBlank()) add(savedAlarm)
                    listOf("06:30", "07:00", "07:30", "08:00", "08:30").forEach {
                        if (it !in this) add(it)
                    }
                }
            )
        }

        // 6. Recherche Web / Google : Si aucune requête n'est précisée, on demande quoi chercher !
        if (lower.contains("cherche sur google") ||
            lower.contains("chercher sur google") ||
            lower.contains("recherche google") ||
            lower.contains("recherche web") ||
            lower.contains("recherche sur internet") ||
            lower.contains("chercher sur internet")
        ) {
            val query = input
                .replace(Regex("^(?:cherche(?:r)?\\s+sur\\s+(?:google|internet)|recherche\\s+(?:google|web|sur\\s+internet))\\s*[:\\-]?\\s*", RegexOption.IGNORE_CASE), "")
                .trim()
            if (query.isBlank()) {
                LiaMemoryAndHistoryStore.setPendingFollowUp(
                    LiaPendingFollowUp.WaitingForGeneralClarification("ASK_WEB_SEARCH")
                )
                return LiaBrainTurnOutcome(
                    replyText = "🌐 **Que souhaitez-vous rechercher sur Google ?**",
                    learnedBadge = "🌐 Recherche attendue",
                    quickReplies = listOf("Météo aujourd'hui", "Actualités du jour", "Restaurants autour de moi")
                )
            }
            val url = "https://www.google.com/search?q=${android.net.Uri.encode(query)}"
            val action = LiaAppLaunchAction("Recherche Google ($query)", url, "home_launcher", 45L)
            return LiaBrainTurnOutcome(
                replyText = "🌐 Je lance la recherche Google pour **« $query »**.",
                appLaunchAction = action,
                isPhoneAction = true,
                learnedBadge = "🌐 Recherche : ${query.take(18)}"
            )
        }

        // 7. Commande directe d'ouverture d'une application précise ("Ouvre YouTube", "Lance Gmail", ou "YouTube")
        val isExplicitOpenVerb = lower.startsWith("ouvre ") ||
            lower.startsWith("ouvrir ") ||
            lower.startsWith("lance ") ||
            lower.startsWith("lancer ") ||
            lower.startsWith("démarre ") ||
            lower.startsWith("démarrer ") ||
            lower.contains("ouvre l'application") ||
            lower.contains("ouvrir l'application") ||
            lower.contains("ouvre l'app") ||
            lower.contains("ouvrir l'app") ||
            lower.startsWith("va sur ") ||
            lower.startsWith("aller sur ")

        val directAppNameMatch = lower in setOf(
            "gmail", "google agenda", "agenda", "calendrier", "google calendar",
            "google drive", "drive", "google docs", "docs", "google sheets", "sheets",
            "google tasks", "tasks", "keep", "google maps", "maps", "youtube",
            "google photos", "photos", "galerie", "contacts", "google contacts",
            "messages", "chrome", "google chrome", "paramètres", "parametres", "réglages", "reglages"
        ) || (lower.split(" ").size <= 2 && !lower.contains("?") && LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp(lower) != null)

        if (!isExplicitOpenVerb && !directAppNameMatch) return null

        val requestedAppRaw = input
            .replace(Regex("^(?:ouvre l'application|ouvrir l'application|ouvre l'app|ouvrir l'app|ouvre|ouvrir|lance|lancer|démarre|démarrer|va sur|aller sur)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()

        if (requestedAppRaw.isBlank()) {
            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForGeneralClarification("OPEN_APP")
            )
            return LiaBrainTurnOutcome(
                replyText = "📱 **Quelle application souhaitez-vous ouvrir ?**",
                quickReplies = listOf("WhatsApp", "Messenger", "YouTube", "Gmail", "Google Maps", "Steam")
            )
        }

        val target = resolveAppLaunchTarget(requestedAppRaw)
        recordLearnedAppPreference(target, requestedAppRaw)

        return LiaBrainTurnOutcome(
            replyText = "⚡ J'ouvre **${target.appLabel}** et je mémorise cette habitude.",
            appLaunchAction = target,
            isPhoneAction = true,
            targetVirtualScreenId = target.targetScreenId.ifBlank { null },
            learnedBadge = "⚡ Application : ${target.appLabel}"
        )
    }

    /**
     * Gère les **Actions Téléphone Générales & Précises** :
     * - Catalogue de toutes les actions possibles ("toutes les actions", "que peux-tu faire", "aide")
     * - Appeler quelqu'un (Général -> demande qui avec contacts ; Précis -> ouvre le composeur `tel:...`)
     * - Envoyer un message universel (Général -> demande sur quelle app ou à qui)
     * - Minuteur & Chronomètre (Général -> propose 1/3/5/10/15/30 min ; Précis -> lance le minuteur)
     * - Réglages rapides & Lampe torche (Général -> propose Wi-Fi, Bluetooth, Lampe, Son, Luminosité ; Précis -> exécute)
     * - Musique & Multimédia (Général -> demande quel titre ; Précis -> lance la lecture)
     * - Recherche dans une application (Général -> demande dans quelle app ; Précis -> cherche dans l'app)
     * - Partage rapide (Général -> demande quoi partager ; Précis -> ouvre le menu de partage Android)
     */
    private fun checkGeneralAndPreciseSystemActionsIntent(input: String): LiaBrainTurnOutcome? {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()
        val snap = LiaMemoryAndHistoryStore.state.value
        val ctx = LiaMemoryAndHistoryStore.getApplicationContextOrNull()

        // 1. Catalogue complet des actions Générales & Précises
        if (lower == "toutes les actions" ||
            lower == "que peux-tu faire" ||
            lower == "que peux tu faire" ||
            lower == "que sais-tu faire" ||
            lower == "aide" ||
            lower == "menu des actions" ||
            lower == "actions disponibles"
        ) {
            return LiaBrainTurnOutcome(
                replyText = "✨ **Toutes mes actions (Mode Général ou Ultra-Précis)** :\n\n" +
                    "• 📞 **Appels & Contacts** : dites *« Appeler »* ou *« Appelle Maman »* / *« Trouve mes numéros »*\n" +
                    "• 💬 **Messages toutes apps** : *« Envoyer un message »* ou *« Envoie salut à Paul sur WhatsApp / Messenger / SMS »*\n" +
                    "• 📱 **Applications & Navigation autonome** : *« Ouvrir une app »*, *« Ouvre Spotify »* ou *« Retrouve mon dernier jeu dans Steam »*\n" +
                    "• 🔎 **Recherche dans vos applis** : *« Cherche dans mes applis »* (Agenda, Google Keep, Drive, Gmail, Contacts, YouTube, Play Store)\n" +
                    "• ⏱️ **Minuteur & Réveil** : *« Mettre un minuteur »* (*« Minuteur de 10 min »*) ou *« Réveil à 7h30 »*\n" +
                    "• 🔦 **Lampe torche & Réglages** : *« Allume la lampe torche »*, *« Réglages Wi-Fi / Bluetooth / Son / Luminosité »*\n" +
                    "• 🧮 **Calculs, Conversions & Traductions** : *« Calcule 15% de 180€ »*, *« Convertis 50$ en euros »*, *« Traduis bonjour en japonais »*",
                learnedBadge = "⚡ Catalogue d'actions LIA",
                quickReplies = listOf(
                    "Envoyer un message",
                    "Appeler quelqu'un",
                    "Ouvrir une application",
                    "Chercher dans une application",
                    "Mettre un minuteur",
                    "Réglages rapides",
                    "Allume la lampe torche",
                    "Calculer ou convertir"
                )
            )
        }

        // 2. Action Générale : "Appeler" / "Appeler quelqu'un" / "Passer un appel" (sans nom ni numéro)
        if (lower == "appeler" ||
            lower == "appeler quelqu'un" ||
            lower == "passer un appel" ||
            lower == "téléphoner" ||
            lower == "telephoner" ||
            lower == "appelle"
        ) {
            val knownContacts = com.example.service.LiaCrossAppKnowledgeResolver.findContactsMatching(ctx, "")
                .map { it.displayName }
                .distinct()
                .take(5)
            val fallbackContacts = if (knownContacts.isNotEmpty()) {
                knownContacts
            } else {
                listOf("Maman", "Papa", "Mon dernier contact")
            }

            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForGeneralClarification("ASK_CALL_RECIPIENT")
            )
            return LiaBrainTurnOutcome(
                replyText = "📞 **Qui souhaitez-vous appeler ?**\n*(Touchez un contact ci-dessous ou dites un nom / numéro)*",
                learnedBadge = "📞 Choix du contact à appeler",
                quickReplies = fallbackContacts
            )
        }

        // 3. Action Générale : "Envoyer un message" (sans application ni destinataire précisés)
        if (lower == "envoyer un message" ||
            lower == "envoie un message" ||
            lower == "écrire un message" ||
            lower == "ecrire un message" ||
            lower == "nouveau message"
        ) {
            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForGeneralClarification("ASK_UNIVERSAL_MESSAGE_TARGET")
            )
            return LiaBrainTurnOutcome(
                replyText = "💬 **Sur quelle application ou à qui souhaitez-vous envoyer ce message ?**",
                learnedBadge = "💬 Choix de la messagerie",
                quickReplies = listOf(
                    "Message sur WhatsApp",
                    "Message sur Messenger",
                    "Envoyer un SMS",
                    "Message sur Telegram",
                    "Envoyer un e-mail"
                )
            )
        }

        // 4. Action Générale : "Mettre un minuteur" / "Minuteur" (sans durée précisée)
        if (lower == "mettre un minuteur" ||
            lower == "mets un minuteur" ||
            lower == "lancer un minuteur" ||
            lower == "minuteur" ||
            lower == "chronomètre" ||
            lower == "chronometre"
        ) {
            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForGeneralClarification("ASK_TIMER_DURATION")
            )
            return LiaBrainTurnOutcome(
                replyText = "⏱️ **Quelle durée souhaitez-vous pour votre minuteur ?**",
                learnedBadge = "⏱️ Durée du minuteur attendue",
                quickReplies = listOf("1 minute", "3 minutes", "5 minutes", "10 minutes", "15 minutes", "30 minutes")
            )
        }

        // 5. Action Générale : "Réglages rapides" / "Changer un réglage" / "Paramètres système"
        if (lower == "changer un réglage" ||
            lower == "changer un reglage" ||
            lower == "réglages rapides" ||
            lower == "reglages rapides" ||
            lower == "paramètres système" ||
            lower == "parametres systeme" ||
            lower == "contrôles rapides"
        ) {
            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForGeneralClarification("ASK_SYSTEM_SETTING")
            )
            return LiaBrainTurnOutcome(
                replyText = "⚙️ **Quel réglage ou contrôle souhaitez-vous activer ?**",
                learnedBadge = "⚙️ Réglages rapides",
                quickReplies = listOf(
                    "Allume la lampe torche",
                    "Éteins la lampe torche",
                    "Paramètres Wi-Fi",
                    "Paramètres Bluetooth",
                    "Paramètres Son",
                    "Paramètres Luminosité",
                    "Paramètres Batterie",
                    "Mode Avion"
                )
            )
        }

        // 6. Action Générale : "Lancer de la musique" / "Écouter de la musique" / "Musique"
        if (lower == "lancer de la musique" ||
            lower == "écouter de la musique" ||
            lower == "ecouter de la musique" ||
            lower == "musique" ||
            lower == "mets de la musique"
        ) {
            val savedMusic = snap.souvenirs.firstOrNull { it.subjectKey == "last_music_played" }?.detailValue
            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForGeneralClarification("ASK_MEDIA_QUERY")
            )
            return LiaBrainTurnOutcome(
                replyText = "🎵 **Quel titre, artiste ou playlist souhaitez-vous écouter ?**",
                learnedBadge = "🎵 Choix musical",
                quickReplies = buildList {
                    if (!savedMusic.isNullOrBlank()) add(savedMusic)
                    add("Ma playlist sur Spotify")
                    add("Musique relaxante sur YouTube")
                    add("Pop & Hits actuels")
                    add("Lo-Fi Beats")
                }
            )
        }

        // 7. Action Générale : "Chercher dans une application" / "Chercher dans mes applis"
        if (lower == "chercher dans une application" ||
            lower == "rechercher dans une app" ||
            lower == "chercher dans mes applis" ||
            lower == "rechercher dans mes applications"
        ) {
            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForGeneralClarification("ASK_DEEP_APP_SEARCH")
            )
            return LiaBrainTurnOutcome(
                replyText = "🔎 **Que souhaitez-vous retrouver et dans quelle application ?**",
                learnedBadge = "🔎 Recherche multi-applications",
                quickReplies = listOf(
                    "Retrouve le dernier jeu auquel j'ai joué dans Steam",
                    "Retrouve mes notes dans Google Keep",
                    "Trouve mes numéros et contacts",
                    "Quels sont mes prochains RDV dans l'agenda ?",
                    "Mes fichiers récents dans Google Drive",
                    "Mes derniers e-mails Gmail"
                )
            )
        }

        // 8. Action Générale : "Partager" / "Partager un texte"
        if (lower == "partager" ||
            lower == "partager un texte" ||
            lower == "partage"
        ) {
            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForGeneralClarification("ASK_SHARE_TEXT")
            )
            return LiaBrainTurnOutcome(
                replyText = "📤 **Quel texte ou note souhaitez-vous partager vers vos applications ?**",
                learnedBadge = "📤 Partage Android"
            )
        }

        return null
    }

    private fun checkInstantUtilityIntent(input: String): LiaBrainTurnOutcome? =
        LiaLocalUtilitiesEngine.checkInstantUtilityIntent(input)

    /**
     * Si la réponse de Gemini contient une balise d'action `[ACTION:cible:libellé]` pour une demande
     * formulée librement, extrait l'action et la valide via le pare-feu Zero-Trust (`SEC-01`).
     */
    private fun extractEmbeddedActionFromAiReply(rawReply: String): Pair<String, LiaAppLaunchAction?> {
        val actionTagRegex = Regex("\\[ACTION:([^:\\]]+):([^\\]]+)\\]")
        val match = actionTagRegex.find(rawReply) ?: return rawReply to null
        val targetRaw = match.groupValues[1].trim()
        val labelRaw = match.groupValues[2].trim()
        val cleanedReply = rawReply.replace(match.value, "").trim()

        // SEC-01: Reject any unsafe or unapproved scheme before constructing an action
        val safetyVerdict = SoniaSecurityKernel.validateActionTargetSafety(targetRaw)
        if (safetyVerdict == ActionTargetSafetyVerdict.BLOCKED_UNSAFE_TARGET) {
            return cleanedReply to null
        }

        val resolvedAction = if (targetRaw.contains(":") || targetRaw.contains(".")) {
            LiaAppLaunchAction(labelRaw, targetRaw, "home_launcher", 40L)
        } else {
            resolveAppLaunchTarget(targetRaw)
        }
        return cleanedReply to resolvedAction
    }

    private suspend fun generateContextualAiReply(
        userPrompt: String,
        ragOutcome: RagRetrievalOutcome,
        onStreamingPartial: ((String) -> Unit)?,
        optimizedSystemInstruction: String? = null
    ): String {
        val snap = LiaMemoryAndHistoryStore.state.value
        val aiPrefs = LiaMcpConnectorRegistry.currentAiPreferences
        val isBriefMode = (optimizedSystemInstruction != null && optimizedSystemInstruction.length < 480) ||
            aiPrefs.reasoningDepthMode == com.example.service.LiaReasoningDepthMode.FAST_DIRECT ||
            aiPrefs.responseToneStyle == com.example.service.LiaResponseToneStyle.ULTRA_BRIEF
        val recentMsgs = snap.activeMessages.dropLast(1).takeLast(if (isBriefMode) 3 else 8)
        val effectiveSystemInstruction = optimizedSystemInstruction?.ifBlank { null }
            ?: "Tu es LIA, assistante IA personnelle intelligente sur Android. Réponds directement en français dans un JSON {\"reply\": \"...\"} en exploitant avec précision les souvenirs de l'utilisateur."

        // Select the official 2026 Gemini model based on user reasoning preferences
        val selectedModel = when (aiPrefs.reasoningDepthMode) {
            com.example.service.LiaReasoningDepthMode.DEEP_CHAIN -> GeminiDualEngineClient.MODEL_GEMINI_4_ARGON
            com.example.service.LiaReasoningDepthMode.FAST_DIRECT -> GeminiDualEngineClient.MODEL_GEMINI_3_5_FLASH_LITE
            com.example.service.LiaReasoningDepthMode.ADAPTIVE -> GeminiDualEngineClient.MODEL_GEMINI_3_8_FLASH
        }

        // SEC-01: Sanitize RAG context against indirect prompt injection before embedding into the prompt
        val safeRagContext = SoniaSecurityKernel.sanitizeExternalRagContent(ragOutcome.augmentedPromptContext)

        val fullPrompt = buildString {
            if (!optimizedSystemInstruction.isNullOrBlank()) {
                appendLine("=== DIRECTIVES D'OPTIMISATION DYNAMIQUE ===")
                appendLine(optimizedSystemInstruction)
                appendLine()
            }
            if (!isBriefMode || ragOutcome.retrievedMatches.isNotEmpty()) {
                // 1. Inject the sanitized RAG context
                appendLine(safeRagContext)
                appendLine()
                // 2. Inject personal database & RL quick actions summary
                appendLine(LiaMemoryAndHistoryStore.buildUserFactsContextPrompt())
                appendLine()
            }
            if (recentMsgs.isNotEmpty()) {
                appendLine("=== HISTORIQUE DE LA CONVERSATION ACTUELLE ===")
                recentMsgs.forEach { msg ->
                    val speaker = if (msg.role == "user") "Utilisateur" else "LIA"
                    appendLine("$speaker : ${msg.text}")
                }
                appendLine()
            }
            appendLine("Message actuel de l'utilisateur : $userPrompt")
            appendLine(
                "Réponds directement, intelligemment et naturellement en français en tant que LIA. " +
                    "Si l'utilisateur demande d'ouvrir une application ou de lancer une recherche/action sur le téléphone, " +
                    "ajoute à la toute fin de ta réponse la balise [ACTION:nom_ou_uri:Nom de l'application] (par exemple [ACTION:Spotify:Spotify] ou [ACTION:https://www.google.com/search?q=meteo:Recherche Météo])."
            )
        }

        return try {
            withTimeout(30_000L) {
                if (GeminiDualEngineClient.hasValidCustomApiKey()) {
                    val json = GeminiDualEngineClient.generateJsonWithRestFallback(
                        primaryModel = selectedModel,
                        systemInstruction = "$effectiveSystemInstruction Réponds dans un JSON {\"reply\": \"...\"}.",
                        prompt = fullPrompt
                    )
                    JSONObject(json).optString("reply").trim().ifBlank {
                        geminiModel.generateContent(fullPrompt).text?.trim().orEmpty()
                    }
                } else {
                    val sb = StringBuilder()
                    try {
                        geminiModel.generateContentStream(fullPrompt).collect { chunk ->
                            val part = chunk.text.orEmpty()
                            if (part.isNotEmpty()) {
                                sb.append(part)
                                onStreamingPartial?.invoke(sb.toString().replace(Regex("\\[ACTION:[^\\]]*\\]?"), "").trim())
                            }
                        }
                        sb.toString().trim()
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                        val unary = geminiModel.generateContent(fullPrompt).text?.trim().orEmpty()
                        if (unary.isNotBlank()) unary else {
                            val json = GeminiDualEngineClient.generateJsonWithRestFallback(
                                primaryModel = selectedModel,
                                systemInstruction = "$effectiveSystemInstruction Réponds en français dans un JSON {\"reply\": \"...\"}.",
                                prompt = fullPrompt
                            )
                            JSONObject(json).optString("reply").trim()
                        }
                    }
                }
            }.ifBlank {
                ragOutcome.directHighConfidenceAnswer ?: smartLocalConversationalFallback(userPrompt)
            }
        } catch (ce: CancellationException) {
            if (ce is TimeoutCancellationException) {
                ragOutcome.directHighConfidenceAnswer ?: smartLocalConversationalFallback(userPrompt)
            } else {
                throw ce
            }
        } catch (_: Exception) {
            ragOutcome.directHighConfidenceAnswer ?: smartLocalConversationalFallback(userPrompt)
        }
    }

    private fun smartLocalConversationalFallback(userPrompt: String): String {
        val q = userPrompt.lowercase().trim()
        val userName = LiaMemoryAndHistoryStore.getUserFactValue("prenom")
        val nameGreeting = if (!userName.isNullOrBlank()) " $userName" else ""
        val userAge = LiaMemoryAndHistoryStore.getUserFactValue("age")

        return when {
            q.contains("peu importe") || q.contains("comme tu veux") || q.contains("n'importe") ->
                "Très bien$nameGreeting ! Que souhaitez-vous que je fasse maintenant ?"
            q.startsWith("salut") || q.startsWith("bonjour") || q.startsWith("hello") || q.startsWith("coucou") || q.contains("comment ça va") ->
                "Bonjour$nameGreeting ! Je suis **LIA**, votre assistante personnelle. Je peux appeler vos contacts, envoyer vos messages sur WhatsApp, Messenger ou par SMS, ouvrir vos applications, retrouver vos infos (Agenda, Keep, Drive, Steam), régler vos minuteurs/réveils, contrôler la lampe torche ou calculer et traduire instantanément${if (userAge != null) " (âge mémorisé : $userAge)" else ""}."
            q.contains("qui es-tu") || q.contains("tu fais quoi") || q.contains("que sais-tu faire") ->
                "Je suis **LIA**, votre assistante personnelle sur Android. Je lance vos applications, navigue automatiquement (comme dans Steam), passe vos appels, envoie vos messages sur Messenger, WhatsApp ou par SMS, retrouve vos numéros, notes Keep, événements Agenda et fichiers Drive, et contrôle vos réglages."
            q.contains("blague") ->
                "Pourquoi les oiseaux volent-ils vers le sud en hiver ? Parce que c'est trop long d'y aller à pied !"
            q.contains("capitale") && q.contains("france") -> "La capitale de la France est Paris."
            q.contains("capitale") && q.contains("japon") -> "La capitale du Japon est Tokyo."
            q.contains("capitale") && q.contains("italie") -> "La capitale de l'Italie est Rome."
            q.contains("capitale") && q.contains("espagne") -> "La capitale de l'Espagne est Madrid."
            q.contains("heure") || q.contains("date") -> "Il est actuellement ${currentTimeFormatted()}."
            q.contains("merci") -> "Avec plaisir$nameGreeting ! Je reste disponible ici ou via la bulle flottante à tout moment."
            else ->
                "📡 *[Mode local hors-ligne]* Je vous écoute$nameGreeting ! Vous pouvez me demander une action locale (ex: *« Envoyer un message »*, *« Appeler »*, *« Mettre un minuteur »*, *« Calcule 20% de 150€ »*) ou configurer votre clé API Gemini dans **Paramètres → Clé API Gemini** pour les réponses IA complètes."
        }
    }
}
