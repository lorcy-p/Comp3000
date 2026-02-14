package com.example.myapplication.detection

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import kotlinx.coroutines.*
import org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper
import org.tensorflow.lite.examples.objectdetection.VideoOverlayView
import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection

/**
 * Screen that displays a video with real-time YOLO object detection overlay.
 * Matches the camera fragment approach for proper bounding box alignment.
 */
@Composable
fun VideoDetectionScreen(
    videoUriString: String,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val videoUri = remember { Uri.parse(Uri.decode(videoUriString)) }
    
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
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var overlayView by remember { mutableStateOf<VideoOverlayView?>(null) }
    var containerView by remember { mutableStateOf<FrameLayout?>(null) }
    
    // Frame extractor
    var mediaRetriever by remember { mutableStateOf<MediaMetadataRetriever?>(null) }
    
    // Initialize media retriever
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
    
    // Object detector helper
    val detectorListener = remember {
        object : ObjectDetectorHelper.DetectorListener {
            override fun onError(error: String) {
                // Handle error
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
    
    val objectDetectorHelper = remember {
        ObjectDetectorHelper(
            threshold = 0.5f,
            numThreads = 2,
            maxResults = 10,
            currentDelegate = ObjectDetectorHelper.DELEGATE_CPU,
            currentModel = ObjectDetectorHelper.MODEL_YOLO,
            context = context,
            objectDetectorListener = detectorListener
        )
    }
    
    // Detection coroutine
    LaunchedEffect(isDetecting, videoView, mediaRetriever) {
        if (isDetecting && videoView != null && mediaRetriever != null) {
            while (isActive && isDetecting) {
                try {
                    val currentPosition = videoView?.currentPosition?.toLong() ?: 0L
                    
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
                        // Run detection (similar to camera fragment)
                        withContext(Dispatchers.Default) {
                            objectDetectorHelper.detect(bitmap, 0) // 0 rotation for video
                        }
                        
                        // Update overlay on main thread (matching camera fragment approach)
                        withContext(Dispatchers.Main) {
                            overlayView?.setResults(
                                detectionResults,
                                imageHeight,
                                imageWidth
                            )
                            overlayView?.invalidate()
                        }
                    }
                    
                    // Detection at ~10 FPS
                    delay(100)
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(100)
                }
            }
        }
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            isDetecting = false
            try {
                mediaRetriever?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            objectDetectorHelper.clearObjectDetector()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video container - takes up available space between controls
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
                        containerView = this
                        
                        // VideoView
                        val video = VideoView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            ).apply {
                                gravity = android.view.Gravity.CENTER
                            }
                            
                            setVideoURI(videoUri)
                            
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                
                                // Get video dimensions
                                videoWidth = mp.videoWidth
                                videoHeight = mp.videoHeight
                                
                                // Calculate proper sizing to fit within container
                                post {
                                    val containerWidth = containerView?.width ?: width
                                    val containerHeight = containerView?.height ?: height
                                    
                                    if (containerWidth > 0 && containerHeight > 0 && videoWidth > 0 && videoHeight > 0) {
                                        val videoAspect = videoWidth.toFloat() / videoHeight
                                        val containerAspect = containerWidth.toFloat() / containerHeight
                                        
                                        val displayWidth: Int
                                        val displayHeight: Int
                                        
                                        if (videoAspect > containerAspect) {
                                            // Video is wider - fit to width
                                            displayWidth = containerWidth
                                            displayHeight = (containerWidth / videoAspect).toInt()
                                        } else {
                                            // Video is taller - fit to height
                                            displayHeight = containerHeight
                                            displayWidth = (containerHeight * videoAspect).toInt()
                                        }
                                        
                                        // Update VideoView size
                                        val videoLp = this.layoutParams as FrameLayout.LayoutParams
                                        videoLp.width = displayWidth
                                        videoLp.height = displayHeight
                                        videoLp.gravity = android.view.Gravity.CENTER
                                        this.layoutParams = videoLp
                                        
                                        
                                    }
                                }
                                
                                start()
                            }
                        }
                        addView(video)
                        videoView = video
                        
                        // VideoOverlayView on top - designed for video FIT mode
                        val overlay = VideoOverlayView(ctx, null).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            ).apply {
                                gravity = android.view.Gravity.CENTER
                            }
                            // Make overlay transparent for click-through
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
                        videoView?.let { 
                            if (it.isPlaying) {
                                it.pause()
                                isPlaying = false
                            } else {
                                it.start()
                                isPlaying = true
                            }
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
