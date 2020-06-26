package com.leith.gaenlogger;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.TwoStatePreference;

import java.util.Map;

//import android.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final boolean DEBUGGING = false;  // generate extra debug output ?

    /******************************************************************************/
    // event handlers

    @SuppressLint("ResourceType")
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.layout.settings_frag, rootKey);

        if (getActivity() == null) {
            Log.e("DL","In SettingsFragment getActivity() is null!");
            return;
        }
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        // add listener which updates summaries of displayed prefs when changed
        sharedPref.registerOnSharedPreferenceChangeListener(configListener);
        // first time around we call the listener manually (to initialise summaries, since they haven't changed)
        for (Map.Entry<String, ?> entry : sharedPref.getAll().entrySet()) {
            if (entry==null) continue;
            Debug.println("key:"+entry.getKey());
            configListener.onSharedPreferenceChanged(sharedPref, entry.getKey());
        }
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener configListener
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Debug.println("onSharedPreferenceChanged(): "+key);
            Preference preference = findPreference(key);
            if (preference==null) return;
            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                String value = sharedPreferences.getString(preference.getKey(), "");
                int index = listPreference.findIndexOfValue(value);

                // Set the summary to reflect the new value.
                preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
            } else if (preference instanceof TwoStatePreference) {
                // its a switch with boolean value
                boolean value = sharedPreferences.getBoolean(preference.getKey(), true);
                TwoStatePreference switchpreference = (TwoStatePreference) preference;
                switchpreference.setChecked(value);
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                String value = sharedPreferences.getString(preference.getKey(), "");
                preference.setSummary(value);
            }
        }
    };

    /***************************************************************************************/
    // debugging
    private static class Debug {
        static void println(String s) {
            if (DEBUGGING) System.out.println("SettingsFrag: "+s);
        }
    }
}
