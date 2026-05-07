/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.demo.sports.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.opentelemetry.android.demo.sports.model.League
import io.opentelemetry.android.demo.sports.model.Match
import io.opentelemetry.android.demo.sports.model.MatchStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsHomeScreen(
    matches: List<Match>,
    leagues: List<League>,
    onMatchClick: (Match) -> Unit,
    onLeagueClick: (League) -> Unit,
) {
    var selectedSport by remember { mutableStateOf("All") }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var watchlistedLeagues by remember { mutableStateOf(setOf<String>()) }

    val sportFilters = listOf("All", "Cricket", "Football")

    val filteredMatches = matches.filter { match ->
        (selectedSport == "All" || match.sport == selectedSport) &&
            (searchQuery.isEmpty() || match.matchTitle.contains(searchQuery, ignoreCase = true))
    }
    val filteredLeagues = leagues.filter { league ->
        (selectedSport == "All" || league.sport == selectedSport) &&
            (searchQuery.isEmpty() || league.name.contains(searchQuery, ignoreCase = true))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Sports Live", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (showSearch) SportOrange else Color.White,
                        )
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
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search matches, leagues…", color = SportTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SportOrange,
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = SportOrange,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sportFilters.forEach { sport ->
                    FilterChip(
                        selected = selectedSport == sport,
                        onClick = { selectedSport = sport },
                        label = {
                            Text(
                                text = sport,
                                fontSize = 13.sp,
                                fontWeight = if (selectedSport == sport) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        leadingIcon = if (selectedSport == sport) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SportOrange,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = SportCardBg,
                            labelColor = SportTextSecondary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedSport == sport,
                            borderColor = Color(0xFF444444),
                            selectedBorderColor = SportOrange,
                        ),
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                val liveMatches = filteredMatches.filter { it.status == MatchStatus.LIVE }
                if (liveMatches.isNotEmpty()) {
                    LiveNowHeader()
                    Spacer(modifier = Modifier.height(12.dp))
                    liveMatches.forEach { match ->
                        FeaturedMatchCard(match = match, onClick = { onMatchClick(match) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (filteredLeagues.isNotEmpty()) {
                    SectionHeader(title = "LEAGUES")
                    Spacer(modifier = Modifier.height(12.dp))
                    filteredLeagues.forEach { league ->
                        LeagueCard(
                            league = league,
                            isWatchlisted = watchlistedLeagues.contains(league.id),
                            onWatchlistToggle = {
                                watchlistedLeagues = if (watchlistedLeagues.contains(league.id)) {
                                    watchlistedLeagues - league.id
                                } else {
                                    watchlistedLeagues + league.id
                                }
                            },
                            onClick = { onLeagueClick(league) },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val upcomingMatches = filteredMatches.filter { it.status == MatchStatus.UPCOMING }
                if (upcomingMatches.isNotEmpty()) {
                    SectionHeader(title = "UPCOMING")
                    Spacer(modifier = Modifier.height(12.dp))
                    upcomingMatches.forEach { match ->
                        UpcomingMatchCard(match = match, onClick = { onMatchClick(match) })
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                val completedMatches = filteredMatches.filter { it.status == MatchStatus.COMPLETED }
                if (completedMatches.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SectionHeader(title = "COMPLETED")
                    Spacer(modifier = Modifier.height(12.dp))
                    completedMatches.forEach { match ->
                        UpcomingMatchCard(match = match, onClick = { onMatchClick(match) })
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                if (filteredMatches.isEmpty() && filteredLeagues.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "No results found", color = SportTextSecondary, fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LiveNowHeader() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot_alpha",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color.Red.copy(alpha = alpha)),
        )
        Text(text = "LIVE NOW", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.5.sp)
}

@Composable
private fun FeaturedMatchCard(match: Match, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, SportOrange, RoundedCornerShape(12.dp))
            .background(SportCardBg)
            .clickable { onClick() },
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1A237E), Color(0xFF4A148C), Color(0xFF880E4F)),
                        ),
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF1565C0)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = match.homeTeam.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = match.homeTeam, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "VS", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = match.sport, color = SportTextSecondary, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF2E7D32)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = match.awayTeam.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = match.awayTeam, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Red)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(text = "LIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = match.homeScore, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = match.overs, color = SportTextSecondary, fontSize = 12.sp)
                    Text(text = match.tournament.take(20), color = SportTextSecondary, fontSize = 10.sp)
                }
                Text(text = match.awayScore, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun LeagueCard(
    league: League,
    isWatchlisted: Boolean,
    onWatchlistToggle: () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SportCardBg)
            .clickable { onClick() },
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(league.primaryColor), Color(0xFF0D47A1), Color(0xFF01579B)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = league.name,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(text = league.sport, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = league.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "${league.sport} • ${league.matchCount} Matches", color = SportTextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onWatchlistToggle,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isWatchlisted) SportOrange else Color.White,
                        ),
                        border = BorderStroke(1.dp, if (isWatchlisted) SportOrange else Color(0xFF555555)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = if (isWatchlisted) "✓ Watchlist" else "+ Watchlist", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onClick() },
                        colors = ButtonDefaults.buttonColors(containerColor = SportOrange),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "BUY A PASS", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingMatchCard(match: Match, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SportCardBg)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when (match.status) {
                            MatchStatus.LIVE -> Color.Red
                            MatchStatus.UPCOMING -> SportOrange
                            MatchStatus.COMPLETED -> Color(0xFF666666)
                        },
                    ),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = match.matchTitle, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = match.tournament, color = SportTextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2A2A2A))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(text = match.overs, color = SportOrange, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
