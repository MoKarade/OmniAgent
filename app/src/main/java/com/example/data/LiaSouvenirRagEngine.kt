package com.example.data

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ScoredSouvenirRagMatch(
    val souvenir: LiaSouvenirItem,
    val similarityPercent: Int,
    val cosineScore: Float,
    val lexicalScore: Float,
    val mem0Triplet: String,
    val matchedTerms: List<String>
)

data class RagRetrievalOutcome(
    val query: String,
    val retrievedMatches: List<ScoredSouvenirRagMatch>,
    val augmentedPromptContext: String,
    val directHighConfidenceAnswer: String? = null,
    val retrievalLatencyMs: Long = 1L
)

/**
 * Moteur RAG (Retrieval-Augmented Generation) embarqué sur la table Room `lia_souvenirs`.
 *
 * Avant chaque génération de réponse par LIA :
 * 1. Découpe et vectorise localement la requête de l'utilisateur en un vecteur dense normalisé (64 dimensions)
 *    combinant des ancres sémantiques personnelles (âge, préférences, famille, ville, métier, événements)
 *    et des trigrammes de caractères.
 * 2. Interroge les vecteurs et métadonnées stockés dans la table Room `lia_souvenirs` (ainsi que `lia_user_facts`).
 * 3. Calcule un score hybride (Similarité Cosinus Vectorielle + Score Lexical BM25 + Synonymes Français).
 * 4. Injecte les souvenirs pertinents sous forme de contexte structuré et de triplets Mem0 (`Sujet ➔ Prédicat ➔ Objet`)
 *    dans le prompt de LIA, et fournit une réponse directe haute-précision lorsqu'une question personnelle
 *    cible un souvenir stocké.
 */
object LiaSouvenirRagEngine {

    private const val EMBEDDING_DIM = 64
    private const val CHUNK_WINDOW_CHARS = 1000

    private val FRENCH_STOP_WORDS = setOf(
        "le", "la", "les", "un", "une", "des", "du", "de", "d", "et", "ou", "mais",
        "donc", "car", "ni", "que", "qui", "quoi", "quel", "quelle", "quels", "quelles",
        "est", "sont", "suis", "es", "ai", "as", "a", "avons", "avez", "ont",
        "mon", "ma", "mes", "ton", "ta", "tes", "son", "sa", "ses", "ce", "cet", "cette",
        "dans", "sur", "sous", "avec", "sans", "pour", "par", "en", "au", "aux",
        "je", "tu", "il", "elle", "nous", "vous", "ils", "elles", "moi", "toi",
        "comment", "combien", "quand", "ou", "où", "pourquoi", "est-ce", "c'est", "qu'est-ce"
    )

    private val CONCEPT_SYNONYM_CLUSTERS: List<Set<String>> = listOf(
        setOf("age", "âge", "ans", "vieux", "jeune", "naissance", "né", "nee"),
        setOf("anniversaire", "fete", "fête", "date", "naissance"),
        setOf("prenom", "prénom", "nom", "appelle", "identite", "identité", "qui"),
        setOf("maman", "mere", "mère", "mom", "mama", "parents", "famille"),
        setOf("papa", "pere", "père", "dad", "parents", "famille"),
        setOf("ville", "habite", "vis", "adresse", "logement", "maison", "domicile", "lieu"),
        setOf("travail", "metier", "métier", "job", "profession", "entreprise", "etudes", "études", "ecole"),
        setOf("aime", "adore", "prefere", "préfère", "preference", "préférence", "favori", "favorite", "gout", "goût", "passion", "loisir"),
        setOf("boisson", "cafe", "café", "the", "thé", "plat", "repas", "manger", "cuisine", "aliment", "nourriture"),
        setOf("musique", "chanson", "artiste", "film", "serie", "série", "sport", "jeu"),
        setOf("rdv", "rendez-vous", "reunion", "réunion", "evenement", "événement", "agenda", "calendrier", "rappel", "demain", "heure"),
        setOf("numero", "numéro", "telephone", "téléphone", "tel", "contact", " joindre", "appeler", "sms")
    )

