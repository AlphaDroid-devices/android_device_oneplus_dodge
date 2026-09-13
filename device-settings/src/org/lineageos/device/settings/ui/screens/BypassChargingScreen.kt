/*
 * SPDX-FileCopyrightText: 2025 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.device.settings.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.axion.compose.preferences.ClickablePreference
import com.android.axion.compose.preferences.CustomSeekBar
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.compose.preferences.SwitchPreference
import org.lineageos.device.settings.Constants
import org.lineageos.device.settings.R
import org.lineageos.device.settings.bypasschrg.BypassChargingController
import org.lineageos.device.settings.bypasschrg.BypassChargingManager
import org.lineageos.device.settings.ui.AppIcon
import org.lineageos.device.settings.ui.OnResume
import org.lineageos.device.settings.ui.PrefIcon
import org.lineageos.device.settings.ui.SettingsScroll
import org.lineageos.device.settings.ui.appLabel
import org.lineageos.device.settings.ui.picker.AppPickerDialog
import org.lineageos.device.settings.ui.picker.ConfirmDeleteDialog
import org.lineageos.device.settings.utils.AppListManager

@Composable
fun BypassChargingScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val controller = remember { BypassChargingController.getInstance(context) }
    val appList = remember {
        AppListManager(context, Constants.KEY_BYPASS_CHARGING_APPS) {
            BypassChargingManager.notifyStateChanged(context, controller.state)
        }.also { it.refreshAppList() }
    }
    val supported = controller.isBypassChargingSupported
    var enabled by remember {
        mutableStateOf(controller.bypassChargingStatus != Constants.BYPASS_OFF)
    }
    var target by remember { mutableIntStateOf(controller.bypassChargingTarget) }
    var packages by remember { mutableStateOf(appList.appList.keys.toList()) }
    var showPicker by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    fun refreshPackages() {
        appList.refreshAppList()
        packages = appList.appList.keys.toList()
    }

    OnResume {
        refreshPackages()
        enabled = controller.bypassChargingStatus != Constants.BYPASS_OFF
        target = controller.bypassChargingTarget
    }

    val listedPackages = packages

    SettingsScroll(contentPadding) {
        PreferenceGroup(title = stringResource(R.string.bypass_charging_category_title)) {
            item {
                SwitchPreference(
                    title = stringResource(R.string.bypass_charging_title),
                    summary = stringResource(
                        if (supported) R.string.bypass_charging_summary
                        else R.string.bypass_charging_unavailable,
                    ),
                    checked = enabled,
                    enabled = supported,
                    customIcon = { PrefIcon(R.drawable.ic_bypass_charging) },
                    onCheckedChange = { enable ->
                        if (enable) controller.enableBypassCharging()
                        else controller.disableBypassCharging()
                        enabled = enable
                    },
                )
            }
            item {
                CustomSeekBar(
                    title = stringResource(R.string.bypass_charging_target_title),
                    value = target,
                    onValueChange = { value ->
                        if (value in Constants.BYPASS_TARGET_MIN..Constants.BYPASS_TARGET_MAX) {
                            controller.bypassChargingTarget = value
                            target = value
                        }
                    },
                    min = Constants.BYPASS_TARGET_MIN,
                    max = Constants.BYPASS_TARGET_MAX,
                    defaultValue = Constants.BYPASS_TARGET_DEFAULT,
                    formatValue = { "$it%" },
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.bypass_charging_app_picker_title)) {
            item {
                ClickablePreference(
                    title = stringResource(R.string.add_bypass_charging_package_title),
                    customIcon = { PrefIcon(R.drawable.ic_add) },
                    onClick = { showPicker = true },
                )
            }
            listedPackages.forEach { pkg ->
                item {
                    ClickablePreference(
                        title = appLabel(context, pkg).ifEmpty { pkg },
                        summary = pkg,
                        customIcon = { AppIcon(pkg) },
                        onClick = { pendingDelete = pkg },
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.bypass_charging_info_title),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier,
        )
    }

    if (showPicker) {
        AppPickerDialog(
            title = stringResource(R.string.add_app),
            excluded = packages.toSet() + context.packageName,
            onPick = { pkg ->
                appList.addApp(pkg)
                refreshPackages()
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
    val toDelete = pendingDelete
    if (toDelete != null) {
        ConfirmDeleteDialog(
            onConfirm = {
                appList.removeApp(toDelete)
                refreshPackages()
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
