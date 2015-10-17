package com.example.android.sunshine.app;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

/**
 * {@link ForecastAdapter} exposes a list of weather forecasts
 * from a {@link android.database.Cursor} to a {@link android.widget.ListView}.
 */
public class ForecastAdapter extends CursorAdapter {

    private final int VIEW_TYPE_TODAY = 0;
    private final int VIEW_TYPE_FUTURE_DAY = 1;
    private final int VIEW_TYPE_COUNT = 2;

    private boolean mUseSpecialTodayView;

    public ForecastAdapter(Context context, Cursor c, int flags) {
        super(context, c, flags);
    }

    @Override
    public int getItemViewType(int position) {
        return (position == 0 && mUseSpecialTodayView)? VIEW_TYPE_TODAY : VIEW_TYPE_FUTURE_DAY;
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    public void setUseSpecialTodayView(boolean mUseSpecialTodayView) {
        this.mUseSpecialTodayView = mUseSpecialTodayView;
    }

    /*
            Remember that these views are reused as needed.
         */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        // Choose the layout type.
        int viewType = getItemViewType(cursor.getPosition());
        int layoutId;

        switch (viewType){
            case VIEW_TYPE_TODAY:
                layoutId = R.layout.list_item_forecast_today;
                break;
            case VIEW_TYPE_FUTURE_DAY:
                layoutId = R.layout.list_item_forecast;
                break;
            default:
                throw new UnsupportedOperationException("Unknown list item layout");
        }

        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);

        ViewHolder vh = new ViewHolder(view);
        view.setTag(vh);

        return view;
    }

    /*
        This is where we fill-in the views with the contents of the cursor.
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // our view is pretty simple here --- just a text view
        // we'll keep the UI functional with a simple (and slow!) binding.

        ViewHolder vh = (ViewHolder) view.getTag();

        // Read weather icon ID from cursor
        int weatherId = cursor.getInt(ForecastFragment.COL_WEATHER_CONDITION_ID);
        String description = Utility.getStringForWeatherCondition(context, weatherId);
        String artUrl = Utility.getArtUrlForWeatherCondition(context, weatherId);

        // Get art or ic depending on view type
        int resourceId;
        if( getItemViewType(cursor.getPosition()) == VIEW_TYPE_FUTURE_DAY ) {
            resourceId = Utility.getIconResourceForWeatherCondition(weatherId);
        } else {
            resourceId = Utility.getArtResourceForWeatherCondition(weatherId);
        }

        // Use a placeholder image for now
        Glide.with(context).load(artUrl).error(resourceId).into(vh.iconView);

        // Read date from cursor
        long date = cursor.getLong(ForecastFragment.COL_WEATHER_DATE);
        vh.dateView.setText(Utility.getFriendlyDayString(context, date));

        // Read weather forecast from cursor
        vh.forecastView.setText(description);
        vh.forecastView.setContentDescription(context.getString(R.string.a11y_forecast, description));

        // Read user preference for metric
        boolean isMetric = Utility.isMetric(context);

        // Read high temperature from cursor
        double high = cursor.getDouble(ForecastFragment.COL_WEATHER_MAX_TEMP);
        String highStr = Utility.formatTemperature(context, high, isMetric);
        vh.highView.setText(highStr);
        vh.highView.setContentDescription(context.getString(R.string.a11y_high_temp, highStr));

        // Read low temperature from cursor
        double low = cursor.getDouble(ForecastFragment.COL_WEATHER_MIN_TEMP);
        String lowStr = Utility.formatTemperature(context, low, isMetric);
        vh.lowView.setText(lowStr);
        vh.lowView.setContentDescription(context.getString(R.string.a11y_low_temp, lowStr));
    }

    static class ViewHolder {
        ImageView iconView;
        TextView forecastView;
        TextView dateView;
        TextView lowView;
        TextView highView;

        public ViewHolder(View view) {
            iconView = (ImageView) view.findViewById(R.id.list_item_icon);
            forecastView = (TextView) view.findViewById(R.id.list_item_forecast_textview);
            dateView = (TextView) view.findViewById(R.id.list_item_date_textview);
            lowView = (TextView) view.findViewById(R.id.list_item_low_textview);
            highView = (TextView) view.findViewById(R.id.list_item_high_textview);
        }
    }
}