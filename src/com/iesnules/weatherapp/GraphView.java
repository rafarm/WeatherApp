package com.iesnules.weatherapp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import local.iesnules.weatherapp.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;

public class GraphView extends View {
	private ArrayList<Forecast> mForecast = null;
	private Map<String, Integer> mIcons;
	private Map<String, Integer> mWeekdays;
	
	private ArrayList<Bitmap> mBitmaps = null;
	private Rect mRect = new Rect();
	private Paint mPaint = new Paint();
	
	private boolean mCelsius = true;
	
	private class LayoutMetrics {
		public final float density;
		public final int minSpacing;
		public final int origDim;
		public final int textSize;
		public final int smallTextSize;
		
		private int dim;
		private int spacing;
		
		public LayoutMetrics(float density, int minSpacing, int origDim, int textSize, int smallTextSize) {
			this.density = density;
			this.minSpacing = minSpacing;
			this.origDim = origDim;
			this.textSize = textSize;
			this.smallTextSize = smallTextSize;
		}
		
		public void computeMetrics(int width, int count) {
			dim = origDim;
			spacing = minSpacing;
			if (width < (origDim*count + minSpacing*(count-1))) { // If optimal icons distribution exceeds width...
				dim = (width - minSpacing*(count-1))/count; 	  // ...reduce icons dimensions.
			}
			else {
				spacing = (width - origDim*count)/(count-1);	  // If not, increase padding.
			}
		}
	}
	
	private LayoutMetrics mMetrics;

	public GraphView(Context context) {
		super(context);
		setupData();
	}

