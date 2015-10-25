package com.example.android.sunshine.app;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.bumptech.glide.Glide;
import com.example.android.sunshine.app.data.WeatherContract;

import java.util.concurrent.ExecutionException;

/**
 * Remote Service for Detail Widget
 * Created by lalo on 24/10/15.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class DetailWidgetRemoteViewsService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new DetailWidgetRemoteViewsFactory(this, intent);
    }

    class DetailWidgetRemoteViewsFactory implements RemoteViewsFactory {

        private final String [] mProjection = {
                WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
                WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
                WeatherContract.WeatherEntry.COLUMN_DATE,
                WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
                WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
        };

        private final int _ID_INDEX = 0;
        private final int WEATHER_ID_INDEX = 1;
        private final int DATE_INDEX = 2;
        private final int MAX_TEMP_INDEX = 3;
        private final int MIN_TEMP_INDEX = 4;

        private Context mContext;
        private int mAppWidgetId;
        private Cursor mCursor;
        private String mLocationSettings;

        public DetailWidgetRemoteViewsFactory(Context mContext, Intent intent) {
            this.mContext = mContext;
            mAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        @Override
        public void onCreate() {

        }

        @Override
        public void onDataSetChanged() {
            if(mCursor != null) {
                mCursor.close();
            }

            mLocationSettings = Utility.getPreferredLocation(mContext);
            Uri daraUri = WeatherContract.WeatherEntry
                    .buildWeatherLocationWithStartDate(mLocationSettings, System.currentTimeMillis());

            // Revert back to our process' identity so we can work with our content provider.
            final long identityToken = Binder.clearCallingIdentity();

            mCursor = getContentResolver().query(daraUri, mProjection, null, null, null);

            // Restore identity.
            Binder.restoreCallingIdentity(identityToken);
        }

        @Override
        public void onDestroy() {
            if(mCursor != null) {
                mCursor.close();
            }
        }

        @Override
        public int getCount() {
            return mCursor != null ? mCursor.getCount() : 0;
        }

        @Override
        public RemoteViews getViewAt(int i) {
            if(mCursor != null && mCursor.moveToPosition(i)) {

                // Read / Format data from cursor
                int weatherId = mCursor.getInt(WEATHER_ID_INDEX);
                int iconResourceId = Utility.getIconResourceForWeatherCondition(weatherId);
                long dateMillis = mCursor.getLong(DATE_INDEX);
                String formattedDate = Utility.getFriendlyDayString(mContext, dateMillis);
                String description = Utility.getStringForWeatherCondition(mContext, weatherId);
                double highTemp = mCursor.getDouble(MAX_TEMP_INDEX);
                double lowTemp = mCursor.getDouble(MIN_TEMP_INDEX);
                String formattedHighTemperature = Utility
                        .formatTemperature(mContext, highTemp, Utility.isMetric(mContext));
                String formattedLowTemperature = Utility
                        .formatTemperature(mContext, lowTemp, Utility.isMetric(mContext));

                // Create remote view.
                RemoteViews rv = new RemoteViews(
                        mContext.getPackageName(), R.layout.widget_detail_list_item);

                // Set list icon
                if(Utility.usingLocalGraphics(mContext)) {
                    rv.setImageViewResource(R.id.list_item_icon, iconResourceId);
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

                    String artUrl = Utility.getArtUrlForWeatherCondition(mContext, weatherId);

                    try {

                        Bitmap largeIcon = Glide.with(mContext)
                                .load(artUrl)
                                .asBitmap()
                                .error(iconResourceId)
                                .into(largeIconWidth, largeIconHeight)
                                .get();

                        rv.setImageViewBitmap(R.id.detail_icon, largeIcon);

                    } catch(InterruptedException | ExecutionException e) {
                        rv.setImageViewResource(R.id.detail_icon, iconResourceId);
                    }
                }
                // Content description for icon.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                    rv.setContentDescription(R.id.list_item_icon, description);
                }

                // Date
                rv.setTextViewText(R.id.list_item_date_textview, formattedDate);

                // Description
                rv.setTextViewText(R.id.list_item_forecast_textview, description);

                // High
                rv.setTextViewText(R.id.list_item_high_textview, formattedHighTemperature);

                // Low
                rv.setTextViewText(R.id.list_item_low_textview, formattedLowTemperature);

                // Fill intent
                Uri dateUri = WeatherContract.WeatherEntry
                        .buildWeatherLocationWithDate(mLocationSettings, dateMillis);
                Intent fillIntent = new Intent();
                fillIntent.setData(dateUri);

                rv.setOnClickFillInIntent(R.id.widget_item, fillIntent);

                return rv;
            }
            return null;
        }

        @Override
        public RemoteViews getLoadingView() {
            return new RemoteViews(getPackageName(), R.layout.widget_detail_list_item);
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int i) {
            if(mCursor != null && mCursor.moveToPosition(i)) {
                return mCursor.getLong(_ID_INDEX);
            }
            return 0;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }

}
