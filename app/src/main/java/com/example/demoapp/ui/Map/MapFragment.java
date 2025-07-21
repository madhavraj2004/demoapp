package com.example.demoapp.ui.Map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.demoapp.R;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MapFragment extends Fragment {

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;

    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay;
    private Marker clickMarker;     // for manual taps
    private Marker receivedMarker;  // for location passed from ChatFragment

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_map, container, false);

        // --- osmdroid setup ---
        Configuration.getInstance().setUserAgentValue("com.example.demoapp");
        mapView = rootView.findViewById(R.id.mapView);
        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);

        // --- “my location” overlay ---
        GpsMyLocationProvider locProvider = new GpsMyLocationProvider(requireContext());
        myLocationOverlay = new MyLocationNewOverlay(locProvider, mapView);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();
        mapView.getOverlays().add(myLocationOverlay);

        // --- runtime permissions ---
        requestPermissionsIfNecessary(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        });

        // --- handle manual map taps ---
        mapView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                GeoPoint tapped = (GeoPoint)mapView.getProjection()
                        .fromPixels((int)event.getX(), (int)event.getY());
                if (tapped != null) {
                    //displayLocationInfo(tapped);
                }
                return true;
            }
            return false;
        });

        // --- check for incoming args ---
        Bundle args = getArguments();
        if (args != null && args.containsKey("lat") && args.containsKey("lon")) {
            float lat = args.getFloat("lat");
            float lon = args.getFloat("lon");
            GeoPoint fromChat = new GeoPoint(lat, lon);
            showReceivedLocationMarker(fromChat);
        } else {
            // default marker (Eiffel Tower)
            GeoPoint defaultPoint = new GeoPoint(48.8583, 2.2944);
            mapView.getController().setCenter(defaultPoint);
            addDefaultMarker(defaultPoint, "Eiffel Tower");
        }

        return rootView;
    }

    private void requestPermissionsIfNecessary(String[] permissions) {
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), p)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),
                        permissions,
                        REQUEST_PERMISSIONS_REQUEST_CODE);
                break;
            }
        }
    }

    private void showReceivedLocationMarker(GeoPoint point) {
        // remove old marker
        if (receivedMarker != null) {
            mapView.getOverlays().remove(receivedMarker);
        }

        // create new marker
        receivedMarker = new Marker(mapView);
        receivedMarker.setPosition(point);
        receivedMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        // short title
        receivedMarker.setTitle("Message Location");

        // detailed snippet
        double latitude  = point.getLatitude();
        double longitude = point.getLongitude();
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        String info = "Lat: " + latitude + "\nLon: " + longitude + "\nTime: " + time;
        receivedMarker.setSnippet(info);

        // add to map and center
        mapView.getOverlays().add(receivedMarker);
        mapView.getController().setCenter(point);

        // show the info window
        receivedMarker.showInfoWindow();

        // refresh map
        mapView.invalidate();

        // also pop a Toast if you like
        Toast.makeText(requireContext(), info, Toast.LENGTH_LONG).show();
    }


    private void addDefaultMarker(GeoPoint point, String title) {
        Marker marker = new Marker(mapView);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle(title);
        mapView.getOverlays().add(marker);
    }

//    private void displayLocationInfo(GeoPoint point) {
//        double lat = point.getLatitude();
//        double lon = point.getLongitude();
//        String time = new SimpleDateFormat(
//                "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
//        ).format(new Date());
//        String msg = "Lat: " + lat + ", Lon: " + lon + "\nTime: " + time;
//
//        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
//
//        if (clickMarker != null) {
//            mapView.getOverlays().remove(clickMarker);
//        }
//        clickMarker = new Marker(mapView);
//        clickMarker.setPosition(point);
//        clickMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
//        clickMarker.setTitle("Clicked Location");
//        clickMarker.setSnippet(msg);
//        mapView.getOverlays().add(clickMarker);
//        clickMarker.showInfoWindow();
//
//        mapView.getController().animateTo(point);
//    }

    @Override public void onResume() {
        super.onResume();
        mapView.onResume();
        if (myLocationOverlay != null) myLocationOverlay.enableMyLocation();
    }

    @Override public void onPause() {
        super.onPause();
        mapView.onPause();
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation();
    }

    @Override public void onDestroy() {
        super.onDestroy();
        mapView.onDetach();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) return;
            }
            if (myLocationOverlay != null) {
                myLocationOverlay.enableMyLocation();
                myLocationOverlay.enableFollowLocation();
            }
        }
    }
}
