package com.example.service

import android.net.Uri
import com.example.data.InstalledPhoneAppItem
import com.example.data.LiaMemoryAndHistoryStore
import com.example.data.LiaUniversalAppsAndGoogleHub
import com.example.security.ActionTargetSafetyVerdict
import com.example.security.SoniaSecurityKernel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiaUniversalGoalAgentAndAppsTest {

    @Test
    fun `normalizeAppQuery strips French determiners articles and accents accurately`() {
        assertEquals("parametres", LiaUniversalAppsAndGoogleHub.normalizeAppQuery("paramètres"))
        assertEquals("parametres", LiaUniversalAppsAndGoogleHub.normalizeAppQuery("parametres"))
        assertEquals("parametres", LiaUniversalAppsAndGoogleHub.normalizeAppQuery("les paramètres"))
        assertEquals("parametres", LiaUniversalAppsAndGoogleHub.normalizeAppQuery("les parametres"))
        assertEquals("reglages", LiaUniversalAppsAndGoogleHub.normalizeAppQuery("les réglages"))
        assertEquals("settings", LiaUniversalAppsAndGoogleHub.normalizeAppQuery("settings"))
        assertEquals("toyota", LiaUniversalAppsAndGoogleHub.normalizeAppQuery("mon application Toyota"))
        assertEquals("steam", LiaUniversalAppsAndGoogleHub.normalizeAppQuery("dans l'application Steam"))
        assertEquals("revolut", LiaUniversalAppsAndGoogleHub.normalizeAppQuery("sur mon app Revolut"))
    }

    @Test
    fun `findBestMatchingInstalledApp resolves bilingual Settings aliases and prevents short label false positives`() {
        // Simulate an English-locale Android emulator where Settings is indexed as "Settings"
        // plus a short-label app "On" / "TV" that should never false-match longer words like "Toyota" or "Notifications"
        LiaUniversalAppsAndGoogleHub.setInstalledAppsForTesting(
            listOf(
                InstalledPhoneAppItem("com.android.settings", "Settings", "⚙️", false, false, "12:00"),
                InstalledPhoneAppItem("com.example.shorton", "On", "📺", false, false, "12:00"),
                InstalledPhoneAppItem("com.example.shorttv", "TV", "📺", false, false, "12:00"),
                InstalledPhoneAppItem("com.valvesoftware.android.steam.community", "Steam", "🎮", false, false, "12:00")
            )
        )

        val settingsQueries = listOf(
            "paramètres",
            "parametres",
            "les paramètres",
            "les parametres",
            "réglages",
            "reglages",
            "settings",
            "configuration",
            "système"
        )
        for (q in settingsQueries) {
            val resolved = LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp(q)
            assertNotNull("Expected '$q' to resolve to com.android.settings", resolved)
            assertEquals("com.android.settings", resolved?.packageName)
            assertEquals("Paramètres", resolved?.appLabel)
        }

        val steamResolved = LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp("dans l'application Steam")
        assertNotNull(steamResolved)
        assertEquals("com.valvesoftware.android.steam.community", steamResolved?.packageName)

        // "mon application Toyota" contains "on" in "mon"/"application", but must NOT false-match the 2-char "On" app!
        val uninstalledToyota = LiaUniversalAppsAndGoogleHub.findBestMatchingInstalledApp("mon application Toyota")
        assertNull(uninstalledToyota)
    }

    @Test
    fun `extractExplicitAppAndSubgoalFromPrompt handles all French natural phrasing patterns`() {
        val p1 = LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
            "va dans mon application Toyota regarde ma charge"
        )
        assertNotNull(p1)
        assertEquals("Toyota", p1?.first)
        assertEquals("regarde ma charge", p1?.second)

        val p2 = LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
            "va dans les paramètres regarde ma batterie"
        )
        assertNotNull(p2)
        assertEquals("Paramètres", p2?.first)
        assertEquals("regarde ma batterie", p2?.second)

        val p3 = LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
            "ouvre les paramètres et cherche le stockage"
        )
        assertNotNull(p3)
        assertEquals("Paramètres", p3?.first)
        assertEquals("cherche le stockage", p3?.second)

        val p4 = LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
            "cherche mes notifications dans les paramètres"
        )
        assertNotNull(p4)
        assertEquals("Paramètres", p4?.first)
        assertEquals("mes notifications", p4?.second)

        val p5 = LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
            "va dans Steam et vérifie mes téléchargements"
        )
        assertNotNull(p5)
        assertEquals("Steam", p5?.first)
        assertEquals("vérifie mes téléchargements", p5?.second)

        val p6 = LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
            "va dans Toyota ma charge"
        )
        assertNotNull(p6)
        assertEquals("Toyota", p6?.first)
        assertEquals("ma charge", p6?.second)

        // Multi-word app names in Pattern 1B and Pattern 2
        val p7 = LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
            "va dans My Renault ma charge"
        )
        assertNotNull(p7)
        assertEquals("My Renault", p7?.first)
        assertEquals("ma charge", p7?.second)

        val p8 = LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
            "va dans Uber Eats ma commande"
        )
        assertNotNull(p8)
        assertEquals("Uber Eats", p8?.first)
        assertEquals("ma commande", p8?.second)

        val p9 = LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
            "va voir dans Google Fit mes pas"
        )
        assertNotNull(p9)
        assertEquals("Google Fit", p9?.first)
        assertEquals("mes pas", p9?.second)

        val p10 = LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
            "regarde dans Google Home la température du salon"
        )
        assertNotNull(p10)
        assertEquals("Google Home", p10?.first)
        assertEquals("la température du salon", p10?.second)

        // Generic domain/device nouns MUST be recognized by isGenericNonAppTarget and MUST return null here
        assertTrue(LiaUniversalGoalAgent.isGenericNonAppTarget("ma voiture"))
        assertTrue(LiaUniversalGoalAgent.isGenericNonAppTarget("mon téléphone"))
        assertTrue(LiaUniversalGoalAgent.isGenericNonAppTarget("mon compte bancaire"))
        assertTrue(LiaUniversalGoalAgent.isGenericNonAppTarget("la maison"))
        assertFalse(LiaUniversalGoalAgent.isGenericNonAppTarget("Paramètres"))
        assertFalse(LiaUniversalGoalAgent.isGenericNonAppTarget("My Renault"))
        assertFalse(LiaUniversalGoalAgent.isGenericNonAppTarget("Banque Populaire"))

        assertNull(
            LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
                "regarde combien de charge j'ai sur ma voiture"
            )
        )
        assertNull(
            LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
                "combien de charge j'ai sur ma voiture"
            )
        )
        assertNull(
            LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
                "va voir combien de charge j'ai sur ma voiture"
            )
        )
        assertNull(
            LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
                "combien de batterie j'ai sur mon téléphone"
            )
        )
        assertNull(
            LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
                "vérifie combien j'ai sur mon compte bancaire"
            )
        )
        assertNull(
            LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
                "regarde la température dans la maison"
            )
        )
    }

    @Test
    fun `checkUniversalGoalOrInspectionIntent routes vehicle and Settings goals without domain hijacking`() {
        // 1. Explicit Toyota inspection
        val toyotaOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "va dans mon application Toyota regarde ma charge"
        )
        assertNotNull(toyotaOutcome)
        val toyotaUri = toyotaOutcome?.appLaunchAction?.packageName.orEmpty()
        assertTrue(toyotaUri.startsWith("agent_goal:vehicle_ev|"))
        assertTrue(Uri.decode(toyotaUri).contains("Toyota", ignoreCase = true))

        // 2. Implicit vehicle inspection ("regarde combien de charge j'ai sur ma voiture")
        val carOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "regarde combien de charge j'ai sur ma voiture"
        )
        assertNotNull(carOutcome)
        val carUri = carOutcome?.appLaunchAction?.packageName.orEmpty()
        assertTrue(carUri.startsWith("agent_goal:vehicle_ev|"))

        // 3. Explicit Settings inspection asking about "batterie" MUST target com.android.settings (system_settings)
        // and MUST NOT hijack into vehicle_ev or overwrite preferred_app_vehicle_ev!
        val settingsBatteryOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "va dans les paramètres regarde ma batterie"
        )
        assertNotNull(settingsBatteryOutcome)
        val settingsUri = settingsBatteryOutcome?.appLaunchAction?.packageName.orEmpty()
        assertTrue(
            "Expected system_settings domain but got: $settingsUri",
            settingsUri.startsWith("agent_goal:system_settings|")
        )
        assertTrue(Uri.decode(settingsUri).contains("com.android.settings"))
        assertNotEquals(
            "Paramètres",
            LiaMemoryAndHistoryStore.getUserFactValue(UniversalDomainCategory.VEHICLE_AND_EV.factKeyForPreferredApp)
        )

        // 4. "ouvre les paramètres et cherche le stockage"
        val settingsStorageOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "ouvre les paramètres et cherche le stockage"
        )
        assertNotNull(settingsStorageOutcome)
        assertTrue(
            settingsStorageOutcome?.appLaunchAction?.packageName.orEmpty()
                .startsWith("agent_goal:system_settings|")
        )

        // 5. "cherche mes notifications dans les paramètres"
        val settingsNotifOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "cherche mes notifications dans les paramètres"
        )
        assertNotNull(settingsNotifOutcome)
        assertTrue(
            settingsNotifOutcome?.appLaunchAction?.packageName.orEmpty()
                .startsWith("agent_goal:system_settings|")
        )

        // 6. "va dans Steam et vérifie mes téléchargements"
        val steamOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "va dans Steam et vérifie mes téléchargements"
        )
        assertNotNull(steamOutcome)
        val steamUri = steamOutcome?.appLaunchAction?.packageName.orEmpty()
        assertTrue(steamUri.startsWith("agent_goal:gaming_steam|"))
        assertTrue(Uri.decode(steamUri).contains("steam", ignoreCase = true))

        // 7. Implicit vehicle questions without or with "va voir" ("combien de charge j'ai sur ma voiture")
        val carQuestionOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "combien de charge j'ai sur ma voiture"
        )
        assertNotNull(carQuestionOutcome)
        val carQuestionUri = carQuestionOutcome?.appLaunchAction?.packageName.orEmpty()
        assertTrue(
            "Expected vehicle_ev domain for 'combien de charge j'ai sur ma voiture', got: $carQuestionUri",
            carQuestionUri.startsWith("agent_goal:vehicle_ev|")
        )

        val vaVoirCarOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "va voir combien de charge j'ai sur ma voiture"
        )
        assertNotNull(vaVoirCarOutcome)
        assertTrue(
            vaVoirCarOutcome?.appLaunchAction?.packageName.orEmpty()
                .startsWith("agent_goal:vehicle_ev|")
        )

        // 8. Phone battery question ("combien de batterie j'ai sur mon téléphone") MUST route to phone battery action
        // and MUST NOT hijack into generic 'téléphone' app or vehicle_ev!
        val phoneBatteryOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "combien de batterie j'ai sur mon téléphone"
        )
        assertNotNull(phoneBatteryOutcome)
        val phoneBatteryUri = phoneBatteryOutcome?.appLaunchAction?.packageName.orEmpty()
        assertFalse(phoneBatteryUri.startsWith("agent_goal:generic|"))
        assertFalse(phoneBatteryUri.startsWith("agent_goal:vehicle_ev|"))
        assertTrue(phoneBatteryOutcome?.isPhoneAction == true)
        assertNull(phoneBatteryOutcome?.appLaunchAction)

        // 9. Implicit bank balance inspection ("vérifie combien j'ai sur mon compte bancaire")
        val bankOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "vérifie combien j'ai sur mon compte bancaire"
        )
        assertNotNull(bankOutcome)
        val bankUri = bankOutcome?.appLaunchAction?.packageName.orEmpty()
        assertTrue(
            "Expected finance_bank domain for 'vérifie combien j'ai sur mon compte bancaire', got: $bankUri",
            bankUri.startsWith("agent_goal:finance_bank|")
        )

        // 10. Implicit smart-home house temperature inspection ("regarde la température dans la maison")
        val homeTempOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "regarde la température dans la maison"
        )
        assertNotNull(homeTempOutcome)
        val homeTempUri = homeTempOutcome?.appLaunchAction?.packageName.orEmpty()
        assertTrue(
            "Expected smart_home domain for 'regarde la température dans la maison', got: $homeTempUri",
            homeTempUri.startsWith("agent_goal:smart_home|")
        )

        // 11. Multi-word apps in Pattern 1B and Pattern 2 ("My Renault", "Uber Eats", "Google Fit", "Google Home")
        val renaultOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "va dans My Renault ma charge"
        )
        assertNotNull(renaultOutcome)
        val renaultUri = renaultOutcome?.appLaunchAction?.packageName.orEmpty()
        assertTrue(renaultUri.startsWith("agent_goal:vehicle_ev|"))
        assertTrue(Uri.decode(renaultUri).contains("My Renault", ignoreCase = true))

        val uberEatsOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "va dans Uber Eats ma commande"
        )
        assertNotNull(uberEatsOutcome)
        val uberEatsUri = uberEatsOutcome?.appLaunchAction?.packageName.orEmpty()
        assertTrue(uberEatsUri.startsWith("agent_goal:delivery_rides|"))
        assertTrue(Uri.decode(uberEatsUri).contains("Uber Eats", ignoreCase = true))

        val fitOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "va voir dans Google Fit mes pas"
        )
        assertNotNull(fitOutcome)
        val fitUri = fitOutcome?.appLaunchAction?.packageName.orEmpty()
        assertTrue(fitUri.startsWith("agent_goal:health_fitness|"))
        assertTrue(Uri.decode(fitUri).contains("Google Fit", ignoreCase = true))

        val googleHomeOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "regarde dans Google Home la température du salon"
        )
        assertNotNull(googleHomeOutcome)
        val googleHomeUri = googleHomeOutcome?.appLaunchAction?.packageName.orEmpty()
        assertTrue(googleHomeUri.startsWith("agent_goal:smart_home|"))
        assertTrue(Uri.decode(googleHomeUri).contains("Google Home", ignoreCase = true))
    }

    @Test
    fun `isGoalInformationFound and hasSufficientDomainData do not quit prematurely on 8+ menu items`() {
        val goalKeywords = LiaUniversalGoalAgent.extractGoalKeywords(
            "ouvre les paramètres et cherche le stockage",
            UniversalDomainCategory.SYSTEM_AND_SETTINGS
        )
        assertTrue(goalKeywords.contains("stockage"))
        assertTrue(goalKeywords.contains("storage"))

        // A typical Settings home screen has 10+ visible text items, none of which contain the storage metric
        val settingsMainMenuTexts = setOf(
            "Rechercher dans les paramètres",
            "Réseau et Internet",
            "Wi-Fi, réseau mobile, consommation",
            "Appareils connectés",
            "Bluetooth, association",
            "Applications",
            "Applications récentes, applis par défaut",
            "Notifications",
            "Historique des notifications, conversations",
            "Son et vibration",
            "Volume, Ne pas déranger",
            "Affichage"
        )
        assertTrue(settingsMainMenuTexts.size >= 8)

        // Previously texts.size >= 8 returned true on step 1! Now it MUST return false so LIA keeps exploring.
        assertFalse(
            LiaUniversalGoalAgent.isGoalInformationFound(
                domain = UniversalDomainCategory.SYSTEM_AND_SETTINGS,
                goalKeywords = goalKeywords,
                collectedTexts = settingsMainMenuTexts,
                collectedPairs = emptyList(),
                step = 1
            )
        )
        assertFalse(
            LiaUniversalGoalAgent.hasSufficientDomainData(
                domain = UniversalDomainCategory.SYSTEM_AND_SETTINGS,
                texts = settingsMainMenuTexts,
                goalKeywords = goalKeywords,
                step = 1
            )
        )

        // Once LIA navigates into the Storage screen and reads concrete storage metrics ("64 Go utilisés sur 128 Go"), it returns true!
        val storageSubScreenTexts = settingsMainMenuTexts + setOf(
            "Stockage",
            "64 Go utilisés sur 128 Go",
            "Stockage utilisé : 50%"
        )
        val storagePairs = listOf("Stockage" to "64 Go utilisés sur 128 Go")
        assertTrue(
            LiaUniversalGoalAgent.isGoalInformationFound(
                domain = UniversalDomainCategory.SYSTEM_AND_SETTINGS,
                goalKeywords = goalKeywords,
                collectedTexts = storageSubScreenTexts,
                collectedPairs = storagePairs,
                step = 2
            )
        )
    }

    @Test
    fun `scoreClickableCandidate prioritizes goal keywords over domain tabs and exploratory menus and blocks visited or critical items`() {
        val keywords = LiaUniversalGoalAgent.extractGoalKeywords(
            "va dans les paramètres regarde ma batterie",
            UniversalDomainCategory.SYSTEM_AND_SETTINGS
        )
        val scoreGoalMatch = LiaUniversalGoalAgent.scoreClickableCandidate(
            candidateLabel = "Batterie — 85% disponible",
            goalKeywords = keywords,
            domain = UniversalDomainCategory.SYSTEM_AND_SETTINGS
        )
        val scoreDomainTab = LiaUniversalGoalAgent.scoreClickableCandidate(
            candidateLabel = "Affichage",
            goalKeywords = keywords,
            domain = UniversalDomainCategory.SYSTEM_AND_SETTINGS
        )
        val scoreExploratory = LiaUniversalGoalAgent.scoreClickableCandidate(
            candidateLabel = "Plus de détails",
            goalKeywords = keywords,
            domain = null
        )
        val scoreVisited = LiaUniversalGoalAgent.scoreClickableCandidate(
            candidateLabel = "Batterie",
            goalKeywords = keywords,
            domain = UniversalDomainCategory.SYSTEM_AND_SETTINGS,
            visitedLabels = setOf("batterie")
        )
        val scoreCritical = LiaUniversalGoalAgent.scoreClickableCandidate(
            candidateLabel = "Effacer toutes les données (Factory reset)",
            goalKeywords = keywords,
            domain = UniversalDomainCategory.SYSTEM_AND_SETTINGS
        )

        assertTrue("Expected goal match >= 100, got $scoreGoalMatch", scoreGoalMatch >= 100)
        assertTrue("Expected domain tab >= 60, got $scoreDomainTab", scoreDomainTab in 60..99)
        assertTrue("Expected exploratory >= 25, got $scoreExploratory", scoreExploratory in 25..59)
        assertEquals(0, scoreVisited)
        assertEquals(0, scoreCritical)
    }

    @Test
    fun `executeExplorationLoop tâtonne across scroll sub-menu probing backtracking and search bar until goal is found`() = runBlocking {
        // Simulate a multi-screen exploration in an app where:
        // Screen 1 (hash 101): Home menu with 9 items, no "Batterie" visible yet, no search bar
        // Action 1: LIA scrolls down (SWIPE_UP) -> Screen 2 (hash 102)
        // Screen 2 (hash 102): Still no "Batterie", scroll stagnates on next scroll -> Screen 2 (hash 102)
        // Action 3: LIA probes exploratory sub-menu "Infos système" (score >= 25) -> Screen 3 (hash 103)
        // Screen 3 (hash 103): Dead end ("Version du noyau 6.1", no clickables)
        // Action 4: LIA backtracks via GLOBAL_ACTION_BACK -> Screen 4 (hash 104, has search bar)
        // Action 5: LIA uses activateAndTypeSearch("batterie") -> Screen 5 (hash 105) with "Batterie : 84% (360 km)"
        var currentScreenIndex = 0
        val screens = listOf(
            LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Accueil", "Profil", "Contrat", "Assistance", "Concessionnaire", "Messages", "Boutique", "Historique", "Mentions"),
                clickableButtonsAndTabs = listOf("Profil", "Infos système"),
                progressAndGaugeValues = emptyList(),
                keyValuePairs = emptyList(),
                hasEditableSearchField = false,
                hasSearchButtonOrBar = false,
                isScrollableContainerPresent = true,
                screenFingerprintHash = 101
            ),
            LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Assistance", "Concessionnaire", "Messages", "Boutique", "Historique", "Mentions", "Infos système", "Garantie"),
                clickableButtonsAndTabs = listOf("Infos système"),
                progressAndGaugeValues = emptyList(),
                keyValuePairs = emptyList(),
                hasEditableSearchField = false,
                hasSearchButtonOrBar = false,
                isScrollableContainerPresent = true,
                screenFingerprintHash = 102
            ),
            LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Assistance", "Concessionnaire", "Messages", "Boutique", "Historique", "Mentions", "Infos système", "Garantie"),
                clickableButtonsAndTabs = listOf("Infos système"),
                progressAndGaugeValues = emptyList(),
                keyValuePairs = emptyList(),
                hasEditableSearchField = false,
                hasSearchButtonOrBar = false,
                isScrollableContainerPresent = true,
                screenFingerprintHash = 102 // Same hash -> scroll stagnation detected!
            ),
            LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Infos système", "Version application 4.2.0", "Serveur Europe"),
                clickableButtonsAndTabs = emptyList(), // Dead end!
                progressAndGaugeValues = emptyList(),
                keyValuePairs = emptyList(),
                hasEditableSearchField = false,
                hasSearchButtonOrBar = false,
                isScrollableContainerPresent = false,
                screenFingerprintHash = 103
            ),
            LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Accueil", "Rechercher"),
                clickableButtonsAndTabs = listOf("Rechercher"),
                progressAndGaugeValues = emptyList(),
                keyValuePairs = emptyList(),
                hasEditableSearchField = false,
                hasSearchButtonOrBar = true,
                isScrollableContainerPresent = false,
                screenFingerprintHash = 104
            ),
            LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Résultat : Batterie", "Charge actuelle : 84%", "Autonomie estimée : 365 km"),
                clickableButtonsAndTabs = emptyList(),
                progressAndGaugeValues = listOf("Batterie : 84%"),
                keyValuePairs = listOf("Charge actuelle" to "84%", "Autonomie estimée" to "365 km"),
                hasEditableSearchField = true,
                hasSearchButtonOrBar = true,
                isScrollableContainerPresent = false,
                screenFingerprintHash = 105
            )
        )

        val driver = object : UiExplorationDriver {
            override fun readSnapshot(): LiaAccessibilityService.StructuredScreenSnapshot {
                return screens[currentScreenIndex.coerceAtMost(screens.lastIndex)]
            }

            override fun clickStrict(targetText: String): Boolean {
                if (targetText.contains("Infos", ignoreCase = true)) {
                    currentScreenIndex = 3
                    return true
                }
                return false
            }

            override fun activateAndTypeSearch(query: String): Boolean {
                // Only allow search bar when screen hasSearchButtonOrBar or hasEditableSearchField is true (Screen index 4)
                val snap = screens[currentScreenIndex.coerceAtMost(screens.lastIndex)]
                if (!snap.hasSearchButtonOrBar && !snap.hasEditableSearchField) return false
                currentScreenIndex = 5
                return true
            }

            override fun scrollDown(): Boolean {
                val snap = screens[currentScreenIndex.coerceAtMost(screens.lastIndex)]
                if (!snap.isScrollableContainerPresent) return false
                if (currentScreenIndex < 2) {
                    currentScreenIndex++
                }
                return true
            }

            override fun navigateBack(): Boolean {
                if (currentScreenIndex == 3) {
                    currentScreenIndex = 4
                    return true
                }
                return false
            }

            override suspend fun waitMs(durationMs: Long) {
                // No-op in unit test
            }
        }

        val result = LiaUniversalGoalAgent.executeExplorationLoop(
            driver = driver,
            domain = UniversalDomainCategory.VEHICLE_AND_EV,
            userGoalPrompt = "regarde la batterie et l'autonomie",
            maxSteps = 10
        )

        assertTrue("Expected goalFound to be true after multi-step exploration", result.goalFound)
        assertTrue("Expected at least 1 scroll, got ${result.scrollCount}", result.scrollCount >= 1)
        assertTrue("Expected at least 1 backtrack from dead-end sub-menu, got ${result.backtrackCount}", result.backtrackCount >= 1)
        assertTrue("Expected searchBarUsed to be true", result.searchBarUsed)
        assertTrue(result.navigatedTabs.any { it.startsWith("Recherche :") })
        assertTrue(result.collectedTexts.any { it.contains("84%") })
        assertTrue(result.collectedTexts.any { it.contains("365 km") })
    }

    @Test
    fun `validateActionTargetSafety permits agent_goal and ui_macro pipe URIs while blocking shell injection`() {
        val goalUri = "agent_goal:vehicle_ev|com.toyota.oneapp.eu|Toyota|va%20dans%20Toyota%20regarde%20ma%20charge"
        assertEquals(
            ActionTargetSafetyVerdict.SAFE_APP_LAUNCH,
            SoniaSecurityKernel.validateActionTargetSafety(goalUri)
        )

        val settingsGoalUri = "agent_goal:system_settings|com.android.settings|Param%C3%A8tres|va%20dans%20les%20param%C3%A8tres%20regarde%20ma%20batterie"
        assertEquals(
            ActionTargetSafetyVerdict.SAFE_APP_LAUNCH,
            SoniaSecurityKernel.validateActionTargetSafety(settingsGoalUri)
        )

        val macroUri = "ui_macro:com.android.settings|Batterie|"
        assertEquals(
            ActionTargetSafetyVerdict.SAFE_APP_LAUNCH,
            SoniaSecurityKernel.validateActionTargetSafety(macroUri)
        )

        // Shell metacharacter injection on non-safePrefixes or with ; / && / ` must still be blocked!
        assertEquals(
            ActionTargetSafetyVerdict.BLOCKED_UNSAFE_TARGET,
            SoniaSecurityKernel.validateActionTargetSafety("com.whatsapp;rm -rf /")
        )
        assertEquals(
            ActionTargetSafetyVerdict.BLOCKED_UNSAFE_TARGET,
            SoniaSecurityKernel.validateActionTargetSafety("com.whatsapp|cat /etc/passwd")
        )
        assertEquals(
            ActionTargetSafetyVerdict.BLOCKED_UNSAFE_TARGET,
            SoniaSecurityKernel.validateActionTargetSafety("agent_goal:vehicle_ev;rm -rf /")
        )
        // Pipe delimiter on non-macro safePrefixes (e.g. settings:foo|bar, screen_click:foo|bar) must be blocked!
        assertEquals(
            ActionTargetSafetyVerdict.BLOCKED_UNSAFE_TARGET,
            SoniaSecurityKernel.validateActionTargetSafety("settings:foo|bar")
        )
        assertEquals(
            ActionTargetSafetyVerdict.BLOCKED_UNSAFE_TARGET,
            SoniaSecurityKernel.validateActionTargetSafety("screen_click:foo|bar")
        )
    }

    @Test
    fun `executeExplorationLoop allows clicking generic label Details on distinct screen hashes while preventing same-screen loops`() = runBlocking {
        var screenIdx = 0
        val screens = listOf(
            LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Accueil véhicule", "Détails"),
                clickableButtonsAndTabs = listOf("Détails"),
                progressAndGaugeValues = emptyList(),
                keyValuePairs = emptyList(),
                screenFingerprintHash = 201
            ),
            LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Sous-menu État", "Détails"),
                clickableButtonsAndTabs = listOf("Détails"),
                progressAndGaugeValues = emptyList(),
                keyValuePairs = emptyList(),
                screenFingerprintHash = 202
            ),
            LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Charge actuelle : 78%", "Autonomie : 310 km"),
                clickableButtonsAndTabs = emptyList(),
                progressAndGaugeValues = listOf("78%"),
                keyValuePairs = listOf("Charge actuelle" to "78%", "Autonomie" to "310 km"),
                screenFingerprintHash = 203
            )
        )
        val driver = object : UiExplorationDriver {
            override fun readSnapshot() = screens[screenIdx.coerceAtMost(screens.lastIndex)]
            override fun clickStrict(targetText: String): Boolean {
                screenIdx++
                return true
            }
            override fun activateAndTypeSearch(query: String) = false
            override fun scrollDown() = false
            override fun navigateBack() = false
            override suspend fun waitMs(durationMs: Long) {}
        }

        val res = LiaUniversalGoalAgent.executeExplorationLoop(
            driver = driver,
            domain = UniversalDomainCategory.VEHICLE_AND_EV,
            userGoalPrompt = "regarde ma charge",
            maxSteps = 6
        )
        assertTrue("Expected goalFound after clicking 'Détails' across two distinct screen hashes", res.goalFound)
        assertEquals(2, res.navigatedTabs.count { it == "Détails" })

        // Verify same-screen loop prevention: if clicking 'Détails' stays on the SAME screen hash (301),
        // it MUST NOT click 'Détails' repeatedly on hash 301!
        var sameScreenClickCount = 0
        val stuckScreen = LiaAccessibilityService.StructuredScreenSnapshot(
            activePackageName = "com.example.carapp",
            allVisibleTexts = listOf("Accueil véhicule", "Détails"),
            clickableButtonsAndTabs = listOf("Détails"),
            progressAndGaugeValues = emptyList(),
            keyValuePairs = emptyList(),
            screenFingerprintHash = 301
        )
        val stuckDriver = object : UiExplorationDriver {
            override fun readSnapshot() = stuckScreen
            override fun clickStrict(targetText: String): Boolean {
                sameScreenClickCount++
                return true
            }
            override fun activateAndTypeSearch(query: String) = false
            override fun scrollDown() = false
            override fun navigateBack() = false
            override suspend fun waitMs(durationMs: Long) {}
        }
        val stuckRes = LiaUniversalGoalAgent.executeExplorationLoop(
            driver = stuckDriver,
            domain = UniversalDomainCategory.VEHICLE_AND_EV,
            userGoalPrompt = "regarde ma charge",
            maxSteps = 6
        )
        assertFalse(stuckRes.goalFound)
        assertEquals(1, sameScreenClickCount)
    }

    @Test
    fun `IntentHandler recognizes ouvre les parametres without trailing space and defers compound inspection to GoalAgent`() {
        val simpleSettings = IntentHandler.classifyAndExtractKnownIntent("ouvre les paramètres", null)
        assertNotNull("Expected 'ouvre les paramètres' to classify as OPEN_SYSTEM_SETTINGS", simpleSettings)
        assertEquals(LiaClassifiedIntentType.OPEN_SYSTEM_SETTINGS, simpleSettings?.intentType)
        assertEquals("com.android.settings", simpleSettings?.targetPackage)

        val simpleSettingsNoAccent = IntentHandler.classifyAndExtractKnownIntent("ouvre les parametres", null)
        assertNotNull(simpleSettingsNoAccent)
        assertEquals(LiaClassifiedIntentType.OPEN_SYSTEM_SETTINGS, simpleSettingsNoAccent?.intentType)

        // Compound inspection inside Settings MUST return null from IntentHandler so LiaUniversalGoalAgent handles it!
        val compoundInspection1 = IntentHandler.classifyAndExtractKnownIntent(
            "ouvre les paramètres et cherche la batterie",
            null
        )
        assertNull(compoundInspection1)

        val compoundInspection2 = IntentHandler.classifyAndExtractKnownIntent(
            "va dans les paramètres regarde ma batterie",
            null
        )
        assertNull(compoundInspection2)
    }

    @Test
    fun `LiaAccessibilityService normalizeUiTextForMatch and scoreNodeTextMatch handle French accents and clickable bonuses`() {
        assertEquals(
            "parametres systeme et batterie",
            LiaAccessibilityService.normalizeUiTextForMatch("  Paramètres Système et Batterie ")
        )

        val exactClickableScore = LiaAccessibilityService.scoreNodeTextMatch(
            query = "Paramètres",
            nodeText = "Parametres",
            hasClickableSelfOrAncestor = true,
            hasVisibleBounds = true
        )
        val partialNonClickableScore = LiaAccessibilityService.scoreNodeTextMatch(
            query = "Paramètres",
            nodeText = "Mes parametres avancés",
            hasClickableSelfOrAncestor = false,
            hasVisibleBounds = true
        )
        assertTrue(exactClickableScore > partialNonClickableScore)
        assertEquals(150, exactClickableScore)
    }

    @Test
    fun `trailing sentence period and curly apostrophe prompts route accurately without generic noun hijacking`() {
        assertEquals("parametres", LiaUniversalAppsAndGoogleHub.normalizeAppQuery("les paramètres."))
        assertEquals("maps.me", LiaUniversalAppsAndGoogleHub.normalizeAppQuery("Maps.me."))
        assertTrue(LiaUniversalAppsAndGoogleHub.isSettingsAliasQuery("paramètres."))

        assertNull(
            LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
                "regarde combien de charge j'ai sur ma voiture."
            )
        )
        assertNull(
            LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
                "combien de batterie j'ai sur mon téléphone."
            )
        )
        assertNull(
            LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
                "vérifie combien j'ai sur mon compte."
            )
        )
        assertNull(
            LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
                "regarde la température dans la maison."
            )
        )

        val settingsDot = LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
            "cherche mes notifications dans les paramètres."
        )
        assertNotNull(settingsDot)
        assertEquals("Paramètres", settingsDot?.first)
        assertEquals("mes notifications", settingsDot?.second)

        val mapsMeDot = LiaUniversalGoalAgent.extractExplicitAppAndSubgoalFromPrompt(
            "cherche Paris dans Maps.me."
        )
        assertNotNull(mapsMeDot)
        assertEquals("Maps.me", mapsMeDot?.first)

        val carDotOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "regarde combien de charge j'ai sur ma voiture."
        )
        assertNotNull(carDotOutcome)
        assertTrue(
            carDotOutcome?.appLaunchAction?.packageName.orEmpty().startsWith("agent_goal:vehicle_ev|")
        )

        val phoneDotOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "combien de batterie j'ai sur mon téléphone."
        )
        assertNotNull(phoneDotOutcome)
        assertTrue(phoneDotOutcome?.isPhoneAction == true)
        assertNull(phoneDotOutcome?.appLaunchAction)

        val settingsDotOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "cherche mes notifications dans les paramètres."
        )
        assertNotNull(settingsDotOutcome)
        assertTrue(
            settingsDotOutcome?.appLaunchAction?.packageName.orEmpty()
                .startsWith("agent_goal:system_settings|com.android.settings|")
        )

        val amazonDotOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "où en est mon colis sur Amazon."
        )
        assertNotNull(amazonDotOutcome)
        val amazonDecoded = Uri.decode(amazonDotOutcome?.appLaunchAction?.packageName.orEmpty())
        assertTrue(amazonDecoded.startsWith("agent_goal:delivery_rides|"))
        assertFalse(amazonDecoded.contains("Amazon."))
        assertTrue(amazonDecoded.contains("|Amazon|"))

        val curlyCarOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "combien j’ai de charge ?"
        )
        assertNotNull(curlyCarOutcome)
        assertTrue(
            curlyCarOutcome?.appLaunchAction?.packageName.orEmpty().startsWith("agent_goal:vehicle_ev|")
        )

        val curlyPhoneOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "combien de batterie j’ai ?"
        )
        assertNotNull(curlyPhoneOutcome)
        assertTrue(curlyPhoneOutcome?.isPhoneAction == true)
        assertNull(curlyPhoneOutcome?.appLaunchAction)

        val curlyBankOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "vérifie combien j’ai sur mon compte ?"
        )
        assertNotNull(curlyBankOutcome)
        assertTrue(
            curlyBankOutcome?.appLaunchAction?.packageName.orEmpty().startsWith("agent_goal:finance_bank|")
        )

        val curlyLearnCarOutcome = LiaUniversalGoalAgent.checkUniversalGoalOrInspectionIntent(
            "ma voiture utilise l’app Toyota"
        )
        assertNotNull(curlyLearnCarOutcome)
        assertEquals(
            "Toyota",
            LiaMemoryAndHistoryStore.getUserFactValue(UniversalDomainCategory.VEHICLE_AND_EV.factKeyForPreferredApp)
        )

        val intentSettingsDot = IntentHandler.classifyAndExtractKnownIntent("ouvre les paramètres.", null)
        assertNotNull(intentSettingsDot)
        assertEquals(LiaClassifiedIntentType.OPEN_SYSTEM_SETTINGS, intentSettingsDot?.intentType)
    }

    @Test
    fun `scoreClickableCandidate prevents short unit and goal keyword substring hijacking`() {
        val storageKeywords = LiaUniversalGoalAgent.extractGoalKeywords(
            "ouvre les paramètres et cherche le stockage",
            UniversalDomainCategory.SYSTEM_AND_SETTINGS
        )
        assertEquals(
            0,
            LiaUniversalGoalAgent.scoreClickableCandidate(
                candidateLabel = "Google",
                goalKeywords = storageKeywords,
                domain = UniversalDomainCategory.SYSTEM_AND_SETTINGS
            )
        )
        assertEquals(
            0,
            LiaUniversalGoalAgent.scoreClickableCandidate(
                candidateLabel = "Google — Services et préférences",
                goalKeywords = storageKeywords,
                domain = UniversalDomainCategory.SYSTEM_AND_SETTINGS
            )
        )
        assertTrue(
            LiaUniversalGoalAgent.scoreClickableCandidate(
                candidateLabel = "Stockage",
                goalKeywords = storageKeywords,
                domain = UniversalDomainCategory.SYSTEM_AND_SETTINGS
            ) >= 100
        )

        val bankKeywords = LiaUniversalGoalAgent.extractGoalKeywords(
            "regarde mon solde sur Revolut",
            UniversalDomainCategory.FINANCE_AND_BANK
        )
        assertEquals(
            0,
            LiaUniversalGoalAgent.scoreClickableCandidate(
                candidateLabel = "Profil utilisateur",
                goalKeywords = bankKeywords,
                domain = UniversalDomainCategory.FINANCE_AND_BANK
            )
        )
        assertEquals(
            0,
            LiaUniversalGoalAgent.scoreClickableCandidate(
                candidateLabel = "Simulateur",
                goalKeywords = bankKeywords,
                domain = UniversalDomainCategory.FINANCE_AND_BANK
            )
        )
        assertTrue(
            LiaUniversalGoalAgent.scoreClickableCandidate(
                candidateLabel = "Cartes",
                goalKeywords = bankKeywords,
                domain = UniversalDomainCategory.FINANCE_AND_BANK
            ) >= 60
        )

        val stepsKeywords = LiaUniversalGoalAgent.extractGoalKeywords(
            "combien de pas aujourd'hui",
            UniversalDomainCategory.HEALTH_AND_FITNESS
        )
        assertFalse(stepsKeywords.contains("aujourd"))
        assertFalse(stepsKeywords.contains("hui"))
        assertEquals(
            0,
            LiaUniversalGoalAgent.scoreClickableCandidate(
                candidateLabel = "Mot de passe et sécurité",
                goalKeywords = stepsKeywords,
                domain = UniversalDomainCategory.HEALTH_AND_FITNESS
            )
        )
        assertTrue(
            LiaUniversalGoalAgent.scoreClickableCandidate(
                candidateLabel = "Mes pas quotidiens",
                goalKeywords = stepsKeywords,
                domain = UniversalDomainCategory.HEALTH_AND_FITNESS
            ) >= 100
        )
    }

    @Test
    fun `executeExplorationLoop explores three exploratory submenus within maxSteps using stagnatedScreenHashes`() = runBlocking {
        var currentHash = 401
        val snapshotsByHash = mapOf(
            401 to LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Menu principal", "Contrat", "Assistance"),
                clickableButtonsAndTabs = emptyList(),
                progressAndGaugeValues = emptyList(),
                keyValuePairs = emptyList(),
                hasEditableSearchField = false,
                hasSearchButtonOrBar = false,
                isScrollableContainerPresent = true,
                screenFingerprintHash = 401
            ),
            402 to LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Bas de page", "Infos", "Détails", "Plus"),
                clickableButtonsAndTabs = listOf("Infos", "Détails", "Plus"),
                progressAndGaugeValues = emptyList(),
                keyValuePairs = emptyList(),
                hasEditableSearchField = false,
                hasSearchButtonOrBar = false,
                isScrollableContainerPresent = true,
                screenFingerprintHash = 402
            ),
            411 to LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Infos", "Version application 4.2"),
                clickableButtonsAndTabs = emptyList(),
                progressAndGaugeValues = emptyList(),
                keyValuePairs = emptyList(),
                hasEditableSearchField = false,
                hasSearchButtonOrBar = false,
                isScrollableContainerPresent = false,
                screenFingerprintHash = 411
            ),
            412 to LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Détails", "Numéro de série JTD12345"),
                clickableButtonsAndTabs = emptyList(),
                progressAndGaugeValues = emptyList(),
                keyValuePairs = emptyList(),
                hasEditableSearchField = false,
                hasSearchButtonOrBar = false,
                isScrollableContainerPresent = false,
                screenFingerprintHash = 412
            ),
            413 to LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Plus", "Charge actuelle : 82%", "Autonomie : 340 km"),
                clickableButtonsAndTabs = emptyList(),
                progressAndGaugeValues = listOf("82%"),
                keyValuePairs = listOf("Charge actuelle" to "82%", "Autonomie" to "340 km"),
                hasEditableSearchField = false,
                hasSearchButtonOrBar = false,
                isScrollableContainerPresent = false,
                screenFingerprintHash = 413
            )
        )

        val driver = object : UiExplorationDriver {
            override fun readSnapshot(): LiaAccessibilityService.StructuredScreenSnapshot {
                return snapshotsByHash.getValue(currentHash)
            }

            override fun clickStrict(targetText: String): Boolean {
                return when (targetText) {
                    "Infos" -> {
                        currentHash = 411
                        true
                    }
                    "Détails" -> {
                        currentHash = 412
                        true
                    }
                    "Plus" -> {
                        currentHash = 413
                        true
                    }
                    else -> false
                }
            }

            override fun activateAndTypeSearch(query: String): Boolean = false

            override fun scrollDown(): Boolean {
                // Simulate live physical gesture dispatch returning true even at list bottom / non-scrollable screens
                if (currentHash == 401) {
                    currentHash = 402
                }
                return true
            }

            override fun navigateBack(): Boolean {
                currentHash = 402
                return true
            }

            override suspend fun waitMs(durationMs: Long) {}
        }

        val result = LiaUniversalGoalAgent.executeExplorationLoop(
            driver = driver,
            domain = UniversalDomainCategory.VEHICLE_AND_EV,
            userGoalPrompt = "regarde ma charge",
            maxSteps = 10
        )

        assertTrue("Expected goalFound on 3rd exploratory sub-menu 'Plus'", result.goalFound)
        assertEquals(listOf("Infos", "Détails", "Plus"), result.navigatedTabs)
        assertEquals(2, result.backtrackCount)
        assertTrue(result.collectedTexts.any { it.contains("82%") })
        assertTrue(result.collectedTexts.any { it.contains("340 km") })
    }

    @Test
    fun `executeExplorationLoop ignores cross screen discount percent and distance km false positive`() = runBlocking {
        var idxOnlyPromo = 0
        val promoAndDealerScreens = listOf(
            LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Accueil", "Offre entretien printemps : -15%", "Véhicule"),
                clickableButtonsAndTabs = listOf("Véhicule"),
                progressAndGaugeValues = emptyList(),
                keyValuePairs = emptyList(),
                hasEditableSearchField = false,
                hasSearchButtonOrBar = false,
                isScrollableContainerPresent = false,
                screenFingerprintHash = 501
            ),
            LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Mon garage", "Concessionnaire Toyota Lyon à 12 km"),
                clickableButtonsAndTabs = emptyList(),
                progressAndGaugeValues = emptyList(),
                keyValuePairs = emptyList(),
                hasEditableSearchField = false,
                hasSearchButtonOrBar = false,
                isScrollableContainerPresent = false,
                screenFingerprintHash = 502
            )
        )
        val promoOnlyDriver = object : UiExplorationDriver {
            override fun readSnapshot() = promoAndDealerScreens[idxOnlyPromo.coerceAtMost(promoAndDealerScreens.lastIndex)]
            override fun clickStrict(targetText: String): Boolean {
                idxOnlyPromo = 1
                return true
            }
            override fun activateAndTypeSearch(query: String) = false
            override fun scrollDown() = false
            override fun navigateBack(): Boolean {
                idxOnlyPromo = 0
                return true
            }
            override suspend fun waitMs(durationMs: Long) {}
        }
        val promoRes = LiaUniversalGoalAgent.executeExplorationLoop(
            driver = promoOnlyDriver,
            domain = UniversalDomainCategory.VEHICLE_AND_EV,
            userGoalPrompt = "regarde ma charge",
            maxSteps = 6
        )
        assertFalse(
            "Expected cross-screen '-15%' + '12 km' NOT to trigger false-positive goalFound",
            promoRes.goalFound
        )

        var idxWithRealBattery = 0
        val fullScreens = listOf(
            promoAndDealerScreens[0],
            LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Mon garage", "Concessionnaire Toyota Lyon à 12 km", "Recharge"),
                clickableButtonsAndTabs = listOf("Recharge"),
                progressAndGaugeValues = emptyList(),
                keyValuePairs = emptyList(),
                hasEditableSearchField = false,
                hasSearchButtonOrBar = false,
                isScrollableContainerPresent = false,
                screenFingerprintHash = 502
            ),
            LiaAccessibilityService.StructuredScreenSnapshot(
                activePackageName = "com.example.carapp",
                allVisibleTexts = listOf("Recharge", "Charge actuelle : 78%", "Autonomie : 310 km"),
                clickableButtonsAndTabs = emptyList(),
                progressAndGaugeValues = listOf("78%"),
                keyValuePairs = listOf("Charge actuelle" to "78%", "Autonomie" to "310 km"),
                hasEditableSearchField = false,
                hasSearchButtonOrBar = false,
                isScrollableContainerPresent = false,
                screenFingerprintHash = 503
            )
        )
        val fullDriver = object : UiExplorationDriver {
            override fun readSnapshot() = fullScreens[idxWithRealBattery.coerceAtMost(fullScreens.lastIndex)]
            override fun clickStrict(targetText: String): Boolean {
                idxWithRealBattery++
                return true
            }
            override fun activateAndTypeSearch(query: String) = false
            override fun scrollDown() = false
            override fun navigateBack(): Boolean {
                if (idxWithRealBattery > 0) idxWithRealBattery--
                return true
            }
            override suspend fun waitMs(durationMs: Long) {}
        }
        val fullRes = LiaUniversalGoalAgent.executeExplorationLoop(
            driver = fullDriver,
            domain = UniversalDomainCategory.VEHICLE_AND_EV,
            userGoalPrompt = "regarde ma charge",
            maxSteps = 6
        )
        assertTrue(fullRes.goalFound)
        assertTrue(fullRes.navigatedTabs.contains("Recharge"))
        assertTrue(fullRes.collectedTexts.any { it.contains("78%") })
        assertTrue(fullRes.collectedTexts.any { it.contains("310 km") })
    }
}
