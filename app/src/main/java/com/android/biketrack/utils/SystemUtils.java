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
package com.android.biketrack.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Utility class for accessing basic Android functionality.
 *
 * Taken from MyTracks Google application source code.
 *
 * @author Gionata Boccalini
 */
public class SystemUtils {

    private static final String TAG = SystemUtils.class.getSimpleName();

    private SystemUtils() {}

    /**
     * Get the application version from the manifest.
     *
     * @param context the context
     *
     * @return the version, or an empty string in case of failure.
     */
    public static String getMyTracksVersion(Context context) {
        try {
            PackageInfo pi = context.getPackageManager()
                    .getPackageInfo("com.android.biketrack", PackageManager.GET_META_DATA);
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to get version info.", e);
            return "";
        }
    }
}
