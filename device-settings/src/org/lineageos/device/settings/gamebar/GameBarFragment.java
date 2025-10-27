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
package org.lineageos.device.settings.gamebar;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreferenceCompat;

import org.lineageos.device.settings.Constants;
import org.lineageos.device.settings.R;
import org.lineageos.device.settings.preferences.CustomSeekBarPreference;
import org.lineageos.device.settings.utils.AppPreferencesHelper;
import org.lineageos.device.settings.utils.PackageListAdapter;
import org.lineageos.device.settings.utils.PackageListAdapter.PackageItem;
import org.lineageos.device.settings.utils.AppListManager;

import java.util.HashSet;

public class GameBarFragment extends PreferenceFragmentCompat {

    private GameBar mGameBar;
    private SwitchPreferenceCompat mFpsSwitch;
    private SwitchPreferenceCompat mBatteryTempSwitch;
    private SwitchPreferenceCompat mCpuUsageSwitch;
    private SwitchPreferenceCompat mCpuClockSwitch;
    private SwitchPreferenceCompat mCpuTempSwitch;
    private SwitchPreferenceCompat mRamSwitch;
    private SwitchPreferenceCompat mGpuUsageSwitch;
    private SwitchPreferenceCompat mGpuClockSwitch;
    private SwitchPreferenceCompat mGpuTempSwitch;
    private Preference mCaptureStartPref;
    private Preference mCaptureStopPref;
    private Preference mCaptureExportPref;
    private SwitchPreferenceCompat mDoubleTapCapturePref;
    private SwitchPreferenceCompat mSingleTapTogglePref;
    private SwitchPreferenceCompat mLongPressEnablePref;
    private ListPreference mLongPressTimeoutPref;
    private CustomSeekBarPreference mTextSizePref;
    private CustomSeekBarPreference mBgAlphaPref;
    private CustomSeekBarPreference mCornerRadiusPref;
    private CustomSeekBarPreference mPaddingPref;
    private CustomSeekBarPreference mItemSpacingPref;
    private ListPreference mUpdateIntervalPref;
    private ListPreference mTitleColorPref;
    private ListPreference mValueColorPref;
    private ListPreference mPositionPref;
    private ListPreference mSplitModePref;
    private ListPreference mOverlayFormatPref;
    private Preference mResetPositionPref;

