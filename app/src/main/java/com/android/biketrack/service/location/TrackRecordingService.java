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
package com.android.biketrack.service.location;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.sqlite.SQLiteException;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager.WakeLock;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.android.biketrack.Constants;
import com.android.biketrack.R;
import com.android.biketrack.content.Track;
import com.android.biketrack.content.TracksProvider;
import com.android.biketrack.content.TracksProviderUtils;
import com.android.biketrack.sensor.BluetoothSensorManager;
import com.android.biketrack.sensor.Sensor;
import com.android.biketrack.sensor.SensorManager;
import com.android.biketrack.utils.LocationUtils;
import com.android.biketrack.utils.PreferencesUtils;
import com.android.biketrack.utils.SystemUtils;
import com.android.biketrack.utils.UnitConversions;
import com.android.biketrack.utils.track.TrackNameUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A background service that registers a location listener and records track
 * points. Track points are saved to the {@link TracksProvider}.
 * <p>
 * Taken from MyTracks Google application source code.
 *
 * @author Gionata Boccalini
 */
public class TrackRecordingService extends Service {
    private static final String TAG = TrackRecordingService.class.getSimpleName();

    /**
     * The name of extra intent property to indicate whether we want to resume a
     * previously recorded track.
     */
    public static final String RESUME_TRACK_EXTRA_NAME = "com.android.biketrack.RESUME_TRACK";

    public static final double PAUSE_LATITUDE = 100.0;
    public static final double RESUME_LATITUDE = 200.0;

    /**
     * Anything faster than that (in meters per second) will be considered moving.
     */
    public static final double MAX_NO_MOVEMENT_SPEED = 0.224;


    // 1 second in milliseconds
    private static final long ONE_SECOND = (long) UnitConversions.S_TO_MS;

    // 1 minute in milliseconds
    private static final long ONE_MINUTE = (long) (UnitConversions.MIN_TO_S * UnitConversions.S_TO_MS);

    @VisibleForTesting
    static final int MAX_AUTO_RESUME_TRACK_RETRY_ATTEMPTS = 3;

    // The following variables are set in onCreate:
    private LocationReceiver mLocationReceiver;
    private ExecutorService executorService;
    private Context context;
    private TracksProviderUtils tracksProviderUtils;
    private Handler handler;
    //private MyTracksLocationManager myTracksLocationManager;
    private SharedPreferences sharedPreferences;
    private long recordingTrackId;
    private boolean recordingTrackPaused;
    private LocationListenerPolicy locationListenerPolicy;
    private int recordingDistanceInterval;
    private int maxRecordingDistance;
    private int recordingGpsAccuracy;
    private int autoResumeTrackTimeout;
    private long currentRecordingInterval;

    // The following variables are set when recording:
    private WakeLock wakeLock;
    private SensorManager sensorManager;
    private Location lastLocation;
    private boolean currentSegmentHasLocation;
    private boolean isIdle; // true if idle

