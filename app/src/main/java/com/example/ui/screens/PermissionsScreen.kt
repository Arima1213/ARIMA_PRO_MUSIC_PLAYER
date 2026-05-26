package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.*

@Composable
fun PermissionsScreen(
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    
    // Manage runtime state of permissions
    var permissionsGranted by remember { 
        mutableStateOf(hasAllRequiredPermissions(context)) 
    }
    
    var showRationaleDialog by remember { mutableStateOf(false) }
    var showDeniedDialog by remember { mutableStateOf(false) }

    val requiredList = remember { getRequiredPermissionsList() }

    // Multi-permission Launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        val allGranted = resultMap.filterKeys { it in requiredList }.values.all { it }
        if (allGranted) {
            permissionsGranted = true
            onPermissionsGranted()
        } else {
            // Check if user permanently denied
            showDeniedDialog = true
        }
    }

    // Function to handle permission button click
    val requestAllPermissions = {
        // First display rationale dialog with user friendly explanation
        showRationaleDialog = true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BackgroundPrimary
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Logo Setup
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(AmberMuted.copy(alpha = 0.2f))
                    .border(2.dp, AmberGold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = AmberGold,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Display title
            Text(
                text = "ARIMA PRO",
                style = HeadlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    letterSpacing = 2.sp
                ),
                color = AmberGold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AUDIOPHILE SETUP ENGINE",
                style = TechnicalSmall.copy(letterSpacing = 3.sp, color = TextSecondary),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Info items container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDefault, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = BackgroundSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "SYSTEM CONFIGURATIONS DEMAND",
                            style = TechnicalLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AmberGold),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    item {
                        PermissionExplanatoryRow(
                            title = "High-Res Storage Access",
                            description = "Required to scan, cache, and play high-resolution file formats (FLAC, WAV, DSD) from storage.",
                            isGranted = isPermissionGranted(context, getStoragePermissionName())
                        )
                    }

                    if (Build.VERSION.SDK_INT >= 33) {
                        item {
                            PermissionExplanatoryRow(
                                title = "Background Notifications",
                                description = "Shows live playback notification widget in the background drawers.",
                                isGranted = isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            if (!permissionsGranted) {
                Button(
                    onClick = { requestAllPermissions() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberGold,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("grant_permissions_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INITIALIZE PERMISSIONS",
                        style = TechnicalLarge.copy(fontWeight = FontWeight.Bold, color = Color.Black)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(FlacTeal.copy(alpha = 0.15f))
                        .border(1.dp, FlacTeal, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = FlacTeal,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "PERMISSIONS READY",
                            style = TechnicalLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FlacTeal)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Core engine environment has been fully configured.",
                            style = BodyMedium,
                            color = TextPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onPermissionsGranted() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FlacTeal,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("onboarding_continue_button")
                ) {
                    Text(
                        text = "CONTINUE TO PLAYER",
                        style = TechnicalLarge.copy(fontWeight = FontWeight.Bold, color = Color.Black)
                    )
                }
            }
        }

        // Rationale Dialog BEFORE launching actual prompt
        if (showRationaleDialog) {
            AlertDialog(
                onDismissRequest = { showRationaleDialog = false },
                containerColor = BackgroundSurface,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = AmberGold)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Environment Setup",
                            style = HeadlineSmall,
                            color = TextPrimary
                        )
                    }
                },
                text = {
                    Text(
                        text = "ARIMA Pro requires access to local files to fetch audio tracks. Bluetooth permissions are needed to support direct output to external devices.\n\nPress setup to trigger Android system permissions prompt.",
                        style = BodyLarge,
                        color = TextPrimary.copy(alpha = 0.9f)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRationaleDialog = false
                            launcher.launch(requiredList.toTypedArray())
                        }
                    ) {
                        Text(
                            text = "PROMPT SETUP",
                            style = TechnicalLarge.copy(fontWeight = FontWeight.Bold),
                            color = AmberGold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRationaleDialog = false }) {
                        Text(
                            text = "CANCEL",
                            style = TechnicalLarge,
                            color = TextSecondary
                        )
                    }
                },
                modifier = Modifier.border(1.dp, BorderDefault, RoundedCornerShape(28.dp))
            )
        }

        // Denied dialog instructions
        if (showDeniedDialog) {
            AlertDialog(
                onDismissRequest = { showDeniedDialog = false },
                containerColor = BackgroundSurface,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = VUPeak)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Configuration Denied",
                            style = HeadlineSmall,
                            color = TextPrimary
                        )
                    }
                },
                text = {
                    Text(
                        text = "Required permissions were denied. ARIMA Pro cannot scan storage or display background widgets without them.\n\nPlease navigate to system app settings and grant permissions manually.",
                        style = BodyLarge,
                        color = TextPrimary.copy(alpha = 0.9f)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeniedDialog = false
                            // Open app settings activity
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text(
                            text = "OPEN APP SETTINGS",
                            style = TechnicalLarge.copy(fontWeight = FontWeight.Bold),
                            color = AmberGold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeniedDialog = false }) {
                        Text(
                            text = "BACK",
                            style = TechnicalLarge,
                            color = TextSecondary
                        )
                    }
                },
                modifier = Modifier.border(1.dp, BorderDefault, RoundedCornerShape(28.dp))
            )
        }
    }
}

@Composable
fun PermissionExplanatoryRow(
    title: String,
    description: String,
    isGranted: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isGranted) FlacTeal else AmberMuted)
        )
        
        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.uppercase(),
                style = TechnicalLarge.copy(
                    fontSize = 13.sp, 
                    fontWeight = FontWeight.Bold,
                    color = if (isGranted) FlacTeal else TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = BodyMedium,
                color = TextSecondary
            )
        }
    }
}

fun getRequiredPermissionsList(): List<String> {
    val list = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= 33) {
        list.add(Manifest.permission.READ_MEDIA_AUDIO)
        list.add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return list
}

fun getStoragePermissionName(): String {
    return if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

fun isPermissionGranted(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

fun hasAllRequiredPermissions(context: Context): Boolean {
    return getRequiredPermissionsList().all {
        isPermissionGranted(context, it)
    }
}
