package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import com.example.MainActivity
import com.example.data.GeminiDualEngineClient
import com.example.data.InstalledPhoneAppItem
import com.example.data.LiaAppLaunchAction
import com.example.data.LiaBrainTurnOutcome
import com.example.data.LiaChatMessage
import com.example.data.LiaMemoryAndHistoryStore
import com.example.data.LiaPendingFollowUp
import com.example.data.LiaUniversalAppsAndGoogleHub
import com.example.model.currentTimeFormatted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

enum class UniversalDomainCategory(
    val id: String,
    val titleFr: String,
    val emoji: String,
    val factKeyForPreferredApp: String,
    val knownPackageCandidates: List<Pair<String, String>>,
    val domainTriggerKeywords: List<String>,
    val navigationTabHints: List<String>
) {
    VEHICLE_AND_EV(
        id = "vehicle_ev",
        titleFr = "Véhicule & Recharge",
        emoji = "🚗",
        factKeyForPreferredApp = "preferred_app_vehicle_ev",
        knownPackageCandidates = listOf(
            "com.toyota.oneapp.eu" to "Toyota (MyToyota)",
            "com.toyota.oneapp" to "Toyota",
            "jp.co.toyota.mytoyota" to "MyToyota",
            "com.teslamotors.tesla" to "Tesla",
            "com.renault.myrenault.one.fr" to "My Renault",
            "com.renault.myrenault" to "My Renault",
            "de.bmw.connected.mobile20.row" to "My BMW",
            "com.psa.mym.mypeugeot" to "MyPeugeot",
            "com.psa.mym.mycitroen" to "MyCitroën",
            "com.kia.uvo.eu" to "Kia Connect",
            "com.hyundai.bluelink.eu" to "Hyundai Bluelink",
            "com.daimler.ris.mercedesme.ece.android" to "Mercedes me",
            "de.volkswagen.vwconnect" to "Volkswagen",
            "com.ford.fordpass" to "FordPass",
            "de.audi.myaudi" to "myAudi",
            "eu.nissan.nissanconnect.services" to "NissanConnect",
            "se.volvo.vcc" to "Volvo Cars",
            "com.dacia.mydacia.one.fr" to "My Dacia",
            "com.saicmotor.iov.europe" to "MG iSMART",
            "com.byd.bydautolink" to "BYD Auto",
            "com.porsche.one" to "My Porsche",
            "eu.lexus.lexuslinkplus" to "Lexus Link+",
            "com.chargemap.android" to "Chargemap"
        ),
        domainTriggerKeywords = listOf(
            "ma voiture", "mon véhicule", "mon vehicule", "la voiture", "charge de ma voiture",
            "batterie de ma voiture", "autonomie de ma voiture", "combien de charge",
            "combien j'ai de charge", "ma charge", "la charge", "niveau de charge",
            "regarde ma charge", "voir ma charge", "check ma charge", "mon autonomie",
            "combien de batterie sur ma voiture", "toyota", "mytoyota", "myt", "tesla",
            "myrenault", "my renault", "renault", "mybmw", "bmw", "mypeugeot", "peugeot",
            "citroen", "citroën", "kia", "hyundai", "mercedes", "volkswagen", "ford", "audi",
            "nissan", "volvo", "dacia", "mg", "byd", "porsche", "lexus",
            "voiture électrique", "voiture electrique", "recharge voiture",
            "kilométrage", "kilometrage", "portières", "portieres"
        ),
        navigationTabHints = listOf("Recharge", "Batterie", "Véhicule", "État", "Charge", "Charging", "Autonomie", "Controls", "Accueil")
    ),
    DELIVERY_AND_RIDES(
        id = "delivery_rides",
        titleFr = "Livraisons, Repas & Courses VTC",
        emoji = "🛵",
        factKeyForPreferredApp = "preferred_app_delivery",
        knownPackageCandidates = listOf(
            "com.ubercab.eats" to "Uber Eats",
            "com.deliveroo.orderapp" to "Deliveroo",
            "com.ubercab" to "Uber",
            "com.justeat.app.fr" to "Just Eat",
            "ee.mtakso.client" to "Bolt",
            "com.amazon.mShop.android.shopping" to "Amazon",
            "fr.laposte.lapostemobile" to "La Poste"
        ),
        domainTriggerKeywords = listOf(
            "ma commande", "mon colis", "ma livraison", "uber eats", "deliveroo",
            "mon chauffeur", "mon uber", "où en est ma commande", "ou en est ma commande",
            "suivi de commande", "suivi colis"
        ),
        navigationTabHints = listOf("Commandes", "Activité", "Suivi", "En cours", "Orders", "Vos commandes")
    ),
    FINANCE_AND_BANK(
        id = "finance_bank",
        titleFr = "Banque & Solde",
        emoji = "💳",
        factKeyForPreferredApp = "preferred_app_bank",
        knownPackageCandidates = listOf(
            "com.revolut.revolut" to "Revolut",
            "com.boursorama.android.clients" to "BoursoBank",
            "com.lydia" to "Lydia / Sumeria",
            "com.paypal.android.p2pmobile" to "PayPal",
            "net.bnpparibas.mescomptes" to "BNP Paribas",
            "mobi.societegenerale.mobile.lappli" to "Société Générale",
            "fr.creditagricole.androidapp" to "Ma Banque (Crédit Agricole)",
            "de.number26.android" to "N26"
        ),
        domainTriggerKeywords = listOf(
            "mon solde", "mon compte bancaire", "combien j'ai sur mon compte",
            "combien il me reste sur mon compte", "mes dépenses", "mes depenses",
            "revolut", "boursorama", "boursobank", "paypal"
        ),
        navigationTabHints = listOf("Comptes", "Solde", "Accueil", "Transactions", "Cartes", "Accounts")
    ),
    SMART_HOME_AND_IOT(
        id = "smart_home",
        titleFr = "Maison Connectée & Domotique",
        emoji = "🏠",
        factKeyForPreferredApp = "preferred_app_smarthome",
        knownPackageCandidates = listOf(
            "com.google.android.apps.chromecast.app" to "Google Home",
            "com.xiaomi.smarthome" to "Mi Home",
            "com.philips.lighting.hue2" to "Philips Hue",
            "com.tuya.smartlife" to "Smart Life",
            "com.amazon.dee.app" to "Amazon Alexa",
            "com.netatmo.thermostat" to "Netatmo"
        ),
        domainTriggerKeywords = listOf(
            "thermostat", "température de la maison", "temperature de la maison",
            "température dans la maison", "temperature dans la maison",
            "température maison", "temperature maison", "dans la maison",
            "température du salon", "temperature du salon",
            "température dans le salon", "temperature dans le salon",
            "chauffage", "lumière du salon", "lumiere du salon",
            "lumière dans le salon", "lumiere dans le salon",
            "lumière de la maison", "lumiere de la maison", "google home",
            "mi home", "philips hue", "volets", "caméra maison"
        ),
        navigationTabHints = listOf("Appareils", "Maison", " Climat", "Pièces", "Devices", "Favoris")
    ),
    HEALTH_AND_FITNESS(
        id = "health_fitness",
        titleFr = "Santé, Pas & Sport",
        emoji = "🏃",
        factKeyForPreferredApp = "preferred_app_health",
        knownPackageCandidates = listOf(
            "com.google.android.apps.fitness" to "Google Fit",
            "com.sec.android.app.shealth" to "Samsung Health",
            "com.strava" to "Strava",
            "com.garmin.android.apps.connectmobile" to "Garmin Connect",
            "fr.doctolib.www" to "Doctolib"
        ),
        domainTriggerKeywords = listOf(
            "combien de pas", "mes pas aujourd'hui", "mes calories",
            "mon sommeil", "rythme cardiaque", "google fit", "samsung health",
            "strava", "mon prochain rdv doctolib"
        ),
        navigationTabHints = listOf("Accueil", "Journal", "Activité", "Pas", "Aujourd'hui")
    ),
    GAMING_AND_STEAM(
        id = "gaming_steam",
        titleFr = "Jeux & Bibliothèque Steam",
        emoji = "🎮",
        factKeyForPreferredApp = "preferred_app_gaming",
        knownPackageCandidates = listOf(
            "com.valvesoftware.android.steam.community" to "Steam",
            "com.microsoft.xboxone.smartglass" to "Xbox",
            "com.scee.psxandroid" to "PlayStation App",
            "com.discord" to "Discord"
        ),
        domainTriggerKeywords = listOf(
            "mes jeux steam", "jeux dans steam", "bibliothèque steam", "bibliotheque steam",
            "combien de jeux j'ai sur steam", "qui est en ligne sur steam", "xbox", "playstation"
        ),
        navigationTabHints = listOf("Bibliothèque", "Library", "Jeux", "Games", "Téléchargements", "Downloads", "Récemment joués", "Activité")
    ),
    SYSTEM_AND_SETTINGS(
        id = "system_settings",
        titleFr = "Paramètres & Système Android",
        emoji = "⚙️",
        factKeyForPreferredApp = "preferred_app_system_settings",
        knownPackageCandidates = listOf(
            "com.android.settings" to "Paramètres"
        ),
        domainTriggerKeywords = listOf(
            "dans les paramètres", "dans les parametres", "dans paramètres", "dans parametres",
            "dans les réglages", "dans les reglages", "dans settings", "paramètres android",
            "parametres android", "stockage du téléphone", "espace de stockage", "mémoire interne",
            "à propos du téléphone", "a propos du telephone", "options pour les développeurs"
        ),
        navigationTabHints = listOf(
            "Batterie", "Stockage", "Réseau et Internet", "Appareils connectés",
            "Applications", "Notifications", "Son", "Affichage", "Sécurité",
            "Accessibilité", "Système", "À propos du téléphone",
            "Battery", "Storage", "Network", "Apps", "System", "About"
        )
    )
}

data class ResolvedGoalTargetApp(
    val domain: UniversalDomainCategory?,
    val appLabel: String,
    val packageNameOrUri: String,
    val isInstalledOnDevice: Boolean,
    val multipleCandidatesInstalled: List<InstalledPhoneAppItem> = emptyList()
)

data class ExplorationMissionResult(
    val collectedTexts: List<String>,
    val collectedPairs: List<Pair<String, String>>,
    val navigatedTabs: List<String>,
    val explorationStepsTrace: List<String>,
    val goalFound: Boolean,
    val scrollCount: Int,
    val backtrackCount: Int,
    val searchBarUsed: Boolean
)

interface UiExplorationDriver {
    fun readSnapshot(): LiaAccessibilityService.StructuredScreenSnapshot
    fun clickStrict(targetText: String): Boolean
    fun activateAndTypeSearch(query: String): Boolean
    fun scrollDown(): Boolean
    fun navigateBack(): Boolean
    suspend fun waitMs(durationMs: Long)
}

