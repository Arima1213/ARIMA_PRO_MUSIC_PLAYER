package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DacHotplugDialog(
    dacInfo: com.arima.pro.core.audio.DacInfo,
    onDismiss: () -> Unit,
    onEnableExclusiveMode: () -> Unit,
    onPlayAnyway: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("USB DAC Terdeteksi!", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column {
                Text(
                    dacInfo.name ?: "Unknown DAC",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    dacInfo.chipName ?: "Unknown Chip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Max ${dacInfo.maxSampleRate / 1000}kHz / ${dacInfo.maxBitDepth}-bit",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Mau enable DAC EXCLUSIVE MODE untuk bit-perfect output?",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = onEnableExclusiveMode) {
                Text("Exclusive Mode")
            }
        },
        dismissButton = {
            TextButton(onClick = onPlayAnyway) {
                Text("Nggak dulu")
            }
        }
    )
}