    private PreferenceGroup mPackagesPreList;
    private Preference mAddPackagesPref;
    private AppListManager mAppListManager;
    private static final String GAME_BAR_ADD_PACKAGES = "game_bar_add_packages";
    private static final String GAME_BAR_APPLICATIONS = "game_bar_applications";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.game_bar_preferences, rootKey);

        mGameBar = GameBar.getInstance(getContext());

        mAppListManager = new AppListManager(
                getContext(),
                Constants.KEY_GAMEBAR_AUTO_APPS,
                this::onAppListChanged
        );

        mPackagesPreList = findPreference(GAME_BAR_APPLICATIONS);
        if (mPackagesPreList != null) {
            mPackagesPreList.setOrderingAsAdded(false);
        }

        mAddPackagesPref = findPreference(GAME_BAR_ADD_PACKAGES);
        if (mAddPackagesPref != null) {
            mAddPackagesPref.setOnPreferenceClickListener(pref -> {
                showAppSelectionDialog();
                return true;
            });
        }

        // global switch
        SwitchPreferenceCompat enableSwitch = findPreference("game_bar_enable");
        if (enableSwitch != null) {
            enableSwitch.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = (boolean) newValue;
                if (enabled) {
                    if (Settings.canDrawOverlays(getContext())) {
                        mGameBar.applyPreferences();
                        mGameBar.show();
                    } else {
                        Toast.makeText(getContext(), R.string.overlay_permission_required, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                } else {
                    mGameBar.hide();
                }
                return true;
            });
        }

        // Display preferences
        mFpsSwitch = findPreference("game_bar_fps_enable");
        if (mFpsSwitch != null) {
            mFpsSwitch.setOnPreferenceChangeListener((pref, newValue) -> {
                mGameBar.setShowFps((boolean) newValue);
                return true;
            });
        }

        mBatteryTempSwitch = findPreference("game_bar_temp_enable");
        if (mBatteryTempSwitch != null) {
            mBatteryTempSwitch.setOnPreferenceChangeListener((pref, newValue) -> {
                mGameBar.setShowBatteryTemp((boolean) newValue);
                return true;
            });
        }

        mCpuUsageSwitch = findPreference("game_bar_cpu_usage_enable");
        if (mCpuUsageSwitch != null) {
            mCpuUsageSwitch.setOnPreferenceChangeListener((pref, newValue) -> {
                mGameBar.setShowCpuUsage((boolean) newValue);
                return true;
            });
        }

        mCpuClockSwitch = findPreference("game_bar_cpu_clock_enable");
        if (mCpuClockSwitch != null) {
            mCpuClockSwitch.setSummary("Shows CPU clock speed\n⚠ Hidden in minimal side-by-side layout");
            mCpuClockSwitch.setOnPreferenceChangeListener((pref, newValue) -> {
                mGameBar.setShowCpuClock((boolean) newValue);
                return true;
            });
        }

        mCpuTempSwitch = findPreference("game_bar_cpu_temp_enable");
        if (mCpuTempSwitch != null) {
            mCpuTempSwitch.setOnPreferenceChangeListener((pref, newValue) -> {
                mGameBar.setShowCpuTemp((boolean) newValue);
                return true;
            });
        }

        mRamSwitch = findPreference("game_bar_ram_enable");
        if (mRamSwitch != null) {
            mRamSwitch.setOnPreferenceChangeListener((pref, newValue) -> {
                mGameBar.setShowRam((boolean) newValue);
                return true;
            });
        }

        mGpuUsageSwitch = findPreference("game_bar_gpu_usage_enable");
        if (mGpuUsageSwitch != null) {
            mGpuUsageSwitch.setOnPreferenceChangeListener((pref, newValue) -> {
                mGameBar.setShowGpuUsage((boolean) newValue);
                return true;
            });
        }

        mGpuClockSwitch = findPreference("game_bar_gpu_clock_enable");
        if (mGpuClockSwitch != null) {
            mGpuClockSwitch.setOnPreferenceChangeListener((pref, newValue) -> {
                mGameBar.setShowGpuClock((boolean) newValue);
                return true;
            });
        }

        mGpuTempSwitch = findPreference("game_bar_gpu_temp_enable");
        if (mGpuTempSwitch != null) {
            mGpuTempSwitch.setOnPreferenceChangeListener((pref, newValue) -> {
                mGameBar.setShowGpuTemp((boolean) newValue);
                return true;
            });
        }

        // Capture preferences
        mCaptureStartPref = findPreference("game_bar_capture_start");
        if (mCaptureStartPref != null) {
            mCaptureStartPref.setOnPreferenceClickListener(pref -> {
                GameDataExport.getInstance().startCapture();
                Toast.makeText(getContext(), "Started logging Data", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        mCaptureStopPref = findPreference("game_bar_capture_stop");
        if (mCaptureStopPref != null) {
            mCaptureStopPref.setOnPreferenceClickListener(pref -> {
                GameDataExport.getInstance().stopCapture();
                Toast.makeText(getContext(), "Stopped logging Data", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        mCaptureExportPref = findPreference("game_bar_capture_export");
        if (mCaptureExportPref != null) {
            mCaptureExportPref.setOnPreferenceClickListener(pref -> {
                GameDataExport.getInstance().exportDataToCsv();
                Toast.makeText(getContext(), "Exported log data to file", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        // Gesture preferences
        mDoubleTapCapturePref = findPreference("game_bar_doubletap_capture");
        if (mDoubleTapCapturePref != null) {
            mDoubleTapCapturePref.setOnPreferenceChangeListener((pref, newValue) -> {
                mGameBar.setDoubleTapCaptureEnabled((boolean) newValue);
                return true;
            });
        }

        mSingleTapTogglePref = findPreference("game_bar_single_tap_toggle");
        if (mSingleTapTogglePref != null) {
            mSingleTapTogglePref.setOnPreferenceChangeListener((pref, newValue) -> {
                mGameBar.setSingleTapToggleEnabled((boolean) newValue);
                return true;
            });
        }

        mLongPressEnablePref = findPreference("game_bar_longpress_enable");
        if (mLongPressEnablePref != null) {
            mLongPressEnablePref.setOnPreferenceChangeListener((pref, newValue) -> {
                mGameBar.setLongPressEnabled((boolean) newValue);
                return true;
            });
        }

        mLongPressTimeoutPref = findPreference("game_bar_longpress_timeout");
        if (mLongPressTimeoutPref != null) {
            mLongPressTimeoutPref.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue instanceof String) {
                    long ms = Long.parseLong((String) newValue);
                    mGameBar.setLongPressThresholdMs(ms);
                }
                return true;
            });
        }

        // UI customization preferences
        mTextSizePref = findPreference("game_bar_text_size");
        if (mTextSizePref != null) {
            mTextSizePref.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue instanceof Integer) {
                    int sizeSp = (Integer) newValue;
                    mGameBar.updateTextSize(sizeSp);
                    pref.setSummary("Size: " + sizeSp + "SP (toggle overlay to preview)");
                }
                return true;
            });
            android.content.SharedPreferences prefs =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
            int sizeSp = prefs.getInt("game_bar_text_size", 16);
            mTextSizePref.setSummary("Size: " + sizeSp + "SP");
        }

        mBgAlphaPref = findPreference("game_bar_background_alpha");
        if (mBgAlphaPref != null) {
            mBgAlphaPref.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue instanceof Integer) {
                    int alpha = (Integer) newValue;
                    int percent = Math.round((alpha / 255f) * 100);
                    mGameBar.updateBackgroundAlpha(alpha);
                    pref.setSummary("Transparency: " + percent + "%");
                }
                return true;
            });
            android.content.SharedPreferences prefsAlpha =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
            int alpha = prefsAlpha.getInt("game_bar_background_alpha", 128);
            int percent = Math.round((alpha / 255f) * 100);
            mBgAlphaPref.setSummary("Transparency: " + percent + "%");
        }

        mCornerRadiusPref = findPreference("game_bar_corner_radius");
        if (mCornerRadiusPref != null) {
            mCornerRadiusPref.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue instanceof Integer) {
                    mGameBar.updateCornerRadius((Integer) newValue);
                }
                return true;
            });
        }

        mPaddingPref = findPreference("game_bar_padding");
        if (mPaddingPref != null) {
            mPaddingPref.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue instanceof Integer) {
                    mGameBar.updatePadding((Integer) newValue);
                }
                return true;
            });
        }

        mItemSpacingPref = findPreference("game_bar_item_spacing");
        if (mItemSpacingPref != null) {
            mItemSpacingPref.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue instanceof Integer) {
                    mGameBar.updateItemSpacing((Integer) newValue);
                }
                return true;
            });
        }

        mUpdateIntervalPref = findPreference("game_bar_update_interval");
        if (mUpdateIntervalPref != null) {
            mUpdateIntervalPref.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue instanceof String) {
                    mGameBar.updateUpdateInterval((String) newValue);
                }
                return true;
            });
        }

        mTitleColorPref = findPreference("game_bar_title_color");
        if (mTitleColorPref != null) {
            mTitleColorPref.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue instanceof String) {
                    String hexColor = (String) newValue;
                    if (mGameBar.isValidHexColor(hexColor)) {
                        mGameBar.updateTitleColor(hexColor);
                        Toast.makeText(getContext(), "Title color updated", Toast.LENGTH_SHORT).show();
                        return true;
                    } else {
                        Toast.makeText(getContext(), "Invalid color format (use #RRGGBB)", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
                return false;
            });
        }

        mValueColorPref = findPreference("game_bar_value_color");
        if (mValueColorPref != null) {
            mValueColorPref.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue instanceof String) {
                    String hexColor = (String) newValue;
                    if (mGameBar.isValidHexColor(hexColor)) {
                        mGameBar.updateValueColor(hexColor);
                        Toast.makeText(getContext(), "Value color updated", Toast.LENGTH_SHORT).show();
                        return true;
                    } else {
                        Toast.makeText(getContext(), "Invalid color format (use #RRGGBB)", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
                return false;
            });
        }

        mPositionPref = findPreference("game_bar_position");
        if (mPositionPref != null) {
            mPositionPref.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue instanceof String) {
                    String pos = (String) newValue;
                    mGameBar.updatePosition(pos);
                    Toast.makeText(getContext(), "Position: " + formatPositionName(pos), Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        mResetPositionPref = findPreference("game_bar_reset_position");
        if (mResetPositionPref != null) {
            mResetPositionPref.setOnPreferenceClickListener(pref -> {
                android.content.SharedPreferences prefs =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
                prefs.edit()
                    .remove("game_bar_dragged_x")
                    .remove("game_bar_dragged_y")
                    .apply();
                mGameBar.updatePosition("top_left");
                Toast.makeText(getContext(), "Position reset to Top Left", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        mSplitModePref = findPreference("game_bar_split_mode");
        if (mSplitModePref != null) {
            mSplitModePref.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue instanceof String) {
                    String layout = (String) newValue;
                    mGameBar.updateSplitMode(layout);
                    Toast.makeText(getContext(), "Layout: " + (layout.equals("side_by_side") ? "Side-by-Side" : "Stacked"), Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        mOverlayFormatPref = findPreference("game_bar_format");
        if (mOverlayFormatPref != null) {
            mOverlayFormatPref.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue instanceof String) {
                    String format = (String) newValue;
                    mGameBar.updateOverlayFormat(format);
                    Toast.makeText(getContext(), "Format: " + (format.equals("minimal") ? "Minimal" : "Full"), Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!hasUsageStatsPermission(requireContext())) {
            requestUsageStatsPermission();
        }
        if (mAppListManager.refreshAppList()) {
            refreshAppListUI();
        }
    }

    private void refreshAppListUI() {
        AppPreferencesHelper.refreshAppPreferences(
                mPackagesPreList,
                mAddPackagesPref,
                mAppListManager.getAppList(),
                getContext(),
                packageName -> showDeleteConfirmation(packageName)
        );
    }

    private void showDeleteConfirmation(String packageName) {
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.delete)
                .setMessage(R.string.delete_message)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    mAppListManager.removeApp(packageName);
                    refreshAppListUI();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAppSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        final Dialog dialog;
        final ListView list = new ListView(requireActivity());

        HashSet<String> excludedPackages = new HashSet<>(mAppListManager.getAppList().keySet());
        excludedPackages.add(getContext().getPackageName());

        AppPreferencesHelper.setupPackageListAdapter(list, excludedPackages, getContext());

        builder.setTitle(R.string.add_app);
        builder.setView(list);
        dialog = builder.create();

        list.setOnItemClickListener((parent, view, position, id) -> {
            PackageItem info = (PackageItem) parent.getItemAtPosition(position);
            mAppListManager.addApp(info.packageName);
            refreshAppListUI();
            dialog.dismiss();
        });

        if (!requireActivity().isFinishing()) {
            dialog.show();
        }
    }

    private void onAppListChanged() {
        GameBarMonitorService.notifyStateChanged(getContext());
    }

    private String formatPositionName(String pos) {
        switch (pos) {
            case "top_left": return "Top Left";
            case "top_center": return "Top Center";
            case "top_right": return "Top Right";
            case "bottom_left": return "Bottom Left";
            case "bottom_center": return "Bottom Center";
            case "bottom_right": return "Bottom Right";
            case "draggable": return "Draggable";
            default: return pos;
        }
    }

    private boolean hasUsageStatsPermission(Context context) {
        android.app.AppOpsManager appOps = (android.app.AppOpsManager)
                context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.getPackageName()
        );
        return (mode == android.app.AppOpsManager.MODE_ALLOWED);
    }

    private void requestUsageStatsPermission() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivity(intent);
    }
}
