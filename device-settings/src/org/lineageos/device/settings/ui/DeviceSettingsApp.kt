/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.device.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.lineageos.device.settings.ui.screens.BypassChargingScreen
import org.lineageos.device.settings.ui.screens.GameBarSettingsScreen
import org.lineageos.device.settings.ui.screens.MainScreen
import org.lineageos.device.settings.ui.screens.RefreshRateScreen

enum class DeviceSettingsDest(val route: String) {
    Main("main"),
    Bypass("bypass"),
    Refresh("refresh"),
    GameBar("gamebar"),
}

@Composable
fun DeviceSettingsApp(
    start: DeviceSettingsDest,
    onFinish: () -> Unit,
) {
    val navController = rememberNavController()
    BackHandler {
        if (!navController.popBackStack()) onFinish()
    }
    DeviceSettingsScaffold(
        onBack = {
            if (!navController.popBackStack()) onFinish()
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = start.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(DeviceSettingsDest.Main.route) {
                MainScreen(
                    contentPadding = padding,
                    onOpenBypass = { navController.navigate(DeviceSettingsDest.Bypass.route) },
                    onOpenRefresh = { navController.navigate(DeviceSettingsDest.Refresh.route) },
                    onOpenGameBar = { navController.navigate(DeviceSettingsDest.GameBar.route) },
                )
            }
            composable(DeviceSettingsDest.Bypass.route) {
                BypassChargingScreen(contentPadding = padding)
            }
            composable(DeviceSettingsDest.Refresh.route) {
                RefreshRateScreen(contentPadding = padding)
            }
            composable(DeviceSettingsDest.GameBar.route) {
                GameBarSettingsScreen(contentPadding = padding)
            }
        }
    }
}
