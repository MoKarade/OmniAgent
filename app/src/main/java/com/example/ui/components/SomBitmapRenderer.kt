package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SecurityPosture
import com.example.model.SomElement
import com.example.model.TargetedSomOverlay
import com.example.model.VirtualDeviceScreen
import com.example.ui.theme.ActionEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OmniCyan
import com.example.ui.theme.SecurityCrimson
import com.example.ui.theme.ThoughtAmber

/**
 * Generates an actual Android Bitmap with Set-of-Mark (SoM / OmniParser v2) bounding boxes
 * and numeric tags [1]..[N] so Gemini 4 Argon receives true multimodal visual grounding.
 */
object SomBitmapRenderer {
    fun renderAnnotatedScreenshot(
        screen: VirtualDeviceScreen,
        highlightedElementId: Int? = null,
        widthPx: Int = 540,
        heightPx: Int = 960
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw base background or custom uploaded screenshot
        if (screen.customBitmap != null) {
            val destRect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
            canvas.drawBitmap(screen.customBitmap, null, destRect, null)
        } else {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#0D1529")
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), bgPaint)

            // Draw status bar & app header
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#182546")
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx * 0.07f, headerPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                textSize = 24f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            canvas.drawText(screen.screenTitle, 24f, heightPx * 0.045f, titlePaint)
        }

        // 2. Draw each Set-of-Mark element and its numeric tag [ID]
        val boxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val boxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val targetCrosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#00E676")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }
        val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.BLACK
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val contentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#EAF2FF")
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val contentAreaTop = heightPx * 0.08f
        val contentAreaHeight = heightPx * 0.84f

        screen.elements.forEach { el ->
            val left = el.normLeft * widthPx
            val top = contentAreaTop + el.normTop * contentAreaHeight
            val right = el.normRight * widthPx
            val bottom = contentAreaTop + el.normBottom * contentAreaHeight
            val rect = RectF(left, top, right, bottom)

            val isHighlighted = el.id == highlightedElementId
            val strokeColor = when {
                isHighlighted -> AndroidColor.parseColor("#00E676")
                el.containsAdversarialPayload -> AndroidColor.parseColor("#FF3D71")
                el.isSensitiveFinancialOrDestructive -> AndroidColor.parseColor("#FFB300")
                else -> AndroidColor.parseColor("#00E5FF")
            }

            if (screen.customBitmap == null) {
                boxFillPaint.color = if (isHighlighted) {
                    AndroidColor.parseColor("#11382A")
                } else {
                    AndroidColor.parseColor("#1B2848")
                }
                canvas.drawRoundRect(rect, 16f, 16f, boxFillPaint)
            }

            boxStrokePaint.color = strokeColor
            boxStrokePaint.strokeWidth = if (isHighlighted) 7f else 4f
            canvas.drawRoundRect(rect, 16f, 16f, boxStrokePaint)

            if (isHighlighted) {
                val cx = (left + right) / 2f
                val cy = (top + bottom) / 2f
                canvas.drawLine(0f, cy, widthPx.toFloat(), cy, targetCrosshairPaint)
                canvas.drawLine(cx, contentAreaTop, cx, contentAreaTop + contentAreaHeight, targetCrosshairPaint)
            }

            // Element label & value inside box
            val labelSnippet = "${el.label}: ${el.currentText}".take(52)
            canvas.drawText(labelSnippet, left + 18f, top + 58f, contentTextPaint)

            // Draw Set-of-Mark numeric badge [id] at top-left corner of bounding box
            val badgeText = if (isHighlighted) "🎯 [${el.id}]" else "[${el.id}]"
            val badgeWidth = tagTextPaint.measureText(badgeText) + 20f
            val badgeRect = RectF(left, top, left + badgeWidth, top + 34f)
            tagBgPaint.color = strokeColor
            canvas.drawRoundRect(badgeRect, 8f, 8f, tagBgPaint)
            canvas.drawText(badgeText, left + 10f, top + 25f, tagTextPaint)
        }

        return bitmap
    }
}

