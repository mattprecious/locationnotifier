/*
 * Copyright 2011 Matthew Precious
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mattprecious.locnotifier;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.RingtonePreference;

public class LocationNotifier extends PreferenceActivity {

    private static LocationNotifier instance;

    private SharedPreferences preferences;
    private OnSharedPreferenceChangeListener prefListener;

    private PreferenceCategory statusCategory;

    private Preference destinationPreference;
    private Preference goPreference;
    private Preference stopPreference;

    private RingtonePreference tonePreference;
    private CheckBoxPreference vibratePreference;
    private CheckBoxPreference insistentPreference;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.main);

        preferences             = ((PreferenceScreen) findPreference("preferences")).getSharedPreferences();

        statusCategory          = (PreferenceCategory) findPreference("category_status");
        destinationPreference   = (Preference) findPreference("set_destination");
        goPreference            = (Preference) findPreference("go");
        stopPreference          = (Preference) findPreference("stop");

        tonePreference          = (RingtonePreference) findPreference("tone");
        vibratePreference       = (CheckBoxPreference) findPreference("vibrate");
        insistentPreference     = (CheckBoxPreference) findPreference("insistent");

        destinationPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference arg0) {
                setDestination();

                return false;
            }
        });

        goPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference arg0) {
                startService(new Intent(getApplicationContext(), LocationService.class));
                updateStartGo(true);

                return false;
            }
        });

        stopPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference arg0) {
                stopService(new Intent(getApplicationContext(), LocationService.class));
                updateStartGo(false);

                return false;
            }
        });
        
        // register a listener for changes
        prefListener = new OnSharedPreferenceChangeListener() {

            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals("tone")) {
                    updateTone();
                }
            }
        };
        
        preferences.registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    protected void onResume() {
        super.onResume();

        instance = this;
        updateStartGo();
        updateTone();
    }

    private void setDestination() {
        startActivity(new Intent(this, ShowMap.class));
    }

    private void updateStartGo() {
        updateStartGo(LocationService.isRunning());
    }

    private void updateStartGo(boolean running) {
        if (running) {
            statusCategory.removePreference(goPreference);
            statusCategory.addPreference(stopPreference);
        } else {
            statusCategory.removePreference(stopPreference);
            statusCategory.addPreference(goPreference);

            if (!preferences.contains("dest_lat") || !preferences.contains("dest_radius")) {
                goPreference.setEnabled(false);
            } else {
                goPreference.setEnabled(true);
            }
        }
    }

    public static void sUpdateStartGo() {
        if (instance != null) {
            instance.updateStartGo();
        }
    }
    
    /**
     * Show the chosen alarm under the preference title
     */
    private void updateTone() {
        String tone = preferences.getString("tone", null);
        
        String title = "";
        
        if (tone != null && tone.equals("")) {
            title = getString(R.string.silent);
        } else {
            Uri uri = (tone == null) ? 
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) :
                    Uri.parse(tone);
            
            Ringtone ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
            
            if (ringtone != null) {
                title = ringtone.getTitle(getApplicationContext());
            }
        }
        
        tonePreference.setSummary(title);
    }
}