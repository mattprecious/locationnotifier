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

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Address;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockMapActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.mattprecious.locnotifier.RadiusOverlay.PointType;

import de.android1.overlaymanager.ManagedOverlay;
import de.android1.overlaymanager.ManagedOverlayGestureDetector;
import de.android1.overlaymanager.ManagedOverlayItem;
import de.android1.overlaymanager.OverlayManager;
import de.android1.overlaymanager.ZoomEvent;

public class ShowMap extends SherlockMapActivity {
    public static final long MIN_DISTANCE = 50;

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
    private MapController mapController;

    private LinearLayout distanceBarPanel;
    private SeekBar distanceBar;
    private long distance;

    private boolean gpsEnabled;
    private boolean followLocation;

    private List<Address> searchResults;

    private final int DIALOG_ID_SEARCH = 1;
    private final int DIALOG_ID_SEARCHING = 2;
    private final int DIALOG_ID_SEARCH_RESULTS = 3;

    @Override
    protected void onCreate(Bundle icicle) {
        // TODO Auto-generated method stub
        super.onCreate(icicle);
        setContentView(R.layout.map);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Bundle extras = getIntent().getExtras();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        mapView = (MapView) findViewById(R.id.mapview);
        mapController = mapView.getController();

        distanceBarPanel = (LinearLayout) findViewById(R.id.distance_bar_panel);
        distanceBar = (SeekBar) findViewById(R.id.distance_bar);
        distanceBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            private UpdateDistanceTask updateDistanceTask;

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(4);
                updateDistanceTask.cancel(true);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                updateDistanceTask = (UpdateDistanceTask) new UpdateDistanceTask().execute();
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }
        });

        // TODO: Either change this preference type to a long or create a new preference
        distance = (long) preferences.getFloat("dest_radius", MIN_DISTANCE);

        gpsEnabled = preferences.getBoolean("use_gps", true);
        followLocation = false;

        overlayManager = new OverlayManager(this, mapView);
        overlayListener = overlayManager.createOverlay("overlayListener");

        overlayListener
                .setOnOverlayGestureListener(new ManagedOverlayGestureDetector.OnOverlayGestureListener() {
                    @Override
                    public boolean onZoom(ZoomEvent zoom, ManagedOverlay overlay) {
                        stopFollow();

                        return false;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e, ManagedOverlay overlay,
                            GeoPoint point, ManagedOverlayItem item) {
                        stopFollow();

                        mapController.animateTo(point);
                        mapController.zoomIn();
                        return true;
                    }

                    @Override
                    public void onLongPress(MotionEvent e, ManagedOverlay overlay) {
                        stopFollow();

                        // due to the weird behavior stated below, it's possible to have the user
                        // lift their finger up while the vibration is queued and waiting, so cancel
                        // any pending vibrations
                        vibrator.cancel();

                        // for some reason, longPressFinished won't fire until quite a while after
                        // longPress fires... so delay the vibration by 450ms
                        long[] pattern = { 450, 50, };

                        vibrator.vibrate(pattern, -1);
                    }

                    @Override
                    public void onLongPressFinished(MotionEvent e, ManagedOverlay overlay,
                            GeoPoint point, ManagedOverlayItem item) {
                        stopFollow();

                        showDestination(point);
                    }

                    @Override
                    public boolean onScrolled(MotionEvent e1, MotionEvent e2, float distanceX,
                            float distanceY, ManagedOverlay overlay) {
                        stopFollow();

                        return false;
                    }

                    @Override
                    public boolean onSingleTap(MotionEvent e, ManagedOverlay overlay,
                            GeoPoint point, ManagedOverlayItem item) {
                        stopFollow();

                        // due to the weird behavior stated above, it's possible to have the user
                        // lift their finger up while the vibration is queued and waiting, so cancel
                        // any pending vibrations
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
                // Called when a new location is found by the network location provider.
                if (LocationHelper.isBetterLocation(location, bestLocation)) {
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

        int dest_lat = preferences.getInt("dest_lat", 0);
        int dest_lng = preferences.getInt("dest_lng", 0);
        float dest_radius = preferences.getFloat("dest_radius", 0);

        boolean moveToDestination = false;
        if (extras != null && extras.containsKey(EXTRA_DEST_LAT)
                && extras.containsKey(EXTRA_DEST_LNG)) {
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
        }

        if (extras != null && extras.containsKey(Intent.EXTRA_TEXT)) {
            String location = extras.getString(Intent.EXTRA_TEXT);
            location = location.split("\n")[0];

            search(location);
        }

        redraw();
        showHint();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0,
                locationListener);
        locationManager
                .requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }

    @Override
    protected void onPause() {
        super.onPause();

        locationManager.removeUpdates(locationListener);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // this needs to be in here so that if the device is rotated while the dialog is open, a
        // second one will be created over top of the existing one. Putting this in onDestroy does
        // nothing for some reason...
        removeDialog(DIALOG_ID_SEARCH_RESULTS);
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

    @Override
    protected Dialog onCreateDialog(int id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        Dialog dialog;

        switch (id) {
            case DIALOG_ID_SEARCH:
                LinearLayout searchDialog = (LinearLayout) LayoutInflater.from(
                        getApplicationContext()).inflate(R.layout.search_dialog, null);
                builder.setView(searchDialog);

                builder.setTitle(R.string.search);
                builder.setPositiveButton(R.string.ok, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        Dialog dialog = (Dialog) dialogInterface;
                        EditText queryText = (EditText) dialog
                                .findViewById(R.id.search_dialog_text);
                        search(queryText.getText().toString());
                    }
                });

                dialog = builder.create();

                break;
            case DIALOG_ID_SEARCHING:
                dialog = ProgressDialog.show(this, null, getString(R.string.searching), true);
                break;
            case DIALOG_ID_SEARCH_RESULTS:
                if (searchResults == null) {
                    return null;
                }

                String[] addresses = new String[searchResults.size()];
                for (int i = 0; i < addresses.length; i++) {
                    addresses[i] = LocationHelper.addressToString(searchResults.get(i));
                }

                builder.setTitle(R.string.search_results);
                builder.setCancelable(false);
                builder.setNegativeButton(R.string.cancel_button,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                removeDialog(DIALOG_ID_SEARCH_RESULTS);
                            }
                        });

                builder.setItems(addresses, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Address address = searchResults.get(which);
                        GeoPoint point = getPoint(address);

                        destinationPoint = new PointOverlay(point, PointType.DESTINATION);

                        redraw();
                        moveToDestination();

                        removeDialog(DIALOG_ID_SEARCH_RESULTS);
                    }
                });

                dialog = builder.create();
                break;
            default:
                dialog = null;
        }

        return dialog;
    }

    @TargetApi(11)
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getSupportMenuInflater();
        menuInflater.inflate(R.menu.map, menu);

        // SearchView was added in HC
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            SearchView searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
            searchView.setOnQueryTextListener(new OnQueryTextListener() {

                @Override
                public boolean onQueryTextSubmit(String query) {
                    search(query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    return false;
                }
            });
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_gps_on).setVisible(gpsEnabled);
        menu.findItem(R.id.menu_gps_off).setVisible(!gpsEnabled);

        // TODO: Create a new icon for when we're currently following the user.
        // Similar to the compass icon in GMM
        // if (followLocation) {
        // menu.findItem(R.id.menu_location).setIcon()
        // }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, LocationNotifier.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
                return true;
            case R.id.menu_save:
                Editor editor = preferences.edit();

                if (destinationPoint != null) {
                    GeoPoint destination = destinationPoint.getPoint();

                    editor.putInt("dest_lat", destination.getLatitudeE6());
                    editor.putInt("dest_lng", destination.getLongitudeE6());
                }

                editor.putFloat("dest_radius", distance);
                editor.putBoolean("use_gps", gpsEnabled);
                editor.commit();

                if (LocationService.isRunning()) {
                    stopService(new Intent(getApplicationContext(), LocationService.class));
                    startService(new Intent(getApplicationContext(), LocationService.class));
                }

                finish();
                return true;
            case R.id.menu_location:
                moveToLocation();
                startFollow();

                return true;
            case R.id.menu_gps_on:
                gpsEnabled = false;
                Toast.makeText(getApplicationContext(), R.string.gps_off_toast, Toast.LENGTH_SHORT)
                        .show();
                supportInvalidateOptionsMenu();

                return true;
            case R.id.menu_gps_off:
                gpsEnabled = true;
                Toast.makeText(getApplicationContext(), R.string.gps_on_toast, Toast.LENGTH_SHORT)
                        .show();
                supportInvalidateOptionsMenu();

                return true;
            case R.id.menu_distance:
                if (destinationPoint == null) {
                    Toast.makeText(getApplicationContext(), R.string.no_destination,
                            Toast.LENGTH_SHORT).show();
                    return true;
                }

                stopFollow();

                if (distanceBarPanel.getVisibility() == View.GONE) {
                    distanceBarPanel.setVisibility(View.VISIBLE);
                    moveToDestination();
                } else {
                    distanceBarPanel.setVisibility(View.GONE);
                }

                return true;
            case R.id.menu_search:
                // only need this for pre-HC
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                    showDialog(DIALOG_ID_SEARCH);
                }

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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

    private void search(String query) {
        new SearchTask().execute(query);
    }

    private void moveToLocation() {
        if (bestLocation != null) {
            moveTo(getPoint(bestLocation));
        }
    }

    private void moveToDestination() {
        if (destinationPoint != null) {
            moveTo(destinationPoint.getPoint());
        }
    }

    private void moveTo(GeoPoint point) {
        if (point != null) {
            mapController.animateTo(point);

            if (mapView.getZoomLevel() < 17) {
                mapController.setZoom(17);
            }
        }
    }

    private void startFollow() {
        followLocation = true;
        invalidateOptionsMenu();
    }

    private void stopFollow() {
        // Only do this when followLocation is true.
        // If the search field is active, invalidating the menu will cause the keyboard to pop open
        // again. It's really annoying.
        if (followLocation) {
            followLocation = false;
            invalidateOptionsMenu();
        }
    }

    private void showLocation(Location location) {
        GeoPoint point = getPoint(location);

        locationPoint = new PointOverlay(point, PointType.LOCATION);
        locationRadius = new RadiusOverlay(point, location.getAccuracy(), PointType.LOCATION);

        redraw();

        if (followLocation) {
            moveToLocation();
        }
    }

    private void showDestination(GeoPoint point) {
        destinationPoint = new PointOverlay(point, PointType.DESTINATION);
        destinationRadius = new RadiusOverlay(point, distance, PointType.DESTINATION);

        redraw();
    }

    private void redraw() {
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

    private GeoPoint getPoint(Address address) {
        Double lat = address.getLatitude() * 1E6;
        Double lng = address.getLongitude() * 1E6;

        return new GeoPoint(lat.intValue(), lng.intValue());
    }

    public class SearchTask extends AsyncTask<String, Void, List<Address>> {

        @Override
        protected void onPreExecute() {
            showDialog(DIALOG_ID_SEARCHING);
        }

        @Override
        public List<Address> doInBackground(String... query) {
            return LocationHelper.stringToAddresses(getApplicationContext(), query[0]);
        }

        @Override
        protected void onPostExecute(List<Address> result) {
            searchResults = result;
            dismissDialog(DIALOG_ID_SEARCHING);

            if (searchResults != null && searchResults.size() > 0) {
                showDialog(DIALOG_ID_SEARCH_RESULTS);
            } else {
                Toast.makeText(getApplicationContext(), R.string.no_results, Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    public class UpdateDistanceTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            while (!this.isCancelled()) {
                // progress bar has a size of 9, so 4 is the midpoint
                // don't do anything in the middle
                if (distanceBar.getProgress() == 4) {
                    continue;
                }

                // get absolute difference
                long modifier = Math.abs(distanceBar.getProgress() - 4);

                // adjust to exponential growth
                modifier = (long) Math.pow(2, modifier);

                // bring in negation
                modifier = distanceBar.getProgress() < 4 ? -modifier : modifier;

                // adjust based on zoom level
                // zoom level increases as you zoom in, we want to flip this around so the more
                // zoomed in you are, the less impact it has on the distance slider
                int invertedZoom = mapView.getMaxZoomLevel() - mapView.getZoomLevel() + 1;
                modifier = (long) (modifier * Math.pow(2, invertedZoom - 3));

                distance = Math.max(distance + modifier, MIN_DISTANCE);
                destinationRadius = new RadiusOverlay(destinationPoint.getPoint(), distance,
                        PointType.DESTINATION);

                mapView.post(new Runnable() {

                    @Override
                    public void run() {
                        redraw();

                    }
                });

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }

            return null;
        }
    }

}
