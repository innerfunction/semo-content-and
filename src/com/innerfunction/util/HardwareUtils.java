package com.innerfunction.util;

import android.content.Context;
import android.util.DisplayMetrics;

public class HardwareUtils {

	public static String getDeviceDensity(Context mContext) {
		String deviceDensity = "";
		switch (mContext.getResources().getDisplayMetrics().densityDpi) {
		case DisplayMetrics.DENSITY_LOW:
			deviceDensity = "ldpi";
			break;
		case DisplayMetrics.DENSITY_MEDIUM:
			deviceDensity = "mdpi";
			break;
		case DisplayMetrics.DENSITY_HIGH:
			deviceDensity = "hdpi";
			break;
		case DisplayMetrics.DENSITY_XHIGH:
			deviceDensity = "xhdpi";
			break;
		case DisplayMetrics.DENSITY_XXHIGH:
			deviceDensity = "xxhdpi";
		}
		return deviceDensity;
	}

}
