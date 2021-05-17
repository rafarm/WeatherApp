package com.iesnules.weatherapp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import local.iesnules.weatherapp.R;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.JsonReader;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity {
	
	private static String JSONSTRING_BUNDLE_KEY = "jsonstring_bundle_key";
	private static String URL_BASE = "http://api.wunderground.com/api/e549a13dddde905c/conditions/forecast/q/";
	
	private static long TIME_TO_OUTDATE = 600;
	private static long TIME_TO_WAIT = 60000;
	
	private String mJsonString;
	private WeatherInfo mCurrWeatherInfo;
	private Address mAddress;
	
	private ImageView mBgImageView;
	private ViewGroup mMsgLayout;
	private TextView mMsgTextView;
	private ViewGroup mDataLayout;
	private GraphView mGraphView;
	private CompassView mCompassView;
	private TextView mCityTextView;
	private TextView mWeatherTextView;
	private ImageView mWgImageView;
	
	private AlertDialog mDialog;
	
	private Handler mTimeoutHandler;
	private Runnable mTimeoutRunnable;
	
	private LocationManager mLocManager;
	private LocationListener mLocListener;
	
	private Map<String, Integer> mBackgrounds;
	private Map<String, Integer> mConditions;
	
	private SensorManager mSensorManager;
	private Sensor mMagnetometer;
	private Sensor mAccelerometer;
	
	private boolean freshStart = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        if (savedInstanceState != null) {
        	mJsonString = savedInstanceState.getString(JSONSTRING_BUNDLE_KEY);
        	if (mJsonString != null) {
        		mCurrWeatherInfo = parseJsonString();
        	}
        }
        else {
        	mJsonString = null;
        	mCurrWeatherInfo = null;
        }
        
        setContentView(R.layout.activity_main);
        
        mBgImageView = (ImageView)findViewById(R.id.bgImageView);
        mMsgLayout = (ViewGroup)findViewById(R.id.msgLayout);
        mMsgTextView = (TextView)findViewById(R.id.msgTextView);
        mDataLayout = (ViewGroup)findViewById(R.id.dataLayout);
        mGraphView = (GraphView)findViewById(R.id.graphView);
        mCompassView = (CompassView)findViewById(R.id.compassView);
        mCityTextView = (TextView)findViewById(R.id.cityTextView);
        mWeatherTextView = (TextView)findViewById(R.id.weatherTextView);
        mWgImageView = (ImageView)findViewById(R.id.wgImageView);
        
        // Hide data views...
		mBgImageView.setVisibility(View.GONE);
    	mDataLayout.setVisibility(View.GONE);
        
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        
        setupData();
    }
    
    @Override
    protected void onStart() {
    	super.onStart();
    	
    	reuseOrReloadData();
    }
    
    @Override
	protected void onPause() {
		super.onPause();
		
		cancelRequests();
		
		// Unregister for sensor events
		mSensorManager.unregisterListener(mCompassView);
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		// Get user preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		if (prefs.getBoolean(SettingsActivity.KEY_SETTINGS_COMPASS, false)) {
			// Register for sensor events
			mSensorManager.registerListener(mCompassView, mMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);
			mSensorManager.registerListener(mCompassView, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		}
		else {
			// Fix compass to normal position
			mCompassView.fixCompass();
		}
		
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		if (mJsonString != null) {
			outState.putSerializable(JSONSTRING_BUNDLE_KEY, mJsonString);
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		reuseOrReloadData();
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    @Override
	public boolean onMenuOpened(int featureId, Menu menu) {
    	cancelRequests();		
		return super.onMenuOpened(featureId, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()) {
    	case R.id.menu_settings:
    		startActivity(new Intent(this, SettingsActivity.class));
    		return true;
    	case R.id.menu_refresh:
    		initLocationServices();
    		return true;
    	default:
    		return super.onOptionsItemSelected(item);
    	}
	}
    
    private void reuseOrReloadData() {
    	mCompassView.setDisplayRotation(getWindowManager().getDefaultDisplay().getRotation());
    	
    	if (mCurrWeatherInfo == null || isDataOld()) {	
    		initLocationServices();
    	}
    	else {
    		updateUI();
    	}
    }

    
    private void cancelRequests() {
    	mLocManager.removeUpdates(mLocListener);
		mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
		mMsgLayout.setVisibility(View.GONE);
		
		if (mDialog != null && mDialog.isShowing()) {
			mDialog.dismiss();
		}
    }
    
	// Initialize location services
    private void initLocationServices() {
    	// Show message view
		mMsgLayout.setAlpha(0f);
		mMsgTextView.setText(R.string.msg_waiting_location);
		mMsgLayout.setVisibility(View.VISIBLE);
		mMsgLayout.animate().alpha(1f);
		
    	// Test for location services availability
    	boolean gpsEnabled = mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    	boolean networkEnabled = mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    	if (gpsEnabled || networkEnabled) {
    		if (gpsEnabled){
    			mLocManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, mLocListener, null);
    		}
    		if (networkEnabled){
    			mLocManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, mLocListener, null);
    		}
    		
    		mTimeoutHandler.postDelayed(mTimeoutRunnable, TIME_TO_WAIT);
    	}
    	else {
    		locationServiceError();
    	}
    }
    
    // Initialize all data structures
    private void setupData() {
    	mTimeoutHandler = new Handler();
		mTimeoutRunnable = new Runnable(){
			@Override
			public void run() {
				mLocManager.removeUpdates(mLocListener);				
				locationTimeoutError();
			}
		};
		
    	mLocManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		mLocListener = new LocationListener() {

			@Override
			public void onLocationChanged(Location arg0) {
				mLocManager.removeUpdates(mLocListener);
				mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
				retrieveData(arg0);
			}

			@Override
			public void onProviderDisabled(String arg0) {}

			@Override
			public void onProviderEnabled(String arg0) {}

			@Override
			public void onStatusChanged(String arg0, int arg1, Bundle arg2) {}
    	};
    	
    	mBackgrounds = new HashMap<String, Integer>();
    	mBackgrounds.put("Drizzle", Integer.valueOf(R.drawable.drizzle));
    	mBackgrounds.put("Light Drizzle", Integer.valueOf(R.drawable.drizzle));
    	mBackgrounds.put("Heavy Drizzle", Integer.valueOf(R.drawable.drizzle));
    	mBackgrounds.put("Rain", Integer.valueOf(R.drawable.rain));
    	mBackgrounds.put("Light Rain", Integer.valueOf(R.drawable.rain));
    	mBackgrounds.put("Heavy Rain", Integer.valueOf(R.drawable.rain));
    	mBackgrounds.put("Snow", Integer.valueOf(R.drawable.snow));
    	mBackgrounds.put("Light Snow", Integer.valueOf(R.drawable.snow));
    	mBackgrounds.put("Heavy Snow", Integer.valueOf(R.drawable.snow));
    	mBackgrounds.put("Snow Grains", Integer.valueOf(R.drawable.snow_grains));
    	mBackgrounds.put("Light Snow Grains", Integer.valueOf(R.drawable.snow_grains));
    	mBackgrounds.put("Heavy Snow Grains", Integer.valueOf(R.drawable.snow_grains));
    	mBackgrounds.put("Ice Crystals", Integer.valueOf(R.drawable.ice_crystals));
    	mBackgrounds.put("Light Ice Crystals", Integer.valueOf(R.drawable.ice_crystals));
    	mBackgrounds.put("Heavy Ice Crystals", Integer.valueOf(R.drawable.ice_crystals));
    	mBackgrounds.put("Ice Pellets", Integer.valueOf(R.drawable.ice_pellets));
    	mBackgrounds.put("Light Ice Pellets", Integer.valueOf(R.drawable.ice_pellets));
    	mBackgrounds.put("Heavy Ice Pellets", Integer.valueOf(R.drawable.ice_pellets));
    	mBackgrounds.put("Hail", Integer.valueOf(R.drawable.hail));
    	mBackgrounds.put("Light Hail", Integer.valueOf(R.drawable.hail));
    	mBackgrounds.put("Heavy Hail", Integer.valueOf(R.drawable.hail));
    	mBackgrounds.put("Mist", Integer.valueOf(R.drawable.mist));
    	mBackgrounds.put("Light Mist", Integer.valueOf(R.drawable.mist));
    	mBackgrounds.put("Heavy Mist", Integer.valueOf(R.drawable.mist));
    	mBackgrounds.put("Fog", Integer.valueOf(R.drawable.fog));
    	mBackgrounds.put("Light Fog", Integer.valueOf(R.drawable.fog));
    	mBackgrounds.put("Heavy Fog", Integer.valueOf(R.drawable.fog));
    	mBackgrounds.put("Fog Patches", Integer.valueOf(R.drawable.fog_patches));
    	mBackgrounds.put("Light Fog Patches", Integer.valueOf(R.drawable.fog_patches));
    	mBackgrounds.put("Heavy Fog Patches", Integer.valueOf(R.drawable.fog_patches));
    	mBackgrounds.put("Smoke", Integer.valueOf(R.drawable.smoke));
    	mBackgrounds.put("Light Smoke", Integer.valueOf(R.drawable.smoke));
    	mBackgrounds.put("Heavy Smoke", Integer.valueOf(R.drawable.smoke));
    	mBackgrounds.put("Volcanic Ash", Integer.valueOf(R.drawable.volcanic_ash));
    	mBackgrounds.put("Light Volcanic Ash", Integer.valueOf(R.drawable.volcanic_ash));
    	mBackgrounds.put("Heavy Volcanic Ash", Integer.valueOf(R.drawable.volcanic_ash));
    	mBackgrounds.put("Widespread Dust", Integer.valueOf(R.drawable.widespread_dust));
    	mBackgrounds.put("Light Widespread Dust", Integer.valueOf(R.drawable.widespread_dust));
    	mBackgrounds.put("Heavy Widespread Dust", Integer.valueOf(R.drawable.widespread_dust));
    	mBackgrounds.put("Sand", Integer.valueOf(R.drawable.sand));
    	mBackgrounds.put("Light Sand", Integer.valueOf(R.drawable.sand));
    	mBackgrounds.put("Heavy Sand", Integer.valueOf(R.drawable.sand));
    	mBackgrounds.put("Haze", Integer.valueOf(R.drawable.haze));
    	mBackgrounds.put("Light Haze", Integer.valueOf(R.drawable.haze));
    	mBackgrounds.put("Heavy Haze", Integer.valueOf(R.drawable.haze));
    	mBackgrounds.put("Spray", Integer.valueOf(R.drawable.spray));
    	mBackgrounds.put("Light Spray", Integer.valueOf(R.drawable.spray));
    	mBackgrounds.put("Heavy Spray", Integer.valueOf(R.drawable.spray));
    	mBackgrounds.put("Dust Whirls", Integer.valueOf(R.drawable.dust_whirls));
    	mBackgrounds.put("Light Dust Whirls", Integer.valueOf(R.drawable.dust_whirls));
    	mBackgrounds.put("Heavy Dust Whirls", Integer.valueOf(R.drawable.dust_whirls));
    	mBackgrounds.put("Sandstorm", Integer.valueOf(R.drawable.sandstorm));
    	mBackgrounds.put("Light Sandstorm", Integer.valueOf(R.drawable.sandstorm));
    	mBackgrounds.put("Heavy Sandstorm", Integer.valueOf(R.drawable.sandstorm));
    	mBackgrounds.put("Low Drifting Snow", Integer.valueOf(R.drawable.low_drifting_snow));
    	mBackgrounds.put("Light Low Drifting Snow", Integer.valueOf(R.drawable.low_drifting_snow));
    	mBackgrounds.put("Heavy Low Drifting Snow", Integer.valueOf(R.drawable.low_drifting_snow));
    	mBackgrounds.put("Low Drifting Widespread Dust", Integer.valueOf(R.drawable.low_drifting_widespread_dust));
    	mBackgrounds.put("Light Low Drifting Widespread Dust", Integer.valueOf(R.drawable.low_drifting_widespread_dust));
    	mBackgrounds.put("Heavy Low Drifting Widespread Dust", Integer.valueOf(R.drawable.low_drifting_widespread_dust));
    	mBackgrounds.put("Low Drifting Sand", Integer.valueOf(R.drawable.sand));
    	mBackgrounds.put("Light Low Drifting Sand", Integer.valueOf(R.drawable.sand));
    	mBackgrounds.put("Heavy Low Drifting Sand", Integer.valueOf(R.drawable.sand));
    	mBackgrounds.put("Blowing Snow", Integer.valueOf(R.drawable.blowing_snow));
    	mBackgrounds.put("Light Blowing Snow", Integer.valueOf(R.drawable.blowing_snow));
    	mBackgrounds.put("Heavy Blowing Snow", Integer.valueOf(R.drawable.blowing_snow));
    	mBackgrounds.put("Blowing Widespread Dust", Integer.valueOf(R.drawable.blowing_widespread_dust));
    	mBackgrounds.put("Light Blowing Widespread Dust", Integer.valueOf(R.drawable.blowing_widespread_dust));
    	mBackgrounds.put("Heavy Blowing Widespread Dust", Integer.valueOf(R.drawable.blowing_widespread_dust));
    	mBackgrounds.put("Blowing Sand", Integer.valueOf(R.drawable.blowing_sand));
    	mBackgrounds.put("Light Blowing Sand", Integer.valueOf(R.drawable.blowing_sand));
    	mBackgrounds.put("Heavy Blowing Sand", Integer.valueOf(R.drawable.blowing_sand));
    	mBackgrounds.put("Rain Mist", Integer.valueOf(R.drawable.rain_mist));
    	mBackgrounds.put("Light Rain Mist", Integer.valueOf(R.drawable.rain_mist));
    	mBackgrounds.put("Heavy Rain Mist", Integer.valueOf(R.drawable.rain_mist));
    	mBackgrounds.put("Rain Showers", Integer.valueOf(R.drawable.rain_showers));
    	mBackgrounds.put("Light Rain Showers", Integer.valueOf(R.drawable.rain_showers));
    	mBackgrounds.put("Heavy Rain Showers", Integer.valueOf(R.drawable.rain_showers));
    	mBackgrounds.put("Snow Showers", Integer.valueOf(R.drawable.snow_showers));
    	mBackgrounds.put("Light Snow Showers", Integer.valueOf(R.drawable.snow_showers));
    	mBackgrounds.put("Heavy Snow Showers", Integer.valueOf(R.drawable.snow_showers));
    	mBackgrounds.put("Snow Blowing Snow Mist", Integer.valueOf(R.drawable.snow_showers));
    	mBackgrounds.put("Light Snow Blowing Snow Mist", Integer.valueOf(R.drawable.snow_showers));
    	mBackgrounds.put("Heavy Snow Blowing Snow Mist", Integer.valueOf(R.drawable.snow_showers));
    	mBackgrounds.put("Ice Pellet Showers", Integer.valueOf(R.drawable.ice_pellet_showers));
    	mBackgrounds.put("Light Ice Pellet Showers", Integer.valueOf(R.drawable.ice_pellet_showers));
    	mBackgrounds.put("Heavy Ice Pellet Showers", Integer.valueOf(R.drawable.ice_pellet_showers));
    	mBackgrounds.put("Hail Showers", Integer.valueOf(R.drawable.hail_showers));
    	mBackgrounds.put("Light Hail Showers", Integer.valueOf(R.drawable.hail_showers));
    	mBackgrounds.put("Heavy Hail Showers", Integer.valueOf(R.drawable.hail_showers));
    	mBackgrounds.put("Small Hail Showers", Integer.valueOf(R.drawable.hail_showers));
    	mBackgrounds.put("Light Small Hail Showers", Integer.valueOf(R.drawable.hail_showers));
    	mBackgrounds.put("Heavy Small Hail Showers", Integer.valueOf(R.drawable.hail_showers));
    	mBackgrounds.put("Thunderstorm", Integer.valueOf(R.drawable.thunderstorm));
    	mBackgrounds.put("Light Thunderstorm", Integer.valueOf(R.drawable.thunderstorm));
    	mBackgrounds.put("Heavy Thunderstorm", Integer.valueOf(R.drawable.thunderstorm));
    	mBackgrounds.put("Thunderstorms and Rain", Integer.valueOf(R.drawable.thunderstorms_and_rain));
    	mBackgrounds.put("Light Thunderstorms and Rain", Integer.valueOf(R.drawable.thunderstorms_and_rain));
    	mBackgrounds.put("Heavy Thunderstorms and Rain", Integer.valueOf(R.drawable.thunderstorms_and_rain));
    	mBackgrounds.put("Thunderstorms and Snow", Integer.valueOf(R.drawable.thunderstorms_and_rain));
    	mBackgrounds.put("Light Thunderstorms and Snow", Integer.valueOf(R.drawable.thunderstorms_and_rain));
    	mBackgrounds.put("Heavy Thunderstorms and Snow", Integer.valueOf(R.drawable.thunderstorms_and_rain));
    	mBackgrounds.put("Thunderstorms and Ice Pellets", Integer.valueOf(R.drawable.thunderstorms_and_rain));
    	mBackgrounds.put("Light Thunderstorms and Ice Pellets", Integer.valueOf(R.drawable.thunderstorms_and_rain));
    	mBackgrounds.put("Heavy Thunderstorms and Ice Pellets", Integer.valueOf(R.drawable.thunderstorms_and_rain));
    	mBackgrounds.put("Thunderstorms with Hail", Integer.valueOf(R.drawable.thunderstorms_and_rain));
    	mBackgrounds.put("Light Thunderstorms with Hail", Integer.valueOf(R.drawable.thunderstorms_and_rain));
    	mBackgrounds.put("Heavy Thunderstorms with Hail", Integer.valueOf(R.drawable.thunderstorms_and_rain));
    	mBackgrounds.put("Thunderstorms with Small Hail", Integer.valueOf(R.drawable.thunderstorms_and_rain));
    	mBackgrounds.put("Light Thunderstorms with Small Hail", Integer.valueOf(R.drawable.thunderstorms_and_rain));
    	mBackgrounds.put("Heavy Thunderstorms with Small Hail", Integer.valueOf(R.drawable.thunderstorms_and_rain));
    	mBackgrounds.put("Freezing Drizzle", Integer.valueOf(R.drawable.freezing_drizzle));
    	mBackgrounds.put("Light Freezing Drizzle", Integer.valueOf(R.drawable.freezing_drizzle));
    	mBackgrounds.put("Heavy Freezing Drizzle", Integer.valueOf(R.drawable.freezing_drizzle));
    	mBackgrounds.put("Freezing Rain", Integer.valueOf(R.drawable.freezing_rain));
    	mBackgrounds.put("Light Freezing Rain", Integer.valueOf(R.drawable.freezing_rain));
    	mBackgrounds.put("Heavy Freezing Rain", Integer.valueOf(R.drawable.freezing_rain));
    	mBackgrounds.put("Freezing Fog", Integer.valueOf(R.drawable.freezing_fog));
    	mBackgrounds.put("Light Freezing Fog", Integer.valueOf(R.drawable.freezing_fog));
    	mBackgrounds.put("Heavy Freezing Fog", Integer.valueOf(R.drawable.freezing_fog));
    	mBackgrounds.put("Patches of Fog", Integer.valueOf(R.drawable.fog_patches));
    	mBackgrounds.put("Shallow Fog", Integer.valueOf(R.drawable.fog));
    	mBackgrounds.put("Partial Fog", Integer.valueOf(R.drawable.fog));
    	mBackgrounds.put("Overcast", Integer.valueOf(R.drawable.overcast));
    	mBackgrounds.put("Clear", Integer.valueOf(R.drawable.clear));
    	mBackgrounds.put("Partly Cloudy", Integer.valueOf(R.drawable.partly_cloudy));
    	mBackgrounds.put("Mostly Cloudy", Integer.valueOf(R.drawable.mostly_cloudy));
    	mBackgrounds.put("Scattered Clouds", Integer.valueOf(R.drawable.scattered_clouds));
    	mBackgrounds.put("Small Hail", Integer.valueOf(R.drawable.hail));
    	mBackgrounds.put("Squalls", Integer.valueOf(R.drawable.squalls));
    	mBackgrounds.put("Funnel Cloud", Integer.valueOf(R.drawable.funnel_cloud));
    	mBackgrounds.put("Unknown Precipitation", Integer.valueOf(R.drawable.unknown));
    	mBackgrounds.put("Unknown", Integer.valueOf(R.drawable.unknown));
    	
    	mConditions = new HashMap<String, Integer>();
    	mConditions.put("Drizzle", Integer.valueOf(R.string.condition_drizzle));
    	mConditions.put("Light Drizzle", Integer.valueOf(R.string.condition_light_drizzle));
    	mConditions.put("Heavy Drizzle", Integer.valueOf(R.string.condition_heavy_drizzle));
    	mConditions.put("Rain", Integer.valueOf(R.string.condition_rain));
    	mConditions.put("Light Rain", Integer.valueOf(R.string.condition_light_rain));
    	mConditions.put("Heavy Rain", Integer.valueOf(R.string.condition_heavy_rain));
    	mConditions.put("Snow", Integer.valueOf(R.string.condition_snow));
    	mConditions.put("Light Snow", Integer.valueOf(R.string.condition_light_snow));
    	mConditions.put("Heavy Snow", Integer.valueOf(R.string.condition_heavy_snow));
    	mConditions.put("Snow Grains", Integer.valueOf(R.string.condition_snow_grains));
    	mConditions.put("Light Snow Grains", Integer.valueOf(R.string.condition_light_snow_grains));
    	mConditions.put("Heavy Snow Grains", Integer.valueOf(R.string.condition_heavy_snow_grains));
    	mConditions.put("Ice Crystals", Integer.valueOf(R.string.condition_ice_crystals));
    	mConditions.put("Light Ice Crystals", Integer.valueOf(R.string.condition_light_ice_crystals));
    	mConditions.put("Heavy Ice Crystals", Integer.valueOf(R.string.condition_heavy_ice_crystals));
    	mConditions.put("Ice Pellets", Integer.valueOf(R.string.condition_ice_pellets));
    	mConditions.put("Light Ice Pellets", Integer.valueOf(R.string.condition_light_ice_pellets));
    	mConditions.put("Heavy Ice Pellets", Integer.valueOf(R.string.condition_heavy_ice_pellets));
    	mConditions.put("Hail", Integer.valueOf(R.string.condition_hail));
    	mConditions.put("Light Hail", Integer.valueOf(R.string.condition_light_hail));
    	mConditions.put("Heavy Hail", Integer.valueOf(R.string.condition_heavy_hail));
    	mConditions.put("Mist", Integer.valueOf(R.string.condition_mist));
    	mConditions.put("Light Mist", Integer.valueOf(R.string.condition_light_mist));
    	mConditions.put("Heavy Mist", Integer.valueOf(R.string.condition_heavy_mist));
    	mConditions.put("Fog", Integer.valueOf(R.string.condition_fog));
    	mConditions.put("Light Fog", Integer.valueOf(R.string.condition_light_fog));
    	mConditions.put("Heavy Fog", Integer.valueOf(R.string.condition_heavy_fog));
    	mConditions.put("Fog Patches", Integer.valueOf(R.string.condition_fog_patches));
    	mConditions.put("Light Fog Patches", Integer.valueOf(R.string.condition_light_fog_patches));
    	mConditions.put("Heavy Fog Patches", Integer.valueOf(R.string.condition_heavy_fog_patches));
    	mConditions.put("Smoke", Integer.valueOf(R.string.condition_smoke));
    	mConditions.put("Light Smoke", Integer.valueOf(R.string.condition_light_smoke));
    	mConditions.put("Heavy Smoke", Integer.valueOf(R.string.condition_heavy_smoke));
    	mConditions.put("Volcanic Ash", Integer.valueOf(R.string.condition_volcanic_ash));
    	mConditions.put("Light Volcanic Ash", Integer.valueOf(R.string.condition_light_volcanic_ash));
    	mConditions.put("Heavy Volcanic Ash", Integer.valueOf(R.string.condition_heavy_volcanic_ash));
    	mConditions.put("Widespread Dust", Integer.valueOf(R.string.condition_widespread_dust));
    	mConditions.put("Light Widespread Dust", Integer.valueOf(R.string.condition_light_widespread_dust));
    	mConditions.put("Heavy Widespread Dust", Integer.valueOf(R.string.condition_heavy_widespread_dust));
    	mConditions.put("Sand", Integer.valueOf(R.string.condition_sand));
    	mConditions.put("Light Sand", Integer.valueOf(R.string.condition_light_sand));
    	mConditions.put("Heavy Sand", Integer.valueOf(R.string.condition_heavy_sand));
    	mConditions.put("Haze", Integer.valueOf(R.string.condition_haze));
    	mConditions.put("Light Haze", Integer.valueOf(R.string.condition_light_haze));
    	mConditions.put("Heavy Haze", Integer.valueOf(R.string.condition_heavy_haze));
    	mConditions.put("Spray", Integer.valueOf(R.string.condition_spray));
    	mConditions.put("Light Spray", Integer.valueOf(R.string.condition_light_spray));
    	mConditions.put("Heavy Spray", Integer.valueOf(R.string.condition_heavy_spray));
    	mConditions.put("Dust Whirls", Integer.valueOf(R.string.condition_dust_whirls));
    	mConditions.put("Light Dust Whirls", Integer.valueOf(R.string.condition_light_dust_whirls));
    	mConditions.put("Heavy Dust Whirls", Integer.valueOf(R.string.condition_heavy_dust_whirls));
    	mConditions.put("Sandstorm", Integer.valueOf(R.string.condition_sandstorm));
    	mConditions.put("Light Sandstorm", Integer.valueOf(R.string.condition_light_sandstorm));
    	mConditions.put("Heavy Sandstorm", Integer.valueOf(R.string.condition_heavy_sandstorm));
    	mConditions.put("Low Drifting Snow", Integer.valueOf(R.string.condition_low_drifting_snow));
    	mConditions.put("Light Low Drifting Snow", Integer.valueOf(R.string.condition_light_low_drifting_snow));
    	mConditions.put("Heavy Low Drifting Snow", Integer.valueOf(R.string.condition_heavy_low_drifting_snow));
    	mConditions.put("Low Drifting Widespread Dust", Integer.valueOf(R.string.condition_low_drifting_widespread_dust));
    	mConditions.put("Light Low Drifting Widespread Dust", Integer.valueOf(R.string.condition_light_low_drifting_widespread_dust));
    	mConditions.put("Heavy Low Drifting Widespread Dust", Integer.valueOf(R.string.condition_heavy_low_drifting_widespread_dust));
    	mConditions.put("Low Drifting Sand", Integer.valueOf(R.string.condition_low_drifting_sand));
    	mConditions.put("Light Low Drifting Sand", Integer.valueOf(R.string.condition_light_low_drifting_sand));
    	mConditions.put("Heavy Low Drifting Sand", Integer.valueOf(R.string.condition_heavy_low_drifting_sand));
    	mConditions.put("Blowing Snow", Integer.valueOf(R.string.condition_blowing_snow));
    	mConditions.put("Light Blowing Snow", Integer.valueOf(R.string.condition_light_blowing_snow));
    	mConditions.put("Heavy Blowing Snow", Integer.valueOf(R.string.condition_heavy_blowing_snow));
    	mConditions.put("Blowing Widespread Dust", Integer.valueOf(R.string.condition_blowing_widespread_dust));
    	mConditions.put("Light Blowing Widespread Dust", Integer.valueOf(R.string.condition_light_blowing_widespread_dust));
    	mConditions.put("Heavy Blowing Widespread Dust", Integer.valueOf(R.string.condition_heavy_blowing_widespread_dust));
    	mConditions.put("Blowing Sand", Integer.valueOf(R.string.condition_blowing_sand));
    	mConditions.put("Light Blowing Sand", Integer.valueOf(R.string.condition_light_blowing_sand));
    	mConditions.put("Heavy Blowing Sand", Integer.valueOf(R.string.condition_heavy_blowing_sand));
    	mConditions.put("Rain Mist", Integer.valueOf(R.string.condition_rain_mist));
    	mConditions.put("Light Rain Mist", Integer.valueOf(R.string.condition_light_rain_mist));
    	mConditions.put("Heavy Rain Mist", Integer.valueOf(R.string.condition_heavy_rain_mist));
    	mConditions.put("Rain Showers", Integer.valueOf(R.string.condition_rain_showers));
    	mConditions.put("Light Rain Showers", Integer.valueOf(R.string.condition_light_rain_showers));
    	mConditions.put("Heavy Rain Showers", Integer.valueOf(R.string.condition_heavy_rain_showers));
    	mConditions.put("Snow Showers", Integer.valueOf(R.string.condition_snow_showers));
    	mConditions.put("Light Snow Showers", Integer.valueOf(R.string.condition_light_snow_showers));
    	mConditions.put("Heavy Snow Showers", Integer.valueOf(R.string.condition_heavy_snow_showers));
    	mConditions.put("Snow Blowing Snow Mist", Integer.valueOf(R.string.condition_snow_blowing_snow_mist));
    	mConditions.put("Light Snow Blowing Snow Mist", Integer.valueOf(R.string.condition_light_snow_blowing_snow_mist));
    	mConditions.put("Heavy Snow Blowing Snow Mist", Integer.valueOf(R.string.condition_heavy_snow_blowing_snow_mist));
    	mConditions.put("Ice Pellet Showers", Integer.valueOf(R.string.condition_ice_pellet_showers));
    	mConditions.put("Light Ice Pellet Showers", Integer.valueOf(R.string.condition_light_ice_pellet_showers));
    	mConditions.put("Heavy Ice Pellet Showers", Integer.valueOf(R.string.condition_heavy_ice_pellet_showers));
    	mConditions.put("Hail Showers", Integer.valueOf(R.string.condition_hail_showers));
    	mConditions.put("Light Hail Showers", Integer.valueOf(R.string.condition_light_hail_showers));
    	mConditions.put("Heavy Hail Showers", Integer.valueOf(R.string.condition_heavy_hail_showers));
    	mConditions.put("Small Hail Showers", Integer.valueOf(R.string.condition_small_hail_showers));
    	mConditions.put("Light Small Hail Showers", Integer.valueOf(R.string.condition_light_small_hail_showers));
    	mConditions.put("Heavy Small Hail Showers", Integer.valueOf(R.string.condition_heavy_small_hail_showers));
    	mConditions.put("Thunderstorm", Integer.valueOf(R.string.condition_thunderstorm));
    	mConditions.put("Light Thunderstorm", Integer.valueOf(R.string.condition_light_thunderstorm));
    	mConditions.put("Heavy Thunderstorm", Integer.valueOf(R.string.condition_heavy_thunderstorm));
    	mConditions.put("Thunderstorms and Rain", Integer.valueOf(R.string.condition_thunderstorms_and_rain));
    	mConditions.put("Light Thunderstorms and Rain", Integer.valueOf(R.string.condition_light_thunderstorms_and_rain));
    	mConditions.put("Heavy Thunderstorms and Rain", Integer.valueOf(R.string.condition_heavy_thunderstorms_and_rain));
    	mConditions.put("Thunderstorms and Snow", Integer.valueOf(R.string.condition_thunderstorms_and_snow));
    	mConditions.put("Light Thunderstorms and Snow", Integer.valueOf(R.string.condition_light_thunderstorms_and_snow));
    	mConditions.put("Heavy Thunderstorms and Snow", Integer.valueOf(R.string.condition_heavy_thunderstorms_and_snow));
    	mConditions.put("Thunderstorms and Ice Pellets", Integer.valueOf(R.string.condition_thunderstorms_and_ice_pellets));
    	mConditions.put("Light Thunderstorms and Ice Pellets", Integer.valueOf(R.string.condition_light_thunderstorms_and_ice_pellets));
    	mConditions.put("Heavy Thunderstorms and Ice Pellets", Integer.valueOf(R.string.condition_heavy_thunderstorms_and_ice_pellets));
    	mConditions.put("Thunderstorms with Hail", Integer.valueOf(R.string.condition_thunderstorms_with_hail));
    	mConditions.put("Light Thunderstorms with Hail", Integer.valueOf(R.string.condition_light_thunderstorms_with_hail));
    	mConditions.put("Heavy Thunderstorms with Hail", Integer.valueOf(R.string.condition_heavy_thunderstorms_with_hail));
    	mConditions.put("Thunderstorms with Small Hail", Integer.valueOf(R.string.condition_thunderstorms_with_small_hail));
    	mConditions.put("Light Thunderstorms with Small Hail", Integer.valueOf(R.string.condition_light_thunderstorms_with_small_hail));
    	mConditions.put("Heavy Thunderstorms with Small Hail", Integer.valueOf(R.string.condition_heavy_thunderstorms_with_small_hail));
    	mConditions.put("Freezing Drizzle", Integer.valueOf(R.string.condition_freezing_drizzle));
    	mConditions.put("Light Freezing Drizzle", Integer.valueOf(R.string.condition_light_freezing_drizzle));
    	mConditions.put("Heavy Freezing Drizzle", Integer.valueOf(R.string.condition_heavy_freezing_drizzle));
    	mConditions.put("Freezing Rain", Integer.valueOf(R.string.condition_freezing_rain));
    	mConditions.put("Light Freezing Rain", Integer.valueOf(R.string.condition_light_freezing_rain));
    	mConditions.put("Heavy Freezing Rain", Integer.valueOf(R.string.condition_heavy_freezing_rain));
    	mConditions.put("Freezing Fog", Integer.valueOf(R.string.condition_freezing_fog));
    	mConditions.put("Light Freezing Fog", Integer.valueOf(R.string.condition_light_freezing_fog));
    	mConditions.put("Heavy Freezing Fog", Integer.valueOf(R.string.condition_heavy_freezing_fog));
    	mConditions.put("Patches of Fog", Integer.valueOf(R.string.condition_patches_of_fog));
    	mConditions.put("Shallow Fog", Integer.valueOf(R.string.condition_shallow_fog));
    	mConditions.put("Partial Fog", Integer.valueOf(R.string.condition_partial_fog));
    	mConditions.put("Overcast", Integer.valueOf(R.string.condition_overcast));
    	mConditions.put("Clear", Integer.valueOf(R.string.condition_clear));
    	mConditions.put("Partly Cloudy", Integer.valueOf(R.string.condition_partly_cloudy));
    	mConditions.put("Mostly Cloudy", Integer.valueOf(R.string.condition_mostly_cloudy));
    	mConditions.put("Scattered Clouds", Integer.valueOf(R.string.condition_scattered_clouds));
    	mConditions.put("Small Hail", Integer.valueOf(R.string.condition_small_hail));
    	mConditions.put("Squalls", Integer.valueOf(R.string.condition_squalls));
    	mConditions.put("Funnel Cloud", Integer.valueOf(R.string.condition_funnel_cloud));
    	mConditions.put("Unknown Precipitation", Integer.valueOf(R.string.condition_unknown_precipitation));
    	mConditions.put("Unknown", Integer.valueOf(R.string.condition_unknown));
    }
    
    // Retrieve weather data from Service
    private void retrieveData(final Location loc) {    	
    	if (loc != null) {
    		mMsgTextView.setText(R.string.msg_downloading);
    		
    		Runnable proc = new Runnable() {

    			@Override
    			public void run() {
    				// Location
    				double latitude = loc.getLatitude();
    				double longitude = loc.getLongitude();
    				
    				// Get local address
    				if (Geocoder.isPresent()) {
    					Geocoder geo = new Geocoder(getBaseContext());
    					List<Address> addr = null;
    					try {
							addr = geo.getFromLocation(latitude, longitude, 1);
							if (addr != null && addr.size()>0) {
								mAddress = addr.get(0);
							}
							else {
								mAddress = null;
							}
						} catch (IOException ex) {
							mAddress = null;
						} catch (IllegalArgumentException ex) {
							mAddress = null;
						}
    				}
    				else {
    					mAddress = null;
    				}
    				
    				// Get weather data from service
    				String queryString = URL_BASE + 
							latitude + 
							"," + 
							longitude + 
							".json";
				
    				URL query = null;
    				try {
    					query = new URL(queryString);
    				} catch (MalformedURLException e) {
    					throw new RuntimeException("Malformed URL", e);
    				}
				
    				InputStream dataStream = null;
    				try {
    					dataStream = query.openStream();
    					
    					// Parse weather data
        				mJsonString = new Scanner(dataStream,"UTF-8").useDelimiter("\\A").next();    				
        				mCurrWeatherInfo = parseJsonString();
    				
        				// Update display
        				runOnUiThread(new Runnable() {

        					@Override
        					public void run() {
        						updateUI();
        					}
    					
        				});
    				} catch (IOException e) {
    					// Process network exception
    					runOnUiThread(new Runnable() {

        					@Override
        					public void run() {
        						networkError();
        					}
    					
        				});
    					e.printStackTrace();
    				}
    			}
    		};
    	
    		new Thread(proc).start();
    	}
    }
    
    private WeatherInfo parseJsonString() {
    	if (mJsonString != null) {
    		JsonReader reader;
			try {
				reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(mJsonString.getBytes("UTF-8"))));
				try {
					WeatherInfo wi = readWeatherInfo(reader);
					reader.close();
					
					return wi;
				} catch (IOException e) {
					// Process json parsing exception
					runOnUiThread(new Runnable() {

    					@Override
    					public void run() {
    						jsonParsingError();
    					}
					
    				});
					e.printStackTrace();
				}
			} catch (UnsupportedEncodingException e1) {
				throw new RuntimeException("Json data encoding error", e1);
			}
    	}
    	
    	return null;
    }
    
    private WeatherInfo readWeatherInfo(JsonReader reader) throws IOException {
    	WeatherInfo wi = null;
    	ArrayList<Forecast> fc = null;
    	
    	reader.beginObject();
		while (reader.hasNext()) {
			String name = reader.nextName();
			if (name.equals("current_observation")) {
				wi = readCurrentObservation(reader);
			}
			else if (name.equals("forecast")) {
				fc = readForecast(reader);
			}
			else {
				reader.skipValue();
			}
		}
		reader.endObject();
		
		if (wi != null) {
			return new WeatherInfo(wi.getLocationInfo(), wi.getConditions(), fc);
		}
		
		return null;
    }
    
    private ArrayList<Forecast> readForecast(JsonReader reader) throws IOException {
    	ArrayList<Forecast> fc = null;
    	
    	reader.beginObject();
		while (reader.hasNext()) {
			String name = reader.nextName();
			if (name.equals("simpleforecast")) {
				fc = readSimpleForecast(reader);
			}
			else {
				reader.skipValue();
			}
		}
		reader.endObject();
    	
    	return fc;
    }
    
    private ArrayList<Forecast> readSimpleForecast(JsonReader reader) throws IOException {
    	ArrayList<Forecast> fc = null;
    	
    	reader.beginObject();
		while (reader.hasNext()) {
			String name = reader.nextName();
			if (name.equals("forecastday")) {
				fc = readForecastday(reader);
			}
			else {
				reader.skipValue();
			}
		}
		reader.endObject();
    	
    	return fc;
    }
    
    private ArrayList<Forecast> readForecastday(JsonReader reader) throws IOException {
    	ArrayList<Forecast> fc = new ArrayList<Forecast>();
    	
    	reader.beginArray();
		while (reader.hasNext()) {
			String weekday = null;
			float[] high_t = {999, 999};
			float[] low_t = {999, 999};
			String icon = null;
			
			reader.beginObject();
			while (reader.hasNext()) {
				String name = reader.nextName();
				if (name.equals("date")) {
					weekday = readForecastdate(reader);
				}
				else if (name.equals("high")) {
					high_t = readForecastTemps(reader);
				}
				else if (name.equals("low")) {
					low_t = readForecastTemps(reader);
				}
				else if (name.equals("icon")) {
					icon = reader.nextString();
				}
				else {
					reader.skipValue();
				}
			}
			reader.endObject();
			
			fc.add(new Forecast(weekday, high_t[0], high_t[1], low_t[0], low_t[1], icon));
		}
		reader.endArray();
    	
    	return fc;
    }
    
    private String readForecastdate(JsonReader reader) throws IOException {
    	String weekday = null;
    	
    	reader.beginObject();
		while (reader.hasNext()) {
			String name = reader.nextName();
			if (name.equals("weekday_short")) {
				weekday = reader.nextString();
			}
			else {
				reader.skipValue();
			}
		}
		reader.endObject();
		
		return weekday;
    }
    
    private float[] readForecastTemps(JsonReader reader) throws IOException {
    	float[] temps = {999, 999};
    	
    	reader.beginObject();
		while (reader.hasNext()) {
			String name = reader.nextName();
			if (name.equals("fahrenheit")) {
				temps[0] = (float) reader.nextDouble();
			}
			else if (name.equals("celsius")) {
				temps[1] = (float) reader.nextDouble();
			}
			else {
				reader.skipValue();
			}
		}
		reader.endObject();
		
		return temps;
    }
    
    private WeatherInfo readCurrentObservation(JsonReader reader) throws IOException {
    	LocationInfo li = null;
    	Conditions cond = null;
    	
    	long local_epoch = 0;
    	String weather = null;
    	float temp_c = 999;
    	float temp_f = 999;
    	int wind_degrees = 999;
    	float wind_kph = 999;
    	float wind_mph = 999;
    	
    	reader.beginObject();
    	while (reader.hasNext()) {
    		String name = reader.nextName();
    		if (name.equals("display_location")) {
    			li = readLocation(reader);
    		}
    		else if (name.equals("local_epoch")) {
    			local_epoch = reader.nextLong();
    		}
    		else if (name.equals("weather")) {
    			weather = reader.nextString();
    		}
    		else if (name.equals("temp_c")) {
    			temp_c = (float) reader.nextDouble();
    		}
    		else if (name.equals("temp_f")) {
    			temp_f = (float) reader.nextDouble();
    		}
    		else if (name.equals("wind_degrees")) {
    			wind_degrees = reader.nextInt();
    		}
    		else if (name.equals("wind_kph")) {
    			wind_kph = (float) reader.nextDouble();
    		}
    		else if (name.equals("wind_mph")) {
    			wind_mph = (float) reader.nextDouble();
    		}
    		else {
    			reader.skipValue();
    		}
    	}
    	reader.endObject();
    	
    	cond = new Conditions(local_epoch, weather, temp_f, temp_c, wind_degrees, wind_mph, wind_kph);
    	
    	return new WeatherInfo(li, cond, null);
    }
    
    private LocationInfo readLocation(JsonReader reader) throws IOException {
    	String city = null;
    	String country = null;
    	String state = null;
    	double longitude = 9999;
    	double latitude = 9999;
    	
    	reader.beginObject();
    	while (reader.hasNext()) {
    		String name = reader.nextName();
    		if (name.equals("city")) {
    			city = reader.nextString();
    		}
    		else if (name.equals("country")) {
    			country = reader.nextString();
    		}
    		else if (name.equals("state_name")) {
    			state = reader.nextString();
    		}
    		else if (name.equals("longitude")) {
    			longitude = reader.nextDouble();
    		}
    		else if (name.equals("latitude")) {
    			latitude = reader.nextDouble();
    		}
    		else {
    			reader.skipValue();		
    		}
    	}
    	reader.endObject();
    	
    	return new LocationInfo(city, state, country, longitude, latitude);
    }
	
	private void updateUI() {
		if (mCurrWeatherInfo != null) {
			LocationInfo li = mCurrWeatherInfo.getLocationInfo();
			Conditions cond = mCurrWeatherInfo.getConditions();
		
		
			// Set background image
			Number index = mBackgrounds.get(cond.getWeather());
			if (index != null) {
				mBgImageView.setImageResource(index.intValue());
			}
			else {
				//mBgImageView.setImageResource(mBackgrounds.get("Unknown").intValue());
				mBgImageView.setImageDrawable(null);
			}
			
			// Set graph
			mGraphView.setForecast(mCurrWeatherInfo.getForecast());
			
			// Set compass
			mCompassView.setCurrentConditions(cond);
		
			// Set bottom area
			if (mAddress != null && mAddress.getLocality() != null) {
				mCityTextView.setText(mAddress.getLocality());
			}
			else {
				mCityTextView.setText(li.getCity());
			}
			
			String weather = cond.getWeather();
			if (weather != null && !weather.equals("")) {
				mWeatherTextView.setText(getString(mConditions.get(weather)));
			}
			else {
				mWeatherTextView.setText(getString(R.string.no_weather_inf));
			}
			
			mWgImageView.setImageResource(R.drawable.wunderground_logo);
		}
		else {
			if (mAddress != null) {
				mCityTextView.setText(mAddress.getLocality());
			}
			else {
				mCityTextView.setText(null);
			}	
			mWeatherTextView.setText(getString(R.string.no_weather_inf));
			mGraphView.setForecast(null);
			mCompassView.setCurrentConditions(null);
			mBgImageView.setImageDrawable(null);
			mWgImageView.setImageDrawable(null);
		}
		
		if (freshStart) {
			freshStart = false;
			
			// Animate data display
			mBgImageView.setAlpha(0f);
			mBgImageView.setVisibility(View.VISIBLE);
			mBgImageView.animate().alpha(1f);
			
			mDataLayout.setAlpha(0f);
			mDataLayout.setVisibility(View.VISIBLE);
			mDataLayout.animate().alpha(1f);
		}
		
		//mMsgLayout.animate().alpha(0f);
		mMsgLayout.setVisibility(View.GONE);
	}
	
	private void locationServiceError() {
		// Location services not available >> show alert dialog
		showAlert(getString(R.string.location_services),
				getString(R.string.activate_location_services),
				new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
						mDialog = null;
					}
				},
				false);
	}
	
	private void networkError() {
		// Network error >> show alert dialog
		showAlert(getString(R.string.network_services),
				getString(R.string.check_network_services),
				new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						startActivity(new Intent(Settings.ACTION_SETTINGS));
						mDialog = null;
					}
				},
				false);
	}
	
	private void jsonParsingError() {
		// Parsing error >> show alert dialog
		showAlert(getString(R.string.parsing_error),
				getString(R.string.parsing_error_explanation),
				new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				},
				false);
	}
	
	
	private void locationTimeoutError() {
		// Timeout error >> show alert dialog
		showAlert(getString(R.string.timeout_error),
				getString(R.string.timeout_error_explanation),
				new DialogInterface.OnClickListener() {
			
					@Override
					public void onClick(DialogInterface dialog, int which) {
						updateUI();
						mDialog = null;
					}
				},
				true);
	}
	
	
	private void showAlert(String title, String message, DialogInterface.OnClickListener listener, boolean cancelable) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(title)
				.setMessage(message)
				.setNegativeButton(R.string.ok, listener)
				.setCancelable(cancelable);
				
		mDialog = builder.create();
		mMsgLayout.setVisibility(View.GONE);
		mDialog.show();
	}
	
	
	private boolean isDataOld() {		
		if (mCurrWeatherInfo != null) {
			// Test if stored data is out of date
			long now = new Date().getTime()/1000;
			long last_observation = mCurrWeatherInfo.getConditions().getLocal_epoch();
			if ((now-last_observation)>TIME_TO_OUTDATE) {
				return true;
			}
		}
		
		return false;
	}
}
