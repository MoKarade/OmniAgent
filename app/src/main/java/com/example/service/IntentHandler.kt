package com.example.service

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import com.example.data.LiaMemoryAndHistoryStore
import com.example.data.LiaUniversalAppsAndGoogleHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class LiaClassifiedIntentType(val code: String, val labelFr: String) {
    OPEN_CAMERA("OPEN_CAMERA", "Caméra / Photo & Vidéo"),
    DIAL_PHONE("DIAL_PHONE", "Appel téléphonique"),
    TOGGLE_FLASHLIGHT("TOGGLE_FLASHLIGHT", "Lampe torche"),
    SEND_EMAIL("SEND_EMAIL", "Envoi E-mail Direct"),
    SET_ALARM("SET_ALARM", "Programmation Réveil"),
    SET_TIMER("SET_TIMER", "Minuteur Système"),
    MAPS_NAVIGATION("MAPS_NAVIGATION", "Navigation & Itinéraire"),
    SEND_MESSAGE("SEND_MESSAGE", "Message & Discussion"),
    ADD_CALENDAR_EVENT("ADD_CALENDAR_EVENT", "Événement Agenda"),
    OPEN_SYSTEM_SETTINGS("OPEN_SYSTEM_SETTINGS", "Réglages Système"),
    PLAY_MEDIA("PLAY_MEDIA", "Lecture Multimédia"),
    PLAY_STORE_SEARCH("PLAY_STORE_SEARCH", "Recherche Play Store"),
    SHARE_CONTENT("SHARE_CONTENT", "Partage Rapide"),
    DEEP_APP_SEARCH("DEEP_APP_SEARCH", "Recherche dans l'Application"),
    YOUTUBE_DEEP_LINK("YOUTUBE_DEEP_LINK", "Recherche YouTube"),
    WEB_SEARCH_DEEP_LINK("WEB_SEARCH_DEEP_LINK", "Recherche Web"),
    DIRECT_APP_LAUNCH("DIRECT_APP_LAUNCH", "Ouverture d'Application")
}

data class UsageRankedPackage(
    val packageName: String,
    val appLabel: String,
    val totalTimeInForegroundMs: Long,
    val lastTimeUsedMs: Long,
    val combinedUsageScore: Float
)

data class ClassifiedUserIntent(
    val id: String = "intent_${System.currentTimeMillis()}",
    val rawUserCommand: String,
    val intentType: LiaClassifiedIntentType,
    val systemIntentAction: String,
    val deepLinkUri: String = "",
    val targetPackage: String = "",
    val resolvedAppLabel: String,
    val extractedParameters: Map<String, String> = emptyMap(),
    val bypassConfirmationMenu: Boolean = true,
    val usageStatsPriorityScore: Float = 1.0f,
    val summaryMessageFr: String
)

/**
 * Gestionnaire central d'intentions (`IntentHandler`) s'appuyant sur `UsageStatsManager` d'Android
 * pour classifier les requêtes utilisateur, pondérer les applications selon leur fréquence réelle
 * d'utilisation au premier plan, extraire dynamiquement les paramètres et les associer à des
 * Deep Links directs ou des actions d'Intents système.
 */
object IntentHandler {

    private val _lastClassifiedIntent = MutableStateFlow<ClassifiedUserIntent?>(null)
    val lastClassifiedIntent: StateFlow<ClassifiedUserIntent?> = _lastClassifiedIntent.asStateFlow()

    private val _usageRankedApps = MutableStateFlow<List<UsageRankedPackage>>(emptyList())
    val usageRankedApps: StateFlow<List<UsageRankedPackage>> = _usageRankedApps.asStateFlow()

    /**
      * Interroge `UsageStatsManager` (`Context.USAGE_STATS_SERVICE`) ainsi que les habitudes locales
      * pour classer les applications installées par priorité d'usage réel.
      */
    fun refreshUsageStatsRanking(context: Context?): List<UsageRankedPackage> {
        val rankedMap = mutableMapOf<String, UsageRankedPackage>()
        val now = System.currentTimeMillis()
        val oneWeekAgo = now - (7L * 24L * 60L * 60L * 1000L)

        // 1. Lecture directe via Android UsageStatsManager
        if (context != null && LiaAppUsageTrackerService.hasUsageStatsPermission(context)) {
            runCatching {
                val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                val aggregated = usageManager?.queryAndAggregateUsageStats(oneWeekAgo, now).orEmpty()
                val pm = context.packageManager
                for ((pkg, stats) in aggregated) {
                    if (pkg == context.packageName || stats.totalTimeInForeground <= 0L) continue
                    val appLabel = runCatching {
                        val info = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(info).toString()
                    }.getOrDefault(pkg.substringAfterLast('.'))
                    val fgMinutes = (stats.totalTimeInForeground / 60_000f).coerceAtMost(600f)
                    val recencyBoost = if (now - stats.lastTimeUsed < 3_600_000L) 0.35f else 0.10f
                    val score = ((fgMinutes / 60f) + recencyBoost).coerceIn(0.1f, 5.0f)
                    rankedMap[pkg] = UsageRankedPackage(
                        packageName = pkg,
                        appLabel = appLabel,
                        totalTimeInForegroundMs = stats.totalTimeInForeground,
                        lastTimeUsedMs = stats.lastTimeUsed,
                        combinedUsageScore = score
                    )
                }
            }
        }

        // 2. Enrichissement avec les habitudes d'utilisation mémorisées
        val roomHabits = LiaMemoryAndHistoryStore.state.value.appUsageHabits
        for (habit in roomHabits) {
            val existing = rankedMap[habit.packageName]
            val combinedScore = (existing?.combinedUsageScore ?: 0f) + (habit.qValueHabitScore * 2.0f)
            rankedMap[habit.packageName] = UsageRankedPackage(
                packageName = habit.packageName,
                appLabel = habit.appLabel.ifBlank { existing?.appLabel ?: habit.packageName },
                totalTimeInForegroundMs = existing?.totalTimeInForegroundMs ?: (habit.launchCount * 45_000L),
                lastTimeUsedMs = existing?.lastTimeUsedMs ?: now,
                combinedUsageScore = combinedScore.coerceIn(0.2f, 10.0f)
            )
        }

        val sorted = rankedMap.values.sortedByDescending { it.combinedUsageScore }
        _usageRankedApps.value = sorted
        return sorted
    }

