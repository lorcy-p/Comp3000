package com.example.myapplication.detection

import android.graphics.Bitmap
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
import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection


@OptIn(UnstableApi::class)
@Composable
fun VideoDetectionScreen(
    videoUriString: String,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoUri = remember { Uri.parse(Uri.decode(videoUriString)) }

    // ExoPlayer instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
        }
    }

    // Detection state
    var isDetecting by remember { mutableStateOf(true) }
    var inferenceTime by remember { mutableStateOf(0L) }
    var detectionResults by remember { mutableStateOf<List<ObjectDetection>>(emptyList()) }
    var imageWidth by remember { mutableStateOf(0) }
    var imageHeight by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }

    // Video dimensions
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

    // View references
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var overlayView by remember { mutableStateOf<VideoOverlayView?>(null) }

    // Frame extractor
    var mediaRetriever by remember { mutableStateOf<MediaMetadataRetriever?>(null) }

    // Object detector helper
    var objectDetectorHelper by remember { mutableStateOf<ObjectDetectorHelper?>(null) }

    val detectorListener = remember {
        object : ObjectDetectorHelper.DetectorListener {
            override fun onError(error: String) {
                android.util.Log.e("DETECTION", "Error: $error")
            }

            override fun onResults(
                results: List<ObjectDetection>,
                inferenceTimeMs: Long,
                imgHeight: Int,
                imgWidth: Int
            ) {
                detectionResults = results
                inferenceTime = inferenceTimeMs
                imageHeight = imgHeight
                imageWidth = imgWidth
            }
        }
    }

    // Initialize detector
    LaunchedEffect(Unit) {
        objectDetectorHelper = ObjectDetectorHelper(
            threshold = 0.5f,
            numThreads = 4,
            maxResults = 1,
            currentDelegate = ObjectDetectorHelper.DELEGATE_CPU,
            currentModel = ObjectDetectorHelper.MODEL_YOLO,
            context = context,
            objectDetectorListener = detectorListener
        )
    }

    // Initialize media retriever for frame extraction
    LaunchedEffect(videoUri) {
        withContext(Dispatchers.IO) {
            try {
                mediaRetriever = MediaMetadataRetriever().apply {
                    setDataSource(context, videoUri)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Listen for video size changes
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    exoPlayer.play()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Handle lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> if (isPlaying) exoPlayer.play()
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
            isDetecting = false
            exoPlayer.release()
            try {
                mediaRetriever?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            objectDetectorHelper?.clearObjectDetector()
        }
    }

    // Detection coroutine
    LaunchedEffect(isDetecting, mediaRetriever, objectDetectorHelper) {
        if (isDetecting && mediaRetriever != null && objectDetectorHelper != null) {
            while (isActive && isDetecting) {
                try {
                    val currentPosition = exoPlayer.currentPosition

                    // Extract frame at current position
                    val frame = withContext(Dispatchers.IO) {
                        try {
                            mediaRetriever?.getFrameAtTime(
                                currentPosition * 1000, // Convert to microseconds
                                MediaMetadataRetriever.OPTION_CLOSEST
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }

                    frame?.let { bitmap ->
                        // Run detection
                        withContext(Dispatchers.Default) {
                            objectDetectorHelper?.detect(bitmap, 0)
                        }

                        // Update overlay on main thread
                        withContext(Dispatchers.Main) {
                            overlayView?.setResults(
                                detectionResults,
                                imageHeight,
                                imageWidth
                            )
                            overlayView?.invalidate()
                        }
                    }

                    delay(100) // ~10 FPS
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(100)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 70.dp, bottom = 150.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // PlayerView (ExoPlayer)
                        val player = PlayerView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.player = exoPlayer
                            useController = false // Hide default controls
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        addView(player)
                        playerView = player

                        // Overlay on top
                        val overlay = VideoOverlayView(ctx, null).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                        addView(overlay)
                        overlayView = overlay
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { navController?.navigateUp() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C1C1E),
                    contentColor = Color.White
                )
            ) {
                Text("← Back")
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Inference: ${inferenceTime}ms",
                    color = Color.White,
                    fontSize = 14.sp
                )
                if (videoWidth > 0) {
                    Text(
                        text = "Video: ${videoWidth}x${videoHeight}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
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
                text = "Detected: ${detectionResults.size} objects",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (detectionResults.isNotEmpty()) {
                Text(
                    text = detectionResults.take(5).joinToString(", ") {
                        "${it.category.label} (${String.format("%.0f", it.category.confidence * 100)}%)"
                    },
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                            isPlaying = false
                        } else {
                            exoPlayer.play()
                            isPlaying = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1C1C1E),
                        contentColor = Color.White
                    )
                ) {
                    Text(if (isPlaying) "⏸ Pause" else "▶ Play")
                }

                Button(
                    onClick = { isDetecting = !isDetecting },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDetecting) Color(0xFF34C759) else Color(0xFF1C1C1E),
                        contentColor = Color.White
                    )
                ) {
                    Text(if (isDetecting) "● Detecting" else "○ Paused")
                }
            }
        }
    }
}