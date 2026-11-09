package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.model.GlobalAccessPolicyMode
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

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

enum class ActionTargetSafetyVerdict {
    SAFE_APP_LAUNCH,
    REQUIRES_USER_CONFIRMATION,
    BLOCKED_UNSAFE_TARGET
}

/**
 * Noyau de sécurité Zero-Trust de LIA :
 * 1. Chiffrement matériel AES-256-GCM via AndroidKeyStore (encrypt + decrypt réels).
 * 2. Code PIN personnel haché avec PBKDF2WithHmacSHA256 (12 000 itérations + sel aléatoire 16 octets).
 * 3. Pare-feu DLP anti-injection (FR/EN), masquage IBAN et carte bancaire validée par algorithme de Luhn.
 * 4. Assainissement des souvenirs RAG externes et validation par liste blanche des cibles [ACTION:...].
 * 5. Chaîne d'audit SHA-256 à indexation strictement monotone.
 */
object SoniaSecurityKernel {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "lia_zero_trust_aes256_master_key"
    private const val ENCRYPTED_PREFIX = "ENCv1:"
    private const val GCM_TAG_LENGTH_BITS = 128

    private const val VAULT_PREFS_NAME = "lia_security_vault_prefs"
    private const val KEY_PIN_SALT = "pin_salt_hex"
    private const val KEY_PIN_HASH = "pin_hash_hex"
    private const val KEY_VAULT_LOCKED = "is_vault_locked"
    private const val KEY_GLOBAL_POLICY = "global_access_policy"
    private const val PBKDF2_ITERATIONS = 12_000
    private const val PBKDF2_KEY_BITS = 256

    private val auditBlockCounter = AtomicInteger(0)

    @Volatile
    var isVaultLockedRuntime: Boolean = false
        private set

    @Volatile
    var globalPolicyRuntime: GlobalAccessPolicyMode = GlobalAccessPolicyMode.FULL_AUTONOMOUS_ACCESS
        private set

    private val injectionPatterns = listOf(
        Regex("ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions", RegexOption.IGNORE_CASE),
        Regex("ignore[rz]?\\s+(toutes\\s+)?les\\s+(instructions|consignes)\\s+pr[eé]c[eé]dentes", RegexOption.IGNORE_CASE),
        Regex("oublie\\s+(toutes\\s+)?tes\\s+(r[eè]gles|instructions|consignes)", RegexOption.IGNORE_CASE),
        Regex("system\\s*override", RegexOption.IGNORE_CASE),
        Regex("virement\\s+imm[eé]diat\\s+vers", RegexOption.IGNORE_CASE),
        Regex("transf[eé]rer\\s+tout\\s+le\\s+solde", RegexOption.IGNORE_CASE),
        Regex("\\[ACTION\\s*:[^\\]]+\\]", RegexOption.IGNORE_CASE),
        Regex("rm\\s+-rf", RegexOption.IGNORE_CASE),
        Regex("<script\\b[^>]*>", RegexOption.IGNORE_CASE)
    )

    private val embeddedActionTagRegex = Regex("\\[ACTION\\s*:[^\\]]*\\]", RegexOption.IGNORE_CASE)
    private val ibanRegex = Regex("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b")
    private val cardCandidateRegex = Regex("\\b(?:\\d[ -]*?){13,19}\\b")
    private val packageNameRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
    private val safeAppLabelRegex = Regex("^[\\p{L}0-9 _.-]{1,60}$")

    fun initVaultState(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(VAULT_PREFS_NAME, Context.MODE_PRIVATE)
        val hasPin = !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank()
        isVaultLockedRuntime = hasPin && prefs.getBoolean(KEY_VAULT_LOCKED, false)
        val savedPolicy = prefs.getString(KEY_GLOBAL_POLICY, null)
        globalPolicyRuntime = GlobalAccessPolicyMode.entries.find { it.name == savedPolicy }
            ?: GlobalAccessPolicyMode.FULL_AUTONOMOUS_ACCESS
    }

