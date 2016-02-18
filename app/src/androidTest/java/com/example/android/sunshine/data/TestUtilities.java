package com.example.android.sunshine.data;

import android.content.ContentValues;
import android.test.AndroidTestCase;

public class TestUtilities extends AndroidTestCase {
    static final String TEST_LOCATION = "99705";

    static ContentValues createNorthPoleLocationValues() {
        // create a new map of values, where column names are the keys
        ContentValues testValues = new ContentValues();
        testValues.put(WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING, TEST_LOCATION);
        testValues.put(WeatherContract.LocationEntry.COLUMN_CITY_NAME, "North Pole");
        testValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LAT, 64.7488);
        testValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LONG, -147.353);

        return testValues;
    }
}
