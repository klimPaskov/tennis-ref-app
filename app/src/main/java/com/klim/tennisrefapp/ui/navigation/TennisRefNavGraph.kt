package com.klim.tennisrefapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.klim.tennisrefapp.ui.courtdetail.CourtDetailRoute
import com.klim.tennisrefapp.ui.courts.CourtsRoute

private const val COURTS_ROUTE = "courts"
private const val COURT_DETAIL_ROUTE = "court"
private const val ARG_COURT_ID = "courtId"

@Composable
fun TennisRefNavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = COURTS_ROUTE,
    ) {
        composable(route = COURTS_ROUTE) {
            CourtsRoute(
                onCourtClick = { courtId ->
                    navController.navigate("$COURT_DETAIL_ROUTE/$courtId")
                },
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
    }
}
