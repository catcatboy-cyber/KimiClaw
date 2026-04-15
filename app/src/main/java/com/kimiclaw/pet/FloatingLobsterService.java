package com.kimiclaw.pet;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class FloatingLobsterService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private TextView lobsterView;
    private TextView speechBubble;
    private TextView hungerIndicator;
    private FrameLayout floatingContainer;
    private PopupWindow menuPopup;

    private WindowManager.LayoutParams params;
    private int screenWidth, screenHeight;
    private float initialX, initialY;
    private float touchX, touchY;

    private Handler handler;
    private Random random;
    private SharedPreferences prefs;

    private static final int LOBSTER_SIZE = 100;
    private static final String CHANNEL_ID = "KimiClawChannel";
    private static final String GLM_API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

    // 小龙虾表情集合
    private final String[] LOBSTER_EMOJIS = {"🦞", "🦀", "🦐", "🐙"};
    private final String[] SPEECHES = {
        "嗨！", "我饿了~", "在呢！", "有消息吗？", "好无聊啊", "🍤", "钳！"
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        random = new Random();
        prefs = getSharedPreferences("KimiClawPrefs", MODE_PRIVATE);

        createNotificationChannel();
        startForeground(1, createNotification());

        setupFloatingWindow();
        startCrawlingAnimation();
        startHungerDecrease();

        // 注册广播接收器
        registerReceiver(feedReceiver, new IntentFilter("com.kimiclaw.pet.FEED"));
        registerReceiver(alertReceiver, new IntentFilter("com.kimiclaw.pet.SHOW_ALERT"));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "KimiClaw Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("🦞 KimiClaw 小龙虾")
                .setContentText("正在桌面守护你...")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void setupFloatingWindow() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_lobster, null);
        lobsterView = floatingView.findViewById(R.id.lobsterView);
        speechBubble = floatingView.findViewById(R.id.speechBubble);
        hungerIndicator = floatingView.findViewById(R.id.hungerIndicator);
        floatingContainer = floatingView.findViewById(R.id.floatingContainer);

        // 获取屏幕尺寸
        Point size = new Point();
        windowManager.getDefaultDisplay().getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;

        // 设置悬浮窗参数
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                LOBSTER_SIZE,
                LOBSTER_SIZE,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = screenWidth / 2 - LOBSTER_SIZE / 2;
        params.y = screenHeight / 2 - LOBSTER_SIZE / 2;

        // 触摸事件处理
        floatingView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    params.x = (int) (initialX + (event.getRawX() - touchX));
                    params.y = (int) (initialY + (event.getRawY() - touchY));
                    windowManager.updateViewLayout(floatingView, params);
                    return true;

                case MotionEvent.ACTION_UP:
                    // 点击事件
                    float deltaX = event.getRawX() - touchX;
                    float deltaY = event.getRawY() - touchY;
                    if (Math.abs(deltaX) < 10 && Math.abs(deltaY) < 10) {
                        onLobsterClicked();
                    }
                    return true;
            }
            return false;
        });

        windowManager.addView(floatingView, params);
    }

    private void onLobsterClicked() {
        // 点击动画
        ScaleAnimation scale = new ScaleAnimation(
                1f, 1.2f, 1f, 1.2f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scale.setDuration(150);
        scale.setRepeatMode(Animation.REVERSE);
        scale.setRepeatCount(1);
        lobsterView.startAnimation(scale);

        // 显示菜单
        showMenuPopup();
    }

    private void showMenuPopup() {
        // 如果菜单已显示，先关闭
        if (menuPopup != null && menuPopup.isShowing()) {
            menuPopup.dismiss();
            return;
        }

        // 创建菜单视图
        View menuView = LayoutInflater.from(this).inflate(R.layout.lobster_menu, null);

        // 设置按钮点击事件
        Button btnFeed = menuView.findViewById(R.id.btnMenuFeed);
        Button btnPet = menuView.findViewById(R.id.btnMenuPet);
        Button btnChat = menuView.findViewById(R.id.btnMenuChat);
        Button btnSettings = menuView.findViewById(R.id.btnMenuSettings);

        btnFeed.setOnClickListener(v -> {
            feedLobster();
            menuPopup.dismiss();
        });

        btnPet.setOnClickListener(v -> {
            petLobster();
            menuPopup.dismiss();
        });

        btnChat.setOnClickListener(v -> {
            openChatDialog();
            menuPopup.dismiss();
        });

        btnSettings.setOnClickListener(v -> {
            openSettings();
            menuPopup.dismiss();
        });

        // 创建PopupWindow
        menuPopup = new PopupWindow(
                menuView,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                true
        );

        menuPopup.setBackgroundDrawable(getResources().getDrawable(R.drawable.menu_bg));
        menuPopup.setElevation(20);

        // 计算菜单位置（在龙虾上方或下方）
        int[] location = new int[2];
        floatingView.getLocationOnScreen(location);
        int x = location[0] + LOBSTER_SIZE / 2 - 60;
        int y = location[1] - 280;

        if (y < 100) {
            y = location[1] + LOBSTER_SIZE + 20;
        }

        menuPopup.showAtLocation(floatingView, Gravity.NO_GRAVITY, x, y);
    }

    private void feedLobster() {
        int hunger = prefs.getInt("hunger", 50);
        hunger = Math.min(100, hunger + 25);
        prefs.edit().putInt("hunger", hunger).apply();

        // 喂食动画
        ScaleAnimation scale = new ScaleAnimation(
                1f, 1.4f, 1f, 1.4f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scale.setDuration(300);
        scale.setRepeatMode(Animation.REVERSE);
        scale.setRepeatCount(2);
        lobsterView.startAnimation(scale);

        showSpeech("好吃！😋 谢谢主人！");
        hungerIndicator.setVisibility(View.GONE);
    }

    private void petLobster() {
        // 抚摸动画
        TranslateAnimation shake = new TranslateAnimation(
                -10, 10, 0, 0
        );
        shake.setDuration(100);
        shake.setRepeatMode(Animation.REVERSE);
        shake.setRepeatCount(5);
        lobsterView.startAnimation(shake);

        // 随机心情回复
        String[] petReplies = {
            "好舒服~ 😊", "喜欢被摸！", "好开心！", "主人最好了！", "钳钳~ ❤️"
        };
        showSpeech(petReplies[random.nextInt(petReplies.length)]);

        // 增加心情值
        int mood = prefs.getInt("mood", 50);
        mood = Math.min(100, mood + 10);
        prefs.edit().putInt("mood", mood).apply();
    }

    private void openChatDialog() {
        // 打开主Activity的聊天对话框
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("openChat", true);
        startActivity(intent);
    }

    private void openSettings() {
        // 打开主Activity的设置页面
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("openSettings", true);
        startActivity(intent);
    }

    private void showSpeech(String text) {
        speechBubble.setText(text);
        speechBubble.setVisibility(View.VISIBLE);

        handler.postDelayed(() -> {
            if (speechBubble != null) {
                speechBubble.setVisibility(View.GONE);
            }
        }, 4000);
    }

    private void startCrawlingAnimation() {
        Runnable crawlRunnable = new Runnable() {
            @Override
            public void run() {
                if (floatingView == null || !floatingView.isAttachedToWindow()) return;

                // 如果菜单显示中，不移动
                if (menuPopup != null && menuPopup.isShowing()) {
                    handler.postDelayed(this, 2000);
                    return;
                }

                // 随机移动
                int moveDistance = 50 + random.nextInt(150);
                int direction = random.nextInt(4);

                int targetX = params.x;
                int targetY = params.y;

                switch (direction) {
                    case 0:
                        targetY = Math.max(0, params.y - moveDistance);
                        break;
                    case 1:
                        targetY = Math.min(screenHeight - LOBSTER_SIZE, params.y + moveDistance);
                        break;
                    case 2:
                        targetX = Math.max(0, params.x - moveDistance);
                        lobsterView.setScaleX(-1);
                        break;
                    case 3:
                        targetX = Math.min(screenWidth - LOBSTER_SIZE, params.x + moveDistance);
                        lobsterView.setScaleX(1);
                        break;
                }

                animateMove(targetX, targetY);

                if (random.nextInt(5) == 0) {
                    showSpeech(SPEECHES[random.nextInt(SPEECHES.length)]);
                }

                handler.postDelayed(this, 2000 + random.nextInt(3000));
            }
        };

        handler.postDelayed(crawlRunnable, 1000);
    }

    private void animateMove(int targetX, int targetY) {
        ValueAnimator animatorX = ValueAnimator.ofInt(params.x, targetX);
        ValueAnimator animatorY = ValueAnimator.ofInt(params.y, targetY);

        animatorX.setDuration(1000);
        animatorY.setDuration(1000);

        animatorX.addUpdateListener(animation -> {
            params.x = (int) animation.getAnimatedValue();
            windowManager.updateViewLayout(floatingView, params);
        });

        animatorY.addUpdateListener(animation -> {
            params.y = (int) animation.getAnimatedValue();
            windowManager.updateViewLayout(floatingView, params);
        });

        animatorX.start();
        animatorY.start();
    }

    private void startHungerDecrease() {
        Runnable hungerRunnable = new Runnable() {
            @Override
            public void run() {
                int hunger = prefs.getInt("hunger", 50);
                hunger = Math.max(0, hunger - 5);
                prefs.edit().putInt("hunger", hunger).apply();

                if (hunger < 30) {
                    hungerIndicator.setVisibility(View.VISIBLE);
                    if (random.nextInt(3) == 0) {
                        showSpeech("我饿了！🍤 快喂我！");
                    }
                } else {
                    hungerIndicator.setVisibility(View.GONE);
                }

                handler.postDelayed(this, 60000);
            }
        };

        handler.postDelayed(hungerRunnable, 60000);
    }

    private BroadcastReceiver feedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            feedLobster();
        }
    };

    private BroadcastReceiver alertReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            if (message != null) {
                showSpeech(message);

                handler.post(() -> {
                    TranslateAnimation jump = new TranslateAnimation(
                            0, 0, 0, -80
                    );
                    jump.setDuration(250);
                    jump.setRepeatMode(Animation.REVERSE);
                    jump.setRepeatCount(4);
                    lobsterView.startAnimation(jump);
                });
            }
        }
    };

    // GLM AI 对话方法
    public void chatWithGLM(String message, GLMCallback callback) {
        String apiKey = prefs.getString("glm_api_key", "");
        if (apiKey.isEmpty()) {
            callback.onResponse("请先设置 GLM API Key！");
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
                callback.onResponse("网络出问题了，检查一下API Key吧！");
            }
        }).start();
    }

    public interface GLMCallback {
        void onResponse(String response);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }
        if (menuPopup != null && menuPopup.isShowing()) {
            menuPopup.dismiss();
        }
        unregisterReceiver(feedReceiver);
        unregisterReceiver(alertReceiver);
        handler.removeCallbacksAndMessages(null);
    }
}
