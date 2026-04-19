package org.tensorflow.lite.examples.objectdetection.detectors

import android.content.Context
import android.graphics.RectF
import com.ultralytics.yolo.ImageProcessing
import com.ultralytics.yolo.models.LocalYoloModel
import com.ultralytics.yolo.predict.detect.DetectedObject
import com.ultralytics.yolo.predict.detect.TfliteDetector
import org.tensorflow.lite.support.image.TensorImage


class YoloDetector(
    var confidenceThreshold: Float = 0.5f,
    var iouThreshold: Float = 0.3f,
    var numThreads: Int = 6,
    var maxResults: Int = 1,
    var currentDelegate: Int = 1,
    var currentModel: Int = 0,
    val context: Context
): ObjectDetector {

    private var yolo: TfliteDetector
    private var ip: ImageProcessing

    init {
        yolo = TfliteDetector(context)
        yolo.setIouThreshold(iouThreshold)
        yolo.setConfidenceThreshold(confidenceThreshold)

        val modelPath = "nano_best_quant.tflite"
        val metadataPath = "metadata.yaml"

        val config = LocalYoloModel(
            "detect",
            "tflite",
            modelPath,
            metadataPath,
        )


        val useGPU = currentDelegate == 1

        android.util.Log.d("YOLO", "Loading model — useGPU=$useGPU delegate=$currentDelegate")
        yolo.loadModel(config, useGPU)
        android.util.Log.d("YOLO", "Model loaded successfully")

        ip = ImageProcessing()
    }

    override fun detect(image: TensorImage, imageRotation: Int): DetectionResult  {

        val bitmap = image.bitmap
        
        // Store original bitmap dimensions before preprocessing
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        val ppImage = yolo.preprocess(bitmap)
        val results = yolo.predict(ppImage)

        val detections = ArrayList<ObjectDetection>()

        // Get preprocessed image dimensions
        val ppWidth = ppImage.width
        val ppHeight = ppImage.height

        for (result: DetectedObject in results) {
            val category = Category(
                result.label,
                result.confidence,
            )
            val yoloBox = result.boundingBox

            // The bounding box coordinates from Ultralytics YOLO are normalized (0-1)
            // need to scale them to the ORIGINAL image dimensions
            // because that's what the OverlayView expects
            
            val left: Float
            val top: Float
            val right: Float
            val bottom: Float
            
            // Check if coordinates are normalized (0-1) or in pixel space
            if (yoloBox.right <= 1.0f && yoloBox.bottom <= 1.0f) {
                // Normalized coordinates (0-1) - scale to original dimensions
                left = yoloBox.left * originalWidth
                top = yoloBox.top * originalHeight
                right = yoloBox.right * originalWidth
                bottom = yoloBox.bottom * originalHeight
            } else {
                // Pixel coordinates relative to preprocessed image
                // Scale from preprocessed dimensions to original dimensions
                val scaleX = originalWidth.toFloat() / ppWidth
                val scaleY = originalHeight.toFloat() / ppHeight
                left = yoloBox.left * scaleX
                top = yoloBox.top * scaleY
                right = yoloBox.right * scaleX
                bottom = yoloBox.bottom * scaleY
            }

            val bbox = RectF(left, top, right, bottom)
            val detection = ObjectDetection(bbox, category)
            detections.add(detection)
        }

        // Return with ORIGINAL bitmap so dimensions match the bounding boxes
        val ret = DetectionResult(bitmap, detections)
        ret.info = yolo.stats.toString()
        return ret

    }


}
