package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiaReinforcementLearningEngineTest {

    @Test
    fun `Q-Learning update increases Q-value on positive reward and respects upper bound`() {
        val initialQ = 0.75f
        val updatedQ = LiaReinforcementLearningEngine.computeUpdatedQValue(
            currentQ = initialQ,
            usesCount = 6,
            reward = 1.0f
        )
        assertTrue("Updated Q ($updatedQ) should be greater than initial Q ($initialQ)", updatedQ > initialQ)
        assertTrue("Updated Q ($updatedQ) should be <= 1.20f", updatedQ <= 1.20f)
    }

    @Test
    fun `rankDailyQuickActions orders shortcuts by Q-value and frequency`() {
        val activeBucket = LiaReinforcementLearningEngine.currentTimeBucket()
        val shortcuts = listOf(
            LiaLearnedShortcut(
                id = "sc_low",
                triggerPhrase = "Ouvre Calculatrice",
                actionTitle = "Calculatrice",
                targetPackageOrScreen = "com.google.android.calculator",
                usesCount = 1,
                stepsSaved = 2,
                avgLatencyMs = 80L,
                qValueScore = 0.55f,
                preferredTimeBucket = "TOUJOURS"
            ),
            LiaLearnedShortcut(
                id = "sc_high",
                triggerPhrase = "Ouvre WhatsApp",
                actionTitle = "WhatsApp",
                targetPackageOrScreen = "com.whatsapp",
                usesCount = 12,
                stepsSaved = 4,
                avgLatencyMs = 45L,
                qValueScore = 0.96f,
                preferredTimeBucket = activeBucket
            )
        )

        val ranked = LiaReinforcementLearningEngine.rankDailyQuickActions(shortcuts)
        assertEquals(2, ranked.size)
        assertEquals("sc_high", ranked.first().shortcutId)
        assertTrue(ranked.first().qValueScore > ranked.last().qValueScore)
    }
}
