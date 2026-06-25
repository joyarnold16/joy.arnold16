package com.nanu.aitradingbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.util.List;

public class TradeService extends Service {
    private static final String TAG = "TradeService";
    public  static final String CHANNEL_TRADE  = "nanu_trades_v2"; // v2: sound+vibration enabled
    public  static final String CHANNEL_STATUS = "nanu_status";
    private static final int    NOTIF_ID       = 1001;
    private static final long   STATUS_INTERVAL_MS = 60_000L;

    // Activity registers here to receive live UI events; null when app is in background
    public static volatile DexEngine.Listener uiListener = null;

    // True while the engine is running (activity reads this to show status)
    public static volatile boolean active = false;

    private DexEngine    engine;
    private DexAppStore  store;
    private Handler      handler;

    private final Runnable statusTick = this::tickNotif;

    // ─ Lifecycle ───────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        handler = new Handler(Looper.getMainLooper());
        store   = new DexAppStore(this);
        engine  = new DexEngine(this, store);
        engine.setListener(bridgeListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if ("STOP".equals(action)) {
            doStop();
            return START_NOT_STICKY;
        }
        if ("PANIC".equals(action)) {
            engine.panicClose();
            return START_STICKY;
        }
        if ("CLOSE_POSITION".equals(action)) {
            String tradeId = intent.getStringExtra("tradeId");
            if (tradeId != null) engine.manualClose(tradeId);
            return START_STICKY;
        }
        if ("SCAN_NOW".equals(action)) {
            if (active) engine.triggerScanNow();
            return START_STICKY;
        }

        // Normal start
        active = true;
        store.botRunning = true;
        store.save();
        startForeground(NOTIF_ID, buildStatusNotif());
        engine.start();
        handler.postDelayed(statusTick, STATUS_INTERVAL_MS);
        Log.i(TAG, "Service started");
        return START_STICKY;
    }

    private void doStop() {
        active = false;
        store.botRunning = false;
        store.save();
        engine.stop();
        handler.removeCallbacks(statusTick);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            stopForeground(STOP_FOREGROUND_REMOVE);
        else
            stopForeground(true);
        stopSelf();
        Log.i(TAG, "Service stopped");
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        active = false;
        handler.removeCallbacks(statusTick);
        if (engine != null) engine.stop();
        super.onDestroy();
    }

    // ─ Notification ────────────────────────────────────────────────

    private void tickNotif() {
        store.reload();
        pushStatusNotif();
        if (active) handler.postDelayed(statusTick, STATUS_INTERVAL_MS);
    }

