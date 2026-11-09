package com.example.data

import com.example.model.AtomicAction
import com.example.model.CognitiveStepResult
import com.example.model.McpCategory
import com.example.model.McpRecord
import com.example.model.SecurityPosture
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Firestore POJO for `/users/{userId}/mcp_records/{recordId}`.
 * Uses default values for no-arg constructor deserialization and @DocumentId / @ServerTimestamp.
 */
data class FirestoreMcpRecord(
    @DocumentId
    val id: String = "",
    val category: String = McpCategory.CONTACTS.name,
    val title: String = "",
    val subtitle: String = "",
    val content: String = "",
    val userId: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    fun toDomain(): McpRecord {
        val resolvedCategory = McpCategory.entries.find {
            it.name.equals(category, ignoreCase = true) || it.mcpKey.equals(category, ignoreCase = true)
        } ?: McpCategory.NOTES
        return McpRecord(
            id = id.ifBlank { "mcp_${System.currentTimeMillis()}" },
            category = resolvedCategory,
            title = title,
            subtitle = subtitle,
            content = content
        )
    }
}

/**
 * Firestore POJO for `/users/{userId}/cognitive_logs/{logId}`.
 */
data class FirestoreCognitiveLog(
    @DocumentId
    val id: String = "",
    val stepIndex: Int = 1,
    val observation: String = "",
    val thought: String = "",
    val subgoal: String = "",
    val formattedAction: String = "",
    val securityPosture: String = "SAFE",
    val executionOutcome: String = "",
    val userId: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    fun toDomainStep(): CognitiveStepResult {
        val posture = when (securityPosture) {
            "HUMAN_CONFIRMATION_REQUIRED" -> SecurityPosture.HUMAN_CONFIRMATION_REQUIRED
            "INJECTION_NEUTRALIZED" -> SecurityPosture.INJECTION_NEUTRALIZED
            "LOOP_RECOVERY" -> SecurityPosture.LOOP_RECOVERY
            else -> SecurityPosture.SAFE
        }
        val toolName = formattedAction.substringBefore("(").ifBlank { "wait" }
        return CognitiveStepResult(
            stepIndex = stepIndex,
            observation = observation,
            thought = thought,
            subgoal = subgoal,
            action = AtomicAction(
                tool = toolName,
                description = formattedAction
            ),
            securityPosture = posture,
            securityNotes = "Synchronisé depuis Cloud Firestore",
            rawJson = "{\n  \"observation\": \"${observation.replace("\"", "\\\"")}\",\n  \"thought\": \"${thought.replace("\"", "\\\"")}\",\n  \"subgoal\": \"${subgoal.replace("\"", "\\\"")}\",\n  \"action\": \"${formattedAction.replace("\"", "\\\"")}\"\n}",
            executionOutcome = executionOutcome,
            modelUsed = "gemini-4-argon (Cloud Sync)",
            durationMs = 0L
        )
    }
}

class OmniFirestoreRepository(
    private val db: FirebaseFirestore
) {
    fun observeMcpRecords(userId: String): Flow<List<McpRecord>> {
        val path = "users/$userId/mcp_records"
        return db.collection("users")
            .document(userId)
            .collection("mcp_records")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(FirestoreMcpRecord::class.java).map { it.toDomain() }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, path)
            }
    }

    fun observeCognitiveLogs(userId: String): Flow<List<FirestoreCognitiveLog>> {
        val path = "users/$userId/cognitive_logs"
        return db.collection("users")
            .document(userId)
            .collection("cognitive_logs")
            .orderBy("stepIndex", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(FirestoreCognitiveLog::class.java)
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, path)
            }
    }

    /**
     * Atomically seeds default MCP records in a single Firestore WriteBatch so snapshot listeners
     * receive one consolidated update rather than 6 individual round-trips.
     */
    suspend fun seedDefaultMcpRecordsBatch(userId: String, records: List<McpRecord>) {
        if (records.isEmpty()) return
        val path = "users/$userId/mcp_records"
        try {
            val batch = db.batch()
            val colRef = db.collection("users").document(userId).collection("mcp_records")
            records.forEach { record ->
                val safeDocId = record.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64).ifBlank {
                    "mcp_${System.currentTimeMillis()}"
                }
                val docModel = FirestoreMcpRecord(
                    id = safeDocId,
                    category = record.category.name.take(40),
                    title = record.title.take(120).ifBlank { "Titre" },
                    subtitle = record.subtitle.take(160).ifBlank { record.category.displayName },
                    content = record.content.take(2000).ifBlank { "-" },
                    userId = userId,
                    createdAt = null
                )
                batch.set(colRef.document(safeDocId), docModel)
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    suspend fun saveMcpRecord(userId: String, record: McpRecord) {
        val safeDocId = record.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64).ifBlank {
            "mcp_${System.currentTimeMillis()}"
        }
        val path = "users/$userId/mcp_records/$safeDocId"
        val docModel = FirestoreMcpRecord(
            id = safeDocId,
            category = record.category.name.take(40),
            title = record.title.take(120).ifBlank { "Titre" },
            subtitle = record.subtitle.take(160).ifBlank { record.category.displayName },
            content = record.content.take(2000).ifBlank { "-" },
            userId = userId,
            createdAt = null // Populated by @ServerTimestamp on write
        )
        try {
            db.collection("users")
                .document(userId)
                .collection("mcp_records")
                .document(safeDocId)
                .set(docModel)
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    suspend fun deleteMcpRecord(userId: String, recordId: String) {
        val safeDocId = recordId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64)
        val path = "users/$userId/mcp_records/$safeDocId"
        try {
            db.collection("users")
                .document(userId)
                .collection("mcp_records")
                .document(safeDocId)
                .delete()
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
        }
    }

    suspend fun saveCognitiveLog(userId: String, step: CognitiveStepResult) {
        val safeDocId = "step_${step.stepIndex}_${System.currentTimeMillis()}"
        val path = "users/$userId/cognitive_logs/$safeDocId"
        val logModel = FirestoreCognitiveLog(
            id = safeDocId,
            stepIndex = step.stepIndex.coerceIn(1, 10000),
            observation = step.observation.take(2000).ifBlank { "Observation écran" },
            thought = step.thought.take(4000).ifBlank { "Analyse cognitive" },
            subgoal = step.subgoal.take(1000).ifBlank { "Sous-objectif" },
            formattedAction = step.action.toFormattedCall().take(1000).ifBlank { "wait(seconds=1)" },
            securityPosture = step.securityPosture.name.take(80),
            executionOutcome = step.executionOutcome.take(2000).ifBlank { "Exécuté" },
            userId = userId,
            createdAt = null
        )
        try {
            db.collection("users")
                .document(userId)
                .collection("cognitive_logs")
                .document(safeDocId)
                .set(logModel)
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    suspend fun clearCognitiveLogs(userId: String) {
        val path = "users/$userId/cognitive_logs"
        try {
            val snap = db.collection("users")
                .document(userId)
                .collection("cognitive_logs")
                .get()
                .await()
            if (!snap.isEmpty) {
                val batch = db.batch()
                for (doc in snap.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
        }
    }
}
