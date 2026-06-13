package com.speedvolume;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.PowerManager;

/**
 * 电量优化器
 * 智能管理传感器采样频率和 WakeLock，优化电池消耗
 */
public class BatteryOptimizer {
    private static final String TAG = "BatteryOptimizer";
    
    // 采样频率配置
    public static final int SAMPLE_RATE_HIGH = SensorManager.SENSOR_DELAY_UI;  // 高频率 (~60ms)
    public static final int SAMPLE_RATE_MEDIUM = 200000;  // 中频率 (200ms, 微秒)
    public static final int SAMPLE_RATE_LOW = 1000000;  // 低频率 (1秒, 微秒)
    
    // 电量阈值
    private static final int LOW_BATTERY_THRESHOLD = 20;  // 低电量阈值 (%)
    
    // 静止检测
    private static final long IDLE_THRESHOLD_MS = 5 * 60 * 1000;  // 静止阈值 (5分钟)
    private static final float MOVEMENT_THRESHOLD = 0.5f;  // 移动检测阈值 (km/h)
    
    // 速度变化率阈值
    private static final float SPEED_VARIATION_HIGH = 5.0f;  // 高变化率阈值 (km/h)
    private static final float SPEED_VARIATION_MEDIUM = 2.0f;  // 中变化率阈值 (km/h)
    
    // 上下文
    private final Context context;
    private final PowerManager powerManager;
    private final BatteryManager batteryManager;
    
    // 状态
    private boolean isLowPowerMode = false;
    private boolean isCharging = false;
    private int batteryLevel = 100;
    private long lastMovementTime = 0;
    private float lastSpeed = 0;
    private float speedVariation = 0;
    private int currentSampleRate = SAMPLE_RATE_HIGH;
    
    // 广播接收器
    private BroadcastReceiver batteryReceiver;
    private boolean isRegistered = false;
    
    // 回调接口
    public interface BatteryOptimizationListener {
        /**
         * 采样频率变化回调
         * @param sampleRate 新的采样频率 (微秒)
         */
        void onSampleRateChanged(int sampleRate);
        
        /**
         * 低电量模式变化回调
         * @param enabled 是否启用低电量模式
         */
        void onLowPowerModeChanged(boolean enabled);
        
        /**
         * 静止检测回调
         * @param idleMinutes 静止时长 (分钟)
         */
        void onIdleDetected(int idleMinutes);
        
        /**
         * 充电状态变化回调
         * @param isCharging 是否充电中
         */
        void onChargingStatusChanged(boolean isCharging);
    }
    
    private BatteryOptimizationListener listener;
    
    /**
     * 构造函数
     * @param context 应用上下文
     */
    public BatteryOptimizer(Context context) {
        this.context = context.getApplicationContext();
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        
        // 初始化状态
        initializeState();
        
        // 注册电池状态监听
        registerBatteryReceiver();
    }
    
    /**
     * 初始化状态
     */
    private void initializeState() {
        // 获取初始电池状态
        if (batteryManager != null) {
            batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            isCharging = isCurrentlyCharging();
        }
        
        lastMovementTime = System.currentTimeMillis();
        
        // 根据初始电量决定是否进入低电量模式
        if (batteryLevel < LOW_BATTERY_THRESHOLD) {
            isLowPowerMode = true;
            currentSampleRate = SAMPLE_RATE_LOW;
        }
    }
    