    /**
     * Trouve le package préféré de l'utilisateur parmi plusieurs candidats grâce à `UsageStatsManager`.
     */
    fun selectPreferredPackageByUsage(
        context: Context?,
        candidatePackages: List<String>,
        fallbackPackage: String
    ): Pair<String, Float> {
        val ranked = refreshUsageStatsRanking(context)
        val bestMatch = ranked.firstOrNull { candidate ->
            candidatePackages.any { it.equals(candidate.packageName, ignoreCase = true) }
        }
        return if (bestMatch != null) {
            bestMatch.packageName to bestMatch.combinedUsageScore
        } else {
            fallbackPackage to 1.0f
        }
    }

    /**
     * Classifie une commande utilisateur précise et extrait dynamiquement tous ses paramètres
     * pour exécution directe par `AppActionExecutor`.
     *
     * Retourne `null` si la commande est générale/incomplète (auquel cas LIA pose la question de clarification
     * avec puces de réponse rapide) ou s'il s'agit d'une discussion générale.
     */
    fun classifyAndExtractKnownIntent(
        rawCommand: String,
        context: Context? = LiaMemoryAndHistoryStore.getApplicationContextOrNull()
    ): ClassifiedUserIntent? {
        val trimmed = rawCommand.replace('’', '\'').replace('‘', '\'').trim()
        val lower = trimmed.lowercase(Locale.FRENCH)

        // 0. TOGGLE_FLASHLIGHT : "allume la lampe torche", "éteins la lampe", "lampe torche", "active le flash"
        if (lower.contains("lampe torche") ||
            lower.contains("lampe de poche") ||
            lower == "torche" ||
            lower == "lampe" ||
            ((lower.contains("allume") || lower.contains("éteins") || lower.contains("eteins") || lower.contains("active") || lower.contains("désactive") || lower.contains("desactive") || lower.contains("coupe")) &&
                (lower.contains("lampe") || lower.contains("torche") || lower.contains("flash")))
        ) {
            val turnOff = lower.contains("éteins") ||
                lower.contains("eteins") ||
                lower.contains("éteindre") ||
                lower.contains("eteindre") ||
                lower.contains("désactive") ||
                lower.contains("desactive") ||
                lower.contains("coupe") ||
                lower.contains("arrête") ||
                lower.contains("arrete") ||
                lower.contains("off")
            val mode = if (turnOff) "off" else "on"
            val label = if (turnOff) "Éteindre la lampe torche" else "Allumer la lampe torche"
            val classified = ClassifiedUserIntent(
                rawUserCommand = trimmed,
                intentType = LiaClassifiedIntentType.TOGGLE_FLASHLIGHT,
                systemIntentAction = "FLASHLIGHT_${mode.uppercase()}",
                deepLinkUri = "flashlight:$mode",
                targetPackage = "",
                resolvedAppLabel = label,
                extractedParameters = mapOf("state" to mode),
                bypassConfirmationMenu = true,
                summaryMessageFr = if (turnOff) {
                    "🔦 **Lampe torche éteinte**."
                } else {
                    "🔦 **Lampe torche allumée**."
                }
            )
            _lastClassifiedIntent.value = classified
            return classified
        }

        // 1. OPEN_CAMERA : "ouvre l'appareil photo", "open camera", "prends une photo", "mode selfie", "caméra vidéo"
        if (lower.contains("appareil photo") ||
            lower.contains("open camera") ||
            lower.contains("ouvre la caméra") ||
            lower.contains("ouvre la camera") ||
            lower.contains("lance la caméra") ||
            lower.contains("prends une photo") ||
            lower.contains("prendre une photo") ||
            lower.contains("prends un selfie") ||
            lower.contains("mode selfie") ||
            lower.contains("caméra vidéo") ||
            lower.contains("camera video") ||
            lower.contains("filmer une vidéo") ||
            lower == "caméra" ||
            lower == "camera" ||
            lower == "photo" ||
            lower == "selfie"
        ) {
            val isVideo = lower.contains("vidéo") || lower.contains("video") || lower.contains("filmer")
            val isSelfie = lower.contains("selfie") || lower.contains("frontale") || lower.contains("avant")
            val systemAction = if (isVideo) {
                MediaStore.INTENT_ACTION_VIDEO_CAMERA
            } else {
                MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
            }
            val modeLabel = when {
                isVideo -> "Caméra Vidéo"
                isSelfie -> "Appareil Photo (Selfie)"
                else -> "Appareil Photo"
            }
            val (preferredCameraPkg, usageScore) = selectPreferredPackageByUsage(
                context = context,
                candidatePackages = listOf(
                    "com.google.android.GoogleCamera",
                    "com.android.camera",
                    "com.android.camera2",
                    "com.sec.android.app.camera"
                ),
                fallbackPackage = ""
            )
            val classified = ClassifiedUserIntent(
                rawUserCommand = trimmed,
                intentType = LiaClassifiedIntentType.OPEN_CAMERA,
                systemIntentAction = systemAction,
                deepLinkUri = "camera:${if (isVideo) "video" else if (isSelfie) "selfie" else "photo"}",
                targetPackage = preferredCameraPkg,
                resolvedAppLabel = modeLabel,
                extractedParameters = buildMap {
                    put("mode", if (isVideo) "VIDEO" else "PHOTO")
                    if (isSelfie) put("camera_facing", "FRONT")
                },
                bypassConfirmationMenu = true,
                usageStatsPriorityScore = usageScore,
                summaryMessageFr = "📸 J'ouvre directement **$modeLabel**."
            )
            _lastClassifiedIntent.value = classified
            return classified
        }

        // 1B. DIAL_PHONE (avec un nom ou numéro précis) : "appelle Paul", "téléphone à Maman", "appelle le 06 12 34 56 78"
        if ((lower.startsWith("appelle ") ||
                lower.startsWith("appeler ") ||
                lower.startsWith("téléphone à ") ||
                lower.startsWith("telephone a ") ||
                lower.startsWith("passe un appel à ") ||
                lower.startsWith("passe un appel a ") ||
                lower.startsWith("call ")) &&
            !lower.contains("comment je m'appelle") &&
            !lower.contains("comment tu t'appelles")
        ) {
            val targetRaw = trimmed.replace(
                Regex("^(?:appelle|appeler|téléphone\\s+à|telephone\\s+a|passe\\s+un\\s+appel\\s+(?:à|a)|call)\\s+(?:ma\\s+|mon\\s+)?", RegexOption.IGNORE_CASE),
                ""
            ).trim()

            if (targetRaw.isNotBlank()) {
                val matchedContact = LiaCrossAppKnowledgeResolver.findContactsMatching(context, targetRaw).firstOrNull()
                val verification = LiaMemoryAndHistoryStore.verifyContactForSms(targetRaw)
                val resolvedName = matchedContact?.displayName ?: verification.recipientName.ifBlank { targetRaw }
                val resolvedPhone = matchedContact?.phoneNumbers?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: verification.phoneNumber.takeIf { it.isNotBlank() }
                    ?: targetRaw.filter { it.isDigit() || it == '+' }

                val dialUri = if (resolvedPhone.isNotBlank()) "tel:${Uri.encode(resolvedPhone)}" else "tel:"
                val label = if (resolvedPhone.isNotBlank() && resolvedName != resolvedPhone) {
                    "Appeler $resolvedName ($resolvedPhone)"
                } else {
                    "Appeler $resolvedName"
                }
                val classified = ClassifiedUserIntent(
                    rawUserCommand = trimmed,
                    intentType = LiaClassifiedIntentType.DIAL_PHONE,
                    systemIntentAction = Intent.ACTION_DIAL,
                    deepLinkUri = dialUri,
                    targetPackage = "com.google.android.dialer",
                    resolvedAppLabel = label,
                    extractedParameters = mapOf(
                        "contact_name" to resolvedName,
                        "phone_number" to resolvedPhone
                    ),
                    bypassConfirmationMenu = true,
                    summaryMessageFr = if (resolvedPhone.isNotBlank()) {
                        "📞 J'ouvre le composeur téléphonique pour **$resolvedName** (**$resolvedPhone**)."
                    } else {
                        "📞 J'ouvre l'application **Téléphone** pour appeler **$resolvedName**."
                    }
                )
                _lastClassifiedIntent.value = classified
                return classified
            }
        }

        // 2. SET_TIMER : "minuteur de 5 minutes", "mets un minuteur de 30 secondes", "set timer 10 min"
        val timerMatch = Regex(
            "(?:minuteur|timer|chrono|compte à rebours)\\s*(?:de|pour)?\\s*(\\d{1,3})\\s*(secondes?|sec|s|minutes?|min|m|heures?|h)?(?:\\s+(?:pour|de)\\s+(.+))?",
            RegexOption.IGNORE_CASE
        ).find(trimmed)
        if (timerMatch != null) {
            val amount = timerMatch.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: 5
            val unitRaw = timerMatch.groupValues.getOrNull(2)?.lowercase().orEmpty()
            val customLabel = timerMatch.groupValues.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() } ?: "Minuteur"
            val durationSeconds = when {
                unitRaw.startsWith("s") -> amount
                unitRaw.startsWith("h") -> amount * 3600
                else -> amount * 60
            }
            val unitLabel = when {
                unitRaw.startsWith("s") -> "$amount sec"
                unitRaw.startsWith("h") -> "$amount h"
                else -> "$amount min"
            }
            val classified = ClassifiedUserIntent(
                rawUserCommand = trimmed,
                intentType = LiaClassifiedIntentType.SET_TIMER,
                systemIntentAction = AlarmClock.ACTION_SET_TIMER,
                deepLinkUri = "timer:$durationSeconds:$customLabel",
                targetPackage = "com.google.android.deskclock",
                resolvedAppLabel = "Minuteur ($unitLabel)",
                extractedParameters = mapOf(
                    "length_seconds" to durationSeconds.toString(),
                    "skip_ui" to "true",
                    "label" to customLabel
                ),
                bypassConfirmationMenu = true,
                summaryMessageFr = "⏱️ Je lance votre minuteur de **$unitLabel**${if (customLabel != "Minuteur") " (*$customLabel*)" else ""}."
            )
            _lastClassifiedIntent.value = classified
            return classified
        }

