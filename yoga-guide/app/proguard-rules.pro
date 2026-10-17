# minifyEnabled is false for both build types, so nothing here runs today.
# Kept so that turning R8 on later does not immediately break the two
# reflection-reached surfaces in the app.

# The JS bridge is called by name from the WebView, not from Java.
-keepclassmembers class com.joy.yogaguide.NativeBridge {
    public *;
}
-keepattributes JavascriptInterface

# WorkManager instantiates workers reflectively.
-keep class * extends androidx.work.Worker
