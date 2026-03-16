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
import com.example.myapplication.utils.ProcessingUI

/**
 * Detection result for a single frame
 */
data class FrameDetection(
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


    val processor = remember { DetectionProcessor() }

    LaunchedEffect(videoUri) {
        try {

            screenState = ScreenState.PROCESSING
            processingStage = "Processing frames..."

            val result = processor.processVideo(
                context,
                videoUri
            ) { progress, frame, total ->

                processingProgress = progress
                currentFrame = frame
                totalFrames = total
            }

            frameDetections = result.frames
            totalFrames = result.totalFrames
            totalDetections = result.totalDetections
            processingTimeMs = result.processingTimeMs
            cachedVideoFile = result.cacheFile

            exoPlayer = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(videoUri))
                repeatMode = Player.REPEAT_MODE_ALL
                prepare()
            }

            screenState = ScreenState.PLAYBACK

        } catch (e: Exception) {

            errorMessage = e.message
            screenState = ScreenState.ERROR
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

