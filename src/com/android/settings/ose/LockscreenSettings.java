/*
 * Copyright (C) 2013 The OSE Project
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

package com.android.settings.ose;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.util.ose.DeviceUtils;

public class LockscreenSettings extends SettingsPreferenceFragment
        implements OnPreferenceChangeListener {

    private static final String TAG = "LockscreenSettings";

    private static final String KEY_INTERFACE_SETTINGS = "lock_screen_interface";
    private static final String KEY_TARGET_SETTINGS = "lock_screen_targets";
    private static final String KEY_WIDGETS_SETTINGS = "lock_screen_widgets";
    private static final String KEY_GENERAL_CATEGORY = "general_category";
    private static final String KEY_BATTERY_AROUND_RING = "battery_around_ring";
    private static final String KEY_ALWAYS_BATTERY_PREF = "lockscreen_battery_status";
    private static final String KEY_LOCKSCREEN_ROTATION = "lockscreen_rotation";
    private static final String KEY_LOCK_BEFORE_UNLOCK = "lock_before_unlock";
    private static final String KEY_QUICK_UNLOCK_CONTROL = "quick_unlock_control";
    private static final String KEY_MENU_UNLOCK_PREF = "menu_unlock";
    private static final String KEY_SHAKE_TO_SECURE = "shake_to_secure";
    private static final String KEY_SHAKE_AUTO_TIMEOUT = "shake_auto_timeout";
    private static final String KEY_PEEK = "notification_peek";
    private static final String KEY_PEEK_PICKUP_TIMEOUT = "peek_pickup_timeout";

    private PackageManager mPM;
    private DevicePolicyManager mDPM;
    private Preference mLockscreenWidgets;

    private CheckBoxPreference mLockRingBattery;
    private CheckBoxPreference mBatteryStatus;
    private ListPreference mLockscreenRotation;
    private CheckBoxPreference mLockBeforeUnlock;
    private CheckBoxPreference mLockQuickUnlock;
    private CheckBoxPreference mMenuUnlock;
    private CheckBoxPreference mShakeToSecure;
    private ListPreference mShakeTimer;
    private CheckBoxPreference mNotificationPeek;
    private ListPreference mPeekPickupTimeout;

    // needed for menu unlock
    private Resources keyguardResource;
    private boolean mMenuUnlockDefault;
    private static final int KEY_MASK_MENU = 0x04;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.ose_lockscreen_settings);

        PreferenceScreen prefs = getPreferenceScreen();

        mLockRingBattery = (CheckBoxPreference) prefs
                .findPreference(KEY_BATTERY_AROUND_RING);
        if (mLockRingBattery != null) {
            mLockRingBattery.setChecked(Settings.System.getInt(getContentResolver(),
                    Settings.System.BATTERY_AROUND_LOCKSCREEN_RING, 0) == 1);
        }

        mLockBeforeUnlock = (CheckBoxPreference) prefs
                .findPreference(KEY_LOCK_BEFORE_UNLOCK);
        if (mLockBeforeUnlock != null) {
            mLockBeforeUnlock.setChecked(Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.LOCK_BEFORE_UNLOCK, 0) == 1);
            mLockBeforeUnlock.setOnPreferenceChangeListener(this);
        }

        mShakeToSecure = (CheckBoxPreference) prefs
                .findPreference(KEY_SHAKE_TO_SECURE);
        if (mShakeToSecure != null) {
            mShakeToSecure.setChecked(Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.LOCK_SHAKE_TEMP_SECURE, 0) == 1);
            mShakeToSecure.setOnPreferenceChangeListener(this);
        }

        mShakeTimer = (ListPreference) prefs
                .findPreference(KEY_SHAKE_AUTO_TIMEOUT);
        if (mShakeTimer != null) {
            long shakeTimer = Settings.Secure.getLongForUser(getContentResolver(),
                    Settings.Secure.LOCK_SHAKE_SECURE_TIMER, 0,
                    UserHandle.USER_CURRENT);
            mShakeTimer.setValue(String.valueOf(shakeTimer));
            updateShakeTimerPreferenceSummary();
            mShakeTimer.setOnPreferenceChangeListener(this);
        }

        mLockQuickUnlock = (CheckBoxPreference) prefs
                .findPreference(KEY_QUICK_UNLOCK_CONTROL);
        if (mLockQuickUnlock != null) {
            mLockQuickUnlock.setChecked(Settings.System.getInt(getContentResolver(),
                    Settings.System.LOCKSCREEN_QUICK_UNLOCK_CONTROL, 0) == 1);
        }

        mPM = getActivity().getPackageManager();
        Resources keyguardResources = null;
        try {
            keyguardResources = mPM.getResourcesForApplication("com.android.keyguard");
        } catch (Exception e) {
            e.printStackTrace();
        }
        mMenuUnlockDefault = keyguardResources != null
            ? keyguardResources.getBoolean(keyguardResources.getIdentifier(
            "com.android.keyguard:bool/config_disableMenuKeyInLockScreen", null, null)) : false;

        mBatteryStatus = (CheckBoxPreference) prefs
                .findPreference(KEY_ALWAYS_BATTERY_PREF);
        if (mBatteryStatus != null) {
            mBatteryStatus.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.LOCKSCREEN_ALWAYS_SHOW_BATTERY, 0) == 1);
            mBatteryStatus.setOnPreferenceChangeListener(this);
        }

        mLockscreenRotation = (ListPreference) prefs
                .findPreference(KEY_LOCKSCREEN_ROTATION);
        if (mLockscreenRotation != null) {
            boolean defaultVal = !DeviceUtils.isPhone(getActivity());
            int userVal = Settings.System.getIntForUser(getContentResolver(),
                    Settings.System.LOCKSCREEN_ROTATION_ENABLED, defaultVal ? 1 : 0,
                    UserHandle.USER_CURRENT);
            mLockscreenRotation.setValue(String.valueOf(userVal));
            if (userVal == 0) {
                mLockscreenRotation.setSummary(mLockscreenRotation.getEntry());
            } else {
                mLockscreenRotation.setSummary(mLockscreenRotation.getEntry()
                        + " " + getResources().getString(
                        R.string.lockscreen_rotation_summary_extra));
            }
            mLockscreenRotation.setOnPreferenceChangeListener(this);
        }

        PreferenceCategory generalCategory = (PreferenceCategory) prefs
                .findPreference(KEY_GENERAL_CATEGORY);

        if (generalCategory != null) {
            Preference lockInterfacePref = findPreference(KEY_INTERFACE_SETTINGS);
            Preference lockTargetsPref = findPreference(KEY_TARGET_SETTINGS);
            if (lockInterfacePref != null && lockTargetsPref != null) {
                if (!DeviceUtils.isPhone(getActivity())) {
                     generalCategory.removePreference(lockInterfacePref);
                } else {
                     generalCategory.removePreference(lockTargetsPref);
                }
            }
        }

        mLockscreenWidgets = prefs.findPreference(KEY_WIDGETS_SETTINGS);
        mDPM = (DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);

        if (mLockscreenWidgets != null) {
            if (ActivityManager.isLowRamDeviceStatic()) {
                if (generalCategory != null) {
                    generalCategory.removePreference(prefs
                            .findPreference(KEY_WIDGETS_SETTINGS));
                    mLockscreenWidgets = null;
                }
            } else {
                final boolean disabled = (0 != (mDPM.getKeyguardDisabledFeatures(null)
                        & DevicePolicyManager.KEYGUARD_DISABLE_WIDGETS_ALL));
                mLockscreenWidgets.setEnabled(!disabled);
            }
        }

        mMenuUnlock = (CheckBoxPreference) prefs.findPreference(KEY_MENU_UNLOCK_PREF);
        if (mMenuUnlock != null) {
            int deviceKeys = getResources().getInteger(
                    com.android.internal.R.integer.config_deviceHardwareKeys);
            boolean hasMenuKey = (deviceKeys & KEY_MASK_MENU) != 0;
            if (hasMenuKey) {
                boolean settingsEnabled = Settings.System.getIntForUser(
                        getContentResolver(),
                        Settings.System.MENU_UNLOCK_SCREEN, mMenuUnlockDefault ? 0 : 1,
                        UserHandle.USER_CURRENT) == 1;
                mMenuUnlock.setChecked(settingsEnabled);
                mMenuUnlock.setOnPreferenceChangeListener(this);
            }
        }

        mNotificationPeek = (CheckBoxPreference) findPreference(KEY_PEEK);
        mNotificationPeek.setPersistent(false);

        updatePeekCheckbox();

        mPeekPickupTimeout = (ListPreference) prefs.findPreference(KEY_PEEK_PICKUP_TIMEOUT);
        int peekTimeout = Settings.System.getIntForUser(getContentResolver(),
                Settings.System.PEEK_PICKUP_TIMEOUT, 0, UserHandle.USER_CURRENT);
        mPeekPickupTimeout.setValue(String.valueOf(peekTimeout));
        mPeekPickupTimeout.setSummary(mPeekPickupTimeout.getEntry());
        mPeekPickupTimeout.setOnPreferenceChangeListener(this);

    }

    private void updateShakeTimerPreferenceSummary() {
        // Update summary message with current value
        long shakeTimer = Settings.Secure.getLongForUser(getContentResolver(),
                Settings.Secure.LOCK_SHAKE_SECURE_TIMER, 0,
                UserHandle.USER_CURRENT);
        final CharSequence[] entries = mShakeTimer.getEntries();
        final CharSequence[] values = mShakeTimer.getEntryValues();
        int best = 0;
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.valueOf(values[i].toString());
            if (shakeTimer >= timeout) {
                best = i;
            }
        }
        mShakeTimer.setSummary(entries[best]);
    }

    private void updatePeekCheckbox() {
        boolean enabled = Settings.System.getInt(getContentResolver(),
                Settings.System.PEEK_STATE, 0) == 1;
        mNotificationPeek.setChecked(enabled);
    }

    private void updatePeekTimeoutOptions(Object newValue) {
        int index = mPeekPickupTimeout.findIndexOfValue((String) newValue);
        int value = Integer.valueOf((String) newValue);
        Settings.Secure.putInt(getActivity().getContentResolver(),
                Settings.System.PEEK_PICKUP_TIMEOUT, value);
        mPeekPickupTimeout.setSummary(mPeekPickupTimeout.getEntries()[index]);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        if (preference == mLockBeforeUnlock) {
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.LOCK_BEFORE_UNLOCK,
                    ((Boolean) value) ? 1 : 0);
        } else if (preference == mShakeToSecure) {
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.LOCK_SHAKE_TEMP_SECURE, 0);
        } else if (preference == mShakeTimer) {
            int shakeTime = Integer.parseInt((String) value);
            try {
                Settings.Secure.putInt(getContentResolver(),
                        Settings.Secure.LOCK_SHAKE_SECURE_TIMER, shakeTime);
            } catch (NumberFormatException e) {
                Log.e("LockscreenSettings", "could not persist lockAfter timeout setting", e);
            }
            updateShakeTimerPreferenceSummary();
        } else if (preference == mBatteryStatus) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.LOCKSCREEN_ALWAYS_SHOW_BATTERY,
                    ((Boolean) value) ? 1 : 0);
        } else if (preference == mLockscreenRotation) {
            int userVal = Integer.valueOf((String) value);
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.LOCKSCREEN_ROTATION_ENABLED,
                    userVal, UserHandle.USER_CURRENT);
            mLockscreenRotation.setValue(String.valueOf(value));
            if (userVal == 0) {
                mLockscreenRotation.setSummary(mLockscreenRotation.getEntry());
            } else {
                mLockscreenRotation.setSummary(mLockscreenRotation.getEntry()
                        + " " + getResources().getString(
                        R.string.lockscreen_rotation_summary_extra));
            }
        } else if (preference == mMenuUnlock) {
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.MENU_UNLOCK_SCREEN,
                    ((Boolean) value) ? 1 : 0, UserHandle.USER_CURRENT);
        } else if (preference == mPeekPickupTimeout) {
            int peekTimeout = Integer.valueOf((String) value);
            Settings.System.putIntForUser(getContentResolver(),
                Settings.System.PEEK_PICKUP_TIMEOUT,
                    peekTimeout, UserHandle.USER_CURRENT);
            updatePeekTimeoutOptions(value);
        }
        return true;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mLockQuickUnlock) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.LOCKSCREEN_QUICK_UNLOCK_CONTROL,
                    mLockQuickUnlock.isChecked() ? 1 : 0);
            return true;
        } else if (preference == mLockRingBattery) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.BATTERY_AROUND_LOCKSCREEN_RING,
                    mLockRingBattery.isChecked() ? 1 : 0);
            return true;
        } else if (preference == mNotificationPeek) {
            Settings.System.putInt(getContentResolver(), Settings.System.PEEK_STATE,
                    mNotificationPeek.isChecked() ? 1 : 0);
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
}
