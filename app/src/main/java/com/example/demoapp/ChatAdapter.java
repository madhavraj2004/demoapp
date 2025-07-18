package com.example.demoapp;

import android.os.Bundle;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private final List<ChatMessage> chatList;

    public ChatAdapter(List<ChatMessage> chatList) {
        this.chatList = chatList;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                viewType == 0 ? R.layout.item_message_sent : R.layout.item_message_received, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage chat = chatList.get(position);
        holder.messageText.setText(chat.getMessage());

        // Automatically link URLs
        Linkify.addLinks(holder.messageText, Linkify.WEB_URLS);

        // Navigate to MapFragment on OSM coordinate click
        // inside onBindViewHolder in ChatAdapter.java:
        holder.messageText.setOnClickListener(v -> {
            String text = chat.getMessage();
            if (text.contains("https://www.openstreetmap.org/?mlat=")) {
                double lat = Double.parseDouble(text.split("mlat=")[1].split("&")[0]);
                double lon = Double.parseDouble(text.split("mlon=")[1].split("\\s")[0]);
                Bundle args = new Bundle();
                args.putDouble("lat", lat);
                args.putDouble("lon", lon);
                Navigation.findNavController(v)
                        .navigate(R.id.action_chatFragment_to_mapFragment, args);
            }
        });

    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return chatList.get(position).isSentByUser() ? 0 : 1;
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;

        ChatViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textMessage);
        }
    }
}
