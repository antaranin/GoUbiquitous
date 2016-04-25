package com.example.android.sunshine.app;

/**
 * Created by Arin on 24/04/16.
 */
public class ForecastDataSet
{
    public final int weatherType;
    public final int maxTemp;
    public final int minTemp;
    public final int windAngle;
    public final int windSpeed;

    public ForecastDataSet(int weatherType, int maxTemp, int minTemp, int windAngle, int windSpeed)
    {
        this.weatherType = weatherType;
        this.maxTemp = maxTemp;
        this.minTemp = minTemp;
        this.windAngle = windAngle;
        this.windSpeed = windSpeed;
    }
}

