package com.example.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.data.LiaGoogleCloudWorkspaceClient
import com.example.data.LiaMemoryAndHistoryStore
import com.example.data.LiaUniversalAppsAndGoogleHub
import com.example.security.SafeContactResolver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MessagingAppChannel(
    val id: String,
    val displayName: String,
    val emoji: String,
    val packageName: String,
    val fallbackWebUrl: String
) {
    MESSENGER(
        id = "messenger",
        displayName = "Messenger",
        emoji = "💙",
        packageName = "com.facebook.orca",
        fallbackWebUrl = "https://www.messenger.com"
    ),
    WHATSAPP(
        id = "whatsapp",
        displayName = "WhatsApp",
        emoji = "🟢",
        packageName = "com.whatsapp",
        fallbackWebUrl = "https://wa.me/"
    ),
    TELEGRAM(
        id = "telegram",
        displayName = "Telegram",
        emoji = "✈️",
        packageName = "org.telegram.messenger",
        fallbackWebUrl = "https://web.telegram.org"
    ),
    INSTAGRAM(
        id = "instagram",
        displayName = "Instagram",
        emoji = "📸",
        packageName = "com.instagram.android",
        fallbackWebUrl = "https://www.instagram.com/direct/inbox/"
    ),
    DISCORD(
        id = "discord",
        displayName = "Discord",
        emoji = "🎮",
        packageName = "com.discord",
        fallbackWebUrl = "https://discord.com/app"
    ),
    SIGNAL(
        id = "signal",
        displayName = "Signal",
        emoji = "🔵",
        packageName = "org.thoughtcrime.securesms",
        fallbackWebUrl = "https://signal.org"
    ),
    GOOGLE_KEEP(
        id = "keep",
        displayName = "Google Keep",
        emoji = "💡",
        packageName = "com.google.android.keep",
        fallbackWebUrl = "https://keep.google.com"
    ),
    SMS(
        id = "sms",
        displayName = "Messages (SMS)",
        emoji = "💬",
        packageName = "com.google.android.apps.messaging",
        fallbackWebUrl = ""
    )
}

data class CrossAppSearchResult(
    val query: String,
    val matchedContacts: List<ContactFullRecord> = emptyList(),
    val matchedCalendarEvents: List<String> = emptyList(),
    val matchedKeepAndNotes: List<String> = emptyList(),
    val matchedDriveFiles: List<String> = emptyList(),
    val matchedEmails: List<String> = emptyList(),
    val matchedInstalledApps: List<Pair<String, String>> = emptyList()
)

data class ContactFullRecord(
    val displayName: String,
    val phoneNumbers: List<String>,
    val emails: List<String> = emptyList(),
    val hasWhatsApp: Boolean = false,
    val hasMessenger: Boolean = false,
    val noteOrOrg: String = ""
)

/**
 * Moteur transversal d'intelligence et de recherche multi-applications (`LiaCrossAppKnowledgeResolver`) :
 * - Détecte intelligemment le canal de messagerie demandé (Messenger, WhatsApp, Telegram, Instagram, Discord, Signal, SMS, Gmail)
 *   pour ne JAMAIS forcer un SMS quand l'utilisateur demande Messenger ou WhatsApp.
 * - Recherche les numéros de téléphone, contacts, événements Calendar, notes Google Keep / Room,
 *   fichiers Google Drive, e-mails Gmail et toutes les applications installées sur le téléphone.
 */
object LiaCrossAppKnowledgeResolver {

    /**
     * Détecte si l'utilisateur mentionne une application de messagerie tierce (Messenger, WhatsApp, Telegram, Instagram, Discord, Signal)
     * ou Google Keep.
     */
    fun detectMessagingChannel(input: String): MessagingAppChannel? {
        val lower = input.lowercase(Locale.FRENCH)
        return when {
            lower.contains("messenger") || lower.contains("facebook") || lower.contains("fb") ->
                MessagingAppChannel.MESSENGER
            lower.contains("whatsapp") || lower.contains("whats app") || lower.contains("what's app") ->
                MessagingAppChannel.WHATSAPP
            lower.contains("telegram") || lower.contains("télégram") ->
                MessagingAppChannel.TELEGRAM
            lower.contains("instagram") || lower.contains("insta") ->
                MessagingAppChannel.INSTAGRAM
            lower.contains("discord") ->
                MessagingAppChannel.DISCORD
            lower.contains("signal") ->
                MessagingAppChannel.SIGNAL
            lower.contains("google keep") || lower.contains("keep") ->
                MessagingAppChannel.GOOGLE_KEEP
            lower.contains("sms") || lower.contains("texto") ->
                MessagingAppChannel.SMS
            else -> null
        }
    }

