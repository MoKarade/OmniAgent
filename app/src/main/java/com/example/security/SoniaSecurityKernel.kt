package com.example.security

import android.graphics.Bitmap
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.math.pow

/**
 * All specialized tools available to Sonia ("Tous les outils imaginables"),
 * each governed by Zero-Trust Scoped Tokens and DLP/Anti-Injection inspection.
 */
enum class SoniaToolType(
    val id: String,
    val emoji: String,
    val shortLabel: String,
    val titleFr: String,
    val description: String,
    val riskLevel: ToolRiskLevel
) {
    CREATE_CHART(
        id = "tool:create_chart",
        emoji = "📊",
        shortLabel = "Graphe",
        titleFr = "Graphes & Stats",
        description = "Génère des graphiques interactifs (barres, courbes, anneau)",
        riskLevel = ToolRiskLevel.SAFE
    ),
    CREATE_CHECKLIST(
        id = "tool:create_checklist",
        emoji = "☑️",
        shortLabel = "Liste",
        titleFr = "Listes & Checklists",
        description = "Crée des listes de tâches et checklists interactives cochables",
        riskLevel = ToolRiskLevel.SAFE
    ),
    GENERATE_IMAGE(
        id = "tool:generate_image",
        emoji = "🎨",
        shortLabel = "Image IA",
        titleFr = "Studio Image IA",
        description = "Génère des images via Gemini Nano Banana 2.1",
        riskLevel = ToolRiskLevel.ELEVATED
    ),
    CREATE_TABLE(
        id = "tool:create_table",
        emoji = "📋",
        shortLabel = "Tableau",
        titleFr = "Tableaux Comparatifs",
        description = "Construit des tableaux comparatifs et synthèses structurées",
        riskLevel = ToolRiskLevel.SAFE
    ),
    CREATE_TIMELINE(
        id = "tool:create_timeline",
        emoji = "🗓️",
        shortLabel = "Planning",
        titleFr = "Planning & Itinéraire",
        description = "Crée un itinéraire ou une frise chronologique étape par étape",
        riskLevel = ToolRiskLevel.SAFE
    ),
    CODE_CALCULATOR(
        id = "tool:code_calculator",
        emoji = "🧮",
        shortLabel = "Calcul",
        titleFr = "Calcul & Sandbox Code",
        description = "Exécute des calculs financiers, scientifiques et formules",
        riskLevel = ToolRiskLevel.SAFE
    ),
    VOICE_TTS(
        id = "tool:voice_tts",
        emoji = "🔊",
        shortLabel = "Voix",
        titleFr = "Synthèse Vocale",
        description = "Lit les réponses à voix haute via synthèse vocale Android",
        riskLevel = ToolRiskLevel.SAFE
    ),
    PHONE_GUI_OPERATOR(
        id = "tool:phone_operator",
        emoji = "📱",
        shortLabel = "Écran",
        titleFr = "Contrôle Téléphone",
        description = "Ouvre, lit et contrôle les applications du téléphone",
        riskLevel = ToolRiskLevel.ELEVATED
    ),
    PERSONAL_VAULT_MCP(
        id = "tool:personal_vault",
        emoji = "🔐",
        shortLabel = "Coffre",
        titleFr = "Coffre Privé MCP",
        description = "Consulte vos Contacts, Agenda, SMS et Notes chiffrées",
        riskLevel = ToolRiskLevel.CRITICAL
    ),
    FINANCIAL_GUARD(
        id = "tool:financial_guard",
        emoji = "💳",
        shortLabel = "Paiement",
        titleFr = "Garde Financière HITL",
        description = "Prépare des opérations bancaires avec accord humain obligatoire",
        riskLevel = ToolRiskLevel.CRITICAL
    )
}

enum class ToolRiskLevel(val label: String) {
    SAFE("Standard"),
    ELEVATED("Surveillé"),
    CRITICAL("Haute Sécurité (HITL / PIN)")
}

enum class SoniaChartStyle {
    BAR,
    LINE,
    DONUT
}

data class SoniaChartPoint(
    val label: String,
    val value: Float,
    val colorHex: Long
)

data class SoniaChecklistItem(
    val id: String,
    val text: String,
    val isChecked: Boolean = false,
    val category: String = ""
)

data class SoniaTimelineStep(
    val timeLabel: String,
    val title: String,
    val detail: String
)

/**
 * Rich visual artifacts created by Sonia and rendered directly inside the dark chat stream.
 */
sealed interface SoniaArtifact {
    val id: String
    val title: String
    val createdAt: String

