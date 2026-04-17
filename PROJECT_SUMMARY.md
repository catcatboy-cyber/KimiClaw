# 🦞 KimiClaw 小龙虾 - 项目总结

## 项目概述

**KimiClaw** 是一款Android桌面悬浮电子宠物应用，用户可以在手机桌面上养一只可爱的小龙虾，它可以：
- 在屏幕上自由爬动
- 与用户互动对话
- 接受喂食
- 监控社交App消息并提醒用户

## 核心功能实现

### 1. 悬浮窗系统 ⭐
**文件**: `FloatingLobsterService.java`

- 使用 `WindowManager` 创建全局悬浮窗
- `TYPE_APPLICATION_OVERLAY` 类型确保在所有应用上层显示
- `FLAG_NOT_FOCUSABLE` 和 `FLAG_NOT_TOUCH_MODAL` 实现穿透点击
- 支持拖动定位和触摸互动

### 2. 动画系统 🎬

**爬动动画**:
- 使用 `ValueAnimator` 实现平滑移动
- 随机方向和距离，模拟真实爬动
- 边界检测防止爬出屏幕

**互动动画**:
- 点击时的缩放动画 (`ScaleAnimation`)
- 消息提醒时的跳动动画 (`TranslateAnimation`)
- 喂食时的变大动画

### 3. 喂养系统 🍤
**文件**: `MainActivity.java`, `FloatingLobsterService.java`

- 使用 `SharedPreferences` 存储饥饿度
- 每分钟自动减少饥饿度
- 低饥饿度时显示提示和表情变化
- 喂食增加饥饿度并播放动画

### 4. AI对话系统 💬
**文件**: `MainActivity.java`

- 弹窗式对话界面
- 基于关键词的简单AI回复
- 支持中文自然语言处理
- 对话历史显示

### 5. 消息监控系统 📱
**文件**: `MessageMonitorService.java`

- 继承 `NotificationListenerService`
- 监控系统通知栏消息
- 支持微信、QQ、微博、钉钉等主流社交App
- 可配置监控指定联系人
- 匹配成功后直接发送广播通知悬浮窗显示提醒

## 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                      应用层 (UI)                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  MainActivity │  │  ChatDialog  │  │ MonitorDialog│  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
├─────────────────────────────────────────────────────────┤
│                      服务层 (Service)                    │
│  ┌──────────────────┐  ┌──────────────────────────┐    │
│  │FloatingLobster   │  │  MessageMonitorService   │    │
│  │Service (悬浮窗)   │  │  (通知监听+消息转发)      │    │
│  └──────────────────┘  └──────────────────────────┘    │
├─────────────────────────────────────────────────────────┤
│                      数据层 (Data)                       │
│  ┌──────────────────────────────────────────────────┐  │
│  │         SharedPreferences (本地存储)              │  │
│  │  - 饥饿度、等级、心情                              │  │
│  │  - 监控联系人列表                                 │  │
│  │  - 应用设置                                      │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## 权限系统

| 权限 | 用途 | 必需 |
|------|------|------|
| `SYSTEM_ALERT_WINDOW` | 悬浮窗显示 | ✅ 是 |
| `FOREGROUND_SERVICE` | 前台服务 | ✅ 是 |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | 消息监控 | ❌ 否 |
| `INTERNET` | 网络功能 | ❌ 否 |

## 项目文件清单

```
KimiClawPet/
├── app/
│   ├── src/main/
│   │   ├── java/com/kimiclaw/pet/
│   │   │   ├── MainActivity.java              # 主界面
│   │   │   ├── FloatingLobsterService.java    # 悬浮窗服务
│   │   │   └── MessageMonitorService.java     # 消息监控服务
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml          # 主界面布局
│   │   │   │   ├── floating_lobster.xml       # 悬浮窗布局
│   │   │   │   ├── dialog_chat.xml            # 对话弹窗
│   │   │   │   └── dialog_monitor.xml         # 监控设置弹窗
│   │   │   ├── drawable/
│   │   │   │   ├── card_bg.xml                # 卡片背景
│   │   │   │   ├── bubble_bg.xml              # 气泡背景
│   │   │   │   ├── dialog_bg.xml              # 对话框背景
│   │   │   │   ├── input_bg.xml               # 输入框背景
│   │   │   │   ├── progress_hunger.xml        # 饥饿度进度条
│   │   │   │   └── ic_launcher_foreground.xml # 图标前景
│   │   │   ├── values/
│   │   │   │   ├── strings.xml                # 字符串
│   │   │   │   ├── colors.xml                 # 颜色
│   │   │   │   └── themes.xml                 # 主题
│   │   │   └── mipmap-anydpi-v26/
│   │   │       ├── ic_launcher.xml            # 启动图标
│   │   │       └── ic_launcher_round.xml      # 圆形图标
│   │   └── AndroidManifest.xml                # 应用清单
│   ├── build.gradle                           # 模块构建配置
│   └── proguard-rules.pro                     # 混淆规则
├── build.gradle                               # 项目构建配置
├── settings.gradle                            # 项目设置
├── gradle.properties                          # Gradle配置
├── build.sh                                   # 构建脚本
├── README.md                                  # 使用说明
└── PROJECT_SUMMARY.md                         # 项目总结
```

## 构建说明

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- Android SDK 34
- Gradle 8.0+

### 构建命令
```bash
# 使用Android Studio
Build → Build Bundle(s) / APK(s) → Build APK(s)

# 使用命令行
./gradlew assembleDebug
```

### 输出位置
```
app/build/outputs/apk/debug/app-debug.apk
```

## 后续优化方向

### 功能增强
- [ ] 更多宠物形象（螃蟹、章鱼等）
- [ ] 宠物成长系统
- [ ] 更多互动小游戏
- [ ] 在线AI对话（接入ChatGPT等）
- [ ] 消息自动回复功能
- [ ] 主题换肤

### 性能优化
- [ ] 动画性能优化
- [ ] 内存占用优化
- [ ] 电池消耗优化
- [ ] 启动速度优化

### 兼容性
- [ ] 适配更多Android版本
- [ ] 适配折叠屏手机
- [ ] 适配平板设备
- [ ] 适配不同分辨率

## 许可证

MIT License - 自由使用和修改

---

**Created with ❤️ and 🦞**
