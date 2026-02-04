package com.example.app.ui.detection

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import kotlinx.coroutines.*
import org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper
import org.tensorflow.lite.examples.objectdetection.OverlayView
import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage

/**
 * Screen that displays a video with real-time YOLO object detection overlay.
 * Uses ExoPlayer for video playback and OverlayView for drawing bounding boxes.
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
    
    // Frame processor for extracting video frames
    val frameProcessor = remember { VideoFrameProcessor(context) }
    
    // ExoPlayer instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }
    
    // Object detector helper
    val detectorListener = remember {
        object : ObjectDetectorHelper.DetectorListener {
            override fun onError(error: String) {
                // Handle error - could show a toast or log
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
            maxResults = 5,
            currentDelegate = ObjectDetectorHelper.DELEGATE_CPU,
            currentModel = ObjectDetectorHelper.MODEL_YOLO,
            context = context,
            objectDetectorListener = detectorListener
        )
    }
    
    // Initialize frame processor and start detection loop
    LaunchedEffect(videoUri) {
        frameProcessor.initialize(videoUri)
    }
    
    // Detection coroutine - extracts frames and runs detection
    LaunchedEffect(isDetecting) {
        if (isDetecting) {
            while (isActive && isDetecting) {
                try {
                    val currentPosition = exoPlayer.currentPosition
                    val frame = frameProcessor.getFrameAtTime(currentPosition)
                    
                    frame?.let { bitmap ->
                        // Run detection on the frame
                        withContext(Dispatchers.Default) {
                            objectDetectorHelper.detect(bitmap, 0)
                        }
                    }
                    
                    // Control detection frame rate (~10 fps for detection)
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
            exoPlayer.release()
            frameProcessor.release()
            objectDetectorHelper.clearObjectDetector()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video player with overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .align(Alignment.Center)
        ) {
            // ExoPlayer view
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Detection overlay
            AndroidView(
                factory = { ctx ->
                    OverlayView(ctx, null).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { overlayView ->
                    if (imageWidth > 0 && imageHeight > 0) {
                        overlayView.setResults(detectionResults, imageHeight, imageWidth)
                        overlayView.invalidate()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Top bar with inference time and back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            Button(
                onClick = { navController?.navigateUp() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C1C1E),
                    contentColor = Color.White
                )
            ) {
                Text("← Back")
            }
            
            // Inference time display
            Surface(
                color = Color(0xFF1C1C1E),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Inference: ${inferenceTime}ms",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        
        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Detection count
            Text(
                text = "Detected: ${detectionResults.size} objects",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Detection categories
            if (detectionResults.isNotEmpty()) {
                Text(
                    text = detectionResults.joinToString(", ") { 
                        "${it.category.label} (${String.format("%.1f", it.category.confidence * 100)}%)"
                    },
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Toggle detection button
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { isDetecting = !isDetecting },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDetecting) Color(0xFF007AFF) else Color(0xFF1C1C1E),
                        contentColor = Color.White
                    )
                ) {
                    Text(if (isDetecting) "Pause Detection" else "Resume Detection")
                }
                
                Button(
                    onClick = { navController?.navigateUp() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1C1C1E),
                        contentColor = Color.White
                    )
                ) {
                    Text("Done")
                }
            }
        }
    }
}
