package com.example.myapplication.detection

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HoopSelectionScreen(
    cacheFilePath: String,
    videoDurationMs: Long,
    onHoopConfirmed: (normalisedX: Float, normalisedY: Float) -> Unit,
    onBack: () -> Unit
) {
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var hoopPosition by remember { mutableStateOf<Offset?>(null) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var isLoading by remember { mutableStateOf(true) }

    // Extract a frame at 20% into the video for a representative court view
    LaunchedEffect(cacheFilePath) {
        val frame = withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(cacheFilePath)
                val targetUs = (videoDurationMs * 0.2).toLong() * 1000L
                val bitmap = retriever.getFrameAtTime(
                    targetUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                retriever.release()
                bitmap
            } catch (e: Exception) {
                null
            }
        }
        previewBitmap = frame
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        } else if (previewBitmap == null) {
            Text(
                text = "Could not load video frame",
                color = Color.Red,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {

                // Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Select Hoop Position",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap where the centre of the hoop is",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }

                // Video frame with tap overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    // Frame image
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "Video frame",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords ->
                                imageSize = coords.size
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { tapOffset ->
                                    // Store raw pixel position within the composable
                                    hoopPosition = tapOffset
                                }
                            }
                    )

                    // Draw crosshair at tapped position
                    if (hoopPosition != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val pos = hoopPosition!!

                            // Outer ring
                            drawCircle(
                                color = Color(0xFFFF9500),
                                radius = 28f,
                                center = pos,
                                style = Stroke(width = 3f)
                            )

                            // Inner dot
                            drawCircle(
                                color = Color(0xFFFF9500),
                                radius = 6f,
                                center = pos
                            )

                            // Crosshair lines
                            val lineLength = 20f
                            drawLine(
                                color = Color(0xFFFF9500),
                                start = Offset(pos.x - lineLength, pos.y),
                                end = Offset(pos.x + lineLength, pos.y),
                                strokeWidth = 2f
                            )
                            drawLine(
                                color = Color(0xFFFF9500),
                                start = Offset(pos.x, pos.y - lineLength),
                                end = Offset(pos.x, pos.y + lineLength),
                                strokeWidth = 2f
                            )
                        }
                    }

                    // Hint if nothing tapped yet
                    if (hoopPosition == null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.6f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "👆 Tap on the hoop",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Bottom controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (hoopPosition != null && imageSize != IntSize.Zero) {
                        Text(
                            text = "Hoop marked ✓  —  tap again to adjust",
                            color = Color(0xFFFF9500),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Text("← Back")
                        }

                        Button(
                            onClick = {
                                val pos = hoopPosition ?: return@Button
                                val size = imageSize

                                // Normalise to 0..1 relative to the displayed image area
                                val normX = (pos.x / size.width).coerceIn(0f, 1f)
                                val normY = (pos.y / size.height).coerceIn(0f, 1f)

                                onHoopConfirmed(normX, normY)
                            },
                            enabled = hoopPosition != null,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9500),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF3A3A3A),
                                disabledContentColor = Color.Gray
                            )
                        ) {
                            Text("Confirm", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}