package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.AtomicAction
import com.example.model.CognitiveStepResult
import com.example.model.McpCategory
import com.example.model.McpRecord
import com.example.model.SecurityPosture
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OmniFirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var repository: OmniFirestoreRepository

    @Before
    override fun setUp() {
        super.setUp()
        repository = OmniFirestoreRepository(db)
    }

    @Test
    fun saveMcpRecord_whenAuthenticatedOwner_succeedsAndStreamsBack() = runTestWithRealHttp {
        val uid = signInTestUser()
        val record = McpRecord(
            id = "mcp_test_1",
            category = McpCategory.CONTACTS,
            title = "Camille Laurent",
            subtitle = "Mobile Personnel",
            content = "+33 6 42 88 19 04"
        )

        repository.saveMcpRecord(uid, record)

        val records = repository.observeMcpRecords(uid).first { it.isNotEmpty() }
        assertEquals(1, records.size)
        assertEquals("Camille Laurent", records.first().title)
    }

    @Test
    fun saveMcpRecord_whenWritingToAnotherUsersVault_throwsPermissionDenied() = runTestWithRealHttp {
        signInTestUser()
        val record = McpRecord(
            id = "mcp_intrusion",
            category = McpCategory.NOTES,
            title = "Intrusion",
            subtitle = "Test",
            content = "Contenu"
        )

        try {
            repository.saveMcpRecord("other_user_uid", record)
            fail("Expected PERMISSION_DENIED when writing to another user's vault")
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.contains("PERMISSION_DENIED"))
        }
    }

    @Test
    fun saveCognitiveLog_whenAuthenticatedOwner_succeeds() = runTestWithRealHttp {
        val uid = signInTestUser()
        val step = CognitiveStepResult(
            stepIndex = 1,
            observation = "Écran Accueil OmniOS visible",
            thought = "Appel prioritaire à query_personal_data",
            subgoal = "Récupérer le numéro de Camille",
            action = AtomicAction(
                tool = "query_personal_data",
                mcpCategory = "contacts",
                mcpQuery = "Camille"
            ),
            securityPosture = SecurityPosture.SAFE,
            securityNotes = "Conforme",
            rawJson = "{}",
            executionOutcome = "Contact récupéré",
            modelUsed = "gemini-4-argon",
            durationMs = 420L
        )

        repository.saveCognitiveLog(uid, step)

        val logs = repository.observeCognitiveLogs(uid).first { it.isNotEmpty() }
        assertEquals(1, logs.size)
        assertEquals(1, logs.first().stepIndex)
    }
}
