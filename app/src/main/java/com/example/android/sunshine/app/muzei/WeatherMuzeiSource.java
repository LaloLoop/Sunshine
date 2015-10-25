package com.example.android.sunshine.app.muzei;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import com.bumptech.glide.util.Util;
import com.example.android.sunshine.app.MainActivity;
import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.apps.muzei.api.Artwork;
import com.google.android.apps.muzei.api.MuzeiArtSource;

/**
 * Dynamic Wallpapers for Sunshine using Muzei
 * Created by lalo on 24/10/15.
 */
public class WeatherMuzeiSource extends MuzeiArtSource {

    private static final String[] WEATHER_PROJECTION = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID
    };

    private static final int WEATHER_ID_INDEX = 0;

    public WeatherMuzeiSource() {
        super("WeatherMuzeiSource");
    }

    @Override
    protected void onUpdate(int reason) {
        String locationSettings = Utility.getPreferredLocation(this);
        Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(
                locationSettings, System.currentTimeMillis());

        Cursor cursor = getContentResolver()
                .query(weatherUri, WEATHER_PROJECTION, null, null, null);
        if(cursor != null && cursor.moveToFirst()) {
            int weatherId = cursor.getInt(WEATHER_ID_INDEX);
            String desc = Utility.getStringForWeatherCondition(this, weatherId);

            String imageUrl = Utility.getImageUrlForWeatherCondition(this, weatherId);
            if(imageUrl != null) {
                publishArtwork(new Artwork.Builder()
                .imageUri(Uri.parse(imageUrl))
                .title(desc)
                .byline(locationSettings)
                .viewIntent(new Intent(this, MainActivity.class))
                .build());
            }
            cursor.close();
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        super.onHandleIntent(intent);
        boolean dataUpdated = (intent != null &&
                SunshineSyncAdapter.ACTION_DATA_UPDATED.equals(intent.getAction()));
        if(dataUpdated && isEnabled()) {
            onUpdate(UPDATE_REASON_OTHER);
        }
    }
}
