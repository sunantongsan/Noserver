package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.p2p.AppPreferences
import com.example.ui.screens.FirstLaunchAuthScreen
import com.example.ui.screens.MainScreen

/**
 * Main Navigation Controller.
 * Evaluates AppPreferences onboarding state upon app launch.
 * Routes new users to mandatory Google Play compliant FirstLaunchAuthScreen,
 * and returning users directly to MainScreen P2P Dashboard.
 */
@Composable
fun MainNavigationController(
    viewModel: MeshViewModel,
    appPreferences: AppPreferences,
    modifier: Modifier = Modifier
) {
    var showFirstLaunchOnboarding by remember {
        mutableStateOf(appPreferences.isFirstLaunch || !appPreferences.isAccountCreated)
    }

    if (showFirstLaunchOnboarding) {
        FirstLaunchAuthScreen(
            viewModel = viewModel,
            appPreferences = appPreferences,
            onOnboardingCompleted = {
                showFirstLaunchOnboarding = false
            },
            modifier = modifier
        )
    } else {
        MainScreen(
            viewModel = viewModel
        )
    }
}
