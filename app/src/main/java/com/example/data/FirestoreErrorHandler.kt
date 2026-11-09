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
    path: String?
) {
    // Never log or wrap coroutine cancellations as Firestore errors
    if (error is CancellationException) {
        throw error
    }

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val errorInfo = buildString {
        append("{")
        append("\"error\": \"${error.message?.replace("\"", "\\\"")}\", ")
        append("\"operationType\": \"${operationType.name}\", ")
        append("\"path\": \"${path ?: ""}\", ")
        append("\"authInfo\": {")
        append("\"userId\": \"${currentUser?.uid ?: ""}\", ")
        append("\"email\": \"${currentUser?.email ?: ""}\", ")
        append("\"emailVerified\": ${currentUser?.isEmailVerified ?: false}, ")
        append("\"isAnonymous\": ${currentUser?.isAnonymous ?: false}")
        append("}")
        append("}")
    }

    Log.e("FirestoreError", errorInfo, error)
    throw RuntimeException(errorInfo, error)
}
