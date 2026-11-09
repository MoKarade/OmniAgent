package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.example.model.currentTimeFormatted
import com.example.security.SafeContactResolver
import com.example.service.LiaAppUsageTrackerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class GoogleWorkspaceIntegrationSpec(
    val id: String,
    val title: String,
    val emoji: String,
    val packageName: String,
    val fallbackWebUrl: String,
    val oauthScope: String,
    val capabilitiesDescription: String,
    val sampleVoiceCommand: String
)

data class InstalledPhoneAppItem(
    val packageName: String,
    val appLabel: String,
    val emoji: String,
    val isGoogleApp: Boolean,
    val isNewlyInstalledAfterBoot: Boolean = false,
    val discoveredAt: String = ""
)

data class UniversalHubState(
    val oauthProjectId: String = "gen-lang-client-0311284453",
    val oauthBrandName: String = "Marc Richard's Apps",
    val enabledOAuthScopes: List<String> = listOf(
        "https://www.googleapis.com/auth/gmail.readonly",
        "https://www.googleapis.com/auth/gmail.send",
        "https://www.googleapis.com/auth/calendar",
        "https://www.googleapis.com/auth/drive.readonly",
        "https://www.googleapis.com/auth/contacts.readonly",
        "https://www.googleapis.com/auth/tasks",
        "https://www.googleapis.com/auth/documents.readonly",
        "https://www.googleapis.com/auth/spreadsheets.readonly"
    ),
    val googleAppsCatalog: List<GoogleWorkspaceIntegrationSpec> = emptyList(),
    val installedPhoneApps: List<InstalledPhoneAppItem> = emptyList(),
    val newlyInstalledPackagesLog: List<String> = emptyList(),
    val lastSyncTimestamp: String = ""
)

/**
 * Hub Universel LIA :
 * 1. Intégration native avec tout l'écosystème Google (Gmail, Google Agenda, Drive, Contacts, Tasks, Docs, Sheets, Maps, YouTube, Photos, Keep, Chrome)
 *    via OAuth (`gen-lang-client-0311284453`), Intents Android natifs (`CalendarContract`, `AlarmClock`, `mailto:`, `geo:`) et Deep-Links.
 * 2. Découverte dynamique de TOUTES les applications installées sur le téléphone via `PackageManager`
 *    + écoute en temps réel des nouvelles installations (`BroadcastReceiver` sur `ACTION_PACKAGE_ADDED` / `ACTION_PACKAGE_REPLACED` / `ACTION_PACKAGE_REMOVED`),
 *    permettant à LIA de piloter immédiatement toute nouvelle application ajoutée par l'utilisateur après coup.
 */
object LiaUniversalAppsAndGoogleHub {

    @Volatile
    private var receiverRegistered = false

