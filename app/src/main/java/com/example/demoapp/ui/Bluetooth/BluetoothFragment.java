package com.example.demoapp.ui.Bluetooth;

import static com.polidea.rxandroidble3.internal.logger.LoggerUtil.bytesToHex;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.demoapp.R;
import com.polidea.rxandroidble3.RxBleClient;
import com.polidea.rxandroidble3.RxBleDevice;
import com.polidea.rxandroidble3.RxBleConnection;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public class BluetoothFragment extends Fragment {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String CHARACTERISTIC_UUID = "0000fef4-0000-1000-8000-00805f9b34fb";
    private static final String TARGET_DEVICE_MAC = "30:30:F9:77:05:32";
    private TextView statusTextView;
    private TextView receivedDataTextView;

    private RxBleClient rxBleClient;
    private RxBleDevice selectedDevice;
    private RxBleConnection connection;
    private Disposable connectionDisposable;
    private CompositeDisposable disposables = new CompositeDisposable();

    private Handler handler = new Handler();
    private Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            if (connection != null) {
                readCharacteristic();
                handler.postDelayed(this, 5000);
            }
        }
    };




    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_bluetooth, container, false);

        // Access the views using the inflated view
        statusTextView = view.findViewById(R.id.statusTextView);
        receivedDataTextView = view.findViewById(R.id.receivedDataTextView);

        Button scanButton = view.findViewById(R.id.btn_scan);
        Button connectButton = view.findViewById(R.id.btn_connect);

        scanButton.setOnClickListener(v -> startScan());
        connectButton.setOnClickListener(v -> connectToDevice());

        checkPermissions();
        rxBleClient = RxBleClient.create(getContext());

        return view;
    }


    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        }
    }



    private void startScan() {
        statusTextView.setText("Scanning...");
        Disposable scanDisposable = rxBleClient.scanBleDevices()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(scanResult -> {
                    if (scanResult.getBleDevice().getMacAddress().equals(TARGET_DEVICE_MAC)) {
                        selectedDevice = scanResult.getBleDevice();
                        statusTextView.setText("Target device found: " + selectedDevice.getName());
                    }
                }, throwable -> {
                    statusTextView.setText("Scan failed.");
                    Log.e("BLE", "Scan failed: " + throwable.toString());
                });
        disposables.add(scanDisposable);
    }
    private void connectToDevice() {
        if (selectedDevice == null) {
            statusTextView.setText("No device selected.");
            Log.d("BLE", "No device selected.");
            return;
        }

        connectionDisposable = selectedDevice.establishConnection(false)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        rxBleConnection -> {
                            connection = rxBleConnection;
                            statusTextView.setText("Connected.");
                            Log.d("BLE", "Connected to device.");

                            // Start the periodic task to read the characteristic every 5 seconds
                            handler.postDelayed(updateTask, 5000);
                        },
                        throwable -> {
                            statusTextView.setText("Connection failed.");
                            Log.e("BLE", "Connection failed: " + throwable.toString());
                        });
        disposables.add(connectionDisposable);
    }


    private void readCharacteristic() {
        if (connection != null) {
            connection.readCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            bytes -> {
                                // Convert bytes to string (or parse it to JSON if needed)
                                String data = new String(bytes, StandardCharsets.UTF_8);
                                receivedDataTextView.setText(data); // Show received data
                            },
                            throwable -> {
                                Log.e("BLE", "Error reading characteristic", throwable);
                            });
        }
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (connectionDisposable != null && !connectionDisposable.isDisposed()) {
            connectionDisposable.dispose();
        }
        if (!disposables.isDisposed()) {
            disposables.dispose();
        }
        handler.removeCallbacks(updateTask);  // Stop the periodic task when the fragment is destroyed
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BluetoothFragment.PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0) {
                boolean allPermissionsGranted = true;
                for (int grantResult : grantResults) {
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false;
                        break;
                    }
                }
                if (allPermissionsGranted) {
                    // Permissions granted, proceed with Bluetooth functionality
                } else {
                    // Permissions denied, handle accordingly
                }
            }
        }
    }
}

//    <item
//android:id="@+id/navigation_bluetooth"
//android:icon="@drawable/ic_notifications_black_24dp"
//android:title="@string/title_bluetooth" />

