package com.arima.pro.core.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.media3.exoplayer.ExoPlayer

class AudioOutputManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * AUDIO SIGNAL PATH:
     * Source File/Stream → ExoPlayer → AudioProcessors (resample/dither/normalize) →
     * AudioSink → AudioTrack → DAC Device
     *
     * This routing configuration is applied exactly once per player initialization/recreation
     * to prevent unpredictable multi-step routing issues.
     */
    fun routeToDac(player: ExoPlayer?) {
        if (player == null) return
        val maxRetries = 3
        var success = false
        var exception: Throwable? = null
        var chosenDeviceName = "Unknown"

        for (attempt in 1..maxRetries) {
            try {
                val dacDevice = getConnectedDacDevice()
                if (dacDevice != null) {
                    chosenDeviceName = dacDevice.productName?.toString() ?: "USB DAC"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        player.setPreferredAudioDevice(dacDevice)
                        android.util.Log.d("AudioOutputManager", "Successfully routed playback to connected DAC: $chosenDeviceName (Attempt $attempt)")
                    }
                    success = true
                    break
                } else {
                    val speakerDevice = getSpeakerDevice()
                    if (speakerDevice != null) {
                        chosenDeviceName = speakerDevice.productName?.toString() ?: "Speaker"
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            player.setPreferredAudioDevice(speakerDevice)
                            android.util.Log.d("AudioOutputManager", "No DAC found. Routing to Speaker: $chosenDeviceName (Attempt $attempt)")
                        }
                        success = true
                        break
                    } else {
                        chosenDeviceName = "System Default"
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            player.setPreferredAudioDevice(null)
                            android.util.Log.d("AudioOutputManager", "No DAC or Speaker found. Resetting routing to system default (Attempt $attempt).")
                        }
                        success = true
                        break
                    }
                }
            } catch (e: Throwable) {
                exception = e
                android.util.Log.e("AudioOutputManager", "Routing attempt $attempt failed for device $chosenDeviceName: ${e.message}")
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(100)
                    } catch (ie: InterruptedException) {
                        // ignore
                    }
                }
            }
        }

        if (!success) {
            android.util.Log.e("AudioOutputManager", "FAILED to route playback to device $chosenDeviceName after $maxRetries attempts. Final Error: ${exception?.message}")
        }
    }

    fun verifyRouting(): Boolean {
        return try {
            val connectedDac = getConnectedDacDevice() ?: return false
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any { it.id == connectedDac.id }
        } catch (e: Throwable) {
            false
        }
    }

    fun getConnectedDacDevice(): AudioDeviceInfo? {
        return try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.find { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
        } catch (e: Throwable) {
            null
        }
    }

    fun getSpeakerDevice(): AudioDeviceInfo? {
        return try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        } catch (e: Throwable) {
            null
        }
    }
}
