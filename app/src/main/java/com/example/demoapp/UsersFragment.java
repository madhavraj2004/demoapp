package com.example.demoapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class UsersFragment extends Fragment {

    private RecyclerView usersRecyclerView;
    private UsersAdapter adapter;
    private final List<String> users = new ArrayList<>();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // inflate using the inflater, not adapter!
        View root = inflater.inflate(R.layout.fragment_users, container, false);

        usersRecyclerView = root.findViewById(R.id.usersRecyclerView);
        adapter = new UsersAdapter(users, name -> {
            // build the args bundle
            Bundle args = new Bundle();
            args.putString("userName", name);
            // make sure your nav-graph action ID matches this:
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_usersFragment_to_chatFragment, args);
        });

        usersRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        usersRecyclerView.setAdapter(adapter);

        root.findViewById(R.id.addUserFab)
                .setOnClickListener(v -> showAddUserDialog());

        return root;
    }

    private void showAddUserDialog() {
        final EditText input = new EditText(requireContext());
        new AlertDialog.Builder(requireContext())
                .setTitle("Add new user")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        users.add(name);
                        adapter.notifyItemInserted(users.size() - 1);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
