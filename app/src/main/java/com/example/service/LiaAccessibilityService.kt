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

        data class InteractiveUiNodeSpec(
            val label: String,
            val viewIdResourceName: String = "",
            val className: String = "",
            val centerX: Int = 0,
            val centerY: Int = 0,
            val isClickable: Boolean = true,
            val isEditable: Boolean = false,
            val isScrollable: Boolean = false,
            val isSelectedOrChecked: Boolean = false
        )

        data class StructuredScreenSnapshot(
            val activePackageName: String,
            val allVisibleTexts: List<String> = emptyList(),
            val clickableButtonsAndTabs: List<String> = emptyList(),
            val progressAndGaugeValues: List<String> = emptyList(),
            val keyValuePairs: List<Pair<String, String>> = emptyList(),
            val interactiveNodes: List<InteractiveUiNodeSpec> = emptyList(),
            val hasEditableSearchField: Boolean = false,
            val hasSearchButtonOrBar: Boolean = false,
            val isScrollableContainerPresent: Boolean = false,
            val screenFingerprintHash: Int = 0
        )

        private val SEARCH_TRIGGER_KEYWORDS = listOf(
            "rechercher dans les parametres",
            "search settings",
            "rechercher",
            "search",
            "chercher",
            "search_action_bar",
            "search_bar",
            "action_search",
            "menu_search",
            "search_button",
            "search_src_text",
            "search_plate",
            "search_box"
        )

        /**
         * Normalise un texte d'interface (suppression des accents NFD + minuscules + espaces unifiés).
         */
        fun normalizeUiTextForMatch(text: String): String {
            if (text.isBlank()) return ""
            val decomposed = java.text.Normalizer.normalize(
                text.replace('’', '\'').trim(),
                java.text.Normalizer.Form.NFD
            )
            return decomposed.replace(Regex("\\p{Mn}+"), "")
                .lowercase()
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        /**
         * Calcule l'empreinte (hash déterministe) d'un écran à partir du package actif et des textes/jauges visibles.
         */
        fun computeScreenFingerprint(
            packageName: String,
            visibleTexts: Collection<String>,
            gauges: Collection<String> = emptyList()
        ): Int {
            if (packageName.isBlank() && visibleTexts.isEmpty() && gauges.isEmpty()) return 0
            val sig = buildString {
                append(packageName.lowercase().trim())
                append("|")
                visibleTexts.take(45).forEach {
                    append(normalizeUiTextForMatch(it))
                    append(";")
                }
                gauges.take(15).forEach {
                    append(normalizeUiTextForMatch(it))
                    append(";")
                }
            }
            val hash = sig.hashCode()
            return if (hash == 0) 1 else hash
        }

        private fun collectAllTargetWindowRoots(svc: LiaAccessibilityService): List<AccessibilityNodeInfo> {
            val roots = mutableListOf<AccessibilityNodeInfo>()
            val activeRoot = runCatching { svc.rootInActiveWindow }.getOrNull()
            val interactiveWindows = runCatching { svc.windows }.getOrNull().orEmpty()

            val windowRoots = interactiveWindows.mapNotNull { win ->
                runCatching { win.root }.getOrNull()
            }

            val allCandidateRoots = buildList {
                if (activeRoot != null) add(activeRoot)
                addAll(windowRoots)
            }

            val hasExternalAppWindow = allCandidateRoots.any { r ->
                val p = r.packageName?.toString().orEmpty()
                p.isNotBlank() && !p.contains("omniagent", ignoreCase = true) && p != svc.packageName
            }

            val seenPackagesAndBounds = mutableSetOf<String>()
            for (r in allCandidateRoots) {
                val p = r.packageName?.toString().orEmpty()
                if (hasExternalAppWindow && (p.contains("omniagent", ignoreCase = true) || p == svc.packageName)) {
                    continue
                }
                val rect = android.graphics.Rect()
                runCatching { r.getBoundsInScreen(rect) }
                val key = "$p:${rect.left},${rect.top},${rect.right},${rect.bottom}:${r.childCount}"
                if (seenPackagesAndBounds.add(key)) {
                    roots.add(r)
                }
            }

            if (roots.isEmpty() && activeRoot != null) {
                roots.add(activeRoot)
            }
            return roots
        }

        fun hasClickableAncestor(node: AccessibilityNodeInfo?, maxDepth: Int = 5): Boolean {
            return findClickableAncestor(node, maxDepth) != null
        }

        private fun findClickableAncestor(node: AccessibilityNodeInfo?, maxDepth: Int = 5): AccessibilityNodeInfo? {
            var curr = node
            var depth = 0
            while (curr != null && depth <= maxDepth) {
                if (curr.isClickable) return curr
                curr = runCatching { curr.parent }.getOrNull()
                depth++
            }
            return null
        }

        private fun collectDirectOrShallowTexts(container: AccessibilityNodeInfo, maxDepth: Int = 3): List<String> {
            val found = mutableListOf<String>()
            fun walk(curr: AccessibilityNodeInfo?, depth: Int) {
                if (curr == null || depth > maxDepth || found.size >= 6) return
                val t = curr.text?.toString()?.trim().orEmpty()
                val d = curr.contentDescription?.toString()?.trim().orEmpty()
                val s = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    curr.stateDescription?.toString()?.trim().orEmpty()
                } else ""
                val primary = t.ifBlank { d }.ifBlank { s }
                if (primary.length in 1..120 && primary !in found) {
                    found.add(primary)
                }
                for (i in 0 until curr.childCount) {
                    walk(runCatching { curr.getChild(i) }.getOrNull(), depth + 1)
                }
            }
            walk(container, 0)
            return found
        }

        /**
         * Capture une vue structurée complète de l'écran actif (multi-fenêtres & hiérarchie profonde) :
         * - Tous les textes, hintText, stateDescription et descriptions d'éléments (jusqu'à 180 noeuds)
         * - Les barres de progression / jauges graphiques (`rangeInfo` ex: batterie 78%)
         * - Les paires Clé -> Valeur adjacentes et agrégées par ligne cliquable (ex: "Batterie" -> "85%")
         * - La liste des onglets, lignes de préférences et boutons cliquables (ancêtres jusqu'à 5 niveaux)
         * - Détection des barres/boutons de recherche, conteneurs scrollables et empreinte d'écran (`screenFingerprintHash`)
         */
        fun readStructuredScreenSnapshot(): StructuredScreenSnapshot {
            val svc = instance ?: return StructuredScreenSnapshot("", emptyList(), emptyList(), emptyList(), emptyList())
            val roots = collectAllTargetWindowRoots(svc)
            if (roots.isEmpty()) {
                return StructuredScreenSnapshot(status.value.activeWindowPackage, emptyList(), emptyList(), emptyList(), emptyList())
            }

            val primaryRoot = roots.firstOrNull {
                val p = it.packageName?.toString().orEmpty()
                p.isNotBlank() && !p.contains("omniagent", ignoreCase = true)
            } ?: roots.first()

            val pkg = primaryRoot.packageName?.toString().orEmpty().ifBlank { status.value.activeWindowPackage }
            val texts = LinkedHashSet<String>()
            val clickables = LinkedHashSet<String>()
            val gauges = LinkedHashSet<String>()
            val pairs = mutableListOf<Pair<String, String>>()
            val interactiveSpecs = mutableListOf<InteractiveUiNodeSpec>()
            var hasEditableSearch = false
            var hasSearchTrigger = false
            var hasScrollable = false
            var previousLabel = ""

            fun traverse(node: AccessibilityNodeInfo?) {
                if (node == null || texts.size >= 180) return

                val t = node.text?.toString()?.trim().orEmpty()
                val d = node.contentDescription?.toString()?.trim().orEmpty()
                val hint = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    node.hintText?.toString()?.trim().orEmpty()
                } else ""
                val stateDesc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    node.stateDescription?.toString()?.trim().orEmpty()
                } else ""
                val viewIdFull = node.viewIdResourceName?.trim().orEmpty()
                val viewIdShort = viewIdFull.substringAfterLast('/')
                val cls = node.className?.toString().orEmpty()

                val isEdit = node.isEditable ||
                    cls.contains("EditText", ignoreCase = true) ||
                    cls.contains("AutoComplete", ignoreCase = true)
                if (node.isScrollable) {
                    hasScrollable = true
                }

                val normCombinedMeta = normalizeUiTextForMatch("$t $d $hint $viewIdShort")
                val matchesSearchMeta = SEARCH_TRIGGER_KEYWORDS.any { kw ->
                    normCombinedMeta.contains(normalizeUiTextForMatch(kw))
                }
                if (isEdit && (matchesSearchMeta || hint.isNotBlank() || t.isNotBlank() || viewIdShort.isNotBlank())) {
                    hasEditableSearch = true
                }
                if (matchesSearchMeta) {
                    hasSearchTrigger = true
                }

                val combined = when {
                    t.isNotBlank() && stateDesc.isNotBlank() && !t.equals(stateDesc, ignoreCase = true) -> "$t ($stateDesc)"
                    t.isNotBlank() && d.isNotBlank() && !t.equals(d, ignoreCase = true) -> "$t ($d)"
                    t.isNotBlank() -> t
                    d.isNotBlank() -> d
                    stateDesc.isNotBlank() -> stateDesc
                    hint.isNotBlank() -> hint
                    else -> ""
                }

                // Lecture des jauges et ProgressBar (ex: jauge de batterie véhicule ou téléchargement)
                val range = node.rangeInfo
                if (range != null && range.max > range.min) {
                    val pct = (((range.current - range.min) / (range.max - range.min)) * 100f).toInt().coerceIn(0, 100)
                    val gaugeLabel = if (combined.isNotBlank()) "$combined : $pct%" else "Jauge : $pct%"
                    gauges.add(gaugeLabel)
                    texts.add(gaugeLabel)
                }

                // Agrégation des lignes cliquables (ex: ligne Preference Android Settings "Batterie" + "85% - En charge")
                if (node.isClickable && node.childCount >= 1) {
                    val rowTexts = collectDirectOrShallowTexts(node, maxDepth = 3)
                    if (rowTexts.isNotEmpty()) {
                        val primaryRowTitle = rowTexts.first()
                        clickables.add(primaryRowTitle)
                        if (rowTexts.size >= 2) {
                            val summaryText = rowTexts.drop(1).joinToString(" • ")
                            val aggregatedRow = "$primaryRowTitle — $summaryText"
                            if (aggregatedRow.length <= 160) {
                                texts.add(aggregatedRow)
                                clickables.add(aggregatedRow)
                            }
                            if (pairs.none { it.first == primaryRowTitle && it.second == summaryText }) {
                                pairs.add(primaryRowTitle to summaryText)
                            }
                        }
                    }
                }

                val clickableAncestor = findClickableAncestor(node, maxDepth = 5)
                if (combined.length in 1..140) {
                    texts.add(combined)
                    val clickLabel = t.ifBlank { d }.ifBlank { hint }
                    if (clickableAncestor != null && clickLabel.isNotBlank()) {
                        clickables.add(clickLabel)
                    }
                    if (previousLabel.isNotBlank() &&
                        previousLabel != combined &&
                        (combined.any { it.isDigit() } || combined.contains("%") || combined.contains("km", ignoreCase = true))
                    ) {
                        if (pairs.none { it.first == previousLabel && it.second == combined }) {
                            pairs.add(previousLabel to combined)
                        }
                    }
                    previousLabel = combined
                } else if (matchesSearchMeta && (node.isClickable || clickableAncestor != null)) {
                    val fallbackSearchLabel = d.ifBlank { hint }.ifBlank { viewIdShort }.ifBlank { "Rechercher" }
                    clickables.add(fallbackSearchLabel)
                }

                if ((clickableAncestor != null || isEdit || node.isScrollable) && interactiveSpecs.size < 80) {
                    val rect = android.graphics.Rect()
                    runCatching { (clickableAncestor ?: node).getBoundsInScreen(rect) }
                    val specLabel = combined.ifBlank { viewIdShort }
                    if (specLabel.isNotBlank()) {
                        interactiveSpecs.add(
                            InteractiveUiNodeSpec(
                                label = specLabel,
                                viewIdResourceName = viewIdFull,
                                className = cls,
                                centerX = rect.centerX(),
                                centerY = rect.centerY(),
                                isClickable = clickableAncestor != null,
                                isEditable = isEdit,
                                isScrollable = node.isScrollable,
                                isSelectedOrChecked = node.isSelected || node.isChecked
                            )
                        )
                    }
                }

                val count = node.childCount
                for (i in 0 until count) {
                    if (texts.size >= 180) break
                    traverse(runCatching { node.getChild(i) }.getOrNull())
                }
            }

            for (root in roots) {
                traverse(root)
            }

            val fingerprint = computeScreenFingerprint(pkg, texts, gauges)
            return StructuredScreenSnapshot(
                activePackageName = pkg,
                allVisibleTexts = texts.toList(),
                clickableButtonsAndTabs = clickables.toList(),
                progressAndGaugeValues = gauges.toList(),
                keyValuePairs = pairs.take(35),
                interactiveNodes = interactiveSpecs,
                hasEditableSearchField = hasEditableSearch,
                hasSearchButtonOrBar = hasSearchTrigger || hasEditableSearch,
                isScrollableContainerPresent = hasScrollable,
                screenFingerprintHash = fingerprint
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
            val norm = normalizeUiTextForMatch(label)
            return CRITICAL_IRREVERSIBLE_KEYWORDS.any {
                lower.contains(it) || norm.contains(normalizeUiTextForMatch(it))
            }
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
         * Score d'adéquation (insensible aux accents) entre une requête de clic et un noeud d'interface.
         */
        fun scoreNodeTextMatch(
            query: String,
            nodeText: String,
            contentDesc: String = "",
            hintText: String = "",
            viewId: String = "",
            hasClickableSelfOrAncestor: Boolean = false,
            hasVisibleBounds: Boolean = true
        ): Int {
            val normQuery = normalizeUiTextForMatch(query).substringBefore(" — ").trim()
            if (normQuery.isBlank()) return 0

            val normText = normalizeUiTextForMatch(nodeText)
            val normDesc = normalizeUiTextForMatch(contentDesc)
            val normHint = normalizeUiTextForMatch(hintText)
            val normId = normalizeUiTextForMatch(viewId.substringAfterLast('/'))

            var baseScore = 0
            if (normText == normQuery || normDesc == normQuery || normHint == normQuery) {
                baseScore = 100
            } else if (
                normText.startsWith("$normQuery ") || normText.endsWith(" $normQuery") || normText.contains(" $normQuery ") ||
                normDesc.startsWith("$normQuery ") || normDesc.endsWith(" $normQuery") || normDesc.contains(" $normQuery ") ||
                normText.startsWith(normQuery) || normDesc.startsWith(normQuery)
            ) {
                baseScore = 75
            } else if (normText.contains(normQuery) || normDesc.contains(normQuery) || normHint.contains(normQuery)) {
                baseScore = 50
            } else if (normId.isNotBlank() && (normId == normQuery || normId.contains(normQuery) || normQuery.contains(normId))) {
                baseScore = 40
            }

            if (baseScore == 0) return 0
            if (hasClickableSelfOrAncestor) baseScore += 30
            if (hasVisibleBounds) baseScore += 20
            return baseScore
        }

        /**
         * Clique de manière stricte sur un élément correspondant à [targetText] SANS jamais taper au hasard au centre de l'écran (0.5f, 0.5f).
         */
        fun clickNodeByTextStrict(
            targetText: String,
            allowCriticalIrreversible: Boolean = false
        ): Boolean {
            return clickNodeByTextOrFallback(
                targetText = targetText,
                fallbackXRatio = 0.5f,
                fallbackYRatio = 0.5f,
                allowCriticalIrreversible = allowCriticalIrreversible,
                allowCoordinateFallback = false
            )
        }

        /**
         * Clique sur un élément de l'écran correspondant à [targetText] (ex: "Bibliothèque", "Batterie", "Envoyer", etc.).
         * Si non trouvé sous forme de noeud accessible et que [allowCoordinateFallback] vaut true, effectue un tap physique aux coordonnées de secours.
         */
        fun clickNodeByTextOrFallback(
            targetText: String,
            fallbackXRatio: Float = 0.5f,
            fallbackYRatio: Float = 0.5f,
            allowCriticalIrreversible: Boolean = false,
            allowCoordinateFallback: Boolean = true
        ): Boolean {
            val svc = instance ?: return false
            val cleaned = targetText.trim()
            if (cleaned.isBlank()) return false
            if (!allowCriticalIrreversible && isCriticalIrreversibleAction(cleaned)) {
                recordAction("🛡️ Clic critique bloqué en attente de confirmation : « $cleaned »")
                return false
            }

            val roots = collectAllTargetWindowRoots(svc)
            val matchedNode = roots.firstNotNullOfOrNull { root ->
                findBestMatchingNode(root, cleaned)
            }

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

            if (!allowCoordinateFallback) {
                return false
            }

            // Fallback : Tap physique par coordonnées via GestureDescription
            return performTapAtRatio(fallbackXRatio, fallbackYRatio, "Clic coordonnées pour « $cleaned »")
        }

        private fun findBestMatchingNode(root: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
            val cleanQuery = query.substringBefore(" — ").trim()
            if (cleanQuery.isBlank()) return null

            var bestNode: AccessibilityNodeInfo? = null
            var bestScore = 0

            fun evaluateCandidate(node: AccessibilityNodeInfo?) {
                if (node == null) return
                val t = node.text?.toString()?.trim().orEmpty()
                val d = node.contentDescription?.toString()?.trim().orEmpty()
                val hint = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    node.hintText?.toString()?.trim().orEmpty()
                } else ""
                val id = node.viewIdResourceName?.trim().orEmpty()
                val hasClickable = findClickableAncestor(node, maxDepth = 5) != null
                val rect = android.graphics.Rect()
                runCatching { node.getBoundsInScreen(rect) }
                val hasBounds = !rect.isEmpty && node.isVisibleToUser

                val score = scoreNodeTextMatch(
                    query = cleanQuery,
                    nodeText = t,
                    contentDesc = d,
                    hintText = hint,
                    viewId = id,
                    hasClickableSelfOrAncestor = hasClickable,
                    hasVisibleBounds = hasBounds
                )
                if (score > bestScore) {
                    bestScore = score
                    bestNode = node
                }
            }

            // 1. Recherche native Android par texte (évaluée avec notre score)
            val byText = runCatching { root.findAccessibilityNodeInfosByText(cleanQuery) }.getOrNull().orEmpty()
            for (candidate in byText) {
                evaluateCandidate(candidate)
            }

            // 2. Parcours récursif complet (insensible aux accents, inspectant text, contentDescription, hintText et viewId)
            fun search(node: AccessibilityNodeInfo?) {
                if (node == null) return
                evaluateCandidate(node)
                for (i in 0 until node.childCount) {
                    search(runCatching { node.getChild(i) }.getOrNull())
                }
            }
            search(root)
            return bestNode
        }

        private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
            var curr: AccessibilityNodeInfo? = node
            var depth = 0
            while (curr != null && depth <= 6) {
                if (curr.isClickable) {
                    val ok = runCatching { curr.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
                    if (ok) return true
                }
                curr = runCatching { curr.parent }.getOrNull()
                depth++
            }
            // Sinon clic aux coordonnées du rectangle de l'élément (ou de son premier ancêtre visible avec des bornes non vides)
            var boundsCursor: AccessibilityNodeInfo? = node
            var boundsDepth = 0
            while (boundsCursor != null && boundsDepth <= 6) {
                val rect = android.graphics.Rect()
                runCatching { boundsCursor.getBoundsInScreen(rect) }
                if (!rect.isEmpty && rect.centerX() > 0 && rect.centerY() > 0) {
                    return performTapExact(rect.centerX().toFloat(), rect.centerY().toFloat(), "Tap sur noeud")
                }
                boundsCursor = runCatching { boundsCursor.parent }.getOrNull()
                boundsDepth++
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

        private fun findPrimaryScrollableNode(roots: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
            for (root in roots) {
                var found: AccessibilityNodeInfo? = null
                fun walk(node: AccessibilityNodeInfo?) {
                    if (node == null || found != null) return
                    if (node.isScrollable && node.isVisibleToUser) {
                        found = node
                        return
                    }
                    for (i in 0 until node.childCount) {
                        walk(runCatching { node.getChild(i) }.getOrNull())
                    }
                }
                walk(root)
                if (found != null) return found
            }
            return null
        }

        /**
         * Exécute un défilement hybride (Sémantique `ACTION_SCROLL_FORWARD`/`ACTION_SCROLL_BACKWARD` sur conteneur scrollable
         * + fallback geste physique `GestureDescription` `SWIPE_UP`, `SWIPE_DOWN`, `SWIPE_LEFT`, `SWIPE_RIGHT`).
         */
        fun performSwipeGesture(direction: String): Boolean {
            val svc = instance ?: return false
            val dirUpper = direction.uppercase().trim()
            val roots = collectAllTargetWindowRoots(svc)

            // 1. Tentative de défilement sémantique Android sur le conteneur scrollable principal
            if (dirUpper in setOf("SWIPE_UP", "SCROLL_DOWN", "DOWN")) {
                val scrollable = findPrimaryScrollableNode(roots)
                if (scrollable != null) {
                    val scrolledSemantically = runCatching {
                        scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    }.getOrDefault(false)
                    if (scrolledSemantically) {
                        recordAction("↕️ Scroll sémantique (ACTION_SCROLL_FORWARD) exécuté")
                        return true
                    }
                }
            } else if (dirUpper in setOf("SWIPE_DOWN", "SCROLL_UP", "UP")) {
                val scrollable = findPrimaryScrollableNode(roots)
                if (scrollable != null) {
                    val scrolledSemantically = runCatching {
                        scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    }.getOrDefault(false)
                    if (scrolledSemantically) {
                        recordAction("↕️ Scroll sémantique (ACTION_SCROLL_BACKWARD) exécuté")
                        return true
                    }
                }
            }

            // 2. Fallback geste physique via GestureDescription
            val metrics = svc.resources.displayMetrics
            val w = metrics.widthPixels.toFloat()
            val h = metrics.heightPixels.toFloat()

            val (startX, startY, endX, endY) = when (dirUpper) {
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
         * Active la barre de recherche interne de l'application ouverte (en cliquant sur l'icône/barre de recherche
         * si le champ n'est pas encore déployé), y saisit [query] et soumet la recherche (`ACTION_IME_ENTER` + bouton rechercher).
         */
        fun activateAndTypeInSearchBar(context: Context, query: String): Boolean {
            val cleanedQuery = query.trim()
            if (cleanedQuery.isBlank()) return false

            runCatching {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("LIA_Search_Query", cleanedQuery))
            }

            val svc = instance ?: return false
            var roots = collectAllTargetWindowRoots(svc)
            var inputNode = roots.firstNotNullOfOrNull { findEditableInputNode(it, "recherch") }
                ?: roots.firstNotNullOfOrNull { findEditableInputNode(it, "search") }
                ?: roots.firstNotNullOfOrNull { findEditableInputNode(it, "") }

            if (inputNode == null) {
                val searchBarCandidates = listOf(
                    "Rechercher dans les paramètres",
                    "Search settings",
                    "Rechercher",
                    "Search",
                    "Chercher",
                    "search_action_bar",
                    "search_bar",
                    "action_search",
                    "menu_search",
                    "search_button",
                    "search_src_text",
                    "search_plate"
                )
                val clickedTrigger = searchBarCandidates.any { candidate ->
                    clickNodeByTextStrict(candidate)
                }
                if (!clickedTrigger) {
                    return false
                }
                // Attendre brièvement l'ouverture du champ de saisie (SearchView / SearchActivity)
                if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                    runCatching { Thread.sleep(600L) }
                }
                roots = collectAllTargetWindowRoots(svc)
                inputNode = roots.firstNotNullOfOrNull { findEditableInputNode(it, "recherch") }
                    ?: roots.firstNotNullOfOrNull { findEditableInputNode(it, "search") }
                    ?: roots.firstNotNullOfOrNull { findEditableInputNode(it, "") }

                if (inputNode == null) {
                    // Si appelé depuis le thread principal, planifier l'injection dès que le champ apparaît (~550ms)
                    automationScope.launch {
                        delay(550L)
                        val delayedRoots = collectAllTargetWindowRoots(svc)
                        val delayedInput = delayedRoots.firstNotNullOfOrNull { findEditableInputNode(it, "") }
                        if (delayedInput != null) {
                            injectAndSubmitSearchQueryOnNode(delayedInput, cleanedQuery)
                        }
                    }
                    recordAction("🔍 Barre de recherche ouverte pour « $cleanedQuery »")
                    return true
                }
            }

            return injectAndSubmitSearchQueryOnNode(inputNode, cleanedQuery)
        }

        private fun injectAndSubmitSearchQueryOnNode(inputNode: AccessibilityNodeInfo, query: String): Boolean {
            runCatching { inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
            runCatching { inputNode.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
            }
            val setOk = runCatching {
                inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }.getOrDefault(false)

            if (!setOk) {
                runCatching { inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE) }
            }

            // Soumettre via ACTION_IME_ENTER (API 30+) puis bouton de validation si présent
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                runCatching {
                    inputNode.performAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
                    )
                }
            }
            clickSendOrSubmitButton()
            recordAction("🔍 Recherche interne soumise : « ${query.take(28)} »")
            return true
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
            val roots = collectAllTargetWindowRoots(svc)
            val inputNode = roots.firstNotNullOfOrNull { findEditableInputNode(it, targetFieldHint) }
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

                if (autoClickSend && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    runCatching {
                        inputNode.performAction(
                            AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
                        )
                    }
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

            val normHint = normalizeUiTextForMatch(hintQuery)
            var firstEditable: AccessibilityNodeInfo? = null
            var hintedEditable: AccessibilityNodeInfo? = null

            fun search(node: AccessibilityNodeInfo?) {
                if (node == null || hintedEditable != null) return
                val cls = node.className?.toString().orEmpty()
                if (node.isEditable || cls.contains("EditText", ignoreCase = true) || cls.contains("AutoComplete", ignoreCase = true)) {
                    if (firstEditable == null) firstEditable = node
                    if (normHint.isNotBlank()) {
                        val t = normalizeUiTextForMatch(node.text?.toString().orEmpty())
                        val d = normalizeUiTextForMatch(node.contentDescription?.toString().orEmpty())
                        val h = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            normalizeUiTextForMatch(node.hintText?.toString().orEmpty())
                        } else ""
                        val id = normalizeUiTextForMatch(node.viewIdResourceName?.substringAfterLast('/').orEmpty())
                        if (t.contains(normHint) || d.contains(normHint) || h.contains(normHint) || id.contains(normHint)) {
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
                if (clickNodeByTextOrFallback(
                        targetText = kw,
                        fallbackXRatio = 0.90f,
                        fallbackYRatio = 0.90f,
                        allowCriticalIrreversible = false,
                        allowCoordinateFallback = false
                    )
                ) {
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
