package com.example.demoapp.ui.Chat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
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
import android.widget.Toast;

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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public class ChatFragment extends Fragment {
    private static final String TAG        = "ChatBluetooth";
    private static final UUID   SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID   RX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID   TX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final int    CHUNK_SIZE   = 100; // MTU–3

    private RxBleClient rxBleClient;
    private RxBleConnection connection;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private TextView statusTextView;
    private EditText messageEditText;
    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private final ArrayList<ChatMessage> chatMessages = new ArrayList<>();

    private FusedLocationProviderClient fusedLocationClient;
    private final Map<String, RxBleDevice> discovered = new HashMap<>();
    private final ArrayList<String> deviceNames = new ArrayList<>();
    private RxBleDevice selectedDevice;

    // Buffer for incoming RX fragments
    private final ByteArrayOutputStream incomingBuffer = new ByteArrayOutputStream();

    // permissions
    private final ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean all = true;
                for (Boolean g : result.values()) if (!g) { all = false; break; }
                if (all) startScan();
                else statusTextView.setText("Permissions denied");
            });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chat, container, false);
        ((AppCompatActivity)requireActivity()).getSupportActionBar().setTitle("iPolluSense Chat");

        statusTextView   = v.findViewById(R.id.statusTextView);
        messageEditText  = v.findViewById(R.id.messageEditText);
        chatRecyclerView = v.findViewById(R.id.chatRecyclerView);
        Button scanBtn      = v.findViewById(R.id.btn_scan);
        Button connectBtn   = v.findViewById(R.id.btn_connect);
        Button disconnectBtn= v.findViewById(R.id.btn_disconnect);
        ImageButton sendBtn = v.findViewById(R.id.sendButton);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        chatAdapter = new ChatAdapter(chatMessages);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        chatRecyclerView.setAdapter(chatAdapter);
        rxBleClient = RxBleClient.create(requireContext());

        scanBtn.setOnClickListener(x -> checkPermissions());
        connectBtn.setOnClickListener(x -> showDeviceDialog());
        disconnectBtn.setOnClickListener(x -> disconnect());
        sendBtn.setOnClickListener(x -> {
            // runtime check for location perms
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                sendMessageWithLocation();

            } else {
                permLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                });
            }
        });

        return v;
    }

    private void checkPermissions() {
        ArrayList<String> perms = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.BLUETOOTH_SCAN);
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (!perms.isEmpty()) permLauncher.launch(perms.toArray(new String[0]));
        else startScan();
    }

    private void startScan() {
        statusTextView.setText("Scanning…");
        discovered.clear(); deviceNames.clear();

        Disposable d = rxBleClient.scanBleDevices()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(scanResult -> {
                    RxBleDevice dev = scanResult.getBleDevice();
                    String mac = dev.getMacAddress();
                    String name = dev.getName() != null ? dev.getName() : mac;
                    if (!discovered.containsKey(mac)) {
                        discovered.put(mac, dev);
                        deviceNames.add(name + " (" + mac + ")");
                        statusTextView.setText("Found " + discovered.size());
                    }
                }, t -> {
                    statusTextView.setText("Scan failed");
                    Log.e(TAG, "scan", t);
                });
        disposables.add(d);
    }

    private void showDeviceDialog() {
        if (discovered.isEmpty()) {
            statusTextView.setText("No devices");
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Select device")
                .setItems(deviceNames.toArray(new String[0]), (dlg, which) -> {
                    String pick = deviceNames.get(which);
                    String mac = pick.substring(pick.indexOf('(')+1, pick.indexOf(')'));
                    selectedDevice = discovered.get(mac);
                    statusTextView.setText("Selected " + pick);
                    connect();
                }).show();
    }

    private void connect() {
        if (selectedDevice == null) return;
        Disposable d = selectedDevice.establishConnection(false)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(conn -> {
                    connection = conn;
                    statusTextView.setText("Connected");
                    subscribeNotifications();
                }, t -> {
                    statusTextView.setText("Connect failed");
                    Log.e(TAG, "conn", t);
                });
        disposables.add(d);
    }

    private void subscribeNotifications() {
        Disposable d = connection
                .setupNotification(TX_CHAR_UUID)
                .flatMap(obs -> obs)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bytes -> {
                    // accumulate fragments
                    incomingBuffer.write(bytes, 0, bytes.length);
                    // if this chunk is smaller than CHUNK_SIZE, it's the last
                    if (bytes.length < CHUNK_SIZE) {
                        String full = new String(incomingBuffer.toByteArray(), StandardCharsets.UTF_8);
                        addChatMessage("RX: " + full, false);
                        incomingBuffer.reset();
                    }
                }, t -> Log.e(TAG, "notif", t));
        disposables.add(d);
    }

    private void disconnect() {
        disposables.clear();
        connection = null;
        statusTextView.setText("Disconnected");
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    })
    private void sendMessageWithLocation() {
        // final safety check
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(),
                    "Location permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    String text = messageEditText.getText().toString().trim();
                    StringBuilder sb = new StringBuilder();
                    if (!TextUtils.isEmpty(text)) sb.append(text).append("\n");
                    if (location != null) {
                        sb.append("Location: https://www.openstreetmap.org/?mlat=")
                                .append(location.getLatitude())
                                .append("&mlon=").append(location.getLongitude());
                    } else {
                        sb.append("Location unavailable");
                    }
                    splitAndSend(sb.toString());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Location failed", e);
                    Toast.makeText(requireContext(),
                            "Could not fetch location", Toast.LENGTH_SHORT).show();
                });
    }

    private void splitAndSend(String msg) {
        if (connection == null) {
            addChatMessage("TX: " + msg + " (not connected)", true);
            return;
        }
        addChatMessage("TX: " + msg, true);
        messageEditText.setText("");

        Disposable d = connection
                .requestMtu(CHUNK_SIZE + 3)
                .flatMapPublisher(mtu -> {
                    byte[] data = msg.getBytes(StandardCharsets.UTF_8);
                    List<byte[]> chunks = new ArrayList<>();
                    for (int i = 0; i < data.length; i += CHUNK_SIZE) {
                        int end = Math.min(data.length, i + CHUNK_SIZE);
                        chunks.add(Arrays.copyOfRange(data, i, end));
                    }
                    return Flowable.fromIterable(chunks);
                })
                .concatMap(chunk ->
                        connection.writeCharacteristic(RX_CHAR_UUID, chunk).toFlowable()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        bytes -> Log.d(TAG, "chunk sent"),
                        t -> {
                            Log.e(TAG, "send failed", t);
                            updateLastChatMessageWithError(msg, t.getMessage());
                        }
                );
        disposables.add(d);
    }

    private void addChatMessage(String text, boolean isSent) {
        chatMessages.add(new ChatMessage(text, isSent));
        chatAdapter.notifyItemInserted(chatMessages.size()-1);
        chatRecyclerView.scrollToPosition(chatMessages.size()-1);
    }

    private void updateLastChatMessageWithError(String fullMessage, String errorMsg) {
        if (!chatMessages.isEmpty()) {
            ChatMessage lastMsg = chatMessages.get(chatMessages.size() - 1);
            lastMsg.setMessage("TX: " + fullMessage + " (couldn't send: " + errorMsg + ")");
            chatAdapter.notifyItemChanged(chatMessages.size() - 1);
        }
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }
}
