/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2020 The LineageOS Project
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
package org.lineageos.device.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.device.settings.bypasschrg.BypassChargingController;
import org.lineageos.device.settings.bypasschrg.BypassChargingManager;
import org.lineageos.device.settings.gamebar.GameBar;
import org.lineageos.device.settings.gamebar.GameBarController;
import org.lineageos.device.settings.gamebar.GameBarMonitorService;
import org.lineageos.device.settings.refreshrate.RefreshRateController;
import org.lineageos.device.settings.refreshrate.RefreshRateMonitorService;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            if (Constants.DEBUG) Log.i(TAG, "Boot completed - initializing services");
            initializeBypassCharging(context);
            initializeGameBar(context);
            initializeRefreshRate(context);
        }
    }

    private void initializeBypassCharging(Context context) {
        if (Constants.DEBUG) Log.i(TAG, "Initializing BypassCharging");
        try {
            BypassChargingController controller = BypassChargingController.getInstance(context);
            BypassChargingManager.notifyStateChanged(context, controller.getState());
            if (Constants.DEBUG) Log.i(TAG, "BypassCharging initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize BypassCharging", e);
        }
    }

    private void initializeGameBar(Context context) {
        if (Constants.DEBUG) Log.i(TAG, "Initializing GameBar");
        try {
            var prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean masterEnabled = prefs.getBoolean("game_bar_enable", false);
            boolean hasAutoApps = hasGameBarAutoApps(context);
            if (Constants.DEBUG) Log.i(TAG, "GameBar state: masterEnabled=" + masterEnabled + ", hasAutoApps=" + hasAutoApps);
            if (masterEnabled) {
                // Restore GameBar overlay state
                GameBar.getInstance(context).applyPreferences();
                GameBar.getInstance(context).show();
                if (Constants.DEBUG) Log.i(TAG, "GameBar overlay restored");
            }
            if (masterEnabled || hasAutoApps) {
                // Start monitoring service
                GameBarMonitorService.notifyStateChanged(context);
                if (Constants.DEBUG) Log.i(TAG, "GameBar monitoring service initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize GameBar", e);
        }
    }

    private void initializeRefreshRate(Context context) {
        if (Constants.DEBUG) Log.i(TAG, "Initializing RefreshRate");
        try {
            RefreshRateController controller = RefreshRateController.getInstance(context);
            RefreshRateMonitorService.notifyStateChanged(context);
            if (Constants.DEBUG) Log.i(TAG, "RefreshRate initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize RefreshRate", e);
        }
    }

    private boolean hasGameBarAutoApps(Context context) {
        String raw = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(Constants.KEY_GAMEBAR_AUTO_APPS, "");
        return !TextUtils.isEmpty(raw);
    }
}
