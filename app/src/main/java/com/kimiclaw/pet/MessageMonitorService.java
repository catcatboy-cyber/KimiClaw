package com.kimiclaw.pet;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Set;

public class MessageMonitorService extends NotificationListenerService {

    private static final String TAG = "MessageMonitor";
    private SharedPreferences prefs;

    // 常见社交App包名
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String QQ_PACKAGE = "com.tencent.mobileqq";
    private static final String WEIBO_PACKAGE = "com.sina.weibo";
    private static final String DINGTALK_PACKAGE = "com.alibaba.android.rimet";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String TELEGRAM_PACKAGE = "org.telegram.messenger";

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("KimiClawPrefs", MODE_PRIVATE);
        Log.d(TAG, "消息监控服务已启动");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }

        String packageName = sbn.getPackageName();
        Notification notification = sbn.getNotification();

        if (notification == null || notification.extras == null) {
            return;
        }

        Bundle extras = notification.extras;

        // 获取通知信息
        String title = extras.getString(Notification.EXTRA_TITLE, "");
        String text = extras.getString(Notification.EXTRA_TEXT, "");
        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        if (bigText != null && bigText.length() > text.length()) {
            text = bigText.toString();
        }

        // 检查是否是监控的App
        if (!isMonitoredApp(packageName)) {
            return;
        }

        // 获取监控联系人列表
        Set<String> monitoredContacts = prefs.getStringSet("monitoredContacts", null);
        if (monitoredContacts == null || monitoredContacts.isEmpty()) {
            return;
        }

        // 检查发送者是否在监控列表中
        String sender = extractSender(title, text, packageName);
        if (sender != null && isMonitoredContact(sender, monitoredContacts)) {
            // 匹配成功，通知悬浮窗
            notifyFloatingLobster(sender, text, packageName, notification.contentIntent);
        }
    }

    private boolean isMonitoredApp(String packageName) {
        boolean monitorWeChat = prefs.getBoolean("monitorWeChat", true);
        boolean monitorQQ = prefs.getBoolean("monitorQQ", true);

        if (monitorWeChat && packageName.equals(WECHAT_PACKAGE)) return true;
        if (monitorQQ && packageName.equals(QQ_PACKAGE)) return true;
        if (packageName.equals(WEIBO_PACKAGE)) return true;
        if (packageName.equals(DINGTALK_PACKAGE)) return true;
        if (packageName.equals(WHATSAPP_PACKAGE)) return true;
        if (packageName.equals(TELEGRAM_PACKAGE)) return true;

        return false;
    }

    private String extractSender(String title, String text, String packageName) {
        // 根据不同App提取发送者名称
        if (packageName.equals(WECHAT_PACKAGE)) {
            // 微信：title通常是发送者或群名
            if (!title.isEmpty() && !title.equals("微信")) {
                return title;
            }
            // 某些ROM或隐藏详情模式下，title为"微信"，尝试从text解析
            // 常见格式："发送者: 消息内容" 或 "发送者：消息内容"
            if (!text.isEmpty()) {
                String sender = parseWeChatSender(text);
                if (sender != null) {
                    return sender;
                }
            }
        } else if (packageName.equals(QQ_PACKAGE)) {
            // QQ：title通常是发送者
            if (!title.isEmpty() && !title.equals("QQ")) {
                return title;
            }
        }

        // 通用处理：尝试从title提取
        if (!title.isEmpty() && title.length() < 20) {
            return title;
        }

        return null;
    }

    private String parseWeChatSender(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        // 尝试匹配 "发送者: 内容" 或 "发送者：内容"
        int colonIndex = text.indexOf(':');
        if (colonIndex == -1) {
            colonIndex = text.indexOf('：');
        }
        if (colonIndex > 0) {
            String sender = text.substring(0, colonIndex).trim();
            if (!sender.isEmpty() && sender.length() < 30) {
                return sender;
            }
        }
        // 如果text本身很短（可能是人名或简短提示），也尝试返回
        if (text.length() < 15 && !text.contains("收到") && !text.contains("条新消息")) {
            return text.trim();
        }
        return null;
    }

    private boolean isMonitoredContact(String sender, Set<String> monitoredContacts) {
        for (String contact : monitoredContacts) {
            if (sender.contains(contact) || contact.contains(sender)) {
                return true;
            }
        }
        return false;
    }

    private void notifyFloatingLobster(String sender, String content, String packageName, PendingIntent contentIntent) {
        // 直接发送广播给悬浮窗服务显示提醒
        android.content.Intent intent = new android.content.Intent("com.kimiclaw.pet.SHOW_ALERT");
        intent.setPackage(getPackageName());
        intent.putExtra("app_token", getPackageName() + "_" + android.os.Process.myPid());
        intent.putExtra("sender", sender);
        intent.putExtra("content", content);
        intent.putExtra("packageName", packageName);
        if (contentIntent != null) {
            intent.putExtra("contentIntent", contentIntent);
        }
        sendBroadcast(intent);

        // 日志脱敏处理
        String maskedSender = maskSensitiveInfo(sender);
        String maskedContent = maskSensitiveInfo(content);
        Log.d(TAG, "监控到消息：" + maskedSender + " - " + maskedContent);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // 通知被移除时的处理
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "通知监听器已连接");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "通知监听器已断开");
    }

    /**
     * 脱敏处理敏感信息
     */
    private String maskSensitiveInfo(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.length() <= 4) {
            return "***";
        }
        // 保留前2个字符和后2个字符，中间用 *** 替代
        return text.substring(0, 2) + "***" + text.substring(text.length() - 2);
    }
}
