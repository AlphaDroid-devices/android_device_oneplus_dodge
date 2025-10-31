/*
 * SPDX-FileCopyrightText: 2025 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 *
 * Main service for DeviceSettings.
 * - Listens for screen on/off and power connect/disconnect
 * - On SCREEN_OFF: stop GameBar monitor service and hide overlay
 * - On SCREEN_ON: restart GameBar monitor service if needed
 *
 * Power connect/disconnect handlers are left empty (TODO).
 */

package org.lineageos.device.settings;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import org.lineageos.device.settings.gamebar.GameBar;
import org.lineageos.device.settings.gamebar.GameBarController;
import org.lineageos.device.settings.gamebar.GameBarMonitorService;

public class DeviceSettingsService extends Service {
    private static final String TAG = "DeviceSettingsService";

    private final Handler mHandler = new Handler();
    private BroadcastReceiver mReceiver;

    @Override
    public void onCreate() {
        super.onCreate();

        if (Constants.DEBUG) Log.i(TAG, "DeviceSettingsService created - registering receivers");
        registerReceivers();
    }

    @Override
    public void onDestroy() {
        if (Constants.DEBUG) Log.i(TAG, "DeviceSettingsService destroyed - unregistering receivers");
        unregisterReceivers();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // not a bound service
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Keep running to listen for events
        return START_STICKY;
    }

    private void registerReceivers() {
        if (mReceiver != null) return;

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                final String action = intent.getAction();

                if (Constants.DEBUG) Log.i(TAG, "Received broadcast: " + action);

                switch (action) {
                    case Intent.ACTION_SCREEN_OFF:
                        handleScreenOff(context);
                        break;

                    case Intent.ACTION_SCREEN_ON:
                        handleScreenOn(context);
                        break;

                    case Intent.ACTION_POWER_CONNECTED:
                        handlePowerConnected(context);
                        break;

                    case Intent.ACTION_POWER_DISCONNECTED:
                        handlePowerDisconnected(context);
                        break;

                    default:
                        // ignore
                        break;
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);

        try {
            registerReceiver(mReceiver, filter);
            if (Constants.DEBUG) Log.i(TAG, "Receivers registered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register DeviceSettingsService receivers", e);
            mReceiver = null;
        }
    }

    private void unregisterReceivers() {
        if (mReceiver == null) return;
        try {
            unregisterReceiver(mReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister DeviceSettingsService receiver", e);
        } finally {
            mReceiver = null;
        }
    }

    // ===== handlers =====

    private void handleScreenOff(Context context) {
        if (Constants.DEBUG) Log.i(TAG, "Screen OFF - stopping GameBar monitoring and hiding overlay");

        try {
            // Ensure overlay hidden
            try {
                GameBar.getInstance(context).hide();
            } catch (Exception e) {
                Log.w(TAG, "GameBar.hide() failed", e);
            }

            // Stop GameBar monitor service. onDestroy() will stop monitoring and hide overlay as well.
            try {
                Intent svc = new Intent(context.getApplicationContext(), GameBarMonitorService.class);
                context.stopService(svc);
                if (Constants.DEBUG) Log.i(TAG, "Requested stop for GameBarMonitorService");
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop GameBarMonitorService", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in handleScreenOff", e);
        }
    }

    private void handleScreenOn(Context context) {
        GameBarMonitorService.notifyStateChanged(context);
    }

    // Power handlers left intentionally empty for now (TODO)
    private void handlePowerConnected(Context context) {
        if (Constants.DEBUG) Log.i(TAG, "Power connected - (noop for now)");
        // TODO: implement central power handling if needed
    }

    private void handlePowerDisconnected(Context context) {
        if (Constants.DEBUG) Log.i(TAG, "Power disconnected - (noop for now)");
        // TODO: implement central power handling if needed
    }
}
