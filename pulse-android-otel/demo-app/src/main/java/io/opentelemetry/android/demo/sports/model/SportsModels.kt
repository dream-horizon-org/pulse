/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.demo.sports.model

enum class MatchStatus { LIVE, UPCOMING, COMPLETED }

enum class VideoBadge { SPECIAL, HIGHLIGHTS }

data class TopPlayer(
    val name: String,
    val role: String,
    val colorHex: Long,
)

data class Match(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: String,
    val awayScore: String,
    val overs: String,
    val status: MatchStatus,
    val tournament: String,
    val matchTitle: String,
    val commentary: String,
    val topPlayers: List<TopPlayer>,
    val sport: String = "Cricket",
)

data class Video(
    val id: String,
    val title: String,
    val category: String,
    val duration: String,
    val badge: VideoBadge,
    val gradientStart: Long,
    val gradientEnd: Long,
)

data class League(
    val id: String,
    val name: String,
    val sport: String,
    val matchCount: Int,
    val startDate: String,
    val endDate: String,
    val primaryColor: Long,
    val videos: List<Video>,
)
