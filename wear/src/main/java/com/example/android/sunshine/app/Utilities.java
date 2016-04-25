package com.example.android.sunshine.app;

import android.content.Context;
import android.support.annotation.StringRes;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Created by Arin on 24/04/16.
 */
public class Utilities
{
    /**
     * Helper method to convert the database representation of the date into something to display
     * to users.  As classy and polished a user experience as "20140102" is, we can do better.
     *
     * @param dateInMillis The date in milliseconds
     * @return a user-friendly representation of the date.
     */
    public static String getFullFriendlyDayString(long dateInMillis)
    {
        return SimpleDateFormat.getDateInstance(DateFormat.LONG).format(dateInMillis);
    }

    public static String formatTemperature(Context context, double temperature, boolean isMetric)
    {
        // Data stored in Celsius by default.  If user prefers to see in Fahrenheit, convert
        // the values here.
        if (!isMetric)
            temperature = (temperature * 1.8) + 32;

        return String.format(context.getString(R.string.format_temperature), temperature);
    }

    public static String formatWindSpeed(Context context, float speed, boolean isMetric)
    {
        int windFormat;
        if (isMetric)
        {
            windFormat = R.string.format_wind_kmh;
        }
        else
        {
            windFormat = R.string.format_wind_mph;
            speed = 0.621371192237334f * speed;
        }
        return context.getString(windFormat, speed);
    }

    public static String formatWindDirection(Context context, @ForecastDataItem.WindDirection int direction)
    {
        @StringRes int res;
        if (direction == ForecastDataItem.NORTH)
            res = R.string.north;
        else if (direction == ForecastDataItem.NORTH_WEST)
            res = R.string.north_west;
        else if (direction == ForecastDataItem.WEST)
            res = R.string.west;
        else if (direction == ForecastDataItem.SOUTH_WEST)
            res = R.string.south_west;
        else if (direction == ForecastDataItem.SOUTH)
            res = R.string.south;
        else if (direction == ForecastDataItem.SOUTH_EAST)
            res = R.string.south_east;
        else if (direction == ForecastDataItem.EAST)
            res = R.string.east;
        else if (direction == ForecastDataItem.NORTH_EAST)
            res = R.string.norht_east;
        else
            throw new AssertionError("Ussuported direction");

        return context.getString(res);
    }
}