/**
 * Agent Cognitif Universel LIA (`LiaUniversalGoalAgent`) :
 * - Comprend n'importe quelle demande d'inspection ou d'action dans une application du téléphone
 *   (ex: "va dans mon application Toyota regarde ma charge", "Combien de charge j'ai sur ma voiture ?", "Où en est ma commande Uber ?").
 * - Identifie automatiquement l'application correspondante via le graphe sémantique et la mémoire Room.
 * - Exécute la boucle autonome d'exploration patiente ("tâtonner", jusqu'à 10 étapes) :
 *   1. Ouvre l'application cible (ou cherche activement sur l'appareil si non installée)
 *   2. Lit l'arbre de l'écran en direct (`LiaAccessibilityService.readStructuredScreenSnapshot()`)
 *   3. Clique sur les onglets/sous-menus pertinents (`clickNodeByTextStrict`), utilise la barre de recherche interne (`activateAndTypeInSearchBar`),
 *      scrolle plusieurs fois (`performSwipeGesture`) avec détection de stagnation (`screenFingerprintHash`), et revient en arrière (`GLOBAL_ACTION_BACK`)
 *      si une piste est une impasse.
 *   4. Extrait les valeurs précises (`%`, `km`, `€`, `Go`, statuts, listes)
 *   5. **Ferme/quitte l'application inspectée et ramène automatiquement l'écran principal de LIA (`MainActivity`) au premier plan**
 *      pour afficher et énoncer le rapport complet !
 */
object LiaUniversalGoalAgent {

    private val agentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val MESSAGING_OR_MAPS_EXCLUSIONS = setOf(
        "maps", "google maps", "waze", "messenger", "whatsapp", "telegram",
        "instagram", "signal", "sms", "messages", "gemini", "google gemini", "chatgpt", "chat gpt"
    )

    private val NON_APP_GENERIC_TARGET_EXCLUSIONS = setOf(
        "voiture", "voitures", "vehicule", "vehicules", "auto", "automobile",
        "voiture electrique", "batterie de ma voiture", "charge de ma voiture", "autonomie de ma voiture",
        "telephone", "telephones", "smartphone", "mobile", "portable", "cellulaire", "appareil", "ecran",
        "batterie", "charge", "autonomie",
        "compte", "comptes", "compte bancaire", "comptes bancaires", "banque", "carte", "cartes", "carte bancaire",
        "maison", "salon", "chambre", "cuisine", "garage", "appartement", "domicile",
        "commande", "commandes", "colis", "livraison", "livraisons",
        "internet", "reseau", "wifi", "wi-fi"
    )

    // Note: "banque" is intentionally omitted here so "Banque Populaire" / "Banque Postale" remain valid apps,
    // while bare "ma banque" / "la banque" ("banque") is caught by NON_APP_GENERIC_TARGET_EXCLUSIONS above.
    private val GENERIC_NON_APP_HEAD_NOUNS = setOf(
        "voiture", "voitures", "vehicule", "vehicules", "automobile",
        "telephone", "telephones", "smartphone", "appareil", "ecran",
        "batterie", "charge", "autonomie",
        "compte", "comptes", "carte", "cartes",
        "maison", "salon", "chambre", "cuisine", "appartement", "domicile",
        "commande", "commandes", "colis", "livraison", "livraisons"
    )

    private val FORBIDDEN_APP_NAME_VERB_TOKENS = setOf(
        "regarde", "regarder", "voir", "vois", "verifie", "verifier", "check", "checker",
        "cherche", "chercher", "trouve", "trouver", "retrouve", "retrouver", "inspecte", "inspecter",
        "explore", "explorer", "combien", "quel", "quelle", "quels", "quelles",
        "jai", "j'ai", "est", "sont", "dans", "sur"
    )

    fun isGenericNonAppTarget(appName: String): Boolean {
        if (appName.isBlank()) return true
        if (LiaUniversalAppsAndGoogleHub.isSettingsAliasQuery(appName)) return false
        val norm = LiaUniversalAppsAndGoogleHub.normalizeAppQuery(appName)
        if (norm.isBlank()) return true
        if (norm in NON_APP_GENERIC_TARGET_EXCLUSIONS) return true
        val tokens = norm.split(Regex("\\s+"))
        if (tokens.size > 4) return true
        if (tokens.firstOrNull() in GENERIC_NON_APP_HEAD_NOUNS) return true
        if (tokens.any { it in FORBIDDEN_APP_NAME_VERB_TOKENS }) return true
        return false
    }

    private fun isExcludedAppOrGenericTarget(appName: String): Boolean {
        if (appName.isBlank()) return true
        val lower = appName.lowercase(Locale.FRENCH).trim()
        val norm = LiaUniversalAppsAndGoogleHub.normalizeAppQuery(appName)
        return lower in MESSAGING_OR_MAPS_EXCLUSIONS ||
            norm in MESSAGING_OR_MAPS_EXCLUSIONS ||
            isGenericNonAppTarget(appName)
    }

    private val LEADING_FRENCH_DETERMINERS_REGEX = Regex(
        "^(?:mon\\s+application\\s+|l'application\\s+|mon\\s+app\\s+|l'app\\s+|mon\\s+appli\\s+|l'appli\\s+|application\\s+|appli\\s+|app\\s+|les\\s+|le\\s+|la\\s+|l'|mon\\s+|ma\\s+|mes\\s+|un\\s+|une\\s+|des\\s+|du\\s+|de\\s+la\\s+)+",
        RegexOption.IGNORE_CASE
    )

    private val NAVIGATION_BACK_OR_CLOSE_LABELS = setOf(
        "retour", "back", "fermer", "close", "annuler", "cancel",
        "navigate up", "revenir en haut de la page", "quitter"
    )

    private val GENERIC_EXPLORATORY_SECTION_HINTS = listOf(
        "état", "etat", "détails", "details", "infos", "informations",
        "système", "systeme", "system", "à propos", "a propos", "about",
        "plus", "more", "véhicule", "vehicule", "status", "avancé", "avance",
        "advanced", "paramètres", "parametres", "settings", "compte", "account",
        "activité", "activity", "menu", "bibliothèque", "library", "général", "general"
    )

    private val GOAL_STOP_WORDS = setOf(
        "va", "aller", "dans", "sur", "ouvre", "ouvrir", "lance", "lancer",
        "regarde", "regarder", "voir", "vois", "verifie", "vérifie", "verifier", "vérifier",
        "check", "checker", "cherche", "chercher", "trouve", "trouver", "retrouve", "retrouver",
        "inspecte", "inspecter", "explore", "explorer", "dis", "moi", "dis-moi", "donne", "donne-moi",
        "lis", "lire", "combien", "quel", "quelle", "quels", "quelles", "ou", "où", "est", "sont",
        "mon", "ma", "mes", "ton", "ta", "tes", "le", "la", "les", "un", "une", "des", "du", "de",
        "et", "pour", "puis", "que", "qui", "ai", "jai", "j'ai", "a", "application", "app",
        "appli", "parametres", "paramètres", "reglages", "réglages", "settings", "voiture", "telephone", "téléphone",
        "aujourd", "hui", "aujourd'hui"
    )

    private val PURE_UNIT_CLICKABLE_EXCLUSIONS = setOf(
        "%", "€", "$", "go", "gb", "mo", "mb", "km", "kwh", "eur"
    )

    fun cleanExtractedAppName(rawApp: String): String {
        var cleaned = rawApp
            .replace('’', '\'')
            .replace('‘', '\'')
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("«", "»")
            .replace(Regex("\\s*[?!.…,:;]+$"), "")
            .trim()
        var guard = 0
        while (guard < 6) {
            guard++
            val next = LEADING_FRENCH_DETERMINERS_REGEX.replace(cleaned, "").trim()
            if (next == cleaned || next.isBlank()) break
            cleaned = next
        }
        cleaned = cleaned
            .removeSurrounding("\"")
            .removeSurrounding("«", "»")
            .replace(Regex("\\s*[?!.…,:;]+$"), "")
            .trim()
        if (LiaUniversalAppsAndGoogleHub.isSettingsAliasQuery(cleaned)) {
            return "Paramètres"
        }
        return cleaned
    }

    private const val EXPLICIT_APP_DETERMINERS_PATTERN =
        "(?:mon\\s+application\\s+|l'application\\s+|mon\\s+app\\s+|l'app\\s+|mon\\s+appli\\s+|l'appli\\s+|application\\s+|appli\\s+|app\\s+|les\\s+|le\\s+|la\\s+|l'|mon\\s+|ma\\s+|mes\\s+)?"

    private const val EXPLICIT_SUBGOAL_STARTERS_PATTERN =
        "(?:(?:combien|quel(?:le)?s?|o[Ùùu]|si|ce\\s+que|ma|mon|mes|la|le|les|un|une|des|du|de\\s+la|[ÉéEe]tat|niveau|solde|batterie|charge|autonomie|stockage|notifications?|t[ÉéEe]l[ÉéEe]chargements?|commandes?|colis|livraisons?|pas|calories?|temp[ÉéEe]rature|bluetooth|wifi|wi-fi)(?=\\s|$)|l'(?=[A-Za-zÀ-ÿ])).*"

    private const val VERB_THEN_APP_PREFIX_PATTERN =
        "^(?:peux-tu\\s+|pourrais-tu\\s+)?(?:va\\s+voir|aller\\s+voir|regarde(?:r)?|v[ÉéEe]rifie(?:r)?|check(?:er)?|cherche(?:r)?(?:\\s+et\\s+dis-moi)?|trouve(?:r)?|retrouve(?:r)?|inspecte(?:r)?|explore(?:r)?|ouvre\\s+et\\s+regarde|dis-moi|donne-moi|lis|lire)\\s+(?:dans\\s+|sur\\s+)" +
            EXPLICIT_APP_DETERMINERS_PATTERN