    private val GOOGLE_ECOSYSTEM_SPECS = listOf(
        GoogleWorkspaceIntegrationSpec(
            id = "gmail",
            title = "Gmail",
            emoji = "📧",
            packageName = "com.google.android.gm",
            fallbackWebUrl = "https://mail.google.com",
            oauthScope = "gmail.readonly & gmail.send",
            capabilitiesDescription = "Rédaction d'e-mails pré-remplis, lecture & recherche Gmail",
            sampleVoiceCommand = "Envoie un mail sur Gmail : Réunion demain à 10h"
        ),
        GoogleWorkspaceIntegrationSpec(
            id = "calendar",
            title = "Google Agenda",
            emoji = "📅",
            packageName = "com.google.android.calendar",
            fallbackWebUrl = "https://calendar.google.com",
            oauthScope = "auth/calendar",
            capabilitiesDescription = "Création d'événements dans l'agenda & synchronisation",
            sampleVoiceCommand = "Ajoute à Google Agenda : Dentiste demain à 14h"
        ),
        GoogleWorkspaceIntegrationSpec(
            id = "drive",
            title = "Google Drive",
            emoji = "📁",
            packageName = "com.google.android.apps.docs",
            fallbackWebUrl = "https://drive.google.com",
            oauthScope = "drive.readonly",
            capabilitiesDescription = "Accès rapide aux fichiers & dossiers Cloud Drive",
            sampleVoiceCommand = "Ouvre Google Drive"
        ),
        GoogleWorkspaceIntegrationSpec(
            id = "docs",
            title = "Google Docs",
            emoji = "📄",
            packageName = "com.google.android.apps.docs.editors.docs",
            fallbackWebUrl = "https://docs.google.com",
            oauthScope = "documents.readonly",
            capabilitiesDescription = "Ouverture et édition de documents Google Docs",
            sampleVoiceCommand = "Ouvre Google Docs"
        ),
        GoogleWorkspaceIntegrationSpec(
            id = "sheets",
            title = "Google Sheets",
            emoji = "📊",
            packageName = "com.google.android.apps.docs.editors.sheets",
            fallbackWebUrl = "https://sheets.google.com",
            oauthScope = "spreadsheets.readonly",
            capabilitiesDescription = "Tableurs, budgets et feuilles de calcul Google Sheets",
            sampleVoiceCommand = "Ouvre Google Sheets"
        ),
        GoogleWorkspaceIntegrationSpec(
            id = "tasks",
            title = "Google Tasks & Keep",
            emoji = "✅",
            packageName = "com.google.android.apps.tasks",
            fallbackWebUrl = "https://tasks.google.com",
            oauthScope = "auth/tasks",
            capabilitiesDescription = "Listes de tâches et notes synchronisées Google",
            sampleVoiceCommand = "Ouvre Google Tasks"
        ),
        GoogleWorkspaceIntegrationSpec(
            id = "contacts",
            title = "Google Contacts",
            emoji = "👤",
            packageName = "com.google.android.contacts",
            fallbackWebUrl = "https://contacts.google.com",
            oauthScope = "contacts.readonly",
            capabilitiesDescription = "Synchronisation directe avec le Safe Contact Resolver",
            sampleVoiceCommand = "Ouvre Google Contacts"
        ),
        GoogleWorkspaceIntegrationSpec(
            id = "maps",
            title = "Google Maps",
            emoji = "🗺️",
            packageName = "com.google.android.apps.maps",
            fallbackWebUrl = "https://maps.google.com",
            oauthScope = "Intent geo: & Navigation",
            capabilitiesDescription = "Itinéraires GPS en 1 clic et recherche de lieux autour de vous",
            sampleVoiceCommand = "Itinéraire vers la gare sur Google Maps"
        ),
        GoogleWorkspaceIntegrationSpec(
            id = "youtube",
            title = "YouTube",
            emoji = "▶️",
            packageName = "com.google.android.youtube",
            fallbackWebUrl = "https://m.youtube.com",
            oauthScope = "Intent Search & Deep-Link",
            capabilitiesDescription = "Recherche directe de vidéos et lancement instantané",
            sampleVoiceCommand = "Cherche musique lo-fi sur YouTube"
        ),
        GoogleWorkspaceIntegrationSpec(
            id = "photos",
            title = "Google Photos",
            emoji = "📸",
            packageName = "com.google.android.apps.photos",
            fallbackWebUrl = "https://photos.google.com",
            oauthScope = "Galerie & Souvenirs",
            capabilitiesDescription = "Accès rapide à vos photos et albums",
            sampleVoiceCommand = "Ouvre Google Photos"
        )
    )

    private val _state = MutableStateFlow(
        UniversalHubState(
            googleAppsCatalog = GOOGLE_ECOSYSTEM_SPECS
        )
    )
    val state: StateFlow<UniversalHubState> = _state.asStateFlow()

