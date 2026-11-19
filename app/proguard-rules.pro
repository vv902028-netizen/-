# ProGuard / R8 rules for SmartScreen (ButtonSaver)

# Keep Android system-bound components referenced from AndroidManifest.xml
-keep class com.example.receiver.ScreenAdminReceiver { *; }
-keep class com.example.receiver.BootCompletedReceiver { *; }
-keep class com.example.service.ScreenLockAccessibilityService { *; }
-keep class com.example.service.ScreenControlService { *; }
-keep class com.example.service.LockTileService { *; }

# Keep enums serialized by name in SharedPreferences
-keepclassmembers enum com.example.data.ShakeSensitivity {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum com.example.data.LockMethod {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
