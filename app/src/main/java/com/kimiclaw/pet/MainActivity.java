package com.kimiclaw.pet;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_OVERLAY = 1001;
    private static final int REQUEST_CODE_NOTIFICATION = 1002;
    private static final String GLM_API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

    private ProgressBar hungerBar;
    private TextView hungerText, moodText, levelText;
    private Button btnStart, btnStop;

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("KimiClawPrefs", MODE_PRIVATE);
        editor = prefs.edit();
        mainHandler = new Handler(Looper.getMainLooper());

        initViews();
        updateStatus();
        checkPermissions();

        // 处理外部打开的请求
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null) {
            if (intent.getBooleanExtra("openChat", false)) {
                showChatDialog();
            } else if (intent.getBooleanExtra("openSettings", false)) {
                showSettingsDialog();
            }
        }
    }

    private void initViews() {
        hungerBar = findViewById(R.id.hungerBar);
        hungerText = findViewById(R.id.hungerText);
        moodText = findViewById(R.id.moodText);
        levelText = findViewById(R.id.levelText);

        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);

        btnStart.setOnClickListener(v -> startLobster());
        btnStop.setOnClickListener(v -> stopLobster());

        // 主界面的按钮
        Button btnFeed = findViewById(R.id.btnFeed);
        Button btnChat = findViewById(R.id.btnChat);
        Button btnMonitor = findViewById(R.id.btnMonitor);
        Button btnSettings = findViewById(R.id.btnSettings);

        btnFeed.setOnClickListener(v -> {
            Intent intent = new Intent("com.kimiclaw.pet.FEED");
            sendBroadcast(intent);
            Toast.makeText(this, "🍤 已发送喂食指令", Toast.LENGTH_SHORT).show();
        });

        btnChat.setOnClickListener(v -> showChatDialog());
        btnMonitor.setOnClickListener(v -> showMonitorDialog());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void checkPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show();
        }

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
        Toast.makeText(this, "🦞 小龙虾已启动！点击小龙虾可以喂养、抚摸、聊天~", Toast.LENGTH_LONG).show();
    }

    private void stopLobster() {
        Intent serviceIntent = new Intent(this, FloatingLobsterService.class);
        stopService(serviceIntent);
        Toast.makeText(this, "🦞 小龙虾已停止", Toast.LENGTH_SHORT).show();
    }

    private void updateStatus() {
        int hunger = prefs.getInt("hunger", 50);
        int mood = prefs.getInt("mood", 50);
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
        if (mood < 30) moodEmoji = "😢";
        else if (mood < 60) moodEmoji = "😐";
        moodText.setText(moodEmoji + " 心情" + mood + "%");

        levelText.setText("Lv." + level);
    }

    private void showChatDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_chat, null);
        builder.setView(view);

        LinearLayout chatContainer = view.findViewById(R.id.chatContainer);
        EditText chatInput = view.findViewById(R.id.chatInput);
        Button btnSend = view.findViewById(R.id.btnSend);
        ScrollView chatScroll = view.findViewById(R.id.chatScroll);

        // 添加欢迎消息
        addChatMessage(chatContainer, "🦞", "嗨！我是KimiClaw，有什么可以帮你的吗？", false);

        AlertDialog dialog = builder.create();

        btnSend.setOnClickListener(v -> {
            String message = chatInput.getText().toString().trim();
            if (!message.isEmpty()) {
                addChatMessage(chatContainer, "你", message, true);
                chatInput.setText("");

                // 滚动到底部
                chatScroll.post(() -> chatScroll.fullScroll(ScrollView.FOCUS_DOWN));

                // 使用GLM AI回复
                chatWithGLM(message, response -> {
                    mainHandler.post(() -> {
                        addChatMessage(chatContainer, "🦞", response, false);
                        chatScroll.post(() -> chatScroll.fullScroll(ScrollView.FOCUS_DOWN));
                    });
                });
            }
        });

        dialog.show();
    }

    private void addChatMessage(LinearLayout container, String sender, String message, boolean isUser) {
        TextView textView = new TextView(this);
        textView.setText(sender + ": " + message);
        textView.setTextSize(14);
        textView.setPadding(12, 8, 12, 8);
        textView.setLineSpacing(4, 1);
        if (isUser) {
            textView.setTextColor(getColor(R.color.lobster_red));
        }
        container.addView(textView);
    }

    private void chatWithGLM(String message, GLMCallback callback) {
        String apiKey = prefs.getString("glm_api_key", "");
        if (apiKey.isEmpty()) {
            callback.onResponse("请先设置 GLM API Key！点击设置按钮进行配置。");
            return;
        }

        new Thread(() -> {
            try {
                URL url = new URL(GLM_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);

                // 构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", "glm-4-flash");

                JSONArray messages = new JSONArray();
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", "你是一只可爱的桌面小龙虾宠物，名字叫KimiClaw。你会用可爱、俏皮的语气回复用户，偶尔使用emoji。你的性格活泼可爱，喜欢帮助主人。");
                messages.put(systemMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", message);
                messages.put(userMsg);

                requestBody.put("messages", messages);

                // 发送请求
                OutputStream os = conn.getOutputStream();
                os.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
                os.close();

                // 读取响应
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
                );
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // 解析响应
                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject messageObj = choice.getJSONObject("message");
                    String content = messageObj.getString("content");
                    callback.onResponse(content);
                } else {
                    callback.onResponse("我卡住了，再试一次吧~");
                }

            } catch (Exception e) {
                e.printStackTrace();
                callback.onResponse("网络出问题了，检查一下API Key吧！错误：" + e.getMessage());
            }
        }).start();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        builder.setView(view);

        EditText apiKeyInput = view.findViewById(R.id.apiKeyInput);
        Button btnSaveKey = view.findViewById(R.id.btnSaveKey);
        Button btnGetKey = view.findViewById(R.id.btnGetKey);

        // 加载已保存的API Key
        String savedKey = prefs.getString("glm_api_key", "");
        if (!savedKey.isEmpty()) {
            // 只显示前8位和后4位
            String maskedKey = savedKey.substring(0, Math.min(8, savedKey.length())) + "..." +
                    savedKey.substring(Math.max(0, savedKey.length() - 4));
            apiKeyInput.setHint("已设置: " + maskedKey);
        }

        AlertDialog dialog = builder.create();

        btnSaveKey.setOnClickListener(v -> {
            String apiKey = apiKeyInput.getText().toString().trim();
            if (!apiKey.isEmpty()) {
                editor.putString("glm_api_key", apiKey);
                editor.apply();
                Toast.makeText(this, "API Key 已保存！", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show();
            }
        });

        btnGetKey.setOnClickListener(v -> {
            // 打开智谱AI官网
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://open.bigmodel.cn/usercenter/apikeys"));
            startActivity(intent);
        });

        dialog.show();
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

    public interface GLMCallback {
        void onResponse(String response);
    }
}
