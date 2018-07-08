/*
 * Copyright 2010 Google Inc.
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

import android.location.Location;

import com.android.biketrack.sensor.Sensor;

/**
 * This class extends the standard Android location with extra information.
 *
 * Taken from MyTracks Google application source code.
 *
 * @author Sandor Dornbush
 */
public class TrackLocation extends Location {

  private int id = -1;
  private Sensor.SensorDataSet sensorDataSet = null;

  /**
   * The id of this location from the provider.
   */
  public TrackLocation(Location location, Sensor.SensorDataSet sd) {
    super(location);
    this.sensorDataSet = sd;
  }

  public TrackLocation(String provider) {
    super(provider);
  }

  public Sensor.SensorDataSet getSensorDataSet() {
    return sensorDataSet;
  }

  public void setSensorDataSet(Sensor.SensorDataSet sensorDataSet) {
    this.sensorDataSet = sensorDataSet;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }
  
  public void reset() {
    super.reset();
    sensorDataSet = null;
    id = -1;
  }
}
