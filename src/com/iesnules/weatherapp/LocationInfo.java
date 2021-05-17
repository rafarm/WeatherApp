package com.iesnules.weatherapp;

public class LocationInfo {
	

	private String mCity;
	private String mState;
	private String mCountry;
	
	private double mLongitude;
	private double mLatitude;
	
	public LocationInfo(String mCity, String mState, String mCountry,
			double mLongitude, double mLatitude) {
		super();
		this.mCity = mCity;
		this.mState = mState;
		this.mCountry = mCountry;
		this.mLongitude = mLongitude;
		this.mLatitude = mLatitude;
	}
	
	public String getCity() {
		return mCity;
	}
	
	public String getState() {
		return mState;
	}
	
	public String getCountry() {
		return mCountry;
	}
	
	public double getLongitude() {
		return mLongitude;
	}
	
	public double getLatitude() {
		return mLatitude;
	}


}
