# 音速骑士 - 技术设计文档

## 设计概览

本文档基于需求文档，详细说明音速骑士应用的技术架构和实现方案。设计遵循 Android 最佳实践，重点优化速度检测准确性、电量消耗和用户体验。

---

## 系统架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ MainActivity│  │ConfigActivity│  │Profile2ConfigAct │   │
│  └──────┬──────┘  └──────┬───────┘  └────────┬─────────┘   │
│         │                │                    │              │
│         └────────────────┴────────────────────┘              │
│                          │                                   │
└──────────────────────────┼───────────────────────────────────┘
                           │
┌──────────────────────────┼───────────────────────────────────┐
│                    Service Layer                             │
│                          │                                   │
│  ┌───────────────────────▼──────────────────────────────┐   │
│  │              SpeedVolumeService                       │   │
│  │  ┌────────────────┐  ┌──────────────────────────┐   │   │
│  │  │ SensorManager  │  │ SpeedCalculator          │   │   │
│  │  │ (Accelerometer)│  │ (GPS + Accelerometer)    │   │   │
│  │  └────────────────┘  └──────────────────────────┘   │   │
│  │  ┌────────────────┐  ┌──────────────────────────┐   │   │
│  │  │ LocationManager│  │ VolumeController         │   │   │
│  │  │ (GPS)          │  │ (AudioManager)           │   │   │
│  │  └────────────────┘  └──────────────────────────┘   │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                               │
└───────────────────────────────────────────────────────────────┘
                           │
┌──────────────────────────┼───────────────────────────────────┐
│                    Data Layer                                 │
│                          │                                   │
│  ┌───────────────────────▼──────────────────────────────┐   │
│  │            SpeedVolumeConfig                          │   │
│  │  ┌────────────────┐  ┌──────────────────────────┐   │   │
│  │  │ SharedPreferences│  │ Profile Manager         │   │   │
│  │  │ (Config Data)   │  │ (Speed-Volume Mapping)  │   │   │
│  │  └────────────────┘  └──────────────────────────┘   │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐   │
│  │            UsageStatsRepository (新增)                 │   │
│  │  ┌────────────────┐  ┌──────────────────────────┐   │   │
│  │  │ SQLite Database│  │ Statistics Manager       │   │   │
│  │  │ (History Data) │  │ (Usage Tracking)         │   │   │
│  │  └────────────────┘  └──────────────────────────┘   │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

### 模块职责

| 模块 | 职责 | 关键类 |
|------|------|--------|
| UI Layer | 用户界面交互、状态展示 | MainActivity, ConfigActivity, Profile2ConfigActivity |
| Service Layer | 后台服务、传感器管理、速度计算、音量控制 | SpeedVolumeService, SpeedCalculator, VolumeController |
| Data Layer | 配置管理、数据持久化、统计记录 | SpeedVolumeConfig, UsageStatsRepository |

---

## 核心模块设计

### 1. SpeedCalculator (新增) - 速度计算器

**职责**: 融合加速度传感器和 GPS 数据，提供准确的速度估算

#### 类设计

```java
public class SpeedCalculator {
    // 传感器数据源
    private SensorManager sensorManager;
    private LocationManager locationManager;
    
    // 算法参数
    private static final float ALPHA = 0.8f;  // 低通滤波系数
    private static final float GPS_WEIGHT = 0.7f;  // GPS 权重
    private static final float ACCEL_WEIGHT = 0.3f;  // 加速度权重
    
    // 状态
    private float[] gravity = new float[3];
    private float accelerometerSpeed = 0;  // 加速度估算速度
    private float gpsSpeed = 0;  // GPS 速度
    private boolean gpsAvailable = false;
    
    // 回调接口
    public interface SpeedCallback {
        void onSpeedUpdated(float speedKmh, boolean fromGps);
    }
    
    // 核心方法
    public void start();  // 启动传感器监听
    public void stop();  // 停止传感器监听
    public float getFusedSpeed();  // 获取融合后的速度
    public boolean isGpsAvailable();  // GPS 是否可用
}
```

#### 算法流程

```
加速度传感器数据 → 低通滤波去重力 → 积分估算速度 → 平滑处理
                                              ↓
GPS 速度数据 → 有效性验证 → 异常值过滤 → 速度范围校验
                                              ↓
                                    传感器融合 (加权平均)
                                              ↓
                                    输出融合速度
```

