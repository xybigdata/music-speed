package com.speedvolume;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

/**
 * 音量控制器
 * 封装音量调节逻辑，支持平滑过渡和音量限制
 */
public class VolumeController {
    private static final String TAG = "VolumeController";
    
    private final AudioManager audioManager;
    private final Handler handler;
    private final int maxSystemVolume;
    
    // 音量限制
    private int minVolumeLimit = 0;  // 最小音量限制 (百分比)
    private int maxVolumeLimit = 100;  // 最大音量限制 (百分比)
    
    // 平滑过渡配置
    private static final int DEFAULT_SMOOTH_STEP = 2;  // 每次调整步长 (百分比)
    private static final int DEFAULT_SMOOTH_DELAY_MS = 50;  // 调整间隔 (毫秒)
    private int smoothStep = DEFAULT_SMOOTH_STEP;
    private int smoothDelayMs = DEFAULT_SMOOTH_DELAY_MS;
    
    // 当前状态
    private int currentTargetVolume = -1;  // 目标音量 (系统音量值)
    private boolean isTransitioning = false;
    private boolean smoothTransitionEnabled = true;
    
    // 回调接口
    public interface VolumeChangeListener {
        void onVolumeChanged(int volumePercent);
    }
    private VolumeChangeListener volumeChangeListener;
    
    // 平滑过渡 Runnable
    private final Runnable smoothTransitionRunnable = new Runnable() {
        @Override
        public void run() {
            performSmoothTransitionStep();
        }
    };
    
    /**
     * 构造函数
     * @param context 应用上下文
     */
    public VolumeController(Context context) {
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
        this.maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }
    
    /**
     * 设置音量 (百分比)
     * @param volumePercent 音量百分比 (0-100)
     */
    public void setVolume(int volumePercent) {
        setVolume(volumePercent, smoothTransitionEnabled);
    }
    
    /**
     * 设置音量 (百分比，可选择是否平滑过渡)
     * @param volumePercent 音量百分比 (0-100)
     * @param smooth 是否使用平滑过渡
     */
    public void setVolume(int volumePercent, boolean smooth) {
        // 应用音量限制
        int limitedVolume = applyVolumeLimits(volumePercent);
        
        // 转换为系统音量值
        int targetSystemVolume = percentToSystemVolume(limitedVolume);
        
        // 如果目标音量相同，不做处理
        if (targetSystemVolume == currentTargetVolume && isTransitioning == false) {
            return;
        }
        
        currentTargetVolume = targetSystemVolume;
        
        if (smooth && smoothTransitionEnabled) {
            startSmoothTransition(targetSystemVolume);
        } else {
            applyVolumeImmediately(targetSystemVolume);
        }
    }
    
    /**
     * 设置音量限制
     * @param min 最小音量百分比 (0-100)
     * @param max 最大音量百分比 (0-100)
     */
    public void setVolumeLimits(int min, int max) {
        this.minVolumeLimit = Math.max(0, Math.min(100, min));
        this.maxVolumeLimit = Math.max(0, Math.min(100, max));
        
        // 确保 min <= max
        if (minVolumeLimit > maxVolumeLimit) {
            int temp = minVolumeLimit;
            minVolumeLimit = maxVolumeLimit;
            maxVolumeLimit = temp;
        }
    }
    
    /**
     * 获取最小音量限制
     * @return 最小音量百分比
     */
    public int getMinVolumeLimit() {
        return minVolumeLimit;
    }
    
    /**
     * 获取最大音量限制
     * @return 最大音量百分比
     */
    public int getMaxVolumeLimit() {
        return maxVolumeLimit;
    }
    
    /**
     * 获取当前音量 (百分比)
     * @return 当前音量百分比 (0-100)
     */
    public int getCurrentVolume() {
        int currentSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        return systemVolumeToPercent(currentSystemVolume);
    }
    
    /**
     * 获取当前系统音量值
     * @return 当前系统音量值
     */
    public int getCurrentSystemVolume() {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }
    
    /**
     * 获取系统最大音量值
     * @return 系统最大音量值
     */
    public int getMaxSystemVolume() {
        return maxSystemVolume;
    }
    
