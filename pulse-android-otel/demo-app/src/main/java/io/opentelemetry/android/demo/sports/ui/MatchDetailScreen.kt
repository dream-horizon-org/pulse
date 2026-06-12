/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.demo.sports.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.opentelemetry.android.demo.sports.model.Match
import io.opentelemetry.android.demo.sports.model.MatchStatus
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(
    match: Match,
    onBack: () -> Unit,
) {
    var isPlaying by remember { mutableStateOf(true) }
    var isSubscribed by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showPassDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    if (showLoginDialog) {
        LoginDialog(onDismiss = { showLoginDialog = false }, onLogin = { showLoginDialog = false })
    }
    if (showPassDialog) {
        PassDialog(onDismiss = { showPassDialog = false }, onBuy = { showPassDialog = false })
    }
    if (showSettingsDialog) {
        SettingsDialog(onDismiss = { showSettingsDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                title = {
                    Text(
                        text = match.homeTeam.take(3).uppercase() + " vs " + match.awayTeam.take(3).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                    IconButton(onClick = { isPlaying = !isPlaying }) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF111111)),
            )
        },
        containerColor = SportDarkBg,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            VideoPlayerArea(match = match, isPlaying = isPlaying, onPlayPause = { isPlaying = !isPlaying })

            Text(
                text = if (match.status == MatchStatus.LIVE) "● LIVE  Free Trial: 4:53 remaining" else "Free Preview",
                color = SportOrange,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = match.matchTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text(text = "›", color = SportOrange, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = match.tournament, color = SportTextSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircleIconButton(
                    isActive = isSubscribed,
                    onClick = { isSubscribed = !isSubscribed },
                ) {
                    Icon(
                        imageVector = if (isSubscribed) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = if (isSubscribed) SportOrange else Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                CircleIconButton(isActive = false, onClick = {}) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            ScoreCard(match = match)
            Spacer(modifier = Modifier.height(16.dp))
            CommentaryCard(match = match)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { showLoginDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SportOrange),
                    border = BorderStroke(1.dp, SportOrange),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "LOGIN", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { showPassDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SportOrange),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "GET A PASS", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun VideoPlayerArea(
    match: Match,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
) {
    var isMuted by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0.02f) }

    LaunchedEffect(isPlaying) {
        while (isPlaying && progress < 1f) {
            delay(150)
            progress = (progress + 0.0015f).coerceAtMost(1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0A0A1A), Color(0xFF1A237E), Color(0xFF0A0A1A)),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.35f)
                .fillMaxSize()
                .align(Alignment.CenterStart)
                .background(Brush.linearGradient(colors = listOf(Color(0x661565C0), Color.Transparent))),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.35f)
                .fillMaxSize()
                .align(Alignment.CenterEnd)
                .background(Brush.linearGradient(colors = listOf(Color.Transparent, Color(0x662E7D32)))),
        )

        if (match.status == MatchStatus.LIVE) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Red)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(text = "● LIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .clickable { onPlayPause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = formatProgress(progress), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            IconButton(onClick = { isMuted = !isMuted }, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0xFF333333)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                    .background(SportOrange),
            )
        }
    }
}

private fun formatProgress(progress: Float): String {
    val totalSeconds = (progress * 5400).toInt()
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun CircleIconButton(
    isActive: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.dp, if (isActive) SportOrange else Color(0xFF444444), CircleShape)
            .background(if (isActive) Color(0xFF2A1500) else Color(0xFF1A1A1A))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
private fun ScoreCard(match: Match) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SportCardBg),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TeamScoreRow(
                teamInitial = match.homeTeam.take(2).uppercase(),
                teamName = match.homeTeam,
                overs = match.overs,
                score = match.homeScore,
                circleColor = Color(0xFF1565C0),
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))
            TeamScoreRow(
                teamInitial = match.awayTeam.take(2).uppercase(),
                teamName = match.awayTeam,
                overs = "yet to bat",
                score = match.awayScore,
                circleColor = Color(0xFF2E7D32),
            )
        }
    }
}

@Composable
private fun TeamScoreRow(
    teamInitial: String,
    teamName: String,
    overs: String,
    score: String,
    circleColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(circleColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = teamInitial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Column {
                Text(text = teamName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(text = overs, color = SportTextSecondary, fontSize = 11.sp)
            }
        }
        Text(text = score, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun CommentaryCard(match: Match) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp))
            .background(SportCardBg),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "✦", color = SportOrange, fontSize = 14.sp)
                    Text(text = "Last Updated 2 min ago", color = SportTextSecondary, fontSize = 12.sp)
                }
                Text(text = "ⓘ", color = SportTextSecondary, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "WICKET! Diving catch at deep square leg", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = match.commentary, color = SportTextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2A2A2A))
                Text(text = "TOP PLAYERS", color = SportTextSecondary, fontSize = 11.sp, letterSpacing = 1.sp)
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2A2A2A))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(modifier = Modifier.height(36.dp)) {
                    match.topPlayers.forEachIndexed { index, player ->
                        Box(
                            modifier = Modifier
                                .offset(x = (index * 24).dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(2.dp, SportCardBg, CircleShape)
                                .background(Color(player.colorHex)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = player.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(start = (match.topPlayers.size * 24 + 8).dp)) {
                    match.topPlayers.take(2).forEach { player ->
                        Text(text = player.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(text = "›", color = SportOrange, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LoginDialog(onDismiss: () -> Unit, onLogin: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SportCardBg,
        title = {
            Text(text = "Sign in to Sports Live", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column {
                Text(text = "Watch live matches, highlights and exclusive content.", color = SportTextSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onLogin,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Continue with Google", color = Color.White, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onLogin,
                    border = BorderStroke(1.dp, Color(0xFF444444)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Sign in with Email", fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel", color = SportTextSecondary) }
        },
    )
}

private data class PassOption(val name: String, val price: String, val desc: String)

@Composable
private fun PassDialog(onDismiss: () -> Unit, onBuy: () -> Unit) {
    val options = listOf(
        PassOption("Day Pass", "₹99", "Watch all matches today"),
        PassOption("Monthly Pass", "₹499", "Full month of live sports"),
        PassOption("Annual Pass", "₹2999", "Best value — save 50%"),
    )
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SportCardBg)
                .padding(20.dp),
        ) {
            Column {
                Text(text = "Get a Pass", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Unlock unlimited access to all live matches", color = SportTextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(16.dp))

                options.forEach { option ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF444444), RoundedCornerShape(10.dp))
                            .background(Color(0xFF2A2A2A))
                            .clickable { onBuy() }
                            .padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(text = option.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(text = option.desc, color = SportTextSecondary, fontSize = 12.sp)
                            }
                            Text(text = option.price, color = SportOrange, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onBuy,
                    colors = ButtonDefaults.buttonColors(containerColor = SportOrange),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Continue", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Maybe later", color = SportTextSecondary)
                }
            }
        }
    }
}

private val qualityOptions = listOf("Auto (Recommended)", "1080p HD", "720p HD", "480p SD", "360p SD")

@Composable
private fun SettingsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SportCardBg,
        title = { Text(text = "Playback Quality", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                qualityOptions.forEach { quality ->
                    val isSelected = quality.startsWith("Auto")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF2A1500) else Color(0xFF2A2A2A))
                            .clickable { onDismiss() }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = quality, color = if (isSelected) SportOrange else Color.White, fontSize = 14.sp)
                        if (isSelected) Text(text = "✓", color = SportOrange, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = "Done", color = SportOrange) }
        },
    )
}
