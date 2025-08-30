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

import org.json.JSONException;
import org.json.JSONObject;

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
    private static final String TAG = "ChatBluetooth";
    private static final UUID SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID RX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID TX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final int CHUNK_SIZE = 125; // MTU–3

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
    private final ArrayList<String> deviceNamesList = new ArrayList<>();
    private RxBleDevice selectedDevice;

    // Group chat variables
    private String currentGroupId = null;
    private String currentGroupName = "Default Group";
    private final Map<String, RxBleDevice> groupMembers = new HashMap<>();
    private final Map<String, String> deviceNames = new HashMap<>();
    private Button createGroupBtn;
    private Button joinGroupBtn;
    private Button inviteDeviceBtn;
    private TextView groupInfoTextView;

    // Buffer for incoming RX fragments
    private final ByteArrayOutputStream incomingBuffer = new ByteArrayOutputStream();

    // permissions
    private final ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean all = true;
                for (Boolean g : result.values()) if (!g) {
                    all = false;
                    break;
                }
                if (all) startScan();
                else statusTextView.setText("Permissions denied");
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chat, container, false);
        ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle("iPolluSense Chat");

        statusTextView = v.findViewById(R.id.statusTextView);
        messageEditText = v.findViewById(R.id.messageEditText);
        chatRecyclerView = v.findViewById(R.id.chatRecyclerView);
        Button scanBtn = v.findViewById(R.id.btn_scan);
        Button connectBtn = v.findViewById(R.id.btn_connect);
        Button disconnectBtn = v.findViewById(R.id.btn_disconnect);
        ImageButton sendBtn = v.findViewById(R.id.sendButton);

        // Group chat buttons
        createGroupBtn = v.findViewById(R.id.btn_create_group);
        joinGroupBtn = v.findViewById(R.id.btn_join_group);
        inviteDeviceBtn = v.findViewById(R.id.btn_invite_device);
        groupInfoTextView = v.findViewById(R.id.groupInfoTextView);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        chatAdapter = new ChatAdapter(chatMessages);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        chatRecyclerView.setAdapter(chatAdapter);
        rxBleClient = RxBleClient.create(requireContext());

        scanBtn.setOnClickListener(x -> checkPermissions());
        connectBtn.setOnClickListener(x -> showDeviceDialog());
        disconnectBtn.setOnClickListener(x -> disconnect());
        sendBtn.setOnClickListener(x -> {
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

        // Group chat button listeners
        createGroupBtn.setOnClickListener(x -> createNewGroup());
        joinGroupBtn.setOnClickListener(x -> joinGroup());
        inviteDeviceBtn.setOnClickListener(x -> inviteDeviceToGroup());

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
        discovered.clear();
        deviceNamesList.clear();

        Disposable d = rxBleClient.scanBleDevices()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(scanResult -> {
                    RxBleDevice dev = scanResult.getBleDevice();
                    String mac = dev.getMacAddress();
                    String name = dev.getName() != null ? dev.getName() : mac;
                    if (!discovered.containsKey(mac)) {
                        discovered.put(mac, dev);
                        deviceNamesList.add(name + " (" + mac + ")");
                        deviceNames.put(mac, name);
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
                .setItems(deviceNamesList.toArray(new String[0]), (dlg, which) -> {
                    String pick = deviceNamesList.get(which);
                    String mac = pick.substring(pick.indexOf('(') + 1, pick.indexOf(')'));
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
                    incomingBuffer.write(bytes, 0, bytes.length);
                    String currentData = new String(incomingBuffer.toByteArray(), StandardCharsets.UTF_8);

                    if (isCompleteJson(currentData)) {
                        try {
                            JSONObject json = new JSONObject(currentData);
                            handleIncomingMessage(json);
                        } catch (JSONException e) {
                            addChatMessage("RX: " + currentData, false, "Unknown", "UNKNOWN",
                                    currentGroupId != null, currentGroupId);
                        }
                        incomingBuffer.reset();
                    }
                }, t -> Log.e(TAG, "notif", t));
        disposables.add(d);
    }

    private boolean isCompleteJson(String data) {
        int braceCount = 0;
        for (char c : data.toCharArray()) {
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;
        }
        return braceCount == 0 && data.trim().endsWith("}");
    }

    private void handleIncomingMessage(JSONObject json) {
        try {
            String type = json.optString("type", "");

            switch (type) {
                case "group_create":
                    handleGroupCreate(json);
                    break;
                case "group_join":
                    handleGroupJoin(json);
                    break;
                case "group_invite":
                    handleGroupInvite(json);
                    break;
                case "group_message":
                    handleGroupMessage(json);
                    break;
                default:
                    String senderMac = json.optString("sender_mac", "Unknown");
                    String senderName = deviceNames.getOrDefault(senderMac, "Unknown");
                    String message = json.optString("message", json.toString());

                    addChatMessage(message, false, senderName, senderMac,
                            currentGroupId != null, currentGroupId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling incoming message", e);
            addChatMessage("RX: " + json.toString(), false, "Unknown", "UNKNOWN",
                    currentGroupId != null, currentGroupId);
        }
    }

    private void createNewGroup() {
        if (connection == null) {
            Toast.makeText(requireContext(), "Not connected to any device", Toast.LENGTH_SHORT).show();
            return;
        }

        currentGroupId = UUID.randomUUID().toString().substring(0, 8);
        currentGroupName = "Group " + currentGroupId;

        groupMembers.clear();
        groupMembers.put("SELF", null);
        deviceNames.put("SELF", "Me");

        updateGroupUI();

        JSONObject groupInfo = new JSONObject();
        try {
            groupInfo.put("type", "group_create");
            groupInfo.put("group_id", currentGroupId);
            groupInfo.put("group_name", currentGroupName);
            groupInfo.put("creator", "SELF");
            sendJsonMessage(groupInfo);

            addChatMessage("Group created: " + currentGroupName, false, "System", "SYSTEM", false, "");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create group message", e);
        }
    }

    private void joinGroup() {
        EditText input = new EditText(requireContext());
        new AlertDialog.Builder(requireContext())
                .setTitle("Join Group")
                .setMessage("Enter Group ID:")
                .setView(input)
                .setPositiveButton("Join", (dialog, which) -> {
                    String groupId = input.getText().toString().trim();
                    if (!groupId.isEmpty()) {
                        joinExistingGroup(groupId);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void joinExistingGroup(String groupId) {
        currentGroupId = groupId;
        currentGroupName = "Group " + groupId;

        JSONObject joinMessage = new JSONObject();
        try {
            joinMessage.put("type", "group_join");
            joinMessage.put("group_id", currentGroupId);
            joinMessage.put("device_mac", "SELF");
            joinMessage.put("device_name", "Me");
            sendJsonMessage(joinMessage);

            addChatMessage("Joined group: " + currentGroupName, false, "System", "SYSTEM", false, "");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create join message", e);
        }

        updateGroupUI();
    }

    private void inviteDeviceToGroup() {
        if (currentGroupId == null) {
            Toast.makeText(requireContext(), "Create or join a group first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (deviceNamesList.isEmpty()) {
            Toast.makeText(requireContext(), "No devices found", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Invite Device to Group")
                .setItems(deviceNamesList.toArray(new String[0]), (dlg, which) -> {
                    String pick = deviceNamesList.get(which);
                    String mac = pick.substring(pick.indexOf('(') + 1, pick.indexOf(')'));

                    JSONObject inviteMessage = new JSONObject();
                    try {
                        inviteMessage.put("type", "group_invite");
                        inviteMessage.put("group_id", currentGroupId);
                        inviteMessage.put("group_name", currentGroupName);
                        sendJsonMessage(inviteMessage);

                        addChatMessage("Invitation sent to " + deviceNames.get(mac), false,
                                "System", "SYSTEM", false, "");
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to create invite message", e);
                    }
                }).show();
    }

    private void updateGroupUI() {
        if (currentGroupId != null) {
            groupInfoTextView.setText("Group: " + currentGroupName + " (" +
                    groupMembers.size() + " members)");
            groupInfoTextView.setVisibility(View.VISIBLE);
        } else {
            groupInfoTextView.setVisibility(View.GONE);
        }
    }

    private void handleGroupCreate(JSONObject json) {
        String groupId = json.optString("group_id");
        String groupName = json.optString("group_name", "Group " + groupId);
        String creator = json.optString("creator");

        currentGroupId = groupId;
        currentGroupName = groupName;
        groupMembers.put(creator, null);

        updateGroupUI();
        addChatMessage("Group created: " + groupName, false, "System", "SYSTEM", true, groupId);
    }

    private void handleGroupJoin(JSONObject json) {
        String deviceMac = json.optString("device_mac");
        String deviceName = json.optString("device_name", deviceMac);

        groupMembers.put(deviceMac, null);
        deviceNames.put(deviceMac, deviceName);

        updateGroupUI();
        addChatMessage(deviceName + " joined the group", false, "System", "SYSTEM", true, currentGroupId);
    }

    private void handleGroupInvite(JSONObject json) {
        String groupId = json.optString("group_id");
        String groupName = json.optString("group_name", "Group " + groupId);

        new AlertDialog.Builder(requireContext())
                .setTitle("Group Invitation")
                .setMessage("Join group: " + groupName + "?")
                .setPositiveButton("Join", (dialog, which) -> joinExistingGroup(groupId))
                .setNegativeButton("Decline", null)
                .show();
    }

    private void handleGroupMessage(JSONObject json) {
        String senderMac = json.optString("sender_mac");
        String senderName = deviceNames.getOrDefault(senderMac, "Unknown");
        String message = json.optString("message");

        addChatMessage(message, false, senderName, senderMac, true, currentGroupId);
    }

    private void sendJsonMessage(JSONObject json) {
        try {
            // Always include sender information
            json.put("sender_mac", "SELF");
            json.put("sender_name", "Me");

            // Include group info if in a group
            if (currentGroupId != null) {
                json.put("group_id", currentGroupId);
                json.put("type", "group_message");
            }

            splitAndSend(json.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Failed to add sender info to message", e);
            // Fallback: just send the original message
            splitAndSend(json.toString());
        }
    }

    private void disconnect() {
        disposables.clear();
        connection = null;
        currentGroupId = null;
        groupMembers.clear();
        updateGroupUI();
        statusTextView.setText("Disconnected");
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    })
    private void sendMessageWithLocation() {
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

                    try {
                        JSONObject json = new JSONObject();
                        if (!TextUtils.isEmpty(text)) {
                            json.put("message", text);
                        }

                        if (location != null) {
                            json.put("latitude", location.getLatitude());
                            json.put("longitude", location.getLongitude());
                            json.put("location_url", "https://www.openstreetmap.org/?mlat=" +
                                    location.getLatitude() + "&mlon=" + location.getLongitude());
                        } else {
                            json.put("location", "unavailable");
                        }

                        sendJsonMessage(json);
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON creation failed", e);
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
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Location failed", e);
                    Toast.makeText(requireContext(),
                            "Could not fetch location", Toast.LENGTH_SHORT).show();
                });
    }

    private void splitAndSend(String msg) {
        if (connection == null) {
            Toast.makeText(requireContext(), "Not connected to any device", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject json = new JSONObject(msg);
            String displayText = "TX: " + json.toString(2);
            addChatMessage(displayText, true, "Me", "SELF",
                    currentGroupId != null, currentGroupId);
        } catch (JSONException e) {
            addChatMessage("TX: " + msg, true, "Me", "SELF",
                    currentGroupId != null, currentGroupId);
        }
        messageEditText.setText("");

        Disposable d = connection
                .requestMtu(128)
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
                        bytes -> Log.d(TAG, "chunk sent, size=" + bytes.length),
                        t -> {
                            Log.e(TAG, "send failed", t);
                            updateLastChatMessageWithError(msg, t.getMessage());
                        }
                );
        disposables.add(d);
    }

    private void addChatMessage(String text, boolean isSent, String senderName,
                                String senderMac, boolean isGroupMessage, String groupId) {
        chatMessages.add(new ChatMessage(text, isSent, senderName, senderMac, isGroupMessage, groupId));
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
    }

    private void updateLastChatMessageWithError(String fullMessage, String errorMsg) {
        if (!chatMessages.isEmpty()) {
            ChatMessage lastMsg = chatMessages.get(chatMessages.size() - 1);
            try {
                JSONObject json = new JSONObject(fullMessage);
                lastMsg.setMessage("TX: " + json.toString(2) + " (couldn't send: " + errorMsg + ")");
            } catch (JSONException e) {
                lastMsg.setMessage("TX: " + fullMessage + " (couldn't send: " + errorMsg + ")");
            }
            chatAdapter.notifyItemChanged(chatMessages.size() - 1);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }
}