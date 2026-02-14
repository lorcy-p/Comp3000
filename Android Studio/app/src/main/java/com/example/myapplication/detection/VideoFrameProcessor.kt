package com.example.app.ui.detection

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper class to extract frames from a video file for object detection processing.
 */
class VideoFrameProcessor(
    private val context: Context
) {
    private var retriever: MediaMetadataRetriever? = null
    private var videoDurationMs: Long = 0
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var frameRate: Float = 30f

    /**
     * Initialize the processor with a video URI
     */
    suspend fun initialize(videoUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            retriever = MediaMetadataRetriever().apply {
                setDataSource(context, videoUri)
            }
            
            // Get video metadata
            videoDurationMs = retriever?.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            
            videoWidth = retriever?.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0
            
            videoHeight = retriever?.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0
            
            // Try to get frame rate, default to 30fps
            val frameRateStr = retriever?.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
            )
            frameRate = frameRateStr?.toFloatOrNull() ?: 30f
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Get video duration in milliseconds
     */
    fun getDurationMs(): Long = videoDurationMs

    /**
     * Get video dimensions
     */
    fun getVideoSize(): Pair<Int, Int> = Pair(videoWidth, videoHeight)

    /**
     * Get frame rate
     */
    fun getFrameRate(): Float = frameRate

    /**
     * Extract a frame at the specified timestamp (in milliseconds)
     */
    suspend fun getFrameAtTime(timeMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        try {
            retriever?.getFrameAtTime(
                timeMs * 1000, // Convert to microseconds
                MediaMetadataRetriever.OPTION_CLOSEST
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Extract a frame at the specified timestamp with specific dimensions
     */
    suspend fun getScaledFrameAtTime(timeMs: Long, width: Int, height: Int): Bitmap? = 
        withContext(Dispatchers.IO) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                    retriever?.getScaledFrameAtTime(
                        timeMs * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        width,
                        height
                    )
                } else {
                    val frame = retriever?.getFrameAtTime(
                        timeMs * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    )
                    frame?.let {
                        Bitmap.createScaledBitmap(it, width, height, true)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * Release resources
     */
    fun release() {
        try {
            retriever?.release()
            retriever = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
