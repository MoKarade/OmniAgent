package com.example.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.data.LiaMemoryAndHistoryStore
import com.example.data.LiaUserFact
import kotlin.math.max
import kotlin.math.min

/**
 * Candidat de contact résolu avec score de correspondance floue (Levenshtein + bigrammes).
 */
data class ResolvedContactCandidate(
    val contactKey: String,
    val contactName: String,
    val phoneNumber: String,
    val matchScorePercent: Int,
    val matchReason: String,
    val isExactOrHighConfidence: Boolean
)

/**
 * Résultat de résolution du Safe Contact Resolver avant tout envoi de SMS.
 */
data class SafeContactResolution(
    val rawQuery: String,
    val bestCandidate: ResolvedContactCandidate,
    val alternatives: List<ResolvedContactCandidate> = emptyList(),
    val requiresExplicitModalConfirmation: Boolean = true,
    val warningMessage: String? = null
)

/**
 * Safe Contact Resolver :
 * - Compare la requête du destinataire (ex: "Maman", "Papa", ou un prénom)
 *   avec les contacts réellement enregistrés dans la base Room de LIA ET (si autorisé)
 *   avec le vrai carnet d'adresses Android (`ContactsContract.CommonDataKinds.Phone`).
 * - N'invente jamais de faux contacts ni de faux numéros de test.
 * - Calcule un score de similarité floue (distance de Levenshtein + similarité de bigrammes + alias familiaux)
 *   et exige une confirmation explicite dans une modale UI avant tout envoi de SMS.
 */
object SafeContactResolver {

    private val FAMILY_ALIASES: Map<String, List<String>> = mapOf(
        "maman" to listOf("maman", "mere", "mère", "mom", "mama", "darone"),
        "papa" to listOf("papa", "pere", "père", "dad", "paternel")
    )

