package com.example.android.sunshine.app;

import android.support.annotation.DrawableRes;
import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import lombok.Data;

/**
 * Created by Arin on 24/04/16.
 */
@Data
public class ForecastDataItem
{
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST})
    public @interface WindDirection
    {
    }

    public static final int NORTH = 0;
    public static final int NORTH_EAST = 1;
    public static final int EAST = 2;
    public static final int SOUTH_EAST = 3;
    public static final int SOUTH = 4;
    public static final int SOUTH_WEST = 5;
    public static final int WEST = 6;
    public static final int NORTH_WEST = 7;


    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STORM, LIGHT_RAIN, RAIN, SNOW, FOG, CLEAR_SKY, LIGHT_CLOUDS, CLOUDS})
    public @interface WeatherType
    {
    }

    private static final int STORM = 1;
    private static final int LIGHT_RAIN = 2;
    private static final int RAIN = 3;
    private static final int SNOW = 4;
    private static final int FOG = 5;
    private static final int CLEAR_SKY = 6;
    private static final int LIGHT_CLOUDS = 7;
    private static final int CLOUDS = 8;

    @WeatherType
    private int weatherType;
    private int maxTemp;
    private int minTemp;
    @WindDirection
    private int windDirection;
    private int windSpeed;

    public ForecastDataItem(@WeatherType  int weatherType, int maxTemp, int minTemp, @WindDirection int windDirection, int windSpeed)
    {
        this.weatherType = weatherType;
        this.maxTemp = maxTemp;
        this.minTemp = minTemp;
        this.windDirection = windDirection;
        this.windSpeed = windSpeed;
    }

    public boolean exchangeDataIfDiffers(@WeatherType  int weatherType, int maxTemp, int minTemp, @WindDirection int windDirection, int windSpeed)
    {
        boolean dataDiffers = false;
        if (this.weatherType != weatherType)
        {
            this.weatherType = weatherType;
            dataDiffers = true;
        }

        if (this.maxTemp != maxTemp)
        {
            this.maxTemp = maxTemp;
            dataDiffers = true;
        }

        if (this.minTemp != minTemp)
        {
            this.minTemp = minTemp;
            dataDiffers = true;
        }

        if (this.windDirection != windDirection)
        {
            this.windDirection = windDirection;
            dataDiffers = true;
        }

        if (this.windSpeed != windSpeed)
        {
            this.windSpeed = windSpeed;
            dataDiffers = true;
        }

        return dataDiffers;

    }

    @WindDirection
    public static int translateWindAngleToDirection(int windAngle)
    {
        //in case it is negative
        while (windAngle < 0)
        {
            windAngle += 360;
        }

        windAngle %= 360; //In case it is over 360

        if (windAngle <= 22)
            return NORTH;
        else if (windAngle <= 67)
            return NORTH_EAST;
        else if (windAngle <= 112)
            return EAST;
        else if (windAngle <= 157)
            return SOUTH_EAST;
        else if (windAngle <= 202)
            return SOUTH;
        else if (windAngle <= 247)
            return SOUTH_WEST;
        else if (windAngle <= 292)
            return WEST;
        else if (windAngle <= 337)
            return NORTH_WEST;
        return NORTH;

    }

    @DrawableRes
    public static int getWeatherTypeIconRes(@WeatherType int weatherType)
    {
        switch (weatherType)
        {
            case STORM:
                return R.drawable.ic_storm;
            case LIGHT_RAIN:
                return R.drawable.ic_light_rain;
            case RAIN:
                return R.drawable.ic_rain;
            case LIGHT_CLOUDS:
                return R.drawable.ic_light_clouds;
            case CLOUDS:
                return R.drawable.ic_cloudy;
            case CLEAR_SKY:
                return R.drawable.ic_clear;
            case FOG:
                return R.drawable.ic_fog;
            case SNOW:
                return R.drawable.ic_snow;
            default:
                throw new AssertionError("Unexpected value => " + weatherType);
        }

    }

    @WeatherType
    public static int translateWeatherIdToWeatherType(int weatherId)
    {
        if (weatherId >= 200 && weatherId <= 232)
            return STORM;
        else if (weatherId >= 300 && weatherId <= 321)
            return LIGHT_RAIN;
        else if (weatherId >= 500 && weatherId <= 504)
            return RAIN;
        else if (weatherId == 511)
            return SNOW;
        else if (weatherId >= 520 && weatherId <= 531)
            return RAIN;
        else if (weatherId >= 600 && weatherId <= 622)
            return SNOW;
        else if (weatherId >= 701 && weatherId <= 761)
            return FOG;
        else if (weatherId == 761 || weatherId == 781)
            return STORM;
        else if (weatherId == 800)
            return CLEAR_SKY;
        else if (weatherId == 801)
            return LIGHT_CLOUDS;
        else if (weatherId >= 802 && weatherId <= 804)
            return CLOUDS;

        return CLEAR_SKY;
    }

    @WindDirection
    public int getWindDirection()
    {
        return windDirection;
    }

}

