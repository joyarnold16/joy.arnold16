package com.nanu.aitradingbot;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DexAppStore {
    private static final String PREFS = "nanu_store_v11";

    // Wallet
    public String bnbAddress = "";
    public String solAddress = "";

    // ML strategy params (evolved by BotEvolution)
    public double stopLossPercent     = 1.5;
    public double takeProfitPercent   = 3.0;
    public double minMomentumPercent  = 1.0;   // fixed: was 0.3 (mismatch with load default)
    public double minLiquidityUsd     = 25_000; // fixed: was 15_000
    public int    minPairAgeHours     = 0;
    public int    maxPositions        = 3;
    public int    maxDailyTrades      = 10;
    public int    maxScanTokens       = 30;
    public int    scanIntervalMin     = 5;
    public int    maxHoldMinutes      = 15;

    // Algo trading settings
    public int     minAlgoScore      = 55;    // fixed: was 45
    public boolean useTrailingStop   = true;
    public double  trailingStopPct   = 1.0;
    public double  breakEvenTriggerPct = 1.5;

    // Auto Mode: ML controls all params
    public boolean autoMode     = false;

    // Live mode (off by default — paper first)
    public boolean liveMode           = false;
    public boolean liveChainBnb       = false;
    public boolean liveChainSol       = false;
    public double  tradeAmountUsd     = 0;
    public double  paperTradeAmountUsd = 10.0;
    public int     slippageBps        = 300;

    // Telegram
    public String telegramToken  = "";
    public String telegramChatId = "";

    // ML state
    public String mlStrategy   = "BALANCED";
    public int    mlGeneration = 0;
    public double mlWinRate    = 0;

    // Bot running state
    public boolean botRunning = false;

    // Risk management
    public double maxDailyLossUsd = 0;
    public double dailyPnlUsd     = 0;
    public long   lastLossTimeMs  = 0;
    public static final long REVENGE_COOLDOWN_MS = 10 * 60 * 1000L;

    // Loss-streak halt
    public int    curDayLossStreak  = 0;
    public int    maxDayLossStreak  = 3;  // halt after 3 consecutive losses
    public double maxWeeklyLossUsd  = 0;  // 0 = disabled
    public double weeklyPnlUsd      = 0;
    public long   lastWeekRollover  = 0;  // epoch-week of last rollover

    // Per-chain exposure cap (0 = disabled)
    public double maxChainExposureUsd = 0;

    // Stats
    public int    totalTrades = 0;
    public int    totalWins   = 0;
    public double totalPnlUsd = 0;
    public int    tradesToday = 0;
    public long   lastTradeDay = 0;

    private final SharedPreferences prefs;
    private final SecurePrefs secure;
    private final NanuDatabase db;

    public DexAppStore(Context ctx) {
        prefs  = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        secure = new SecurePrefs(ctx);
        db     = NanuDatabase.get(ctx);
        load();
        migrateIfNeeded();
    }

    public void save() {
        prefs.edit()
            .putString("bnbAddress", bnbAddress)
            .putString("solAddress", solAddress)
            .putFloat("stopLoss",   (float) stopLossPercent)
            .putFloat("takeProfit", (float) takeProfitPercent)
            .putFloat("minMom",     (float) minMomentumPercent)
            .putFloat("minLiq",     (float) minLiquidityUsd)
            .putInt("minAge",       minPairAgeHours)
            .putInt("maxPos",       maxPositions)
            .putInt("maxDaily",     maxDailyTrades)
            .putInt("maxScanTok",   maxScanTokens)
            .putBoolean("autoMode", autoMode)
            .putInt("scanMin",      scanIntervalMin)
            .putInt("maxHold",      maxHoldMinutes)
            .putInt("minAlgoScore",    minAlgoScore)
            .putBoolean("trailStop",   useTrailingStop)
            .putFloat("trailPct",      (float) trailingStopPct)
            .putFloat("breakEvenPct",  (float) breakEvenTriggerPct)
            .putBoolean("liveMode",    liveMode)
            .putBoolean("liveBnb",     liveChainBnb)
            .putBoolean("liveSol",     liveChainSol)
            .putFloat("tradeAmt",      (float) tradeAmountUsd)
            .putFloat("paperAmt",      (float) paperTradeAmountUsd)
            .putInt("slippageBps",    slippageBps)
            .putString("tgToken",   telegramToken)
            .putString("tgChat",    telegramChatId)
            .putString("mlStrat",   mlStrategy)
            .putInt("mlGen",        mlGeneration)
            .putFloat("mlWR",       (float) mlWinRate)
            .putFloat("maxDailyLoss", (float) maxDailyLossUsd)
            .putFloat("dailyPnl",    (float) dailyPnlUsd)
            .putLong("lastLossMs",   lastLossTimeMs)
            .putInt("totalTrades",  totalTrades)
            .putInt("totalWins",    totalWins)
            .putFloat("totalPnl",   (float) totalPnlUsd)
            .putInt("tradesToday",  tradesToday)
            .putLong("lastDay",     lastTradeDay)
            .putBoolean("botRunning", botRunning)
            .putInt("curLossStreak",  curDayLossStreak)
            .putInt("maxLossStreak",  maxDayLossStreak)
            .putFloat("maxWeekLoss",  (float) maxWeeklyLossUsd)
            .putFloat("weekPnl",      (float) weeklyPnlUsd)
            .putLong("lastWeekRoll",  lastWeekRollover)
            .putFloat("maxChainExp",  (float) maxChainExposureUsd)
            .apply();
    }

    private void load() {
        bnbAddress          = prefs.getString("bnbAddress", "");
        solAddress          = prefs.getString("solAddress", "");
        stopLossPercent     = prefs.getFloat("stopLoss",  1.5f);
        takeProfitPercent   = prefs.getFloat("takeProfit",3.0f);
        minMomentumPercent  = prefs.getFloat("minMom",   1.0f);
        minLiquidityUsd     = prefs.getFloat("minLiq",   25000f);
        minPairAgeHours     = prefs.getInt("minAge",     0);
        maxPositions        = prefs.getInt("maxPos",     3);
        maxDailyTrades      = prefs.getInt("maxDaily",   10);
        maxScanTokens       = prefs.getInt("maxScanTok", 30);
        autoMode            = prefs.getBoolean("autoMode", false);
        scanIntervalMin     = prefs.getInt("scanMin",    5);
        maxHoldMinutes      = prefs.getInt("maxHold",    15);
        minAlgoScore        = prefs.getInt("minAlgoScore",   55);
        useTrailingStop     = prefs.getBoolean("trailStop",   true);
        trailingStopPct     = prefs.getFloat("trailPct",      1.0f);
        breakEvenTriggerPct = prefs.getFloat("breakEvenPct",  1.5f);
        liveMode            = prefs.getBoolean("liveMode",   false);
        liveChainBnb        = prefs.getBoolean("liveBnb",    false);
        liveChainSol        = prefs.getBoolean("liveSol",    false);
        tradeAmountUsd      = prefs.getFloat("tradeAmt",     0f);
        paperTradeAmountUsd = prefs.getFloat("paperAmt",     10.0f);
        slippageBps         = prefs.getInt("slippageBps",    300);
        telegramToken       = prefs.getString("tgToken",  "");
        telegramChatId      = prefs.getString("tgChat",   "");
        mlStrategy          = prefs.getString("mlStrat",  "BALANCED");
        mlGeneration        = prefs.getInt("mlGen",      0);
        mlWinRate           = prefs.getFloat("mlWR",     0f);
        maxDailyLossUsd     = prefs.getFloat("maxDailyLoss", 0f);
        dailyPnlUsd         = prefs.getFloat("dailyPnl",     0f);
        lastLossTimeMs      = prefs.getLong("lastLossMs",    0L);
        totalTrades         = prefs.getInt("totalTrades",0);
        totalWins           = prefs.getInt("totalWins",  0);
        totalPnlUsd         = prefs.getFloat("totalPnl", 0f);
        tradesToday         = prefs.getInt("tradesToday",0);
        lastTradeDay        = prefs.getLong("lastDay",   0L);
        botRunning          = prefs.getBoolean("botRunning", false);
        curDayLossStreak    = prefs.getInt("curLossStreak",  0);
        maxDayLossStreak    = prefs.getInt("maxLossStreak",  3);
        maxWeeklyLossUsd    = prefs.getFloat("maxWeekLoss",  0f);
        weeklyPnlUsd        = prefs.getFloat("weekPnl",      0f);
        lastWeekRollover    = prefs.getLong("lastWeekRoll",  0L);
        maxChainExposureUsd = prefs.getFloat("maxChainExp",  0f);
    }

    /** One-time migration from SharedPreferences JSON blob to NanuDatabase. */
    private void migrateIfNeeded() {
        if (!prefs.getBoolean("db_migrated", false)) {
            String json = prefs.getString("trades", "[]");
            if (!"[]".equals(json) && !json.isEmpty()) {
                try {
                    List<TradeRecord> old = loadHistoryFromJson(json);
                    if (!old.isEmpty()) {
                        db.upsertTrades(old);
                        android.util.Log.i("DexAppStore", "Migrated " + old.size() + " trades to SQLite");
                    }
                } catch (Exception e) {
                    android.util.Log.w("DexAppStore", "Migration failed: " + e.getMessage());
                }
            }
            prefs.edit().putBoolean("db_migrated", true).remove("trades").apply();
        }
    }

    public void reload() { load(); }

    public void rolloverDayIfNeeded() {
        long today = System.currentTimeMillis() / 86_400_000L;
        if (today != lastTradeDay) {
            tradesToday       = 0;
            dailyPnlUsd       = 0;
            curDayLossStreak  = 0;
            lastTradeDay      = today;
            save();
        }
    }

    public void rolloverWeekIfNeeded() {
        long week = System.currentTimeMillis() / (7L * 86_400_000L);
        if (week != lastWeekRollover) {
            weeklyPnlUsd    = 0;
            lastWeekRollover = week;
            save();
        }
    }

    public boolean isDailyLossLimitHit() {
        return maxDailyLossUsd > 0 && dailyPnlUsd <= -maxDailyLossUsd;
    }

    public boolean isDayLossStreakHit() {
        return maxDayLossStreak > 0 && curDayLossStreak >= maxDayLossStreak;
    }

    public boolean isWeeklyLossLimitHit() {
        return maxWeeklyLossUsd > 0 && weeklyPnlUsd <= -maxWeeklyLossUsd;
    }

    public boolean isRevengeTradeCooldownActive() {
        if (lastLossTimeMs <= 0) return false;
        return (System.currentTimeMillis() - lastLossTimeMs) < REVENGE_COOLDOWN_MS;
    }

    public long revengeCooldownRemainingMs() {
        if (!isRevengeTradeCooldownActive()) return 0;
        return REVENGE_COOLDOWN_MS - (System.currentTimeMillis() - lastLossTimeMs);
    }

    /**
     * Dynamic position sizing.
     * Scales the base size down for: high scam risk, loss streak, thin liquidity.
     * Never exceeds chain exposure cap.
     */
    public double calcDynamicPositionSize(DexCandidate c) {
        double base = liveMode ? tradeAmountUsd : paperTradeAmountUsd;
        if (base <= 0) return base;

        double factor = 1.0;

        // Scam risk reduces size
        if (c.scamRiskScore >= 60) factor *= 0.5;
        else if (c.scamRiskScore >= 40) factor *= 0.75;

        // Loss streak reduces size
        if (curDayLossStreak >= 2) factor *= Math.pow(0.75, curDayLossStreak - 1);

        // Thin liquidity reduces size
        if (c.liquidityUsd > 0 && c.liquidityUsd < 50_000) factor *= 0.8;

        double sized = base * factor;

        // Chain exposure cap
        if (maxChainExposureUsd > 0) {
            double chainExposure = getChainExposureUsd(c.chain);
            double remaining = maxChainExposureUsd - chainExposure;
            if (remaining <= 0) return 0;
            sized = Math.min(sized, remaining);
        }

        // Floor: $1 paper, $5 live
        double floor = liveMode ? 5.0 : 1.0;
        return Math.max(floor, sized);
    }

    /** Sum of amountUsd for all currently open positions on the given chain. */
    public double getChainExposureUsd(String chain) {
        double total = 0;
        for (TradeRecord r : getOpenPositions())
            if (chain.equals(r.chain)) total += r.amountUsd;
        return total;
    }

    public String getMnemonic()         { return secure.loadMnemonic(); }
    public boolean hasWallet()          { return !bnbAddress.isEmpty(); }
    public boolean hasTelegram()        { return !telegramToken.isEmpty() && !telegramChatId.isEmpty(); }

    public void saveMnemonic(String m) {
        try {
            secure.saveMnemonic(m);
            bnbAddress = secure.deriveBnbAddress(m);
            solAddress = secure.deriveSolAddress(m);
        } catch (Throwable e) {
            android.util.Log.e("DexAppStore", "saveMnemonic failed: " + e.getMessage());
        }
        save();
    }

    // ── Trade History ─────────────────────────────────────────────────────

    public List<TradeRecord> loadHistory() {
        return db.getAllTrades();
    }

    public void saveHistory(List<TradeRecord> trades) {
        db.upsertTrades(trades);
    }

    public TradeRecord openPosition(DexCandidate c) {
        return openPosition(c, liveMode ? tradeAmountUsd : paperTradeAmountUsd);
    }

    public TradeRecord openPosition(DexCandidate c, double orderSize) {
        TradeRecord r     = new TradeRecord();
        r.id              = UUID.randomUUID().toString();
        r.tokenName       = c.name;
        r.tokenSymbol     = c.symbol;
        r.tokenAddress    = c.tokenAddress;
        r.pairAddress     = c.pairAddress;
        r.chain           = c.chain;
        r.entryPrice      = c.priceUsd;
        r.openTimeMs      = System.currentTimeMillis();
        r.isOpen          = true;
        r.strategyName    = mlStrategy;
        r.confidenceScore = c.score;
        r.patterns        = new ArrayList<>(c.patterns);
        r.amountUsd       = orderSize;
        r.isLive          = liveMode;
        r.peakPrice       = c.priceUsd;
        r.liquidityAtEntry = c.liquidityUsd;
        r.volumeAtEntry    = c.volumeUsd24h;
        r.fdvAtEntry       = c.fdv;
        r.scamScore        = c.scamRiskScore;
        r.chainSafetyScore = c.chainSafetyScore;
        r.sellSimOk        = c.sellSimOk;

        db.upsertTrade(r);

        long today = System.currentTimeMillis() / 86_400_000L;
        if (today != lastTradeDay) { tradesToday = 0; lastTradeDay = today; }
        tradesToday++;
        save();
        return r;
    }

    public TradeRecord closePosition(String id, double exitPrice, String reason,
                                     String sellTxHash) {
        List<TradeRecord> list = loadHistory();
        TradeRecord closed = null;
        for (TradeRecord r : list) {
            if (id.equals(r.id) && r.isOpen) {
                r.isOpen      = false;
                r.exitPrice   = exitPrice;
                r.closeTimeMs = System.currentTimeMillis();
                r.exitReason  = reason;
                r.sellTxHash  = sellTxHash != null ? sellTxHash : "";
                r.pnlPercent  = r.entryPrice > 0
                    ? (exitPrice - r.entryPrice) / r.entryPrice * 100.0 : 0;
                r.pnlUsd      = r.amountUsd * r.pnlPercent / 100.0;
                r.isWin       = r.pnlUsd > 0;
                totalTrades++;
                if (r.isWin) {
                    totalWins++;
                    curDayLossStreak = 0;  // reset streak on win
                } else {
                    lastLossTimeMs    = System.currentTimeMillis();
                    curDayLossStreak++;    // increment streak on loss
                }
                totalPnlUsd += r.pnlUsd;
                dailyPnlUsd += r.pnlUsd;
                weeklyPnlUsd += r.pnlUsd;
                mlWinRate    = totalTrades > 0 ? (double) totalWins / totalTrades * 100.0 : 0;
                db.upsertTrade(r);
                closed = r;
                break;
            }
        }
        save();
        return closed;
    }

    public List<TradeRecord> getOpenPositions() {
        return db.getOpenTrades();
    }

    public int openPositionCount() {
        return db.countOpenTrades();
    }

    // ── Extended Stats ────────────────────────────────────────────────────

    public static class Stats {
        public double avgWinUsd      = 0;
        public double avgLossUsd     = 0;
        public double maxDrawdownUsd = 0;
        public double profitFactor   = 0;
        public int    maxLossStreak  = 0;
        public int    curLossStreak  = 0;
        public double expectancyUsd  = 0;
    }

    public Stats computeStats() {
        Stats s = new Stats();
        List<TradeRecord> closed = new ArrayList<>();
        for (TradeRecord r : loadHistory())
            if (!r.isOpen) closed.add(r);
        if (closed.isEmpty()) return s;

        double totalWinAmt = 0, totalLossAmt = 0;
        int wins = 0, losses = 0, streak = 0;
        double runningPnl = 0, peakPnl = 0;

        for (TradeRecord r : closed) {
            if (r.isWin) {
                totalWinAmt += r.pnlUsd;
                wins++;
                streak = 0;
            } else {
                totalLossAmt += Math.abs(r.pnlUsd);
                losses++;
                streak++;
                if (streak > s.maxLossStreak) s.maxLossStreak = streak;
            }
            runningPnl += r.pnlUsd;
            if (runningPnl > peakPnl) peakPnl = runningPnl;
            double dd = peakPnl - runningPnl;
            if (dd > s.maxDrawdownUsd) s.maxDrawdownUsd = dd;
        }

        s.curLossStreak = streak;
        s.avgWinUsd     = wins   > 0 ? totalWinAmt  / wins   : 0;
        s.avgLossUsd    = losses > 0 ? totalLossAmt / losses  : 0;
        s.profitFactor  = totalLossAmt > 0 ? totalWinAmt / totalLossAmt
                        : (totalWinAmt > 0 ? 99.9 : 0);
        s.expectancyUsd = closed.isEmpty() ? 0 : (totalWinAmt - totalLossAmt) / closed.size();
        return s;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Parse the legacy SharedPreferences JSON blob into TradeRecord list. */
    private List<TradeRecord> loadHistoryFromJson(String json) {
        List<TradeRecord> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                TradeRecord r      = new TradeRecord();
                r.id               = o.optString("id", UUID.randomUUID().toString());
                r.tokenName        = o.optString("name");
                r.tokenSymbol      = o.optString("sym");
                r.tokenAddress     = o.optString("addr");
                r.pairAddress      = o.optString("pair", "");
                r.chain            = o.optString("chain");
                r.entryPrice       = o.optDouble("entry");
                r.exitPrice        = o.optDouble("exit");
                r.amountUsd        = o.optDouble("amt", 10.0);
                r.pnlUsd           = o.optDouble("pnl");
                r.pnlPercent       = o.optDouble("pct");
                r.openTimeMs       = o.optLong("openMs");
                r.closeTimeMs      = o.optLong("closeMs");
                r.isWin            = o.optBoolean("win");
                r.isOpen           = o.optBoolean("open");
                r.exitReason       = o.optString("reason");
                r.strategyName     = o.optString("strat");
                r.confidenceScore  = o.optInt("conf");
                r.isLive           = o.optBoolean("live");
                r.buyTxHash        = o.optString("buyTx",  "");
                r.sellTxHash       = o.optString("sellTx", "");
                r.liquidityAtEntry = o.optDouble("liqEntry", 0);
                r.volumeAtEntry    = o.optDouble("volEntry",  0);
                r.fdvAtEntry       = o.optDouble("fdvEntry",  0);
                r.scamScore        = o.optInt("scamScore",    0);
                r.entryAlgoScore   = o.optInt("algoEntry",    0);
                r.exitAlgoScore    = o.optInt("algoExit",     0);
                r.algoSignal       = o.optString("algoSig",  "");
                r.peakPrice        = o.optDouble("peak",       0);
                list.add(r);
            }
        } catch (Exception ignored) {}
        return list;
    }
}