    /**
     * Envoie ou prépare un message directement dans l'application choisie (Messenger, WhatsApp, Telegram, Instagram, etc.)
     * via `Intent.ACTION_SEND` + `PackageManager` ou Deep Link (`whatsapp://send`, `fb-messenger://share`, etc.).
     */
    fun launchMessagingAppWithPayload(
        context: Context,
        channel: MessagingAppChannel,
        recipientNameOrPhone: String,
        messageText: String
    ): Boolean {
        val pm = context.packageManager
        val cleanPhone = recipientNameOrPhone.replace(Regex("[^+0-9]"), "")
        val resolvedContact = if (cleanPhone.length < 6 && recipientNameOrPhone.isNotBlank()) {
            findContactsMatching(context, recipientNameOrPhone).firstOrNull()
        } else null
        val effectivePhone = cleanPhone.ifBlank {
            resolvedContact?.phoneNumbers?.firstOrNull()?.replace(Regex("[^+0-9]"), "").orEmpty()
        }

        return runCatching {
            when (channel) {
                MessagingAppChannel.WHATSAPP -> {
                    // Si on a le numéro du contact et un message, on ouvre directement la conversation WhatsApp du contact !
                    if (effectivePhone.isNotBlank()) {
                        val intlPhone = if (effectivePhone.startsWith("0") && effectivePhone.length == 10) {
                            "33${effectivePhone.drop(1)}"
                        } else {
                            effectivePhone.removePrefix("+")
                        }
                        val waUri = if (messageText.isNotBlank()) {
                            Uri.parse("https://api.whatsapp.com/send?phone=$intlPhone&text=${Uri.encode(messageText)}")
                        } else {
                            Uri.parse("https://api.whatsapp.com/send?phone=$intlPhone")
                        }
                        val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                            if (isPackageInstalled(pm, channel.packageName)) {
                                setPackage(channel.packageName)
                            }
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(waIntent)
                        return true
                    }
                    // Sinon partage direct du texte dans WhatsApp
                    if (messageText.isNotBlank() && isPackageInstalled(pm, channel.packageName)) {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            setPackage(channel.packageName)
                            putExtra(Intent.EXTRA_TEXT, messageText)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(sendIntent)
                        return true
                    }
                    openAppOrFallbackWeb(context, channel.packageName, channel.fallbackWebUrl, channel.displayName)
                }

                MessagingAppChannel.MESSENGER -> {
                    if (isPackageInstalled(pm, channel.packageName)) {
                        if (messageText.isNotBlank()) {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                setPackage(channel.packageName)
                                putExtra(Intent.EXTRA_TEXT, messageText)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            val resolved = pm.resolveActivity(sendIntent, PackageManager.MATCH_DEFAULT_ONLY)
                            if (resolved != null) {
                                context.startActivity(sendIntent)
                                LiaAppUsageTrackerService.recordAppOpenEvent(context, channel.packageName, channel.displayName)
                                return true
                            }
                        }
                        val launch = pm.getLaunchIntentForPackage(channel.packageName)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        if (launch != null) {
                            context.startActivity(launch)
                            LiaAppUsageTrackerService.recordAppOpenEvent(context, channel.packageName, channel.displayName)
                            return true
                        }
                    }
                    // Fallback URI fb-messenger://threads ou Messenger Web
                    runCatching {
                        val fbIntent = Intent(Intent.ACTION_VIEW, Uri.parse("fb-messenger://threads")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(fbIntent)
                        LiaAppUsageTrackerService.recordAppOpenEvent(context, channel.packageName, channel.displayName)
                        true
                    }.getOrElse {
                        openAppOrFallbackWeb(context, channel.packageName, channel.fallbackWebUrl, channel.displayName)
                    }
                }

                MessagingAppChannel.GOOGLE_KEEP -> {
                    if (messageText.isNotBlank() && isPackageInstalled(pm, channel.packageName)) {
                        val keepIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            setPackage(channel.packageName)
                            putExtra(Intent.EXTRA_SUBJECT, "Note LIA")
                            putExtra(Intent.EXTRA_TEXT, messageText)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        if (pm.resolveActivity(keepIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                            context.startActivity(keepIntent)
                            return true
                        }
                    }
                    openAppOrFallbackWeb(context, channel.packageName, channel.fallbackWebUrl, channel.displayName)
                }

                else -> {
                    if (messageText.isNotBlank() && isPackageInstalled(pm, channel.packageName)) {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            setPackage(channel.packageName)
                            putExtra(Intent.EXTRA_TEXT, messageText)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        if (pm.resolveActivity(sendIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                            context.startActivity(sendIntent)
                            return true
                        }
                    }
                    openAppOrFallbackWeb(context, channel.packageName, channel.fallbackWebUrl, channel.displayName)
                }
            }
        }.getOrElse {
            openAppOrFallbackWeb(context, channel.packageName, channel.fallbackWebUrl, channel.displayName)
        }
    }

    private fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean {
        return runCatching {
            pm.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)
    }

    private fun openAppOrFallbackWeb(
        context: Context,
        packageName: String,
        fallbackWebUrl: String,
        appLabel: String
    ): Boolean {
        return runCatching {
            val pm = context.packageManager
            val launch = pm.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (launch != null) {
                context.startActivity(launch)
                LiaAppUsageTrackerService.recordAppOpenEvent(context, packageName, appLabel)
                true
            } else if (fallbackWebUrl.isNotBlank()) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackWebUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /**
     * Lit tous les contacts du téléphone (numéros, e-mails, comptes WhatsApp/Messenger associés)
     * + les contacts enregistrés dans Room (`lia_user_facts`) et Google Cloud Contacts API.
     */
    fun getAllKnownContacts(context: Context?): List<ContactFullRecord> {
        val mapByName = linkedMapOf<String, ContactFullRecord>()
        val snap = LiaMemoryAndHistoryStore.state.value
        val ws = LiaGoogleCloudWorkspaceClient.state.value

        // 1. Contacts réels Android via ContactsContract
        if (context != null) {
            val realDeviceContacts = SafeContactResolver.readRealAndroidContacts(context)
            for ((name, phone) in realDeviceContacts) {
                val key = name.lowercase(Locale.FRENCH)
                val existing = mapByName[key]
                val phones = ((existing?.phoneNumbers ?: emptyList()) + phone).distinct()
                mapByName[key] = ContactFullRecord(
                    displayName = existing?.displayName ?: name,
                    phoneNumbers = phones,
                    hasWhatsApp = true,
                    hasMessenger = true
                )
            }

            // Enrichissement e-mails et comptes liés si READ_CONTACTS est accordé
            val hasContactsPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasContactsPerm) {
                runCatching {
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY,
                            ContactsContract.CommonDataKinds.Email.ADDRESS
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY)
                        val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                        while (cursor.moveToNext()) {
                            val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                            val email = if (emailIdx >= 0) cursor.getString(emailIdx)?.trim().orEmpty() else ""
                            if (name.isNotBlank() && email.isNotBlank()) {
                                val key = name.lowercase(Locale.FRENCH)
                                val existing = mapByName[key]
                                mapByName[key] = ContactFullRecord(
                                    displayName = existing?.displayName ?: name,
                                    phoneNumbers = existing?.phoneNumbers ?: emptyList(),
                                    emails = ((existing?.emails ?: emptyList()) + email).distinct(),
                                    hasWhatsApp = true,
                                    hasMessenger = true
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Contacts synchronisés via Google People API (`LiaGoogleCloudWorkspaceClient`)
        for (gc in ws.syncedContacts) {
            val key = gc.displayName.lowercase(Locale.FRENCH)
            val existing = mapByName[key]
            val phones = ((existing?.phoneNumbers ?: emptyList()) + listOfNotNull(gc.phoneNumber.takeIf { it.isNotBlank() })).distinct()
            val emails = ((existing?.emails ?: emptyList()) + listOfNotNull(gc.emailAddress.takeIf { it.isNotBlank() })).distinct()
            mapByName[key] = ContactFullRecord(
                displayName = existing?.displayName ?: gc.displayName,
                phoneNumbers = phones,
                emails = emails,
                hasWhatsApp = true,
                hasMessenger = true
            )
        }

        // 3. Contacts enregistrés dans la mémoire Room LIA (`lia_user_facts`)
        for (fact in snap.userFacts) {
            if (fact.key.endsWith("_tel") || fact.label.startsWith("Numéro ", ignoreCase = true)) {
                val name = fact.label.removePrefix("Numéro ").trim().ifBlank {
                    fact.key.removeSuffix("_tel").replaceFirstChar { it.uppercase() }
                }
                val key = name.lowercase(Locale.FRENCH)
                val existing = mapByName[key]
                val phones = ((existing?.phoneNumbers ?: emptyList()) + fact.value).distinct()
                mapByName[key] = ContactFullRecord(
                    displayName = existing?.displayName ?: name,
                    phoneNumbers = phones,
                    emails = existing?.emails ?: emptyList(),
                    hasWhatsApp = true,
                    hasMessenger = true
                )
            }
        }

        return mapByName.values.toList()
    }

    /**
     * Cherche un ou plusieurs contacts par nom, prénom ou numéro partiel.
     */
    fun findContactsMatching(context: Context?, query: String): List<ContactFullRecord> {
        val all = getAllKnownContacts(context)
        val cleanQ = query.trim().lowercase(Locale.FRENCH)
        if (cleanQ.isBlank()) return all
        return all.filter { c ->
            c.displayName.lowercase(Locale.FRENCH).contains(cleanQ) ||
                cleanQ.contains(c.displayName.lowercase(Locale.FRENCH)) ||
                c.phoneNumbers.any { it.contains(cleanQ) } ||
                c.emails.any { it.lowercase(Locale.FRENCH).contains(cleanQ) }
        }
    }

    /**
     * Recherche universelle transversale dans :
     * 1. Contacts & Numéros de téléphone (Android Contacts + Google Contacts + Room)
     * 2. Google Calendar & Android Calendar Provider (`CalendarContract.Events`)
     * 3. Google Keep & Notes / Souvenirs Room (`lia_souvenirs` + `LongTermMemory`)
     * 4. Google Drive (`recentDriveFiles`)
     * 5. Gmail (`recentEmails`)
     * 6. Applications installées sur le téléphone (WhatsApp, Messenger, Keep, Drive, etc.)
     */
    fun searchAcrossAllPhoneAppsAndData(
        context: Context?,
        rawQuery: String
    ): CrossAppSearchResult {
        val snap = LiaMemoryAndHistoryStore.state.value
        val ws = LiaGoogleCloudWorkspaceClient.state.value

        // Nettoyage des mots vides de recherche pour extraire le sujet recherché
        val cleanedTopic = rawQuery
            .replace(
                Regex(
                    "^(?:trouve|trouver|cherche|chercher|quels?\\s+sont|quel\\s+est|montre|affiche|lis|lire|donne-moi|donne|dans\\s+mes|sur\\s+mon|mes|mon|ma|le|la|les)\\s+",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(
                Regex(
                    "\\s*(?:dans|sur)\\s+(?:calendar|google\\s+calendar|l'agenda|agenda|google\\s+keep|keep|google\\s+drive|drive|messenger|whatsapp|mes\\s+applications|mon\\s+téléphone).*$",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()

        val tokens = cleanedTopic.lowercase(Locale.FRENCH)
            .split(Regex("[^a-zà-ÿ0-9]+"))
            .filter { it.length >= 2 && it !in setOf("numéro", "numero", "numéros", "numeros", "info", "infos", "information", "informations", "contact", "contacts") }

        // 1. Contacts & Numéros
        val allContacts = getAllKnownContacts(context)
        val matchedContacts = if (tokens.isEmpty()) {
            allContacts
        } else {
            allContacts.filter { c ->
                tokens.any { tok ->
                    c.displayName.lowercase(Locale.FRENCH).contains(tok) ||
                        c.phoneNumbers.any { it.contains(tok) } ||
                        c.emails.any { it.lowercase(Locale.FRENCH).contains(tok) }
                }
            }.ifEmpty { allContacts.take(8) }
        }

        // 2. Événements Calendar (Android CalendarContract + Google Calendar Cloud API + Souvenirs Room EVENEMENT)
        val calEvents = mutableListOf<String>()
        ws.upcomingEvents.forEach { ev ->
            calEvents.add("📅 **${ev.title}** — ${ev.startFormatted}${if (ev.locationOrDescription.isNotBlank()) " (${ev.locationOrDescription})" else ""}")
        }
        if (context != null) {
            val hasCalPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
            if (hasCalPerm) {
                runCatching {
                    val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.FRENCH)
                    context.contentResolver.query(
                        CalendarContract.Events.CONTENT_URI,
                        arrayOf(
                            CalendarContract.Events.TITLE,
                            CalendarContract.Events.DTSTART,
                            CalendarContract.Events.EVENT_LOCATION,
                            CalendarContract.Events.DESCRIPTION
                        ),
                        null,
                        null,
                        "${CalendarContract.Events.DTSTART} DESC"
                    )?.use { cursor ->
                        val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                        val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                        val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                        var count = 0
                        while (cursor.moveToNext() && count < 10) {
                            val t = if (titleIdx >= 0) cursor.getString(titleIdx)?.trim().orEmpty() else ""
                            val startMs = if (startIdx >= 0) cursor.getLong(startIdx) else 0L
                            val loc = if (locIdx >= 0) cursor.getString(locIdx)?.trim().orEmpty() else ""
                            if (t.isNotBlank()) {
                                val dateStr = if (startMs > 0) fmt.format(Date(startMs)) else ""
                                val entry = "📅 **$t**${if (dateStr.isNotBlank()) " ($dateStr)" else ""}${if (loc.isNotBlank()) " • $loc" else ""}"
                                if (entry !in calEvents) {
                                    calEvents.add(entry)
                                    count++
                                }
                            }
                        }
                    }
                }
            }
        }
        snap.souvenirs.filter { it.type == "EVENEMENT" }.forEach { souv ->
            calEvents.add("📅 **${souv.title}** : ${souv.detailValue}")
        }
        val filteredCal = if (tokens.isEmpty()) calEvents else {
            calEvents.filter { line -> tokens.any { line.lowercase(Locale.FRENCH).contains(it) } }.ifEmpty { calEvents.take(5) }
        }

        // 3. Google Keep & Notes / Souvenirs Room
        val notesAndKeep = mutableListOf<String>()
        snap.souvenirs.filter { it.type != "EVENEMENT" }.forEach { souv ->
            notesAndKeep.add("💡 **[${souv.type}] ${souv.title}** : ${souv.detailValue}")
        }
        snap.longTermMemories.forEach { mem ->
            notesAndKeep.add("🧠 **${mem.title}** : ${mem.contentValue}")
        }
        val filteredKeep = if (tokens.isEmpty()) notesAndKeep else {
            notesAndKeep.filter { line -> tokens.any { line.lowercase(Locale.FRENCH).contains(it) } }.ifEmpty { notesAndKeep.take(6) }
        }

        // 4. Google Drive
        val driveList = ws.recentDriveFiles.map { "📁 **${it.name}** (${it.mimeType.substringAfterLast('.')} • ${it.modifiedTime})" }
        val filteredDrive = if (tokens.isEmpty()) driveList else {
            driveList.filter { line -> tokens.any { line.lowercase(Locale.FRENCH).contains(it) } }.ifEmpty { driveList.take(5) }
        }

        // 5. Gmail
        val emailList = ws.recentEmails.map { "📧 **${it.subject}** (de *${it.from.substringBefore("<").trim()}*) — ${it.snippet.take(65)}" }
        val filteredEmails = if (tokens.isEmpty()) emailList else {
            emailList.filter { line -> tokens.any { line.lowercase(Locale.FRENCH).contains(it) } }.ifEmpty { emailList.take(5) }
        }

        // 6. Applications installées correspondantes
        val installedApps = LiaUniversalAppsAndGoogleHub.state.value.installedPhoneApps
        val matchedApps = if (tokens.isEmpty()) {
            installedApps.take(8).map { "${it.emoji} ${it.appLabel}" to it.packageName }
        } else {
            installedApps.filter { app ->
                tokens.any { tok ->
                    app.appLabel.lowercase(Locale.FRENCH).contains(tok) ||
                        app.packageName.lowercase(Locale.FRENCH).contains(tok)
                }
            }.map { "${it.emoji} ${it.appLabel}" to it.packageName }
        }

        return CrossAppSearchResult(
            query = cleanedTopic,
            matchedContacts = matchedContacts,
            matchedCalendarEvents = filteredCal,
            matchedKeepAndNotes = filteredKeep,
            matchedDriveFiles = filteredDrive,
            matchedEmails = filteredEmails,
            matchedInstalledApps = matchedApps
        )
    }
}
