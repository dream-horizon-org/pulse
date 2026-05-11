/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.demo.sports.model

object SportsData {

    private val topPlayers1 = listOf(
        TopPlayer("Rahmanullah Gurbaz", "WK-BAT", 0xFFFF6B00),
        TopPlayer("Hashmatullah Shahidi", "BAT", 0xFF4FC3F7),
        TopPlayer("Mujeeb Ur Rahman", "BOWL", 0xFF81C784),
    )

    private val topPlayers2 = listOf(
        TopPlayer("Naveen-ul-Haq", "BOWL", 0xFFFF8A65),
        TopPlayer("Azmatullah Omarzai", "ALL", 0xFFCE93D8),
        TopPlayer("Ibrahim Zadran", "BAT", 0xFF4DB6AC),
    )

    private val topPlayersFootball = listOf(
        TopPlayer("Vinicius Jr", "FWD", 0xFFFFD600),
        TopPlayer("Pedri", "MID", 0xFF00B0FF),
        TopPlayer("Marc-André ter Stegen", "GK", 0xFFAA00FF),
    )

    val matches = listOf(
        Match(
            id = "m1",
            homeTeam = "Band-e-Amir",
            awayTeam = "Boost Region",
            homeScore = "142/6",
            awayScore = "138/8",
            overs = "20.0 ov",
            status = MatchStatus.LIVE,
            tournament = "Afghanistan Premier League T20 • Match 12",
            matchTitle = "Band-e-Amir Region vs Boost Region",
            commentary = "Rashid Khan to Boost Region's tail-ender — short delivery, mistimed pull, diving catch at deep square leg! Band-e-Amir closing in on a thriller win.",
            topPlayers = topPlayers1,
            sport = "Cricket",
        ),
        Match(
            id = "m3",
            homeTeam = "Real Madrid",
            awayTeam = "FC Barcelona",
            homeScore = "2",
            awayScore = "1",
            overs = "67'",
            status = MatchStatus.LIVE,
            tournament = "LaLiga 2025-26 • Matchday 8",
            matchTitle = "El Clásico",
            commentary = "Vinicius Jr cuts inside from the left, nutmegs Iñigo Martínez and fires low into the bottom corner — 2-1 Real Madrid! The Bernabéu erupts.",
            topPlayers = topPlayersFootball,
            sport = "Football",
        ),
        Match(
            id = "m2",
            homeTeam = "Mis Ainak",
            awayTeam = "Speenghar",
            homeScore = "-",
            awayScore = "-",
            overs = "Starts 18:30",
            status = MatchStatus.UPCOMING,
            tournament = "Afghanistan Premier League T20 • Match 13",
            matchTitle = "Mis Ainak Knights vs Speenghar Tigers",
            commentary = "Pre-match: Both sides announced their playing XI. Toss at 18:00.",
            topPlayers = topPlayers2,
            sport = "Cricket",
        ),
        Match(
            id = "m4",
            homeTeam = "Atletico Madrid",
            awayTeam = "Sevilla FC",
            homeScore = "-",
            awayScore = "-",
            overs = "Tomorrow 21:00",
            status = MatchStatus.UPCOMING,
            tournament = "LaLiga 2025-26 • Matchday 8",
            matchTitle = "Atletico Madrid vs Sevilla FC",
            commentary = "Key clash at the Metropolitano — Atletico look to stay in the top 3.",
            topPlayers = topPlayersFootball,
            sport = "Football",
        ),
        Match(
            id = "m5",
            homeTeam = "Kandahar",
            awayTeam = "Balkh",
            homeScore = "178/4",
            awayScore = "156/9",
            overs = "Completed",
            status = MatchStatus.COMPLETED,
            tournament = "Afghanistan Premier League T20 • Match 11",
            matchTitle = "Kandahar Knights vs Balkh Legends",
            commentary = "Kandahar won by 22 runs. Stunning innings from the captain.",
            topPlayers = topPlayers1,
            sport = "Cricket",
        ),
    )

    private val laligaVideos = listOf(
        Video(id = "v1", title = "El Clásico: Last 10 Minutes Chaos", category = "Chaos Corner", duration = "8:24", badge = VideoBadge.SPECIAL, gradientStart = 0xFF1A237E, gradientEnd = 0xFF7B1FA2),
        Video(id = "v2", title = "Week 3 Top Tackles & Saves", category = "Chaos Corner", duration = "5:11", badge = VideoBadge.SPECIAL, gradientStart = 0xFF880E4F, gradientEnd = 0xFF4A148C),
        Video(id = "v3", title = "Matchday 4 Highlights Reel", category = "Highlights", duration = "12:05", badge = VideoBadge.HIGHLIGHTS, gradientStart = 0xFF1B5E20, gradientEnd = 0xFF006064),
        Video(id = "v4", title = "Benzema Hat-trick vs Valencia", category = "Highlights", duration = "9:47", badge = VideoBadge.HIGHLIGHTS, gradientStart = 0xFF33691E, gradientEnd = 0xFF00695C),
        Video(id = "v5", title = "Vinicius Jr Best Dribbles", category = "Chaos Corner", duration = "6:33", badge = VideoBadge.SPECIAL, gradientStart = 0xFF3E2723, gradientEnd = 0xFF4E342E),
        Video(id = "v6", title = "Week 2 Full Highlights", category = "Highlights", duration = "14:22", badge = VideoBadge.HIGHLIGHTS, gradientStart = 0xFF0D47A1, gradientEnd = 0xFF01579B),
    )

    val leagues = listOf(
        League(id = "l1", name = "LaLiga 2025-26", sport = "Football", matchCount = 380, startDate = "15 Aug", endDate = "24 May, 2026", primaryColor = 0xFF1565C0, videos = laligaVideos),
        League(id = "l2", name = "APL T20 2025", sport = "Cricket", matchCount = 24, startDate = "1 Nov", endDate = "20 Nov, 2025", primaryColor = 0xFF2E7D32, videos = laligaVideos.take(4)),
    )
}
