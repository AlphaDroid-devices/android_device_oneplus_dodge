/*
 * Copyright (C) 2025 kenway214
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

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.lineageos.device.settings.Constants;
import org.lineageos.device.settings.R;

public class BypassChargingTile extends TileService {


    private BypassChargingController mBypassController;
    private boolean mEnabled;

    @Override
    public void onCreate() {
        super.onCreate();
        mBypassController = BypassChargingController.getInstance(this);
    }

    @Override
    public void onStartListening() {
        int status = mBypassController.getBypassChargingStatus();
        mEnabled = status != Constants.BYPASS_OFF;
        updateTileState(status);
    }

    @Override
    public void onClick() {
        boolean enabled = mBypassController.getBypassChargingStatus() != Constants.BYPASS_OFF;
        if (mEnabled == enabled) {
            boolean success;
            if (mEnabled) {
                success = mBypassController.disableBypassCharging() ? true : false;
            }
            else {
                success = mBypassController.enableBypassCharging() ? true : false;
            }
            if (success) {
                mEnabled = !mEnabled;
                updateTileState(mBypassController.getBypassChargingStatus());
            }
        }
    }

    private void updateTileState(int status) {
        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setState(status==Constants.BYPASS_OFF ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        tile.setLabel(getString(R.string.bypass_charging_title));
        tile.setContentDescription(getString(R.string.bypass_charging_summary));
        if (status==Constants.BYPASS_WAITING) {
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_bypass_waiting));
        }
        else {
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_bypass_charging));
        }
        tile.updateTile();
    }
}
