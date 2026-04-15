package com.kimiclaw.pet;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_OVERLAY = 1001;
    private static final int REQUEST_CODE_NOTIFICATION = 1002;

    private ProgressBar hungerBar;
    private TextView hungerText, moodText, levelText;
    private Button btnStart, btnFeed, btnChat, btnMonitor, btnStop;

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("KimiClawPrefs", MODE_PRIVATE);
        editor = prefs.edit();

        initViews();
        updateStatus();
        checkPermissions();
    }

    private void initViews() {
        hungerBar = findViewById(R.id.hungerBar);
        hungerText = findViewById(R.id.hungerText);
        moodText = findViewById(R.id.moodText);
        levelText = findViewById(R.id.levelText);

        btnStart = findViewById(R.id.btnStart);
        btnFeed = findViewById(R.id.btnFeed);
        btnChat = findViewById(R.id.btnChat);
        btnMonitor = findViewById(R.id.btnMonitor);
        btnStop = findViewById(R.id.btnStop);

        btnStart.setOnClickListener(v -> startLobster());
        btnFeed.setOnClickListener(v -> feedLobster());
        btnChat.setOnClickListener(v -> showChatDialog());
        btnMonitor.setOnClickListener(v -> showMonitorDialog());
        btnStop.setOnClickListener(v -> stopLobster());
    }

    private void checkPermissions() {
        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show();
        }

        // 检查通知监听权限
        if (!isNotificationServiceEnabled()) {
            Toast.makeText(this, "需要通知监听权限来监控消息", Toast.LENGTH_LONG).show();
        }
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat != null) {
            return flat.contains(pkgName);
        }
        return false;
    }

    private void startLobster() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_OVERLAY);
            return;
        }

        if (!isNotificationServiceEnabled()) {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivityForResult(intent, REQUEST_CODE_NOTIFICATION);
            Toast.makeText(this, "请开启通知监听权限", Toast.LENGTH_LONG).show();
            return;
        }

        Intent serviceIntent = new Intent(this, FloatingLobsterService.class);
        startService(serviceIntent);
        Toast.makeText(this, "🦞 小龙虾已启动！", Toast.LENGTH_SHORT).show();
    }

    private void stopLobster() {
        Intent serviceIntent = new Intent(this, FloatingLobsterService.class);
        stopService(serviceIntent);
        Toast.makeText(this, "🦞 小龙虾已停止", Toast.LENGTH_SHORT).show();
    }

    private void feedLobster() {
        int hunger = prefs.getInt("hunger", 50);
        hunger = Math.min(100, hunger + 20);
        editor.putInt("hunger", hunger);
        editor.apply();

        updateStatus();
        Toast.makeText(this, "🍤 喂食成功！小龙虾很开心~", Toast.LENGTH_SHORT).show();

        // 通知悬浮窗更新
        Intent intent = new Intent("com.kimiclaw.pet.FEED");
        sendBroadcast(intent);
    }

    private void updateStatus() {
        int hunger = prefs.getInt("hunger", 50);
        String mood = prefs.getString("mood", "开心");
        int level = prefs.getInt("level", 1);

        hungerBar.setProgress(hunger);
        hungerText.setText(hunger + "%");

        if (hunger > 60) {
            hungerText.setTextColor(getColor(R.color.hunger_green));
        } else if (hunger > 30) {
            hungerText.setTextColor(getColor(R.color.hunger_yellow));
        } else {
            hungerText.setTextColor(getColor(R.color.hunger_red));
        }

        String moodEmoji = "😊";
        if (hunger < 30) moodEmoji = "😢";
        else if (hunger < 60) moodEmoji = "😐";
        moodText.setText(moodEmoji + " " + mood);

        levelText.setText("Lv." + level);
    }

    private void showChatDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_chat, null);
        builder.setView(view);

        LinearLayout chatContainer = view.findViewById(R.id.chatContainer);
        EditText chatInput = view.findViewById(R.id.chatInput);
        Button btnSend = view.findViewById(R.id.btnSend);

        // 添加欢迎消息
        addChatMessage(chatContainer, "🦞", "嗨！我是KimiClaw，有什么可以帮你的吗？", false);

        AlertDialog dialog = builder.create();

        btnSend.setOnClickListener(v -> {
            String message = chatInput.getText().toString().trim();
            if (!message.isEmpty()) {
                addChatMessage(chatContainer, "你", message, true);
                chatInput.setText("");

                // AI回复
                String reply = getAIReply(message);
                addChatMessage(chatContainer, "🦞", reply, false);
            }
        });

        dialog.show();
    }

    private void addChatMessage(LinearLayout container, String sender, String message, boolean isUser) {
        TextView textView = new TextView(this);
        textView.setText(sender + ": " + message);
        textView.setTextSize(14);
        textView.setPadding(8, 8, 8, 8);
        if (isUser) {
            textView.setTextColor(getColor(R.color.lobster_red));
        }
        container.addView(textView);
    }

    private String getAIReply(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("你好") || lower.contains("hi")) {
            return "你好呀！今天过得怎么样？";
        } else if (lower.contains("吃") || lower.contains("饿")) {
            return "我也饿了...快给我喂食吧！🍤";
        } else if (lower.contains("消息") || lower.contains("监控")) {
            return "我在帮你盯着呢！有消息会第一时间告诉你~";
        } else if (lower.contains("谁") || lower.contains("名字")) {
            return "我是KimiClaw，你的桌面小龙虾宠物！";
        } else if (lower.contains("笑话")) {
            return "为什么龙虾不去健身房？因为它已经有'钳'了！🤣";
        } else {
            return "嗯嗯，我在听呢！继续说吧~";
        }
    }

    private void showMonitorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_monitor, null);
        builder.setView(view);

        EditText contactInput = view.findViewById(R.id.contactInput);
        Button btnAddContact = view.findViewById(R.id.btnAddContact);
        ListView contactList = view.findViewById(R.id.contactList);
        CheckBox cbWeChat = view.findViewById(R.id.cbWeChat);
        CheckBox cbQQ = view.findViewById(R.id.cbQQ);
        CheckBox cbShowContent = view.findViewById(R.id.cbShowContent);
        Button btnSaveMonitor = view.findViewById(R.id.btnSaveMonitor);

        // 加载已保存的联系人
        Set<String> contacts = prefs.getStringSet("monitoredContacts", new HashSet<>());
        List<String> contactListData = new ArrayList<>(contacts);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, contactListData);
        contactList.setAdapter(adapter);

        btnAddContact.setOnClickListener(v -> {
            String contact = contactInput.getText().toString().trim();
            if (!contact.isEmpty()) {
                contacts.add(contact);
                contactListData.add(contact);
                adapter.notifyDataSetChanged();
                contactInput.setText("");
            }
        });

        AlertDialog dialog = builder.create();

        btnSaveMonitor.setOnClickListener(v -> {
            editor.putStringSet("monitoredContacts", contacts);
            editor.putBoolean("monitorWeChat", cbWeChat.isChecked());
            editor.putBoolean("monitorQQ", cbQQ.isChecked());
            editor.putBoolean("showContent", cbShowContent.isChecked());
            editor.apply();

            Toast.makeText(this, "设置已保存！", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }
}
