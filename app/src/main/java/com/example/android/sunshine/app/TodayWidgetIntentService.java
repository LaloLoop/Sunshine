package com.example.android.sunshine.app;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

import com.bumptech.glide.Glide;
import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.widget.TodayWidgetProvider;

import java.util.concurrent.ExecutionException;


/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions and extra parameters.
 */
public class TodayWidgetIntentService extends IntentService {

    private static final String [] mProjection = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP
    };
    private static final int WEATHER_ID_INDEX = 0;
    private static final int SHORT_DESC_INDEX = 1;
    private static final int MAX_TEMP_INDEX = 2;

    public TodayWidgetIntentService() {
        super("TodayWidgetIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            handleUpdateWidget();
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleUpdateWidget() {

        String locationSettings = Utility.getPreferredLocation(this);
        Uri dataUri = WeatherContract.WeatherEntry
                .buildWeatherLocationWithDate(locationSettings, System.currentTimeMillis());

        Cursor cursor = getContentResolver().query(dataUri, mProjection, null, null, null);

        if(cursor.moveToFirst()) {

            int weatherId = cursor.getInt(WEATHER_ID_INDEX);
            int weatherArtResourceId = Utility.getArtResourceForWeatherCondition(weatherId);
            String description = cursor.getString(SHORT_DESC_INDEX);
            double maxTemp = cursor.getDouble(MAX_TEMP_INDEX);
            String formattedMaxTemperature = Utility.formatTemperature(this, maxTemp,
                    Utility.isMetric(this));

            RemoteViews views = new RemoteViews(this.getPackageName(),R.layout.widget_today_small);

            // Add data to the RemoteViews
            if(Utility.usingLocalGraphics(this)) {
                views.setImageViewResource(R.id.detail_icon, weatherArtResourceId);
            } else {
                int largeIconWidth = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
                        ? getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
                        : getResources().getDimensionPixelSize(R.dimen.notification_large_icon_default);
                int largeIconHeight = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
                        ? getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_height)
                        : getResources().getDimensionPixelSize(R.dimen.notification_large_icon_default);

                String artUrl = Utility.getArtUrlForWeatherCondition(this, weatherId);

                try {

                    Bitmap largeIcon = Glide.with(this)
                            .load(artUrl)
                            .asBitmap()
                            .error(weatherArtResourceId)
                            .into(largeIconWidth, largeIconHeight)
                            .get();

                    views.setImageViewBitmap(R.id.detail_icon, largeIcon);

                } catch(InterruptedException | ExecutionException e) {
                    views.setImageViewResource(R.id.detail_icon, weatherArtResourceId);
                }
            }

            // Content description for the icon but only prior to ICMS MR1
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                setRemoteContentDescription(views, description);
            }

            views.setTextViewText(R.id.detail_high_textview, formattedMaxTemperature);

            // Create an Intent to launch the MainActivity.
            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
            views.setOnClickPendingIntent(R.id.widget, pendingIntent);

            // Update widget views
            ComponentName widget = new ComponentName(this, TodayWidgetProvider.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(widget, views);
        }

    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void setRemoteContentDescription(RemoteViews views, String description) {
        views.setContentDescription(R.id.detail_icon, description);
    }
}
