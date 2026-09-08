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
 * AOD brightness on dodge (AA569). 0x51 is ignored in idle; the panel uses
 * page 0x1E register 0x81 (PeakLumin dimming): 0x00 = high, 0x10 = low.
 * {@link Constants#NODE_AOD_LIGHT_MODE} is stored for the kernel LP1 hook.
 * Live changes while already in AOD also poke 0x81 through write_panel_reg.
 */
public class AodBrightnessController {
    private static final String TAG = "AodBrightnessController";
    private static AodBrightnessController sInstance;

    /** SDE_MODE_DPMS_LP1 / LP2. */
    private static final int POWER_LP1 = 1;
    private static final int POWER_LP2 = 2;

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

    /**
     * Re-apply PeakLumin if the panel is already in LP1/LP2. Used after doze
     * entry so a live 0x81 write lands after LP1, not during the transition.
     */
    public void applyIfInAod() {
        if (!isInAod()) {
            return;
        }
        applyPeakLumin(isHighBrightnessEnabled());
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
        if (isInAod()) {
            applyPeakLumin(highBrightness);
        }
        Log.i(TAG, "AOD light mode set to " + nodeValue
                + " (" + (highBrightness ? "high" : "low") + ")");
        return true;
    }

    private static boolean isInAod() {
        String status = FileUtils.readLineTrimmed(Constants.NODE_POWER_STATUS);
        if (status == null) {
            return false;
        }
        int colon = status.lastIndexOf(':');
        if (colon < 0 || colon + 1 >= status.length()) {
            return false;
        }
        try {
            int mode = Integer.parseInt(status.substring(colon + 1).trim());
            return mode == POWER_LP1 || mode == POWER_LP2;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void applyPeakLumin(boolean highBrightness) {
        if (!FileUtils.isFileWritable(Constants.NODE_WRITE_PANEL_REG)) {
            Log.w(TAG, "write_panel_reg is not writable");
            return;
        }
        final String peaklumin = highBrightness ? "81 00" : "81 10";
        boolean ok = FileUtils.writeLine(Constants.NODE_WRITE_PANEL_REG, "FF 5A A5 1E")
                && FileUtils.writeLine(Constants.NODE_WRITE_PANEL_REG, peaklumin)
                && FileUtils.writeLine(Constants.NODE_WRITE_PANEL_REG, "FF 5A A5 00");
        if (ok) {
            Log.i(TAG, "AOD PeakLumin 0x81=" + (highBrightness ? "00" : "10"));
        } else {
            Log.e(TAG, "Failed to write AOD PeakLumin register");
        }
    }
}