#### GPS 校准逻辑

```java
// 伪代码
float getFusedSpeed() {
    if (gpsAvailable && gpsSpeed > 0) {
        // GPS 可用：融合 GPS 和加速度数据
        return GPS_WEIGHT * gpsSpeed + ACCEL_WEIGHT * accelerometerSpeed;
    } else {
        // GPS 不可用：仅使用加速度估算
        return accelerometerSpeed;
    }
}

// GPS 速度验证
boolean isValidGpsSpeed(float speed) {
    // 过滤异常值 (0 < speed < 120 km/h)
    return speed > 0 && speed < 120;
}
```

---

### 2. VolumeController (新增) - 音量控制器

**职责**: 封装音量调节逻辑，支持平滑过渡和限制

#### 类设计

```java
public class VolumeController {
    private AudioManager audioManager;
    private int maxVolume;
    private int minVolumeLimit = 0;  // 最小音量限制
    private int maxVolumeLimit = 100;  // 最大音量限制
    private int currentTargetVolume = -1;
    
    // 平滑过渡
    private Handler handler;
    private static final int SMOOTH_STEP = 2;  // 每次调整步长
    private static final int SMOOTH_DELAY = 50;  // 调整间隔(ms)
    
    // 核心方法
    public void setVolume(int volumePercent);  // 设置目标音量
    public void setVolumeLimits(int min, int max);  // 设置音量限制
    public int getCurrentVolume();  // 获取当前音量
    public void applyImmediately();  // 立即应用（无平滑）
}
```

#### 平滑过渡算法

```java
void smoothTransition(int targetVolume) {
    int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    
    if (Math.abs(targetVolume - currentVolume) <= SMOOTH_STEP) {
        // 差距小，直接设置
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
        return;
    }
    
    // 逐步调整
    int step = targetVolume > currentVolume ? SMOOTH_STEP : -SMOOTH_STEP;
    int newVolume = currentVolume + step;
    
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
    
    // 延迟继续调整
    handler.postDelayed(() -> smoothTransition(targetVolume), SMOOTH_DELAY);
}
```

---

### 3. PermissionHelper (新增) - 权限管理助手

**职责**: 统一管理权限请求和状态检查

#### 类设计

```java
public class PermissionHelper {
    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    
    // 必需权限列表
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.POST_NOTIFICATIONS,  // Android 13+
        Manifest.permission.ACCESS_FINE_LOCATION,  // GPS 速度检测
        Manifest.permission.ACCESS_COARSE_LOCATION
    };
    
    // 核心方法
    public boolean hasAllPermissions(Activity activity);  // 检查权限
    public void requestPermissions(Activity activity);  // 请求权限
    public boolean shouldShowRationale(Activity activity);  // 是否需要说明
    public void openSettings(Activity activity);  // 打开设置页面
    public boolean hasLocationPermission(Context context);  // 检查位置权限
    public boolean hasNotificationPermission(Context context);  // 检查通知权限
}
```

#### 权限请求流程

```
启动服务 → 检查权限
              ↓
        权限完整？ ─Yes─→ 启动服务
              ↓No
        显示说明对话框
              ↓
        请求权限
              ↓
        用户选择 ─拒绝─→ 显示引导界面
              ↓允许
        启动服务
```

---

### 4. BatteryOptimizer (新增) - 电量优化器

**职责**: 智能管理传感器采样频率和 WakeLock

#### 类设计

```java
public class BatteryOptimizer {
    private PowerManager powerManager;
    private BatteryManager batteryManager;
    
    // 采样频率配置
    private static final int SAMPLE_RATE_HIGH = SensorManager.SENSOR_DELAY_UI;  // 高频率
    private static final int SAMPLE_RATE_LOW = 1000000;  // 1秒 (微秒)
    
    // 状态
    private boolean isLowPowerMode = false;
    private long lastMovementTime = 0;
    private static final long IDLE_THRESHOLD = 5 * 60 * 1000;  // 5分钟
    
    // 核心方法
    public int getOptimalSampleRate(float speedVariation);  // 获取最优采样率
    public boolean shouldReleaseWakeLock();  // 是否应释放 WakeLock
    public void enableLowPowerMode();  // 启用省电模式
    public void disableLowPowerMode();  // 禁用省电模式
    public int getBatteryLevel();  // 获取电量百分比
    public boolean isCharging();  // 是否充电中
}
```

