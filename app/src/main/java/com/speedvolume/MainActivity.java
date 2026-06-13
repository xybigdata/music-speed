package com.speedvolume;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Button btnToggle, btnConfig, btnStats, btnBluetooth;
    private TextView tvStatus, tvVolume;
    private SpeedDashboardView speedDashboard;
    private ProgressBar progressVolume;
    private Spinner spinnerProfile;
    private boolean isServiceRunning = false;
    private SpeedVolumeConfig config;
    private PermissionHelper permissionHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化权限助手
        permissionHelper = new PermissionHelper();

        btnToggle = findViewById(R.id.btnToggle);
        btnConfig = findViewById(R.id.btnConfig);
        btnStats = findViewById(R.id.btnStats);
        btnBluetooth = findViewById(R.id.btnBluetooth);
        tvStatus = findViewById(R.id.tvStatus);
        tvVolume = findViewById(R.id.tvVolume);
        speedDashboard = findViewById(R.id.speedDashboard);
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
        
        btnStats.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_up));
            startActivity(new Intent(this, StatsActivity.class));
        });
        
        btnBluetooth.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_up));
            startActivity(new Intent(this, BluetoothDeviceActivity.class));
        });

        spinnerProfile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 1 && !config.isProfile2Unlocked(MainActivity.this)) {
                    showUnlockDialog(1, "我的生日是多少（例如：0101）？", "0628");
                    spinnerProfile.setSelection(config.getActiveProfileIndex());
                } else if (position == 2 && !config.isProfile3Unlocked(MainActivity.this)) {
                    showUnlockDialog(2, "我喜欢什么（两个字）？", "泥鸭");
                    spinnerProfile.setSelection(config.getActiveProfileIndex());
                } else {
                    config.setActiveProfileIndex(position);
                    config.save(MainActivity.this);
                    sendBroadcast(new Intent("com.speedvolume.CONFIG_CHANGED"));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void showUnlockDialog(int profileIndex, String question, String answer) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔒 解锁方案");
        builder.setMessage(question);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("请输入答案");
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String userAnswer = input.getText().toString().trim();
            if (userAnswer.equals(answer)) {
                if (profileIndex == 1) {
                    config.unlockProfile2(this);
                } else if (profileIndex == 2) {
                    config.unlockProfile3(this);
                }
                config.setActiveProfileIndex(profileIndex);
                config.save(this);
                setupSpinner();
                spinnerProfile.setSelection(profileIndex);
                sendBroadcast(new Intent("com.speedvolume.CONFIG_CHANGED"));
                Toast.makeText(this, "✅ 解锁成功！", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "❌ 答案错误", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void setupSpinner() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < config.getProfiles().size(); i++) {
            String name = config.getProfiles().get(i).name;
            if (i == 1) {
                name = "狂飙模式";
                if (!config.isProfile2Unlocked(this)) name += " 🔒";
            } else if (i == 2) {
                name = "叫卖模式";
                if (!config.isProfile3Unlocked(this)) name += " 🔒";
            }
            names.add(name);
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
            // 启动服务前检查权限
            checkAndRequestPermissions();
        }
    }

    /**
     * 检查并请求权限
     */
    private void checkAndRequestPermissions() {
        if (permissionHelper.hasAllPermissions(this)) {
            // 权限已授予，启动服务
            startSpeedVolumeService();
        } else if (permissionHelper.shouldShowRationale(this)) {
            // 用户之前拒绝过权限，显示说明对话框
            showPermissionRationaleDialog();
        } else {
            // 直接请求权限
            permissionHelper.requestPermissions(this);
        }
    }

    /**
     * 显示权限说明对话框
     */
    private void showPermissionRationaleDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_permission_rationale, null);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create();
        
        dialog.show();
        
        // 授予权限按钮
        Button btnGrantPermission = dialogView.findViewById(R.id.btnGrantPermission);
        btnGrantPermission.setOnClickListener(v -> {
            dialog.dismiss();
            permissionHelper.requestPermissions(MainActivity.this);
        });
        
        // 前往设置按钮
        Button btnGoToSettings = dialogView.findViewById(R.id.btnGoToSettings);
        btnGoToSettings.setOnClickListener(v -> {
            dialog.dismiss();
            permissionHelper.openSettings(MainActivity.this);
        });
    }

    /**
     * 显示权限被拒绝对话框
     */
    private void showPermissionDeniedDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_permission_denied, null);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create();
        
        dialog.show();
        
        // 前往设置按钮
        Button btnOpenSettings = dialogView.findViewById(R.id.btnOpenSettings);
        btnOpenSettings.setOnClickListener(v -> {
            dialog.dismiss();
            permissionHelper.openSettings(MainActivity.this);
        });
        
        // 稍后再说按钮
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "⚠️ 部分功能可能无法正常使用", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 启动速度音量服务
     */
    private void startSpeedVolumeService() {
        Intent intent = new Intent(this, SpeedVolumeService.class);
        startForegroundService(intent);
        isServiceRunning = true;
        btnToggle.setText("停止服务");
        tvStatus.setText("状态: 运行中");
        tvStatus.setTextColor(0xFF4CAF50);
        tvStatus.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in));
    }

    /**
     * 处理权限请求结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == permissionHelper.getRequestCode()) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                // 检查是否需要请求后台位置权限（Android 10+）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // 暂时不强制请求后台位置权限，让用户自行选择
                    // permissionHelper.requestBackgroundLocationPermission(this);
                }
                startSpeedVolumeService();
            } else {
                // 用户拒绝了部分权限
                showPermissionDeniedDialog();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        config.load(this);
        setupSpinner();
        
        SpeedVolumeService.setUpdateListener((speedKmh, volume) -> runOnUiThread(() -> {
            // 更新速度仪表盘
            speedDashboard.setSpeed(speedKmh);
            
            // 更新音量显示
            tvVolume.setText(String.format("音量: %d%%", volume));
            
            // 更新音量进度条
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
