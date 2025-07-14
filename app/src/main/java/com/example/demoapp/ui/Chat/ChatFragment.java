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
import android.widget.EditText;
import android.widget.ImageButton;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demoapp.ChatAdapter;
import com.example.demoapp.ChatMessage;
import com.example.demoapp.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;

public class ChatFragment extends Fragment {

    private ChatAdapter chatAdapter;
    private final ArrayList<ChatMessage> chatMessages = new ArrayList<>();
    private EditText messageEditText;
    private RecyclerView recyclerView;
    private FusedLocationProviderClient fusedLocationClient;

    public ChatFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        recyclerView = view.findViewById(R.id.chatRecyclerView);
        messageEditText = view.findViewById(R.id.messageEditText);
        ImageButton sendButton = view.findViewById(R.id.sendButton);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        chatAdapter = new ChatAdapter(chatMessages);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(chatAdapter);

        sendButton.setOnClickListener(v -> sendMessageWithLocation());

        return view;
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

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    String fullMessage = message;
                    if (location != null) {
                        double lat = location.getLatitude();
                        double lon = location.getLongitude();

                        String osmLink = "https://www.openstreetmap.org/?mlat=" + lat + "&mlon=" + lon;
                        fullMessage += "\nLocation: " + osmLink;
                    } else {
                        fullMessage += "\n(Location not available)";
                    }

                    ChatMessage chatMessage = new ChatMessage(fullMessage, true);
                    chatMessages.add(chatMessage);
                    chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                    recyclerView.scrollToPosition(chatMessages.size() - 1);
                    messageEditText.setText("");

                    // Simulate receiver response
                    receiveMessage("Received: " + fullMessage);

                })
                .addOnFailureListener(e -> Log.e("LocationError", "Failed to get location", e));
    }

    private void receiveMessage(String msg) {
        recyclerView.postDelayed(() -> {
            chatMessages.add(new ChatMessage(msg, false));
            chatAdapter.notifyItemInserted(chatMessages.size() - 1);
            recyclerView.scrollToPosition(chatMessages.size() - 1);
        }, 1000);
    }
}
