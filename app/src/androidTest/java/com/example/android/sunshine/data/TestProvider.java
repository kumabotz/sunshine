package com.example.android.sunshine.data;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.test.AndroidTestCase;
import android.util.Log;

import com.example.android.sunshine.data.WeatherContract.LocationEntry;
import com.example.android.sunshine.data.WeatherContract.WeatherEntry;

public class TestProvider extends AndroidTestCase {
    public static final String LOG_TAG = TestProvider.class.getSimpleName();

    /*
        This helper function deletes all records from both database tables using the ContentProvider.
        It also queries the ContentProvider to make sure that the database has been successfully
        deleted, so it cannot be used until the Query and Delete functions have been written
        in the ContentProvider
     */
    public void deleteAllRecordsFromProvider() {
        mContext.getContentResolver().delete(
                WeatherEntry.CONTENT_URI,
                null,
                null
        );
        mContext.getContentResolver().delete(
                LocationEntry.CONTENT_URI,
                null,
                null
        );

        Cursor cursor = mContext.getContentResolver().query(
            WeatherEntry.CONTENT_URI,
            null,
            null,
            null,
            null
        );
        assertEquals("Error: Records not deleted from Weather table during delete", 0, cursor.getCount());
        cursor.close();

        cursor = mContext.getContentResolver().query(
                LocationEntry.CONTENT_URI,
                null,
                null,
                null,
                null
        );
        assertEquals("Error: Records not deleted from Location table during delete", 0, cursor.getCount());
        cursor.close();
    }

    public void deleteAllRecords() {
        deleteAllRecordsFromProvider();
    }

