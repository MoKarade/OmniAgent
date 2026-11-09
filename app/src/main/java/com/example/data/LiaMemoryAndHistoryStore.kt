package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.room.LiaAppUsageEntity
import com.example.data.room.LiaMessageEntity
import com.example.data.room.LiaRoomDatabase
import com.example.data.room.LiaRoomRepository
import com.example.data.room.LiaShortcutEntity
import com.example.data.room.LiaSouvenirEntity
import com.example.data.room.LiaThreadEntity
import com.example.data.room.LiaUserFactEntity
import com.example.data.room.LongTermMemoryEntity
import com.example.model.currentTimeFormatted
import com.example.security.SafeContactResolver
import com.example.service.LiaAppUsageTrackerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class LiaContactVerificationResult(
    val recipientName: String,
    val phoneNumber: String,
    val isVerified: Boolean,
    val statusLabel: String,
    val fuzzyMatchScore: Int = 100,
    val matchReason: String = ""
)

data class LiaSmsDraft(
    val recipientName: String,
    val phoneNumber: String,
    val messageBody: String,
    val isSent: Boolean = false,
    val isContactVerified: Boolean = true,
    val contactVerificationStatus: String = "✓ Contact vérifié",
    val fuzzyMatchScore: Int = 100
)

data class LiaAppLaunchAction(
    val appLabel: String,
    val packageName: String,
    val targetScreenId: String = "",
    val learnedSpeedMs: Long = 95L
)

data class LiaChatMessage(
    val id: String,
    val role: String, // "user" or "lia"
    val text: String,
    val timestamp: String,
    val source: String = "app", // "app" or "bubble"
    val smsDraft: LiaSmsDraft? = null,
    val appLaunchAction: LiaAppLaunchAction? = null,
    val isPhoneAction: Boolean = false,
    val learnedNote: String? = null,
    val quickReplies: List<String> = emptyList()
)

data class LiaConversationThread(
    val id: String,
    val title: String,
    val updatedAt: String,
    val messages: List<LiaChatMessage>
)

data class LiaUserFact(
    val key: String,
    val label: String,
    val value: String,
    val updatedAt: String
)

data class LiaLearnedShortcut(
    val id: String,
    val triggerPhrase: String,
    val actionTitle: String,
    val targetPackageOrScreen: String,
    val stepsSaved: Int,
    val avgLatencyMs: Long,
    val usesCount: Int,
    val qValueScore: Float = 0.85f,
    val preferredTimeBucket: String = "TOUJOURS"
)

data class LiaLongTermMemoryItem(
    val id: String,
    val category: String,
    val memoryKey: String,
    val title: String,
    val contentValue: String,
    val targetActionOrPackage: String = "",
    val confidenceScore: Float = 1.0f,
    val executionSpeedupMs: Long = 75L,
    val accessFrequency: Int = 1,
    val lastAccessedAt: String
)

/**
 * Domain representation of a specific user souvenir stored in the Room `lia_souvenirs` table:
 * - `"FAIT"` (personal facts: age, name, family, city, job)
 * - `"PREFERENCE"` (user tastes, habits, preferred settings)
 * - `"EVENEMENT"` (birthdays, appointments, key dates/events)
 */
data class LiaSouvenirItem(
    val id: String,
    val type: String, // "FAIT", "PREFERENCE", "EVENEMENT"
    val subjectKey: String,
    val title: String,
    val detailValue: String,
    val searchTags: String = "",
    val eventDateOrTime: String = "",
    val importanceScore: Float = 1.0f,
    val createdAt: String,
    val embeddingVectorCsv: String = "",
    val chunkText: String = ""
)

data class LiaAppUsageHabitItem(
    val packageName: String,
    val appLabel: String,
    val launchCount: Int,
    val morningCount: Int,
    val afternoonCount: Int,
    val eveningCount: Int,
    val lastTimeFormatted: String,
    val qValueHabitScore: Float
)

sealed interface LiaPendingFollowUp {
    data class WaitingForSmsBody(val recipientName: String, val phoneNumber: String) : LiaPendingFollowUp
    data class WaitingForSmsPhone(val recipientName: String, val messageBody: String) : LiaPendingFollowUp
    data class WaitingForUserFact(val factKey: String, val factLabel: String) : LiaPendingFollowUp
    data class WaitingForGeneralClarification(val previousQuestion: String) : LiaPendingFollowUp
}

data class LiaProactiveActionCard(
    val id: String,
    val emoji: String,
    val categoryBadge: String,
    val title: String,
    val subtitle: String,
    val ragSourceLabel: String,
    val confidencePercent: Int,
    val buttonLabel: String,
    val commandPrompt: String,
    val directPackageOrUri: String? = null
)

