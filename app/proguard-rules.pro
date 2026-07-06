# MogoMarket - project specific ProGuard / R8 rules

# Keep useful stack trace info for Play Console crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations/signatures used by reflection libraries
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-keep class com.google.gson.stream.** { *; }
-keep class sun.misc.Unsafe { *; }

# Firebase Realtime Database model used via reflection
# Detected in code: child.getValue(StockData.class)
-keep class com.mogomarket.app.StockData { *; }

# JavaMail
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**

# Optional: preserve no-arg constructors if future Firebase models are added
-keepclassmembers class com.mogomarket.app.** {
    public <init>();
}