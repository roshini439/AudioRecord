package com.v2v.audiorecorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.v2v.audiorecorder.R;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private List<Recording> recordings = new ArrayList<>();
    private RecordingsAdapter adapter;
    private boolean isRecording = false;
    private String currentFilePath = "";
    private long startTime = 0; // For duration calculation

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        MaterialButton btnRecord = findViewById(R.id.btnRecord);
        MaterialButton btnStop = findViewById(R.id.btnStop);
        TextView tvStatus = findViewById(R.id.tvStatus);


        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecordingsAdapter(recordings, recording -> playRecording(recording));
        recyclerView.setAdapter(adapter);


        btnRecord.setOnClickListener(v -> startRecording());
        btnStop.setOnClickListener(v -> stopRecording());
    }

    private void startRecording() {
        // Check permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }

        try {
            // Setup media recorder
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

            // Create file path
            currentFilePath = getExternalCacheDir().getAbsolutePath()
                    + "/recording_" + System.currentTimeMillis() + ".mp4";
            mediaRecorder.setOutputFile(currentFilePath);

            // Start recording
            mediaRecorder.prepare();
            mediaRecorder.start();

            // Save start time for duration calculation
            startTime = System.currentTimeMillis();

            // Update state
            isRecording = true;
            updateUI(true);
        } catch (IOException e) {
            Log.e("Recording", "Start failed", e);
            Toast.makeText(this, "Recording failed to start", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (isRecording && mediaRecorder != null) {
            try {
                // Calculate duration
                long durationMillis = System.currentTimeMillis() - startTime;
                String duration = formatDuration(durationMillis);

                // Stop recording
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                isRecording = false;

                // Add to recordings list
                Recording recording = new Recording(
                        "Recording_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()),
                        currentFilePath,
                        duration,
                        System.currentTimeMillis()
                );
                recordings.add(0, recording);
                adapter.notifyItemInserted(0);

                // Update UI
                updateUI(false);
            } catch (Exception e) {
                Log.e("Recording", "Stop failed", e);
                Toast.makeText(this, "Recording failed to stop", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String formatDuration(long millis) {
        int seconds = (int) (millis / 1000);
        int minutes = seconds / 60;
        seconds %= 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private void updateUI(boolean recordingInProgress) {
        TextView tvStatus = findViewById(R.id.tvStatus);
        MaterialButton btnRecord = findViewById(R.id.btnRecord);
        MaterialButton btnStop = findViewById(R.id.btnStop);

        if (recordingInProgress) {
            tvStatus.setText("Recording...");
            btnRecord.setEnabled(false);
            btnStop.setEnabled(true);
        } else {
            tvStatus.setText("Ready to record");
            btnRecord.setEnabled(true);
            btnStop.setEnabled(false);
        }
    }

    private void playRecording(Recording recording) {
        try {
            // Release any previous player
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }

            // Create and start new player
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(recording.getPath());
            mediaPlayer.prepare();
            mediaPlayer.start();

            Toast.makeText(this, "Playing: " + recording.getName(), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Playback failed", Toast.LENGTH_SHORT).show();
            Log.e("Playback", "Error playing recording", e);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Release media resources when activity stops
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Recording data model
    public static class Recording {
        private final String name;
        private final String path;
        private final String duration;
        private final long timestamp;

        public Recording(String name, String path, String duration, long timestamp) {
            this.name = name;
            this.path = path;
            this.duration = duration;
            this.timestamp = timestamp;
        }

        public String getName() { return name; }
        public String getPath() { return path; }
        public String getDuration() { return duration; }
        public long getTimestamp() { return timestamp; }
    }

    // RecyclerView Adapter
    public static class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.ViewHolder> {
        private final List<Recording> recordings;
        private final OnItemClickListener listener;

        public interface OnItemClickListener {
            void onItemClick(Recording recording);
        }

        public RecordingsAdapter(List<Recording> recordings, OnItemClickListener listener) {
            this.recordings = recordings;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_recording, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Recording recording = recordings.get(position);
            holder.tvName.setText(recording.getName());
            holder.tvDuration.setText(recording.getDuration());

            // Format timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            holder.tvDate.setText(sdf.format(new Date(recording.getTimestamp())));

            // Play button click
            holder.btnPlay.setOnClickListener(v -> listener.onItemClick(recording));
        }

        @Override
        public int getItemCount() {
            return recordings.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            public TextView tvName, tvDuration, tvDate;
            public ImageButton btnPlay;

            public ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvDuration = itemView.findViewById(R.id.tvDuration);
                tvDate = itemView.findViewById(R.id.tvDate);
                btnPlay = itemView.findViewById(R.id.btnPlay);
            }
        }
    }
}