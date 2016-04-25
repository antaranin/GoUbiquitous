package com.example.android.sunshine.app;

import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

public class DataRequestListenerService extends WearableListenerService
{
    private static final String TAG = DataRequestListenerService.class.getSimpleName();

    private static final String MESSAGE_CONFIG = "/path/message";
    private static final String WEATHER_TYPE_KEY = "weather_type_key";
    private static final String MAX_TEMP_KEY = "max_temperature_key";
    private static final String MIN_TEMP_KEY = "min_temperature_key";
    private static final String WIND_ANGLE_KEY = "wind_angle_key";
    private static final String WIND_SPEED_KEY = "wind_speed_key";
    private static final String IS_METRIC_KEY = "is_metric_key";
    private static final String SUNSHINE_CONFIG = "/sunshine_wear_config";

    @Override
    public void onMessageReceived(MessageEvent messageEvent)
    {
        super.onMessageReceived(messageEvent);
        if (messageEvent.getPath().equals(MESSAGE_CONFIG))
        {

            GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .build();

            ConnectionResult result = googleApiClient.blockingConnect(30, TimeUnit.SECONDS);
            if (!result.isSuccess())
            {
                Log.d(TAG, "Blocking connection failed");
                return;
            }

            ForecastDataSet dataItem = extractData();
            PutDataMapRequest putDataMapReq = PutDataMapRequest.create(SUNSHINE_CONFIG);

            putDataMapReq.getDataMap().putInt(MAX_TEMP_KEY, dataItem.maxTemp);
            putDataMapReq.getDataMap().putInt(MIN_TEMP_KEY, dataItem.minTemp);
            putDataMapReq.getDataMap().putInt(WEATHER_TYPE_KEY, dataItem.weatherType);
            putDataMapReq.getDataMap().putInt(WIND_ANGLE_KEY, dataItem.windAngle);
            putDataMapReq.getDataMap().putInt(WIND_SPEED_KEY, dataItem.windSpeed);
            putDataMapReq.getDataMap().putBoolean(IS_METRIC_KEY, Utility.isMetric(this));


            final PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
            boolean success = Wearable.DataApi.putDataItem(googleApiClient, putDataReq).await().getStatus().isSuccess();
            Log.d(TAG, "Data item success => " + success);
        }
    }

    private ForecastDataSet extractData()
    {
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";

        String locationSetting = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                locationSetting, System.currentTimeMillis());
        String[] projection = {
                WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
                WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
                WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
                WeatherContract.WeatherEntry.COLUMN_WIND_SPEED,
                WeatherContract.WeatherEntry.COLUMN_DEGREES
        };

        Cursor cursor = getContentResolver().query(weatherForLocationUri, projection, null, null, sortOrder);
        if(cursor != null && cursor.moveToFirst())
        {
            int weatherId = cursor.getInt(0);
            int maxTemp = cursor.getInt(1);
            int minTemp = cursor.getInt(2);
            int windSpeed = cursor.getInt(3);
            int windAngle = cursor.getInt(4);
            return new ForecastDataSet(weatherId, maxTemp, minTemp, windAngle, windSpeed);
        }
        if(cursor != null)
            cursor.close();

        return null;

    }
}
