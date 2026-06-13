package com.speedvolume;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;

public class SpeedVolumeService extends Service implements SpeedCalculator.SpeedCallback, BatteryOptimizer.BatteryOptimizationListener {
    private static final String CHANNEL_ID = "SpeedVolumeChannel";
    private static final String PREFS_NAME = "SpeedVolumePrefs";
    private static final String KEY_SERVICE_RUNNING = "serviceRunning";
    private static final String KEY_ACTIVE_PROFILE = "activeProfile";
    private static final int RESTART_DELAY_MS = 1000;  // 1秒后重启
    
    // 用于通知磁贴状态更新的广播 Action
    public static final String ACTION_SERVICE_STATE_CHANGED = "com.speedvolume.SERVICE_STATE_CHANGED";
    public static final String EXTRA_SERVICE_RUNNING = "serviceRunning";
    
    // 振动反馈广播 Action
    public static final String ACTION_PROFILE_SWITCHED = "com.speedvolume.PROFILE_SWITCHED";
    public static final String ACTION_THRESHOLD_REACHED = "com.speedvolume.THRESHOLD_REACHED";
    
    // 蓝牙触发广播 Action
    public static final String ACTION_BLUETOOTH_TRIGGERED = "com.speedvolume.BLUETOOTH_TRIGGERED";
    public static final String EXTRA_TRIGGER_ACTION = "triggerAction";
    
    // 通知操作广播 Action
    public static final String ACTION_NOTIFICATION_PAUSE = "com.speedvolume.NOTIFICATION_PAUSE";
    public static final String ACTION_NOTIFICATION_RESUME = "com.speedvolume.NOTIFICATION_RESUME";
    public static final String ACTION_NOTIFICATION_STOP = "com.speedvolume.NOTIFICATION_STOP";
    
    private SpeedCalculator speedCalculator;
    private VolumeController volumeController;
    private BatteryOptimizer batteryOptimizer;
    private VibrationHelper vibrationHelper;
    private PowerManager.WakeLock wakeLock;
    private float currentSpeedKmh = 0;
    private float previousSpeedKmh = 0;
    private boolean isGpsAvailable = false;
    private SpeedVolumeConfig config;
    private static UpdateListener updateListener;
    private BroadcastReceiver configReceiver;
    private BroadcastReceiver bluetoothTriggerReceiver;
    
    // 使用统计仓库
    private UsageStatsRepository usageStatsRepository;
    
    // WakeLock 管理状态
    private boolean isWakeLockReleased = false;
    private static final int IDLE_NOTIFICATION_THRESHOLD_MINUTES = 5;
    
    // 振动阈值检测状态
    private boolean hasVibratedForThreshold = false;
    private int previousProfileIndex = -1;
    
    // 暂停状态
    private boolean isPaused = false;
    private BroadcastReceiver notificationActionReceiver;

    public interface UpdateListener {
        void onUpdate(float speedKmh, int volume);
    }

    public static void setUpdateListener(UpdateListener listener) {
        updateListener = listener;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        speedCalculator = new SpeedCalculator(this);
        speedCalculator.setSpeedCallback(this);
        volumeController = new VolumeController(this);
        
        // 初始化电量优化器
        batteryOptimizer = new BatteryOptimizer(this);
        batteryOptimizer.setListener(this);
        
        // 初始化振动助手
        vibrationHelper = new VibrationHelper(this);
        
        // 初始化使用统计仓库
        usageStatsRepository = new UsageStatsRepository(this);
        
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "SpeedVolume::WakeLock");
        wakeLock.setReferenceCounted(false);
        config = new SpeedVolumeConfig();
        config.load(this);
        
        // 从配置加载音量限制
        volumeController.setVolumeLimits(
            config.getMinVolumeLimit(this),
            config.getMaxVolumeLimit(this)
        );
        volumeController.setSmoothTransitionEnabled(config.isSmoothTransitionEnabled(this));
        
        // 从配置加载振动设置
        vibrationHelper.setEnabled(config.isVibrationEnabled(this));
        previousProfileIndex = config.getActiveProfileIndex();
        
