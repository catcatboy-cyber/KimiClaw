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
import android.util.Log;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class FloatingLobsterService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private LobsterView lobsterView;
    private TextView speechBubble;
    private TextView hungerIndicator;
    private FrameLayout floatingContainer;
    private PopupWindow menuPopup;
    private View messagePopupView;
    private WindowManager.LayoutParams messagePopupParams;

    private int petSize;
    private int bubbleHeight;
    private int bubbleMargin = 10;

    private WindowManager.LayoutParams params;
    private int screenWidth, screenHeight;
    private float initialX, initialY;
    private float touchX, touchY;

    private Handler handler;
    private Random random;
    private SharedPreferences prefs;

    private static final int LOBSTER_SIZE = 140;
    private static final int MESSAGE_POPUP_WIDTH_DP = 320;
    private static final String CHANNEL_ID = "KimiClawChannel";
    private static final String LOCK_SCREEN_CHANNEL_ID = "KimiClawLockScreenChannel";
    private static final String TAG = "FloatingLobster";

    // 消息队列：支持多App、多联系人、多条消息
    private final List<MessageItem> messageQueue = new ArrayList<>();
    private MessagePopupAdapter messageAdapter;
    private TextView tvTitle;
    private RecyclerView rvMessages;
    private static final int MAX_MESSAGE_QUEUE_SIZE = 20;
    private Runnable messagePopupDismissRunnable;
    private int lastNotificationId = -1;

    // 当前状态
    private enum LobsterState { NORMAL, EATING, HUNGRY, SAD }
    private LobsterState currentState = LobsterState.NORMAL;

    // 台词
    private final String[] SPEECHES_NORMAL = {
        "嗨！", "在呢！", "好无聊啊", "钳钳~", "主人好~"
    };
    private final String[] SPEECHES_HUNGRY = {
        "我饿了...", "🍤 想吃虾虾", "肚子咕咕叫", "给点吃的吧~", "好饿呀"
    };
    private final String[] SPEECHES_SAD = {
        "不开心...", "呜呜...", "想被摸摸", "好孤单", "😢"
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
        startStateCheck();
        startCrawlingAnimation();
        startHungerDecrease();

        registerReceiver(feedReceiver, new IntentFilter("com.kimiclaw.pet.FEED"));
        registerReceiver(alertReceiver, new IntentFilter("com.kimiclaw.pet.SHOW_ALERT"));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);

            // 前台服务通知渠道
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "KimiClaw Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            manager.createNotificationChannel(serviceChannel);

            // 锁屏消息提醒渠道（高优先级，可亮屏）
            NotificationChannel lockScreenChannel = new NotificationChannel(
                    LOCK_SCREEN_CHANNEL_ID,
                    "锁屏消息提醒",
                    NotificationManager.IMPORTANCE_HIGH
            );
            lockScreenChannel.setDescription("锁屏时收到监控联系人消息的通知提醒");
            lockScreenChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(lockScreenChannel);
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

        // 初始化尺寸（dp转px）
        petSize = (int) (100 * getResources().getDisplayMetrics().density);
        bubbleHeight = (int) (60 * getResources().getDisplayMetrics().density);
        bubbleMargin = (int) (10 * getResources().getDisplayMetrics().density);

        // 设置初始状态
        updateLobsterState();

        Point size = new Point();
        windowManager.getDefaultDisplay().getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;

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

    /**
     * 更新龙虾状态
     */
    private void updateLobsterState() {
        if (lobsterView != null) {
            switch (currentState) {
                case EATING:
                    lobsterView.setState(LobsterView.State.EATING);
                    break;
                case HUNGRY:
                    lobsterView.setState(LobsterView.State.HUNGRY);
                    break;
                case SAD:
                    lobsterView.setState(LobsterView.State.SAD);
                    break;
                default:
                    lobsterView.setState(LobsterView.State.NORMAL);
                    break;
            }
        }
    }

    /**
     * 定时检查状态，自动切换动画
     */
    private void startStateCheck() {
        Runnable stateRunnable = new Runnable() {
            @Override
            public void run() {
                if (floatingView == null || !floatingView.isAttachedToWindow()) return;

                int hunger = prefs.getInt("hunger", 50);
                int mood = prefs.getInt("mood", 50);

                LobsterState newState = LobsterState.NORMAL;

                if (hunger < 30) {
                    newState = LobsterState.HUNGRY;
                } else if (mood < 30) {
                    newState = LobsterState.SAD;
                }

                if (newState != currentState) {
                    currentState = newState;
                    updateLobsterState();
                }

                handler.postDelayed(this, 2000);
            }
        };
        handler.postDelayed(stateRunnable, 2000);
    }

    private void onLobsterClicked() {
        ScaleAnimation scale = new ScaleAnimation(
                1f, 1.15f, 1f, 1.15f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scale.setDuration(150);
        scale.setRepeatMode(Animation.REVERSE);
        scale.setRepeatCount(1);
        lobsterView.startAnimation(scale);

        showMenuPopup();
    }

    private void showMenuPopup() {
        if (menuPopup != null && menuPopup.isShowing()) {
            menuPopup.dismiss();
            return;
        }

        View menuView = LayoutInflater.from(this).inflate(R.layout.lobster_menu, null);

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

        menuPopup = new PopupWindow(
                menuView,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                true
        );

        menuPopup.setBackgroundDrawable(getResources().getDrawable(R.drawable.menu_bg));
        menuPopup.setElevation(20);

        // 使用 showAsDropDown 让菜单跟随龙虾，显示在上方
        // x偏移：居中；y偏移：在龙虾上方
        int menuWidth = 240;
        int xOffset = (LOBSTER_SIZE - menuWidth) / 2;
        menuPopup.showAsDropDown(floatingView, xOffset, -320);
    }

    private void feedLobster() {
        int hunger = prefs.getInt("hunger", 50);
        hunger = Math.min(100, hunger + 25);
        prefs.edit().putInt("hunger", hunger).apply();

        currentState = LobsterState.EATING;
        updateLobsterState();

        handler.postDelayed(() -> {
            currentState = LobsterState.NORMAL;
            updateLobsterState();
        }, 2000);

        ScaleAnimation scale = new ScaleAnimation(
                1f, 1.25f, 1f, 1.25f,
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
        TranslateAnimation shake = new TranslateAnimation(
                -8, 8, 0, 0
        );
        shake.setDuration(80);
        shake.setRepeatMode(Animation.REVERSE);
        shake.setRepeatCount(6);
        lobsterView.startAnimation(shake);

        String[] petReplies = {
            "好舒服~ 😊", "喜欢被摸！", "好开心！", "主人最好了！", "钳钳~ ❤️"
        };
        showSpeech(petReplies[random.nextInt(petReplies.length)]);

        int mood = prefs.getInt("mood", 50);
        mood = Math.min(100, mood + 15);
        prefs.edit().putInt("mood", mood).apply();
    }

    private void openChatDialog() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("openChat", true);
        startActivity(intent);
    }

    private void openSettings() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("openSettings", true);
        startActivity(intent);
    }

    private void showMessagePopup(String sender, String content, String packageName, PendingIntent contentIntent) {
        // 防空指针
        String safeSender = sender != null ? sender : "未知";
        String safeContent = content != null ? content : "";
        String safePackage = packageName != null ? packageName : "";

        // 添加到消息队列（去重：同一联系人+同一App更新并置顶）
        MessageItem newItem = new MessageItem(safeSender, safeContent, safePackage, contentIntent, System.currentTimeMillis());
        addOrUpdateMessage(newItem);

        // 如果弹窗已显示，刷新列表并重新计时自动关闭
        if (messagePopupView != null && messagePopupView.getParent() != null) {
            refreshMessagePopup();
            scheduleMessagePopupDismiss();
            return;
        }

        // 移除旧弹窗（如果有）
        dismissMessagePopupInternal();

        // 创建消息弹窗视图
        messagePopupView = LayoutInflater.from(this).inflate(R.layout.message_popup, null);

        tvTitle = messagePopupView.findViewById(R.id.tvTitle);
        rvMessages = messagePopupView.findViewById(R.id.rvMessages);
        TextView btnDismissAll = messagePopupView.findViewById(R.id.btnDismissAll);

        // 检测锁屏和屏幕状态
        android.app.KeyguardManager keyguardManager = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isLocked = keyguardManager != null && keyguardManager.isKeyguardLocked();
        boolean isScreenOn = powerManager != null && powerManager.isInteractive();
        Log.d(TAG, "showMessagePopup: isLocked=" + isLocked + ", isScreenOn=" + isScreenOn);

        // 锁屏时点亮屏幕
        boolean wakeScreen = prefs.getBoolean("wakeScreenOnMessage", true);
        if (isLocked && wakeScreen && powerManager != null) {
            try {
                // 去掉 !isScreenOn 限制：即使屏幕已亮，也保持亮屏状态确保弹窗可见
                android.os.PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                        android.os.PowerManager.FULL_WAKE_LOCK
                                | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP
                                | android.os.PowerManager.ON_AFTER_RELEASE,
                        "KimiClaw:MessageWakeLock"
                );
                wakeLock.acquire(5000);
                Log.d(TAG, "WakeLock acquired for 5000ms, isScreenOn=" + isScreenOn);
            } catch (Exception e) {
                Log.e(TAG, "Failed to acquire WakeLock", e);
            }
        }

        // 锁屏时是否显示消息内容
        boolean showContentOnLockScreen = prefs.getBoolean("showContentOnLockScreen", true);
        boolean hideContent = isLocked && !showContentOnLockScreen;

        // 锁屏时发送系统通知兜底（弹窗可能被ROM拦截，通知更可靠）
        if (isLocked) {
            sendLockScreenNotification(safeSender, safeContent, safePackage);
        }

        // 设置RecyclerView
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new MessagePopupAdapter(messageQueue, new MessagePopupAdapter.OnMessageActionListener() {
            @Override
            public void onItemClick(MessageItem item, int position) {
                openAppWithContentIntent(item.packageName, item.contentIntent);
                dismissMessage(position);
            }

            @Override
            public void onDismissClick(MessageItem item, int position) {
                dismissMessage(position);
            }
        }, hideContent);
        rvMessages.setAdapter(messageAdapter);

        refreshMessagePopup();

        // 忽略全部
        btnDismissAll.setOnClickListener(v -> dismissAllMessages());

        // 使用独立的 WindowManager 窗口，彻底和小龙虾解耦
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

        // 锁屏时允许显示在锁屏界面并点亮屏幕
        if (isLocked) {
            windowFlags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
            windowFlags |= WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
            windowFlags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            Log.d(TAG, "Window flags: SHOW_WHEN_LOCKED + TURN_SCREEN_ON + KEEP_SCREEN_ON");
        }

        messagePopupParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                windowFlags,
                PixelFormat.TRANSLUCENT
        );
        messagePopupParams.gravity = Gravity.TOP | Gravity.START;

        int popupWidthPx = (int) (MESSAGE_POPUP_WIDTH_DP * getResources().getDisplayMetrics().density);
        int marginPx = (int) (12 * getResources().getDisplayMetrics().density);

        int statusBarHeight = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }

        messagePopupParams.x = screenWidth - popupWidthPx - marginPx;
        messagePopupParams.y = statusBarHeight + marginPx;

        try {
            windowManager.addView(messagePopupView, messagePopupParams);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add message popup window", e);
            messagePopupView = null;
            return;
        }

        scheduleMessagePopupDismiss();
    }

    /**
     * 安全移除消息弹窗视图
     */
    private void dismissMessagePopupInternal() {
        if (messagePopupView != null) {
            try {
                if (messagePopupView.getParent() != null) {
                    windowManager.removeView(messagePopupView);
                }
            } catch (Exception e) {
                Log.e(TAG, "remove message popup error", e);
            }
            messagePopupView = null;
        }
    }

    /**
     * 根据设置调度消息弹窗的自动关闭任务
     */
    private void scheduleMessagePopupDismiss() {
        // 取消之前的自动关闭任务
        if (messagePopupDismissRunnable != null) {
            handler.removeCallbacks(messagePopupDismissRunnable);
        }

        int durationSeconds = prefs.getInt("messagePopupDuration", 10);
        if (durationSeconds <= 0) {
            // -1 或 0 表示永久显示（手动关闭），不启动自动关闭
            return;
        }

        messagePopupDismissRunnable = () -> {
            dismissMessagePopupInternal();
        };
        handler.postDelayed(messagePopupDismissRunnable, durationSeconds * 1000L);
    }

    /**
     * 添加或更新消息到队列。同一联系人+同一App的消息会去重并置顶。
     */
    private void addOrUpdateMessage(MessageItem newItem) {
        String newKey = newItem.getKey();
        for (int i = 0; i < messageQueue.size(); i++) {
            MessageItem existing = messageQueue.get(i);
            if (existing.getKey().equals(newKey)) {
                // 更新内容、时间和contentIntent，并移到队首
                existing.content = newItem.content;
                existing.timestamp = newItem.timestamp;
                existing.contentIntent = newItem.contentIntent;
                messageQueue.remove(i);
                messageQueue.add(0, existing);
                return;
            }
        }
        // 新消息，添加到队首
        messageQueue.add(0, newItem);
        // 限制队列大小
        if (messageQueue.size() > MAX_MESSAGE_QUEUE_SIZE) {
            messageQueue.remove(messageQueue.size() - 1);
        }
    }

    /**
     * 刷新弹窗UI
     */
    private void refreshMessagePopup() {
        if (tvTitle != null) {
            tvTitle.setText("📱 " + messageQueue.size() + " 条新消息");
        }
        if (messageAdapter != null) {
            messageAdapter.notifyDataSetChanged();
        }
        // 注意：RecyclerView 已放在固定高度 280dp 的 FrameLayout 中，
        // 不需要再动态调整高度。PopupWindow 也无需 update()，
        // 否则在 WRAP_CONTENT 模式下可能导致窗口测量崩溃。
    }

    /**
     * 忽略单条消息
     */
    private void dismissMessage(int position) {
        if (position >= 0 && position < messageQueue.size()) {
            messageQueue.remove(position);
            if (messageQueue.isEmpty()) {
                dismissMessagePopupInternal();
                if (messagePopupDismissRunnable != null) {
                    handler.removeCallbacks(messagePopupDismissRunnable);
                }
            } else {
                refreshMessagePopup();
                scheduleMessagePopupDismiss();
            }
        }
    }

    /**
     * 忽略全部消息
     */
    private void dismissAllMessages() {
        messageQueue.clear();
        dismissMessagePopupInternal();
        if (messagePopupDismissRunnable != null) {
            handler.removeCallbacks(messagePopupDismissRunnable);
        }
    }

    private void openAppWithContentIntent(String packageName, PendingIntent contentIntent) {
        if (contentIntent != null) {
            try {
                contentIntent.send();
                return;
            } catch (Exception e) {
                Log.e(TAG, "contentIntent send failed", e);
            }
        }

        if ("com.tencent.mm".equals(packageName)) {
            openWeChat();
        } else if ("com.tencent.mobileqq".equals(packageName)) {
            openQQ();
        } else {
            try {
                Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
                if (intent != null) {
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            } catch (Exception e) {
                Toast.makeText(this, "打开应用失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openWeChat() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                Toast.makeText(this, "未找到微信", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "打开微信失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void openQQ() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.tencent.mobileqq");
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                Toast.makeText(this, "未找到 QQ", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "打开 QQ 失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSpeech(String text) {
        // 设置冒泡文字
        speechBubble.setText(text);
        speechBubble.setVisibility(View.VISIBLE);

        // 计算新高度：宠物 + 冒泡 + 间距
        int newHeight = petSize + bubbleHeight + bubbleMargin;

        // 更新容器高度和位置（容器上移，让宠物保持在原位置）
        params.height = newHeight;
        params.y = params.y - bubbleHeight - bubbleMargin;

        // 确保不超出屏幕顶部
        if (params.y < 0) {
            params.y = 0;
        }

        windowManager.updateViewLayout(floatingView, params);

        // 4秒后隐藏冒泡
        handler.postDelayed(() -> {
            hideSpeech();
        }, 4000);
    }

    private void hideSpeech() {
        if (speechBubble != null) {
            speechBubble.setVisibility(View.GONE);

            // 恢复容器高度和位置
            params.height = petSize;
            params.y = params.y + bubbleHeight + bubbleMargin;

            windowManager.updateViewLayout(floatingView, params);
        }
    }

    private void startCrawlingAnimation() {
        Runnable crawlRunnable = new Runnable() {
            @Override
            public void run() {
                if (floatingView == null || !floatingView.isAttachedToWindow()) return;
                if (menuPopup != null && menuPopup.isShowing()) {
                    handler.postDelayed(this, 2000);
                    return;
                }

                int moveDistance = 40 + random.nextInt(120);
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
                    String[] speeches;
                    switch (currentState) {
                        case HUNGRY:
                            speeches = SPEECHES_HUNGRY;
                            break;
                        case SAD:
                            speeches = SPEECHES_SAD;
                            break;
                        default:
                            speeches = SPEECHES_NORMAL;
                    }
                    showSpeech(speeches[random.nextInt(speeches.length)]);
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
                hunger = Math.max(0, hunger - 3);
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
            String sender = intent.getStringExtra("sender");
            String content = intent.getStringExtra("content");
            String packageName = intent.getStringExtra("packageName");
            PendingIntent contentIntent = intent.getParcelableExtra("contentIntent");
            String message = intent.getStringExtra("message");

            if (sender != null && content != null) {
                // 显示消息弹窗
                showMessagePopup(sender, content, packageName, contentIntent);
                // 同时让龙虾说话
                showSpeech("📱 " + sender + " 发来消息！");
            } else if (message != null) {
                // 兼容旧格式
                showSpeech(message);
            }

            handler.post(() -> {
                TranslateAnimation jump = new TranslateAnimation(
                        0, 0, 0, -70
                );
                jump.setDuration(200);
                jump.setRepeatMode(Animation.REVERSE);
                jump.setRepeatCount(4);
                lobsterView.startAnimation(jump);
            });
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 锁屏时发送系统通知兜底
     * 弹窗在锁屏上可能被ROM拦截，通知是更可靠的标准机制
     */
    private void sendLockScreenNotification(String sender, String content, String packageName) {
        try {
            String appName = "消息";
            if ("com.tencent.mm".equals(packageName)) appName = "微信";
            else if ("com.tencent.mobileqq".equals(packageName)) appName = "QQ";
            else if ("com.sina.weibo".equals(packageName)) appName = "微博";
            else if ("com.alibaba.android.rimet".equals(packageName)) appName = "钉钉";

            // 根据设置决定是否显示消息内容
            boolean showContentOnLockScreen = prefs.getBoolean("showContentOnLockScreen", true);
            String displayText = showContentOnLockScreen ? content : "🔒 收到一条新消息";

            // 根据设置决定是否每次通知都触发亮屏/声音
            boolean alertEveryTime = prefs.getBoolean("alertEveryTime", true);

            // 点击通知：打开对应应用
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent == null) {
                launchIntent = new Intent(this, MainActivity.class);
            }
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
            );

            // 构建通知：每次都用唯一ID
            int notifyId = (int) System.currentTimeMillis();
            Notification.Builder builder = new Notification.Builder(this, LOCK_SCREEN_CHANNEL_ID)
                    .setContentTitle("📱 " + sender + "（" + appName + "）")
                    .setContentText(displayText)
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setOnlyAlertOnce(!alertEveryTime);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(LOCK_SCREEN_CHANNEL_ID);
            }

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            // 取消上一条通知，让系统重新触发声音/亮屏提醒
            if (lastNotificationId != -1) {
                notificationManager.cancel(lastNotificationId);
            }
            lastNotificationId = notifyId;
            notificationManager.notify(notifyId, builder.build());
            Log.d(TAG, "Lock screen notification sent: id=" + notifyId + ", sender=" + sender + ", showContent=" + showContentOnLockScreen + ", alertEveryTime=" + alertEveryTime);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send lock screen notification", e);
        }
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
        dismissMessagePopupInternal();
        if (messagePopupDismissRunnable != null) {
            handler.removeCallbacks(messagePopupDismissRunnable);
        }
        unregisterReceiver(feedReceiver);
        unregisterReceiver(alertReceiver);
        handler.removeCallbacksAndMessages(null);
    }
}
