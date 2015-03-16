package org.jimhopp.GPSvsNetwork.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import org.jimhopp.GPSvsNetwork.GPSvsNetworkMap;

public class LocationsContentProvider extends ContentProvider {
	
	private static final int DATABASE_VERSION = 5;
	private static final String DATABASE_NAME = "location_db";
    private static final String LOCATIONS_TABLE_NAME = "locations";
    public static final String TIME_COL = "time";
    public static final String TYPE_COL = "type";
    public static final String LAT_COL = "lat";
    public static final String LON_COL = "lon";
    public static final String ACCURACY_COL = "accuracy";
    public static final String[][] ALL_COLS = { {BaseColumns._ID, "INTEGER PRIMARY KEY AUTOINCREMENT"},
    	                                  {TIME_COL, "INTEGER"},
    	                                  {TYPE_COL, "TEXT"},
    	                                  {LAT_COL, "REAL"},
    	                                  {LON_COL, "REAL"},
    	                                  {ACCURACY_COL, "REAL"}
    	                                }; 
    private static final String[] COL_NAMES = {
    	                                ALL_COLS[0][0],
    	                                ALL_COLS[1][0],
    	                                ALL_COLS[2][0],
    	                                ALL_COLS[3][0],
    	                                ALL_COLS[4][0],
    	                                ALL_COLS[5][0]
    	                               };
    private static final String LOCATIONS_TABLE_CREATE = "CREATE TABLE " 
    		+ LOCATIONS_TABLE_NAME + " (" 
            + ALL_COLS[0][0] + " " + ALL_COLS[0][1] + ", " 
            + ALL_COLS[1][0] + " " + ALL_COLS[1][1] + ", " 
            + ALL_COLS[2][0] + " " + ALL_COLS[2][1] + ", "
            + ALL_COLS[3][0] + " " + ALL_COLS[3][1] + ", "
            + ALL_COLS[4][0] + " " + ALL_COLS[4][1] + ", "
            + ALL_COLS[5][0] + " " + ALL_COLS[5][1] + ");";
	
    private static final int ALL_LOCS = 1;
    private static final int ONE_LOC = 2;
    private static final int LAST_GPS_LOC = 3;
    private static final int LAST_NETWORK_LOC = 4;
    
    

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static
    {
        sURIMatcher.addURI(LocationContentProvider.AUTHORITY, 
        		LocationContentProvider.LOCATION_PATH + "/all", ALL_LOCS);
        sURIMatcher.addURI(LocationContentProvider.AUTHORITY, 
        		LocationContentProvider.LOCATION_PATH + "/#", ONE_LOC);
        sURIMatcher.addURI(LocationContentProvider.AUTHORITY, 
        		LocationContentProvider.LOCATION_PATH + "/" 
                + LocationContentProvider.LAST_LOCATION_GPS, LAST_GPS_LOC);
        sURIMatcher.addURI(LocationContentProvider.AUTHORITY, 
        		LocationContentProvider.LOCATION_PATH + "/" 
                + LocationContentProvider.LAST_LOCATION_NETWORK, LAST_NETWORK_LOC);
    }
    
    private LocationOpenHelper dbh;
    
    class LocationOpenHelper extends SQLiteOpenHelper {

	    public LocationOpenHelper(Context context) {
	        super(context, DATABASE_NAME, null, DATABASE_VERSION);
	        Log.i(GPSvsNetworkMap.TAG, "instantiated");
	    }

