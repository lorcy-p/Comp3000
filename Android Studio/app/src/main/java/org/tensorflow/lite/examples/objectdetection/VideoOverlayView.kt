package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection
import java.util.LinkedList
import kotlin.math.min

/**
 * Custom overlay view for VIDEO detection.
 * Uses FIT scaling (min) instead of FILL scaling (max) to properly
 * align bounding boxes with video content.
 */
class VideoOverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: List<ObjectDetection> = LinkedList<ObjectDetection>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    // Scaling and offset for FIT mode
    private var scaleFactor: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    private var bounds = Rect()

    init {
        initPaints()
    }

    fun clear() {
        results = LinkedList()
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        // Green color for bounding boxes
        boxPaint.color = Color.GREEN
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        for (result in results) {
            val boundingBox = result.boundingBox

            // Apply scale and offset for FIT mode
            val left = boundingBox.left * scaleFactor + offsetX
            val top = boundingBox.top * scaleFactor + offsetY
            val right = boundingBox.right * scaleFactor + offsetX
            val bottom = boundingBox.bottom * scaleFactor + offsetY

            // Draw bounding box around detected objects
            val drawableRect = RectF(left, top, right, bottom)
            canvas.drawRect(drawableRect, boxPaint)

            // Create text to display alongside detected objects
            val drawableText =
                result.category.label + " " +
                        String.format("%.2f", result.category.confidence)

            // Draw rect behind display text
            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()
            canvas.drawRect(
                left,
                top,
                left + textWidth + BOUNDING_RECT_TEXT_PADDING,
                top + textHeight + BOUNDING_RECT_TEXT_PADDING,
                textBackgroundPaint
            )

            // Draw text for detected object
            canvas.drawText(drawableText, left, top + bounds.height(), textPaint)
        }
    }

    /**
     * 
     * @param detectionResults List of detected objects with bounding boxes in image coordinates
     * @param imageHeight Original image/frame height
     * @param imageWidth Original image/frame width
     */
    fun setResults(
        detectionResults: List<ObjectDetection>,
        imageHeight: Int,
        imageWidth: Int,
    ) {
        results = detectionResults

        // For video in FIT mode , use MIN scale factor
        // This ensures the image fits within the view while maintaining aspect ratio
        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight
        scaleFactor = min(scaleX, scaleY)

        // Calculate offset for centering (letterboxing)
        val scaledImageWidth = imageWidth * scaleFactor
        val scaledImageHeight = imageHeight * scaleFactor
        offsetX = (width - scaledImageWidth) / 2f
        offsetY = (height - scaledImageHeight) / 2f
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}
