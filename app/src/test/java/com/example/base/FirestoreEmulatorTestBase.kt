package com.example.base

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLog

abstract class FirestoreEmulatorTestBase {

    protected lateinit var db: FirebaseFirestore
    protected lateinit var auth: FirebaseAuth
    protected lateinit var databaseId: String

    companion object {
        private const val PROJECT_ID = "demo-test-project"
        private const val FIRESTORE_HOST = "127.0.0.1"
        private const val FIRESTORE_PORT = 8085
        private const val AUTH_HOST = "127.0.0.1"
        private const val AUTH_PORT = 9099
    }

    @Before
    open fun setUp() {
        ShadowLog.stream = System.out
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseId = context.getString(R.string.firestore_database_id)

        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setProjectId(PROJECT_ID)
                .setApplicationId("1:1234567890:android:abcdef")
                .setApiKey("fake-api-key-for-emulator")
                .build()
            FirebaseApp.initializeApp(context, options)
        }

        auth = FirebaseAuth.getInstance()
        try {
            auth.useEmulator(AUTH_HOST, AUTH_PORT)
        } catch (_: IllegalStateException) {
        }

        db = FirebaseFirestore.getInstance(databaseId)
        try {
            db.useEmulator(FIRESTORE_HOST, FIRESTORE_PORT)
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            db.firestoreSettings = settings
        } catch (_: IllegalStateException) {
        }

        clearFirestoreEmulatorData()
        auth.signOut()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    open fun tearDown() {
        try {
            runTestWithRealHttp {
                db.terminate().await()
            }
        } catch (_: Exception) {
        }
        FirebaseApp.clearInstancesForTest()
    }

    protected fun runTestWithRealHttp(block: suspend () -> Unit) = runBlocking {
        withTimeout(30_000) {
            val deferred = async(Dispatchers.IO) {
                block()
            }
            while (deferred.isActive) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(15)
            }
            deferred.await()
        }
    }

    protected suspend fun signInTestUser(
        email: String = "user_${UUID.randomUUID()}@example.com",
        password: String = "password123"
    ): String {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user!!.uid
        } catch (_: Exception) {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user!!.uid
        }
    }

    private fun clearFirestoreEmulatorData() {
        val url = URL(
            "http://$FIRESTORE_HOST:$FIRESTORE_PORT/emulator/v1/projects/$PROJECT_ID/databases/$databaseId/documents"
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "DELETE"
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }
}