data class LiaMemorySnapshot(
    val currentThreadId: String = "thread_default",
    val threads: List<LiaConversationThread> = emptyList(),
    val userFacts: List<LiaUserFact> = emptyList(),
    val learnedShortcuts: List<LiaLearnedShortcut> = emptyList(),
    val longTermMemories: List<LiaLongTermMemoryItem> = emptyList(),
    val souvenirs: List<LiaSouvenirItem> = emptyList(),
    val appUsageHabits: List<LiaAppUsageHabitItem> = emptyList(),
    val lastRagOutcome: RagRetrievalOutcome? = null,
    val pendingFollowUp: LiaPendingFollowUp? = null
) {
    val activeThread: LiaConversationThread?
        get() = threads.find { it.id == currentThreadId } ?: threads.firstOrNull()

    val activeMessages: List<LiaChatMessage>
        get() = activeThread?.messages ?: emptyList()

    val recommendedQuickActions: List<LiaQuickActionRecommendation>
        get() = LiaReinforcementLearningEngine.rankDailyQuickActions(learnedShortcuts, appUsageHabits)

    /**
     * Génère dynamiquement les Cartes d'Actions Proactives ("Action Cards") à partir
     * de l'analyse RAG de l'historique Room (`lia_souvenirs`, `LongTermMemory`, messages récents et Google Workspace).
     */
    val proactiveRagActionCards: List<LiaProactiveActionCard>
        get() {
            val cards = mutableListOf<LiaProactiveActionCard>()
            val ws = LiaGoogleCloudWorkspaceClient.state.value

            // 1. Carte Proactive : "Reprendre la rédaction de l'e-mail" (Resume writing email)
            val emailDraftMem = longTermMemories.firstOrNull { it.category == "EMAIL_DRAFT" }
            val emailDraftSouvenir = souvenirs.firstOrNull { it.subjectKey == "last_email_draft" }
            val recentGmail = ws.recentEmails.firstOrNull()

            when {
                emailDraftMem != null -> {
                    cards.add(
                        LiaProactiveActionCard(
                            id = "rag_resume_email_mem",
                            emoji = "✉️",
                            categoryBadge = "E-mail en cours",
                            title = "Reprendre votre e-mail",
                            subtitle = "« ${emailDraftMem.contentValue.take(55)} »",
                            ragSourceLabel = "Brouillon enregistré",
                            confidencePercent = 96,
                            buttonLabel = "Reprendre",
                            commandPrompt = "Reprendre la rédaction de l'e-mail",
                            directPackageOrUri = emailDraftMem.targetActionOrPackage.takeIf { it.startsWith("mailto:") }
                        )
                    )
                }
                emailDraftSouvenir != null -> {
                    cards.add(
                        LiaProactiveActionCard(
                            id = "rag_resume_email_souvenir",
                            emoji = "✉️",
                            categoryBadge = "E-mail en cours",
                            title = "Reprendre votre e-mail",
                            subtitle = "« ${emailDraftSouvenir.detailValue.take(55)} »",
                            ragSourceLabel = "Brouillon récent",
                            confidencePercent = 94,
                            buttonLabel = "Reprendre",
                            commandPrompt = "Reprendre la rédaction de l'e-mail"
                        )
                    )
                }
                recentGmail != null -> {
                    cards.add(
                        LiaProactiveActionCard(
                            id = "rag_reply_recent_gmail",
                            emoji = "✉️",
                            categoryBadge = "Gmail",
                            title = "Répondre au dernier e-mail",
                            subtitle = "${recentGmail.subject} • ${recentGmail.from.substringBefore("<").trim()}",
                            ragSourceLabel = recentGmail.dateFormatted.ifBlank { "Récent" },
                            confidencePercent = 91,
                            buttonLabel = "Répondre",
                            commandPrompt = "Reprendre la rédaction de l'e-mail"
                        )
                    )
                }
                else -> {
                    cards.add(
                        LiaProactiveActionCard(
                            id = "rag_start_email",
                            emoji = "✉️",
                            categoryBadge = "E-mail",
                            title = "Rédiger un e-mail",
                            subtitle = "Dictez ou écrivez votre message, LIA ouvre Gmail",
                            ragSourceLabel = "Gmail",
                            confidencePercent = 85,
                            buttonLabel = "Rédiger",
                            commandPrompt = "Reprendre la rédaction de l'e-mail"
                        )
                    )
                }
            }

            // 2. Carte Proactive : "Itinéraire vers le prochain événement" (Navigate to next event)
            val nextCalEvent = ws.upcomingEvents.firstOrNull()
            val nextSouvenirEvent = souvenirs.firstOrNull { it.type == "EVENEMENT" }
            val lastDestination = souvenirs.firstOrNull { it.subjectKey == "last_maps_destination" }

            when {
                nextCalEvent != null -> {
                    val locText = nextCalEvent.locationOrDescription.takeIf { it.isNotBlank() } ?: nextCalEvent.title
                    cards.add(
                        LiaProactiveActionCard(
                            id = "rag_nav_cal_event",
                            emoji = "🗺️",
                            categoryBadge = "Prochain RDV",
                            title = "Y aller maintenant",
                            subtitle = "${nextCalEvent.title} • ${nextCalEvent.startFormatted}",
                            ragSourceLabel = locText,
                            confidencePercent = 97,
                            buttonLabel = "Itinéraire",
                            commandPrompt = "Itinéraire vers le prochain événement",
                            directPackageOrUri = "geo:0,0?q=${Uri.encode(locText)}"
                        )
                    )
                }
                nextSouvenirEvent != null -> {
                    cards.add(
                        LiaProactiveActionCard(
                            id = "rag_nav_souvenir_event",
                            emoji = "🗺️",
                            categoryBadge = "Agenda",
                            title = "Itinéraire vers votre RDV",
                            subtitle = nextSouvenirEvent.detailValue,
                            ragSourceLabel = nextSouvenirEvent.eventDateOrTime.ifBlank { "Aujourd'hui" },
                            confidencePercent = 95,
                            buttonLabel = "Itinéraire",
                            commandPrompt = "Itinéraire vers le prochain événement",
                            directPackageOrUri = "geo:0,0?q=${Uri.encode(nextSouvenirEvent.detailValue)}"
                        )
                    )
                }
                lastDestination != null -> {
                    cards.add(
                        LiaProactiveActionCard(
                            id = "rag_nav_last_dest",
                            emoji = "🗺️",
                            categoryBadge = "Trajet récent",
                            title = "Aller vers ${lastDestination.detailValue.take(22)}",
                            subtitle = "Destination fréquente",
                            ragSourceLabel = "Google Maps",
                            confidencePercent = 92,
                            buttonLabel = "Naviguer",
                            commandPrompt = "Itinéraire vers ${lastDestination.detailValue}",
                            directPackageOrUri = "geo:0,0?q=${Uri.encode(lastDestination.detailValue)}"
                        )
                    )
                }
                else -> {
                    cards.add(
                        LiaProactiveActionCard(
                            id = "rag_nav_next_event_prompt",
                            emoji = "🗺️",
                            categoryBadge = "Navigation",
                            title = "Itinéraire vers le prochain RDV",
                            subtitle = "Lance Google Maps vers votre prochain événement",
                            ragSourceLabel = "Agenda & Maps",
                            confidencePercent = 86,
                            buttonLabel = "Y aller",
                            commandPrompt = "Itinéraire vers le prochain événement"
                        )
                    )
                }
            }

            // 3. Carte Proactive : Application apprise des demandes passées ("Relancer [App]")
            val lastLearnedApp = longTermMemories.firstOrNull { it.category == "LEARNED_APP_PREF" }
            val topHabitApp = appUsageHabits.maxByOrNull { it.qValueHabitScore }
            if (lastLearnedApp != null && lastLearnedApp.targetActionOrPackage.isNotBlank()) {
                cards.add(
                    LiaProactiveActionCard(
                        id = "rag_learned_app_${lastLearnedApp.memoryKey}",
                        emoji = "🚀",
                        categoryBadge = "Habitude",
                        title = "Ouvrir ${lastLearnedApp.contentValue}",
                        subtitle = "Utilisé fréquemment",
                        ragSourceLabel = "Accès direct",
                        confidencePercent = 95,
                        buttonLabel = "Ouvrir",
                        commandPrompt = "Ouvrir ${lastLearnedApp.contentValue}",
                        directPackageOrUri = lastLearnedApp.targetActionOrPackage
                    )
                )
            } else if (topHabitApp != null) {
                cards.add(
                    LiaProactiveActionCard(
                        id = "rag_habit_app_${topHabitApp.packageName}",
                        emoji = "📱",
                        categoryBadge = "Favori",
                        title = "Ouvrir ${topHabitApp.appLabel}",
                        subtitle = "Utilisé à ${topHabitApp.lastTimeFormatted}",
                        ragSourceLabel = "Accès direct",
                        confidencePercent = (topHabitApp.qValueHabitScore * 100).toInt().coerceIn(78, 99),
                        buttonLabel = "Ouvrir",
                        commandPrompt = "Ouvrir ${topHabitApp.appLabel}",
                        directPackageOrUri = topHabitApp.packageName
                    )
                )
            }

            // 4. Carte Proactive : Brouillon SMS ou Dernier Contact / Dernière Note enregistrée
            val unsentSms = activeMessages.lastOrNull { it.smsDraft != null && !it.smsDraft.isSent }?.smsDraft
            val lastSmsContact = souvenirs.firstOrNull { it.subjectKey == "last_sms_contact" }
            val latestNote = souvenirs.firstOrNull { it.type == "NOTE" && it.subjectKey != "last_email_draft" }

            if (unsentSms != null) {
                cards.add(
                    LiaProactiveActionCard(
                        id = "rag_resume_sms_draft",
                        emoji = "💬",
                        categoryBadge = "Message en attente",
                        title = "Message pour ${unsentSms.recipientName}",
                        subtitle = "« ${unsentSms.messageBody.take(50)} »",
                        ragSourceLabel = "En attente d'envoi",
                        confidencePercent = 96,
                        buttonLabel = "Continuer",
                        commandPrompt = "Reprendre le SMS"
                    )
                )
            } else if (lastSmsContact != null) {
                val contactName = lastSmsContact.detailValue.substringBefore("(").trim()
                cards.add(
                    LiaProactiveActionCard(
                        id = "rag_sms_last_contact",
                        emoji = "💬",
                        categoryBadge = "Contact récent",
                        title = "Écrire à $contactName",
                        subtitle = "Envoyer un message rapide",
                        ragSourceLabel = contactName,
                        confidencePercent = 90,
                        buttonLabel = "Écrire",
                        commandPrompt = "Envoyer un SMS à $contactName"
                    )
                )
            } else if (latestNote != null) {
                val cleanNote = latestNote.detailValue.substringBefore("(enregistrée").trim()
                cards.add(
                    LiaProactiveActionCard(
                        id = "rag_note_to_event_${latestNote.id}",
                        emoji = "📝",
                        categoryBadge = "Dernière note",
                        title = "Planifier dans l'Agenda",
                        subtitle = "« ${cleanNote.take(48)} »",
                        ragSourceLabel = "Notes",
                        confidencePercent = 89,
                        buttonLabel = "Planifier",
                        commandPrompt = "Ajouter à l'agenda : $cleanNote"
                    )
                )
            }

            // 5. Carte Proactive : Steam & Messageries intelligentes
            val steamGame = souvenirs.firstOrNull {
                it.subjectKey.contains("steam", ignoreCase = true) ||
                    it.searchTags.contains("steam", ignoreCase = true)
            }?.detailValue
            cards.add(
                LiaProactiveActionCard(
                    id = "rag_steam_recent_game",
                    emoji = "🎮",
                    categoryBadge = "Steam",
                    title = if (!steamGame.isNullOrBlank()) "Reprendre $steamGame" else "Dernier jeu joué",
                    subtitle = "Retrouve votre activité récente dans Steam",
                    ragSourceLabel = "Bibliothèque Steam",
                    confidencePercent = 94,
                    buttonLabel = "Voir",
                    commandPrompt = "Retrouve le dernier jeu auquel j'ai joué dans Steam",
                    directPackageOrUri = "steam:recent_games"
                )
            )

            return cards
        }
}

object LiaMemoryAndHistoryStore {

    private const val PREFS_NAME = "lia_clean_memory_v4"
    private const val KEY_THREADS_JSON = "threads_json"
    private const val KEY_CURRENT_THREAD_ID = "current_thread_id"
    private const val KEY_USER_FACTS_JSON = "user_facts_json"
    private const val KEY_SHORTCUTS_JSON = "shortcuts_json"

