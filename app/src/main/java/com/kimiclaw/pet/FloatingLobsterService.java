package com.kimiclaw.pet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

public class FloatingLobsterService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private TextView lobsterView;
    private TextView speechBubble;
    private TextView hungerIndicator;
    private FrameLayout floatingContainer;

    private WindowManager.LayoutParams params;
    private int screenWidth, screenHeight;
    private float initialX, initialY;
    private float touchX, touchY;

    private Handler handler;
    private Random random;
    private SharedPreferences prefs;

    private static final int LOBSTER_SIZE = 120;
    private static final String CHANNEL_ID = "KimiClawChannel";

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
                1f, 1.3f, 1f, 1.3f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scale.setDuration(200);
        scale.setRepeatMode(Animation.REVERSE);
        scale.setRepeatCount(1);
        lobsterView.startAnimation(scale);

        // 显示对话
        showSpeech("钳！");

        // 随机切换表情
        if (random.nextInt(3) == 0) {
            lobsterView.setText(LOBSTER_EMOJIS[random.nextInt(LOBSTER_EMOJIS.length)]);
        }
    }

    private void showSpeech(String text) {
        speechBubble.setText(text);
        speechBubble.setVisibility(View.VISIBLE);

        handler.postDelayed(() -> {
            if (speechBubble != null) {
                speechBubble.setVisibility(View.GONE);
            }
        }, 3000);
    }

    private void startCrawlingAnimation() {
        Runnable crawlRunnable = new Runnable() {
            @Override
            public void run() {
                if (floatingView == null || !floatingView.isAttachedToWindow()) return;

                // 随机移动
                int moveDistance = 50 + random.nextInt(150);
                int direction = random.nextInt(4); // 0:上 1:下 2:左 3:右

                int targetX = params.x;
                int targetY = params.y;

                switch (direction) {
                    case 0: // 上
                        targetY = Math.max(0, params.y - moveDistance);
                        break;
                    case 1: // 下
                        targetY = Math.min(screenHeight - LOBSTER_SIZE, params.y + moveDistance);
                        break;
                    case 2: // 左
                        targetX = Math.max(0, params.x - moveDistance);
                        lobsterView.setScaleX(-1); // 翻转
                        break;
                    case 3: // 右
                        targetX = Math.min(screenWidth - LOBSTER_SIZE, params.x + moveDistance);
                        lobsterView.setScaleX(1);
                        break;
                }

                // 动画移动
                animateMove(targetX, targetY);

                // 随机说话
                if (random.nextInt(5) == 0) {
                    showSpeech(SPEECHES[random.nextInt(SPEECHES.length)]);
                }

                // 下次移动
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

                // 饥饿时显示提示
                if (hunger < 30) {
                    hungerIndicator.setVisibility(View.VISIBLE);
                    if (random.nextInt(3) == 0) {
                        showSpeech("我饿了！🍤");
                    }
                } else {
                    hungerIndicator.setVisibility(View.GONE);
                }

                handler.postDelayed(this, 60000); // 每分钟减少
            }
        };

        handler.postDelayed(hungerRunnable, 60000);
    }

    private BroadcastReceiver feedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 喂食动画
            ScaleAnimation scale = new ScaleAnimation(
                    1f, 1.5f, 1f, 1.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
            );
            scale.setDuration(300);
            scale.setRepeatMode(Animation.REVERSE);
            scale.setRepeatCount(2);
            lobsterView.startAnimation(scale);

            showSpeech("好吃！😋");
            hungerIndicator.setVisibility(View.GONE);
        }
    };

    private BroadcastReceiver alertReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            if (message != null) {
                showSpeech(message);

                // 跳动动画
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

    // 消息提醒方法（由MessageMonitorService调用）
    public void showMessageAlert(String sender, String content) {
        handler.post(() -> {
            showSpeech("📱 " + sender + "发来消息！");

            // 跳动动画
            TranslateAnimation jump = new TranslateAnimation(
                    0, 0, 0, -50
            );
            jump.setDuration(300);
            jump.setRepeatMode(Animation.REVERSE);
            jump.setRepeatCount(3);
            lobsterView.startAnimation(jump);
        });
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
        unregisterReceiver(feedReceiver);
        unregisterReceiver(alertReceiver);
        handler.removeCallbacksAndMessages(null);
    }
}
