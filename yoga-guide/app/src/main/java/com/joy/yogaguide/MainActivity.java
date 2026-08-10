package com.joy.yogaguide;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

import java.util.Locale;

/**
 * Single-activity WebView host for Yoga Guide.
 *
 * The app itself is a set of HTML/CSS/JS assets. Everything that a page cannot
 * do reliably from inside a WebView - speech, haptics, keeping the screen on,
 * and a daily reminder that survives the app being closed - lives in
 * {@link NativeBridge} and is reached from JS as {@code window.Android}.
 */
public class MainActivity extends AppCompatActivity {

    /** Served over a virtual https origin, not file://, so localStorage has a real origin. */
    private static final String APP_URL = "https://yogaguide.local/assets/index.html";
    private static final long BACK_TO_QUIT_WINDOW_MS = 2000L;

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private long lastBackPress = 0L;
    private ActivityResultLauncher<String> notificationPermission;

    @SuppressLint({"SetJavaScriptEnabled"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge to edge. targetSdk 35+ enforces it on Android 15 regardless, so opt
        // in deliberately; the page respects env(safe-area-inset-*) via viewport-fit=cover.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        Reminders.createChannel(this);
        notificationPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> { /* JS re-queries */ });

        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            int r = tts.setLanguage(Locale.getDefault());
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Falling back rather than giving up: a device with no voice for the
                // system locale almost always still has en-US installed.
                r = tts.setLanguage(Locale.US);
            }
            ttsReady = (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED);
        });

        web = new WebView(this);
        // Matches --bg in css/app.css, so there is no white flash before first paint.
        web.setBackgroundColor(0xFFF7F3EC);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        // Off by default in a WebView. Without this the health profile, streak and
        // every logged session silently vanish on relaunch.
        s.setDomStorageEnabled(true);
        // Web Audio drives the transition chimes; without this it stays muted
        // until a tap, which is exactly when the cue is least useful.
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        // The page has its own text-size control. Letting the system font scale
        // through as well compounds the two and breaks the session player layout.
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);

        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setHorizontalScrollBarEnabled(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain("yogaguide.local")
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                // The app never navigates away. Anything that tries is a bug.
                return true;
            }
        });

        web.addJavascriptInterface(new NativeBridge(this), "Android");

        setContentView(web);
        web.loadUrl(APP_URL);

        // The page owns its own screen stack, so back goes to JS first. Only when
        // JS says it is already at the root does back mean "leave the app", and
        // then only on a second press - losing a half-finished session to a stray
        // edge swipe is the kind of thing that gets an app uninstalled.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web == null) {
                    finish();
                    return;
                }
                web.evaluateJavascript(
                        "(window.yg && window.yg.onBack) ? window.yg.onBack() : false",
                        value -> {
                            if ("true".equals(value)) return;
                            long now = System.currentTimeMillis();
                            if (now - lastBackPress < BACK_TO_QUIT_WINDOW_MS) {
                                finish();
                                return;
                            }
                            lastBackPress = now;
                            Toast.makeText(MainActivity.this,
                                    "Press back again to exit", Toast.LENGTH_SHORT).show();
                        });
            }
        });
    }

    // --- called from NativeBridge, always marshalled onto the UI thread ---

    void speak(String text) {
        if (!ttsReady || tts == null || text == null || text.isEmpty()) return;
        // FLUSH, not ADD: if the user skips ahead, the cue for the pose they just
        // left should be cut off rather than queued in front of the new one.
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "yg");
    }

    void stopSpeaking() {
        if (tts != null) tts.stop();
    }

    boolean isTtsReady() {
        return ttsReady;
    }

    /**
     * A pose held for 90 seconds produces no touch input, so without this the
     * screen dims mid-session. The page turns it on when a session starts and off
     * when it ends, rather than holding it app-wide - browsing the pose library
     * should still let the screen sleep normally.
     */
    void setKeepAwake(boolean on) {
        runOnUiThread(() -> {
            if (on) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    void setImmersive(boolean on) {
        runOnUiThread(() -> {
            WindowInsetsControllerCompat c =
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (on) {
                c.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                c.hide(WindowInsetsCompat.Type.systemBars());
            } else {
                c.show(WindowInsetsCompat.Type.systemBars());
            }
        });
    }

    boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        runOnUiThread(() ->
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) {
            // Without pauseTimers the session clock keeps counting down behind the
            // launcher and the user comes back to a finished session they never did.
            web.onPause();
            web.pauseTimers();
        }
        stopSpeaking();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
            web.resumeTimers();
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
