package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Song
import com.example.ui.components.glassCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.AudioViewModel

@Composable
fun MiniPlayerPill(
    activeSong: Song,
    isPlaying: Boolean,
    viewModel: AudioViewModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
            .height(64.dp)
            .glassCard(
                shape = RoundedCornerShape(32.dp),
                surfaceColor = BackgroundPrimary.copy(alpha = 0.85f)
            )
            .clickable { viewModel.selectTab("player") }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(BackgroundCard),
                contentAlignment = Alignment.Center
            ) {
                // We'd render actual album art here. For now, placeholder or icon.
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AmberGold)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeSong.title,
                    style = BodyLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = activeSong.artist,
                    style = BodyMedium.copy(fontSize = 12.sp),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Controls
            IconButton(onClick = { if (isPlaying) viewModel.audioEngine.pause() else viewModel.audioEngine.play() }) {
                if (isPlaying) {
                    CustomPauseIcon(color = AmberGold, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = AmberGold, modifier = Modifier.size(24.dp))
                }
            }
            // Skip button placeholder
            IconButton(onClick = { /* Implement next */ }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next", tint = TextPrimary)
            }
        }
    }
}
