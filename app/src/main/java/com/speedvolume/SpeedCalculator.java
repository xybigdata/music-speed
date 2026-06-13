package com.speedvolume;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;

/**
 * 速度计算器
 * 融合加速度传感器和GPS数据，提供准确的速度估算
 */
public class SpeedCalculator implements SensorEventListener, LocationListener {
    // 滤波和融合参数
    private static final float ALPHA = 0.8f;           // 低通滤波系数（去重力）
    private static final float GPS_WEIGHT = 0.7f;     // GPS 权重
    private static final float ACCEL_WEIGHT = 0.3f;   // 加速度权重
    private static final float MAX_VALID_SPEED = 120.0f;  // 最大有效速度 (km/h)
    private static final float SMOOTH_FACTOR = 0.85f; // 速度平滑系数
    private static final float GRAVITY_THRESHOLD = 0.3f; // 重力检测阈值
    private static final long GPS_TIMEOUT = 5000;     // GPS 超时时间 (ms)
    
    // 传感器管理器
    private final SensorManager sensorManager;
    private final LocationManager locationManager;
    private final Context context;
    
    // 传感器状态
    private float[] gravity = new float[3];
    private float[] linearAcceleration = new float[3];
    private float accelerometerSpeed = 0;           // 加速度估算速度 (km/h)
    private float gpsSpeed = 0;                     // GPS 速度 (km/h)
    private float fusedSpeed = 0;                   // 融合速度 (km/h)
    private boolean gpsAvailable = false;
    private long lastGpsUpdateTime = 0;
    private long lastAccelUpdateTime = 0;
    
    // 积分估算相关变量
    private float velocityX = 0, velocityY = 0, velocityZ = 0;
    private long lastIntegrationTime = 0;
    
    // 回调接口
    private SpeedCallback callback;
    private final Handler handler;
    
    /**
     * 速度更新回调接口
     */
    public interface SpeedCallback {
        /**
         * 速度更新回调
         * @param speedKmh 速度 (km/h)
         * @param fromGps 是否来自GPS（true=GPS，false=加速度估算）
         */
        void onSpeedUpdated(float speedKmh, boolean fromGps);
        
        /**
         * GPS 状态变化回调
         * @param available GPS是否可用
         */
        void onGpsStatusChanged(boolean available);
    }
    
    /**
     * 构造函数
     * @param context 应用上下文
     */
    public SpeedCalculator(Context context) {
        this.context = context.getApplicationContext();
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * 设置速度回调
     * @param callback 回调接口
     */
    public void setSpeedCallback(SpeedCallback callback) {
        this.callback = callback;
    }
    
    // 当前传感器采样频率
    private int currentSampleRate = SensorManager.SENSOR_DELAY_UI;
    
    /**
     * 启动传感器监听
     * @return 是否成功启动
     */
    public boolean start() {
        // 启动加速度传感器
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, currentSampleRate);
        }
        
        // 启动GPS（如果有权限）
        if (hasLocationPermission()) {
            startLocationUpdates();
        }
        
