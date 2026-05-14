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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.opentelemetry.android.demo.sports.model.League
import io.opentelemetry.android.demo.sports.model.Video
import io.opentelemetry.android.demo.sports.model.VideoBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeagueDetailScreen(
    league: League,
    onBack: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(1) }
    var isWatchlisted by remember { mutableStateOf(false) }
    var showPassDialog by remember { mutableStateOf(false) }
    var selectedVideo by remember { mutableStateOf<Video?>(null) }
    val tabs = listOf("Matches", "Videos", "Standings", "Teams")

    if (showPassDialog) {
        PassDialog(onDismiss = { showPassDialog = false }, onBuy = { showPassDialog = false })
    }
    selectedVideo?.let { video ->
        VideoPlayerDialog(video = video, onDismiss = { selectedVideo = null })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SportDarkBg)
            .verticalScroll(rememberScrollState()),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF0D2137), Color(0xFF1565C0), Color(0xFF0D2137)),
                        ),
                    ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                PlayerSilhouette(height = 140.dp, fontSize = 40, colorHex = 0xFF1A3A6E)
                PlayerSilhouette(height = 200.dp, fontSize = 48, colorHex = 0xFF1E4A8A)
                PlayerSilhouette(height = 160.dp, fontSize = 40, colorHex = 0xFF1A3A6E)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .size(width = 80.dp, height = 48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = league.name.take(7), color = SportOrange, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp)
            }

            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            IconButton(
                onClick = {},
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = league.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(
                onClick = { isWatchlisted = !isWatchlisted },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isWatchlisted) SportOrange else Color.White),
                border = BorderStroke(1.dp, if (isWatchlisted) SportOrange else Color(0xFF555555)),
            ) {
                Text(text = if (isWatchlisted) "✓ Watchlist" else "+ Watchlist", fontSize = 12.sp)
            }
        }

        Text(
            text = "${league.sport} • ${league.matchCount} Matches • ${league.startDate} - ${league.endDate}",
            color = SportTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A2E))
                .border(1.dp, Color(0xFF2A2A3E), RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Watch full tour live", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Unlimited access to all matches", color = SportTextSecondary, fontSize = 12.sp)
                }
                Button(
                    onClick = { showPassDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SportOrange),
                ) {
                    Text(text = "BUY A PASS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PrimaryScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF111111),
            contentColor = Color.White,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedTab, matchContentSize = false),
                    color = SportOrange,
                )
            },
            divider = { HorizontalDivider(color = Color(0xFF222222)) },
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTab == index) SportOrange else SportTextSecondary,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            1 -> VideosTabContent(videos = league.videos, onVideoClick = { selectedVideo = it })
            else -> {
                val label = listOf("Matches", null, "Standings", "Teams")[selectedTab] ?: ""
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "$label coming soon", color = SportTextSecondary, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PlayerSilhouette(height: Dp, fontSize: Int, colorHex: Long) {
    Box(
        modifier = Modifier
            .width(if (height == 200.dp) 80.dp else 70.dp)
            .height(height)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(Brush.verticalGradient(colors = listOf(Color(0x00000000), Color(colorHex)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "👤", fontSize = fontSize.sp)
    }
}

@Composable
private fun VideosTabContent(videos: List<Video>, onVideoClick: (Video) -> Unit) {
    val chaosVideos = videos.filter { it.category == "Chaos Corner" }
    val highlightVideos = videos.filter { it.badge == VideoBadge.HIGHLIGHTS }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (chaosVideos.isNotEmpty()) {
            VideoSectionHeader(title = "Chaos Corner")
            Spacer(modifier = Modifier.height(10.dp))
            TwoColumnVideoGrid(videos = chaosVideos.take(2), onVideoClick = onVideoClick)
            Spacer(modifier = Modifier.height(20.dp))
        }
        if (highlightVideos.isNotEmpty()) {
            VideoSectionHeader(title = "Highlights")
            Spacer(modifier = Modifier.height(10.dp))
            TwoColumnVideoGrid(videos = highlightVideos.take(2), onVideoClick = onVideoClick)
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun VideoSectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(text = "›", color = SportOrange, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TwoColumnVideoGrid(videos: List<Video>, onVideoClick: (Video) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        videos.forEach { video ->
            VideoCard(video = video, onClick = { onVideoClick(video) }, modifier = Modifier.weight(1f))
        }
        if (videos.size == 1) Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun VideoCard(video: Video, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(colors = listOf(Color(video.gradientStart), Color(video.gradientEnd))),
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (video.badge == VideoBadge.SPECIAL) SportOrange else Color.Red)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = if (video.badge == VideoBadge.SPECIAL) "SPECIAL" else "HIGHLIGHTS",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(14.dp))
                Text(text = video.duration, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = video.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun VideoPlayerDialog(video: Video, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SportCardBg)
                .padding(20.dp),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(colors = listOf(Color(video.gradientStart), Color(video.gradientEnd)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (video.badge == VideoBadge.SPECIAL) SportOrange else Color.Red)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(text = if (video.badge == VideoBadge.SPECIAL) "SPECIAL" else "HIGHLIGHTS", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = video.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "${video.category} • ${video.duration}", color = SportTextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SportOrange),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Watch Now", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PassDialog(onDismiss: () -> Unit, onBuy: () -> Unit) {
    val options = listOf(
        Triple("Day Pass", "₹99", "Watch all matches today"),
        Triple("Monthly Pass", "₹499", "Full month of live sports"),
        Triple("Annual Pass", "₹2999", "Best value — save 50%"),
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

                options.forEach { (name, price, desc) ->
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
                                Text(text = name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(text = desc, color = SportTextSecondary, fontSize = 12.sp)
                            }
                            Text(text = price, color = SportOrange, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Maybe later", color = SportTextSecondary)
                }
            }
        }
    }
}
