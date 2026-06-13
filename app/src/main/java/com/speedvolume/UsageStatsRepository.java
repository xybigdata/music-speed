package com.speedvolume;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 使用统计数据仓库
 * 封装骑行会话和速度快照的数据库操作
 */
public class UsageStatsRepository {
    private static final String TAG = "UsageStatsRepository";
    
    private final UsageStatsDbHelper dbHelper;
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat csvDateFormat;
    
    // 当前会话 ID
    private long currentSessionId = -1;
    private long sessionStartTime = 0;
    private float sessionMaxSpeed = 0;
    private float sessionTotalSpeed = 0;
    private int sessionSpeedCount = 0;
    private float sessionDistance = 0;
    private float lastSpeed = 0;
    private long lastSnapshotTime = 0;
    
    // 快照记录间隔 (毫秒)
    private static final long SNAPSHOT_INTERVAL = 5000;  // 5秒
    
    /**
     * 骑行会话数据类
     */
    public static class RideSession {
        public long id;
        public long startTime;
        public long endTime;
        public float distance;      // 公里
        public float maxSpeed;      // km/h
        public float avgSpeed;      // km/h
        public long duration;       // 秒
        
        public RideSession() {}
        
        public RideSession(long id, long startTime, long endTime, 
                          float distance, float maxSpeed, float avgSpeed, long duration) {
            this.id = id;
            this.startTime = startTime;
            this.endTime = endTime;
            this.distance = distance;
            this.maxSpeed = maxSpeed;
            this.avgSpeed = avgSpeed;
            this.duration = duration;
        }
    }
    
    /**
     * 速度快照数据类
     */
    public static class SpeedSnapshot {
        public long id;
        public long sessionId;
        public long timestamp;
        public float speed;         // km/h
        public int volume;          // 百分比
        public double latitude;
        public double longitude;
        
        public SpeedSnapshot() {}
        