    /**
     * Découpe un souvenir en fenêtre de 1 000 caractères max (conformément à la spécification RAG NPU).
     */
    fun buildChunkText(
        type: String,
        subjectKey: String,
        title: String,
        detailValue: String,
        searchTags: String,
        eventDateOrTime: String
    ): String {
        val raw = buildString {
            append("[$type] ")
            append(title.trim())
            append(" (")
            append(subjectKey.trim())
            append(") : ")
            append(detailValue.trim())
            if (eventDateOrTime.isNotBlank()) {
                append(" | Date/Heure: ")
                append(eventDateOrTime.trim())
            }
            if (searchTags.isNotBlank()) {
                append(" | Mots-clés: ")
                append(searchTags.trim())
            }
        }
        return raw.take(CHUNK_WINDOW_CHARS)
    }

    /**
     * Calcule un vecteur d'embedding local normalisé de dimension 64 :
     * - Dimensions 0..11 : activation sémantique sur les 12 clusters de concepts personnels français
     * - Dimensions 12..63 : projection par hachage de trigrammes de caractères et de radicaux lexicaux
     */
    fun computeLocalEmbedding(text: String): FloatArray {
        val vec = FloatArray(EMBEDDING_DIM)
        val normalized = normalizeText(text)
        if (normalized.isBlank()) return vec

        val tokens = normalized.split(" ").filter { it.isNotBlank() }

        // 1. Concept cluster dimensions (0..11)
        CONCEPT_SYNONYM_CLUSTERS.forEachIndexed { clusterIdx, clusterWords ->
            if (clusterIdx < 12) {
                var hits = 0f
                for (token in tokens) {
                    if (clusterWords.any { cw -> token == cw || (token.length >= 3 && cw.startsWith(token)) || (cw.length >= 3 && token.startsWith(cw)) }) {
                        hits += 1.0f
                    }
                }
                vec[clusterIdx] = hits * 1.8f
            }
        }

        // 2. Lexical & character trigram projection (12..63)
        val padded = " $normalized "
        for (i in 0 until max(0, padded.length - 2)) {
            val trigram = padded.substring(i, i + 3)
            val hash = (trigram.hashCode() and 0x7FFFFFFF)
            val bucket = 12 + (hash % (EMBEDDING_DIM - 12))
            vec[bucket] += 0.45f
        }

        for (token in tokens) {
            if (token !in FRENCH_STOP_WORDS && token.length >= 2) {
                val stem = token.take(5)
                val hash = (stem.hashCode() and 0x7FFFFFFF)
                val bucket = 12 + (hash % (EMBEDDING_DIM - 12))
                vec[bucket] += 1.35f
            }
        }

        // L2 Normalization
        var normSq = 0f
        for (v in vec) normSq += v * v
        val norm = sqrt(normSq)
        if (norm > 1e-6f) {
            for (i in vec.indices) {
                vec[i] = vec[i] / norm
            }
        }
        return vec
    }

    fun serializeEmbeddingCsv(vec: FloatArray): String {
        return vec.joinToString(",") { "%.4f".format(java.util.Locale.US, it) }
    }

