/*
 * Copyright (C) 2025 kenway214
 * Copyright (C) 2025 AlphaDroid
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.device.settings.ui.screens

import android.app.AppOpsManager
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.axion.compose.preferences.ClickablePreference
import com.android.axion.compose.preferences.CustomSeekBar
import com.android.axion.compose.preferences.ListPreference
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.compose.preferences.SwitchPreference
import org.lineageos.device.settings.Constants
import org.lineageos.device.settings.R
import org.lineageos.device.settings.gamebar.GameBar
import org.lineageos.device.settings.gamebar.GameBarMonitorService
import org.lineageos.device.settings.gamebar.GameDataExport
import org.lineageos.device.settings.ui.AppIcon
import org.lineageos.device.settings.ui.OnResume
import org.lineageos.device.settings.ui.PrefIcon
import org.lineageos.device.settings.ui.SettingsScroll
import org.lineageos.device.settings.ui.appLabel
import org.lineageos.device.settings.ui.arrayOptions
import org.lineageos.device.settings.ui.defaultPrefs
import org.lineageos.device.settings.ui.picker.AppPickerDialog
import org.lineageos.device.settings.ui.picker.ConfirmDeleteDialog
import org.lineageos.device.settings.utils.AppListManager

@Composable
fun GameBarSettingsScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val prefs = remember { defaultPrefs(context) }
    val gameBar = remember { GameBar.getInstance(context) }
    val appList = remember {
        AppListManager(context, Constants.KEY_GAMEBAR_AUTO_APPS) {
            GameBarMonitorService.notifyStateChanged(context)
        }.also { it.refreshAppList() }
    }

    var enable by remember { mutableStateOf(prefs.getBoolean("game_bar_enable", false)) }
    var fps by remember { mutableStateOf(prefs.getBoolean("game_bar_fps_enable", false)) }
    var temp by remember { mutableStateOf(prefs.getBoolean("game_bar_temp_enable", false)) }
    var cpuUsage by remember { mutableStateOf(prefs.getBoolean("game_bar_cpu_usage_enable", false)) }
    var cpuClock by remember { mutableStateOf(prefs.getBoolean("game_bar_cpu_clock_enable", false)) }
    var cpuTemp by remember { mutableStateOf(prefs.getBoolean("game_bar_cpu_temp_enable", false)) }
    var ram by remember { mutableStateOf(prefs.getBoolean("game_bar_ram_enable", false)) }
    var gpuUsage by remember { mutableStateOf(prefs.getBoolean("game_bar_gpu_usage_enable", false)) }
    var gpuClock by remember { mutableStateOf(prefs.getBoolean("game_bar_gpu_clock_enable", false)) }
    var gpuTemp by remember { mutableStateOf(prefs.getBoolean("game_bar_gpu_temp_enable", false)) }
    var position by remember {
        mutableStateOf(prefs.getString("game_bar_position", "top_left")!!)
    }
    var splitMode by remember {
        mutableStateOf(prefs.getString("game_bar_split_mode", "stacked")!!)
    }
    var textSize by remember { mutableIntStateOf(prefs.getInt("game_bar_text_size", 16)) }
    var bgAlpha by remember { mutableIntStateOf(prefs.getInt("game_bar_background_alpha", 128)) }
    var corner by remember { mutableIntStateOf(prefs.getInt("game_bar_corner_radius", 16)) }
    var padding by remember { mutableIntStateOf(prefs.getInt("game_bar_padding", 12)) }
    var spacing by remember { mutableIntStateOf(prefs.getInt("game_bar_item_spacing", 8)) }
    var titleColor by remember {
        mutableStateOf(prefs.getString("game_bar_title_color", "#FFFFFF")!!)
    }
    var valueColor by remember {
        mutableStateOf(prefs.getString("game_bar_value_color", "#4CAF50")!!)
    }
    var interval by remember {
        mutableStateOf(prefs.getString("game_bar_update_interval", "1000")!!)
    }
    var singleTap by remember {
        mutableStateOf(prefs.getBoolean("game_bar_single_tap_toggle", false))
    }
    var doubleTap by remember {
        mutableStateOf(prefs.getBoolean("game_bar_doubletap_capture", false))
    }
    var longPress by remember {
        mutableStateOf(prefs.getBoolean("game_bar_longpress_enable", false))
    }
    var longPressTimeout by remember {
        mutableStateOf(prefs.getString("game_bar_longpress_timeout", "1000")!!)
    }
    var packages by remember { mutableStateOf(appList.appList.keys.toList()) }
    var showPicker by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var askedOverlay by rememberSaveable { mutableStateOf(false) }
    val listedPackages = packages

    fun persistBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun persistString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun persistInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun hasUsageStats(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    OnResume {
        appList.refreshAppList()
        packages = appList.appList.keys.toList()
        splitMode = prefs.getString("game_bar_split_mode", "stacked")!!
        if (!hasUsageStats()) {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        if (!askedOverlay && !Settings.canDrawOverlays(context)) {
            askedOverlay = true
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}"),
                ),
            )
        }
    }

    SettingsScroll(contentPadding) {
        PreferenceGroup {
            item {
                SwitchPreference(
                    title = stringResource(R.string.game_bar_enable),
                    summary = stringResource(R.string.game_bar_enable_summary),
                    checked = enable,
                    onCheckedChange = { on ->
                        if (on) {
                            if (!Settings.canDrawOverlays(context)) {
                                Toast.makeText(
                                    context,
                                    R.string.overlay_permission_denied,
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return@SwitchPreference
                            }
                            persistBool("game_bar_enable", true)
                            gameBar.applyPreferences()
                            gameBar.show()
                            enable = true
                        } else {
                            persistBool("game_bar_enable", false)
                            gameBar.hide()
                            enable = false
                        }
                    },
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.game_bar_applications)) {
            item {
                ClickablePreference(
                    title = stringResource(R.string.add_app),
                    summary = stringResource(R.string.game_bar_add_packages_summary),
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

        PreferenceGroup(title = stringResource(R.string.game_bar_stats_display)) {
            item {
                SwitchPreference(
                    title = stringResource(R.string.game_bar_fps_enable),
                    summary = stringResource(R.string.game_bar_fps_enable_summary),
                    checked = fps,
                    onCheckedChange = {
                        fps = it
                        persistBool("game_bar_fps_enable", it)
                        gameBar.setShowFps(it)
                    },
                )
            }
            item {
                SwitchPreference(
                    title = stringResource(R.string.game_bar_temp_enable),
                    summary = stringResource(R.string.game_bar_temp_enable_summary),
                    checked = temp,
                    onCheckedChange = {
                        temp = it
                        persistBool("game_bar_temp_enable", it)
                        gameBar.setShowBatteryTemp(it)
                    },
                )
            }
            item {
                SwitchPreference(
                    title = stringResource(R.string.game_bar_cpu_usage_enable),
                    summary = stringResource(R.string.game_bar_cpu_usage_enable_summary),
                    checked = cpuUsage,
                    onCheckedChange = {
                        cpuUsage = it
                        persistBool("game_bar_cpu_usage_enable", it)
                        gameBar.setShowCpuUsage(it)
                    },
                )
            }
            item {
                SwitchPreference(
                    title = stringResource(R.string.game_bar_cpu_clock_enable),
                    summary = stringResource(R.string.game_bar_cpu_clock_enable_summary),
                    checked = cpuClock,
                    onCheckedChange = {
                        cpuClock = it
                        persistBool("game_bar_cpu_clock_enable", it)
                        gameBar.setShowCpuClock(it)
                    },
                )
            }
            item {
                SwitchPreference(
                    title = stringResource(R.string.game_bar_cpu_temp_enable),
                    summary = stringResource(R.string.game_bar_cpu_temp_enable_summary),
                    checked = cpuTemp,
                    onCheckedChange = {
                        cpuTemp = it
                        persistBool("game_bar_cpu_temp_enable", it)
                        gameBar.setShowCpuTemp(it)
                    },
                )
            }
            item {
                SwitchPreference(
                    title = stringResource(R.string.game_bar_ram_enable),
                    summary = stringResource(R.string.game_bar_ram_enable_summary),
                    checked = ram,
                    onCheckedChange = {
                        ram = it
                        persistBool("game_bar_ram_enable", it)
                        gameBar.setShowRam(it)
                    },
                )
            }
            item {
                SwitchPreference(
                    title = stringResource(R.string.game_bar_gpu_usage_enable),
                    summary = stringResource(R.string.game_bar_gpu_usage_enable_summary),
                    checked = gpuUsage,
                    onCheckedChange = {
                        gpuUsage = it
                        persistBool("game_bar_gpu_usage_enable", it)
                        gameBar.setShowGpuUsage(it)
                    },
                )
            }
            item {
                SwitchPreference(
                    title = stringResource(R.string.game_bar_gpu_clock_enable),
                    summary = stringResource(R.string.game_bar_gpu_clock_enable_summary),
                    checked = gpuClock,
                    onCheckedChange = {
                        gpuClock = it
                        persistBool("game_bar_gpu_clock_enable", it)
                        gameBar.setShowGpuClock(it)
                    },
                )
            }
            item {
                SwitchPreference(
                    title = stringResource(R.string.game_bar_gpu_temp_enable),
                    summary = stringResource(R.string.game_bar_gpu_temp_enable_summary),
                    checked = gpuTemp,
                    onCheckedChange = {
                        gpuTemp = it
                        persistBool("game_bar_gpu_temp_enable", it)
                        gameBar.setShowGpuTemp(it)
                    },
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.game_bar_ui_customization)) {
            item {
                ListPreference(
                    title = stringResource(R.string.game_bar_position),
                    options = arrayOptions(
                        context,
                        R.array.game_bar_position_entries,
                        R.array.game_bar_position_values,
                    ),
                    value = position,
                    onValueChange = {
                        position = it
                        persistString("game_bar_position", it)
                        gameBar.updatePosition(it)
                    },
                )
            }
            item {
                ClickablePreference(
                    title = stringResource(R.string.game_bar_reset_position),
                    summary = stringResource(R.string.game_bar_reset_position_summary),
                    onClick = {
                        prefs.edit()
                            .remove("game_bar_dragged_x")
                            .remove("game_bar_dragged_y")
                            .apply()
                        position = "top_left"
                        persistString("game_bar_position", "top_left")
                        gameBar.updatePosition("top_left")
                    },
                )
            }
            item {
                ListPreference(
                    title = stringResource(R.string.game_bar_split_mode),
                    options = arrayOptions(
                        context,
                        R.array.game_bar_split_mode_entries,
                        R.array.game_bar_split_mode_values,
                    ),
                    value = splitMode,
                    onValueChange = {
                        splitMode = it
                        persistString("game_bar_split_mode", it)
                        gameBar.updateSplitMode(it)
                    },
                )
            }
            item {
                CustomSeekBar(
                    title = stringResource(R.string.game_bar_text_size),
                    value = textSize,
                    onValueChange = {
                        textSize = it
                        persistInt("game_bar_text_size", it)
                        gameBar.updateTextSize(it)
                    },
                    min = 10,
                    max = 24,
                    defaultValue = 16,
                )
            }
            item {
                CustomSeekBar(
                    title = stringResource(R.string.game_bar_background_alpha),
                    value = bgAlpha,
                    onValueChange = {
                        bgAlpha = it
                        persistInt("game_bar_background_alpha", it)
                        gameBar.updateBackgroundAlpha(it)
                    },
                    min = 0,
                    max = 255,
                    defaultValue = 128,
                )
            }
            item {
                CustomSeekBar(
                    title = stringResource(R.string.game_bar_corner_radius),
                    value = corner,
                    onValueChange = {
                        corner = it
                        persistInt("game_bar_corner_radius", it)
                        gameBar.updateCornerRadius(it)
                    },
                    min = 0,
                    max = 32,
                    defaultValue = 16,
                )
            }
            item {
                CustomSeekBar(
                    title = stringResource(R.string.game_bar_padding),
                    value = padding,
                    onValueChange = {
                        padding = it
                        persistInt("game_bar_padding", it)
                        gameBar.updatePadding(it)
                    },
                    min = 4,
                    max = 24,
                    defaultValue = 12,
                )
            }
            item {
                CustomSeekBar(
                    title = stringResource(R.string.game_bar_item_spacing),
                    value = spacing,
                    onValueChange = {
                        spacing = it
                        persistInt("game_bar_item_spacing", it)
                        gameBar.updateItemSpacing(it)
                    },
                    min = 0,
                    max = 16,
                    defaultValue = 8,
                )
            }
            item {
                ListPreference(
                    title = stringResource(R.string.game_bar_title_color),
                    options = arrayOptions(
                        context,
                        R.array.game_bar_color_entries,
                        R.array.game_bar_color_values,
                    ),
                    value = titleColor,
                    onValueChange = {
                        if (gameBar.isValidHexColor(it)) {
                            titleColor = it
                            persistString("game_bar_title_color", it)
                            gameBar.updateTitleColor(it)
                        }
                    },
                )
            }
            item {
                ListPreference(
                    title = stringResource(R.string.game_bar_value_color),
                    options = arrayOptions(
                        context,
                        R.array.game_bar_color_entries,
                        R.array.game_bar_color_values,
                    ),
                    value = valueColor,
                    onValueChange = {
                        if (gameBar.isValidHexColor(it)) {
                            valueColor = it
                            persistString("game_bar_value_color", it)
                            gameBar.updateValueColor(it)
                        }
                    },
                )
            }
            item {
                ListPreference(
                    title = stringResource(R.string.game_bar_update_interval),
                    options = arrayOptions(
                        context,
                        R.array.game_bar_update_interval_entries,
                        R.array.game_bar_update_interval_values,
                    ),
                    value = interval,
                    onValueChange = {
                        interval = it
                        persistString("game_bar_update_interval", it)
                        gameBar.updateUpdateInterval(it)
                    },
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.game_bar_gestures)) {
            item {
                SwitchPreference(
                    title = stringResource(R.string.game_bar_single_tap_toggle),
                    summary = stringResource(R.string.game_bar_single_tap_toggle_summary),
                    checked = singleTap,
                    onCheckedChange = {
                        singleTap = it
                        persistBool("game_bar_single_tap_toggle", it)
                        gameBar.setSingleTapToggleEnabled(it)
                    },
                )
            }
            item {
                SwitchPreference(
                    title = stringResource(R.string.game_bar_doubletap_capture),
                    summary = stringResource(R.string.game_bar_doubletap_capture_summary),
                    checked = doubleTap,
                    onCheckedChange = {
                        doubleTap = it
                        persistBool("game_bar_doubletap_capture", it)
                        gameBar.setDoubleTapCaptureEnabled(it)
                    },
                )
            }
            item {
                SwitchPreference(
                    title = stringResource(R.string.game_bar_longpress_enable),
                    summary = stringResource(R.string.game_bar_longpress_enable_summary),
                    checked = longPress,
                    onCheckedChange = {
                        longPress = it
                        persistBool("game_bar_longpress_enable", it)
                        gameBar.setLongPressEnabled(it)
                    },
                )
            }
            item {
                ListPreference(
                    title = stringResource(R.string.game_bar_longpress_timeout),
                    options = arrayOptions(
                        context,
                        R.array.game_bar_longpress_timeout_entries,
                        R.array.game_bar_longpress_timeout_values,
                    ),
                    value = longPressTimeout,
                    enabled = longPress,
                    onValueChange = {
                        longPressTimeout = it
                        persistString("game_bar_longpress_timeout", it)
                        gameBar.setLongPressThresholdMs(it.toLongOrNull() ?: 1000L)
                    },
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.game_bar_data_capture)) {
            item {
                ClickablePreference(
                    title = stringResource(R.string.game_bar_capture_start),
                    summary = stringResource(R.string.game_bar_capture_start_summary),
                    onClick = { GameDataExport.getInstance().startCapture() },
                )
            }
            item {
                ClickablePreference(
                    title = stringResource(R.string.game_bar_capture_stop),
                    summary = stringResource(R.string.game_bar_capture_stop_summary),
                    onClick = { GameDataExport.getInstance().stopCapture() },
                )
            }
            item {
                ClickablePreference(
                    title = stringResource(R.string.game_bar_capture_export),
                    summary = stringResource(R.string.game_bar_capture_export_summary),
                    onClick = { GameDataExport.getInstance().exportDataToCsv() },
                )
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            title = stringResource(R.string.add_app),
            excluded = packages.toSet() + context.packageName,
            onPick = { pkg ->
                appList.addApp(pkg)
                appList.refreshAppList()
                packages = appList.appList.keys.toList()
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
                appList.refreshAppList()
                packages = appList.appList.keys.toList()
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
