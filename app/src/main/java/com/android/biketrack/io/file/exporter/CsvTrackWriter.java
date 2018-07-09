package com.android.biketrack.io.file.exporter;

import android.content.Context;
import android.location.Location;

import com.android.biketrack.content.Track;
import com.android.biketrack.content.Waypoint;

import java.io.OutputStream;

// TODO to be done
public class CsvTrackWriter implements TrackWriter {
    public CsvTrackWriter(Context context) {
    }

    @Override
    public String getExtension() {
        return null;
    }

    @Override
    public void prepare(OutputStream outputStream) {

    }

    @Override
    public void close() {

    }

    @Override
    public void writeHeader(Track[] tracks) {

    }

    @Override
    public void writeFooter() {

    }

    @Override
    public void writeBeginWaypoints(Track track) {

    }

    @Override
    public void writeEndWaypoints() {

    }

    @Override
    public void writeWaypoint(Waypoint waypoint) {

    }

    @Override
    public void writeBeginTracks() {

    }

    @Override
    public void writeEndTracks() {

    }

    @Override
    public void writeBeginTrack(Track track, Location startLocation) {

    }

    @Override
    public void writeEndTrack(Track track, Location endLocation) {

    }

    @Override
    public void writeOpenSegment() {

    }

    @Override
    public void writeCloseSegment() {

    }

    @Override
    public void writeLocation(Location location) {

    }
}
