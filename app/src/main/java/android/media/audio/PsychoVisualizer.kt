package android.media.audio

import android.media.audiofx.Visualizer

class PsychoVisualizer(audioSessionId: Int) {
    private var delegate: Visualizer? = null

    init {
        try {
            delegate = Visualizer(audioSessionId)
        } catch (e: Exception) {
            android.util.Log.e("PsychoVisualizer", "Failed to create underlying Visualizer: ${e.message}")
        }
    }

    var captureSize: Int
        get() = delegate?.captureSize ?: 0
        set(value) {
            try {
                delegate?.captureSize = value
            } catch (e: Exception) {
                android.util.Log.e("PsychoVisualizer", "Error setting capture size: ${e.message}")
            }
        }

    var enabled: Boolean
        get() = delegate?.enabled ?: false
        set(value) {
            try {
                delegate?.enabled = value
            } catch (e: Exception) {
                android.util.Log.e("PsychoVisualizer", "Error setting enabled: ${e.message}")
            }
        }

    fun setDataCaptureListener(
        listener: Visualizer.OnDataCaptureListener,
        rate: Int,
        waveform: Boolean,
        fft: Boolean
    ) {
        try {
            delegate?.setDataCaptureListener(listener, rate, waveform, fft)
        } catch (e: Exception) {
            android.util.Log.e("PsychoVisualizer", "Error setting data capture listener: ${e.message}")
        }
    }

    fun release() {
        try {
            delegate?.release()
        } catch (e: Exception) {
            android.util.Log.e("PsychoVisualizer", "Error releasing Visualizer: ${e.message}")
        }
        delegate = null
    }
}
