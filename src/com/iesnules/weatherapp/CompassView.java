package com.iesnules.weatherapp;

import local.iesnules.weatherapp.R;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;


public class CompassView extends FrameLayout implements SensorEventListener {
	
	private ViewGroup mCompassLayout;
	private ViewGroup mWindDirLayout;
	private TextView mTempTextView;
	private TextView mWindSpeedTextView;
	
	private Conditions mCurrentConditions;
	
	float[] mGravity;
	float[] mGeomagnetic;
	int displayRotation;

	public CompassView(Context context) {
		super(context);
		
		setup(context);
	}

	public CompassView(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		setup(context);
	}

	public CompassView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		setup(context);
	}
	
	private void setup(Context context) {
		mGravity = null;
		mGeomagnetic = null;
		displayRotation = 0;
		
		View.inflate(context, R.layout.compass_view_layout, this);
		
		mCompassLayout = (ViewGroup)findViewWithTag("compass");
		mWindDirLayout = (ViewGroup)findViewWithTag("windDirection");
		mTempTextView = (TextView)findViewWithTag("temperature");
		mWindSpeedTextView = (TextView)findViewWithTag("windSpeed");
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// Get willing dimensions
		float desiredWidth = getMeasuredWidth();
		float desiredHeight = getMeasuredHeight();
		
		// Compute aspect ratio
		float ratio = 1;
		if (desiredHeight > 0) {
			ratio = desiredWidth/desiredHeight;
		}
		
		// Compute proposed dimensions respecting aspect ratio
		int defaultWidth = View.getDefaultSize((int) desiredWidth, widthMeasureSpec);
		int defaultHeight = View.getDefaultSize((int) desiredHeight, heightMeasureSpec);
		int computedWidth = (int) (defaultHeight*ratio);
		int computedHeight = defaultHeight;
		if (computedWidth > defaultWidth) {
			if (ratio > 0) {
				computedHeight = (int) (defaultWidth/ratio);
			}
			computedWidth = defaultWidth;
		}

		super.onMeasure(View.MeasureSpec.makeMeasureSpec(computedWidth, View.MeasureSpec.getMode(widthMeasureSpec)),
				View.MeasureSpec.makeMeasureSpec(computedHeight, View.MeasureSpec.getMode(heightMeasureSpec)));
	}

	public void setDisplayRotation(int rotation) {
		switch(rotation){
		case Surface.ROTATION_90:
			displayRotation = 90;
			break;
		case Surface.ROTATION_180:
			displayRotation = 180;
			break;
		case Surface.ROTATION_270:
			displayRotation = 270;
			break;
		default:
			displayRotation = 0;
		}
	}
	
	public int getDisplayRotation() {
		return displayRotation;
	}
	
	public Conditions getCurrentConditions() {
		return mCurrentConditions;
	}
	
	public void setCurrentConditions(Conditions cond) {
		this.mCurrentConditions = cond;
		
		// Get user preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		
		if (cond != null) {
			this.mWindDirLayout.setAlpha(1);
			this.mWindDirLayout.setRotation(cond.getWind_degrees());
			if (prefs.getBoolean(SettingsActivity.KEY_SETTINGS_TEMP_UNIT, true)) {
				this.mTempTextView.setText(""+cond.getTemp_c()+"°");
			}
			else {
				this.mTempTextView.setText(""+cond.getTemp_f()+"°");
			}
			if (prefs.getBoolean(SettingsActivity.KEY_SETTINGS_SPEED_UNIT, true)) {
				this.mWindSpeedTextView.setText(""+cond.getWind_kph()+" kph");
			}
			else {
				this.mWindSpeedTextView.setText(""+cond.getWind_mph()+" mph");
			}
		}
		else {
			this.mWindDirLayout.setAlpha(0);
			this.mTempTextView.setText(null);
			this.mWindSpeedTextView.setText(null);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			mGravity = event.values;
		}
		if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
			mGeomagnetic = event.values;
		}
		
		if (mGravity != null && mGeomagnetic != null) {
			float R[] = new float[9];
			float I[] = new float[9];
			boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
			if (success) {
				// Get device orientation
				float orientation[] = new float[3];
		        SensorManager.getOrientation(R, orientation);
		        
		        mCompassLayout.setRotation((float) -(Math.toDegrees(orientation[0]) + displayRotation));
		    }
		}
	}
	
	public void fixCompass() {
		mCompassLayout.setRotation(0);
	}
}