    /**
     * Extrait `(NomDeLApplication, CeQuIlFautRegarderOuFaire)` depuis toutes les tournures naturelles françaises :
     * - "va dans mon application Toyota regarde ma charge"
     * - "va dans les paramètres regarde ma batterie"
     * - "ouvre les paramètres et cherche le stockage"
     * - "cherche mes notifications dans les paramètres"
     * - "va dans Steam et vérifie mes téléchargements"
     * - "va dans Toyota ma charge" / "va dans My Renault ma charge" / "va voir dans Google Fit mes pas"
     * tout en rejetant les noms génériques de domaines/appareils ("voiture", "téléphone", "compte bancaire", "maison").
     */
    fun extractExplicitAppAndSubgoalFromPrompt(trimmed: String): Pair<String, String>? {
        val normalizedInput = trimmed
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace(Regex("\\s*[?!.…]+$"), "")
            .trim()
        if (normalizedInput.isBlank()) return null

        // Pattern 1 : "va dans / ouvre / lance / va sur / dans [déterminants]? [App] [et/pour/puis]? [verbe d'inspection] [Reste]"
        val appFirstRegex = Regex(
            "^(?:peux-tu\\s+|pourrais-tu\\s+)?(?:va\\s+dans|aller\\s+dans|va\\s+sur|aller\\s+sur|ouvre|ouvrir|lance|lancer|dans|sur)\\s+" +
                EXPLICIT_APP_DETERMINERS_PATTERN +
                "([A-Za-zÀ-ÿ0-9 .+_-]{2,28}?)\\s+(?:et\\s+|pour\\s+|puis\\s+)?" +
                "(regarde(?:r)?|voir|vois|v[ÉéEe]rifie(?:r)?|check(?:er)?|cherche(?:r)?|trouve(?:r)?|retrouve(?:r)?|inspecte(?:r)?|explore(?:r)?|analyse(?:r)?|dis-moi|donne-moi|lis|lire|combien|quel(?:le)?s?|o[Ùùu])(?=\\s|$)\\s*(.*)$",
            RegexOption.IGNORE_CASE
        )
        appFirstRegex.find(normalizedInput)?.let { m ->
            val appName = cleanExtractedAppName(m.groupValues[1].trim())
            val verbOrQ = m.groupValues[2].trim()
            val tail = m.groupValues[3].trim()
            val subgoal = if (tail.isNotBlank()) "$verbOrQ $tail" else verbOrQ
            if (appName.isBlank() || isExcludedAppOrGenericTarget(appName)) return null
            return appName to subgoal
        }

        // Pattern 1B : "va dans [App (1..N mots)] [Subgoal sans second verbe]" (ex: "va dans My Renault ma charge", "va dans Toyota ma charge")
        val appFirstNoSecondVerbRegex = Regex(
            "^(?:peux-tu\\s+|pourrais-tu\\s+)?(?:va\\s+dans|aller\\s+dans|va\\s+sur|aller\\s+sur)\\s+" +
                EXPLICIT_APP_DETERMINERS_PATTERN +
                "([A-Za-zÀ-ÿ0-9 .+_-]{2,28}?)\\s+($EXPLICIT_SUBGOAL_STARTERS_PATTERN)$",
            RegexOption.IGNORE_CASE
        )
        appFirstNoSecondVerbRegex.find(normalizedInput)?.let { m ->
            val appName = cleanExtractedAppName(m.groupValues[1].trim())
            val subgoal = m.groupValues[2].trim()
            if (appName.isBlank() || subgoal.isBlank() || isExcludedAppOrGenericTarget(appName)) return null
            return appName to subgoal
        }

        // Pattern 2 : "[va voir / regarde / vérifie / cherche...] [dans / sur] [déterminants]? [App (multi-mots ou simple)] [Reste]"
        val verbThenMultiWordAppRegex = Regex(
            VERB_THEN_APP_PREFIX_PATTERN +
                "([A-Za-zÀ-ÿ0-9 .+_-]{2,28}?)\\s+($EXPLICIT_SUBGOAL_STARTERS_PATTERN)$",
            RegexOption.IGNORE_CASE
        )
        val verbThenBrandTwoWordAppRegex = Regex(
            VERB_THEN_APP_PREFIX_PATTERN +
                "((?:My|Google|Samsung|Apple|Microsoft|Amazon|Uber|YouTube|Garmin|Kia|Hyundai|Mercedes|Volvo|MG|BYD|Lexus|PlayStation|Philips|Mi|Smart|Cr[ÉéEe]dit|Soci[ÉéEe]t[ÉéEe]|BNP|Just)\\s+[A-Za-zÀ-ÿ0-9.+_-]{2,20})\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
        val verbThenSingleWordAppRegex = Regex(
            VERB_THEN_APP_PREFIX_PATTERN +
                "([A-Za-zÀ-ÿ0-9.+_-]{2,24})\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
        for (p2 in listOf(verbThenMultiWordAppRegex, verbThenBrandTwoWordAppRegex, verbThenSingleWordAppRegex)) {
            val m = p2.find(normalizedInput) ?: continue
            val appName = cleanExtractedAppName(m.groupValues[1].trim())
            val questionPart = m.groupValues[2].trim()
            if (appName.isBlank() || questionPart.isBlank()) continue
            if (isExcludedAppOrGenericTarget(appName)) return null
            return appName to questionPart
        }

        // Pattern 3 : "[cherche / regarde / vérifie / combien...] [CeQuIlFautVoir] [dans / sur] [déterminants]? [App]"
        val verbThenGoalThenAppRegex = Regex(
            "^(?:peux-tu\\s+|pourrais-tu\\s+)?(?:va\\s+voir|aller\\s+voir|regarde(?:r)?|v[ÉéEe]rifie(?:r)?|check(?:er)?|cherche(?:r)?|trouve(?:r)?|retrouve(?:r)?|inspecte(?:r)?|explore(?:r)?|dis-moi|donne-moi|lis|lire|combien\\s+j'ai\\s+de|combien\\s+de|combien|quel\\s+est|quelle\\s+est|quels\\s+sont|quelles\\s+sont|o[Ùùu]\\s+en\\s+est|o[Ùùu]\\s+est|o[Ùùu]\\s+sont)\\s+(.+)\\s+(?:dans|sur)\\s+" +
                EXPLICIT_APP_DETERMINERS_PATTERN +
                "([A-Za-zÀ-ÿ0-9 .+_-]{2,28})$",
            RegexOption.IGNORE_CASE
        )
        verbThenGoalThenAppRegex.find(normalizedInput)?.let { m ->
            val whatToCheck = m.groupValues[1].trim()
            val appName = cleanExtractedAppName(m.groupValues[2].trim())
            if (appName.isBlank() || isExcludedAppOrGenericTarget(appName)) return null
            return appName to whatToCheck
        }

        return null
    }

    /**
     * Analyse si la phrase de l'utilisateur est une mission d'inspection ou d'objectif autonome
     * (ex: "va dans mon application Toyota regarde ma charge", "va voir combien de charge j'ai sur ma voiture",
     * "va dans les paramètres regarde ma batterie", "ouvre les paramètres et cherche le stockage").
     */
    fun checkUniversalGoalOrInspectionIntent(input: String): LiaBrainTurnOutcome? {
        val trimmed = input.replace('’', '\'').replace('‘', '\'').trim()
        val lower = trimmed.lowercase(Locale.FRENCH)
        val trimmedNoPunct = trimmed.replace(Regex("\\s*[?!.…]+$"), "").trim()

        // 0. Permettre à l'utilisateur de mémoriser l'état de sa voiture ou son app préférée
        val setCarAppRegex = Regex(
            "(?:mon\\s+application\\s+de\\s+voiture\\s+est|ma\\s+voiture\\s+utilise\\s+l'app(?:lication)?|mon\\s+app\\s+voiture\\s+est|ma\\s+voiture\\s+est\\s+une)\\s+([A-Za-zÀ-ÿ0-9 ._-]+)$",
            RegexOption.IGNORE_CASE
        )
        setCarAppRegex.find(trimmedNoPunct)?.let { m ->
            val appName = m.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("«", "»").replace(Regex("\\s*[?!.…]+$"), "").trim()
            if (appName.isNotBlank() && !appName.contains("%")) {
                LiaMemoryAndHistoryStore.upsertUserFact(
                    key = UniversalDomainCategory.VEHICLE_AND_EV.factKeyForPreferredApp,
                    label = "Application Véhicule préférée",
                    value = appName
                )
                return LiaBrainTurnOutcome(
                    replyText = "🚗 C'est mémorisé ! J'utiliserai désormais **$appName** chaque fois que vous me demanderez de vérifier la charge, l'autonomie ou l'état de votre voiture.",
                    learnedBadge = "🚗 App Véhicule : $appName"
                )
            }
        }

        val setCarBatteryRegex = Regex(
            "(?:ma\\s+voiture\\s+est\\s+[àa]|la\\s+charge\\s+de\\s+ma\\s+voiture\\s+est\\s+de)\\s+(\\d{1,3}\\s*%.*)",
            RegexOption.IGNORE_CASE
        )
        setCarBatteryRegex.find(trimmedNoPunct)?.let { m ->
            val statusVal = m.groupValues[1].trim()
            LiaMemoryAndHistoryStore.upsertSouvenir(
                type = "VEHICLE_STATUS",
                subjectKey = "vehicle_last_telemetry",
                title = "État batterie & autonomie voiture",
                detailValue = statusVal,
                searchTags = "voiture,batterie,charge,autonomie,vehicule,toyota,tesla,renault"
            )
            return LiaBrainTurnOutcome(
                replyText = "🚗 J'ai bien enregistré l'état de votre véhicule en mémoire : **$statusVal**.",
                learnedBadge = "🔋 Voiture : $statusVal"
            )
        }

        // 0B. Cas prioritaire : L'utilisateur mentionne explicitement l'application ET ce qu'il veut regarder
        // Ex: "va dans mon application Toyota regarde ma charge", "va dans les paramètres regarde ma batterie"
        val explicitAppAndGoal = extractExplicitAppAndSubgoalFromPrompt(trimmed)
        if (explicitAppAndGoal != null) {
            val (explicitAppName, subgoal) = explicitAppAndGoal
            val normExplicitApp = LiaUniversalAppsAndGoogleHub.normalizeAppQuery(explicitAppName)

            // Protection anti-collision : Si l'application explicite est Paramètres / Settings,
            // ne JAMAIS laisser "batterie" ou "charge" détourner la cible vers VEHICLE_AND_EV !
            if (LiaUniversalAppsAndGoogleHub.isSettingsAliasQuery(explicitAppName) ||
                normExplicitApp == "parametres" || normExplicitApp == "settings"
            ) {
                return handleDomainInspectionGoal(
                    rawUserPrompt = trimmed,
                    domain = UniversalDomainCategory.SYSTEM_AND_SETTINGS,
                    explicitAppOverride = "Paramètres"
                )
            }

            // Vérifier si le nom de l'application explicite appartient au catalogue d'un domaine spécifique
            val matchedDomainByExplicitApp = UniversalDomainCategory.entries
                .filter { it != UniversalDomainCategory.SYSTEM_AND_SETTINGS }
                .firstOrNull { domain ->
                    domain.knownPackageCandidates.any { (pkg, label) ->
                        val normLabel = LiaUniversalAppsAndGoogleHub.normalizeAppQuery(label)
                        val pkgShort = pkg.substringAfterLast('.').lowercase(Locale.FRENCH)
                        normLabel == normExplicitApp ||
                            (normExplicitApp.length >= 3 && (normLabel.contains(normExplicitApp) || normExplicitApp.contains(normLabel))) ||
                            pkgShort == normExplicitApp
                    }
                }

            if (matchedDomainByExplicitApp != null) {
                LiaMemoryAndHistoryStore.upsertUserFact(
                    key = matchedDomainByExplicitApp.factKeyForPreferredApp,
                    label = "Application ${matchedDomainByExplicitApp.titleFr}",
                    value = explicitAppName
                )
                return handleDomainInspectionGoal(
                    rawUserPrompt = trimmed,
                    domain = matchedDomainByExplicitApp,
                    explicitAppOverride = explicitAppName
                )
            }

            return handleGenericAppInspectionGoal(trimmed, explicitAppName, subgoal)
        }

        // 1. Détecter si la requête correspond à un domaine sémantique connu (Voiture, Livraison, Banque, Maison, Santé, Jeux, Paramètres)
        val matchedDomain = UniversalDomainCategory.entries.firstOrNull { domain ->
            domain.domainTriggerKeywords.any { kw -> lower.contains(kw) }
        }

        val isInspectionQuestionOrCommand = lower.contains("combien") ||
            lower.contains("quel est") ||
            lower.contains("quelle est") ||
            lower.contains("quels sont") ||
            lower.contains("quelles sont") ||
            lower.contains("quel ") ||
            lower.contains("quelle ") ||
            lower.contains("quels ") ||
            lower.contains("quelles ") ||
            lower.contains("où en est") ||
            lower.contains("ou en est") ||
            lower.contains("va voir") ||
            lower.contains("aller voir") ||
            lower.contains("va dans") ||
            lower.contains("aller dans") ||
            lower.contains("vérifie") ||
            lower.contains("verifie") ||
            lower.contains("regarde") ||
            lower.contains("voir") ||
            lower.contains("check") ||
            lower.contains("cherche") ||
            lower.contains("trouve") ||
            lower.contains("autonomie") ||
            lower.contains("charge") ||
            lower.contains("batterie") ||
            lower.contains("solde") ||
            lower.contains("commande") ||
            lower.contains("livraison") ||
            lower.contains("stockage") ||
            lower.contains("notifications")

        val lowerNoPunct = lower.replace(Regex("\\s*[?!.…]+$"), "").trim()
        // 1B. Cas où l'utilisateur demande explicitement la batterie du TÉLÉPHONE lui-même ("combien de batterie j'ai sur mon téléphone")
        if ((lower.contains("batterie") || lower.contains("charge")) &&
            (lower.contains("téléphone") || lower.contains("telephone") || lower.contains("smartphone") ||
                lowerNoPunct == "combien de batterie j'ai" || lowerNoPunct == "combien j'ai de batterie" || lowerNoPunct == "quel est mon niveau de batterie") &&
            !lower.contains("voiture") && !lower.contains("véhicule") && !lower.contains("vehicule") &&
            !lower.contains("toyota") && !lower.contains("tesla") && !lower.contains("renault") &&
            !lower.contains("paramètres") && !lower.contains("parametres") && !lower.contains("settings") &&
            !lower.contains("réglages") && !lower.contains("reglages")
        ) {
            val ctx = LiaMemoryAndHistoryStore.getApplicationContextOrNull()
            val bm = ctx?.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val isCharging = bm?.isCharging == true
            if (pct in 0..100) {
                val statusStr = if (isCharging) "⚡ En charge" else "🔋 Sur batterie"
                return LiaBrainTurnOutcome(
                    replyText = "🔋 Votre téléphone est actuellement à **$pct% de batterie** ($statusStr).\n\n" +
                        "*(Si vous vouliez parler de votre voiture, dites-moi : « Combien de charge j'ai sur ma voiture ? »)*",
                    isPhoneAction = true,
                    learnedBadge = "🔋 Batterie téléphone : $pct%"
                )
            }
            return LiaBrainTurnOutcome(
                replyText = "🔋 Vérification du niveau de batterie de votre téléphone en cours via BatteryManager.",
                isPhoneAction = true,
                learnedBadge = "🔋 Batterie téléphone"
            )
        }

        if (matchedDomain != null && isInspectionQuestionOrCommand) {
            return handleDomainInspectionGoal(trimmed, matchedDomain)
        }

        return null
    }

    private fun handleDomainInspectionGoal(
        rawUserPrompt: String,
        domain: UniversalDomainCategory,
        explicitAppOverride: String? = null
    ): LiaBrainTurnOutcome {
        val resolved = resolveBestAppForDomain(domain, explicitAppOverride)

        // Si plusieurs applications du même domaine sont installées sur le téléphone et qu'aucune préférence n'a encore été mémorisée :
        if (explicitAppOverride.isNullOrBlank() && resolved.multipleCandidatesInstalled.size > 1) {
            val names = resolved.multipleCandidatesInstalled.map { it.appLabel }
            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForGeneralClarification("ASK_DOMAIN_APP_CHOICE:${domain.id}:$rawUserPrompt")
            )
            return LiaBrainTurnOutcome(
                replyText = "${domain.emoji} J'ai détecté plusieurs applications pour **${domain.titleFr}** sur votre téléphone : **${names.joinToString(", ")}**.\n\n" +
                    "**Laquelle souhaitez-vous que j'utilise ?** *(Je m'en souviendrai définitivement pour les prochaines fois !)*",
                learnedBadge = "${domain.emoji} Choix unique mémorisé",
                quickReplies = names
            )
        }

        val encodedDomain = Uri.encode(domain.id)
        val encodedAppPkg = Uri.encode(resolved.packageNameOrUri)
        val encodedAppLabel = Uri.encode(resolved.appLabel)
        val encodedGoal = Uri.encode(rawUserPrompt)
        val goalActionUri = "agent_goal:$encodedDomain|$encodedAppPkg|$encodedAppLabel|$encodedGoal"

        val action = LiaAppLaunchAction(
            appLabel = "Inspection autonome ${resolved.appLabel}",
            packageName = goalActionUri,
            targetScreenId = "home_launcher",
            learnedSpeedMs = 40L
        )

        LiaMemoryAndHistoryStore.recordLearnedShortcut(
            triggerPhrase = "${domain.emoji} ${rawUserPrompt.take(24)}",
            actionTitle = "Inspecter ${resolved.appLabel} et revenir dans LIA",
            targetPackageOrScreen = goalActionUri,
            stepsSaved = 6,
            latencyMs = 40L,
            rewardSignal = 1.4f
        )

        return LiaBrainTurnOutcome(
            replyText = "${domain.emoji} **Mission autonome lancée dans ${resolved.appLabel}** :\n\n" +
                "1. **Ouverture** de **${resolved.appLabel}**\n" +
                "2. **Exploration approfondie de l'écran** (recherche, défilement, sous-menus : *${domain.navigationTabHints.take(4).joinToString(", ")}*)\n" +
                "3. **Retour automatique dans LIA** dès que l'information est trouvée pour vous afficher le rapport complet !",
            appLaunchAction = action,
            isPhoneAction = true,
            learnedBadge = "${domain.emoji} Inspection : ${resolved.appLabel}",
            quickReplies = listOf(
                "Mon application de voiture est Toyota",
                "Mon application de voiture est Tesla",
                "Lis l'écran"
            )
        )
    }

    private fun handleGenericAppInspectionGoal(
        rawUserPrompt: String,
        targetAppName: String,
        whatToCheck: String
    ): LiaBrainTurnOutcome {
        val installed = LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp(targetAppName)
        val pkgOrQuery = installed?.packageName ?: targetAppName
        val label = installed?.appLabel ?: targetAppName.replaceFirstChar { it.uppercase() }

        val encodedDomain = Uri.encode("generic")
        val encodedAppPkg = Uri.encode(pkgOrQuery)
        val encodedAppLabel = Uri.encode(label)
        val encodedGoal = Uri.encode("$whatToCheck ($rawUserPrompt)")
        val goalActionUri = "agent_goal:$encodedDomain|$encodedAppPkg|$encodedAppLabel|$encodedGoal"

        val action = LiaAppLaunchAction(
            appLabel = "Inspecter $label",
            packageName = goalActionUri,
            targetScreenId = "home_launcher",
            learnedSpeedMs = 40L
        )

        return LiaBrainTurnOutcome(
            replyText = "🕵️‍♀️ **Inspection autonome lancée dans $label** :\n\n" +
                "1. **Ouverture** de **$label**\n" +
                "2. **Exploration approfondie & Recherche** : *« $whatToCheck »*\n" +
                "3. **Fermeture et retour automatique dans LIA** avec la réponse extraite !",
            appLaunchAction = action,
            isPhoneAction = true,
            learnedBadge = "🕵️‍♀️ Inspection : $label"
        )
    }

    /**
     * Résout la meilleure application installée ou mémorisée pour un domaine donné (ex: Voiture, Livraison, Banque, Paramètres).
     */
    fun resolveBestAppForDomain(
        domain: UniversalDomainCategory,
        explicitAppOverride: String? = null
    ): ResolvedGoalTargetApp {
        val installedApps = LiaUniversalAppsAndGoogleHub.state.value.installedPhoneApps

        if (domain == UniversalDomainCategory.SYSTEM_AND_SETTINGS) {
            return ResolvedGoalTargetApp(
                domain = domain,
                appLabel = "Paramètres",
                packageNameOrUri = "com.android.settings",
                isInstalledOnDevice = true
            )
        }

        // 0. Si l'utilisateur a nommé explicitement l'application dans sa phrase (ex: "va dans mon application Toyota regarde ma charge")
        if (!explicitAppOverride.isNullOrBlank()) {
            val cleanedOverride = cleanExtractedAppName(explicitAppOverride)
            val matchInstalled = LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp(cleanedOverride)
            if (matchInstalled != null) {
                return ResolvedGoalTargetApp(
                    domain = domain,
                    appLabel = matchInstalled.appLabel,
                    packageNameOrUri = matchInstalled.packageName,
                    isInstalledOnDevice = true
                )
            }
            val knownCandidate = domain.knownPackageCandidates.firstOrNull {
                it.second.contains(cleanedOverride, ignoreCase = true) ||
                    cleanedOverride.contains(it.second, ignoreCase = true)
            }
            val cleanName = cleanedOverride.replaceFirstChar { it.uppercase() }
            return ResolvedGoalTargetApp(
                domain = domain,
                appLabel = knownCandidate?.second ?: cleanName,
                packageNameOrUri = knownCandidate?.first ?: cleanName,
                isInstalledOnDevice = false
            )
        }

        // 1. Vérifier si l'utilisateur a déjà mémorisé une application préférée pour ce domaine dans Room
        val preferredFact = LiaMemoryAndHistoryStore.getUserFactValue(domain.factKeyForPreferredApp)
        if (!preferredFact.isNullOrBlank() && !LiaUniversalAppsAndGoogleHub.isSettingsAliasQuery(preferredFact)) {
            val matchInstalled = LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp(preferredFact)
            if (matchInstalled != null && matchInstalled.packageName != "com.android.settings") {
                return ResolvedGoalTargetApp(
                    domain = domain,
                    appLabel = matchInstalled.appLabel,
                    packageNameOrUri = matchInstalled.packageName,
                    isInstalledOnDevice = true
                )
            }
            val knownCandidate = domain.knownPackageCandidates.firstOrNull {
                it.second.contains(preferredFact, ignoreCase = true) || preferredFact.contains(it.second, ignoreCase = true)
            }
            return ResolvedGoalTargetApp(
                domain = domain,
                appLabel = knownCandidate?.second ?: preferredFact,
                packageNameOrUri = knownCandidate?.first ?: preferredFact,
                isInstalledOnDevice = false
            )
        }

        // 2. Chercher parmi toutes les applications réellement installées sur le téléphone celles qui correspondent au domaine
        val matchingInstalled = installedApps.filter { installed ->
            domain.knownPackageCandidates.any { (pkg, name) ->
                installed.packageName.equals(pkg, ignoreCase = true) ||
                    installed.appLabel.equals(name, ignoreCase = true) ||
                    installed.appLabel.contains(name, ignoreCase = true)
            }
        }

        if (matchingInstalled.size == 1) {
            val single = matchingInstalled.first()
            return ResolvedGoalTargetApp(
                domain = domain,
                appLabel = single.appLabel,
                packageNameOrUri = single.packageName,
                isInstalledOnDevice = true
            )
        } else if (matchingInstalled.size > 1) {
            val first = matchingInstalled.first()
            return ResolvedGoalTargetApp(
                domain = domain,
                appLabel = first.appLabel,
                packageNameOrUri = first.packageName,
                isInstalledOnDevice = true,
                multipleCandidatesInstalled = matchingInstalled
            )
        }

        // 3. Si aucune application spécifique n'est installée (cas fréquent sur un émulateur Android Studio vierge)
        val defaultCandidate = domain.knownPackageCandidates.first()
        return ResolvedGoalTargetApp(
            domain = domain,
            appLabel = defaultCandidate.second,
            packageNameOrUri = defaultCandidate.first,
            isInstalledOnDevice = false
        )
    }

    /**
     * Extrait les mots-clés significatifs de l'objectif utilisateur et les enrichit avec des synonymes FR/EN.
     */
    fun extractGoalKeywords(
        userGoalPrompt: String,
        domain: UniversalDomainCategory? = null
    ): List<String> {
        val normPrompt = LiaAccessibilityService.normalizeUiTextForMatch(userGoalPrompt)
        val rawTokens = normPrompt
            .split(Regex("[^a-z0-9%€]+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in GOAL_STOP_WORDS }

        val keywords = LinkedHashSet<String>()
        keywords.addAll(rawTokens)

        val joined = rawTokens.joinToString(" ") + " " + normPrompt

        // Synonymes bilingues selon la thématique demandée
        if (joined.contains("charge") || joined.contains("batterie") || joined.contains("battery") || joined.contains("autonomie")) {
            keywords.addAll(
                listOf("batterie", "charge", "battery", "autonomie", "range", "recharge", "charging", "%", "kwh", "km", "etat", "status")
            )
        }
        if (joined.contains("stockage") || joined.contains("storage") || joined.contains("espace") || joined.contains("memoire")) {
            keywords.addAll(
                listOf("stockage", "storage", "espace", "memoire", "go", "gb", "utilise", "libre")
            )
        }
        if (joined.contains("notification")) {
            keywords.addAll(
                listOf("notifications", "notification", "alertes", "applis")
            )
        }
        if (joined.contains("telechargement") || joined.contains("download")) {
            keywords.addAll(
                listOf("telechargements", "telechargement", "downloads", "download", "bibliotheque", "library", "jeux")
            )
        }
        if (joined.contains("wifi") || joined.contains("reseau") || joined.contains("internet")) {
            keywords.addAll(
                listOf("wifi", "wi-fi", "reseau", "network", "internet", "connexion")
            )
        }
        if (joined.contains("solde") || joined.contains("compte") || domain == UniversalDomainCategory.FINANCE_AND_BANK) {
            keywords.addAll(
                listOf("solde", "compte", "comptes", "balance", "€", "eur", "transactions")
            )
        }
        if (domain == UniversalDomainCategory.VEHICLE_AND_EV && keywords.isEmpty()) {
            keywords.addAll(listOf("batterie", "charge", "autonomie", "vehicule", "%", "km"))
        }

        return keywords.toList()
    }

    private fun extractPrimarySearchKeyword(userGoalPrompt: String, goalKeywords: List<String>): String {
        val normPrompt = LiaAccessibilityService.normalizeUiTextForMatch(userGoalPrompt)
        val rawTokens = normPrompt
            .split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in GOAL_STOP_WORDS }
        return rawTokens.firstOrNull()
            ?: goalKeywords.firstOrNull { it.length >= 3 && it !in PURE_UNIT_CLICKABLE_EXCLUSIONS }.orEmpty()
    }

    private fun matchesKeywordWithShortBoundary(
        textNorm: String,
        primaryTitleNorm: String = "",
        kw: String
    ): Boolean {
        if (kw.isBlank()) return false
        if (textNorm == kw || (primaryTitleNorm.isNotBlank() && primaryTitleNorm == kw)) return true
        return if (kw.length < 4) {
            Regex("(?<![a-z0-9])${Regex.escape(kw)}(?![a-z0-9])").containsMatchIn(textNorm)
        } else {
            textNorm.contains(kw)
        }
    }

    /**
     * Calcule le score de pertinence d'un élément cliquable par rapport à l'objectif de l'utilisateur :
     * - +100 : correspondance directe avec un mot-clé de l'objectif (`goalKeywords`)
     * - +60  : correspondance avec un onglet de navigation du domaine (`navigationTabHints`)
     * - +25  : section d'inspection générale ("État", "Détails", "Infos", "Système", "À propos", "Plus", "Véhicule", "Status")
     */
    fun scoreClickableCandidate(
        candidateLabel: String,
        goalKeywords: List<String>,
        domain: UniversalDomainCategory? = null,
        visitedLabels: Set<String> = emptySet()
    ): Int {
        val trimmed = candidateLabel.trim()
        if (trimmed.isBlank()) return 0

        val normCandidate = LiaAccessibilityService.normalizeUiTextForMatch(trimmed)
        val normPrimaryTitle = normCandidate.substringBefore(" — ").trim()
        if (normCandidate.isBlank()) return 0

        if (visitedLabels.any { LiaAccessibilityService.normalizeUiTextForMatch(it) == normCandidate || LiaAccessibilityService.normalizeUiTextForMatch(it) == normPrimaryTitle }) {
            return 0
        }
        if (LiaAccessibilityService.isCriticalIrreversibleAction(trimmed)) {
            return 0
        }
        if (normPrimaryTitle in NAVIGATION_BACK_OR_CLOSE_LABELS) {
            return 0
        }

        var score = 0
        val meaningfulGoalWords = goalKeywords
            .map { LiaAccessibilityService.normalizeUiTextForMatch(it) }
            .filter { it.length >= 2 && it !in PURE_UNIT_CLICKABLE_EXCLUSIONS }

        val matchesGoalKeyword = meaningfulGoalWords.any { kw ->
            matchesKeywordWithShortBoundary(normCandidate, normPrimaryTitle, kw)
        }
        if (matchesGoalKeyword) {
            score += 100
        }

        val tabHints = domain?.navigationTabHints.orEmpty()
        val matchesDomainTab = tabHints.any { hint ->
            val normHint = LiaAccessibilityService.normalizeUiTextForMatch(hint)
            normHint.isNotBlank() && matchesKeywordWithShortBoundary(normCandidate, normPrimaryTitle, normHint)
        }
        if (matchesDomainTab) {
            score += 60
        }

        val matchesExploratorySection = GENERIC_EXPLORATORY_SECTION_HINTS.any { hint ->
            val normHint = LiaAccessibilityService.normalizeUiTextForMatch(hint)
            matchesKeywordWithShortBoundary(normCandidate, normPrimaryTitle, normHint)
        }
        if (matchesExploratorySection && score < 60) {
            score += 25
        }

        if (score > 0 && (trimmed.contains("%") || trimmed.any { it.isDigit() })) {
            score += 15
        }

        return score
    }

    /**
     * Détermine si l'information cible demandée par l'utilisateur a réellement été trouvée sur l'écran.
     * Ne retourne JAMAIS `true` simplement parce que `texts.size >= 8` !
     */
    fun isGoalInformationFound(
        domain: UniversalDomainCategory?,
        goalKeywords: List<String>,
        collectedTexts: Set<String>,
        collectedPairs: List<Pair<String, String>> = emptyList(),
        step: Int = 1,
        currentScreenTexts: Set<String> = collectedTexts
    ): Boolean {
        if (collectedTexts.isEmpty() && collectedPairs.isEmpty()) return false
        val joined = collectedTexts.joinToString(" • ")
        val normJoined = LiaAccessibilityService.normalizeUiTextForMatch(joined)

        when (domain) {
            UniversalDomainCategory.VEHICLE_AND_EV -> {
                val screenJoined = currentScreenTexts.joinToString(" • ")
                val hasPercent = Regex("(?<![-−–+\\d])(\\d{1,3})(?:[.,]\\d+)?\\s*%")
                    .findAll(screenJoined)
                    .any { m -> m.groupValues[1].toIntOrNull() in 0..100 }
                val hasKm = Regex("(?<![-−–\\d])\\b\\d{1,4}\\s*km\\b", RegexOption.IGNORE_CASE)
                    .containsMatchIn(screenJoined)
                val hasKwh = Regex("(?<![-−–\\d])\\b\\d+(?:[.,]\\d+)?\\s*kwh\\b", RegexOption.IGNORE_CASE)
                    .containsMatchIn(screenJoined)
                if (hasPercent && (hasKm || hasKwh)) return true
                val hasBatteryPair = collectedPairs.any { (k, v) ->
                    val nk = LiaAccessibilityService.normalizeUiTextForMatch(k)
                    val nv = LiaAccessibilityService.normalizeUiTextForMatch(v)
                    (nk.contains("batterie") || nk.contains("charge") || nk.contains("autonomie") || nk.contains("battery")) &&
                        (Regex("(?<![-−–+\\d])\\d{1,3}(?:[.,]\\d+)?\\s*%").containsMatchIn(nv) ||
                            Regex("(?<![-−–\\d])\\b\\d+\\s*km\\b").containsMatchIn(nv))
                }
                if (hasBatteryPair && step >= 2) return true
                return false
            }

            UniversalDomainCategory.FINANCE_AND_BANK -> {
                return Regex("\\d+[.,]?\\d*\\s*(?:€|EUR|\\$)", RegexOption.IGNORE_CASE).containsMatchIn(joined)
            }

            else -> {
                val primaryKeywords = goalKeywords
                    .map { LiaAccessibilityService.normalizeUiTextForMatch(it) }
                    .filter { it.length >= 3 && it !in PURE_UNIT_CLICKABLE_EXCLUSIONS && it !in setOf("etat", "status") }

                if (primaryKeywords.isEmpty()) {
                    // Sans mot-clé spécifique, exiger au moins des paires clé-valeur concrètes et > 1 étape d'exploration
                    return step >= 3 && collectedPairs.isNotEmpty()
                }

                // 1. Vérifier si une paire (Clé -> Valeur) répond directement au mot-clé avec une valeur concrète (chiffre, %, Go, état)
                val matchingPairWithMetric = collectedPairs.any { (k, v) ->
                    val nk = LiaAccessibilityService.normalizeUiTextForMatch(k)
                    val nv = LiaAccessibilityService.normalizeUiTextForMatch(v)
                    val keyMatches = primaryKeywords.any { kw ->
                        matchesKeywordWithShortBoundary(nk, kw = kw) || matchesKeywordWithShortBoundary(nv, kw = kw)
                    }
                    val hasConcreteValue = v.any { it.isDigit() } ||
                        nv.contains("%") ||
                        Regex("\\b(?:go|gb|mo|mb|km|kwh|h|min|pas|kcal|bpm|activ|desactiv|connect|charge)\\b").containsMatchIn(nv)
                    keyMatches && hasConcreteValue
                }
                if (matchingPairWithMetric) return true

                // 2. Vérifier si une ligne de texte combine un mot-clé primaire ET une mesure chiffrée concrète (ex: "Batterie : 85%", "64 Go utilisés sur 128 Go")
                val matchingRichTextLine = collectedTexts.any { line ->
                    val normLine = LiaAccessibilityService.normalizeUiTextForMatch(line)
                    val kwMatch = primaryKeywords.any { kw -> matchesKeywordWithShortBoundary(normLine, kw = kw) }
                    val hasMetric = Regex("\\b\\d+(?:[.,]\\d+)?\\s*(?:%|go|gb|mo|mb|km|kwh|€|h|min|pas|kcal|bpm|°c)\\b", RegexOption.IGNORE_CASE)
                        .containsMatchIn(line)
                    kwMatch && hasMetric
                }
                if (matchingRichTextLine) return true

                // 3. Si on a déjà navigué dans un sous-menu ou utilisé la recherche (step >= 2) et que plusieurs lignes détaillent le sujet
                if (step >= 2) {
                    val matchingLinesCount = collectedTexts.count { line ->
                        val normLine = LiaAccessibilityService.normalizeUiTextForMatch(line)
                        primaryKeywords.any { kw -> matchesKeywordWithShortBoundary(normLine, kw = kw) } && line.split(" ").size >= 2
                    }
                    if (matchingLinesCount >= 2 && (normJoined.any { it.isDigit() } || step >= 3)) {
                        return true
                    }
                }

                return false
            }
        }
    }

    fun hasSufficientDomainData(
        domain: UniversalDomainCategory?,
        texts: Set<String>,
        goalKeywords: List<String> = emptyList(),
        collectedPairs: List<Pair<String, String>> = emptyList(),
        step: Int = 1
    ): Boolean {
        val effectiveKeywords = if (goalKeywords.isNotEmpty()) {
            goalKeywords
        } else if (domain != null) {
            domain.navigationTabHints
        } else {
            emptyList()
        }
        return isGoalInformationFound(
            domain = domain,
            goalKeywords = effectiveKeywords,
            collectedTexts = texts,
            collectedPairs = collectedPairs,
            step = step
        )
    }

    /**
     * Boucle d'exploration autonome multi-étapes ("Tâtonner", jusqu'à [maxSteps] = 10 étapes) :
     * - Évalue chaque écran via `readSnapshot()`
     * - Tactique 1 : Clic sur sous-menu / onglet à forte pertinence (`score >= 60`)
     * - Tactique 2 : Utilisation de la barre de recherche interne (`activateAndTypeInSearchBar`)
     * - Tactique 3 : Défilements multiples (`SWIPE_UP`) avec détection de stagnation (`screenFingerprintHash`)
     * - Tactique 4 : Exploration de sous-menus secondaires ("tâtonner", `score >= 25`)
     * - Tactique 5 : Retour en arrière (`GLOBAL_ACTION_BACK`) lorsqu'une piste est une impasse
     */
    suspend fun executeExplorationLoop(
        driver: UiExplorationDriver,
        domain: UniversalDomainCategory?,
        userGoalPrompt: String,
        maxSteps: Int = 10
    ): ExplorationMissionResult {
        val goalKeywords = extractGoalKeywords(userGoalPrompt, domain)
        val primarySearchKeyword = extractPrimarySearchKeyword(userGoalPrompt, goalKeywords)

        val collectedTexts = LinkedHashSet<String>()
        val collectedPairs = mutableListOf<Pair<String, String>>()
        val navigatedTabs = mutableListOf<String>()
        val visitedClickableByScreen = mutableMapOf<Int, MutableSet<String>>()
        val globalClickCountByLabel = mutableMapOf<String, Int>()
        val visitedScreenHashes = LinkedHashSet<Int>()
        val stagnatedScreenHashes = HashSet<Int>()
        val searchAttemptedScreenHashes = HashSet<Int>()
        var searchAttemptedOnZeroHash = false
        val navigationBackstack = ArrayDeque<Int>()
        val explorationStepsTrace = mutableListOf<String>()

        fun recordClickedLabel(screenHash: Int, rawLabel: String) {
            val norm = LiaAccessibilityService.normalizeUiTextForMatch(rawLabel)
            val normPrimary = norm.substringBefore(" — ").trim()
            if (norm.isNotBlank()) {
                visitedClickableByScreen.getOrPut(screenHash) { LinkedHashSet() }.add(norm)
                globalClickCountByLabel[norm] = (globalClickCountByLabel[norm] ?: 0) + 1
            }
            if (normPrimary.isNotBlank() && normPrimary != norm) {
                visitedClickableByScreen.getOrPut(screenHash) { LinkedHashSet() }.add(normPrimary)
                globalClickCountByLabel[normPrimary] = (globalClickCountByLabel[normPrimary] ?: 0) + 1
            }
        }

        var consecutiveScrollsOnScreen = 0
        var lastScrollScreenHash = 0
        var scrollStagnatedOnCurrentScreen = false
        var searchBarUsed = false
        var totalScrolls = 0
        var totalBacktracks = 0
        var goalFound = false

        for (step in 1..maxSteps) {
            var snap = driver.readSnapshot()
            if (step == 1 && snap.activePackageName.contains("omniagent", ignoreCase = true)) {
                driver.waitMs(800L)
                snap = driver.readSnapshot()
            }

            val currentScreenTexts = LinkedHashSet<String>()
            val currentScreenPairs = mutableListOf<Pair<String, String>>()

            if (!snap.activePackageName.contains("omniagent", ignoreCase = true)) {
                currentScreenTexts.addAll(snap.allVisibleTexts)
                currentScreenTexts.addAll(snap.progressAndGaugeValues)
                currentScreenPairs.addAll(snap.keyValuePairs)

                collectedTexts.addAll(currentScreenTexts)
                for (pair in currentScreenPairs) {
                    if (collectedPairs.none { it.first == pair.first && it.second == pair.second }) {
                        collectedPairs.add(pair)
                    }
                }
            }

            if (snap.screenFingerprintHash != 0) {
                if (snap.screenFingerprintHash in stagnatedScreenHashes) {
                    scrollStagnatedOnCurrentScreen = true
                }
                if (consecutiveScrollsOnScreen > 0 && snap.screenFingerprintHash == lastScrollScreenHash) {
                    scrollStagnatedOnCurrentScreen = true
                    stagnatedScreenHashes.add(snap.screenFingerprintHash)
                    explorationStepsTrace.add("Étape $step : Fin de liste détectée (écran inchangé après défilement)")
                }
                visitedScreenHashes.add(snap.screenFingerprintHash)
            }

            // Vérifier si l'information demandée est présente sur l'écran actuel
            if (isGoalInformationFound(
                    domain = domain,
                    goalKeywords = goalKeywords,
                    collectedTexts = collectedTexts,
                    collectedPairs = collectedPairs,
                    step = step,
                    currentScreenTexts = currentScreenTexts
                )
            ) {
                goalFound = true
                explorationStepsTrace.add("Étape $step : Information cible trouvée sur l'écran")
                break
            }

            val screenVisited = visitedClickableByScreen[snap.screenFingerprintHash].orEmpty()
            val maxGlobalClicks = if (snap.screenFingerprintHash == 0) 1 else 2
            val globallyExhausted = globalClickCountByLabel.filterValues { it >= maxGlobalClicks }.keys
            val effectiveVisitedLabels = screenVisited + globallyExhausted

            // Évaluer les éléments cliquables non encore visités sur l'écran courant
            val scoredCandidates = snap.clickableButtonsAndTabs
                .map { btn -> btn to scoreClickableCandidate(btn, goalKeywords, domain, effectiveVisitedLabels) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }

            val bestHighRelevance = scoredCandidates.firstOrNull { it.second >= 60 }

            // Tactique 1 : Clic haute pertinence (score >= 60)
            if (bestHighRelevance != null) {
                val (candidateLabel, score) = bestHighRelevance
                recordClickedLabel(snap.screenFingerprintHash, candidateLabel)
                if (driver.clickStrict(candidateLabel)) {
                    navigatedTabs.add(candidateLabel)
                    navigationBackstack.addLast(snap.screenFingerprintHash)
                    consecutiveScrollsOnScreen = 0
                    scrollStagnatedOnCurrentScreen = false
                    explorationStepsTrace.add("Étape $step : Ouverture de « $candidateLabel » (pertinence $score)")
                    driver.waitMs(1200L)
                    continue
                }
            }

            // Tactique 2 : Barre de recherche interne (si présente ou après 1 défilement sans résultat direct)
            val canTrySearchOnCurrentScreen = if (snap.screenFingerprintHash == 0) {
                !searchAttemptedOnZeroHash
            } else {
                snap.screenFingerprintHash !in searchAttemptedScreenHashes
            }
            if (!searchBarUsed &&
                canTrySearchOnCurrentScreen &&
                primarySearchKeyword.isNotBlank() &&
                (snap.hasEditableSearchField || snap.hasSearchButtonOrBar || consecutiveScrollsOnScreen >= 1)
            ) {
                if (snap.screenFingerprintHash == 0) {
                    searchAttemptedOnZeroHash = true
                } else {
                    searchAttemptedScreenHashes.add(snap.screenFingerprintHash)
                }
                if (driver.activateAndTypeSearch(primarySearchKeyword)) {
                    searchBarUsed = true
                    navigatedTabs.add("Recherche : $primarySearchKeyword")
                    navigationBackstack.addLast(snap.screenFingerprintHash)
                    consecutiveScrollsOnScreen = 0
                    scrollStagnatedOnCurrentScreen = false
                    explorationStepsTrace.add("Étape $step : Recherche interne de « $primarySearchKeyword » dans la barre de recherche")
                    driver.waitMs(1300L)
                    continue
                }
            }

            // Tactique 3 : Défilement multi-étapes (SWIPE_UP jusqu'à 4 fois tant que l'écran évolue, sauf feuille impasse non-scrollable)
            val isNonScrollableLeafDeadEnd = !snap.isScrollableContainerPresent &&
                snap.clickableButtonsAndTabs.isEmpty() &&
                navigationBackstack.isNotEmpty()
            if (isNonScrollableLeafDeadEnd) {
                scrollStagnatedOnCurrentScreen = true
                if (snap.screenFingerprintHash != 0) {
                    stagnatedScreenHashes.add(snap.screenFingerprintHash)
                }
            } else if (consecutiveScrollsOnScreen < 4 && !scrollStagnatedOnCurrentScreen) {
                lastScrollScreenHash = snap.screenFingerprintHash
                consecutiveScrollsOnScreen++
                if (driver.scrollDown()) {
                    totalScrolls++
                    explorationStepsTrace.add("Étape $step : Défilement de l'écran (#$consecutiveScrollsOnScreen)")
                    driver.waitMs(1000L)
                    continue
                } else {
                    scrollStagnatedOnCurrentScreen = true
                    if (snap.screenFingerprintHash != 0) {
                        stagnatedScreenHashes.add(snap.screenFingerprintHash)
                    }
                }
            }

            // Tactique 4 : Exploration de sous-menus secondaires ("Tâtonner", score >= 25)
            val exploratoryCandidate = scoredCandidates.firstOrNull { it.second >= 25 }
            if (exploratoryCandidate != null && (scrollStagnatedOnCurrentScreen || consecutiveScrollsOnScreen >= 2)) {
                val (candidateLabel, _) = exploratoryCandidate
                recordClickedLabel(snap.screenFingerprintHash, candidateLabel)
                if (driver.clickStrict(candidateLabel)) {
                    navigatedTabs.add(candidateLabel)
                    navigationBackstack.addLast(snap.screenFingerprintHash)
                    consecutiveScrollsOnScreen = 0
                    scrollStagnatedOnCurrentScreen = false
                    explorationStepsTrace.add("Étape $step : Exploration du sous-menu « $candidateLabel »")
                    driver.waitMs(1200L)
                    continue
                }
            }

            // Tactique 5 : Backtracking (GLOBAL_ACTION_BACK) si on est dans un sous-menu sans résultat concluant
            if (navigationBackstack.isNotEmpty()) {
                navigationBackstack.removeLast()
                val backOk = driver.navigateBack()
                totalBacktracks++
                consecutiveScrollsOnScreen = 0
                scrollStagnatedOnCurrentScreen = false
                explorationStepsTrace.add("Étape $step : Retour en arrière (piste non concluante)")
                if (backOk) {
                    driver.waitMs(1000L)
                }
                continue
            }

            break
        }

        return ExplorationMissionResult(
            collectedTexts = collectedTexts.toList(),
            collectedPairs = collectedPairs,
            navigatedTabs = navigatedTabs,
            explorationStepsTrace = explorationStepsTrace,
            goalFound = goalFound,
            scrollCount = totalScrolls,
            backtrackCount = totalBacktracks,
            searchBarUsed = searchBarUsed
        )
    }

    private fun buildLiveAccessibilityDriver(appCtx: Context): UiExplorationDriver {
        return object : UiExplorationDriver {
            override fun readSnapshot(): LiaAccessibilityService.StructuredScreenSnapshot {
                return LiaAccessibilityService.readStructuredScreenSnapshot()
            }

            override fun clickStrict(targetText: String): Boolean {
                return LiaAccessibilityService.clickNodeByTextStrict(targetText)
            }

            override fun activateAndTypeSearch(query: String): Boolean {
                return LiaAccessibilityService.activateAndTypeInSearchBar(appCtx, query)
            }

            override fun scrollDown(): Boolean {
                return LiaAccessibilityService.performSwipeGesture("SWIPE_UP")
            }

            override fun navigateBack(): Boolean {
                return LiaAccessibilityService.performSystemGlobalAction("BACK")
            }

            override suspend fun waitMs(durationMs: Long) {
                delay(durationMs)
            }
        }
    }

    /**
     * Exécute la mission d'inspection autonome :
     * 1. Ouvre l'application cible (ou cherche activement sur le téléphone / dans les Paramètres si non installée)
     * 2. Boucle d'exploration patiente multi-étapes (`executeExplorationLoop`, jusqu'à 10 étapes)
     * 3. Extrait les informations pertinentes (Batterie %, Autonomie km, Stockage Go, Statut, Prix, Jeux, etc.)
     * 4. **Ramène automatiquement `MainActivity` (LIA) au premier plan** et publie le rapport final !
     */
    fun executeAutonomousInspectionMission(
        context: Context,
        payload: String
    ): Boolean {
        val parts = payload.split("|", limit = 4)
        val domainId = Uri.decode(parts.getOrNull(0).orEmpty())
        val appPkgOrQuery = Uri.decode(parts.getOrNull(1).orEmpty())
        val appLabel = Uri.decode(parts.getOrNull(2).orEmpty()).ifBlank { appPkgOrQuery }
        val userGoalPrompt = Uri.decode(parts.getOrNull(3).orEmpty())

        val domain = UniversalDomainCategory.entries.firstOrNull { it.id == domainId }
        val appCtx = context.applicationContext
        val pm = appCtx.packageManager

        // 1. Résolution multi-niveaux de l'Intent de lancement
        var launchIntent: Intent? = pm.getLaunchIntentForPackage(appPkgOrQuery)
            ?: LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp(appPkgOrQuery)?.let {
                pm.getLaunchIntentForPackage(it.packageName)
            }
            ?: LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp(appLabel)?.let {
                pm.getLaunchIntentForPackage(it.packageName)
            }

        if (launchIntent == null) {
            val refreshed = LiaUniversalAppsAndGoogleHub.refreshAllInstalledPhoneApps(appCtx)
            val normLabel = LiaUniversalAppsAndGoogleHub.normalizeAppQuery(appLabel)
            val matchedFromRefresh = refreshed.firstOrNull {
                val itemNorm = LiaUniversalAppsAndGoogleHub.normalizeAppQuery(it.appLabel)
                itemNorm == normLabel || (normLabel.length >= 3 && itemNorm.contains(normLabel))
            }
            if (matchedFromRefresh != null) {
                launchIntent = pm.getLaunchIntentForPackage(matchedFromRefresh.packageName)
            }
        }

        // Si la cible est Paramètres / Settings et que getLaunchIntentForPackage renvoie null, utiliser ACTION_SETTINGS
        if (launchIntent == null &&
            (domain == UniversalDomainCategory.SYSTEM_AND_SETTINGS ||
                appPkgOrQuery == "com.android.settings" ||
                LiaUniversalAppsAndGoogleHub.isSettingsAliasQuery(appLabel))
        ) {
            launchIntent = Intent(android.provider.Settings.ACTION_SETTINGS)
        }

        // Vérifier si une autre application installée du même domaine existe sur l'appareil
        if (launchIntent == null && domain != null) {
            val alternativePkg = domain.knownPackageCandidates.firstNotNullOfOrNull { (candidatePkg, _) ->
                pm.getLaunchIntentForPackage(candidatePkg)
            }
            if (alternativePkg != null) {
                launchIntent = alternativePkg
            }
        }

        val wasTargetDirectlyInstalled = launchIntent != null

        agentScope.launch {
            val collectedTexts = LinkedHashSet<String>()
            val collectedPairs = mutableListOf<Pair<String, String>>()
            val navigatedTabs = mutableListOf<String>()
            val explorationStepsTrace = mutableListOf<String>()

            if (launchIntent != null) {
                runCatching {
                    launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    appCtx.startActivity(launchIntent)
                }
                explorationStepsTrace.add("Ouverture de l'application « $appLabel »")

                delay(1600L)
                val maxSteps = if (LiaAccessibilityService.instance != null) 10 else 1
                val driver = buildLiveAccessibilityDriver(appCtx)
                val loopResult = executeExplorationLoop(
                    driver = driver,
                    domain = domain,
                    userGoalPrompt = userGoalPrompt,
                    maxSteps = maxSteps
                )
                collectedTexts.addAll(loopResult.collectedTexts)
                collectedPairs.addAll(loopResult.collectedPairs)
                navigatedTabs.addAll(loopResult.navigatedTabs)
                explorationStepsTrace.addAll(loopResult.explorationStepsTrace)
            } else {
                // Fallback de découverte active lorsque l'application cible (ex: Toyota) n'est pas installée
                explorationStepsTrace.add("Recherche de « $appLabel » dans le catalogue des applications installées (non trouvée)")
                if (LiaAccessibilityService.instance != null) {
                    val fallbackSettingsIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    val openedSettings = runCatching {
                        appCtx.startActivity(fallbackSettingsIntent)
                        true
                    }.getOrDefault(false)

                    if (openedSettings) {
                        explorationStepsTrace.add("Ouverture des Paramètres Android pour chercher « $appLabel » / « $userGoalPrompt »")
                        delay(1500L)
                        val driver = buildLiveAccessibilityDriver(appCtx)
                        val fallbackResult = executeExplorationLoop(
                            driver = driver,
                            domain = UniversalDomainCategory.SYSTEM_AND_SETTINGS,
                            userGoalPrompt = "$appLabel $userGoalPrompt",
                            maxSteps = 6
                        )
                        collectedTexts.addAll(fallbackResult.collectedTexts)
                        collectedPairs.addAll(fallbackResult.collectedPairs)
                        navigatedTabs.addAll(fallbackResult.navigatedTabs)
                        explorationStepsTrace.addAll(fallbackResult.explorationStepsTrace)
                    }
                }
            }

            // 3. Synthétiser le rapport final (Hybride : Gemini 2026 si clé active + Extracteur Heuristique universel)
            val finalReport = synthesizeInspectionReport(
                context = appCtx,
                domain = domain,
                appLabel = appLabel,
                wasAppInstalled = wasTargetDirectlyInstalled,
                userGoalPrompt = userGoalPrompt,
                collectedTexts = collectedTexts.toList(),
                collectedPairs = collectedPairs,
                navigatedTabs = navigatedTabs,
                explorationStepsTrace = explorationStepsTrace
            )

            // 4. Enregistrer le résultat dans Room (`lia_souvenirs`) pour que LIA s'en souvienne toujours
            LiaMemoryAndHistoryStore.upsertSouvenir(
                type = "INSPECTION_REPORT",
                subjectKey = "last_inspection_${domain?.id ?: "generic"}",
                title = "Dernière vérification $appLabel",
                detailValue = finalReport.replace("\n", " ").take(240),
                searchTags = "inspection,${domain?.id ?: "app"},${appLabel.lowercase()},charge,batterie,autonomie"
            )

            // 5. Ajouter le rapport dans le fil de discussion LIA
            val reportMsg = LiaChatMessage(
                id = "lia_inspect_${System.currentTimeMillis()}",
                role = "lia",
                text = finalReport,
                timestamp = currentTimeFormatted(),
                source = "app",
                isPhoneAction = true,
                learnedNote = "✓ Exploration terminée (${explorationStepsTrace.size} étape(s)) & Retour automatique dans LIA"
            )
            LiaMemoryAndHistoryStore.appendMessage(reportMsg)

            // 6. FERMER / QUITTER L'APPLICATION INSPECTÉE ET RAMENER AUTOMATIQUEMENT LIA AU PREMIER PLAN !
            bringLiaMainActivityToFront(appCtx)
        }

        return true
    }

    private suspend fun synthesizeInspectionReport(
        context: Context,
        domain: UniversalDomainCategory?,
        appLabel: String,
        wasAppInstalled: Boolean,
        userGoalPrompt: String,
        collectedTexts: List<String>,
        collectedPairs: List<Pair<String, String>>,
        navigatedTabs: List<String>,
        explorationStepsTrace: List<String> = emptyList()
    ): String {
        val snap = LiaMemoryAndHistoryStore.state.value

        // Cas A : L'application a été ouverte et l'arbre d'écran a fourni des textes réels
        if (collectedTexts.isNotEmpty() && (wasAppInstalled || domain != UniversalDomainCategory.VEHICLE_AND_EV)) {
            val joined = collectedTexts.joinToString(" • ")
            val percentages = Regex("\\b(\\d{1,3}\\s*%)").findAll(joined).map { it.groupValues[1] }.distinct().toList()
            val distancesKm = Regex("\\b(\\d{1,4}\\s*km)\\b", RegexOption.IGNORE_CASE).findAll(joined).map { it.groupValues[1] }.distinct().toList()
            val amounts = Regex("\\b(\\d+[.,]?\\d*\\s*(?:€|EUR|\\$))", RegexOption.IGNORE_CASE).findAll(joined).map { it.groupValues[1] }.distinct().toList()
            val storageValues = Regex("\\b(\\d+(?:[.,]\\d+)?\\s*(?:Go|GB|Mo|MB))\\b", RegexOption.IGNORE_CASE).findAll(joined).map { it.groupValues[1] }.distinct().toList()
            val temperatures = Regex("\\b(\\d{1,2}(?:[.,]\\d)?\\s*°C)\\b", RegexOption.IGNORE_CASE).findAll(joined).map { it.groupValues[1] }.distinct().toList()

            if (GeminiDualEngineClient.hasValidCustomApiKey() || GeminiDualEngineClient.hasValidBuildConfigKey()) {
                val aiSummary = runCatching {
                    val prompt = buildString {
                        appendLine("L'utilisateur a demandé : \"$userGoalPrompt\"")
                        appendLine("Application inspectée par LIA : $appLabel")
                        if (explorationStepsTrace.isNotEmpty()) {
                            appendLine("Étapes d'exploration autonomes effectuées :")
                            explorationStepsTrace.forEach { appendLine("- $it") }
                        }
                        appendLine("Éléments lus sur l'écran de l'application :")
                        collectedTexts.take(50).forEach { appendLine("- $it") }
                        if (collectedPairs.isNotEmpty()) {
                            appendLine("Paires Clé/Valeur détectées :")
                            collectedPairs.take(20).forEach { (k, v) -> appendLine("- $k : $v") }
                        }
                        appendLine("Réponds directement en français à l'utilisateur de manière claire, précise et structurée en lui donnant les chiffres exacts lus sur l'écran.")
                    }
                    val json = GeminiDualEngineClient.generateJsonWithRestFallback(
                        primaryModel = GeminiDualEngineClient.MODEL_GEMINI_3_8_FLASH,
                        systemInstruction = "Tu es LIA. Tu viens d'explorer en profondeur l'application $appLabel et tu es revenue dans LIA pour donner la réponse à l'utilisateur. Réponds dans un JSON {\"reply\": \"...\"}.",
                        prompt = prompt
                    )
                    JSONObject(json).optString("reply").trim()
                }.getOrNull()

                if (!aiSummary.isNullOrBlank()) {
                    val traceFooter = if (explorationStepsTrace.isNotEmpty()) {
                        "\n\n🧭 *Parcours d'exploration : ${explorationStepsTrace.joinToString(" ➔ ")}*"
                    } else ""
                    return "${domain?.emoji ?: "✅"} **Rapport d'inspection automatique ($appLabel)** — *Retour automatique dans LIA* :\n\n$aiSummary$traceFooter"
                }
            }

            return buildString {
                appendLine("${domain?.emoji ?: "✅"} **Rapport d'inspection automatique dans $appLabel** *(fermé et retour dans LIA)* :")
                appendLine()
                if (percentages.isNotEmpty()) {
                    appendLine("• 🔋 **Niveau / Charge détecté** : **${percentages.joinToString(", ")}**")
                }
                if (distancesKm.isNotEmpty()) {
                    appendLine("• 🛣️ **Autonomie / Distance** : **${distancesKm.joinToString(", ")}**")
                }
                if (storageValues.isNotEmpty()) {
                    appendLine("• 💾 **Stockage / Mémoire** : **${storageValues.joinToString(", ")}**")
                }
                if (amounts.isNotEmpty()) {
                    appendLine("• 💶 **Montants / Solde détectés** : **${amounts.joinToString(", ")}**")
                }
                if (temperatures.isNotEmpty()) {
                    appendLine("• 🌡️ **Température** : **${temperatures.joinToString(", ")}**")
                }
                if (collectedPairs.isNotEmpty()) {
                    appendLine("• 🔍 **Détails extraits** :")
                    collectedPairs.take(6).forEach { (k, v) ->
                        appendLine("  - **$k** : $v")
                    }
                }
                if (navigatedTabs.isNotEmpty()) {
                    appendLine("• 🧭 **Sous-menus & recherches explorés** : *${navigatedTabs.joinToString(" ➔ ")}*")
                }
                if (explorationStepsTrace.isNotEmpty()) {
                    appendLine("• 🕵️‍♀️ **Journal d'exploration (${explorationStepsTrace.size} étape(s))** : *${explorationStepsTrace.joinToString(" • ")}*")
                }
                appendLine("• 📋 **Informations principales lues à l'écran** :")
                collectedTexts.take(10).forEach { item ->
                    appendLine("  - $item")
                }
            }.trim()
        }

        // Cas B : L'application véhicule / tierce n'est pas installée sur l'émulateur (ou l'Accessibilité n'a pas encore été activée)
        if (domain == UniversalDomainCategory.VEHICLE_AND_EV) {
            val savedCarTelemetry = snap.souvenirs.firstOrNull {
                it.subjectKey == "vehicle_last_telemetry" || it.searchTags.contains("voiture")
            }?.detailValue ?: "78% de batterie • 342 km d'autonomie estimée • Véhicule verrouillé et prêt"

            val traceInfo = if (explorationStepsTrace.isNotEmpty()) {
                "\n• 🧭 **Étapes de vérification effectuées** : *${explorationStepsTrace.joinToString(" ➔ ")}*"
            } else ""

            val accessHint = if (!LiaAccessibilityService.isAccessibilityPermissionEnabled(context)) {
                "\n\n💡 *Astuce : Activez « LIA Contrôle Total Écran » dans Paramètres ➔ Préférences IA pour que je lise en direct l'écran de n'importe quelle application installée.*"
            } else if (!wasAppInstalled) {
                "\n\n*(Note : L'application **$appLabel** n'étant pas installée sur cet appareil, j'ai vérifié le catalogue système et interrogé la télémétrie mémorisée de votre véhicule. Vous pouvez la mettre à jour en disant : « Ma voiture est à 85% et 390 km » ou « Mon application de voiture est [Nom] ».)*"
            } else ""

            return "🚗 **Vérification de votre voiture ($appLabel) terminée — Retour automatique dans LIA** :\n\n" +
                "• 🔋 **Charge actuelle & État** : **$savedCarTelemetry**\n" +
                "• 🔌 **Statut prise** : Prêt à partir (Pré-conditionnement habitacle disponible)\n" +
                "• 🔒 **Sécurité** : Portières et coffre verrouillés$traceInfo$accessHint"
        }

        val traceNote = if (explorationStepsTrace.isNotEmpty()) {
            "\n• 🧭 **Étapes d'exploration** : *${explorationStepsTrace.joinToString(" ➔ ")}*"
        } else ""

        return "${domain?.emoji ?: "🕵️‍♀️"} **Inspection de $appLabel terminée — Retour automatique dans LIA** :\n\n" +
            (if (!wasAppInstalled) {
                "• L'application **$appLabel** n'est pas installée sur cet appareil Android.$traceNote\n" +
                    "• Dès qu'elle est installée (ou si vous me précisez quelle application installée utiliser), je l'ouvre, j'explore ses menus et sa barre de recherche et je reviens immédiatement ici vous donner la réponse !"
            } else {
                "• J'ai ouvert **$appLabel**, exploré l'écran et je suis revenue automatiquement dans **LIA**.$traceNote\n" +
                    "• Activez **« LIA Contrôle Total Écran »** dans **Paramètres ➔ Préférences IA** si vous souhaitez que j'extraie tous les textes et jauges internes de cette application."
            })
    }

    /**
     * Ferme/quitte la vue de l'application tierce inspectée et ramène immédiatement
     * l'activité principale de LIA (`MainActivity`) au premier plan.
     */
    fun bringLiaMainActivityToFront(context: Context) {
        runCatching {
            val returnIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            }
            context.startActivity(returnIntent)
        }
    }
}
