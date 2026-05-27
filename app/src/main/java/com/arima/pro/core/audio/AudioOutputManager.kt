package com.arima.pro.core.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.media3.exoplayer.ExoPlayer

class AudioOutputManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun routeToDac(player: ExoPlayer?) {
        try {
            val dacDevice = getConnectedDacDevice()
            if (dacDevice != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    player?.setPreferredAudioDevice(dacDevice)
                    android.util.Log.d("AudioOutputManager", "Successfully routed playback to connected DAC: ${dacDevice.productName}")
                }
            } else {
                val speakerDevice = getSpeakerDevice()
                if (speakerDevice != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        player?.setPreferredAudioDevice(speakerDevice)
                        android.util.Log.d("AudioOutputManager", "No DAC found. Routing to default Speaker: ${speakerDevice.productName}")
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        player?.setPreferredAudioDevice(null)
                        android.util.Log.d("AudioOutputManager", "No DAC or Speaker found. Resetting routing to system default.")
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("AudioOutputManager", "Error in routeToDac: ${e.message}")
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
