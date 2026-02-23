package com.example.myapplication.detection

import android.content.Context
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import kotlinx.coroutines.*
import org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper
import org.tensorflow.lite.examples.objectdetection.VideoOverlayView
import org.tensorflow.lite.examples.objectdetection.detectors.Category
import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection
import java.io.File
import java.io.FileOutputStream

/**
 * Detection result for a single frame
 */
private data class FrameDetection(
    val timestampMs: Long,
    val detections: List<ObjectDetection>,
    val frameWidth: Int,
    val frameHeight: Int
)

/**
 * Screen states
 */
private enum class ScreenState {
    LOADING,
    PROCESSING,
    PLAYBACK,
    ERROR
}


@OptIn(UnstableApi::class)
@Composable
fun VideoDetectionScreen(
    videoUriString: String,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoUri = remember { Uri.parse(Uri.decode(videoUriString)) }

    // Screen state
    var screenState by remember { mutableStateOf(ScreenState.LOADING) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Processing state
    var processingProgress by remember { mutableStateOf(0f) }
    var processingStage by remember { mutableStateOf("Initializing...") }
    var currentFrame by remember { mutableStateOf(0) }
    var totalFrames by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(true) }

    // Detection results
    var frameDetections by remember { mutableStateOf<List<FrameDetection>>(emptyList()) }
    var processingTimeMs by remember { mutableStateOf(0L) }
    var totalDetections by remember { mutableStateOf(0) }

    // Playback state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var currentDetectionCount by remember { mutableStateOf(0) }
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

    // Cached video file
    var cachedVideoFile by remember { mutableStateOf<File?>(null) }

    // ExoPlayer
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Overlay view reference
    var overlayView by remember { mutableStateOf<VideoOverlayView?>(null) }


    LaunchedEffect(videoUri) {
        try {
            val startTime = System.currentTimeMillis()
            screenState = ScreenState.PROCESSING

            processingStage = "Preparing video..."

            // Copy video to cache
            val cacheFile = withContext(Dispatchers.IO) {
                copyVideoToCache(context, videoUri)
            }

            if (cacheFile == null) {
                errorMessage = "Failed to access video file"
                screenState = ScreenState.ERROR
                return@LaunchedEffect
            }
            cachedVideoFile = cacheFile

            processingStage = "Analyzing video..."

            // Get video metadata
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(cacheFile.absolutePath)
            } catch (e: Exception) {
                errorMessage = "Failed to read video: ${e.message}"
                screenState = ScreenState.ERROR
                cacheFile.delete()
                return@LaunchedEffect
            }

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f

            // Calculate frame interval (process every frame)
            val frameIntervalMs = (1000f / frameRate).toLong().coerceAtLeast(33L)
            totalFrames = (durationMs / frameIntervalMs).toInt()

            if (totalFrames <= 0) {
                errorMessage = "Could not determine video length"
                screenState = ScreenState.ERROR
                retriever.release()
                cacheFile.delete()
                return@LaunchedEffect
            }

            processingStage = "Loading detection model..."

            // Initialize detector with synchronous result capture
            val detectionResults = mutableListOf<FrameDetection>()
            var lastDetections = listOf<ObjectDetection>()
            var lastWidth = 0
            var lastHeight = 0

            val listener = object : ObjectDetectorHelper.DetectorListener {
                override fun onError(error: String) {
                    android.util.Log.e("PROCESSING", "Detector error: $error")
                }

                override fun onResults(
                    results: List<ObjectDetection>,
                    inferenceTimeMs: Long,
                    imageHeight: Int,
                    imageWidth: Int
                ) {
                    // Deep copy results
                    lastDetections = results.map { det ->
                        ObjectDetection(
                            RectF(det.boundingBox),
                            Category(det.category.label, det.category.confidence)
                        )
                    }
                    lastWidth = imageWidth
                    lastHeight = imageHeight
                }
            }

            val detector = ObjectDetectorHelper(
                threshold = 0.5f,
                numThreads = 4,
                maxResults = 5,
                currentDelegate = ObjectDetectorHelper.DELEGATE_CPU,
                currentModel = ObjectDetectorHelper.MODEL_YOLO,
                context = context,
                objectDetectorListener = listener
            )

            processingStage = "Processing frames..."

            // Process each frame
            withContext(Dispatchers.Default) {
                for (i in 0 until totalFrames) {
                    if (!isProcessing) break

                    val timestampMs = i * frameIntervalMs
                    val timestampUs = timestampMs * 1000L

                    try {
                        val bitmap = retriever.getFrameAtTime(
                            timestampUs,
                            MediaMetadataRetriever.OPTION_CLOSEST
                        )

                        if (bitmap != null) {
                            lastDetections = emptyList()
                            lastWidth = bitmap.width
                            lastHeight = bitmap.height

                            // Run detection
                            detector.detect(bitmap, 0)

                            // Store results
                            detectionResults.add(
                                FrameDetection(
                                    timestampMs = timestampMs,
                                    detections = lastDetections,
                                    frameWidth = lastWidth,
                                    frameHeight = lastHeight
                                )
                            )

                            bitmap.recycle()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PROCESSING", "Frame $i error: ${e.message}")
                    }

                    // Update progress
                    withContext(Dispatchers.Main) {
                        currentFrame = i + 1
                        processingProgress = (i + 1).toFloat() / totalFrames
                    }
                }
            }

            retriever.release()
            detector.clearObjectDetector()

            if (!isProcessing) {
                // Cancelled
                cacheFile.delete()
                navController?.navigateUp()
                return@LaunchedEffect
            }

            // Store results
            frameDetections = detectionResults
            processingTimeMs = System.currentTimeMillis() - startTime
            totalDetections = detectionResults.sumOf { it.detections.size }

            processingStage = "Complete!"
            delay(300)

            // Create ExoPlayer for playback
            withContext(Dispatchers.Main) {
                exoPlayer = ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(videoUri))
                    repeatMode = Player.REPEAT_MODE_ALL
                    prepare()
                }
            }

            // Switch to playback
            screenState = ScreenState.PLAYBACK

        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            screenState = ScreenState.ERROR
            android.util.Log.e("PROCESSING", "Processing error", e)
        }
    }

    // Sync overlay with video playback
    LaunchedEffect(screenState, exoPlayer, overlayView) {
        if (screenState == ScreenState.PLAYBACK && exoPlayer != null && overlayView != null && frameDetections.isNotEmpty()) {
            while (isActive) {
                val player = exoPlayer ?: break
                currentPositionMs = player.currentPosition

                // Find closest frame detection
                val frameDetection = findClosestFrame(frameDetections, currentPositionMs)

                if (frameDetection != null) {
                    currentDetectionCount = frameDetection.detections.size

                    withContext(Dispatchers.Main) {
                        overlayView?.setResults(
                            frameDetection.detections,
                            frameDetection.frameHeight,
                            frameDetection.frameWidth
                        )
                        overlayView?.invalidate()
                    }
                }

                delay(33) // ~30 FPS sync
            }
        }
    }

    // Handle ExoPlayer video size
    DisposableEffect(exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}

        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    player.play()
                }
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
        }
    }

    // Handle lifecycle
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer?.pause()
                Lifecycle.Event.ON_RESUME -> if (isPlaying) exoPlayer?.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            isProcessing = false
            exoPlayer?.release()
            cachedVideoFile?.delete()
        }
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (screenState) {
            ScreenState.LOADING -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            ScreenState.PROCESSING -> {
                ProcessingUI(
                    progress = processingProgress,
                    stage = processingStage,
                    currentFrame = currentFrame,
                    totalFrames = totalFrames,
                    onCancel = {
                        isProcessing = false
                        navController?.navigateUp()
                    }
                )
            }

            ScreenState.PLAYBACK -> {
                PlaybackUI(
                    exoPlayer = exoPlayer,
                    overlayView = overlayView,
                    onOverlayCreated = { overlayView = it },
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    currentDetectionCount = currentDetectionCount,
                    videoWidth = videoWidth,
                    videoHeight = videoHeight,
                    totalFrames = totalFrames,
                    totalDetections = totalDetections,
                    processingTimeMs = processingTimeMs,
                    onPlayPause = {
                        val player = exoPlayer ?: return@PlaybackUI
                        if (player.isPlaying) {
                            player.pause()
                            isPlaying = false
                        } else {
                            player.play()
                            isPlaying = true
                        }
                    },
                    onSeek = { fraction ->
                        exoPlayer?.let { player ->
                            player.seekTo((fraction * player.duration).toLong())
                        }
                    },
                    onBack = { navController?.navigateUp() }
                )
            }

            ScreenState.ERROR -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("❌", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(errorMessage ?: "Unknown error", color = Color.Red, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { navController?.navigateUp() }) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}

// ==================== UI Components ====================

@Composable
private fun ProcessingUI(
    progress: Float,
    stage: String,
    currentFrame: Int,
    totalFrames: Int,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Processing Video",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Circular progress
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF007AFF),
                strokeWidth = 8.dp,
                trackColor = Color(0xFF1C1C1E)
            )

            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = stage, fontSize = 16.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(8.dp))

        if (totalFrames > 0) {
            Text(
                text = "Frame $currentFrame / $totalFrames",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Color(0xFF007AFF),
            trackColor = Color(0xFF1C1C1E)
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlaybackUI(
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
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Video + Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp, bottom = 200.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // PlayerView
                        val player = PlayerView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        addView(player)

                        // Overlay
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
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    Text("← Back")
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Pre-processed", color = Color(0xFF34C759), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (videoWidth > 0) {
                        Text("${videoWidth}x${videoHeight}", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Frames", "$totalFrames")
                StatItem("Time", "${processingTimeMs / 1000}s")
                StatItem("Detections", "$totalDetections")
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.9f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Detected: $currentDetectionCount basketball${if (currentDetectionCount != 1) "s" else ""}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = formatTime(currentPositionMs),
                color = Color.Gray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Playback controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { exoPlayer?.seekTo(maxOf(0, (exoPlayer?.currentPosition ?: 0) - 5000)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1E), contentColor = Color.White)
                ) {
                    Text("⏪ 5s")
                }

                Button(
                    onClick = onPlayPause,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF), contentColor = Color.White),
                    modifier = Modifier.size(64.dp)
                ) {
                    Text(if (isPlaying) "⏸" else "▶", fontSize = 20.sp)
                }

                Button(
                    onClick = { exoPlayer?.seekTo(minOf(exoPlayer?.duration ?: 0, (exoPlayer?.currentPosition ?: 0) + 5000)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1E), contentColor = Color.White)
                ) {
                    Text("5s ⏩")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Seek bar
            Slider(
                value = if ((exoPlayer?.duration ?: 0) > 0) currentPositionMs.toFloat() / (exoPlayer?.duration ?: 1) else 0f,
                onValueChange = onSeek,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF007AFF),
                    activeTrackColor = Color(0xFF007AFF),
                    inactiveTrackColor = Color(0xFF1C1C1E)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.Gray, fontSize = 12.sp)
    }
}

// ==================== Helper Functions ====================

private fun copyVideoToCache(context: Context, uri: Uri): File? {
    return try {
        val cacheFile = File(context.cacheDir, "detection_video_${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        }
        if (cacheFile.exists() && cacheFile.length() > 0) cacheFile else null
    } catch (e: Exception) {
        android.util.Log.e("COPY", "Failed to copy video: ${e.message}")
        null
    }
}

private fun findClosestFrame(frames: List<FrameDetection>, targetMs: Long): FrameDetection? {
    if (frames.isEmpty()) return null

    var low = 0
    var high = frames.size - 1

    while (low < high) {
        val mid = (low + high) / 2
        if (frames[mid].timestampMs < targetMs) {
            low = mid + 1
        } else {
            high = mid
        }
    }

    if (low > 0) {
        val prev = frames[low - 1]
        val curr = frames[low]
        return if (kotlin.math.abs(targetMs - prev.timestampMs) < kotlin.math.abs(targetMs - curr.timestampMs)) prev else curr
    }

    return frames[low]
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}