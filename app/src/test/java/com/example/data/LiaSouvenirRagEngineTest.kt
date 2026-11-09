package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LiaSouvenirRagEngineTest {

    @Test
    fun `64D semantic embedding vector is L2 normalized`() {
        val vec = LiaSouvenirRagEngine.computeEmbeddingVector("Rendez-vous dentiste mardi à 14h30")
        assertEquals(64, vec.size)
        var sumSq = 0f
        for (v in vec) {
            sumSq += v * v
        }
        assertTrue("L2 norm should be ~1.0, was $sumSq", abs(sumSq - 1.0f) < 0.02f)
    }

    @Test
    fun `Cosine similarity ranks semantically related souvenirs higher than unrelated ones`() {
        val vDentist1 = LiaSouvenirRagEngine.computeEmbeddingVector("Mon dentiste est le Dr Martin à Lyon")
        val vDentist2 = LiaSouvenirRagEngine.computeEmbeddingVector("Quel est le nom de mon dentiste à Lyon ?")
        val vSteam = LiaSouvenirRagEngine.computeEmbeddingVector("Dernier jeu lancé sur Steam Counter Strike")

        val simRelated = LiaSouvenirRagEngine.cosineSimilarity(vDentist1, vDentist2)
        val simUnrelated = LiaSouvenirRagEngine.cosineSimilarity(vDentist1, vSteam)

        assertTrue("Related similarity ($simRelated) should exceed unrelated ($simUnrelated)", simRelated > simUnrelated)
    }

    @Test
    fun `Passive souvenir extraction detects personal preferences and ignores simple questions`() {
        val extracted = LiaSouvenirRagEngine.extractPassiveSouvenirsFromUserTurn(
            "J'habite à Bordeaux et j'adore le café italien"
        )
        assertTrue(extracted.isNotEmpty())

        val questionIgnored = LiaSouvenirRagEngine.extractPassiveSouvenirsFromUserTurn(
            "Quelle heure est-il à Paris ?"
        )
        assertTrue(questionIgnored.isEmpty())
    }

    @Test
    fun `RAG search retrieves top matching souvenirs and caches vector representations`() {
        LiaSouvenirRagEngine.clearVectorCache()
        val souvenirs = listOf(
            LiaSouvenirItem(
                id = "souv_1",
                type = "CONTACT",
                subjectKey = "maman_tel",
                title = "Numéro de Maman",
                detailValue = "+33 6 11 22 33 44",
                searchTags = "famille téléphone maman sms",
                confidenceScore = 0.98f,
                usesCount = 5,
                updatedAt = "12:00"
            ),
            LiaSouvenirItem(
                id = "souv_2",
                type = "GOUT",
                subjectKey = "musique_pref",
                title = "Musique préférée",
                detailValue = "Jazz et Lo-Fi sur Spotify",
                searchTags = "spotify musique jazz",
                confidenceScore = 0.90f,
                usesCount = 2,
                updatedAt = "12:05"
            )
        )

        val outcome = LiaSouvenirRagEngine.retrieveRelevantSouvenirs(
            userQuery = "Envoie un SMS à Maman",
            allSouvenirs = souvenirs,
            userFacts = emptyList()
        )
        assertFalse(outcome.retrievedMatches.isEmpty())
        assertEquals("souv_1", outcome.retrievedMatches.first().souvenir.id)
    }
}
