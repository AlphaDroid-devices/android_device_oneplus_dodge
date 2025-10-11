/*
 * Copyright (C) 2025 The LineageOS Project
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

package org.lineageos.device.settings.bypasschrg;

import android.content.ContentResolver;
import android.content.Context;
import android.os.BatteryManager;
import android.util.Log;
import android.widget.Toast;
import androidx.preference.PreferenceManager;

import org.lineageos.device.settings.Constants;
import org.lineageos.device.settings.R;
import org.lineageos.device.settings.utils.FileUtils;

public class BypassChargingController {

    private static final boolean DEBUG = true;
    private static final String TAG = "BypassChargingController";
    private static final String BYPASS_CHARGING_ENABLED = "0";
    private static final String BYPASS_CHARGING_DISABLED = "1";
    private static final String KEY_BATTERY_LEVEL = "current_battery_level";

    private int mBatteryLevel;

    private Context mContext;
    private ContentResolver mContentResolver;
    private final Object mLock = new Object();

    private static BypassChargingController sInstance;
    public static synchronized BypassChargingController getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BypassChargingController(context);
        }
        return sInstance;
    }

    private BypassChargingController(Context context) {
        mContext = context.getApplicationContext();
        mContentResolver = mContext.getContentResolver();
        mBatteryLevel = getLevelFromIntent();
        if (isValidLevel(mBatteryLevel)) {
            saveCurrentBatteryLevel(mBatteryLevel);
        }
    }

    // Called from Service when battery level changes
    public void onBatteryLevelChanged(int level) {
        synchronized (mLock) {
            if (isValidLevel(level) && (mBatteryLevel == -1 || mBatteryLevel != level)) {
                mBatteryLevel = level;
                saveCurrentBatteryLevel(level);
                maybeEnableBypassCharging();
                if (DEBUG) Log.d(TAG, "Battery level changed (service): " + level + "%");
            }
        }
    }

    // get battery level using a sticky intent
    private int getLevelFromIntent() {
        android.content.IntentFilter filter =
                new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
        android.content.Intent intent = mContext.registerReceiver(null, filter);

        if (intent == null) {
            if (DEBUG) Log.w(TAG, "Sticky battery intent was null");
            return -1;
        }

        int extraLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int extraScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        return (extraLevel >= 0 && extraScale > 0) ?
                (int)((extraLevel / (float)extraScale) * 100) : -1;
    }

    private boolean isNodeAccessible(String node) {
        try {
            String status = FileUtils.readOneLine(node);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Node " + node + " not accessible", e);
            return false;
        }
    }

    private boolean writeToNode(String status) {
        synchronized (mLock) {
            try {
                FileUtils.writeLine(Constants.NODE_BYPASS_CHARGING, status);
            } catch (Exception e) {
                Log.e(TAG, "Failed to write bypass sysnode", e);
                return false;
            }
            return true;
        }
    }

    private int readFromNode() {
        synchronized (mLock) {
            try {
                String value = FileUtils.readOneLine(Constants.NODE_BYPASS_CHARGING);
                return Integer.parseInt(value);
            } catch (Exception e) {
                Log.e(TAG, "Failed to read bypass sysnode", e);
                return -1;
            }
        }
    }

    public boolean isBypassChargingSupported() {
        return isNodeAccessible(Constants.NODE_BYPASS_CHARGING);
    }

    private void maybeEnableBypassCharging() {
        if (mBatteryLevel >= getBypassChargingTarget()
                && getBypassChargingStatus() == Constants.BYPASS_WAITING) {
            if (writeToNode(BYPASS_CHARGING_ENABLED)) {
                saveBypassChargingStatus(Constants.BYPASS_ON);
                BypassChargingService.stop(mContext);
            }
        }
    }

    private void maybeDisableBypassCharging(int target) {
        if (mBatteryLevel < target
                && getBypassChargingStatus() == Constants.BYPASS_ON) {
            if (writeToNode(BYPASS_CHARGING_DISABLED)) {
                saveBypassChargingStatus(Constants.BYPASS_WAITING);
                BypassChargingService.start(mContext);
            }
        }
    }

    public boolean enableBypassCharging() {
        int level = getCurrentBatteryLevel();

        if (!isValidLevel(level)) {
            if (DEBUG) Log.w(TAG, "Cannot enable bypass: invalid battery level " + level);
            return false;
        }

        if (getBypassChargingTarget() > level) {
            saveBypassChargingStatus(Constants.BYPASS_WAITING);
            BypassChargingService.start(mContext);
            return true;
        }
        else if (writeToNode(BYPASS_CHARGING_ENABLED)) {
            saveBypassChargingStatus(Constants.BYPASS_ON);
            BypassChargingService.stop(mContext);
            return true;
        }
        return false;
    }

    public boolean disableBypassCharging() {
        if (writeToNode(BYPASS_CHARGING_DISABLED)) {
            saveBypassChargingStatus(Constants.BYPASS_OFF);
            BypassChargingService.stop(mContext);
            return true;
        }
        return false;
    }

    private void saveBypassChargingStatus(int status) {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putInt(Constants.KEY_BYPASS_CHARGING, status)
                .apply();
    }

    public int getBypassChargingStatus() {
        return PreferenceManager.getDefaultSharedPreferences(mContext)
                .getInt(Constants.KEY_BYPASS_CHARGING, Constants.BYPASS_OFF);
    }

    public void setBypassChargingTarget(int target) {
        if (target >= 0 && target <= 100) {
            saveBypassChargingTarget(target);
            if (getBypassChargingStatus() == Constants.BYPASS_ON) {
                maybeDisableBypassCharging(target);
            }
            else {
                maybeEnableBypassCharging();
            }
        }
    }

    private void saveBypassChargingTarget(int target) {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putInt(Constants.KEY_BYPASS_CHARGING_TARGET, target)
                .apply();
    }

    public int getBypassChargingTarget() {
        return PreferenceManager.getDefaultSharedPreferences(mContext)
                .getInt(Constants.KEY_BYPASS_CHARGING_TARGET, 0);
    }

    private void saveCurrentBatteryLevel(int level) {
        synchronized (mLock) {
            if (isValidLevel(level)) {
                PreferenceManager.getDefaultSharedPreferences(mContext)
                        .edit()
                        .putInt(KEY_BATTERY_LEVEL, level)
                        .apply();
            } else {
                if (DEBUG) Log.w(TAG, "Attempted to save invalid battery level: " + level);
            }
        }
    }

    public int getCurrentBatteryLevel() {
        synchronized (mLock) {
            int level = PreferenceManager.getDefaultSharedPreferences(mContext)
                    .getInt(KEY_BATTERY_LEVEL, -1);
            if (!isValidLevel(level)) {
                if (DEBUG) Log.w(TAG, "Battery level is invalid or not set: " + level);
            }
            return level;
        }
    }

    private boolean isValidLevel(int level) {
        return level >= 0 && level <= 100;
    }

    private void showToast(int resId) {
        Toast.makeText(mContext, mContext.getString(resId),
                Toast.LENGTH_LONG).show();
    }
}
