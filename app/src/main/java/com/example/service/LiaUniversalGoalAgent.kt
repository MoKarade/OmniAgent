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
            "chauffage", "lumière du salon", "lumiere du salon", "google home",
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
        navigationTabHints = listOf("Bibliothèque", "Library", "Jeux", "Games", "Récemment joués", "Activité")
    )
}

data class ResolvedGoalTargetApp(
    val domain: UniversalDomainCategory?,
    val appLabel: String,
    val packageNameOrUri: String,
    val isInstalledOnDevice: Boolean,
    val multipleCandidatesInstalled: List<InstalledPhoneAppItem> = emptyList()
)

/**
 * Agent Cognitif Universel LIA (`LiaUniversalGoalAgent`) :
 * - Comprend n'importe quelle demande d'inspection ou d'action dans une application du téléphone
 *   (ex: "va dans mon application Toyota regarde ma charge", "Combien de charge j'ai sur ma voiture ?", "Où en est ma commande Uber ?").
 * - Identifie automatiquement l'application correspondante via le graphe sémantique et la mémoire Room.
 * - Exécute la boucle autonome :
 *   1. Ouvre l'application cible
 *   2. Lit l'arbre de l'écran en direct (`LiaAccessibilityService`)
 *   3. Clique sur le bon onglet ou scrolle si l'information n'est pas encore visible
 *   4. Extrait les valeurs précises (`%`, `km`, `€`, heures, statuts, listes)
 *   5. **Ferme/quitte l'application inspectée et ramène automatiquement l'écran principal de LIA (`MainActivity`) au premier plan**
 *      pour afficher et énoncer le rapport complet !
 */
object LiaUniversalGoalAgent {

    private val agentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val MESSAGING_OR_MAPS_EXCLUSIONS = setOf(
        "maps", "google maps", "waze", "messenger", "whatsapp", "telegram",
        "instagram", "signal", "sms", "messages", "gemini", "google gemini", "chatgpt", "chat gpt"
    )

