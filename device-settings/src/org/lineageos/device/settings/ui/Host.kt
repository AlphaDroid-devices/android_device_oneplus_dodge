/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.device.settings.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.android.axion.compose.theme.AxionTheme

fun ComponentActivity.setDeviceSettingsContent(start: DeviceSettingsDest) {
    enableEdgeToEdge()
    setContent {
        AxionTheme {
            DeviceSettingsApp(start = start, onFinish = { finish() })
        }
    }
}
