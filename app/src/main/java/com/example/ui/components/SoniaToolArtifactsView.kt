package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.SoniaArtifact
import com.example.security.SoniaChartPoint
import com.example.security.SoniaChartStyle

private val GoogleDarkCard = Color(0xFF1E1F20)
private val GoogleDarkElevated = Color(0xFF282A2C)
private val GoogleBlue = Color(0xFF8AB4F8)
private val GoogleGreen = Color(0xFF81C995)
private val GoogleAmber = Color(0xFFFDD663)
private val GoogleCoral = Color(0xFFF28B82)
private val GooglePurple = Color(0xFFC58AF9)
private val TextLight = Color(0xFFE3E3E3)
private val TextMuted = Color(0xFF9AA0A6)

@Composable
fun SoniaArtifactCard(
    artifact: SoniaArtifact,
    onToggleChecklistItem: (String, String) -> Unit,
    onDismissArtifact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (artifact) {
        is SoniaArtifact.ChartArtifact -> SoniaChartCard(
            artifact = artifact,
            onDismiss = { onDismissArtifact(artifact.id) },
            modifier = modifier
        )
        is SoniaArtifact.ChecklistArtifact -> SoniaChecklistCard(
            artifact = artifact,
            onToggleItem = { itemId -> onToggleChecklistItem(artifact.id, itemId) },
            onDismiss = { onDismissArtifact(artifact.id) },
            modifier = modifier
        )
        is SoniaArtifact.TableArtifact -> SoniaTableCard(
            artifact = artifact,
            onDismiss = { onDismissArtifact(artifact.id) },
            modifier = modifier
        )
        is SoniaArtifact.TimelineArtifact -> SoniaTimelineCard(
            artifact = artifact,
            onDismiss = { onDismissArtifact(artifact.id) },
            modifier = modifier
        )
        is SoniaArtifact.CodeCalcArtifact -> SoniaCodeCalcCard(
            artifact = artifact,
            onDismiss = { onDismissArtifact(artifact.id) },
            modifier = modifier
        )
        is SoniaArtifact.GeneratedImageArtifact -> SoniaGeneratedImageCard(
            artifact = artifact,
            onDismiss = { onDismissArtifact(artifact.id) },
            modifier = modifier
        )
    }
}

