package com.example.myapplication.detection

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import org.tensorflow.lite.examples.objectdetection.VideoOverlayView
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun PlaybackUI(
    exoPlayer: ExoPlayer?,
    overlayView: VideoOverlayView?,
    onOverlayCreated: (VideoOverlayView) -> Unit,
    isPlaying: Boolean,
    currentPositionMs: Long,
    currentDetectionCount: Int,
    videoWidth: Int,
    videoHeight: Int,
    totalFrames: Int,
    totalDetections: Int,
    processingTimeMs: Long,
    shotCount: Int,
    shots: List<ShotAttempt>,
    hoopPosition: Offset,
    navController: NavController? = null,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onBack: () -> Unit
) {
    val activeShotIndex by remember(currentPositionMs, shots) {
        derivedStateOf {
            shots.indexOfFirst { shot ->
                currentPositionMs in shot.startTimestampMs..shot.endTimestampMs
            }
        }
    }

    val shotAngles = remember(shots) {
        shots.mapNotNull { it.curve?.entryAngleDeg?.let { angle -> abs(angle).toDouble() } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ── Top bar ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.95f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C1C1E),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back")
            }

            if (shotAngles.isNotEmpty()) {
                Button(
                    onClick = {
                        val anglesParam = shotAngles.joinToString(",") { "%.2f".format(it) }
                        navController?.navigate("analysis/${Uri.encode(anglesParam)}/free_throw")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A84FF),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = "Analyse",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Analyse Shots")
                }
            } else {
                Text(
                    text = "Pre-processed",
                    color = Color(0xFF34C759),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Video ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val playerView = PlayerView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            tag = "playerView"
                        }
                        addView(playerView)

                        val overlay = VideoOverlayView(ctx, null).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                        addView(overlay)
                        onOverlayCreated(overlay)
                    }
                },
                update = { frameLayout ->
                    val playerView = frameLayout.findViewWithTag<PlayerView>("playerView")
                    playerView?.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )

            ShotArcOverlay(
                shot = shots.getOrNull(activeShotIndex),
                hoopPosition = hoopPosition,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                modifier = Modifier.fillMaxSize()
            )

            if (activeShotIndex >= 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0xFFFF9500), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Shot ${activeShotIndex + 1}  •  ${
                            shots.getOrNull(activeShotIndex)?.curve?.entryAngleDeg
                                ?.let { "%.1f°".format(abs(it)) } ?: "N/A"
                        }",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── Playback controls ────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.SportsBasketball,
                    contentDescription = null,
                    tint = if (currentDetectionCount > 0) Color(0xFFFF9500) else Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (currentDetectionCount > 0)
                        "$currentDetectionCount ball${if (currentDetectionCount != 1) "s" else ""} detected"
                    else
                        "No ball detected",
                    color = if (currentDetectionCount > 0) Color.White else Color.Gray,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = formatTime(currentPositionMs),
                color = Color.Gray,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        exoPlayer?.seekTo(maxOf(0, (exoPlayer.currentPosition) - 5000))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1C1C1E),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.Default.FastRewind,
                        contentDescription = "Rewind 5s",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("5s")
                }

                Button(
                    onClick = onPlayPause,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF007AFF),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(60.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(28.dp)
                    )
                }

                Button(
                    onClick = {
                        exoPlayer?.seekTo(
                            minOf(exoPlayer.duration, (exoPlayer.currentPosition) + 5000)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1C1C1E),
                        contentColor = Color.White
                    )
                ) {
                    Text("5s")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.FastForward,
                        contentDescription = "Forward 5s",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = if ((exoPlayer?.duration ?: 0) > 0)
                    currentPositionMs.toFloat() / (exoPlayer?.duration ?: 1)
                else 0f,
                onValueChange = onSeek,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF007AFF),
                    activeTrackColor = Color(0xFF007AFF),
                    inactiveTrackColor = Color(0xFF2C2C2E)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Shot attempts list ───────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111))
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Shot Attempts",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${shots.size}",
                    color = Color(0xFFFF9500),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xFF2C2C2E), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (shots.isEmpty()) {
                Text(
                    text = "No shots detected",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(shots) { index, shot ->
                        ShotCard(
                            index = index,
                            shot = shot,
                            isActive = index == activeShotIndex,
                            onClick = {
                                exoPlayer?.seekTo(shot.startTimestampMs)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ShotCard(
    index: Int,
    shot: ShotAttempt,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isActive) Color(0xFFFF9500) else Color(0xFF2C2C2E)
    val bgColor = if (isActive) Color(0xFF2A1F00) else Color(0xFF1C1C1E)

    val angleText = shot.curve?.entryAngleDeg
        ?.let { "%.1f°".format(abs(it)) } ?: "N/A"

    val (qualityLabel, qualityColor) = if (shot.curve != null) {
        QuadraticFitter.angleQuality(shot.curve.entryAngleDeg)
    } else {
        "N/A" to Color.Gray
    }

    Column(
        modifier = Modifier
            .width(90.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "${index + 1}",
            color = if (isActive) Color(0xFFFF9500) else Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)

        Text(
            text = angleText,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .background(qualityColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = qualityLabel,
                color = qualityColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}