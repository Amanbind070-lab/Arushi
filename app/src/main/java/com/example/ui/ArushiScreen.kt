package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.DarkObsidian
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.DeepOrange
import com.example.ui.theme.GoldWarm
import com.example.ui.theme.StatusExecuting
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ArushiScreen(
    viewModel: ArushiViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            viewModel.startSession()
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactsPermission = isGranted
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkObsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            ArushiHeader(isLiveConnected = uiState.isLiveConnected)

            Spacer(modifier = Modifier.height(16.dp))

            // Central Glowing Orb & Waveform Visualizer
            ArushiOrbVisualizer(
                status = uiState.status,
                amplitude = uiState.activeAmplitude,
                onClick = {
                    if (!hasMicPermission) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.toggleVoiceSession()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status Badge
            StatusPill(status = uiState.status)

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle & Live Transcript Card
            TranscriptCard(
                transcript = uiState.liveTranscript,
                status = uiState.status,
                errorMessage = uiState.errorMessage
            )

            // Last Device Action Card (if any executed)
            AnimatedVisibility(visible = uiState.lastAction != null) {
                uiState.lastAction?.let { action ->
                    Spacer(modifier = Modifier.height(10.dp))
                    ActionBadgeCard(
                        actionName = action.actionName,
                        summary = action.userSummary,
                        isSuccess = action.success
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            ActionGrid(
                onActionClick = { command ->
                    if (!hasMicPermission) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    if (command.contains("Call", ignoreCase = true) && !hasContactsPermission) {
                        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                    viewModel.sendCommand(command)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Controls: Main Mic Button, Interruption, Speaker Test
            BottomControls(
                status = uiState.status,
                isDiagnosticPlaying = uiState.isDiagnosticTonePlaying,
                onMicClick = {
                    if (!hasMicPermission) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.toggleVoiceSession()
                    }
                },
                onInterruptClick = {
                    viewModel.interruptSpeech()
                },
                onSpeakerDiagnosticClick = {
                    viewModel.runSpeakerDiagnostic()
                }
            )
        }
    }
}

@Composable
fun ArushiHeader(isLiveConnected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(DeepOrange, AmberGlow))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "Arushi Logo",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Arushi AI",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Voice Assistant • Gemini Live",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        // Live badge
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isLiveConnected) EmeraldSuccess.copy(alpha = 0.15f) else SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isLiveConnected) EmeraldSuccess.copy(alpha = 0.5f) else SurfaceCardBorder
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isLiveConnected) EmeraldSuccess else TextMuted)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isLiveConnected) "LIVE AUDIO" else "STANDBY",
                    color = if (isLiveConnected) EmeraldSuccess else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

@Composable
fun ArushiOrbVisualizer(
    status: AssistantStatus,
    amplitude: Float,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotateAngle"
    )

    val orbColor by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.LISTENING -> GoldWarm
            AssistantStatus.SPEAKING -> DeepOrange
            AssistantStatus.TOOL_EXECUTING -> StatusExecuting
            AssistantStatus.CONNECTING -> AmberGlow
            AssistantStatus.ERROR -> Color(0xFFEF4444)
            AssistantStatus.IDLE -> AmberGlow
        },
        animationSpec = tween(400),
        label = "orbColor"
    )

    Box(
        modifier = Modifier
            .size(240.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Dynamic Wave Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.28f

            // Dynamic amplitude expansion
            val ampRadius = baseRadius + (amplitude * 55.dp.toPx())

            // Outer ambient glow ring
            drawCircle(
                color = orbColor.copy(alpha = 0.12f * (1f + amplitude)),
                radius = ampRadius * 1.5f * pulseScale,
                center = center
            )

            // Middle ring
            drawCircle(
                color = orbColor.copy(alpha = 0.25f * (1f + amplitude)),
                radius = ampRadius * 1.25f,
                center = center
            )

            // Core glowing orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        orbColor,
                        orbColor.copy(alpha = 0.4f)
                    ),
                    center = center,
                    radius = baseRadius * pulseScale
                ),
                radius = baseRadius * pulseScale,
                center = center
            )
        }

        // Center Icon
        when (status) {
            AssistantStatus.CONNECTING -> {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(42.dp),
                    strokeWidth = 3.dp
                )
            }
            AssistantStatus.LISTENING -> {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Listening",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }
            AssistantStatus.SPEAKING -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Speaking",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }
            AssistantStatus.TOOL_EXECUTING -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Executing",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }
            AssistantStatus.ERROR -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }
            AssistantStatus.IDLE -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Tap to speak",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }
        }
    }
}

