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

import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;

import org.lineageos.device.settings.Constants;
import org.lineageos.device.settings.R;
import org.lineageos.device.settings.preferences.CustomSeekBarPreference;

public class BypassChargingFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.bypass_charging_settings, rootKey);

        BypassChargingController bypassController =
                BypassChargingController.getInstance(getContext());
        boolean bypassSupported = bypassController.isBypassChargingSupported();

        TwoStatePreference bypassPreference = findPreference(Constants.KEY_BYPASS_CHARGING);
        if (bypassPreference != null) {
            bypassPreference.setEnabled(bypassSupported);
            if (bypassSupported) {
                bypassPreference.setChecked(bypassController.getBypassChargingStatus() != Constants.BYPASS_OFF);
                bypassPreference.setOnPreferenceChangeListener((pref, newValue) -> {
                    boolean enable = (boolean) newValue;
                    if (enable) {
                        bypassController.enableBypassCharging();
                    }
                    else {
                        bypassController.disableBypassCharging();
                    }
                    return true;
                });
            } else {
                bypassPreference.setSummary(R.string.bypass_charging_unavailable);
            }
        }

        CustomSeekBarPreference targetPreference = findPreference(Constants.KEY_BYPASS_CHARGING_TARGET);
        if (targetPreference != null) {
            targetPreference.setValue(bypassController.getBypassChargingTarget());
            targetPreference.setOnPreferenceChangeListener((pref, newValue) -> {
                    bypassController.setBypassChargingTarget((int) newValue);
                    return true;
                });
        }
    }
}
