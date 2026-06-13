package com.speedvolume;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置编辑界面
 * 支持输入验证、实时预览、滑块控制、配置模板
 */
public class ConfigActivity extends AppCompatActivity {
    private static final String TAG = "ConfigActivity";
    
    // 速度范围常量
    private static final float MIN_SPEED = 0f;
    private static final float MAX_SPEED = 150f;
    private static final int MIN_VOLUME = 0;
    private static final int MAX_VOLUME = 100;
    
    // UI 组件
    private Spinner spinnerProfile;
    private EditText etProfileName;
    private LinearLayout rangesContainer;
    private FunctionGraphView graphView;
    
    // 音量限制组件
    private SeekBar seekMinVolume;
    private SeekBar seekMaxVolume;
    private TextView tvMinVolume;
    private TextView tvMaxVolume;
    private CheckBox cbSmoothTransition;
    
    // 振动反馈组件
    private CheckBox cbVibrationEnabled;
    private CheckBox cbVibrationOnProfileSwitch;
    private CheckBox cbVibrationOnThreshold;
    private SeekBar seekVibrationThreshold;
    private TextView tvVibrationThreshold;
    
    // 配置数据
    private SpeedVolumeConfig config;
    private int currentProfileIndex = 0;
    
    // 配置模板定义
    private static final ConfigTemplate[] CONFIG_TEMPLATES = {
        new ConfigTemplate(
            "城市通勤",
            "适合城市骑行，低速安静，高速渐强",
            new SpeedVolumeConfig.SpeedRange(0, 15, 15),
            new SpeedVolumeConfig.SpeedRange(15, 25, 30),
            new SpeedVolumeConfig.SpeedRange(25, 35, 60),
            new SpeedVolumeConfig.SpeedRange(35, 999, 100)
        ),
        new ConfigTemplate(
            "高速公路",
            "适合高速骑行，音量随速度快速增长",
            new SpeedVolumeConfig.SpeedRange(0, 30, 20),
            new SpeedVolumeConfig.SpeedRange(30, 60, 50),
            new SpeedVolumeConfig.SpeedRange(60, 90, 80),
            new SpeedVolumeConfig.SpeedRange(90, 999, 100)
        ),
        new ConfigTemplate(
            "休闲骑行",
            "适合休闲骑行，音量变化平缓",
            new SpeedVolumeConfig.SpeedRange(0, 20, 25),
            new SpeedVolumeConfig.SpeedRange(20, 40, 40),
            new SpeedVolumeConfig.SpeedRange(40, 999, 55)
        ),
        new ConfigTemplate(
            "运动模式",
            "适合运动骑行，音量范围大",
            new SpeedVolumeConfig.SpeedRange(0, 10, 10),
            new SpeedVolumeConfig.SpeedRange(10, 20, 20),
            new SpeedVolumeConfig.SpeedRange(20, 30, 40),
            new SpeedVolumeConfig.SpeedRange(30, 40, 60),
            new SpeedVolumeConfig.SpeedRange(40, 50, 80),
            new SpeedVolumeConfig.SpeedRange(50, 999, 100)
        ),
        new ConfigTemplate(
            "安静模式",
            "适合图书馆等安静场所，音量较低",
            new SpeedVolumeConfig.SpeedRange(0, 999, 20)
        ),
        new ConfigTemplate(
            "步行模式",
            "适合步行使用，低速较低音量",
            new SpeedVolumeConfig.SpeedRange(0, 5, 15),
            new SpeedVolumeConfig.SpeedRange(5, 10, 30),
            new SpeedVolumeConfig.SpeedRange(10, 15, 50),
            new SpeedVolumeConfig.SpeedRange(15, 999, 70)
        )
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        initViews();
        initConfig();
        setupSpinner();
        loadProfile(currentProfileIndex);
        loadVolumeLimits();
        loadVibrationSettings();
        updateGraphPreview();
        setupListeners();
    }

