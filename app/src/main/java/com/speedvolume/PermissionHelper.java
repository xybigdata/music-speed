package com.speedvolume;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 权限管理助手
 * 统一管理权限请求和状态检查，支持 Android 13+ 的动态权限请求
 */
public class PermissionHelper {
    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    
    // 必需权限列表（基本权限）
    private static final String[] REQUIRED_PERMISSIONS = buildRequiredPermissions();
    
    /**
     * 根据Android版本构建必需权限列表
     */
    private static String[] buildRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 需要通知权限
            return new String[]{
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 需要后台位置权限（但需要分步请求）
            return new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            };
        } else {
            return new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }
    }
    
    /**
     * 获取请求码
     */
    public int getRequestCode() {
        return REQUEST_CODE_PERMISSIONS;
    }
    
    /**
     * 检查是否拥有所有必需权限
     * @param activity Activity实例
     * @return 是否拥有所有权限
     */
    public boolean hasAllPermissions(Activity activity) {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 检查是否拥有位置权限
     * @param context Context实例
     * @return 是否拥有位置权限
     */
    public boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, 
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, 
            Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * 检查是否拥有精确定位权限
     * @param context Context实例
     * @return 是否拥有精确定位权限
     */
    public boolean hasFineLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, 
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * 检查是否拥有通知权限（Android 13+）
     * @param context Context实例
     * @return 是否拥有通知权限
     */
    public boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, 
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        // Android 13以下无需动态请求通知权限
        return true;
    }
    
    /**
     * 请求所有必需权限
     * @param activity Activity实例
     */
    public void requestPermissions(Activity activity) {
        ActivityCompat.requestPermissions(
            activity,
            REQUIRED_PERMISSIONS,
            REQUEST_CODE_PERMISSIONS
        );
    }
    
    /**
     * 请求后台位置权限（Android 10+）
     * 注意：必须先获得前台位置权限后才能请求后台位置权限
     * @param activity Activity实例
     */
    public void requestBackgroundLocationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                REQUEST_CODE_PERMISSIONS + 1
            );
        }
    }
    
    /**
     * 检查是否需要显示权限说明
     * 当用户之前拒绝过权限时返回true
     * @param activity Activity实例
     * @return 是否需要显示说明
     */
    public boolean shouldShowRationale(Activity activity) {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查特定权限是否需要显示说明
     * @param activity Activity实例
     * @param permission 权限名称
     * @return 是否需要显示说明
     */
    public boolean shouldShowRationaleForPermission(Activity activity, String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }
    
    /**
     * 检查用户是否永久拒绝了权限（勾选了"不再询问"）
     * @param activity Activity实例
     * @return 是否永久拒绝
     */
    public boolean isPermanentlyDenied(Activity activity) {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission) 
                != PackageManager.PERMISSION_GRANTED
                && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 打开应用设置页面
     * @param activity Activity实例
     */
    public void openSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
    }
    
    /**
     * 打开位置服务设置页面
     * @param activity Activity实例
     */
    public void openLocationSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        activity.startActivity(intent);
    }
    
    /**
     * 获取必需权限列表
     * @return 权限数组
     */
    public String[] getRequiredPermissions() {
        return REQUIRED_PERMISSIONS.clone();
    }
    
    /**
     * 获取权限的友好名称
     * @param permission 权限名称
     * @return 友好名称
     */
    public String getPermissionFriendlyName(String permission) {
        if (permission.equals(Manifest.permission.POST_NOTIFICATIONS)) {
            return "通知权限";
        } else if (permission.equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return "精确定位权限";
        } else if (permission.equals(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return "粗略定位权限";
        } else if (permission.equals(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            return "后台定位权限";
        } else if (permission.equals(Manifest.permission.BLUETOOTH) || 
                   permission.equals(Manifest.permission.BLUETOOTH_CONNECT)) {
            return "蓝牙权限";
        }
        return permission;
    }
    
    /**
     * 获取权限用途说明
     * @param permission 权限名称
     * @return 用途说明
     */
    public String getPermissionRationale(String permission) {
        if (permission.equals(Manifest.permission.POST_NOTIFICATIONS)) {
            return "需要通知权限来显示服务运行状态和控制按钮";
        } else if (permission.equals(Manifest.permission.ACCESS_FINE_LOCATION) ||
                   permission.equals(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return "需要位置权限来通过GPS获取准确的移动速度，用于自动调节音量";
        } else if (permission.equals(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            return "需要后台定位权限来在骑行时持续检测速度变化";
        } else if (permission.equals(Manifest.permission.BLUETOOTH) ||
                   permission.equals(Manifest.permission.BLUETOOTH_CONNECT)) {
            return "需要蓝牙权限来自动连接蓝牙设备并实现自动启停功能";
        }
        return "此权限是应用正常运行的必要条件";
    }
}
