package com.speedvolume;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.Set;

/**
 * 蓝牙连接状态广播接收器
 * 监听蓝牙设备的连接和断开事件，用于自动启停服务
 */
public class BluetoothReceiver extends BroadcastReceiver {
    private static final String TAG = "BluetoothReceiver";
    private static final String PREFS_NAME = "SpeedVolumePrefs";
    
    // 自动启停配置
    private static final String KEY_AUTO_START_STOP_ENABLED = "autoStartStopEnabled";
    private static final String KEY_TRIGGER_BLUETOOTH_ADDRESS = "triggerBluetoothAddress";
    private static final String KEY_TRIGGER_BLUETOOTH_NAME = "triggerBluetoothName";
    
    // 广播 Action：蓝牙触发启停
    public static final String ACTION_BLUETOOTH_TRIGGERED = "com.speedvolume.BLUETOOTH_TRIGGERED";
    public static final String EXTRA_TRIGGER_ACTION = "triggerAction";  // "connect" or "disconnect"
    public static final String EXTRA_DEVICE_NAME = "deviceName";
    public static final String EXTRA_DEVICE_ADDRESS = "deviceAddress";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        
        String action = intent.getAction();
        Log.d(TAG, "收到蓝牙广播: " + action);
        
        // 检查是否启用自动启停
        if (!isAutoStartStopEnabled(context)) {
            Log.d(TAG, "自动启停未启用，忽略广播");
            return;
        }
        
        // 获取目标设备地址
        String targetAddress = getTriggerBluetoothAddress(context);
        if (targetAddress == null || targetAddress.isEmpty()) {
            Log.d(TAG, "未设置触发设备，忽略广播");
            return;
        }
        
        BluetoothDevice device = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        } else {
            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        }
        
        if (device == null) {
            Log.d(TAG, "无法获取设备信息");
            return;
        }
        
        String deviceAddress = device.getAddress();
        String deviceName = device.getName();
        Log.d(TAG, "设备: " + deviceName + " (" + deviceAddress + ")");
        
        // 检查是否是目标设备
        if (!targetAddress.equals(deviceAddress)) {
            Log.d(TAG, "非目标设备，忽略");
            return;
        }
        
        // 处理不同的连接状态
        switch (action) {
            case BluetoothDevice.ACTION_ACL_CONNECTED:
                Log.d(TAG, "目标蓝牙设备已连接，触发启动服务");
                sendTriggerBroadcast(context, "connect", deviceName, deviceAddress);
                break;
                
            case BluetoothDevice.ACTION_ACL_DISCONNECTED:
            case BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED:
                Log.d(TAG, "目标蓝牙设备已断开，触发停止服务");
                sendTriggerBroadcast(context, "disconnect", deviceName, deviceAddress);
                break;
        }
    }
    
    /**
     * 发送触发广播
     */
    private void sendTriggerBroadcast(Context context, String triggerAction, String deviceName, String deviceAddress) {
        Intent intent = new Intent(ACTION_BLUETOOTH_TRIGGERED);
        intent.putExtra(EXTRA_TRIGGER_ACTION, triggerAction);
        intent.putExtra(EXTRA_DEVICE_NAME, deviceName);
        intent.putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
    
    // ==================== 配置方法 ====================
    
    /**
     * 是否启用自动启停
     */
    public static boolean isAutoStartStopEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_AUTO_START_STOP_ENABLED, false);
    }
    
    /**
     * 设置是否启用自动启停
     */
    public static void setAutoStartStopEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_AUTO_START_STOP_ENABLED, enabled).apply();
    }
    
    /**
     * 获取触发蓝牙设备地址
     */
    public static String getTriggerBluetoothAddress(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_TRIGGER_BLUETOOTH_ADDRESS, "");
    }
    
    /**
     * 设置触发蓝牙设备地址
     */
    public static void setTriggerBluetoothAddress(Context context, String address) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_TRIGGER_BLUETOOTH_ADDRESS, address).apply();
    }
    
    /**
     * 获取触发蓝牙设备名称
     */
    public static String getTriggerBluetoothName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_TRIGGER_BLUETOOTH_NAME, "");
    }
    
    /**
     * 设置触发蓝牙设备名称
     */
    public static void setTriggerBluetoothName(Context context, String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_TRIGGER_BLUETOOTH_NAME, name).apply();
    }
    
    /**
     * 清除触发设备设置
     */
    public static void clearTriggerDevice(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .remove(KEY_TRIGGER_BLUETOOTH_ADDRESS)
            .remove(KEY_TRIGGER_BLUETOOTH_NAME)
            .apply();
    }
    
    /**
     * 检查指定设备当前是否已连接
     * @param context 上下文
     * @param deviceAddress 设备地址
     * @return 是否已连接
     */
    public static boolean isDeviceConnected(Context context, String deviceAddress) {
        if (deviceAddress == null || deviceAddress.isEmpty()) {
            return false;
        }
        
        try {
            BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                return false;
            }
            
            BluetoothAdapter adapter = bluetoothManager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                return false;
            }
            
            // 获取已连接的蓝牙设备
            // A2DP profile (音频设备如耳机、音箱)
            int a2dpState = bluetoothManager.getConnectionState(adapter.getRemoteDevice(deviceAddress), BluetoothProfile.A2DP);
            if (a2dpState == BluetoothProfile.STATE_CONNECTED) {
                return true;
            }
            
            // HEADSET profile (耳机)
            int headsetState = bluetoothManager.getConnectionState(adapter.getRemoteDevice(deviceAddress), BluetoothProfile.HEADSET);
            if (headsetState == BluetoothProfile.STATE_CONNECTED) {
                return true;
            }
            
            return false;
        } catch (SecurityException e) {
            Log.e(TAG, "检查设备连接状态失败: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "检查设备连接状态异常: " + e.getMessage());
            return false;
        }
    }
}
