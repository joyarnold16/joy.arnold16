package com.nanu.aitradingbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class TradeService extends Service {
    private static final String TAG = "TradeService";
    public  static final String CHANNEL_TRADE  = "nanu_trades";
    public  static final String CHANNEL_STATUS = "nanu_status";
    private static final int    NOTIF_ID       = 1001;

    private DexEngine engine;
    private DexAppStore store;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        store  = new DexAppStore(this);
        engine = new DexEngine(this, store);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if ("STOP".equals(action)) {
            engine.stop();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if ("PANIC".equals(action)) {
            engine.panicClose();
            return START_STICKY;
        }
        startForeground(NOTIF_ID, buildStatusNotif("Scanning BNB + SOL…"));
        engine.start();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        if (engine != null) engine.stop();
        super.onDestroy();
    }

    // ─ Static helpers called from DexEngine ──────────────────────────

    public static void notifyOpened(Context ctx, TradeRecord r) {
        String chain = "bsc".equals(r.chain) ? "BNB" : "SOL";
        String mode  = r.isLive ? "LIVE" : "PAPER";
        String title = mode + " Trade Opened: " + r.tokenSymbol;
        String text  = chain + " | Score " + r.confidenceScore
            + " | Entry $" + String.format("%.8f", r.entryPrice);
        push(ctx, title, text, (int)(System.currentTimeMillis() % 10000));
    }

    public static void notifyClosed(Context ctx, TradeRecord r) {
        String result = r.isWin ? "✅ WIN" : "❌ LOSS";
        String title  = result + ": " + r.tokenSymbol;
        String text   = String.format("%+.2f USD (%+.1f%%) | %s",
            r.pnlUsd, r.pnlPercent, r.exitReason);
        push(ctx, title, text, (int)(System.currentTimeMillis() % 10000) + 1);
    }

    private static void push(Context ctx, String title, String text, int id) {
        try {
            Intent intent = new Intent(ctx, DexActivity.class);
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
        } catch (Exception e) { Log.w(TAG, "Push notif failed: " + e.getMessage()); }
    }

    private Notification buildStatusNotif(String text) {
        Intent intent = new Intent(this, DexActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Nanu AI Trading Bot")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(
            CHANNEL_TRADE, "Trade Alerts", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(
            CHANNEL_STATUS, "Bot Status", NotificationManager.IMPORTANCE_LOW));
    }

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
}
