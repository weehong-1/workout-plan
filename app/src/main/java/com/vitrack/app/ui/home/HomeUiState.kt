package com.vitrack.app.ui.home

/** Everything the home screen needs to render, in one immutable object. */
data class HomeUiState(
    val isLoading: Boolean = true,
    val greeting: String = "",
)