    private final IBinder binder = new LocalBinder();

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public TrackRecordingService getService() {
            return TrackRecordingService.this;
        }
    }

    /*
     * Note that sharedPreferenceChangeListener cannot be an anonymous inner
     * class. Anonymous inner class will get garbage collected.
     */
    private final OnSharedPreferenceChangeListener
            sharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            if (key == null || key.equals(PreferencesUtils.getKey(context, R.string.recording_track_id_key))) {
                long trackId = PreferencesUtils.getLong(context, R.string.recording_track_id_key);
                /*
                 * Only through the TrackRecordingService can one stop a recording
                 * and set the recordingTrackId to -1L.
                 */
                if (trackId != PreferencesUtils.RECORDING_TRACK_ID_DEFAULT) {
                    recordingTrackId = trackId;
                }
            }
           /* if (key == null || key.equals(
                    PreferencesUtils.getKey(context, R.string.recording_track_paused_key))) {
                recordingTrackPaused = PreferencesUtils.getBoolean(context,
                        R.string.recording_track_paused_key,
                        PreferencesUtils.RECORDING_TRACK_PAUSED_DEFAULT);
            }*/
            if (key == null || key.equals(
                    PreferencesUtils.getKey(context, R.string.min_recording_interval_key))) {
                int minRecordingInterval = PreferencesUtils.getInt(context,
                        R.string.min_recording_interval_key,
                        PreferencesUtils.MIN_RECORDING_INTERVAL_DEFAULT);
                switch (minRecordingInterval) {
                    case PreferencesUtils.MIN_RECORDING_INTERVAL_ADAPT_BATTERY_LIFE:
                        // Choose battery life over moving time accuracy.
                        locationListenerPolicy = new AdaptiveLocationListenerPolicy(
                                30 * ONE_SECOND, 5 * ONE_MINUTE, 5);
                        break;
                    case PreferencesUtils.MIN_RECORDING_INTERVAL_ADAPT_ACCURACY:
                        // Get all the updates.
                        locationListenerPolicy = new AdaptiveLocationListenerPolicy(
                                ONE_SECOND, 30 * ONE_SECOND, 0);
                        break;
                    default:
                        locationListenerPolicy = new AbsoluteLocationListenerPolicy(
                                minRecordingInterval * ONE_SECOND);
                }
            }
            if (key == null || key.equals(
                    PreferencesUtils.getKey(context, R.string.recording_distance_interval_key))) {
                recordingDistanceInterval = PreferencesUtils.getInt(context,
                        R.string.recording_distance_interval_key,
                        PreferencesUtils.RECORDING_DISTANCE_INTERVAL_DEFAULT);
            }
            if (key == null || key.equals(
                    PreferencesUtils.getKey(context, R.string.max_recording_distance_key))) {
                maxRecordingDistance = PreferencesUtils.getInt(context,
                        R.string.max_recording_distance_key,
                        PreferencesUtils.MAX_RECORDING_DISTANCE_DEFAULT);
            }
            if (key == null || key.equals(
                    PreferencesUtils.getKey(context, R.string.recording_gps_accuracy_key))) {
                recordingGpsAccuracy = PreferencesUtils.getInt(context,
                        R.string.recording_gps_accuracy_key,
                        PreferencesUtils.RECORDING_GPS_ACCURACY_DEFAULT);
            }
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
                onLocationChangedAsync(location);
            }
        }
    }

/*    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(final Location location) {
//            if (myTracksLocationManager == null || executorService == null
//                    || !myTracksLocationManager.isAllowed() || executorService.isShutdown()
//                    || executorService.isTerminated()) {
            if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
                return;
            }
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    onLocationChangedAsync(location);
                }
            });
        }
    };*/

    /*private final ConnectionCallbacks activityRecognitionCallbacks = new ConnectionCallbacks() {
        @Override
        public void onDisconnected() {
        }

        @Override
        public void onConnected(Bundle bundle) {
        }
    };

    private final OnConnectionFailedListener
            activityRecognitionFailedListener = new OnConnectionFailedListener() {

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
        }
    };*/

    /*private final Runnable registerLocationRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecording() && !isPaused()) {
                registerLocationListener();
            }
            handler.postDelayed(this, ONE_MINUTE);
        }
    };*/

    /*
     * Note that this service, through the AndroidManifest.xml, is configured to
     * allow both MyTracks and third party apps to invoke it. For the onCreate
     * callback, we cannot tell whether the caller is MyTracks or a third party
     * app, thus it cannot start/stop a recording or write/update MyTracks
     * database.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
        context = this;
        tracksProviderUtils = TracksProviderUtils.Factory.get(this);
        handler = new Handler();
        mLocationReceiver = new LocationReceiver();

        LocalBroadcastManager.getInstance(this).registerReceiver(mLocationReceiver,
                new IntentFilter(LocationUpdatesService.ACTION_BROADCAST));

//        myTracksLocationManager = new MyTracksLocationManager(this, handler.getLooper(), true);
        sharedPreferences = getSharedPreferences(Constants.SETTINGS_FILE_NAME, Context.MODE_PRIVATE);
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

        // onSharedPreferenceChanged might not set recordingTrackId.
        recordingTrackId = PreferencesUtils.RECORDING_TRACK_ID_DEFAULT;

        // Require voiceExecutor and splitExecutor to be created.
        sharedPreferenceChangeListener.onSharedPreferenceChanged(sharedPreferences, null);

//        handler.post(registerLocationRunnable);

        /*
         * Try to restart the previous recording track in case the service has been
         * restarted by the system, which can sometimes happen.
         */
        Track track = tracksProviderUtils.getTrack(recordingTrackId);
        if (track != null) {
            restartTrack(track);
        } else {
            if (isRecording()) {
                Log.w(TAG, "track is null, but recordingTrackId not -1L. " + recordingTrackId);
                updateRecordingState(PreferencesUtils.RECORDING_TRACK_ID_DEFAULT, true);
            }
            showNotification(false);
        }
    }

    /*
     * Note that this service, through the AndroidManifest.xml, is configured to
     * allow both MyTracks and third party apps to invoke it. For the onStart
     * callback, we cannot tell whether the caller is MyTracks or a third party
     * app, thus it cannot start/stop a recording or write/update MyTracks
     * database.
     */
