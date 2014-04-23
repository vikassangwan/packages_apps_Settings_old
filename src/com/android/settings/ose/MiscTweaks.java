/*
 * Copyright (C) 2014 The Dirty Unicorns Project
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
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.text.Editable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Slog;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.preference.SeekBarPreference;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.ose.AppMultiSelectListPreference;
import com.android.internal.util.ose.DeviceUtils;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Properties;

public class MiscTweaks extends SettingsPreferenceFragment implements OnPreferenceChangeListener {

    private static final String TAG = "MiscTweaks";

    private static final String DISABLE_FC_NOTIFICATIONS = "disable_fc_notifications";
    private static final String KEY_LCD_DENSITY = "lcd_density";
    private static final int DIALOG_CUSTOM_DENSITY = 101;
    private static final String DENSITY_PROP = "persist.sys.lcd_density";
    private static final String PREF_INCLUDE_APP_CIRCLE_BAR_KEY = "app_circle_bar_included_apps";

    private static Preference mLcdDensity;
    private static Activity mActivity;
    private static int mMaxDensity = DisplayMetrics.getDeviceDensity();
    private static int mDefaultDensity = getOSEDefaultDensity();
    private static int mMinDensity = getMinimumDensity();

    private AppMultiSelectListPreference mIncludedAppCircleBar;
    private CheckBoxPreference mDisableFC;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = getActivity();

        updateSettings();
    }

    private void updateSettings() {
        setPreferenceScreen(null);
        addPreferencesFromResource(R.xml.misc_tweaks);

        PreferenceScreen prefSet = getPreferenceScreen();
        final ContentResolver resolver = getActivity().getContentResolver();

        mDisableFC = (CheckBoxPreference) findPreference(DISABLE_FC_NOTIFICATIONS);
        mDisableFC.setChecked((Settings.System.getInt(resolver,
                Settings.System.DISABLE_FC_NOTIFICATIONS, 0) == 1));

        mIncludedAppCircleBar = (AppMultiSelectListPreference) prefSet.findPreference(PREF_INCLUDE_APP_CIRCLE_BAR_KEY);
        Set<String> includedApps = getIncludedApps();
        if (includedApps != null) mIncludedAppCircleBar.setValues(includedApps);
        mIncludedAppCircleBar.setOnPreferenceChangeListener(this);

        mLcdDensity = (Preference) findPreference(KEY_LCD_DENSITY);
        String current = SystemProperties.get(DENSITY_PROP,
                SystemProperties.get("ro.sf.lcd_density"));
        mLcdDensity.setSummary(getResources().getString(R.string.current_density) + current);
        mLcdDensity.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showDialogInner(DIALOG_CUSTOM_DENSITY);
                return true;
            }
        });
    }

    private static int getMinimumDensity() {
        int min = -1;
        int[] densities = { 91, 121, 161, 241, 321, 481 };
        for (int density : densities) {
            if (density < mMaxDensity) {
                min = density;
            }
        }
        return min;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if  (preference == mDisableFC) {
            boolean checked = ((CheckBoxPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.DISABLE_FC_NOTIFICATIONS, checked ? 1:0);
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private Set<String> getIncludedApps() {
        String included = Settings.System.getString(getActivity().getContentResolver(),
                Settings.System.WHITELIST_APP_CIRCLE_BAR);
        if (TextUtils.isEmpty(included)) {
            return null;
        }
        return new HashSet<String>(Arrays.asList(included.split("\\|")));
    }

    private void storeIncludedApps(Set<String> values) {
        StringBuilder builder = new StringBuilder();
        String delimiter = "";
        for (String value : values) {
            builder.append(delimiter);
            builder.append(value);
            delimiter = "|";
        }
        Settings.System.putString(getActivity().getContentResolver(),
                Settings.System.WHITELIST_APP_CIRCLE_BAR, builder.toString());
    }

    private static void setDensity(int density) {
        SystemProperties.set(DENSITY_PROP, Integer.toString(density));
        DisplayMetrics.setCurrentDensity(density);
        final IWindowManager windowManagerService = IWindowManager.Stub.asInterface(
                ServiceManager.getService(Context.WINDOW_SERVICE));
        try {
            windowManagerService.updateStatusBarNavBarHeight();
        } catch (RemoteException e) {
            Slog.w(TAG, "Failure communicating with window manager", e);
        }
        Configuration configuration = new Configuration();
        configuration.setToDefaults();
        configuration.densityDpi = density;
        try {
            ActivityManagerNative.getDefault().updateConfiguration(configuration);
        } catch (RemoteException e) {
            Slog.w(TAG, "Failure communicating with activity manager", e);
        }

        killRunningApps();
    }

    private static void killRunningApps() {
        ActivityManager am = (ActivityManager) mActivity.getSystemService(
                Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo pid : am.getRunningAppProcesses()) {
            am.killBackgroundProcesses(pid.processName);
        }
    }

    private void showDialogInner(int id) {
        DialogFragment newFragment = MyAlertDialogFragment.newInstance(id);
        newFragment.setTargetFragment(this, 0);
        newFragment.show(getFragmentManager(), "dialog " + id);
    }

    public static class MyAlertDialogFragment extends DialogFragment {

        public static MyAlertDialogFragment newInstance(int id) {
            MyAlertDialogFragment frag = new MyAlertDialogFragment();
            Bundle args = new Bundle();
            args.putInt("id", id);
            frag.setArguments(args);
            return frag;
        }

        MiscTweaks getOwner() {
            return (MiscTweaks) getTargetFragment();
        }

        private void setTextDPI(TextView tv, String dpi) {
            tv.setText(getResources().getString(R.string.new_density) + dpi);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            LayoutInflater factory = LayoutInflater.from(getActivity());
            int id = getArguments().getInt("id");
            switch (id) {
                case DIALOG_CUSTOM_DENSITY:
                    final View dialogView = factory.inflate(
                            R.layout.density_changer_dialog, null);
                    final TextView currentDPI = (TextView)
                            dialogView.findViewById(R.id.current_dpi);
                    final TextView newDPI = (TextView) dialogView.findViewById(R.id.new_dpi);
                    final SeekBar dpi = (SeekBar) dialogView.findViewById(R.id.dpi_edit);
                    String current = SystemProperties.get(DENSITY_PROP,
                            SystemProperties.get("ro.sf.lcd_density"));
                    setTextDPI(newDPI, current);
                    currentDPI.setText(getResources().getString(
                            R.string.current_density) + current);
                    dpi.setMax(mMaxDensity - mMinDensity);
                    dpi.setProgress(Integer.parseInt(current) - mMinDensity);
                    dpi.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar,
                                int progress, boolean fromUser) {
                            int value = progress + mMinDensity;
                            setTextDPI(newDPI, Integer.toString(value));
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                        }
                    });
                    ImageView setDefault = (ImageView) dialogView.findViewById(R.id.default_dpi);
                    setDefault.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (mDefaultDensity != -1) {
                                dpi.setProgress(mDefaultDensity - mMinDensity);
                                setTextDPI(newDPI, Integer.toString(mDefaultDensity));
                            }
                        }
                    });
                    return new AlertDialog.Builder(getActivity())
                            .setTitle(getResources().getString(R.string.set_custom_density_title))
                            .setView(dialogView)
                            .setPositiveButton(getResources().getString(
                                    R.string.set_custom_density_set),
                                    new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    dialog.dismiss();
                                    setDensity(dpi.getProgress() + mMinDensity);
                                }
                            })
                            .setNegativeButton(getResources().getString(R.string.cancel),
                                    new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    dialog.dismiss();
                                }
                            })
                            .create();
            }
            throw new IllegalArgumentException("unknown id " + id);
        }

        @Override
        public void onCancel(DialogInterface dialog) {
        }
    }

    public static int getOSEDefaultDensity() {
        Properties prop = new Properties();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream("/system/build.prop");
            prop.load(fis);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (Exception e) {
            }
        }
        return Integer.parseInt(prop.getProperty(DENSITY_PROP, "-1"));
    }
}