        createNotificationChannel();
        registerConfigReceiver();
        registerBluetoothTriggerReceiver();
        registerNotificationActionReceiver();
    }

    private void registerConfigReceiver() {
        configReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int oldProfileIndex = config.getActiveProfileIndex();
                
                config.reloadActiveProfile(context);
                // 重新加载音量限制设置
                if (volumeController != null) {
                    volumeController.setVolumeLimits(
                        config.getMinVolumeLimit(context),
                        config.getMaxVolumeLimit(context)
                    );
                    volumeController.setSmoothTransitionEnabled(config.isSmoothTransitionEnabled(context));
                }
                
                // 重新加载振动设置
                if (vibrationHelper != null) {
                    vibrationHelper.setEnabled(config.isVibrationEnabled(context));
                }
                
                // 检测方案切换
                int newProfileIndex = config.getActiveProfileIndex();
                if (oldProfileIndex != newProfileIndex) {
                    onProfileSwitched(newProfileIndex);
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.speedvolume.CONFIG_CHANGED");
        registerReceiver(configReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }
    
    /**
     * 注册蓝牙触发接收器
     * 接收蓝牙连接/断开触发的自动启停广播
     */
    private void registerBluetoothTriggerReceiver() {
        bluetoothTriggerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    return;
                }
                
                String triggerAction = intent.getStringExtra(EXTRA_TRIGGER_ACTION);
                String deviceName = intent.getStringExtra("deviceName");
                
                if ("connect".equals(triggerAction)) {
                    // 蓝牙设备连接，启动服务
                    android.util.Log.d("SpeedVolumeService", "蓝牙设备 " + deviceName + " 已连接，自动启动服务");
                    // 服务已在运行，无需操作
                } else if ("disconnect".equals(triggerAction)) {
                    // 蓝牙设备断开，停止服务
                    android.util.Log.d("SpeedVolumeService", "蓝牙设备 " + deviceName + " 已断开，自动停止服务");
                    stopSelf();
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_BLUETOOTH_TRIGGERED);
        registerReceiver(bluetoothTriggerReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }
    
    /**
     * 注册通知操作接收器
     * 处理通知栏按钮点击事件
     */
    private void registerNotificationActionReceiver() {
        notificationActionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    return;
                }
                
                String action = intent.getAction();
                if (action == null) {
                    return;
                }
                
                switch (action) {
                    case ACTION_NOTIFICATION_PAUSE:
                        setPaused(true);
                        break;
                    case ACTION_NOTIFICATION_RESUME:
                        setPaused(false);
                        break;
                    case ACTION_NOTIFICATION_STOP:
                        stopSelf();
                        break;
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_NOTIFICATION_PAUSE);
        filter.addAction(ACTION_NOTIFICATION_RESUME);
        filter.addAction(ACTION_NOTIFICATION_STOP);
        registerReceiver(notificationActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }
    
    /**
     * 设置暂停状态
     * @param paused 是否暂停
     */
    private void setPaused(boolean paused) {
        isPaused = paused;
        updateCustomNotification();
        
        if (paused) {
            // 暂停时停止速度计算
            if (speedCalculator != null) {
                speedCalculator.stop();
            }
        } else {
            // 恢复时重新启动速度计算
            if (speedCalculator != null) {
                speedCalculator.start();
            }
        }
    }
    
    /**
     * 广播服务状态变化
     * 通知磁贴更新状态
     * @param isRunning 服务是否正在运行
     */
    private void broadcastServiceStateChange(boolean isRunning) {
        Intent intent = new Intent(ACTION_SERVICE_STATE_CHANGED);
        intent.putExtra(EXTRA_SERVICE_RUNNING, isRunning);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 保存服务运行状态
        saveServiceRunningState(true);
        
        // 广播服务状态变化
        broadcastServiceStateChange(true);
        
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
        
        // 创建并显示自定义通知
        Notification notification = buildCustomNotification();
        startForeground(1, notification);

        // 使用 SpeedCalculator 替代直接传感器监听
        speedCalculator.start();

        // 开始使用统计会话
        if (usageStatsRepository != null) {
            usageStatsRepository.startSession();
        }

        return START_STICKY;
    }
    
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // 用户从最近任务中移除应用时，设置重启定时器
        scheduleServiceRestart();
        super.onTaskRemoved(rootIntent);
    }
    
    /**
     * 设置服务重启定时器
     * 当服务被系统杀死或从最近任务移除时，自动重启服务
     */
    private void scheduleServiceRestart() {
        Intent restartIntent = new Intent(this, SpeedVolumeService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
            this, 
            1, 
            restartIntent, 
            PendingIntent.FLAG_IMMUTABLE
        );
        
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + RESTART_DELAY_MS,
                pendingIntent
            );
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
            .putInt(KEY_ACTIVE_PROFILE, config.getActiveProfileIndex())
            .apply();
    }
    
    /**
     * 获取服务之前是否在运行
     * @return 之前是否在运行
     */
    public static boolean wasServiceRunning(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SERVICE_RUNNING, false);
    }
    
    /**
     * 恢复服务状态（在重启后调用）
     */
    private void restoreServiceState() {
        // 配置已在 onCreate 中加载
        // 这里可以恢复其他需要的状态
    }

    @Override
    public void onSpeedUpdated(float speedKmh, boolean fromGps) {
        // 如果已暂停，不处理速度更新
        if (isPaused) {
            return;
        }
        
        // 速度更新回调（来自 SpeedCalculator）
        previousSpeedKmh = currentSpeedKmh;
        currentSpeedKmh = speedKmh;
        
        // 更新电量优化器的速度数据（用于计算变化率和静止检测）
        if (batteryOptimizer != null) {
            batteryOptimizer.updateSpeed(speedKmh);
        }
        
        // 检测速度阈值振动
        checkSpeedThresholdVibration(speedKmh);
        
        adjustVolume(currentSpeedKmh);
        
        // 更新通知显示
        updateCustomNotification();
        
        // 记录速度快照
        if (usageStatsRepository != null) {
            int volumePercent = volumeController != null ? volumeController.getCurrentVolume() : 0;
            // 获取 GPS 坐标（如果有）
            double latitude = 0, longitude = 0;
            if (speedCalculator != null && fromGps) {
                // 从 SpeedCalculator 获取位置信息
                // 注意：SpeedCalculator 需要暴露位置接口，这里暂时使用 0
            }
            usageStatsRepository.addSnapshot(speedKmh, volumePercent, latitude, longitude);
        }
    }
    
    /**
     * 检测速度阈值振动
     * 当速度超过阈值时触发一次振动
     * @param speedKmh 当前速度
     */
    private void checkSpeedThresholdVibration(float speedKmh) {
        if (vibrationHelper == null || !vibrationHelper.isEnabled()) {
            return;
        }
        
        int threshold = config.getVibrationSpeedThreshold(this);
        
        // 检查是否启用阈值振动
        if (!config.isVibrationOnThreshold(this)) {
            return;
        }
        
        // 速度超过阈值时触发振动（只触发一次）
        if (speedKmh >= threshold && !hasVibratedForThreshold) {
            vibrationHelper.vibrateThreshold();
            hasVibratedForThreshold = true;
            
            // 广播阈值达到事件
            broadcastThresholdReached(speedKmh, threshold);
        }
        // 速度降低到阈值以下时重置状态
        else if (speedKmh < threshold * 0.8f) {  // 使用80%阈值避免频繁触发
            hasVibratedForThreshold = false;
        }
    }
    
    /**
     * 方案切换时的处理
     * @param newProfileIndex 新的方案索引
     */
    private void onProfileSwitched(int newProfileIndex) {
        previousProfileIndex = newProfileIndex;
        
        // 触发方案切换振动
        if (vibrationHelper != null && config.isVibrationOnProfileSwitch(this)) {
            vibrationHelper.vibrateProfileSwitch();
        }
        
        // 广播方案切换事件
        broadcastProfileSwitched(newProfileIndex);
    }
    
    /**
     * 广播方案切换事件
     * @param profileIndex 方案索引
     */
    private void broadcastProfileSwitched(int profileIndex) {
        Intent intent = new Intent(ACTION_PROFILE_SWITCHED);
        intent.putExtra("profileIndex", profileIndex);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
    
    /**
     * 广播阈值达到事件
     * @param currentSpeed 当前速度
     * @param threshold 阈值
     */
    private void broadcastThresholdReached(float currentSpeed, int threshold) {
        Intent intent = new Intent(ACTION_THRESHOLD_REACHED);
        intent.putExtra("currentSpeed", currentSpeed);
        intent.putExtra("threshold", threshold);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
    
    @Override
    public void onGpsStatusChanged(boolean available) {
        // GPS 状态变化回调
        isGpsAvailable = available;
        // 更新通知显示GPS状态
        updateCustomNotification();
    }
    
    /**
     * 构建自定义通知
     * @return 通知对象
     */
    private Notification buildCustomNotification() {
        // 创建自定义布局
        RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notification_speed_volume);
        
        // 设置速度和音量值
        contentView.setTextViewText(R.id.notification_speed_value, String.format("%.1f", currentSpeedKmh));
        contentView.setTextViewText(R.id.notification_volume_value, volumeController.getCurrentVolume() + "%");
        
        // 设置电池状态
        int batteryLevel = batteryOptimizer != null ? batteryOptimizer.getBatteryLevel() : 100;
        contentView.setTextViewText(R.id.notification_battery_value, batteryLevel + "%");
        
        // 设置状态文本
        String statusText;
        if (isPaused) {
            statusText = getString(R.string.notification_status_paused);
        } else if (isWakeLockReleased) {
            statusText = getString(R.string.notification_status_idle);
        } else if (batteryOptimizer != null && batteryOptimizer.isLowPowerMode()) {
            statusText = getString(R.string.notification_status_low_power);
        } else if (isGpsAvailable) {
            statusText = getString(R.string.notification_status_gps);
        } else {
            statusText = getString(R.string.notification_status_sensor);
        }
        contentView.setTextViewText(R.id.notification_status, statusText);
        
        // 设置暂停/恢复按钮图标
        if (isPaused) {
            contentView.setImageViewResource(R.id.btn_pause, R.drawable.ic_notification_play);
            contentView.setContentDescription(R.id.btn_pause, getString(R.string.notification_action_resume));
        } else {
            contentView.setImageViewResource(R.id.btn_pause, R.drawable.ic_notification_pause);
            contentView.setContentDescription(R.id.btn_pause, getString(R.string.notification_action_pause));
        }
        
        // 设置按钮点击事件
        // 暂停/恢复按钮
        Intent pauseIntent = new Intent(isPaused ? ACTION_NOTIFICATION_RESUME : ACTION_NOTIFICATION_PAUSE);
        pauseIntent.setPackage(getPackageName());
        PendingIntent pausePendingIntent = PendingIntent.getBroadcast(
            this, 0, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        contentView.setOnClickPendingIntent(R.id.btn_pause, pausePendingIntent);
        
        // 停止按钮
        Intent stopIntent = new Intent(ACTION_NOTIFICATION_STOP);
        stopIntent.setPackage(getPackageName());
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        contentView.setOnClickPendingIntent(R.id.btn_stop, stopPendingIntent);
        
        // 打开应用按钮
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
            this, 2, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        contentView.setOnClickPendingIntent(R.id.btn_open, openPendingIntent);
        
        // 构建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile_speed)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getNotificationContentText())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContent(contentView)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle());
        
        // 设置点击打开应用
        builder.setContentIntent(openPendingIntent);
        
        return builder.build();
    }
    
    /**
     * 获取通知内容文本
     * @return 内容文本
     */
    private String getNotificationContentText() {
        if (isPaused) {
            return "音量控制已暂停";
        } else if (isWakeLockReleased) {
            return "静止待机中";
        } else {
            return String.format("速度: %.1f km/h | 音量: %d%%", currentSpeedKmh, 
                volumeController != null ? volumeController.getCurrentVolume() : 0);
        }
    }
    
    /**
     * 更新自定义通知
     */
    private void updateCustomNotification() {
        try {
            Notification notification = buildCustomNotification();
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.notify(1, notification);
        } catch (Exception e) {
            android.util.Log.e("SpeedVolumeService", "Failed to update notification", e);
        }
    }
    
    /**
     * 旧版通知更新方法（保留兼容）
     */
    private void updateNotification() {
        updateCustomNotification();
    }

    private void adjustVolume(float speedKmh) {
        int volumePercent = config.getVolumeForSpeed(speedKmh, this);
        
        // 使用 VolumeController 设置音量（自动应用限制和平滑过渡）
        volumeController.setVolume(volumePercent);

        if (updateListener != null) {
            updateListener.onUpdate(speedKmh, volumePercent);
        }
    }

    /**
     * 获取当前是否使用GPS模式
     * @return true=GPS可用，false=使用加速度传感器
     */
    public boolean isGpsAvailable() {
        return isGpsAvailable;
    }
    
    /**
     * 获取加速度估算速度
     * @return 速度 (km/h)
     */
    public float getAccelerometerSpeed() {
        return speedCalculator != null ? speedCalculator.getAccelerometerSpeed() : 0;
    }
    
    /**
     * 获取GPS速度
     * @return 速度 (km/h)
     */
    public float getGpsSpeed() {
        return speedCalculator != null ? speedCalculator.getGpsSpeed() : 0;
    }
    
    /**
     * 获取当前音量百分比
     * @return 音量百分比 (0-100)
     */
    public int getCurrentVolumePercent() {
        return volumeController != null ? volumeController.getCurrentVolume() : 0;
    }
    
    /**
     * 立即应用音量（跳过平滑过渡）
     */
    public void applyVolumeImmediately() {
        if (volumeController != null) {
            volumeController.applyImmediately();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // 取消重启定时器
        cancelServiceRestart();
        
        // 保存服务已停止状态
        saveServiceRunningState(false);
        
        // 广播服务状态变化
        broadcastServiceStateChange(false);
        
        // 结束使用统计会话
        if (usageStatsRepository != null) {
            usageStatsRepository.endSession();
            usageStatsRepository.close();
        }
        
        if (speedCalculator != null) {
            speedCalculator.stop();
        }
        if (volumeController != null) {
            volumeController.stop();
        }
        if (batteryOptimizer != null) {
            batteryOptimizer.stop();
        }
        if (vibrationHelper != null) {
            vibrationHelper.cancel();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (configReceiver != null) {
            unregisterReceiver(configReceiver);
        }
        if (bluetoothTriggerReceiver != null) {
            unregisterReceiver(bluetoothTriggerReceiver);
        }
        if (notificationActionReceiver != null) {
            unregisterReceiver(notificationActionReceiver);
        }
    }
    
    /**
     * 取消服务重启定时器
     */
    private void cancelServiceRestart() {
        Intent restartIntent = new Intent(this, SpeedVolumeService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
            this, 
            1, 
            restartIntent, 
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE
        );
        
        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "音速骑士服务",
            NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }
    
    // ==================== BatteryOptimizer.BatteryOptimizationListener 实现 ====================
    
    @Override
    public void onSampleRateChanged(int sampleRate) {
        // 采样频率变化，更新 SpeedCalculator
        if (speedCalculator != null) {
            speedCalculator.updateSampleRate(sampleRate);
        }
    }
    
    @Override
    public void onLowPowerModeChanged(boolean enabled) {
        // 低电量模式变化，更新通知
        updateNotificationWithBatteryStatus();
    }
    
    @Override
    public void onIdleDetected(int idleMinutes) {
        // 静止检测，检查是否需要释放 WakeLock
        checkAndReleaseWakeLock();
    }
    
    @Override
    public void onChargingStatusChanged(boolean isCharging) {
        // 充电状态变化
        if (isCharging && isWakeLockReleased) {
            // 充电时重新获取 WakeLock
            acquireWakeLock();
        }
        updateNotificationWithBatteryStatus();
    }
    
    /**
     * 检查并释放 WakeLock
     */
    private void checkAndReleaseWakeLock() {
        if (batteryOptimizer == null || wakeLock == null) {
            return;
        }
        
        // 检查是否应该释放 WakeLock
        if (batteryOptimizer.shouldReleaseWakeLock() && !isWakeLockReleased) {
            releaseWakeLock();
        }
    }
    
    /**
     * 释放 WakeLock
     */
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            isWakeLockReleased = true;
            
            // 更新通知显示空闲状态
            updateNotificationWithBatteryStatus();
        }
    }
    
    /**
     * 获取 WakeLock
     */
    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
            isWakeLockReleased = false;
        }
    }
    
    /**
     * 更新通知显示电池状态
     */
    private void updateNotificationWithBatteryStatus() {
        // 使用自定义通知更新
        updateCustomNotification();
    }
    
    /**
     * 获取当前电池电量百分比
     * @return 电量百分比 (0-100)
     */
    public int getBatteryLevel() {
        return batteryOptimizer != null ? batteryOptimizer.getBatteryLevel() : 100;
    }
    
    /**
     * 是否处于低电量模式
     * @return 是否低电量模式
     */
    public boolean isLowPowerMode() {
        return batteryOptimizer != null && batteryOptimizer.isLowPowerMode();
    }
    
    /**
     * 是否正在充电
     * @return 是否充电中
     */
    public boolean isCharging() {
        return batteryOptimizer != null && batteryOptimizer.isCharging();
    }
    
    /**
     * 获取静止时长（分钟）
     * @return 静止时长
     */
    public int getIdleMinutes() {
        return batteryOptimizer != null ? batteryOptimizer.getIdleMinutes() : 0;
    }
    
    /**
     * 获取 WakeLock 是否已释放
     * @return 是否已释放
     */
    public boolean isWakeLockReleased() {
        return isWakeLockReleased;
    }
    
    /**
     * 获取当前采样模式描述
     * @return 采样模式描述
     */
    public String getSamplingModeText() {
        return batteryOptimizer != null ? batteryOptimizer.getSamplingModeText() : "高频采样";
    }
    
    /**
     * 获取当前速度 (km/h)
     * @return 当前速度
     */
    public float getCurrentSpeedKmh() {
        return currentSpeedKmh;
    }
    
    /**
     * 获取是否暂停状态
     * @return 是否暂停
     */
    public boolean isPaused() {
        return isPaused;
    }
}