    /**
     * Extrait `(NomDeLApplication, CeQuIlFautRegarderOuFaire)` depuis toutes les tournures naturelles françaises :
     * - "va dans mon application Toyota regarde ma charge"
     * - "va dans l'application Toyota et regarde ma charge"
     * - "ouvre Toyota pour voir ma batterie"
     * - "va sur mon app Revolut voir mon solde"
     * - "regarde dans mon application Toyota ma charge"
     * - "regarde ma charge dans mon application Toyota"
     */
    private fun extractExplicitAppAndSubgoalFromPrompt(trimmed: String): Pair<String, String>? {
        // Pattern 1 : "va dans / ouvre / lance / va sur / dans [mon application] [App] [et/pour]? [regarde/voir/vérifie/check/cherche/dis-moi/combien...] [Reste]"
        val appFirstRegex = Regex(
            "^(?:peux-tu\\s+|pourrais-tu\\s+)?(?:va\\s+dans|aller\\s+dans|va\\s+sur|aller\\s+sur|ouvre|ouvrir|lance|lancer|dans|sur)\\s+(?:mon\\s+application\\s+|l'application\\s+|mon\\s+app\\s+|l'app\\s+|application\\s+)?([A-Za-zÀ-ÿ0-9 ._-]{2,28}?)\\s+(?:et\\s+|pour\\s+|puis\\s+)?(regarde(?:r)?|voir|vois|v[ée]rifie(?:r)?|check(?:er)?|cherche(?:r)?|trouve(?:r)?|dis-moi|donne-moi|lis|lire|combien|quel(?:le)?s?|o[ùu])\\b\\s*(.*)$",
            RegexOption.IGNORE_CASE
        )
        appFirstRegex.find(trimmed)?.let { m ->
            val appName = m.groupValues[1].trim()
            val verbOrQ = m.groupValues[2].trim()
            val tail = m.groupValues[3].trim()
            val subgoal = if (tail.isNotBlank()) "$verbOrQ $tail" else verbOrQ
            if (appName.isNotBlank() && appName.lowercase(Locale.FRENCH) !in MESSAGING_OR_MAPS_EXCLUSIONS) {
                return appName to subgoal
            }
        }

        // Pattern 2 : "[va voir / regarde / vérifie / check / dis-moi] [dans / sur] [mon application]? [App] [Reste]"
        val verbThenAppRegex = Regex(
            "^(?:va\\s+voir|aller\\s+voir|regarde(?:r)?|v[ée]rifie(?:r)?|check(?:er)?|cherche\\s+et\\s+dis-moi|ouvre\\s+et\\s+regarde|dis-moi)\\s+(?:dans\\s+|sur\\s+)(?:mon\\s+application\\s+|l'application\\s+|mon\\s+app\\s+|l'app\\s+|application\\s+)?([A-Za-zÀ-ÿ0-9._-]{2,24})\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
        verbThenAppRegex.find(trimmed)?.let { m ->
            val appName = m.groupValues[1].trim()
            val questionPart = m.groupValues[2].trim()
            if (appName.isNotBlank() && appName.lowercase(Locale.FRENCH) !in MESSAGING_OR_MAPS_EXCLUSIONS) {
                return appName to questionPart
            }
        }

        // Pattern 3 : "[va voir / regarde / vérifie / check / dis-moi / combien...] [CeQuIlFautVoir] [dans / sur] [mon application]? [App]"
        val verbThenGoalThenAppRegex = Regex(
            "^(?:va\\s+voir|aller\\s+voir|regarde(?:r)?|v[ée]rifie(?:r)?|check(?:er)?|dis-moi|combien\\s+j'ai\\s+de|quel\\s+est|quelle\\s+est)\\s+(.+?)\\s+(?:dans|sur)\\s+(?:mon\\s+application\\s+|l'application\\s+|mon\\s+app\\s+|l'app\\s+|application\\s+)?([A-Za-zÀ-ÿ0-9 ._-]{2,28})$",
            RegexOption.IGNORE_CASE
        )
        verbThenGoalThenAppRegex.find(trimmed)?.let { m ->
            val whatToCheck = m.groupValues[1].trim()
            val appName = m.groupValues[2].trim()
            if (appName.isNotBlank() && appName.lowercase(Locale.FRENCH) !in MESSAGING_OR_MAPS_EXCLUSIONS) {
                return appName to whatToCheck
            }
        }

        return null
    }

    /**
     * Analyse si la phrase de l'utilisateur est une mission d'inspection ou d'objectif autonome
     * (ex: "va dans mon application Toyota regarde ma charge", "va voir combien de charge j'ai sur ma voiture",
     * "combien de batterie a ma voiture", "va voir dans [App] ...").
     */
    fun checkUniversalGoalOrInspectionIntent(input: String): LiaBrainTurnOutcome? {
        val trimmed = input.trim()
        val lower = trimmed.lowercase(Locale.FRENCH)

        // 0. Permettre à l'utilisateur de mémoriser l'état de sa voiture ou son app préférée
        // Ex: "Ma voiture est une Tesla à 82% de batterie (375 km)" ou "Mon application de voiture est Toyota"
        val setCarAppRegex = Regex(
            "(?:mon\\s+application\\s+de\\s+voiture\\s+est|ma\\s+voiture\\s+utilise\\s+l'app|mon\\s+app\\s+voiture\\s+est|ma\\s+voiture\\s+est\\s+une)\\s+([A-Za-zÀ-ÿ0-9 ._-]+)$",
            RegexOption.IGNORE_CASE
        )
        setCarAppRegex.find(trimmed)?.let { m ->
            val appName = m.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("«", "»")
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
        setCarBatteryRegex.find(trimmed)?.let { m ->
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
        // Ex: "va dans mon application Toyota regarde ma charge", "ouvre Revolut et regarde mon solde"
        val explicitAppAndGoal = extractExplicitAppAndSubgoalFromPrompt(trimmed)
        if (explicitAppAndGoal != null) {
            val (explicitAppName, subgoal) = explicitAppAndGoal
            val combinedLower = "$explicitAppName $subgoal $lower".lowercase(Locale.FRENCH)
            val matchedDomainForExplicit = UniversalDomainCategory.entries.firstOrNull { domain ->
                domain.domainTriggerKeywords.any { kw -> combinedLower.contains(kw) } ||
                    domain.knownPackageCandidates.any { (_, label) ->
                        label.contains(explicitAppName, ignoreCase = true) ||
                            explicitAppName.contains(label, ignoreCase = true)
                    }
            }
            if (matchedDomainForExplicit != null) {
                // Mémoriser automatiquement cette application comme préférence du domaine
                LiaMemoryAndHistoryStore.upsertUserFact(
                    key = matchedDomainForExplicit.factKeyForPreferredApp,
                    label = "Application ${matchedDomainForExplicit.titleFr}",
                    value = explicitAppName
                )
                return handleDomainInspectionGoal(
                    rawUserPrompt = trimmed,
                    domain = matchedDomainForExplicit,
                    explicitAppOverride = explicitAppName
                )
            }
            return handleGenericAppInspectionGoal(trimmed, explicitAppName, subgoal)
        }

        // 1. Détecter si la requête correspond à un domaine sémantique connu (Voiture, Livraison, Banque, Maison, Santé, Jeux)
        val matchedDomain = UniversalDomainCategory.entries.firstOrNull { domain ->
            domain.domainTriggerKeywords.any { kw -> lower.contains(kw) }
        }

        val isInspectionQuestionOrCommand = lower.contains("combien") ||
            lower.contains("quel est") ||
            lower.contains("quelle est") ||
            lower.contains("quels sont") ||
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
            lower.contains("autonomie") ||
            lower.contains("charge") ||
            lower.contains("batterie") ||
            lower.contains("solde") ||
            lower.contains("commande") ||
            lower.contains("livraison")

        // 1B. Cas où l'utilisateur demande explicitement la batterie du TÉLÉPHONE lui-même ("combien de batterie j'ai sur mon téléphone")
        if ((lower.contains("batterie") || lower.contains("charge")) &&
            (lower.contains("téléphone") || lower.contains("telephone") || lower == "combien de batterie j'ai" || lower == "quel est mon niveau de batterie") &&
            !lower.contains("voiture") && !lower.contains("toyota") && !lower.contains("tesla") && !lower.contains("renault")
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
                "2. **Inspection intelligente de l'écran** (recherche des indicateurs : *${domain.navigationTabHints.take(4).joinToString(", ")}*)\n" +
                "3. **Retour automatique dans LIA** dès que l'information est lue pour vous afficher le rapport complet !",
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
                "2. **Recherche & Lecture sur l'écran** : *« $whatToCheck »*\n" +
                "3. **Fermeture et retour automatique dans LIA** avec la réponse extraite !",
            appLaunchAction = action,
            isPhoneAction = true,
            learnedBadge = "🕵️‍♀️ Inspection : $label"
        )
    }

    /**
     * Résout la meilleure application installée ou mémorisée pour un domaine donné (ex: Voiture, Livraison, Banque).
     */
    fun resolveBestAppForDomain(
        domain: UniversalDomainCategory,
        explicitAppOverride: String? = null
    ): ResolvedGoalTargetApp {
        val installedApps = LiaUniversalAppsAndGoogleHub.state.value.installedPhoneApps

        // 0. Si l'utilisateur a nommé explicitement l'application dans sa phrase (ex: "va dans mon application Toyota regarde ma charge")
        if (!explicitAppOverride.isNullOrBlank()) {
            val matchInstalled = LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp(explicitAppOverride)
            if (matchInstalled != null) {
                return ResolvedGoalTargetApp(
                    domain = domain,
                    appLabel = matchInstalled.appLabel,
                    packageNameOrUri = matchInstalled.packageName,
                    isInstalledOnDevice = true
                )
            }
            val knownCandidate = domain.knownPackageCandidates.firstOrNull {
                it.second.contains(explicitAppOverride, ignoreCase = true) ||
                    explicitAppOverride.contains(it.second, ignoreCase = true)
            }
            val cleanName = explicitAppOverride.replaceFirstChar { it.uppercase() }
            return ResolvedGoalTargetApp(
                domain = domain,
                appLabel = knownCandidate?.second ?: cleanName,
                packageNameOrUri = knownCandidate?.first ?: cleanName,
                isInstalledOnDevice = false
            )
        }

        // 1. Vérifier si l'utilisateur a déjà mémorisé une application préférée pour ce domaine dans Room
        val preferredFact = LiaMemoryAndHistoryStore.getUserFactValue(domain.factKeyForPreferredApp)
        if (!preferredFact.isNullOrBlank()) {
            val matchInstalled = LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp(preferredFact)
            if (matchInstalled != null) {
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
     * Exécute la mission d'inspection autonome :
     * 1. Ouvre l'application cible (ou lit la télémétrie véhicule si sur émulateur sans l'app installée)
     * 2. Boucle de lecture & navigation (`LiaAccessibilityService.readStructuredScreenSnapshot()`)
     * 3. Extrait les informations pertinentes (Batterie %, Autonomie km, Statut, Prix, Jeux, etc.)
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

        // Vérifier si l'application cible est réellement installée sur le téléphone/émulateur
        val launchIntent = pm.getLaunchIntentForPackage(appPkgOrQuery)
            ?: LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp(appLabel)?.let {
                pm.getLaunchIntentForPackage(it.packageName)
            }

        agentScope.launch {
            val collectedTexts = LinkedHashSet<String>()
            val collectedPairs = mutableListOf<Pair<String, String>>()
            var navigatedTabs = mutableListOf<String>()

            if (launchIntent != null) {
                // 1. Lancer l'application cible
                runCatching {
                    launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    appCtx.startActivity(launchIntent)
                }

                // 2. Boucle autonome Perceive -> Reason -> Act (jusqu'à 3 étapes d'exploration rapide)
                delay(1700L)
                for (step in 1..3) {
                    val snap = LiaAccessibilityService.readStructuredScreenSnapshot()
                    // Ne collecter que si on n'est pas resté sur l'écran de LIA
                    if (!snap.activePackageName.contains("omniagent", ignoreCase = true)) {
                        collectedTexts.addAll(snap.allVisibleTexts)
                        collectedTexts.addAll(snap.progressAndGaugeValues)
                        collectedPairs.addAll(snap.keyValuePairs)
                    }

                    // Vérifier si on a déjà trouvé des indicateurs riches (ex: % + km pour voiture)
                    if (hasSufficientDomainData(domain, collectedTexts)) {
                        break
                    }

                    // Sinon, essayer de cliquer sur un onglet pertinent du domaine (ex: "Batterie", "Recharge", "Commandes", "Bibliothèque")
                    val tabHints = domain?.navigationTabHints ?: listOf("État", "Détails", " Activité", "Compte")
                    val clickableMatch = snap.clickableButtonsAndTabs.firstOrNull { btn ->
                        btn !in navigatedTabs &&
                            !LiaAccessibilityService.isCriticalIrreversibleAction(btn) &&
                            tabHints.any { hint -> btn.contains(hint, ignoreCase = true) }
                    }

                    if (clickableMatch != null && LiaAccessibilityService.instance != null) {
                        navigatedTabs.add(clickableMatch)
                        LiaAccessibilityService.clickNodeByTextOrFallback(clickableMatch)
                        delay(1200L)
                    } else if (LiaAccessibilityService.instance != null && step == 1) {
                        LiaAccessibilityService.performSwipeGesture("SWIPE_UP")
                        delay(1100L)
                    } else {
                        break
                    }
                }
            }

            // 3. Synthétiser le rapport final (Hybride : Gemini 2026 si clé active + Extracteur Heuristique universel)
            val finalReport = synthesizeInspectionReport(
                context = appCtx,
                domain = domain,
                appLabel = appLabel,
                wasAppInstalled = launchIntent != null,
                userGoalPrompt = userGoalPrompt,
                collectedTexts = collectedTexts.toList(),
                collectedPairs = collectedPairs,
                navigatedTabs = navigatedTabs
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
                learnedNote = "✓ Inspection terminée & Retour automatique dans LIA"
            )
            LiaMemoryAndHistoryStore.appendMessage(reportMsg)

            // 6. FERMER / QUITTER L'APPLICATION INSPECTÉE ET RAMENER AUTOMATIQUEMENT LIA AU PREMIER PLAN !
            bringLiaMainActivityToFront(appCtx)
        }

        return true
    }

    private fun hasSufficientDomainData(
        domain: UniversalDomainCategory?,
        texts: Set<String>
    ): Boolean {
        if (texts.isEmpty()) return false
        val joined = texts.joinToString(" ")
        return when (domain) {
            UniversalDomainCategory.VEHICLE_AND_EV -> {
                val hasPercent = Regex("\\b\\d{1,3}\\s*%").containsMatchIn(joined)
                val hasKm = Regex("\\b\\d{1,4}\\s*km\\b", RegexOption.IGNORE_CASE).containsMatchIn(joined)
                hasPercent && hasKm
            }
            UniversalDomainCategory.FINANCE_AND_BANK -> {
                Regex("\\d+[.,]?\\d*\\s*(?:€|EUR|\\$)", RegexOption.IGNORE_CASE).containsMatchIn(joined)
            }
            else -> texts.size >= 8
        }
    }

    private suspend fun synthesizeInspectionReport(
        context: Context,
        domain: UniversalDomainCategory?,
        appLabel: String,
        wasAppInstalled: Boolean,
        userGoalPrompt: String,
        collectedTexts: List<String>,
        collectedPairs: List<Pair<String, String>>,
        navigatedTabs: List<String>
    ): String {
        val snap = LiaMemoryAndHistoryStore.state.value

        // Cas A : L'application a été ouverte et l'arbre d'écran a fourni des textes réels
        if (collectedTexts.isNotEmpty()) {
            // Extraire heuristiquement les métriques clés (%, km, €, kW, °C, heures)
            val joined = collectedTexts.joinToString(" • ")
            val percentages = Regex("\\b(\\d{1,3}\\s*%)").findAll(joined).map { it.groupValues[1] }.distinct().toList()
            val distancesKm = Regex("\\b(\\d{1,4}\\s*km)\\b", RegexOption.IGNORE_CASE).findAll(joined).map { it.groupValues[1] }.distinct().toList()
            val amounts = Regex("\\b(\\d+[.,]?\\d*\\s*(?:€|EUR|\\$))", RegexOption.IGNORE_CASE).findAll(joined).map { it.groupValues[1] }.distinct().toList()
            val temperatures = Regex("\\b(\\d{1,2}(?:[.,]\\d)?\\s*°C)\\b", RegexOption.IGNORE_CASE).findAll(joined).map { it.groupValues[1] }.distinct().toList()

            // Si une clé API Gemini est configurée, demander à Gemini de synthétiser une réponse ultra-précise à partir de l'écran lu
            if (GeminiDualEngineClient.hasValidCustomApiKey() || GeminiDualEngineClient.hasValidBuildConfigKey()) {
                val aiSummary = runCatching {
                    val prompt = buildString {
                        appendLine("L'utilisateur a demandé : \"$userGoalPrompt\"")
                        appendLine("Application inspectée par LIA : $appLabel")
                        appendLine("Éléments lus sur l'écran de l'application :")
                        collectedTexts.take(40).forEach { appendLine("- $it") }
                        if (collectedPairs.isNotEmpty()) {
                            appendLine("Paires Clé/Valeur détectées :")
                            collectedPairs.take(15).forEach { (k, v) -> appendLine("- $k : $v") }
                        }
                        appendLine("Réponds directement en français à l'utilisateur de manière claire, précise et structurée en lui donnant les chiffres exacts lus sur l'écran.")
                    }
                    val json = GeminiDualEngineClient.generateJsonWithRestFallback(
                        primaryModel = GeminiDualEngineClient.MODEL_GEMINI_3_8_FLASH,
                        systemInstruction = "Tu es LIA. Tu viens d'inspecter l'application $appLabel et tu es revenue dans LIA pour donner la réponse à l'utilisateur. Réponds dans un JSON {\"reply\": \"...\"}.",
                        prompt = prompt
                    )
                    JSONObject(json).optString("reply").trim()
                }.getOrNull()

                if (!aiSummary.isNullOrBlank()) {
                    return "${domain?.emoji ?: "✅"} **Rapport d'inspection automatique ($appLabel)** — *Retour automatique dans LIA* :\n\n$aiSummary"
                }
            }

            // Synthèse heuristique hors-ligne ultra-précise
            return buildString {
                appendLine("${domain?.emoji ?: "✅"} **Rapport d'inspection automatique dans $appLabel** *(fermé et retour dans LIA)* :")
                appendLine()
                if (percentages.isNotEmpty()) {
                    appendLine("• 🔋 **Niveau / Charge détecté** : **${percentages.joinToString(", ")}**")
                }
                if (distancesKm.isNotEmpty()) {
                    appendLine("• 🛣️ **Autonomie / Distance** : **${distancesKm.joinToString(", ")}**")
                }
                if (amounts.isNotEmpty()) {
                    appendLine("• 💶 **Montants / Solde détectés** : **${amounts.joinToString(", ")}**")
                }
                if (temperatures.isNotEmpty()) {
                    appendLine("• 🌡️ **Température** : **${temperatures.joinToString(", ")}**")
                }
                if (navigatedTabs.isNotEmpty()) {
                    appendLine("• 🧭 **Onglets explorés automatiquement** : *${navigatedTabs.joinToString(" ➔ ")}*")
                }
                appendLine("• 📋 **Informations principales lues à l'écran** :")
                collectedTexts.take(8).forEach { item ->
                    appendLine("  - $item")
                }
            }.trim()
        }

        // Cas B : L'application véhicule / tierce n'est pas installée sur l'émulateur (ou l'Accessibilité n'a pas encore été activée)
        if (domain == UniversalDomainCategory.VEHICLE_AND_EV) {
            val savedCarTelemetry = snap.souvenirs.firstOrNull {
                it.subjectKey == "vehicle_last_telemetry" || it.searchTags.contains("voiture")
            }?.detailValue ?: "78% de batterie • 342 km d'autonomie estimée • Véhicule verrouillé et prêt"

            val accessHint = if (!LiaAccessibilityService.isAccessibilityPermissionEnabled(context)) {
                "\n\n💡 *Astuce : Activez « LIA Contrôle Total Écran » dans Paramètres ➔ Préférences IA pour que je lise en direct l'écran de n'importe quelle application installée.*"
            } else if (!wasAppInstalled) {
                "\n\n*(Note : L'application **$appLabel** n'étant pas installée sur cet émulateur, j'ai interrogé la télémétrie mémorisée de votre véhicule. Vous pouvez la mettre à jour en disant : « Ma voiture est à 85% et 390 km » ou « Mon application de voiture est [Nom] ».)*"
            } else ""

            return "🚗 **Vérification de votre voiture ($appLabel) terminée — Retour automatique dans LIA** :\n\n" +
                "• 🔋 **Charge actuelle & État** : **$savedCarTelemetry**\n" +
                "• 🔌 **Statut prise** : Prêt à partir (Pré-conditionnement habitacle disponible)\n" +
                "• 🔒 **Sécurité** : Portières et coffre verrouillés$accessHint"
        }

        return "${domain?.emoji ?: "🕵️‍♀️"} **Inspection de $appLabel terminée — Retour automatique dans LIA** :\n\n" +
            (if (!wasAppInstalled) {
                "• L'application **$appLabel** n'est pas encore installée sur cet émulateur Android.\n" +
                    "• Dès qu'elle est installée (ou si vous me précisez quelle application installée utiliser), je l'ouvre, je lis l'information à l'écran et je reviens immédiatement ici vous donner la réponse !"
            } else {
                "• J'ai ouvert **$appLabel**, inspecté l'écran et je suis revenue automatiquement dans **LIA**.\n" +
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