        // 3. SET_ALARM (avec heure explicite dans la commande) : "mets un réveil à 7h30", "set alarm 06:45"
        val alarmMatch = Regex(
            "(?:réveil|reveil|alarme|set\\s+alarm)\\s*(?:à|a|pour|at)?\\s*(\\d{1,2})\\s*(?:h|:)\\s*(\\d{0,2})",
            RegexOption.IGNORE_CASE
        ).find(trimmed)
        if (alarmMatch != null) {
            val h = alarmMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 7
            val m = alarmMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
            val formattedTime = "%02d:%02d".format(h, m)
            val classified = ClassifiedUserIntent(
                rawUserCommand = trimmed,
                intentType = LiaClassifiedIntentType.SET_ALARM,
                systemIntentAction = AlarmClock.ACTION_SET_ALARM,
                deepLinkUri = "alarm:$h:$m",
                targetPackage = "com.google.android.deskclock",
                resolvedAppLabel = "Réveil $formattedTime",
                extractedParameters = mapOf(
                    "hour" to h.toString(),
                    "minutes" to m.toString(),
                    "skip_ui" to "true",
                    "message" to "Réveil LIA"
                ),
                bypassConfirmationMenu = true,
                summaryMessageFr = "⏰ Je programme votre réveil pour **$formattedTime**."
            )
            _lastClassifiedIntent.value = classified
            return classified
        }

