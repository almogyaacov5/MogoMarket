# MogoMarket - project specific ProGuard / R8 rules

# Keep useful stack trace info for Play Console crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations/signatures used by reflection libraries
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ── Firebase ──────────────────────────────────────────────────
# כל מודלי Firebase — reflection דורש שמות מקוריים + getters/setters
-keep class com.mogomarket.app.StockData       { *; }
-keep class com.mogomarket.app.StockWatchData  { *; }
-keep class com.mogomarket.app.AnalysisData    { *; }

# Firebase Auth + Database
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Database ClassMapper — reflection על getters/setters
-keepclassmembers class com.mogomarket.app.** {
    public <init>();
    public <fields>;
    public <methods>;
}

# ── Gson ──────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }
-keep class sun.misc.Unsafe { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── OkHttp ────────────────────────────────────────────────────
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ── JavaMail ──────────────────────────────────────────────────
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**
-dontwarn com.sun.activation.**

# ── MPAndroidChart ────────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ── Navigation Component ──────────────────────────────────────
-keep class androidx.navigation.** { *; }

# ── Biometric ─────────────────────────────────────────────────
-keep class androidx.biometric.** { *; }

# ── ViewBinding ───────────────────────────────────────────────
-keep class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(android.view.LayoutInflater);
    public static * inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static * bind(android.view.View);
}
