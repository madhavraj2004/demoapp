package com.example.demoapp.ui.Map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.osmdroid.config.Configuration;
import org.osmdroid.library.BuildConfig;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.example.demoapp.R;

public class MapFragment extends Fragment {

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_map, container, false);

        // Set up osmdroid configuration
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        // Initialize MapView
        mapView = rootView.findViewById(R.id.mapView);
        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);

        // Set a default location (Eiffel Tower)
        GeoPoint startPoint = new GeoPoint(48.8583, 2.2944);
        mapView.getController().setCenter(startPoint);

        // Add a marker at the location
        Marker startMarker = new Marker(mapView);
        startMarker.setPosition(startPoint);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        startMarker.setTitle("Eiffel Tower");
        mapView.getOverlays().add(startMarker);

        // Initialize MyLocationNewOverlay to show current location
        GpsMyLocationProvider locationProvider = new GpsMyLocationProvider(requireContext());
        myLocationOverlay = new MyLocationNewOverlay(locationProvider, mapView);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();
        mapView.getOverlays().add(myLocationOverlay);

        // Request permissions if necessary
        requestPermissionsIfNecessary(new String[] {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION
        });

        // Set up a click listener on the map
        mapView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                GeoPoint clickedPoint = (GeoPoint) mapView.getProjection().fromPixels((int) event.getX(), (int) event.getY());
                if (clickedPoint != null) {
                    displayLocationInfo(clickedPoint);
                }
                return true;
            }
            return false;
        });


        return rootView;
    }

    private void requestPermissionsIfNecessary(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), permissions, REQUEST_PERMISSIONS_REQUEST_CODE);
                break;
            }
        }
    }

    private Marker clickMarker; // Declare at the class level

    private void displayLocationInfo(GeoPoint point) {
        double latitude = point.getLatitude();
        double longitude = point.getLongitude();
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        // Displaying the information
        String message = "Lat: " + latitude + ", Lon: " + longitude + "\nTime: " + currentTime;
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();

        // Remove the previous marker if it exists
        if (clickMarker != null) {
            mapView.getOverlays().remove(clickMarker);
            mapView.invalidate(); // Refresh the map to remove the marker
        }

        // Add a new marker at the clicked location
        clickMarker = new Marker(mapView);
        clickMarker.setPosition(point);
        clickMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        clickMarker.setTitle("Clicked Location");
        clickMarker.setSnippet(message);
        mapView.getOverlays().add(clickMarker);
        clickMarker.showInfoWindow();

        // Smoothly pan to the clicked location
        mapView.getController().animateTo(point);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            if (myLocationOverlay != null) {
                myLocationOverlay.enableMyLocation();
                myLocationOverlay.enableFollowLocation();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        if (myLocationOverlay != null) {
            myLocationOverlay.enableMyLocation();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
        }
    }
}
