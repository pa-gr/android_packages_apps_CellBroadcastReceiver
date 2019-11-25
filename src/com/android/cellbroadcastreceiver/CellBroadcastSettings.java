/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.cellbroadcastreceiver;

import android.annotation.NonNull;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.MenuItem;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;

import java.util.HashMap;
import java.util.Map;

/**
 * Settings activity for the cell broadcast receiver.
 */
public class CellBroadcastSettings extends Activity {

    private static final String TAG = "CellBroadcastSettings";

    private static final boolean DBG = false;

    // Preference key for a master toggle to enable/disable all alerts message (default enabled).
    public static final String KEY_ENABLE_ALERTS_MASTER_TOGGLE = "enable_alerts_master_toggle";

    // Preference key for whether to enable public safety messages (default enabled).
    public static final String KEY_ENABLE_PUBLIC_SAFETY_MESSAGES = "enable_public_safety_messages";

    // Preference key for whether to enable emergency alerts (default enabled).
    public static final String KEY_ENABLE_EMERGENCY_ALERTS = "enable_emergency_alerts";

    // Enable vibration on alert (unless master volume is silent).
    public static final String KEY_ENABLE_ALERT_VIBRATE = "enable_alert_vibrate";

    // Speak contents of alert after playing the alert sound.
    public static final String KEY_ENABLE_ALERT_SPEECH = "enable_alert_speech";

    // Always play at full volume when playing the alert sound.
    public static final String KEY_USE_FULL_VOLUME = "use_full_volume";

    public static final String KEY_USE_FULL_VOLUME_SETTINGS_CHANGED =
            "use_full_volume_settings_changed";

    // Preference category for emergency alert and CMAS settings.
    public static final String KEY_CATEGORY_EMERGENCY_ALERTS = "category_emergency_alerts";

    // Preference category for alert preferences.
    public static final String KEY_CATEGORY_ALERT_PREFERENCES = "category_alert_preferences";

    // Show checkbox for Presidential alerts in settings
    // Whether to display CMAS presidential alert notifications (always enabled).
    public static final String KEY_ENABLE_CMAS_PRESIDENTIAL_ALERTS =
            "enable_cmas_presidential_alerts";

    // Whether to display CMAS extreme threat notifications (default is enabled).
    public static final String KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS =
            "enable_cmas_extreme_threat_alerts";

    // Whether to display CMAS severe threat notifications (default is enabled).
    public static final String KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS =
            "enable_cmas_severe_threat_alerts";

    // Whether to display CMAS amber alert messages (default is enabled).
    public static final String KEY_ENABLE_CMAS_AMBER_ALERTS = "enable_cmas_amber_alerts";

    // Preference category for development settings (enabled by settings developer options toggle).
    public static final String KEY_CATEGORY_DEV_SETTINGS = "category_dev_settings";

    // Whether to display monthly test messages (default is disabled).
    public static final String KEY_ENABLE_TEST_ALERTS = "enable_test_alerts";

    // Whether to display state/local test messages (default disabled).
    public static final String KEY_ENABLE_STATE_LOCAL_TEST_ALERTS =
            "enable_state_local_test_alerts";

    // Preference key for whether to enable area update information notifications
    // Enabled by default for phones sold in Brazil and India, otherwise this setting may be hidden.
    public static final String KEY_ENABLE_AREA_UPDATE_INFO_ALERTS =
            "enable_area_update_info_alerts";

    // Preference key for initial opt-in/opt-out dialog.
    public static final String KEY_SHOW_CMAS_OPT_OUT_DIALOG = "show_cmas_opt_out_dialog";

    // Alert reminder interval ("once" = single 2 minute reminder).
    public static final String KEY_ALERT_REMINDER_INTERVAL = "alert_reminder_interval";

    // Preference key for emergency alerts history
    public static final String KEY_EMERGENCY_ALERT_HISTORY = "emergency_alert_history";

    // For watch layout
    private static final String KEY_WATCH_ALERT_REMINDER = "watch_alert_reminder";

