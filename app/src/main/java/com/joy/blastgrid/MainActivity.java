package com.joy.blastgrid;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

/**
 * Single-activity WebView host for Blastgrid.
 *
 * The game is one self-contained HTML file in assets/. It makes no network calls,
 * so the app declares no INTERNET permission.
 */
public class MainActivity extends AppCompatActivity {

    /** Served over a virtual https origin, not file://, so localStorage has a real origin. */
    private static final String GAME_URL = "https://blastgrid.local/assets/blastgrid.html";
    private static final long BACK_TO_QUIT_WINDOW_MS = 2000L;

    private WebView web;
    private long lastBackPress = 0L;

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // A game that idles between inputs must not let the screen sleep.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Edge to edge. targetSdk 35 enforces this on Android 15 regardless, so opt in
        // deliberately; the page already respects env(safe-area-inset-*) via viewport-fit=cover.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        hideSystemBars();

        web = new WebView(this);
        // Matches --void in the stylesheet, so there is no white flash before first paint.
        web.setBackgroundColor(0xFF0E0D10);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        // Off by default in a WebView. Without this, every high score silently vanishes.
        s.setDomStorageEnabled(true);
        // Web Audio needs this or the whole sound engine stays muted.
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        // A large system font scale would otherwise blow the HUD layout apart.
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);

        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        // Suppress the text-selection popup when a thumb rests on the d-pad.
        web.setLongClickable(false);
        web.setHapticFeedbackEnabled(false);
        web.setOnLongClickListener(v -> true);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain("blastgrid.local")
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                // The game never navigates. Anything that tries is a bug or an accident.
                return true;
            }
        });

        setContentView(web);
        web.loadUrl(GAME_URL);

        // Back once pauses the run. Back twice within two seconds quits.
        // Dropping out of the app mid-sector because of a stray swipe is infuriating.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                long now = System.currentTimeMillis();
                if (now - lastBackPress < BACK_TO_QUIT_WINDOW_MS) {
                    finish();
                    return;
                }
                lastBackPress = now;
                web.evaluateJavascript(
                        "(window.blastgrid && window.blastgrid.pause()) ? 'paused' : 'idle'",
                        value -> {
                            if (value != null && value.contains("paused")) {
                                toast("Paused \u2014 back again to quit");
                            } else {
                                toast("Back again to quit");
                            }
                        });
            }
        });
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        c.hide(WindowInsetsCompat.Type.systemBars());
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) {
            // pauseTimers stops rAF and the audio clock; without it the game
            // keeps running (and draining battery) behind the launcher.
            web.onPause();
            web.pauseTimers();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
            web.resumeTimers();
        }
        hideSystemBars();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
