/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.demo.sports.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.opentelemetry.android.demo.sports.model.SportsData

private object SportsRoutes {
    const val HOME = "home"
    const val MATCH = "match/{matchId}"
    const val LEAGUE = "league/{leagueId}"

    fun match(id: String) = "match/$id"
    fun league(id: String) = "league/$id"
}

@Composable
fun SportsNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = SportsRoutes.HOME) {
        composable(SportsRoutes.HOME) {
            SportsHomeScreen(
                matches = SportsData.matches,
                leagues = SportsData.leagues,
                onMatchClick = { nav.navigate(SportsRoutes.match(it.id)) },
                onLeagueClick = { nav.navigate(SportsRoutes.league(it.id)) },
            )
        }
        composable(SportsRoutes.MATCH) { backStackEntry ->
            val match = SportsData.matches.find { it.id == backStackEntry.arguments?.getString("matchId") }
            match?.let { MatchDetailScreen(match = it, onBack = { nav.popBackStack() }) }
        }
        composable(SportsRoutes.LEAGUE) { backStackEntry ->
            val league = SportsData.leagues.find { it.id == backStackEntry.arguments?.getString("leagueId") }
            league?.let { LeagueDetailScreen(league = it, onBack = { nav.popBackStack() }) }
        }
    }
}
