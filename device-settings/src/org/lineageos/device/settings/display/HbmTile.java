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

public class HbmTile extends TileService {
    private static final String TAG = "HbmTile";
    private HbmController mHbmController;

    @Override
    public void onCreate() {
        super.onCreate();
        mHbmController = HbmController.getInstance(this);
    }

    @Override
    public void onStartListening() {
        boolean enabled = mHbmController.isHbmEnabled();
        updateTileState(enabled);
    }

    @Override
    public void onClick() {
        boolean currentState = mHbmController.isHbmEnabled();
        boolean success;

        if (currentState) {
            success = mHbmController.disableHbm();
        } else {
            success = mHbmController.enableHbm();
        }

        if (success) {
            updateTileState(!currentState);
            Log.i(TAG, "HBM toggled to: " + !currentState);
        } else {
            Log.w(TAG, "Failed to toggle HBM");
            // Refresh to show current state
            updateTileState(mHbmController.isHbmEnabled());
        }
    }

    private void updateTileState(boolean enabled) {
        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.hbm_title));
        tile.setContentDescription(getString(R.string.hbm_summary));
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_hbm));
        tile.updateTile();
    }
}
