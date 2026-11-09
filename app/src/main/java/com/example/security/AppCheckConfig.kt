package com.example.security

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            val app = runCatching { FirebaseApp.initializeApp(context) }.getOrNull()
            if (app == null) {
                val apiKeyRes = context.resources.getIdentifier("google_api_key", "string", context.packageName)
                val appIdRes = context.resources.getIdentifier("google_app_id", "string", context.packageName)
                val projIdRes = context.resources.getIdentifier("project_id", "string", context.packageName)
                val bucketRes = context.resources.getIdentifier("google_storage_bucket", "string", context.packageName)
                val senderIdRes = context.resources.getIdentifier("gcm_defaultSenderId", "string", context.packageName)

                val apiKey = if (apiKeyRes != 0) context.getString(apiKeyRes).trim() else ""
                val appId = if (appIdRes != 0) context.getString(appIdRes).trim() else ""
                val projId = if (projIdRes != 0) context.getString(projIdRes).trim() else ""
                val bucket = if (bucketRes != 0) context.getString(bucketRes).trim() else ""
                val senderId = if (senderIdRes != 0) context.getString(senderIdRes).trim() else ""

                val builder = com.google.firebase.FirebaseOptions.Builder()
                    .setApiKey(if (apiKey.isNotBlank()) apiKey else "AIzaSyDummyKeyForOmniAgentLocalTestingOnly000")
                    .setApplicationId(if (appId.isNotBlank()) appId else "1:100000000000:android:omniagentdummyappid")
                    .setProjectId(if (projId.isNotBlank()) projId else "omniagent-local")
                if (bucket.isNotBlank()) builder.setStorageBucket(bucket)
                if (senderId.isNotBlank()) builder.setGcmSenderId(senderId)
                runCatching { FirebaseApp.initializeApp(context.applicationContext, builder.build()) }
                Log.d(TAG, "FirebaseApp initialized via explicit FirebaseOptions fallback.")
            }
        }

        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.w(TAG, "FirebaseApp is not available, skipping App Check configuration.")
            return
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            }

            val debugFactory = runCatching {
                val clazz = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                clazz.getMethod("getInstance").invoke(null) as? AppCheckProviderFactory
            }.getOrNull()

            if (debugFactory != null) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(debugFactory)
                Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            }
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
