package org.tensorflow.lite.examples.objectdetection.detectors

import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.support.image.TensorImage

/**
 * Interface for object detectors (YOLO, EfficientDet, etc.)
 */
interface ObjectDetector {
    fun detect(image: TensorImage, imageRotation: Int): DetectionResult
}

/**
 * Represents a detected object with its bounding box and category
 */
data class ObjectDetection(
    val boundingBox: RectF,
    val category: Category
)

/**
 * Category information for a detected object
 */
data class Category(
    val label: String,
    val confidence: Float
)

/**
 * Result from object detection containing the processed image and list of detections
 */
data class DetectionResult(
    val image: Bitmap,
    val detections: List<ObjectDetection>,
    var info: String? = null
)
