package com.speedvolume;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class Profile2ConfigActivity extends AppCompatActivity {
    private RadioGroup rgMode;
    private LinearLayout linearParams, powerParams;
    private SeekBar seekVolumeCrit, seekSpeedCrit, seekEaseCoef, seekPowerIndex;
    private TextView tvVolumeCrit, tvSpeedCrit, tvCoefficient, tvEaseCoef, tvPowerIndex;
    private FunctionGraphView graphView;
    private SpeedVolumeConfig config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile2_config);

        rgMode = findViewById(R.id.rgMode);
        linearParams = findViewById(R.id.linearParams);
        powerParams = findViewById(R.id.powerParams);
        seekVolumeCrit = findViewById(R.id.seekVolumeCrit);
        seekSpeedCrit = findViewById(R.id.seekSpeedCrit);
        seekEaseCoef = findViewById(R.id.seekEaseCoef);
        seekPowerIndex = findViewById(R.id.seekPowerIndex);
        tvVolumeCrit = findViewById(R.id.tvVolumeCrit);
        tvSpeedCrit = findViewById(R.id.tvSpeedCrit);
        tvCoefficient = findViewById(R.id.tvCoefficient);
        tvEaseCoef = findViewById(R.id.tvEaseCoef);
        tvPowerIndex = findViewById(R.id.tvPowerIndex);
        graphView = findViewById(R.id.graphView);
        Button btnSave = findViewById(R.id.btnSave);

        config = new SpeedVolumeConfig();
        config.load(this);

        loadSettings();

        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbLinear) {
                linearParams.setVisibility(View.VISIBLE);
                powerParams.setVisibility(View.GONE);
                graphView.setMode(0);
                graphView.setLinearParams(seekVolumeCrit.getProgress(), seekSpeedCrit.getProgress());
            } else {
                linearParams.setVisibility(View.GONE);
                powerParams.setVisibility(View.VISIBLE);
                graphView.setMode(1);
                graphView.setPowerParams(seekEaseCoef.getProgress() / 10.0f, 1.0f + seekPowerIndex.getProgress() / 10.0f);
            }
        });

        seekVolumeCrit.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvVolumeCrit.setText(String.valueOf(progress));
                updateCoefficient();
                graphView.setLinearParams(progress, seekSpeedCrit.getProgress());
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekSpeedCrit.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvSpeedCrit.setText(String.valueOf(progress));
                updateCoefficient();
                graphView.setLinearParams(seekVolumeCrit.getProgress(), progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekEaseCoef.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 10.0f;
                tvEaseCoef.setText(String.format("%.1f", value));
                graphView.setPowerParams(value, 1.0f + seekPowerIndex.getProgress() / 10.0f);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekPowerIndex.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = 1.0f + progress / 10.0f;
                tvPowerIndex.setText(String.format("%.1f", value));
                graphView.setPowerParams(seekEaseCoef.getProgress() / 10.0f, value);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void loadSettings() {
        int mode = config.getProfile2Mode(this);
        if (mode == 0) {
            rgMode.check(R.id.rbLinear);
            linearParams.setVisibility(View.VISIBLE);
            powerParams.setVisibility(View.GONE);
        } else {
            rgMode.check(R.id.rbPower);
            linearParams.setVisibility(View.GONE);
            powerParams.setVisibility(View.VISIBLE);
        }

        int volumeCrit = config.getProfile2VolumeCrit(this);
        int speedCrit = config.getProfile2SpeedCrit(this);
        seekVolumeCrit.setProgress(volumeCrit);
        seekSpeedCrit.setProgress(speedCrit);
        tvVolumeCrit.setText(String.valueOf(volumeCrit));
        tvSpeedCrit.setText(String.valueOf(speedCrit));
        updateCoefficient();

        float easeCoef = config.getProfile2EaseCoef(this);
        float powerIndex = config.getProfile2PowerIndex(this);
        seekEaseCoef.setProgress((int) (easeCoef * 10));
        seekPowerIndex.setProgress((int) ((powerIndex - 1.0f) * 10));
        tvEaseCoef.setText(String.format("%.1f", easeCoef));
        tvPowerIndex.setText(String.format("%.1f", powerIndex));
        
        if (mode == 0) {
            graphView.setMode(0);
            graphView.setLinearParams(volumeCrit, speedCrit);
        } else {
            graphView.setMode(1);
            graphView.setPowerParams(easeCoef, powerIndex);
        }
    }

    private void updateCoefficient() {
        int volumeCrit = seekVolumeCrit.getProgress();
        int speedCrit = seekSpeedCrit.getProgress();
        if (speedCrit > 0) {
            float k = (float) volumeCrit / speedCrit;
            tvCoefficient.setText(String.format("狂飙系数 (k): %.2f", k));
        }
    }

    private void saveSettings() {
        int mode = rgMode.getCheckedRadioButtonId() == R.id.rbLinear ? 0 : 1;
        config.setProfile2Mode(this, mode);

        if (mode == 0) {
            config.setProfile2VolumeCrit(this, seekVolumeCrit.getProgress());
            config.setProfile2SpeedCrit(this, seekSpeedCrit.getProgress());
        } else {
            config.setProfile2EaseCoef(this, seekEaseCoef.getProgress() / 10.0f);
            config.setProfile2PowerIndex(this, 1.0f + seekPowerIndex.getProgress() / 10.0f);
        }

        sendBroadcast(new Intent("com.speedvolume.CONFIG_CHANGED"));
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
