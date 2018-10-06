package com.android.biketrack.sensor;

public final class Sensor {
    private Sensor() {
    }

    public enum SensorState {
        NONE,
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        SENDING;
    }

    public static final class SensorDataSet {
        private boolean hasCreationTime;
        private long creationTime_ = 0L;
        public boolean hasCreationTime() { return hasCreationTime; }
        public long getCreationTime() { return creationTime_; }
    }
    }
