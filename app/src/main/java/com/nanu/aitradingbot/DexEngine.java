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
                // Add QUALIFIED candidates to queue (avoid duplicates)
                for (DexCandidate c : candidates) {
                    if ("QUALIFIED".equals(c.status) && !inQueue(c.tokenAddress))
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

    // ─ QUEUE DRAIN (auto-retry next if blocked) ─────────────────────

    private void drainQueue() {
        Iterator<DexCandidate> it = queue.iterator();
        while (it.hasNext() && store.openPositionCount() < store.maxPositions) {
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
                    BotEvolution.evolve(store);
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
            BotEvolution.evolve(store);
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

        // Get current prices from DEX Screener for open positions
        for (TradeRecord r : open) {
            try {
                double current = fetchCurrentPrice(r);
                if (current <= 0) continue;

                double pct = (current - r.entryPrice) / r.entryPrice * 100.0;
                long heldMin = (System.currentTimeMillis() - r.openTimeMs) / 60_000L;

                // Update trailing peak price
                if (store.useTrailingStop && current > r.peakPrice) {
                    r.peakPrice = current;
                    List<TradeRecord> hist = store.loadHistory();
                    for (TradeRecord h : hist) if (r.id.equals(h.id)) { h.peakPrice = current; break; }
                    store.saveHistory(hist);
                }

                // Trailing stop: exit if price drops trailingStopPct% below peak
                double trailStop = store.useTrailingStop && r.peakPrice > r.entryPrice
                    ? (r.peakPrice - current) / r.peakPrice * 100.0
                    : -1;

                String reason = null;
                if (pct >= store.takeProfitPercent)
                    reason = "take_profit";
                else if (store.useTrailingStop && trailStop >= store.trailingStopPct
                         && r.peakPrice > r.entryPrice * 1.005)
                    reason = "trailing_stop";
                else if (pct <= -store.stopLossPercent)
                    reason = "stop_loss";
                else if (heldMin >= store.maxHoldMinutes)
                    reason = "force_close";

                if (reason != null)
                    closePosition(r, current, reason);
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
        String chain = "bsc".equals(r.chain) ? "bsc" : "solana";
        String url = "https://api.dexscreener.com/tokens/" + r.tokenAddress;
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

    private boolean inQueue(String addr) {
        for (DexCandidate c : queue) if (addr.equals(c.tokenAddress)) return true;
        return false;
    }

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
        BotEvolution.evolve(store);
        if (listener != null) listener.onPositionClosed(r);
        Log.i(TAG, "Closed: " + r.tokenSymbol + " P/L=" + r.pnlUsd);
    }
}
