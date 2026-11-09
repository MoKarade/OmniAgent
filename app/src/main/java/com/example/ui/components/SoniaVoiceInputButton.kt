package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.GoogleRed
import com.example.ui.theme.GoogleSurfaceVariant
import java.util.Locale

/**
 * Voice-to-Text Microphone button using Android SpeechRecognizer & RecognizerIntent.
 * Requests RECORD_AUDIO runtime permission, streams live partial transcripts and RMS audio levels,
 * and supports automatic hands-free restart (`autoStartSignal`) for commands and voice notes.
 */
@Composable
fun SoniaVoiceInputButton(
    onPartialTranscript: (String) -> Unit,
    onFinalTranscript: (String) -> Unit,
    onVoiceError: (String) -> Unit = {},
    onListeningStateChanged: (Boolean) -> Unit = {},
    onRmsLevelChanged: (Float) -> Unit = {},
    autoStartSignal: Int = 0,
    buttonSize: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    testTag: String = "voice_mic_input_button"
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun updateListeningState(active: Boolean) {
        isListening = active
        onListeningStateChanged(active)
        if (!active) {
            onRmsLevelChanged(0f)
        }
    }

    // Fallback system speech activity launcher if direct SpeechRecognizer service is unavailable
    val recognizerActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        updateListeningState(false)
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()?.trim().orEmpty()
            if (spokenText.isNotBlank()) {
                onFinalTranscript(spokenText)
            }
        }
    }

    fun startSpeechRecognitionInternal() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRENCH.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez à LIA (Commande ou Note vocale)…")
        }

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        updateListeningState(true)
                    }

                    override fun onBeginningOfSpeech() {
                        updateListeningState(true)
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1f)
                        onRmsLevelChanged(normalized)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        updateListeningState(false)
                    }

                    override fun onError(error: Int) {
                        updateListeningState(false)
                        if (error == SpeechRecognizer.ERROR_CLIENT ||
                            error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                        ) {
                            onVoiceError("Microphone non disponible ou permission refusée.")
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        updateListeningState(false)
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotBlank()) {
                            onFinalTranscript(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            onPartialTranscript(partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                updateListeningState(true)
                recognizer.startListening(intent)
            } catch (_: Exception) {
                updateListeningState(false)
                runCatching { recognizerActivityLauncher.launch(intent) }
                    .onFailure { onVoiceError("Service vocal indisponible sur cet appareil.") }
            }
        } else {
            runCatching {
                updateListeningState(true)
                recognizerActivityLauncher.launch(intent)
            }.onFailure {
                updateListeningState(false)
                onVoiceError("Reconnaissance vocale non installée sur cet appareil.")
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeechRecognitionInternal()
        } else {
            updateListeningState(false)
            onVoiceError("Autorisez l'accès au microphone pour parler à LIA.")
        }
    }

    // Trigger automatic hands-free listening when autoStartSignal increments
    LaunchedEffect(autoStartSignal) {
        if (autoStartSignal > 0 && !isListening) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                startSpeechRecognitionInternal()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            }
        }
    }

    val pulseScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isListening) 1.15f else 1f,
        animationSpec = tween(250),
        label = "mic_scale"
    )

    Box(contentAlignment = Alignment.Center) {
        IconButton(
            onClick = {
                if (isListening) {
                    updateListeningState(false)
                    runCatching { speechRecognizer?.stopListening() }
                } else {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        startSpeechRecognitionInternal()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            modifier = Modifier
                .size(buttonSize)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    if (isListening) GoogleRed.copy(alpha = 0.24f)
                    else GoogleSurfaceVariant
                )
                .testTag(testTag)
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Arrêter la dictée vocale" else "Dicter à LIA au microphone",
                tint = if (isListening) GoogleRed else GoogleBlue,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
