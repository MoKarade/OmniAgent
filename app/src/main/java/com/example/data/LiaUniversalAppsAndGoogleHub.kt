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

    @Volatile
    private var packageMonitorReceiver: BroadcastReceiver? = null

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
                        packageMonitorReceiver = receiver
                        receiverRegistered = true
                    }
                }
            }
        }
    }

    fun unregisterDynamicPackageMonitor(context: Context) {
        synchronized(this) {
            val receiver = packageMonitorReceiver ?: return
            runCatching {
                context.applicationContext.unregisterReceiver(receiver)
            }
            packageMonitorReceiver = null
            receiverRegistered = false
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
                val rawLabel = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank {
                    pkg.substringAfterLast('.')
                }
                val label = if (pkg.equals("com.android.settings", ignoreCase = true) &&
                    rawLabel.equals("Settings", ignoreCase = true)
                ) {
                    "Paramètres"
                } else {
                    rawLabel
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

    private val LEADING_APP_DETERMINER_REGEX = Regex(
        "^(?:dans|sur|vers|chez|ouvre|ouvrir|lance|lancer|va|aller|de\\s+la|du|des|les|le|la|un|une|mon|ma|mes|ton|ta|tes|notre|nos|votre|vos|application|applications|appli|applis|app|apps)\\s+",
        RegexOption.IGNORE_CASE
    )
    private val LEADING_APOSTROPHE_PREFIX_REGEX = Regex(
        "^(?:l'|d')",
        RegexOption.IGNORE_CASE
    )

    private val SETTINGS_NORMALIZED_ALIASES = setOf(
        "parametres", "parametre", "reglages", "reglage", "settings", "setting",
        "configuration", "systeme", "parametres android", "parametres systeme",
        "system settings", "com.android.settings"
    )

    private val BILINGUAL_SYSTEM_APP_ALIASES: Map<String, Pair<String, String>> = mapOf(
        "parametres" to ("com.android.settings" to "Paramètres"),
        "parametre" to ("com.android.settings" to "Paramètres"),
        "reglages" to ("com.android.settings" to "Paramètres"),
        "reglage" to ("com.android.settings" to "Paramètres"),
        "settings" to ("com.android.settings" to "Paramètres"),
        "setting" to ("com.android.settings" to "Paramètres"),
        "configuration" to ("com.android.settings" to "Paramètres"),
        "systeme" to ("com.android.settings" to "Paramètres"),
        "parametres android" to ("com.android.settings" to "Paramètres"),
        "parametres systeme" to ("com.android.settings" to "Paramètres"),
        "system settings" to ("com.android.settings" to "Paramètres"),
        "horloge" to ("com.google.android.deskclock" to "Horloge & Réveil"),
        "reveil" to ("com.google.android.deskclock" to "Horloge & Réveil"),
        "alarme" to ("com.google.android.deskclock" to "Horloge & Réveil"),
        "minuteur" to ("com.google.android.deskclock" to "Horloge & Réveil"),
        "clock" to ("com.google.android.deskclock" to "Horloge & Réveil"),
        "agenda" to ("com.google.android.calendar" to "Google Agenda"),
        "calendrier" to ("com.google.android.calendar" to "Google Agenda"),
        "calendar" to ("com.google.android.calendar" to "Google Agenda"),
        "google calendar" to ("com.google.android.calendar" to "Google Agenda"),
        "google agenda" to ("com.google.android.calendar" to "Google Agenda"),
        "messages" to ("com.google.android.apps.messaging" to "Messages SMS"),
        "sms" to ("com.google.android.apps.messaging" to "Messages SMS"),
        "messagerie" to ("com.google.android.apps.messaging" to "Messages SMS"),
        "contacts" to ("com.google.android.contacts" to "Contacts"),
        "google contacts" to ("com.google.android.contacts" to "Contacts"),
        "chrome" to ("com.android.chrome" to "Google Chrome"),
        "google chrome" to ("com.android.chrome" to "Google Chrome"),
        "navigateur" to ("com.android.chrome" to "Google Chrome"),
        "browser" to ("com.android.chrome" to "Google Chrome"),
        "gmail" to ("com.google.android.gm" to "Gmail"),
        "mail" to ("com.google.android.gm" to "Gmail"),
        "youtube" to ("com.google.android.youtube" to "YouTube"),
        "maps" to ("com.google.android.apps.maps" to "Google Maps"),
        "google maps" to ("com.google.android.apps.maps" to "Google Maps"),
        "photos" to ("com.google.android.apps.photos" to "Google Photos"),
        "google photos" to ("com.google.android.apps.photos" to "Google Photos"),
        "drive" to ("com.google.android.apps.docs" to "Google Drive"),
        "google drive" to ("com.google.android.apps.docs" to "Google Drive"),
        "play store" to ("com.android.vending" to "Google Play Store"),
        "google play" to ("com.android.vending" to "Google Play Store")
    )

    /**
     * Normalise une requête de nom d'application en retirant de manière répétée les articles,
     * déterminants et préfixes français ("les", "le", "la", "l'", "mon", "ma", "mes", "un", "une",
     * "des", "du", "de la", "dans", "sur", "application", "app", "appli"), ainsi que les accents (NFD).
     */
    fun normalizeAppQuery(raw: String): String {
        if (raw.isBlank()) return ""
        val preCleaned = raw
            .replace('’', '\'')
            .replace('‘', '\'')
            .trim()
            .replace(Regex("[?!.…,:;]+$"), "")
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("«", "»")
            .trim()
            .replace(Regex("[?!.…,:;]+$"), "")
            .trim()
        val decomposed = java.text.Normalizer.normalize(preCleaned, java.text.Normalizer.Form.NFD)
        var cleaned = decomposed.replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

        var changed = true
        var guard = 0
        while (changed && guard < 10) {
            changed = false
            guard++
            val withoutApostrophe = LEADING_APOSTROPHE_PREFIX_REGEX.replace(cleaned, "").trim()
            if (withoutApostrophe != cleaned && withoutApostrophe.isNotBlank()) {
                cleaned = withoutApostrophe
                changed = true
            }
            val withoutWord = LEADING_APP_DETERMINER_REGEX.replace(cleaned, "").trim()
            if (withoutWord != cleaned && withoutWord.isNotBlank()) {
                cleaned = withoutWord
                changed = true
            }
        }
        return cleaned
    }

    fun isSettingsAliasQuery(rawQuery: String): Boolean {
        val norm = normalizeAppQuery(rawQuery)
        return norm in SETTINGS_NORMALIZED_ALIASES
    }

    fun setInstalledAppsForTesting(apps: List<InstalledPhoneAppItem>) {
        _state.update { it.copy(installedPhoneApps = apps) }
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
        val clean = normalizeAppQuery(rawQuery)
        if (clean.isBlank()) return null

        // 0. Résolution bilingue prioritaire pour Paramètres / Settings et applications système canoniques
        if (clean in SETTINGS_NORMALIZED_ALIASES) {
            val existingSettings = apps.firstOrNull {
                it.packageName.equals("com.android.settings", ignoreCase = true)
            }
            return existingSettings?.copy(appLabel = "Paramètres")
                ?: InstalledPhoneAppItem(
                    packageName = "com.android.settings",
                    appLabel = "Paramètres",
                    emoji = "⚙️",
                    isGoogleApp = false,
                    isNewlyInstalledAfterBoot = false,
                    discoveredAt = ""
                )
        }

        BILINGUAL_SYSTEM_APP_ALIASES[clean]?.let { (canonicalPkg, canonicalLabel) ->
            val matchedByPkg = apps.firstOrNull { it.packageName.equals(canonicalPkg, ignoreCase = true) }
            if (matchedByPkg != null) return matchedByPkg
            if (canonicalPkg == "com.android.settings") {
                return InstalledPhoneAppItem(canonicalPkg, canonicalLabel, "⚙️", false, false, "")
            }
        }

        // 1. Exact match on normalized app label or package segment
        val exactMatch = apps.firstOrNull { app ->
            val lblNorm = normalizeAppQuery(app.appLabel)
            val pkgShort = app.packageName.substringAfterLast('.').lowercase()
            lblNorm == clean || pkgShort == clean || app.packageName.equals(clean, ignoreCase = true)
        }
        if (exactMatch != null) return exactMatch

        // 2. Safe substring match (preventing short labels length < 3 from false-matching clean.contains(lbl))
        val substringMatch = apps.firstOrNull { app ->
            val lblNorm = normalizeAppQuery(app.appLabel)
            val pkgShort = app.packageName.substringAfterLast('.').lowercase()
            (lblNorm.length >= 3 && clean.contains(lblNorm)) ||
                (clean.length >= 3 && lblNorm.contains(clean)) ||
                (pkgShort.length >= 3 && (clean.contains(pkgShort) || pkgShort.contains(clean)))
        }
        if (substringMatch != null) return substringMatch

        // 3. Fuzzy similarity match via SafeContactResolver's Levenshtein + Bigram scorer
        val scored = apps
            .filter { normalizeAppQuery(it.appLabel).length >= 3 || it.packageName.substringAfterLast('.').length >= 3 }
            .map { app ->
                val lblNorm = normalizeAppQuery(app.appLabel)
                val pkgShort = app.packageName.substringAfterLast('.').lowercase()
                val scoreLabel = if (lblNorm.length >= 3) {
                    SafeContactResolver.computeFuzzySimilarityPercent(clean, lblNorm)
                } else 0
                val scorePkg = if (pkgShort.length >= 3) {
                    SafeContactResolver.computeFuzzySimilarityPercent(clean, pkgShort)
                } else 0
                app to maxOf(scoreLabel, scorePkg)
            }
            .maxByOrNull { it.second }

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
