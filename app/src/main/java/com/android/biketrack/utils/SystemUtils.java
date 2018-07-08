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
     * Get the My Tracks version from the manifest.
     *
     * @return the version, or an empty string in case of failure.
     */
    public static String getMyTracksVersion(Context context) {
        try {
            PackageInfo pi = context.getPackageManager()
                    .getPackageInfo("com.google.android.maps.mytracks", PackageManager.GET_META_DATA);
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to get version info.", e);
            return "";
        }
    }
}
