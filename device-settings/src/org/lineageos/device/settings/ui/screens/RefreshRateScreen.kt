/*
 * SPDX-FileCopyrightText: 2025 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.device.settings.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.axion.compose.preferences.ClickablePreference
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.compose.preferences.SwitchPreference
import org.lineageos.device.settings.Constants
import org.lineageos.device.settings.R
import org.lineageos.device.settings.display.DisplayModeController
import org.lineageos.device.settings.refreshrate.RefreshRateController
import org.lineageos.device.settings.refreshrate.RefreshRateMonitorService
import org.lineageos.device.settings.ui.AppIcon
import org.lineageos.device.settings.ui.IconListPreference
import org.lineageos.device.settings.ui.OnResume
import org.lineageos.device.settings.ui.PrefIcon
import org.lineageos.device.settings.ui.SettingsScroll
import org.lineageos.device.settings.ui.appLabel
import org.lineageos.device.settings.ui.arrayOptions
import org.lineageos.device.settings.ui.picker.AppPickerDialog
import org.lineageos.device.settings.ui.picker.ConfirmDeleteDialog
import org.lineageos.device.settings.ui.picker.RadioChoiceDialog

private const val TAG = "RefreshRateScreen"

internal fun refreshRateLabel(context: Context, fps: Int): String = when (fps) {
    0 -> context.getString(R.string.refresh_rate_auto)
    60 -> context.getString(R.string.refresh_rate_60hz)
    90 -> context.getString(R.string.refresh_rate_90hz)
    120 -> context.getString(R.string.refresh_rate_120hz)
    else -> ""
}

@Composable
fun RefreshRateScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val controller = remember { RefreshRateController.getInstance(context) }
    val display = remember { DisplayModeController.getInstance(context) }
    var global by remember { mutableStateOf(controller.globalRefreshRate.toString()) }
    var ltpo by remember { mutableStateOf(controller.isLtpoEnabled) }
    var overrides by remember { mutableStateOf(controller.appOverridesMap.toList()) }
    var hbmLock by remember { mutableStateOf(display.isHbmEnabled) }
    var showPicker by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var editingPkg by remember { mutableStateOf<String?>(null) }
    val options = arrayOptions(context, R.array.refresh_rate_entries, R.array.refresh_rate_values)

    fun refresh() {
        global = controller.globalRefreshRate.toString()
        ltpo = controller.isLtpoEnabled
        overrides = controller.appOverridesMap.toList()
        hbmLock = display.isHbmEnabled
    }

    OnResume {
        refresh()
        RefreshRateMonitorService.notifyStateChanged(context)
    }

    val listedOverrides = overrides

    SettingsScroll(contentPadding) {
        PreferenceGroup(title = stringResource(R.string.refresh_rate_settings)) {
            item {
                if (hbmLock) {
                    ClickablePreference(
                        title = stringResource(R.string.refresh_rate_global_title),
                        summary = stringResource(R.string.refresh_rate_locked_hbm),
                        enabled = false,
                        customIcon = { PrefIcon(R.drawable.ic_refresh_rate) },
                        onClick = {},
                    )
                } else {
                    IconListPreference(
                        title = stringResource(R.string.refresh_rate_global_title),
                        options = options,
                        value = global,
                        onValueChange = { value ->
                            val fps = value.toIntOrNull() ?: return@IconListPreference
                            controller.globalRefreshRate = fps
                            global = value
                        },
                        icon = R.drawable.ic_refresh_rate,
                    )
                }
            }
            item {
                SwitchPreference(
                    title = stringResource(R.string.ltpo_title),
                    summary = stringResource(R.string.ltpo_summary),
                    checked = ltpo,
                    customIcon = { PrefIcon(R.drawable.ic_ltpo) },
                    onCheckedChange = { enable ->
                        controller.setLtpoEnabled(enable)
                        ltpo = enable
                    },
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.refresh_rate_overrides_title)) {
            item {
                ClickablePreference(
                    title = stringResource(R.string.add_app),
                    summary = if (listedOverrides.isEmpty()) {
                        stringResource(R.string.no_app_overrides)
                    } else {
                        null
                    },
                    customIcon = { PrefIcon(R.drawable.ic_add) },
                    onClick = { showPicker = true },
                )
            }
            listedOverrides.forEach { (pkg, fps) ->
                item {
                    ClickablePreference(
                        title = appLabel(context, pkg).ifEmpty { pkg },
                        summary = refreshRateLabel(context, fps),
                        customIcon = { AppIcon(pkg) },
                        onClick = { editingPkg = pkg },
                        onLongClick = { pendingDelete = pkg },
                    )
                }
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            title = stringResource(R.string.add_app),
            excluded = overrides.map { it.first }.toSet() + context.packageName,
            onPick = { pkg ->
                val fps = controller.globalRefreshRate
                controller.setAppRefreshRate(pkg, fps)
                if (Constants.DEBUG) Log.i(TAG, "Added app: $pkg with fps: $fps")
                refresh()
                RefreshRateMonitorService.notifyStateChanged(context)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
    val toDelete = pendingDelete
    if (toDelete != null) {
        ConfirmDeleteDialog(
            onConfirm = {
                controller.removeAppRefreshRate(toDelete)
                refresh()
                RefreshRateMonitorService.notifyStateChanged(context)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
    val editing = editingPkg
    if (editing != null) {
        val current = overrides.firstOrNull { it.first == editing }?.second ?: 0
        RadioChoiceDialog(
            title = appLabel(context, editing).ifEmpty { editing },
            options = options,
            selectedKey = current.toString(),
            onSelect = { value ->
                val fps = value.toIntOrNull() ?: return@RadioChoiceDialog
                controller.setAppRefreshRate(editing, fps)
                refresh()
                editingPkg = null
            },
            onDismiss = { editingPkg = null },
        )
    }
}
