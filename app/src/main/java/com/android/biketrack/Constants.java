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
package com.android.biketrack;

/**
 * Common constants.
 *
 * Taken from MyTracks Google application source code.
 *
 * @author Gionata Boccalini
 */
public class Constants {

  private Constants() {}

  /**
   * The google account type.
   */
  public static final String ACCOUNT_TYPE = "com.google";

  /**
   * Maximum number of waypoints that will be loaded at one time.
   */
  public static final int MAX_LOADED_WAYPOINTS_POINTS = 10000;

  /**
   * The settings file name.
   */
  public static final String SETTINGS_FILE_NAME = "com.android.biketrack.pref_data";
}