    private void initViews() {
        spinnerProfile = findViewById(R.id.spinnerProfile);
        etProfileName = findViewById(R.id.etProfileName);
        rangesContainer = findViewById(R.id.rangesContainer);
        graphView = findViewById(R.id.graphView);
        
        // 音量限制
        seekMinVolume = findViewById(R.id.seekMinVolume);
        seekMaxVolume = findViewById(R.id.seekMaxVolume);
        tvMinVolume = findViewById(R.id.tvMinVolume);
        tvMaxVolume = findViewById(R.id.tvMaxVolume);
        cbSmoothTransition = findViewById(R.id.cbSmoothTransition);
        
        // 振动反馈
        cbVibrationEnabled = findViewById(R.id.cbVibrationEnabled);
        cbVibrationOnProfileSwitch = findViewById(R.id.cbVibrationOnProfileSwitch);
        cbVibrationOnThreshold = findViewById(R.id.cbVibrationOnThreshold);
        seekVibrationThreshold = findViewById(R.id.seekVibrationThreshold);
        tvVibrationThreshold = findViewById(R.id.tvVibrationThreshold);
        
        // 按钮
        findViewById(R.id.btnRename).setOnClickListener(v -> renameProfile());
        findViewById(R.id.btnAdd).setOnClickListener(v -> addRange());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveConfig());
        findViewById(R.id.btnReset).setOnClickListener(v -> resetProfile());
        findViewById(R.id.btnTemplates).setOnClickListener(v -> showTemplatesDialog());
    }

    private void initConfig() {
        config = new SpeedVolumeConfig();
        config.load(this);
        currentProfileIndex = config.getActiveProfileIndex();
    }

    private void setupSpinner() {
        List<String> names = new ArrayList<>();
        List<SpeedVolumeConfig.Profile> profiles = config.getProfiles();
        for (int i = 0; i < profiles.size(); i++) {
            String name = profiles.get(i).name;
            if (i == 1) name = "狂飙模式";
            if (i == 2) name = "叫卖模式";
            names.add(name);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProfile.setAdapter(adapter);
        spinnerProfile.setSelection(currentProfileIndex);
        
        spinnerProfile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 1) {
                    startActivity(new Intent(ConfigActivity.this, Profile2ConfigActivity.class));
                    finish();
                    return;
                }
                currentProfileIndex = position;
                loadProfile(position);
                updateGraphPreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupListeners() {
        // 音量限制滑块监听
        seekMinVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvMinVolume.setText(progress + "%");
                validateVolumeLimits();
                updateGraphPreview();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekMaxVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvMaxVolume.setText(progress + "%");
                validateVolumeLimits();
                updateGraphPreview();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 振动设置监听
        cbVibrationEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateVibrationSettingsEnabled(isChecked);
        });

        seekVibrationThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvVibrationThreshold.setText(progress + "km/h");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void loadProfile(int index) {
        SpeedVolumeConfig.Profile profile = config.getProfiles().get(index);
        etProfileName.setText(profile.name);
        etProfileName.setEnabled(index != 2);
        rangesContainer.removeAllViews();
        
        if (index != 2) {
            for (SpeedVolumeConfig.SpeedRange range : profile.ranges) {
                addRangeView(range);
            }
        } else {
            TextView tv = new TextView(this);
            tv.setText("叫卖模式：全速100%音量");
            tv.setTextSize(16);
            tv.setPadding(16, 16, 16, 16);
            rangesContainer.addView(tv);
        }
    }

    private void addRangeView(SpeedVolumeConfig.SpeedRange range) {
        View view = getLayoutInflater().inflate(R.layout.item_range_slider, rangesContainer, false);
        
        SeekBar seekMinSpeed = view.findViewById(R.id.seekMinSpeed);
        SeekBar seekMaxSpeed = view.findViewById(R.id.seekMaxSpeed);
        SeekBar seekVolume = view.findViewById(R.id.seekVolume);
        TextView tvMinSpeed = view.findViewById(R.id.tvMinSpeed);
        TextView tvMaxSpeed = view.findViewById(R.id.tvMaxSpeed);
        TextView tvVolume = view.findViewById(R.id.tvVolume);
        TextView tvSpeedRange = view.findViewById(R.id.tvSpeedRange);
        Button btnDelete = view.findViewById(R.id.btnDelete);

        // 设置初始值
        seekMinSpeed.setProgress((int) range.minSpeed);
        seekMaxSpeed.setProgress((int) range.maxSpeed);
        seekVolume.setProgress(range.volume);
        tvMinSpeed.setText(String.valueOf((int) range.minSpeed));
        tvMaxSpeed.setText(String.valueOf((int) range.maxSpeed));
        tvVolume.setText(range.volume + "%");
        updateSpeedRangeText(tvSpeedRange, (int) range.minSpeed, (int) range.maxSpeed);

        // 监听器
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int minSpeed = seekMinSpeed.getProgress();
                int maxSpeed = seekMaxSpeed.getProgress();
                int volume = seekVolume.getProgress();
                
                // 验证速度范围 - 实时修正
                if (seekBar == seekMinSpeed && minSpeed > maxSpeed) {
                    seekMinSpeed.setProgress(maxSpeed);
                    minSpeed = maxSpeed;
                }
                if (seekBar == seekMaxSpeed && maxSpeed < minSpeed) {
                    seekMaxSpeed.setProgress(minSpeed);
                    maxSpeed = minSpeed;
                }
                
                // 更新显示
                tvMinSpeed.setText(String.valueOf(minSpeed));
                tvMaxSpeed.setText(String.valueOf(maxSpeed));
                tvVolume.setText(volume + "%");
                updateSpeedRangeText(tvSpeedRange, minSpeed, maxSpeed);
                
                // 实时更新预览曲线
                updateGraphPreview();
                
                // 实时验证并显示提示
                validateAndUpdateUI(minSpeed, maxSpeed, volume, tvSpeedRange);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        seekMinSpeed.setOnSeekBarChangeListener(listener);
        seekMaxSpeed.setOnSeekBarChangeListener(listener);
        seekVolume.setOnSeekBarChangeListener(listener);

        btnDelete.setOnClickListener(v -> {
            // 删除前确认（可选）
            rangesContainer.removeView(view);
            updateGraphPreview();
        });

        rangesContainer.addView(view);
    }
    
    /**
     * 实时验证并更新UI提示
     */
    private void validateAndUpdateUI(int minSpeed, int maxSpeed, int volume, TextView tvSpeedRange) {
        // 检查范围是否有效
        if (minSpeed > maxSpeed) {
            tvSpeedRange.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else {
            tvSpeedRange.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        }
    }

    private void updateSpeedRangeText(TextView tv, int min, int max) {
        if (max >= 150) {
            tv.setText(min + " km/h 以上");
        } else {
            tv.setText(min + " - " + max + " km/h");
        }
    }

    private void addRange() {
        // 找到最后一个范围的最大速度作为新范围的最小速度
        int minSpeed = 0;
        if (rangesContainer.getChildCount() > 0) {
            View lastView = rangesContainer.getChildAt(rangesContainer.getChildCount() - 1);
            SeekBar seekMaxSpeed = lastView.findViewById(R.id.seekMaxSpeed);
            minSpeed = Math.min(seekMaxSpeed.getProgress(), 140);
        }
        
        addRangeView(new SpeedVolumeConfig.SpeedRange(minSpeed, Math.min(minSpeed + 10, 150), 30));
        updateGraphPreview();
    }

    private void renameProfile() {
        String newName = etProfileName.getText().toString().trim();
        if (newName.isEmpty()) {
            showInputErrorDialog("方案名称不能为空");
            return;
        }
        config.getProfiles().get(currentProfileIndex).name = newName;
        setupSpinner();
        Toast.makeText(this, "已重命名", Toast.LENGTH_SHORT).show();
    }

    /**
     * 验证输入并保存配置
     */
    private void saveConfig() {
        // 收集并验证速度范围
        List<SpeedVolumeConfig.SpeedRange> ranges = new ArrayList<>();
        
        // 验证：至少需要一个范围
        if (rangesContainer.getChildCount() == 0) {
            showInputErrorDialog("请至少添加一个速度范围");
            return;
        }
        
        for (int i = 0; i < rangesContainer.getChildCount(); i++) {
            View view = rangesContainer.getChildAt(i);
            SeekBar seekMinSpeed = view.findViewById(R.id.seekMinSpeed);
            SeekBar seekMaxSpeed = view.findViewById(R.id.seekMaxSpeed);
            SeekBar seekVolume = view.findViewById(R.id.seekVolume);

            int min = seekMinSpeed.getProgress();
            int max = seekMaxSpeed.getProgress();
            int vol = seekVolume.getProgress();

            // 验证速度范围
            if (min > max) {
                showInputErrorDialog("第 " + (i + 1) + " 个范围：最小速度不能大于最大速度");
                return;
            }
            
            // 验证：范围不能为空（最小和最大都是0且不是第一个范围）
            if (min == 0 && max == 0 && i > 0) {
                showInputErrorDialog("第 " + (i + 1) + " 个范围：速度范围不能为空");
                return;
            }

            // 验证音量范围（SeekBar 已限制在 0-100，但保留验证以增强安全性）
            if (vol < MIN_VOLUME || vol > MAX_VOLUME) {
                showInputErrorDialog("第 " + (i + 1) + " 个范围：音量必须在 0-100% 之间");
                return;
            }

            ranges.add(new SpeedVolumeConfig.SpeedRange(min, max == 150 ? 999 : max, vol));
        }
        
        // 验证：检查范围是否有重叠
        if (validateRangesOverlap(ranges)) {
            new AlertDialog.Builder(this)
                .setTitle("警告")
                .setMessage("速度范围存在重叠，后定义的范围会覆盖先定义的范围。是否继续保存？")
                .setPositiveButton("继续保存", (dialog, which) -> continueSaveValidation(ranges))
                .setNegativeButton("返回修改", null)
                .show();
            return;
        }

        continueSaveValidation(ranges);
    }
    
    /**
     * 继续保存验证流程
     */
    private void continueSaveValidation(List<SpeedVolumeConfig.SpeedRange> ranges) {
        // 验证范围是否连续（可选警告）
        if (!validateRangesContinuity(ranges)) {
            new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("速度范围不连续，可能存在未覆盖的速度区间。是否继续保存？")
                .setPositiveButton("继续保存", (dialog, which) -> doSaveConfig(ranges))
                .setNegativeButton("返回修改", null)
                .show();
        } else {
            doSaveConfig(ranges);
        }
    }
    
    /**
     * 验证范围是否存在重叠
     */
    private boolean validateRangesOverlap(List<SpeedVolumeConfig.SpeedRange> ranges) {
        if (ranges.size() < 2) return false;
        
        for (int i = 0; i < ranges.size() - 1; i++) {
            for (int j = i + 1; j < ranges.size(); j++) {
                SpeedVolumeConfig.SpeedRange r1 = ranges.get(i);
                SpeedVolumeConfig.SpeedRange r2 = ranges.get(j);
                
                // 检查是否重叠
                if (r1.minSpeed < r2.maxSpeed && r1.maxSpeed > r2.minSpeed) {
                    return true;
                }
            }
        }
        return false;
    }

    private void doSaveConfig(List<SpeedVolumeConfig.SpeedRange> ranges) {
        config.getProfiles().get(currentProfileIndex).ranges = ranges;
        config.setActiveProfileIndex(currentProfileIndex);
        config.save(this);
        
        // 保存音量限制设置
        saveVolumeLimits();
        
        // 保存振动设置
        saveVibrationSettings();
        
        sendBroadcast(new Intent("com.speedvolume.CONFIG_CHANGED"));
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * 验证范围是否连续
     */
    private boolean validateRangesContinuity(List<SpeedVolumeConfig.SpeedRange> ranges) {
        if (ranges.isEmpty()) return true;
        
        // 按最小速度排序
        ranges.sort((a, b) -> Float.compare(a.minSpeed, b.minSpeed));
        
        for (int i = 0; i < ranges.size() - 1; i++) {
            if (ranges.get(i).maxSpeed < ranges.get(i + 1).minSpeed) {
                return false; // 存在间隙
            }
        }
        return true;
    }

    private void resetProfile() {
        SpeedVolumeConfig tempConfig = new SpeedVolumeConfig();
        config.getProfiles().set(currentProfileIndex, tempConfig.getProfiles().get(currentProfileIndex));
        loadProfile(currentProfileIndex);
        updateGraphPreview();
        Toast.makeText(this, "已重置为默认配置", Toast.LENGTH_SHORT).show();
    }

    private void loadVolumeLimits() {
        int minVol = config.getMinVolumeLimit(this);
        int maxVol = config.getMaxVolumeLimit(this);
        
        seekMinVolume.setProgress(minVol);
        seekMaxVolume.setProgress(maxVol);
        tvMinVolume.setText(minVol + "%");
        tvMaxVolume.setText(maxVol + "%");
        cbSmoothTransition.setChecked(config.isSmoothTransitionEnabled(this));
    }

    private void validateVolumeLimits() {
        int minVol = seekMinVolume.getProgress();
        int maxVol = seekMaxVolume.getProgress();
        
        if (minVol > maxVol) {
            // 自动调整
            seekMaxVolume.setProgress(minVol);
            tvMaxVolume.setText(minVol + "%");
        }
    }

    private void saveVolumeLimits() {
        int minVol = seekMinVolume.getProgress();
        int maxVol = seekMaxVolume.getProgress();
        
        config.setMinVolumeLimit(this, minVol);
        config.setMaxVolumeLimit(this, maxVol);
        config.setSmoothTransitionEnabled(this, cbSmoothTransition.isChecked());
    }

    private void loadVibrationSettings() {
        cbVibrationEnabled.setChecked(config.isVibrationEnabled(this));
        cbVibrationOnProfileSwitch.setChecked(config.isVibrationOnProfileSwitch(this));
        cbVibrationOnThreshold.setChecked(config.isVibrationOnThreshold(this));
        
        int threshold = config.getVibrationSpeedThreshold(this);
        seekVibrationThreshold.setProgress(threshold);
        tvVibrationThreshold.setText(threshold + "km/h");
        
        updateVibrationSettingsEnabled(config.isVibrationEnabled(this));
    }

    private void updateVibrationSettingsEnabled(boolean enabled) {
        cbVibrationOnProfileSwitch.setEnabled(enabled);
        cbVibrationOnThreshold.setEnabled(enabled);
        seekVibrationThreshold.setEnabled(enabled);
    }

    private void saveVibrationSettings() {
        config.setVibrationEnabled(this, cbVibrationEnabled.isChecked());
        config.setVibrationOnProfileSwitch(this, cbVibrationOnProfileSwitch.isChecked());
        config.setVibrationOnThreshold(this, cbVibrationOnThreshold.isChecked());
        config.setVibrationSpeedThreshold(this, seekVibrationThreshold.getProgress());
    }

    /**
     * 显示输入错误对话框
     */
    private void showInputErrorDialog(String message) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_input_error, null);
        TextView tvMessage = dialogView.findViewById(R.id.tvErrorMessage);
        Button btnOk = dialogView.findViewById(R.id.btnOk);
        
        tvMessage.setText(message);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create();
        
        btnOk.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * 显示配置模板对话框
     */
    private void showTemplatesDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_templates, null);
        LinearLayout templatesContainer = dialogView.findViewById(R.id.templatesContainer);
        
        for (ConfigTemplate template : CONFIG_TEMPLATES) {
            View templateView = LayoutInflater.from(this).inflate(R.layout.item_template, templatesContainer, false);
            
            TextView tvName = templateView.findViewById(R.id.tvTemplateName);
            TextView tvDesc = templateView.findViewById(R.id.tvTemplateDesc);
            Button btnApply = templateView.findViewById(R.id.btnApplyTemplate);
            
            tvName.setText(template.name);
            tvDesc.setText(template.description);
            
            btnApply.setOnClickListener(v -> {
                applyTemplate(template);
                ((AlertDialog) templatesContainer.getTag()).dismiss();
            });
            
            templatesContainer.addView(templateView);
        }
        
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create();
        
        templatesContainer.setTag(dialog);
        dialog.show();
    }

    /**
     * 应用配置模板
     */
    private void applyTemplate(ConfigTemplate template) {
        rangesContainer.removeAllViews();
        
        for (SpeedVolumeConfig.SpeedRange range : template.ranges) {
            addRangeView(range);
        }
        
        updateGraphPreview();
        Toast.makeText(this, "已应用模板：" + template.name, Toast.LENGTH_SHORT).show();
    }

    /**
     * 更新曲线预览
     */
    private void updateGraphPreview() {
        // 收集当前范围数据
        List<SpeedVolumeConfig.SpeedRange> ranges = new ArrayList<>();
        
        for (int i = 0; i < rangesContainer.getChildCount(); i++) {
            View view = rangesContainer.getChildAt(i);
            SeekBar seekMinSpeed = view.findViewById(R.id.seekMinSpeed);
            SeekBar seekMaxSpeed = view.findViewById(R.id.seekMaxSpeed);
            SeekBar seekVolume = view.findViewById(R.id.seekVolume);
            
            int min = seekMinSpeed.getProgress();
            int max = seekMaxSpeed.getProgress();
            int vol = seekVolume.getProgress();
            
            ranges.add(new SpeedVolumeConfig.SpeedRange(min, max == 150 ? 999 : max, vol));
        }
        
        // 获取音量限制
        int minVol = seekMinVolume.getProgress();
        int maxVol = seekMaxVolume.getProgress();
        
        // 配置曲线图
        graphView.setMode(2); // 自定义模式
        graphView.setCustomRanges(ranges);
        graphView.setVolumeLimits(minVol, maxVol);
    }

    /**
     * 配置模板类
     */
    private static class ConfigTemplate {
        String name;
        String description;
        SpeedVolumeConfig.SpeedRange[] ranges;

        ConfigTemplate(String name, String description, SpeedVolumeConfig.SpeedRange... ranges) {
            this.name = name;
            this.description = description;
            this.ranges = ranges;
        }
    }
}
