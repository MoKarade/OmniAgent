package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    GET,
    LIST,
    WRITE
}

fun handleFirestoreError(
    error: Throwable,
    operationType: OperationType,
    path: String?,
    rethrow: Boolean = true
) {
    if (error is CancellationException) {
        throw error
    }

    val currentUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
    val maskedUid = currentUser?.uid?.let { uid ->
        if (uid.length > 6) "${uid.take(3)}***${uid.takeLast(3)}" else "***"
    }.orEmpty()

    val errorInfo = buildString {
        append("{")
        append("\"error\": \"${error.message?.replace("\"", "\\\"")}\", ")
        append("\"operationType\": \"${operationType.name}\", ")
        append("\"path\": \"${path ?: ""}\", ")
        append("\"authInfo\": {")
        append("\"userId\": \"$maskedUid\", ")
        append("\"emailVerified\": ${currentUser?.isEmailVerified ?: false}, ")
        append("\"isAnonymous\": ${currentUser?.isAnonymous ?: false}")
        append("}")
        append("}")
    }

    Log.e("FirestoreError", errorInfo, error)
    if (rethrow) {
        throw RuntimeException(errorInfo, error)
    }
}