    /**
     * Initialise le scan dynamique de toutes les applications du téléphone et enregistre le
     * BroadcastReceiver système (`ACTION_PACKAGE_ADDED` / `ACTION_PACKAGE_REMOVED`) pour détecter
     * automatiquement toute nouvelle application installée ultérieurement par l'utilisateur.
     */
    fun initAndRegisterDynamicPackageMonitor(context: Context) {
        val appCtx = context.applicationContext
        refreshAllInstalledPhoneApps(appCtx)

        if (!receiverRegistered) {
            synchronized(this) {
                if (!receiverRegistered) {
                    val filter = IntentFilter().apply {
                        addAction(Intent.ACTION_PACKAGE_ADDED)
                        addAction(Intent.ACTION_PACKAGE_REPLACED)
                        addAction(Intent.ACTION_PACKAGE_REMOVED)
                        addDataScheme("package")
                    }
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context?, intent: Intent?) {
                            val pkg = intent?.data?.schemeSpecificPart?.trim().orEmpty()
                            val action = intent?.action.orEmpty()
                            if (ctx != null && pkg.isNotBlank()) {
                                handleDynamicPackageEvent(ctx.applicationContext, pkg, action)
                            }
                        }
                    }
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            appCtx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                        } else {
                            appCtx.registerReceiver(receiver, filter)
                        }
                        receiverRegistered = true
                    }
                }
            }
        }
    }

    /**
     * Scanne dynamiquement toutes les applications ayant une activité `MAIN` + `LAUNCHER` sur le téléphone.
     */
    fun refreshAllInstalledPhoneApps(context: Context): List<InstalledPhoneAppItem> {
        val pm = context.packageManager
        val discovered = mutableListOf<InstalledPhoneAppItem>()
        val seenPackages = mutableSetOf<String>()
        val now = currentTimeFormatted()
        val existingNewlyInstalled = _state.value.newlyInstalledPackagesLog.toSet()

        runCatching {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            for (info in resolveInfos) {
                val pkg = info.activityInfo?.packageName?.trim().orEmpty()
                if (pkg.isBlank() || pkg == context.packageName || !seenPackages.add(pkg)) continue
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank {
                    pkg.substringAfterLast('.')
                }
                val isGoogle = pkg.startsWith("com.google.") || pkg.startsWith("com.android.chrome")
                discovered.add(
                    InstalledPhoneAppItem(
                        packageName = pkg,
                        appLabel = label,
                        emoji = emojiForApp(pkg, label),
                        isGoogleApp = isGoogle,
                        isNewlyInstalledAfterBoot = existingNewlyInstalled.contains(pkg),
                        discoveredAt = now
                    )
                )
            }
        }

        // Assurer que les applications Google et Système essentielles figurent aussi dans le catalogue universel
        val fallbackSystemApps = listOf(
            InstalledPhoneAppItem("com.google.android.gm", "Gmail", "📧", true, false, now),
            InstalledPhoneAppItem("com.google.android.calendar", "Google Agenda", "📅", true, false, now),
            InstalledPhoneAppItem("com.google.android.apps.docs", "Google Drive", "📁", true, false, now),
            InstalledPhoneAppItem("com.google.android.apps.maps", "Google Maps", "🗺️", true, false, now),
            InstalledPhoneAppItem("com.google.android.youtube", "YouTube", "▶️", true, false, now),
            InstalledPhoneAppItem("com.google.android.apps.photos", "Google Photos", "📸", true, false, now),
            InstalledPhoneAppItem("com.android.chrome", "Google Chrome", "🌐", true, false, now),
            InstalledPhoneAppItem("com.google.android.apps.messaging", "Messages SMS", "💬", true, false, now),
            InstalledPhoneAppItem("com.google.android.contacts", "Contacts", "👤", true, false, now),
            InstalledPhoneAppItem("com.android.settings", "Paramètres", "⚙️", false, false, now),
            InstalledPhoneAppItem("com.google.android.deskclock", "Horloge & Réveil", "⏰", true, false, now)
        )
        for (sysApp in fallbackSystemApps) {
            if (seenPackages.add(sysApp.packageName)) {
                discovered.add(sysApp)
            }
        }

        val sorted = discovered.sortedWith(
            compareByDescending<InstalledPhoneAppItem> { it.isNewlyInstalledAfterBoot }
                .thenByDescending { it.isGoogleApp }
                .thenBy { it.appLabel.lowercase() }
        )

        _state.update {
            it.copy(
                installedPhoneApps = sorted,
                lastSyncTimestamp = now
            )
        }
        return sorted
    }

    /**
     * Appelé automatiquement par Android dès que l'utilisateur installe, met à jour ou désinstalle une application.
     */
    private fun handleDynamicPackageEvent(context: Context, packageName: String, action: String) {
        val pm = context.packageManager
        when (action) {
            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> {
                val appLabel = runCatching {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString().trim()
                }.getOrDefault(packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() })

                _state.update { current ->
                    val updatedLog = (listOf(packageName) + current.newlyInstalledPackagesLog).distinct()
                    current.copy(newlyInstalledPackagesLog = updatedLog)
                }
                refreshAllInstalledPhoneApps(context)

                // Enregistrer automatiquement la nouvelle application dans la mémoire Room de LIA
                LiaMemoryAndHistoryStore.upsertLongTermMemory(
                    category = "APP_SHORTCUT",
                    memoryKey = "new_app_${packageName.lowercase()}",
                    title = "Nouvelle app détectée : $appLabel",
                    contentValue = "Application $appLabel ($packageName) détectée dynamiquement et prête à être ouverte par LIA",
                    targetActionOrPackage = packageName,
                    speedupMs = 45L
                )
                LiaMemoryAndHistoryStore.upsertSouvenir(
                    type = "FAIT",
                    subjectKey = "app_${appLabel.lowercase().replace(" ", "_")}",
                    title = "Application installée : $appLabel",
                    detailValue = "L'application $appLabel ($packageName) est installée sur le téléphone.",
                    searchTags = "application,app,installee,${appLabel.lowercase()},$packageName"
                )
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                refreshAllInstalledPhoneApps(context)
            }
        }
    }

    /**
     * Recherche floue universelle parmi TOUTES les applications installées sur le téléphone
     * (y compris celles ajoutées après coup) et tout l'écosystème Google.
     */
    fun findBestMatchingInstalledApp(rawQuery: String): InstalledPhoneAppItem? {
        val ctx = LiaMemoryAndHistoryStore.getApplicationContextOrNull()
        val apps = if (_state.value.installedPhoneApps.isEmpty() && ctx != null) {
            refreshAllInstalledPhoneApps(ctx)
        } else {
            _state.value.installedPhoneApps
        }
        val clean = rawQuery.lowercase().trim()
        if (clean.isBlank()) return null

        // 1. Exact or substring match on app label or package segment
        val directMatch = apps.firstOrNull { app ->
            val lbl = app.appLabel.lowercase()
            val pkgShort = app.packageName.substringAfterLast('.').lowercase()
            lbl == clean || clean.contains(lbl) || lbl.contains(clean) || pkgShort == clean
        }
        if (directMatch != null) return directMatch

        // 2. Fuzzy similarity match via SafeContactResolver's Levenshtein + Bigram scorer
        val scored = apps.map { app ->
            val scoreLabel = SafeContactResolver.computeFuzzySimilarityPercent(clean, app.appLabel.lowercase())
            val scorePkg = SafeContactResolver.computeFuzzySimilarityPercent(
                clean,
                app.packageName.substringAfterLast('.').lowercase()
            )
            app to maxOf(scoreLabel, scorePkg)
        }.maxByOrNull { it.second }

        return if (scored != null && scored.second >= 62) scored.first else null
    }

    /**
     * Exécute un Intent profond Google Workspace / Android (Gmail, Google Agenda, Google Maps, YouTube,
     * Drive, Docs, Sheets, Tasks, Horloge/Alarme) ou lance n'importe quelle application installée avec fallback Web.
     */
    fun launchGoogleOrUniversalTarget(context: Context, targetSpecOrPackage: String, optionalQuery: String = ""): Boolean {
        val cleanTarget = targetSpecOrPackage.trim()
        LiaAppUsageTrackerService.recordAppOpenEvent(context, cleanTarget, optionalQuery)

        return try {
            when {
                cleanTarget.startsWith("mailto:") -> {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(cleanTarget)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }
                cleanTarget.startsWith("geo:") || cleanTarget.startsWith("https://") -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanTarget)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }
                cleanTarget.startsWith("calendar_insert:") -> {
                    val title = cleanTarget.removePrefix("calendar_insert:").ifBlank { "Événement LIA" }
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, title)
                        putExtra(CalendarContract.Events.DESCRIPTION, "Créé par LIA Assistant")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }
                cleanTarget.startsWith("alarm:") -> {
                    val parts = cleanTarget.removePrefix("alarm:").split(":")
                    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
                    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "Réveil LIA")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }
                else -> {
                    val pm = context.packageManager
                    val launchIntent = pm.getLaunchIntentForPackage(cleanTarget)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                        true
                    } else {
                        // Fallback sur l'URL Web officielle Google si l'APK n'est pas installé sur l'émulateur
                        val googleSpec = GOOGLE_ECOSYSTEM_SPECS.find {
                            it.packageName.equals(cleanTarget, ignoreCase = true) ||
                                it.id.equals(cleanTarget, ignoreCase = true)
                        }
                        if (googleSpec != null) {
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(googleSpec.fallbackWebUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(webIntent)
                            true
                        } else {
                            false
                        }
                    }
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun emojiForApp(pkg: String, label: String): String {
        val l = label.lowercase()
        val p = pkg.lowercase()
        return when {
            p.contains("gm") || l.contains("gmail") || l.contains("mail") -> "📧"
            p.contains("calendar") || l.contains("agenda") -> "📅"
            p.contains("maps") || l.contains("waze") -> "🗺️"
            p.contains("youtube") -> "▶️"
            p.contains("docs") && p.contains("sheets") -> "📊"
            p.contains("docs") -> "📁"
            p.contains("photos") || l.contains("galerie") -> "📸"
            p.contains("chrome") || l.contains("browser") -> "🌐"
            p.contains("messaging") || l.contains("message") -> "💬"
            p.contains("contacts") -> "👤"
            p.contains("deskclock") || l.contains("horloge") -> "⏰"
            p.contains("settings") || l.contains("param") -> "⚙️"
            p.contains("camera") || l.contains("appareil photo") -> "📷"
            p.contains("music") || p.contains("spotify") -> "🎵"
            p.contains("whatsapp") -> "🟢"
            else -> "📱"
        }
    }
}
