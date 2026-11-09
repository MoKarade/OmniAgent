package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.SoniaToolType
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.GoogleBlueContainer
import com.example.ui.theme.GoogleBlueVivid
import com.example.ui.theme.GoogleBorderSubtle
import com.example.ui.theme.GoogleGreen
import com.example.ui.theme.GoogleRed
import com.example.ui.theme.GoogleSurfaceVariant
import com.example.ui.theme.GoogleTextMuted
import com.example.ui.theme.GoogleTextPrimary
import com.example.ui.theme.GoogleTextSecondary
import com.example.viewmodel.OmniAgentUiState
import com.example.viewmodel.OmniAgentViewModel
import kotlin.math.roundToInt

/**
 * Always-present subtle side icon + Gemini-style Bottom Chat Bar:
 * - Minimized (`isBubbleMinimizedToEdge == true`): A simple, discreet 32.dp ✨ circle icon docked on the
 *   right or left edge of the screen that you can drag up/down or side-to-side.
 * - Clicked (`isBubbleMinimizedToEdge == false`): Opens a sleek floating Gemini-style bottom chat bar
 *   right at the bottom of the screen (with automatic keyboard rise via parent `imePadding()`).
 */
@Composable
fun SoniaFloatingBubbleOverlay(
    uiState: OmniAgentUiState,
    viewModel: OmniAgentViewModel
) {
    var verticalOffsetPx by remember { mutableFloatStateOf(-40f) }
    val isMinimized = uiState.isBubbleMinimizedToEdge
    val dockedRight = uiState.isBubbleDockedRight
    val backdropInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. SIMPLE DRAGGABLE SIDE ICON (Always visible on the border when minimized!)
        if (isMinimized) {
            Box(
                modifier = Modifier
                    .align(if (dockedRight) Alignment.CenterEnd else Alignment.CenterStart)
                    .offset {
                        IntOffset(
                            x = if (dockedRight) 8.dp.roundToPx() else (-8).dp.roundToPx(),
                            y = verticalOffsetPx.roundToInt()
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            verticalOffsetPx = (verticalOffsetPx + dragAmount.y).coerceIn(-500f, 500f)
                            if (dragAmount.x < -20f) {
                                viewModel.setBubbleDockedSide(false)
                            } else if (dragAmount.x > 20f) {
                                viewModel.setBubbleDockedSide(true)
                            }
                        }
                    }
                    .alpha(if (uiState.isThinkingStep) 0.95f else 0.72f)
                    .testTag("sonia_edge_hidden_bubble")
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xE61E1F20),
                    modifier = Modifier
                        .size(34.dp)
                        .border(
                            width = 1.dp,
                            color = GoogleBlue.copy(alpha = 0.55f),
                            shape = CircleShape
                        )
                        .clickable {
                            viewModel.setBubbleMinimizedToEdge(false)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (uiState.isThinkingStep) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 1.8.dp,
                                color = GoogleBlue
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Ouvrir la barre de chat Sonia",
                                tint = GoogleBlue,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }

        // Tap anywhere above the bottom Gemini bar to fold it back into the side icon
        if (!isMinimized) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        interactionSource = backdropInteraction,
                        indication = null
                    ) {
                        viewModel.setBubbleMinimizedToEdge(true)
                    }
            )
        }

        // 2. GEMINI-STYLE BOTTOM CHAT BAR (Slides up from the bottom when the side icon is clicked)
        AnimatedVisibility(
            visible = !isMinimized,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            SoniaGeminiBottomChatBar(
                uiState = uiState,
                viewModel = viewModel,
                onMinimizeToSideIcon = { viewModel.setBubbleMinimizedToEdge(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sonia_expanded_floating_chat")
            )
        }
    }
}

@Composable
fun SoniaGeminiBottomChatBar(
    uiState: OmniAgentUiState,
    viewModel: OmniAgentViewModel,
    onMinimizeToSideIcon: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showQuickTools by rememberSaveable { mutableStateOf(false) }
    val lastStep = uiState.cognitiveHistory.lastOrNull()
    val replyText = when {
        uiState.errorMessage != null -> uiState.errorMessage
        uiState.missionCompletedSummary != null -> uiState.missionCompletedSummary
        lastStep != null -> if (lastStep.action.summary.isNotBlank()) lastStep.action.summary else lastStep.subgoal
        else -> null
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Compact Sonia Reply Pill right above the Gemini bar
        if (replyText != null || uiState.isThinkingStep) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF51E1F20),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GoogleBorderSubtle, RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState.isThinkingStep) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = GoogleBlue
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (uiState.errorMessage != null) GoogleRed else GoogleGreen,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = if (uiState.isThinkingStep) "Sonia agit…" else replyText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.errorMessage != null) GoogleRed else GoogleTextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Optional '+' quick tools strip
        AnimatedVisibility(visible = showQuickTools) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tools = listOf(
                    SoniaToolType.CREATE_CHART,
                    SoniaToolType.CREATE_CHECKLIST,
                    SoniaToolType.GENERATE_IMAGE,
                    SoniaToolType.CREATE_TABLE,
                    SoniaToolType.CREATE_TIMELINE,
                    SoniaToolType.CODE_CALCULATOR
                )
                tools.forEach { tool ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xF51E1F20),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, GoogleBorderSubtle, RoundedCornerShape(50))
                            .clickable {
                                viewModel.executeSoniaTool(tool)
                                showQuickTools = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(text = tool.emoji, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = tool.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = GoogleTextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Sleek Floating Gemini Bottom Input Pill
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFA1E1F20),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = GoogleBlue.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(50)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // '+' button to toggle tools
                IconButton(
                    onClick = { showQuickTools = !showQuickTools },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (showQuickTools) GoogleBlueContainer else GoogleSurfaceVariant)
                ) {
                    Icon(
                        imageVector = if (showQuickTools) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Outils",
                        tint = if (showQuickTools) GoogleBlue else GoogleTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Clean borderless input field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (uiState.userMission.isEmpty()) {
                        Text(
                            text = "Demandez à Sonia…",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp,
                            color = GoogleTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = uiState.userMission,
                        onValueChange = viewModel::updateMissionText,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = GoogleTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(GoogleBlue),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Voice-to-Text Microphone button
                SoniaVoiceInputButton(
                    onPartialTranscript = viewModel::updateMissionText,
                    onFinalTranscript = viewModel::submitVoiceCommand,
                    onVoiceError = viewModel::reportVoiceInputError,
                    buttonSize = 34.dp,
                    iconSize = 17.dp,
                    testTag = "bubble_voice_mic_button"
                )

                // Send button
                IconButton(
                    onClick = viewModel::executeSingleCognitiveStep,
                    enabled = !uiState.isThinkingStep,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(GoogleBlueVivid)
                        .testTag("bubble_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Envoyer",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Fold back into the subtle side icon
                IconButton(
                    onClick = onMinimizeToSideIcon,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(GoogleSurfaceVariant)
                        .testTag("tuck_bubble_to_edge_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Replier sur le côté",
                        tint = GoogleTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