    @Volatile
    private var isInitialized = false
    private var appContext: Context? = null
    private var roomRepository: LiaRoomRepository? = null
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(LiaMemorySnapshot())
    val state: StateFlow<LiaMemorySnapshot> = _state.asStateFlow()

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            loadFromPreferences(ctx)
            val db = LiaRoomDatabase.getInstance(ctx)
            val repo = LiaRoomRepository(db.liaDao())
            roomRepository = repo
            seedAndObserveRoomDatabase(repo)
            isInitialized = true
        }
    }

    fun getApplicationContextOrNull(): Context? = appContext

    private fun seedAndObserveRoomDatabase(repo: LiaRoomRepository) {
        storeScope.launch {
            val initialSnap = _state.value
            if (repo.getThreadCount() == 0 && initialSnap.threads.isNotEmpty()) {
                val threadEntities = initialSnap.threads.mapIndexed { idx, t ->
                    LiaThreadEntity(
                        id = t.id,
                        title = t.title,
                        updatedAt = t.updatedAt,
                        updatedAtEpochMs = System.currentTimeMillis() - (idx * 1000L)
                    )
                }
                repo.upsertThreads(threadEntities)
                val allMsgEntities = initialSnap.threads.flatMap { t ->
                    t.messages.mapIndexed { mIdx, m ->
                        m.toRoomEntity(t.id, System.currentTimeMillis() + mIdx)
                    }
                }
                if (allMsgEntities.isNotEmpty()) {
                    repo.upsertMessages(allMsgEntities)
                }
            }

            if (repo.getUserFactsCount() == 0 && initialSnap.userFacts.isNotEmpty()) {
                repo.upsertUserFacts(initialSnap.userFacts.map { it.toRoomEntity() })
            }

            if (repo.getShortcutsCount() == 0 && initialSnap.learnedShortcuts.isNotEmpty()) {
                repo.upsertShortcuts(initialSnap.learnedShortcuts.map { it.toRoomEntity() })
            }

            if (repo.getLongTermMemoryCount() == 0 && initialSnap.longTermMemories.isNotEmpty()) {
                repo.upsertLongTermMemories(initialSnap.longTermMemories.map { it.toRoomEntity() })
            }

            if (repo.getSouvenirsCount() == 0 && initialSnap.souvenirs.isNotEmpty()) {
                repo.upsertSouvenirs(initialSnap.souvenirs.map { it.toRoomEntity() })
            }

            // Once one-time migration from legacy SharedPreferences to Room is done, purge legacy JSON keys
            appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
                ?.remove(KEY_THREADS_JSON)
                ?.remove(KEY_USER_FACTS_JSON)
                ?.remove(KEY_SHORTCUTS_JSON)
                ?.apply()

            // Observe all 7 Room tables continuously with a strongly-typed holder (no UNCHECKED_CAST)
            combine(
                combine(repo.allThreads, repo.allMessages) { t, m -> t to m },
                combine(repo.allUserFacts, repo.allSouvenirs) { f, s -> f to s },
                combine(repo.allShortcuts, repo.allAppUsageFrequencies) { sc, au -> sc to au },
                repo.allLongTermMemories
            ) { (threadEntities, messageEntities), (factEntities, souvenirEntities), (shortcutEntities, appUsageEntities), ltmEntities ->
                val messagesByThread = messageEntities.groupBy { it.threadId }
                val mappedThreads = threadEntities.map { tEntity ->
                    val msgs = messagesByThread[tEntity.id]
                        .orEmpty()
                        .sortedBy { it.createdAtEpochMs }
                        .map { it.toDomainModel() }
                    LiaConversationThread(
                        id = tEntity.id,
                        title = tEntity.title,
                        updatedAt = tEntity.updatedAt,
                        messages = msgs
                    )
                }.ifEmpty {
                    listOf(
                        LiaConversationThread(
                            id = "thread_default",
                            title = "Nouvelle discussion",
                            updatedAt = currentTimeFormatted(),
                            messages = emptyList()
                        )
                    )
                }

                CombinedRoomStreams(
                    threads = mappedThreads,
                    userFacts = factEntities.map { it.toDomainModel() }.ifEmpty { defaultUserFacts() },
                    learnedShortcuts = shortcutEntities.map { it.toDomainModel() }.ifEmpty { defaultLearnedShortcuts() },
                    longTermMemories = ltmEntities.map { it.toDomainModel() }.ifEmpty { defaultLongTermMemories() },
                    souvenirs = souvenirEntities.map { it.toDomainModel() }.ifEmpty { defaultSouvenirs() },
                    appUsageHabits = appUsageEntities.map { it.toDomainModel() }
                )
            }.collect { combined ->
                _state.update { current ->
                    val validThreadId = if (combined.threads.any { it.id == current.currentThreadId }) {
                        current.currentThreadId
                    } else {
                        combined.threads.first().id
                    }
                    current.copy(
                        currentThreadId = validThreadId,
                        threads = combined.threads,
                        userFacts = combined.userFacts,
                        learnedShortcuts = combined.learnedShortcuts,
                        longTermMemories = combined.longTermMemories,
                        souvenirs = combined.souvenirs,
                        appUsageHabits = combined.appUsageHabits
                    )
                }
            }
        }
    }

    private data class CombinedRoomStreams(
        val threads: List<LiaConversationThread>,
        val userFacts: List<LiaUserFact>,
        val learnedShortcuts: List<LiaLearnedShortcut>,
        val longTermMemories: List<LiaLongTermMemoryItem>,
        val souvenirs: List<LiaSouvenirItem>,
        val appUsageHabits: List<LiaAppUsageHabitItem>
    )

    private fun loadFromPreferences(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedThreadId = prefs.getString(KEY_CURRENT_THREAD_ID, null)
        val threadsRaw = prefs.getString(KEY_THREADS_JSON, null)
        val factsRaw = prefs.getString(KEY_USER_FACTS_JSON, null)
        val shortcutsRaw = prefs.getString(KEY_SHORTCUTS_JSON, null)

        val loadedThreads = if (!threadsRaw.isNullOrBlank()) {
            parseThreadsJson(threadsRaw)
        } else {
            emptyList()
        }

        val initialThreads = loadedThreads.ifEmpty {
            listOf(
                LiaConversationThread(
                    id = "thread_default",
                    title = "Nouvelle discussion",
                    updatedAt = currentTimeFormatted(),
                    messages = emptyList()
                )
            )
        }

        val activeId = if (savedThreadId != null && initialThreads.any { it.id == savedThreadId }) {
            savedThreadId
        } else {
            initialThreads.first().id
        }

        val loadedFacts = if (!factsRaw.isNullOrBlank()) {
            parseFactsJson(factsRaw)
        } else {
            defaultUserFacts()
        }

        val loadedShortcuts = if (!shortcutsRaw.isNullOrBlank()) {
            parseShortcutsJson(shortcutsRaw)
        } else {
            defaultLearnedShortcuts()
        }

        _state.value = LiaMemorySnapshot(
            currentThreadId = activeId,
            threads = initialThreads,
            userFacts = loadedFacts,
            learnedShortcuts = loadedShortcuts,
            longTermMemories = defaultLongTermMemories(),
            souvenirs = defaultSouvenirs(),
            pendingFollowUp = null
        )
    }

    /**
     * PERF-01: Room v6 is the single source of truth for threads, messages, facts, souvenirs, and shortcuts.
     * SharedPreferences only stores the lightweight active thread ID pointer.
     */
    private fun persistToPreferences() {
        val ctx = appContext ?: return
        val snapshot = _state.value
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CURRENT_THREAD_ID, snapshot.currentThreadId)
            .apply()
    }

    private fun defaultUserFacts(): List<LiaUserFact> = emptyList()

    private fun defaultSouvenirs(): List<LiaSouvenirItem> = emptyList()

    private fun defaultLearnedShortcuts(): List<LiaLearnedShortcut> = emptyList()

    private fun defaultLongTermMemories(): List<LiaLongTermMemoryItem> = emptyList()

    /**
     * Upserts a specific user souvenir (`FAIT`, `PREFERENCE`, or `EVENEMENT`) in the Room `lia_souvenirs` table,
     * automatically computing its 1000-char RAG chunk and normalized 64-dim vector embedding.
     */
    fun upsertSouvenir(
        type: String,
        subjectKey: String,
        title: String,
        detailValue: String,
        searchTags: String = "",
        eventDateOrTime: String = "",
        importanceScore: Float = 1.0f
    ) {
        val cleanDetail = detailValue.trim()
        if (cleanDetail.isBlank()) return
        val now = currentTimeFormatted()
        val normKey = subjectKey.lowercase().trim().replace(" ", "_")
        val finalTags = searchTags.ifBlank { "$normKey,${title.lowercase()}" }
        val chunk = LiaSouvenirRagEngine.buildChunkText(
            type = type,
            subjectKey = normKey,
            title = title,
            detailValue = cleanDetail,
            searchTags = finalTags,
            eventDateOrTime = eventDateOrTime
        )
        val embeddingCsv = LiaSouvenirRagEngine.serializeEmbeddingCsv(
            LiaSouvenirRagEngine.computeLocalEmbedding(chunk)
        )
        var savedSouvenir: LiaSouvenirItem? = null

        _state.update { current ->
            val existing = current.souvenirs.find {
                it.subjectKey.equals(normKey, ignoreCase = true) ||
                    (it.type == type && it.title.equals(title, ignoreCase = true))
            }
            val nextList = if (existing != null) {
                current.souvenirs.map { item ->
                    if (item.id == existing.id) {
                        val updated = item.copy(
                            type = type,
                            title = title.ifBlank { item.title },
                            detailValue = cleanDetail,
                            searchTags = finalTags,
                            eventDateOrTime = eventDateOrTime.ifBlank { item.eventDateOrTime },
                            importanceScore = (item.importanceScore + 0.05f).coerceAtMost(1.2f),
                            createdAt = now,
                            embeddingVectorCsv = embeddingCsv,
                            chunkText = chunk
                        )
                        savedSouvenir = updated
                        updated
                    } else item
                }
            } else {
                val created = LiaSouvenirItem(
                    id = "souv_${normKey}_${System.currentTimeMillis() % 10000}",
                    type = type,
                    subjectKey = normKey,
                    title = title,
                    detailValue = cleanDetail,
                    searchTags = finalTags,
                    eventDateOrTime = eventDateOrTime,
                    importanceScore = importanceScore,
                    createdAt = now,
                    embeddingVectorCsv = embeddingCsv,
                    chunkText = chunk
                )
                savedSouvenir = created
                listOf(created) + current.souvenirs
            }
            current.copy(souvenirs = nextList)
        }

        savedSouvenir?.let { souv ->
            storeScope.launch {
                roomRepository?.upsertSouvenir(souv.toRoomEntity())
            }
        }
    }

    fun deleteSouvenir(id: String) {
        _state.update { current ->
            current.copy(souvenirs = current.souvenirs.filterNot { it.id == id })
        }
        storeScope.launch {
            roomRepository?.deleteSouvenir(id)
        }
    }

    /**
     * Annule en 1 clic un souvenir ou fait appris passivement depuis une bulle de discussion.
     */
    fun undoLatestLearnedSouvenir(messageId: String) {
        val latestSouvenir = _state.value.souvenirs.firstOrNull()
        if (latestSouvenir != null) {
            deleteSouvenir(latestSouvenir.id)
            deleteUserFact(latestSouvenir.subjectKey)
        }
        var updatedMsgForRoom: Pair<String, LiaChatMessage>? = null
        _state.update { current ->
            val updatedThreads = current.threads.map { thread ->
                thread.copy(
                    messages = thread.messages.map { msg ->
                        if (msg.id == messageId) {
                            val updated = msg.copy(learnedNote = null)
                            updatedMsgForRoom = thread.id to updated
                            updated
                        } else msg
                    }
                )
            }
            current.copy(threads = updatedThreads)
        }
        persistToPreferences()
        updatedMsgForRoom?.let { (tId, msg) ->
            storeScope.launch {
                roomRepository?.upsertMessage(msg.toRoomEntity(tId, System.currentTimeMillis()))
            }
        }
    }

    /**
     * Exécute le pipeline RAG (Retrieval-Augmented Generation) sur la table Room `lia_souvenirs`
     * avant chaque génération de réponse de LIA.
     */
    fun executeSouvenirsRag(userQuery: String, topK: Int = 5): RagRetrievalOutcome {
        val snap = _state.value
        val outcome = LiaSouvenirRagEngine.retrieveForPrompt(
            userQuery = userQuery,
            souvenirs = snap.souvenirs,
            userFacts = snap.userFacts,
            topK = topK
        )
        _state.update { it.copy(lastRagOutcome = outcome) }
        return outcome
    }

    /**
     * Queries the Room `lia_souvenirs` table (and in-memory cache) for any souvenirs matching [topicQuery].
     */
    fun querySouvenirsForTopic(topicQuery: String): List<LiaSouvenirItem> {
        val q = topicQuery.lowercase().trim()
        if (q.isBlank()) return _state.value.souvenirs
        return _state.value.souvenirs.filter { s ->
            s.subjectKey.lowercase().contains(q) ||
                s.title.lowercase().contains(q) ||
                s.detailValue.lowercase().contains(q) ||
                s.searchTags.lowercase().contains(q) ||
                s.type.lowercase().contains(q)
        }
    }

    fun upsertLongTermMemory(
        category: String,
        memoryKey: String,
        title: String,
        contentValue: String,
        targetActionOrPackage: String = "",
        speedupMs: Long = 70L
    ) {
        val now = currentTimeFormatted()
        var savedEntry: LiaLongTermMemoryItem? = null

        _state.update { current ->
            val existing = current.longTermMemories.find {
                it.memoryKey.equals(memoryKey, ignoreCase = true) ||
                    (it.category == category && it.title.equals(title, ignoreCase = true))
            }
            val nextList = if (existing != null) {
                current.longTermMemories.map { item ->
                    if (item.id == existing.id) {
                        val updated = item.copy(
                            title = title.ifBlank { item.title },
                            contentValue = contentValue.ifBlank { item.contentValue },
                            targetActionOrPackage = targetActionOrPackage.ifBlank { item.targetActionOrPackage },
                            executionSpeedupMs = ((item.executionSpeedupMs + speedupMs) / 2).coerceAtLeast(35L),
                            accessFrequency = item.accessFrequency + 1,
                            confidenceScore = (item.confidenceScore + 0.02f).coerceAtMost(1.0f),
                            lastAccessedAt = now
                        )
                        savedEntry = updated
                        updated
                    } else item
                }
            } else {
                val created = LiaLongTermMemoryItem(
                    id = "ltm_${System.currentTimeMillis()}",
                    category = category,
                    memoryKey = memoryKey,
                    title = title,
                    contentValue = contentValue,
                    targetActionOrPackage = targetActionOrPackage,
                    confidenceScore = 0.95f,
                    executionSpeedupMs = speedupMs,
                    accessFrequency = 1,
                    lastAccessedAt = now
                )
                savedEntry = created
                listOf(created) + current.longTermMemories
            }
            current.copy(longTermMemories = nextList)
        }

        savedEntry?.let { entry ->
            storeScope.launch {
                roomRepository?.upsertLongTermMemory(entry.toRoomEntity())
            }
        }
    }

    fun deleteLongTermMemory(id: String) {
        _state.update { current ->
            current.copy(longTermMemories = current.longTermMemories.filterNot { it.id == id })
        }
        storeScope.launch {
            roomRepository?.deleteLongTermMemory(id)
        }
    }

    fun startNewConversation(): String {
        val now = currentTimeFormatted()
        val newId = "thread_${System.currentTimeMillis()}"
        val newThread = LiaConversationThread(
            id = newId,
            title = "Discussion ($now)",
            updatedAt = now,
            messages = emptyList()
        )
        _state.update { current ->
            current.copy(
                currentThreadId = newId,
                threads = listOf(newThread) + current.threads,
                pendingFollowUp = null
            )
        }
        persistToPreferences()
        storeScope.launch {
            roomRepository?.upsertThread(
                LiaThreadEntity(
                    id = newId,
                    title = newThread.title,
                    updatedAt = now,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
        return newId
    }

    fun selectConversation(threadId: String) {
        _state.update { current ->
            if (current.threads.any { it.id == threadId }) {
                current.copy(currentThreadId = threadId, pendingFollowUp = null)
            } else current
        }
        persistToPreferences()
    }

    fun deleteConversation(threadId: String) {
        val fallbackThread = LiaConversationThread(
            id = "thread_${System.currentTimeMillis()}",
            title = "Nouvelle discussion",
            updatedAt = currentTimeFormatted(),
            messages = emptyList()
        )
        var createdFallback = false
        _state.update { current ->
            val filtered = current.threads.filterNot { it.id == threadId }
            val remaining = if (filtered.isEmpty()) {
                createdFallback = true
                listOf(fallbackThread)
            } else {
                filtered
            }
            val nextActive = if (current.currentThreadId == threadId) remaining.first().id else current.currentThreadId
            current.copy(
                currentThreadId = nextActive,
                threads = remaining,
                pendingFollowUp = null
            )
        }
        persistToPreferences()
        storeScope.launch {
            roomRepository?.deleteThread(threadId)
            if (createdFallback) {
                roomRepository?.upsertThread(
                    LiaThreadEntity(
                        id = fallbackThread.id,
                        title = fallbackThread.title,
                        updatedAt = fallbackThread.updatedAt,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun clearCurrentConversationMessages() {
        val activeId = _state.value.currentThreadId
        val now = currentTimeFormatted()
        _state.update { current ->
            val updatedThreads = current.threads.map { t ->
                if (t.id == activeId) {
                    t.copy(
                        title = "Nouvelle discussion",
                        messages = emptyList(),
                        updatedAt = now
                    )
                } else t
            }
            current.copy(threads = updatedThreads, pendingFollowUp = null)
        }
        persistToPreferences()
        storeScope.launch {
            roomRepository?.clearMessagesInThread(activeId)
            roomRepository?.upsertThread(
                LiaThreadEntity(
                    id = activeId,
                    title = "Nouvelle discussion",
                    updatedAt = now,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun appendMessage(message: LiaChatMessage) {
        val nowMs = System.currentTimeMillis()
        var targetThreadEntity: LiaThreadEntity? = null

        _state.update { current ->
            val targetId = current.currentThreadId
            val exists = current.threads.any { it.id == targetId }
            val baseThreads = if (exists) current.threads else {
                listOf(
                    LiaConversationThread(
                        id = targetId,
                        title = "Discussion",
                        updatedAt = currentTimeFormatted(),
                        messages = emptyList()
                    )
                ) + current.threads
            }

            val updatedThreads = baseThreads.map { thread ->
                if (thread.id == targetId) {
                    val replacedOrAdded = if (thread.messages.any { it.id == message.id }) {
                        thread.messages.map { if (it.id == message.id) message else it }
                    } else {
                        thread.messages + message
                    }
                    val newTitle = if (thread.messages.isEmpty() && message.role == "user") {
                        message.text.take(34).ifBlank { thread.title }
                    } else {
                        thread.title
                    }
                    targetThreadEntity = LiaThreadEntity(
                        id = thread.id,
                        title = newTitle,
                        updatedAt = message.timestamp,
                        updatedAtEpochMs = nowMs
                    )
                    thread.copy(
                        title = newTitle,
                        updatedAt = message.timestamp,
                        messages = replacedOrAdded
                    )
                } else {
                    thread
                }
            }
            current.copy(threads = updatedThreads)
        }
        persistToPreferences()

        val activeThreadId = _state.value.currentThreadId
        storeScope.launch {
            targetThreadEntity?.let { roomRepository?.upsertThread(it) }
            roomRepository?.upsertMessage(message.toRoomEntity(activeThreadId, nowMs))
        }
    }

    fun setPendingFollowUp(followUp: LiaPendingFollowUp?) {
        _state.update { it.copy(pendingFollowUp = followUp) }
    }

    fun upsertUserFact(key: String, label: String, value: String) {
        val cleanVal = value.trim()
        if (cleanVal.isBlank()) return
        val now = currentTimeFormatted()
        val normalizedKey = key.lowercase().trim()
        var updatedFact: LiaUserFact? = null

        _state.update { current ->
            val exists = current.userFacts.any { it.key.equals(normalizedKey, ignoreCase = true) }
            val nextFacts = if (exists) {
                current.userFacts.map {
                    if (it.key.equals(normalizedKey, ignoreCase = true)) {
                        val f = it.copy(label = label.ifBlank { it.label }, value = cleanVal, updatedAt = now)
                        updatedFact = f
                        f
                    } else it
                }
            } else {
                val f = LiaUserFact(normalizedKey, label, cleanVal, now)
                updatedFact = f
                listOf(f) + current.userFacts
            }
            current.copy(userFacts = nextFacts)
        }
        persistToPreferences()
        updatedFact?.let { fact ->
            storeScope.launch {
                roomRepository?.upsertUserFact(fact.toRoomEntity())
            }
        }
        // Also store in Room `lia_souvenirs` table as a personal FAIT so LIA can query it accurately
        upsertSouvenir(
            type = "FAIT",
            subjectKey = normalizedKey,
            title = label,
            detailValue = cleanVal,
            searchTags = "$normalizedKey,${label.lowercase()},${cleanVal.lowercase()}"
        )
        upsertLongTermMemory(
            category = "PREFERENCE",
            memoryKey = "fact_$normalizedKey",
            title = label,
            contentValue = cleanVal,
            targetActionOrPackage = "user_profile"
        )
    }

    fun deleteUserFact(key: String) {
        _state.update { current ->
            current.copy(userFacts = current.userFacts.filterNot { it.key == key })
        }
        persistToPreferences()
        storeScope.launch {
            roomRepository?.deleteUserFact(key)
        }
    }

    fun getUserFactValue(key: String): String? {
        // First query Room `lia_souvenirs` table for exact subjectKey match
        val souvenirMatch = _state.value.souvenirs.find {
            it.subjectKey.equals(key, ignoreCase = true) && !it.detailValue.startsWith("Non renseigné", ignoreCase = true)
        }
        if (souvenirMatch != null) return souvenirMatch.detailValue

        val fact = _state.value.userFacts.find { it.key.equals(key, ignoreCase = true) } ?: return null
        if (fact.value.startsWith("Non renseigné", ignoreCase = true)) return null
        return fact.value
    }

    fun verifyContactForSms(rawTarget: String): LiaContactVerificationResult {
        val resolution = SafeContactResolver.resolveContact(rawTarget, _state.value.userFacts)
        val best = resolution.bestCandidate
        val validPhone = isPhoneNumberValid(best.phoneNumber)
        val statusLabel = if (validPhone) {
            "✓ Safe Contact Resolver (${best.matchScorePercent}%) : ${best.contactName} ↔ ${best.phoneNumber}"
        } else {
            "⚠️ ${resolution.warningMessage ?: "Numéro non trouvé pour « ${best.contactName} »"}"
        }
        return LiaContactVerificationResult(
            recipientName = best.contactName,
            phoneNumber = best.phoneNumber,
            isVerified = validPhone,
            statusLabel = statusLabel,
            fuzzyMatchScore = best.matchScorePercent,
            matchReason = best.matchReason
        )
    }

    fun isPhoneNumberValid(phone: String): Boolean {
        val digits = phone.filter { it.isDigit() }
        return digits.length >= 8 && !phone.contains("00 00 00 00")
    }

    fun findPhoneNumberForRecipient(recipientName: String): String {
        return verifyContactForSms(recipientName).phoneNumber
    }

    /**
     * Records an app launch or shortcut execution and updates its Reinforcement Learning Q-Value
     * and contextual time-of-day bucket via [LiaReinforcementLearningEngine].
     */
    fun recordLearnedShortcut(
        triggerPhrase: String,
        actionTitle: String,
        targetPackageOrScreen: String,
        stepsSaved: Int = 2,
        latencyMs: Long = 80L,
        rewardSignal: Float = 1.0f
    ) {
        val activeTimeBucket = LiaReinforcementLearningEngine.currentTimeBucket()
        var savedShortcut: LiaLearnedShortcut? = null

        _state.update { current ->
            val existing = current.learnedShortcuts.find {
                it.triggerPhrase.equals(triggerPhrase, ignoreCase = true) ||
                    it.targetPackageOrScreen.equals(targetPackageOrScreen, ignoreCase = true)
            }
            val updatedList = if (existing != null) {
                current.learnedShortcuts.map { sc ->
                    if (sc.id == existing.id) {
                        val nextUses = sc.usesCount + 1
                        val nextQ = LiaReinforcementLearningEngine.computeUpdatedQValue(
                            currentQ = sc.qValueScore,
                            usesCount = nextUses,
                            reward = rewardSignal
                        )
                        val updated = sc.copy(
                            usesCount = nextUses,
                            avgLatencyMs = ((sc.avgLatencyMs + latencyMs) / 2).coerceAtLeast(45L),
                            qValueScore = nextQ,
                            preferredTimeBucket = activeTimeBucket
                        )
                        savedShortcut = updated
                        updated
                    } else sc
                }
            } else {
                val initialQ = LiaReinforcementLearningEngine.computeUpdatedQValue(0.82f, 1, rewardSignal)
                val created = LiaLearnedShortcut(
                    id = "sc_${System.currentTimeMillis()}",
                    triggerPhrase = triggerPhrase,
                    actionTitle = actionTitle,
                    targetPackageOrScreen = targetPackageOrScreen,
                    stepsSaved = stepsSaved,
                    avgLatencyMs = latencyMs,
                    usesCount = 1,
                    qValueScore = initialQ,
                    preferredTimeBucket = activeTimeBucket
                )
                savedShortcut = created
                listOf(created) + current.learnedShortcuts
            }
            current.copy(learnedShortcuts = updatedList)
        }
        persistToPreferences()
        savedShortcut?.let { sc ->
            storeScope.launch {
                roomRepository?.upsertShortcut(sc.toRoomEntity())
            }
        }
        val category = if (targetPackageOrScreen.startsWith("com.")) "APP_SHORTCUT" else "INTERACTION_PATTERN"
        upsertLongTermMemory(
            category = category,
            memoryKey = "ltm_${triggerPhrase.lowercase().replace(" ", "_")}",
            title = triggerPhrase,
            contentValue = "$actionTitle ($stepsSaved étapes économisées • Q=${savedShortcut?.qValueScore ?: 0.9f})",
            targetActionOrPackage = targetPackageOrScreen,
            speedupMs = latencyMs
        )
    }

    fun updateSmsDraftPreview(
        messageId: String,
        updatedRecipient: String,
        updatedPhone: String,
        updatedBody: String
    ) {
        val cleanRecipient = updatedRecipient.trim().ifBlank { "Contact" }
        val cleanPhone = updatedPhone.trim()
        val cleanBody = updatedBody.trim()
        val verified = isPhoneNumberValid(cleanPhone)
        val statusText = if (verified) {
            "✓ Safe Contact Resolver (100%) : $cleanRecipient ↔ $cleanPhone"
        } else {
            "⚠️ Numéro incomplet ou manquant pour « $cleanRecipient »"
        }

        if (verified && cleanRecipient != "Numéro direct") {
            upsertUserFact("${cleanRecipient.lowercase()}_tel", "Numéro $cleanRecipient", cleanPhone)
        }

        var updatedMsgForRoom: Pair<String, LiaChatMessage>? = null
        _state.update { current ->
            val updatedThreads = current.threads.map { thread ->
                thread.copy(
                    messages = thread.messages.map { msg ->
                        if (msg.id == messageId && msg.smsDraft != null) {
                            val newMsg = msg.copy(
                                smsDraft = msg.smsDraft.copy(
                                    recipientName = cleanRecipient,
                                    phoneNumber = cleanPhone,
                                    messageBody = cleanBody,
                                    isContactVerified = verified,
                                    contactVerificationStatus = statusText,
                                    fuzzyMatchScore = if (verified) 100 else 0
                                )
                            )
                            updatedMsgForRoom = thread.id to newMsg
                            newMsg
                        } else msg
                    }
                )
            }
            current.copy(threads = updatedThreads)
        }
        persistToPreferences()
        updatedMsgForRoom?.let { (threadId, msg) ->
            storeScope.launch {
                roomRepository?.upsertMessage(msg.toRoomEntity(threadId, System.currentTimeMillis()))
            }
        }
    }

    fun markSmsMessageSent(
        messageId: String,
        updatedRecipient: String,
        updatedPhone: String,
        updatedBody: String
    ) {
        val cleanRecipient = updatedRecipient.trim().ifBlank { "Contact" }
        val cleanPhone = updatedPhone.trim()
        val cleanBody = updatedBody.trim()
        var updatedMsgForRoom: Pair<String, LiaChatMessage>? = null

        _state.update { current ->
            val updatedThreads = current.threads.map { thread ->
                thread.copy(
                    messages = thread.messages.map { msg ->
                        if (msg.id == messageId && msg.smsDraft != null) {
                            val newMsg = msg.copy(
                                text = "↗ Brouillon SMS ouvert dans Messages pour $cleanRecipient ($cleanPhone) : « $cleanBody »",
                                smsDraft = msg.smsDraft.copy(
                                    recipientName = cleanRecipient,
                                    phoneNumber = cleanPhone,
                                    messageBody = cleanBody,
                                    isSent = true,
                                    isContactVerified = true,
                                    contactVerificationStatus = "↗ Ouvert dans Messages ($cleanPhone)",
                                    fuzzyMatchScore = 100
                                )
                            )
                            updatedMsgForRoom = thread.id to newMsg
                            newMsg
                        } else msg
                    }
                )
            }
            current.copy(threads = updatedThreads)
        }
        persistToPreferences()
        updatedMsgForRoom?.let { (threadId, msg) ->
            storeScope.launch {
                roomRepository?.upsertMessage(msg.toRoomEntity(threadId, System.currentTimeMillis()))
            }
        }
    }

    fun cancelSmsDraft(messageId: String? = null) {
        val updatedMsgs = mutableListOf<Pair<String, LiaChatMessage>>()
        _state.update { current ->
            val targetMsgId = messageId ?: current.activeMessages.lastOrNull { it.smsDraft != null && !it.smsDraft.isSent }?.id
            val updatedThreads = current.threads.map { thread ->
                thread.copy(
                    messages = thread.messages.map { msg ->
                        if ((targetMsgId != null && msg.id == targetMsgId) || (targetMsgId == null && msg.smsDraft != null && !msg.smsDraft.isSent)) {
                            val newMsg = msg.copy(
                                text = "✓ Envoi de SMS annulé.",
                                smsDraft = null,
                                learnedNote = null
                            )
                            updatedMsgs.add(thread.id to newMsg)
                            newMsg
                        } else msg
                    }
                )
            }
            current.copy(
                threads = updatedThreads,
                pendingFollowUp = null
            )
        }
        persistToPreferences()
        updatedMsgs.forEach { (threadId, msg) ->
            storeScope.launch {
                roomRepository?.upsertMessage(msg.toRoomEntity(threadId, System.currentTimeMillis()))
            }
        }
    }

    fun sendSmsDirectly(
        context: Context,
        messageId: String?,
        recipientName: String,
        phoneNumber: String,
        messageBody: String
    ) {
        val cleanRecipient = recipientName.trim().ifBlank { "Contact" }
        val cleanPhone = phoneNumber.trim()
        val cleanBody = messageBody.trim()

        if (isPhoneNumberValid(cleanPhone) && cleanRecipient != "Numéro direct") {
            upsertUserFact("${cleanRecipient.lowercase()}_tel", "Numéro $cleanRecipient", cleanPhone)
        }

        val launched = runCatching {
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$cleanPhone")).apply {
                putExtra("sms_body", cleanBody)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(smsIntent)
            true
        }.getOrDefault(false)

        if (launched && messageId != null) {
            markSmsMessageSent(messageId, cleanRecipient, cleanPhone, cleanBody)
        }
        recordLearnedShortcut(
            triggerPhrase = "SMS $cleanRecipient",
            actionTitle = "Ouverture SMS vérifié vers $cleanRecipient ($cleanPhone)",
            targetPackageOrScreen = "smsto:$cleanPhone",
            stepsSaved = 3,
            latencyMs = 70L,
            rewardSignal = 1.15f
        )
    }

    fun launchAppFast(context: Context, packageName: String, appLabel: String = ""): Boolean {
        val matchedShortcut = _state.value.learnedShortcuts.find {
            it.targetPackageOrScreen.equals(packageName, ignoreCase = true)
        }
        if (matchedShortcut != null) {
            recordLearnedShortcut(
                triggerPhrase = matchedShortcut.triggerPhrase,
                actionTitle = matchedShortcut.actionTitle,
                targetPackageOrScreen = matchedShortcut.targetPackageOrScreen,
                stepsSaved = matchedShortcut.stepsSaved,
                latencyMs = matchedShortcut.avgLatencyMs,
                rewardSignal = 1.15f
            )
        }
        return com.example.service.AppActionExecutor.executeTargetDirectly(
            context = context,
            packageNameOrUri = packageName,
            appLabel = appLabel
        )
    }

    /**
     * Automatically detects if the user is telling LIA a personal fact, preference, or event
     * and stores it in both `lia_user_facts` and the dedicated `lia_souvenirs` Room table.
     */
    fun extractAndLearnPersonalFacts(rawInput: String): String? {
        val text = rawInput.trim()
        val lower = text.lowercase()

        val pending = _state.value.pendingFollowUp
        if (pending is LiaPendingFollowUp.WaitingForUserFact) {
            val numberMatch = Regex("(\\d{1,3})").find(text)
            if (pending.factKey == "age" && numberMatch != null) {
                val ageVal = "${numberMatch.groupValues[1]} ans"
                upsertUserFact("age", "Âge", ageVal)
                upsertSouvenir(
                    type = "FAIT",
                    subjectKey = "age",
                    title = "Âge de l'utilisateur",
                    detailValue = ageVal,
                    searchTags = "age,ans,anniversaire,naissance,$ageVal"
                )
                setPendingFollowUp(null)
                return "🧠 Mémorisé : Âge = $ageVal"
            } else if (text.length in 1..60 && !lower.contains("?")) {
                upsertUserFact(pending.factKey, pending.factLabel, text)
                upsertSouvenir(
                    type = "FAIT",
                    subjectKey = pending.factKey,
                    title = pending.factLabel,
                    detailValue = text,
                    searchTags = "${pending.factKey},${pending.factLabel.lowercase()}"
                )
                setPendingFollowUp(null)
                return "🧠 Mémorisé : ${pending.factLabel} = $text"
            }
        }

        // 0. Spontaneous Steam / Last played game statement: "j'ai joué à X sur steam", "mon dernier jeu steam est X"
        val steamGameRegex = Regex("(?:j'ai joué à|jai joue a|mon dernier jeu(?:\\s+steam)?\\s+est|je joue à|je joue a)\\s+([A-Za-z0-9À-ÿ\\s:'\\-]{2,40}?)(?:\\s+sur\\s+steam|\\s+dans\\s+steam|$)", RegexOption.IGNORE_CASE)
        if (lower.contains("steam") || lower.contains("dernier jeu") || lower.contains("j'ai joué à") || lower.contains("jai joue a")) {
            steamGameRegex.find(text)?.let { match ->
                val gameName = match.groupValues[1].trim()
                if (gameName.isNotBlank() && !gameName.lowercase().contains("quel")) {
                    upsertUserFact("last_steam_game", "Dernier jeu Steam", gameName)
                    upsertSouvenir(
                        type = "PREFERENCE",
                        subjectKey = "last_steam_game",
                        title = "Dernier jeu joué sur Steam",
                        detailValue = gameName,
                        searchTags = "steam,jeu,game,joue,recent,${gameName.lowercase()}"
                    )
                    return "🎮 Mémorisé : Dernier jeu Steam = $gameName"
                }
            }
        }

        // 1. Spontaneous age statement: "j'ai 22 ans", "jai 22 ans", "mon age est 22 ans"
        val ageRegex = Regex("(?:j'ai|jai|j ai|mon âge est|mon age est)\\s+(\\d{1,3})\\s*(?:ans)?", RegexOption.IGNORE_CASE)
        ageRegex.find(text)?.let { match ->
            val age = "${match.groupValues[1]} ans"
            upsertUserFact("age", "Âge", age)
            upsertSouvenir(
                type = "FAIT",
                subjectKey = "age",
                title = "Âge de l'utilisateur",
                detailValue = age,
                searchTags = "age,ans,anniversaire,naissance,$age"
            )
            return "🧠 Mémorisé : Âge = $age"
        }

        // 2. Spontaneous birthday / event statement: "mon anniversaire est le ...", "j'ai un rdv ..."
        val birthdayRegex = Regex("(?:mon anniversaire est le|je suis né le|je suis ne le|anniversaire le)\\s+(.+)", RegexOption.IGNORE_CASE)
        birthdayRegex.find(text)?.let { match ->
            val bday = match.groupValues[1].trim()
            upsertUserFact("anniversaire", "Anniversaire", bday)
            upsertSouvenir(
                type = "EVENEMENT",
                subjectKey = "anniversaire",
                title = "Date d'anniversaire",
                detailValue = bday,
                searchTags = "anniversaire,date,naissance,fete,age",
                eventDateOrTime = bday
            )
            return "🎂 Mémorisé : Anniversaire = $bday"
        }

        val eventRegex = Regex("(?:j'ai un rendez-vous|jai un rdv|j'ai un rdv|rappelle-moi que|événement|evenement)\\s+(.+)", RegexOption.IGNORE_CASE)
        eventRegex.find(text)?.let { match ->
            val ev = match.groupValues[1].trim()
            upsertSouvenir(
                type = "EVENEMENT",
                subjectKey = "event_${System.currentTimeMillis() % 10000}",
                title = "Événement / Rendez-vous",
                detailValue = ev,
                searchTags = "evenement,rdv,rendez-vous,agenda,rappel",
                eventDateOrTime = currentTimeFormatted()
            )
            return "📅 Mémorisé : « $ev »"
        }

        // 3. Spontaneous preference statement: "j'aime ...", "j'adore ...", "je préfère ..."
        val prefRegex = Regex("(?:j'adore|jadore|j'aime beaucoup|je préfère|je prefere|ma préférence est)\\s+(.+)", RegexOption.IGNORE_CASE)
        prefRegex.find(text)?.let { match ->
            val pref = match.groupValues[1].trim()
            val key = "pref_${System.currentTimeMillis() % 10000}"
            upsertSouvenir(
                type = "PREFERENCE",
                subjectKey = key,
                title = "Préférence personnelle",
                detailValue = pref,
                searchTags = "preference,aime,adore,gout,favori,$pref"
            )
            upsertLongTermMemory(
                category = "PREFERENCE",
                memoryKey = key,
                title = "Préférence : ${pref.take(24)}",
                contentValue = pref
            )
            return "💜 Préférence mémorisée : « $pref »"
        }

        // 4. Spontaneous name statement: "je m'appelle X", "mon prénom est X"
        val nameRegex = Regex("(?:je m'appelle|je m appelle|mon prénom est|mon prenom est|appelle-moi)\\s+([A-Za-zÀ-ÿ\\-]+)", RegexOption.IGNORE_CASE)
        nameRegex.find(text)?.let { match ->
            val name = match.groupValues[1].trim()
            upsertUserFact("prenom", "Prénom", name)
            return "🧠 Mémorisé : Prénom = $name"
        }

        // 5. Spontaneous phone number for Maman / Papa / someone
        val phoneRegex = Regex("(?:numéro de|numero de|tel de|téléphone de)\\s+([a-zA-ZÀ-ÿ]+)\\s*(?:est|:)?\\s*([+0-9\\s.]{8,16})", RegexOption.IGNORE_CASE)
        phoneRegex.find(text)?.let { match ->
            val who = match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            val num = match.groupValues[2].trim()
            upsertUserFact("${who.lowercase()}_tel", "Numéro $who", num)
            return "📞 Contact mémorisé : $who ($num)"
        }

        // 6. Spontaneous city statement: "j'habite à X", "je vis à X"
        val cityRegex = Regex("(?:j'habite à|j habite a|je vis à|je vis a|ma ville est)\\s+([A-Za-zÀ-ÿ\\-\\s]{2,25})", RegexOption.IGNORE_CASE)
        cityRegex.find(text)?.let { match ->
            val city = match.groupValues[1].trim()
            upsertUserFact("ville", "Ville", city)
            return "📍 Mémorisé : Ville = $city"
        }

        // 7. Explicit "retiens que ..." / "souviens-toi que ..."
        val rememberRegex = Regex("(?:retiens que|souviens-toi que|souviens toi que|mémorise que|memorise que|note que)\\s+(.+)", RegexOption.IGNORE_CASE)
        rememberRegex.find(text)?.let { match ->
            val info = match.groupValues[1].trim()
            val key = "souv_${System.currentTimeMillis() % 10000}"
            upsertSouvenir(
                type = "FAIT",
                subjectKey = key,
                title = "Souvenir mémorisé",
                detailValue = info,
                searchTags = "souvenir,note,important,${info.lowercase()}"
            )
            upsertUserFact(key, "Souvenir appris", info)
            return "🧠 Mémorisé : « $info »"
        }

        // 8. Passive Auto-Learning: Job / Studies ("je travaille chez/comme...", "je suis étudiant en...")
        val jobRegex = Regex("(?:je travaille chez|je travaille comme|mon métier est|mon metier est|je suis étudiant en|je suis etudiant en)\\s+([^.,?!]+)", RegexOption.IGNORE_CASE)
        jobRegex.find(text)?.let { match ->
            val jobVal = match.groupValues[1].trim()
            if (jobVal.length in 2..60) {
                upsertUserFact("metier", "Métier / Études", jobVal)
                upsertSouvenir(
                    type = "FAIT",
                    subjectKey = "metier",
                    title = "Métier / Études",
                    detailValue = jobVal,
                    searchTags = "metier,travail,job,profession,etudes,${jobVal.lowercase()}"
                )
                return "💭 Auto-appris : Métier/Études = $jobVal"
            }
        }

        // 9. Passive Auto-Learning: Family / Pet / Relationship ("mon frère/ma sœur/mon chien/mon chat s'appelle...")
        val familyRegex = Regex("(?:mon frère|mon frere|ma sœur|ma soeur|mon chien|mon chat|ma copine|mon copain|ma femme|mon mari)\\s+s'appelle\\s+([A-Za-zÀ-ÿ\\-]+)", RegexOption.IGNORE_CASE)
        familyRegex.find(text)?.let { match ->
            val relName = match.groupValues[1].trim()
            val key = "proche_${relName.lowercase()}"
            upsertSouvenir(
                type = "FAIT",
                subjectKey = key,
                title = "Proche / Entourage",
                detailValue = match.value.trim(),
                searchTags = "famille,proche,animal,$relName"
            )
            return "💭 Auto-appris : ${match.value.trim()}"
        }

        // 10. Passive Auto-Learning: Implicit schedule / appointment ("demain j'ai...", "ce soir je dois...", "lundi prochain...")
        val implicitEventRegex = Regex("(?:demain j'ai|demain jai|ce soir j'ai|ce soir je dois|la semaine prochaine j'ai)\\s+([^.,?!]+)", RegexOption.IGNORE_CASE)
        implicitEventRegex.find(text)?.let { match ->
            val evDetail = match.value.trim()
            if (evDetail.length in 6..90) {
                val key = "agenda_${System.currentTimeMillis() % 10000}"
                upsertSouvenir(
                    type = "EVENEMENT",
                    subjectKey = key,
                    title = "Agenda détecté automatiquement",
                    detailValue = evDetail,
                    searchTags = "agenda,demain,soir,rdv,planning",
                    eventDateOrTime = currentTimeFormatted()
                )
                return "💭 Auto-appris (Agenda) : « $evDetail »"
            }
        }

        // 11. Préférences spécifiques ("mon plat/film/sport/jeu/artiste/couleur/boisson/équipe préféré(e) est ...")
        val favThingRegex = Regex(
            "(?:mon|ma)\\s+(plat|film|série|serie|sport|jeu|artiste|chanteur|groupe|couleur|boisson|équipe|equipe|livre|application|app)\\s+préféré(?:e)?\\s+(?:est|c'est)\\s+([^.,?!]+)",
            RegexOption.IGNORE_CASE
        )
        favThingRegex.find(text)?.let { match ->
            val categoryName = match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            val favVal = match.groupValues[2].trim()
            if (favVal.length in 2..60) {
                val key = "fav_${categoryName.lowercase()}"
                upsertUserFact(key, "$categoryName préféré(e)", favVal)
                upsertSouvenir(
                    type = "PREFERENCE",
                    subjectKey = key,
                    title = "$categoryName préféré(e)",
                    detailValue = favVal,
                    searchTags = "prefere,favori,${categoryName.lowercase()},${favVal.lowercase()}"
                )
                return "💜 Préférence apprise : $categoryName = $favVal"
            }
        }

        // 12. Pseudo / Compte / E-mail / Adresse ("mon pseudo X est Y", "mon email est Y", "mon adresse est Y")
        val accountOrAddressRegex = Regex(
            "(?:mon\\s+pseudo(?:\\s+[a-zA-Z0-9]+)?|mon\\s+compte(?:\\s+[a-zA-Z0-9]+)?|mon\\s+(?:adresse\\s+)?e-?mail|mon\\s+adresse)\\s+(?:est|c'est|:)\\s+([^,?!]+)",
            RegexOption.IGNORE_CASE
        )
        accountOrAddressRegex.find(text)?.let { match ->
            val fullSubject = match.value.substringBefore("est").substringBefore("c'est").substringBefore(":").trim()
                .replaceFirstChar { it.uppercase() }
            val value = match.groupValues[1].trim()
            if (value.length in 2..75) {
                val key = fullSubject.lowercase().replace(Regex("[^a-z0-9à-ÿ]"), "_").take(26)
                upsertUserFact(key, fullSubject, value)
                upsertSouvenir(
                    type = "FAIT",
                    subjectKey = key,
                    title = fullSubject,
                    detailValue = value,
                    searchTags = "compte,pseudo,email,adresse,${value.lowercase()}"
                )
                return "🧠 Mémorisé : $fullSubject = $value"
            }
        }

        // 13. Habitudes quotidiennes & Objectifs ("tous les matins je ...", "chaque soir je ...", "mon objectif est ...")
        val habitOrGoalRegex = Regex(
            "(?:tous\\s+les\\s+(?:matins|soirs|jours)|chaque\\s+(?:matin|soir|jour)|mon\\s+objectif\\s+est(?:\\s+de)?|mon\\s+but\\s+est(?:\\s+de)?)\\s+([^.?!]+)",
            RegexOption.IGNORE_CASE
        )
        habitOrGoalRegex.find(text)?.let { match ->
            val fullHabit = match.value.trim()
            if (fullHabit.length in 8..95 && !lower.contains("?")) {
                val key = "habit_${System.currentTimeMillis() % 10000}"
                upsertSouvenir(
                    type = "PREFERENCE",
                    subjectKey = key,
                    title = "Habitude / Objectif personnel",
                    detailValue = fullHabit,
                    searchTags = "habitude,objectif,routine,matin,soir,${fullHabit.lowercase()}"
                )
                return "💭 Auto-appris (Habitude/Objectif) : « $fullHabit »"
            }
        }

        // 14. Allergies / Aversions ("je suis allergique à ...", "je déteste ...", "je n'aime pas ...")
        val dislikeRegex = Regex(
            "(?:je\\s+suis\\s+allergique\\s+(?:à|a|aux)|je\\s+déteste|je\\s+deteste|je\\s+n'aime\\s+pas)\\s+([^.,?!]+)",
            RegexOption.IGNORE_CASE
        )
        dislikeRegex.find(text)?.let { match ->
            val itemVal = match.groupValues[1].trim()
            if (itemVal.length in 2..60 && !lower.contains("?")) {
                val isAllergy = lower.contains("allergique")
                val label = if (isAllergy) "Allergie importante" else "À éviter / N'aime pas"
                val key = if (isAllergy) "allergie_${itemVal.lowercase().take(15)}" else "dislike_${itemVal.lowercase().take(15)}"
                upsertUserFact(key, label, itemVal)
                upsertSouvenir(
                    type = "FAIT",
                    subjectKey = key,
                    title = label,
                    detailValue = itemVal,
                    searchTags = "allergie,eviter,deteste,${itemVal.lowercase()}",
                    importanceScore = 1.2f
                )
                return "🧠 Mémorisé ($label) : $itemVal"
            }
        }

        return null
    }

    fun buildUserFactsContextPrompt(): String {
        val snap = _state.value
        return buildString {
            appendLine("=== TABLE ROOM `lia_souvenirs` (FAITS, PRÉFÉRENCES & ÉVÉNEMENTS DE L'UTILISATEUR) ===")
            snap.souvenirs.forEach { souv ->
                val datePart = if (souv.eventDateOrTime.isNotBlank()) " [${souv.eventDateOrTime}]" else ""
                appendLine("- [${souv.type}] ${souv.title} (${souv.subjectKey})$datePart : ${souv.detailValue}")
            }
            appendLine("=== CONTACTS & PROFIL UTILISATEUR ===")
            snap.userFacts.forEach { fact ->
                appendLine("- ${fact.label} (${fact.key}) : ${fact.value}")
            }
            appendLine("=== APPRENTISSAGE PAR RENFORCEMENT (ACTIONS RAPIDES QUOTIDIENNES) ===")
            snap.recommendedQuickActions.take(6).forEach { qa ->
                appendLine("- ${qa.emoji} ${qa.label} -> ${qa.targetPackageOrScreen} (Score RL=${qa.confidencePercent}%, fréquence=${qa.dailyFrequency}x/j)")
            }
        }
    }

    // --- Room Entity Mappers ---

    private fun LiaChatMessage.toRoomEntity(threadId: String, createdAtMs: Long): LiaMessageEntity {
        return LiaMessageEntity(
            id = id,
            threadId = threadId,
            role = role,
            text = text,
            timestamp = timestamp,
            createdAtEpochMs = createdAtMs,
            source = source,
            isPhoneAction = isPhoneAction,
            learnedNote = learnedNote,
            hasSmsDraft = smsDraft != null,
            smsRecipientName = smsDraft?.recipientName.orEmpty(),
            smsPhoneNumber = smsDraft?.phoneNumber.orEmpty(),
            smsMessageBody = smsDraft?.messageBody.orEmpty(),
            smsIsSent = smsDraft?.isSent ?: false,
            smsIsContactVerified = smsDraft?.isContactVerified ?: false,
            smsContactVerificationStatus = smsDraft?.contactVerificationStatus.orEmpty(),
            smsFuzzyMatchScore = smsDraft?.fuzzyMatchScore ?: 100,
            hasAppLaunchAction = appLaunchAction != null,
            appLabel = appLaunchAction?.appLabel.orEmpty(),
            appPackageName = appLaunchAction?.packageName.orEmpty(),
            appTargetScreenId = appLaunchAction?.targetScreenId.orEmpty(),
            appLearnedSpeedMs = appLaunchAction?.learnedSpeedMs ?: 95L
        )
    }

    private fun LiaMessageEntity.toDomainModel(): LiaChatMessage {
        val sms = if (hasSmsDraft) {
            LiaSmsDraft(
                recipientName = smsRecipientName,
                phoneNumber = smsPhoneNumber,
                messageBody = smsMessageBody,
                isSent = smsIsSent,
                isContactVerified = smsIsContactVerified,
                contactVerificationStatus = smsContactVerificationStatus.ifBlank {
                    if (smsIsContactVerified) "✓ Safe Contact Resolver ($smsFuzzyMatchScore%) : $smsRecipientName ↔ $smsPhoneNumber"
                    else "⚠️ Vérifiez le numéro de $smsRecipientName avant envoi"
                },
                fuzzyMatchScore = smsFuzzyMatchScore
            )
        } else null

        val appAction = if (hasAppLaunchAction) {
            LiaAppLaunchAction(
                appLabel = appLabel,
                packageName = appPackageName,
                targetScreenId = appTargetScreenId,
                learnedSpeedMs = appLearnedSpeedMs
            )
        } else null

        return LiaChatMessage(
            id = id,
            role = role,
            text = text,
            timestamp = timestamp,
            source = source,
            smsDraft = sms,
            appLaunchAction = appAction,
            isPhoneAction = isPhoneAction,
            learnedNote = learnedNote
        )
    }

    private fun LiaUserFact.toRoomEntity(): LiaUserFactEntity =
        LiaUserFactEntity(
            key = key,
            label = label,
            value = value,
            updatedAt = updatedAt,
            updatedAtEpochMs = System.currentTimeMillis()
        )

    private fun LiaUserFactEntity.toDomainModel(): LiaUserFact =
        LiaUserFact(
            key = key,
            label = label,
            value = value,
            updatedAt = updatedAt
        )

    private fun LiaLearnedShortcut.toRoomEntity(): LiaShortcutEntity =
        LiaShortcutEntity(
            id = id,
            triggerPhrase = triggerPhrase,
            actionTitle = actionTitle,
            targetPackageOrScreen = targetPackageOrScreen,
            stepsSaved = stepsSaved,
            avgLatencyMs = avgLatencyMs,
            usesCount = usesCount,
            qValueScore = qValueScore,
            preferredTimeBucket = preferredTimeBucket
        )

    private fun LiaShortcutEntity.toDomainModel(): LiaLearnedShortcut =
        LiaLearnedShortcut(
            id = id,
            triggerPhrase = triggerPhrase,
            actionTitle = actionTitle,
            targetPackageOrScreen = targetPackageOrScreen,
            stepsSaved = stepsSaved,
            avgLatencyMs = avgLatencyMs,
            usesCount = usesCount,
            qValueScore = qValueScore,
            preferredTimeBucket = preferredTimeBucket
        )

    private fun LiaLongTermMemoryItem.toRoomEntity(): LongTermMemoryEntity =
        LongTermMemoryEntity(
            id = id,
            category = category,
            memoryKey = memoryKey,
            title = title,
            contentValue = contentValue,
            targetActionOrPackage = targetActionOrPackage,
            confidenceScore = confidenceScore,
            executionSpeedupMs = executionSpeedupMs,
            accessFrequency = accessFrequency,
            lastAccessedAt = lastAccessedAt,
            updatedAtEpochMs = System.currentTimeMillis()
        )

    private fun LongTermMemoryEntity.toDomainModel(): LiaLongTermMemoryItem =
        LiaLongTermMemoryItem(
            id = id,
            category = category,
            memoryKey = memoryKey,
            title = title,
            contentValue = contentValue,
            targetActionOrPackage = targetActionOrPackage,
            confidenceScore = confidenceScore,
            executionSpeedupMs = executionSpeedupMs,
            accessFrequency = accessFrequency,
            lastAccessedAt = lastAccessedAt
        )

    private fun LiaSouvenirItem.toRoomEntity(): LiaSouvenirEntity {
        val computedChunk = chunkText.ifBlank {
            LiaSouvenirRagEngine.buildChunkText(type, subjectKey, title, detailValue, searchTags, eventDateOrTime)
        }
        val computedEmb = embeddingVectorCsv.ifBlank {
            LiaSouvenirRagEngine.serializeEmbeddingCsv(LiaSouvenirRagEngine.computeLocalEmbedding(computedChunk))
        }
        return LiaSouvenirEntity(
            id = id,
            type = type,
            subjectKey = subjectKey,
            title = title,
            detailValue = detailValue,
            searchTags = searchTags,
            eventDateOrTime = eventDateOrTime,
            importanceScore = importanceScore,
            embeddingVectorCsv = computedEmb,
            chunkText = computedChunk,
            createdAt = createdAt,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    private fun LiaSouvenirEntity.toDomainModel(): LiaSouvenirItem =
        LiaSouvenirItem(
            id = id,
            type = type,
            subjectKey = subjectKey,
            title = title,
            detailValue = detailValue,
            searchTags = searchTags,
            eventDateOrTime = eventDateOrTime,
            importanceScore = importanceScore,
            createdAt = createdAt,
            embeddingVectorCsv = embeddingVectorCsv,
            chunkText = chunkText
        )

    private fun LiaAppUsageEntity.toDomainModel(): LiaAppUsageHabitItem =
        LiaAppUsageHabitItem(
            packageName = packageName,
            appLabel = appLabel,
            launchCount = launchCount,
            morningCount = morningCount,
            afternoonCount = afternoonCount,
            eveningCount = eveningCount,
            lastTimeFormatted = lastTimeFormatted,
            qValueHabitScore = qValueHabitScore
        )

    private fun serializeThreadsJson(threads: List<LiaConversationThread>): String {
        val arr = JSONArray()
        threads.take(25).forEach { t ->
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("title", t.title)
            obj.put("updatedAt", t.updatedAt)
            val msgsArr = JSONArray()
            t.messages.takeLast(60).forEach { m ->
                val mObj = JSONObject()
                mObj.put("id", m.id)
                mObj.put("role", m.role)
                mObj.put("text", m.text)
                mObj.put("timestamp", m.timestamp)
                mObj.put("source", m.source)
                mObj.put("isPhoneAction", m.isPhoneAction)
                if (m.learnedNote != null) mObj.put("learnedNote", m.learnedNote)
                if (m.smsDraft != null) {
                    mObj.put("smsDraft", JSONObject().apply {
                        put("recipientName", m.smsDraft.recipientName)
                        put("phoneNumber", m.smsDraft.phoneNumber)
                        put("messageBody", m.smsDraft.messageBody)
                        put("isSent", m.smsDraft.isSent)
                        put("isContactVerified", m.smsDraft.isContactVerified)
                        put("contactVerificationStatus", m.smsDraft.contactVerificationStatus)
                        put("fuzzyMatchScore", m.smsDraft.fuzzyMatchScore)
                    })
                }
                if (m.appLaunchAction != null) {
                    mObj.put("appLaunchAction", JSONObject().apply {
                        put("appLabel", m.appLaunchAction.appLabel)
                        put("packageName", m.appLaunchAction.packageName)
                        put("targetScreenId", m.appLaunchAction.targetScreenId)
                        put("learnedSpeedMs", m.appLaunchAction.learnedSpeedMs)
                    })
                }
                if (m.quickReplies.isNotEmpty()) {
                    val qrArr = JSONArray()
                    m.quickReplies.forEach { qrArr.put(it) }
                    mObj.put("quickReplies", qrArr)
                }
                msgsArr.put(mObj)
            }
            obj.put("messages", msgsArr)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseThreadsJson(raw: String): List<LiaConversationThread> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val msgsArr = obj.optJSONArray("messages") ?: JSONArray()
                val msgs = buildList {
                    for (j in 0 until msgsArr.length()) {
                        val m = msgsArr.getJSONObject(j)
                        val smsObj = m.optJSONObject("smsDraft")
                        val sms = smsObj?.let {
                            LiaSmsDraft(
                                recipientName = it.optString("recipientName", "Contact"),
                                phoneNumber = it.optString("phoneNumber", ""),
                                messageBody = it.optString("messageBody", ""),
                                isSent = it.optBoolean("isSent", false),
                                isContactVerified = it.optBoolean("isContactVerified", true),
                                contactVerificationStatus = it.optString("contactVerificationStatus", "✓ Contact vérifié"),
                                fuzzyMatchScore = it.optInt("fuzzyMatchScore", 100)
                            )
                        }
                        val appObj = m.optJSONObject("appLaunchAction")
                        val appLaunch = appObj?.let {
                            LiaAppLaunchAction(
                                appLabel = it.optString("appLabel", "Application"),
                                packageName = it.optString("packageName", ""),
                                targetScreenId = it.optString("targetScreenId", ""),
                                learnedSpeedMs = it.optLong("learnedSpeedMs", 90L)
                            )
                        }
                        val qrJson = m.optJSONArray("quickReplies")
                        val parsedQuickReplies = buildList {
                            if (qrJson != null) {
                                for (k in 0 until qrJson.length()) {
                                    val item = qrJson.optString(k).trim()
                                    if (item.isNotBlank()) add(item)
                                }
                            }
                        }
                        add(
                            LiaChatMessage(
                                id = m.optString("id", "msg_${i}_$j"),
                                role = m.optString("role", "lia"),
                                text = m.optString("text", ""),
                                timestamp = m.optString("timestamp", ""),
                                source = m.optString("source", "app"),
                                smsDraft = sms,
                                appLaunchAction = appLaunch,
                                isPhoneAction = m.optBoolean("isPhoneAction", false),
                                learnedNote = m.optString("learnedNote", "").ifBlank { null },
                                quickReplies = parsedQuickReplies
                            )
                        )
                    }
                }
                add(
                    LiaConversationThread(
                        id = obj.optString("id", "thread_$i"),
                        title = obj.optString("title", "Discussion"),
                        updatedAt = obj.optString("updatedAt", ""),
                        messages = msgs
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun serializeFactsJson(facts: List<LiaUserFact>): String {
        val arr = JSONArray()
        facts.forEach { f ->
            arr.put(JSONObject().apply {
                put("key", f.key)
                put("label", f.label)
                put("value", f.value)
                put("updatedAt", f.updatedAt)
            })
        }
        return arr.toString()
    }

    private fun parseFactsJson(raw: String): List<LiaUserFact> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    LiaUserFact(
                        key = o.optString("key", "k_$i"),
                        label = o.optString("label", "Info"),
                        value = o.optString("value", ""),
                        updatedAt = o.optString("updatedAt", "")
                    )
                )
            }
        }
    }.getOrDefault(defaultUserFacts())

    private fun serializeShortcutsJson(shortcuts: List<LiaLearnedShortcut>): String {
        val arr = JSONArray()
        shortcuts.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("triggerPhrase", s.triggerPhrase)
                put("actionTitle", s.actionTitle)
                put("targetPackageOrScreen", s.targetPackageOrScreen)
                put("stepsSaved", s.stepsSaved)
                put("avgLatencyMs", s.avgLatencyMs)
                put("usesCount", s.usesCount)
                put("qValueScore", s.qValueScore.toDouble())
                put("preferredTimeBucket", s.preferredTimeBucket)
            })
        }
        return arr.toString()
    }

    private fun parseShortcutsJson(raw: String): List<LiaLearnedShortcut> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    LiaLearnedShortcut(
                        id = o.optString("id", "sc_$i"),
                        triggerPhrase = o.optString("triggerPhrase", ""),
                        actionTitle = o.optString("actionTitle", ""),
                        targetPackageOrScreen = o.optString("targetPackageOrScreen", ""),
                        stepsSaved = o.optInt("stepsSaved", 2),
                        avgLatencyMs = o.optLong("avgLatencyMs", 80L),
                        usesCount = o.optInt("usesCount", 1),
                        qValueScore = o.optDouble("qValueScore", 0.85).toFloat(),
                        preferredTimeBucket = o.optString("preferredTimeBucket", "TOUJOURS")
                    )
                )
            }
        }
    }.getOrDefault(defaultLearnedShortcuts())
}