    // Resource cache
    private static final Map<Integer, Resources> sResourcesCache = new HashMap<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        if (userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_CELL_BROADCASTS)) {
            setContentView(R.layout.cell_broadcast_disallowed_preference_screen);
            return;
        }

        // We only add new CellBroadcastSettingsFragment if no fragment is restored.
        Fragment fragment = getFragmentManager().findFragmentById(android.R.id.content);
        if (fragment == null) {
            getFragmentManager().beginTransaction().add(android.R.id.content,
                    new CellBroadcastSettingsFragment()).commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * New fragment-style implementation of preferences.
     */
    public static class CellBroadcastSettingsFragment extends PreferenceFragment {

        private TwoStatePreference mExtremeCheckBox;
        private TwoStatePreference mSevereCheckBox;
        private TwoStatePreference mAmberCheckBox;
        private TwoStatePreference mMasterToggle;
        private TwoStatePreference mPublicSafetyMessagesChannelCheckBox;
        private TwoStatePreference mEmergencyAlertsCheckBox;
        private ListPreference mReminderInterval;
        private TwoStatePreference mSpeechCheckBox;
        private TwoStatePreference mFullVolumeCheckBox;
        private TwoStatePreference mAreaUpdateInfoCheckBox;
        private TwoStatePreference mTestCheckBox;
        private TwoStatePreference mStateLocalTestCheckBox;
        private Preference mAlertHistory;
        private PreferenceCategory mAlertCategory;
        private PreferenceCategory mAlertPreferencesCategory;
        private PreferenceCategory mDevSettingCategory;
        private boolean mDisableSevereWhenExtremeDisabled = true;

        // WATCH
        private TwoStatePreference mAlertReminder;

        // Show checkbox for Presidential alerts in settings
        private TwoStatePreference mPresidentialCheckBox;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

            // Load the preferences from an XML resource
            PackageManager pm = getActivity().getPackageManager();
            if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
                addPreferencesFromResource(R.xml.watch_preferences);
            } else {
                addPreferencesFromResource(R.xml.preferences);
            }

            PreferenceScreen preferenceScreen = getPreferenceScreen();

            mExtremeCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS);
            mSevereCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS);
            mAmberCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_CMAS_AMBER_ALERTS);
            mMasterToggle = (TwoStatePreference)
                    findPreference(KEY_ENABLE_ALERTS_MASTER_TOGGLE);
            mPublicSafetyMessagesChannelCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_PUBLIC_SAFETY_MESSAGES);
            mEmergencyAlertsCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_EMERGENCY_ALERTS);
            mReminderInterval = (ListPreference)
                    findPreference(KEY_ALERT_REMINDER_INTERVAL);
            mSpeechCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_ALERT_SPEECH);
            mFullVolumeCheckBox = (TwoStatePreference)
                    findPreference(KEY_USE_FULL_VOLUME);
            mAreaUpdateInfoCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_AREA_UPDATE_INFO_ALERTS);
            mTestCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_TEST_ALERTS);
            mStateLocalTestCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_STATE_LOCAL_TEST_ALERTS);
            mAlertHistory = findPreference(KEY_EMERGENCY_ALERT_HISTORY);
            mDevSettingCategory = (PreferenceCategory)
                    findPreference(KEY_CATEGORY_DEV_SETTINGS);

            // Show checkbox for Presidential alerts in settings
            mPresidentialCheckBox = (TwoStatePreference)
                    findPreference(KEY_ENABLE_CMAS_PRESIDENTIAL_ALERTS);

            if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
                mAlertReminder = (TwoStatePreference)
                        findPreference(KEY_WATCH_ALERT_REMINDER);
                if (Integer.valueOf(mReminderInterval.getValue()) == 0) {
                    mAlertReminder.setChecked(false);
                } else {
                    mAlertReminder.setChecked(true);
                }
                mAlertReminder.setOnPreferenceChangeListener((p, newVal) -> {
                    try {
                        mReminderInterval.setValueIndex((Boolean) newVal ? 1 : 3);
                    } catch (IndexOutOfBoundsException e) {
                        mReminderInterval.setValue(String.valueOf(0));
                        Log.w(TAG, "Setting default value");
                    }
                    return true;
                });
                PreferenceScreen watchScreen = (PreferenceScreen)
                        findPreference(KEY_CATEGORY_ALERT_PREFERENCES);
                watchScreen.removePreference(mReminderInterval);
            } else {
                mAlertPreferencesCategory = (PreferenceCategory)
                        findPreference(KEY_CATEGORY_ALERT_PREFERENCES);
                mAlertCategory = (PreferenceCategory)
                        findPreference(KEY_CATEGORY_EMERGENCY_ALERTS);
            }

            Resources res = CellBroadcastSettings.getResources(getContext(),
                    SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
            mDisableSevereWhenExtremeDisabled = res.getBoolean(
                    R.bool.disable_severe_when_extreme_disabled);

            // Handler for settings that require us to reconfigure enabled channels in radio
            Preference.OnPreferenceChangeListener startConfigServiceListener =
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference pref, Object newValue) {
                            CellBroadcastReceiver.startConfigService(pref.getContext());

                            if (mDisableSevereWhenExtremeDisabled) {
                                if (pref.getKey().equals(KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS)) {
                                    boolean isExtremeAlertChecked = (Boolean) newValue;
                                    if (mSevereCheckBox != null) {
                                        mSevereCheckBox.setEnabled(isExtremeAlertChecked);
                                        mSevereCheckBox.setChecked(false);
                                    }
                                }
                            }

                            if (pref.getKey().equals(KEY_ENABLE_ALERTS_MASTER_TOGGLE)) {
                                boolean isEnableAlerts = (Boolean) newValue;
                                setAlertsEnabled(isEnableAlerts);
                            }

                            // Notify backup manager a backup pass is needed.
                            new BackupManager(getContext()).dataChanged();
                            return true;
                        }
                    };

            // Show extra settings when developer options is enabled in settings.
            boolean enableDevSettings =
                    DevelopmentSettingsHelper.isDevelopmentSettingsEnabled(getContext());

            initReminderIntervalList();

            boolean emergencyAlertOnOffOptionEnabled = isFeatureEnabled(getContext(),
                    CarrierConfigManager.KEY_ALWAYS_SHOW_EMERGENCY_ALERT_ONOFF_BOOL, false);

            if (enableDevSettings || emergencyAlertOnOffOptionEnabled) {
                // enable/disable all alerts except CMAS presidential alerts.
                if (mMasterToggle != null) {
                    mMasterToggle.setOnPreferenceChangeListener(startConfigServiceListener);
                    // If allow alerts are disabled, we turn all sub-alerts off. If it's enabled, we
                    // leave them as they are.
                    if (!mMasterToggle.isChecked()) {
                        setAlertsEnabled(false);
                    }
                }
            } else {
                if (mMasterToggle != null) preferenceScreen.removePreference(mMasterToggle);
            }

            boolean hideTestAlertMenu = CellBroadcastSettings.isFeatureEnabled(getContext(),
                    CarrierConfigManager.KEY_CARRIER_FORCE_DISABLE_ETWS_CMAS_TEST_BOOL, false);

            CellBroadcastChannelManager channelManager = new CellBroadcastChannelManager(
                    getContext(), SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

            boolean isTestAlertsAvailable = !channelManager.getCellBroadcastChannelRanges(
                    R.array.required_monthly_test_range_strings).isEmpty()
                    || !channelManager.getCellBroadcastChannelRanges(
                            R.array.exercise_alert_range_strings).isEmpty()
                    || !channelManager.getCellBroadcastChannelRanges(
                            R.array.operator_defined_alert_range_strings).isEmpty()
                    || !channelManager.getCellBroadcastChannelRanges(
                            R.array.etws_test_alerts_range_strings).isEmpty();

            // Check if we want to hide the test alert toggle.
            if (hideTestAlertMenu || !enableDevSettings || !isTestAlertsAvailable) {
                if (mTestCheckBox != null) {
                    mAlertCategory.removePreference(mTestCheckBox);
                }
            }

            if (!enableDevSettings && !pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
                if (mDevSettingCategory != null) {
                    preferenceScreen.removePreference(mDevSettingCategory);
                }
            }

            // Remove preferences
            if (!res.getBoolean(R.bool.show_cmas_settings)) {
                // Remove CMAS preference items in emergency alert category.
                if (mAlertCategory != null) {
                    if (mExtremeCheckBox != null) mAlertCategory.removePreference(mExtremeCheckBox);
                    if (mSevereCheckBox != null) mAlertCategory.removePreference(mSevereCheckBox);
                    if (mAmberCheckBox != null) mAlertCategory.removePreference(mAmberCheckBox);
                }
            }

            if (!isAreaUpdateInfoSettingsEnabled(getContext())) {
                if (mAlertCategory != null) {
                    if (mAreaUpdateInfoCheckBox != null) {
                        mAlertCategory.removePreference(mAreaUpdateInfoCheckBox);
                    }
                }
            }

            // Remove preferences based on range configurations
            if (channelManager.getCellBroadcastChannelRanges(
                    R.array.cmas_amber_alerts_channels_range_strings).isEmpty()) {
                // Remove ambert alert
                if (mAlertCategory != null) {
                    if (mAmberCheckBox != null) {
                        mAlertCategory.removePreference(mAmberCheckBox);
                    }
                }
            }

            // Remove preferences based on range configurations
            if (channelManager.getCellBroadcastChannelRanges(
                    R.array.public_safety_messages_channels_range_strings).isEmpty() ||
                    !res.getBoolean(R.bool.show_public_safety_settings)) {
                // Remove public safety messages
                if (mAlertCategory != null) {
                    if (mPublicSafetyMessagesChannelCheckBox != null) {
                        mAlertCategory.removePreference(mPublicSafetyMessagesChannelCheckBox);
                    }
                }
            }

            if (channelManager.getCellBroadcastChannelRanges(
                    R.array.emergency_alerts_channels_range_strings).isEmpty()) {
                // Remove emergency alert messages
                if (mAlertCategory != null) {
                    if (mEmergencyAlertsCheckBox != null) {
                        mAlertCategory.removePreference(mEmergencyAlertsCheckBox);
                    }
                }
            }

            if (channelManager.getCellBroadcastChannelRanges(
                    R.array.state_local_test_alert_range_strings).isEmpty()) {
                // Remove state local test messages
                if (mAlertCategory != null) {
                    if (mStateLocalTestCheckBox != null) {
                        mAlertCategory.removePreference(mStateLocalTestCheckBox);
                    }
                }
            }

            if (mAreaUpdateInfoCheckBox != null) {
                mAreaUpdateInfoCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (mExtremeCheckBox != null) {
                mExtremeCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (mPublicSafetyMessagesChannelCheckBox != null) {
                mPublicSafetyMessagesChannelCheckBox.setOnPreferenceChangeListener(
                        startConfigServiceListener);
            }
            if (mEmergencyAlertsCheckBox != null) {
                mEmergencyAlertsCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (mSevereCheckBox != null) {
                mSevereCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
                if (mDisableSevereWhenExtremeDisabled) {
                    if (mExtremeCheckBox != null) {
                        mSevereCheckBox.setEnabled(mExtremeCheckBox.isChecked());
                    }
                }
            }
            if (mAmberCheckBox != null) {
                mAmberCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (mTestCheckBox != null) {
                mTestCheckBox.setOnPreferenceChangeListener(startConfigServiceListener);
            }
            if (mStateLocalTestCheckBox != null) {
                mStateLocalTestCheckBox.setOnPreferenceChangeListener(
                        startConfigServiceListener);
            }

            if (mFullVolumeCheckBox != null
                    && !sp.getBoolean(KEY_USE_FULL_VOLUME_SETTINGS_CHANGED, false)) {
                // If the user hasn't changed this settings yet, use the default settings from
                // resource overlay.
                mFullVolumeCheckBox.setChecked(res.getBoolean(R.bool.use_full_volume));
                mFullVolumeCheckBox.setOnPreferenceChangeListener(
                        (pref, newValue) -> {
                            sp.edit().putBoolean(KEY_USE_FULL_VOLUME_SETTINGS_CHANGED,
                                    true).apply();
                            return true;
                        });
            }

            if (mAlertHistory != null) {
                mAlertHistory.setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(final Preference preference) {
                                final Intent intent = new Intent(getContext(),
                                        CellBroadcastListActivity.class);
                                startActivity(intent);
                                return true;
                            }
                        });
            }

            // Show checkbox for Presidential alerts in settings
            if (!res.getBoolean(R.bool.show_presidential_alerts_in_settings)) {
                if (mAlertCategory != null) {
                    mAlertCategory.removePreference(mPresidentialCheckBox);
                }
            }
        }

        private void initReminderIntervalList() {
            Resources res = CellBroadcastSettings.getResources(
                    getContext(), SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

            String[] activeValues =
                    res.getStringArray(R.array.alert_reminder_interval_active_values);
            String[] allEntries = res.getStringArray(R.array.alert_reminder_interval_entries);
            String[] newEntries = new String[activeValues.length];

            // Only add active interval to the list
            for (int i = 0; i < activeValues.length; i++) {
                int index = mReminderInterval.findIndexOfValue(activeValues[i]);
                if (index != -1) {
                    newEntries[i] = allEntries[index];
                    if (DBG) Log.d(TAG, "Added " + allEntries[index]);
                } else {
                    Log.e(TAG, "Can't find " + activeValues[i]);
                }
            }

            mReminderInterval.setEntries(newEntries);
            mReminderInterval.setEntryValues(activeValues);
            mReminderInterval.setSummary(mReminderInterval.getEntry());
            mReminderInterval.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference pref, Object newValue) {
                            final ListPreference listPref = (ListPreference) pref;
                            final int idx = listPref.findIndexOfValue((String) newValue);
                            listPref.setSummary(listPref.getEntries()[idx]);
                            return true;
                        }
                    });
        }


        private void setAlertsEnabled(boolean alertsEnabled) {
            if (mSevereCheckBox != null) {
                mSevereCheckBox.setEnabled(alertsEnabled);
                mSevereCheckBox.setChecked(alertsEnabled);
            }
            if (mExtremeCheckBox != null) {
                mExtremeCheckBox.setEnabled(alertsEnabled);
                mExtremeCheckBox.setChecked(alertsEnabled);
            }
            if (mAmberCheckBox != null) {
                mAmberCheckBox.setEnabled(alertsEnabled);
                mAmberCheckBox.setChecked(alertsEnabled);
            }
            if (mAreaUpdateInfoCheckBox != null) {
                mAreaUpdateInfoCheckBox.setEnabled(alertsEnabled);
                mAreaUpdateInfoCheckBox.setChecked(alertsEnabled);
            }
            if (mAlertPreferencesCategory != null) {
                mAlertPreferencesCategory.setEnabled(alertsEnabled);
            }
            if (mDevSettingCategory != null) {
                mDevSettingCategory.setEnabled(alertsEnabled);
            }
            if (mEmergencyAlertsCheckBox != null) {
                mEmergencyAlertsCheckBox.setEnabled(alertsEnabled);
                mEmergencyAlertsCheckBox.setChecked(alertsEnabled);
            }
            if (mPublicSafetyMessagesChannelCheckBox != null) {
                mPublicSafetyMessagesChannelCheckBox.setEnabled(alertsEnabled);
                mPublicSafetyMessagesChannelCheckBox.setChecked(alertsEnabled);
            }
            if (mStateLocalTestCheckBox != null) {
                mStateLocalTestCheckBox.setEnabled(alertsEnabled);
                mStateLocalTestCheckBox.setChecked(alertsEnabled);
            }
        }
    }

     public static boolean isAreaUpdateInfoSettingsEnabled(Context context) {
         int subId = SubscriptionManager.getDefaultSmsSubscriptionId();
         if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
             subId = SubscriptionManager.getDefaultSubscriptionId();
         }
         Resources res = SubscriptionManager.getResourcesForSubId( context, subId);
         Log.d(TAG, "subId: " + subId);
         boolean value = res.getBoolean(R.bool.config_showAreaUpdateInfoSettings);
         Log.d(TAG, "value: " + value);
         return value;
     }


    public static boolean isFeatureEnabled(Context context, String feature, boolean defaultValue) {
        CarrierConfigManager configManager =
                (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);

        if (configManager != null) {
            PersistableBundle carrierConfig = configManager.getConfig();
            if (carrierConfig != null) {
                return carrierConfig.getBoolean(feature, defaultValue);
            }
        }

        return defaultValue;
    }

    /**
     * Get the device resource based on SIM
     *
     * @param context Context
     * @param subId Subscription index
     *
     * @return The resource
     */
    public static @NonNull Resources getResources(@NonNull Context context, int subId) {
        if (subId == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID
                || !SubscriptionManager.isValidSubscriptionId(subId)) {
            return context.getResources();
        }

        if (sResourcesCache.containsKey(subId)) {
            return sResourcesCache.get(subId);
        }

        Resources res = SubscriptionManager.getResourcesForSubId(context, subId);
        sResourcesCache.put(subId, res);

        return res;
    }
}