@Composable
fun StatusPill(status: AssistantStatus) {
    val (label, bg, fg) = when (status) {
        AssistantStatus.IDLE -> Triple("READY • TAP TO SPEAK", SurfaceCard, TextSecondary)
        AssistantStatus.CONNECTING -> Triple("CONNECTING LIVE...", AmberGlow.copy(alpha = 0.2f), AmberGlow)
        AssistantStatus.LISTENING -> Triple("LISTENING TO YOU...", GoldWarm.copy(alpha = 0.2f), GoldWarm)
        AssistantStatus.SPEAKING -> Triple("ARUSHI SPEAKING...", DeepOrange.copy(alpha = 0.2f), DeepOrange)
        AssistantStatus.TOOL_EXECUTING -> Triple("EXECUTING ACTION...", StatusExecuting.copy(alpha = 0.2f), StatusExecuting)
        AssistantStatus.ERROR -> Triple("ERROR OCCURRED", Color(0xFFEF4444).copy(alpha = 0.2f), Color(0xFFEF4444))
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, fg.copy(alpha = 0.4f)),
        modifier = Modifier.testTag("status_indicator")
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun TranscriptCard(
    transcript: String,
    status: AssistantStatus,
    errorMessage: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transcript_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (errorMessage != null) Color(0xFFEF4444).copy(alpha = 0.6f) else SurfaceCardBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Arushi Conversation",
                    color = AmberGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Auto-Language",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFFCA5A5),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            } else {
                Text(
                    text = transcript,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun ActionBadgeCard(
    actionName: String,
    summary: String,
    isSuccess: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isSuccess) EmeraldSuccess.copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSuccess) EmeraldSuccess.copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = "Action Result",
                tint = if (isSuccess) EmeraldSuccess else Color(0xFFEF4444),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Device Action: $actionName",
                    color = if (isSuccess) EmeraldSuccess else Color(0xFFEF4444),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = summary,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ActionGrid(
    onActionClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ActionButton(icon = Icons.Default.Message, label = "WhatsApp") { onActionClick("Open WhatsApp") }
        ActionButton(icon = Icons.Default.Call, label = "Dial") { onActionClick("Make a call") }
        ActionButton(icon = Icons.Default.Language, label = "Web") { onActionClick("Open Chrome") }
        ActionButton(icon = Icons.Default.Contacts, label = "People") { onActionClick("Open Contacts") }
        ActionButton(icon = Icons.Default.History, label = "History") { onActionClick("Show history") }
        ActionButton(icon = Icons.Default.Settings, label = "Settings") { onActionClick("Open Settings") }
    }
}

@Composable
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp)
            .testTag("action_${label}")
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(SurfaceCard, CircleShape)
                .border(1.dp, SurfaceCardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = AmberGlow,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun BottomControls(
    status: AssistantStatus,
    isDiagnosticPlaying: Boolean,
    onMicClick: () -> Unit,
    onInterruptClick: () -> Unit,
    onSpeakerDiagnosticClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Speaker Diagnostic Test Button
        IconButton(
            onClick = onSpeakerDiagnosticClick,
            modifier = Modifier
                .size(48.dp)
                .background(SurfaceCard, CircleShape)
                .border(1.dp, SurfaceCardBorder, CircleShape)
                .testTag("speaker_diagnostic_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "440Hz Speaker Test",
                tint = if (isDiagnosticPlaying) ElectricCyan else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        // Primary Mic / Power Toggle Button
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(16.dp, CircleShape, spotColor = AmberGlow)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            DeepOrange,
                            AmberGlow
                        )
                    )
                )
                .clickable { onMicClick() }
                .testTag("main_mic_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (status == AssistantStatus.IDLE || status == AssistantStatus.ERROR) {
                    Icons.Default.Mic
                } else {
                    Icons.Default.PowerSettingsNew
                },
                contentDescription = "Toggle Arushi Voice Session",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }

        // Barge-in / Stop Talking Button (active when speaking)
        IconButton(
            onClick = onInterruptClick,
            enabled = (status == AssistantStatus.SPEAKING),
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (status == AssistantStatus.SPEAKING) DeepOrange.copy(alpha = 0.2f) else SurfaceCard,
                    CircleShape
                )
                .border(
                    1.dp,
                    if (status == AssistantStatus.SPEAKING) DeepOrange else SurfaceCardBorder,
                    CircleShape
                )
                .testTag("interrupt_speech_button")
        ) {
            Icon(
                imageVector = Icons.Default.MicOff,
                contentDescription = "Interrupt / Barge-in",
                tint = if (status == AssistantStatus.SPEAKING) DeepOrange else TextMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
