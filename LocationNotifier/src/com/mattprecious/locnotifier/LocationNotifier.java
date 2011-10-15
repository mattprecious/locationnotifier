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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.RingtonePreference;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.PhoneLookup;

public class LocationNotifier extends PreferenceActivity {
    
    private final int VERSION_CODE = 3;

    private static LocationNotifier instance;

    // static variables used to store information for dialogs
    private static String[] phoneNumbers;
    private static String[] phoneLabels;
    private static String contactName;

    private SharedPreferences preferences;
    private OnSharedPreferenceChangeListener prefListener;

    private PreferenceCategory statusCategory;

    private Preference destinationPreference;
    private Preference goPreference;
    private Preference stopPreference;

    private RingtonePreference tonePreference;
    private CheckBoxPreference vibratePreference;
    private CheckBoxPreference insistentPreference;

    private CheckBoxPreference smsActivePreference;
    private Preference smsContactPreference;
    private EditTextPreference smsMessagePreference;

    private final int REQUEST_CODE_CONTACT_PICKER = 1;

    private final int DIALOG_ID_PHONE_PICKER = 1;
    private final int DIALOG_ID_PHONE_PICKER_NO_NUMBERS = 2;
    private final int DIALOG_ID_CHANGE_LOG = 3;

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

        smsActivePreference     = (CheckBoxPreference) findPreference("sms_enabled");
        smsContactPreference    = (Preference) findPreference("sms_contact");
        smsMessagePreference    = (EditTextPreference) findPreference("sms_message");

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

        smsContactPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference arg0) {
                Intent contactPickerIntent = new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI);
                startActivityForResult(contactPickerIntent, REQUEST_CODE_CONTACT_PICKER);
                return false;
            }
        });

        smsMessagePreference.getEditText().setLines(2);

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
        
        // debug the change log
        //preferences.edit().putInt("version_code", 0).commit();
        
        checkAndShowChangeLog();
    }

    @Override
    protected void onResume() {
        super.onResume();

        instance = this;
        updateStartGo();
        updateSMSContact();
        updateTone();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_CONTACT_PICKER:
                    Uri contactUri = data.getData();

                    String contactId = contactUri.getLastPathSegment();

                    String[] columns = new String[] { StructuredName.DISPLAY_NAME, StructuredName.GIVEN_NAME };

                    String selection = Data.MIMETYPE + "=? AND " + StructuredName.CONTACT_ID + "=?";
                    String[] selectionArgs = new String[] { StructuredName.CONTENT_ITEM_TYPE, contactId, };

                    Cursor c = getContentResolver().query(Data.CONTENT_URI, columns, selection, selectionArgs, null);

                    contactName = null;
                    if (c.moveToFirst()) {
                        contactName = c.getString(c.getColumnIndex(StructuredName.GIVEN_NAME));
                        if (contactName == null) {
                            contactName = c.getString(c.getColumnIndex(StructuredName.DISPLAY_NAME));
                        }
                    }

                    c.close();

                    columns = new String[] { Phone.NUMBER, Phone.TYPE, Phone.LABEL };
                    c = getContentResolver().query(Phone.CONTENT_URI, columns, Phone.CONTACT_ID + "=?", new String[] { contactId }, null);

                    if (c.getCount() == 1 && c.moveToFirst()) {
                        Editor editor = preferences.edit();
                        editor.putString("sms_contact", c.getString(c.getColumnIndex(Phone.NUMBER)));
                        editor.commit();
                    } else if (c.getCount() > 1) {
                        phoneNumbers = new String[c.getCount()];
                        phoneLabels = new String[c.getCount()];

                        while (c.moveToNext()) {
                            String phoneNumber = c.getString(c.getColumnIndex(Phone.NUMBER));
                            int phoneType = c.getInt(c.getColumnIndex(Phone.TYPE));

                            String phoneLabel = "";
                            if (phoneType == Phone.TYPE_CUSTOM) {
                                phoneLabel = c.getString(c.getColumnIndex(Phone.LABEL));
                            } else {
                                phoneLabel = (String) Phone.getTypeLabel(getResources(), phoneType, "");
                            }

                            phoneNumbers[c.getPosition()] = phoneNumber;
                            phoneLabels[c.getPosition()] = "(" + phoneLabel + ") " + phoneNumber;
                        }

                        removeDialog(DIALOG_ID_PHONE_PICKER);
                        showDialog(DIALOG_ID_PHONE_PICKER);
                    } else {
                        removeDialog(DIALOG_ID_PHONE_PICKER_NO_NUMBERS);
                        showDialog(DIALOG_ID_PHONE_PICKER_NO_NUMBERS);
                    }

                    c.close();

                    return;
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        Dialog dialog;

        String contactNameUpper = getString(R.string.default_contact_upper);
        String contactNameLower = getString(R.string.default_contact_lower);

        if (contactName != null) {
            contactNameUpper = contactNameLower = contactName;
        }
        
        String contactNamePossessive;
        
        // if need be in the future, break this condition out into a 
        // language-aware helper function
        if (contactNameUpper.charAt(contactNameUpper.length() - 1) == 's') {
            contactNamePossessive = getString(R.string.possessive_name_s, contactNameUpper);
        } else {
            contactNamePossessive = getString(R.string.possessive_name, contactNameUpper);
        }

        switch (id) {
            case DIALOG_ID_CHANGE_LOG:
                builder.setTitle(R.string.whats_new)
                       .setIcon(android.R.drawable.ic_dialog_info)
                       .setMessage(R.string.change_log)
                       .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                           
                           public void onClick(DialogInterface dialog, int id) {
                               dialog.cancel();
                           }
                       })
                       ;
                dialog = builder.create();
                break;
            case DIALOG_ID_PHONE_PICKER:
                builder.setTitle(getString(R.string.sms_number_pick_title, contactNamePossessive));
                builder.setItems(phoneLabels, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        Editor editor = preferences.edit();
                        editor.putString("sms_contact", phoneNumbers[item]);
                        editor.commit();

                        updateSMSContact();
                    }
                });
                dialog = builder.create();
                break;
            case DIALOG_ID_PHONE_PICKER_NO_NUMBERS:
                builder.setTitle(getString(R.string.sms_no_numbers_title, contactNameUpper));
                builder.setMessage(getString(R.string.sms_no_numbers_message, contactNameUpper, contactNameLower));
                builder.setPositiveButton(R.string.ok, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

                dialog = builder.create();
                break;
            default:
                dialog = null;
        }
        return dialog;
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

    private void updateSMSContact() {
        String number = preferences.getString("sms_contact", null);

        String summary = null;
        if (number != null) {
            Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));

            String[] columns = new String[] { PhoneLookup.DISPLAY_NAME };
            Cursor c = getContentResolver().query(uri, columns, null, null, null);

            if (c.moveToFirst()) {
                summary = c.getString(c.getColumnIndex(PhoneLookup.DISPLAY_NAME));
            }
            
            c.close();
        }
        
        if (summary == null) {
            summary = number;
        }

        smsContactPreference.setSummary(summary);
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
    
    private void checkAndShowChangeLog() {
        if (preferences.getInt("version_code", 0) != VERSION_CODE) {
            showDialog(DIALOG_ID_CHANGE_LOG);
            
            Editor editor = preferences.edit();
            editor.putInt("version_code", VERSION_CODE);
            editor.commit();
        }
    }
}