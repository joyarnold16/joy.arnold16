package com.joy.blastgrid;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.libraries.ads.mobile.sdk.MobileAds;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration;
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdPreloader;

/**
 * Single-activity WebView host for Blastgrid.
 *
 * The game is one self-contained HTML file in assets/ and still makes no network
 * calls of its own. INTERNET/ACCESS_NETWORK_STATE are declared only for the
 * interstitial ads shown between runs (see AdBridge below).
 */
public class MainActivity extends AppCompatActivity {

    /** Served over a virtual https origin, not file://, so localStorage has a real origin. */
    private static final String GAME_URL = "https://blastgrid.local/assets/blastgrid.html";
    private static final long BACK_TO_QUIT_WINDOW_MS = 2000L;

    // TEST ad unit ID - Google's public sample ID, always fills with a test ad.
    // Swap for the real interstitial ad unit ID from your own AdMob account
    // before shipping a build with ads to the Play Store.
    private static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712";
    // TEST ad unit ID - same deal, swap for your own before shipping ads for real.
    private static final String REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";

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
        initAdsSdk();

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

        // Lets the page ask for an interstitial at a natural break (end of a run)
        // instead of the native side guessing at gameplay state from the outside.
        web.addJavascriptInterface(new AdBridge(), "AdBridge");
        web.addJavascriptInterface(new ShareBridge(), "ShareBridge");

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

    /**
     * Initializes the GMA Next-Gen SDK and starts preloading an interstitial so one
     * is ready by the time the player finishes a run. Init must happen off the UI
     * thread or it can ANR; preloading keeps a fresh ad on hand without a per-show
     * load delay, since InterstitialAdPreloader automatically fetches a replacement
     * each time pollAd() hands one out.
     */
    private void initAdsSdk() {
        new Thread(() -> {
            MobileAds.initialize(
                    this,
                    new InitializationConfig.Builder(getString(R.string.admob_app_id)).build(),
                    status -> Log.d("Ads", "Mobile Ads SDK initialized"));

            AdRequest adRequest = new AdRequest.Builder(INTERSTITIAL_AD_UNIT_ID).build();
            InterstitialAdPreloader.start(
                    INTERSTITIAL_AD_UNIT_ID,
                    new PreloadConfiguration(adRequest),
                    new PreloadCallback() {
                        @Override
                        public void onAdPreloaded(@NonNull String preloadId, @NonNull ResponseInfo info) {
                            Log.d("Ads", "Interstitial preloaded");
                        }

                        @Override
                        public void onAdFailedToPreload(@NonNull String preloadId, @NonNull LoadAdError error) {
                            Log.d("Ads", "Interstitial failed to preload: " + error.getMessage());
                        }

                        @Override
                        public void onAdsExhausted(@NonNull String preloadId) {
                            Log.d("Ads", "Interstitial pool exhausted, refilling");
                        }
                    });

            AdRequest rewardedRequest = new AdRequest.Builder(REWARDED_AD_UNIT_ID).build();
            RewardedAdPreloader.start(
                    REWARDED_AD_UNIT_ID,
                    new PreloadConfiguration(rewardedRequest),
                    new PreloadCallback() {
                        @Override
                        public void onAdPreloaded(@NonNull String preloadId, @NonNull ResponseInfo info) {
                            Log.d("Ads", "Rewarded ad preloaded");
                        }

                        @Override
                        public void onAdFailedToPreload(@NonNull String preloadId, @NonNull LoadAdError error) {
                            Log.d("Ads", "Rewarded ad failed to preload: " + error.getMessage());
                        }

                        @Override
                        public void onAdsExhausted(@NonNull String preloadId) {
                            Log.d("Ads", "Rewarded ad pool exhausted, refilling");
                        }
                    });
        }).start();
    }

    /** Shows a preloaded interstitial if one is ready; silently no-ops otherwise. */
    private void showInterstitialIfReady() {
        InterstitialAd ad = InterstitialAdPreloader.pollAd(INTERSTITIAL_AD_UNIT_ID);
        if (ad == null) return; // no ad yet (offline, still loading, etc.) - never block the retry
        ad.setAdEventCallback(new InterstitialAdEventCallback() {
            @Override
            public void onAdImpression() {
                Log.d("Ads", "Interstitial impression recorded");
            }
        });
        ad.show(this);
    }

    /**
     * Polls for a rewarded ad and shows it if one's ready. The page finds out the
     * outcome (revive or not) via onReviveResult() on window.blastgrid, called once
     * the ad is actually dismissed - not the moment the reward itself is granted,
     * since a player could still back out before the ad closes on some networks.
     */
    private void requestRewardedRevive() {
        RewardedAd ad = RewardedAdPreloader.pollAd(REWARDED_AD_UNIT_ID);
        if (ad == null) {
            notifyReviveResult(false);
            return;
        }
        final boolean[] earned = {false};
        ad.setAdEventCallback(new RewardedAdEventCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                notifyReviveResult(earned[0]);
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError error) {
                Log.d("Ads", "Rewarded ad failed to show: " + error.getMessage());
                notifyReviveResult(false);
            }
        });
        ad.show(this, rewardItem -> earned[0] = true);
    }

    private void notifyReviveResult(boolean earned) {
        runOnUiThread(() -> {
            if (web == null) return;
            web.evaluateJavascript(
                    "window.blastgrid && window.blastgrid.onReviveResult && "
                            + "window.blastgrid.onReviveResult(" + earned + ")",
                    null);
        });
    }

    /**
     * Exposed to the page as window.AdBridge. blastgrid.html decides when a run has
     * actually ended and how often to ask (see the ovBtn click handler) - this side
     * just shows an ad if one happens to be ready, on the UI thread the ad SDK needs.
     */
    private class AdBridge {
        @JavascriptInterface
        public void onRunEnd() {
            runOnUiThread(MainActivity.this::showInterstitialIfReady);
        }

        // Synchronous on purpose: the page needs this before deciding whether to
        // even offer the revive prompt, not after.
        @JavascriptInterface
        public boolean isReviveAvailable() {
            return RewardedAdPreloader.isAdAvailable(REWARDED_AD_UNIT_ID);
        }

        @JavascriptInterface
        public void requestRevive() {
            runOnUiThread(MainActivity.this::requestRewardedRevive);
        }
    }

    /** Exposed to the page as window.ShareBridge, for the "Share score" button. */
    private class ShareBridge {
        @JavascriptInterface
        public void share(String text) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(send, null));
            });
        }
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
        InterstitialAdPreloader.destroy(INTERSTITIAL_AD_UNIT_ID);
        RewardedAdPreloader.destroy(REWARDED_AD_UNIT_ID);
        super.onDestroy();
    }
}
