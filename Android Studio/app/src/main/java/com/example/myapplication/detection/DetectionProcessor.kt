package com.example.myapplication.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper
import org.tensorflow.lite.examples.objectdetection.detectors.Category
import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection
import java.io.File
import java.io.FileOutputStream

class DetectionProcessor {

    @RequiresApi(Build.VERSION_CODES.P)
    suspend fun processVideo(
        context: Context,
        videoUri: Uri,
        onProgress: (progress: Float, frame: Int, total: Int) -> Unit
    ): DetectionResult = withContext(Dispatchers.Default) {

        val startTime = System.currentTimeMillis()

        val cacheFile = copyVideoToCache(context, videoUri)
            ?: throw Exception("Failed to access video")

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(cacheFile.absolutePath)

        val durationMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

        val frameRate =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull() ?: 30f

        val videoW = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
        )?.toIntOrNull() ?: 1280

        val videoH = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
        )?.toIntOrNull() ?: 720

        val imgsz = 736
        val scale = imgsz.toFloat() / maxOf(videoW, videoH)
        val targetWidth  = (videoW * scale).toInt()
        val targetHeight = (videoH * scale).toInt()

        android.util.Log.d("TIMING",
            "Video: ${videoW}x${videoH} → Target: ${targetWidth}x${targetHeight}")

        val frameIntervalMs = (1000f / frameRate).toLong().coerceAtLeast(33L)
        val totalFrames = (durationMs / frameIntervalMs).toInt()

        if (totalFrames <= 0) {
            retriever.release()
            throw Exception("Could not determine video length")
        }

        val frameSkip = 4
        val framesToProcess = (0 until totalFrames step frameSkip).toList()
        val processCount = framesToProcess.size

        val detectionResults = mutableListOf<FrameDetection>()
        var lastDetections = listOf<ObjectDetection>()
        var lastWidth = 0
        var lastHeight = 0

        val listener = object : ObjectDetectorHelper.DetectorListener {
            override fun onError(error: String) {
                android.util.Log.e("TIMING", "Detector error: $error")
            }
            override fun onResults(
                results: List<ObjectDetection>,
                inferenceTimeMs: Long,
                imageHeight: Int,
                imageWidth: Int
            ) {
                lastDetections = results.map {
                    ObjectDetection(
                        RectF(it.boundingBox),
                        Category(it.category.label, it.category.confidence)
                    )
                }
                lastWidth = imageWidth
                lastHeight = imageHeight
            }
        }

        val detector = ObjectDetectorHelper(
            threshold = 0.4f,
            numThreads = 6,
            maxResults = 1,
            currentDelegate = ObjectDetectorHelper.DELEGATE_GPU,
            currentModel = ObjectDetectorHelper.MODEL_YOLO,
            context = context,
            objectDetectorListener = listener
        )

        val channel = Channel<Pair<Long, Bitmap>>(capacity = 16)

        // Producer
        val producerJob = launch(Dispatchers.IO) {
            var totalRetrievalMs = 0L
            var totalScaleMs = 0L
            var framesRetrieved = 0

            for (frameIndex in framesToProcess) {
                try {
                    val retrievalStart = System.currentTimeMillis()
                    val raw = retriever.getFrameAtIndex(frameIndex)
                    val retrievalMs = System.currentTimeMillis() - retrievalStart
                    totalRetrievalMs += retrievalMs

                    if (raw != null) {
                        val scaleStart = System.currentTimeMillis()
                        val timestampMs = frameIndex * frameIntervalMs
                        val scaled = Bitmap.createScaledBitmap(raw, targetWidth, targetHeight, false)
                        raw.recycle()
                        val scaleMs = System.currentTimeMillis() - scaleStart
                        totalScaleMs += scaleMs
                        framesRetrieved++

                        android.util.Log.d("TIMING",
                            "Frame $frameIndex: retrieval=${retrievalMs}ms scale=${scaleMs}ms")

                        channel.send(Pair(timestampMs, scaled))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TIMING", "Frame $frameIndex error: ${e.message}")
                }
            }

            android.util.Log.d("TIMING", "=== RETRIEVAL SUMMARY ===")
            android.util.Log.d("TIMING", "Total frames retrieved: $framesRetrieved")
            android.util.Log.d("TIMING", "Total retrieval time: ${totalRetrievalMs}ms")
            android.util.Log.d("TIMING", "Total scale time: ${totalScaleMs}ms")
            android.util.Log.d("TIMING",
                "Avg retrieval per frame: ${if (framesRetrieved > 0) totalRetrievalMs / framesRetrieved else 0}ms")

            channel.close()
        }

        // Consumer
        var processedCount = 0
        var totalInferenceMs = 0L

        for ((timestampMs, bitmap) in channel) {
            lastDetections = emptyList()
            lastWidth  = bitmap.width
            lastHeight = bitmap.height

            val inferenceStart = System.currentTimeMillis()
            detector.detect(bitmap, 0)
            val inferenceMs = System.currentTimeMillis() - inferenceStart
            totalInferenceMs += inferenceMs

            android.util.Log.d("TIMING",
                "Frame $processedCount: inference=${inferenceMs}ms " +
                        "detections=${lastDetections.size}")

            detectionResults.add(
                FrameDetection(
                    timestampMs = timestampMs,
                    detections  = lastDetections,
                    frameWidth  = lastWidth,
                    frameHeight = lastHeight
                )
            )
            bitmap.recycle()

            processedCount++
            onProgress(
                processedCount.toFloat() / processCount,
                processedCount,
                processCount
            )
        }

        producerJob.join()
        retriever.release()
        detector.clearObjectDetector()

        val totalMs = System.currentTimeMillis() - startTime
        android.util.Log.d("TIMING", "=== FINAL SUMMARY ===")
        android.util.Log.d("TIMING", "Total frames processed: $processedCount")
        android.util.Log.d("TIMING", "Total processing time: ${totalMs}ms")
        android.util.Log.d("TIMING", "Total inference time: ${totalInferenceMs}ms")
        android.util.Log.d("TIMING",
            "Avg inference per frame: ${if (processedCount > 0) totalInferenceMs / processedCount else 0}ms")
        android.util.Log.d("TIMING", "Overhead (retrieval+other): ${totalMs - totalInferenceMs}ms")

        DetectionResult(
            frames           = detectionResults,
            totalFrames      = totalFrames,
            totalDetections  = detectionResults.sumOf { it.detections.size },
            processingTimeMs = totalMs,
            cacheFile        = cacheFile
        )
    }

    private fun copyVideoToCache(context: Context, uri: Uri): File? {
        return try {
            val cacheFile =
                File(context.cacheDir, "detection_video_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output, 8192)
                }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) cacheFile else null
        } catch (e: Exception) {
            null
        }
    }
}