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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final String LOG_TAG = SunshineFace.class.getSimpleName();

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            DataApi.DataListener
    {
        private static final String KEY_HIGH = "com.example.sunshine.datalayer.key.HIGH";
        private static final String KEY_LOW = "com.example.sunshine.datalayer.key.LOW";
        private static final String KEY_ICON = "com.example.sunshine.datalayer.key.ICON";

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        GoogleApiClient mGoogleApiClient;

        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mDatePaint;
        Paint mSeparatorPaint;
        Paint mHighPaint;
        Paint mLowTempPaint;

        boolean mAmbient;
        Calendar mCalendar;
        SimpleDateFormat mDateFormat;
        int mTapCount;

        int mWidth;
        int mHeight;

        float mTimeXOffset;
        float mTimeYOffset;

        float mDateXOffset;
        float mDateYOffset;

        float mSeparatorYOffset;
        float mSeparatorXBaseOffset;

        String mHighTemp;
        String mLowTemp;
        Bitmap mForecastIcon;
        float mForecastXOffset;
        float mForecastYOffset;
        float mForecastIconYOffset;
        float mForecastTextSeparatorSize;

        float mIconSize;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            Log.d("GoogleAPITest", "onCreate!");


            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineFace.this.getResources();
            mTimeYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mDateYOffset = mTimeYOffset + resources.getDimension(R.dimen.date_y_offset);
            mSeparatorYOffset = mDateYOffset + resources.getDimension(R.dimen.separator_y_offset);
            mForecastYOffset = mSeparatorYOffset + resources.getDimension(R.dimen.forecast_y_offset);
            mForecastIconYOffset = mSeparatorYOffset + resources.getDimension(R.dimen.forecast_icon_y_offset);

            mIconSize = resources.getDimension(R.dimen.icon_size);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(SunshineFace.this.getBaseContext(), R.color.primary));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(ContextCompat.getColor(SunshineFace.this.getBaseContext(), R.color.digital_text));

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(ContextCompat.getColor(SunshineFace.this.getBaseContext(), R.color.primary_light));

            mSeparatorPaint = new Paint();
            mSeparatorPaint.setColor(ContextCompat.getColor(SunshineFace.this.getBaseContext(), R.color.primary_light));

            mHighPaint = new Paint();
            mHighPaint = createTextPaint(ContextCompat.getColor(SunshineFace.this.getBaseContext(), R.color.digital_text));

            mLowTempPaint = new Paint();
            mLowTempPaint = createTextPaint(ContextCompat.getColor(SunshineFace.this.getBaseContext(), R.color.primary_light));
            mForecastTextSeparatorSize = resources.getDimension(R.dimen.forecast_padding);

            mCalendar = Calendar.getInstance();
            mDateFormat = new SimpleDateFormat(getString(R.string.date_format), Locale.getDefault());

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineFace.this.getBaseContext())
                    .addApiIfAvailable(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();


            Log.d("GoogleAPITest", "Connecting client!");


            mGoogleApiClient.connect();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineFace.this.getResources();

            boolean isRound = insets.isRound();

            // Time measurements
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            mTextPaint.setTextSize(textSize);
            float textWidth = mTextPaint.measureText("59:59");
            mTimeXOffset = (mWidth - textWidth) / 2;

            // Date measurements
            textSize = resources.getDimension(isRound
                ? R.dimen.date_text_size_round : R.dimen.date_text_size);
            String baseDateText = mDateFormat.format(mCalendar.getTime());
            mDatePaint.setTextSize(textSize);
            textWidth = mDatePaint.measureText(baseDateText);
            mDateXOffset = (mWidth - textWidth) / 2;

            // Separator measurements
            float separatorWidth = 40; // TODO: 20/05/16 Change to smaller value
            mSeparatorXBaseOffset = (mWidth - separatorWidth) / 2;

            // Forecast measurements
            textSize = resources.getDimension(isRound
                    ? R.dimen.forecast_text_size_round : R.dimen.forecast_text_size
            );

            mHighPaint.setTextSize(textSize);
            mLowTempPaint.setTextSize(textSize);

            textSize = mLowTempPaint.measureText("99º");
            float forecastTotalLength =
                    mIconSize + mForecastTextSeparatorSize +
                    textSize + mForecastTextSeparatorSize +
                    textSize;

            mForecastXOffset = (mWidth - forecastTotalLength) / 2;
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {

                // Change colors
                adjustColorToMode(mBackgroundPaint, R.color.primary, R.color.background);
                adjustColorToMode(mDatePaint, R.color.primary_light, R.color.digital_text);

                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    boolean antiAlias = !inAmbientMode;
                    mTextPaint.setAntiAlias(antiAlias);
                    mDatePaint.setAntiAlias(antiAlias);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void adjustColorToMode(Paint paint, int nonAmbient, int ambient) {
            paint.setColor(ContextCompat.getColor(getBaseContext(), isInAmbientMode() ? ambient : nonAmbient));
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {

            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    int backColor = mTapCount % 2 == 0 ?
                            R.color.primary : R.color.background2;
                    mBackgroundPaint.setColor(ContextCompat.getColor(SunshineFace.this.getBaseContext(), backColor));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM.
            mCalendar.setTimeInMillis(System.currentTimeMillis());

            int hour = mCalendar.get(Calendar.HOUR_OF_DAY);
            int minute = mCalendar.get(Calendar.MINUTE);

            String timeText = String.format(Locale.getDefault(), "%d:%02d", hour, minute);

            canvas.drawText(timeText, mTimeXOffset, mTimeYOffset, mTextPaint);

            // Draw current date
            String dateText = mDateFormat.format(mCalendar.getTime());

            canvas.drawText(dateText, mDateXOffset, mDateYOffset, mDatePaint);

            if(!isInAmbientMode()) {
                // Draw separator
                canvas.drawLine(
                        mSeparatorXBaseOffset,
                        mSeparatorYOffset,
                        mSeparatorXBaseOffset + 40, // TODO: 20/05/16 Calculate every second
                        mSeparatorYOffset,
                        mSeparatorPaint
                );

                if(mLowTemp != null && mHighTemp != null && mForecastIcon != null) {
                    canvas.drawBitmap(
                            mForecastIcon,
                            mForecastXOffset,
                            mForecastIconYOffset,
                            mBackgroundPaint
                    );
                    canvas.drawText(
                            mHighTemp,
                            mForecastXOffset + mIconSize + mForecastTextSeparatorSize,
                            mForecastYOffset,
                            mHighPaint
                    );
                    canvas.drawText(
                            mLowTemp,
                            mForecastXOffset + mIconSize +
                                    mForecastTextSeparatorSize +
                                    mHighPaint.measureText(mHighTemp) +
                                    mForecastTextSeparatorSize,
                            mForecastYOffset,
                            mLowTempPaint
                    );
                }
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(LOG_TAG, "Connected!");
            Wearable.DataApi.addListener(mGoogleApiClient, this);

            // Sent request for data
            PutDataRequest putDataRequest = PutDataRequest.create("/weather-data-requested");
            putDataRequest.setUrgent();

            PendingResult<DataApi.DataItemResult> pendingResult =
                    Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);

            pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                    if(dataItemResult.getStatus().isSuccess()) {
                        Log.d(LOG_TAG, "Data item sent: " + dataItemResult.getDataItem().getUri());
                    } else {
                        Log.d(LOG_TAG, "Result unsuccessful");
                    }
                }
            });
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(LOG_TAG, "Connection Suspended!");

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

            Log.d(LOG_TAG, "Connection Failed! " + connectionResult.getErrorMessage());

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for(DataEvent event : dataEventBuffer) {
                Log.d(LOG_TAG, "Item received " + event.getDataItem().getUri().getPath());
                if(event.getType() == DataEvent.TYPE_CHANGED) {

                    DataItem item = event.getDataItem();
                    if(item.getUri().getPath().compareTo("/sunshine/weather") == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        updateWeatherInfo(
                                dataMap.getString(KEY_HIGH),
                                dataMap.getString(KEY_LOW),
                                dataMap.getByteArray(KEY_ICON)
                        );
                    }
                }
            }
        }

        private void updateWeatherInfo(String high, String low, byte[] iconBytes) {

            Log.d(LOG_TAG, "Received: " + high + " " + low + " icon bytes: " + iconBytes.length);

            mLowTemp = low;
            mHighTemp = high;
            Bitmap bitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.length);
            mForecastIcon = Bitmap.createScaledBitmap(bitmap, (int)mIconSize, (int)mIconSize, true);

            invalidate();

        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineFace.Engine> mWeakReference;

        public EngineHandler(SunshineFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
