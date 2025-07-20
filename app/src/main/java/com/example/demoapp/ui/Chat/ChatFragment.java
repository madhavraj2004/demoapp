package com.example.demoapp.ui.Chat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

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

    // Nordic UART Service UUIDs
    private static final UUID SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID TX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"); // notify from device
    private static final UUID RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"); // write to device

    // Target device MAC
    private static final String TARGET_DEVICE_MAC = "48:27:E2:3D:D0:D6";

    private RxBleClient rxBleClient;
    private RxBleDevice selectedDevice;
    private RxBleConnection connection;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // UI Elements
    private TextView statusTextView;
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

        statusTextView = view.findViewById(R.id.statusTextView);
        messageEditText = view.findViewById(R.id.messageEditText);
        chatRecyclerView = view.findViewById(R.id.chatRecyclerView);
        Button scanButton = view.findViewById(R.id.btn_scan);
        Button connectButton = view.findViewById(R.id.btn_connect);
        Button disconnectButton = view.findViewById(R.id.btn_disconnect);
        ImageButton sendButton = view.findViewById(R.id.sendButton);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        chatAdapter = new ChatAdapter(chatMessages);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        chatRecyclerView.setAdapter(chatAdapter);

        scanButton.setOnClickListener(v -> startScan());
        connectButton.setOnClickListener(v -> connectToDevice());
        disconnectButton.setOnClickListener(v -> disconnectFromDevice());
        sendButton.setOnClickListener(v -> sendMessageWithLocation());

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
        Disposable scanDisp = rxBleClient.scanBleDevices()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(scanResult -> {
                    if (scanResult.getBleDevice().getMacAddress().equals(TARGET_DEVICE_MAC)) {
                        selectedDevice = scanResult.getBleDevice();
                        statusTextView.setText("Device found: " + selectedDevice.getName());
                    }
                }, t -> {
                    statusTextView.setText("Scan failed.");
                    Log.e(TAG, "Scan failed", t);
                });
        disposables.add(scanDisp);
    }

    private void connectToDevice() {
        if (selectedDevice == null) {
            statusTextView.setText("No device selected.");
            return;
        }
        Disposable connDisp = selectedDevice.establishConnection(false)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(rxBleConnection -> {
                    connection = rxBleConnection;
                    statusTextView.setText("Connected.");

                    // Once connected, subscribe to incoming messages
                    subscribeToNotifications();
                }, t -> {
                    statusTextView.setText("Connection failed.");
                    Log.e(TAG, "Connection failed", t);
                });
        disposables.add(connDisp);
    }

    /**
     * Handles incoming BLE notifications on the TX characteristic.
     * Each received byte array is converted to UTF-8 string and displayed in chat.
     */
    private void subscribeToNotifications() {
        Disposable notifDisp = connection
                .setupNotification(TX_CHAR_UUID)
                .flatMap(obs -> obs)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bytes -> {
                    String received = new String(bytes, StandardCharsets.UTF_8);
                    addChatMessage("RX: " + received, false);
                }, t -> Log.e(TAG, "Notification error", t));
        disposables.add(notifDisp);
    }

    private void disconnectFromDevice() {
        disposables.clear();
        connection = null;
        statusTextView.setText("Disconnected.");
    }

    private void sendMessageWithLocation() {
        if (connection == null) {
            statusTextView.setText("Not connected to device.");
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_CODE);
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            String userText = messageEditText.getText().toString().trim();
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(userText)) sb.append(userText).append("\n");
            if (location != null) {
                sb.append("Location: https://www.openstreetmap.org/?mlat=")
                        .append(location.getLatitude())
                        .append("&mlon=")
                        .append(location.getLongitude());
            } else sb.append("Location unavailable");
            final String fullMessage = sb.toString();

            addChatMessage("TX: " + fullMessage, true);
            messageEditText.setText("");

            Disposable writeDisp = connection
                    .writeCharacteristic(RX_CHAR_UUID, fullMessage.getBytes(StandardCharsets.UTF_8))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            bytes -> Log.d(TAG, "Sent: " + fullMessage),
                            t -> {
                                Log.e(TAG, "Write failed", t);
                                statusTextView.setText("Send failed: " + t.getMessage());
                            }
                    );
            disposables.add(writeDisp);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Location fetch failed", e);
            statusTextView.setText("Could not fetch location.");
        });
    }

    private void addChatMessage(String text, boolean isSent) {
        ChatMessage msg = new ChatMessage(text, isSent);
        chatMessages.add(msg);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.dispose();
    }
}
