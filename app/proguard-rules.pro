# Tab ML Box
-keep class org.tensorflow.lite.** { *; }
-keep interface org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.lite.**
-dontwarn org.tensorflow.lite.annotations.**

# Llama Bro / llama.cpp (on-device GGUF)
-keep class com.suhel.llamabro.** { *; }
-dontwarn com.suhel.llamabro.**

# Vosk on-device STT
-keep class org.vosk.** { *; }
-dontwarn org.vosk.**

# org.json (remote streaming + transcript persistence)
-keep class org.json.** { *; }

# WorkManager (due task notifications)
-keep class dev.tabml.box.DueTaskWorker { public <init>(...); }
-keep class dev.tabml.box.DueTaskSnoozeReceiver { public <init>(...); }

# OkHttp (AI HTTPS client)
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
