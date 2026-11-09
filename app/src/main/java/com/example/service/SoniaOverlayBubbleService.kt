package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.MainActivity
import com.example.data.GeminiDualEngineClient
import com.example.data.LiaAppLaunchAction
import com.example.data.LiaAssistantBrain
import com.example.data.LiaMemoryAndHistoryStore
import com.example.data.LiaSmsDraft
import com.example.security.SoniaSecurityKernel
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * True system-wide floating overlay service (`TYPE_APPLICATION_OVERLAY`) for LIA.
 * Shown ONLY when the user leaves/minimizes the LIA app (never duplicated inside the app).
 *
 * - State 1 (Minimized): A subtle, half-tucked 34dp circle with a ✨ icon on the screen edge.
 * - State 2 (Expanded): Clicking the side icon opens a sleek Gemini-style bottom chat bar
 *   with persistent Room-backed conversation history, explicit contact verification (Nom vs Numéro),
 *   an editable SMS preview card directly inside the bubble before sending, fast app launch button,
 *   and voice-to-text microphone.
 */
class SoniaOverlayBubbleService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var sideBubbleView: View? = null
    private var sideBubbleParams: WindowManager.LayoutParams? = null

    private var bottomChatView: View? = null
    private var bottomChatParams: WindowManager.LayoutParams? = null
    private var historyScrollView: ScrollView? = null
    private var historyListLayout: LinearLayout? = null
    private var replyCardContainer: LinearLayout? = null

    // Editable SMS Preview views inside the floating bubble
    private var smsPreviewContainer: LinearLayout? = null
    private var smsVerificationBadgeTv: TextView? = null
    private var smsRecipientEdit: EditText? = null
    private var smsPhoneEdit: EditText? = null
    private var smsBodyEdit: EditText? = null
    private var smsVerifyContactBtn: TextView? = null
    private var smsConfirmSendBtn: TextView? = null
    private var currentPreviewMsgId: String? = null

    private var actionButtonView: TextView? = null
    private var inputEditText: EditText? = null
    private var micButtonView: TextView? = null
    private var continuousVoiceBtnView: TextView? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var isContinuousVoiceMode = false
    private var isListeningMic = false
    private var dockedRight = true
    private var currentYPx = -120

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        GeminiDualEngineClient.init(applicationContext)
        LiaMemoryAndHistoryStore.init(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager

        runCatching {
            textToSpeech = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = Locale.FRENCH
                    isTtsReady = true
                }
            }.apply {
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        // In Continuous Voice Mode, once LIA finishes speaking, automatically re-open mic listening
                        if (isContinuousVoiceMode && bottomChatView != null && smsPreviewContainer?.visibility != View.VISIBLE) {
                            serviceScope.launch {
                                if (!isListeningMic) {
                                    toggleOverlaySpeechRecognition()
                                }
                            }
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })
            }
        }

        // Observe Room-backed state so conversation history in the bubble never disappears
        serviceScope.launch {
            LiaMemoryAndHistoryStore.state.collect {
                if (bottomChatView != null) {
                    refreshOverlayConversationHistory(thinkingStatus = null)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_HIDE_OVERLAY -> {
                removeAllOverlayViews()
            }
            ACTION_SHOW_EXPANDED_BAR -> {
                showBottomGeminiBarOverlay()
            }
            else -> {
                if (sideBubbleView == null && bottomChatView == null) {
                    showSubtleSideBubbleOverlay()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeAllOverlayViews()
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun screenWidthPx(): Int = resources.displayMetrics.widthPixels

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun showSubtleSideBubbleOverlay() {
        val wm = windowManager ?: return
        removeBottomChatOverlay()
        if (sideBubbleView != null) return

        val bubbleSize = dp(34)
        val halfTuckOffset = dp(10)
        val screenW = screenWidthPx()

        val initialX = if (dockedRight) screenW - bubbleSize + halfTuckOffset else -halfTuckOffset

        val params = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = (resources.displayMetrics.heightPixels / 2) + currentYPx
        }

        val container = FrameLayout(this).apply {
            alpha = 0.72f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E61E1F20"))
                setStroke(dp(1), Color.parseColor("#888AB4F8"))
            }
            elevation = dp(6).toFloat()
        }

        val sparkleIcon = TextView(this).apply {
            text = "✨"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#8AB4F8"))
        }
        container.addView(
            sparkleIcon,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        var downRawX = 0f
        var downRawY = 0f
        var startParamX = 0
        var startParamY = 0
        var isDragging = false

        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startParamX = params.x
                    startParamY = params.y
                    isDragging = false
                    container.alpha = 0.95f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = startParamX + dx
                        params.y = (startParamY + dy).coerceIn(dp(48), resources.displayMetrics.heightPixels - dp(96))
                        currentYPx = params.y - (resources.displayMetrics.heightPixels / 2)
                        runCatching { wm.updateViewLayout(container, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    container.alpha = 0.72f
                    if (!isDragging) {
                        showBottomGeminiBarOverlay()
                    } else {
                        val width = screenWidthPx()
                        dockedRight = event.rawX >= width / 2f
                        params.x = if (dockedRight) width - bubbleSize + halfTuckOffset else -halfTuckOffset
                        runCatching { wm.updateViewLayout(container, params) }
                    }
                    true
                }
                else -> false
            }
        }

        runCatching {
            wm.addView(container, params)
            sideBubbleView = container
            sideBubbleParams = params
        }
    }

    private fun showBottomGeminiBarOverlay() {
        val wm = windowManager ?: return
        removeSideBubbleOverlay()
        if (bottomChatView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            y = dp(10)
        }

        val rootColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(8))
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    showSubtleSideBubbleOverlay()
                    true
                } else {
                    false
                }
            }
        }

        // 1. Conversation History + Editable SMS Preview Card above the bottom bar
        val replyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#F51E1F20"))
                setStroke(dp(1), Color.parseColor("#3D8AB4F8"))
            }
        }

        // Top header row inside the card (✨ LIA • Historique Room + ⤢ Ouvrir l'app)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerTitle = TextView(this).apply {
            text = "✨ LIA • Discussion sauvegardée"
            setTextColor(Color.parseColor("#8AB4F8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val openAppBtn = TextView(this).apply {
            text = "Ouvrir LIA ⤢"
            setTextColor(Color.parseColor("#8AB4F8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(8), dp(2), dp(4), dp(2))
            setOnClickListener {
                val launchIntent = Intent(this@SoniaOverlayBubbleService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(launchIntent)
                removeAllOverlayViews()
            }
        }
        headerRow.addView(headerTitle)
        headerRow.addView(openAppBtn)
        replyCard.addView(headerRow)

        // Scrollable mini-list of messages (Room-persisted, never erased when typing new messages)
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
        }
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(listLayout)
        replyCard.addView(scroll)

        // 1B. Interactive Editable SMS Preview Card inside the bubble (Nom vs Numéro verification + editable fields)
        val smsPreviewBox = buildBubbleEditableSmsPreviewBox()
        replyCard.addView(smsPreviewBox)

        // 1C. Direct App Launch Action Button inside the bubble (e.g. "🚀 Ouvrir YouTube")
        val directActionBtn = TextView(this).apply {
            visibility = View.GONE
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#4285F4"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }
        replyCard.addView(directActionBtn)

        val replyCardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(8)
        }
        rootColumn.addView(replyCard, replyCardParams)

        // 2. Gemini Bottom Pill Bar
        val barRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(8), dp(7))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28).toFloat()
                setColor(Color.parseColor("#FA1E1F20"))
                setStroke(dp(1), Color.parseColor("#668AB4F8"))
            }
            elevation = dp(10).toFloat()
        }

        // Left Sparkle Badge (tap to open full LIA app)
        val badgeView = TextView(this).apply {
            text = "✨"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#248AB4F8"))
            }
            setOnClickListener {
                val launchIntent = Intent(this@SoniaOverlayBubbleService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(launchIntent)
                removeAllOverlayViews()
            }
        }
        barRow.addView(badgeView, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
            rightMargin = dp(8)
        })

        // EditText ("Demandez à LIA…")
        val editText = EditText(this).apply {
            hint = "Demandez à LIA…"
            setHintTextColor(Color.parseColor("#9AA0A6"))
            setTextColor(Color.parseColor("#E3E3E3"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = null
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    val query = text?.toString()?.trim().orEmpty()
                    if (query.isNotEmpty()) {
                        executeOverlayQuery(query)
                    }
                    true
                } else {
                    false
                }
            }
        }
        barRow.addView(
            editText,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        // Continuous Hands-Free Voice Conversation Toggle Button ("🗣️" / "🔊")
        val continuousVoiceBtn = TextView(this).apply {
            text = if (isContinuousVoiceMode) "🔊" else "🗣️"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(if (isContinuousVoiceMode) "#1E3A28" else "#282A2C"))
            }
            setOnClickListener {
                isContinuousVoiceMode = !isContinuousVoiceMode
                updateContinuousVoiceVisual()
                if (isContinuousVoiceMode && !isListeningMic) {
                    toggleOverlaySpeechRecognition()
                } else if (!isContinuousVoiceMode) {
                    runCatching { textToSpeech?.stop() }
                }
            }
        }
        barRow.addView(continuousVoiceBtn, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
            leftMargin = dp(4)
        })

        // Microphone Voice-to-Text Button
        val micBtn = TextView(this).apply {
            text = "🎤"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#282A2C"))
            }
            setOnClickListener {
                toggleOverlaySpeechRecognition()
            }
        }
        barRow.addView(micBtn, LinearLayout.LayoutParams(dp(34), dp(34)).apply {
            leftMargin = dp(6)
        })

        // Send Button
        val sendBtn = TextView(this).apply {
            text = "➤"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4285F4"))
            }
            setOnClickListener {
                val query = editText.text?.toString()?.trim().orEmpty()
                if (query.isNotEmpty()) {
                    executeOverlayQuery(query)
                }
            }
        }
        barRow.addView(sendBtn, LinearLayout.LayoutParams(dp(34), dp(34)).apply {
            leftMargin = dp(6)
        })

        // Close ('✕') Button -> folds back into the subtle side icon on the edge
        val closeBtn = TextView(this).apply {
            text = "✕"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#9AA0A6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#282A2C"))
            }
            setOnClickListener {
                showSubtleSideBubbleOverlay()
            }
        }
        barRow.addView(closeBtn, LinearLayout.LayoutParams(dp(28), dp(28)).apply {
            leftMargin = dp(6)
        })

        rootColumn.addView(
            barRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        replyCardContainer = replyCard
        historyScrollView = scroll
        historyListLayout = listLayout
        actionButtonView = directActionBtn
        inputEditText = editText
        micButtonView = micBtn
        continuousVoiceBtnView = continuousVoiceBtn

        // Populate existing conversation messages immediately if any exist
        refreshOverlayConversationHistory(thinkingStatus = null)

        runCatching {
            wm.addView(rootColumn, params)
            bottomChatView = rootColumn
            bottomChatParams = params
            editText.requestFocus()
        }
    }

    /**
     * Builds the interactive editable SMS preview card inside the floating bubble so the user
     * can verify/edit Contact Name, Phone Number, and SMS Message before sending.
     */
    private fun buildBubbleEditableSmsPreviewBox(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#131314"))
                setStroke(dp(1), Color.parseColor("#558AB4F8"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }

        val titleTv = TextView(this).apply {
            text = "💬 Message SMS"
            setTextColor(Color.parseColor("#8AB4F8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
        }
        box.addView(titleTv)

        val statusBadge = TextView(this).apply {
            text = "✓ Contact vérifié"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(6)
            }
        }
        box.addView(statusBadge)

        // Row with Contact Name & Phone Number editable fields
        val contactRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val recipientInput = EditText(this).apply {
            hint = "Nom (ex: Maman)"
            setHintTextColor(Color.parseColor("#777A80"))
            setTextColor(Color.parseColor("#E3E3E3"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            isSingleLine = true
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#1E1F20"))
                setStroke(dp(1), Color.parseColor("#3C4043"))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.42f).apply {
                rightMargin = dp(6)
            }
        }

        val phoneInput = EditText(this).apply {
            hint = "Numéro (+33 6...)"
            inputType = InputType.TYPE_CLASS_PHONE
            setHintTextColor(Color.parseColor("#777A80"))
            setTextColor(Color.parseColor("#E3E3E3"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            isSingleLine = true
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#1E1F20"))
                setStroke(dp(1), Color.parseColor("#3C4043"))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.58f)
        }

        contactRow.addView(recipientInput)
        contactRow.addView(phoneInput)
        box.addView(contactRow)

        val bodyInput = EditText(this).apply {
            hint = "Message SMS à envoyer…"
            setHintTextColor(Color.parseColor("#777A80"))
            setTextColor(Color.parseColor("#E3E3E3"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            minLines = 2
            maxLines = 4
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#1E1F20"))
                setStroke(dp(1), Color.parseColor("#3C4043"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
        }
        box.addView(bodyInput)

        // Buttons Row: "🔍 Vérifier Contact" + "📤 Confirmer & Envoyer"
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }

        val cancelSmsBtn = TextView(this).apply {
            text = "✕ Annuler"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#F28B82"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#3C1E1E"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6)
            }
            setOnClickListener {
                LiaMemoryAndHistoryStore.cancelSmsDraft(currentPreviewMsgId)
                box.visibility = View.GONE
                currentPreviewMsgId = null
                refreshOverlayConversationHistory()
            }
        }

        val verifyBtn = TextView(this).apply {
            text = "Vérifier"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#8AB4F8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#248AB4F8"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6)
            }
        }

        val sendBtn = TextView(this).apply {
            text = "📤 Envoyer"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#1E8E3E"))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        actionsRow.addView(cancelSmsBtn)
        actionsRow.addView(verifyBtn)
        actionsRow.addView(sendBtn)
        box.addView(actionsRow)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateBubbleSmsStatusBadge(
                    recipient = recipientInput.text?.toString().orEmpty(),
                    phone = phoneInput.text?.toString().orEmpty()
                )
            }
        }
        recipientInput.addTextChangedListener(watcher)
        phoneInput.addTextChangedListener(watcher)

        verifyBtn.setOnClickListener {
            val currentName = recipientInput.text?.toString()?.trim().orEmpty()
            val currentPhone = phoneInput.text?.toString()?.trim().orEmpty()
            val currentBody = bodyInput.text?.toString()?.trim().orEmpty()
            val resolved = LiaMemoryAndHistoryStore.verifyContactForSms(
                currentPhone.ifBlank { currentName }
            )
            if (currentPhone.isBlank() && resolved.phoneNumber.isNotBlank()) {
                phoneInput.setText(resolved.phoneNumber)
            }
            if (currentName.isBlank() && resolved.recipientName.isNotBlank()) {
                recipientInput.setText(resolved.recipientName)
            }
            val finalName = recipientInput.text?.toString()?.trim().orEmpty().ifBlank { resolved.recipientName }
            val finalPhone = phoneInput.text?.toString()?.trim().orEmpty().ifBlank { resolved.phoneNumber }
            currentPreviewMsgId?.let { msgId ->
                LiaMemoryAndHistoryStore.updateSmsDraftPreview(msgId, finalName, finalPhone, currentBody)
            }
            updateBubbleSmsStatusBadge(finalName, finalPhone)
        }

        var awaitingModalConfirmation = false

        sendBtn.setOnClickListener {
            val finalName = recipientInput.text?.toString()?.trim().orEmpty().ifBlank { "Contact" }
            val finalPhone = phoneInput.text?.toString()?.trim().orEmpty()
            val finalBody = bodyInput.text?.toString()?.trim().orEmpty()

            if (!LiaMemoryAndHistoryStore.isPhoneNumberValid(finalPhone)) {
                awaitingModalConfirmation = false
                updateBubbleSmsStatusBadge(finalName, finalPhone, false)
                return@setOnClickListener
            }

            if (!awaitingModalConfirmation) {
                // Step 1: Safe Contact Resolver explicit confirmation step inside the bubble modal
                awaitingModalConfirmation = true
                updateBubbleSmsStatusBadge(finalName, finalPhone, true)
                return@setOnClickListener
            }

            // Step 2: User explicitly confirmed the recipient -> execute SMS send
            awaitingModalConfirmation = false
            LiaMemoryAndHistoryStore.sendSmsDirectly(
                context = this@SoniaOverlayBubbleService,
                messageId = currentPreviewMsgId,
                recipientName = finalName,
                phoneNumber = finalPhone,
                messageBody = finalBody
            )
            box.visibility = View.GONE
            refreshOverlayConversationHistory()
        }

        smsPreviewContainer = box
        smsVerificationBadgeTv = statusBadge
        smsRecipientEdit = recipientInput
        smsPhoneEdit = phoneInput
        smsBodyEdit = bodyInput
        smsVerifyContactBtn = verifyBtn
        smsConfirmSendBtn = sendBtn
        return box
    }

    private fun updateBubbleSmsStatusBadge(
        recipient: String,
        phone: String,
        awaitingFinalConfirm: Boolean = false
    ) {
        val badge = smsVerificationBadgeTv ?: return
        val sendBtn = smsConfirmSendBtn ?: return
        val validPhone = LiaMemoryAndHistoryStore.isPhoneNumberValid(phone)

        if (validPhone && awaitingFinalConfirm) {
            badge.text = "Confirmez-vous l'envoi à « $recipient » ($phone) ?"
            badge.setTextColor(Color.parseColor("#8AB4F8"))
            badge.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#1A3050"))
            }
            sendBtn.text = "✅ Oui, envoyer"
            sendBtn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#1E8E3E"))
            }
        } else if (validPhone) {
            badge.text = "✓ Destinataire vérifié : ${recipient.ifBlank { "Contact" }} ($phone)"
            badge.setTextColor(Color.parseColor("#81C995"))
            badge.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#1E3A28"))
            }
            sendBtn.text = "📤 Envoyer"
            sendBtn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#4285F4"))
            }
        } else {
            badge.text = "⚠️ Numéro requis pour « ${recipient.ifBlank { "Contact" }} »"
            badge.setTextColor(Color.parseColor("#FDD663"))
            badge.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#3D3014"))
            }
            sendBtn.text = "Numéro requis"
            sendBtn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#5F6368"))
            }
        }
    }

    private fun refreshOverlayConversationHistory(
        thinkingStatus: String? = null,
        latestSmsDraft: LiaSmsDraft? = null,
        latestAppAction: LiaAppLaunchAction? = null
    ) {
        val card = replyCardContainer ?: return
        val list = historyListLayout ?: return
        val smsBox = smsPreviewContainer ?: return
        val actBtn = actionButtonView ?: return

        val allMessages = LiaMemoryAndHistoryStore.state.value.activeMessages
        val recentMessages = allMessages.takeLast(6)
        if (recentMessages.isEmpty() && thinkingStatus == null) {
            card.visibility = View.GONE
            return
        }

        card.visibility = View.VISIBLE
        list.removeAllViews()

        recentMessages.forEach { msg ->
            val isUser = msg.role == "user"
            val cleanText = msg.text.replace("**", "").replace("`", "").replace("« *", "« ").replace("* »", " »")
            val itemTv = TextView(this).apply {
                text = if (isUser) "Vous : $cleanText" else "✨ $cleanText"
                setTextColor(Color.parseColor(if (isUser) "#9AA0A6" else "#E3E3E3"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isUser) 12f else 13f)
                maxLines = if (isUser) 3 else 7
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(3), 0, dp(3))
            }
            list.addView(itemTv)

            if (!isUser && !msg.learnedNote.isNullOrBlank()) {
                val noteBadge = TextView(this).apply {
                    text = "⚡ ${msg.learnedNote}"
                    setTextColor(Color.parseColor("#81C995"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setPadding(0, dp(1), 0, dp(3))
                }
                list.addView(noteBadge)
            }
        }

        val lastLiaMsg = recentMessages.lastOrNull { it.role == "lia" }
        if (thinkingStatus == null && lastLiaMsg != null && lastLiaMsg.quickReplies.isNotEmpty()) {
            val hScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(0, dp(4), 0, dp(2))
            }
            val chipRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            lastLiaMsg.quickReplies.take(5).forEach { qr ->
                val chip = TextView(this).apply {
                    text = qr
                    setTextColor(Color.parseColor("#8AB4F8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(14).toFloat()
                        setColor(Color.parseColor("#1A3050"))
                        setStroke(dp(1), Color.parseColor("#395E8F"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dp(6)
                    }
                    setOnClickListener {
                        executeOverlayQuery(qr)
                    }
                }
                chipRow.addView(chip)
            }
            hScroll.addView(chipRow)
            list.addView(hScroll)
        }

        if (thinkingStatus != null) {
            val thinkingTv = TextView(this).apply {
                text = "⏳ $thinkingStatus"
                setTextColor(Color.parseColor("#8AB4F8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(4), 0, dp(2))
            }
            list.addView(thinkingTv)
        }

        // Only show the SMS draft box if the VERY LAST message in the conversation is an active unsent SMS draft
        val latestMsg = recentMessages.lastOrNull()
        val activeSms = latestSmsDraft ?: latestMsg?.smsDraft?.takeIf { !it.isSent }
        val activeApp = latestAppAction ?: latestMsg?.appLaunchAction

        if (activeSms != null) {
            smsBox.visibility = View.VISIBLE
            val msgId = latestMsg?.id
            if (currentPreviewMsgId != msgId || smsRecipientEdit?.text.isNullOrBlank()) {
                currentPreviewMsgId = msgId
                smsRecipientEdit?.setText(activeSms.recipientName)
                smsPhoneEdit?.setText(activeSms.phoneNumber)
                smsBodyEdit?.setText(activeSms.messageBody)
                updateBubbleSmsStatusBadge(activeSms.recipientName, activeSms.phoneNumber)
            }
        } else {
            smsBox.visibility = View.GONE
            currentPreviewMsgId = null
        }

        if (activeApp != null) {
            actBtn.visibility = View.VISIBLE
            actBtn.text = "🚀 Ouvrir ${activeApp.appLabel} maintenant"
            actBtn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#4285F4"))
            }
            actBtn.setOnClickListener {
                LiaMemoryAndHistoryStore.launchAppFast(this@SoniaOverlayBubbleService, activeApp.packageName)
                showSubtleSideBubbleOverlay()
            }
        } else {
            actBtn.visibility = View.GONE
        }

        historyScrollView?.post {
            historyScrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun toggleOverlaySpeechRecognition() {
        if (isListeningMic) {
            isListeningMic = false
            updateMicVisual(false)
            runCatching { speechRecognizer?.stopListening() }
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            refreshOverlayConversationHistory(thinkingStatus = "Reconnaissance vocale non disponible sur cet appareil.")
            return
        }

        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListeningMic = true
                    updateMicVisual(true)
                }

                override fun onBeginningOfSpeech() {
                    isListeningMic = true
                    updateMicVisual(true)
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListeningMic = false
                    updateMicVisual(false)
                }

                override fun onError(error: Int) {
                    isListeningMic = false
                    updateMicVisual(false)
                }

                override fun onResults(results: Bundle?) {
                    isListeningMic = false
                    updateMicVisual(false)
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (spoken.isNotBlank()) {
                        inputEditText?.setText(spoken)
                        executeOverlayQuery(spoken)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        inputEditText?.setText(partial)
                        inputEditText?.setSelection(partial.length)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRENCH.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            isListeningMic = true
            updateMicVisual(true)
            recognizer.startListening(intent)
        } catch (_: Exception) {
            isListeningMic = false
            updateMicVisual(false)
        }
    }

    private fun updateMicVisual(listening: Boolean) {
        val btn = micButtonView ?: return
        btn.text = if (listening) "⏹" else "🎤"
        btn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(if (listening) "#44F28B82" else "#282A2C"))
        }
    }

    private fun updateContinuousVoiceVisual() {
        val btn = continuousVoiceBtnView ?: return
        btn.text = if (isContinuousVoiceMode) "🔊" else "🗣️"
        btn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(if (isContinuousVoiceMode) "#1E3A28" else "#282A2C"))
        }
    }

    private fun speakBubbleReplyIfNeeded(replyText: String) {
        if (!isContinuousVoiceMode || !isTtsReady) return
        val cleanSpeech = replyText
            .replace(Regex("[*`_#•✨🔍⚡💭☀️🚗🌙📱🛡️✓⚠️]"), "")
            .trim()
        if (cleanSpeech.isNotBlank()) {
            runCatching {
                textToSpeech?.speak(
                    cleanSpeech,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "lia_bubble_utterance_${System.currentTimeMillis()}"
                )
            }
        }
    }

    private fun executeOverlayQuery(rawQuery: String) {
        inputEditText?.setText("")
        hideKeyboard(inputEditText)

        // SEC-05: Zero-Trust Check for Vault PIN Lock & Global Lockdown Policy in Floating Bubble
        if (SoniaSecurityKernel.isVaultLockedRuntime) {
            refreshOverlayConversationHistory(
                thinkingStatus = "🔒 LIA est verrouillée par code PIN. Déverrouillez-la dans Paramètres > Sécurité & PIN."
            )
            return
        }
        if (SoniaSecurityKernel.globalPolicyRuntime == com.example.model.GlobalAccessPolicyMode.LOCKDOWN) {
            refreshOverlayConversationHistory(
                thinkingStatus = "🛑 Mode Verrouillage Total actif. Autorisez l'accès dans Paramètres > Sécurité & PIN."
            )
            return
        }

        // DLP & Anti-Injection Security Check
        val dlp = SoniaSecurityKernel.inspectAndSanitize(rawQuery)
        if (!dlp.isSafe) {
            refreshOverlayConversationHistory(
                thinkingStatus = "🛡️ Pare-feu DLP LIA : Requête bloquée (${dlp.detectedThreats.firstOrNull()})."
            )
            return
        }

        refreshOverlayConversationHistory(thinkingStatus = "LIA réfléchit…")

        serviceScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                LiaAssistantBrain.processUserMessage(
                    rawUserText = dlp.sanitizedText,
                    source = "bubble"
                )
            }
            outcome.appLaunchAction?.let { launchAction ->
                // SEC-01: Sensitive URIs (tel:, smsto:, mailto:, https://) require explicit user tap on the bubble action button
                AppActionExecutor.executeTargetDirectly(
                    context = this@SoniaOverlayBubbleService,
                    targetPackageOrUri = launchAction.packageName,
                    label = launchAction.appLabel,
                    userExplicitlyConfirmed = false
                )
            }
            refreshOverlayConversationHistory(
                thinkingStatus = null,
                latestSmsDraft = outcome.smsDraft,
                latestAppAction = outcome.appLaunchAction
            )
            speakBubbleReplyIfNeeded(outcome.replyText)
        }
    }

    private fun hideKeyboard(view: View?) {
        if (view == null) return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun removeSideBubbleOverlay() {
        val wm = windowManager
        val v = sideBubbleView
        if (wm != null && v != null) {
            runCatching { wm.removeView(v) }
        }
        sideBubbleView = null
        sideBubbleParams = null
    }

    private fun removeBottomChatOverlay() {
        val wm = windowManager
        val v = bottomChatView
        if (wm != null && v != null) {
            runCatching { wm.removeView(v) }
        }
        runCatching { textToSpeech?.stop() }
        bottomChatView = null
        bottomChatParams = null
        replyCardContainer = null
        historyScrollView = null
        historyListLayout = null
        smsPreviewContainer = null
        smsVerificationBadgeTv = null
        smsRecipientEdit = null
        smsPhoneEdit = null
        smsBodyEdit = null
        smsVerifyContactBtn = null
        smsConfirmSendBtn = null
        currentPreviewMsgId = null
        actionButtonView = null
        inputEditText = null
        micButtonView = null
        continuousVoiceBtnView = null
    }

    private fun removeAllOverlayViews() {
        removeSideBubbleOverlay()
        removeBottomChatOverlay()
    }

    companion object {
        const val ACTION_SHOW_BUBBLE = "com.example.service.action.SHOW_BUBBLE"
        const val ACTION_SHOW_EXPANDED_BAR = "com.example.service.action.SHOW_EXPANDED_BAR"
        const val ACTION_HIDE_OVERLAY = "com.example.service.action.HIDE_OVERLAY"

        fun canDrawOverlays(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }

        fun startSideBubble(context: Context) {
            if (!canDrawOverlays(context)) return
            val intent = Intent(context, SoniaOverlayBubbleService::class.java).apply {
                action = ACTION_SHOW_BUBBLE
            }
            runCatching { context.startService(intent) }
        }

        fun hideOverlay(context: Context) {
            val intent = Intent(context, SoniaOverlayBubbleService::class.java).apply {
                action = ACTION_HIDE_OVERLAY
            }
            runCatching { context.startService(intent) }
        }
    }
}