	    @Override
	    public void onCreate(SQLiteDatabase db) {
	    	Log.i(GPSvsNetworkMap.TAG, "creating database");
	        db.execSQL(LOCATIONS_TABLE_CREATE);
	    	Log.i(GPSvsNetworkMap.TAG, "created database");
	    }

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	    	Log.i(GPSvsNetworkMap.TAG, "upgrading database");
	    	db.execSQL("DROP TABLE IF EXISTS " + LOCATIONS_TABLE_NAME + ";");
	    	onCreate(db);
			
		}
	}
    
    //do not need a no-arg constructor?

	@Override
	public String getType(Uri uri) {
		int match = sURIMatcher.match(uri);
        switch (match)
        {
            case ALL_LOCS:
                return "vnd.android.cursor.dir/vnd.jimhopp.location";
            case ONE_LOC:
            case LAST_GPS_LOC:
            case LAST_NETWORK_LOC:
            	return "vnd.android.cursor.dir/vnd.jimhopp.location";
            case UriMatcher.NO_MATCH:
            	throw new RuntimeException("unmatched URI: " + uri);
            default: 
            	throw new RuntimeException("unrecognized URI: " + uri);
        }
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		try{ 
			long rowId = dbh.getWritableDatabase().insert(LOCATIONS_TABLE_NAME, null, values);
			if (rowId > 0) {
	            Uri locURi =
	                    ContentUris.withAppendedId(LocationContentProvider.LOCATIONS_URI, rowId);
	            getContext().getContentResolver().notifyChange(locURi, null);
	            return locURi;
	        }
		} catch (SQLException e) { Log.e(GPSvsNetworkMap.TAG, "Error writing new location" + e.toString());
		}
		return null;
	}

	@Override
	public boolean onCreate() {
		dbh = new LocationOpenHelper(getContext());
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = BaseColumns._ID + " ASC";
        } else {
            orderBy = sortOrder;
        }

        int match = sURIMatcher.match(uri);

        Cursor c;

        switch (match) {
            case ALL_LOCS:
                c = dbh.getReadableDatabase().query(LOCATIONS_TABLE_NAME, COL_NAMES,
                        null, 
                        null,
                        null, 
                        null, 
                        orderBy);
                c.setNotificationUri(getContext().getContentResolver(),
                        LocationContentProvider.LOCATIONS_URI);
                break;
            case LAST_GPS_LOC:
            	c = dbh.getReadableDatabase().query(LOCATIONS_TABLE_NAME, COL_NAMES,
                        BaseColumns._ID  + " in (select max(" + BaseColumns._ID + ") "
                        + "from " + LOCATIONS_TABLE_NAME + " where " + TYPE_COL + "='" 
                        + LocationContentProvider.GPS + "')",
                        null,
                        null, 
                        null, 
                        orderBy);
                c.setNotificationUri(getContext().getContentResolver(),
                        LocationContentProvider.LOCATIONS_URI);
            	break;
            case LAST_NETWORK_LOC:
            	c = dbh.getReadableDatabase().query(LOCATIONS_TABLE_NAME, COL_NAMES,
                        BaseColumns._ID  + " in (select max(" + BaseColumns._ID + ") "
                        + "from " + LOCATIONS_TABLE_NAME + " where " + TYPE_COL + "='" 
                        + LocationContentProvider.NETWORK + "')",
                        null,
                        null, 
                        null, 
                        orderBy);
                c.setNotificationUri(getContext().getContentResolver(),
                        LocationContentProvider.LOCATIONS_URI);
            	break;
            case ONE_LOC:
            	long id = ContentUris.parseId(uri);
            	c = dbh.getReadableDatabase().query(LOCATIONS_TABLE_NAME, COL_NAMES,
                        BaseColumns._ID + " = " + id,
                        null,
                        null, 
                        null, 
                        orderBy);
                c.setNotificationUri(getContext().getContentResolver(),
                        LocationContentProvider.LOCATIONS_URI);
            	break;
            default:
                throw new IllegalArgumentException("unrecognized uri format: " + uri);
        }

        return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		// TODO Auto-generated method stub
		throw new RuntimeException("update not supported");
	}
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
        int match = sURIMatcher.match(uri);
        int cnt;
        switch (match) {
            case ALL_LOCS:
            	cnt = dbh.getWritableDatabase().delete(LOCATIONS_TABLE_NAME, selection, 
            			selectionArgs);
            	break;

            default:
                throw new IllegalArgumentException("unrecognized uri format: " + uri);
        }
        return cnt;
	}
}
