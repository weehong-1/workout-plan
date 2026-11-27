package com.vitrack.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.CompositionLocalProvider
import com.vitrack.app.R
import com.vitrack.app.ui.account.AccountRoute as AccountScreenRoute
import com.vitrack.app.ui.common.LocalOpenAccount
import com.vitrack.app.ui.activity.ActivityRoute as ActivityScreenRoute
import com.vitrack.app.data.share.SharePeriod
import com.vitrack.app.ui.review.ReviewRoute as ReviewScreenRoute
import com.vitrack.app.ui.review.share.TrainerLinkRoute
import com.vitrack.app.ui.strain.StrainRoute as StrainScreenRoute
import com.vitrack.app.ui.food.FoodRoute as FoodScreenRoute
import com.vitrack.app.ui.weight.WeightRoute as WeightScreenRoute

/** App shell: the navigation bar on every screen, and the nav graph. */
@Composable
fun VitrackApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTab(destination) },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val openAccount = { navController.navigate(AccountRoute) { launchSingleTop = true } }
        // Screens draw under the status bar themselves; only the nav bar is padded here.
        CompositionLocalProvider(LocalOpenAccount provides openAccount) {
            NavHost(
                navController = navController,
                startDestination = ActivityGraph,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            ) {
                navigation<ActivityGraph>(startDestination = ActivityRoute) {
                    composable<ActivityRoute> {
                        ActivityScreenRoute(onOpenStrain = { navController.navigate(StrainRoute) })
                    }
                    composable<StrainRoute> {
                        StrainScreenRoute(onBack = { navController.popBackStack() })
                    }
                }
                composable<WeightRoute> {
                    WeightScreenRoute()
                }
                composable<FoodRoute> {
                    FoodScreenRoute()
                }
                navigation<ReviewGraph>(startDestination = ReviewRoute) {
                    composable<ReviewRoute> {
                        ReviewScreenRoute(
                            onShare = { period ->
                                navController.navigate(
                                    when (period) {
                                        is SharePeriod.Month -> ShareRoute(period.month.year, period.month.monthValue)
                                        is SharePeriod.Year -> ShareRoute(period.year, 0)
                                    },
                                )
                            },
                        )
                    }
                    composable<ShareRoute> {
                        TrainerLinkRoute(onClose = { navController.popBackStack() }, onSignIn = openAccount)
                    }
                }
                composable<AccountRoute> {
                    AccountScreenRoute(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

private fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
