package com.nanu.aitradingbot;

import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core trading engine.
 * - Scans DEX Screener every store.scanIntervalMin minutes.
 * - Maintains a queue of QUALIFIED candidates; tries each in order.
 * - Supports up to store.maxPositions simultaneous open positions.
 * - Monitors open positions for TP/SL/force-close.
 * - Calls SwapEngine for live trades (disabled when store.liveMode==false).
 * - Sends Telegram + Android notifications on open/close.
 */
public class DexEngine {
    private static final String TAG = "DexEngine";
    private static final int MONITOR_INTERVAL_MS = 15_000; // price check every 15s

    public interface Listener {
        void onScanStart();
        void onScanResult(List<DexCandidate> candidates);
        void onPositionOpened(TradeRecord r);
        void onPositionClosed(TradeRecord r);
        void onError(String msg);
    }

    private final DexAppStore store;
    private final Context ctx;
    private Listener listener;

    private volatile boolean running = false;
    private Thread scanThread;
    private Thread monitorThread;

    private final CopyOnWriteArrayList<DexCandidate> queue = new CopyOnWriteArrayList<>();
    private List<DexCandidate> lastScanResults = new ArrayList<>();

    public DexEngine(Context ctx, DexAppStore store) {
        this.ctx   = ctx;
        this.store = store;
    }

    public void setListener(Listener l) { this.listener = l; }

    public List<DexCandidate> getLastScanResults() { return lastScanResults; }

    // ─ START / STOP ───────────────────────────────────────────────

    public synchronized void start() {
        if (running) return;
        running = true;
        startScanLoop();
        startMonitorLoop();
        Log.i(TAG, "Engine started");
    }

    public synchronized void stop() {
        running = false;
        if (scanThread    != null) scanThread.interrupt();
        if (monitorThread != null) monitorThread.interrupt();
        Log.i(TAG, "Engine stopped");
    }

    public boolean isRunning() { return running; }

    public void panicClose() {
        List<TradeRecord> open = store.getOpenPositions();
        for (TradeRecord r : open)
            closePosition(r, r.entryPrice, "panic");
        Log.w(TAG, "PANIC: closed " + open.size() + " positions");
    }

    public void manualClose(String tradeId) {
        for (TradeRecord r : store.getOpenPositions()) {
            if (r.id.equals(tradeId)) {
                new Thread(() -> {
                    double price = r.entryPrice;
                    try {
                        double p = fetchCurrentPrice(r);
                        if (p > 0) price = p;
                    } catch (Exception ignored) {}
                    closePosition(r, price, "manual_exit");
                    Log.i(TAG, "Manual exit: " + r.tokenSymbol + " @ " + price);
                }, "nanu-manual-close").start();
                return;
            }
        }
        Log.w(TAG, "manualClose: trade " + tradeId + " not found or already closed");
    }

    // ─ SCAN LOOP ─────────────────────────────────────────────────

    private void startScanLoop() {
        scanThread = new Thread(() -> {
            while (running) {
                doScan();
                try {
                    long sleepMs = (long) store.scanIntervalMin * 60_000L;
                    Thread.sleep(Math.max(60_000, sleepMs));
                } catch (InterruptedException e) { break; }
            }
        }, "nanu-scan");
        scanThread.setDaemon(true);
        scanThread.start();
    }

    public void triggerScanNow() {
        new Thread(this::doScan, "nanu-scan-now").start();
    }

    private void doScan() {
        if (listener != null) listener.onScanStart();
        DexMarketClient.discover(store, new DexMarketClient.Callback() {
            @Override public void onResult(List<DexCandidate> candidates) {
                lastScanResults = candidates;
                // Rebuild queue from this scan only — stale candidates from
                // previous cycles expire so we never enter on outdated data.
                queue.clear();
                for (DexCandidate c : candidates) {
                    if ("QUALIFIED".equals(c.status) && !alreadyOpen(c.tokenAddress))
                        queue.add(c);
                }
                if (listener != null) listener.onScanResult(candidates);
                drainQueue();
            }
            @Override public void onError(String msg) {
                if (listener != null) listener.onError(msg);
            }
        });
    }

    // ─ ADAPTIVE RISK PARAMS ─────────────────────────────────────────

    static class RiskParams {
        final double sl, tp, trailPct;
        final int    holdMin;
        final String mode;
        RiskParams(double sl, double tp, double trailPct, int holdMin, String mode) {
            this.sl = sl; this.tp = tp; this.trailPct = trailPct;
            this.holdMin = holdMin; this.mode = mode;
        }
    }

