package com.vitrack.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.vitrack.app.R
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

/* Type-safe routes. Each tab is a graph so its back stack is kept per tab. */

@Serializable data object ActivityGraph
@Serializable data object ActivityRoute
@Serializable data object StrainRoute

@Serializable data object WeightRoute
@Serializable data object FoodRoute
@Serializable data object ReviewRoute

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Activity(ActivityGraph, ActivityGraph::class, R.string.tab_activity, R.drawable.ic_nav_activity),
    Weight(WeightRoute, WeightRoute::class, R.string.tab_weight, R.drawable.ic_nav_weight),
    Food(FoodRoute, FoodRoute::class, R.string.tab_food, R.drawable.ic_nav_food),
    Review(ReviewRoute, ReviewRoute::class, R.string.tab_review, R.drawable.ic_nav_review),
}
