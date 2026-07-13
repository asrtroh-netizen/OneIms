# 本项目大量走反射调用隐藏 API，保守起见 release 默认不混淆核心类
-keep class rikka.shizuku.** { *; }
-keep class org.lsposed.hiddenapibypass.** { *; }
-keep class com.oneims.app.core.** { *; }
-dontwarn android.telephony.**
