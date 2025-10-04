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
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.preference.PreferenceManager;

import org.lineageos.device.settings.R;
import org.lineageos.device.settings.utils.FileUtils;

/**
 * This class is implemented to coexist with Lineage Charging Control (CC).
 * Bypass Charging will override (disable) CC, while it's enabled.
 * CC status will be restored, when Bypass Charging is disabled.
 * Any user changes to CC settings, while Bypass Charging is enabled,
 * will override Bypass Charging settings.
 */
public class BypassChargingController {

    private static final boolean DEBUG = false;

    private static final String TAG = "BypassChargingController";
    private static final String BYPASS_CHARGING_NODE = "/sys/class/oplus_chg/battery/mmi_charging_enable";
    private static final String KEY_BYPASS_CHARGING_ENABLED = "bypass_charging_enabled";

    // Bypass modes
    private static final String BYPASS_CHARGING_ENABLED = "0";
    private static final String BYPASS_CHARGING_DISABLED = "1";

    // Charging Control settings
    private static final String KEY_CHARGING_CONTROL_ENABLED = "charging_control_enabled";

    private Context mContext;
    private ContentResolver mContentResolver;

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
    }

    public boolean isBypassChargingSupported() {
        return isNodeAccessible(BYPASS_CHARGING_NODE);
    }

    public boolean isBypassChargingEnabled() {
        try {
            String value = FileUtils.readOneLine(BYPASS_CHARGING_NODE);
            return BYPASS_CHARGING_ENABLED.equals(value) && isSavedBypassChargingEnabled()
                    && !isChargingControlEnabled();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read bypass sysnode", e);
            return false;
        }
    }

    private boolean isNodeAccessible(String node) {
        try {
            String value = FileUtils.readOneLine(node);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Node " + node + " not accessible", e);
            return false;
        }
    }

    private boolean writeToNode(String value) {
        try {
            FileUtils.writeLine(BYPASS_CHARGING_NODE, value);
        } catch (Exception e) {
            Log.e(TAG, "Failed to write bypass sysnode", e);
            return false;
        }
        return true;
    }

    public void setBypassCharging(boolean enable) {
        if (enable) {
            setChargingControlEnabled(false);
        }
        if (writeToNode(enable ? BYPASS_CHARGING_ENABLED : BYPASS_CHARGING_DISABLED)) {
            saveBypassChargingEnabled(enable);
        }
        else {
           showToast(R.string.bypass_charging_error);
        }
    }

    private void saveBypassChargingEnabled(boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putBoolean(KEY_BYPASS_CHARGING_ENABLED, enabled)
                .commit();
    }

    private boolean isSavedBypassChargingEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(mContext)
                .getBoolean(KEY_BYPASS_CHARGING_ENABLED, false);
    }

    private boolean isChargingControlEnabled() {
        return Settings.System.getInt(mContentResolver,
                KEY_CHARGING_CONTROL_ENABLED, 0) != 0;
    }

    private void setChargingControlEnabled(boolean enabled) {
        Settings.System.putInt(mContentResolver,
                KEY_CHARGING_CONTROL_ENABLED, enabled ? 1 : 0);
    }

    private void showToast(int resId) {
        Toast.makeText(mContext, mContext.getString(resId),
                Toast.LENGTH_LONG).show();
    }
}