    // since we want each test to start with a clean state, run deleteAllRecords
    // in setUp (called by the test runner before each test)
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteAllRecords();
    }

    public void testGetType() {
        // content://com.example.android.sunshine/weather/
        String type = mContext.getContentResolver().getType(WeatherEntry.CONTENT_URI);
        // vnd.android.cursor.dir/com.example.android.sunshine/weather
        assertEquals("Error: the WeatherEntry CONTENT_URI should return WeatherEntry.CONTENT_TYPE",
                WeatherEntry.CONTENT_TYPE, type);

        String testLocation = "94074";
        // content://com.example.android.sunshine/weather/94074
        type = mContext.getContentResolver().getType(WeatherEntry.buildWeatherLocation(testLocation));
        // vnd.android.cursor.dir/com.example.android.sunshine/weather
        assertEquals("Error: the WeatherEntry CONTENT_URI with location should return WeatherEntry.CONTENT_TYPE",
                WeatherEntry.CONTENT_TYPE, type);

        long testDate = 1419120000L; // December 21st, 2014
        // content://com.example.android.sunshine/weather/94074/20141221
        type = mContext.getContentResolver().getType(
                WeatherEntry.buildWeatherLocationWithDate(testLocation, testDate));
        // vnd.android.cursor.item/com.example.android.sunshine/weather/1419120000
        assertEquals("Error: the WeatherEntry CONTENT_URI with location and date should return WeatherEntry.CONTENT_ITEM_TYPE",
                WeatherEntry.CONTENT_ITEM_TYPE, type);

        // content://com.example.android.sunshine/location
        type = mContext.getContentResolver().getType(LocationEntry.CONTENT_URI);
        // vnd.android.cursor.item/com.example.android.sunshine/location
        assertEquals("Error: the LocationEntry CONTENT_URI should return LocationEntry.CONTENT_TYPE",
                LocationEntry.CONTENT_TYPE, type);
    }

    public void testBasicLocationQueries() {
        // insert our test records into the database
        WeatherDbHelper dbHelper = new WeatherDbHelper(mContext);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues testValues = TestUtilities.createNorthPoleLocationValues();
        long locationRowId = TestUtilities.insertNorthPoleLocationValues(mContext);

        // Test the basic content provider query
        Cursor locationCursor = mContext.getContentResolver().query(
                LocationEntry.CONTENT_URI,
                null,
                null,
                null,
                null
        );

        // Make sure we get the correct cursor out of the database
        TestUtilities.validateCursor("testBasicLocationQueries, location query", locationCursor, testValues);

        // Has the NotificationUri been set correctly? --- we can only test this easily against API
        // level 19 or greater because getNotificationUri was added in API level 19.
        if ( Build.VERSION.SDK_INT >= 19 ) {
            assertEquals("Error: Location Query did not properly set NotificationUri",
                    locationCursor.getNotificationUri(), LocationEntry.CONTENT_URI);
        }
    }

    // this test uses provider to insert then update the data
    public void testUpdateLocation() {
        // create a new map of values, where column names are the keys
        ContentValues values = TestUtilities.createNorthPoleLocationValues();

        Uri locationUri = mContext.getContentResolver().
                insert(LocationEntry.CONTENT_URI, values);
        long locationRowId = ContentUris.parseId(locationUri);

        // verify we got a row back
        assertTrue(locationRowId != -1);
        Log.d(LOG_TAG, "New row id: " + locationRowId);

        ContentValues updatedValues = new ContentValues(values);
        updatedValues.put(LocationEntry._ID, locationRowId);
        updatedValues.put(LocationEntry.COLUMN_CITY_NAME, "Santa's Village");

        // create a cursor with observer to make sure that the content provider is notifying
        // the observer as expected
        Cursor locationCursor = mContext.getContentResolver().query(LocationEntry.CONTENT_URI, null, null, null, null);

        TestUtilities.TestContentObserver tco = TestUtilities.getTestContentObserver();
        locationCursor.registerContentObserver(tco);

        int count = mContext.getContentResolver().update(
                LocationEntry.CONTENT_URI, updatedValues, LocationEntry._ID + "= ?",
                new String[] { Long.toString(locationRowId) });
        assertEquals(count, 1);

        // test to make sure our observer is called, if not, we throw an assertion
        tco.waitForNotificationOrFail();

        locationCursor.unregisterContentObserver(tco);
        locationCursor.close();

        // a cursor is your primary interface to the query results
        Cursor cursor = mContext.getContentResolver().query(
                LocationEntry.CONTENT_URI,
                null, // projection
                LocationEntry._ID + " = " + locationRowId,
                null, // values for the "where" clause
                null // sort order
        );

        TestUtilities.validateCursor("testUpdateLocation. Error validating location entry update.",
                cursor, updatedValues);
        cursor.close();
    }

    public void testInsertReadProvider() {
        ContentValues testValues = TestUtilities.createNorthPoleLocationValues();

        // Register a content observer for our insert. This time, directly with the content resolver
        TestUtilities.TestContentObserver tco = TestUtilities.getTestContentObserver();
        mContext.getContentResolver().registerContentObserver(LocationEntry.CONTENT_URI, true, tco);
        Uri locationUri = mContext.getContentResolver().insert(LocationEntry.CONTENT_URI, testValues);

        // did our content observer get called?
        tco.waitForNotificationOrFail();
        mContext.getContentResolver().unregisterContentObserver(tco);

        long locationRowId = ContentUris.parseId(locationUri);

        // verify we got a row back
        assertTrue(locationRowId != -1);

        // data's inserted. IN THEORY. Now pull some out to stare at if and verify it made
        // the round trip

        // a cursor is your primary interface to the query results
        Cursor cursor = mContext.getContentResolver().query(
                LocationEntry.CONTENT_URI,
                null, // leaving "columns" null just returns all the columns
                null, // columns for "where" clause
                null, // values for "where" clause
                null  // sort order
        );

        TestUtilities.validateCursor("testInsertReadProvider. Error validating LocationEntry.",
                cursor, testValues);

        // fantastic, now that we have a location, add some weather
        ContentValues weatherValues = TestUtilities.createWeatherValues(locationRowId);
        // the TestContentObserver is a one-shot class
        tco = TestUtilities.getTestContentObserver();

        mContext.getContentResolver().registerContentObserver(WeatherEntry.CONTENT_URI, true, tco);

        Uri weatherInsertUri = mContext.getContentResolver()
                .insert(WeatherEntry.CONTENT_URI, weatherValues);
        assertTrue(weatherInsertUri != null);

        // did our content observer get called?
        tco.waitForNotificationOrFail();
        mContext.getContentResolver().unregisterContentObserver(tco);

        // a cursor is your primary interface to the query results
        Cursor weatherCursor = mContext.getContentResolver().query(
                WeatherEntry.CONTENT_URI, // table to query
                null, // leaving "columns" null just returns all the columns
                null, // columns for "where" clause
                null, // values for "where" clause
                null  // columns for group by
        );
        TestUtilities.validateCursor("testInsertReadProvider. Error validating WeatherEntry insert.",
                weatherCursor, weatherValues);

        // add the location values in with the weather data so that wa can make sure that the join
        // worked and we actually get all the values back
        weatherValues.putAll(testValues);

        // get the joined Weather and Location data
        weatherCursor = mContext.getContentResolver().query(
                WeatherEntry.buildWeatherLocation(TestUtilities.TEST_LOCATION),
                null, // leaving "columns" null just returns all the columns
                null, // columns for "where" clause
                null, // values for "where" clause
                null  // sort order
        );
        TestUtilities.validateCursor("testInsertReadProvider. Error validating joined Weather and Location Data.",
                weatherCursor, weatherValues);

        // get the joined Weather and Location data with a start date
        weatherCursor = mContext.getContentResolver().query(
                WeatherEntry.buildWeatherLocationWithStartDate(TestUtilities.TEST_LOCATION, TestUtilities.TEST_DATE),
                null, // leaving "columns" null just returns all the columns
                null, // columns for "where" clause
                null, // values for "where" clause
                null  // sort order
        );
        TestUtilities.validateCursor("testInsertReadProvider. Error validating joined Weather and Location Data with start date.",
                weatherCursor, weatherValues);

        // get the joined Weather data for a specific date
        weatherCursor = mContext.getContentResolver().query(
                WeatherEntry.buildWeatherLocationWithDate(TestUtilities.TEST_LOCATION, TestUtilities.TEST_DATE),
                null, // leaving "columns" null just returns all the columns
                null, // columns for "where" clause
                null, // values for "where" clause
                null  // sort order
        );
        TestUtilities.validateCursor("testInsertReadProvider. Error validating joined Weather and Location Data for a specific date.",
                weatherCursor, weatherValues);
    }

    // make sure we can still delete after adding/updating stuff
    public void testDeleteRecords() {
        testInsertReadProvider();

        // register a content observer for our location delete
        TestUtilities.TestContentObserver locationObserver = TestUtilities.getTestContentObserver();
        mContext.getContentResolver().registerContentObserver(LocationEntry.CONTENT_URI, true, locationObserver);

        // register a content observer for our weather delete
        TestUtilities.TestContentObserver weatherObserver = TestUtilities.getTestContentObserver();
        mContext.getContentResolver().registerContentObserver(WeatherEntry.CONTENT_URI, true, weatherObserver);

        deleteAllRecordsFromProvider();

        locationObserver.waitForNotificationOrFail();
        weatherObserver.waitForNotificationOrFail();

        mContext.getContentResolver().unregisterContentObserver(locationObserver);
        mContext.getContentResolver().unregisterContentObserver(weatherObserver);
    }
}
