package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.AudioViewModel
import com.example.ui.components.AppHeader

@Composable
fun FormatBadgeVariantsScreen(viewModel: AudioViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .padding(bottom = 80.dp) // space for bottom navigation
    ) {
        // Unified App Header
        AppHeader(
            title = "Codecs"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = ScreenHorizontalPadding, vertical = 8.dp)
        ) {
            // Elegant Introduction Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BackgroundSurface)
                        .border(1.dp, BorderDefault, RoundedCornerShape(8.dp))
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Format Badge Design",
                        style = HeadlineSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
                        color = AmberGold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Technical format badges for track metadata, designed to stand out against the dark chassis while maintaining a premium analog aesthetic.",
                        style = BodyLarge,
                        color = TextPrimary.copy(alpha = 0.9f)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Grid variants catalog
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BackgroundSurface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "— AUDIO ENCODINGS PLATFORMS",
                        style = LabelCaps.copy(fontWeight = FontWeight.Bold)
                    )

                    FormatCatalogRow("WAV", "Lossless Uncompressed", WavGold)
                    FormatCatalogRow("FLAC", "Lossless Compressed", FlacTeal)
                    FormatCatalogRow("DSD", "Direct Stream Digital", DsdPurple)
                    FormatCatalogRow("MP3", "Lossy Highly Compressed", Mp3Gray)
                    FormatCatalogRow("AIFF", "Audio Interchange Format", AiffBlue)
                }
            }
        }
    }
}

@Composable
fun FormatCatalogRow(format: String, description: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = format, style = BodyLarge.copy(fontWeight = FontWeight.Bold))
            Text(text = description, style = BodyMedium, color = TextSecondary)
        }
        
        Box(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = color.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(3.dp)
                )
                .background(color.copy(alpha = 0.08f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = format,
                style = LabelCaps.copy(
                    fontSize = 11.sp,
                    color = color,
                    fontWeight = FontWeight.Black
                )
            )
        }
    }
}
