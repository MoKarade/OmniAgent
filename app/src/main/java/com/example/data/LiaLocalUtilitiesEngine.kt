package com.example.data

import com.example.model.currentTimeFormatted
import java.util.Locale

/**
 * Moteur d'utilitaires locaux hors-ligne de LIA :
 * - Calculatrice mathématique (avec gestion des parenthèses, priorités opératoires, pourcentages et partage d'addition).
 * - Convertisseur d'unités (distances, masses, températures) et de devises courantes.
 * - Traducteur local hors-ligne d'expressions courantes.
 * - Prise de notes vocales / rapides et exécution des routines multi-étapes (Matin, Départ, Soir).
 */
object LiaLocalUtilitiesEngine {

    fun checkVoiceNoteOrQuickNoteIntent(input: String): LiaBrainTurnOutcome? {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        if (lower == "prendre une note" ||
            lower == "prends une note" ||
            lower == "nouvelle note" ||
            lower == "note vocale" ||
            lower == "ajouter une note" ||
            lower == "créer une note" ||
            lower == "creer une note" ||
            lower == "enregistrer une note"
        ) {
            val key = "note_${System.currentTimeMillis()}"
            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForUserFact(key, "Note personnelle")
            )
            return LiaBrainTurnOutcome(
                replyText = "📝 **Prise de note prête** : Écrivez ou dictez au micro ce que vous souhaitez noter, et je l'enregistrerai immédiatement.",
                learnedBadge = "📝 En attente de votre note"
            )
        }

        val noteRegex = Regex(
            pattern = "^(?:note\\s+vocale|prends?\\s+en\\s+note|prendre\\s+une\\s+note|enregistre\\s+la\\s+note|m[ée]morise\\s+la\\s+note|note)\\s*[:\\-]?\\s+(.+)$",
            option = RegexOption.IGNORE_CASE
        )
        val match = noteRegex.find(trimmed) ?: return null
        val noteContent = match.groupValues[1].trim()
        if (noteContent.length < 2) return null

        val now = currentTimeFormatted()
        val shortTitle = noteContent.take(42).let { if (noteContent.length > 42) "$it…" else it }
        val key = "note_${System.currentTimeMillis()}"

        LiaMemoryAndHistoryStore.upsertSouvenir(
            type = "NOTE",
            subjectKey = key,
            title = "Note : $shortTitle",
            detailValue = "$noteContent (enregistrée à $now)",
            searchTags = "note,vocale,memo,rappel,${noteContent.lowercase().replace(Regex("[^a-zà-ÿ0-9 ]"), " ")}"
        )
        LiaMemoryAndHistoryStore.upsertLongTermMemory(
            category = "VOICE_NOTE",
            memoryKey = key,
            title = "📝 $shortTitle",
            contentValue = noteContent,
            targetActionOrPackage = "note_saved",
            speedupMs = 25L
        )

