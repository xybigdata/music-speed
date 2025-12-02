package com.speedvolume;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class ConfigActivity extends AppCompatActivity {
    private Spinner spinnerProfile;
    private EditText etProfileName;
    private LinearLayout rangesContainer;
    private SpeedVolumeConfig config;
    private int currentProfileIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        spinnerProfile = findViewById(R.id.spinnerProfile);
        etProfileName = findViewById(R.id.etProfileName);
        rangesContainer = findViewById(R.id.rangesContainer);
        Button btnRename = findViewById(R.id.btnRename);
        Button btnAdd = findViewById(R.id.btnAdd);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnReset = findViewById(R.id.btnReset);

        config = new SpeedVolumeConfig();
        config.load(this);
        currentProfileIndex = config.getActiveProfileIndex();

        setupSpinner();
        loadProfile(currentProfileIndex);

        spinnerProfile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentProfileIndex = position;
                loadProfile(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnRename.setOnClickListener(v -> renameProfile());
        btnAdd.setOnClickListener(v -> addRange());
        btnSave.setOnClickListener(v -> saveConfig());
        btnReset.setOnClickListener(v -> resetProfile());
    }

    private void setupSpinner() {
        List<String> names = new ArrayList<>();
        for (SpeedVolumeConfig.Profile p : config.getProfiles()) {
            names.add(p.name);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProfile.setAdapter(adapter);
        spinnerProfile.setSelection(currentProfileIndex);
    }

    private void loadProfile(int index) {
        SpeedVolumeConfig.Profile profile = config.getProfiles().get(index);
        etProfileName.setText(profile.name);
        rangesContainer.removeAllViews();
        for (SpeedVolumeConfig.SpeedRange range : profile.ranges) {
            addRangeView(range);
        }
    }

    private void addRangeView(SpeedVolumeConfig.SpeedRange range) {
        View view = getLayoutInflater().inflate(R.layout.item_range, rangesContainer, false);
        EditText etMin = view.findViewById(R.id.etMinSpeed);
        EditText etMax = view.findViewById(R.id.etMaxSpeed);
        EditText etVol = view.findViewById(R.id.etVolume);
        Button btnDelete = view.findViewById(R.id.btnDelete);

        etMin.setText(String.valueOf((int) range.minSpeed));
        etMax.setText(String.valueOf((int) range.maxSpeed));
        etVol.setText(String.valueOf(range.volume));

        btnDelete.setOnClickListener(v -> rangesContainer.removeView(view));
        rangesContainer.addView(view);
    }

    private void addRange() {
        addRangeView(new SpeedVolumeConfig.SpeedRange(0, 10, 10));
    }

    private void renameProfile() {
        String newName = etProfileName.getText().toString().trim();
        if (!newName.isEmpty()) {
            config.getProfiles().get(currentProfileIndex).name = newName;
            setupSpinner();
            Toast.makeText(this, "已重命名", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveConfig() {
        try {
            List<SpeedVolumeConfig.SpeedRange> ranges = new ArrayList<>();
            for (int i = 0; i < rangesContainer.getChildCount(); i++) {
                View view = rangesContainer.getChildAt(i);
                EditText etMin = view.findViewById(R.id.etMinSpeed);
                EditText etMax = view.findViewById(R.id.etMaxSpeed);
                EditText etVol = view.findViewById(R.id.etVolume);

                float min = Float.parseFloat(etMin.getText().toString());
                float max = Float.parseFloat(etMax.getText().toString());
                int vol = Integer.parseInt(etVol.getText().toString());

                ranges.add(new SpeedVolumeConfig.SpeedRange(min, max, vol));
            }
            config.getProfiles().get(currentProfileIndex).ranges = ranges;
            config.setActiveProfileIndex(currentProfileIndex);
            config.save(this);
            
            sendBroadcast(new Intent("com.speedvolume.CONFIG_CHANGED"));
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "输入错误", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetProfile() {
        SpeedVolumeConfig tempConfig = new SpeedVolumeConfig();
        config.getProfiles().set(currentProfileIndex, tempConfig.getProfiles().get(currentProfileIndex));
        loadProfile(currentProfileIndex);
    }
}
