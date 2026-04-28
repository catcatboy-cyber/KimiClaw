package com.kimiclaw.pet;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/**
 * 系统设置跳转工具类
 * 支持鸿蒙和安卓系统，自动识别品牌并跳转到正确的设置页面
 */
public class SystemSettingsHelper {

    private Context context;

    public SystemSettingsHelper(Context context) {
        this.context = context;
    }

    /**
     * 检测是否为鸿蒙系统
     */
    public boolean isHarmonyOS() {
        try {
            Class<?> clazz = Class.forName("com.huawei.system.BuildEx");
            Object obj = clazz.getMethod("getOsBrand").invoke(clazz);
            return "harmony".equalsIgnoreCase(obj.toString());
        } catch (Exception e) {
            // 通过系统属性检测
            String harmonyVersion = getSystemProperty("hw_sc.build.platform.version");
            return harmonyVersion != null && !harmonyVersion.isEmpty();
        }
    }

    /**
     * 获取系统属性
     */
    private String getSystemProperty(String key) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            return (String) clazz.getMethod("get", String.class).invoke(null, key);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取制造商名称
     */
    public String getManufacturer() {
        return Build.MANUFACTURER.toLowerCase();
    }

    /**
     * 跳转到悬浮窗权限设置
     */
    public void openOverlaySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            context.startActivity(intent);
        } catch (Exception e) {
            showErrorAndOpenAppSettings("无法打开悬浮窗设置");
        }
    }

    /**
     * 跳转到通知监听权限设置
     */
    public void openNotificationListenerSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            context.startActivity(intent);
        } catch (Exception e) {
            showErrorAndOpenAppSettings("无法打开通知监听设置");
        }
    }

    /**
     * 跳转到电池优化设置
     */    public void openBatteryOptimizationSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            context.startActivity(intent);
        } catch (Exception e) {
            // 尝试打开电池优化列表
            try {
                Intent listIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                context.startActivity(listIntent);
            } catch (Exception ex) {
                showErrorAndOpenAppSettings("无法打开电池优化设置");
            }
        }
    }

    /**
     * 跳转到自启动管理（根据品牌和系统）
     */
    public void openAutoStartSettings() {
        String manufacturer = getManufacturer();
        boolean isHarmony = isHarmonyOS();

        try {
            Intent intent = new Intent();

            if (isHarmony || manufacturer.contains("huawei") || manufacturer.contains("honor")) {
                // 鸿蒙/华为/荣耀
                openHuaweiAutoStart(intent);
            } else if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")) {
                // 小米/红米
                openXiaomiAutoStart(intent);
            } else if (manufacturer.contains("oppo") || manufacturer.contains("realme")) {
                // OPPO/Realme
                openOppoAutoStart(intent);
            } else if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
                // vivo/iQOO
                openVivoAutoStart(intent);
            } else if (manufacturer.contains("meizu")) {
                // 魅族
                openMeizuAutoStart(intent);
            } else if (manufacturer.contains("samsung")) {
                // 三星
                openSamsungAutoStart(intent);
            } else if (manufacturer.contains("oneplus")) {
                // 一加
                openOnePlusAutoStart(intent);
            } else {
                // 其他品牌，打开应用详情
                openAppDetailsSettings();
                return;
            }

            context.startActivity(intent);
        } catch (Exception e) {
            showErrorAndOpenAppSettings("无法打开自启动设置，请在系统设置中手动允许");
        }
    }

    /**
     * 华为/鸿蒙自启动设置
     */
    private void openHuaweiAutoStart(Intent intent) {
        try {
            // 尝试新版本
            intent.setComponent(new ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ));
        } catch (Exception e) {
            // 尝试旧版本
            intent.setComponent(new ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
            ));
        }
    }

    /**
     * 小米自启动设置
     */
    private void openXiaomiAutoStart(Intent intent) {
        try {
            // MIUI 12+
            intent.setComponent(new ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ));
        } catch (Exception e) {
            // MIUI 旧版本
            intent.setComponent(new ComponentName(
                    "com.xiaomi.mipermission",
                    "com.xiaomi.mipermission.permission.AutoStartManagementActivity"
            ));
        }
    }

    /**
     * OPPO自启动设置
     */
    private void openOppoAutoStart(Intent intent) {
        try {
            // ColorOS 7+
            intent.setComponent(new ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ));
        } catch (Exception e) {
            // ColorOS 旧版本
            intent.setComponent(new ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
            ));
        }
    }

    /**
     * vivo自启动设置
     */
    private void openVivoAutoStart(Intent intent) {
        try {
            // Funtouch OS / Origin OS
            intent.setComponent(new ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ));
        } catch (Exception e) {
            // 旧版本
            intent.setComponent(new ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
            ));
        }
    }

    /**
     * 魅族自启动设置
     */
    private void openMeizuAutoStart(Intent intent) {
        intent.setComponent(new ComponentName(
                "com.meizu.safe",
                "com.meizu.safe.security.SHOW_APPSEC"
        ));
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.putExtra("packageName", context.getPackageName());
    }

    /**
     * 三星自启动设置
     */
    private void openSamsungAutoStart(Intent intent) {
        try {
            // One UI 3+
            intent.setComponent(new ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
            ));
        } catch (Exception e) {
            // 旧版本
            intent.setComponent(new ComponentName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
            ));
        }
    }

    /**
     * 一加自启动设置
     */
    private void openOnePlusAutoStart(Intent intent) {
        intent.setComponent(new ComponentName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
        ));
    }

    /**
     * 跳转到安装未知应用权限设置
     */
    public void openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                context.startActivity(intent);
            } catch (Exception e) {
                showErrorAndOpenAppSettings("无法打开安装权限设置");
            }
        } else {
            Toast.makeText(context, "Android 8.0 以下无需此权限", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 跳转到应用详情设置
     */
    public void openAppDetailsSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "无法打开应用设置", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 跳转到通知设置
     */
    public void openNotificationSettings() {
        try {
            Intent intent = new Intent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            } else {
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
            }
            context.startActivity(intent);
        } catch (Exception e) {
            showErrorAndOpenAppSettings("无法打开通知设置");
        }
    }

    /**
     * 显示错误并打开应用详情页
     */
    private void showErrorAndOpenAppSettings(String message) {
        Toast.makeText(context, message + "，将打开应用详情页", Toast.LENGTH_LONG).show();
        openAppDetailsSettings();
    }

    /**
     * 获取系统信息（用于调试）
     */
    public String getSystemInfo() {
        StringBuilder info = new StringBuilder();
        info.append("系统类型: ").append(isHarmonyOS() ? "鸿蒙" : "安卓").append("\n");
        info.append("制造商: ").append(Build.MANUFACTURER).append("\n");
        info.append("品牌: ").append(Build.BRAND).append("\n");
        info.append("型号: ").append(Build.MODEL).append("\n");
        info.append("Android版本: ").append(Build.VERSION.RELEASE).append("\n");
        info.append("API级别: ").append(Build.VERSION.SDK_INT);
        return info.toString();
    }
}