@Composable
fun InteractiveSomPhoneViewport(
    screen: VirtualDeviceScreen,
    showSomAnnotations: Boolean,
    lastTargetedElementId: Int?,
    targetedOverlay: TargetedSomOverlay? = null,
    onElementClick: (SomElement) -> Unit,
    onGlobalNavClick: (String) -> Unit,
    onClearTargetOverlay: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Resolve the currently targeted element on this screen (if any)
    val activeTargetId = targetedOverlay?.elementId ?: lastTargetedElementId
    val targetedElement = screen.elements.find { it.id == activeTargetId }

    val infiniteTransition = rememberInfiniteTransition(label = "som_target_pulse")
    val pulseFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_fraction"
    )
    val scanlineProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanline_progress"
    )

    val overlayAccentColor = when (targetedOverlay?.securityPosture) {
        SecurityPosture.HUMAN_CONFIRMATION_REQUIRED -> ThoughtAmber
        SecurityPosture.INJECTION_NEUTRALIZED -> SecurityCrimson
        SecurityPosture.LOOP_RECOVERY -> OmniCyan
        else -> if (targetedOverlay?.isPlanningPhase == true) OmniCyan else ActionEmerald
    }

    Surface(
        modifier = modifier
            .border(
                width = if (targetedElement != null) 2.5.dp else 2.dp,
                color = if (targetedElement != null) overlayAccentColor else OmniCyan.copy(alpha = 0.55f),
                shape = RoundedCornerShape(28.dp)
            )
            .clip(RoundedCornerShape(28.dp)),
        color = ObsidianBackground,
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Smartphone Status & App Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = screen.screenTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${screen.packageName} • ${screen.statusBanner}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (targetedElement != null) {
                        Surface(
                            color = overlayAccentColor.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, overlayAccentColor),
                            modifier = Modifier.testTag("som_active_target_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GpsFixed,
                                    contentDescription = "Cible SoM Active",
                                    tint = overlayAccentColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "CIBLE #${targetedElement.id}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = overlayAccentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Surface(
                        color = OmniCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "SoM v2 (${screen.elements.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = OmniCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Viewport Canvas + Interactive Set-of-Mark Elements + Target Reticle Overlay
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0E162B))
            ) {
                if (screen.customBitmap != null) {
                    Image(
                        bitmap = screen.customBitmap.asImageBitmap(),
                        contentDescription = "Capture d'écran importée",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Subtle technical HUD grid background
                ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                    val step = 36.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = OmniCyan.copy(alpha = 0.05f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1f
                        )
                        x += step
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = OmniCyan.copy(alpha = 0.05f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                        y += step
                    }
                }

                // Render each interactive UI element inside the virtual phone screen
                screen.elements.forEach { element ->
                    val leftDp = maxWidth * element.normLeft
                    val topDp = maxHeight * element.normTop
                    val boxWidthDp = maxWidth * (element.normRight - element.normLeft)
                    val boxHeightDp = maxHeight * (element.normBottom - element.normTop)

                    val isTarget = element.id == targetedElement?.id
                    val isDimmedByOtherTarget = targetedElement != null && !isTarget

                    val boxColor = when {
                        isTarget -> overlayAccentColor
                        element.containsAdversarialPayload -> SecurityCrimson
                        element.isSensitiveFinancialOrDestructive -> ThoughtAmber
                        else -> OmniCyan
                    }

                    val contentAlpha = if (isDimmedByOtherTarget) 0.45f else 1f

                    Box(
                        modifier = Modifier
                            .offset(x = leftDp, y = topDp)
                            .width(boxWidthDp)
                            .height(boxHeightDp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isTarget -> overlayAccentColor.copy(alpha = 0.16f + 0.10f * pulseFraction)
                                    screen.customBitmap != null -> boxColor.copy(alpha = 0.10f * contentAlpha)
                                    else -> Color(0xFF172240).copy(alpha = 0.90f * contentAlpha)
                                }
                            )
                            .border(
                                width = if (isTarget) 2.5.dp else 1.5.dp,
                                color = if (showSomAnnotations || isTarget) {
                                    boxColor.copy(alpha = contentAlpha)
                                } else {
                                    Color.White.copy(alpha = 0.2f * contentAlpha)
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onElementClick(element) }
                            .testTag("som_element_${element.id}")
                            .padding(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (showSomAnnotations || isTarget) {
                                    Surface(
                                        color = boxColor.copy(alpha = contentAlpha),
                                        shape = RoundedCornerShape(5.dp)
                                    ) {
                                        Text(
                                            text = if (isTarget) "🎯 #${element.id}" else "#${element.id}",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color.Black,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = element.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = boxColor.copy(alpha = contentAlpha),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (element.containsAdversarialPayload) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Injection Adversariale",
                                        tint = SecurityCrimson.copy(alpha = contentAlpha),
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else if (element.isSensitiveFinancialOrDestructive) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = "Action Sensible HITL",
                                        tint = ThoughtAmber.copy(alpha = contentAlpha),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            if (element.currentText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = element.currentText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = contentAlpha),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                // 3. DEDICATED OMNIPARSER SoM TARGET HIGHLIGHT OVERLAY (Reticle, Crosshairs, Scanline & Floating Callout)
                if (targetedElement != null) {
                    SomTargetHighlightOverlay(
                        targetElement = targetedElement,
                        targetedOverlay = targetedOverlay,
                        accentColor = overlayAccentColor,
                        pulseFraction = pulseFraction,
                        scanlineProgress = scanlineProgress,
                        viewportWidth = maxWidth,
                        viewportHeight = maxHeight,
                        onDismiss = onClearTargetOverlay
                    )
                }
            }

            // Global OS Navigation Bar (BACK / HOME / RECENTS)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface)
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onGlobalNavClick("BACK") },
                    modifier = Modifier.testTag("os_nav_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Action Globale BACK",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = { onGlobalNavClick("HOME") },
                    modifier = Modifier.testTag("os_nav_home")
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Action Globale HOME",
                        tint = OmniCyan
                    )
                }
                IconButton(
                    onClick = { onGlobalNavClick("RECENTS") },
                    modifier = Modifier.testTag("os_nav_recents")
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Action Globale RECENTS",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Renders the animated OmniParser Set-of-Mark (SoM) Target Acquisition Overlay:
 * - Full-viewport dashed crosshair lines intersecting the targeted element's center
 * - Expanding sonar pulse halo + sweeping horizontal laser scanline inside the bounding box
 * - Tactical L-shaped corner lock brackets around the targeted element
 * - Floating HUD callout card showing the SoM index, planned atomic action, and subgoal
 */
@Composable
private fun SomTargetHighlightOverlay(
    targetElement: SomElement,
    targetedOverlay: TargetedSomOverlay?,
    accentColor: Color,
    pulseFraction: Float,
    scanlineProgress: Float,
    viewportWidth: Dp,
    viewportHeight: Dp,
    onDismiss: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("som_target_overlay_layer")
    ) {
        // A. Tactical Reticle, Crosshair & Scanline Canvas
        ComposeCanvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("som_target_reticle_${targetElement.id}")
        ) {
            val left = size.width * targetElement.normLeft
            val top = size.height * targetElement.normTop
            val right = size.width * targetElement.normRight
            val bottom = size.height * targetElement.normBottom
            val boxWidth = right - left
            val boxHeight = bottom - top
            val centerX = (left + right) / 2f
            val centerY = (top + bottom) / 2f

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)

            // 1. Full-viewport dashed crosshair lines leading to the target bounding box
            drawLine(
                color = accentColor.copy(alpha = 0.55f),
                start = Offset(0f, centerY),
                end = Offset(left, centerY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = dashEffect
            )
            drawLine(
                color = accentColor.copy(alpha = 0.55f),
                start = Offset(right, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = dashEffect
            )
            drawLine(
                color = accentColor.copy(alpha = 0.55f),
                start = Offset(centerX, 0f),
                end = Offset(centerX, top),
                strokeWidth = 2.dp.toPx(),
                pathEffect = dashEffect
            )
            drawLine(
                color = accentColor.copy(alpha = 0.55f),
                start = Offset(centerX, bottom),
                end = Offset(centerX, size.height),
                strokeWidth = 2.dp.toPx(),
                pathEffect = dashEffect
            )

            // 2. Expanding outer sonar pulse ring around the targeted SoM bounding box
            val expandPx = 10.dp.toPx() * pulseFraction
            drawRoundRect(
                color = accentColor.copy(alpha = 0.45f * (1f - pulseFraction * 0.5f)),
                topLeft = Offset(left - expandPx, top - expandPx),
                size = Size(boxWidth + expandPx * 2, boxHeight + expandPx * 2),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )

            // 3. Sweeping horizontal laser scanline inside the targeted element
            val scanY = top + boxHeight * scanlineProgress
            drawLine(
                color = accentColor.copy(alpha = 0.75f),
                start = Offset(left + 4.dp.toPx(), scanY),
                end = Offset(right - 4.dp.toPx(), scanY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 4. Heavy Cybernetic L-Shaped Corner Brackets
            val bracketLen = minOf(boxWidth, boxHeight) * 0.28f
            val strokePx = 4.dp.toPx()
            val pad = 3.dp.toPx()

            // Top-Left Bracket
            drawLine(
                color = accentColor,
                start = Offset(left - pad, top - pad),
                end = Offset(left - pad + bracketLen, top - pad),
                strokeWidth = strokePx,
                cap = StrokeCap.Square
            )
            drawLine(
                color = accentColor,
                start = Offset(left - pad, top - pad),
                end = Offset(left - pad, top - pad + bracketLen),
                strokeWidth = strokePx,
                cap = StrokeCap.Square
            )

            // Top-Right Bracket
            drawLine(
                color = accentColor,
                start = Offset(right + pad, top - pad),
                end = Offset(right + pad - bracketLen, top - pad),
                strokeWidth = strokePx,
                cap = StrokeCap.Square
            )
            drawLine(
                color = accentColor,
                start = Offset(right + pad, top - pad),
                end = Offset(right + pad, top - pad + bracketLen),
                strokeWidth = strokePx,
                cap = StrokeCap.Square
            )

            // Bottom-Left Bracket
            drawLine(
                color = accentColor,
                start = Offset(left - pad, bottom + pad),
                end = Offset(left - pad + bracketLen, bottom + pad),
                strokeWidth = strokePx,
                cap = StrokeCap.Square
            )
            drawLine(
                color = accentColor,
                start = Offset(left - pad, bottom + pad),
                end = Offset(left - pad, bottom + pad - bracketLen),
                strokeWidth = strokePx,
                cap = StrokeCap.Square
            )

            // Bottom-Right Bracket
            drawLine(
                color = accentColor,
                start = Offset(right + pad, bottom + pad),
                end = Offset(right + pad - bracketLen, bottom + pad),
                strokeWidth = strokePx,
                cap = StrokeCap.Square
            )
            drawLine(
                color = accentColor,
                start = Offset(right + pad, bottom + pad),
                end = Offset(right + pad, bottom + pad - bracketLen),
                strokeWidth = strokePx,
                cap = StrokeCap.Square
            )

            // 5. Center Bullseye Target Dot
            drawCircle(
                color = accentColor,
                radius = 5.dp.toPx(),
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.35f),
                radius = 12.dp.toPx() * (0.7f + 0.5f * pulseFraction),
                center = Offset(centerX, centerY),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // B. Floating HUD Callout Banner positioned dynamically above or below the targeted element
        val placeCalloutBelow = targetElement.normTop < 0.32f
        val calloutTopDp = if (placeCalloutBelow) {
            (viewportHeight * targetElement.normBottom + 8.dp).coerceAtMost(viewportHeight - 96.dp)
        } else {
            (viewportHeight * targetElement.normTop - 88.dp).coerceAtLeast(6.dp)
        }

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .offset(x = 12.dp, y = calloutTopDp)
                .width(viewportWidth - 24.dp)
                .testTag("som_target_overlay_hud")
        ) {
            Surface(
                color = Color(0xF50B1021),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, accentColor),
                tonalElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = accentColor,
                                shape = RoundedCornerShape(5.dp)
                            ) {
                                Text(
                                    text = "SoM #${targetElement.id}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = if (targetedOverlay?.isPlanningPhase == true) {
                                    "PLANIFICATION ACTION → ${targetElement.label}"
                                } else {
                                    "CIBLE VERROUILLÉE → ${targetElement.label}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (onDismiss != null) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable { onDismiss() }
                                    .testTag("dismiss_som_target_overlay"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Masquer le ciblage SoM",
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    val formattedCommand = targetedOverlay?.formattedAction
                        ?: "click(element_id=${targetElement.id}, description=\"${targetElement.label}\")"

                    Text(
                        text = formattedCommand,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!targetedOverlay?.subgoal.isNullOrBlank()) {
                        Text(
                            text = "Sous-objectif : ${targetedOverlay?.subgoal}",
                            style = MaterialTheme.typography.labelSmall,
                            color = OmniCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
