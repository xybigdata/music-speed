package com.speedvolume;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * 振动反馈助手类
 * 封装 Vibrator 操作，提供不同模式的振动反馈
 */
public class VibrationHelper {
    private final Vibrator vibrator;
    private boolean vibrationEnabled = true;
    
    // 振动模式常量
    /** 短振动 - 用于一般反馈 */
    public static final int MODE_SHORT = 1;
    /** 中振动 - 用于重要反馈 */
    public static final int MODE_MEDIUM = 2;
    /** 长振动 - 用于警告或特殊事件 */
    public static final int MODE_LONG = 3;
    /** 双击振动 - 用于方案切换 */
    public static final int MODE_DOUBLE = 4;
    /** 阈值振动 - 用于速度达到阈值 */
    public static final int MODE_THRESHOLD = 5;
    
    // 振动时长定义 (毫秒)
    private static final long DURATION_SHORT = 50;
    private static final long DURATION_MEDIUM = 100;
    private static final long DURATION_LONG = 200;
    
    // 双击振动模式: [振动时长, 间隔, 振动时长]
    private static final long[] DOUBLE_PATTERN = {0, DURATION_SHORT, 50, DURATION_SHORT};
    private static final int[] DOUBLE_AMPLITUDES = {0, 128, 0, 128};
    
    // 阈值振动模式: [振动时长, 间隔, 振动时长]
    private static final long[] THRESHOLD_PATTERN = {0, DURATION_MEDIUM, 100, DURATION_LONG};
    private static final int[] THRESHOLD_AMPLITUDES = {0, 180, 0, 255};
    
    public VibrationHelper(Context context) {
        // 获取 Vibrator 服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) 
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }
    
    /**
     * 设置振动是否启用
     * @param enabled 是否启用振动
     */
    public void setEnabled(boolean enabled) {
        this.vibrationEnabled = enabled;
    }
    
    /**
     * 检查振动是否启用
     * @return 是否启用振动
     */
    public boolean isEnabled() {
        return vibrationEnabled && hasVibrator();
    }
    
    /**
     * 检查设备是否有振动器
     * @return 是否有振动器
     */
    public boolean hasVibrator() {
        return vibrator != null && vibrator.hasVibrator();
    }
    
    /**
     * 执行振动
     * @param mode 振动模式 (MODE_SHORT, MODE_MEDIUM, MODE_LONG, MODE_DOUBLE, MODE_THRESHOLD)
     */
    public void vibrate(int mode) {
        if (!isEnabled()) {
            return;
        }
        
        switch (mode) {
            case MODE_SHORT:
                vibrateSingle(DURATION_SHORT, 80);
                break;
            case MODE_MEDIUM:
                vibrateSingle(DURATION_MEDIUM, 128);
                break;
            case MODE_LONG:
                vibrateSingle(DURATION_LONG, 200);
                break;
            case MODE_DOUBLE:
                vibratePattern(DOUBLE_PATTERN, DOUBLE_AMPLITUDES);
                break;
            case MODE_THRESHOLD:
                vibratePattern(THRESHOLD_PATTERN, THRESHOLD_AMPLITUDES);
                break;
            default:
                vibrateSingle(DURATION_SHORT, 80);
        }
    }
    
    /**
     * 执行单次振动
     * @param duration 振动时长 (毫秒)
     * @param amplitude 振动强度 (1-255, 仅 Android 8.0+)
     */
    private void vibrateSingle(long duration, int amplitude) {
        if (vibrator == null) {
            return;
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ 使用 VibrationEffect
                VibrationEffect effect = VibrationEffect.createOneShot(
                    duration, 
                    clampAmplitude(amplitude)
                );
                vibrator.vibrate(effect);
            } else {
                // 旧版本使用已弃用的方法
                vibrator.vibrate(duration);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 执行模式振动
     * @param pattern 振动模式数组 [等待, 振动, 等待, 振动, ...]
     * @param amplitudes 振动强度数组 (仅 Android 8.0+)
     */
    private void vibratePattern(long[] pattern, int[] amplitudes) {
        if (vibrator == null) {
            return;
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ 使用 VibrationEffect
                int[] safeAmplitudes = clampAmplitudes(amplitudes);
                VibrationEffect effect = VibrationEffect.createWaveform(
                    pattern, 
                    safeAmplitudes, 
                    -1  // 不重复
                );
                vibrator.vibrate(effect);
            } else {
                // 旧版本使用已弃用的方法
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 执行自定义模式振动
     * @param pattern 振动模式数组 [等待, 振动, 等待, 振动, ...]
     */
    public void vibratePattern(long[] pattern) {
        if (!isEnabled() || vibrator == null) {
            return;
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, -1);
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 取消振动
     */
    public void cancel() {
        if (vibrator != null) {
            vibrator.cancel();
        }
    }
    
    /**
     * 限制振动强度在有效范围内 (1-255)
     * @param amplitude 原始强度
     * @return 限制后的强度
     */
    private int clampAmplitude(int amplitude) {
        return Math.max(1, Math.min(255, amplitude));
    }
    
    /**
     * 限制振动强度数组在有效范围内
     * @param amplitudes 原始强度数组
     * @return 限制后的强度数组
     */
    private int[] clampAmplitudes(int[] amplitudes) {
        int[] result = new int[amplitudes.length];
        for (int i = 0; i < amplitudes.length; i++) {
            result[i] = clampAmplitude(amplitudes[i]);
        }
        return result;
    }
    
    /**
     * 方案切换振动反馈
     */
    public void vibrateProfileSwitch() {
        vibrate(MODE_DOUBLE);
    }
    
    /**
     * 速度阈值振动反馈
     */
    public void vibrateThreshold() {
        vibrate(MODE_THRESHOLD);
    }
    
    /**
     * 短振动反馈
     */
    public void vibrateShort() {
        vibrate(MODE_SHORT);
    }
    
    /**
     * 中振动反馈
     */
    public void vibrateMedium() {
        vibrate(MODE_MEDIUM);
    }
    
    /**
     * 长振动反馈
     */
    public void vibrateLong() {
        vibrate(MODE_LONG);
    }
}