/*    @Override
    public void onStart(Intent intent, int startId) {
        handleStartCommand(intent, startId);
    }*/

    /*
     * Note that this service, through the AndroidManifest.xml, is configured to
     * allow both MyTracks and third party apps to invoke it. For the
     * onStartCommand callback, we cannot tell whether the caller is MyTracks or a
     * third party app, thus it cannot start/stop a recording or write/update
     * MyTracks database.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        handleStartCommand(intent, startId);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if (sensorManager != null) {
//            SensorManagerFactory.releaseSystemSensorManager();
            sensorManager = null;
        }

        // Reverse order from onCreate
        showNotification(false);

//        handler.removeCallbacks(registerLocationRunnable);
//        unregisterLocationListener();

        // unregister sharedPreferences before shutting down splitExecutor and voiceExecutor
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

//        myTracksLocationManager.close();
//        myTracksLocationManager = null;
        tracksProviderUtils = null;

        // This should be the next to last operation
        releaseWakeLock();

        /*
         * Shutdown the executorService last to avoid sending events to a dead
         * executor.
         */
        executorService.shutdown();
        super.onDestroy();
    }

    /**
     * Returns true if the service is recording.
     */
    public boolean isRecording() {
        return recordingTrackId != PreferencesUtils.RECORDING_TRACK_ID_DEFAULT;
    }

    /**
     * Returns true if the current recording is paused.
     */
    public boolean isPaused() {
        return recordingTrackPaused;
    }

    /**
     * Inserts a waypoint.
     *
     * @param waypointCreationRequest the waypoint creation request
     * @return the waypoint id
     */
    /*public long insertWaypoint(WaypointCreationRequest waypointCreationRequest) {
        if (!isRecording() || isPaused()) {
            return -1L;
        }

        WaypointType waypointType = waypointCreationRequest.getType();
        boolean isStatistics = waypointType == WaypointType.STATISTICS;

        // Get name
        String name;
        if (waypointCreationRequest.getName() != null) {
            name = waypointCreationRequest.getName();
        } else {
            int nextWaypointNumber = tracksProviderUtils.getNextWaypointNumber(
                    recordingTrackId, waypointType);
            if (nextWaypointNumber == -1) {
                nextWaypointNumber = 0;
            }
            name = getString(
                    isStatistics ? R.string.marker_split_name_format : R.string.marker_name_format,
                    nextWaypointNumber);
        }

        // Get category
        String category = waypointCreationRequest.getCategory() != null ? waypointCreationRequest
                .getCategory()
                : "";


        // Get length and duration
        double length;
        long duration;
        Location location = getLastValidTrackPointInCurrentSegment(recordingTrackId);
        if (location != null && trackTripStatisticsUpdater != null) {
            TripStatistics stats = trackTripStatisticsUpdater.getTripStatistics();
            length = stats.getTotalDistance();
            duration = stats.getTotalTime();
        } else {
            if (!waypointCreationRequest.isTrackStatistics()) {
                return -1L;
            }
            // For track statistics, make it an impossible location
            location = new Location("");
            location.setLatitude(100);
            location.setLongitude(180);
            length = 0.0;
            duration = 0L;
        }

        String photoUrl = waypointCreationRequest.getPhotoUrl() != null ? waypointCreationRequest
                .getPhotoUrl()
                : "";

        // Insert waypoint
        Waypoint waypoint = new Waypoint(name, description, category, icon, recordingTrackId,
                waypointType, length, duration, -1L, -1L, location, tripStatistics, photoUrl);
        Uri uri = tracksProviderUtils.insertWaypoint(waypoint);
        return Long.parseLong(uri.getLastPathSegment());
    }
*/
    /**
     * Starts the service as a foreground service.
     *
     * @param pendingIntent the notification pending intent
     * @param messageId     the notification message id
     */
/*    @VisibleForTesting
    protected void startForegroundService(PendingIntent pendingIntent, int messageId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this).setContentIntent(
                pendingIntent).setContentText(getString(messageId))
                .setContentTitle(getString(R.string.app_name)).setOngoing(true)
                .setSmallIcon(R.drawable.ic_stat_notify_recording).setWhen(System.currentTimeMillis());
        startForeground(1, builder.build());
    }

    *//**
     * Stops the service as a foreground service.
     *//*
    @VisibleForTesting
    protected void stopForegroundService() {
        stopForeground(true);
    }*/

    /**
     * Handles start command.
     *
     * @param intent  the intent
     * @param startId the start id
     */
    /*private void handleStartCommand(Intent intent, int startId) {
        // Check if the service is called to resume track (from phone reboot)
        if (intent != null && intent.getBooleanExtra(RESUME_TRACK_EXTRA_NAME, false)) {
            if (!shouldResumeTrack()) {
                Log.i(TAG, "Stop resume track.");
                updateRecordingState(PreferencesUtils.RECORDING_TRACK_ID_DEFAULT, true);
                stopSelfResult(startId);
                return;
            }
        }
    }*/

    /**
     * Returns true if should resume.
     */
