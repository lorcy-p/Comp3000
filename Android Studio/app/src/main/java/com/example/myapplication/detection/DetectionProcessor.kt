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
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 0L

        val frameRate =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull() ?: 30f

        val frameIntervalMs = (1000f / frameRate).toLong().coerceAtLeast(33L)
        val totalFrames = (durationMs / frameIntervalMs).toInt()

        if (totalFrames <= 0) {
            retriever.release()
            throw Exception("Could not determine video length")
        }

        val frameSkip = 3
        val framesToProcess = (0 until totalFrames step frameSkip).toList()
        val processCount = framesToProcess.size

        val detectionResults = mutableListOf<FrameDetection>()

        var lastDetections = listOf<ObjectDetection>()
        var lastWidth = 0
        var lastHeight = 0

        val listener = object : ObjectDetectorHelper.DetectorListener {

            override fun onError(error: String) {}

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
            threshold = 0.5f,
            numThreads = 4,
            maxResults = 5,
            currentDelegate = ObjectDetectorHelper.DELEGATE_GPU,
            currentModel = ObjectDetectorHelper.MODEL_YOLO,
            context = context,
            objectDetectorListener = listener
        )

        // Channel buffers up to 8 bitmaps between producer and consumer
        val channel = Channel<Pair<Long, Bitmap>>(capacity = 8)

        // frame retrieval on IO thread using index-based access
        val producerJob = launch(Dispatchers.IO) {
            framesToProcess.forEachIndexed { idx, frameIndex ->
                try {

                    val bitmap = retriever.getFrameAtIndex(frameIndex)

                    if (bitmap != null) {
                        val timestampMs = frameIndex * frameIntervalMs

                        // Downscale before sending to reduce inference time
                        val scaledBitmap = Bitmap.createScaledBitmap(
                            bitmap,
                            bitmap.width / 2,
                            bitmap.height / 2,
                            true
                        )
                        bitmap.recycle()

                        channel.send(Pair(timestampMs, scaledBitmap))
                    }
                } catch (_: Exception) {}

                // Progress based on retrieval
                onProgress((idx + 1).toFloat() / processCount, idx + 1, processCount)
            }
            channel.close()
        }

        for ((timestampMs, bitmap) in channel) {
            lastDetections = emptyList()
            lastWidth = bitmap.width
            lastHeight = bitmap.height

            detector.detect(bitmap, 0)

            detectionResults.add(
                FrameDetection(
                    timestampMs,
                    lastDetections,
                    lastWidth,
                    lastHeight
                )
            )

            bitmap.recycle()
        }

        // Wait for producer to finish before continuing
        producerJob.join()

        retriever.release()
        detector.clearObjectDetector()

        DetectionResult(
            frames = detectionResults,
            totalFrames = totalFrames,
            totalDetections = detectionResults.sumOf { it.detections.size },
            processingTimeMs = System.currentTimeMillis() - startTime,
            cacheFile = cacheFile
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