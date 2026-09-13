/*
 * Copyright (C) 2025 kenway215
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

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import org.lineageos.device.settings.bypasschrg.BypassChargingActivity
import org.lineageos.device.settings.bypasschrg.BypassChargingTile
import org.lineageos.device.settings.display.HbmTile
import org.lineageos.device.settings.display.PwmTile
import org.lineageos.device.settings.gamebar.GameBarSettingsActivity
import org.lineageos.device.settings.gamebar.GameBarTileService
import org.lineageos.device.settings.refreshrate.LtpoTile
import org.lineageos.device.settings.refreshrate.RefreshRateActivity
import org.lineageos.device.settings.refreshrate.RefreshRateTile

class TileHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        if (intent == null || TileService.ACTION_QS_TILE_PREFERENCES != intent.action) {
            Log.e(TAG, "Invalid or null intent received")
            finish()
            return
        }

        val qsTile = intent.getParcelableExtra<ComponentName>(Intent.EXTRA_COMPONENT_NAME)
        if (qsTile == null) {
            Log.e(TAG, "No QS tile component found in intent")
            finish()
            return
        }

        val qsName = qsTile.className
        val targetIntent = Intent()
        val mapped = TILE_ACTIVITY_MAP[qsName]
        if (mapped != null) {
            targetIntent.setClass(this, mapped)
            Log.i(TAG, "Launching settings activity for QS tile: $qsName")
        } else {
            val packageName = qsTile.packageName
            if (packageName == null) {
                Log.e(TAG, "QS tile package name is null")
                finish()
                return
            }
            targetIntent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            targetIntent.data = Uri.fromParts("package", packageName, null)
            Log.i(TAG, "Opening app info for package: $packageName")
        }

        targetIntent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NEW_TASK,
        )
        startActivity(targetIntent)
        finish()
    }

    companion object {
        private const val TAG = "TileHandlerActivity"

        private val TILE_ACTIVITY_MAP = mapOf(
            GameBarTileService::class.java.name to GameBarSettingsActivity::class.java,
            BypassChargingTile::class.java.name to BypassChargingActivity::class.java,
            HbmTile::class.java.name to DeviceSettingsActivity::class.java,
            PwmTile::class.java.name to DeviceSettingsActivity::class.java,
            RefreshRateTile::class.java.name to RefreshRateActivity::class.java,
            LtpoTile::class.java.name to RefreshRateActivity::class.java,
        )
    }
}
