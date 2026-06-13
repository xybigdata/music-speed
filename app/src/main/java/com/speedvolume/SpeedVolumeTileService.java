package com.speedvolume;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * 快捷设置磁贴服务
 * 允许用户在通知栏快捷设置面板中快速启停音速骑士服务
 */
public class SpeedVolumeTileService extends TileService {
    
    private static final String PREFS_NAME = "SpeedVolumePrefs";
    private static final String KEY_SERVICE_RUNNING = "serviceRunning";
    
    private BroadcastReceiver stateChangeReceiver;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // 注册状态变化接收器
        stateChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (SpeedVolumeService.ACTION_SERVICE_STATE_CHANGED.equals(intent.getAction())) {
                    boolean isRunning = intent.getBooleanExtra(SpeedVolumeService.EXTRA_SERVICE_RUNNING, false);
                    updateTileState(isRunning);
                }
            }
        };
    }
    
    @Override
    public void onStartListening() {
        super.onStartListening();
        
        // 注册广播接收器
        IntentFilter filter = new IntentFilter(SpeedVolumeService.ACTION_SERVICE_STATE_CHANGED);
        registerReceiver(stateChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        
        // 磁贴变为可见时，更新磁贴状态
        updateTileState();
    }
    
    @Override
    public void onStopListening() {
        super.onStopListening();
        
        // 取消注册广播接收器
        try {
            if (stateChangeReceiver != null) {
                unregisterReceiver(stateChangeReceiver);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void onClick() {
        super.onClick();
        
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        
        // 根据当前状态切换服务
        boolean isCurrentlyActive = tile.getState() == Tile.STATE_ACTIVE;
        
        if (isCurrentlyActive) {
            // 停止服务
            stopSpeedVolumeService();
            updateTileState(false);
        } else {
            // 启动服务
            startSpeedVolumeService();
            updateTileState(true);
        }
    }
    
    /**
     * 更新磁贴状态（从 SharedPreferences 读取）
     */
    private void updateTileState() {
        boolean isRunning = wasServiceRunning();
        updateTileState(isRunning);
    }
    
    /**
     * 更新磁贴状态
     * @param isActive 服务是否正在运行
     */
    private void updateTileState(boolean isActive) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        
        if (isActive) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel("音速骑士");
            tile.setSubtitle("运行中");
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("音速骑士");
            tile.setSubtitle("已停止");
        }
        
        tile.updateTile();
    }
    
    /**
     * 启动速度音量服务
     */
    private void startSpeedVolumeService() {
        try {
            Intent intent = new Intent(this, SpeedVolumeService.class);
            
            // Android 8.0+ 需要使用 startForegroundService
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            
            // 保存服务运行状态
            saveServiceRunningState(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 停止速度音量服务
     */
    private void stopSpeedVolumeService() {
        try {
            Intent intent = new Intent(this, SpeedVolumeService.class);
            stopService(intent);
            
            // 保存服务停止状态
            saveServiceRunningState(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 保存服务运行状态
     * @param isRunning 服务是否正在运行
     */
    private void saveServiceRunningState(boolean isRunning) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean(KEY_SERVICE_RUNNING, isRunning)
            .apply();
    }
    
    /**
     * 获取服务之前是否在运行
     * @return 之前是否在运行
     */
    private boolean wasServiceRunning() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SERVICE_RUNNING, false);
    }
}
