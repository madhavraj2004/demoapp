package com.example.demoapp.ui.Chat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demoapp.ChatAdapter;
import com.example.demoapp.ChatMessage;
import com.example.demoapp.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.polidea.rxandroidble3.RxBleClient;
import com.polidea.rxandroidble3.RxBleConnection;
import com.polidea.rxandroidble3.RxBleDevice;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public class ChatFragment extends Fragment {

    private static final String TAG = "ChatBluetooth";
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String CHARACTERISTIC_UUID = "0000fef4-0000-1000-8000-00805f9b34fb";
    private static final String TARGET_DEVICE_MAC = "30:30:F9:77:05:32";

    // Bluetooth
    private RxBleClient rxBleClient;
    private RxBleDevice selectedDevice;
    private RxBleConnection connection;
    private Disposable connectionDisposable;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Handler handler = new Handler();
    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            if (connection != null) {
                readCharacteristic();
                handler.postDelayed(this, 5000);
            }
        }
    };

    // UI Elements
    private TextView statusTextView, receivedDataTextView;
    private EditText messageEditText;
    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private final ArrayList<ChatMessage> chatMessages = new ArrayList<>();
    private FusedLocationProviderClient fusedLocationClient;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        // Init Views
        statusTextView = view.findViewById(R.id.statusTextView);
        receivedDataTextView = view.findViewById(R.id.receivedDataTextView);
        messageEditText = view.findViewById(R.id.messageEditText);
        chatRecyclerView = view.findViewById(R.id.chatRecyclerView);
        Button scanButton = view.findViewById(R.id.btn_scan);
        Button connectButton = view.findViewById(R.id.btn_connect);
        Button disconnectButton = view.findViewById(R.id.btn_disconnect);
        ImageButton sendButton = view.findViewById(R.id.sendButton);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // Init Chat
        chatAdapter = new ChatAdapter(chatMessages);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        chatRecyclerView.setAdapter(chatAdapter);

        // Button listeners
        scanButton.setOnClickListener(v -> startScan());
        connectButton.setOnClickListener(v -> connectToDevice());
        disconnectButton.setOnClickListener(v -> disconnectFromDevice());
        sendButton.setOnClickListener(v -> sendMessageWithLocation());

        // Init BLE
        rxBleClient = RxBleClient.create(requireContext());
        checkPermissions();

        return view;
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
        };
        ArrayList<String> missing = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), p) != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private void startScan() {
        statusTextView.setText("Scanning...");
        Disposable scanDisposable = rxBleClient.scanBleDevices()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(scanResult -> {
                    if (scanResult.getBleDevice().getMacAddress().equals(TARGET_DEVICE_MAC)) {
                        selectedDevice = scanResult.getBleDevice();
                        statusTextView.setText("Device found: " + selectedDevice.getName());
                    }
                }, throwable -> {
                    statusTextView.setText("Scan failed.");
                    Log.e(TAG, "Scan failed", throwable);
                });
        disposables.add(scanDisposable);
    }

    private void connectToDevice() {
        if (selectedDevice == null) {
            statusTextView.setText("No device selected.");
            return;
        }

        connectionDisposable = selectedDevice.establishConnection(false)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(rxBleConnection -> {
                    connection = rxBleConnection;
                    statusTextView.setText("Connected.");
                    handler.postDelayed(updateTask, 5000);
                }, throwable -> {
                    statusTextView.setText("Connection failed.");
                    Log.e(TAG, "Connection failed", throwable);
                });

        disposables.add(connectionDisposable);
    }

    private void disconnectFromDevice() {
        if (connectionDisposable != null && !connectionDisposable.isDisposed()) {
            connectionDisposable.dispose();
            connection = null;
            statusTextView.setText("Disconnected.");
        }
    }

    private void readCharacteristic() {
        if (connection != null) {
            connection.readCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(bytes -> {
                        String data = new String(bytes, StandardCharsets.UTF_8);
                        receivedDataTextView.setText(data);
                    }, throwable -> Log.e(TAG, "Read failed", throwable));
        }
    }

    private void sendMessageWithLocation() {
        String message = messageEditText.getText().toString().trim();
        if (TextUtils.isEmpty(message)) return;

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            String fullMessage = message;
            if (location != null) {
                String osmLink = "https://www.openstreetmap.org/?mlat=" + location.getLatitude() + "&mlon=" + location.getLongitude();
                fullMessage += "\nLocation: " + osmLink;
            } else {
                fullMessage += "\n(Location unavailable)";
            }

            // Add to UI chat
            ChatMessage chatMessage = new ChatMessage(fullMessage, true);
            chatMessages.add(chatMessage);
            chatAdapter.notifyItemInserted(chatMessages.size() - 1);
            chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
            messageEditText.setText("");

            // BLE: Send to device
            sendMessageOverBLE(fullMessage);

            // Simulate receiver response
            receiveMessage("Received: " + fullMessage);
        });
    }

    private void sendMessageOverBLE(String message) {
        if (connection == null) {
            Log.w(TAG, "BLE connection not available.");
            return;
        }

        byte[] data = message.getBytes(StandardCharsets.UTF_8);

        connection.writeCharacteristic(UUID.fromString(CHARACTERISTIC_UUID), data)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        writtenBytes -> Log.d(TAG, "Message sent over BLE: " + message),
                        throwable -> Log.e(TAG, "Failed to send message over BLE", throwable)
                );
    }

    private void receiveMessage(String msg) {
        chatRecyclerView.postDelayed(() -> {
            chatMessages.add(new ChatMessage(msg, false));
            chatAdapter.notifyItemInserted(chatMessages.size() - 1);
            chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
        }, 1000);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (connectionDisposable != null && !connectionDisposable.isDisposed()) {
            connectionDisposable.dispose();
        }
        if (!disposables.isDisposed()) disposables.dispose();
        handler.removeCallbacks(updateTask);
    }
}
