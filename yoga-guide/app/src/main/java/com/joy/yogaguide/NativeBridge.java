package com.joy.yogaguide;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.webkit.JavascriptInterface;

/**
 * Everything the page cannot do for itself, exposed to JS as {@code window.Android}.
 *
 * The page degrades gracefully when this object is absent (desktop browser during
 * development), so every method here has a web fallback in js/native.js.
 *
 * Note: these run on the WebView's JavaBridge thread, never the UI thread. Anything
 * touching the window has to hop threads itself - see MainActivity.
 */
public class NativeBridge {

    private final MainActivity activity;

    NativeBridge(MainActivity activity) {
        this.activity = activity;
    }

    /**
     * WebView's own SpeechSynthesis is present but unreliable - on several OEM
     * builds it reports voices and then never fires an utterance. The platform
     * TextToSpeech engine is the one that actually works offline, and it is also
     * the one with Indic voices installed on most devices here.
     */
    @JavascriptInterface
    public void speak(String text) {
        activity.speak(text);
    }

    @JavascriptInterface
    public void stopSpeaking() {
        activity.stopSpeaking();
    }

    @JavascriptInterface
    public boolean ttsReady() {
        return activity.isTtsReady();
    }

    @JavascriptInterface
    public void vibrate(int ms) {
        if (ms <= 0) return;
        Vibrator v;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm =
                    (VibratorManager) activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vm == null) return;
            v = vm.getDefaultVibrator();
        } else {
            v = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (v == null || !v.hasVibrator()) return;
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    @JavascriptInterface
    public void setKeepAwake(boolean on) {
        activity.setKeepAwake(on);
    }

    @JavascriptInterface
    public void setImmersive(boolean on) {
        activity.setImmersiveMode(on);
    }

    @JavascriptInterface
    public boolean notificationsAllowed() {
        return activity.hasNotificationPermission();
    }

    @JavascriptInterface
    public void requestNotifications() {
        activity.requestNotificationPermission();
    }

    @JavascriptInterface
    public void scheduleReminder(int hour, int minute) {
        Reminders.schedule(activity.getApplicationContext(), hour, minute);
    }

    @JavascriptInterface
    public void cancelReminder() {
        Reminders.cancel(activity.getApplicationContext());
    }
}
