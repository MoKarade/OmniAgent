package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CognitivePipelineStage
import com.example.model.LiveCognitiveTelemetry
import com.example.ui.theme.ActionEmerald
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.OmniCyan
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThoughtAmber
import kotlinx.coroutines.delay

/**
 * Sleek, visual 4-stage reasoning indicator (Écran -> Idée -> Cible -> Action).
 * Minimal text by default, expandable on tap.
 */
@Composable
fun LiveReasoningMonitor(
    telemetry: LiveCognitiveTelemetry,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var liveElapsedMs by remember(telemetry.startedAtEpochMs, isThinking) {
        mutableLongStateOf(telemetry.elapsedMs)
    }

    LaunchedEffect(isThinking, telemetry.startedAtEpochMs) {
        if (isThinking && telemetry.startedAtEpochMs > 0L) {
            while (true) {
                liveElapsedMs = (System.currentTimeMillis() - telemetry.startedAtEpochMs).coerceAtLeast(0L)
                delay(100L)
            }
        } else {
            liveElapsedMs = telemetry.elapsedMs
        }
    }

    val activeStageIndex = when (telemetry.stage) {
        CognitivePipelineStage.IDLE -> 0
        CognitivePipelineStage.OBSERVING -> 1
        CognitivePipelineStage.THINKING -> 2
        CognitivePipelineStage.PLANNING_SUBGOAL, CognitivePipelineStage.STAGING_ACTION -> 3
        CognitivePipelineStage.EXECUTING_ACTION, CognitivePipelineStage.COMPLETED -> 4
    }

    val currentSummary = when {
        telemetry.liveActionPreview.isNotBlank() -> telemetry.liveActionPreview
        telemetry.liveSubgoal.isNotBlank() -> telemetry.liveSubgoal
        telemetry.liveThought.isNotBlank() -> telemetry.liveThought
        telemetry.liveObservation.isNotBlank() -> telemetry.liveObservation
        else -> "Prêt à analyser l'écran"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isThinking) 1.5.dp else 1.dp,
                color = if (isThinking) OmniCyan else BorderSubtle,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { expanded = !expanded }
            .testTag("live_reasoning_monitor"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Visual 4-Node Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VisualStageNode(
                    icon = Icons.Default.Visibility,
                    label = "1. Écran",
                    isActive = activeStageIndex >= 1,
                    isCurrent = activeStageIndex == 1,
                    color = OmniCyan
                )
                VisualStageConnector(isActive = activeStageIndex >= 2)
                VisualStageNode(
                    icon = Icons.Default.Psychology,
                    label = "2. Idée",
                    isActive = activeStageIndex >= 2,
                    isCurrent = activeStageIndex == 2,
                    color = ThoughtAmber
                )
                VisualStageConnector(isActive = activeStageIndex >= 3)
                VisualStageNode(
                    icon = Icons.Default.GpsFixed,
                    label = "3. Cible",
                    isActive = activeStageIndex >= 3,
                    isCurrent = activeStageIndex == 3,
                    color = OmniCyan
                )
                VisualStageConnector(isActive = activeStageIndex >= 4)
                VisualStageNode(
                    icon = Icons.Default.Bolt,
                    label = "4. Action",
                    isActive = activeStageIndex >= 4,
                    isCurrent = activeStageIndex == 4,
                    color = ActionEmerald
                )
            }

            if (isThinking) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = OmniCyan,
                    trackColor = ObsidianSurfaceVariant
                )
            }

            // Compact 1-Line Live Status Pill
            Surface(
                color = ObsidianCardElevated,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (liveElapsedMs > 0L) {
                        Text(
                            text = "${liveElapsedMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = OmniCyan,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Détails",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Optional expandable detail rows only if tapped
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (telemetry.liveObservation.isNotBlank()) {
                        CompactDetailRow("Écran", telemetry.liveObservation, OmniCyan)
                    }
                    if (telemetry.liveThought.isNotBlank()) {
                        CompactDetailRow("Réflexion", telemetry.liveThought, ThoughtAmber)
                    }
                    if (telemetry.liveActionPreview.isNotBlank()) {
                        CompactDetailRow("Action", telemetry.liveActionPreview, ActionEmerald)
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualStageNode(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    isCurrent: Boolean,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            color = if (isActive) color.copy(alpha = 0.22f) else ObsidianSurfaceVariant,
            shape = CircleShape,
            border = BorderStroke(
                width = if (isCurrent) 2.dp else 1.dp,
                color = if (isActive) color else BorderSubtle
            ),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) color else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) Color.White else TextSecondary,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun VisualStageConnector(isActive: Boolean) {
    Box(
        modifier = Modifier
            .width(24.dp)
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(if (isActive) OmniCyan else BorderSubtle)
    )
}

@Composable
private fun CompactDetailRow(title: String, value: String, color: Color) {
    Surface(
        color = ObsidianSurfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