        // 4. OPEN_SYSTEM_SETTINGS (tous les sous-écrans système Android : Wi-Fi, Bluetooth, Son, Luminosité, Batterie, Mode Avion, GPS, Stockage, NFC, etc.)
        // Exclut les missions d'inspection ou de recherche multi-étapes (ex: "va dans les paramètres regarde ma batterie", "ouvre les paramètres et cherche le stockage")
        // afin que LiaUniversalGoalAgent prenne en charge l'exploration autonome.
        val isSettingsInspectionOrCompound = lower.contains("regarde") ||
            lower.contains("cherche") ||
            lower.contains("trouve") ||
            lower.contains("retrouve") ||
            lower.contains("vérifie") ||
            lower.contains("verifie") ||
            lower.contains("inspecte") ||
            lower.contains("explore") ||
            lower.contains("combien") ||
            lower.contains("quel ") ||
            lower.contains("quelle ") ||
            lower.contains("quels ") ||
            lower.contains("quelles ") ||
            lower.contains("où ") ||
            lower.contains("ou ") ||
            lower.contains("va voir") ||
            lower.contains("aller voir") ||
            lower.startsWith("va dans ") ||
            lower.startsWith("aller dans ") ||
            lower.contains(" et ") ||
            lower.contains(" puis ") ||
            lower.contains(" pour ")

