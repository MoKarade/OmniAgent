package com.example.service

import com.example.data.LiaLocalUtilitiesEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentHandlerAndUtilitiesTest {

    @Test
    fun `IntentHandler classifies flashlight and camera commands deterministically`() {
        val torchOn = IntentHandler.classifyAndExtractKnownIntent("allume la lampe torche", null)
        assertNotNull(torchOn)
        assertEquals(LiaClassifiedIntentType.TOGGLE_FLASHLIGHT, torchOn?.intentType)
        assertEquals("true", torchOn?.extractedParameters?.get("enable"))

        val torchOff = IntentHandler.classifyAndExtractKnownIntent("éteins la lampe torche", null)
        assertNotNull(torchOff)
        assertEquals(LiaClassifiedIntentType.TOGGLE_FLASHLIGHT, torchOff?.intentType)
        assertEquals("false", torchOff?.extractedParameters?.get("enable"))

        val cameraSelfie = IntentHandler.classifyAndExtractKnownIntent("prends un selfie", null)
        assertNotNull(cameraSelfie)
        assertEquals(LiaClassifiedIntentType.OPEN_CAMERA, cameraSelfie?.intentType)
    }

    @Test
    fun `IntentHandler classifies timer alarm and navigation intents`() {
        val timerIntent = IntentHandler.classifyAndExtractKnownIntent("mets un minuteur de 10 minutes", null)
        assertNotNull(timerIntent)
        assertEquals(LiaClassifiedIntentType.SET_TIMER, timerIntent?.intentType)
        assertEquals("600", timerIntent?.extractedParameters?.get("seconds"))

        val alarmIntent = IntentHandler.classifyAndExtractKnownIntent("mets un réveil à 7h30", null)
        assertNotNull(alarmIntent)
        assertEquals(LiaClassifiedIntentType.SET_ALARM, alarmIntent?.intentType)
        assertEquals("7", alarmIntent?.extractedParameters?.get("hour"))
        assertEquals("30", alarmIntent?.extractedParameters?.get("minutes"))

        val mapsIntent = IntentHandler.classifyAndExtractKnownIntent("itinéraire vers Lyon", null)
        assertNotNull(mapsIntent)
        assertEquals(LiaClassifiedIntentType.MAPS_NAVIGATION, mapsIntent?.intentType)
        assertTrue(mapsIntent?.deepLinkUri?.contains("Lyon", ignoreCase = true) == true)
    }

    @Test
    fun `LiaLocalUtilitiesEngine evaluates math expressions and operator precedence`() {
        val res1 = LiaLocalUtilitiesEngine.evaluateSimpleMathExpression("10 + 5 * 2")
        assertNotNull(res1)
        assertEquals(20.0, res1!!, 0.001)

        val res2 = LiaLocalUtilitiesEngine.evaluateSimpleMathExpression("(10 + 5) * 2")
        assertNotNull(res2)
        assertEquals(30.0, res2!!, 0.001)

        val divByZero = LiaLocalUtilitiesEngine.evaluateSimpleMathExpression("15 / 0")
        assertNull(divByZero)
    }

    @Test
    fun `LiaLocalUtilitiesEngine handles unit conversions percentages and bill splitting`() {
        val pctOutcome = LiaLocalUtilitiesEngine.checkInstantUtilityIntent("Calcule 20% de 150€")
        assertNotNull(pctOutcome)
        assertTrue(pctOutcome!!.replyText.contains("30€"))
        assertTrue(pctOutcome.replyText.contains("120€"))

        val splitOutcome = LiaLocalUtilitiesEngine.checkInstantUtilityIntent("Partage 120€ en 4")
        assertNotNull(splitOutcome)
        assertTrue(splitOutcome!!.replyText.contains("30€"))

        val convPair = LiaLocalUtilitiesEngine.computeUnitOrCurrencyConversion(100.0, "km", "miles")
        assertNotNull(convPair)
        assertTrue(convPair!!.first.contains("62,14") || convPair.first.contains("62.14"))
    }
}
