package com.android.biketrack.ui.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v14.preference.MultiSelectListPreference;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;

import com.android.biketrack.R;

import java.util.Arrays;
import java.util.Map;

/**
 * Fragment to show the application settings.
 *
 * Library support needed: com.android.support:preference-v7 and com.android.support:preference-v14.
 */
public class SettingsFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private SharedPreferences mSharedPreferences;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
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
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = getPreferenceScreen().findPreference(key);
        setSummary(pref);
    }
}
