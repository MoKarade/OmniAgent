package com.example

import android.app.Application
import android.util.Log
import com.example.data.GeminiDualEngineClient
import com.example.data.LiaMemoryAndHistoryStore
import com.example.security.configureAppCheck

class LiaApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("LiaApplication", "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
        }

        // Initialize Firebase with fallback options so FirebaseAuth/Firestore never throw on startup
        runCatching { configureAppCheck(this, null) }

        // Initialize Core Singletons early
        runCatching { GeminiDualEngineClient.init(this) }
        runCatching { LiaMemoryAndHistoryStore.init(this) }
    }
}