    private void pushStatusNotif() {
        try {
            NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, buildStatusNotif());
        } catch (Exception ignore) {}
    }

    private String statusLine() {
        int open = store.openPositionCount();
        String pnl  = String.format("%+.2f", store.totalPnlUsd);
        String mode = store.liveMode ? "LIVE" : "PAPER";
        String chains;
        if (store.liveChainBnb && store.liveChainSol) chains = "BNB+SOL";
        else if (store.liveChainBnb) chains = "BNB";
        else chains = "SOL";
        String auto = store.autoMode ? "AUTO" : "MANUAL";
        return mode + " | " + chains + " | " + open + " open | P/L " + pnl + " USD | " + auto;
    }

    private Notification buildStatusNotif() {
        // Tap → open app
        Intent openApp = new Intent(this, DexActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(this, 0, openApp,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Stop action
        Intent stopIntent = new Intent(this, TradeService.class);
        stopIntent.setAction("STOP");
        PendingIntent piStop = PendingIntent.getService(this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE);

        String title = "Nanu AI Bot  •  " + (active ? "SCANNING" : "IDLE");
        return new NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(statusLine())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(piOpen)
            .addAction(android.R.drawable.ic_delete, "Stop Bot", piStop)
            .build();
    }

    // ─ Bridge listener: engine → notification + UI ────────────────

    private final DexEngine.Listener bridgeListener = new DexEngine.Listener() {
        @Override public void onScanStart() {
            pushStatusNotif();
            DexEngine.Listener l = uiListener;
            if (l != null) l.onScanStart();
        }
        @Override public void onScanResult(List<DexCandidate> c) {
            pushStatusNotif();
            DexEngine.Listener l = uiListener;
            if (l != null) l.onScanResult(c);
        }
        @Override public void onPositionOpened(TradeRecord r) {
            pushStatusNotif();
            DexEngine.Listener l = uiListener;
            if (l != null) l.onPositionOpened(r);
        }
        @Override public void onPositionClosed(TradeRecord r) {
            pushStatusNotif();
            DexEngine.Listener l = uiListener;
            if (l != null) l.onPositionClosed(r);
        }
        @Override public void onError(String msg) {
            DexEngine.Listener l = uiListener;
            if (l != null) l.onError(msg);
        }
    };

    // ─ Trade alert notifications ───────────────────────────────────

    public static void notifyOpened(Context ctx, TradeRecord r) {
        String chain = "bsc".equals(r.chain) ? "BNB" : "SOL";
        String mode  = r.isLive ? "LIVE" : "PAPER";
        String title = mode + " Trade Opened: " + r.tokenSymbol;
        String text  = chain + " | Score " + r.confidenceScore
            + " | Entry $" + String.format("%.8f", r.entryPrice);
        pushAlert(ctx, title, text, (int)(System.currentTimeMillis() % 10_000) + 2);
    }

    public static void notifyClosed(Context ctx, TradeRecord r) {
        String result = r.isWin ? "WIN" : "LOSS";
        String title  = result + ": " + r.tokenSymbol;
        String text   = String.format("%+.2f USD (%+.1f%%) | %s",
            r.pnlUsd, r.pnlPercent, r.exitReason);
        pushAlert(ctx, title, text, (int)(System.currentTimeMillis() % 10_000) + 3);
    }

    private static void pushAlert(Context ctx, String title, String text, int id) {
        try {
            Intent intent = new Intent(ctx, DexActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new NotificationCompat.Builder(ctx, CHANNEL_TRADE)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
            NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(id, n);
        } catch (Exception e) { Log.w(TAG, "Alert notif failed: " + e.getMessage()); }
    }

    // ─ Notification channels ────────────────────────────────────────

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        // Trade alert channel: sound + vibration + cyan LED
        NotificationChannel trade = new NotificationChannel(
            CHANNEL_TRADE, "Trade Alerts", NotificationManager.IMPORTANCE_HIGH);
        trade.setDescription("Sound alert when a trade opens or closes");
        trade.setSound(
            android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
            new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        trade.enableVibration(true);
        trade.setVibrationPattern(new long[]{0, 200, 100, 200});
        trade.enableLights(true);
        trade.setLightColor(0xFF00E5FF); // cyan
        nm.createNotificationChannel(trade);

        // Status channel: silent persistent notification
        NotificationChannel status = new NotificationChannel(
            CHANNEL_STATUS, "Bot Status", NotificationManager.IMPORTANCE_LOW);
        status.setDescription("Persistent background scanner notification");
        status.setSound(null, null);
        nm.createNotificationChannel(status);
    }

    // ─ Static control helpers called from Activity ──────────────────

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, TradeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ctx.startForegroundService(i);
        else
            ctx.startService(i);
    }

    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, TradeService.class);
        i.setAction("STOP");
        ctx.startService(i);
    }

    public static void panic(Context ctx) {
        Intent i = new Intent(ctx, TradeService.class);
        i.setAction("PANIC");
        ctx.startService(i);
    }

    public static void triggerScan(Context ctx) {
        Intent i = new Intent(ctx, TradeService.class);
        i.setAction("SCAN_NOW");
        ctx.startService(i);
    }

    public static void closePosition(Context ctx, String tradeId) {
        Intent i = new Intent(ctx, TradeService.class);
        i.setAction("CLOSE_POSITION");
        i.putExtra("tradeId", tradeId);
        ctx.startService(i);
    }
}
