/*
 * SPDX-FileCopyrightText: 2026 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.device.settings.display;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.device.settings.Constants;
import org.lineageos.device.settings.utils.FileUtils;

/**
 * Stock AOD brightness. The panel supports two fixed levels via
 * {@link Constants#NODE_AOD_LIGHT_MODE}: 0 = high (~50 nits), 1 = low (~10 nits).
 * Kernel OFP applies the value on doze entry, and live if already in AOD.
 */
public class AodBrightnessController {
    private static final String TAG = "AodBrightnessController";
    private static AodBrightnessController sInstance;

    private final SharedPreferences mSharedPrefs;

    private AodBrightnessController(Context context) {
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(
                context.getApplicationContext());
    }

    public static synchronized AodBrightnessController getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AodBrightnessController(context);
        }
        return sInstance;
    }

    public boolean isHighBrightnessEnabled() {
        return mSharedPrefs.getBoolean(Constants.KEY_AOD_HIGH_BRIGHTNESS, false);
    }

    public boolean setHighBrightness(boolean highBrightness) {
        if (!FileUtils.isFileWritable(Constants.NODE_AOD_LIGHT_MODE)) {
            Log.w(TAG, "Node is not writable: " + Constants.NODE_AOD_LIGHT_MODE);
            // Persist anyway so restoreAodBrightness() applies it once the node is ready
            mSharedPrefs.edit()
                    .putBoolean(Constants.KEY_AOD_HIGH_BRIGHTNESS, highBrightness)
                    .commit();
            return false;
        }
        return apply(highBrightness);
    }

    /** The kernel boots aod_light_mode=0 (50 nits), so the default has to be re-asserted. */
    public void restoreAodBrightness() {
        boolean high = isHighBrightnessEnabled();
        if (!FileUtils.isFileWritable(Constants.NODE_AOD_LIGHT_MODE)) {
            Log.w(TAG, "Cannot restore AOD brightness: node not writable");
            return;
        }
        if (apply(high)) {
            Log.i(TAG, "Restored AOD brightness: " + (high ? "high" : "low"));
        }
    }

    private boolean apply(boolean highBrightness) {
        final String nodeValue = highBrightness ? "0" : "1";
        if (!FileUtils.writeLine(Constants.NODE_AOD_LIGHT_MODE, nodeValue)) {
            Log.e(TAG, "Failed to write AOD light mode " + nodeValue);
            return false;
        }
        mSharedPrefs.edit()
                .putBoolean(Constants.KEY_AOD_HIGH_BRIGHTNESS, highBrightness)
                .commit();
        Log.i(TAG, "AOD light mode set to " + nodeValue
                + " (" + (highBrightness ? "50 nits" : "10 nits") + ")");
        return true;
    }
}
