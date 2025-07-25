package com.example.demoapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.VH> {

    public interface OnUserClick {
        void onClick(String name);
    }

    private final List<String> users;
    private final OnUserClick callback;

    public UsersAdapter(List<String> users, OnUserClick callback) {
        this.users = users;
        this.callback = callback;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // inflate the row layout
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String name = users.get(position);
        holder.name.setText(name);
        holder.itemView.setOnClickListener(view -> callback.onClick(name));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name;

        public VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.userNameText);
        }
    }
}