#### 智能采样策略

```
监测速度变化率
      ↓
变化率 > 阈值 ─Yes─→ 高频率采样 (SENSOR_DELAY_UI)
      ↓No
变化率平缓 ────→ 低频率采样 (1秒间隔)
      ↓
速度 = 0 超过 5分钟 → 释放 WakeLock，进入待机模式
      ↓
检测到移动 → 重新获取 WakeLock，恢复高频率采样
```

---

### 5. UsageStatsRepository (新增) - 使用统计仓库

**职责**: 记录和查询使用统计数据

#### 数据库设计

**SQLite Schema:**

```sql
-- 骑行记录表
CREATE TABLE ride_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    start_time INTEGER NOT NULL,  -- 开始时间戳
    end_time INTEGER,  -- 结束时间戳
    distance REAL,  -- 距离(km)
    max_speed REAL,  -- 最高速度(km/h)
    avg_speed REAL,  -- 平均速度(km/h)
    duration INTEGER  -- 时长(秒)
);

-- 速度快照表
CREATE TABLE speed_snapshots (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL,
    timestamp INTEGER NOT NULL,  -- 时间戳
    speed REAL NOT NULL,  -- 速度(km/h)
    volume INTEGER NOT NULL,  -- 音量(%)
    latitude REAL,  -- 纬度
    longitude REAL,  -- 经度
    FOREIGN KEY (session_id) REFERENCES ride_sessions(id)
);
```

#### 类设计

```java
public class UsageStatsRepository {
    private SQLiteDatabase database;
    
    // 核心方法
    public long startSession();  // 开始新会话
    public void endSession(long sessionId, RideStats stats);  // 结束会话
    public void addSnapshot(long sessionId, SpeedSnapshot snapshot);  // 添加快照
    public List<RideSession> getRecentSessions(int limit);  // 获取最近会话
    public RideStats getTotalStats();  // 获取总统计
    public void exportToCsv(File file);  // 导出为 CSV
}
```

---

## 关键技术实现

### 1. 权限管理实现

#### AndroidManifest.xml 更新

```xml
<!-- 现有权限 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

<!-- 新增权限 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

#### 动态权限请求

```java
// MainActivity.java
private void checkAndRequestPermissions() {
    if (!permissionHelper.hasAllPermissions(this)) {
        if (permissionHelper.shouldShowRationale(this)) {
            // 显示权限说明
            showPermissionRationaleDialog();
        } else {
            // 直接请求权限
            permissionHelper.requestPermissions(this);
        }
    } else {
        // 权限已授予，启动服务
        startSpeedVolumeService();
    }
}

@Override
public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    
    if (requestCode == REQUEST_CODE_PERMISSIONS) {
        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        
        if (allGranted) {
            startSpeedVolumeService();
        } else {
            showPermissionDeniedDialog();
        }
    }
}
```

---

### 2. GPS 速度检测实现

#### LocationListener 配置

```java
// SpeedCalculator.java
private void setupLocationListener() {
    LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (location.hasSpeed()) {
                float speedMps = location.getSpeed();  // 米/秒
                float speedKmh = speedMps * 3.6f;  // 转换为 km/h
                
                if (isValidGpsSpeed(speedKmh)) {
                    gpsSpeed = speedKmh;
                    gpsAvailable = true;
                    
                    // 通知速度更新
                    if (callback != null) {
                        callback.onSpeedUpdated(getFusedSpeed(), true);
                    }
                }
            }
        }
        
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (status == LocationProvider.OUT_OF_SERVICE) {
                gpsAvailable = false;
            }
        }
    };
    
    // 请求位置更新
    locationManager.requestLocationUpdates(
        LocationManager.GPS_PROVIDER,
        1000,  // 1秒更新间隔
        0,  // 最小距离
        locationListener,
        Looper.getMainLooper()
    );
}
```

---

### 3. 电量优化实现

#### 智能采样频率调整

```java
// BatteryOptimizer.java
public int getOptimalSampleRate(float speedVariation) {
    // 低电量模式
    if (isLowPowerMode || getBatteryLevel() < 20) {
        return SAMPLE_RATE_LOW;
    }
    
    // 速度变化剧烈时使用高频率
    if (speedVariation > 5.0f) {  // 变化 > 5 km/h
        return SAMPLE_RATE_HIGH;
    }
    
    // 平稳状态使用低频率
    return SAMPLE_RATE_LOW;
}

