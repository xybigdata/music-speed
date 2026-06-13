package com.speedvolume;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机启动接收器
 * 用于在设备启动后自动恢复服务运行状态
 */
public class BootReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            
            // 检查服务之前是否在运行
            if (SpeedVolumeService.wasServiceRunning(context)) {
                // 重新启动服务
                Intent serviceIntent = new Intent(context, SpeedVolumeService.class);
                
                // Android 8.0+ 需要使用 startForegroundService
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }
    }
}
