package com.nanu.aitradingbot;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core trading engine.
 * - Scans DEX Screener every store.scanIntervalMin minutes.
 * - Maintains a queue of QUALIFIED candidates; tries each in order.
 * - Supports up to store.maxPositions simultaneous open positions.
 * - Monitors open positions for TP/SL/trailing/break-even/signal exits.
 * - Calls SwapEngine for live trades (disabled when store.liveMode==false).
 * - Sends Telegram + Android notifications on open/close.
 *
 * Tier 3 safety: on-chain RPC checks (Solana + BSC) before each entry.
 */
public class DexEngine {
    private static final String TAG = "DexEngine";
    private static final int MONITOR_INTERVAL_MS = 15_000;

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
                        DexCandidate live = fetchCurrentData(r);
                        if (live != null && live.priceUsd > 0) price = live.priceUsd;
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
     * Manual mode: returns user's exact settings — no override.
     * Auto mode:   adaptive tier by trade size:
     *   ≤$25   SCALP    SL ≤1.5% TP ≤3%   hold ≤8 min
     *   ≤$100  NORMAL   user settings as-is
     *   ≤$300  SWING    SL ≥2.5% TP ≥6%   hold ≥20 min
     *   >$300  POSITION SL ≥4.0% TP ≥10%  hold ≥45 min
     */
    static RiskParams effectiveRisk(double amtUsd, DexAppStore store) {
        double sl    = store.stopLossPercent;
        double tp    = store.takeProfitPercent;
        double trail = store.trailingStopPct;
        int    hold  = store.maxHoldMinutes;

        if (!store.autoMode)
            return new RiskParams(sl, tp, trail, hold, "MANUAL");

        if (amtUsd > 0 && amtUsd <= 25)
            return new RiskParams(Math.min(sl, 1.5), Math.min(tp, 3.0),
                Math.min(trail, 0.8), Math.min(hold, 8), "SCALP");
        else if (amtUsd <= 100)
            return new RiskParams(sl, tp, trail, hold, "NORMAL");
        else if (amtUsd <= 300)
            return new RiskParams(Math.max(sl, 2.5), Math.max(tp, 6.0),
                Math.max(trail, 1.2), Math.max(hold, 20), "SWING");
        else
            return new RiskParams(Math.max(sl, 4.0), Math.max(tp, 10.0),
                Math.max(trail, 2.0), Math.max(hold, 45), "POSITION");
    }

    // ─ QUEUE DRAIN ─────────────────────────────────────────────────

    private void drainQueue() {
        store.reload();
        store.rolloverDayIfNeeded();

        if (store.isDailyLossLimitHit()) {
            Log.w(TAG, "Daily loss limit $" + store.maxDailyLossUsd + " hit. No new entries.");
            return;
        }

        // Revenge-trade cooldown: 10 min after a loss before any new entry
        if (store.isRevengeTradeCooldownActive()) {
            long remMin = store.revengeCooldownRemainingMs() / 60_000;
            Log.d(TAG, "Revenge-trade cooldown: " + remMin + " min remaining.");
            return;
        }

        if (store.autoMode) BotEvolution.evolve(store);

        Iterator<DexCandidate> it = queue.iterator();
        while (it.hasNext()
                && store.openPositionCount() < store.maxPositions
                && store.tradesToday < store.maxDailyTrades) {
            DexCandidate c = it.next();
            queue.remove(c);

            // Re-check safety with fresh settings
            String block = DexSafetyPolicy.check(c, store);
            if (block != null) {
                Log.d(TAG, "Queue skip (re-check): " + c.symbol + " | " + block);
                continue;
            }
            if (alreadyOpen(c.tokenAddress)) continue;

            // Order size vs. liquidity guard: don't take >0.5% of pool
            double orderSize = store.liveMode ? store.tradeAmountUsd : store.paperTradeAmountUsd;
            if (c.liquidityUsd > 0 && orderSize > 0 && orderSize / c.liquidityUsd > 0.005) {
                Log.d(TAG, "Queue skip (order " + orderSize + " > 0.5% of liq " + c.liquidityUsd + "): " + c.symbol);
                continue;
            }

            // Algo entry filter
            AlgoEngine.Signal sig = AlgoEngine.entrySignal(c);
            if (!sig.isGoodEntry(store.minAlgoScore)) {
                Log.d(TAG, "Queue skip (algo " + sig.score + "<" + store.minAlgoScore + "): " + c.symbol);
                continue;
            }
            c.algoSignal = sig.type;
            c.algoScore  = sig.score;

            // Tier 3: on-chain safety check (synchronous, runs on this bg thread)
            if (!onChainSafe(c)) {
                Log.w(TAG, "Queue skip (on-chain unsafe): " + c.symbol);
                continue;
            }

            openPosition(c, sig);
        }
    }

