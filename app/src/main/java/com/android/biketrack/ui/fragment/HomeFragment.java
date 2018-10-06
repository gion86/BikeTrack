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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.BuildConfig;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.biketrack.R;
import com.android.biketrack.service.location.LocationUpdatesService;
import com.android.biketrack.service.location.TrackRecordingService;
import com.android.biketrack.service.location.TrackRecordingServiceConnection;
import com.android.biketrack.utils.PreferencesUtils;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.text.DateFormat;
import java.util.Date;

import static com.android.biketrack.utils.LocationUtils.getLocationText;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HomeFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 * @author Gionata Boccalini
 */
public class HomeFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = HomeFragment.class.getSimpleName();

    private static final String STATE_BUNDLE = "STATE_BUNDLE";
    private static final String IS_REQUESTING_UPDATES = "bundle_is_requesting_updates";
    private static final String LAST_KNOWN_LOCATION = "bundle_last_known_location";
    private static final String LAST_UPDATED_ON = "bundle_last_updated_on";

    TextView txtLocationResult;
    TextView txtUpdatedOn;
    Button btnStartUpdates;
    Button btnStopUpdates;
    Button btnGetLastLocation;

    // Location last updated time
    private String mLastUpdateTime;

    private static final int REQUEST_CHECK_SETTINGS = 100;

    // Bunch of location related apis
    private SettingsClient mSettingsClient;
    private LocationSettingsRequest mLocationSettingsRequest;
    private Location mCurrentLocation;

    // Boolean flag to toggle the ui
    private Boolean mRequestingLocationUpdates;

    private LocationUpdatesService mService;
    private TrackRecordingService mTrackService;

    private TrackRecordingServiceConnection mTrackRecordingServiceConnection;

    private boolean mBound;

    private Bundle mSavedState = null;

    private LocationReceiver mLocationReceiver;
    private LocationRequest mLocationRequest;

    // Monitors the state of the connection to the service.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationUpdatesService.LocalBinder binder = (LocationUpdatesService.LocalBinder) service;
            mService = binder.getService();

            // Resuming location updates depending on button state and
            // allowed permissions
            if (mRequestingLocationUpdates && checkPermissions()) {
                startLocationUpdates();
            }

            updateLocationUI();

            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
        }
    };

    /**
     * Receiver for broadcasts sent by {@link LocationUpdatesService}.
     */
    private class LocationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Location location = intent.getParcelableExtra(LocationUpdatesService.EXTRA_LOCATION);
            if (location != null) {
                mCurrentLocation = location;
                mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
                updateLocationUI();
            }
        }
    }

    /**
     * Update the UI displaying the location data
     * and toggling the buttons
     */
    private void updateLocationUI() {
        if (mCurrentLocation != null) {
            txtLocationResult.setText(getLocationText(mCurrentLocation));

            // Giving a blink animation on TextView
            txtLocationResult.setAlpha(0);
            txtLocationResult.animate().alpha(1).setDuration(300);

            // location last updated time
            txtUpdatedOn.setText("Last updated on: " + mLastUpdateTime);
        }

        toggleButtons();
    }

    private void toggleButtons() {
        if (mRequestingLocationUpdates) {
            btnStartUpdates.setEnabled(false);
            btnStopUpdates.setEnabled(true);
        } else {
            btnStartUpdates.setEnabled(true);
            btnStopUpdates.setEnabled(false);
        }
    }

    /**
     * Starting location updates
     * Check whether location settings are satisfied and then
     * location updates will be requested
     */
    private void startLocationUpdates() {

        Toast.makeText(getActivity().getApplicationContext(), "Started location updates!", Toast.LENGTH_SHORT).show();
        mService.requestLocationUpdates();
        mTrackService = mTrackRecordingServiceConnection.getServiceIfBound();
        if (mTrackService != null) {
            mTrackService.startNewTrack();
        }
        updateLocationUI();

        mSettingsClient
                .checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener(getActivity(), new OnSuccessListener<LocationSettingsResponse>() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.i(TAG, "All location settings are satisfied.");

                        Toast.makeText(getActivity().getApplicationContext(), "Started location updates!", Toast.LENGTH_SHORT).show();

                        //noinspection MissingPermission
                        //mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                        //        mLocationCallback, Looper.myLooper());

                        mService.requestLocationUpdates();

                        updateLocationUI();
                    }
                })
                .addOnFailureListener(getActivity(), new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings ");
                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the
                                    // result in onActivityResult().
                                    ResolvableApiException rae = (ResolvableApiException) e;
                                    rae.startResolutionForResult(getActivity(), REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException sie) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                Log.e(TAG, errorMessage);

                                Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
                        }

                        updateLocationUI();
                    }
                });
    }

    private boolean checkPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(getActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void stopLocationButtonClick() {
        mRequestingLocationUpdates = false;
        stopLocationUpdates();
    }

    private void startLocationButtonClick() {
        // Requesting ACCESS_FINE_LOCATION using Dexter library
        Dexter.withActivity(getActivity())
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse response) {
                        mRequestingLocationUpdates = true;
                        startLocationUpdates();
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse response) {
                        if (response.isPermanentlyDenied()) {
                            // open device settings when the permission is
                            // denied permanently
                            openSettings();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    private void stopLocationUpdates() {
        mService.removeLocationUpdates();
        Toast.makeText(getActivity().getApplicationContext(), "Location updates stopped!", Toast.LENGTH_SHORT).show();
        toggleButtons();
    }

    private void showLastKnownLocation() {
        if (mCurrentLocation != null) {
            Toast.makeText(getActivity().getApplicationContext(), "Lat: " + mCurrentLocation.getLatitude()
                    + ", Lng: " + mCurrentLocation.getLongitude(), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getActivity().getApplicationContext(), "Last known location is not available!", Toast.LENGTH_SHORT).show();
        }
    }

    public HomeFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment HomeFragment.
     */
    public static HomeFragment newInstance() {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /* If the Fragment was destroyed inbetween (screen rotation), we need to recover the savedState first */
        /* However, if it was not, it stays in the instance from the last onDestroyView() and we don't want to overwrite it */
        if (savedInstanceState != null && mSavedState == null) {
            mSavedState = savedInstanceState.getBundle(STATE_BUNDLE);
        }
        if (mSavedState != null) {
            if (mSavedState.containsKey(IS_REQUESTING_UPDATES)) {
                mRequestingLocationUpdates = mSavedState.getBoolean(IS_REQUESTING_UPDATES);
            } else {
                mRequestingLocationUpdates = PreferencesUtils.getBoolean(getActivity(), R.string.prefkey_req_loc_updates, false);
            }
            if (mSavedState.containsKey(LAST_KNOWN_LOCATION)) {
                mCurrentLocation = mSavedState.getParcelable(LAST_KNOWN_LOCATION);
            }
            if (mSavedState.containsKey(LAST_UPDATED_ON)) {
                mLastUpdateTime = mSavedState.getString(LAST_UPDATED_ON);
            }
        } else {
            mRequestingLocationUpdates = PreferencesUtils.getBoolean(getActivity(), R.string.prefkey_req_loc_updates, false);
        }

        mSavedState = null;

        mSettingsClient = LocationServices.getSettingsClient(getActivity());

        mLocationRequest = new LocationRequest();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();

        mLocationReceiver = new LocationReceiver();

        mTrackRecordingServiceConnection = new TrackRecordingServiceConnection(getActivity(), null);
        mTrackRecordingServiceConnection.startAndBind();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        txtLocationResult = getView().findViewById(R.id.location_result);
        txtUpdatedOn = getView().findViewById(R.id.updated_on);
        btnStartUpdates = getView().findViewById(R.id.btn_start_location_updates);
        btnStopUpdates = getView().findViewById(R.id.btn_stop_location_updates);
        btnGetLastLocation = getView().findViewById(R.id.btn_get_last_location);;

        btnStartUpdates.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startLocationButtonClick();
            }
        });

        btnStopUpdates.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopLocationButtonClick();
            }
        });

        btnGetLastLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLastKnownLocation();
            }
        });

        updateLocationUI();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Bind to the service. If the service is in foreground mode, this signals to the service
        // that since this activity is in the foreground, the service can exit foreground mode.
        getActivity().bindService(new Intent(getActivity(), LocationUpdatesService.class), mServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume() {
        super.onResume();

        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mLocationReceiver,
                new IntentFilter(LocationUpdatesService.ACTION_BROADCAST));

        mRequestingLocationUpdates = PreferencesUtils.getBoolean(getActivity(), R.string.prefkey_req_loc_updates, false);
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mLocationReceiver);
        super.onPause();

        PreferencesUtils.setBoolean(getActivity(), R.string.prefkey_req_loc_updates, mRequestingLocationUpdates);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // If onDestroyView() is called first, we can use the previously savedState but we can't
        // call saveState() anymore. If onSaveInstanceState() is called first, we don't have savedState,
        // so we need to call saveState().
        outState.putBundle(STATE_BUNDLE, (mSavedState != null) ? mSavedState : saveState());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mSavedState = saveState(); /* vstup defined here for sure */
    }

    private Bundle saveState() { /* called either from onDestroyView() or onSaveInstanceState() */
        Bundle state = new Bundle();
        state.putBoolean(IS_REQUESTING_UPDATES, mRequestingLocationUpdates);
        state.putParcelable(LAST_KNOWN_LOCATION, mCurrentLocation);
        state.putString(LAST_UPDATED_ON, mLastUpdateTime);
        return state;
    }

    @Override
    public void onStop() {
        if (mBound) {
            // Unbind from the service. This signals to the service that this activity is no longer
            // in the foreground, and the service can respond by promoting itself to a foreground
            // service.
            getActivity().unbindService(mServiceConnection);
            mBound = false;
        }

        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onStop();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Update the buttons state depending on whether location updates are being requested.
        if (key.equals(getString(R.string.prefkey_req_loc_updates))) {
            mRequestingLocationUpdates = sharedPreferences.getBoolean(key, false);
            toggleButtons();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.e(TAG, "User agreed to make required location settings changes.");
                        // Nothing to do. startLocationupdates() gets called in onResume again.
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.e(TAG, "User chose not to make required location settings changes.");
                        mRequestingLocationUpdates = false;
                        break;
                }
                break;
        }
    }

    private void openSettings() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null);
        intent.setData(uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
