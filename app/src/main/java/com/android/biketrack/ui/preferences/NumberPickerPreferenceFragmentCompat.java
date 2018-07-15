package com.android.biketrack.ui.preferences;

import android.os.Bundle;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.view.View;
import android.widget.NumberPicker;

import com.android.biketrack.R;

/**
 * A {@link android.support.v7.preference.PreferenceDialogFragmentCompat} that displays a custom dialog
 * to pick a number in a range of values.
 *
 * Library support needed: com.android.support:preference-v7
 */
public class NumberPickerPreferenceFragmentCompat extends PreferenceDialogFragmentCompat {

    private NumberPicker mNumberPicker;

    public static NumberPickerPreferenceFragmentCompat newInstance(
            String key) {
        final NumberPickerPreferenceFragmentCompat fragment = new NumberPickerPreferenceFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);

        return fragment;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mNumberPicker = view.findViewById(R.id.edit);

        // Exception when there is no mNumberPicker
        if (mNumberPicker == null) {
            throw new IllegalStateException("Dialog view must contain a view with id 'edit'");
        }

        // Get the value from the related Preference
        Integer value;
        DialogPreference preference = getPreference();
        if (preference instanceof NumberPickerPreference) {
            NumberPickerPreference p = (NumberPickerPreference) preference;
            mNumberPicker.setMaxValue(p.getMaxValue());
            mNumberPicker.setMinValue(p.getMinValue());
            mNumberPicker.setWrapSelectorWheel(p.isWrapSelectorWheel());
            mNumberPicker.setValue(p.getValue());
        }
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // Value to save
            int value = mNumberPicker.getValue();

            // Get the related Preference and save the value
            DialogPreference preference = getPreference();
            if (preference instanceof NumberPickerPreference) {
                NumberPickerPreference timePreference = ((NumberPickerPreference) preference);
                // This allows the client to ignore the user value.
                if (timePreference.callChangeListener(value)) {
                    // Save the value
                    timePreference.setValue(value);
                }
            }
        }
    }
}


