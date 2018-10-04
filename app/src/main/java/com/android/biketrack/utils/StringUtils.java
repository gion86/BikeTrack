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

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * Various string manipulation methods.
 *
 * Taken from MyTracks Google application source code.
 *
 * @author Gionata Boccalini
 */
public class StringUtils {

    private static final String COORDINATE_DEGREE = "\u00B0";
    private static final SimpleDateFormat ISO_8601_DATE_TIME_FORMAT = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    private static final SimpleDateFormat ISO_8601_BASE = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private static final Pattern ISO_8601_EXTRAS = Pattern.compile(
            "^(\\.\\d+)?(?:Z|([+-])(\\d{2}):(\\d{2}))?$");
    static {
        ISO_8601_DATE_TIME_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        ISO_8601_BASE.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * Formats the given text as a XML CDATA element. This includes adding the
     * starting and ending CDATA tags. Please notice that this may result in
     * multiple consecutive CDATA tags.
     *
     * @param text the given text
     */
    public static String formatCData(String text) {
        return "<![CDATA[" + text.replaceAll("]]>", "]]]]><![CDATA[>") + "]]>";
    }

    /**
     * Formats the time using the ISO 8601 date time format with fractional
     * seconds in UTC time zone.
     *
     * @param time the time in milliseconds
     */
    public static String formatDateTimeIso8601(long time) {
        return ISO_8601_DATE_TIME_FORMAT.format(time);
    }
}
