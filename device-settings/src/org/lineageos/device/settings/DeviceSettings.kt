/*
 * Copyright (C) 2018-2024 crDroid Android Project
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
package org.lineageos.device.settings

import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.util.Log
import org.lineageos.device.settings.display.PwmController
import org.lineageos.device.settings.utils.FileUtils

/** Non-UI slider restore / broadcast. Boot and KeyHandler still call these. */
object DeviceSettings {
    private const val TAG = "DeviceSettings"

    @JvmStatic
    fun sendUpdateBroadcast(context: Context, usage: Int, actions: IntArray) {
        val intent = Intent(Constants.ACTION_UPDATE_SLIDER_SETTINGS).apply {
            putExtra(Constants.EXTRA_SLIDER_USAGE, usage)
            putExtra(Constants.EXTRA_SLIDER_ACTIONS, actions)
            putExtra(Constants.EXTRA_SLIDER_APPS, sliderApps(context))
            setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
        }
        context.sendBroadcastAsUser(intent, UserHandle.CURRENT)
        Log.i(TAG, "update slider usage $usage with actions: ${actions.contentToString()}")
    }

    @JvmStatic
    fun restoreSliderStates(context: Context) {
        val res = context.resources
        val prefs = context.getSharedPreferences(
            context.packageName + "_preferences",
            Context.MODE_PRIVATE,
        )
        val usage = prefs.getString(
            Constants.KEY_NOTIF_SLIDER_USAGE,
            res.getString(R.string.config_defaultNotificationSliderUsage),
        ) ?: return
        val defaultsResId = defaultActionsResId(usage)
        if (defaultsResId == 0) return
        val defaults = res.getStringArray(defaultsResId)
        if (defaults.size != 3) return

        val actionTop = prefs.getString(Constants.KEY_NOTIF_SLIDER_ACTION_TOP, defaults[0])!!
        val actionMiddle = prefs.getString(Constants.KEY_NOTIF_SLIDER_ACTION_MIDDLE, defaults[1])!!
        val actionBottom = prefs.getString(Constants.KEY_NOTIF_SLIDER_ACTION_BOTTOM, defaults[2])!!

        prefs.edit()
            .putString(Constants.KEY_NOTIF_SLIDER_USAGE, usage)
            .putString(Constants.KEY_NOTIF_SLIDER_ACTION_TOP, actionTop)
            .putString(Constants.KEY_NOTIF_SLIDER_ACTION_MIDDLE, actionMiddle)
            .putString(Constants.KEY_NOTIF_SLIDER_ACTION_BOTTOM, actionBottom)
            .commit()

        sendUpdateBroadcast(
            context,
            usage.toInt(),
            intArrayOf(actionTop.toInt(), actionMiddle.toInt(), actionBottom.toInt()),
        )
    }

    @JvmStatic
    fun restoreOnePulsePwmSetting(context: Context) {
        if (FileUtils.isFileWritable(Constants.NODE_ONEPULSE_PWM)) {
            val pwmController = PwmController.getInstance(context)
            if (pwmController.isPwmEnabled) {
                pwmController.enablePwm()
            }
        }
    }

    @JvmStatic
    fun defaultActionsResId(usage: String): Int = when (usage) {
        Constants.NOTIF_SLIDER_FOR_NOTIFICATION ->
            R.array.config_defaultSliderActionsForNotification
        Constants.NOTIF_SLIDER_FOR_FLASHLIGHT ->
            R.array.config_defaultSliderActionsForFlashlight
        Constants.NOTIF_SLIDER_FOR_BRIGHTNESS ->
            R.array.config_defaultSliderActionsForBrightness
        Constants.NOTIF_SLIDER_FOR_ROTATION ->
            R.array.config_defaultSliderActionsForRotation
        Constants.NOTIF_SLIDER_FOR_RINGER ->
            R.array.config_defaultSliderActionsForRinger
        Constants.NOTIF_SLIDER_FOR_NOTIFICATION_RINGER ->
            R.array.config_defaultSliderActionsForNotificationRinger
        Constants.NOTIF_SLIDER_FOR_APPLAUNCH ->
            R.array.config_defaultSliderActionsForApplaunch
        else -> 0
    }

    @JvmStatic
    fun sliderActionArrays(usage: String): Pair<Int, Int>? = when (usage) {
        Constants.NOTIF_SLIDER_FOR_NOTIFICATION ->
            R.array.notification_slider_mode_entries to
                R.array.notification_slider_mode_entry_values
        Constants.NOTIF_SLIDER_FOR_FLASHLIGHT ->
            R.array.notification_slider_flashlight_entries to
                R.array.notification_slider_flashlight_entry_values
        Constants.NOTIF_SLIDER_FOR_BRIGHTNESS ->
            R.array.notification_slider_brightness_entries to
                R.array.notification_slider_brightness_entry_values
        Constants.NOTIF_SLIDER_FOR_ROTATION ->
            R.array.notification_slider_rotation_entries to
                R.array.notification_slider_rotation_entry_values
        Constants.NOTIF_SLIDER_FOR_RINGER ->
            R.array.notification_slider_ringer_entries to
                R.array.notification_slider_ringer_entry_values
        Constants.NOTIF_SLIDER_FOR_NOTIFICATION_RINGER ->
            R.array.notification_ringer_slider_mode_entries to
                R.array.notification_ringer_slider_mode_entry_values
        Constants.NOTIF_SLIDER_FOR_APPLAUNCH ->
            R.array.notification_slider_applaunch_entries to
                R.array.notification_slider_applaunch_entry_values
        else -> null
    }

    @JvmStatic
    fun sliderApps(context: Context): Array<String> {
        val prefs = context.getSharedPreferences(
            context.packageName + "_preferences",
            Context.MODE_PRIVATE,
        )
        return arrayOf(
            prefs.getString(Constants.KEY_NOTIF_SLIDER_APP_TOP, "") ?: "",
            prefs.getString(Constants.KEY_NOTIF_SLIDER_APP_MIDDLE, "") ?: "",
            prefs.getString(Constants.KEY_NOTIF_SLIDER_APP_BOTTOM, "") ?: "",
        )
    }
}
