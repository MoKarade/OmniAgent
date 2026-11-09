package com.example.service

import android.app.AppOpsManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Process
import android.provider.Settings
import com.example.data.LiaMemoryAndHistoryStore
import com.example.data.LiaReinforcementLearningEngine
import com.example.data.room.LiaAppUsageEntity
import com.example.data.room.LiaRoomDatabase
import com.example.data.room.LiaRoomRepository
import com.example.model.currentTimeFormatted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class InstalledAppHabitStatus(
    val isServiceRunning: Boolean = false,
    val hasSystemUsagePermission: Boolean = false,
    val trackedAppsCount: Int = 0,
    val lastSyncTimeFormatted: String = "En attente",
    val activeTimeBucket: String = LiaReinforcementLearningEngine.currentTimeBucket()
)

/**
 * Service d'arrière-plan (`LiaAppUsageTrackerService`) qui suit la fréquence d'ouverture
 * des applications installées selon les tranches horaires (`MATIN`, `APRES_MIDI`, `SOIR`)
 * et stocke ces données dans la table Room `lia_app_usage_frequency`.
 *
 * Ces fréquences alimentent le moteur d'apprentissage par renforcement (`LiaReinforcementLearningEngine`)
 * afin que LIA suggère dynamiquement des « Actions rapides » basées sur les habitudes quotidiennes
 * de l'utilisateur.
 */
class LiaAppUsageTrackerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private var lastProcessedEventTimeMs: Long = System.currentTimeMillis() - (60 * 60 * 1000L)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LiaMemoryAndHistoryStore.init(applicationContext)
        updateTrackerStatus(isRunning = true)
        startUsagePollingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SYNC_NOW -> {
                serviceScope.launch {
                    syncDeviceUsageStatsToRoom(applicationContext)
                }
            }
            ACTION_RECORD_APP_OPEN -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
                val label = intent.getStringExtra(EXTRA_APP_LABEL).orEmpty()
                if (pkg.isNotBlank()) {
                    serviceScope.launch {
                        recordAppOpenInRoom(applicationContext, pkg, label)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startUsagePollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                syncDeviceUsageStatsToRoom(applicationContext)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun syncDeviceUsageStatsToRoom(context: Context) {
        val hasPerm = hasUsageStatsPermission(context)
        val repo = LiaRoomRepository(LiaRoomDatabase.getInstance(context).liaDao())

        if (hasPerm) {
            runCatching {
                val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                if (usageManager != null) {
                    val now = System.currentTimeMillis()
                    val events = usageManager.queryEvents(lastProcessedEventTimeMs, now)
                    val event = UsageEvents.Event()
                    val launchCountsByPkg = mutableMapOf<String, Int>()

                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                            val pkg = event.packageName.orEmpty()
                            if (pkg.isNotBlank() && pkg != context.packageName && !isIgnoredSystemPackage(pkg)) {
                                launchCountsByPkg[pkg] = (launchCountsByPkg[pkg] ?: 0) + 1
                            }
                        }
                    }
                    lastProcessedEventTimeMs = now

                    for ((pkg, deltaLaunches) in launchCountsByPkg) {
                        val label = resolveAppLabel(context, pkg)
                        incrementPackageUsageInRoom(repo, pkg, label, deltaLaunches)
                    }
                }
            }
        }

        val count = repo.getAppUsageCount()
        _trackerStatus.value = InstalledAppHabitStatus(
            isServiceRunning = true,
            hasSystemUsagePermission = hasPerm,
            trackedAppsCount = count,
            lastSyncTimeFormatted = currentTimeFormatted(),
            activeTimeBucket = LiaReinforcementLearningEngine.currentTimeBucket()
        )
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        serviceScope.cancel()
        updateTrackerStatus(isRunning = false)
        super.onDestroy()
    }

    private fun updateTrackerStatus(isRunning: Boolean) {
        _trackerStatus.value = _trackerStatus.value.copy(
            isServiceRunning = isRunning,
            hasSystemUsagePermission = hasUsageStatsPermission(applicationContext),
            activeTimeBucket = LiaReinforcementLearningEngine.currentTimeBucket()
        )
    }

    companion object {
        private const val POLL_INTERVAL_MS = 20_000L
        const val ACTION_SYNC_NOW = "com.example.service.action.SYNC_USAGE_NOW"
        const val ACTION_RECORD_APP_OPEN = "com.example.service.action.RECORD_APP_OPEN"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_LABEL = "extra_app_label"

        private val _trackerStatus = MutableStateFlow(InstalledAppHabitStatus())
        val trackerStatus: StateFlow<InstalledAppHabitStatus> = _trackerStatus.asStateFlow()

        fun startTracking(context: Context) {
            runCatching {
                val appCtx = context.applicationContext
                val intent = Intent(appCtx, LiaAppUsageTrackerService::class.java).apply {
                    action = ACTION_SYNC_NOW
                }
                appCtx.startService(intent)
            }
        }

        fun recordAppOpenEvent(context: Context, packageName: String, appLabel: String = "") {
            if (packageName.isBlank()) return
            runCatching {
                val appCtx = context.applicationContext
                val intent = Intent(appCtx, LiaAppUsageTrackerService::class.java).apply {
                    action = ACTION_RECORD_APP_OPEN
                    putExtra(EXTRA_PACKAGE_NAME, packageName)
                    putExtra(EXTRA_APP_LABEL, appLabel)
                }
                appCtx.startService(intent)
            }
        }

        fun hasUsageStatsPermission(context: Context): Boolean {
            return runCatching {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
                mode == AppOpsManager.MODE_ALLOWED
            }.getOrDefault(false)
        }

        fun openUsageAccessSettings(context: Context) {
            runCatching {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }

        suspend fun recordAppOpenInRoom(
            context: Context,
            packageName: String,
            providedLabel: String = ""
        ) {
            val repo = LiaRoomRepository(LiaRoomDatabase.getInstance(context).liaDao())
            val label = providedLabel.ifBlank { resolveAppLabel(context, packageName) }
            incrementPackageUsageInRoom(repo, packageName, label, deltaLaunches = 1)
            val count = repo.getAppUsageCount()
            _trackerStatus.value = _trackerStatus.value.copy(
                trackedAppsCount = count,
                lastSyncTimeFormatted = currentTimeFormatted(),
                activeTimeBucket = LiaReinforcementLearningEngine.currentTimeBucket()
            )
        }

        private suspend fun incrementPackageUsageInRoom(
            repo: LiaRoomRepository,
            packageName: String,
            appLabel: String,
            deltaLaunches: Int
        ) {
            val bucket = LiaReinforcementLearningEngine.currentTimeBucket()
            val nowMs = System.currentTimeMillis()
            val nowFormatted = currentTimeFormatted()
            val existing = repo.getAppUsageByPackage(packageName)

            val updated = if (existing != null) {
                val nextTotal = existing.launchCount + deltaLaunches
                val nextMorning = existing.morningCount + if (bucket == "MATIN") deltaLaunches else 0
                val nextAfternoon = existing.afternoonCount + if (bucket == "APRES_MIDI") deltaLaunches else 0
                val nextEvening = existing.eveningCount + if (bucket == "SOIR") deltaLaunches else 0
                val nextQ = LiaReinforcementLearningEngine.computeUpdatedQValue(
                    currentQ = existing.qValueHabitScore,
                    usesCount = nextTotal,
                    reward = 1.12f
                )
                existing.copy(
                    appLabel = appLabel.ifBlank { existing.appLabel },
                    launchCount = nextTotal,
                    morningCount = nextMorning,
                    afternoonCount = nextAfternoon,
                    eveningCount = nextEvening,
                    lastLaunchedEpochMs = nowMs,
                    lastTimeFormatted = nowFormatted,
                    qValueHabitScore = nextQ
                )
            } else {
                val initialQ = LiaReinforcementLearningEngine.computeUpdatedQValue(0.84f, deltaLaunches, 1.1f)
                LiaAppUsageEntity(
                    packageName = packageName,
                    appLabel = appLabel.ifBlank { packageName.substringAfterLast('.') },
                    launchCount = deltaLaunches,
                    morningCount = if (bucket == "MATIN") deltaLaunches else 0,
                    afternoonCount = if (bucket == "APRES_MIDI") deltaLaunches else 0,
                    eveningCount = if (bucket == "SOIR") deltaLaunches else 0,
                    totalForegroundTimeMs = 0L,
                    lastLaunchedEpochMs = nowMs,
                    lastTimeFormatted = nowFormatted,
                    qValueHabitScore = initialQ
                )
            }

            repo.upsertAppUsage(updated)

            // Synchronize with LIA's learned shortcuts so "Actions rapides" updates immediately
            LiaMemoryAndHistoryStore.recordLearnedShortcut(
                triggerPhrase = "Ouvre ${updated.appLabel}",
                actionTitle = "Habitude quotidienne (${updated.launchCount}x • Créneau $bucket)",
                targetPackageOrScreen = updated.packageName,
                stepsSaved = 3,
                latencyMs = 55L,
                rewardSignal = 1.12f
            )
        }

        fun resolveAppLabel(context: Context, packageName: String): String {
            return runCatching {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(info).toString()
            }.getOrDefault(
                packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            )
        }

        private fun isIgnoredSystemPackage(pkg: String): Boolean {
            return pkg.startsWith("com.android.systemui") ||
                pkg.contains("inputmethod") ||
                pkg.contains("permissioncontroller")
        }

        /**
         * Retourne la liste des applications installées sur le téléphone ayant une activité Launcher.
         */
        fun getInstalledLauncherApps(context: Context): List<Pair<String, String>> {
            return runCatching {
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
                    .mapNotNull { resolveInfo ->
                        val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                        if (pkg == context.packageName) return@mapNotNull null
                        val label = resolveInfo.loadLabel(pm)?.toString().orEmpty().ifBlank { pkg }
                        pkg to label
                    }
                    .distinctBy { it.first }
                    .sortedBy { it.second.lowercase() }
            }.getOrDefault(emptyList())
        }
    }
}
