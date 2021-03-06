/*
 * This file is part of BikeTrack application.
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
package com.android.biketrack.ui.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v14.preference.MultiSelectListPreference;
import android.support.v4.app.DialogFragment;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;

import com.android.biketrack.Constants;
import com.android.biketrack.R;
import com.android.biketrack.ui.preferences.NumberPickerPreference;
import com.android.biketrack.ui.preferences.NumberPickerPreferenceFragmentCompat;

import java.util.Arrays;
import java.util.Map;

/**
 * Fragment to show the application settings.
 *
 * Library support needed: com.android.support:preference-v7 and com.android.support:preference-v14.
 *
 * @author Gionata Boccalini
 */
public class SettingsFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String FRAG_TAG_NUMBER_PICKER = "FRAG_TAG_NUMBER_PICKER";
    private SharedPreferences mSharedPreferences;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreatePreferences(Bundle bundle, String s) {

        // Define the settings file to use by this settings fragment
        getPreferenceManager().setSharedPreferencesName(Constants.SETTINGS_FILE_NAME);

        // Load the Preferences from the XML file.
        addPreferencesFromResource(R.xml.fragment_settings);
    }

    @Override
    public void onResume() {
        super.onResume();

        // We want to watch the preference values changes.
        mSharedPreferences = getPreferenceManager().getSharedPreferences();
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);

        // Iterate through the preference entries and update their summary.
        Map<String, ?> preferencesMap = mSharedPreferences.getAll();
        for (String preferenceKey : preferencesMap.keySet()) {
            setSummary(getPreferenceScreen().findPreference(preferenceKey));
        }
    }

    @Override
    public void onPause() {
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    private void setSummary(Preference pref) {
        if (pref instanceof EditTextPreference) {
            pref.setSummary(((EditTextPreference) pref).getText());
        } else if (pref instanceof ListPreference) {
            pref.setSummary(((ListPreference) pref).getValue());
        } else if (pref instanceof MultiSelectListPreference) {
            pref.setSummary(Arrays.toString(((MultiSelectListPreference) pref).getValues().toArray()));
        } else if (pref instanceof NumberPickerPreference) {
            ((NumberPickerPreference) pref).setTitleValue();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = getPreferenceScreen().findPreference(key);
        setSummary(pref);
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        // Try if the preference is one of our custom Preferences
        DialogFragment dialogFragment = null;
        if (preference instanceof NumberPickerPreference) {
            // Create a new instance of NumberPickerPreferenceFragmentCompat with the key of the related
            // preference as argument
            dialogFragment = NumberPickerPreferenceFragmentCompat.newInstance(preference.getKey());
        }

        if (dialogFragment != null) {
            // If it was one of our custom Preferences, show its dialog
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(this.getFragmentManager(),FRAG_TAG_NUMBER_PICKER);
        }
        else {
            // Could not be handled here. Try with the super method.
            super.onDisplayPreferenceDialog(preference);
        }
    }
}