/*    private boolean shouldResumeTrack() {
        Track track = tracksProviderUtils.getTrack(recordingTrackId);

        if (track == null) {
            Log.d(TAG, "Not resuming. Track is null.");
            return false;
        }
        int retries = PreferencesUtils.getInt(this, R.string.auto_resume_track_current_retry_key,
                PreferencesUtils.AUTO_RESUME_TRACK_CURRENT_RETRY_DEFAULT);
        if (retries >= MAX_AUTO_RESUME_TRACK_RETRY_ATTEMPTS) {
            Log.d(TAG, "Not resuming. Exceeded maximum retry attempts.");
            return false;
        }
        PreferencesUtils.setInt(this, R.string.auto_resume_track_current_retry_key, retries + 1);

        if (autoResumeTrackTimeout == PreferencesUtils.AUTO_RESUME_TRACK_TIMEOUT_NEVER) {
            Log.d(TAG, "Not resuming. Auto-resume track timeout set to never.");
            return false;
        } else if (autoResumeTrackTimeout == PreferencesUtils.AUTO_RESUME_TRACK_TIMEOUT_ALWAYS) {
            Log.d(TAG, "Resuming. Auto-resume track timeout set to always.");
            return true;
        }

        if (track.getTripStatistics() == null) {
            Log.d(TAG, "Not resuming. No trip statistics.");
            return false;
        }
        long stopTime = track.getTripStatistics().getStopTime();
        return stopTime > 0
                && (System.currentTimeMillis() - stopTime) <= autoResumeTrackTimeout * ONE_MINUTE;
    }*/

    /**
     * Starts a new track.
     *
     * @return the track id
     */
    public long startNewTrack() {
        if (isRecording()) {
            Log.d(TAG, "Ignore startNewTrack. Already recording.");
            return -1L;
        }
        long now = System.currentTimeMillis();

        // Insert a track
        Track track = new Track();
        Uri uri = tracksProviderUtils.insertTrack(track);
        long trackId = Long.parseLong(uri.getLastPathSegment());

        // Update shared preferences
        updateRecordingState(trackId, false);
//        PreferencesUtils.setInt(this, R.string.auto_resume_track_current_retry_key, 0);

        // Update database
        track.setId(trackId);
        track.setName(TrackNameUtils.getTrackName(this, trackId, now, null));

//        String category = PreferencesUtils.getString(
//                this, R.string.default_activity_key, PreferencesUtils.DEFAULT_ACTIVITY_DEFAULT);
//        track.setCategory(category);
//        track.setIcon(TrackIconUtils.getIconValue(this, category));
//        track.setTripStatistics(trackTripStatisticsUpdater.getTripStatistics());
        tracksProviderUtils.updateTrack(track);
//        insertWaypoint(WaypointCreationRequest.DEFAULT_START_TRACK);

        startRecording(true);
        return trackId;
    }

    /**
     * Restart a track.
     *
     * @param track the track
     */
    public void restartTrack(Track track) {
        Log.d(TAG, "Restarting track: " + track.getId());

        startRecording(true);
    }

    /**
     * Resumes current track.
     */
    public void resumeCurrentTrack() {
        if (!isRecording() || !isPaused()) {
            Log.d(TAG, "Ignore resumeCurrentTrack. Not recording or not paused.");
            return;
        }

        // Update shared preferences
        recordingTrackPaused = false;
//        PreferencesUtils.setBoolean(this, R.string.recording_track_paused_key, false);

        // Update database
        Track track = tracksProviderUtils.getTrack(recordingTrackId);
        if (track != null) {
            Location resume = new Location(LocationManager.GPS_PROVIDER);
            resume.setLongitude(0);
            resume.setLatitude(RESUME_LATITUDE);
            resume.setTime(System.currentTimeMillis());
            insertLocation(track, resume, null);
        }

        startRecording(false);
    }

    /**
     * Common code for starting a new track, resuming a track, or restarting after
     * phone reboot.
     *
     * @param trackStarted true if track is started, false if track is resumed
     */
    private void startRecording(boolean trackStarted) {

        // Update instance variables
//        sensorManager = SensorManagerFactory.getSystemSensorManager(this);
        sensorManager = new BluetoothSensorManager(this);

        lastLocation = null;
        currentSegmentHasLocation = false;
        isIdle = false;

        startGps();
//        sendTrackBroadcast(trackStarted ? R.string.track_started_broadcast_action
//                : R.string.track_resumed_broadcast_action, recordingTrackId);
    }

    /**
     * Starts gps.
     */
    private void startGps() {
        wakeLock = SystemUtils.acquireWakeLock(this, wakeLock);
//        registerLocationListener();
        showNotification(true);
    }

    /**
     * Ends the current track.
     */
    public void endCurrentTrack() {
        if (!isRecording()) {
            Log.d(TAG, "Ignore endCurrentTrack. Not recording.");
            return;
        }

        // Need to remember the recordingTrackId before setting it to -1L
        long trackId = recordingTrackId;
        boolean paused = recordingTrackPaused;

        // Update shared preferences
        updateRecordingState(PreferencesUtils.RECORDING_TRACK_ID_DEFAULT, true);

        // Update database
        Track track = tracksProviderUtils.getTrack(trackId);
        if (track != null) {

            // If not paused, add the last location
            if (!paused) {
                insertLocation(track, lastLocation, getLastValidTrackPointInCurrentSegment(trackId));

                // Update the recording track time
                updateRecordingTrack(track, tracksProviderUtils.getLastTrackPointId(trackId), false);
            }

            /*String trackName = TrackNameUtils.getTrackName(this, trackId,
                    track.getTripStatistics().getStartTime(),
                    tracksProviderUtils.getFirstValidTrackPoint(trackId));
            if (trackName != null && !trackName.equals(track.getName())) {
                track.setName(trackName);
                tracksProviderUtils.updateTrack(track);
            }*/

            // Activity tipe: case DetectedActivity.ON_BICYCLE
        }
        endRecording(true, trackId);
    }

    /**
     * Pauses the current track.
     */
    public void pauseCurrentTrack() {
        if (!isRecording() || isPaused()) {
            Log.d(TAG, "Ignore pauseCurrentTrack. Not recording or paused.");
            return;
        }

        // Update shared preferences
        recordingTrackPaused = true;
//        PreferencesUtils.setBoolean(this, R.string.recording_track_paused_key, true);

        // Update database
        Track track = tracksProviderUtils.getTrack(recordingTrackId);
        if (track != null) {
            insertLocation(track, lastLocation, getLastValidTrackPointInCurrentSegment(track.getId()));

            Location pause = new Location(LocationManager.GPS_PROVIDER);
            pause.setLongitude(0);
            pause.setLatitude(PAUSE_LATITUDE);
            pause.setTime(System.currentTimeMillis());
            insertLocation(track, pause, null);
        }

        endRecording(false, recordingTrackId);
    }

    /**
     * Common code for ending a track or pausing a track.
     *
     * @param trackStopped true if track is stopped, false if track is paused
     * @param trackId      the track id
     */
    private void endRecording(boolean trackStopped, long trackId) {

        // Update instance variables
        if (sensorManager != null) {
//            SensorManagerFactory.releaseSystemSensorManager();
            sensorManager = null;
        }
        lastLocation = null;

//        sendTrackBroadcast(trackStopped ? R.string.track_stopped_broadcast_action
//                : R.string.track_paused_broadcast_action, trackId);
        stopGps(trackStopped);
    }

    /**
     * Stops gps.
     *
     * @param stop true to stop self
     */
    private void stopGps(boolean stop) {
//        unregisterLocationListener();
        showNotification(false);
        releaseWakeLock();
        if (stop) {
            stopSelf();
        }
    }

    /**
     * Gets the last valid track point in the current segment. Returns null if not
     * available.
     *
     * @param trackId the track id
     */
    private Location getLastValidTrackPointInCurrentSegment(long trackId) {
        if (!currentSegmentHasLocation) {
            return null;
        }
        return tracksProviderUtils.getLastValidTrackPoint(trackId);
    }

    /**
     * Updates the recording states.
     *
     * @param trackId the recording track id
     * @param paused  true if the recording is paused
     */
    private void updateRecordingState(long trackId, boolean paused) {
        recordingTrackId = trackId;
        PreferencesUtils.setLong(this, R.string.recording_track_id_key, trackId);
        recordingTrackPaused = paused;
//        PreferencesUtils.setBoolean(this, R.string.recording_track_paused_key, recordingTrackPaused);
    }

    /**
     * Called when location changed.
     *
     * @param location the location
     */
    private void onLocationChangedAsync(Location location) {
        try {
            if (!isRecording() || isPaused()) {
                Log.w(TAG, "Ignore onLocationChangedAsync. Not recording or paused.");
                return;
            }

            Track track = tracksProviderUtils.getTrack(recordingTrackId);
            if (track == null) {
                Log.w(TAG, "Ignore onLocationChangedAsync. No track.");
                return;
            }

            if (!LocationUtils.isValidLocation(location)) {
                Log.w(TAG, "Ignore onLocationChangedAsync. location is invalid.");
                return;
            }

            if (!location.hasAccuracy() || location.getAccuracy() >= recordingGpsAccuracy) {
                Log.d(TAG, "Ignore onLocationChangedAsync. Poor accuracy.");
                return;
            }

            // Fix for phones that do not set the time field
            if (location.getTime() == 0L) {
                location.setTime(System.currentTimeMillis());
            }

            Location lastValidTrackPoint = getLastValidTrackPointInCurrentSegment(track.getId());
            long idleTime = 0L;
            if (lastValidTrackPoint != null && location.getTime() > lastValidTrackPoint.getTime()) {
                idleTime = location.getTime() - lastValidTrackPoint.getTime();
            }
            locationListenerPolicy.updateIdleTime(idleTime);
            if (currentRecordingInterval != locationListenerPolicy.getDesiredPollingInterval()) {
//                registerLocationListener();
            }

            // Always insert the first segment location
            if (!currentSegmentHasLocation) {
                insertLocation(track, location, null);
                currentSegmentHasLocation = true;
                lastLocation = location;
                return;
            }

            if (!LocationUtils.isValidLocation(lastValidTrackPoint)) {
                /*
                 * Should not happen. The current segment should have a location. Just
                 * insert the current location.
                 */
                insertLocation(track, location, null);
                lastLocation = location;
                return;
            }

            double distanceToLastTrackLocation = location.distanceTo(lastValidTrackPoint);
            if (distanceToLastTrackLocation > maxRecordingDistance) {
                insertLocation(track, lastLocation, lastValidTrackPoint);

                Location pause = new Location(LocationManager.GPS_PROVIDER);
                pause.setLongitude(0);
                pause.setLatitude(PAUSE_LATITUDE);
                pause.setTime(lastLocation.getTime());
                insertLocation(track, pause, null);

                insertLocation(track, location, null);
                isIdle = false;
            } else if (distanceToLastTrackLocation >= recordingDistanceInterval) {
                insertLocation(track, lastLocation, lastValidTrackPoint);
                insertLocation(track, location, null);
                isIdle = false;
            } else if (!isIdle && location.hasSpeed() && location.getSpeed() < MAX_NO_MOVEMENT_SPEED) {
                insertLocation(track, lastLocation, lastValidTrackPoint);
                insertLocation(track, location, null);
                isIdle = true;
            } else if (isIdle && location.hasSpeed() && location.getSpeed() >= MAX_NO_MOVEMENT_SPEED) {
                insertLocation(track, lastLocation, lastValidTrackPoint);
                insertLocation(track, location, null);
                isIdle = false;
            } else {
                Log.d(TAG, "Not recording location, idle");
            }
            lastLocation = location;
        } catch (Error e) {
            Log.e(TAG, "Error in onLocationChangedAsync", e);
            throw e;
        } catch (RuntimeException e) {
            Log.e(TAG, "RuntimeException in onLocationChangedAsync", e);
            throw e;
        }
    }

    /**
     * Inserts a location.
     *
     * @param track               the track
     * @param location            the location
     * @param lastValidTrackPoint the last valid track point, can be null
     */
    private void insertLocation(Track track, Location location, Location lastValidTrackPoint) {
        if (location == null) {
            Log.w(TAG, "Ignore insertLocation. location is null.");
            return;
        }
        // Do not insert if inserted already
        if (lastValidTrackPoint != null && lastValidTrackPoint.getTime() == location.getTime()) {
            Log.w(TAG, "Ignore insertLocation. location time same as last valid track point time.");
            return;
        }

        try {
            Uri uri = tracksProviderUtils.insertTrackPoint(location, track.getId());
            long trackPointId = Long.parseLong(uri.getLastPathSegment());
            updateRecordingTrack(track, trackPointId, LocationUtils.isValidLocation(location));
            Log.i(TAG, "Inserted location:" + LocationUtils.getLocationText(location) + ", ID: " + trackPointId);
        } catch (SQLiteException e) {
            /*
             * Insert failed, most likely because of SqlLite error code 5
             * (SQLite_BUSY). This is expected to happen extremely rarely (if our
             * listener gets invoked twice at about the same time).
             */
            Log.w(TAG, "SQLiteException", e);
        }
        sendTrackBroadcast(R.string.track_update_broadcast_action, track.getId());

    }

    /**
     * Updates the recording track time. Also updates the startId and the stopId.
     * Increase the number of points if it is a new and valid track point.
     *
     * @param track                  the track
     * @param lastTrackPointId       the last track point id
     * @param increaseNumberOfPoints true to increase the number of points
     */
    private void updateRecordingTrack(Track track, long lastTrackPointId, boolean increaseNumberOfPoints) {
        if (lastTrackPointId >= 0) {
            if (track.getStartId() < 0) {
                track.setStartId(lastTrackPointId);
            }
            track.setStopId(lastTrackPointId);
        }
        if (increaseNumberOfPoints) {
            track.setNumberOfPoints(track.getNumberOfPoints() + 1);
        }

        tracksProviderUtils.updateTrack(track);
    }

    private Sensor.SensorDataSet getSensorDataSet() {
        if (sensorManager == null || !sensorManager.isEnabled()
                || !sensorManager.isSensorDataSetValid()) {
            return null;
        }
        return sensorManager.getSensorDataSet();
    }

    /**
     * Registers the location listener.
     */
    /*private void registerLocationListener() {
        if (myTracksLocationManager == null) {
            Log.e(TAG, "locationManager is null.");
            return;
        }
        try {
            long interval = locationListenerPolicy.getDesiredPollingInterval();
            myTracksLocationManager.requestLocationUpdates(
                    interval, locationListenerPolicy.getMinDistance(), locationListener);
            currentRecordingInterval = interval;
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not register location listener.", e);
        }
    }*/

    /**
     * Unregisters the location manager.
     */
    /*private void unregisterLocationListener() {
        if (myTracksLocationManager == null) {
            Log.e(TAG, "locationManager is null.");
            return;
        }
        myTracksLocationManager.removeLocationUpdates(locationListener);
    }*/

    /**
     * Releases the wake lock.
     */
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    /**
     * Shows the notification.
     *
     * @param isGpsStarted true if GPS is started
     */
    private void showNotification(boolean isGpsStarted) {
       /* if (isRecording()) {
            if (isPaused()) {
                stopForegroundService();
            } else {
                Intent intent = IntentUtils.newIntent(this, TrackDetailActivity.class)
                        .putExtra(TrackDetailActivity.EXTRA_TRACK_ID, recordingTrackId);
                PendingIntent pendingIntent = TaskStackBuilder.create(this)
                        .addParentStack(TrackDetailActivity.class).addNextIntent(intent)
                        .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
                startForegroundService(pendingIntent, R.string.track_record_notification);
            }
            return;
        } else {
            // Not recording
            if (isGpsStarted) {
                Intent intent = IntentUtils.newIntent(this, TrackListActivity.class);
                PendingIntent pendingIntent = TaskStackBuilder.create(this)
                        .addNextIntent(intent).getPendingIntent(0, 0);
                startForegroundService(pendingIntent, R.string.gps_starting);
            } else {
                stopForegroundService();
            }
        }*/
    }

    /**
     * Sends track broadcast.
     *
     * @param actionId the intent action id
     * @param trackId  the track id
     */
    private void sendTrackBroadcast(int actionId, long trackId) {
        Intent intent = new Intent().setAction(getString(actionId))
                .putExtra(getString(R.string.track_id_broadcast_extra), trackId);
        sendBroadcast(intent);
    }

    // TODO onbind, rebind ecc
    /**
     * TODO: There is a bug in Android that leaks Binder instances. This bug is
     * especially visible if we have a non-static class, as there is no way to
     * nullify reference to the outer class (the service). A workaround is to use
     * a static class and explicitly clear service and detach it from the
     * underlying Binder. With this approach, we minimize the leak to 24 bytes per
     * each service instance. For more details, see the following bug:
     * http://code.google.com/p/android/issues/detail?id=6426.
     */
    /*private static class ServiceBinder extends ITrackRecordingService.Stub {
        private TrackRecordingService trackRecordingService;
        private DeathRecipient deathRecipient;

        public ServiceBinder(TrackRecordingService trackRecordingService) {
            this.trackRecordingService = trackRecordingService;
        }

        @Override
        public boolean isBinderAlive() {
            return trackRecordingService != null;
        }

        @Override
        public boolean pingBinder() {
            return isBinderAlive();
        }

        @Override
        public void linkToDeath(DeathRecipient recipient, int flags) {
            deathRecipient = recipient;
        }

        @Override
        public boolean unlinkToDeath(DeathRecipient recipient, int flags) {
            if (!isBinderAlive()) {
                return false;
            }
            deathRecipient = null;
            return true;
        }

        @Override
        public void startGps() {
            if (!canAccess()) {
                return;
            }
            if (!trackRecordingService.isRecording()) {
                trackRecordingService.startGps();
            }
        }

        public void stopGps() {
            if (!canAccess()) {
                return;
            }
            if (!trackRecordingService.isRecording()) {
                trackRecordingService.stopGps(true);
            }
        }

        @Override
        public long startNewTrack() {
            if (!canAccess()) {
                return -1L;
            }
            return trackRecordingService.startNewTrack();
        }

        @Override
        public void pauseCurrentTrack() {
            if (!canAccess()) {
                return;
            }
            trackRecordingService.pauseCurrentTrack();
        }

        @Override
        public void resumeCurrentTrack() {
            if (!canAccess()) {
                return;
            }
            trackRecordingService.resumeCurrentTrack();
        }

        @Override
        public void endCurrentTrack() {
            if (!canAccess()) {
                return;
            }
            trackRecordingService.endCurrentTrack();
        }

        @Override
        public boolean isRecording() {
            if (!canAccess()) {
                return false;
            }
            return trackRecordingService.isRecording();
        }

        @Override
        public boolean isPaused() {
            if (!canAccess()) {
                return false;
            }
            return trackRecordingService.isPaused();
        }

        @Override
        public long getRecordingTrackId() {
            if (!canAccess()) {
                return -1L;
            }
            return trackRecordingService.recordingTrackId;
        }

        @Override
        public long getTotalTime() {
            if (!canAccess()) {
                return 0;
            }
            TripStatisticsUpdater updater = trackRecordingService.trackTripStatisticsUpdater;
            if (updater == null) {
                return 0;
            }
            if (!trackRecordingService.isPaused()) {
                updater.updateTime(System.currentTimeMillis());
            }
            return updater.getTripStatistics().getTotalTime();
        }

        @Override
        public long insertWaypoint(WaypointCreationRequest waypointCreationRequest) {
            if (!canAccess()) {
                return -1L;
            }
            return trackRecordingService.insertWaypoint(waypointCreationRequest);
        }

        @Override
        public void insertTrackPoint(Location location) {
            if (!canAccess()) {
                return;
            }
            trackRecordingService.locationListener.onLocationChanged(location);
        }

        @Override
        public byte[] getSensorData() {
            if (!canAccess()) {
                return null;
            }
            if (trackRecordingService.sensorManager == null) {
                Log.d(TAG, "sensorManager is null.");
                return null;
            }
            if (trackRecordingService.sensorManager.getSensorDataSet() == null) {
                Log.d(TAG, "Sensor data set is null.");
                return null;
            }
            return trackRecordingService.sensorManager.getSensorDataSet().toByteArray();
        }

        @Override
        public int getSensorState() {
            if (!canAccess()) {
                return Sensor.SensorState.NONE.getNumber();
            }
            if (trackRecordingService.sensorManager == null) {
                Log.d(TAG, "sensorManager is null.");
                return Sensor.SensorState.NONE.getNumber();
            }
            return trackRecordingService.sensorManager.getSensorState().getNumber();
        }

        *//**
         * Returns true if the RPC caller is from the same application or if the
         * "Allow access" setting indicates that another app can invoke this
         * service's RPCs.
         *//*
        private boolean canAccess() {
            // As a precondition for access, must check if the service is available.
            if (trackRecordingService == null) {
                throw new IllegalStateException("The track recording service has been detached!");
            }
            if (Process.myPid() == Binder.getCallingPid()) {
                return true;
            } else {
                return PreferencesUtils.getBoolean(trackRecordingService, R.string.allow_access_key,
                        PreferencesUtils.ALLOW_ACCESS_DEFAULT);
            }
        }

        *//**
         * Detaches from the track recording service. Clears the reference to the
         * outer class to minimize the leak.
         *//*
        private void detachFromService() {
            trackRecordingService = null;
            attachInterface(null, null);

            if (deathRecipient != null) {
                deathRecipient.binderDied();
            }
        }

        @Override
        public void updateCalorie() {
            if (!canAccess()) {
                return;
            }
            trackRecordingService.updateCalorie();
        }
    }*/
}