    /**
     * Returns effective SL/TP/hold for a position.
     *
     * Manual mode: returns user's exact settings unchanged — no override.
     * Auto mode:   applies adaptive tier based on position size so ML-chosen
     *              params stay proportional to capital at risk.
     *   ≤$25   → SCALP   : SL ≤1.5%,  TP ≤3%,   hold ≤8 min
     *   ≤$100  → NORMAL  : settings unchanged
     *   ≤$300  → SWING   : SL ≥2.5%,  TP ≥6%,   hold ≥20 min
     *   >$300  → POSITION: SL ≥4.0%,  TP ≥10%,  hold ≥45 min
     */
    static RiskParams effectiveRisk(double amtUsd, DexAppStore store) {
        double sl    = store.stopLossPercent;
        double tp    = store.takeProfitPercent;
        double trail = store.trailingStopPct;
        int    hold  = store.maxHoldMinutes;

        // Manual mode: user's settings are sacred — use them exactly as set
        if (!store.autoMode) {
            return new RiskParams(sl, tp, trail, hold, "MANUAL");
        }

        // Auto mode only: adapt to position size
        if (amtUsd > 0 && amtUsd <= 25) {
            return new RiskParams(Math.min(sl, 1.5), Math.min(tp, 3.0),
                Math.min(trail, 0.8), Math.min(hold, 8), "SCALP");
        } else if (amtUsd <= 100) {
            return new RiskParams(sl, tp, trail, hold, "NORMAL");
        } else if (amtUsd <= 300) {
            return new RiskParams(Math.max(sl, 2.5), Math.max(tp, 6.0),
                Math.max(trail, 1.2), Math.max(hold, 20), "SWING");
        } else {
            return new RiskParams(Math.max(sl, 4.0), Math.max(tp, 10.0),
                Math.max(trail, 2.0), Math.max(hold, 45), "POSITION");
        }
    }

    // ─ QUEUE DRAIN (auto-retry next if blocked) ─────────────────────

    private void drainQueue() {
        // Always reload settings so user changes from the Control tab take effect immediately
        store.reload();

        // Reset the daily counter if the calendar day changed while idle
        store.rolloverDayIfNeeded();

        // Hard stop: daily loss limit
        if (store.isDailyLossLimitHit()) {
            Log.w(TAG, "Daily loss limit $" + store.maxDailyLossUsd
                + " hit (today: $" + String.format("%.2f", store.dailyPnlUsd) + "). No new entries.");
            return;
        }

        // Auto mode: re-evolve ML params before each drain cycle
        if (store.autoMode) BotEvolution.evolve(store);

        Iterator<DexCandidate> it = queue.iterator();
        while (it.hasNext()
                && store.openPositionCount() < store.maxPositions
                && store.tradesToday < store.maxDailyTrades) {
            DexCandidate c = it.next();
            queue.remove(c);

            // Re-check safety (may have changed since scan)
            String block = DexSafetyPolicy.check(c, store);
            if (block != null) {
                Log.d(TAG, "Queue skip (re-check blocked): " + c.symbol + " | " + block);
                continue;
            }
            if (alreadyOpen(c.tokenAddress)) continue;

            // Algo entry filter
            AlgoEngine.Signal sig = AlgoEngine.entrySignal(c);
            if (!sig.isGoodEntry(store.minAlgoScore)) {
                Log.d(TAG, "Queue skip (algo score " + sig.score + "<" + store.minAlgoScore + "): " + c.symbol);
                continue;
            }
            c.algoSignal     = sig.type;
            c.algoScore      = sig.score;
            openPosition(c, sig);
        }
    }

    // ─ OPEN POSITION ─────────────────────────────────────────────

    private void openPosition(DexCandidate c, AlgoEngine.Signal sig) {
        if (store.liveMode) {
            SwapEngine.buy(store, c, new SwapEngine.SwapCallback() {
                @Override public void onSuccess(String txHash, double price) {
                    TradeRecord r = store.openPosition(c);
                    r.buyTxHash      = txHash;
                    r.entryAlgoScore = sig.score;
                    r.algoSignal     = sig.type;
                    r.peakPrice      = price;
                    List<TradeRecord> hist = store.loadHistory();
                    for (TradeRecord h : hist) {
                        if (r.id.equals(h.id)) {
                            h.buyTxHash      = txHash;
                            h.entryAlgoScore = sig.score;
                            h.algoSignal     = sig.type;
                            h.peakPrice      = price;
                            break;
                        }
                    }
                    store.saveHistory(hist);
                    DexEngine.this.notify(r);
                }
                @Override public void onFail(String reason) {
                    Log.w(TAG, "Live buy failed: " + reason);
                    if (listener != null) listener.onError("Buy failed: " + reason);
                }
            });
        } else {
            TradeRecord r    = store.openPosition(c);
            r.entryAlgoScore = sig.score;
            r.algoSignal     = sig.type;
            r.peakPrice      = c.priceUsd;
            // Persist algo fields
            List<TradeRecord> hist = store.loadHistory();
            for (TradeRecord h : hist) {
                if (r.id.equals(h.id)) {
                    h.entryAlgoScore = sig.score;
                    h.algoSignal     = sig.type;
                    h.peakPrice      = c.priceUsd;
                    break;
                }
            }
            store.saveHistory(hist);
            notify(r);
        }
    }

    // ─ MONITOR LOOP ─────────────────────────────────────────────

