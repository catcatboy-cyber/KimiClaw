package com.kimiclaw.pet;

import android.app.PendingIntent;

/**
 * 消息项数据模型，用于消息列表弹窗
 * 支持多App、多联系人、多条消息
 */
public class MessageItem {
    public String sender;
    public String content;
    public String packageName;
    public PendingIntent contentIntent;
    public long timestamp;

    public MessageItem(String sender, String content, String packageName, PendingIntent contentIntent, long timestamp) {
        this.sender = sender;
        this.content = content;
        this.packageName = packageName;
        this.contentIntent = contentIntent;
        this.timestamp = timestamp;
    }

    /**
     * 根据包名返回应用显示名称
     */
    public String getAppName() {
        if ("com.tencent.mm".equals(packageName)) {
            return "微信";
        } else if ("com.tencent.mobileqq".equals(packageName)) {
            return "QQ";
        } else if ("com.sina.weibo".equals(packageName)) {
            return "微博";
        } else if ("com.alibaba.android.rimet".equals(packageName)) {
            return "钉钉";
        } else if ("com.whatsapp".equals(packageName)) {
            return "WhatsApp";
        } else if ("org.telegram.messenger".equals(packageName)) {
            return "Telegram";
        }
        return "消息";
    }

    /**
     * 返回唯一标识：包名+发送者，用于去重
     */
    public String getKey() {
        return (packageName != null ? packageName : "") + "|" + (sender != null ? sender : "");
    }
}
