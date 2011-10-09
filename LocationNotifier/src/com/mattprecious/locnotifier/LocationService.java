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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * 
 * @author Matthew Precious
 * 
 */
public class LocationService extends Service {

    private SharedPreferences preferences;

    private NotificationManager notificationManager;
    private LocationManager locationManager;
    private LocationListener locationListener;

    private Location destination;
    private float radius;

    private Location bestLocation;

    private static boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();

        LocationService.isRunning = true;

        updateRunningNotification();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        int lat = preferences.getInt("dest_lat", 0);
        int lng = preferences.getInt("dest_lng", 0);

        destination = new Location("");
        destination.setLatitude(lat / 1E6);
        destination.setLongitude(lng / 1E6);

        radius = preferences.getFloat("dest_radius", 0);

        notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                Log.d(getClass().getSimpleName(), "Location changed");
                updateLocation(location);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);

        if (preferences.getBoolean("use_gps", false)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }

        Log.d(getClass().getSimpleName(), "Watching your location"); // creepy
    }

    @Override
    public void onDestroy() {
        LocationService.isRunning = false;
        locationManager.removeUpdates(locationListener);

        LocationNotifier.sUpdateStartGo();
        stopForeground(true);

        Log.d(getClass().getSimpleName(), "No longer watching");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    public static boolean isRunning() {
        return LocationService.isRunning;
    }

    private void updateLocation(Location location) {
        if (LocationHelper.isBetterLocation(location, bestLocation)) {
            bestLocation = location;

            float distance = location.distanceTo(destination);
            updateRunningNotification(distance);

            if (distance <= radius) {
                approachingDestination();
            }
        }
    }

    private void approachingDestination() {
        Log.d(getClass().getSimpleName(), "Within distance to destination");
        
        locationManager.removeUpdates(locationListener);

        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, new Intent(), 0);

        String notifTitle = getString(R.string.notification_alert_title);
        String notifText = getString(R.string.notification_alert_text);

        Notification notification = new Notification(R.drawable.notification_alert, notifTitle, System.currentTimeMillis());
        notification.setLatestEventInfo(getApplicationContext(), notifTitle, notifText, contentIntent);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        String tone = preferences.getString("tone", null);
        Uri toneUri = (tone == null) ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) : Uri.parse(tone);

        notification.sound = toneUri;

        if (preferences.getBoolean("vibrate", false)) {
            int shortVib = 150;
            int shortPause = 150;

            // vibrate 3 short times
            long[] pattern = { 0, shortVib, shortPause, shortVib, shortPause, shortVib, };
            
            notification.vibrate = pattern;
        }

        notification.flags |= Notification.FLAG_SHOW_LIGHTS;
        notification.ledARGB = 0xffff0000;
        notification.ledOnMS = 500;
        notification.ledOffMS = 500;

        if (preferences.getBoolean("insistent", false)) {
            notification.flags |= Notification.FLAG_INSISTENT;
        }

        notificationManager.notify(0, notification);

        stopSelf();
    }

    private void updateRunningNotification() {
        updateRunningNotification(-1);
    }

    private void updateRunningNotification(float distance) {
        String message = (distance == -1) ? getString(R.string.notification_awaiting)
                : (Math.round(distance) + getString(R.string.notification_tracking));

        Notification runningNotification = new Notification(R.drawable.notification_running, null, 0);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, LocationNotifier.class), 0);
        runningNotification.setLatestEventInfo(this, getString(R.string.app_name), message, contentIntent);
        startForeground(R.string.app_name, runningNotification);
    }

}
