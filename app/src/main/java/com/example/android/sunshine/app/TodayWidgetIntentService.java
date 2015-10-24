package com.example.android.sunshine.app;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
    };
    private static final int WEATHER_ID_INDEX = 0;
    private static final int MAX_TEMP_INDEX = 1;
    private static final int MIN_TEMP_INDEX = 2;

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

        // Get Widget manager
        ComponentName widget = new ComponentName(this, TodayWidgetProvider.class);
        AppWidgetManager manager = AppWidgetManager.getInstance(this);

        // Get bound Ids to this provider.
        int[] ids = manager.getAppWidgetIds(widget);

        int widgetLayout;

        String locationSettings = Utility.getPreferredLocation(this);

        Uri dataUri = WeatherContract.WeatherEntry
                .buildWeatherLocationWithDate(locationSettings, System.currentTimeMillis());

        Cursor cursor = getContentResolver().query(dataUri, mProjection, null, null, null);

        if(cursor != null && cursor.moveToFirst()) {

            for(int id : ids) {

                // Choose the right layout if needed
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    Bundle options = manager.getAppWidgetOptions(id);
                    int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);

                    if (minWidth < 110) {
                        widgetLayout = R.layout.widget_today_small;
                    } else if(minWidth >= 110 && minWidth < 200) {
                        widgetLayout = R.layout.widget_today;
                    } else {
                        widgetLayout = R.layout.widget_today_large;
                    }
                } else {
                    widgetLayout = R.layout.widget_today;
                }

                int weatherId = cursor.getInt(WEATHER_ID_INDEX);
                int weatherArtResourceId = Utility.getArtResourceForWeatherCondition(weatherId);
                String description = Utility.getStringForWeatherCondition(this, weatherId);
                double maxTemp = cursor.getDouble(MAX_TEMP_INDEX);
                String formattedMaxTemperature = Utility.formatTemperature(this, maxTemp,
                        Utility.isMetric(this));

                RemoteViews views = new RemoteViews(this.getPackageName(), widgetLayout);

                // Add data to the RemoteViews
                if(Utility.usingLocalGraphics(this)) {
                    views.setImageViewResource(R.id.detail_icon, weatherArtResourceId);
                } else {

                    int largeIconWidth;
                    int largeIconHeight;

                    Resources resources = getResources();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        largeIconWidth = resources
                                .getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
                        largeIconHeight = resources
                                .getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
                    } else {
                        largeIconWidth = resources
                                .getDimensionPixelSize(R.dimen.notification_large_icon_default);
                        largeIconHeight = largeIconWidth;
                    }

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

                // Set extra info depending on widget layout.
                if(widgetLayout == R.layout.widget_today ||
                        widgetLayout == R.layout.widget_today_large) {

                    double lowTemp = cursor.getDouble(MIN_TEMP_INDEX);
                    String formattedLowTemperature = Utility
                            .formatTemperature(this, lowTemp, Utility.isMetric(this));
                    views.setTextViewText(R.id.detail_low_textview, formattedLowTemperature);

                    if(widgetLayout == R.layout.widget_today_large) {
                        views.setTextViewText(R.id.detail_forecast_textview, description);
                    }
                }

                // Create an Intent to launch the MainActivity.
                Intent intent = new Intent(this, MainActivity.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
                views.setOnClickPendingIntent(R.id.widget, pendingIntent);

                // Update widget views
                manager.updateAppWidget(id, views);

            } // Ends Ids for

            cursor.close();
        }

    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void setRemoteContentDescription(RemoteViews views, String description) {
        views.setContentDescription(R.id.detail_icon, description);
    }
}
