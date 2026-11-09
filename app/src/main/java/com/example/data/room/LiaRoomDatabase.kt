package com.example.data.room

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "lia_threads")
data class LiaThreadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val updatedAt: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "lia_messages",
    indices = [
        Index(value = ["threadId", "createdAtEpochMs"], name = "index_lia_messages_threadId_createdAtEpochMs")
    ]
)
data class LiaMessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val role: String, // "user" or "lia"
    val text: String,
    val timestamp: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val source: String = "app", // "app" or "bubble"
    val isPhoneAction: Boolean = false,
    val learnedNote: String? = null,
    // SMS Draft embedded columns
    val hasSmsDraft: Boolean = false,
    val smsRecipientName: String = "",
    val smsPhoneNumber: String = "",
    val smsMessageBody: String = "",
    val smsIsSent: Boolean = false,
    val smsIsContactVerified: Boolean = false,
    val smsContactVerificationStatus: String = "",
    val smsFuzzyMatchScore: Int = 100,
    // Fast App Launch embedded columns
    val hasAppLaunchAction: Boolean = false,
    val appLabel: String = "",
    val appPackageName: String = "",
    val appTargetScreenId: String = "",
    val appLearnedSpeedMs: Long = 95L
)

@Entity(tableName = "lia_user_facts")
data class LiaUserFactEntity(
    @PrimaryKey val key: String,
    val label: String,
    val value: String,
    val updatedAt: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "lia_learned_shortcuts")
data class LiaShortcutEntity(
    @PrimaryKey val id: String,
    val triggerPhrase: String,
    val actionTitle: String,
    val targetPackageOrScreen: String,
    val stepsSaved: Int,
    val avgLatencyMs: Long,
    val usesCount: Int,
    val qValueScore: Float = 0.85f,
    val preferredTimeBucket: String = "TOUJOURS"
)

/**
 * Dedicated Room table `LongTermMemory` for LIA to store:
 * - User preferences (`category = "PREFERENCE"`)
 * - Frequently accessed app shortcuts (`category = "APP_SHORTCUT"`)
 * - Custom interaction patterns & macros (`category = "INTERACTION_PATTERN"`)
 */
