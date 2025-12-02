package com.speedvolume;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;

public class SpeedVolumeService extends Service implements SensorEventListener {
    private static final String CHANNEL_ID = "SpeedVolumeChannel";
    private SensorManager sensorManager;
    private AudioManager audioManager;
    private PowerManager.WakeLock wakeLock;
    private float[] gravity = new float[3];
    private float currentSpeedKmh = 0;
    private SpeedVolumeConfig config;
    private static UpdateListener updateListener;
    private BroadcastReceiver configReceiver;
    private Handler handler;
    private int targetVolume = -1;
    private Runnable volumeAdjuster;

    public interface UpdateListener {
        void onUpdate(float speedKmh, int volume);
    }

    public static void setUpdateListener(UpdateListener listener) {
        updateListener = listener;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "SpeedVolume::WakeLock");
        wakeLock.setReferenceCounted(false);
        config = new SpeedVolumeConfig();
        config.load(this);
        handler = new Handler(Looper.getMainLooper());
        volumeAdjuster = new Runnable() {
            @Override
            public void run() {
                applyVolumeChange();
                handler.postDelayed(this, 100);
            }
        };
        createNotificationChannel();
        registerConfigReceiver();
    }

    private void registerConfigReceiver() {
        configReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                config.reloadActiveProfile(context);
            }
        };
        IntentFilter filter = new IntentFilter("com.speedvolume.CONFIG_CHANGED");
        registerReceiver(configReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("音速骑士")
                .setContentText("正在根据移动速度调节音量")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build();

        startForeground(1, notification);

        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        handler.post(volumeAdjuster);

        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final float alpha = 0.8f;
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        float linearX = event.values[0] - gravity[0];
        float linearY = event.values[1] - gravity[1];
        float linearZ = event.values[2] - gravity[2];

        float acceleration = (float) Math.sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ);
        currentSpeedKmh = currentSpeedKmh * 0.85f + acceleration * 3.6f * 0.15f;
        
        adjustVolume(currentSpeedKmh);
    }

    private void adjustVolume(float speedKmh) {
        int volumePercent = config.getVolumeForSpeed(speedKmh, this);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        targetVolume = Math.max(0, (int) (volumePercent * maxVolume / 100.0f));

        if (updateListener != null) {
            updateListener.onUpdate(speedKmh, volumePercent);
        }
    }
    
    private void applyVolumeChange() {
        if (targetVolume < 0) return;
        
        try {
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (targetVolume != currentVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        if (handler != null && volumeAdjuster != null) {
            handler.removeCallbacks(volumeAdjuster);
        }
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (configReceiver != null) {
            unregisterReceiver(configReceiver);
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
}
