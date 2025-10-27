/*
 * SPDX-FileCopyrightText: 2025 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.device.settings.display;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import org.lineageos.device.settings.Constants;
import org.lineageos.device.settings.R;

public class PwmTile extends TileService {
    private static final String TAG = "PwmTile";
    private PwmController mPwmController;

    @Override
    public void onCreate() {
        super.onCreate();
        mPwmController = PwmController.getInstance(this);
    }

    @Override
    public void onStartListening() {
        boolean enabled = mPwmController.isPwmEnabled();
        updateTileState(enabled);
    }

    @Override
    public void onClick() {
        boolean currentState = mPwmController.isPwmEnabled();
        boolean success;

        if (currentState) {
            success = mPwmController.disablePwm();
        } else {
            success = mPwmController.enablePwm();
        }

        if (success) {
            updateTileState(!currentState);
            Log.i(TAG, "PWM toggled to: " + !currentState);
        } else {
            Log.w(TAG, "Failed to toggle PWM");
            // Refresh to show current state
            updateTileState(mPwmController.isPwmEnabled());
        }
    }

    private void updateTileState(boolean enabled) {
        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.onepulse_pwm_mode_title));
        tile.setContentDescription(getString(R.string.onepulse_pwm_mode_summary));
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_pwm));
        tile.updateTile();
    }
}