        return accelerometer != null;
    }
    
    /**
     * 更新传感器采样频率
     * @param sampleRate 采样频率 (微秒)
     */
    public void updateSampleRate(int sampleRate) {
        if (sampleRate == currentSampleRate) {
            return;
        }
        
        currentSampleRate = sampleRate;
        
        // 重新注册传感器以应用新的采样频率
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.unregisterListener(this, accelerometer);
            sensorManager.registerListener(this, accelerometer, currentSampleRate);
        }
    }
    
    /**
     * 获取当前采样频率
     * @return 采样频率 (微秒)
     */
    public int getCurrentSampleRate() {
        return currentSampleRate;
    }
    
    /**
     * 停止传感器监听
     */
    public void stop() {
        sensorManager.unregisterListener(this);
        
        if (hasLocationPermission()) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException e) {
                // 忽略权限异常
            }
        }
        
        // 重置状态
        gpsAvailable = false;
        accelerometerSpeed = 0;
        fusedSpeed = 0;
        velocityX = velocityY = velocityZ = 0;
        lastIntegrationTime = 0;
    }
    
    /**
     * 获取融合后的速度
     * @return 速度 (km/h)
     */
    public float getFusedSpeed() {
        return fusedSpeed;
    }
    
    /**
     * 获取加速度估算速度
     * @return 速度 (km/h)
     */
    public float getAccelerometerSpeed() {
        return accelerometerSpeed;
    }
    
    /**
     * 获取GPS速度
     * @return 速度 (km/h)
     */
    public float getGpsSpeed() {
        return gpsSpeed;
    }
    
    /**
     * GPS是否可用
     * @return 是否可用
     */
    public boolean isGpsAvailable() {
        // 检查GPS是否超时
        if (gpsAvailable && (System.currentTimeMillis() - lastGpsUpdateTime) > GPS_TIMEOUT) {
            gpsAvailable = false;
            notifyGpsStatusChanged(false);
        }
        return gpsAvailable;
    }
    
    /**
     * 是否有位置权限
     * @return 是否有权限
     */
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context, 
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, 
            Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * 启动位置更新
     */
    private void startLocationUpdates() {
        if (!hasLocationPermission()) {
            return;
        }
        
        try {
            // 尝试使用GPS提供者
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,  // 1秒更新间隔
                    0,     // 最小距离
                    this,
                    Looper.getMainLooper()
                );
            }
            
            // 同时使用网络定位作为备用
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000,  // 2秒更新间隔
                    0,     // 最小距离
                    this,
                    Looper.getMainLooper()
                );
            }
        } catch (SecurityException e) {
            // 权限异常，忽略
        }
    }
    
    // ==================== SensorEventListener 实现 ====================
    
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // 低通滤波分离重力
        applyLowPassFilter(event.values);
        
        // 计算线性加速度（去除重力后）
        linearAcceleration[0] = event.values[0] - gravity[0];
        linearAcceleration[1] = event.values[1] - gravity[1];
        linearAcceleration[2] = event.values[2] - gravity[2];
        
        // 积分估算速度
        integrateAcceleration(currentTime);
        
        // 计算融合速度
        calculateFusedSpeed();
        
        // 通知速度更新
        notifySpeedUpdated();
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 精度变化时的处理（可选）
    }
    
    /**
     * 低通滤波分离重力
     * @param values 传感器原始值
     */
    private void applyLowPassFilter(float[] values) {
        gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * values[0];
        gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * values[1];
        gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * values[2];
    }
    
    /**
     * 积分加速度估算速度
     * @param currentTime 当前时间
     */
    private void integrateAcceleration(long currentTime) {
        if (lastIntegrationTime == 0) {
            lastIntegrationTime = currentTime;
            return;
        }
        
        float dt = (currentTime - lastIntegrationTime) / 1000.0f;  // 转换为秒
        lastIntegrationTime = currentTime;
        
        // 限制时间步长，避免过大的积分误差
        if (dt > 0.5f || dt <= 0) {
            return;
        }
        
        // 积分计算速度 (v = v0 + a * dt)
        velocityX += linearAcceleration[0] * dt;
        velocityY += linearAcceleration[1] * dt;
        velocityZ += linearAcceleration[2] * dt;
        
        // 计算速度标量 (m/s)
        float speedMps = (float) Math.sqrt(
            velocityX * velocityX + 
            velocityY * velocityY + 
            velocityZ * velocityZ
        );
        
        // 转换为 km/h
        float speedKmh = speedMps * 3.6f;
        
        // 速度衰减（模拟阻力，防止漂移）
        velocityX *= 0.98f;
        velocityY *= 0.98f;
        velocityZ *= 0.98f;
        
        // 速度平滑处理
        accelerometerSpeed = SMOOTH_FACTOR * accelerometerSpeed + (1 - SMOOTH_FACTOR) * speedKmh;
        
        // 过滤异常值
        if (accelerometerSpeed < 0) {
            accelerometerSpeed = 0;
        }
    }
    
    // ==================== LocationListener 实现 ====================
    
    @Override
    public void onLocationChanged(Location location) {
        if (location.hasSpeed()) {
            float speedMps = location.getSpeed();  // 米/秒
            float speedKmh = speedMps * 3.6f;     // 转换为 km/h
            
            // 有效性验证（过滤异常值）
            if (isValidGpsSpeed(speedKmh)) {
                gpsSpeed = speedKmh;
                lastGpsUpdateTime = System.currentTimeMillis();
                
                boolean wasAvailable = gpsAvailable;
                gpsAvailable = true;
                
                // 如果之前不可用，通知状态变化
                if (!wasAvailable) {
                    notifyGpsStatusChanged(true);
                }
                
                // 使用GPS速度校准加速度积分漂移
                calibrateAccelerometer(gpsSpeed);
            }
        }
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            if (status == LocationProvider.OUT_OF_SERVICE || 
                status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
                gpsAvailable = false;
                notifyGpsStatusChanged(false);
            }
        }
    }
    
    @Override
    public void onProviderEnabled(String provider) {
        // 提供者启用
    }
    
    @Override
    public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            gpsAvailable = false;
            notifyGpsStatusChanged(false);
        }
    }
    
    /**
     * 验证GPS速度是否有效
     * @param speed 速度 (km/h)
     * @return 是否有效
     */
    private boolean isValidGpsSpeed(float speed) {
        // 过滤异常值 (0 <= speed < 120 km/h)
        // 允许0值（静止状态）
        return speed >= 0 && speed < MAX_VALID_SPEED;
    }
    
    /**
     * 使用GPS速度校准加速度传感器估算
     * @param referenceSpeed 参考速度 (km/h)
     */
    private void calibrateAccelerometer(float referenceSpeed) {
        // 计算加速度估算的误差
        float error = accelerometerSpeed - referenceSpeed;
        
        // 如果误差过大，进行校准
        if (Math.abs(error) > 10.0f) {
            // 调整速度估计值
            accelerometerSpeed = accelerometerSpeed * 0.5f + referenceSpeed * 0.5f;
            
            // 调整积分速度分量
            float correctionFactor = referenceSpeed / (accelerometerSpeed + 0.001f);
            velocityX *= correctionFactor * 0.1f;
            velocityY *= correctionFactor * 0.1f;
            velocityZ *= correctionFactor * 0.1f;
        }
    }
    
    /**
     * 计算融合速度
     */
    private void calculateFusedSpeed() {
        float newFusedSpeed;
        
        if (isGpsAvailable() && gpsSpeed >= 0) {
            // GPS 可用：融合 GPS 和加速度数据
            newFusedSpeed = GPS_WEIGHT * gpsSpeed + ACCEL_WEIGHT * accelerometerSpeed;
        } else {
            // GPS 不可用：仅使用加速度估算
            newFusedSpeed = accelerometerSpeed;
        }
        
        // 平滑过渡，避免跳变
        fusedSpeed = SMOOTH_FACTOR * fusedSpeed + (1 - SMOOTH_FACTOR) * newFusedSpeed;
        
        // 确保速度非负
        if (fusedSpeed < 0) {
            fusedSpeed = 0;
        }
    }
    
    /**
     * 通知速度更新
     */
    private void notifySpeedUpdated() {
        if (callback != null) {
            final float speed = fusedSpeed;
            final boolean fromGps = isGpsAvailable();
            
            handler.post(() -> callback.onSpeedUpdated(speed, fromGps));
        }
    }
    
    /**
     * 通知GPS状态变化
     * @param available 是否可用
     */
    private void notifyGpsStatusChanged(boolean available) {
        if (callback != null) {
            handler.post(() -> callback.onGpsStatusChanged(available));
        }
    }
    
    /**
     * 重置速度估算状态
     */
    public void reset() {
        gravity = new float[3];
        linearAcceleration = new float[3];
        accelerometerSpeed = 0;
        gpsSpeed = 0;
        fusedSpeed = 0;
        velocityX = velocityY = velocityZ = 0;
        lastIntegrationTime = 0;
        lastGpsUpdateTime = 0;
        lastAccelUpdateTime = 0;
    }
}
