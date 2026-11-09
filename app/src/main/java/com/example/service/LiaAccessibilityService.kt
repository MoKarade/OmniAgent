package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiaAccessibilityStatus(
    val isServiceConnected: Boolean = false,
    val activeWindowPackage: String = "",
    val lastExecutedAction: String = "En attente d'action écran",
    val visibleScreenElementsSample: List<String> = emptyList(),
    val totalActionsExecuted: Int = 0
)

sealed class UiAutomationStep {
    data class OpenApp(val packageNameOrQuery: String, val appLabel: String = "") : UiAutomationStep()
    data class WaitMs(val durationMs: Long) : UiAutomationStep()
    data class ClickByTextOrId(val queryText: String, val fallbackXRatio: Float = 0.5f, val fallbackYRatio: Float = 0.5f) : UiAutomationStep()
    data class ClickCoordinates(val xRatio: Float, val yRatio: Float, val label: String = "") : UiAutomationStep()
    data class SwipeGesture(val direction: String) : UiAutomationStep()
    data class TypeTextIntoField(val text: String, val targetFieldHint: String = "", val autoClickSend: Boolean = false) : UiAutomationStep()
    data class GlobalSystemAction(val actionName: String) : UiAutomationStep()
}

/**
 * Service Android `AccessibilityService` temps réel (`LiaAccessibilityService`) permettant à LIA :
 * 1. De lire tous les éléments, textes et boutons affichés dans n'importe quelle application (Steam, Gemini, Messenger, Chrome, etc.)
 * 2. D'exécuter de vrais clics (`ACTION_CLICK` ou `dispatchGesture`), de vrais défilements/swipes (`GestureDescription`),
 *    de saisir du texte dans n'importe quel champ (`ACTION_SET_TEXT`) et d'enchaîner des macros multi-étapes autonomes.
 */
class LiaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _status.update {
            it.copy(
                isServiceConnected = true,
                lastExecutedAction = "✓ Contrôle Total Accessibilité LIA connecté"
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isNotBlank()) {
            val root = runCatching { rootInActiveWindow }.getOrNull()
            val sampleTexts = if (root != null) {
                collectVisibleTexts(root, maxItems = 15)
            } else emptyList()

            _status.update { state ->
                state.copy(
                    isServiceConnected = true,
                    activeWindowPackage = pkg,
                    visibleScreenElementsSample = if (sampleTexts.isNotEmpty()) sampleTexts else state.visibleScreenElementsSample
                )
            }
        }
    }

    override fun onInterrupt() {
        _status.update {
            it.copy(lastExecutedAction = "⚠️ Service Accessibilité interrompu")
        }
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        _status.update {
            it.copy(
                isServiceConnected = false,
                lastExecutedAction = "Service Accessibilité arrêté"
            )
        }
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: LiaAccessibilityService? = null
            private set

        private val _status = MutableStateFlow(LiaAccessibilityStatus())
        val status: StateFlow<LiaAccessibilityStatus> = _status.asStateFlow()

        private val automationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        fun isAccessibilityPermissionEnabled(context: Context): Boolean {
            if (instance != null) return true
            val expectedComponentName = "${context.packageName}/${LiaAccessibilityService::class.java.canonicalName}"
            val enabledServices = runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            }.getOrNull().orEmpty()
            return enabledServices.contains(expectedComponentName, ignoreCase = true) ||
                enabledServices.contains(LiaAccessibilityService::class.java.simpleName, ignoreCase = true)
        }

        fun openAccessibilitySettings(context: Context): Boolean {
            return runCatching {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }

        /**
         * Lit l'arbre de l'écran actuel pour permettre à LIA de savoir quels jeux, boutons ou textes sont affichés.
         */
        fun readCurrentScreenSnapshot(): List<String> {
            val svc = instance ?: return emptyList()
            val root = runCatching { svc.rootInActiveWindow }.getOrNull() ?: return emptyList()
            return collectVisibleTexts(root, maxItems = 50)
        }

        data class StructuredScreenSnapshot(
            val activePackageName: String,
            val allVisibleTexts: List<String>,
            val clickableButtonsAndTabs: List<String>,
            val progressAndGaugeValues: List<String>,
            val keyValuePairs: List<Pair<String, String>>
        )

        /**
         * Capture une vue structurée complète de l'écran actif :
         * - Tous les textes et descriptions d'éléments
         * - Les barres de progression / jauges graphiques (`rangeInfo` ex: batterie 78%)
         * - Les paires Clé -> Valeur adjacentes (ex: "Autonomie" -> "342 km", "Batterie" -> "78%")
         * - La liste des onglets et boutons cliquables pour la navigation autonome
         */
        fun readStructuredScreenSnapshot(): StructuredScreenSnapshot {
            val svc = instance ?: return StructuredScreenSnapshot("", emptyList(), emptyList(), emptyList(), emptyList())
            val root = runCatching { svc.rootInActiveWindow }.getOrNull()
                ?: return StructuredScreenSnapshot(status.value.activeWindowPackage, emptyList(), emptyList(), emptyList(), emptyList())

            val pkg = root.packageName?.toString().orEmpty().ifBlank { status.value.activeWindowPackage }
            val texts = LinkedHashSet<String>()
            val clickables = LinkedHashSet<String>()
            val gauges = LinkedHashSet<String>()
            val pairs = mutableListOf<Pair<String, String>>()
            var previousLabel = ""

            fun traverse(node: AccessibilityNodeInfo?) {
                if (node == null || texts.size >= 80) return
                val t = node.text?.toString()?.trim().orEmpty()
                val d = node.contentDescription?.toString()?.trim().orEmpty()
                val combined = when {
                    t.isNotBlank() && d.isNotBlank() && !t.equals(d, ignoreCase = true) -> "$t ($d)"
                    t.isNotBlank() -> t
                    else -> d
                }

                // Lecture des jauges et ProgressBar (ex: jauge de batterie véhicule ou téléchargement)
                val range = node.rangeInfo
                if (range != null && range.max > range.min) {
                    val pct = (((range.current - range.min) / (range.max - range.min)) * 100f).toInt().coerceIn(0, 100)
                    val gaugeLabel = if (combined.isNotBlank()) "$combined : $pct%" else "Jauge : $pct%"
                    gauges.add(gaugeLabel)
                    texts.add(gaugeLabel)
                }

                if (combined.length in 1..140) {
                    texts.add(combined)
                    if (node.isClickable || node.parent?.isClickable == true) {
                        clickables.add(t.ifBlank { d })
                    }
                    if (previousLabel.isNotBlank() && previousLabel != combined && combined.any { it.isDigit() }) {
                        pairs.add(previousLabel to combined)
                    }
                    previousLabel = combined
                }

                val count = node.childCount
                for (i in 0 until count) {
                    traverse(runCatching { node.getChild(i) }.getOrNull())
                }
            }

            traverse(root)
            return StructuredScreenSnapshot(
                activePackageName = pkg,
                allVisibleTexts = texts.toList(),
                clickableButtonsAndTabs = clickables.toList(),
                progressAndGaugeValues = gauges.toList(),
                keyValuePairs = pairs.take(25)
            )
        }

        private val CRITICAL_IRREVERSIBLE_KEYWORDS = listOf(
            "payer maintenant", "confirmer le paiement", "acheter maintenant", "valider le virement",
            "confirmer le virement", "supprimer le compte", "effacer toutes les données",
            "déverrouiller les portières", "deverrouiller les portieres", "ouvrir le coffre",
            "démarrer le moteur", "factory reset"
        )

        fun isCriticalIrreversibleAction(label: String): Boolean {
            val lower = label.lowercase().trim()
            return CRITICAL_IRREVERSIBLE_KEYWORDS.any { lower.contains(it) }
        }

        private fun collectVisibleTexts(node: AccessibilityNodeInfo?, maxItems: Int): List<String> {
            val results = LinkedHashSet<String>()
            fun traverse(curr: AccessibilityNodeInfo?) {
                if (curr == null || results.size >= maxItems) return
                val text = curr.text?.toString()?.trim().orEmpty()
                val desc = curr.contentDescription?.toString()?.trim().orEmpty()
                if (text.length in 2..120) results.add(text)
                if (desc.length in 2..120 && desc != text) results.add(desc)
                val count = curr.childCount
                for (i in 0 until count) {
                    if (results.size >= maxItems) break
                    traverse(runCatching { curr.getChild(i) }.getOrNull())
                }
            }
            traverse(node)
            return results.toList()
        }

        /**
         * Clique sur un élément de l'écran correspondant à [targetText] (ex: "Bibliothèque", "Jouer", "Envoyer", etc.).
         * Si non trouvé sous forme de noeud accessible, effectue un tap physique aux coordonnées de secours.
         */
        fun clickNodeByTextOrFallback(
            targetText: String,
            fallbackXRatio: Float = 0.5f,
            fallbackYRatio: Float = 0.5f,
            allowCriticalIrreversible: Boolean = false
        ): Boolean {
            val svc = instance ?: return false
            val cleaned = targetText.trim()
            if (!allowCriticalIrreversible && isCriticalIrreversibleAction(cleaned)) {
                recordAction("🛡️ Clic critique bloqué en attente de confirmation : « $cleaned »")
                return false
            }

            val root = runCatching { svc.rootInActiveWindow }.getOrNull()

            if (root != null && cleaned.isNotBlank()) {
                val matchedNode = findBestMatchingNode(root, cleaned)
                if (matchedNode != null) {
                    val nodeText = (matchedNode.text?.toString().orEmpty() + " " + matchedNode.contentDescription?.toString().orEmpty())
                    if (!allowCriticalIrreversible && isCriticalIrreversibleAction(nodeText)) {
                        recordAction("🛡️ Noeud critique bloqué (confirmation requise) : « ${nodeText.trim()} »")
                        return false
                    }
                    val clicked = performClickOnNodeOrParent(matchedNode)
                    if (clicked) {
                        recordAction("👆 Clic Accessibilité sur « $cleaned »")
                        return true
                    }
                }
            }

            // Fallback : Tap physique par coordonnées via GestureDescription
            return performTapAtRatio(fallbackXRatio, fallbackYRatio, "Clic coordonnées pour « $cleaned »")
        }

        private fun findBestMatchingNode(root: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
            val lowerQuery = query.lowercase()
            // 1. Recherche native Android par texte
            val byText = runCatching { root.findAccessibilityNodeInfosByText(query) }.getOrNull().orEmpty()
            byText.firstOrNull()?.let { return it }

            // 2. Parcours récursif sur text, contentDescription et viewIdResourceName
            var bestMatch: AccessibilityNodeInfo? = null
            fun search(node: AccessibilityNodeInfo?) {
                if (node == null || bestMatch != null) return
                val t = node.text?.toString()?.lowercase().orEmpty()
                val d = node.contentDescription?.toString()?.lowercase().orEmpty()
                val id = node.viewIdResourceName?.lowercase().orEmpty()

                if (t.contains(lowerQuery) || d.contains(lowerQuery) || id.contains(lowerQuery)) {
                    bestMatch = node
                    return
                }
                for (i in 0 until node.childCount) {
                    search(runCatching { node.getChild(i) }.getOrNull())
                }
            }
            search(root)
            return bestMatch
        }

        private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
            var curr: AccessibilityNodeInfo? = node
            var depth = 0
            while (curr != null && depth < 6) {
                if (curr.isClickable) {
                    val ok = runCatching { curr.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
                    if (ok) return true
                }
                curr = runCatching { curr.parent }.getOrNull()
                depth++
            }
            // Sinon clic aux coordonnées du rectangle de l'élément
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty) {
                return performTapExact(rect.centerX().toFloat(), rect.centerY().toFloat(), "Tap sur noeud")
            }
            return false
        }

        /**
         * Exécute un tap physique réel aux coordonnées relatives (0.0 .. 1.0) de l'écran.
         */
        fun performTapAtRatio(xRatio: Float, yRatio: Float, label: String = "Tap écran"): Boolean {
            val svc = instance ?: return false
            val metrics = svc.resources.displayMetrics
            val x = (metrics.widthPixels * xRatio.coerceIn(0.05f, 0.95f))
            val y = (metrics.heightPixels * yRatio.coerceIn(0.05f, 0.95f))
            return performTapExact(x, y, label)
        }

        fun performTapExact(x: Float, y: Float, label: String = "Tap écran"): Boolean {
            val svc = instance ?: return false
            return runCatching {
                val path = Path().apply { moveTo(x, y) }
                val stroke = GestureDescription.StrokeDescription(path, 0L, 90L)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                val dispatched = svc.dispatchGesture(gesture, null, null)
                if (dispatched) {
                    recordAction("👆 $label (${x.toInt()}, ${y.toInt()})")
                }
                dispatched
            }.getOrDefault(false)
        }

        /**
         * Exécute un vrai geste de glissement / défilement (`SWIPE_UP`, `SWIPE_DOWN`, `SWIPE_LEFT`, `SWIPE_RIGHT`).
         */
        fun performSwipeGesture(direction: String): Boolean {
            val svc = instance ?: return false
            val metrics = svc.resources.displayMetrics
            val w = metrics.widthPixels.toFloat()
            val h = metrics.heightPixels.toFloat()

            val (startX, startY, endX, endY) = when (direction.uppercase()) {
                "SWIPE_UP", "SCROLL_DOWN", "DOWN" -> listOf(w * 0.5f, h * 0.78f, w * 0.5f, h * 0.22f)
                "SWIPE_DOWN", "SCROLL_UP", "UP" -> listOf(w * 0.5f, h * 0.24f, w * 0.5f, h * 0.78f)
                "SWIPE_LEFT", "LEFT" -> listOf(w * 0.85f, h * 0.5f, w * 0.15f, h * 0.5f)
                "SWIPE_RIGHT", "RIGHT" -> listOf(w * 0.15f, h * 0.5f, w * 0.85f, h * 0.5f)
                else -> listOf(w * 0.5f, h * 0.78f, w * 0.5f, h * 0.22f)
            }

            return runCatching {
                val path = Path().apply {
                    moveTo(startX, startY)
                    lineTo(endX, endY)
                }
                val stroke = GestureDescription.StrokeDescription(path, 0L, 320L)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                val ok = svc.dispatchGesture(gesture, null, null)
                if (ok) {
                    recordAction("↕️ Swipe $direction exécuté sur l'écran")
                }
                ok
            }.getOrDefault(false)
        }

        /**
         * Trouve le champ de saisie (EditText) sur l'écran actuel (ex: barre de prompt Gemini, barre de recherche Steam,
         * zone de message Messenger/WhatsApp/ChatGPT), y injecte [textToType] via `ACTION_SET_TEXT` et clique optionnellement sur Envoyer.
         */
        fun typeTextIntoFocusedOrFirstInputField(
            context: Context,
            textToType: String,
            targetFieldHint: String = "",
            autoClickSend: Boolean = false
        ): Boolean {
            // Toujours copier dans le presse-papiers en secours
            runCatching {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("LIA_Input", textToType))
            }

            val svc = instance ?: return false
            val root = runCatching { svc.rootInActiveWindow }.getOrNull() ?: return false

            val inputNode = findEditableInputNode(root, targetFieldHint)
            if (inputNode != null) {
                runCatching { inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
                runCatching { inputNode.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
                }
                val setOk = runCatching {
                    inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }.getOrDefault(false)

                if (!setOk) {
                    runCatching { inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE) }
                }

                recordAction("⌨️ Texte saisi dans le champ : « ${textToType.take(28)} »")

                if (autoClickSend) {
                    automationScope.launch {
                        delay(450L)
                        clickSendOrSubmitButton()
                    }
                }
                return true
            }
            return false
        }

        private fun findEditableInputNode(root: AccessibilityNodeInfo, hintQuery: String): AccessibilityNodeInfo? {
            // 1. Vérifier si le noeud ayant le focus d'entrée est déjà éditable
            val focused = runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
            if (focused != null && focused.isEditable) return focused

            val lowerHint = hintQuery.lowercase()
            var firstEditable: AccessibilityNodeInfo? = null
            var hintedEditable: AccessibilityNodeInfo? = null

            fun search(node: AccessibilityNodeInfo?) {
                if (node == null || hintedEditable != null) return
                val cls = node.className?.toString().orEmpty()
                if (node.isEditable || cls.contains("EditText", ignoreCase = true) || cls.contains("AutoComplete", ignoreCase = true)) {
                    if (firstEditable == null) firstEditable = node
                    if (lowerHint.isNotBlank()) {
                        val t = node.text?.toString()?.lowercase().orEmpty()
                        val d = node.contentDescription?.toString()?.lowercase().orEmpty()
                        val h = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            node.hintText?.toString()?.lowercase().orEmpty()
                        } else ""
                        if (t.contains(lowerHint) || d.contains(lowerHint) || h.contains(lowerHint)) {
                            hintedEditable = node
                            return
                        }
                    }
                }
                for (i in 0 until node.childCount) {
                    search(runCatching { node.getChild(i) }.getOrNull())
                }
            }
            search(root)
            return hintedEditable ?: firstEditable
        }

        fun clickSendOrSubmitButton(): Boolean {
            val sendKeywords = listOf(
                "Envoyer", "Send", "Rechercher", "Search", "Soumettre", "Valider",
                "send", "submit", "search_button", "btn_send", "right_icon"
            )
            for (kw in sendKeywords) {
                if (clickNodeByTextOrFallback(kw, fallbackXRatio = 0.90f, fallbackYRatio = 0.90f)) {
                    return true
                }
            }
            return false
        }

        /**
         * Exécute une action système globale (`BACK`, `HOME`, `RECENTS`, `NOTIFICATIONS`, `QUICK_SETTINGS`, `SCREENSHOT`).
         */
        fun performSystemGlobalAction(actionName: String): Boolean {
            val svc = instance ?: return false
            val globalCode = when (actionName.uppercase()) {
                "BACK", "RETOUR" -> GLOBAL_ACTION_BACK
                "HOME", "ACCUEIL" -> GLOBAL_ACTION_HOME
                "RECENTS", "RECENT_APPS" -> GLOBAL_ACTION_RECENTS
                "NOTIFICATIONS" -> GLOBAL_ACTION_NOTIFICATIONS
                "QUICK_SETTINGS" -> GLOBAL_ACTION_QUICK_SETTINGS
                "SCREENSHOT", "CAPTURE" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    GLOBAL_ACTION_TAKE_SCREENSHOT
                } else GLOBAL_ACTION_HOME
                else -> GLOBAL_ACTION_BACK
            }
            val ok = runCatching { svc.performGlobalAction(globalCode) }.getOrDefault(false)
            if (ok) {
                recordAction("🧭 Action globale exécutée : $actionName")
            }
            return ok
        }

        /**
         * Exécute une séquence multi-étapes autonome sur le téléphone (ex: Ouvrir Steam -> Attendre -> Cliquer Bibliothèque -> Scroller ;
         * ou Ouvrir l'app Gemini -> Attendre -> Saisir le prompt -> Cliquer Envoyer).
         */
        fun executeAutonomousMacroSequence(
            context: Context,
            steps: List<UiAutomationStep>,
            onComplete: ((Boolean, String) -> Unit)? = null
        ) {
            automationScope.launch {
                val appCtx = context.applicationContext
                var allOk = true
                val logBuilder = StringBuilder()

                for ((idx, step) in steps.withIndex()) {
                    when (step) {
                        is UiAutomationStep.OpenApp -> {
                            val ok = AppActionExecutor.executeTargetDirectly(
                                context = appCtx,
                                packageNameOrUri = step.packageNameOrQuery,
                                appLabel = step.appLabel.ifBlank { step.packageNameOrQuery },
                                userExplicitlyConfirmed = true
                            )
                            logBuilder.append("Étape ${idx + 1}: Ouverture ${step.appLabel.ifBlank { step.packageNameOrQuery }} ($ok) • ")
                        }
                        is UiAutomationStep.WaitMs -> {
                            delay(step.durationMs.coerceIn(100L, 10_000L))
                        }
                        is UiAutomationStep.ClickByTextOrId -> {
                            val ok = clickNodeByTextOrFallback(
                                targetText = step.queryText,
                                fallbackXRatio = step.fallbackXRatio,
                                fallbackYRatio = step.fallbackYRatio
                            )
                            allOk = allOk && ok
                            logBuilder.append("Étape ${idx + 1}: Clic « ${step.queryText} » ($ok) • ")
                        }
                        is UiAutomationStep.ClickCoordinates -> {
                            val ok = performTapAtRatio(step.xRatio, step.yRatio, step.label)
                            allOk = allOk && ok
                            logBuilder.append("Étape ${idx + 1}: Tap (${step.xRatio}, ${step.yRatio}) ($ok) • ")
                        }
                        is UiAutomationStep.SwipeGesture -> {
                            val ok = performSwipeGesture(step.direction)
                            allOk = allOk && ok
                            logBuilder.append("Étape ${idx + 1}: Swipe ${step.direction} ($ok) • ")
                        }
                        is UiAutomationStep.TypeTextIntoField -> {
                            val ok = typeTextIntoFocusedOrFirstInputField(
                                context = appCtx,
                                textToType = step.text,
                                targetFieldHint = step.targetFieldHint,
                                autoClickSend = step.autoClickSend
                            )
                            allOk = allOk && ok
                            logBuilder.append("Étape ${idx + 1}: Saisie « ${step.text.take(20)} » ($ok) • ")
                        }
                        is UiAutomationStep.GlobalSystemAction -> {
                            val ok = performSystemGlobalAction(step.actionName)
                            allOk = allOk && ok
                            logBuilder.append("Étape ${idx + 1}: Système ${step.actionName} ($ok) • ")
                        }
                    }
                }
                onComplete?.invoke(allOk, logBuilder.toString())
            }
        }

        private fun recordAction(summary: String) {
            _status.update {
                it.copy(
                    lastExecutedAction = summary,
                    totalActionsExecuted = it.totalActionsExecuted + 1
                )
            }
        }
    }
}
