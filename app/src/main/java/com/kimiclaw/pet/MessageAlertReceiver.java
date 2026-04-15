package com.kimiclaw.pet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.TextView;

public class MessageAlertReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.kimiclaw.pet.MESSAGE_ALERT".equals(intent.getAction())) {
            String sender = intent.getStringExtra("sender");
            String content = intent.getStringExtra("content");

            SharedPreferences prefs = context.getSharedPreferences("KimiClawPrefs", Context.MODE_PRIVATE);
            boolean showContent = prefs.getBoolean("showContent", false);

            // 构建提示消息
            String alertMessage = "📱 " + sender + " 发来消息！";
            if (showContent && content != null && !content.isEmpty()) {
                alertMessage += "\n" + content.substring(0, Math.min(content.length(), 30));
                if (content.length() > 30) {
                    alertMessage += "...";
                }
            }

            // 通知悬浮窗显示提醒
            // 这里我们通过发送另一个广播给FloatingLobsterService
            Intent alertIntent = new Intent("com.kimiclaw.pet.SHOW_ALERT");
            alertIntent.putExtra("message", alertMessage);
            context.sendBroadcast(alertIntent);
        }
    }
}
