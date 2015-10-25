package com.example.android.sunshine.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.io.IOException;


public class MainActivity extends ActionBarActivity implements ForecastFragment.Callback {

    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";

    // Store current known location
    private String mLocation;
    private boolean mTwoPane;

    private static final String DETAILFRAGMENT_TAG = "DFTAG";
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    static final String PROJECT_NUMBER = "672426133293";

    private GoogleCloudMessaging mGcm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLocation = Utility.getPreferredLocation(this);
        super.onCreate(savedInstanceState);
        Uri detailUri = getIntent() != null ? getIntent().getData() : null;

        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }

        if(findViewById(R.id.weather_detail_container) != null) {
            // The detail container view will be present only in the large-screen layouts
            // (res/layout-sw600dp). If this view is present, then activity should be.
            // in two-pane mode.
            mTwoPane = true;
            // In two-pane mode show the detail view in this activity by adding or replacing
            // fragment using a fragment transaction.
            if(savedInstanceState == null) {
                DetailFragment df = (detailUri != null)?
                        DetailFragment.newInstance(detailUri, false):new DetailFragment();
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.weather_detail_container, df, DETAILFRAGMENT_TAG)
                        .commit();
            }
        } else {
            mTwoPane = false;
            if(getSupportActionBar()!=null) {
                getSupportActionBar().setElevation(0f);
            }
        }

        // Get forecast fragment
        ForecastFragment ff = (ForecastFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_forecast);
        if(ff != null) {
            ff.setUseSpecialTodayView(!mTwoPane);
        }

        SunshineSyncAdapter.initializeSyncAdapter(this);

        if(checkPlayServices()) {
            mGcm = GoogleCloudMessaging.getInstance(this);
            String regId = getRegistrationId(this);

            // Uncomment to log regId and test the app.
            //Log.i(LOG_TAG, "Reg ID: " + regId);

            if(PROJECT_NUMBER.equals("Your Project Number")) {
                new AlertDialog.Builder(this)
                        .setTitle("Needs Project Number")
                        .setMessage("GCM will not function in Sunshine until you set the Project Number to the one from Google Developer Console")
                        .setPositiveButton(android.R.string.ok, null)
                        .create().show();
            } else if(regId.isEmpty()) {
                registerInBackground(this);
            }
        } else {
            Log.i(LOG_TAG, getString(R.string.error_invalid_gps_weather_dis));
            storeRegistrationId(this, null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If not available, GCM-powered weather alerts, will not be available.
        if(!checkPlayServices()) {
            // Store regID as null. Do not know what this is, yet.
        }

        // Check if location changed
        String location = Utility.getPreferredLocation(this);

        if(mLocation != null && !mLocation.equals(location)){

            ForecastFragment ff = (ForecastFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_forecast);
            if(ff != null) {
                ff.onLocationChanged();
            }

            DetailFragment df = (DetailFragment) getSupportFragmentManager()
                    .findFragmentByTag(DETAILFRAGMENT_TAG);
            if ( df != null ) {
                df.onLocationChanged(location);
            }

            mLocation = location;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(LOG_TAG, "onStart");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(LOG_TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.v(LOG_TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(LOG_TAG, "onDestroy");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.pref_general.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemSelected(Uri dateUri, ForecastAdapter.ViewHolder viewHolder) {
        if(mTwoPane) {
            // Replace detail fragment.
            getSupportFragmentManager().beginTransaction().replace(
                    R.id.weather_detail_container,
                    DetailFragment.newInstance(dateUri, false),
                    DETAILFRAGMENT_TAG
            ).commit();

        } else {

            Pair<View, String> pair = new Pair<View, String>(
                    viewHolder.mIconView, getString(R.string.detail_icon_transition_name));

            ActivityOptionsCompat activityOptions =
                    ActivityOptionsCompat.makeSceneTransitionAnimation(this, pair);

            // Launch activity
            Intent detailIntent = new Intent(this, DetailActivity.class);
            detailIntent.setData(dateUri);

            ActivityCompat.startActivity(this, detailIntent, activityOptions.toBundle());
        }
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from the
     * Google Play Store or enable it in the device's system settings.
     * @return
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if(resultCode != ConnectionResult.SUCCESS) {
            if(GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(
                        resultCode, this, PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(LOG_TAG, getString(R.string.error_device_not_supported));
                finish();
            }
            return false;
        }
        return true;
    }

    /**
     * Gets the current registration ID for application on GCM service.
     * If result is empty, the app needs to register.
     * @return  registration ID, or empty string if there is no existing
     *          registration ID.
     */
    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if(registrationId.isEmpty()) {
            Log.i(LOG_TAG, "GCM Registration not found.");
            return "";
        }

        // Check if app was updated; if so, it must clear the registration ID
        // since the existing registration ID is not guaranteed to work with
        // the new app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if(registeredVersion != currentVersion) {
            Log.i(LOG_TAG, "App version changed");
            return "";
        }

        return registrationId;
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGCMPreferences(Context context) {
        /* Sunshine persists the registration ID in shared preferences, but
         * how you store the registration ID in your app is up to you.
         * Make sure that it is private!
         */
        return getSharedPreferences(MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    /**
     * @return Application's version code from the {@code PackageManager}
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch(PackageManager.NameNotFoundException e) {
            // Should neve happen. WHAT DID YOU DO!?!
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * Stores the registration ID and app versioncode in the application's
     * shared preferences.
     */
    private void registerInBackground(final Context context) {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
                String msg = "";
                try {
                    if(mGcm == null) {
                        mGcm = GoogleCloudMessaging.getInstance(context);
                    }
                    String regId = mGcm.register(PROJECT_NUMBER);
                    msg = "Device registered, registration ID=" + regId;

                    /* You should send the registratin ID to your server over HTTP,
                     * so it can use GCM/HTTP or CCS to send messages to your app.
                     * The requesto to your server should be authenticated if your
                     * app is using accounts. */
                    // sendRegistrationIdToBackend();
                    /* For this demo: we don't need to send it because the device
                     * will send upstream messages to a server that echo back the
                     * message using the 'from' address in the message. */

                    // Persist the registration ID - no need to register again.
                    storeRegistrationId(context, regId);
                } catch (IOException e) {
                    msg = "Error :" + e.getMessage();
                    /* TODO: If there is an error, don't just keep trying to register.
                    * Require the user to click a button again, or perform exponential
                    * back-off. */
                }

                return null;
            }
        }.execute(null, null, null);
    }

    /**
     * Stores the registration ID and app versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId registration ID.
     */
    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(LOG_TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }
}