    fun parseEmbeddingCsv(csv: String): FloatArray {
        if (csv.isBlank()) return FloatArray(EMBEDDING_DIM)
        val parts = csv.split(",")
        val out = FloatArray(EMBEDDING_DIM)
        for (i in 0 until minOf(parts.size, EMBEDDING_DIM)) {
            out[i] = parts[i].toFloatOrNull() ?: 0f
        }
        return out
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 1e-6f) (dot / denom).coerceIn(0f, 1f) else 0f
    }

    /**
     * Exécute la recherche RAG hybride sur les souvenirs Room (`lia_souvenirs`) et faits (`lia_user_facts`)
     * avant la génération de réponse.
     */
    fun retrieveForPrompt(
        userQuery: String,
        souvenirs: List<LiaSouvenirItem>,
        userFacts: List<LiaUserFact>,
        topK: Int = 5
    ): RagRetrievalOutcome {
        val startMs = System.currentTimeMillis()
        val normalizedQuery = normalizeText(userQuery)
        val queryTokens = normalizedQuery
            .split(" ")
            .filter { it.length >= 2 && it !in FRENCH_STOP_WORDS }

        // Expand query tokens with concept synonyms
        val expandedTokens = mutableSetOf<String>().apply {
            addAll(queryTokens)
            for (qt in queryTokens) {
                CONCEPT_SYNONYM_CLUSTERS.forEach { cluster ->
                    if (cluster.any { it == qt || (qt.length >= 3 && it.startsWith(qt)) }) {
                        addAll(cluster.map { normalizeText(it) })
                    }
                }
            }
        }

        // Merge souvenirs and any userFacts not yet represented in souvenirs
        val combinedSouvenirs = buildList {
            addAll(souvenirs)
            userFacts.forEach { fact ->
                val alreadyInSouvenirs = souvenirs.any {
                    it.subjectKey.equals(fact.key, ignoreCase = true) ||
                        it.detailValue.equals(fact.value, ignoreCase = true)
                }
                if (!alreadyInSouvenirs) {
                    val chunk = buildChunkText("FAIT", fact.key, fact.label, fact.value, fact.key, "")
                    add(
                        LiaSouvenirItem(
                            id = "fact_rag_${fact.key}",
                            type = if (fact.key.endsWith("_tel")) "CONTACT" else "FAIT",
                            subjectKey = fact.key,
                            title = fact.label,
                            detailValue = fact.value,
                            searchTags = "${fact.key},${fact.label.lowercase()}",
                            createdAt = fact.updatedAt,
                            embeddingVectorCsv = serializeEmbeddingCsv(computeLocalEmbedding(chunk)),
                            chunkText = chunk
                        )
                    )
                }
            }
        }

        if (combinedSouvenirs.isEmpty()) {
            return RagRetrievalOutcome(
                query = userQuery,
                retrievedMatches = emptyList(),
                augmentedPromptContext = "=== CONTEXTE RAG ROOM (`lia_souvenirs`) ===\nAucun souvenir personnel enregistré dans la base Room pour le moment.",
                directHighConfidenceAnswer = null,
                retrievalLatencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            )
        }

        val queryVec = computeLocalEmbedding(userQuery)

        val scored = combinedSouvenirs.map { item ->
            val docText = item.chunkText.ifBlank {
                buildChunkText(item.type, item.subjectKey, item.title, item.detailValue, item.searchTags, item.eventDateOrTime)
            }
            val docVec = if (item.embeddingVectorCsv.isNotBlank()) {
                parseEmbeddingCsv(item.embeddingVectorCsv)
            } else {
                computeLocalEmbedding(docText)
            }

            val cosSim = cosineSimilarity(queryVec, docVec)
            val normDoc = normalizeText("$docText ${item.subjectKey} ${item.title} ${item.detailValue} ${item.searchTags}")
            val docWords = normDoc.split(" ").filter { it.isNotBlank() }

            val matched = mutableListOf<String>()
            var lexicalPoints = 0f

            for (qt in queryTokens) {
                if (normalizeText(item.subjectKey).contains(qt) || normalizeText(item.title).contains(qt)) {
                    lexicalPoints += 0.55f
                    matched.add(qt)
                } else if (docWords.any { dw -> dw == qt || (qt.length >= 3 && dw.startsWith(qt)) || (dw.length >= 3 && qt.startsWith(dw)) }) {
                    lexicalPoints += 0.40f
                    matched.add(qt)
                }
            }

            for (syn in expandedTokens) {
                if (syn !in matched && docWords.any { it == syn }) {
                    lexicalPoints += 0.22f
                    matched.add(syn)
                }
            }

            val lexicalNorm = lexicalPoints.coerceIn(0f, 1.0f)
            val combinedScore = ((cosSim * 0.48f) + (lexicalNorm * 0.52f) * item.importanceScore).coerceIn(0f, 1f)
            val simPercent = (combinedScore * 100f).roundToInt().coerceIn(0, 99)

            val predicate = when (item.type.uppercase()) {
                "PREFERENCE" -> "PRÉFÈRE"
                "EVENEMENT" -> "A_POUR_ÉVÉNEMENT"
                "CONTACT" -> "A_POUR_CONTACT"
                else -> "A_POUR_FAIT"
            }
            val triplet = "Utilisateur ➔ [$predicate] ➔ ${item.title} (${item.detailValue})"

            ScoredSouvenirRagMatch(
                souvenir = item,
                similarityPercent = simPercent,
                cosineScore = cosSim,
                lexicalScore = lexicalNorm,
                mem0Triplet = triplet,
                matchedTerms = matched.distinct()
            )
        }.sortedWith(
            compareByDescending<ScoredSouvenirRagMatch> { it.similarityPercent }
                .thenByDescending { it.souvenir.importanceScore }
        )

        // Filter relevant matches (or include top items if the user asks a broad personal question)
        val relevant = scored.filter { it.similarityPercent >= 24 || it.matchedTerms.isNotEmpty() }
            .ifEmpty { scored.take(topK) }
            .take(topK)

        val latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        // Build augmented prompt block for LLM generation
        val augmentedBlock = buildString {
            appendLine("=== CONTEXTE RAG EXTRAIT DE LA BASE ROOM `lia_souvenirs` (${relevant.size} souvenirs pertinents en ${latencyMs} ms) ===")
            relevant.forEachIndexed { idx, m ->
                val s = m.souvenir
                val dateInfo = if (s.eventDateOrTime.isNotBlank()) " • Date/Heure: ${s.eventDateOrTime}" else ""
                appendLine("${idx + 1}. [Score RAG ${m.similarityPercent}% | ${s.type}] ${s.title} (clé: ${s.subjectKey})$dateInfo => ${s.detailValue}")
                appendLine("   Triplet Mem0 : ${m.mem0Triplet}")
            }
            appendLine("INSTRUCTION RAG CRITIQUE : Utilise EXCLUSIVEMENT et AVEC PRÉCISION ces souvenirs Room pour répondre aux questions personnelles de l'utilisateur.")
        }

        // Check if this is a direct personal question that can be answered with high precision from the top RAG match
        val topMatch = relevant.firstOrNull()
        val isExplicitPersonalQuestion = userQuery.contains("?") ||
            normalizedQuery.startsWith("quel") ||
            normalizedQuery.startsWith("quand") ||
            normalizedQuery.startsWith("ou ") ||
            normalizedQuery.startsWith("comment") ||
            normalizedQuery.startsWith("combien") ||
            normalizedQuery.startsWith("qui ") ||
            normalizedQuery.startsWith("que sais") ||
            normalizedQuery.startsWith("qu est ce que j aime") ||
            normalizedQuery.startsWith("qu est ce que je prefere")

        val isActionCommand = normalizedQuery.startsWith("ouvre") ||
            normalizedQuery.startsWith("lance") ||
            normalizedQuery.startsWith("envoie") ||
            normalizedQuery.startsWith("envoyer") ||
            normalizedQuery.startsWith("ecris") ||
            normalizedQuery.startsWith("retrouve") ||
            normalizedQuery.startsWith("trouve") ||
            normalizedQuery.startsWith("cherche") ||
            normalizedQuery.startsWith("appelle") ||
            normalizedQuery.startsWith("mets") ||
            normalizedQuery.startsWith("ajoute")

        val directAnswer = if (isExplicitPersonalQuestion && !isActionCommand && topMatch != null && (topMatch.similarityPercent >= 48 || topMatch.matchedTerms.size >= 2)) {
            val s = topMatch.souvenir
            val extraMatches = relevant.drop(1).filter { it.similarityPercent >= 45 }
            buildString {
                when (s.type.uppercase()) {
                    "PREFERENCE" -> append("D'après vos préférences, **${s.title}** : **${s.detailValue}**.")
                    "EVENEMENT" -> {
                        val dateNote = if (s.eventDateOrTime.isNotBlank()) " (${s.eventDateOrTime})" else ""
                        append("Voici ce qui est noté dans votre agenda : **${s.title}**$dateNote — **${s.detailValue}**.")
                    }
                    else -> append("**${s.title}** : **${s.detailValue}**.")
                }
                if (extraMatches.isNotEmpty()) {
                    append("\n\n**Autres informations liées :**\n")
                    extraMatches.take(3).forEach { em ->
                        append("• **${em.souvenir.title}** : ${em.souvenir.detailValue}\n")
                    }
                }
            }.trim()
        } else null

        return RagRetrievalOutcome(
            query = userQuery,
            retrievedMatches = relevant,
            augmentedPromptContext = augmentedBlock,
            directHighConfidenceAnswer = directAnswer,
            retrievalLatencyMs = latencyMs
        )
    }

    private fun normalizeText(input: String): String {
        return input.lowercase()
            .replace("é", "e")
            .replace("è", "e")
            .replace("ê", "e")
            .replace("ë", "e")
            .replace("à", "a")
            .replace("â", "a")
            .replace("î", "i")
            .replace("ï", "i")
            .replace("ô", "o")
            .replace("ù", "u")
            .replace("û", "u")
            .replace("ç", "c")
            .replace("'", " ")
            .replace("’", " ")
            .replace(Regex("[^a-z0-9+ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
