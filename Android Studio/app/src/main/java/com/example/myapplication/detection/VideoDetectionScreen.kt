package com.example.myapplication.detection

import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.tensorflow.lite.examples.objectdetection.VideoOverlayView
import com.example.myapplication.utils.ProcessingUI
import kotlin.math.abs

data class FrameDetection(
    val timestampMs: Long,
    val detections: List<org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection>,
    val frameWidth: Int,
    val frameHeight: Int
)

@OptIn(UnstableApi::class)
@Composable
fun VideoDetectionScreen(
    videoUriString: String,
    navController: NavController? = null,
    viewModel: VideoDetectionViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoUri = remember { Uri.parse(Uri.decode(videoUriString)) }

    val processingState by viewModel.state.collectAsState()

    // These are fine as local state — they're view-layer concerns
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var currentDetectionCount by remember { mutableStateOf(0) }
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }
    var overlayView by remember { mutableStateOf<VideoOverlayView?>(null) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Kick off processing — ViewModel guard means this is safe to call on every rotation
    LaunchedEffect(videoUri) {
        viewModel.startProcessing(videoUri)
    }

    // Create ExoPlayer once processing finishes
    LaunchedEffect(processingState) {
        if (processingState is ProcessingState.Complete && exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(videoUri))
                repeatMode = Player.REPEAT_MODE_ALL
                prepare()
            }
        }
    }

    // Sync overlay with playback position
    LaunchedEffect(processingState, exoPlayer, overlayView) {
        val state = processingState
        val player = exoPlayer
        val overlay = overlayView

        if (state is ProcessingState.Complete && player != null && overlay != null) {
            val frames = state.result.frames
            while (isActive) {
                currentPositionMs = player.currentPosition
                val frameDetection = findClosestFrame(frames, currentPositionMs)
                if (frameDetection != null) {
                    currentDetectionCount = frameDetection.detections.size
                    withContext(Dispatchers.Main) {
                        overlay.setResults(
                            frameDetection.detections,
                            frameDetection.frameHeight,
                            frameDetection.frameWidth
                        )
                        overlay.invalidate()
                    }
                }
                delay(33)
            }
        }
    }

    // ExoPlayer video size + autoplay
    DisposableEffect(exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) player.play()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Pause/resume on lifecycle events
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer?.pause()
                Lifecycle.Event.ON_RESUME -> if (isPlaying) exoPlayer?.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Release ExoPlayer only on permanent exit, not rotation
    DisposableEffect(Unit) {
        onDispose { exoPlayer?.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = processingState) {

            is ProcessingState.Idle -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            is ProcessingState.Processing -> {
                ProcessingUI(
                    progress = state.progress,
                    stage = state.stage,
                    currentFrame = state.currentFrame,
                    totalFrames = state.totalFrames,
                    onCancel = { navController?.navigateUp() }
                )
            }

            // New — inserted between Processing and Complete
            is ProcessingState.AwaitingHoopSelection -> {
                HoopSelectionScreen(
                    cacheFilePath = state.result.cacheFile.absolutePath,
                    videoDurationMs = state.result.frames
                        .lastOrNull()?.timestampMs ?: 0L,
                    onHoopConfirmed = { normX, normY ->
                        viewModel.confirmHoopPosition(normX, normY)
                    },
                    onBack = { navController?.navigateUp() }
                )
            }

            is ProcessingState.Complete -> {
                PlaybackUI(
                    exoPlayer = exoPlayer,
                    overlayView = overlayView,
                    onOverlayCreated = { overlayView = it },
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    currentDetectionCount = currentDetectionCount,
                    videoWidth = videoWidth,
                    videoHeight = videoHeight,
                    totalFrames = state.result.totalFrames,
                    totalDetections = state.result.totalDetections,
                    processingTimeMs = state.result.processingTimeMs,
                    onPlayPause = {
                        val player = exoPlayer ?: return@PlaybackUI
                        if (player.isPlaying) { player.pause(); isPlaying = false }
                        else { player.play(); isPlaying = true }
                    },
                    onSeek = { fraction ->
                        exoPlayer?.let { it.seekTo((fraction * it.duration).toLong()) }
                    },
                    onBack = { viewModel.backToHoopSelection() }  // back goes to hoop selection, not nav up
                )
            }

            is ProcessingState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("❌", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(state.message, color = Color.Red, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { navController?.navigateUp() }) {
                        Text("Go Back")
                    }
                }
            }
        }
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
        val prevDiff = abs(targetMs - prev.timestampMs)
        val currDiff = abs(targetMs - curr.timestampMs)
        return if (prevDiff < currDiff) prev else curr
    }
    return frames[low]
}