	public GraphView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setupData();
	}

	public GraphView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setupData();
	}
	
	// Initialize all data structures
    private void setupData() {
    	float density = getResources().getDisplayMetrics().density;
    	mMetrics = new LayoutMetrics(density, (int)(20*density), (int)(50*density), (int)(12*density), (int)(10*density));
    	
    	mIcons = new HashMap<String, Integer>();
    	mIcons.put("chanceflurries", Integer.valueOf(R.drawable.chanceflurries));
    	mIcons.put("chancerain", Integer.valueOf(R.drawable.chancerain));
    	mIcons.put("chancesleet", Integer.valueOf(R.drawable.chancesleet));
    	mIcons.put("chancesnow", Integer.valueOf(R.drawable.chancesnow));
    	mIcons.put("chancetstorms", Integer.valueOf(R.drawable.chancetstorms));
    	mIcons.put("clear", Integer.valueOf(R.drawable.clear_i));
    	mIcons.put("cloudy", Integer.valueOf(R.drawable.cloudy));
    	mIcons.put("flurries", Integer.valueOf(R.drawable.flurries));
    	mIcons.put("fog", Integer.valueOf(R.drawable.fog_i));
    	mIcons.put("hazy", Integer.valueOf(R.drawable.hazy));
    	mIcons.put("mostlycloudy", Integer.valueOf(R.drawable.mostlycloudy));
    	mIcons.put("mostlysunny", Integer.valueOf(R.drawable.mostlysunny));
    	mIcons.put("partlycloudy", Integer.valueOf(R.drawable.partlycloudy));
    	mIcons.put("partlysunny", Integer.valueOf(R.drawable.partlysunny));
    	mIcons.put("sleet", Integer.valueOf(R.drawable.sleet));
    	mIcons.put("rain", Integer.valueOf(R.drawable.rain_i));
    	mIcons.put("snow", Integer.valueOf(R.drawable.snow_i));
    	mIcons.put("sunny", Integer.valueOf(R.drawable.sunny));
    	mIcons.put("tstorms", Integer.valueOf(R.drawable.tstorms));
    	mIcons.put("unknown", Integer.valueOf(R.drawable.partlycloudy));
    	
    	mWeekdays = new HashMap<String, Integer>();
    	mWeekdays.put("Sun", Integer.valueOf(R.string.weekday_sun));
    	mWeekdays.put("Mon", Integer.valueOf(R.string.weekday_mon));
    	mWeekdays.put("Tue", Integer.valueOf(R.string.weekday_tue));
    	mWeekdays.put("Wed", Integer.valueOf(R.string.weekday_wed));
    	mWeekdays.put("Thu", Integer.valueOf(R.string.weekday_thu));
    	mWeekdays.put("Fri", Integer.valueOf(R.string.weekday_fri));
    	mWeekdays.put("Sat", Integer.valueOf(R.string.weekday_sat));
    }

	public ArrayList<Forecast> getForecast() {
		return mForecast;
	}

	public void setForecast(ArrayList<Forecast> mForecast) {
		this.mForecast = mForecast;
		if (mForecast != null) {
			// Preload forecast icons...
			int count = mForecast.size();
			mBitmaps = new ArrayList<Bitmap>();
			for (int i=0; i<count; i++) {
				Number index = mIcons.get(mForecast.get(i).getIcon());
				mBitmaps.add(BitmapFactory.decodeResource(getResources(), index.intValue()));
			}
		}
		else {
			mBitmaps = null;
		}
		
		// Get user preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		mCelsius = prefs.getBoolean(SettingsActivity.KEY_SETTINGS_TEMP_UNIT, true);
		
		invalidate(); // Ask for display refresh...
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (mForecast != null) {
			// General data
			int count = mForecast.size();
			int width = getWidth();
			int height = getHeight();
			int orientation = getResources().getConfiguration().orientation;
			
			// Layout values
			float density = mMetrics.density;
			//int minSpacing = mMetrics.minSpacing;
			//int origDim = mMetrics.origDim;
			int textSize = mMetrics.textSize;
			int smallTextSize = mMetrics.smallTextSize;
			
			// Compute icons dimensions and padding
			/*
			int dim = origDim;
			int spacing = minSpacing;
			if (width < (origDim*count + minSpacing*(count-1))) { // If optimal icons distribution exceeds width...
				dim = (width - minSpacing*(count-1))/count; 	  // ...reduce icons dimensions.
			}
			else {
				spacing = (width - origDim*count)/(count-1);	  // If not, increase padding.
			}
			*/
			mMetrics.computeMetrics(width, count);
			int dim = mMetrics.dim;
			int spacing = mMetrics.spacing;
		
			// Draw conditions forecast (bottom part)
			mRect.left = 0;				//
			mRect.right = dim;			// First icon destination rectangle
			if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
				//mRect.top = height-dim-smallTextSize;
				mRect.top = textSize;
				mRect.bottom = mRect.top + dim;
			}
			else {
				mRect.top = height-dim;
				mRect.bottom = height;
			}
			
			int x = dim/2;				//
			int y = mRect.bottom-dim;   // Positioning for first weekday
			
			float temp_min = 999;		// Extreme values...
			float temp_max = -999;		// ...for computing forecast overall max&min temperatures
			
			int text_color = getResources().getColor(R.color.text_color);
			int high_color = getResources().getColor(R.color.high_color);
			int low_color  = getResources().getColor(R.color.low_color);
			int shadow_color = getResources().getColor(R.color.black_color);
			
			mPaint.setFakeBoldText(true);
			for (int i=0; i<count; i++) {	// Let's draw all conditions icons and weekdays...
				Forecast f = mForecast.get(i);
				
				// Draw icon
				mPaint.setColor(text_color);
				canvas.drawBitmap(mBitmaps.get(i), null, mRect, null);
				
				// Draw text
				mPaint.setTextAlign(Paint.Align.CENTER);
				mPaint.setTextSize(textSize);
				mPaint.setShadowLayer(2, 0, 0, shadow_color);
				canvas.drawText(getResources().getString(mWeekdays.get(f.getWeekday())), x, y, mPaint);
				
				// Localized temperatures
				float low;
				float high;
				if (mCelsius) {
					low = f.getLow_c();
					high = f.getHigh_c();
				}
				else {
					low = f.getLow_f();
					high = f.getHigh_f();
				}
				
				// If device orientation is landscape show temperatures
				if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
					mPaint.setTextSize(textSize);
					
					//mPaint.setColor(high_color);
					mPaint.setTextAlign(Paint.Align.LEFT);
					canvas.drawText(""+(int)high+"°", mRect.left, mRect.bottom+smallTextSize, mPaint);
					
					mPaint.setShadowLayer(2, 0, 0, text_color);
					mPaint.setColor(low_color);
					mPaint.setTextAlign(Paint.Align.RIGHT);
					canvas.drawText(""+(int)low+"°", mRect.right, mRect.bottom+smallTextSize, mPaint);
				}
				else {
					// Compute max&min temperatures
					// These will be used later for computing chart limits
					if (low < temp_min) {
						temp_min = low;
					}
					if (high > temp_max) {
						temp_max = high;
					}
				}
				
				mRect.left = mRect.left + dim + spacing;	//
				mRect.right = mRect.right + dim + spacing;	// Compute next icon's destination rectangle
				
				x = x + dim + spacing;	// Compute next weekday's position
			}
			
			// Let's start drawing temperatures chart...
			// ...only if device is in portrait orientation...
			if (orientation == Configuration.ORIENTATION_PORTRAIT) {
			
				int step = 5;		//
				if (!mCelsius) {	// The distance between reference lines
					step = 10;		// will depend on temperature unit.
				}					//
				textSize = smallTextSize;			//
				int small_padding = textSize/2;		// Utility values
			
				int chart_width = width;						//
				int chart_height = height - dim - 3*textSize;	// Compute chart dimensions
			
				if (temp_min < 0) {									//
					temp_min = (((int)temp_min)/step - 1)*step;		//
				}													//
				else {												// All these cases are for computing
					temp_min = (((int)temp_min)/step)*step;			// chart upper and lower limits which
				}													// are also multiples of 'step'.
				if (temp_max > 0) {									//
					temp_max = (((int)temp_max)/step + 1)*step;		// The chart will show reference horizontal
				}													// lines at steps of 'step' degrees.
				else {												//
					temp_max = (((int)temp_max)/step)*step;			//
				}													//
			
																	// This computes the scale factor for properly
				float factor = chart_height/(temp_max - temp_min);	// display temperatures in the chart.
			
				// Draw reference lines
				int startX = dim/2;					//
				int stopX = chart_width -startX;	// All reference lines have the same width
			
				mPaint.setStrokeWidth(1*density);		//
				mPaint.setColor(text_color);    //
				mPaint.setTextSize(textSize);	// Paint's stroke and text settings
			
				int startY;
				for (int i=(int) temp_max; i>=temp_min; i-=step) { // Let's draw reference lines...
					startY = (int) ((temp_max - i)*factor) + small_padding; // Compute vertical positioning for the line
				
					canvas.drawLine(startX, startY, stopX, startY, mPaint);
				
					mPaint.setTextAlign(Paint.Align.RIGHT);										// Reference line value
					canvas.drawText(""+i+"°", startX-small_padding, startY+small_padding, mPaint);	// is displayed at the left
					mPaint.setTextAlign(Paint.Align.LEFT);										// and the right of
					canvas.drawText(""+i+"°", stopX+small_padding, startY+small_padding, mPaint);	// the line itself.
				}
			
				// Draw temperatures lines
				mPaint.setStrokeWidth(3*density);
				mPaint.setAntiAlias(true);
			
				Forecast prev_f = mForecast.get(0); // Get first day's forecast
			
				startX = dim/2;					// X axis starting and ending coordinates
				stopX = startX + dim + spacing;	// for first day's temperatures
			
				float low;
				float high;
				if (mCelsius) {
					low = prev_f.getLow_c();
					high = prev_f.getHigh_c();
				}
				else {
					low = prev_f.getLow_f();
					high = prev_f.getHigh_f();
				}
				int startY_low = (int) ((temp_max - low)*factor) + small_padding;	// Chart value for first
				int startY_high = (int) ((temp_max - high)*factor) + small_padding;	// day's temperatures.
			
				for (int i=1; i<count; i++) { // Let's draw chart lines... (we start with second day's forecast...)
					Forecast f = mForecast.get(i);
				
					if (mCelsius) {
						low = f.getLow_c();
						high = f.getHigh_c();
					}
					else {
						low = f.getLow_f();
						high = f.getHigh_f();
					}
					int stopY_low = (int) ((temp_max - low)*factor) + small_padding;	// Chart value for next
					int stopY_high = (int) ((temp_max - high)*factor) + small_padding;	// day's temperatures.
				
					mPaint.setColor(low_color);										//
					canvas.drawLine(startX, startY_low, stopX, stopY_low, mPaint);	// Draw low temperature line
				
					mPaint.setColor(high_color);										//
					canvas.drawLine(startX, startY_high, stopX, stopY_high, mPaint);	// Draw high temperature line
				
					startX = stopX;				//
					stopX += (dim + spacing);	//  
					startY_low = stopY_low;		// Current ending point is
					startY_high = stopY_high;	// the starting point for next day's temperatures
				}
			}
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		//int desiredWidth = 300;
		//int desiredHeight = 120;
		
		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);
		
		int desiredWidth = widthSize;
		int desiredHeight = optimalHeight(widthSize);
		
		int width;
		int height;
		
		switch (widthMode) {
		case MeasureSpec.AT_MOST:
			width = Math.min(desiredWidth, widthSize);
			break;
		case MeasureSpec.EXACTLY:
			width = widthSize;
			break;
		default:
			width = desiredWidth;
		}
		
		switch (heightMode) {
		case MeasureSpec.AT_MOST:
			height = Math.min(desiredHeight, heightSize);
			break;
		case MeasureSpec.EXACTLY:
			height = heightSize;
			break;
		default:
			height = desiredHeight;
		}
		
		setMeasuredDimension(width, height);
	}
	
	private int optimalHeight(int width) {
		int height = 0;
		if (mForecast != null && mForecast.size()>2) {
			mMetrics.computeMetrics(width, mForecast.size());
		
			height = 2*(mMetrics.textSize + mMetrics.dim);
		}
		
		return height;
	}
}
