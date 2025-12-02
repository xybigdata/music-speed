package com.speedvolume;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Button btnToggle, btnConfig;
    private TextView tvStatus, tvSpeed, tvVolume;
    private ProgressBar progressSpeed, progressVolume;
    private Spinner spinnerProfile;
    private boolean isServiceRunning = false;
    private SpeedVolumeConfig config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToggle = findViewById(R.id.btnToggle);
        btnConfig = findViewById(R.id.btnConfig);
        tvStatus = findViewById(R.id.tvStatus);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvVolume = findViewById(R.id.tvVolume);
        progressSpeed = findViewById(R.id.progressSpeed);
        progressVolume = findViewById(R.id.progressVolume);
        spinnerProfile = findViewById(R.id.spinnerProfile);

        config = new SpeedVolumeConfig();
        config.load(this);
        setupSpinner();

        btnToggle.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_up));
            toggleService();
        });
        
        btnConfig.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_up));
            startActivity(new Intent(this, ConfigActivity.class));
        });

        spinnerProfile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                config.setActiveProfileIndex(position);
                config.save(MainActivity.this);
                sendBroadcast(new Intent("com.speedvolume.CONFIG_CHANGED"));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupSpinner() {
        List<String> names = new ArrayList<>();
        for (SpeedVolumeConfig.Profile p : config.getProfiles()) {
            names.add(p.name);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProfile.setAdapter(adapter);
        spinnerProfile.setSelection(config.getActiveProfileIndex());
    }

    private void toggleService() {
        Intent intent = new Intent(this, SpeedVolumeService.class);
        if (isServiceRunning) {
            stopService(intent);
            isServiceRunning = false;
            btnToggle.setText("启动服务");
            tvStatus.setText("状态: 已停止");
            tvStatus.setTextColor(0xFF666666);
        } else {
            startForegroundService(intent);
            isServiceRunning = true;
            btnToggle.setText("停止服务");
            tvStatus.setText("状态: 运行中");
            tvStatus.setTextColor(0xFF4CAF50);
            tvStatus.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        config.load(this);
        setupSpinner();
        
        SpeedVolumeService.setUpdateListener((speedKmh, volume) -> runOnUiThread(() -> {
            tvSpeed.setText(String.format("速度: %.1f km/h", speedKmh));
            tvVolume.setText(String.format("音量: %d%%", volume));
            
            animateProgress(progressSpeed, (int) Math.min(100, speedKmh));
            animateProgress(progressVolume, volume);
        }));
    }

    private void animateProgress(ProgressBar progressBar, int targetProgress) {
        ObjectAnimator animator = ObjectAnimator.ofInt(progressBar, "progress", progressBar.getProgress(), targetProgress);
        animator.setDuration(300);
        animator.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        SpeedVolumeService.setUpdateListener(null);
    }
}