    fun hasReadContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Lit les vrais contacts du répertoire Android (`ContactsContract`) lorsque la permission `READ_CONTACTS` est accordée.
     */
    fun readRealAndroidContacts(context: Context, maxContacts: Int = 150): List<Pair<String, String>> {
        if (!hasReadContactsPermission(context)) return emptyList()
        val results = mutableListOf<Pair<String, String>>()
        val seenKeys = mutableSetOf<String>()
        runCatching {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext() && results.size < maxContacts) {
                    val rawName = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val rawNumber = if (numIdx >= 0) cursor.getString(numIdx)?.trim().orEmpty() else ""
                    val cleanedPhone = extractPhoneNumber(rawNumber).ifBlank { rawNumber }
                    if (rawName.isNotBlank() && cleanedPhone.filter { it.isDigit() }.length >= 8) {
                        val dedupKey = "${rawName.lowercase()}_${cleanedPhone.filter { it.isDigit() }}"
                        if (seenKeys.add(dedupKey)) {
                            results.add(rawName to cleanedPhone)
                        }
                    }
                }
            }
        }
        return results
    }

    /**
     * Synchronise les vrais contacts du carnet d'adresses Android vers la base Room de LIA (`lia_user_facts` & `lia_souvenirs`).
     * Retourne le nombre de contacts réels importés.
     */
    fun syncAndroidContactsIntoRoom(context: Context): Int {
        val deviceContacts = readRealAndroidContacts(context)
        var importedCount = 0
        deviceContacts.forEach { (displayName, phoneNumber) ->
            val cleanKey = "${displayName.lowercase().replace(Regex("[^a-z0-9à-ÿ]"), "_").trim('_')}_tel"
            if (cleanKey.isNotBlank()) {
                LiaMemoryAndHistoryStore.upsertUserFact(
                    key = cleanKey,
                    label = "Numéro $displayName",
                    value = phoneNumber
                )
                importedCount++
            }
        }
        return importedCount
    }

    fun resolveContact(
        rawQuery: String,
        userFacts: List<LiaUserFact> = LiaMemoryAndHistoryStore.state.value.userFacts
    ): SafeContactResolution {
        val cleanQuery = rawQuery.trim()
        val normalizedQuery = normalizeToken(cleanQuery)

        // 1. Si l'utilisateur a saisi directement un numéro de téléphone
        val directPhone = extractPhoneNumber(cleanQuery)
        if (directPhone.isNotBlank() && normalizedQuery.all { it.isDigit() || it == '+' || it == ' ' }) {
            val candidate = ResolvedContactCandidate(
                contactKey = "direct_phone",
                contactName = "Numéro direct",
                phoneNumber = directPhone,
                matchScorePercent = 100,
                matchReason = "Numéro saisi directement",
                isExactOrHighConfidence = true
            )
            return SafeContactResolution(
                rawQuery = cleanQuery,
                bestCandidate = candidate,
                alternatives = emptyList(),
                requiresExplicitModalConfirmation = true,
                warningMessage = null
            )
        }

        // 2. Filtrer les contacts enregistrés dans Room + fusionner avec le carnet Android si permission accordée
        val roomContactFacts = userFacts.filter { fact ->
            fact.key.endsWith("_tel", ignoreCase = true) ||
                fact.key.contains("contact", ignoreCase = true) ||
                fact.label.contains("numéro", ignoreCase = true) ||
                fact.label.contains("numero", ignoreCase = true) ||
                extractPhoneNumber(fact.value).isNotBlank()
        }.toMutableList()

        val ctx = LiaMemoryAndHistoryStore.getApplicationContextOrNull()
        if (ctx != null && hasReadContactsPermission(ctx)) {
            val deviceContacts = readRealAndroidContacts(ctx, maxContacts = 100)
            for ((devName, devPhone) in deviceContacts) {
                val key = "${devName.lowercase().replace(" ", "_")}_tel"
                if (roomContactFacts.none { it.key.equals(key, ignoreCase = true) }) {
                    roomContactFacts.add(
                        LiaUserFact(
                            key = key,
                            label = "Numéro $devName (Carnet Android)",
                            value = devPhone,
                            updatedAt = "Android"
                        )
                    )
                }
            }
        }

        val contactFacts = roomContactFacts
        val scoredCandidates = mutableListOf<ResolvedContactCandidate>()

        for (fact in contactFacts) {
            val candidateName = extractDisplayName(fact.key, fact.label)
            val candidatePhone = extractPhoneNumber(fact.value).ifBlank {
                if (!fact.value.startsWith("Non renseigné", ignoreCase = true)) fact.value.trim() else ""
            }
            val normKey = normalizeToken(fact.key.removeSuffix("_tel"))
            val normName = normalizeToken(candidateName)

            if (normalizedQuery.isBlank()) {
                scoredCandidates.add(
                    ResolvedContactCandidate(
                        contactKey = fact.key,
                        contactName = candidateName,
                        phoneNumber = candidatePhone,
                        matchScorePercent = 60,
                        matchReason = "Contact enregistré dans LIA",
                        isExactOrHighConfidence = false
                    )
                )
                continue
            }

            val exactMatch = normName == normalizedQuery ||
                normKey == normalizedQuery ||
                normKey.split(" ").contains(normalizedQuery) ||
                normName.split(" ").contains(normalizedQuery)

            val aliasMatched = FAMILY_ALIASES.entries.any { (canonical, aliases) ->
                val queryInGroup = normalizedQuery == canonical || aliases.contains(normalizedQuery)
                val contactInGroup = normName.contains(canonical) || normKey.contains(canonical) ||
                    aliases.any { normName.contains(it) || normKey.contains(it) }
                queryInGroup && contactInGroup
            }

            val nameSimilarity = computeFuzzySimilarityPercent(normalizedQuery, normName)
            val keySimilarity = computeFuzzySimilarityPercent(normalizedQuery, normKey)
            val bestFuzzy = max(nameSimilarity, keySimilarity)

            val finalScore = when {
                exactMatch -> 100
                aliasMatched -> max(96, bestFuzzy)
                else -> bestFuzzy
            }

            val reason = when {
                exactMatch -> "Correspondance exacte Nom ↔ Numéro"
                aliasMatched -> "Alias familial reconnu (« $cleanQuery » → $candidateName)"
                finalScore >= 75 -> "Correspondance floue proche ($finalScore%)"
                else -> "Autre contact enregistré ($finalScore%)"
            }

            scoredCandidates.add(
                ResolvedContactCandidate(
                    contactKey = fact.key,
                    contactName = candidateName,
                    phoneNumber = candidatePhone,
                    matchScorePercent = finalScore,
                    matchReason = reason,
                    isExactOrHighConfidence = finalScore >= 85 && candidatePhone.isNotBlank()
                )
            )
        }

        val sorted = scoredCandidates.sortedByDescending { it.matchScorePercent }
        val top = sorted.firstOrNull { it.matchScorePercent >= 55 }

        return if (top != null) {
            val hasPhone = top.phoneNumber.isNotBlank()
            val warning = when {
                !hasPhone -> "Le contact « ${top.contactName} » n'a pas encore de numéro enregistré dans LIA."
                top.matchScorePercent < 95 -> "Correspondance floue (${top.matchScorePercent}%) : « $cleanQuery » associé à « ${top.contactName} » (${top.phoneNumber}). Confirmez le destinataire."
                else -> null
            }
            SafeContactResolution(
                rawQuery = cleanQuery,
                bestCandidate = top,
                alternatives = sorted.filter { it.contactKey != top.contactKey }.take(4),
                requiresExplicitModalConfirmation = true,
                warningMessage = warning
            )
        } else {
            val fallbackName = cleanQuery
                .ifBlank { "Destinataire" }
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val unresolved = ResolvedContactCandidate(
                contactKey = "${fallbackName.lowercase()}_tel",
                contactName = fallbackName,
                phoneNumber = "",
                matchScorePercent = 0,
                matchReason = "Aucun numéro enregistré pour « $fallbackName »",
                isExactOrHighConfidence = false
            )
            SafeContactResolution(
                rawQuery = cleanQuery,
                bestCandidate = unresolved,
                alternatives = sorted.take(4),
                requiresExplicitModalConfirmation = true,
                warningMessage = "Aucun numéro enregistré pour « $fallbackName » dans votre base LIA. Saisissez son numéro pour l'enregistrer et envoyer le SMS."
            )
        }
    }

    fun extractPhoneNumber(raw: String): String {
        val regex = Regex("(\\+?[0-9][0-9\\s.-]{6,16}[0-9])")
        return regex.find(raw)?.value?.replace(Regex("[\\s.-]"), " ")?.trim() ?: ""
    }

    private fun extractDisplayName(key: String, label: String): String {
        val cleanedLabel = label
            .replace("Numéro ", "", ignoreCase = true)
            .replace("Numero ", "", ignoreCase = true)
            .replace("Téléphone ", "", ignoreCase = true)
            .replace("Telephone ", "", ignoreCase = true)
            .trim()
        if (cleanedLabel.isNotBlank()) {
            return cleanedLabel.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        val cleanedKey = key.removeSuffix("_tel").replace("_", " ").trim()
        return cleanedKey.ifBlank { "Contact" }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun normalizeToken(input: String): String {
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
            .replace(Regex("[^a-z0-9+ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun computeFuzzySimilarityPercent(a: String, b: String): Int {
        if (a.isBlank() || b.isBlank()) return 0
        if (a == b) return 100
        if (b.contains(a) || a.contains(b)) {
            val ratio = min(a.length, b.length).toDouble() / max(a.length, b.length).toDouble()
            return (78 + (ratio * 20)).toInt().coerceIn(78, 98)
        }

        val levDist = levenshteinDistance(a, b)
        val maxLen = max(a.length, b.length).coerceAtLeast(1)
        val levScore = ((1.0 - levDist.toDouble() / maxLen.toDouble()) * 100.0).coerceIn(0.0, 100.0)
        val bigramScore = diceBigramSimilarity(a, b) * 100.0
        return ((levScore * 0.6) + (bigramScore * 0.4)).toInt().coerceIn(0, 100)
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        var cost = IntArray(lhsLen + 1) { it }
        var newCost = IntArray(lhsLen + 1)

        for (i in 1..rhsLen) {
            newCost[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }

    private fun diceBigramSimilarity(s1: String, s2: String): Double {
        if (s1.length < 2 || s2.length < 2) return if (s1 == s2) 1.0 else 0.0
        val bigrams1 = (0 until s1.length - 1).map { s1.substring(it, it + 2) }
        val bigrams2 = (0 until s2.length - 1).map { s2.substring(it, it + 2) }.toMutableList()
        var intersection = 0
        for (bg in bigrams1) {
            val idx = bigrams2.indexOf(bg)
            if (idx != -1) {
                intersection++
                bigrams2.removeAt(idx)
            }
        }
        return (2.0 * intersection) / (bigrams1.size + (s2.length - 1))
    }
}