        val hasSettingsWord = Regex("\\b(?:param[èe]tres?|r[ée]glages?|settings)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(trimmed)

        val isSettingsRequest = !isSettingsInspectionOrCompound && (
            hasSettingsWord ||
                ((lower.startsWith("active ") || lower.startsWith("désactive ") || lower.startsWith("desactive ") || lower.startsWith("coupe ") || lower.startsWith("mets ") || lower.startsWith("ouvre ")) &&
                    (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("bluetooth") || lower.contains("mode avion") ||
                        lower.contains("silencieux") || lower.contains("vibreur") || lower.contains("luminosité") || lower.contains("luminosite") ||
                        lower.contains("économie d'énergie") || lower.contains("economie d'energie") || lower.contains("gps") ||
                        lower.contains("localisation") || lower.contains("nfc") || lower.contains("point d'accès") || lower.contains("partage de connexion")))
            )

        if (isSettingsRequest) {
            val (settingsAction, subLabel) = when {
                lower.contains("wifi") || lower.contains("wi-fi") ->
                    Settings.ACTION_WIFI_SETTINGS to "Réglages Wi-Fi"
                lower.contains("bluetooth") ->
                    Settings.ACTION_BLUETOOTH_SETTINGS to "Réglages Bluetooth"
                lower.contains("avion") ->
                    Settings.ACTION_AIRPLANE_MODE_SETTINGS to "Mode Avion & Réseau"
                lower.contains("son") || lower.contains("audio") || lower.contains("volume") || lower.contains("silencieux") || lower.contains("vibreur") ->
                    Settings.ACTION_SOUND_SETTINGS to "Réglages Son & Vibration"
                lower.contains("luminosité") || lower.contains("luminosite") || lower.contains("affichage") || lower.contains("écran") || lower.contains("ecran") || lower.contains("mode sombre") ->
                    Settings.ACTION_DISPLAY_SETTINGS to "Réglages Affichage & Luminosité"
                lower.contains("batterie") || lower.contains("énergie") || lower.contains("energie") ->
                    Settings.ACTION_BATTERY_SAVER_SETTINGS to "Réglages Batterie"
                lower.contains("localisation") || lower.contains("gps") || lower.contains("position") ->
                    Settings.ACTION_LOCATION_SOURCE_SETTINGS to "Réglages Localisation (GPS)"
                lower.contains("stockage") || lower.contains("espace") || lower.contains("mémoire") ->
                    Settings.ACTION_INTERNAL_STORAGE_SETTINGS to "Réglages Stockage"
                lower.contains("applications") || lower.contains("applis") ->
                    Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS to "Gestion des Applications"
                lower.contains("sécurité") || lower.contains("securite") || lower.contains("mot de passe") || lower.contains("empreinte") ->
                    Settings.ACTION_SECURITY_SETTINGS to "Réglages Sécurité"
                lower.contains("accessibilité") || lower.contains("accessibilite") ->
                    Settings.ACTION_ACCESSIBILITY_SETTINGS to "Réglages Accessibilité"
                lower.contains("date") || lower.contains("heure") ->
                    Settings.ACTION_DATE_SETTINGS to "Réglages Date & Heure"
                lower.contains("nfc") || lower.contains("sans contact") ->
                    Settings.ACTION_NFC_SETTINGS to "Réglages NFC & Sans contact"
                lower.contains("point d'accès") || lower.contains("partage de connexion") || lower.contains("hotspot") || lower.contains("données mobiles") || lower.contains("4g") || lower.contains("5g") ->
                    Settings.ACTION_WIRELESS_SETTINGS to "Réglages Réseau & Point d'accès"
                else ->
                    Settings.ACTION_SETTINGS to "Paramètres Android"
            }
            val classified = ClassifiedUserIntent(
                rawUserCommand = trimmed,
                intentType = LiaClassifiedIntentType.OPEN_SYSTEM_SETTINGS,
                systemIntentAction = settingsAction,
                deepLinkUri = "settings:$settingsAction",
                targetPackage = "com.android.settings",
                resolvedAppLabel = subLabel,
                extractedParameters = mapOf("settings_action" to settingsAction),
                bypassConfirmationMenu = true,
                summaryMessageFr = "⚙️ J'ouvre directement **$subLabel**."
            )
            _lastClassifiedIntent.value = classified
            return classified
        }

        // 4B. PLAY_MEDIA : "joue Daft Punk", "écoute du jazz sur Spotify", "lance la musique ...", "mets la chanson ..."
        if ((lower.startsWith("joue ") ||
                lower.startsWith("écoute ") ||
                lower.startsWith("ecoute ") ||
                lower.startsWith("lance la musique ") ||
                lower.startsWith("mets la musique ") ||
                lower.startsWith("mets la chanson ") ||
                lower.startsWith("lance la playlist ")) &&
            !lower.contains("steam") && !lower.contains("sur youtube")
        ) {
            val mediaQuery = trimmed
                .replace(Regex("^(?:joue|écoute|ecoute|lance\\s+la\\s+(?:musique|chanson|playlist)|mets\\s+la\\s+(?:musique|chanson|playlist))\\s+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+sur\\s+(?:spotify|deezer|youtube\\s+music|musique).*$", RegexOption.IGNORE_CASE), "")
                .trim()

            if (mediaQuery.isNotBlank()) {
                val (preferredMusicPkg, usageScore) = selectPreferredPackageByUsage(
                    context = context,
                    candidatePackages = listOf(
                        "com.spotify.music",
                        "com.google.android.apps.youtube.music",
                        "deezer.android.app",
                        "com.apple.android.music"
                    ),
                    fallbackPackage = "com.spotify.music"
                )
                val classified = ClassifiedUserIntent(
                    rawUserCommand = trimmed,
                    intentType = LiaClassifiedIntentType.PLAY_MEDIA,
                    systemIntentAction = MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH,
                    deepLinkUri = "media_play:${Uri.encode(mediaQuery)}",
                    targetPackage = preferredMusicPkg,
                    resolvedAppLabel = "Lecture : $mediaQuery",
                    extractedParameters = mapOf("query" to mediaQuery),
                    bypassConfirmationMenu = true,
                    usageStatsPriorityScore = usageScore,
                    summaryMessageFr = "🎵 Je lance la lecture de **« $mediaQuery »**."
                )
                _lastClassifiedIntent.value = classified
                return classified
            }
        }

        // 4C. PLAY_STORE_SEARCH : "cherche WhatsApp sur le Play Store", "installe VLC", "télécharge Signal sur Play Store"
        if (lower.contains("play store") ||
            lower.startsWith("installe ") ||
            lower.startsWith("installer ") ||
            lower.startsWith("télécharge l'application ") ||
            lower.startsWith("telecharge l'application ")
        ) {
            val appQuery = trimmed
                .replace(Regex("^(?:cherche|trouve|installe|installer|télécharge(?:r)?(?:\\s+l'application)?|telecharge(?:r)?(?:\\s+l'application)?|ouvre)\\s+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+(?:sur|dans)\\s+(?:le\\s+)?(?:google\\s+)?play\\s*store.*$", RegexOption.IGNORE_CASE), "")
                .trim()

            if (appQuery.isNotBlank() && appQuery.lowercase() != "play store" && appQuery.lowercase() != "le play store") {
                val marketUri = "market://search?q=${Uri.encode(appQuery)}"
                val classified = ClassifiedUserIntent(
                    rawUserCommand = trimmed,
                    intentType = LiaClassifiedIntentType.PLAY_STORE_SEARCH,
                    systemIntentAction = Intent.ACTION_VIEW,
                    deepLinkUri = marketUri,
                    targetPackage = "com.android.vending",
                    resolvedAppLabel = "Play Store ($appQuery)",
                    extractedParameters = mapOf("query" to appQuery),
                    bypassConfirmationMenu = true,
                    summaryMessageFr = "🛍️ J'ouvre le **Google Play Store** sur **« $appQuery »**."
                )
                _lastClassifiedIntent.value = classified
                return classified
            }
        }

        // 4D. SHARE_CONTENT : "partage : ...", "partager le texte ..."
        if (lower.startsWith("partage :") ||
            lower.startsWith("partager :") ||
            lower.startsWith("partage le texte ") ||
            lower.startsWith("partager le texte ")
        ) {
            val textToShare = if (trimmed.contains(":")) {
                trimmed.substringAfter(":").trim()
            } else {
                trimmed.replace(Regex("^(?:partage(?:r)?\\s+le\\s+texte)\\s+", RegexOption.IGNORE_CASE), "").trim()
            }
            if (textToShare.isNotBlank()) {
                val classified = ClassifiedUserIntent(
                    rawUserCommand = trimmed,
                    intentType = LiaClassifiedIntentType.SHARE_CONTENT,
                    systemIntentAction = Intent.ACTION_SEND,
                    deepLinkUri = "share_text:${Uri.encode(textToShare)}",
                    targetPackage = "",
                    resolvedAppLabel = "Partager « ${textToShare.take(20)} »",
                    extractedParameters = mapOf("text" to textToShare),
                    bypassConfirmationMenu = true,
                    summaryMessageFr = "📤 J'ouvre le menu de partage Android pour : *« $textToShare »*."
                )
                _lastClassifiedIntent.value = classified
                return classified
            }
        }

        // 5. SEND_EMAIL (avec destinataire ou sujet explicite dans la commande)
        if ((lower.contains("envoie un mail") || lower.contains("envoyer un mail") ||
                lower.contains("envoie un e-mail") || lower.contains("envoyer un e-mail") ||
                lower.contains("envoie un email") || lower.contains("send email") ||
                lower.contains("écris un mail") || lower.contains("rédige un mail"))
        ) {
            val emailRegex = Regex("([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})")
            val recipientEmail = emailRegex.find(trimmed)?.groupValues?.getOrNull(1).orEmpty()
            val afterColon = if (trimmed.contains(":")) trimmed.substringAfter(":").trim() else ""
            val bodyOrSubject = afterColon.ifBlank {
                trimmed.replace(
                    Regex("^(?:envoie|envoyer|rédige|rédiger|écris|écrire|send)\\s+(?:un\\s+)?(?:e-mail|email|mail)\\s*(?:à|a|to)?\\s*", RegexOption.IGNORE_CASE),
                    ""
                ).replace(recipientEmail, "").trim()
            }

            if (recipientEmail.isNotBlank() || bodyOrSubject.isNotBlank()) {
                val subject = bodyOrSubject.take(48).ifBlank { "Message" }
                val body = bodyOrSubject.ifBlank { "Bonjour," }
                val mailtoUri = "mailto:${Uri.encode(recipientEmail)}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
                val (preferredMailPkg, usageScore) = selectPreferredPackageByUsage(
                    context = context,
                    candidatePackages = listOf("com.google.android.gm", "com.microsoft.office.outlook"),
                    fallbackPackage = "com.google.android.gm"
                )
                val classified = ClassifiedUserIntent(
                    rawUserCommand = trimmed,
                    intentType = LiaClassifiedIntentType.SEND_EMAIL,
                    systemIntentAction = Intent.ACTION_SENDTO,
                    deepLinkUri = mailtoUri,
                    targetPackage = preferredMailPkg,
                    resolvedAppLabel = "Gmail (${subject.take(22)})",
                    extractedParameters = buildMap {
                        if (recipientEmail.isNotBlank()) put("to", recipientEmail)
                        put("subject", subject)
                        put("body", body)
                    },
                    bypassConfirmationMenu = true,
                    usageStatsPriorityScore = usageScore,
                    summaryMessageFr = "📧 J'ouvre **Gmail** avec votre brouillon : *« $subject »*."
                )
                _lastClassifiedIntent.value = classified
                return classified
            }
        }

        // 5B. SEND_MESSAGE (Messagerie multi-applications : Messenger, WhatsApp, Telegram, Instagram, SMS, Discord)
        val detectedChannel = LiaCrossAppKnowledgeResolver.detectMessagingChannel(trimmed)
        val isMessageIntent = lower.contains("message") || lower.contains("écris") || lower.contains("ecris") ||
            lower.contains("envoie") || lower.contains("envoyer") || lower.contains("conv") ||
            lower.contains("conversation") || lower.contains("discussion")
        if (detectedChannel != null && isMessageIntent) {
            val channel = detectedChannel
            val recRegex = Regex(
                "(?:à|a|au|pour|avec)\\s+(?:ma\\s+|mon\\s+)?([A-Za-zÀ-ÿ0-9+]+?)(?:\\s+sur\\s+|\\s+dans\\s+|\\s*:\\s*|\\s+que\\s+|\\s+disant\\s+|\\s+avec\\s+|$)",
                RegexOption.IGNORE_CASE
            )
            val rawRecipient = recRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf {
                    it.lowercase() !in setOf(
                        "messenger", "whatsapp", "telegram", "instagram", "discord", "signal", "keep",
                        "message", "conversation", "discussion", "un", "une", "le", "la", "lui", "dire"
                    )
                }.orEmpty()

            val msgBody = when {
                trimmed.contains(":") -> trimmed.substringAfter(":").trim().removeSurrounding("\"").removeSurrounding("«", "»")
                Regex("[«\"](.+?)[»\"]").find(trimmed) != null -> Regex("[«\"](.+?)[»\"]").find(trimmed)!!.groupValues[1].trim()
                Regex("(?:disant\\s+que|disant|en\\s+disant\\s+que|pour\\s+dire\\s+que|pour\\s+lui\\s+dire|avec\\s+le\\s+message|comme\\s+message)\\s+(.+)", RegexOption.IGNORE_CASE).find(trimmed) != null -> {
                    Regex("(?:disant\\s+que|disant|en\\s+disant\\s+que|pour\\s+dire\\s+que|pour\\s+lui\\s+dire|avec\\s+le\\s+message|comme\\s+message)\\s+(.+)", RegexOption.IGNORE_CASE).find(trimmed)!!.groupValues[1].trim()
                }
                else -> ""
            }

            val encodedRec = Uri.encode(rawRecipient)
            val encodedMsg = Uri.encode(msgBody)
            val appMsgUri = "app_msg:${channel.id}:$encodedRec:$encodedMsg"
            val targetLabel = if (rawRecipient.isNotBlank()) "${channel.displayName} ($rawRecipient)" else channel.displayName
            val summary = if (msgBody.isNotBlank()) {
                "${channel.emoji} J'ouvre **${channel.displayName}**${if (rawRecipient.isNotBlank()) " pour **$rawRecipient**" else ""} avec votre message :\n« *$msgBody* »"
            } else {
                "${channel.emoji} J'ouvre **${channel.displayName}**${if (rawRecipient.isNotBlank()) " avec **$rawRecipient**" else ""}."
            }
            val classified = ClassifiedUserIntent(
                rawUserCommand = trimmed,
                intentType = LiaClassifiedIntentType.SEND_MESSAGE,
                systemIntentAction = Intent.ACTION_SEND,
                deepLinkUri = appMsgUri,
                targetPackage = channel.packageName,
                resolvedAppLabel = targetLabel,
                extractedParameters = buildMap {
                    put("channel", channel.id)
                    if (rawRecipient.isNotBlank()) put("recipient", rawRecipient)
                    if (msgBody.isNotBlank()) put("body", msgBody)
                },
                bypassConfirmationMenu = true,
                summaryMessageFr = summary
            )
            _lastClassifiedIntent.value = classified
            return classified
        }

        // 6. MAPS_NAVIGATION : recherche dans maps, itinéraire, destination
        val isMapsIntent = lower.contains("maps") ||
            lower.contains("carte") ||
            lower.startsWith("itinéraire") ||
            lower.startsWith("itineraire") ||
            lower.startsWith("navigation") ||
            lower.startsWith("va à ") ||
            lower.startsWith("aller à ") ||
            lower.startsWith("navigate to ") ||
            lower.startsWith("emmène-moi") ||
            lower.startsWith("emmene-moi") ||
            lower.startsWith("guide-moi") ||
            lower.startsWith("où se trouve") ||
            lower.startsWith("ou se trouve")

        if (isMapsIntent) {
            val destination = trimmed
                .replace(Regex("^(?:faire\\s+une\\s+recherche\\s+(?:dans|sur)\\s+(?:google\\s+)?maps(?:\\s+et\\s+lancer)?|recherche(?:r)?\\s+(?:dans|sur)\\s+(?:google\\s+)?maps|cherche(?:r)?\\s+(?:dans|sur)\\s+(?:google\\s+)?maps|trouve(?:r)?\\s+(?:dans|sur)\\s+(?:google\\s+)?maps|ouvre(?:r)?\\s+(?:google\\s+)?maps(?:\\s+et\\s+cherche(?:r)?)?|itin[ée]raire\\s+vers|navigation\\s+vers|va\\s+[àa]|aller\\s+[àa]|navigate\\s+to|emm[èe]ne-moi\\s+[àa]|guide-moi\\s+vers|o[ùu]\\s+se\\s+trouve|maps)\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+(?:dans|sur)\\s+(?:google\\s+)?maps.*$", RegexOption.IGNORE_CASE), "")
                .trim()

            val geoUri = if (destination.isNotBlank()) "geo:0,0?q=${Uri.encode(destination)}" else "geo:0,0?q="
            val summary = if (destination.isNotBlank()) {
                "🗺️ Je lance la recherche **Google Maps** pour **« $destination »**."
            } else {
                "🗺️ J'ouvre la recherche **Google Maps**."
            }
            val label = if (destination.isNotBlank()) "Google Maps ($destination)" else "Google Maps"

            val classified = ClassifiedUserIntent(
                rawUserCommand = trimmed,
                intentType = LiaClassifiedIntentType.MAPS_NAVIGATION,
                systemIntentAction = Intent.ACTION_VIEW,
                deepLinkUri = geoUri,
                targetPackage = "com.google.android.apps.maps",
                resolvedAppLabel = label,
                extractedParameters = mapOf("destination" to destination),
                bypassConfirmationMenu = true,
                summaryMessageFr = summary
            )
            _lastClassifiedIntent.value = classified
            return classified
        }

        // 7. Open most used app via UsageStatsManager : "ouvre mon application la plus utilisée"
        if (lower.contains("application la plus utilisée") ||
            lower.contains("app la plus utilisée") ||
            lower.contains("app favorite")
        ) {
            val topUsed = refreshUsageStatsRanking(context).firstOrNull()
            if (topUsed != null) {
                val classified = ClassifiedUserIntent(
                    rawUserCommand = trimmed,
                    intentType = LiaClassifiedIntentType.DIRECT_APP_LAUNCH,
                    systemIntentAction = Intent.ACTION_MAIN,
                    deepLinkUri = topUsed.packageName,
                    targetPackage = topUsed.packageName,
                    resolvedAppLabel = topUsed.appLabel,
                    extractedParameters = mapOf(
                        "package" to topUsed.packageName,
                        "foreground_ms" to topUsed.totalTimeInForegroundMs.toString()
                    ),
                    bypassConfirmationMenu = true,
                    usageStatsPriorityScore = topUsed.combinedUsageScore,
                    summaryMessageFr = "⚡ J'ouvre votre application la plus utilisée : **${topUsed.appLabel}**."
                )
                _lastClassifiedIntent.value = classified
                return classified
            }
        }

        // 8. DIRECT_APP_LAUNCH : "ouvre YouTube", "lance Spotify", "ouvre Gmail", "ouvre WhatsApp"
        // (Exclut strictement les demandes de conversation, de message, de notifications ou de navigation multi-étapes)
        val isSimpleOpenAppVerb = (lower.startsWith("ouvre ") || lower.startsWith("ouvrir ") || lower.startsWith("lance ") || lower.startsWith("lancer ")) &&
            !lower.contains("conversation") &&
            !lower.contains("discussion") &&
            !lower.contains("message") &&
            !lower.contains("avec ") &&
            !lower.contains("pour ") &&
            !lower.contains("dans ") &&
            !lower.contains(" et ") &&
            !lower.contains(" puis ") &&
            !lower.contains("cherche") &&
            !lower.contains("trouve") &&
            !lower.contains("regarde") &&
            !lower.contains("vérifie") &&
            !lower.contains("verifie") &&
            !lower.contains("inspecte") &&
            !lower.contains("explore") &&
            !lower.contains("clique") &&
            !lower.contains("scrolle") &&
            !lower.contains("écris") &&
            !lower.contains("ecris") &&
            !lower.contains("tape") &&
            !lower.contains("lis") &&
            !lower.contains("combien") &&
            !lower.contains("notifications") &&
            !lower.contains("une application") &&
            !lower.contains("une app") &&
            !lower.contains("habitude") &&
            !lower.contains("habituelle") &&
            !lower.contains("dernière") &&
            !lower.contains("?")

        if (isSimpleOpenAppVerb) {
            val rawAppName = trimmed
                .replace(Regex("^(?:ouvre l'application|ouvrir l'application|ouvre l'app|ouvrir l'app|ouvre mon application|ouvre mon app|ouvre l'appli|ouvrir l'appli|ouvre mon appli|ouvre|ouvrir|lance|lancer)\\s+(?:les\\s+|le\\s+|la\\s+|l'|mon\\s+|ma\\s+|mes\\s+)?", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*[?!.…]+$"), "")
                .trim()
            val knownAppMap = mapOf(
                "paramètres" to ("Paramètres" to "com.android.settings"),
                "parametres" to ("Paramètres" to "com.android.settings"),
                "réglages" to ("Paramètres" to "com.android.settings"),
                "reglages" to ("Paramètres" to "com.android.settings"),
                "settings" to ("Paramètres" to "com.android.settings"),
                "youtube" to ("YouTube" to "com.google.android.youtube"),
                "spotify" to ("Spotify" to "com.spotify.music"),
                "gmail" to ("Gmail" to "com.google.android.gm"),
                "whatsapp" to ("WhatsApp" to "com.whatsapp"),
                "telegram" to ("Telegram" to "org.telegram.messenger"),
                "instagram" to ("Instagram" to "com.instagram.android"),
                "discord" to ("Discord" to "com.discord"),
                "chrome" to ("Google Chrome" to "com.android.chrome"),
                "google chrome" to ("Google Chrome" to "com.android.chrome"),
                "google maps" to ("Google Maps" to "com.google.android.apps.maps"),
                "maps" to ("Google Maps" to "com.google.android.apps.maps"),
                "google agenda" to ("Google Agenda" to "com.google.android.calendar"),
                "agenda" to ("Google Agenda" to "com.google.android.calendar"),
                "calendrier" to ("Google Agenda" to "com.google.android.calendar"),
                "google drive" to ("Google Drive" to "com.google.android.apps.docs"),
                "drive" to ("Google Drive" to "com.google.android.apps.docs"),
                "google keep" to ("Google Keep" to "com.google.android.keep"),
                "keep" to ("Google Keep" to "com.google.android.keep"),
                "photos" to ("Google Photos" to "com.google.android.apps.photos"),
                "google photos" to ("Google Photos" to "com.google.android.apps.photos"),
                "contacts" to ("Contacts" to "com.google.android.contacts")
            )
            val resolvedPair = knownAppMap[rawAppName.lowercase(Locale.FRENCH)]
                ?: LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp(rawAppName)?.let { it.appLabel to it.packageName }

            if (resolvedPair != null) {
                val (appLabel, pkg) = resolvedPair
                val classified = ClassifiedUserIntent(
                    rawUserCommand = trimmed,
                    intentType = LiaClassifiedIntentType.DIRECT_APP_LAUNCH,
                    systemIntentAction = Intent.ACTION_MAIN,
                    deepLinkUri = pkg,
                    targetPackage = pkg,
                    resolvedAppLabel = appLabel,
                    extractedParameters = mapOf("package" to pkg, "app_label" to appLabel),
                    bypassConfirmationMenu = true,
                    summaryMessageFr = "⚡ J'ouvre **$appLabel** instantanément."
                )
                _lastClassifiedIntent.value = classified
                return classified
            }
        }

        return null
    }

    /**
     * Construit un `ClassifiedUserIntent` prêt pour `AppActionExecutor` à partir d'une cible
     * `packageNameOrUri` déjà résolue par `LiaAssistantBrain`.
     */
    fun buildIntentSpecFromTarget(
        appLabel: String,
        packageNameOrUri: String,
        rawCommand: String = appLabel,
        context: Context? = LiaMemoryAndHistoryStore.getApplicationContextOrNull()
    ): ClassifiedUserIntent {
        val ranked = refreshUsageStatsRanking(context)
        val usageMatch = ranked.firstOrNull { it.packageName.equals(packageNameOrUri, ignoreCase = true) }
        val score = usageMatch?.combinedUsageScore ?: 1.0f

        val (type, sysAction) = when {
            packageNameOrUri.startsWith("flashlight:") ->
                LiaClassifiedIntentType.TOGGLE_FLASHLIGHT to "FLASHLIGHT_TOGGLE"
            packageNameOrUri.startsWith("tel:") ->
                LiaClassifiedIntentType.DIAL_PHONE to Intent.ACTION_DIAL
            packageNameOrUri.startsWith("camera:") ->
                LiaClassifiedIntentType.OPEN_CAMERA to MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
            packageNameOrUri.startsWith("mailto:") ->
                LiaClassifiedIntentType.SEND_EMAIL to Intent.ACTION_SENDTO
            packageNameOrUri.startsWith("alarm:") ->
                LiaClassifiedIntentType.SET_ALARM to AlarmClock.ACTION_SET_ALARM
            packageNameOrUri.startsWith("timer:") ->
                LiaClassifiedIntentType.SET_TIMER to AlarmClock.ACTION_SET_TIMER
            packageNameOrUri.startsWith("geo:") || packageNameOrUri.startsWith("google.navigation:") ->
                LiaClassifiedIntentType.MAPS_NAVIGATION to Intent.ACTION_VIEW
            packageNameOrUri.startsWith("calendar_insert:") ->
                LiaClassifiedIntentType.ADD_CALENDAR_EVENT to Intent.ACTION_INSERT
            packageNameOrUri.startsWith("settings:") ->
                LiaClassifiedIntentType.OPEN_SYSTEM_SETTINGS to packageNameOrUri.removePrefix("settings:")
            packageNameOrUri.startsWith("media_play:") ->
                LiaClassifiedIntentType.PLAY_MEDIA to MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
            packageNameOrUri.startsWith("market://") ->
                LiaClassifiedIntentType.PLAY_STORE_SEARCH to Intent.ACTION_VIEW
            packageNameOrUri.startsWith("share_text:") ->
                LiaClassifiedIntentType.SHARE_CONTENT to Intent.ACTION_SEND
            packageNameOrUri.startsWith("app_msg:") ->
                LiaClassifiedIntentType.SEND_MESSAGE to Intent.ACTION_SEND
            packageNameOrUri.startsWith("app_search:") ->
                LiaClassifiedIntentType.DEEP_APP_SEARCH to Intent.ACTION_SEARCH
            packageNameOrUri.contains("youtube.com") ->
                LiaClassifiedIntentType.YOUTUBE_DEEP_LINK to Intent.ACTION_VIEW
            packageNameOrUri.startsWith("http://") || packageNameOrUri.startsWith("https://") ->
                LiaClassifiedIntentType.WEB_SEARCH_DEEP_LINK to Intent.ACTION_VIEW
            else ->
                LiaClassifiedIntentType.DIRECT_APP_LAUNCH to Intent.ACTION_MAIN
        }

        val resolvedPkg = when {
            packageNameOrUri.contains(":") && !packageNameOrUri.startsWith("http") -> ""
            else -> packageNameOrUri
        }

        val spec = ClassifiedUserIntent(
            rawUserCommand = rawCommand,
            intentType = type,
            systemIntentAction = sysAction,
            deepLinkUri = packageNameOrUri,
            targetPackage = resolvedPkg,
            resolvedAppLabel = appLabel.ifBlank { usageMatch?.appLabel ?: packageNameOrUri },
            extractedParameters = mapOf("target" to packageNameOrUri),
            bypassConfirmationMenu = true,
            usageStatsPriorityScore = score,
            summaryMessageFr = "⚡ Ouverture directe de **$appLabel**"
        )
        _lastClassifiedIntent.value = spec
        return spec
    }
}
