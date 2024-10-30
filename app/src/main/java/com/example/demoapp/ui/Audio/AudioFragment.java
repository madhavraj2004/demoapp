package com.example.demoapp.ui.Audio;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.demoapp.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class AudioFragment extends Fragment {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private Button buttonTapToSpeak;
    private EditText editTextSpeechToText;
    private ImageView microphoneIcon;
    private boolean isRecording = false;
    private AudioRecord audioRecord;
    private File audioFile;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_audio, container, false);

        buttonTapToSpeak = view.findViewById(R.id.button_tap_to_speak);
        editTextSpeechToText = view.findViewById(R.id.edittext_speech_to_text);
        microphoneIcon = view.findViewById(R.id.microphone_icon);

        // Request permissions
        ActivityCompat.requestPermissions(getActivity(), permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        buttonTapToSpeak.setOnClickListener(v -> {
            if (permissionToRecordAccepted) {
                if (isRecording) {
                    stopRecording();
                } else {
                    startRecording();
                }
            } else {
                Toast.makeText(getContext(), "Permission to record not granted", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    private void startRecording() {
        isRecording = true;
        buttonTapToSpeak.setText("Stop Recording");
        microphoneIcon.setImageResource(R.drawable.ic_microphone_active); // Change the icon

        int bufferSize = AudioRecord.getMinBufferSize(
                MediaRecorder.AudioSource.MIC,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);

        audioFile = new File(Environment.getExternalStorageDirectory(), "recorded_audio.wav");

        audioRecord.startRecording();
        byte[] audioData = new byte[bufferSize];

        new Thread(() -> {
            try (FileOutputStream outputStream = new FileOutputStream(audioFile)) {
                while (isRecording) {
                    int read = audioRecord.read(audioData, 0, audioData.length);
                    if (read > 0) {
                        outputStream.write(audioData, 0, read);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void stopRecording() {
        isRecording = false;
        buttonTapToSpeak.setText("Tap to Speak");
        microphoneIcon.setImageResource(R.drawable.ic_microphone); // Reset the icon

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        Toast.makeText(getContext(), "Audio recorded: " + audioFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
    }
}