@Composable
private fun SoniaArtifactHeader(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    trailingControl: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = GoogleGreen,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            trailingControl?.invoke()
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fermer",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SoniaChartCard(
    artifact: SoniaArtifact.ChartArtifact,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeStyle by remember(artifact.id) { mutableStateOf(artifact.style) }
    var selectedIndex by remember(artifact.id) { mutableStateOf<Int?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sonia_chart_card_${artifact.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleDarkCard),
        border = BorderStroke(1.dp, GoogleBlue.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SoniaArtifactHeader(
                icon = {
                    Icon(
                        imageVector = when (activeStyle) {
                            SoniaChartStyle.BAR -> Icons.Default.BarChart
                            SoniaChartStyle.LINE -> Icons.AutoMirrored.Filled.ShowChart
                            SoniaChartStyle.DONUT -> Icons.Default.DonutLarge
                        },
                        contentDescription = null,
                        tint = GoogleBlue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                title = artifact.title,
                subtitle = artifact.subtitle,
                accentColor = GoogleBlue,
                onDismiss = onDismiss,
                trailingControl = {
                    Surface(
                        onClick = {
                            activeStyle = when (activeStyle) {
                                SoniaChartStyle.BAR -> SoniaChartStyle.LINE
                                SoniaChartStyle.LINE -> SoniaChartStyle.DONUT
                                SoniaChartStyle.DONUT -> SoniaChartStyle.BAR
                            }
                        },
                        shape = CircleShape,
                        color = GoogleDarkElevated
                    ) {
                        Text(
                            text = when (activeStyle) {
                                SoniaChartStyle.BAR -> "Barres"
                                SoniaChartStyle.LINE -> "Courbe"
                                SoniaChartStyle.DONUT -> "Anneau"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = GoogleBlue,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            )

            // Interactive Canvas Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background( Color(0xFF161719) )
                    .padding(16.dp)
            ) {
                when (activeStyle) {
                    SoniaChartStyle.BAR -> BarChartCanvas(
                        points = artifact.points,
                        unit = artifact.unit
                    )
                    SoniaChartStyle.LINE -> LineChartCanvas(
                        points = artifact.points
                    )
                    SoniaChartStyle.DONUT -> DonutChartCanvas(
                        points = artifact.points,
                        unit = artifact.unit
                    )
                }
            }

            // Legend Chips (clickable to inspect)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                artifact.points.forEachIndexed { index, pt ->
                    val isSelected = selectedIndex == index
                    val ptColor = Color(pt.colorHex)
                    Surface(
                        onClick = { selectedIndex = if (isSelected) null else index },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) ptColor.copy(alpha = 0.2f) else GoogleDarkElevated,
                        border = if (isSelected) BorderStroke(1.dp, ptColor) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ptColor)
                            )
                            Text(
                                text = "${pt.label}: ${pt.value.toInt()}${artifact.unit}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = TextLight
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarChartCanvas(
    points: List<SoniaChartPoint>,
    unit: String
) {
    if (points.isEmpty()) return
    val maxVal = (points.maxOfOrNull { it.value } ?: 1f).coerceAtLeast(1f)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val count = points.size
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.52f).coerceAtMost(42.dp.toPx())

            // Subtle horizontal guide lines
            for (i in 1..3) {
                val y = size.height * (i / 4f)
                drawLine(
                    color = Color.White.copy(alpha = 0.06f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            points.forEachIndexed { idx, pt ->
                val ratio = (pt.value / maxVal).coerceIn(0.08f, 1f)
                val barHeight = size.height * ratio
                val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                val top = size.height - barHeight
                val color = Color(pt.colorHex)

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(color, color.copy(alpha = 0.45f)),
                        startY = top,
                        endY = size.height
                    ),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            points.forEach { pt ->
                Text(
                    text = pt.label.take(6),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun LineChartCanvas(points: List<SoniaChartPoint>) {
    if (points.size < 2) return
    val maxVal = (points.maxOfOrNull { it.value } ?: 1f).coerceAtLeast(1f)
    val minVal = (points.minOfOrNull { it.value } ?: 0f).coerceAtMost(maxVal - 1f)
    val range = (maxVal - minVal).coerceAtLeast(1f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
        val path = Path()
        val fillPath = Path()

        val offsets = points.mapIndexed { idx, pt ->
            val x = idx * stepX
            val norm = (pt.value - minVal) / range
            val y = size.height - (norm * (size.height * 0.8f) + size.height * 0.1f)
            Offset(x, y)
        }

        offsets.forEachIndexed { index, offset ->
            if (index == 0) {
                path.moveTo(offset.x, offset.y)
                fillPath.moveTo(offset.x, size.height)
                fillPath.lineTo(offset.x, offset.y)
            } else {
                path.lineTo(offset.x, offset.y)
                fillPath.lineTo(offset.x, offset.y)
            }
        }
        fillPath.lineTo(offsets.last().x, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(GoogleBlue.copy(alpha = 0.32f), Color.Transparent)
            )
        )
        drawPath(
            path = path,
            color = GoogleBlue,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        offsets.forEachIndexed { idx, center ->
            val ptColor = Color(points[idx].colorHex)
            drawCircle(
                color = Color(0xFF161719),
                radius = 6.dp.toPx(),
                center = center
            )
            drawCircle(
                color = ptColor,
                radius = 4.dp.toPx(),
                center = center
            )
        }
    }
}

@Composable
private fun DonutChartCanvas(
    points: List<SoniaChartPoint>,
    unit: String
) {
    if (points.isEmpty()) return
    val total = points.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(
            modifier = Modifier.size(125.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 18.dp.toPx()
                var startAngle = -90f
                points.forEach { pt ->
                    val sweep = (pt.value / total) * 360f
                    drawArc(
                        color = Color(pt.colorHex),
                        startAngle = startAngle,
                        sweepAngle = (sweep - 3f).coerceAtLeast(2f),
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${total.toInt()}$unit",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextLight
                )
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            points.take(4).forEach { pt ->
                val pct = ((pt.value / total) * 100).toInt()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(pt.colorHex))
                    )
                    Text(
                        text = "${pt.label} ($pct%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextLight
                    )
                }
            }
        }
    }
}

@Composable
private fun SoniaChecklistCard(
    artifact: SoniaArtifact.ChecklistArtifact,
    onToggleItem: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val doneCount = artifact.items.count { it.isChecked }
    val totalCount = artifact.items.size.coerceAtLeast(1)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sonia_checklist_card_${artifact.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleDarkCard),
        border = BorderStroke(1.dp, GoogleGreen.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SoniaArtifactHeader(
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckBox,
                        contentDescription = null,
                        tint = GoogleGreen,
                        modifier = Modifier.size(18.dp)
                    )
                },
                title = artifact.title,
                subtitle = "$doneCount / ${artifact.items.size} complétés • Interactif",
                accentColor = GoogleGreen,
                onDismiss = onDismiss
            )

            // Slim progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(GoogleDarkElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = doneCount.toFloat() / totalCount)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(GoogleGreen)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                artifact.items.forEach { item ->
                    val rowBg by animateColorAsState(
                        targetValue = if (item.isChecked) GoogleGreen.copy(alpha = 0.1f) else GoogleDarkElevated,
                        label = "check_bg"
                    )
                    Surface(
                        onClick = { onToggleItem(item.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = rowBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (item.isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = if (item.isChecked) GoogleGreen else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (item.isChecked) TextMuted else TextLight,
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                            if (item.category.isNotBlank()) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF161719)
                                ) {
                                    Text(
                                        text = item.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoniaTableCard(
    artifact: SoniaArtifact.TableArtifact,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sonia_table_card_${artifact.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleDarkCard),
        border = BorderStroke(1.dp, GoogleAmber.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SoniaArtifactHeader(
                icon = {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = null,
                        tint = GoogleAmber,
                        modifier = Modifier.size(18.dp)
                    )
                },
                title = artifact.title,
                subtitle = "${artifact.rows.size} lignes • Comparatif structuré",
                accentColor = GoogleAmber,
                onDismiss = onDismiss
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF161719),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        artifact.headers.forEach { header ->
                            Text(
                                text = header,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GoogleAmber,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    artifact.rows.forEachIndexed { idx, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (idx % 2 == 1) Color.White.copy(alpha = 0.03f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            row.forEachIndexed { colIdx, cell ->
                                Text(
                                    text = cell,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (colIdx == 0) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (colIdx == 0) TextLight else TextMuted,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoniaTimelineCard(
    artifact: SoniaArtifact.TimelineArtifact,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sonia_timeline_card_${artifact.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleDarkCard),
        border = BorderStroke(1.dp, GooglePurple.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SoniaArtifactHeader(
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.EventNote,
                        contentDescription = null,
                        tint = GooglePurple,
                        modifier = Modifier.size(18.dp)
                    )
                },
                title = artifact.title,
                subtitle = "${artifact.steps.size} étapes planifiées",
                accentColor = GooglePurple,
                onDismiss = onDismiss
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                artifact.steps.forEach { step ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = GoogleDarkElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = GooglePurple.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = step.timeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GooglePurple,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = step.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextLight
                                )
                                Text(
                                    text = step.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoniaCodeCalcCard(
    artifact: SoniaArtifact.CodeCalcArtifact,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sonia_calc_card_${artifact.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleDarkCard),
        border = BorderStroke(1.dp, GoogleGreen.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SoniaArtifactHeader(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = null,
                        tint = GoogleGreen,
                        modifier = Modifier.size(18.dp)
                    )
                },
                title = artifact.title,
                subtitle = "Sandbox Math & Code • Chiffrement AES-256",
                accentColor = GoogleGreen,
                onDismiss = onDismiss
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF101112),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = artifact.expressionOrCode,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = GoogleBlue
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Résultat vérifié",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Text(
                            text = artifact.evaluatedResult,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoogleGreen
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Empreinte SHA-256 : ${artifact.sha256Signature.take(18)}…",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoniaGeneratedImageCard(
    artifact: SoniaArtifact.GeneratedImageArtifact,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sonia_image_card_${artifact.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GoogleDarkCard),
        border = BorderStroke(1.dp, GooglePurple.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SoniaArtifactHeader(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = GooglePurple,
                        modifier = Modifier.size(18.dp)
                    )
                },
                title = "Studio Image IA Sonia",
                subtitle = artifact.prompt,
                accentColor = GooglePurple,
                onDismiss = onDismiss
            )

            when {
                artifact.isLoading -> {
                    val infiniteTransition = rememberInfiniteTransition(label = "img_shimmer")
                    val shimmerAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 0.65f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(900, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "shimmer_alpha"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(GoogleDarkElevated.copy(alpha = shimmerAlpha)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = GooglePurple,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Génération de l'image via Gemini Image…",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextLight
                            )
                        }
                    }
                }
                artifact.bitmap != null -> {
                    Image(
                        bitmap = artifact.bitmap.asImageBitmap(),
                        contentDescription = artifact.prompt,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                }
                artifact.errorMessage != null -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = GoogleCoral.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, GoogleCoral.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = GoogleCoral,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = artifact.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextLight
                            )
                        }
                    }
                }
            }
        }
    }
}
