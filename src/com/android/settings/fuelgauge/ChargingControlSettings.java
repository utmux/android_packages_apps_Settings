/*
 * Copyright (C) 2026 The LineageOS Project
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

package com.android.settings.fuelgauge;

import android.app.settings.SettingsEnums;
import android.os.Bundle;
import android.os.SystemProperties;

import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.widget.SeekBarPreference;

/** Controls the device-specific charge limit and bypass charging features. */
public class ChargingControlSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_CHARGE_LIMIT_ENABLED = "charge_limit_enabled";
    private static final String KEY_CHARGE_LIMIT_LEVEL = "charge_limit_level";
    private static final String KEY_BYPASS_CHARGING = "bypass_charging";
    private static final String KEY_STATUS = "charging_control_status";

    private static final String PROP_CHARGE_LIMIT = "persist.sys.charge_limit";
    private static final String PROP_CHARGE_START = "persist.sys.charge_start";
    private static final String PROP_CHARGE_END = "persist.sys.charge_end";
    private static final String PROP_BYPASS_CHARGE = "persist.sys.bypass_charge";

    private static final int DEFAULT_CHARGE_END = 80;
    private static final int MIN_CHARGE_END = 50;
    private static final int MAX_CHARGE_END = 100;
    private static final int CHARGE_START_THRESHOLD = 40;

    private SwitchPreference mChargeLimitSwitch;
    private SeekBarPreference mChargeLimitLevel;
    private SwitchPreference mBypassChargingSwitch;
    private Preference mStatusPreference;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.FUELGAUGE_POWER_USAGE_SUMMARY_V2;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.charging_control_settings);

        mChargeLimitSwitch = (SwitchPreference) findPreference(KEY_CHARGE_LIMIT_ENABLED);
        mChargeLimitLevel = (SeekBarPreference) findPreference(KEY_CHARGE_LIMIT_LEVEL);
        mBypassChargingSwitch = (SwitchPreference) findPreference(KEY_BYPASS_CHARGING);
        mStatusPreference = findPreference(KEY_STATUS);

        mChargeLimitSwitch.setOnPreferenceChangeListener(this);
        mChargeLimitLevel.setOnPreferenceChangeListener(this);
        mChargeLimitLevel.setContinuousUpdates(false);
        mBypassChargingSwitch.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUi();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mChargeLimitSwitch) {
            final boolean enabled = (Boolean) newValue;
            if (enabled) {
                SystemProperties.set(PROP_BYPASS_CHARGE, "0");
                mBypassChargingSwitch.setChecked(false);
            }
            SystemProperties.set(PROP_CHARGE_LIMIT, enabled ? "1" : "0");
            updateUi(enabled, false, getChargeEndThreshold());
            return true;
        }

        if (preference == mChargeLimitLevel) {
            final int endThreshold = clamp((Integer) newValue,
                    MIN_CHARGE_END, MAX_CHARGE_END);
            final int startThreshold = CHARGE_START_THRESHOLD;

            // Set the lower threshold first; changing the upper threshold triggers init to
            // reapply both values in a validation-safe order.
            SystemProperties.set(PROP_CHARGE_START, Integer.toString(startThreshold));
            SystemProperties.set(PROP_CHARGE_END, Integer.toString(endThreshold));
            updateUi(true, false, endThreshold);
            return true;
        }

        if (preference == mBypassChargingSwitch) {
            final boolean enabled = (Boolean) newValue;
            if (enabled) {
                SystemProperties.set(PROP_CHARGE_LIMIT, "0");
                mChargeLimitSwitch.setChecked(false);
            }
            SystemProperties.set(PROP_BYPASS_CHARGE, enabled ? "1" : "0");
            updateUi(false, enabled, getChargeEndThreshold());
            return true;
        }

        return false;
    }

    private void refreshUi() {
        boolean chargeLimitEnabled = SystemProperties.getBoolean(PROP_CHARGE_LIMIT, false);
        final boolean bypassChargingEnabled =
                SystemProperties.getBoolean(PROP_BYPASS_CHARGE, false);

        // Bypass charging has priority in the kernel. Normalize a manually-created conflict.
        if (chargeLimitEnabled && bypassChargingEnabled) {
            chargeLimitEnabled = false;
            SystemProperties.set(PROP_CHARGE_LIMIT, "0");
        }

        updateUi(chargeLimitEnabled, bypassChargingEnabled, getChargeEndThreshold());
    }

    private void updateUi(boolean chargeLimitEnabled, boolean bypassChargingEnabled,
            int endThreshold) {
        final int startThreshold = CHARGE_START_THRESHOLD;

        mChargeLimitSwitch.setChecked(chargeLimitEnabled);
        mBypassChargingSwitch.setChecked(bypassChargingEnabled);
        mChargeLimitLevel.setEnabled(chargeLimitEnabled && !bypassChargingEnabled);
        mChargeLimitLevel.setProgress(endThreshold);
        mChargeLimitLevel.setTitle(
                getString(R.string.charge_limit_level_title, endThreshold));

        if (bypassChargingEnabled) {
            mStatusPreference.setSummary(R.string.charging_control_status_bypass);
        } else if (chargeLimitEnabled) {
            mStatusPreference.setSummary(getString(R.string.charging_control_status_limit,
                    endThreshold, startThreshold));
        } else {
            mStatusPreference.setSummary(R.string.charging_control_status_normal);
        }
    }

    private static int getChargeEndThreshold() {
        return clamp(SystemProperties.getInt(PROP_CHARGE_END, DEFAULT_CHARGE_END),
                MIN_CHARGE_END, MAX_CHARGE_END);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
