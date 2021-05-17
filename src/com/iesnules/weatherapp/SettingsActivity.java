package com.iesnules.weatherapp;

import local.iesnules.weatherapp.R;
import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceFragment;

public class SettingsActivity extends Activity {
	
	public static final String KEY_SETTINGS_TEMP_UNIT = "settings_temp_unit";
	public static final String KEY_SETTINGS_SPEED_UNIT = "settings_speed_unit";
	public static final String KEY_SETTINGS_COMPASS = "settings_compass";
	
	public static class SettingsFragment extends PreferenceFragment {

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preferences);
		}
		
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getFragmentManager().beginTransaction()
			.replace(android.R.id.content, new SettingsFragment())
			.commit();
	}
}
