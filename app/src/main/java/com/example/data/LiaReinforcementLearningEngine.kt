package com.example.data

import java.util.Calendar
import kotlin.math.roundToInt

data class LiaQuickActionRecommendation(
    val shortcutId: String,
    val emoji: String,
    val label: String,
    val triggerPhrase: String,
    val targetPackageOrScreen: String,
    val qValueScore: Float,
    val confidencePercent: Int,
    val dailyFrequency: Int,
    val timeBucketMatch: Boolean,
    val latencyMs: Long,
    val habitSummary: String = ""
)

/**
 * Lightweight On-Device Reinforcement Learning Engine (Contextual Bandit / Q-Learning) for LIA.
 *
 * Combines learned shortcuts (`lia_learned_shortcuts`) and background-tracked installed application
 * launch frequencies (`lia_app_usage_frequency` populated by `LiaAppUsageTrackerService`) across
 * contextual time-of-day buckets (`MATIN`, `APRES_MIDI`, `SOIR`) to dynamically surface
 * personalized daily "Actions rapides".
 */
object LiaReinforcementLearningEngine {

    private const val ALPHA_LEARNING_RATE = 0.28f
    private const val GAMMA_FREQUENCY_WEIGHT = 0.15f

    fun currentTimeBucket(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "MATIN"
            in 12..17 -> "APRES_MIDI"
            else -> "SOIR"
        }
    }

    /**
     * Computes the updated Q-value after observing a positive or negative user interaction reward:
     * Q_{t+1}(a) = Q_t(a) + alpha * (reward + gamma * min(uses / 12, 1.0) - Q_t(a))
     */
    fun computeUpdatedQValue(
        currentQ: Float,
        usesCount: Int,
        reward: Float = 1.0f
    ): Float {
        val frequencyBonus = (usesCount.coerceAtMost(12) / 12f) * GAMMA_FREQUENCY_WEIGHT
        val target = (reward + frequencyBonus).coerceIn(0.1f, 1.25f)
        val nextQ = currentQ + ALPHA_LEARNING_RATE * (target - currentQ)
        return nextQ.coerceIn(0.1f, 1.20f)
    }

    /**
     * Ranks all learned shortcuts and background-tracked installed app usage habits using their
     * RL Q-value, daily access frequency, and current time-of-day context to surface the best
     * dynamic "Actions rapides" for the user.
     */
    fun rankDailyQuickActions(
        shortcuts: List<LiaLearnedShortcut>,
        appUsageHabits: List<LiaAppUsageHabitItem> = emptyList()
    ): List<LiaQuickActionRecommendation> {
        val activeBucket = currentTimeBucket()
        val habitsByPkg = appUsageHabits.associateBy { it.packageName.lowercase() }

        val fromShortcuts = shortcuts.map { sc ->
            val matchedHabit = habitsByPkg[sc.targetPackageOrScreen.lowercase()]
            val totalFreq = maxOf(sc.usesCount, matchedHabit?.launchCount ?: 0)
            val bucketCount = when (activeBucket) {
                "MATIN" -> matchedHabit?.morningCount ?: 0
                "APRES_MIDI" -> matchedHabit?.afternoonCount ?: 0
                else -> matchedHabit?.eveningCount ?: 0
            }
            val timeMatch = sc.preferredTimeBucket.equals(activeBucket, ignoreCase = true) ||
                sc.preferredTimeBucket == "TOUJOURS" ||
                bucketCount > 0

            val contextualBoost = if (timeMatch) 0.08f else 0f
            val freqBoost = (totalFreq.coerceAtMost(15) * 0.025f)
            val effectiveQ = (maxOf(sc.qValueScore, matchedHabit?.qValueHabitScore ?: 0f) + contextualBoost + freqBoost)
                .coerceIn(0.1f, 1.25f)
            val confidencePct = ((effectiveQ / 1.25f) * 100f).roundToInt().coerceIn(55, 99)

            val emoji = resolveEmojiForTarget(sc.triggerPhrase, sc.targetPackageOrScreen)

            LiaQuickActionRecommendation(
                shortcutId = sc.id,
                emoji = emoji,
                label = sc.triggerPhrase,
                triggerPhrase = sc.triggerPhrase,
                targetPackageOrScreen = sc.targetPackageOrScreen,
                qValueScore = effectiveQ,
                confidencePercent = confidencePct,
                dailyFrequency = totalFreq,
                timeBucketMatch = timeMatch,
                latencyMs = sc.avgLatencyMs,
                habitSummary = "${totalFreq}x ouvert • Créneau $activeBucket"
            )
        }

        // Include any background-tracked installed apps not yet in `fromShortcuts`
        val coveredPackages = fromShortcuts.map { it.targetPackageOrScreen.lowercase() }.toSet()
        val fromBackgroundService = appUsageHabits
            .filter { it.packageName.lowercase() !in coveredPackages && it.launchCount > 0 }
            .map { habit ->
                val bucketHits = when (activeBucket) {
                    "MATIN" -> habit.morningCount
                    "APRES_MIDI" -> habit.afternoonCount
                    else -> habit.eveningCount
                }
                val timeMatch = bucketHits > 0
                val contextualBoost = if (timeMatch) 0.09f else 0f
                val freqBoost = (habit.launchCount.coerceAtMost(15) * 0.025f)
                val effectiveQ = (habit.qValueHabitScore + contextualBoost + freqBoost).coerceIn(0.1f, 1.25f)
                val confidencePct = ((effectiveQ / 1.25f) * 100f).roundToInt().coerceIn(55, 99)

                LiaQuickActionRecommendation(
                    shortcutId = "habit_${habit.packageName}",
                    emoji = resolveEmojiForTarget(habit.appLabel, habit.packageName),
                    label = "Ouvre ${habit.appLabel}",
                    triggerPhrase = "Ouvre ${habit.appLabel}",
                    targetPackageOrScreen = habit.packageName,
                    qValueScore = effectiveQ,
                    confidencePercent = confidencePct,
                    dailyFrequency = habit.launchCount,
                    timeBucketMatch = timeMatch,
                    latencyMs = 55L,
                    habitSummary = "${habit.launchCount}x suivi en arrière-plan ($activeBucket)"
                )
            }

        return (fromShortcuts + fromBackgroundService).sortedWith(
            compareByDescending<LiaQuickActionRecommendation> { it.qValueScore }
                .thenByDescending { it.dailyFrequency }
        )
    }

    private fun resolveEmojiForTarget(label: String, pkg: String): String {
        val combined = "$label $pkg".lowercase()
        return when {
            combined.contains("maman") || combined.contains("sms") || combined.contains("messag") -> "💬"
            combined.contains("youtube") || combined.contains("video") -> "▶️"
            combined.contains("gmail") || combined.contains("mail") -> "📧"
            combined.contains("vpn") || combined.contains("sécurité") || combined.contains("settings") || combined.contains("param") -> "🛡️"
            combined.contains("banque") || combined.contains("virement") -> "🏦"
            combined.contains("chrome") || combined.contains("browser") -> "🌐"
            combined.contains("note") || combined.contains("keep") -> "📝"
            combined.contains("calendar") || combined.contains("agenda") -> "📅"
            combined.contains("photo") || combined.contains("camera") -> "📷"
            else -> "⚡"
        }
    }
}
