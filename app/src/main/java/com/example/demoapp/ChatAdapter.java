package com.example.demoapp;

import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private final List<ChatMessage> chatList;
    // Regex to match your OSM link
    private static final Pattern OSM_PATTERN = Pattern.compile(
            "(https://www\\.openstreetmap\\.org/\\?mlat=([-+]?\\d*\\.\\d+)&mlon=([-+]?\\d*\\.\\d+))"
    );

    public ChatAdapter(List<ChatMessage> chatList) {
        this.chatList = chatList;
    }

    @NonNull @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = (viewType == 0)
                ? R.layout.item_message_sent
                : R.layout.item_message_received;
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layout, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage chat = chatList.get(position);
        String text = chat.getMessage();

        // Create a SpannableString from the full text
        SpannableString spannable = new SpannableString(text);

        // Find an OSM link match
        Matcher m = OSM_PATTERN.matcher(text);
        if (m.find()) {
            final String url = m.group(1);
            final double lat = Double.parseDouble(m.group(2));
            final double lon = Double.parseDouble(m.group(3));

            // apply clickable span over the exact range
            spannable.setSpan(new ClickableSpan() {
                @Override public void onClick(@NonNull @NotNull View widget) {
                    Bundle args = new Bundle();
                    args.putFloat("lat", (float) lat);
                    args.putFloat("lon", (float) lon);
                    Navigation.findNavController(widget)
                            .navigate(R.id.action_chatFragment_to_mapFragment, args);
                }
            }, m.start(1), m.end(1), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        holder.messageText.setText(spannable);
        // Important! Enables ClickableSpan
        holder.messageText.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override public int getItemCount()    { return chatList.size(); }
    @Override public int getItemViewType(int pos) {
        return chatList.get(pos).isSentByUser() ? 0 : 1;
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        ChatViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textMessage);
        }
    }
}
