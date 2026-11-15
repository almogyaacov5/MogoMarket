# MogoMarket - project specific ProGuard / R8 rules

# Keep useful stack trace info for Play Console crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations/signatures used by reflection libraries
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Firebase ClassMapper constructs models and accesses their public fields by name.
-keepclassmembers class com.mogomarket.app.StockData {
    public <init>();
    public <fields>;
}
-keepclassmembers class com.mogomarket.app.StockWatchData {
    public <init>();
    public <fields>;
}
-keepclassmembers class com.mogomarket.app.AnalysisData {
    public <init>();
    public <fields>;
}
-keepclassmembers class com.mogomarket.app.DailyTradeEvent {
    public <init>();
    public <fields>;
}

# JavaMail loads provider implementations from provider metadata.
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**
-dontwarn com.sun.activation.**

# Navigation XML resolves fragment classes by name and instantiates them reflectively.
-keep,allowoptimization class * extends androidx.fragment.app.Fragment {
    public <init>();
}
-keep,allowoptimization class androidx.navigation.fragment.NavHostFragment {
    public <init>();
}
