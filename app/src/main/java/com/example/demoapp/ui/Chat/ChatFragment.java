package com.example.demoapp.ui.Chat;

import android.Manifest;
import android.app.AlertDialog;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public class ChatFragment extends Fragment {

    private static final String TAG = "ChatBluetooth";
    private static final UUID SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID TX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID RX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    private RxBleClient rxBleClient;
    private RxBleDevice selectedDevice;
    private RxBleConnection connection;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private TextView statusTextView;
    private EditText messageEditText;
    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private final ArrayList<ChatMessage> chatMessages = new ArrayList<>();
    private FusedLocationProviderClient fusedLocationClient;

    private final Map<String, RxBleDevice> discoveredDevices = new HashMap<>();
    private final ArrayList<String> deviceNamesList = new ArrayList<>();

    // Permissions launcher
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    startScan();
                } else {
                    statusTextView.setText("Permissions denied. Can't scan or connect.");
                }
            });

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);
        Bundle args = getArguments();
        String userName = args != null ? args.getString("userName", "Unknown") : "Unknown";

        ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(userName);

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

        rxBleClient = RxBleClient.create(requireContext());

        scanButton.setOnClickListener(v -> checkAndRequestPermissions());
        connectButton.setOnClickListener(v -> showDeviceSelectionDialog());
        disconnectButton.setOnClickListener(v -> disconnectFromDevice());
        sendButton.setOnClickListener(v -> sendMessageWithLocation());

        return view;
    }

    private void checkAndRequestPermissions() {
        ArrayList<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!permissionsNeeded.isEmpty()) {
            permissionLauncher.launch(permissionsNeeded.toArray(new String[0]));
        } else {
            startScan();
        }
    }

    private void startScan() {
        statusTextView.setText("Scanning...");
        discoveredDevices.clear();
        deviceNamesList.clear();

        Disposable scanDisp = rxBleClient.scanBleDevices()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(scanResult -> {
                    RxBleDevice device = scanResult.getBleDevice();
                    String mac = device.getMacAddress();
                    String name = device.getName() != null ? device.getName() : "Unknown";

                    if (!discoveredDevices.containsKey(mac)) {
                        discoveredDevices.put(mac, device);
                        deviceNamesList.add(name + " (" + mac + ")");
                        statusTextView.setText("Found: " + discoveredDevices.size() + " device(s)");
                    }
                }, t -> {
                    statusTextView.setText("Scan failed.");
                    Log.e(TAG, "Scan failed", t);
                });
        disposables.add(scanDisp);
    }

    private void showDeviceSelectionDialog() {
        if (discoveredDevices.isEmpty()) {
            statusTextView.setText("No devices found.");
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Select a device")
                .setItems(deviceNamesList.toArray(new String[0]), (dialog, which) -> {
                    String selectedText = deviceNamesList.get(which);
                    String mac = selectedText.substring(selectedText.indexOf('(') + 1, selectedText.indexOf(')'));
                    selectedDevice = discoveredDevices.get(mac);
                    statusTextView.setText("Selected: " + selectedText);
                    connectToDevice();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
                    statusTextView.setText("Connected to: " + selectedDevice.getMacAddress());
                    subscribeToNotifications();
                }, t -> {
                    statusTextView.setText("Connection failed.");
                    Log.e(TAG, "Connection failed", t);
                });
        disposables.add(connDisp);
    }

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
        selectedDevice = null;
        statusTextView.setText("Disconnected.");
    }

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    private void sendMessageWithLocation() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            String userText = messageEditText.getText().toString().trim();
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(userText)) sb.append(userText).append("\n");
            if (location != null) {
                sb.append("Location: https://www.openstreetmap.org/?mlat=")
                        .append(location.getLatitude())
                        .append("&mlon=")
                        .append(location.getLongitude());
            } else {
                sb.append("Location unavailable");
            }
            sendMessage(sb.toString());
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Location fetch failed", e);
            statusTextView.setText("Could not fetch location.");
            String userText = messageEditText.getText().toString().trim();
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(userText)) sb.append(userText).append("\n");
            sb.append("Location unavailable");
            sendMessage(sb.toString());
        });
    }

    private void sendMessage(String message) {
        if (connection == null) {
            addChatMessage("TX: " + message + " (couldn't send: Not connected)", true);
            statusTextView.setText("Not connected to device.");
        } else {
            addChatMessage("TX: " + message, true);
            Disposable writeDisp = connection
                    .writeCharacteristic(RX_CHAR_UUID, message.getBytes(StandardCharsets.UTF_8))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            bytes -> Log.d(TAG, "Sent: " + message),
                            t -> {
                                Log.e(TAG, "Write failed", t);
                                updateLastChatMessageWithError(message, t.getMessage());
                                statusTextView.setText("Send failed: " + t.getMessage());
                            }
                    );
            disposables.add(writeDisp);
        }
        messageEditText.setText("");
    }

    private void updateLastChatMessageWithError(String fullMessage, String errorMsg) {
        if (!chatMessages.isEmpty()) {
            ChatMessage lastMsg = chatMessages.get(chatMessages.size() - 1);
            lastMsg.setMessage("TX: " + fullMessage + " (couldn't send: " + errorMsg + ")");
            chatAdapter.notifyItemChanged(chatMessages.size() - 1);
        }
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
