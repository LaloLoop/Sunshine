package com.example.android.sunshine.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.StringRes;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;


/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>,
        SharedPreferences.OnSharedPreferenceChangeListener, ForecastAdapter.ViewHolder.ForecastViewHolderClick {
    private final String LOG_TAG = ForecastFragment.class.getSimpleName();

    // Main list adapter
    private ForecastAdapter mForecastAdapter;

    // Cursor loader
    private final int WEATHER_CURSOR_ID = 0;
    private final int LOCATION_CURSOR_ID = 1;

    private final String EXTRA_ITEM_POSITION = "itemPosition";


    public static final String[] FORECAST_COLUMNS = {
            // In this case the id needs to be fully qualified with a table name, since
            // the content provider joins the location & weather tables in the background
            // (both have an _id column)
            // On the one hand, that's annoying.  On the other, you can search the weather table
            // using the location set by the user, which is only in the Location table.
            // So the convenience is worth it.
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.LocationEntry.COLUMN_COORD_LAT,
            WeatherContract.LocationEntry.COLUMN_COORD_LONG
    };

    // These indices are tied to FORECAST_COLUMNS.  If DETAIL_COLUMNS changes, these
    // must change.
    static final int COL_WEATHER_ID = 0;
    static final int COL_WEATHER_DATE = 1;
    static final int COL_WEATHER_DESC = 2;
    static final int COL_WEATHER_MAX_TEMP = 3;
    static final int COL_WEATHER_MIN_TEMP = 4;
    static final int COL_LOCATION_SETTING = 5;
    static final int COL_WEATHER_CONDITION_ID = 6;
    static final int COL_COORD_LAT = 7;
    static final int COL_COORD_LONG = 8;

    private static final String[] LOCATION_COLUMNS = {
        WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING,
        WeatherContract.LocationEntry.COLUMN_CITY_NAME,
        WeatherContract.LocationEntry.COLUMN_COORD_LAT,
        WeatherContract.LocationEntry.COLUMN_COORD_LONG,
    };

    // Column index for location
    static final int COL_L_LOCATION_SETTING = 0;
    static final int COL_L_CITY_NAME = 1;
    static final int COL_L_COORD_LAT = 2;
    static final int COL_L_COORD_LONG = 3;

    // Vars for map feature
    private long mLatLong[];
    private String mCityName;
    private int mPosition = RecyclerView.NO_POSITION;
    private RecyclerView mRecyclerView;
    private boolean mUseTodayView;

    // Empty ListView.
    private TextView mEmptyWeatherView;
    private View mParallaxBar;
    private RecyclerView.OnScrollListener mScrollListener;

    @SunshineSyncAdapter.LocationStatus
    private int mSyncStatus;

    // Postpone animation
    private static final boolean DEFAULT_POSTPONE_ANIMATION = false;
    private boolean mPostponeAnimation = DEFAULT_POSTPONE_ANIMATION;

    public ForecastFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             final Bundle savedInstanceState) {
        // Inflate main fragment view
        View fragmentView = inflater.inflate(R.layout.fragment_main, container, false);

        // Get reference to ListView to set adapter
        mRecyclerView = (RecyclerView) fragmentView.findViewById(R.id.recyclerview_forecast);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        mEmptyWeatherView = (TextView) fragmentView.findViewById(R.id.no_weather_info_view);

        mForecastAdapter = new ForecastAdapter(null, getActivity(), this, mEmptyWeatherView);
        mForecastAdapter.setUseSpecialTodayView(mUseTodayView);

        // Indicate that we want to receive onCreateOptionsMenu call.
        this.setHasOptionsMenu(true);

        if(savedInstanceState != null && savedInstanceState.containsKey(EXTRA_ITEM_POSITION)) {
            mPosition = savedInstanceState.getInt(EXTRA_ITEM_POSITION);
        }

        // Set the adapter
        mRecyclerView.setAdapter(mForecastAdapter);

        // GetParallax bar
        mParallaxBar = fragmentView.findViewById(R.id.parallax_bar);
        if(mParallaxBar != null) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {

                mScrollListener = new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                        super.onScrolled(recyclerView, dx, dy);
                        int max = mParallaxBar.getHeight();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                            if (dy > 0) {

                                mParallaxBar.setTranslationY(
                                        Math.max(-max, mParallaxBar.getTranslationY() - (dy / 2))
                                );

                            } else {
                                mParallaxBar.setTranslationY(
                                        Math.min(0, mParallaxBar.getTranslationY() - (dy / 2))
                                );
                            }
                        }
                    }
                };

                mRecyclerView.addOnScrollListener(mScrollListener);
            }
        }

        final AppBarLayout appbarView = (AppBarLayout) fragmentView.findViewById(R.id.appbar);
        if(appbarView != null) {
            ViewCompat.setElevation(appbarView, 0);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                        if(mRecyclerView.computeVerticalScrollOffset() == 0) {
                            appbarView.setElevation(0);
                        } else {
                            appbarView.setElevation(appbarView.getTargetElevation());
                        }
                    }
                });
            }
        }

        return fragmentView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if(mPosition != ListView.INVALID_POSITION) {
            outState.putInt(EXTRA_ITEM_POSITION, mPosition);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onInflate(Activity activity, AttributeSet attrs, Bundle savedInstanceState) {
        super.onInflate(activity, attrs, savedInstanceState);
        TypedArray ta = activity.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.ForecastFragment,
                0, 0
        );

        try {
            mPostponeAnimation = ta.getBoolean(R.styleable.ForecastFragment_postponeAnimation,
                    DEFAULT_POSTPONE_ANIMATION);
        } finally {
            ta.recycle();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
//        if(id == R.id.action_refresh){
//
//            updateWeather();
//            return true;
//
//        }else
        if(id == R.id.action_view_map){

            // Init Location data cursor.
            getLoaderManager().initLoader(LOCATION_CURSOR_ID, null, this);

        }

        return super.onOptionsItemSelected(item);
    }

    // Create Location Uri.
    private Uri formLocationData() {
        Uri geoUriBuilder = null;
        if(mCityName != null && mLatLong != null) {

             geoUriBuilder = Uri.parse("geo:0,0?").buildUpon()
                     .appendQueryParameter("q", String.valueOf(mLatLong[0]) + "," + String.valueOf(mLatLong[1]) + "(" + mCityName + ")").build();

        }

        return geoUriBuilder;
    }

    private void updateWeather() {
        SunshineSyncAdapter.syncImmediately(getActivity());
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        mSyncStatus = Utility.getSyncStatus(getActivity());
    }

    @Override
    public void onPause() {
        super.onPause();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mRecyclerView.clearOnScrollListeners();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        // Init weather data cursor.

        if(mPostponeAnimation) {
            getActivity().supportPostponeEnterTransition();
        }
        getLoaderManager().initLoader(WEATHER_CURSOR_ID, null, this);

        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        String locationSetting = Utility.getPreferredLocation(getActivity());

        switch (id) {
            case WEATHER_CURSOR_ID:

                // Sort order: Ascending, by date.
                String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";
                Uri weatherForecastLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                        locationSetting,
                        System.currentTimeMillis()
                );

                return new CursorLoader(
                        getActivity(),
                        weatherForecastLocationUri,
                        FORECAST_COLUMNS,
                        null,
                        null,
                        sortOrder);

            case LOCATION_CURSOR_ID:
                return new CursorLoader(
                        getActivity(),
                        WeatherContract.LocationEntry.CONTENT_URI,
                        LOCATION_COLUMNS,
                        WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
                        new String[] { locationSetting },
                        null
                        );
            default:
                Log.e(LOG_TAG, "Unknown cursor ID: " + id);
                break;
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        int loaderId = loader.getId();

        switch (loaderId) {
            case WEATHER_CURSOR_ID:


                mForecastAdapter.swapCursor(data);

                if (mPosition != RecyclerView.NO_POSITION) {
                    mRecyclerView.scrollToPosition(mPosition);
                }

                updateEmptyView();

                if(data.getCount() == 0) {
                    getActivity().supportStartPostponedEnterTransition();
                } else {
                    mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            if(mRecyclerView.getChildCount() > 0) {
                                mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
                                if (mPostponeAnimation) {
                                    getActivity().supportStartPostponedEnterTransition();
                                }
                                return true;
                            }
                            return false;
                        }
                    });
                }

                break;
            case LOCATION_CURSOR_ID:
                if(data.moveToFirst()){
                    mCityName = data.getString(COL_L_CITY_NAME);
                    mLatLong = new long[]{ data.getLong(COL_L_COORD_LAT), data.getLong(COL_L_COORD_LONG) };

                    Intent viewOnMapIntent = new Intent(Intent.ACTION_VIEW);
                    Uri geoUri = formLocationData();

                    if(geoUri != null) {
                        Log.v(LOG_TAG, "Geo Uri: " + geoUri.toString());

                        viewOnMapIntent.setData(geoUri);

                        // Check if we have any app that can handle the map viewing.
                        if(viewOnMapIntent.resolveActivity(getActivity().getPackageManager()) != null){

                            startActivity(viewOnMapIntent);

                        }else {
                            Toast.makeText(getActivity(), getString(R.string.error_no_maps_app), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Log.w(LOG_TAG, "Geo Uri was null");
                    }

                }
                break;
            default:
                Log.e(LOG_TAG, "Unknown cursor ID: " + loaderId);
                break;
        }
    }

    private void updateEmptyView() {
        if(mForecastAdapter.getItemCount() == 0) {
            mEmptyWeatherView.setText(R.string.no_weather_data);

            @StringRes
            int msgId = 0;

            if(!Utility.checkNetworkConnected(getActivity())) {
                msgId = R.string.no_network_available;
            } else if(mSyncStatus == SunshineSyncAdapter.LOCATION_STATUS_SERVER_DOWN){
                msgId = R.string.server_is_down;
            } else if(mSyncStatus == SunshineSyncAdapter.LOCATION_STATUS_SERVER_INVALID){
                msgId = R.string.invalid_server;
            } else if(mSyncStatus == SunshineSyncAdapter.LOCATION_STATUS_INVALID) {
                msgId = R.string.empty_forecast_list_invalid_location;
            }
            if(msgId != 0) {
                mEmptyWeatherView.append("\n" + getString(msgId));
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        switch (loader.getId()) {
            case WEATHER_CURSOR_ID:
                mForecastAdapter.swapCursor(null);
                break;
            default:
                break;
        }

    }

    public void onLocationChanged () {
        updateWeather();
        getLoaderManager().restartLoader(WEATHER_CURSOR_ID, null, this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        mSyncStatus = Utility.getSyncStatus(getActivity());
        updateEmptyView();
    }

    @Override
    public void onItemClicked(int position, ForecastAdapter.ViewHolder vHolder) {
        // Get the currently selected item
        Cursor cursor = mForecastAdapter.getCursor();

        if (cursor != null && cursor.moveToPosition(position)) {
            Activity a = getActivity();

            if (a instanceof Callback) {
                String locationSetting = Utility.getPreferredLocation(a);

                Uri dateUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(
                        locationSetting,
                        cursor.getLong(COL_WEATHER_DATE));

                Callback c = (Callback) a;

                c.onItemSelected(dateUri, vHolder);

                mPosition = position;
            }

        }
    }

    /**
     * Callback interface that all activities containing this fragment must implement.
     * This mechanism allows activities to be notified of item selections.
     */
    public interface Callback {
        /**
         * DetailFragmentCallback for when an item has been selected.
         */
        public void onItemSelected(Uri dateUri, ForecastAdapter.ViewHolder vHolder);
    }

    public void setUseSpecialTodayView(boolean useSpecialTodayView) {
        mUseTodayView = useSpecialTodayView;
        if(mForecastAdapter != null) {
            mForecastAdapter.setUseSpecialTodayView(mUseTodayView);
        }
    }

}
