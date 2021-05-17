package com.iesnules.weatherapp;


public class Forecast {

	private String mWeekday;
	
	private float mHigh_f;
	private float mHigh_c;
	private float mLow_f;
	private float mLow_c;
	
	private String mIcon;
	
	public Forecast(String mWeekday, float mHigh_f, float mHigh_c, float mLow_f, float mLow_c, String mIcon) {
		super();
		this.mWeekday = mWeekday;
		this.mHigh_f = mHigh_f;
		this.mHigh_c = mHigh_c;
		this.mLow_f = mLow_f;
		this.mLow_c = mLow_c;
		this.mIcon = mIcon;
	}

	public String getWeekday() {
		return mWeekday;
	}

	public float getHigh_f() {
		return mHigh_f;
	}

	public float getHigh_c() {
		return mHigh_c;
	}

	public float getLow_f() {
		return mLow_f;
	}

	public float getLow_c() {
		return mLow_c;
	}

	public String getIcon() {
		return mIcon;
	}
}
