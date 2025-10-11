/*
 * Copyright (C) 2025 The LineageOS Project
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

import static android.os.BatteryManager.EXTRA_LEVEL;
import static android.os.BatteryManager.EXTRA_SCALE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.lineageos.device.settings.Constants;
import org.lineageos.device.settings.R;

public class BypassChargingService extends Service {
    public static final String CHANNEL_ID = "bypass_charging_channel";
    public static final int NOTIF_ID = 1337;
    private static final String TAG = "BypassChargingService";

    private BypassChargingController mController;
    private BroadcastReceiver mBatteryReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        mController = BypassChargingController.getInstance(getApplicationContext());
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        registerBatteryReceiver();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterBatteryReceiver();
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    private void registerBatteryReceiver() {
        if (mBatteryReceiver != null) return;
        mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int extraLevel = intent.getIntExtra(EXTRA_LEVEL, -1);
                int extraScale = intent.getIntExtra(EXTRA_SCALE, -1);
                int level = (extraLevel >= 0 && extraScale > 0) ?
                        (int) ((extraLevel / (float) extraScale) * 100) : -1;

                Log.d(TAG, "Battery level in service: " + level);
                mController.onBatteryLevelChanged(level);
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(mBatteryReceiver, filter);
    }

    private void unregisterBatteryReceiver() {
        if (mBatteryReceiver != null) {
            try {
                unregisterReceiver(mBatteryReceiver);
            } catch (Exception e) {}
            mBatteryReceiver = null;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.bypass_charging_title),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.bypass_charging_waiting));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.bypass_charging_title))
                .setContentText(getString(R.string.bypass_charging_waiting))
                .setSmallIcon(R.drawable.ic_bypass_charging)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        return builder.build();
    }

    // Static method to start/stop service from anywhere
    public static void start(Context context) {
        Intent intent = new Intent(context, BypassChargingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundServiceAsUser(intent, UserHandle.CURRENT);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, BypassChargingService.class);
        context.stopService(intent);
    }
}
