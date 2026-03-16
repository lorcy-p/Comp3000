package com.example.myapplication.detection

import java.io.File

data class DetectionResult(
    val frames: List<FrameDetection>,
    val totalFrames: Int,
    val totalDetections: Int,
    val processingTimeMs: Long,
    val cacheFile: File
)