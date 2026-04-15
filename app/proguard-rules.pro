# ProGuard rules for KimiClaw
# Keep all public classes
-keep public class * { public *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep setters and getters
-keepclassmembers class * {
    void set*(***);
    *** get*();
}
