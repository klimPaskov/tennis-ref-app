package com.klim.tennisrefapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.klim.tennisrefapp.ui.courtdetail.CourtDetailRoute
import com.klim.tennisrefapp.ui.courts.CourtsRoute
import com.klim.tennisrefapp.ui.history.HistoryRoute
import com.klim.tennisrefapp.ui.history.MatchLogRoute
import com.klim.tennisrefapp.ui.home.HomeRoute
import com.klim.tennisrefapp.ui.players.PlayerDetailRoute
import com.klim.tennisrefapp.ui.players.PlayersRoute

private const val HOME_ROUTE = "home"
private const val COURTS_ROUTE = "courts"
private const val COURT_DETAIL_ROUTE = "court"
private const val HISTORY_ROUTE = "history"
private const val MATCH_LOG_ROUTE = "matchLog"
private const val PLAYERS_ROUTE = "players"
private const val PLAYER_DETAIL_ROUTE = "player"
private const val ARG_COURT_ID = "courtId"
private const val ARG_MATCH_ID = "matchId"
private const val ARG_PLAYER_ID = "playerId"

@Composable
fun TennisRefNavGraph(
    launchCourtId: Long? = null,
    onLaunchCourtConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    LaunchedEffect(launchCourtId) {
        val courtId = launchCourtId ?: return@LaunchedEffect
        if (courtId <= 0L) return@LaunchedEffect
        navController.navigate("$COURT_DETAIL_ROUTE/$courtId") {
            launchSingleTop = true
        }
        onLaunchCourtConsumed()
    }
    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE,
    ) {
        composable(route = HOME_ROUTE) {
            HomeRoute(
                onContinue = { navController.navigate(COURTS_ROUTE) },
                onPlayers = { navController.navigate(PLAYERS_ROUTE) },
                onHistory = { navController.navigate(HISTORY_ROUTE) },
            )
        }
        composable(route = COURTS_ROUTE) {
            CourtsRoute(
                onCourtClick = { courtId ->
                    navController.navigate("$COURT_DETAIL_ROUTE/$courtId")
                },
                onBack = { navController.popBackStack() },
                onOpenHistory = { navController.navigate(HISTORY_ROUTE) },
            )
        }
        composable(
            route = "$COURT_DETAIL_ROUTE/{$ARG_COURT_ID}",
            arguments = listOf(navArgument(ARG_COURT_ID) { type = NavType.LongType }),
        ) {
            CourtDetailRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable(route = HISTORY_ROUTE) {
            HistoryRoute(
                onBack = { navController.popBackStack() },
                onOpenMatchLog = { matchId ->
                    navController.navigate("$MATCH_LOG_ROUTE/$matchId")
                },
            )
        }
        composable(
            route = "$MATCH_LOG_ROUTE/{$ARG_MATCH_ID}",
            arguments = listOf(navArgument(ARG_MATCH_ID) { type = NavType.LongType }),
        ) {
            MatchLogRoute(onBack = { navController.popBackStack() })
        }
        composable(route = PLAYERS_ROUTE) {
            PlayersRoute(
                onBack = { navController.popBackStack() },
                onPlayerClick = { playerId ->
                    navController.navigate("$PLAYER_DETAIL_ROUTE/$playerId")
                },
            )
        }
        composable(
            route = "$PLAYER_DETAIL_ROUTE/{$ARG_PLAYER_ID}",
            arguments = listOf(navArgument(ARG_PLAYER_ID) { type = NavType.LongType }),
        ) {
            PlayerDetailRoute(onBack = { navController.popBackStack() })
        }
    }
}