    private void startMonitorLoop() {
        monitorThread = new Thread(() -> {
            while (running) {
                try {
                    checkOpenPositions();
                    Thread.sleep(MONITOR_INTERVAL_MS);
                } catch (InterruptedException e) { break; }
            }
        }, "nanu-monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    private void checkOpenPositions() {
        List<TradeRecord> open = store.getOpenPositions();
        if (open.isEmpty()) return;

        for (TradeRecord r : open) {
            try {
                double current = fetchCurrentPrice(r);
                if (current <= 0) continue;

                // Use risk params tuned to this position's size
                RiskParams rp = effectiveRisk(r.amountUsd, store);

                double pct     = (current - r.entryPrice) / r.entryPrice * 100.0;
                long   heldMin = (System.currentTimeMillis() - r.openTimeMs) / 60_000L;

                // Update trailing peak price
                if (store.useTrailingStop && current > r.peakPrice) {
                    r.peakPrice = current;
                    List<TradeRecord> hist = store.loadHistory();
                    for (TradeRecord h : hist) if (r.id.equals(h.id)) { h.peakPrice = current; break; }
                    store.saveHistory(hist);
                }

                // Trailing stop: exit if price drops trailPct% below peak
                double trailDrop = store.useTrailingStop && r.peakPrice > r.entryPrice
                    ? (r.peakPrice - current) / r.peakPrice * 100.0
                    : -1;

                String reason = null;
                if (pct >= rp.tp)
                    reason = "take_profit";
                else if (store.useTrailingStop && trailDrop >= rp.trailPct
                         && r.peakPrice > r.entryPrice * 1.005)
                    reason = "trailing_stop";
                else if (pct <= -rp.sl)
                    reason = "stop_loss";
                else if (heldMin >= rp.holdMin)
                    reason = "force_close";

                if (reason != null) {
                    Log.d(TAG, rp.mode + " exit → " + reason + " | " + r.tokenSymbol
                        + " pct=" + String.format("%.2f", pct)
                        + " sl=" + rp.sl + " tp=" + rp.tp + " hold=" + rp.holdMin + "min");
                    closePosition(r, current, reason);
                }
            } catch (Exception e) {
                Log.w(TAG, "Monitor error for " + r.tokenSymbol + ": " + e.getMessage());
            }
        }
    }

    // ─ CLOSE POSITION ────────────────────────────────────────────

    private void closePosition(TradeRecord r, double exitPrice, String reason) {
        if (store.liveMode && !"panic".equals(reason)) {
            DexCandidate dummy = new DexCandidate();
            dummy.chain = r.chain;
            dummy.tokenAddress = r.tokenAddress;
            dummy.priceUsd = exitPrice;
            SwapEngine.sell(store, dummy, r.tokenAddress, r.amountUsd / r.entryPrice,
                new SwapEngine.SwapCallback() {
                    @Override public void onSuccess(String txHash, double price) {
                        TradeRecord closed = store.closePosition(r.id, price, reason, txHash);
                        if (closed != null) notifyClose(closed);
                    }
                    @Override public void onFail(String err) {
                        Log.w(TAG, "Sell failed: " + err + " — closing at last price");
                        TradeRecord closed = store.closePosition(r.id, exitPrice, reason, null);
                        if (closed != null) notifyClose(closed);
                    }
                });
        } else {
            TradeRecord closed = store.closePosition(r.id, exitPrice, reason, null);
            if (closed != null) notifyClose(closed);
        }
        // After closing, drain queue for next opportunity
        new Thread(this::drainQueue, "nanu-drain").start();
    }

    // ─ PRICE FETCH ───────────────────────────────────────────────

    private double fetchCurrentPrice(TradeRecord r) throws Exception {
        String url = "https://api.dexscreener.com/latest/dex/tokens/" + r.tokenAddress;
        java.net.HttpURLConnection conn =
            (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        if (conn.getResponseCode() != 200) return -1;
        java.io.BufferedReader br = new java.io.BufferedReader(
            new java.io.InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder(); String ln;
        while ((ln = br.readLine()) != null) sb.append(ln);
        org.json.JSONObject resp = new org.json.JSONObject(sb.toString());
        org.json.JSONArray pairs = resp.optJSONArray("pairs");
        if (pairs == null || pairs.length() == 0) return -1;
        Object priceObj = pairs.getJSONObject(0).opt("priceUsd");
        if (priceObj == null) return -1;
        return Double.parseDouble(priceObj.toString());
    }

    // ─ HELPERS ──────────────────────────────────────────────────

    private boolean alreadyOpen(String addr) {
        for (TradeRecord r : store.getOpenPositions())
            if (addr.equals(r.tokenAddress)) return true;
        return false;
    }

    private void notify(TradeRecord r) {
        TelegramBot.notifyOpen(store, r);
        TradeService.notifyOpened(ctx, r);
        if (listener != null) listener.onPositionOpened(r);
        Log.i(TAG, "Opened: " + r.tokenSymbol + " @ " + r.entryPrice);
    }

    private void notifyClose(TradeRecord r) {
        TelegramBot.notifyClose(store, r);
        TradeService.notifyClosed(ctx, r);
        if (store.autoMode) BotEvolution.evolve(store);
        if (listener != null) listener.onPositionClosed(r);
        Log.i(TAG, "Closed: " + r.tokenSymbol + " P/L=" + r.pnlUsd);
    }
}
