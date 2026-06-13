package com.speedvolume;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 蓝牙设备选择界面
 * 用于配置自动启停的触发设备
 */
public class BluetoothDeviceActivity extends AppCompatActivity {
    private Switch switchAutoStartStop;
    private ListView listViewDevices;
    private Button btnClearSelection;
    private TextView tvCurrentDevice;
    private TextView tvNoDevice;
    
    private BluetoothAdapter bluetoothAdapter;
    private DeviceAdapter deviceAdapter;
    private List<BluetoothDeviceInfo> deviceList = new ArrayList<>();
    
    private SpeedVolumeConfig config;
    private String selectedAddress = "";
    private String selectedName = "";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_device);
        
        // 初始化
        config = new SpeedVolumeConfig();
        config.load(this);
        
        // 初始化蓝牙适配器
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }
        
        initViews();
        loadCurrentSettings();
        loadPairedDevices();
    }
    
    private void initViews() {
        switchAutoStartStop = findViewById(R.id.switchAutoStartStop);
        listViewDevices = findViewById(R.id.listViewDevices);
        btnClearSelection = findViewById(R.id.btnClearSelection);
        tvCurrentDevice = findViewById(R.id.tvCurrentDevice);
        tvNoDevice = findViewById(R.id.tvNoDevice);
        
        // 自动启停开关
        switchAutoStartStop.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setAutoStartStopEnabled(this, isChecked);
            updateDeviceListEnabled(isChecked);
        });
        
        // 清除选择按钮
        btnClearSelection.setOnClickListener(v -> {
            config.clearTriggerDevice(this);
            selectedAddress = "";
            selectedName = "";
            updateCurrentDeviceDisplay();
            deviceAdapter.notifyDataSetChanged();
            Toast.makeText(this, R.string.bluetooth_selection_cleared, Toast.LENGTH_SHORT).show();
        });
        
        // 设备列表适配器
        deviceAdapter = new DeviceAdapter();
        listViewDevices.setAdapter(deviceAdapter);
        listViewDevices.setOnItemClickListener((parent, view, position, id) -> {
            if (!switchAutoStartStop.isChecked()) {
                Toast.makeText(this, R.string.bluetooth_enable_auto_start_first, Toast.LENGTH_SHORT).show();
                return;
            }
            
            BluetoothDeviceInfo deviceInfo = deviceList.get(position);
            selectDevice(deviceInfo);
        });
    }
    
    private void loadCurrentSettings() {
        boolean enabled = config.isAutoStartStopEnabled(this);
        switchAutoStartStop.setChecked(enabled);
        updateDeviceListEnabled(enabled);
        
        selectedAddress = config.getTriggerBluetoothAddress(this);
        selectedName = config.getTriggerBluetoothName(this);
        updateCurrentDeviceDisplay();
    }
    
    private void updateCurrentDeviceDisplay() {
        if (selectedName != null && !selectedName.isEmpty()) {
            tvCurrentDevice.setText(getString(R.string.bluetooth_current_device, selectedName));
            tvCurrentDevice.setVisibility(View.VISIBLE);
        } else {
            tvCurrentDevice.setVisibility(View.GONE);
        }
    }
    
    private void updateDeviceListEnabled(boolean enabled) {
        listViewDevices.setEnabled(enabled);
        deviceAdapter.setEnabled(enabled);
    }
    
    private void loadPairedDevices() {
        deviceList.clear();
        
        if (bluetoothAdapter == null) {
            tvNoDevice.setText(R.string.bluetooth_not_supported);
            tvNoDevice.setVisibility(View.VISIBLE);
            listViewDevices.setVisibility(View.GONE);
            return;
        }
        
        if (!bluetoothAdapter.isEnabled()) {
            tvNoDevice.setText(R.string.bluetooth_disabled);
            tvNoDevice.setVisibility(View.VISIBLE);
            listViewDevices.setVisibility(View.GONE);
            return;
        }
        
        // 检查蓝牙权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                    != PackageManager.PERMISSION_GRANTED) {
                tvNoDevice.setText(R.string.bluetooth_permission_required);
                tvNoDevice.setVisibility(View.VISIBLE);
                listViewDevices.setVisibility(View.GONE);
                // 请求权限
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100);
                return;
            }
        }
        
        // 获取已配对设备
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        
        if (pairedDevices == null || pairedDevices.isEmpty()) {
            tvNoDevice.setText(R.string.bluetooth_no_paired_devices);
            tvNoDevice.setVisibility(View.VISIBLE);
            listViewDevices.setVisibility(View.GONE);
            return;
        }
        
        // 添加到列表
        for (BluetoothDevice device : pairedDevices) {
            String name = device.getName();
            String address = device.getAddress();
            if (name == null || name.isEmpty()) {
                name = address;
            }
            deviceList.add(new BluetoothDeviceInfo(name, address));
        }
        
        tvNoDevice.setVisibility(View.GONE);
        listViewDevices.setVisibility(View.VISIBLE);
        deviceAdapter.notifyDataSetChanged();
    }
    
    private void selectDevice(BluetoothDeviceInfo deviceInfo) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.bluetooth_select_device_title)
            .setMessage(getString(R.string.bluetooth_select_device_message, deviceInfo.name))
            .setPositiveButton(R.string.confirm, (dialog, which) -> {
                selectedAddress = deviceInfo.address;
                selectedName = deviceInfo.name;
                config.setTriggerBluetoothAddress(this, selectedAddress);
                config.setTriggerBluetoothName(this, selectedName);
                updateCurrentDeviceDisplay();
                deviceAdapter.notifyDataSetChanged();
                Toast.makeText(this, getString(R.string.bluetooth_device_selected, deviceInfo.name), Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadPairedDevices();
            }
        }
    }
    
    /**
     * 蓝牙设备信息
     */
    private static class BluetoothDeviceInfo {
        String name;
        String address;
        
        BluetoothDeviceInfo(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }
    
    /**
     * 设备列表适配器
     */
    private class DeviceAdapter extends BaseAdapter {
        private boolean enabled = true;
        
        void setEnabled(boolean enabled) {
            this.enabled = enabled;
            notifyDataSetChanged();
        }
        
        @Override
        public int getCount() {
            return deviceList.size();
        }
        
        @Override
        public Object getItem(int position) {
            return deviceList.get(position);
        }
        
        @Override
        public long getItemId(int position) {
            return position;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            
            if (convertView == null) {
                convertView = LayoutInflater.from(BluetoothDeviceActivity.this)
                    .inflate(R.layout.item_bluetooth_device, parent, false);
                holder = new ViewHolder();
                holder.tvDeviceName = convertView.findViewById(R.id.tvDeviceName);
                holder.tvDeviceAddress = convertView.findViewById(R.id.tvDeviceAddress);
                holder.cbSelected = convertView.findViewById(R.id.cbSelected);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            
            BluetoothDeviceInfo deviceInfo = deviceList.get(position);
            holder.tvDeviceName.setText(deviceInfo.name);
            holder.tvDeviceAddress.setText(deviceInfo.address);
            
            boolean isSelected = deviceInfo.address.equals(selectedAddress);
            holder.cbSelected.setChecked(isSelected);
            
            // 视觉提示
            convertView.setAlpha(enabled ? 1.0f : 0.5f);
            
            return convertView;
        }
    }
    
    private static class ViewHolder {
        TextView tvDeviceName;
        TextView tvDeviceAddress;
        CheckBox cbSelected;
    }
}