@Entity(
    tableName = "LongTermMemory",
    indices = [
        Index(value = ["category", "updatedAtEpochMs"], name = "index_LongTermMemory_category_updatedAtEpochMs")
    ]
)
data class LongTermMemoryEntity(
    @PrimaryKey val id: String,
    val category: String, // "PREFERENCE", "APP_SHORTCUT", "INTERACTION_PATTERN"
    val memoryKey: String,
    val title: String,
    val contentValue: String,
    val targetActionOrPackage: String = "",
    val confidenceScore: Float = 1.0f,
    val executionSpeedupMs: Long = 75L,
    val accessFrequency: Int = 1,
    val lastAccessedAt: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * Dedicated Room table `lia_souvenirs` for storing specific user memories ("souvenirs")
 * indexed for Retrieval-Augmented Generation (RAG):
 * - Facts (`type = "FAIT"`, e.g. age, birthday, family names, city, profession)
 * - Preferences (`type = "PREFERENCE"`, e.g. favorite drink, music, communication style)
 * - Events (`type = "EVENEMENT"`, e.g. upcoming appointments, birthdays, reminders)
 * Includes `embeddingVectorCsv` (normalized 64-dim vector) and `chunkText` (1000-char window)
 * so LIA can execute hybrid lexical + vector RAG before generating every response.
 */
@Entity(
    tableName = "lia_souvenirs",
    indices = [
        Index(value = ["subjectKey"], name = "index_lia_souvenirs_subjectKey"),
        Index(value = ["type", "updatedAtEpochMs"], name = "index_lia_souvenirs_type_updatedAtEpochMs")
    ]
)
data class LiaSouvenirEntity(
    @PrimaryKey val id: String,
    val type: String, // "FAIT", "PREFERENCE", "EVENEMENT"
    val subjectKey: String, // e.g. "age", "prenom", "anniversaire", "cafe", "rdv_medecin"
    val title: String,
    val detailValue: String,
    val searchTags: String = "",
    val eventDateOrTime: String = "",
    val importanceScore: Float = 1.0f,
    val embeddingVectorCsv: String = "",
    val chunkText: String = "",
    val createdAt: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * Dedicated Room table `lia_app_usage_frequency` populated by `LiaAppUsageTrackerService`
 * to track the launch frequency and time-of-day habits (`MATIN`, `APRES_MIDI`, `SOIR`)
 * of installed applications and surface dynamic "Actions rapides".
 */
@Entity(tableName = "lia_app_usage_frequency")
data class LiaAppUsageEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val launchCount: Int = 1,
    val morningCount: Int = 0,
    val afternoonCount: Int = 0,
    val eveningCount: Int = 0,
    val totalForegroundTimeMs: Long = 0L,
    val lastLaunchedEpochMs: Long = System.currentTimeMillis(),
    val lastTimeFormatted: String = "",
    val qValueHabitScore: Float = 0.85f
)

@Dao
interface LiaRoomDao {

    @Query("SELECT * FROM lia_threads ORDER BY updatedAtEpochMs DESC")
    fun getAllThreads(): Flow<List<LiaThreadEntity>>

    @Query("SELECT * FROM lia_messages ORDER BY createdAtEpochMs ASC")
    fun getAllMessages(): Flow<List<LiaMessageEntity>>

    @Query("SELECT * FROM lia_user_facts ORDER BY updatedAtEpochMs DESC")
    fun getAllUserFacts(): Flow<List<LiaUserFactEntity>>

    @Query("SELECT * FROM lia_learned_shortcuts ORDER BY qValueScore DESC, usesCount DESC")
    fun getAllShortcuts(): Flow<List<LiaShortcutEntity>>

    @Query("SELECT * FROM LongTermMemory ORDER BY accessFrequency DESC, updatedAtEpochMs DESC")
    fun getAllLongTermMemories(): Flow<List<LongTermMemoryEntity>>

    @Query("SELECT * FROM LongTermMemory WHERE category = :category ORDER BY accessFrequency DESC, updatedAtEpochMs DESC")
    fun getLongTermMemoriesByCategory(category: String): Flow<List<LongTermMemoryEntity>>

    @Query("SELECT * FROM lia_souvenirs ORDER BY importanceScore DESC, updatedAtEpochMs DESC")
    fun getAllSouvenirs(): Flow<List<LiaSouvenirEntity>>

    @Query("SELECT * FROM lia_souvenirs ORDER BY importanceScore DESC, updatedAtEpochMs DESC")
    suspend fun getAllSouvenirsSnapshot(): List<LiaSouvenirEntity>

    @Query(
        "SELECT * FROM lia_souvenirs WHERE " +
            "LOWER(subjectKey) LIKE '%' || LOWER(:query) || '%' OR " +
            "LOWER(title) LIKE '%' || LOWER(:query) || '%' OR " +
            "LOWER(detailValue) LIKE '%' || LOWER(:query) || '%' OR " +
            "LOWER(searchTags) LIKE '%' || LOWER(:query) || '%' OR " +
            "LOWER(chunkText) LIKE '%' || LOWER(:query) || '%' " +
            "ORDER BY importanceScore DESC, updatedAtEpochMs DESC"
    )
    suspend fun querySouvenirs(query: String): List<LiaSouvenirEntity>

    @Query("SELECT * FROM lia_app_usage_frequency ORDER BY qValueHabitScore DESC, launchCount DESC, lastLaunchedEpochMs DESC")
    fun getAllAppUsageFrequencies(): Flow<List<LiaAppUsageEntity>>

    @Query("SELECT * FROM lia_app_usage_frequency WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppUsageByPackage(packageName: String): LiaAppUsageEntity?

    @Query("SELECT COUNT(*) FROM lia_threads")
    suspend fun getThreadCount(): Int

    @Query("SELECT COUNT(*) FROM lia_user_facts")
    suspend fun getUserFactsCount(): Int

    @Query("SELECT COUNT(*) FROM lia_learned_shortcuts")
    suspend fun getShortcutsCount(): Int

    @Query("SELECT COUNT(*) FROM LongTermMemory")
    suspend fun getLongTermMemoryCount(): Int

    @Query("SELECT COUNT(*) FROM lia_souvenirs")
    suspend fun getSouvenirsCount(): Int

    @Query("SELECT COUNT(*) FROM lia_app_usage_frequency")
    suspend fun getAppUsageCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: LiaThreadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThreads(threads: List<LiaThreadEntity>)

    @Query("DELETE FROM lia_threads WHERE id = :threadId")
    suspend fun deleteThreadById(threadId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: LiaMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<LiaMessageEntity>)

    @Query("DELETE FROM lia_messages WHERE threadId = :threadId")
    suspend fun deleteMessagesForThread(threadId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserFact(fact: LiaUserFactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserFacts(facts: List<LiaUserFactEntity>)

    @Query("DELETE FROM lia_user_facts WHERE `key` = :key")
    suspend fun deleteUserFactByKey(key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: LiaShortcutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcuts(shortcuts: List<LiaShortcutEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLongTermMemory(entry: LongTermMemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLongTermMemories(entries: List<LongTermMemoryEntity>)

    @Query("DELETE FROM LongTermMemory WHERE id = :id")
    suspend fun deleteLongTermMemoryById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSouvenir(souvenir: LiaSouvenirEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSouvenirs(souvenirs: List<LiaSouvenirEntity>)

    @Query("DELETE FROM lia_souvenirs WHERE id = :id")
    suspend fun deleteSouvenirById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppUsage(usage: LiaAppUsageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppUsages(usages: List<LiaAppUsageEntity>)

    @Query("DELETE FROM lia_app_usage_frequency WHERE packageName = :packageName")
    suspend fun deleteAppUsageByPackage(packageName: String)
}

@Database(
    entities = [
        LiaThreadEntity::class,
        LiaMessageEntity::class,
        LiaUserFactEntity::class,
        LiaShortcutEntity::class,
        LongTermMemoryEntity::class,
        LiaSouvenirEntity::class,
        LiaAppUsageEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class LiaRoomDatabase : RoomDatabase() {
    abstract fun liaDao(): LiaRoomDao

    companion object {
        @Volatile
        private var INSTANCE: LiaRoomDatabase? = null

        /**
         * Non-destructive migration from Room v5 to v6 adding SQLite performance indices
         * on `lia_messages`, `LongTermMemory`, and `lia_souvenirs` (`PERF-04`).
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_lia_messages_threadId_createdAtEpochMs` " +
                        "ON `lia_messages` (`threadId`, `createdAtEpochMs`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_LongTermMemory_category_updatedAtEpochMs` " +
                        "ON `LongTermMemory` (`category`, `updatedAtEpochMs`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_lia_souvenirs_subjectKey` " +
                        "ON `lia_souvenirs` (`subjectKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_lia_souvenirs_type_updatedAtEpochMs` " +
                        "ON `lia_souvenirs` (`type`, `updatedAtEpochMs`)"
                )
            }
        }

        fun getInstance(context: Context): LiaRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LiaRoomDatabase::class.java,
                    "lia_clean_database_v5.db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

/**
 * Repository Pattern abstracting Room DAO access for LIA's conversation history,
 * messages, personal database (contacts/facts), RL-ranked app shortcuts, `LongTermMemory`,
 * `lia_souvenirs` RAG table, and `lia_app_usage_frequency` background tracker table.
 */
class LiaRoomRepository(private val dao: LiaRoomDao) {

    val allThreads: Flow<List<LiaThreadEntity>> = dao.getAllThreads()
    val allMessages: Flow<List<LiaMessageEntity>> = dao.getAllMessages()
    val allUserFacts: Flow<List<LiaUserFactEntity>> = dao.getAllUserFacts()
    val allShortcuts: Flow<List<LiaShortcutEntity>> = dao.getAllShortcuts()
    val allLongTermMemories: Flow<List<LongTermMemoryEntity>> = dao.getAllLongTermMemories()
    val allSouvenirs: Flow<List<LiaSouvenirEntity>> = dao.getAllSouvenirs()
    val allAppUsageFrequencies: Flow<List<LiaAppUsageEntity>> = dao.getAllAppUsageFrequencies()

    suspend fun getThreadCount(): Int = dao.getThreadCount()
    suspend fun getUserFactsCount(): Int = dao.getUserFactsCount()
    suspend fun getShortcutsCount(): Int = dao.getShortcutsCount()
    suspend fun getLongTermMemoryCount(): Int = dao.getLongTermMemoryCount()
    suspend fun getSouvenirsCount(): Int = dao.getSouvenirsCount()
    suspend fun getAppUsageCount(): Int = dao.getAppUsageCount()

    suspend fun upsertThread(thread: LiaThreadEntity) = dao.insertThread(thread)
    suspend fun upsertThreads(threads: List<LiaThreadEntity>) = dao.insertThreads(threads)
    suspend fun deleteThread(threadId: String) {
        dao.deleteMessagesForThread(threadId)
        dao.deleteThreadById(threadId)
    }

    suspend fun upsertMessage(message: LiaMessageEntity) = dao.insertMessage(message)
    suspend fun upsertMessages(messages: List<LiaMessageEntity>) = dao.insertMessages(messages)
    suspend fun clearMessagesInThread(threadId: String) = dao.deleteMessagesForThread(threadId)

    suspend fun upsertUserFact(fact: LiaUserFactEntity) = dao.insertUserFact(fact)
    suspend fun upsertUserFacts(facts: List<LiaUserFactEntity>) = dao.insertUserFacts(facts)
    suspend fun deleteUserFact(key: String) = dao.deleteUserFactByKey(key)

    suspend fun upsertShortcut(shortcut: LiaShortcutEntity) = dao.insertShortcut(shortcut)
    suspend fun upsertShortcuts(shortcuts: List<LiaShortcutEntity>) = dao.insertShortcuts(shortcuts)

    suspend fun upsertLongTermMemory(entry: LongTermMemoryEntity) = dao.insertLongTermMemory(entry)
    suspend fun upsertLongTermMemories(entries: List<LongTermMemoryEntity>) = dao.insertLongTermMemories(entries)
    suspend fun deleteLongTermMemory(id: String) = dao.deleteLongTermMemoryById(id)

    suspend fun upsertSouvenir(souvenir: LiaSouvenirEntity) = dao.insertSouvenir(souvenir)
    suspend fun upsertSouvenirs(souvenirs: List<LiaSouvenirEntity>) = dao.insertSouvenirs(souvenirs)
    suspend fun querySouvenirs(query: String): List<LiaSouvenirEntity> = dao.querySouvenirs(query)
    suspend fun getAllSouvenirsSnapshot(): List<LiaSouvenirEntity> = dao.getAllSouvenirsSnapshot()
    suspend fun deleteSouvenir(id: String) = dao.deleteSouvenirById(id)

    suspend fun getAppUsageByPackage(packageName: String): LiaAppUsageEntity? =
        dao.getAppUsageByPackage(packageName)
    suspend fun upsertAppUsage(usage: LiaAppUsageEntity) = dao.insertAppUsage(usage)
    suspend fun upsertAppUsages(usages: List<LiaAppUsageEntity>) = dao.insertAppUsages(usages)
    suspend fun deleteAppUsage(packageName: String) = dao.deleteAppUsageByPackage(packageName)
}
