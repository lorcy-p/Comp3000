package com.example.myapplication.detection

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

@Composable
fun HoopSelectionScreen(
    cacheFilePath: String,
    videoDurationMs: Long,
    onHoopConfirmed: (normalisedX: Float, normalisedY: Float) -> Unit,
    onBack: () -> Unit
) {
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var hoopPosition  by remember { mutableStateOf<Offset?>(null) }   // canvas pixels
    var normHoopPos   by remember { mutableStateOf<Pair<Float, Float>?>(null) } // 0..1
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isLoading     by remember { mutableStateOf(true) }

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
            } catch (e: Exception) { null }
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
            val bitmap = previewBitmap!!

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

                // ── Image + tap area ─────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                        .onGloballyPositioned { coords ->
                            containerSize = coords.size
                        }
                        .pointerInput(bitmap) {
                            detectTapGestures { tapOffset ->
                                if (containerSize == IntSize.Zero) return@detectTapGestures

                                val cW = containerSize.width.toFloat()
                                val cH = containerSize.height.toFloat()
                                val bW = bitmap.width.toFloat()
                                val bH = bitmap.height.toFloat()

                                // Calculate the actual image rect within the container
                                // (ContentScale.Fit letterboxing)
                                val imageRect = calculateFitRect(cW, cH, bW, bH)

                                // Clamp tap to within the image rect
                                val clampedX = tapOffset.x
                                    .coerceIn(imageRect.left, imageRect.right)
                                val clampedY = tapOffset.y
                                    .coerceIn(imageRect.top, imageRect.bottom)

                                // Normalise relative to the image rect, not the container
                                val normX = ((clampedX - imageRect.left) / imageRect.width)
                                    .coerceIn(0f, 1f)
                                val normY = ((clampedY - imageRect.top) / imageRect.height)
                                    .coerceIn(0f, 1f)

                                normHoopPos = Pair(normX, normY)

                                // Store canvas position for crosshair drawing
                                hoopPosition = Offset(clampedX, clampedY)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Draw bitmap manually so we control exact placement
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cW = size.width
                        val cH = size.height
                        val bW = bitmap.width.toFloat()
                        val bH = bitmap.height.toFloat()

                        val rect = calculateFitRect(cW, cH, bW, bH)

                        drawImage(
                            image = bitmap.asImageBitmap(),
                            dstOffset = androidx.compose.ui.unit.IntOffset(
                                rect.left.toInt(),
                                rect.top.toInt()
                            ),
                            dstSize = androidx.compose.ui.unit.IntSize(
                                rect.width.toInt(),
                                rect.height.toInt()
                            )
                        )

                        // Crosshair at tapped position
                        val pos = hoopPosition
                        if (pos != null) {
                            val lineLength = 20f

                            drawCircle(
                                color = Color(0xFFFF9500),
                                radius = 28f,
                                center = pos,
                                style = Stroke(width = 3f)
                            )
                            drawCircle(
                                color = Color(0xFFFF9500),
                                radius = 6f,
                                center = pos
                            )
                            drawLine(
                                color = Color(0xFFFF9500),
                                start = Offset(pos.x - lineLength, pos.y),
                                end   = Offset(pos.x + lineLength, pos.y),
                                strokeWidth = 2f
                            )
                            drawLine(
                                color = Color(0xFFFF9500),
                                start = Offset(pos.x, pos.y - lineLength),
                                end   = Offset(pos.x, pos.y + lineLength),
                                strokeWidth = 2f
                            )
                        }
                    }

                    // Tap hint
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

                // ── Bottom controls ──────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (hoopPosition != null) {
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
                                val (normX, normY) = normHoopPos ?: return@Button
                                onHoopConfirmed(normX, normY)
                            },
                            enabled = normHoopPos != null,
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

/**
 * Calculates the rect an image occupies inside a container when using
 * ContentScale.Fit (maintains aspect ratio, no cropping).
 */
private data class FitRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    val right  get() = left + width
    val bottom get() = top + height
}

private fun calculateFitRect(
    containerW: Float,
    containerH: Float,
    imageW: Float,
    imageH: Float
): FitRect {
    val scale = min(containerW / imageW, containerH / imageH)
    val dispW = imageW * scale
    val dispH = imageH * scale
    val left  = (containerW - dispW) / 2f
    val top   = (containerH - dispH) / 2f
    return FitRect(left, top, dispW, dispH)
}