package com.example.android.sunshine.app.wear;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.ByteArrayOutputStream;

/**
 * Listener to wearable data events
 * Created by lalo on 22/05/16.
 */
public class WearDataListener extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String LOG_TAG = WearDataListener.class.getSimpleName();
    public static final String DATA_ITEM_RECEIVED = "/weather-data-requested";

    // Projection and indices to query notification info
    private static final String[] WEARABLE_WEATHER_PROJECTION = new String[] {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;

    private static final String KEY_HIGH = "com.example.sunshine.datalayer.key.HIGH";
    private static final String KEY_LOW = "com.example.sunshine.datalayer.key.LOW";
    private static final String KEY_ICON = "com.example.sunshine.datalayer.key.ICON";
    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(LOG_TAG, messageEvent.getPath());
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {

        Log.d(LOG_TAG, "onDataChanged");

        // Loop through events
        for(DataEvent dataEvent :  dataEventBuffer) {
            Log.d(LOG_TAG, "Event received on path: " +  dataEvent.getDataItem().getUri().getPath());

            if(dataEvent.getDataItem().getUri().getPath().compareTo(DATA_ITEM_RECEIVED) == 0) {

                mGoogleApiClient = new GoogleApiClient.Builder(getBaseContext())
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApiIfAvailable(Wearable.API)
                        .build();

                mGoogleApiClient.connect();

            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(LOG_TAG, "Connected!");
        // Send weather data
        queryAndSendWeatherData(mGoogleApiClient);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(LOG_TAG, "Connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(LOG_TAG, "Connection failed");


    }

    private void queryAndSendWeatherData(GoogleApiClient googleApiClient) {
        Context context = getBaseContext();

        Cursor cursor = null;

        String locationQuery = Utility.getPreferredLocation(context);
        Uri weatherUri =
                WeatherContract.WeatherEntry.buildWeatherLocationWithDate(
                        locationQuery, System.currentTimeMillis()
                );

        try {

            cursor = context.getContentResolver().query(weatherUri, WEARABLE_WEATHER_PROJECTION, null, null, null);

            if(cursor != null && cursor.moveToFirst()) {
                int weatherId = cursor.getInt(INDEX_WEATHER_ID);
                double high = cursor.getDouble(INDEX_MAX_TEMP);
                double low = cursor.getDouble(INDEX_MIN_TEMP);

                int iconId = Utility.getIconResourceForWeatherCondition(weatherId);

                boolean isMetric = Utility.isMetric(context);

                String highFormatted = Utility.formatTemperature(context, high, isMetric);
                String lowFormatted = Utility.formatTemperature(context, low, isMetric);
                Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), iconId);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                largeIcon.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] iconData = baos.toByteArray();

                // Send data
                PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/sunshine/weather");
                putDataMapRequest.getDataMap().putString(KEY_HIGH, highFormatted);
                putDataMapRequest.getDataMap().putString(KEY_LOW, lowFormatted);
                putDataMapRequest.getDataMap().putByteArray(KEY_ICON, iconData);
                putDataMapRequest.getDataMap().putDouble("change", Math.random());

                PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
                putDataRequest.setUrgent();

                Log.d(LOG_TAG, "Sending event: " + putDataRequest.getUri().getPath());

                PendingResult<DataApi.DataItemResult> pendingResult =
                        Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);

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

        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

    }
}
