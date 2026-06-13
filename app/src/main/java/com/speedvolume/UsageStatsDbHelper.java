package com.speedvolume;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 使用统计数据库帮助类
 * 管理骑行会话和速度快照数据的存储
 */
public class UsageStatsDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "UsageStats.db";
    private static final int DATABASE_VERSION = 1;
    
    // 骑行会话表
    public static final String TABLE_RIDE_SESSIONS = "ride_sessions";
    public static final String COLUMN_SESSION_ID = "id";
    public static final String COLUMN_SESSION_START_TIME = "start_time";
    public static final String COLUMN_SESSION_END_TIME = "end_time";
    public static final String COLUMN_SESSION_DISTANCE = "distance";
    public static final String COLUMN_SESSION_MAX_SPEED = "max_speed";
    public static final String COLUMN_SESSION_AVG_SPEED = "avg_speed";
    public static final String COLUMN_SESSION_DURATION = "duration";
    
    // 速度快照表
    public static final String TABLE_SPEED_SNAPSHOTS = "speed_snapshots";
    public static final String COLUMN_SNAPSHOT_ID = "id";
    public static final String COLUMN_SNAPSHOT_SESSION_ID = "session_id";
    public static final String COLUMN_SNAPSHOT_TIMESTAMP = "timestamp";
    public static final String COLUMN_SNAPSHOT_SPEED = "speed";
    public static final String COLUMN_SNAPSHOT_VOLUME = "volume";
    public static final String COLUMN_SNAPSHOT_LATITUDE = "latitude";
    public static final String COLUMN_SNAPSHOT_LONGITUDE = "longitude";
    
    // 创建会话表 SQL
    private static final String SQL_CREATE_SESSIONS =
        "CREATE TABLE " + TABLE_RIDE_SESSIONS + " (" +
        COLUMN_SESSION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COLUMN_SESSION_START_TIME + " INTEGER NOT NULL, " +
        COLUMN_SESSION_END_TIME + " INTEGER, " +
        COLUMN_SESSION_DISTANCE + " REAL, " +
        COLUMN_SESSION_MAX_SPEED + " REAL, " +
        COLUMN_SESSION_AVG_SPEED + " REAL, " +
        COLUMN_SESSION_DURATION + " INTEGER)";
    
    // 创建快照表 SQL
    private static final String SQL_CREATE_SNAPSHOTS =
        "CREATE TABLE " + TABLE_SPEED_SNAPSHOTS + " (" +
        COLUMN_SNAPSHOT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COLUMN_SNAPSHOT_SESSION_ID + " INTEGER NOT NULL, " +
        COLUMN_SNAPSHOT_TIMESTAMP + " INTEGER NOT NULL, " +
        COLUMN_SNAPSHOT_SPEED + " REAL NOT NULL, " +
        COLUMN_SNAPSHOT_VOLUME + " INTEGER NOT NULL, " +
        COLUMN_SNAPSHOT_LATITUDE + " REAL, " +
        COLUMN_SNAPSHOT_LONGITUDE + " REAL, " +
        "FOREIGN KEY (" + COLUMN_SNAPSHOT_SESSION_ID + ") REFERENCES " +
        TABLE_RIDE_SESSIONS + "(" + COLUMN_SESSION_ID + "))";
    
    // 删除表 SQL
    private static final String SQL_DELETE_SESSIONS =
        "DROP TABLE IF EXISTS " + TABLE_RIDE_SESSIONS;
    private static final String SQL_DELETE_SNAPSHOTS =
        "DROP TABLE IF EXISTS " + TABLE_SPEED_SNAPSHOTS;
    
    public UsageStatsDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_SESSIONS);
        db.execSQL(SQL_CREATE_SNAPSHOTS);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 简单的升级策略：删除旧表并重新创建
        db.execSQL(SQL_DELETE_SNAPSHOTS);
        db.execSQL(SQL_DELETE_SESSIONS);
        onCreate(db);
    }
    
    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
