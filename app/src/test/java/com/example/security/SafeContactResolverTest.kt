package com.example.security

import com.example.data.LiaUserFact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeContactResolverTest {

    private val sampleFacts = listOf(
        LiaUserFact(
            key = "maman_tel",
            label = "Numéro Maman",
            value = "+33 6 12 34 56 78",
            updatedAt = "10:00"
        ),
        LiaUserFact(
            key = "papa_tel",
            label = "Numéro Papa",
            value = "06 98 76 54 32",
            updatedAt = "10:00"
        ),
        LiaUserFact(
            key = "dr_martin_tel",
            label = "Numéro Docteur Martin",
            value = "01 42 68 55 00",
            updatedAt = "10:00"
        )
    )

    @Test
    fun `resolveContact matches exact contact name with 100 percent confidence`() {
        val resolution = SafeContactResolver.resolveContact("Maman", sampleFacts)
        assertEquals("Maman", resolution.bestCandidate.contactName)
        assertEquals("+33 6 12 34 56 78", resolution.bestCandidate.phoneNumber)
        assertEquals(100, resolution.bestCandidate.matchScorePercent)
        assertTrue(resolution.bestCandidate.isExactOrHighConfidence)
        assertTrue(resolution.requiresExplicitModalConfirmation)
        assertNull(resolution.warningMessage)
    }

    @Test
    fun `resolveContact matches family alias mere to Maman`() {
        val resolution = SafeContactResolver.resolveContact("mère", sampleFacts)
        assertEquals("Maman", resolution.bestCandidate.contactName)
        assertEquals("+33 6 12 34 56 78", resolution.bestCandidate.phoneNumber)
        assertTrue(resolution.bestCandidate.matchScorePercent >= 96)
        assertTrue(resolution.bestCandidate.isExactOrHighConfidence)
    }

    @Test
    fun `resolveContact handles direct phone number input`() {
        val resolution = SafeContactResolver.resolveContact("+33 7 11 22 33 44", sampleFacts)
        assertEquals("direct_phone", resolution.bestCandidate.contactKey)
        assertEquals("+33 7 11 22 33 44", resolution.bestCandidate.phoneNumber)
        assertEquals(100, resolution.bestCandidate.matchScorePercent)
        assertTrue(resolution.requiresExplicitModalConfirmation)
    }

    @Test
    fun `resolveContact returns warning and empty phone when contact is unknown`() {
        val resolution = SafeContactResolver.resolveContact("InconnuTotal", sampleFacts)
        assertEquals("", resolution.bestCandidate.phoneNumber)
        assertEquals(0, resolution.bestCandidate.matchScorePercent)
        assertFalse(resolution.bestCandidate.isExactOrHighConfidence)
        assertTrue(resolution.requiresExplicitModalConfirmation)
        assertNotNull(resolution.warningMessage)
    }

    @Test
    fun `computeFuzzySimilarityPercent scores identical and close strings accurately`() {
        assertEquals(100, SafeContactResolver.computeFuzzySimilarityPercent("martin", "martin"))
        val closeScore = SafeContactResolver.computeFuzzySimilarityPercent("martn", "martin")
        assertTrue("Expected close fuzzy score >= 70, got $closeScore", closeScore >= 70)
        val distantScore = SafeContactResolver.computeFuzzySimilarityPercent("xyz", "martin")
        assertTrue("Expected distant score < 30, got $distantScore", distantScore < 30)
    }
}
