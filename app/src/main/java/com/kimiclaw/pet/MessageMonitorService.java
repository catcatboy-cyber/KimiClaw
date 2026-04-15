package com.kimiclaw.pet;

import android.app.Notification;
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
        String packageName = sbn.getPackageName();
        Notification notification = sbn.getNotification();
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
            notifyFloatingLobster(sender, text);
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

    private boolean isMonitoredContact(String sender, Set<String> monitoredContacts) {
        for (String contact : monitoredContacts) {
            if (sender.contains(contact) || contact.contains(sender)) {
                return true;
            }
        }
        return false;
    }

    private void notifyFloatingLobster(String sender, String content) {
        // 发送广播通知悬浮窗服务
        android.content.Intent intent = new android.content.Intent("com.kimiclaw.pet.MESSAGE_ALERT");
        intent.putExtra("sender", sender);
        intent.putExtra("content", content);
        sendBroadcast(intent);

        Log.d(TAG, "监控到消息：" + sender + " - " + content);
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
}
