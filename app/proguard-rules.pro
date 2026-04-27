# ProGuard rules for KimiClaw

# 保留 Service 类（Android 系统需要反射调用）
-keep public class * extends android.app.Service
-keep public class * extends android.service.notification.NotificationListenerService

# 保留 BroadcastReceiver
-keep public class * extends android.content.BroadcastReceiver

# 保留自定义 View
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留 Parcelable 实现
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保留 Serializable 类
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保留 R 类的所有字段
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 保留注解
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# 保留行号信息（用于调试崩溃日志）
-keepattributes SourceFile,LineNumberTable

# AndroidX 相关
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# Material Design
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# 保留项目核心类（避免过度混淆导致功能异常）
-keep class com.kimiclaw.pet.MainActivity { *; }
-keep class com.kimiclaw.pet.FloatingLobsterService { *; }
-keep class com.kimiclaw.pet.MessageMonitorService { *; }
-keep class com.kimiclaw.pet.UpdateManager { *; }
-keep class com.kimiclaw.pet.LobsterView { *; }
-keep class com.kimiclaw.pet.MessageItem { *; }
-keep class com.kimiclaw.pet.MessagePopupAdapter { *; }

# 保留 FileProvider
-keep class androidx.core.content.FileProvider { *; }

# 移除日志（Release 版本）
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# 优化配置
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
