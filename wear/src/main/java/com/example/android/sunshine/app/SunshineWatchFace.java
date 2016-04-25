/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.Pair;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Collection;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import hugo.weaving.DebugLog;
import lombok.NonNull;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService
{
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private static final String TAG = SunshineWatchFace.class.getSimpleName();

    @Override
    public Engine onCreateEngine()
    {
        return new Engine();
    }

    private static class EngineHandler extends Handler
    {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference)
        {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg)
        {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null)
            {
                switch (msg.what)
                {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener
    {
        private static final String WEATHER_TYPE_KEY = "weather_type_key";
        private static final String MAX_TEMP_KEY = "max_temperature_key";
        private static final String MIN_TEMP_KEY = "min_temperature_key";
        private static final String WIND_ANGLE_KEY = "wind_angle_key";
        private static final String WIND_SPEED_KEY = "wind_speed_key";
        private static final String SUNSHINE_CONFIG = "/sunshine_wear_config";
        private static final String IS_METRIC_KEY = "is_metric_key";
        private static final String REQUEST_UPDATE_KEY = "request_update_key";
        private static final String MESSAGE_CONFIG = "/path/message";
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        private Paint primaryTextPaint;
        private Paint secondaryTextPaint;
        private Paint smallTextPaint;
        boolean mAmbient;
        float clockTextYPosition;
        float dateYPosition;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        private ForecastDataItem dataItem;
        private GoogleApiClient googleApiClient;
        private DataApi.DataListener onDataChangedListener = new SunshineDataListener();
        private ResultCallback<DataItemBuffer> onConnectedResultCallback = new SunshineResultCallback();
        private Bitmap weatherIcon;
        private String noData;
        private float margin;
        private float horizontal_margin;
        private float lastRowYPosition;
        private float dividerYPosition;
        private Pair<Float, Float> dividerWidthHeight;
        private float weatherIconCenterXOffset;
        private int weatherIconTextTopYOffset;
        private float minTempTextCenterXOffset;
        private float speedCenterXOffset;
        private float directionCenterXOffset;
        private String dateText = "";
        private String maxTemp;
        private String minTemp;
        private String windSpeed;
        private String windDirection;
        private boolean isMetric;
        private boolean showAlternative;

        private Calendar calendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };


        @Override
        public void onCreate(SurfaceHolder holder)
        {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            googleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            initResources();
        }

        private void initResources()
        {
            Resources resources = SunshineWatchFace.this.getResources();
            margin = resources.getDimension(R.dimen.watch_interior_element_margin);
            horizontal_margin = resources.getDimension(R.dimen.watch_interior_horizontal_margin);

            calendar = Calendar.getInstance();
            dateText = Utilities.getFullFriendlyDayString(calendar.getTimeInMillis());

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(SunshineWatchFace.this, R.color.background));


            primaryTextPaint = createTextPaint(ContextCompat.getColor(SunshineWatchFace.this, R.color.text_color));

            secondaryTextPaint = createTextPaint(ContextCompat.getColor(SunshineWatchFace.this, R.color.secondary_text_color));

            smallTextPaint = createTextPaint(ContextCompat.getColor(SunshineWatchFace.this, R.color.secondary_text_color));


            dividerWidthHeight = new Pair<>(getResources().getDimension(R.dimen.divider_length),
                    getResources().getDimension(R.dimen.divider_height));


            noData = getString(R.string.no_data);
        }

        private void prepareIcon(@NonNull ForecastDataItem item)
        {
            weatherIcon = BitmapFactory.decodeResource(getResources(), ForecastDataItem.getWeatherTypeIconRes(item.getWeatherType()));
        }

        private void updateGuiWithData()
        {
            prepareBottomLine(dataItem);

        }

        private void prepareBottomLine(ForecastDataItem dataItem)
        {
            prepareIcon(dataItem);
            Rect bounds = new Rect();

            maxTemp = Utilities.formatTemperature(SunshineWatchFace.this, dataItem.getMaxTemp(), isMetric);
            minTemp = Utilities.formatTemperature(SunshineWatchFace.this, dataItem.getMinTemp(), isMetric);
            windSpeed = Utilities.formatWindSpeed(SunshineWatchFace.this, dataItem.getWindSpeed(), isMetric);
            windDirection = Utilities.formatWindDirection(SunshineWatchFace.this, dataItem.getWindDirection());

            primaryTextPaint.getTextBounds(maxTemp, 0, maxTemp.length(), bounds);
            float centralWidth = bounds.width();
            weatherIconCenterXOffset = -centralWidth / 2 - weatherIcon.getWidth() - horizontal_margin;
            int textHeight = bounds.height();
            weatherIconTextTopYOffset = (textHeight - weatherIcon.getHeight()) / 2 - textHeight;
            float rightWidth = secondaryTextPaint.measureText(minTemp);
            minTempTextCenterXOffset = centralWidth / 2 + rightWidth / 2 + horizontal_margin;

            directionCenterXOffset = - primaryTextPaint.measureText(windDirection) / 2 - 4 * horizontal_margin;
            speedCenterXOffset = secondaryTextPaint.measureText(windSpeed) / 2 - 3 * horizontal_margin;




        }

        private void requestDataFromMobile()
        {
            new AsyncTask<Void, Void, Void>()
            {
                @Override
                protected Void doInBackground(Void... params)
                {
                    NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                    PutDataMapRequest putDataMapReq = PutDataMapRequest.create(SUNSHINE_CONFIG);

                    putDataMapReq.getDataMap().putBoolean(REQUEST_UPDATE_KEY, true);


                    boolean success =
                            Wearable.MessageApi
                                    .sendMessage(googleApiClient, pickBestNodeId(nodes.getNodes()), MESSAGE_CONFIG, new byte[0])
                                    .await().getStatus().isSuccess();
                                    Log.d(TAG, "Data item success => " + success);
                    return null;
                }


            }.execute();


        }

        private String pickBestNodeId(Collection<Node> nodes)
        {
            String bestNodeId = null;
            // Find a nearby node or pick one arbitrarily
            for (Node node : nodes)
            {
                if (node.isNearby())
                {
                    return node.getId();
                }
                bestNodeId = node.getId();
            }
            return bestNodeId;
        }


        @Override
        public void onDestroy()
        {
            releaseGoogleApiClient();
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor)
        {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextAlign(Paint.Align.CENTER);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible)
        {
            super.onVisibilityChanged(visible);

            if (visible)
            {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                calendar.setTimeZone(TimeZone.getDefault());
                long now = System.currentTimeMillis();
                calendar.setTimeInMillis(now);
                googleApiClient.connect();
            }
            else
            {
                unregisterReceiver();
                releaseGoogleApiClient();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void releaseGoogleApiClient()
        {
            Log.d(TAG, "Disconnectiong GoogleApi");
            if (googleApiClient != null && googleApiClient.isConnected())
            {
                Wearable.DataApi.removeListener(googleApiClient, onDataChangedListener);
                googleApiClient.disconnect();
            }
        }

        private void registerReceiver()
        {
            if (mRegisteredTimeZoneReceiver)
            {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver()
        {
            if (!mRegisteredTimeZoneReceiver)
            {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets)
        {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);


            primaryTextPaint.setTextSize(textSize);
            secondaryTextPaint.setTextSize(textSize - 8);

            Rect bounds = new Rect();
            primaryTextPaint.getTextBounds("0", 0, 1, bounds);
            final int textHeight = bounds.height();

            clockTextYPosition = resources.getDimension(isRound
                    ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset) + textHeight;


            float smallTextSize = resources.getDimension(isRound ? R.dimen.small_text_size_round : R.dimen.small_text_size);
            smallTextPaint.setTextSize(smallTextSize);
            smallTextPaint.getTextBounds("0", 0, 1, bounds);
            final int dateTextHeight = bounds.height();

            dateYPosition = clockTextYPosition + dateTextHeight + margin;


            dividerYPosition = dateYPosition + margin;

            secondaryTextPaint.getTextBounds("0", 0, 1, bounds);
            final int secondaryTextHeight = bounds.height();
            lastRowYPosition = dividerYPosition + dividerWidthHeight.second + secondaryTextHeight + margin;


        }

        @Override
        public void onPropertiesChanged(Bundle properties)
        {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick()
        {
            super.onTimeTick();

            int day = calendar.get(Calendar.DAY_OF_YEAR);
            long now = System.currentTimeMillis();
            calendar.setTimeInMillis(now);
            if (day != calendar.get(Calendar.DAY_OF_YEAR))
                dateText = Utilities.getFullFriendlyDayString(System.currentTimeMillis());
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode)
        {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode)
            {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient)
                    primaryTextPaint.setAntiAlias(!inAmbientMode);

                //If we quit ambient mode, and there is no data, we should attempt to get it from the device
                if (!mAmbient && googleApiClient.isConnected() && dataItem == null)
                    requestDataFromMobile();


                invalidate();
            }


            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime)
        {
            switch (tapType)
            {
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    showAlternative = !showAlternative;
                    if(!isInAmbientMode())
                        invalidate();
                    break;
                default:
                    break;
            }
        }
        @Override
        public void onDraw(Canvas canvas, Rect bounds)
        {
            // Draw the background.
            if (isInAmbientMode())
                canvas.drawColor(Color.BLACK);
            else
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            final int center = bounds.width() / 2;
            long now = System.currentTimeMillis();
            calendar.setTimeInMillis(now);
            String text = mAmbient
                    ? String.format("%d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND));
            canvas.drawText(text, center, clockTextYPosition, primaryTextPaint);
            canvas.drawText(dateText, center, dateYPosition, smallTextPaint);

            if (!isInAmbientMode())
            {
                canvas.drawRect(center - dividerWidthHeight.first / 2, dividerYPosition,
                        center + dividerWidthHeight.first / 2, dividerYPosition + dividerWidthHeight.second, primaryTextPaint);

                if (dataItem == null)
                {
                    canvas.drawText(noData, center, lastRowYPosition, secondaryTextPaint);
                }
                else if(!showAlternative)
                {
                    canvas.drawText(maxTemp, center, lastRowYPosition, primaryTextPaint);
                    canvas.drawBitmap(weatherIcon, center + weatherIconCenterXOffset, lastRowYPosition + weatherIconTextTopYOffset, null);
                    canvas.drawText(minTemp, center + minTempTextCenterXOffset, lastRowYPosition, secondaryTextPaint);
                }
                else
                {
                    canvas.drawText(windDirection, center + directionCenterXOffset, lastRowYPosition, primaryTextPaint);
                    canvas.drawText(windSpeed, center + speedCenterXOffset, lastRowYPosition, secondaryTextPaint);
                }
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer()
        {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning())
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning()
        {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage()
        {
            invalidate();
            if (shouldTimerBeRunning())
            {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle)
        {
            Log.d(TAG, "connected GoogleAPI");
            Wearable.DataApi.addListener(googleApiClient, onDataChangedListener);
            Wearable.DataApi.getDataItems(googleApiClient).setResultCallback(onConnectedResultCallback);
            if (dataItem == null)
                requestDataFromMobile();
        }

        @Override
        public void onConnectionSuspended(int i)
        {
            Log.d(TAG, "connection suspended GoogleApi");

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
        {
            Log.d(TAG, "connection failed GoogleApi");
        }

        @DebugLog
        private boolean processItem(DataItem item)
        {
            if (SUNSHINE_CONFIG.equals(item.getUri().getPath()))
            {
                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                if (dataMap.containsKey(IS_METRIC_KEY))
                    updateIsMetricIfNeeded(dataMap.getBoolean(IS_METRIC_KEY));

                if (!dataMap.containsKey(MAX_TEMP_KEY) || !dataMap.containsKey(MIN_TEMP_KEY) || !dataMap.containsKey(WEATHER_TYPE_KEY)
                        || !dataMap.containsKey(WIND_ANGLE_KEY) || !dataMap.containsKey(WIND_SPEED_KEY))
                    return false;
                boolean dataDiffers;
                if (dataItem == null)
                {
                    dataItem = new ForecastDataItem(
                            ForecastDataItem.translateWeatherIdToWeatherType(dataMap.getInt(WEATHER_TYPE_KEY)),
                            dataMap.getInt(MAX_TEMP_KEY), dataMap.getInt(MIN_TEMP_KEY),
                            ForecastDataItem.translateWindAngleToDirection(dataMap.getInt(WIND_ANGLE_KEY)),
                            dataMap.getInt(WIND_SPEED_KEY));
                    dataDiffers = true;
                }
                else
                {
                    dataDiffers = dataItem.exchangeDataIfDiffers(
                            ForecastDataItem.translateWeatherIdToWeatherType(dataMap.getInt(WEATHER_TYPE_KEY)),
                            dataMap.getInt(MAX_TEMP_KEY), dataMap.getInt(MIN_TEMP_KEY),
                            ForecastDataItem.translateWindAngleToDirection(dataMap.getInt(WIND_ANGLE_KEY)),
                            dataMap.getInt(WIND_SPEED_KEY));
                }

                if (dataDiffers)
                    updateGuiWithData();
                return dataDiffers;

            }
            return false;
        }


        private void updateIsMetricIfNeeded(boolean isMetric)
        {
            if (isMetric != this.isMetric)
            {
                this.isMetric = isMetric;
                if (dataItem != null)
                    prepareBottomLine(dataItem);
                invalidate();
            }
        }

        private class SunshineDataListener implements DataApi.DataListener
        {
            @Override
            public void onDataChanged(DataEventBuffer dataEvents)
            {
                Log.d(TAG, "On data changed");
                boolean dataDiffers = false;
                for (DataEvent event : dataEvents)
                {
                    if (event.getType() == DataEvent.TYPE_CHANGED)
                    {
                        DataItem item = event.getDataItem();
                        dataDiffers = processItem(item);
                        if (dataDiffers)
                            break;
                        Log.d(TAG, "Data item => " + dataItem);
                    }
                }

                dataEvents.release();
                if (dataDiffers)
                    invalidate();
            }
        }

        private class SunshineResultCallback implements ResultCallback<DataItemBuffer>
        {
            @Override
            public void onResult(@NonNull DataItemBuffer dataItems)
            {
                Log.d(TAG, "On result");
                boolean dataDiffers = false;
                for (DataItem item : dataItems)
                {
                    if (processItem(item))
                        dataDiffers = true;
                    Log.d(TAG, "Data item => " + dataItem);
                }

                dataItems.release();
                if (dataDiffers)
                    invalidate();
            }
        }

        ;
    }
}

