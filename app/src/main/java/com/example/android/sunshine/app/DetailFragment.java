package com.example.android.sunshine.app;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.util.Util;
import com.example.android.sunshine.app.data.WeatherContract;

import java.util.concurrent.ExecutionException;

/**
 * A placeholder fragment containing a simple view.
 */
public class DetailFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    // Action provider for sharing.
    private ShareActionProvider mShareActionProvider;
    private String mForecast;
    private static final String HASH_TAG_SUNSHINE = " #SunshineApp";
    private static final String LOG_TAG = DetailActivity.class.getSimpleName();

    // Loaders Ids
    private int DETAIL_LOADER = 0;

    // Projection fro weather data
    public static final String[] DETAIL_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_HUMIDITY,
            WeatherContract.WeatherEntry.COLUMN_WIND_SPEED,
            WeatherContract.WeatherEntry.COLUMN_DEGREES,
            WeatherContract.WeatherEntry.COLUMN_PRESSURE,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID
    };

    // Column indices
    static final int COL_WEATHER_DATE = 0;
    static final int COL_WEATHER_DESC = 1;
    static final int COL_WEATHER_MAX_TEMP = 2;
    static final int COL_WEATHER_MIN_TEMP = 3;
    static final int COL_WEATHER_HUMIDITY = 4;
    static final int COL_WEATHER_WIND_SPEED = 5;
    static final int COL_WEATHER_DEGREES = 6;
    static final int COL_WEATHER_PRESSURE = 7;
    static final int COL_WEATHER_CONDITION_ID = 8;

    // Ui elements
    private TextView mDayTextView;
    private TextView mMonthTextView;
    private TextView mHighTextView;
    private TextView mLowTextView;
    private TextView mHumidityTextView;
    private TextView mWindTextView;
    private TextView mPressureTextView;
    private TextView mForecastTextView;
    private ImageView mIconView;

    // Uri to get Data.
    private Uri mUri;

    public DetailFragment() {
    }

    public static  DetailFragment newInstance(Uri dateUri) {
        DetailFragment f = new DetailFragment();

        // Supply uri to fragment
        Bundle args = new Bundle();
        args.putParcelable("dateUri", dateUri);

        f.setArguments(args);

        return f;
    }

    public Uri getDateUri() {
        if(getArguments() != null) {
            return getArguments().getParcelable("dateUri");
        }
        return null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Get views to bind with values
        View rootView = inflater.inflate(R.layout.fragment_detail, container, false);
        mDayTextView = (TextView) rootView.findViewById(R.id.detail_day_textview);
        mMonthTextView = (TextView) rootView.findViewById(R.id.detail_month_textview);
        mHighTextView = (TextView) rootView.findViewById(R.id.detail_high_textview);
        mLowTextView = (TextView) rootView.findViewById(R.id.detail_low_textview);
        mHumidityTextView = (TextView) rootView.findViewById(R.id.detail_humidity_textview);
        mWindTextView = (TextView) rootView.findViewById(R.id.detail_wind_textview);
        mPressureTextView = (TextView) rootView.findViewById(R.id.detail_preassure_textview);
        mForecastTextView = (TextView) rootView.findViewById(R.id.detail_forecast_textview);
        mIconView = (ImageView) rootView.findViewById(R.id.detail_icon);

        // Get intent that called the activity.
        Intent detailIntent = getActivity().getIntent();

        mUri = getDateUri();

        getLoaderManager().initLoader(DETAIL_LOADER, detailIntent.getExtras(), this);

        // Ask for menu events.
        setHasOptionsMenu(true);

        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        // Inflate Resource file
        inflater.inflate(R.menu.detailfragment, menu);

        // Locate MenuItem with ShareActionProvider
        MenuItem item = menu.findItem(R.id.action_share);

        // Store the ShareActionProvider
        mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);

        if(mShareActionProvider != null){
            if(mForecast != null){
                mShareActionProvider.setShareIntent(createForecastIntent());
            }
        }else {
            Log.e(LOG_TAG, "Share Action Provider is null!");
        }

    }

    // Create the shareIntent
    private Intent createForecastIntent() {
        // Form data to share
        String extraText = mForecast + HASH_TAG_SUNSHINE;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        // Prevent app from staying in the stack and returning to the sharing state.
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        shareIntent.putExtra(Intent.EXTRA_TEXT, extraText);
        shareIntent.setType("text/plain");

        return shareIntent;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Intent intent = getActivity().getIntent();

        Uri dateUri;

        if(intent == null || intent.getData() == null) {
            dateUri = mUri;
            if(dateUri == null) {
                return null;
            }
        } else {
            dateUri = intent.getData();
        }

        return new CursorLoader(
                getActivity(),
                dateUri,
                DETAIL_COLUMNS,
                null,
                null,
                null
        );
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if( data != null && data.moveToFirst() ){

            // Populate ui
            int weatherId = data.getInt(COL_WEATHER_CONDITION_ID);
            int artResourceId = Utility.getArtResourceForWeatherCondition(weatherId);
            String artUrl = Utility.getArtUrlForWeatherCondition(getActivity(), weatherId);
            Glide.with(getActivity())
                    .load(artUrl)
                    .error(artResourceId)
                    .into(mIconView);

            long date = data.getLong(COL_WEATHER_DATE);
            mDayTextView.setText(Utility.getDayName(
                    getActivity(),
                    date
            ));
            mMonthTextView.setText(Utility.getFormattedMonthDay(
                    getActivity(),
                    date));

            boolean isMetric = Utility.isMetric(getActivity());

            String highStr = Utility.formatTemperature(
                    getActivity(), data.getDouble(COL_WEATHER_MAX_TEMP), isMetric);
            mHighTextView.setText(highStr);
            mHighTextView.setContentDescription(getString(R.string.a11y_high_temp, highStr));

            String lowStr = Utility.formatTemperature(
                    getActivity(), data.getDouble(COL_WEATHER_MIN_TEMP), isMetric);
            mLowTextView.setText(lowStr);
            mLowTextView.setContentDescription(getString(R.string.a11y_low_temp, lowStr));

            String description = Utility.getStringForWeatherCondition(getActivity(), weatherId);
            mForecastTextView.setText(description);
            mForecastTextView.setContentDescription(getString(R.string.a11y_forecast, description));

            /* For accessibility, ad a content descripton for the icon field.
             * Because the ImageView is indepently focusable, it's better to have a description
             * of the image. Using null is appropiate when the image is purely decorative or when
             * the image already has text describing it in the same UI component.
             */

            mIconView.setContentDescription(getString(R.string.a11y_forecast_icon, description));

            mHumidityTextView.setText(getString(R.string.format_humidity,
                    data.getFloat(COL_WEATHER_HUMIDITY)));
            mHumidityTextView.setContentDescription(mHumidityTextView.getText());

            mWindTextView.setText(Utility.getFormattedWind(
                    getActivity(),
                    data.getFloat(COL_WEATHER_WIND_SPEED),
                    data.getFloat(COL_WEATHER_DEGREES)));
            mWindTextView.setContentDescription(mWindTextView.getText());

            mPressureTextView.setText(getString(R.string.format_pressure,
                    data.getFloat(COL_WEATHER_PRESSURE)));
            mPressureTextView.setContentDescription(mPressureTextView.getText());

            // Get share string
            mForecast = convertCursorRowToUXFormat(data);

            // Set if on createOptionMenu has already happened
            if(mForecast != null && mShareActionProvider != null) {
                mShareActionProvider.setShareIntent(createForecastIntent());
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }

    /*
        This is ported from FetchWeatherTask --- but now we go straight from the cursor to the
        string.
     */
    private String convertCursorRowToUXFormat(Cursor cursor) {

        String highAndLow = formatHighLows(
                cursor.getDouble(COL_WEATHER_MAX_TEMP),
                cursor.getDouble(COL_WEATHER_MIN_TEMP));

        return Utility.formatDate(cursor.getLong(COL_WEATHER_DATE)) +
                " - " + cursor.getString(COL_WEATHER_DESC) +
                " - " + highAndLow;
    }

    /**
     * Prepare the weather high/lows for presentation.
     */
    private String formatHighLows(double high, double low) {
        boolean isMetric = Utility.isMetric(getActivity());
        return Utility.formatTemperature(
                getActivity(),
                high,
                isMetric) + "/" +
                Utility.formatTemperature(getActivity(), low, isMetric);
    }

    void onLocationChanged( String newLocation ) {
        // Replace the uri, since the has changed.
        Uri uri = mUri;
        if(uri != null) {
            long date = WeatherContract.WeatherEntry.getDateFromUri(uri);
            Uri updatedUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(newLocation, date);
            mUri = updatedUri;
            getLoaderManager().restartLoader(DETAIL_LOADER, null, this);
        }
    }

}