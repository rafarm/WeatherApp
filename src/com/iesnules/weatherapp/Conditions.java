package com.iesnules.weatherapp;

public class Conditions {

	private long mLocal_epoch;
	
	private String mWeather;
	
	private float mTemp_f;
	private float mTemp_c;
	
	private int mWind_degrees;
	private float mWind_mph;
	private float mWind_kph;
	
	public Conditions(long mLocal_epoch, String mWeather, float mTemp_f, float mTemp_c,
			int mWind_degrees, float mWind_mph, float mWind_kph) {
		super();
		this.mLocal_epoch = mLocal_epoch;
		this.mWeather = mWeather;
		this.mTemp_f = mTemp_f;
		this.mTemp_c = mTemp_c;
		this.mWind_degrees = mWind_degrees;
		this.mWind_mph = mWind_mph;
		this.mWind_kph = mWind_kph;
	}
	
	public long getLocal_epoch() {
		return mLocal_epoch;
	}

	public String getWeather() {
		return mWeather;
	}
	
	public float getTemp_f() {
		return mTemp_f;
	}
	
	public float getTemp_c() {
		return mTemp_c;
	}
	
	public int getWind_degrees() {
		return mWind_degrees;
	}
	
	public float getWind_mph() {
		return mWind_mph;
	}

	public float getWind_kph() {
		return mWind_kph;
	}
	
}
