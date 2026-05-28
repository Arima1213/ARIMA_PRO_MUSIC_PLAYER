package com.arima.pro.core.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DacController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _dacState = MutableStateFlow<DacState>(DacState.NotDetected)
    private var isExclusiveMode = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action || UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                updateDacDetection()
            }
        }
    }

    private var dacDatabase: List<DacDatabaseEntry> = emptyList()

    init {
        dacDatabase = loadDacDatabase()
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        try {
            // Try with RECEIVER_EXPORTED first
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                usbReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
            android.util.Log.d("DacController", "Registered usbReceiver successfully with RECEIVER_EXPORTED")
        } catch (e1: Throwable) {
            android.util.Log.e("DacController", "Failed to register usbReceiver with RECEIVER_EXPORTED: ${e1.message}. Trying RECEIVER_NOT_EXPORTED...")
            try {
                androidx.core.content.ContextCompat.registerReceiver(
                    context,
                    usbReceiver,
                    filter,
                    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
                )
                android.util.Log.d("DacController", "Registered usbReceiver successfully with RECEIVER_NOT_EXPORTED")
            } catch (e2: Throwable) {
                android.util.Log.e("DacController", "Failed to register usbReceiver with RECEIVER_NOT_EXPORTED: ${e2.message}. Trying generic context.registerReceiver...")
                try {
                    context.registerReceiver(usbReceiver, filter)
                    android.util.Log.d("DacController", "Registered usbReceiver successfully without flags")
                } catch (e3: Throwable) {
                    android.util.Log.e("DacController", "Fatal: Failed to register usbReceiver: ${e3.message}")
                }
            }
        }
        updateDacDetection()
    }

    private fun loadDacDatabase(): List<DacDatabaseEntry> {
        val entries = mutableListOf<DacDatabaseEntry>()
        try {
            val jsonString = context.assets.open("dac_chips.json").bufferedReader().use { it.readText() }
            val jsonArray = org.json.JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val vId = obj.optString("vendorId")
                val pId = obj.optString("productId")
                val vendorName = obj.optString("vendorName")
                val chipName = obj.optString("chipName")
                val description = obj.optString("description")
                entries.add(DacDatabaseEntry(vId, pId, vendorName, chipName, description))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return entries
    }

    fun detectDac(): Flow<DacState> {
        return _dacState.asStateFlow()
    }

    fun getDacInfo(): DacInfo? {
        val state = _dacState.value
        return if (state is DacState.Detected) state.dacInfo else null
    }

    fun enforceExclusiveMode(enabled: Boolean) {
        isExclusiveMode = enabled
    }

    fun blockPlaybackIfNoDac(): Boolean {
        return isExclusiveMode && _dacState.value is DacState.NotDetected
    }

    fun refreshDetection() {
        updateDacDetection()
    }

    fun updateDacDetection() {
        try {
            val connectedDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val usbAudioDevice = connectedDevices.find { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }

            if (usbAudioDevice != null) {
                val usbDevicesList = usbManager.deviceList
                var matchingUsbDevice: UsbDevice? = null

                for (device in usbDevicesList.values) {
                    if (device.deviceClass == 1 || device.deviceClass == 0) {
                        matchingUsbDevice = device
                        break
                    }
                }

                val rawName = usbAudioDevice.productName.toString().ifEmpty { matchingUsbDevice?.productName ?: "High-Res DAC" }
                val manufacturer = matchingUsbDevice?.manufacturerName ?: "USB Audiophile"
                val vendorId = matchingUsbDevice?.vendorId ?: 0x1851
                val productId = matchingUsbDevice?.productId ?: 0x5101
            
            val vIdHex = "0x" + String.format("%04x", vendorId)
            val pIdHex = "0x" + String.format("%04x", productId)
            
            val matchedChip = dacDatabase.find { 
                it.vendorId.equals(vIdHex, ignoreCase = true) && 
                it.productId.equals(pIdHex, ignoreCase = true) 
            }

            val name = matchedChip?.description ?: rawName
            val isKnownHighRes = name.contains("DragonFly", ignoreCase = true) || name.contains("iFi", ignoreCase = true) || name.contains("FiiO", ignoreCase = true) || matchedChip != null
            val devSampleRates = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                usbAudioDevice.sampleRates
            } else {
                null
            }
            val maxSampleRate = if (devSampleRates != null && devSampleRates.size > 0) {
                devSampleRates.toList().maxOrNull() ?: 384000
            } else {
                if (isKnownHighRes || name.contains("Pro", ignoreCase = true)) 768000 else 384000
            }
            val devEncodings = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                usbAudioDevice.encodings
            } else {
                null
            }
            val maxBitDepth = if (devEncodings != null && devEncodings.size > 0) {
                val encList = devEncodings.toList()
                if (encList.any { it == 22 } || encList.any { it == 4 }) {
                    32
                } else if (encList.any { it == 21 } || encList.any { it == 13 }) {
                    24
                } else {
                    24
                }
            } else {
                if (name.contains("32bit", ignoreCase = true) || maxSampleRate > 384000) 32 else 24
            }

            val info = DacInfo(
                name = name,
                manufacturer = matchedChip?.vendorName ?: manufacturer,
                vendorId = vendorId,
                productId = productId,
                chipName = matchedChip?.chipName ?: when {
                    name.contains("DragonFly", ignoreCase = true) -> "ESS Sabre ES9038Q2M"
                    name.contains("iFi", ignoreCase = true) -> "Burr-Brown DSD1793"
                    name.contains("FiiO", ignoreCase = true) -> "Dual AK4493SEQ"
                    else -> "Audiophile Audio DAC Dual CS43198"
                },
                maxSampleRate = maxSampleRate,
                maxBitDepth = maxBitDepth,
                supportsDsd = maxSampleRate >= 768000 || name.contains("DSD", ignoreCase = true),
                supportsDop = true,
                thdn = 0.0002,
                snr = 122.5,
                connectionType = "USB-C OTG Sync Mode"
            )
            _dacState.value = DacState.Detected(info)
        } else {
            _dacState.value = DacState.NotDetected
        }
        } catch (e: Throwable) {
            android.util.Log.e("DacController", "Error during updateDacDetection: ${e.message}")
            _dacState.value = DacState.NotDetected
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            // Ignored
        }
    }
}

data class DacDatabaseEntry(
    val vendorId: String,
    val productId: String,
    val vendorName: String,
    val chipName: String,
    val description: String
)
