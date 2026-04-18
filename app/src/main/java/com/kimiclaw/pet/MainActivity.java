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
import android.os.PowerManager;
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
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
    private static final int PERMISSION_REQUEST_STORAGE = 1003;
    private static final String GLM_API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

    private ProgressBar hungerBar;
    private TextView hungerText, moodText, levelText;
    private Button btnStart, btnStop;

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private Handler mainHandler;
    private UpdateManager updateManager;
    private ConfigManager configManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("KimiClawPrefs", MODE_PRIVATE);
        editor = prefs.edit();
        mainHandler = new Handler(Looper.getMainLooper());
        updateManager = new UpdateManager(this);
        configManager = new ConfigManager(this);

        initViews();
        updateStatus();
        checkPermissions();

        // 检查是否需要导入配置（首次启动且有备份）
        checkImportConfig();

        // 处理外部打开的请求
        handleIntent(getIntent());
    }

    /**
     * 检查是否需要导入配置
     */
    private void checkImportConfig() {
        // 检查是否是首次启动（没有保存过API Key）
        String savedKey = prefs.getString("glm_api_key", "");
        boolean hasLaunched = prefs.getBoolean("has_launched", false);

        if (!hasLaunched && configManager.hasBackupConfig()) {
            // 首次启动且有备份配置，提示导入
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("发现配置备份");
            builder.setMessage("检测到之前保存的配置文件，是否恢复？\n\n包含：\n• GLM API Key\n• 监控联系人\n• 应用设置");
            builder.setPositiveButton("恢复配置", (dialog, which) -> {
                if (configManager.importConfig()) {
                    Toast.makeText(this, "配置恢复成功！", Toast.LENGTH_SHORT).show();
                    updateStatus();
                } else {
                    Toast.makeText(this, "配置恢复失败", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("暂不恢复", null);
            builder.show();
        }

        // 标记已启动过
        editor.putBoolean("has_launched", true);
        editor.apply();
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

        // 检查更新按钮
        Button btnUpdate = findViewById(R.id.btnUpdate);
        btnUpdate.setOnClickListener(v -> {
            if (updateManager != null) {
                updateManager.checkForUpdate(true);
            }
        });

        // 启动时自动检查更新（每天一次）
        checkAutoUpdate();
    }

    private void checkAutoUpdate() {
        long lastCheck = prefs.getLong("last_update_check", 0);
        long now = System.currentTimeMillis();
        // 24小时检查一次
        if (now - lastCheck > 24 * 60 * 60 * 1000) {
            if (updateManager != null) {
                updateManager.checkForUpdate(false);
            }
            editor.putLong("last_update_check", now);
            editor.apply();
        }
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
        TextView configStatusText = view.findViewById(R.id.configStatusText);
        Button btnExportConfig = view.findViewById(R.id.btnExportConfig);
        Button btnImportConfig = view.findViewById(R.id.btnImportConfig);
        Button btnPermissionOverlay = view.findViewById(R.id.btnPermissionOverlay);
        Button btnPermissionNotification = view.findViewById(R.id.btnPermissionNotification);

        Button btnIgnoreBattery = view.findViewById(R.id.btnIgnoreBattery);
        Button btnAutoStart = view.findViewById(R.id.btnAutoStart);
        Button btnAppSettings = view.findViewById(R.id.btnAppSettings);
        Button btnInstallPermission = view.findViewById(R.id.btnInstallPermission);
        CheckBox cbWakeScreen = view.findViewById(R.id.cbWakeScreen);
        CheckBox cbShowContentOnLockScreen = view.findViewById(R.id.cbShowContentOnLockScreen);
        CheckBox cbAlertEveryTime = view.findViewById(R.id.cbAlertEveryTime);
        Button btnSaveLockScreen = view.findViewById(R.id.btnSaveLockScreen);

        RadioGroup rgPopupDuration = view.findViewById(R.id.rgPopupDuration);
        RadioButton rbDurationForever = view.findViewById(R.id.rbDurationForever);
        RadioButton rbDuration10s = view.findViewById(R.id.rbDuration10s);
        RadioButton rbDurationCustom = view.findViewById(R.id.rbDurationCustom);
        EditText etCustomDuration = view.findViewById(R.id.etCustomDuration);
        Button btnSaveDuration = view.findViewById(R.id.btnSaveDuration);

        // 加载已保存的API Key
        String savedKey = prefs.getString("glm_api_key", "");
        if (!savedKey.isEmpty()) {
            String maskedKey = savedKey.substring(0, Math.min(8, savedKey.length())) + "..." +
                    savedKey.substring(Math.max(0, savedKey.length() - 4));
            apiKeyInput.setHint("已设置: " + maskedKey);
        }

        // 加载已保存的弹窗持续时间
        int popupDuration = prefs.getInt("messagePopupDuration", 10);
        if (popupDuration == -1) {
            rbDurationForever.setChecked(true);
            etCustomDuration.setVisibility(View.GONE);
        } else if (popupDuration == 10) {
            rbDuration10s.setChecked(true);
            etCustomDuration.setVisibility(View.GONE);
        } else {
            rbDurationCustom.setChecked(true);
            etCustomDuration.setVisibility(View.VISIBLE);
            etCustomDuration.setText(String.valueOf(popupDuration));
        }

        rgPopupDuration.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbDurationCustom) {
                etCustomDuration.setVisibility(View.VISIBLE);
            } else {
                etCustomDuration.setVisibility(View.GONE);
            }
        });

        // 更新配置备份状态
        if (configManager.hasBackupConfig()) {
            configStatusText.setText("✅ 已找到备份配置\n位置: " + configManager.getConfigFilePath());
            configStatusText.setTextColor(getColor(android.R.color.holo_green_dark));
        } else {
            configStatusText.setText("❌ 未找到备份配置");
            configStatusText.setTextColor(getColor(android.R.color.darker_gray));
        }

        AlertDialog dialog = builder.create();

        // 保存弹窗持续时间
        btnSaveDuration.setOnClickListener(v -> {
            int duration;
            int checkedId = rgPopupDuration.getCheckedRadioButtonId();
            if (checkedId == R.id.rbDurationForever) {
                duration = -1;
            } else if (checkedId == R.id.rbDuration10s) {
                duration = 10;
            } else {
                String customStr = etCustomDuration.getText().toString().trim();
                if (customStr.isEmpty()) {
                    Toast.makeText(this, "请输入自定义秒数", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    duration = Integer.parseInt(customStr);
                    if (duration < 1) {
                        Toast.makeText(this, "秒数必须大于 0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            editor.putInt("messagePopupDuration", duration);
            editor.apply();
            Toast.makeText(this, "弹窗持续时间已保存", Toast.LENGTH_SHORT).show();
        });

        // API Key 按钮
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
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://open.bigmodel.cn/usercenter/apikeys"));
            startActivity(intent);
        });

        // 配置备份按钮
        btnExportConfig.setOnClickListener(v -> {
            if (configManager.exportConfig()) {
                Toast.makeText(this, "配置已导出到下载目录！", Toast.LENGTH_LONG).show();
                configStatusText.setText("✅ 已找到备份配置\n位置: " + configManager.getConfigFilePath());
                configStatusText.setTextColor(getColor(android.R.color.holo_green_dark));
            } else {
                Toast.makeText(this, "配置导出失败，请检查存储权限", Toast.LENGTH_SHORT).show();
            }
        });

        btnImportConfig.setOnClickListener(v -> {
            if (configManager.hasBackupConfig()) {
                if (configManager.importConfig()) {
                    Toast.makeText(this, "配置恢复成功！", Toast.LENGTH_SHORT).show();
                    updateStatus();
                    dialog.dismiss();
                } else {
                    Toast.makeText(this, "配置恢复失败", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "未找到备份配置，请先导出", Toast.LENGTH_SHORT).show();
            }
        });

        // 权限设置按钮
        btnPermissionOverlay.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                Toast.makeText(this, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show();
            }
        });

        btnPermissionNotification.setOnClickListener(v -> {
            if (!isNotificationServiceEnabled()) {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                startActivity(intent);
            } else {
                Toast.makeText(this, "通知监听权限已开启", Toast.LENGTH_SHORT).show();
            }
        });

        // 加载锁屏通知设置
        boolean wakeScreen = prefs.getBoolean("wakeScreenOnMessage", true);
        boolean showContentOnLockScreen = prefs.getBoolean("showContentOnLockScreen", true);
        boolean alertEveryTime = prefs.getBoolean("alertEveryTime", true);
        cbWakeScreen.setChecked(wakeScreen);
        cbShowContentOnLockScreen.setChecked(showContentOnLockScreen);
        cbAlertEveryTime.setChecked(alertEveryTime);

        // 后台运行设置按钮
        btnIgnoreBattery.setOnClickListener(v -> {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Toast.makeText(this, "已忽略电池优化", Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });

        btnAutoStart.setOnClickListener(v -> {
            try {
                // 尝试打开各厂商的自启动管理页面
                Intent intent = new Intent();
                String manufacturer = Build.MANUFACTURER.toLowerCase();

                if (manufacturer.contains("xiaomi")) {
                    // 小米
                    intent.setComponent(new ComponentName("com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
                    // 华为/荣耀
                    intent.setComponent(new ComponentName("com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
                } else if (manufacturer.contains("oppo")) {
                    // OPPO
                    intent.setComponent(new ComponentName("com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
                } else if (manufacturer.contains("vivo")) {
                    // vivo
                    intent.setComponent(new ComponentName("com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
                } else if (manufacturer.contains("meizu")) {
                    // 魅族
                    intent.setComponent(new ComponentName("com.meizu.safe",
                            "com.meizu.safe.security.SHOW_APPSEC"));
                    intent.addCategory(Intent.CATEGORY_DEFAULT);
                    intent.putExtra("packageName", getPackageName());
                } else if (manufacturer.contains("samsung")) {
                    // 三星
                    intent.setComponent(new ComponentName("com.samsung.android.lool",
                            "com.samsung.android.sm.ui.battery.BatteryActivity"));
                } else {
                    // 其他品牌，打开应用详情页
                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                }

                startActivity(intent);
            } catch (Exception e) {
                // 如果打开失败，提示用户手动设置
                Toast.makeText(this, "无法自动打开，请在系统设置中手动允许自启动", Toast.LENGTH_LONG).show();
                // 打开应用详情页作为备选
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });

        btnAppSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });

        btnInstallPermission.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (getPackageManager().canRequestPackageInstalls()) {
                    Toast.makeText(this, "已允许安装未知来源应用", Toast.LENGTH_SHORT).show();
                } else {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            } else {
                Toast.makeText(this, "Android 8.0 以下无需此权限", Toast.LENGTH_SHORT).show();
            }
        });

        // 保存锁屏通知设置
        btnSaveLockScreen.setOnClickListener(v -> {
            editor.putBoolean("wakeScreenOnMessage", cbWakeScreen.isChecked());
            editor.putBoolean("showContentOnLockScreen", cbShowContentOnLockScreen.isChecked());
            editor.putBoolean("alertEveryTime", cbAlertEveryTime.isChecked());
            editor.apply();
            Toast.makeText(this, "锁屏通知设置已保存", Toast.LENGTH_SHORT).show();
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

        // 加载已保存的联系人（需要创建可修改的副本）
        Set<String> contacts = new HashSet<>(prefs.getStringSet("monitoredContacts", new HashSet<>()));
        List<String> contactListData = new ArrayList<>(contacts);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, contactListData);
        contactList.setAdapter(adapter);

        // 添加联系人
        btnAddContact.setOnClickListener(v -> {
            String contact = contactInput.getText().toString().trim();
            if (!contact.isEmpty()) {
                if (contacts.contains(contact)) {
                    Toast.makeText(this, "该联系人已存在", Toast.LENGTH_SHORT).show();
                } else {
                    contacts.add(contact);
                    contactListData.add(contact);
                    adapter.notifyDataSetChanged();
                    contactInput.setText("");
                    Toast.makeText(this, "已添加：" + contact, Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 长按删除联系人
        contactList.setOnItemLongClickListener((parent, view1, position, id) -> {
            String contactToDelete = contactListData.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("删除联系人")
                    .setMessage("确定要删除「" + contactToDelete + "」吗？")
                    .setPositiveButton("删除", (dialog1, which) -> {
                        contacts.remove(contactToDelete);
                        contactListData.remove(position);
                        adapter.notifyDataSetChanged();
                        Toast.makeText(this, "已删除：" + contactToDelete, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateManager != null) {
            updateManager.cleanup();
        }
    }

    public interface GLMCallback {
        void onResponse(String response);
    }
}