    /**
     * 注册电池状态广播接收器
     */
    private void registerBatteryReceiver() {
        if (isRegistered) return;
        
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    handleBatteryChanged(intent);
                }
            }
        };
        
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(batteryReceiver, filter);
        isRegistered = true;
    }
    
    /**
     * 处理电池状态变化
     * @param intent 电池状态意图
     */
    private void handleBatteryChanged(Intent intent) {
        // 获取电池电量
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        
        if (level >= 0 && scale > 0) {
            int newBatteryLevel = (int) ((level / (float) scale) * 100);
            
            // 检查电量是否变化
            if (newBatteryLevel != batteryLevel) {
                batteryLevel = newBatteryLevel;
                checkLowPowerMode();
            }
        }
        
        // 获取充电状态
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean wasCharging = isCharging;
        isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || 
                      status == BatteryManager.BATTERY_STATUS_FULL);
        
        // 充电状态变化通知
        if (isCharging != wasCharging) {
            notifyChargingStatusChanged(isCharging);
            
            // 充电时退出低电量模式
            if (isCharging && isLowPowerMode) {
                isLowPowerMode = false;
                notifyLowPowerModeChanged(false);
            }
        }
    }
    
    /**
     * 检查是否需要切换低电量模式
     */
    private void checkLowPowerMode() {
        boolean shouldLowPower = (batteryLevel < LOW_BATTERY_THRESHOLD) && !isCharging;
        
        if (shouldLowPower != isLowPowerMode) {
            isLowPowerMode = shouldLowPower;
            notifyLowPowerModeChanged(shouldLowPower);
        }
    }
    
    /**
     * 设置监听器
     * @param listener 监听器
     */
    public void setListener(BatteryOptimizationListener listener) {
        this.listener = listener;
    }
    
    /**
     * 更新速度（用于计算速度变化率和静止检测）
     * 应在每次速度更新时调用
     * @param speedKmh 当前速度 (km/h)
     */
    public void updateSpeed(float speedKmh) {
        // 计算速度变化率
        speedVariation = Math.abs(speedKmh - lastSpeed);
        lastSpeed = speedKmh;
        
        // 检测移动
        if (speedKmh > MOVEMENT_THRESHOLD) {
            lastMovementTime = System.currentTimeMillis();
        }
        
        // 根据状态更新采样频率
        updateSampleRate();
    }
    
    /**
     * 更新采样频率
     */
    private void updateSampleRate() {
        int newSampleRate = calculateOptimalSampleRate();
        
        if (newSampleRate != currentSampleRate) {
            currentSampleRate = newSampleRate;
            notifySampleRateChanged(currentSampleRate);
        }
    }
    
    /**
     * 计算最优采样频率
     * @return 采样频率 (微秒)
     */
    private int calculateOptimalSampleRate() {
        // 低电量模式：使用最低频率
        if (isLowPowerMode) {
            return SAMPLE_RATE_LOW;
        }
        
        // 充电时使用高频率
        if (isCharging) {
            return SAMPLE_RATE_HIGH;
        }
        
        // 根据速度变化率调整
        if (speedVariation > SPEED_VARIATION_HIGH) {
            // 高变化率：使用高频率
            return SAMPLE_RATE_HIGH;
        } else if (speedVariation > SPEED_VARIATION_MEDIUM) {
            // 中变化率：使用中频率
            return SAMPLE_RATE_MEDIUM;
        } else {
            // 低变化率：使用低频率
            return SAMPLE_RATE_LOW;
        }
    }
    
    /**
     * 获取最优采样频率
     * @return 采样频率 (微秒)
     */
    public int getOptimalSampleRate() {
        return currentSampleRate;
    }
    
    /**
     * 是否应该释放 WakeLock
     * 静止超过5分钟且未充电时返回true
     * @return 是否应该释放
     */
    public boolean shouldReleaseWakeLock() {
        // 充电中不释放
        if (isCharging) {
            return false;
        }
        
        // 低电量模式也不释放（保持最低限度运行）
        if (isLowPowerMode) {
            return false;
        }
        
        // 检查静止时间
        long currentTime = System.currentTimeMillis();
        long idleTime = currentTime - lastMovementTime;
        
        return idleTime > IDLE_THRESHOLD_MS;
    }
    
    /**
     * 获取静止时长（分钟）
     * @return 静止时长
     */
    public int getIdleMinutes() {
        long idleTime = System.currentTimeMillis() - lastMovementTime;
        return (int) (idleTime / (60 * 1000));
    }
    
    /**
     * 检查是否处于静止状态
     * @return 是否静止
     */
    public boolean isIdle() {
        return getIdleMinutes() > 0 && lastSpeed <= MOVEMENT_THRESHOLD;
    }
    
    /**
     * 获取电池电量百分比
     * @return 电量百分比 (0-100)
     */
    public int getBatteryLevel() {
        return batteryLevel;
    }
    
    /**
     * 是否正在充电
     * @return 是否充电中
     */
    public boolean isCharging() {
        return isCharging;
    }
    
    /**
     * 是否处于低电量模式
     * @return 是否低电量模式
     */
    public boolean isLowPowerMode() {
        return isLowPowerMode;
    }
    
    /**
     * 获取当前速度变化率
     * @return 速度变化率 (km/h)
     */
    public float getSpeedVariation() {
        return speedVariation;
    }
    
    /**
     * 手动启用低电量模式
     */
    public void enableLowPowerMode() {
        if (!isLowPowerMode) {
            isLowPowerMode = true;
            currentSampleRate = SAMPLE_RATE_LOW;
            notifyLowPowerModeChanged(true);
            notifySampleRateChanged(currentSampleRate);
        }
    }
    
    /**
     * 手动禁用低电量模式
     */
    public void disableLowPowerMode() {
        if (isLowPowerMode && !shouldStayInLowPowerMode()) {
            isLowPowerMode = false;
            updateSampleRate();
            notifyLowPowerModeChanged(false);
        }
    }
    
    /**
     * 检查是否应该保持在低电量模式
     * @return 是否应该保持
     */
    private boolean shouldStayInLowPowerMode() {
        return batteryLevel < LOW_BATTERY_THRESHOLD && !isCharging;
    }
    
    /**
     * 获取当前是否充电中（内部方法）
     * @return 是否充电中
     */
    private boolean isCurrentlyCharging() {
        if (batteryManager == null) return false;
        
        int status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
        return status == BatteryManager.BATTERY_STATUS_CHARGING || 
               status == BatteryManager.BATTERY_STATUS_FULL;
    }
    
    /**
     * 获取电量状态描述
     * @return 状态描述字符串
     */
    public String getBatteryStatusText() {
        StringBuilder sb = new StringBuilder();
        
        if (isCharging) {
            sb.append("充电中 ");
        }
        
        sb.append(batteryLevel).append("%");
        
        if (isLowPowerMode) {
            sb.append(" (省电模式)");
        }
        
        return sb.toString();
    }
    
    /**
     * 获取采样模式描述
     * @return 模式描述
     */
    public String getSamplingModeText() {
        if (currentSampleRate == SAMPLE_RATE_HIGH) {
            return "高频采样";
        } else if (currentSampleRate == SAMPLE_RATE_MEDIUM) {
            return "中频采样";
        } else {
            return "低频采样";
        }
    }
    
    /**
     * 重置静止计时器
     * 当检测到移动时调用
     */
    public void resetIdleTimer() {
        lastMovementTime = System.currentTimeMillis();
    }
    
    /**
     * 停止电量优化器
     */
    public void stop() {
        if (isRegistered && batteryReceiver != null) {
            try {
                context.unregisterReceiver(batteryReceiver);
            } catch (Exception e) {
                // 忽略未注册异常
            }
            isRegistered = false;
        }
    }
    
    // ==================== 通知方法 ====================
    
    private void notifySampleRateChanged(int sampleRate) {
        if (listener != null) {
            listener.onSampleRateChanged(sampleRate);
        }
    }
    
    private void notifyLowPowerModeChanged(boolean enabled) {
        if (listener != null) {
            listener.onLowPowerModeChanged(enabled);
        }
    }
    
    private void notifyIdleDetected(int idleMinutes) {
        if (listener != null) {
            listener.onIdleDetected(idleMinutes);
        }
    }
    
    private void notifyChargingStatusChanged(boolean isCharging) {
        if (listener != null) {
            listener.onChargingStatusChanged(isCharging);
        }
    }
}
