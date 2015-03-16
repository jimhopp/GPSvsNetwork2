package org.jimhopp.GPSvsNetwork;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.jimhopp.GPSvsNetwork.model.PhoneLocationModel;
import org.jimhopp.GPSvsNetwork.provider.LocationContentProvider;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class GPSvsNetworkMap extends FragmentActivity
    implements OnMapReadyCallback {

    public static final String TAG = "GPSvsNetwork";
    public static final String MAP_TYPE = "MapType";

	private PhoneLocationModel model;

    private GoogleMap map;

    private int initialMapType;

    private Location prevgps, prevnetwork;

    private class LocationObserver extends ContentObserver {

		public LocationObserver(Handler handler) {
			super(handler);
		}

		public void onChange(boolean selfChange) {
			Log.i(TAG, "onChange() called");
			updateMarkers();
		}
	}
	
	public PhoneLocationModel getModel() { return model; }

	void updateMarkers() {
        if (map == null) {
            Log.w(TAG, "updateMarkers called but map is null");
            return;
        }
		Log.i(TAG, "updateMarkers called on thread " + Thread.currentThread());
		Location gps = model.getGPSLocation();
		Location network = model.getNetworkLocation();
        Log.i(TAG, "gps too close    : " + tooClose(gps, prevgps));
        Log.i(TAG, "network too close: " + tooClose(network, prevnetwork));

		if (gps != null && !tooClose(gps, prevgps)) {
            Log.i(TAG, "adding gps marker");
		    map.addMarker(new MarkerOptions()
                            .position(new LatLng(gps.getLatitude(), gps.getLongitude()))
                            .title("GPS")
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.gps_marker)));
		}
		if (network != null && !tooClose(network, prevnetwork)) {
            Log.i(TAG, "adding network marker");
			map.addMarker(new MarkerOptions()
                            .position(new LatLng(network.getLatitude(), network.getLongitude()))
                            .title("Network")
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.network_marker))
                         );
		}
		if (gps != null && network != null) {
			double diffLate6 = Math.abs(gps.getLatitude()
					- network.getLatitude()) * 1e6;
			double diffLone6 = Math.abs(gps.getLongitude()
					- network.getLongitude()) * 1e6;

			double avgLat = (gps.getLatitude() + network.getLatitude()) / 2;
			double avgLon = (gps.getLongitude() + network.getLongitude()) / 2;
			//TODO: calc zoom setting that includes both locations
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(avgLat, avgLon), 15));
		} else if (network != null) {
			map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(network.getLatitude(),
                    network.getLongitude()), 15));
		} else if (gps != null) {
			map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(gps.getLatitude(),
                    gps.getLongitude()), 15));
		} else {
			Log.i(TAG, "gps and network both null");
		}
        prevgps = gps; prevnetwork = network;
	}

    boolean tooClose(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null) return false;
        Log.i(TAG, "loc1 to loc2 = " + loc1.distanceTo(loc2));
        return loc1.distanceTo(loc2) < 10.0f;
    }

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        initialMapType = prefs.getInt(MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL);

		setContentView(R.layout.main);

        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
		//TODO: restore last position, restore traffic, satellite settings for map

        LocationObserver locObs = new LocationObserver(new Handler());
		this.getContentResolver().registerContentObserver(
				LocationContentProvider.LOCATIONS_URI, true, locObs);
		
		LocationManager lm = (LocationManager) this
				.getSystemService(Context.LOCATION_SERVICE);
		model = new PhoneLocationModel(lm, this);
	}

    @Override
    protected void onStop() {
        super.onStop();
        //TODO: save last loc, map settings (camera settings?)
        // Do stop the loc updates?
        if (map != null) {
            SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(MAP_TYPE, map.getMapType());

            // Using apply() because we don't care if it works or not
            editor.apply();
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        Log.i(TAG, "map is ready");
        this.map = map;
        map.setMapType(initialMapType);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, 0, Menu.NONE, "Toggle Normal/Satellite")
                .setAlphabeticShortcut('t');
		menu.add(Menu.NONE, 1, Menu.NONE, "Email Location")
				.setAlphabeticShortcut('e');
		menu.add(Menu.NONE, 2, Menu.NONE, "Dump locations")
				.setAlphabeticShortcut('d');
		menu.add(Menu.NONE, 3, Menu.NONE, "Exit").setAlphabeticShortcut('x');
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent email;
		Uri addr;
		switch (item.getItemId()) {
        case 0:
            int type = map.getMapType();
            if (type == GoogleMap.MAP_TYPE_NORMAL) {
                map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            }
            else {
                map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            }
            return true;
		case 1:
			Location locG = model.getGPSLocation();
			Location locN = model.getNetworkLocation();
			String body;
            int itemCnt = -1;
			if (locG != null && locN != null) {
				String text = "My current locations:\n"
                        + "GPS    : " + locG.getLatitude() + ", " + locG.getLongitude() + "\n"
                        + "Network: " + locN.getLatitude() + ", " + locN.getLongitude() + "\n"
                        + "Number of items in the list: " + itemCnt;
				String qmap_link;
				try {
					String query_string = "size=512x512"
							+ "&markers="
							+ URLEncoder.encode(
									"color:blue|label:G|" + locG.getLatitude()
											+ "," + locG.getLongitude(),
									"UTF-8")
							+ "&markers="
							+ URLEncoder.encode(
									"color:green|label:N|" + locN.getLatitude()
											+ "," + locN.getLongitude(),
									"UTF-8") + "&sensor=true";
					qmap_link = "http://maps.googleapis.com/maps/api/staticmap?"
							+ query_string;
				} catch (UnsupportedEncodingException e) {
					qmap_link = e.getMessage();
				}
				body = text + "\n " + qmap_link;
			} else {
				body = "(location unknown!)";
			}
			addr = Uri.fromParts("mailto", "jimhopp@gmail.com", null);
			email = new Intent(Intent.ACTION_SENDTO, addr);
			email.putExtra(Intent.EXTRA_TEXT, body);
			email.putExtra(Intent.EXTRA_SUBJECT, "my location");
			if (getPackageManager().resolveActivity(email,
					PackageManager.MATCH_DEFAULT_ONLY) != null) {
				startActivity(email);
			} else {
				Toast toast = Toast.makeText(this,
						"Sorry, email not configured on this device",
						Toast.LENGTH_SHORT);
				toast.show();
			}
			return true;

		case 2:
			addr = Uri.fromParts("mailto", "jimhopp@gmail.com", null);
			email = new Intent(Intent.ACTION_SENDTO, addr);
			email.putExtra(Intent.EXTRA_TEXT, model.dumpLocations());
			email.putExtra(Intent.EXTRA_SUBJECT, "my locations");
			if (getPackageManager().resolveActivity(email,
					PackageManager.MATCH_DEFAULT_ONLY) != null) {
				startActivity(email);
			} else {
				Toast toast = Toast.makeText(this,
						"Sorry, cannot find an editor on this device",
						Toast.LENGTH_SHORT);
				toast.show();
			}
			return true;
		case 3:
			finish();
			Log.i(TAG, "called finish(); isFinishing() says " + isFinishing());
			return true;

		default:
			break;
		}
		return false;
	}
}