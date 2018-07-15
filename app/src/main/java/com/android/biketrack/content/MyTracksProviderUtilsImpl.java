/*
 * Copyright 2008 Google Inc.
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

package com.android.biketrack.content;

import android.content.ContentResolver;
import android.content.Context;
import android.location.Location;
import android.net.Uri;

import com.android.biketrack.stats.TripStatistics;

import java.util.List;

/**
 * {@link MyTracksProviderUtils} implementation.
 * 
 * @author Leif Hendrik Wilden
 */
public class MyTracksProviderUtilsImpl implements MyTracksProviderUtils {

  private static final String TAG = MyTracksProviderUtilsImpl.class.getSimpleName();

  private static final int MAX_LATITUDE = 90000000;

  private final ContentResolver contentResolver;
  private int defaultCursorBatchSize = 2000;

  public MyTracksProviderUtilsImpl(ContentResolver contentResolver) {
    this.contentResolver = contentResolver;
  }

  @Override
  public void clearTrack(Context context, long trackId) {
    //deleteTrackPointsAndWaypoints(context, trackId);
    Track track = new Track();
    track.setId(trackId);
    updateTrack(track);
  }

    @Override
    public void deleteAllTracks(Context context) {

    }

    @Override
    public void deleteTrack(Context context, long trackId) {

    }

    @Override
    public List<Track> getAllTracks() {
        return null;
    }

    @Override
    public Track getLastTrack() {
        return null;
    }

    @Override
    public Track getTrack(long trackId) {
        Track track = new Track();
        TripStatistics tripStatistics = track.getTripStatistics();

        track.setId(0);
        track.setName("Test track");
        track.setDescription("Test description");
        track.setCategory("Test category");
        track.setStartId(0);
        track.setStopId(100);
        tripStatistics.setStartTime(998888);
        tripStatistics.setStopTime(2323782);
        track.setNumberOfPoints(10);
        tripStatistics.setTotalDistance(100.44);
        tripStatistics.setTotalTime(34224444);
        tripStatistics.setMovingTime(324243);

        return track;
    }

    @Override
    public Uri insertTrack(Track track) {
        return null;
    }

    @Override
    public void updateTrack(Track track) {

    }

    @Override
    public void deleteWaypoint(Context context, long waypointId) {

    }

    @Override
    public long getFirstWaypointId(long trackId) {
        return 0;
    }

    @Override
    public Waypoint getLastWaypoint(long trackId, Waypoint.WaypointType waypointType) {
        return null;
    }

    @Override
    public int getNextWaypointNumber(long trackId, Waypoint.WaypointType waypointType) {
        return 0;
    }

    @Override
    public Waypoint getWaypoint(long waypointId) {
        return null;
    }

    @Override
    public int getWaypointCount(long trackId) {
        return 0;
    }

    @Override
    public Uri insertWaypoint(Waypoint waypoint) {
        return null;
    }

    @Override
    public boolean updateWaypoint(Waypoint waypoint) {
        return false;
    }

    @Override
    public int bulkInsertTrackPoint(Location[] locations, int length, long trackId) {
        return 0;
    }

    @Override
    public long getFirstTrackPointId(long trackId) {
        return 0;
    }

    @Override
    public long getLastTrackPointId(long trackId) {
        return 0;
    }

    @Override
    public long getTrackPointId(long trackId, Location location) {
        return 0;
    }

    @Override
    public Location getFirstValidTrackPoint(long trackId) {
        return null;
    }

    @Override
    public Location getLastValidTrackPoint(long trackId) {
        return null;
    }

    @Override
    public Location getLastValidTrackPoint() {
        return null;
    }

    @Override
    public LocationIterator getTrackPointLocationIterator(long trackId, long startTrackPointId, boolean descending, LocationFactory locationFactory) {
        return null;
    }

    @Override
    public Uri insertTrackPoint(Location location, long trackId) {
        return null;
    }

}