    /**
     * 立即应用音量（无平滑）
     */
    public void applyImmediately() {
        if (currentTargetVolume >= 0) {
            stopSmoothTransition();
            applyVolumeImmediately(currentTargetVolume);
        }
    }
    
    /**
     * 设置是否启用平滑过渡
     * @param enabled 是否启用
     */
    public void setSmoothTransitionEnabled(boolean enabled) {
        this.smoothTransitionEnabled = enabled;
    }
    
    /**
     * 设置平滑过渡步长
     * @param step 每次调整步长 (百分比)
     */
    public void setSmoothStep(int step) {
        this.smoothStep = Math.max(1, Math.min(10, step));
    }
    
    /**
     * 设置平滑过渡间隔
     * @param delayMs 调整间隔 (毫秒)
     */
    public void setSmoothDelay(int delayMs) {
        this.smoothDelayMs = Math.max(10, Math.min(500, delayMs));
    }
    
    /**
     * 设置音量变化监听器
     * @param listener 监听器
     */
    public void setVolumeChangeListener(VolumeChangeListener listener) {
        this.volumeChangeListener = listener;
    }
    
    /**
     * 停止所有操作
     */
    public void stop() {
        stopSmoothTransition();
    }
    
    /**
     * 应用音量限制
     * @param volumePercent 原始音量百分比
     * @return 限制后的音量百分比
     */
    private int applyVolumeLimits(int volumePercent) {
        if (volumePercent < minVolumeLimit) {
            return minVolumeLimit;
        }
        if (volumePercent > maxVolumeLimit) {
            return maxVolumeLimit;
        }
        return volumePercent;
    }
    
    /**
     * 百分比转系统音量值
     * @param percent 百分比 (0-100)
     * @return 系统音量值
     */
    private int percentToSystemVolume(int percent) {
        return Math.round((float) percent * maxSystemVolume / 100f);
    }
    
    /**
     * 系统音量值转百分比
     * @param systemVolume 系统音量值
     * @return 百分比 (0-100)
     */
    private int systemVolumeToPercent(int systemVolume) {
        if (maxSystemVolume == 0) return 0;
        return Math.round((float) systemVolume * 100f / maxSystemVolume);
    }
    
    /**
     * 开始平滑过渡
     * @param targetVolume 目标系统音量值
     */
    private void startSmoothTransition(int targetVolume) {
        isTransitioning = true;
        performSmoothTransitionStep();
    }
    
    /**
     * 执行一步平滑过渡
     */
    private void performSmoothTransitionStep() {
        if (currentTargetVolume < 0) {
            isTransitioning = false;
            return;
        }
        
        try {
            int currentSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            
            // 计算差值
            int difference = currentTargetVolume - currentSystemVolume;
            
            if (Math.abs(difference) <= smoothStep) {
                // 差距小于步长，直接设置目标值
                applyVolumeImmediately(currentTargetVolume);
                isTransitioning = false;
                return;
            }
            
            // 计算下一步音量
            int step = difference > 0 ? smoothStep : -smoothStep;
            int newVolume = currentSystemVolume + step;
            
            // 应用音量
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
            
            // 通知监听器
            notifyVolumeChanged(newVolume);
            
            // 继续下一步
            handler.postDelayed(smoothTransitionRunnable, smoothDelayMs);
        } catch (Exception e) {
            e.printStackTrace();
            isTransitioning = false;
        }
    }
    
    /**
     * 立即应用音量
     * @param systemVolume 系统音量值
     */
    private void applyVolumeImmediately(int systemVolume) {
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0);
            notifyVolumeChanged(systemVolume);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 停止平滑过渡
     */
    private void stopSmoothTransition() {
        isTransitioning = false;
        handler.removeCallbacks(smoothTransitionRunnable);
    }
    
    /**
     * 通知音量变化
     * @param systemVolume 系统音量值
     */
    private void notifyVolumeChanged(int systemVolume) {
        if (volumeChangeListener != null) {
            int percent = systemVolumeToPercent(systemVolume);
            volumeChangeListener.onVolumeChanged(percent);
        }
    }
}