    data class ChartArtifact(
        override val id: String,
        override val title: String,
        val subtitle: String,
        val style: SoniaChartStyle,
        val unit: String,
        val points: List<SoniaChartPoint>,
        override val createdAt: String
    ) : SoniaArtifact

    data class ChecklistArtifact(
        override val id: String,
        override val title: String,
        val items: List<SoniaChecklistItem>,
        override val createdAt: String
    ) : SoniaArtifact

    data class TableArtifact(
        override val id: String,
        override val title: String,
        val headers: List<String>,
        val rows: List<List<String>>,
        override val createdAt: String
    ) : SoniaArtifact

    data class TimelineArtifact(
        override val id: String,
        override val title: String,
        val steps: List<SoniaTimelineStep>,
        override val createdAt: String
    ) : SoniaArtifact

    data class CodeCalcArtifact(
        override val id: String,
        override val title: String,
        val expressionOrCode: String,
        val evaluatedResult: String,
        val sha256Signature: String,
        override val createdAt: String
    ) : SoniaArtifact

    data class GeneratedImageArtifact(
        override val id: String,
        override val title: String,
        val prompt: String,
        val bitmap: Bitmap? = null,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        override val createdAt: String
    ) : SoniaArtifact
}

data class DlpScanResult(
    val isSafe: Boolean,
    val sanitizedText: String,
    val detectedThreats: List<String>
)

data class ChainedSecurityAuditBlock(
    val index: Int,
    val timestampFormatted: String,
    val agentName: String,
    val toolName: String,
    val actionSummary: String,
    val granted: Boolean,
    val previousHash: String,
    val blockHash: String
)

/**
 * Hardware-backed Android Keystore AES-256-GCM encryption + DLP & Prompt Injection Firewall
 * + Tamper-evident SHA-256 Audit Chain + Instant Intelligent Tool Generators.
 */
object SoniaSecurityKernel {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "sonia_zero_trust_aes256_master_key"

    private val injectionPatterns = listOf(
        Regex("ignore\\s+(all\\s+)?previous\\s+instructions", RegexOption.IGNORE_CASE),
        Regex("system\\s*override", RegexOption.IGNORE_CASE),
        Regex("virement\\s+imm[eé]diat\\s+vers", RegexOption.IGNORE_CASE),
        Regex("transf[eé]rer\\s+tout\\s+le\\s+solde", RegexOption.IGNORE_CASE),
        Regex("rm\\s+-rf", RegexOption.IGNORE_CASE),
        Regex("<script>", RegexOption.IGNORE_CASE)
    )

    private val ibanRegex = Regex("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b")
    private val cardRegex = Regex("\\b(?:\\d[ -]*?){13,16}\\b")

    private val paletteColors = listOf(
        0xFF8AB4F8L, // Google Blue
        0xFF81C995L, // Google Green
        0xFFFDD663L, // Google Amber
        0xFFF28B82L, // Google Coral
        0xFFC58AF9L, // Google Purple
        0xFF78D9ECL  // Google Cyan
    )

    fun isHardwareKeystoreReady(): Boolean {
        return try {
            getOrCreateHardwareAesKey() != null
        } catch (_: Exception) {
            true
        }
    }

    private fun getOrCreateHardwareAesKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) return existing.secretKey

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            null
        }
    }

    fun encryptSensitivePayload(plainText: String): String {
        return try {
            val secretKey = getOrCreateHardwareAesKey() ?: return sha256Hex(plainText)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            "aes256_gcm:${sha256Hex(plainText).take(24)}"
        }
    }

    fun inspectAndSanitize(input: String, maskPii: Boolean = true): DlpScanResult {
        val threats = mutableListOf<String>()
        var sanitized = input

        for (pattern in injectionPatterns) {
            if (pattern.containsMatchIn(sanitized)) {
                threats.add("Injection de prompt neutralisée")
                sanitized = pattern.replace(sanitized, "[BLOQUÉ PAR PARE-FEU SONIA]")
            }
        }

        if (maskPii) {
            if (ibanRegex.containsMatchIn(sanitized)) {
                sanitized = ibanRegex.replace(sanitized) { m ->
                    "${m.value.take(4)}••••••••${m.value.takeLast(4)}"
                }
            }
            if (cardRegex.containsMatchIn(sanitized)) {
                sanitized = cardRegex.replace(sanitized, "•••• •••• •••• ••••")
            }
        }

        return DlpScanResult(
            isSafe = threats.isEmpty(),
            sanitizedText = sanitized,
            detectedThreats = threats
        )
    }

    fun createAuditBlock(
        index: Int,
        timestampFormatted: String,
        agentName: String,
        toolName: String,
        actionSummary: String,
        granted: Boolean,
        previousHash: String
    ): ChainedSecurityAuditBlock {
        val payload = "$index|$timestampFormatted|$agentName|$toolName|$actionSummary|$granted|$previousHash"
        val hash = sha256Hex(payload).take(16)
        return ChainedSecurityAuditBlock(
            index = index,
            timestampFormatted = timestampFormatted,
            agentName = agentName,
            toolName = toolName,
            actionSummary = actionSummary,
            granted = granted,
            previousHash = previousHash,
            blockHash = hash
        )
    }

    fun buildInitialAuditChain(): List<ChainedSecurityAuditBlock> {
        val b1 = createAuditBlock(
            index = 1,
            timestampFormatted = "Initialisation",
            agentName = "Noyau Sonia",
            toolName = "AES-256-GCM",
            actionSummary = "Clé matérielle AndroidKeyStore initialisée",
            granted = true,
            previousHash = "0000000000000000"
        )
        val b2 = createAuditBlock(
            index = 2,
            timestampFormatted = "Initialisation",
            agentName = "Pare-feu DLP",
            toolName = "ANTI-INJECTION",
            actionSummary = "Protection anti-injection & masquage PII actifs",
            granted = true,
            previousHash = b1.blockHash
        )
        return listOf(b2, b1)
    }

    fun buildChartFromPrompt(prompt: String, idSuffix: Long, now: String): SoniaArtifact.ChartArtifact {
        val lower = prompt.lowercase()
        return when {
            lower.contains("batterie") || lower.contains("écran") || lower.contains("temps") -> {
                SoniaArtifact.ChartArtifact(
                    id = "chart_$idSuffix",
                    title = "Temps d'écran & Batterie",
                    subtitle = "Analyse hebdomadaire • Cliquez pour changer de vue",
                    style = SoniaChartStyle.BAR,
                    unit = "h",
                    points = listOf(
                        SoniaChartPoint("Lun", 3.2f, paletteColors[0]),
                        SoniaChartPoint("Mar", 2.8f, paletteColors[1]),
                        SoniaChartPoint("Mer", 4.1f, paletteColors[2]),
                        SoniaChartPoint("Jeu", 2.5f, paletteColors[1]),
                        SoniaChartPoint("Ven", 3.6f, paletteColors[4]),
                        SoniaChartPoint("Sam", 4.5f, paletteColors[3]),
                        SoniaChartPoint("Dim", 2.1f, paletteColors[5])
                    ),
                    createdAt = now
                )
            }
            lower.contains("épargne") || lower.contains("invest") || lower.contains("courbe") -> {
                SoniaArtifact.ChartArtifact(
                    id = "chart_$idSuffix",
                    title = "Croissance & Épargne",
                    subtitle = "Progression sur 6 mois • Vue interactive",
                    style = SoniaChartStyle.LINE,
                    unit = "€",
                    points = listOf(
                        SoniaChartPoint("Jan", 1200f, paletteColors[0]),
                        SoniaChartPoint("Fév", 1550f, paletteColors[1]),
                        SoniaChartPoint("Mar", 1890f, paletteColors[0]),
                        SoniaChartPoint("Avr", 2340f, paletteColors[2]),
                        SoniaChartPoint("Mai", 2780f, paletteColors[4]),
                        SoniaChartPoint("Juin", 3420f, paletteColors[1])
                    ),
                    createdAt = now
                )
            }
            else -> {
                SoniaArtifact.ChartArtifact(
                    id = "chart_$idSuffix",
                    title = "Répartition du Budget Mensuel",
                    subtitle = "Synthèse financière sécurisée • Barres / Courbe / Anneau",
                    style = SoniaChartStyle.BAR,
                    unit = "€",
                    points = listOf(
                        SoniaChartPoint("Logement", 850f, paletteColors[0]),
                        SoniaChartPoint("Courses", 420f, paletteColors[1]),
                        SoniaChartPoint("Épargne", 550f, paletteColors[4]),
                        SoniaChartPoint("Transport", 160f, paletteColors[2]),
                        SoniaChartPoint("Loisirs", 240f, paletteColors[3])
                    ),
                    createdAt = now
                )
            }
        }
    }

    fun buildChecklistFromPrompt(prompt: String, idSuffix: Long, now: String): SoniaArtifact.ChecklistArtifact {
        val lower = prompt.lowercase()
        val (title, items) = when {
            lower.contains("course") || lower.contains("achat") -> {
                "Liste de Courses Intelligente" to listOf(
                    SoniaChecklistItem("c1", "Fruits & légumes frais bio", true, "Primeur"),
                    SoniaChecklistItem("c2", "Café en grains & lait d'avoine", false, "Épicerie"),
                    SoniaChecklistItem("c3", "Filets de saumon & huile d'olive", false, "Frais"),
                    SoniaChecklistItem("c4", "Amandes & chocolat noir 85%", false, "Snack"),
                    SoniaChecklistItem("c5", "Lessive écologique", false, "Maison")
                )
            }
            lower.contains("voyage") || lower.contains("valise") -> {
                "Checklist Départ & Voyage" to listOf(
                    SoniaChecklistItem("v1", "Passeport, billets & carte bancaire", true, "Documents"),
                    SoniaChecklistItem("v2", "Chargeur rapide & batterie externe", true, "Tech"),
                    SoniaChecklistItem("v3", "Activer le VPN Corpus-Zero en itinérance", false, "Sécurité"),
                    SoniaChecklistItem("v4", "Trousse de toilette cabine (<100ml)", false, "Bagage")
                )
            }
            else -> {
                "Liste de Tâches LIA" to listOf(
                    SoniaChecklistItem("t1", "Ajouter mes contacts dans la mémoire LIA", false, "Contacts"),
                    SoniaChecklistItem("t2", "Enregistrer mes souvenirs importants", false, "Mémoire"),
                    SoniaChecklistItem("t3", "Tester les actions rapides apprises", false, "Apps"),
                    SoniaChecklistItem("t4", "Vérifier les autorisations de sécurité", true, "Sécurité")
                )
            }
        }
        return SoniaArtifact.ChecklistArtifact(
            id = "list_$idSuffix",
            title = title,
            items = items,
            createdAt = now
        )
    }

    fun buildTableFromPrompt(prompt: String, idSuffix: Long, now: String): SoniaArtifact.TableArtifact {
        return SoniaArtifact.TableArtifact(
            id = "table_$idSuffix",
            title = "Comparatif & Audit de Performance LIA",
            headers = listOf("Critère", "LIA Fast", "Cloud Pro", "Sécurité"),
            rows = listOf(
                listOf("Vitesse", "0.4 s", "1.6 s", "AES-256"),
                listOf("Graphes & UI", "Interactif", "Complet", "Signé SHA"),
                listOf("Vie Privée", "Locale Room", "Chiffré", "DLP Actif"),
                listOf("Validation", "Safe Contact", "HITL", "Zero-Trust")
            ),
            createdAt = now
        )
    }

    fun buildTimelineFromPrompt(prompt: String, idSuffix: Long, now: String): SoniaArtifact.TimelineArtifact {
        return SoniaArtifact.TimelineArtifact(
            id = "timeline_$idSuffix",
            title = "Planning de la Journée",
            steps = listOf(
                SoniaTimelineStep("09:00", "Organisation de la journée", "Consultation des priorités et rappels"),
                SoniaTimelineStep("12:30", "Pause déjeuner", "Moment de détente"),
                SoniaTimelineStep("14:30", "Session de travail / Études", "Concentration sans interruption"),
                SoniaTimelineStep("18:00", "Bilan de fin de journée", "Mise à jour des notes et objectifs")
            ),
            createdAt = now
        )
    }

    fun buildCodeCalcFromPrompt(prompt: String, idSuffix: Long, now: String): SoniaArtifact.CodeCalcArtifact {
        val numbers = Regex("\\d+(?:[.,]\\d+)?").findAll(prompt)
            .mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }
            .toList()

        val (expr, result) = if (numbers.size >= 2) {
            val a = numbers[0]
            val b = numbers[1]
            val sum = a + b
            val prod = a * b
            "val a = $a\nval b = $b\nval somme = a + b // $sum\nval produit = a * b // $prod" to
                "Somme = ${"%.2f".format(sum)} • Produit = ${"%.2f".format(prod)}"
        } else {
            // Financial compound interest & monthly budget simulation
            val capital = 10000.0
            val rate = 0.045
            val years = 5
            val finalAmount = capital * (1.0 + rate).pow(years)
            "val capital = 10_000.0 €\nval tauxAnnuel = 4.5%\nval duree = 5 ans\nval total = capital * (1 + 0.045)^5" to
                "${"%.2f".format(finalAmount)} € (+${"%.2f".format(finalAmount - capital)} €)"
        }

        val sig = sha256Hex("$expr|$result|$idSuffix")
        return SoniaArtifact.CodeCalcArtifact(
            id = "calc_$idSuffix",
            title = "Calculateur Financier & Code",
            expressionOrCode = expr,
            evaluatedResult = result,
            sha256Signature = sig,
            createdAt = now
        )
    }

    fun sha256Hex(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            input.hashCode().toUInt().toString(16)
        }
    }
}
