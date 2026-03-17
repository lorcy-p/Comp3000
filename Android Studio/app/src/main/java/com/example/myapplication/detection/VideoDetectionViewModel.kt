package com.example.myapplication.detection

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ProcessingState {
    object Idle : ProcessingState()
    data class Processing(
        val progress: Float = 0f,
        val currentFrame: Int = 0,
        val totalFrames: Int = 0,
        val stage: String = "Initializing..."
    ) : ProcessingState()
    // Processing done, waiting for user to place hoop
    data class AwaitingHoopSelection(val result: DetectionResult) : ProcessingState()
    // Hoop confirmed, ready for playback
    data class Complete(
        val result: DetectionResult,
        val hoopPosition: Offset  // normalised 0..1
    ) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}

class VideoDetectionViewModel(application: Application) : AndroidViewModel(application) {

    private val processor = DetectionProcessor()

    private val _state = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val state: StateFlow<ProcessingState> = _state.asStateFlow()

    fun startProcessing(videoUri: Uri) {
        if (_state.value is ProcessingState.Processing ||
            _state.value is ProcessingState.AwaitingHoopSelection ||
            _state.value is ProcessingState.Complete) return

        _state.value = ProcessingState.Processing()

        viewModelScope.launch {
            try {
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    processor.processVideo(
                        context = getApplication(),
                        videoUri = videoUri
                    ) { progress, frame, total ->
                        _state.value = ProcessingState.Processing(
                            progress = progress,
                            currentFrame = frame,
                            totalFrames = total,
                            stage = "Processing frames..."
                        )
                    }
                } else {
                    throw Exception("Android 9 (API 28) or higher is required")
                }
                // Move to hoop selection instead of directly to Complete
                _state.value = ProcessingState.AwaitingHoopSelection(result)
            } catch (e: Exception) {
                _state.value = ProcessingState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun confirmHoopPosition(normalisedX: Float, normalisedY: Float) {
        val current = _state.value as? ProcessingState.AwaitingHoopSelection ?: return
        _state.value = ProcessingState.Complete(
            result = current.result,
            hoopPosition = Offset(normalisedX, normalisedY)
        )
    }

    fun backToHoopSelection() {
        val current = _state.value as? ProcessingState.Complete ?: return
        _state.value = ProcessingState.AwaitingHoopSelection(current.result)
    }

    override fun onCleared() {
        super.onCleared()
        when (val s = _state.value) {
            is ProcessingState.Complete -> s.result.cacheFile.delete()
            is ProcessingState.AwaitingHoopSelection -> s.result.cacheFile.delete()
            else -> {}
        }
    }
}