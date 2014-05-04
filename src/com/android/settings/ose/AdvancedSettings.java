/*
 * Copyright (C) 2014 OSE Project
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

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.Settings;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import java.util.List;

import android.app.AlertDialog;
import android.content.res.Resources;
import android.content.DialogInterface;
import android.view.WindowManagerGlobal;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.preference.PreferenceActivity;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceCategory;
import android.text.Spannable;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.widget.EditText;
import com.android.settings.Utils;



import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.text.Editable;
import android.util.Slog;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.preference.SeekBarPreference;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.ose.AppMultiSelectListPreference;
import com.android.internal.util.ose.DeviceUtils;

import java.lang.Thread;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;






public class AdvancedSettings extends SettingsPreferenceFragment
        implements OnPreferenceChangeListener {

    private static final String PREF_MEDIA_SCANNER_ON_BOOT = "media_scanner_on_boot";
    private static final String PREF_DEVICESETTINGS_APP = "devicesettings_app";
    private static final String PREF_DISABLE_FC_NOTIFICATIONS = "disable_fc_notifications";

    private PreferenceScreen mDeviceSettingsApp;
    private ListPreference mMsob;
    CheckBoxPreference mDisableFC;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.ose_advanced_settings);
        PreferenceScreen prefScreen = getPreferenceScreen();

        mMsob = (ListPreference) findPreference(PREF_MEDIA_SCANNER_ON_BOOT);
        mMsob.setValue(String.valueOf(Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.MEDIA_SCANNER_ON_BOOT, 0)));
        mMsob.setSummary(mMsob.getEntry());
        mMsob.setOnPreferenceChangeListener(this);
        
        mDisableFC = (CheckBoxPreference) findPreference(PREF_DISABLE_FC_NOTIFICATIONS);
        mDisableFC.setChecked(Settings.System.getBoolean(mContext.getContentResolver(),
                Settings.System.DISABLE_FC_NOTIFICATIONS, true));

        mDeviceSettingsApp = (PreferenceScreen) findPreference(PREF_DEVICESETTINGS_APP);

        if (!deviceSettingsAppExists()) {
            getPreferenceScreen().removePreference(mDeviceSettingsApp);
        }

    }

    private boolean deviceSettingsAppExists() {
        Intent intent = mDeviceSettingsApp.getIntent();
        if (intent != null) {
            PackageManager pm = getActivity().getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA);
            int listSize = list.size();
            return (listSize > 0) ? true : false;

        }
        return false;

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String value = (String) newValue;
        if (preference == mMsob) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.MEDIA_SCANNER_ON_BOOT,
                    Integer.valueOf(value));

            mMsob.setValue(String.valueOf(value));
            mMsob.setSummary(mMsob.getEntry());
            return true;
        }
        return false;
    }
    
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mDisableFC) {
            Settings.System.putBoolean(mContext.getContentResolver(),
                    Settings.System.DISABLE_FC_NOTIFICATIONS,
                    ((CheckBoxPreference) preference).isChecked());
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

}
