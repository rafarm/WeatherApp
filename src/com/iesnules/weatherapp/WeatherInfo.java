package com.iesnules.weatherapp;

import java.util.ArrayList;

public class WeatherInfo {

	private LocationInfo mLocationInfo;
	private Conditions mConditions;
	private ArrayList<Forecast> mForecast;
	
	public WeatherInfo(LocationInfo mLocationInfo, Conditions mConditions, ArrayList<Forecast> mForecast) {
		super();
		this.mLocationInfo = mLocationInfo;
		this.mConditions = mConditions;
		this.mForecast = mForecast;
	}

	public LocationInfo getLocationInfo() {
		return mLocationInfo;
	}
	
	public Conditions getConditions() {
		return mConditions;
	}
	
	public ArrayList<Forecast> getForecast() {
		return mForecast;
	}

}