    /**
     * Tier 3: on-chain safety checks before live/paper entry.
     * Returns false only for hard failures (honeypot detected, extreme sell tax).
     * Soft warnings update the candidate's fields but don't block entry.
     */
    private boolean onChainSafe(DexCandidate c) {
        try {
            if ("solana".equals(c.chain)) {
                SolanaChecker.Result r = SolanaChecker.check(c.tokenAddress);
                c.mintAuthorityRevoked   = r.mintAuthorityRevoked;
                c.freezeAuthorityRevoked = r.freezeAuthorityRevoked;
                c.onChainNote = r.note;
                // Compute rough scam risk: unrevoked authorities are red flags but not a hard block
                c.scamRiskScore = 100 - r.safetyScore;
                Log.d(TAG, "SOL on-chain " + c.symbol + ": " + r.note);
            } else if ("bsc".equals(c.chain)) {
                BscChecker.Result r = BscChecker.check(c.tokenAddress);
                c.contractVerified = r.contractVerified;
                c.ownerRenounced   = r.ownerRenounced;
                c.onChainNote      = r.note;
                c.scamRiskScore    = 100 - r.safetyScore;
                Log.d(TAG, "BSC on-chain " + c.symbol + ": " + r.note);
                // Hard blocks for BSC
                if (r.isHoneypot) {
                    Log.w(TAG, "BSC HONEYPOT: " + c.symbol);
                    return false;
                }
                if (r.sellTaxPct > 10) {
                    Log.w(TAG, "BSC sell tax " + r.sellTaxPct + "% too high: " + c.symbol);
                    return false;
                }
            }
        } catch (Exception e) {
            // On-chain check failure is non-blocking (RPC may be down)
            Log.w(TAG, "on-chain check error for " + c.symbol + ": " + e.getMessage());
        }
        return true;
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
                    updateHistoryRecord(r);
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
            updateHistoryRecord(r);
            notify(r);
        }
    }

    private void updateHistoryRecord(TradeRecord updated) {
        List<TradeRecord> hist = store.loadHistory();
        for (TradeRecord h : hist) {
            if (updated.id.equals(h.id)) {
                h.buyTxHash      = updated.buyTxHash;
                h.entryAlgoScore = updated.entryAlgoScore;
                h.algoSignal     = updated.algoSignal;
                h.peakPrice      = updated.peakPrice;
                break;
            }
        }
        store.saveHistory(hist);
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
                DexCandidate live = fetchCurrentData(r);
                if (live == null || live.priceUsd <= 0) continue;
                double current = live.priceUsd;

                RiskParams rp  = effectiveRisk(r.amountUsd, store);
                double pct     = (current - r.entryPrice) / r.entryPrice * 100.0;
                long   heldMin = (System.currentTimeMillis() - r.openTimeMs) / 60_000L;

                // Update trailing peak
                if (store.useTrailingStop && current > r.peakPrice) {
                    r.peakPrice = current;
                    List<TradeRecord> hist = store.loadHistory();
                    for (TradeRecord h : hist) if (r.id.equals(h.id)) { h.peakPrice = current; break; }
                    store.saveHistory(hist);
                }

                // Trailing stop
                double trailDrop = store.useTrailingStop && r.peakPrice > r.entryPrice
                    ? (r.peakPrice - current) / r.peakPrice * 100.0 : -1;

                String reason = null;

                // Take profit
                if (pct >= rp.tp)
                    reason = "take_profit";

                // Trailing stop (only triggers after reaching peak above entry)
                else if (store.useTrailingStop && trailDrop >= rp.trailPct
                         && r.peakPrice > r.entryPrice * 1.005)
                    reason = "trailing_stop";

                // Break-even stop: once price was +breakEven% above entry, never close below entry
                else if (store.breakEvenTriggerPct > 0
                         && r.peakPrice >= r.entryPrice * (1 + store.breakEvenTriggerPct / 100.0)
                         && pct <= 0)
                    reason = "break_even_stop";

                // Stop loss
                else if (pct <= -rp.sl)
                    reason = "stop_loss";

                // Force close on max hold time
                else if (heldMin >= rp.holdMin)
                    reason = "force_close";

                // Algo signal exit: bearish / sell-pressure / RSI overbought
                else {
                    live.patterns = CandlePatterns.detect(live);
                    int exitSc = AlgoEngine.exitScore(live, pct);
                    if (exitSc < 25)
                        reason = "signal_exit";
                }

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
        new Thread(this::drainQueue, "nanu-drain").start();
    }

    // ─ PRICE / DATA FETCH ────────────────────────────────────────

    /**
     * Fetches full current pair data from DEX Screener.
     * Returns a DexCandidate with live price, buy/sell counts, price changes.
     * Used by both monitor loop (for exitScore) and manualClose.
     */
    private DexCandidate fetchCurrentData(TradeRecord r) throws Exception {
        String url = "https://api.dexscreener.com/latest/dex/tokens/" + r.tokenAddress;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8_000);
        conn.setReadTimeout(8_000);
        if (conn.getResponseCode() != 200) return null;
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder(); String ln;
        while ((ln = br.readLine()) != null) sb.append(ln);
        br.close();
        org.json.JSONObject resp = new org.json.JSONObject(sb.toString());
        org.json.JSONArray pairs = resp.optJSONArray("pairs");
        if (pairs == null || pairs.length() == 0) return null;
        // Prefer pair on the same chain
        for (int i = 0; i < pairs.length(); i++) {
            org.json.JSONObject pair = pairs.getJSONObject(i);
            if (r.chain.equals(pair.optString("chainId", ""))) {
                return DexMarketClient.parsePair(pair);
            }
        }
        return DexMarketClient.parsePair(pairs.getJSONObject(0));
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
        Log.i(TAG, "Closed: " + r.tokenSymbol + " P/L=" + String.format("%.4f", r.pnlUsd));
    }
}