    fun isUserPinConfigured(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(VAULT_PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank() &&
            !prefs.getString(KEY_PIN_SALT, null).isNullOrBlank()
    }

    fun setOrUpdateUserPin(context: Context, newPin: String): Boolean {
        val trimmed = newPin.trim()
        if (trimmed.length < 4 || trimmed.length > 12 || !trimmed.all { it.isDigit() }) {
            return false
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hashHex = hashPinWithSalt(trimmed, salt)
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        context.applicationContext
            .getSharedPreferences(VAULT_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PIN_SALT, saltHex)
            .putString(KEY_PIN_HASH, hashHex)
            .putBoolean(KEY_VAULT_LOCKED, false)
            .apply()
        isVaultLockedRuntime = false
        return true
    }

    fun verifyUserPin(context: Context, candidatePin: String): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(VAULT_PREFS_NAME, Context.MODE_PRIVATE)
        val saltHex = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val expectedHashHex = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = hexToBytes(saltHex) ?: return false
        val candidateHashHex = hashPinWithSalt(candidatePin.trim(), salt)
        val matched = MessageDigest.isEqual(
            expectedHashHex.toByteArray(Charsets.UTF_8),
            candidateHashHex.toByteArray(Charsets.UTF_8)
        )
        if (matched) {
            isVaultLockedRuntime = false
            prefs.edit().putBoolean(KEY_VAULT_LOCKED, false).apply()
        }
        return matched
    }

    fun lockVault(context: Context): Boolean {
        if (!isUserPinConfigured(context)) return false
        isVaultLockedRuntime = true
        context.applicationContext
            .getSharedPreferences(VAULT_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VAULT_LOCKED, true)
            .apply()
        return true
    }

    fun unlockVault(context: Context) {
        isVaultLockedRuntime = false
        context.applicationContext
            .getSharedPreferences(VAULT_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VAULT_LOCKED, false)
            .apply()
    }

    fun unlockVaultRuntime() {
        isVaultLockedRuntime = false
    }

    fun getAuditBlockCount(): Int = auditBlockCounter.get()

    fun clearUserPin(context: Context) {
        isVaultLockedRuntime = false
        context.applicationContext
            .getSharedPreferences(VAULT_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_VAULT_LOCKED, false)
            .apply()
    }

    fun updateGlobalPolicy(context: Context, mode: GlobalAccessPolicyMode) {
        globalPolicyRuntime = mode
        context.applicationContext
            .getSharedPreferences(VAULT_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GLOBAL_POLICY, mode.name)
            .apply()
    }

    fun updateGlobalPolicy(mode: GlobalAccessPolicyMode) {
        globalPolicyRuntime = mode
        LiaMemoryAndHistoryStore.getApplicationContextOrNull()?.let { ctx ->
            updateGlobalPolicy(ctx, mode)
        }
    }

    fun hashPinWithSalt(pin: String, salt: ByteArray): String {
        return try {
            val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val bytes = factory.generateSecret(spec).encoded
            spec.clearPassword()
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            val salted = salt + pin.toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256").digest(salted)
            digest.joinToString("") { "%02x".format(it) }
        }
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Retourne true uniquement si AndroidKeyStore est réellement opérationnel sur l'appareil.
     */
    fun isHardwareKeystoreReady(): Boolean {
        return try {
            getOrCreateHardwareAesKey() != null
        } catch (_: Exception) {
            false
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

    /**
     * Chiffre une valeur sensible en AES-256-GCM via AndroidKeyStore.
     * Retourne null si le Keystore matériel n'est pas disponible (ne retourne jamais un hash irréversible).
     */
    fun encryptSensitivePayload(plainText: String): String? {
        if (plainText.isBlank()) return ""
        if (plainText.startsWith(ENCRYPTED_PREFIX)) return plainText
        return try {
            val secretKey = getOrCreateHardwareAesKey() ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
            ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Déchiffre une valeur préalablement chiffrée par [encryptSensitivePayload].
     * Si la chaîne ne commence pas par `ENCv1:`, elle est retournée telle quelle pour permettre la migration
     * transparente des anciennes valeurs stockées en clair.
     */
    fun decryptSensitivePayload(storedPayload: String): String? {
        if (storedPayload.isBlank()) return ""
        if (!storedPayload.startsWith(ENCRYPTED_PREFIX)) return storedPayload
        return try {
            val secretKey = getOrCreateHardwareAesKey() ?: return null
            val encoded = storedPayload.removePrefix(ENCRYPTED_PREFIX)
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < 2) return null
            val ivLength = combined[0].toInt() and 0xFF
            if (ivLength <= 0 || combined.size <= 1 + ivLength) return null
            val iv = combined.copyOfRange(1, 1 + ivLength)
            val cipherBytes = combined.copyOfRange(1 + ivLength, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Vérifie si une séquence de 13 à 19 chiffres correspond à un vrai numéro de carte bancaire selon l'algorithme de Luhn.
     */
    fun isValidLuhnCard(rawCandidate: String): Boolean {
        val digits = rawCandidate.filter { it.isDigit() }
        if (digits.length !in 13..19) return false
        if (digits.all { it == digits[0] }) return false
        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }

    fun inspectAndSanitize(input: String, maskPii: Boolean = true): DlpScanResult {
        val threats = mutableListOf<String>()
        var sanitized = input

        for (pattern in injectionPatterns) {
            if (pattern.containsMatchIn(sanitized)) {
                threats.add("Injection de prompt neutralisée")
                sanitized = pattern.replace(sanitized, "[BLOQUÉ PAR PARE-FEU LIA]")
            }
        }

        if (maskPii) {
            if (ibanRegex.containsMatchIn(sanitized)) {
                sanitized = ibanRegex.replace(sanitized) { m ->
                    "${m.value.take(4)}••••••••${m.value.takeLast(4)}"
                }
            }
            if (cardCandidateRegex.containsMatchIn(sanitized)) {
                sanitized = cardCandidateRegex.replace(sanitized) { match ->
                    if (isValidLuhnCard(match.value)) {
                        "•••• •••• •••• ${match.value.filter { it.isDigit() }.takeLast(4)}"
                    } else {
                        match.value
                    }
                }
            }
        }

        return DlpScanResult(
            isSafe = threats.isEmpty(),
            sanitizedText = sanitized,
            detectedThreats = threats
        )
    }

    /**
     * Assainit un souvenir externe (ex: extrait d'e-mail Gmail ou événement Calendar) avant son injection
     * dans le prompt du LLM pour empêcher toute injection indirecte via `[ACTION:...]`.
     */
    fun sanitizeExternalRagContent(rawText: String): String {
        val withoutActionTags = embeddedActionTagRegex.replace(rawText, "[balise-action-retirée]")
        return inspectAndSanitize(withoutActionTags, maskPii = false).sanitizedText
    }

    /**
     * Valide la cible d'une action système ou d'une balise `[ACTION:cible:libellé]` contre une liste blanche stricte.
     */
    fun validateActionTargetSafety(rawTarget: String): ActionTargetSafetyVerdict {
        val trimmed = rawTarget.trim()
        if (trimmed.isBlank() || trimmed.length > 240) {
            return ActionTargetSafetyVerdict.BLOCKED_UNSAFE_TARGET
        }
        if (trimmed.any { it == ';' || it == '|' || it == '`' || it == '\n' || it == '\r' } ||
            trimmed.contains("&&") || trimmed.contains("$(")
        ) {
            return ActionTargetSafetyVerdict.BLOCKED_UNSAFE_TARGET
        }

        val lower = trimmed.lowercase()
        val blockedPrefixes = listOf(
            "javascript:",
            "file:",
            "content:",
            "intent:",
            "data:",
            "vbscript:",
            "blob:",
            "market://details?id=com.aistudio"
        )
        if (blockedPrefixes.any { lower.startsWith(it) }) {
            return ActionTargetSafetyVerdict.BLOCKED_UNSAFE_TARGET
        }

        val confirmationPrefixes = listOf(
            "tel:",
            "smsto:",
            "sms:",
            "mailto:",
            "https://",
            "http://",
            "geo:",
            "google.navigation:"
        )
        if (confirmationPrefixes.any { lower.startsWith(it) }) {
            return ActionTargetSafetyVerdict.REQUIRES_USER_CONFIRMATION
        }

        if (packageNameRegex.matches(trimmed) || safeAppLabelRegex.matches(trimmed)) {
            return ActionTargetSafetyVerdict.SAFE_APP_LAUNCH
        }

        return ActionTargetSafetyVerdict.BLOCKED_UNSAFE_TARGET
    }

    fun nextMonotonicAuditBlock(
        timestampFormatted: String,
        agentName: String,
        toolName: String,
        actionSummary: String,
        granted: Boolean,
        previousHash: String
    ): ChainedSecurityAuditBlock {
        val idx = auditBlockCounter.incrementAndGet()
        return createAuditBlock(
            index = idx,
            timestampFormatted = timestampFormatted,
            agentName = agentName,
            toolName = toolName,
            actionSummary = actionSummary,
            granted = granted,
            previousHash = previousHash
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
        auditBlockCounter.set(0)
        val b1 = nextMonotonicAuditBlock(
            timestampFormatted = "Initialisation",
            agentName = "Noyau LIA",
            toolName = "AES-256-GCM",
            actionSummary = "Protection AndroidKeyStore & PBKDF2 initialisée",
            granted = true,
            previousHash = "0000000000000000"
        )
        val b2 = nextMonotonicAuditBlock(
            timestampFormatted = "Initialisation",
            agentName = "Pare-feu DLP",
            toolName = "ANTI-INJECTION",
            actionSummary = "Filtrage anti-injection, Luhn & liste blanche d'actions actifs",
            granted = true,
            previousHash = b1.blockHash
        )
        return listOf(b2, b1)
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
