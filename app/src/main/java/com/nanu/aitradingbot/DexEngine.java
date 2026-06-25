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
        store.rolloverWeekIfNeeded();

        if (store.isDailyLossLimitHit()) {
            Log.w(TAG, "Daily loss limit $" + store.maxDailyLossUsd + " hit. No new entries.");
            return;
        }

        // Loss-streak halt
        if (store.isDayLossStreakHit()) {
            String msg = "Loss streak " + store.curDayLossStreak + " hit (max "
                + store.maxDayLossStreak + "). No new entries today.";
            Log.w(TAG, msg);
            TelegramBot.notifyEmergencyStop(store, msg);
            return;
        }

        // Weekly loss limit
        if (store.isWeeklyLossLimitHit()) {
            Log.w(TAG, "Weekly loss limit $" + store.maxWeeklyLossUsd + " hit.");
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

            // Staleness gate: skip candidates older than 5 minutes
            long ageMs = System.currentTimeMillis() - c.dataFetchedAtMs;
            if (ageMs > 5 * 60_000L) {
                Log.d(TAG, "Queue skip (stale " + ageMs / 1000 + "s): " + c.symbol);
                continue;
            }

            // Re-check safety with fresh settings
            String block = DexSafetyPolicy.check(c, store);
            if (block != null) {
                Log.d(TAG, "Queue skip (re-check): " + c.symbol + " | " + block);
                NanuDatabase db = NanuDatabase.get(ctx);
                db.insertRejected(c, block);
                db.insertRiskCheck(c.tokenAddress, c.chain, "POLICY_BLOCKED", block, 0, c.liquidityUsd);
                continue;
            }
            if (alreadyOpen(c.tokenAddress)) continue;

            // Per-chain exposure cap
            if (store.maxChainExposureUsd > 0) {
                double chainExp = store.getChainExposureUsd(c.chain);
                if (chainExp >= store.maxChainExposureUsd) {
                    Log.d(TAG, "Queue skip (chain exposure $" + chainExp + " >= cap): " + c.symbol);
                    continue;
                }
            }

            // Dynamic position size
            double orderSize = store.calcDynamicPositionSize(c);
            if (orderSize <= 0) {
                Log.d(TAG, "Queue skip (chain exposure full): " + c.symbol);
                continue;
            }

            // Order size vs. liquidity guard: don't take >0.5% of pool
            if (c.liquidityUsd > 0 && orderSize / c.liquidityUsd > 0.005) {
                Log.d(TAG, "Queue skip (order " + orderSize + " > 0.5% of liq " + c.liquidityUsd + "): " + c.symbol);
                NanuDatabase.get(ctx).insertRiskCheck(c.tokenAddress, c.chain,
                    "LIQ_GUARD", "order " + (int) orderSize + " > 0.5% of liq", orderSize, c.liquidityUsd);
                continue;
            }

            // Algo entry filter
            AlgoEngine.Signal sig = AlgoEngine.entrySignal(c);
            if (!sig.isGoodEntry(store.minAlgoScore)) {
                Log.d(TAG, "Queue skip (algo " + sig.score + "<" + store.minAlgoScore + "): " + c.symbol);
                NanuDatabase.get(ctx).insertRiskCheck(c.tokenAddress, c.chain,
                    "ALGO_LOW", "score " + sig.score + "<" + store.minAlgoScore, orderSize, c.liquidityUsd);
                continue;
            }
            c.algoSignal = sig.type;
            c.algoScore  = sig.score;

            // Tier 3: on-chain safety check (synchronous, runs on this bg thread)
            if (!onChainSafe(c)) {
                Log.w(TAG, "Queue skip (on-chain unsafe): " + c.symbol);
                continue;
            }

            // Stage-band routing (evaluated after on-chain score is incorporated)
            String band = c.stageBand != null ? c.stageBand : DexSafetyPolicy.stageBand(c.score);
            if ("REJECT".equals(band) || "WATCH".equals(band)) {
                Log.d(TAG, "Queue skip (stage=" + band + " score=" + c.score + "): " + c.symbol);
                NanuDatabase.get(ctx).insertRiskCheck(c.tokenAddress, c.chain,
                    "STAGE_" + band, "score=" + c.score, orderSize, c.liquidityUsd);
                continue;
            }
            // Record the passing signal
            NanuDatabase.get(ctx).insertSignal(c, true);

            // PAPER band → force paper mode regardless of liveMode setting
            boolean forcePaper = "PAPER".equals(band);
            // SMALL_LIVE band → reduce size by 50%
            if ("SMALL_LIVE".equals(band) && store.liveMode) orderSize *= 0.5;

            openPosition(c, sig, orderSize, forcePaper);
        }
    }

    /**
     * Tier 3: on-chain safety checks before live/paper entry.
     * Returns false for hard failures; soft warnings adjust score but don't block.
     * After checks, recalculates c.score and c.stageBand.
     */
    private boolean onChainSafe(DexCandidate c) {
        NanuDatabase db = NanuDatabase.get(ctx);
        try {
            if ("solana".equals(c.chain)) {
                SolanaChecker.Result r = SolanaChecker.check(c.tokenAddress);
                c.mintAuthorityRevoked   = r.mintAuthorityRevoked;
                c.freezeAuthorityRevoked = r.freezeAuthorityRevoked;
                c.chainSafetyScore       = r.safetyScore;
                c.onChainNote            = r.note;
                c.scamRiskScore          = 100 - r.safetyScore;
                Log.d(TAG, "SOL on-chain " + c.symbol + ": " + r.note);
                db.cacheOnChain(c);

                // Hard block: RPC confirmed active authority
                if (r.isHardBlocked) {
                    Log.w(TAG, "SOL HARD BLOCK (" + r.hardBlockReason + "): " + c.symbol);
                    if (!r.mintAuthorityRevoked)   TelegramBot.notifyMintActive(store, c);
                    if (!r.freezeAuthorityRevoked) TelegramBot.notifyFreezeActive(store, c);
                    db.insertRejected(c, r.hardBlockReason);
                    return false;
                }

                // Sell simulation (soft)
                SellSimulator.Result sim = SellSimulator.checkSolana(c.tokenAddress, store.slippageBps);
                c.sellSimOk     = sim.simOk;
                c.sellImpactPct = sim.impactPct;
                c.sellSimNote   = sim.note;
                db.insertSellSim(c.tokenAddress, c.chain, sim.simOk, sim.impactPct, sim.note);

            } else if ("bsc".equals(c.chain)) {
                BscChecker.Result r = BscChecker.check(c.tokenAddress, c.pairAddress);
                c.contractVerified = r.contractVerified;
                c.ownerRenounced   = r.ownerRenounced;
                c.lpBurned         = r.lpBurned;
                c.ownerPowerFlags  = r.ownerPowerFlags;
                c.chainSafetyScore = r.safetyScore;
                c.onChainNote      = r.note;
                c.scamRiskScore    = 100 - r.safetyScore;
                Log.d(TAG, "BSC on-chain " + c.symbol + ": " + r.note);
                db.cacheOnChain(c);

                // Hard blocks
                if (r.isHoneypot) {
                    Log.w(TAG, "BSC HONEYPOT: " + c.symbol);
                    TelegramBot.notifyHoneypot(store, c);
                    db.insertRejected(c, "honeypot");
                    return false;
                }
                if (!r.canSell) {
                    Log.w(TAG, "BSC canSell=false: " + c.symbol);
                    TelegramBot.notifyHoneypot(store, c);
                    db.insertRejected(c, "canSell=false");
                    return false;
                }
                if (r.hasBlacklist) {
                    Log.w(TAG, "BSC BLACKLIST in source: " + c.symbol);
                    TelegramBot.notifyOwnerRisk(store, c, r.ownerPowerFlags);
                    db.insertRejected(c, "blacklist_capability");
                    return false;
                }
                if (r.sellTaxPct > 10) {
                    Log.w(TAG, "BSC sell tax " + r.sellTaxPct + "% too high: " + c.symbol);
                    return false;
                }
                // Soft warn for other owner powers
                if (!r.ownerPowerFlags.isEmpty()) {
                    Log.d(TAG, "BSC owner powers (" + r.ownerPowerFlags + "): " + c.symbol);
                    TelegramBot.notifyOwnerRisk(store, c, r.ownerPowerFlags);
                }

                // Sell simulation comes from BscChecker
                SellSimulator.Result sim = SellSimulator.checkBsc(r);
                c.sellSimOk     = sim.simOk;
                c.sellImpactPct = sim.impactPct;
                c.sellSimNote   = sim.note;
                db.insertSellSim(c.tokenAddress, c.chain, sim.simOk, sim.impactPct, sim.note);
            }
        } catch (Exception e) {
            // On-chain check failure is non-blocking (RPC may be down)
            Log.w(TAG, "on-chain check error for " + c.symbol + ": " + e.getMessage());
            NanuDatabase.get(ctx).logError("onchain", c.symbol + ": " + e.getMessage());
        }

        // Recalculate score and stage band incorporating on-chain + sell-sim results
        c.score     = DexSafetyPolicy.score(c, store);
        c.stageBand = DexSafetyPolicy.stageBand(c.score);
        return true;
    }

    // ─ OPEN POSITION ─────────────────────────────────────────────

    private void openPosition(DexCandidate c, AlgoEngine.Signal sig,
                              double orderSize, boolean forcePaper) {
        boolean goLive = store.liveMode && !forcePaper;
        NanuDatabase db = NanuDatabase.get(ctx);
        db.insertSnapshot(c);
        db.upsertToken(c);
        db.upsertPair(c);

        if (goLive) {
            SwapEngine.buy(store, c, new SwapEngine.SwapCallback() {
                @Override public void onSuccess(String txHash, double price) {
                    TradeRecord r    = store.openPosition(c, orderSize);
                    r.buyTxHash      = txHash;
                    r.entryAlgoScore = sig.score;
                    r.algoSignal     = sig.type;
                    r.peakPrice      = price;
                    RiskParams rp    = effectiveRisk(r.amountUsd, store);
                    r.trailingSl     = r.entryPrice * (1.0 - rp.sl / 100.0);
                    updateHistoryRecord(r);
                    DexEngine.this.notify(r);
                }
                @Override public void onFail(String reason) {
                    Log.w(TAG, "Live buy failed: " + reason);
                    NanuDatabase.get(ctx).logError("buy_failed", c.symbol + ": " + reason);
                    if (listener != null) listener.onError("Buy failed: " + reason);
                }
            });
        } else {
            TradeRecord r    = store.openPosition(c, orderSize);
            r.entryAlgoScore = sig.score;
            r.algoSignal     = sig.type;
            r.peakPrice      = c.priceUsd;
            RiskParams rp    = effectiveRisk(r.amountUsd, store);
            r.trailingSl     = r.entryPrice * (1.0 - rp.sl / 100.0);
            updateHistoryRecord(r);
            notify(r);
        }
    }

    private void updateHistoryRecord(TradeRecord updated) {
        // NanuDatabase upsert handles field updates directly
        NanuDatabase.get(ctx).upsertTrade(updated);
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

                // Fixed absolute distance used to anchor the trailing SL
                double trailDistance = r.entryPrice * rp.sl / 100.0;

                // Update trailing peak; also ratchet the trailing SL price upward
                boolean dirty = false;
                if (current > r.peakPrice) {
                    r.peakPrice = current;
                    dirty = true;
                }
                // Trailing SL: always (trailDistance) below the peak; only moves up, never down
                if (r.trailingSl == 0)
                    r.trailingSl = r.entryPrice - trailDistance; // init for legacy trades
                double newSl = r.peakPrice - trailDistance;
                if (newSl > r.trailingSl) {
                    r.trailingSl = newSl;
                    dirty = true;
                }
                if (dirty) NanuDatabase.get(ctx).upsertTrade(r);

                // Trailing stop (percentage-from-peak, only after price has risen 0.5%)
                double trailDrop = r.peakPrice > r.entryPrice
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

                // Price-locked trailing SL: exit when price falls below the ratcheted SL price
                else if (current <= r.trailingSl)
                    reason = "stop_loss";

                // Force close on max hold time
                else if (heldMin >= rp.holdMin)
                    reason = "force_close";

                // Liquidity-drop exit: >30% liq drop since entry = rug risk
                else if (r.liquidityAtEntry > 0 && live.liquidityUsd > 0) {
                    double liqDrop = (r.liquidityAtEntry - live.liquidityUsd)
                        / r.liquidityAtEntry * 100.0;
                    // Track lowest liquidity seen
                    if (r.liquidityLow <= 0 || live.liquidityUsd < r.liquidityLow) {
                        r.liquidityLow = live.liquidityUsd;
                        NanuDatabase.get(ctx).upsertTrade(r);
                    }
                    if (liqDrop >= 30.0) {
                        Log.w(TAG, "Liq drop " + String.format("%.1f", liqDrop)
                            + "% for " + r.tokenSymbol + " — emergency exit");
                        TelegramBot.notifyLiquidityDrop(store, r, liqDrop);
                        reason = "liq_drop_exit";
                    }
                }

                // Algo signal exit: bearish / sell-pressure / RSI overbought
                if (reason == null) {
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
                NanuDatabase.get(ctx).logError("monitor", r.tokenSymbol + ": " + e.getMessage());
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
        NanuDatabase.get(ctx).insertWalletEvent(r.chain, "trade_open", r.buyTxHash, r.amountUsd);
        if (listener != null) listener.onPositionOpened(r);
        Log.i(TAG, "Opened: " + r.tokenSymbol + " @ " + r.entryPrice);
    }

    private void notifyClose(TradeRecord r) {
        TelegramBot.notifyClose(store, r);
        TradeService.notifyClosed(ctx, r);
        NanuDatabase.get(ctx).insertWalletEvent(r.chain, "trade_close", r.sellTxHash, r.pnlUsd);
        if (store.autoMode) {
            BotEvolution.evolve(store);
            double avgPnl = store.totalTrades > 0 ? store.totalPnlUsd / store.totalTrades : 0;
            NanuDatabase.get(ctx).recordStratPerf(store.mlStrategy, store.mlGeneration,
                store.mlWinRate, avgPnl, store.totalTrades);
        }
        if (listener != null) listener.onPositionClosed(r);
        Log.i(TAG, "Closed: " + r.tokenSymbol + " P/L=" + String.format("%.4f", r.pnlUsd));
    }
}