public boolean shouldReleaseWakeLock() {
    // 充电中不释放
    if (isCharging()) {
        return false;
    }
    
    // 检查静止时间
    long currentTime = System.currentTimeMillis();
    return (currentTime - lastMovementTime) > IDLE_THRESHOLD;
}
```

---

### 4. 服务稳定性实现

#### 前台服务配置

```xml
<!-- AndroidManifest.xml -->
<service
    android:name=".SpeedVolumeService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="location|dataSync" />
```

#### 服务重启机制

```java
// SpeedVolumeService.java
@Override
public int onStartCommand(Intent intent, int flags, int startId) {
    // 确保服务被杀死后自动重启
    return START_STICKY;
}

@Override
public void onTaskRemoved(Intent rootIntent) {
    // 用户从最近任务中移除应用时重启服务
    Intent restartIntent = new Intent(this, SpeedVolumeService.class);
    PendingIntent pendingIntent = PendingIntent.getService(
        this, 1, restartIntent, PendingIntent.FLAG_IMMUTABLE
    );
    
    AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
    alarmManager.set(
        AlarmManager.ELAPSED_REALTIME,
        SystemClock.elapsedRealtime() + 1000,
        pendingIntent
    );
    
    super.onTaskRemoved(rootIntent);
}
```

---

## 数据流设计

### 速度-音量调节流程

```
用户移动手机
      ↓
加速度传感器检测 → 低通滤波 → 积分估算速度
      ↓
GPS 获取速度 → 有效性验证
      ↓
传感器融合 → 计算最终速度
      ↓
配置查找 → 获取目标音量百分比
      ↓
音量限制检查 → 确保在 [minLimit, maxLimit] 范围内
      ↓
平滑过渡算法 → 逐步调整音量
      ↓
AudioManager.setStreamVolume()
      ↓
更新 UI 显示
```

### 配置更新流程

```
用户编辑配置
      ↓
ConfigActivity 收集输入
      ↓
验证输入有效性
      ↓
更新 SpeedVolumeConfig 对象
      ↓
保存到 SharedPreferences
      ↓
发送广播 "com.speedvolume.CONFIG_CHANGED"
      ↓
SpeedVolumeService 接收广播
      ↓
重新加载配置
      ↓
应用新配置
```

---

## 性能优化策略

### 1. 内存优化
- 避免在循环中创建对象
- 使用对象池复用对象
- 及时释放资源（传感器、LocationManager）
- 使用 WeakReference 持有 Activity 引用

### 2. 电量优化
- 动态调整传感器采样频率
- 智能管理 WakeLock
- 避免频繁的 GPS 更新
- 使用 JobScheduler 进行后台任务

### 3. 响应速度优化
- 使用 HandlerThread 处理传感器数据
- 异步保存配置数据
- 缓存常用计算结果

---

## 兼容性设计

### Android 版本适配

| Android 版本 | 适配要点 |
|-------------|---------|
| 7.0-9.0 (API 24-28) | 基础功能，无需特殊适配 |
| 10 (API 29) | 后台位置权限需要分步请求 |
| 11+ (API 30+) | 强制分区存储，需要适配 |
| 12+ (API 31+) | 前台服务类型必须声明 |
| 13+ (API 33+) | 通知权限需要动态请求 |

### 屏幕适配
- 使用 ConstraintLayout 布局
- 支持横竖屏切换
- 适配不同屏幕密度

---

## 测试策略

### 单元测试
- SpeedCalculator: 速度计算算法测试
- VolumeController: 音量控制逻辑测试
- SpeedVolumeConfig: 配置保存/加载测试

### 集成测试
- 权限请求流程测试
- 服务启停测试
- 配置更新测试

### UI 测试
- Espresso UI 自动化测试
- 权限对话框测试
- 配置编辑测试

### 性能测试
- 电量消耗测试
- 内存占用测试
- 启动速度测试

---

## 部署说明

### 构建配置
- minSdkVersion: 24 (Android 7.0)
- targetSdkVersion: 34 (Android 14)
- 编译 SDK: 34

### 签名配置
- Debug 签名：使用默认 debug keystore
- Release 签名：使用独立 keystore

### 混淆配置
- 启用 ProGuard
- 保留关键类：@Keep 注解

---

## 下一步：任务分解

基于本设计文档，将创建详细的实现任务列表，按优先级和依赖关系组织。
