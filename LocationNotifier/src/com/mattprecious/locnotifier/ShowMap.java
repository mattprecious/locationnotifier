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

import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.mattprecious.locnotifier.RadiusOverlay.PointType;

import de.android1.overlaymanager.ManagedOverlay;
import de.android1.overlaymanager.ManagedOverlayGestureDetector;
import de.android1.overlaymanager.ManagedOverlayItem;
import de.android1.overlaymanager.OverlayManager;
import de.android1.overlaymanager.ZoomEvent;

public class ShowMap extends MapActivity {

    public static final String EXTRA_DEST_LAT = "dest_lat";
    public static final String EXTRA_DEST_LNG = "dest_lng";

    private LocationManager locationManager;
    private LocationListener locationListener;

    private SharedPreferences preferences;

    private Vibrator vibrator;

    private Location bestLocation;

    private OverlayManager overlayManager;

    private PointOverlay locationPoint;
    private PointOverlay destinationPoint;
    private RadiusOverlay locationRadius;
    private RadiusOverlay destinationRadius;
    private ManagedOverlay overlayListener;

    private MapView mapView;
    private ToggleButton gpsToggle;
    private Spinner radiusSpinner;
    private ImageButton locationButton;
    private Button saveButton;

    private MapController mapController;

    @Override
    protected void onCreate(Bundle icicle) {
        // TODO Auto-generated method stub
        super.onCreate(icicle);
        setContentView(R.layout.map);

        Bundle extras = getIntent().getExtras();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        mapView = (MapView) findViewById(R.id.mapview);
        mapView.setBuiltInZoomControls(true);

        mapController = mapView.getController();

        gpsToggle = (ToggleButton) findViewById(R.id.gps_button);
        gpsToggle.setChecked(preferences.getBoolean("use_gps", false));

        radiusSpinner = (Spinner) findViewById(R.id.radius);
        radiusSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (destinationPoint != null) {
                    Log.d(getClass().getSimpleName(), "Radius updated: " + (pos + 1) * 50 + "m");
                    destinationRadius = new RadiusOverlay(destinationPoint.getPoint(), (pos + 1) * 50, PointType.DESTINATION);
                    redraw();
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        ArrayAdapter<CharSequence> radiusAdapter = ArrayAdapter.createFromResource(this, R.array.radius_array, android.R.layout.simple_spinner_item);
        radiusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        radiusSpinner.setAdapter(radiusAdapter);
        
        locationButton = (ImageButton) findViewById(R.id.my_location);
        locationButton.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View arg0) {
                moveToLocation();
            }
        });

        saveButton = (Button) findViewById(R.id.save);
        saveButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                // use the spinner instead of the radius object so there's one
                // less thing to catch
                float radius = (radiusSpinner.getSelectedItemPosition() + 1) * 50;

                Editor editor = preferences.edit();

                if (destinationPoint != null) {
                    GeoPoint destination = destinationPoint.getPoint();

                    editor.putInt("dest_lat", destination.getLatitudeE6());
                    editor.putInt("dest_lng", destination.getLongitudeE6());
                }

                editor.putFloat("dest_radius", radius);
                editor.putBoolean("use_gps", gpsToggle.isChecked());
                editor.commit();
                
                if (LocationService.isRunning()) {
                    stopService(new Intent(getApplicationContext(), LocationService.class));
                    startService(new Intent(getApplicationContext(), LocationService.class));
                }

                finish();
            }
        });

        overlayManager = new OverlayManager(this, mapView);
        overlayListener = overlayManager.createOverlay("overlayListener");

        overlayListener.setOnOverlayGestureListener(new ManagedOverlayGestureDetector.OnOverlayGestureListener() {
            @Override
            public boolean onZoom(ZoomEvent zoom, ManagedOverlay overlay) {
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e, ManagedOverlay overlay, GeoPoint point, ManagedOverlayItem item) {
                mapController.animateTo(point);
                mapController.zoomIn();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e, ManagedOverlay overlay) {
                // due to the weird behavior stated below, it's possible to have
                // the user lift their finger up while the vibration is queued
                // and waiting, so cancel any pending vibrations
                vibrator.cancel();
                
                // for some reason, longPressFinished won't fire until quite a
                // while after longPress fires... so delay the vibration by
                // 450ms
                long[] pattern = {
                        450,
                        50,
                };

                vibrator.vibrate(pattern, -1);
            }

            @Override
            public void onLongPressFinished(MotionEvent e, ManagedOverlay overlay, GeoPoint point, ManagedOverlayItem item) {
                showDestination(point);
            }

            @Override
            public boolean onScrolled(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY, ManagedOverlay overlay) {
                return false;
            }

            @Override
            public boolean onSingleTap(MotionEvent e, ManagedOverlay overlay, GeoPoint point, ManagedOverlayItem item) {
                // due to the weird behavior stated above, it's possible to have
                // the user lift their finger up while the vibration is queued
                // and waiting, so cancel any pending vibrations
                vibrator.cancel();
                
                return false;
            }
        });

        overlayManager.populate();

        // Acquire a reference to the system Location Manager
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location
                // provider.
                Log.d(getClass().getSimpleName(), "Location update. Radius: " + location.getAccuracy());
                if (LocationHelper.isBetterLocation(location, bestLocation)) {
                    Log.d(getClass().getSimpleName(), "Is better.");
                    bestLocation = location;
                    showLocation(location);
                }
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        bestLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (bestLocation == null) {
            bestLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        if (bestLocation != null) {
            showLocation(bestLocation);
            moveToLocation();
        }

        // Register the listener with the Location Manager to receive location
        // updates
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

        int dest_lat = preferences.getInt("dest_lat", 0);
        int dest_lng = preferences.getInt("dest_lng", 0);
        float dest_radius = preferences.getFloat("dest_radius", 0);

        boolean moveToDestination = false;
        if (extras != null && extras.containsKey(EXTRA_DEST_LAT) && extras.containsKey(EXTRA_DEST_LNG)) {
            dest_lat = extras.getInt(EXTRA_DEST_LAT);
            dest_lng = extras.getInt(EXTRA_DEST_LNG);
            
            moveToDestination = true;
        }

        GeoPoint destination = new GeoPoint(dest_lat, dest_lng);
        
        if (moveToDestination) {
            mapController.animateTo(destination);
        }

        if (dest_lat != 0 && dest_lng != 0) {
            destinationPoint = new PointOverlay(destination, PointType.DESTINATION);
        }

        if (dest_radius != 0) {
            destinationRadius = new RadiusOverlay(destination, dest_radius, PointType.DESTINATION);
            radiusSpinner.setSelection((int) ((dest_radius / 50) - 1));
        }

        redraw();
        showHint();
    }

    @Override
    protected void onDestroy() {
        locationManager.removeUpdates(locationListener);

        super.onDestroy();
    }

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }
    
    private void showHint() {
        int hintShown = preferences.getInt("hint_shown", 0); 
        if (hintShown < 5) {
            Toast hintToast = Toast.makeText(this, R.string.hint_toast, Toast.LENGTH_LONG);
            hintToast.show();
            
            Editor editor = preferences.edit();
            editor.putInt("hint_shown", hintShown + 1);
            editor.commit();
        }
    }
    
    private void moveToLocation() {
        if (bestLocation != null) {
            mapController.animateTo(getPoint(bestLocation));
            mapController.setZoom(17);
        }
    }

    private void showLocation(Location location) {
        GeoPoint point = getPoint(location);

        locationPoint = new PointOverlay(point, PointType.LOCATION);
        locationRadius = new RadiusOverlay(point, location.getAccuracy(), PointType.LOCATION);

        redraw();
    }

    private void showDestination(GeoPoint point) {
        destinationPoint = new PointOverlay(point, PointType.DESTINATION);
        destinationRadius = new RadiusOverlay(point, (radiusSpinner.getSelectedItemPosition() + 1) * 50, PointType.DESTINATION);

        redraw();
    }

    private void redraw() {
        Log.d(getClass().getSimpleName(), "Redraw");
        List<Overlay> mapOverlays = mapView.getOverlays();

        mapOverlays.clear();

        if (locationRadius != null) {
            mapOverlays.add(locationRadius);
        }

        if (locationPoint != null) {
            mapOverlays.add(locationPoint);
        }

        if (destinationRadius != null) {
            mapOverlays.add(destinationRadius);
        }

        if (destinationPoint != null) {
            mapOverlays.add(destinationPoint);
        }

        overlayManager.populate();
        mapView.invalidate();
    }

    private GeoPoint getPoint(Location location) {
        Double lat = location.getLatitude() * 1E6;
        Double lng = location.getLongitude() * 1E6;

        return new GeoPoint(lat.intValue(), lng.intValue());
    }

}