        public SpeedSnapshot(long sessionId, long timestamp, float speed, int volume, 
                           double latitude, double longitude) {
            this.sessionId = sessionId;
            this.timestamp = timestamp;
            this.speed = speed;
            this.volume = volume;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
    
    /**
     * 骑行统计汇总
     */
    public static class RideStats {
        public long totalSessions;      // 总骑行次数
        public long totalDuration;      // 总时长（秒）
        public float totalDistance;     // 总距离（公里）
        public float maxSpeedEver;      // 历史最高速度
        public float avgSpeed;          // 平均速度
        public long totalSnapshots;     // 总快照数
        
        public RideStats() {
            totalSessions = 0;
            totalDuration = 0;
            totalDistance = 0;
            maxSpeedEver = 0;
            avgSpeed = 0;
            totalSnapshots = 0;
        }
    }
    
    public UsageStatsRepository(Context context) {
        dbHelper = new UsageStatsDbHelper(context);
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        csvDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }
    
    /**
     * 开始新的骑行会话
     * @return 会话 ID
     */
    public long startSession() {
        if (currentSessionId >= 0) {
            // 已有会话，先结束
            endSession();
        }
        
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        sessionStartTime = System.currentTimeMillis();
        sessionMaxSpeed = 0;
        sessionTotalSpeed = 0;
        sessionSpeedCount = 0;
        sessionDistance = 0;
        lastSpeed = 0;
        lastSnapshotTime = 0;
        
        ContentValues values = new ContentValues();
        values.put(UsageStatsDbHelper.COLUMN_SESSION_START_TIME, sessionStartTime);
        
        currentSessionId = db.insert(UsageStatsDbHelper.TABLE_RIDE_SESSIONS, null, values);
        
        Log.d(TAG, "Started session: " + currentSessionId);
        return currentSessionId;
    }
    
    /**
     * 结束当前骑行会话
     */
    public void endSession() {
        if (currentSessionId < 0) {
            return;
        }
        
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        long endTime = System.currentTimeMillis();
        long duration = (endTime - sessionStartTime) / 1000;  // 秒
        
        // 计算平均速度
        float avgSpeed = sessionSpeedCount > 0 ? sessionTotalSpeed / sessionSpeedCount : 0;
        
        ContentValues values = new ContentValues();
        values.put(UsageStatsDbHelper.COLUMN_SESSION_END_TIME, endTime);
        values.put(UsageStatsDbHelper.COLUMN_SESSION_DISTANCE, sessionDistance);
        values.put(UsageStatsDbHelper.COLUMN_SESSION_MAX_SPEED, sessionMaxSpeed);
        values.put(UsageStatsDbHelper.COLUMN_SESSION_AVG_SPEED, avgSpeed);
        values.put(UsageStatsDbHelper.COLUMN_SESSION_DURATION, duration);
        
        db.update(UsageStatsDbHelper.TABLE_RIDE_SESSIONS, values,
                UsageStatsDbHelper.COLUMN_SESSION_ID + " = ?",
                new String[]{String.valueOf(currentSessionId)});
        
        Log.d(TAG, "Ended session: " + currentSessionId + 
              ", duration: " + duration + "s, distance: " + sessionDistance + "km");
        
        currentSessionId = -1;
    }
    
    /**
     * 添加速度快照
     * @param speed 速度 (km/h)
     * @param volume 音量百分比
     * @param latitude 纬度 (可为 0)
     * @param longitude 经度 (可为 0)
     */
    public void addSnapshot(float speed, int volume, double latitude, double longitude) {
        if (currentSessionId < 0) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // 检查是否达到快照间隔
        if (currentTime - lastSnapshotTime < SNAPSHOT_INTERVAL) {
            return;
        }
        
        // 更新会话统计
        sessionMaxSpeed = Math.max(sessionMaxSpeed, speed);
        sessionTotalSpeed += speed;
        sessionSpeedCount++;
        
        // 简单估算距离 (速度 * 时间)
        if (lastSnapshotTime > 0) {
            float timeDiffHours = (currentTime - lastSnapshotTime) / 3600000f;
            sessionDistance += (lastSpeed + speed) / 2 * timeDiffHours;  // 平均速度 * 时间
        }
        
        lastSpeed = speed;
        lastSnapshotTime = currentTime;
        
        // 保存快照到数据库
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        ContentValues values = new ContentValues();
        values.put(UsageStatsDbHelper.COLUMN_SNAPSHOT_SESSION_ID, currentSessionId);
        values.put(UsageStatsDbHelper.COLUMN_SNAPSHOT_TIMESTAMP, currentTime);
        values.put(UsageStatsDbHelper.COLUMN_SNAPSHOT_SPEED, speed);
        values.put(UsageStatsDbHelper.COLUMN_SNAPSHOT_VOLUME, volume);
        
        if (latitude != 0 && longitude != 0) {
            values.put(UsageStatsDbHelper.COLUMN_SNAPSHOT_LATITUDE, latitude);
            values.put(UsageStatsDbHelper.COLUMN_SNAPSHOT_LONGITUDE, longitude);
        }
        
        db.insert(UsageStatsDbHelper.TABLE_SPEED_SNAPSHOTS, null, values);
    }
    
    /**
     * 获取最近的骑行会话
     * @param limit 最大数量
     * @return 会话列表
     */
    public List<RideSession> getRecentSessions(int limit) {
        List<RideSession> sessions = new ArrayList<>();
        
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        Cursor cursor = db.query(
            UsageStatsDbHelper.TABLE_RIDE_SESSIONS,
            null,
            null,
            null,
            null,
            null,
            UsageStatsDbHelper.COLUMN_SESSION_START_TIME + " DESC",
            String.valueOf(limit)
        );
        
        while (cursor.moveToNext()) {
            RideSession session = new RideSession();
            session.id = cursor.getLong(
                cursor.getColumnIndexOrThrow(UsageStatsDbHelper.COLUMN_SESSION_ID));
            session.startTime = cursor.getLong(
                cursor.getColumnIndexOrThrow(UsageStatsDbHelper.COLUMN_SESSION_START_TIME));
            session.endTime = cursor.getLong(
                cursor.getColumnIndexOrThrow(UsageStatsDbHelper.COLUMN_SESSION_END_TIME));
            session.distance = cursor.getFloat(
                cursor.getColumnIndexOrThrow(UsageStatsDbHelper.COLUMN_SESSION_DISTANCE));
            session.maxSpeed = cursor.getFloat(
                cursor.getColumnIndexOrThrow(UsageStatsDbHelper.COLUMN_SESSION_MAX_SPEED));
            session.avgSpeed = cursor.getFloat(
                cursor.getColumnIndexOrThrow(UsageStatsDbHelper.COLUMN_SESSION_AVG_SPEED));
            session.duration = cursor.getLong(
                cursor.getColumnIndexOrThrow(UsageStatsDbHelper.COLUMN_SESSION_DURATION));
            
            sessions.add(session);
        }
        
        cursor.close();
        return sessions;
    }
    
    /**
     * 获取指定会话的速度快照
     * @param sessionId 会话 ID
     * @return 快照列表
     */
    public List<SpeedSnapshot> getSessionSnapshots(long sessionId) {
        List<SpeedSnapshot> snapshots = new ArrayList<>();
        
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        Cursor cursor = db.query(
            UsageStatsDbHelper.TABLE_SPEED_SNAPSHOTS,
            null,
            UsageStatsDbHelper.COLUMN_SNAPSHOT_SESSION_ID + " = ?",
            new String[]{String.valueOf(sessionId)},
            null,
            null,
            UsageStatsDbHelper.COLUMN_SNAPSHOT_TIMESTAMP + " ASC"
        );
        
        while (cursor.moveToNext()) {
            SpeedSnapshot snapshot = new SpeedSnapshot();
            snapshot.id = cursor.getLong(
                cursor.getColumnIndexOrThrow(UsageStatsDbHelper.COLUMN_SNAPSHOT_ID));
            snapshot.sessionId = cursor.getLong(
                cursor.getColumnIndexOrThrow(UsageStatsDbHelper.COLUMN_SNAPSHOT_SESSION_ID));
            snapshot.timestamp = cursor.getLong(
                cursor.getColumnIndexOrThrow(UsageStatsDbHelper.COLUMN_SNAPSHOT_TIMESTAMP));
            snapshot.speed = cursor.getFloat(
                cursor.getColumnIndexOrThrow(UsageStatsDbHelper.COLUMN_SNAPSHOT_SPEED));
            snapshot.volume = cursor.getInt(
                cursor.getColumnIndexOrThrow(UsageStatsDbHelper.COLUMN_SNAPSHOT_VOLUME));
            
            int latIndex = cursor.getColumnIndex(UsageStatsDbHelper.COLUMN_SNAPSHOT_LATITUDE);
            int lngIndex = cursor.getColumnIndex(UsageStatsDbHelper.COLUMN_SNAPSHOT_LONGITUDE);
            
            if (!cursor.isNull(latIndex) && !cursor.isNull(lngIndex)) {
                snapshot.latitude = cursor.getDouble(latIndex);
                snapshot.longitude = cursor.getDouble(lngIndex);
            }
            
            snapshots.add(snapshot);
        }
        
        cursor.close();
        return snapshots;
    }
    
    /**
     * 获取总体骑行统计
     * @return 统计汇总
     */
    public RideStats getTotalStats() {
        RideStats stats = new RideStats();
        
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        // 查询会话统计
        Cursor cursor = db.rawQuery(
            "SELECT COUNT(*) as count, " +
            "SUM(" + UsageStatsDbHelper.COLUMN_SESSION_DURATION + ") as total_duration, " +
            "SUM(" + UsageStatsDbHelper.COLUMN_SESSION_DISTANCE + ") as total_distance, " +
            "MAX(" + UsageStatsDbHelper.COLUMN_SESSION_MAX_SPEED + ") as max_speed, " +
            "AVG(" + UsageStatsDbHelper.COLUMN_SESSION_AVG_SPEED + ") as avg_speed " +
            "FROM " + UsageStatsDbHelper.TABLE_RIDE_SESSIONS +
            " WHERE " + UsageStatsDbHelper.COLUMN_SESSION_END_TIME + " IS NOT NULL",
            null
        );
        
        if (cursor.moveToFirst()) {
            stats.totalSessions = cursor.getLong(0);
            stats.totalDuration = cursor.getLong(1);
            stats.totalDistance = cursor.getFloat(2);
            stats.maxSpeedEver = cursor.getFloat(3);
            stats.avgSpeed = cursor.getFloat(4);
        }
        
        cursor.close();
        
        // 查询快照总数
        cursor = db.rawQuery(
            "SELECT COUNT(*) FROM " + UsageStatsDbHelper.TABLE_SPEED_SNAPSHOTS,
            null
        );
        
        if (cursor.moveToFirst()) {
            stats.totalSnapshots = cursor.getLong(0);
        }
        
        cursor.close();
        
        return stats;
    }
    
    /**
     * 导出数据为 CSV 格式
     * @param file 目标文件
     * @return 是否成功
     */
    public boolean exportToCsv(File file) {
        try {
            FileWriter writer = new FileWriter(file);
            
            // 写入会话数据
            writer.append("骑行会话记录\n");
            writer.append("ID,开始时间,结束时间,时长(秒),距离(km),最高速度(km/h),平均速度(km/h)\n");
            
            List<RideSession> sessions = getRecentSessions(Integer.MAX_VALUE);
            
            for (RideSession session : sessions) {
                writer.append(String.valueOf(session.id)).append(",");
                writer.append(csvDateFormat.format(new Date(session.startTime))).append(",");
                writer.append(session.endTime > 0 ? csvDateFormat.format(new Date(session.endTime)) : "").append(",");
                writer.append(String.valueOf(session.duration)).append(",");
                writer.append(String.format(Locale.US, "%.2f", session.distance)).append(",");
                writer.append(String.format(Locale.US, "%.1f", session.maxSpeed)).append(",");
                writer.append(String.format(Locale.US, "%.1f", session.avgSpeed)).append("\n");
            }
            
            writer.append("\n");
            
            // 写入快照数据
            writer.append("速度快照记录\n");
            writer.append("会话ID,时间,速度(km/h),音量(%),纬度,经度\n");
            
            for (RideSession session : sessions) {
                List<SpeedSnapshot> snapshots = getSessionSnapshots(session.id);
                
                for (SpeedSnapshot snapshot : snapshots) {
                    writer.append(String.valueOf(snapshot.sessionId)).append(",");
                    writer.append(csvDateFormat.format(new Date(snapshot.timestamp))).append(",");
                    writer.append(String.format(Locale.US, "%.1f", snapshot.speed)).append(",");
                    writer.append(String.valueOf(snapshot.volume)).append(",");
                    writer.append(String.format(Locale.US, "%.6f", snapshot.latitude)).append(",");
                    writer.append(String.format(Locale.US, "%.6f", snapshot.longitude)).append("\n");
                }
            }
            
            writer.flush();
            writer.close();
            
            Log.d(TAG, "Exported data to: " + file.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to export CSV: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 清除所有统计数据
     */
    public void clearAllData() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(UsageStatsDbHelper.TABLE_SPEED_SNAPSHOTS, null, null);
        db.delete(UsageStatsDbHelper.TABLE_RIDE_SESSIONS, null, null);
        Log.d(TAG, "All usage stats data cleared");
    }
    
    /**
     * 删除指定会话及其快照
     * @param sessionId 会话 ID
     */
    public void deleteSession(long sessionId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        // 先删除快照
        db.delete(UsageStatsDbHelper.TABLE_SPEED_SNAPSHOTS,
                UsageStatsDbHelper.COLUMN_SNAPSHOT_SESSION_ID + " = ?",
                new String[]{String.valueOf(sessionId)});
        
        // 再删除会话
        db.delete(UsageStatsDbHelper.TABLE_RIDE_SESSIONS,
                UsageStatsDbHelper.COLUMN_SESSION_ID + " = ?",
                new String[]{String.valueOf(sessionId)});
        
        Log.d(TAG, "Deleted session: " + sessionId);
    }
    
    /**
     * 获取当前会话 ID
     * @return 当前会话 ID，如果没有活跃会话返回 -1
     */
    public long getCurrentSessionId() {
        return currentSessionId;
    }
    
    /**
     * 获取当前会话的最大速度
     * @return 最大速度 (km/h)
     */
    public float getSessionMaxSpeed() {
        return sessionMaxSpeed;
    }
    
    /**
     * 获取当前会话的估算距离
     * @return 距离 (km)
     */
    public float getSessionDistance() {
        return sessionDistance;
    }
    
    /**
     * 关闭数据库连接
     */
    public void close() {
        dbHelper.close();
    }
    
    /**
     * 格式化时长显示
     * @param seconds 秒数
     * @return 格式化字符串 (如 "2小时30分")
     */
    public static String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            if (secs > 0) {
                return minutes + "分" + secs + "秒";
            }
            return minutes + "分钟";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            if (minutes > 0) {
                return hours + "小时" + minutes + "分";
            }
            return hours + "小时";
        }
    }
    
    /**
     * 格式化距离显示
     * @param distanceKm 距离 (公里)
     * @return 格式化字符串 (如 "12.5km")
     */
    public static String formatDistance(float distanceKm) {
        if (distanceKm < 1) {
            return String.format(Locale.getDefault(), "%.0f米", distanceKm * 1000);
        }
        return String.format(Locale.getDefault(), "%.1f公里", distanceKm);
    }
    
    /**
     * 格式化速度显示
     * @param speedKmh 速度 (km/h)
     * @return 格式化字符串 (如 "25.5km/h")
     */
    public static String formatSpeed(float speedKmh) {
        return String.format(Locale.getDefault(), "%.1fkm/h", speedKmh);
    }
}