        return LiaBrainTurnOutcome(
            replyText = "📝 **Note enregistrée** :\n\n« *$noteContent* »\n\nVous pouvez me la redemander à tout moment.",
            learnedBadge = "📝 Note mémorisée"
        )
    }

    fun checkMacroRoutineIntent(input: String): LiaBrainTurnOutcome? {
        val lower = input.lowercase().trim()
        val snap = LiaMemoryAndHistoryStore.state.value

        return when {
            lower.contains("mode matin") || lower.contains("routine matin") -> {
                val events = snap.souvenirs.filter { it.type == "EVENEMENT" }
                val morningApp = snap.appUsageHabits.maxByOrNull { it.morningCount * 2 + it.launchCount }
                val appTarget = if (morningApp != null) {
                    LiaAppLaunchAction(morningApp.appLabel, morningApp.packageName, "home_launcher", 55L)
                } else {
                    LiaAppLaunchAction("Gmail", "com.google.android.gm", "home_launcher", 55L)
                }
                val eventsText = if (events.isEmpty()) {
                    "• Aucun événement particulier prévu pour aujourd'hui."
                } else {
                    events.take(3).joinToString("\n") { "• 📅 **${it.title}** : ${it.detailValue}" }
                }
                LiaMemoryAndHistoryStore.recordLearnedShortcut(
                    triggerPhrase = "☀️ Mode Matin",
                    actionTitle = "Routine Matin (Agenda + ${appTarget.appLabel})",
                    targetPackageOrScreen = appTarget.packageName,
                    stepsSaved = 4,
                    latencyMs = 55L
                )
                LiaBrainTurnOutcome(
                    replyText = "☀️ **Routine du matin prête** :\n\n" +
                        "**1. Votre agenda** :\n$eventsText\n\n" +
                        "**2. Votre application habituelle** :\nPrêt à ouvrir **${appTarget.appLabel}** ci-dessous.",
                    appLaunchAction = appTarget,
                    isPhoneAction = true,
                    learnedBadge = "☀️ Mode Matin"
                )
            }

            lower.contains("mode départ") || lower.contains("mode depart") || lower.contains("mode trajet") || lower.contains("routine départ") -> {
                val firstContact = snap.userFacts.firstOrNull { it.key.endsWith("_tel") }
                val targetName = firstContact?.label?.removePrefix("Numéro ")?.trim()?.ifBlank { "Maman" } ?: "Maman"
                val verification = LiaMemoryAndHistoryStore.verifyContactForSms(targetName)
                val body = "Je viens de partir, j'arrive dans environ 15 minutes !"
                val navApp = LiaAppLaunchAction("Google Maps", "com.google.android.apps.maps", "home_launcher", 55L)

                LiaMemoryAndHistoryStore.recordLearnedShortcut(
                    triggerPhrase = "🚗 Mode Départ",
                    actionTitle = "Routine Départ (SMS $targetName + Navigation)",
                    targetPackageOrScreen = "messages_inbox",
                    stepsSaved = 5,
                    latencyMs = 60L
                )
                LiaBrainTurnOutcome(
                    replyText = "🚗 **Mode Départ prêt** :\n\n" +
                        "• Message préparé pour **${verification.recipientName}** (${verification.phoneNumber.ifBlank { "numéro à vérifier" }}).\n" +
                        "• Accès direct à la navigation ci-dessous.",
                    smsDraft = LiaSmsDraft(
                        recipientName = verification.recipientName,
                        phoneNumber = verification.phoneNumber,
                        messageBody = body,
                        isSent = false,
                        isContactVerified = verification.isVerified,
                        contactVerificationStatus = verification.statusLabel,
                        fuzzyMatchScore = verification.fuzzyMatchScore
                    ),
                    appLaunchAction = navApp,
                    isPhoneAction = true,
                    targetVirtualScreenId = "messages_inbox",
                    learnedBadge = "🚗 Mode Départ"
                )
            }

            lower.contains("mode soir") || lower.contains("routine soir") -> {
                val eveningApp = snap.appUsageHabits.maxByOrNull { it.eveningCount * 2 + it.launchCount }
                val appTarget = if (eveningApp != null) {
                    LiaAppLaunchAction(eveningApp.appLabel, eveningApp.packageName, "home_launcher", 55L)
                } else {
                    LiaAppLaunchAction("YouTube", "com.google.android.youtube", "home_launcher", 55L)
                }
                val learnedTodayCount = snap.souvenirs.size
                LiaMemoryAndHistoryStore.recordLearnedShortcut(
                    triggerPhrase = "🌙 Mode Soir",
                    actionTitle = "Routine Soir (${appTarget.appLabel})",
                    targetPackageOrScreen = appTarget.packageName,
                    stepsSaved = 4,
                    latencyMs = 55L
                )
                LiaBrainTurnOutcome(
                    replyText = "🌙 **Mode Soir prêt** :\n\n" +
                        "• **$learnedTodayCount souvenir(s)** et **${snap.learnedShortcuts.size} raccourci(s)** mémorisés.\n" +
                        "• Votre application du soir (**${appTarget.appLabel}**) est prête ci-dessous.",
                    appLaunchAction = appTarget,
                    isPhoneAction = true,
                    learnedBadge = "🌙 Mode Soir"
                )
            }

            else -> null
        }
    }

    fun checkInstantUtilityIntent(input: String): LiaBrainTurnOutcome? {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        if (lower == "calculer ou convertir" || lower == "calculer" || lower == "convertir" || lower == "calculatrice") {
            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForGeneralClarification("ASK_CALCULATION")
            )
            return LiaBrainTurnOutcome(
                replyText = "🧮 **Que souhaitez-vous calculer ou convertir ?**\n*(Exemples : un pourcentage, une opération, une devise ou une distance)*",
                learnedBadge = "🧮 Calcul & Conversion",
                quickReplies = listOf(
                    "Calcule 20% de 145€",
                    "Partage 128€ en 4",
                    "Convertis 100$ en euros",
                    "Convertis 50 miles en km"
                )
            )
        }

        if (lower == "traduire" || lower == "traduire un texte" || lower == "traduction") {
            LiaMemoryAndHistoryStore.setPendingFollowUp(
                LiaPendingFollowUp.WaitingForGeneralClarification("ASK_TRANSLATION")
            )
            return LiaBrainTurnOutcome(
                replyText = "🌍 **Quel texte souhaitez-vous traduire et dans quelle langue ?**",
                learnedBadge = "🌍 Traduction instantanée",
                quickReplies = listOf(
                    "Traduis Bonjour, où est la gare ? en anglais",
                    "Traduis Merci beaucoup en japonais",
                    "Traduis L'addition s'il vous plaît en espagnol",
                    "Traduis À bientôt en italien"
                )
            )
        }

        val percentRegex = Regex(
            "(?:calcule\\s+|combien\\s+f(?:ait|ont)\\s+|remise\\s+de\\s+|réduction\\s+de\\s+)?(\\d+(?:[.,]\\d+)?)\\s*%\\s*(?:de|sur)\\s*(\\d+(?:[.,]\\d+)?)\\s*(€|euros?|\\$|dollars?)?",
            RegexOption.IGNORE_CASE
        )
        percentRegex.find(trimmed)?.let { m ->
            val pct = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return@let
            val total = m.groupValues[2].replace(",", ".").toDoubleOrNull() ?: return@let
            val unit = m.groupValues.getOrNull(3).orEmpty().let {
                if (it.startsWith("euro")) "€" else if (it.startsWith("dollar")) "$" else it
            }
            val value = (pct / 100.0) * total
            val discounted = total - value
            val added = total + value
            return LiaBrainTurnOutcome(
                replyText = "🧮 **Résultat du calcul (${formatNum(pct)}% de ${formatNum(total)}$unit)** :\n\n" +
                    "• **Montant (${formatNum(pct)}%)** : **${formatNum(value)}$unit**\n" +
                    "• **Après réduction (-${formatNum(pct)}%)** : **${formatNum(discounted)}$unit**\n" +
                    "• **Après ajout (+${formatNum(pct)}%)** : **${formatNum(added)}$unit**",
                learnedBadge = "🧮 ${formatNum(value)}$unit"
            )
        }

        val splitBillRegex = Regex(
            "(?:partage|divise|répartis|repartis)\\s+(\\d+(?:[.,]\\d+)?)\\s*(€|euros?|\\$)?\\s*(?:en|par|entre)\\s*(\\d+)\\s*(?:personnes?|parts?)?",
            RegexOption.IGNORE_CASE
        )
        splitBillRegex.find(trimmed)?.let { m ->
            val amount = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return@let
            val unit = m.groupValues.getOrNull(2)?.ifBlank { "€" } ?: "€"
            val people = m.groupValues[3].toIntOrNull()?.coerceAtLeast(1) ?: return@let
            val perPerson = amount / people
            return LiaBrainTurnOutcome(
                replyText = "🧮 **Partage de ${formatNum(amount)}$unit entre $people personnes** :\n\n" +
                    "• **Par personne** : **${formatNum(perPerson)}$unit**",
                learnedBadge = "🧮 ${formatNum(perPerson)}$unit / pers."
            )
        }

        val convRegex = Regex(
            "(?:convertis(?:sez)?|combien\\s+font|transforme)?\\s*(-?\\d+(?:[.,]\\d+)?)\\s*(€|euros?|\\$|dollars?|usd|£|livres?|gbp|yen|jpy|chf|km|miles?|mi|kg|kilos?|lbs?|livres\\s+poids|m|pieds?|ft|cm|pouces?|inch(?:es)?|°?c|celsius|°?f|fahrenheit)\\s+(?:en|to|vers|in)\\s+(€|euros?|\\$|dollars?|usd|£|livres?|gbp|yen|jpy|chf|km|miles?|mi|kg|kilos?|lbs?|m|pieds?|ft|cm|pouces?|inch(?:es)?|°?c|celsius|°?f|fahrenheit)",
            RegexOption.IGNORE_CASE
        )
        convRegex.find(trimmed)?.let { m ->
            val v = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return@let
            val fromRaw = m.groupValues[2].lowercase()
            val toRaw = m.groupValues[3].lowercase()
            val resultPair = computeUnitOrCurrencyConversion(v, fromRaw, toRaw)
            if (resultPair != null) {
                val (formattedResult, explanation) = resultPair
                return LiaBrainTurnOutcome(
                    replyText = "🔄 **Conversion instantanée** :\n\n• **$formattedResult**\n*($explanation)*",
                    learnedBadge = "🔄 $formattedResult"
                )
            }
        }

        if (lower.startsWith("calcule ") || lower.startsWith("combien font ") || lower.startsWith("combien fait ")) {
            val exprRaw = trimmed
                .replace(Regex("^(?:calcule|combien\\s+font|combien\\s+fait)\\s+", RegexOption.IGNORE_CASE), "")
                .replace("?", "")
                .replace("x", "*")
                .replace("×", "*")
                .replace("÷", "/")
                .replace(",", ".")
                .trim()
            val evalResult = evaluateSimpleMathExpression(exprRaw)
            if (evalResult != null) {
                return LiaBrainTurnOutcome(
                    replyText = "🧮 **Résultat du calcul** :\n\n`$exprRaw` = **${formatNum(evalResult)}**",
                    learnedBadge = "🧮 = ${formatNum(evalResult)}"
                )
            }
        }

        val transRegex = Regex(
            "^(?:traduis|traduire|comment\\s+dit-on|comment\\s+on\\s+dit)\\s+(.+?)\\s+(?:en|vers\\s+le|vers\\s+l'|in)\\s+([a-zA-ZÀ-ÿ]+)\\??$",
            RegexOption.IGNORE_CASE
        )
        transRegex.find(trimmed)?.let { m ->
            val phrase = m.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("«", "»").trim()
            val targetLang = m.groupValues[2].trim().lowercase()
            val localTrans = translateCommonPhraseLocally(phrase, targetLang)
            if (localTrans != null) {
                return LiaBrainTurnOutcome(
                    replyText = "🌍 **Traduction en ${targetLang.replaceFirstChar { it.uppercase() }}** :\n\n" +
                        "• Français : *« $phrase »*\n" +
                        "• **${targetLang.replaceFirstChar { it.uppercase() }}** : **$localTrans**",
                    learnedBadge = "🌍 Traduit en $targetLang"
                )
            }
        }

        return null
    }

    fun formatNum(d: Double): String {
        return if (d % 1.0 == 0.0) {
            d.toLong().toString()
        } else {
            String.format(Locale.FRENCH, "%.2f", d)
        }
    }

    fun computeUnitOrCurrencyConversion(value: Double, from: String, to: String): Pair<String, String>? {
        fun norm(u: String): String = when {
            u in setOf("€", "euro", "euros") -> "EUR"
            u in setOf("$", "dollar", "dollars", "usd") -> "USD"
            u in setOf("£", "livre", "livres", "gbp") -> "GBP"
            u in setOf("yen", "jpy") -> "JPY"
            u == "chf" -> "CHF"
            u == "km" -> "KM"
            u.startsWith("mi") -> "MI"
            u.startsWith("kg") || u.startsWith("kilo") -> "KG"
            u.startsWith("lb") -> "LB"
            u == "cm" -> "CM"
            u.startsWith("pouce") || u.startsWith("inch") -> "IN"
            u == "m" -> "M"
            u.startsWith("pied") || u == "ft" -> "FT"
            u.endsWith("c") || u == "celsius" -> "C"
            u.endsWith("f") || u == "fahrenheit" -> "F"
            else -> u.uppercase()
        }
        val f = norm(from)
        val t = norm(to)
        return when (f to t) {
            "EUR" to "USD" -> "${formatNum(value)} € = ${formatNum(value * 1.08)} $" to "Taux indicatif : 1 € ≈ 1,08 $"
            "USD" to "EUR" -> "${formatNum(value)} $ = ${formatNum(value * 0.925)} €" to "Taux indicatif : 1 $ ≈ 0,925 €"
            "EUR" to "GBP" -> "${formatNum(value)} € = ${formatNum(value * 0.85)} £" to "Taux indicatif : 1 € ≈ 0,85 £"
            "GBP" to "EUR" -> "${formatNum(value)} £ = ${formatNum(value * 1.17)} €" to "Taux indicatif : 1 £ ≈ 1,17 €"
            "EUR" to "JPY" -> "${formatNum(value)} € = ${formatNum(value * 163.0)} ¥" to "Taux indicatif : 1 € ≈ 163 ¥"
            "EUR" to "CHF" -> "${formatNum(value)} € = ${formatNum(value * 0.96)} CHF" to "Taux indicatif : 1 € ≈ 0,96 CHF"
            "KM" to "MI" -> "${formatNum(value)} km = ${formatNum(value * 0.621371)} miles" to "Distance : 1 km = 0,621 mi"
            "MI" to "KM" -> "${formatNum(value)} miles = ${formatNum(value * 1.60934)} km" to "Distance : 1 mile = 1,609 km"
            "KG" to "LB" -> "${formatNum(value)} kg = ${formatNum(value * 2.20462)} lbs" to "Masse : 1 kg = 2,205 lbs"
            "LB" to "KG" -> "${formatNum(value)} lbs = ${formatNum(value * 0.453592)} kg" to "Masse : 1 lb = 0,454 kg"
            "CM" to "IN" -> "${formatNum(value)} cm = ${formatNum(value * 0.393701)} pouces" to "Longueur : 1 pouce = 2,54 cm"
            "IN" to "CM" -> "${formatNum(value)} pouces = ${formatNum(value * 2.54)} cm" to "Longueur : 1 pouce = 2,54 cm"
            "M" to "FT" -> "${formatNum(value)} m = ${formatNum(value * 3.28084)} pieds" to "Longueur : 1 m = 3,28 pieds"
            "FT" to "M" -> "${formatNum(value)} pieds = ${formatNum(value * 0.3048)} m" to "Longueur : 1 pied = 0,305 m"
            "C" to "F" -> "${formatNum(value)} °C = ${formatNum(value * 9.0 / 5.0 + 32.0)} °F" to "Température : (°C × 9/5) + 32"
            "F" to "C" -> "${formatNum(value)} °F = ${formatNum((value - 32.0) * 5.0 / 9.0)} °C" to "Température : (°F - 32) × 5/9"
            else -> null
        }
    }

    /**
     * Évaluateur arithmétique supportant `+`, `-`, `*`, `/` et les parenthèses imbriquées `(...)`.
     */
    fun evaluateSimpleMathExpression(expr: String): Double? {
        val clean = expr.replace(" ", "").replace(",", ".")
        if (clean.isBlank() || !clean.matches(Regex("^[0-9.+\\-*/()]+$"))) return null
        return runCatching {
            var working = clean
            val parenRegex = Regex("\\(([^()]+)\\)")
            var guard = 0
            while (parenRegex.containsMatchIn(working) && guard < 16) {
                guard++
                val match = parenRegex.find(working) ?: break
                val subVal = evalFlatExpression(match.groupValues[1]) ?: return null
                working = working.replaceRange(match.range, subVal.toString())
            }
            if (working.contains("(") || working.contains(")")) return null
            evalFlatExpression(working)
        }.getOrNull()
    }

    private fun evalFlatExpression(flat: String): Double? {
        val tokens = Regex("((?:^|(?<=[+\\-*/]))-?\\d+(?:\\.\\d+)?|[+\\-*/])")
            .findAll(flat)
            .map { it.value }
            .toList()
        if (tokens.isEmpty()) return null

        val addSubList = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val tok = tokens[i]
            if ((tok == "*" || tok == "/") && addSubList.isNotEmpty() && i + 1 < tokens.size) {
                val left = addSubList.removeAt(addSubList.lastIndex).toDoubleOrNull() ?: return null
                val right = tokens[i + 1].toDoubleOrNull() ?: return null
                val res = if (tok == "*") left * right else if (right != 0.0) left / right else return null
                addSubList.add(res.toString())
                i += 2
            } else {
                addSubList.add(tok)
                i++
            }
        }

        var acc = addSubList.firstOrNull()?.toDoubleOrNull() ?: return null
        var j = 1
        while (j + 1 < addSubList.size) {
            val op = addSubList[j]
            val nextVal = addSubList[j + 1].toDoubleOrNull() ?: return null
            acc = when (op) {
                "+" -> acc + nextVal
                "-" -> acc - nextVal
                else -> return null
            }
            j += 2
        }
        return acc
    }

    fun translateCommonPhraseLocally(phrase: String, targetLang: String): String? {
        val p = phrase.lowercase().trim()
        val lang = targetLang.lowercase().trim()
        val dict = mapOf(
            "bonjour" to mapOf(
                "anglais" to "Hello / Good morning",
                "espagnol" to "¡Hola! / Buenos días",
                "italien" to "Buongiorno / Ciao",
                "allemand" to "Guten Tag / Hallo",
                "japonais" to "こんにちは (Konnichiwa)",
                "arabe" to "مرحبا (Marhaban)",
                "portugais" to "Olá / Bom dia"
            ),
            "merci" to mapOf(
                "anglais" to "Thank you",
                "espagnol" to "Muchas gracias",
                "italien" to "Grazie mille",
                "allemand" to "Vielen Dank",
                "japonais" to "ありがとうございます (Arigatō gozaimasu)",
                "arabe" to "شكراً (Shukran)",
                "portugais" to "Obrigado / Obrigada"
            ),
            "merci beaucoup" to mapOf(
                "anglais" to "Thank you very much!",
                "espagnol" to "¡Muchísimas gracias!",
                "italien" to "Grazie mille!",
                "allemand" to "Vielen herzlichen Dank!",
                "japonais" to "どうもありがとうございます (Dōmo arigatō gozaimasu)",
                "arabe" to "شكراً جزيلاً (Shukran jazilan)",
                "portugais" to "Muito obrigado!"
            ),
            "bonjour, où est la gare ?" to mapOf(
                "anglais" to "Hello, where is the train station?",
                "espagnol" to "Hola, ¿dónde está la estación de tren?",
                "italien" to "Buongiorno, dov'è la stazione?",
                "allemand" to "Hallo, wo ist der Bahnhof?",
                "japonais" to "すみません、駅はどこですか？ (Sumimasen, eki wa doko desu ka?)"
            ),
            "l'addition s'il vous plaît" to mapOf(
                "anglais" to "Could we have the bill, please?",
                "espagnol" to "La cuenta, por favor.",
                "italien" to "Il conto, per favore.",
                "allemand" to "Die Rechnung, bitte.",
                "japonais" to "お会計をお願いします (O-kaikei o onegaishimasu)"
            ),
            "à bientôt" to mapOf(
                "anglais" to "See you soon!",
                "espagnol" to "¡Hasta pronto!",
                "italien" to "A presto!",
                "allemand" to "Bis bald!",
                "japonais" to "またね (Mata ne)"
            )
        )
        return dict[p]?.get(lang)
    }
}
