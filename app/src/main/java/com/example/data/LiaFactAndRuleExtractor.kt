package com.example.data

/**
 * Extracteur dédié à l'apprentissage de faits personnels et de règles personnalisées ("Quand je dis X, fais Y",
 * "Retiens que...") extrait de `LiaAssistantBrain.kt` pour alléger le cerveau et renforcer la précision des regex.
 */
object LiaFactAndRuleExtractor {

    fun checkCustomLearningAndRuleIntent(
        input: String,
        executeRuleTargetCommand: (String) -> LiaBrainTurnOutcome?
    ): LiaBrainTurnOutcome? {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()
        val snap = LiaMemoryAndHistoryStore.state.value

        if (lower == "apprendre une règle" ||
            lower == "apprendre une regle" ||
            lower == "apprends une règle" ||
            lower == "apprends quelque chose" ||
            lower == "mode apprentissage"
        ) {
            return LiaBrainTurnOutcome(
                replyText = "🧠 **Apprentissage LIA actif (Mémoire Room + RL Q-Learning)** :\n\n" +
                    "• **M'enseigner un raccourci / une règle** : dites *« Quand je dis [mot-clé], fais [action] »* (ex: *« Quand je dis pause café, ouvre YouTube »*).\n" +
                    "• **M'enseigner un fait ou souvenir** : dites *« Retiens que mon pseudo Discord est Alex99 »* ou *« Apprends que mon plat préféré est les sushis »*.\n" +
                    "• **Apprentissage continu automatique** : chaque action renforce son score Q-Value (${snap.learnedShortcuts.size} raccourcis appris • ${snap.souvenirs.size} souvenirs RAG).",
                learnedBadge = "🧠 Capacité : ${snap.souvenirs.size} souvenirs • ${snap.learnedShortcuts.size} règles RL",
                quickReplies = listOf(
                    "Quand je dis pause café, ouvre YouTube",
                    "Retiens que mon jeu préféré est Elden Ring",
                    "Qu'as-tu appris ?"
                )
            )
        }

        val customRuleRegex = Regex(
            "^(?:apprends(?:\\s+que)?\\s+|retiens(?:\\s+que)?\\s+)?(?:quand\\s+je\\s+(?:dis|demande|tape)|si\\s+je\\s+dis)\\s+[«\"]?(.+?)[»\"]?\\s*(?:,|alors|->|=|\\s+(?=fais|ouvre|lance|envoie|appelle|mets|va|écris|ecris|clique|scrolle))\\s*(?:fais\\s+|exécute\\s+|execute\\s+)?[«\"]?(.+?)[»\"]?$",
            RegexOption.IGNORE_CASE
        )
        val associeRegex = Regex(
            "^(?:associe|relie|apprends\\s+la\\s+r[èe]gle)\\s+[«\"]?(.+?)[»\"]?\\s+(?:à|a|avec|->|=)\\s+[«\"]?(.+?)[»\"]?$",
            RegexOption.IGNORE_CASE
        )
        val ruleMatch = customRuleRegex.find(trimmed) ?: associeRegex.find(trimmed)
        if (ruleMatch != null) {
            val trigger = ruleMatch.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("«", "»").trim()
            val targetCommand = ruleMatch.groupValues[2].trim().removeSurrounding("\"").removeSurrounding("«", "»").trim()
            if (trigger.length >= 2 && targetCommand.length >= 2) {
                val ruleKey = "custom_rule_${trigger.lowercase().replace(Regex("[^a-z0-9à-ÿ]"), "_")}"
                LiaMemoryAndHistoryStore.upsertSouvenir(
                    type = "PREFERENCE",
                    subjectKey = ruleKey,
                    title = "Règle apprise : « $trigger »",
                    detailValue = "Exécuter : $targetCommand",
                    searchTags = "regle,raccourci,macro,${trigger.lowercase()},${targetCommand.lowercase()}",
                    importanceScore = 1.2f
                )
                LiaMemoryAndHistoryStore.upsertLongTermMemory(
                    category = "CUSTOM_RULE",
                    memoryKey = ruleKey,
                    title = trigger,
                    contentValue = targetCommand,
                    targetActionOrPackage = "rule:$targetCommand",
                    speedupMs = 20L
                )
                LiaMemoryAndHistoryStore.recordLearnedShortcut(
                    triggerPhrase = trigger,
                    actionTitle = "Règle : $targetCommand",
                    targetPackageOrScreen = "rule:$targetCommand",
                    stepsSaved = 5,
                    latencyMs = 25L,
                    rewardSignal = 1.35f
                )
                return LiaBrainTurnOutcome(
                    replyText = "🧠 **Nouvelle règle apprise et mémorisée dans Room & RL** :\n\n" +
                        "• **Déclencheur** : *« $trigger »*\n" +
                        "• **Action associée** : **« $targetCommand »**\n\n" +
                        "Dites simplement *« $trigger »* (ou touchez le bouton ci-dessous) pour que je l'exécute immédiatement !",
                    learnedBadge = "🧠 Règle apprise : « $trigger »",
                    quickReplies = listOf(trigger, "Qu'as-tu appris ?")
                )
            }
        }

        val explicitFactRegex = Regex(
            "^(?:apprends\\s+que|retiens\\s+que|souviens(?:-toi)?\\s+que|m[ée]morise\\s+que|enregistre\\s+que|sache\\s+que|note\\s+que)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
        explicitFactRegex.find(trimmed)?.let { m ->
            val rawFact = m.groupValues[1].trim().removeSuffix(".")
            if (rawFact.length >= 3) {
                val parsed = parseStructuredSubjectAndValue(rawFact)
                LiaMemoryAndHistoryStore.upsertUserFact(parsed.key, parsed.title, parsed.value)
                LiaMemoryAndHistoryStore.upsertSouvenir(
                    type = "FAIT",
                    subjectKey = parsed.key,
                    title = parsed.title,
                    detailValue = parsed.value,
                    searchTags = "appris,retenu,${rawFact.lowercase().replace(Regex("[^a-z0-9à-ÿ ]"), " ")}",
                    importanceScore = 1.15f
                )
                return LiaBrainTurnOutcome(
                    replyText = "🧠 **C'est appris et indexé dans ma mémoire RAG** :\n\n" +
                        "• **${parsed.title}** : **${parsed.value}**\n\n" +
                        "Je m'en servirai automatiquement dans nos prochaines conversations et actions.",
                    learnedBadge = "🧠 Appris : ${parsed.title} = ${parsed.value}"
                )
            }
        }

        val matchedCustomRule = snap.longTermMemories.firstOrNull {
            it.category == "CUSTOM_RULE" && (
                it.title.equals(trimmed, ignoreCase = true) ||
                    lower == "exécute ${it.title.lowercase()}" ||
                    lower == "lance ${it.title.lowercase()}"
                )
        }
        if (matchedCustomRule != null) {
            val targetCmd = matchedCustomRule.contentValue
            LiaMemoryAndHistoryStore.recordLearnedShortcut(
                triggerPhrase = matchedCustomRule.title,
                actionTitle = "Règle exécutée : $targetCmd",
                targetPackageOrScreen = "rule:$targetCmd",
                stepsSaved = 5,
                latencyMs = 20L,
                rewardSignal = 1.4f
            )
            val delegated = executeRuleTargetCommand(targetCmd)
            if (delegated != null) {
                return delegated.copy(
                    replyText = "🧠 **Règle apprise activée (« ${matchedCustomRule.title} » ➔ « $targetCmd »)** :\n\n${delegated.replyText}",
                    learnedBadge = "🧠 Règle RL exécutée : ${matchedCustomRule.title}"
                )
            }
        }

        return null
    }

    data class ParsedFactTriple(
        val title: String,
        val value: String,
        val key: String
    )

    fun parseStructuredSubjectAndValue(rawFact: String): ParsedFactTriple {
        val cleaned = rawFact.trim().removeSuffix(".")
        val subParts = Regex("^(.+?)\\s+(?:est|sont|c'est|vaut|=)\\s+(.+)$", RegexOption.IGNORE_CASE).find(cleaned)
        return if (subParts != null) {
            val subject = subParts.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            val value = subParts.groupValues[2].trim()
            val key = subject.lowercase().replace(Regex("[^a-z0-9à-ÿ]"), "_").take(28).ifBlank {
                "fait_${System.currentTimeMillis() % 100000}"
            }
            ParsedFactTriple(subject, value, key)
        } else {
            val shortTitle = cleaned.take(38)
            val key = "souvenir_${System.currentTimeMillis() % 100000}"
            ParsedFactTriple("Souvenir : $shortTitle", cleaned, key)
        }
    }
}
