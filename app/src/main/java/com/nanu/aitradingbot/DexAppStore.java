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
    public double minMomentumPercent  = 1.0;
    public double minLiquidityUsd     = 25_000;
    public int    minPairAgeHours     = 0;
    public int    maxPositions        = 3;
    public int    maxDailyTrades      = 10;
    public int    maxScanTokens       = 30;
    public int    scanIntervalMin     = 5;
    public int    maxHoldMinutes      = 15;

    // Algo trading settings
    public int     minAlgoScore      = 55;
    public boolean useTrailingStop   = true;
    public double  trailingStopPct   = 1.0;

    // Auto Mode: ML controls all params; manual overrides locked
    public boolean autoMode     = false;

    // Live mode (off by default — paper first)
    public boolean liveMode     = false;
    public boolean liveChainBnb = false;
    public boolean liveChainSol = false;
    public double  tradeAmountUsd = 0;

    // Telegram
    public String telegramToken  = "";
    public String telegramChatId = "";

    // ML state
    public String mlStrategy   = "BALANCED";
    public int    mlGeneration = 0;
    public double mlWinRate    = 0;

    // Bot running state (persisted for boot-restart)
    public boolean botRunning = false;

    // Stats
    public int    totalTrades = 0;
    public int    totalWins   = 0;
    public double totalPnlUsd = 0;
    public int    tradesToday = 0;
    public long   lastTradeDay = 0;

    private final SharedPreferences prefs;
    private final SecurePrefs secure;

    public DexAppStore(Context ctx) {
        prefs  = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        secure = new SecurePrefs(ctx);
        load();
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
            .putFloat("trailPct",     (float) trailingStopPct)
            .putBoolean("liveMode",    liveMode)
            .putBoolean("liveBnb",     liveChainBnb)
            .putBoolean("liveSol",     liveChainSol)
            .putFloat("tradeAmt",     (float) tradeAmountUsd)
            .putString("tgToken",   telegramToken)
            .putString("tgChat",    telegramChatId)
            .putString("mlStrat",   mlStrategy)
            .putInt("mlGen",        mlGeneration)
            .putFloat("mlWR",       (float) mlWinRate)
            .putInt("totalTrades",  totalTrades)
            .putInt("totalWins",    totalWins)
            .putFloat("totalPnl",   (float) totalPnlUsd)
            .putInt("tradesToday",  tradesToday)
            .putLong("lastDay",     lastTradeDay)
            .putBoolean("botRunning", botRunning)
            .apply();
    }

    private void load() {
        bnbAddress         = prefs.getString("bnbAddress", "");
        solAddress         = prefs.getString("solAddress", "");
        stopLossPercent    = prefs.getFloat("stopLoss",  1.5f);
        takeProfitPercent  = prefs.getFloat("takeProfit",3.0f);
        minMomentumPercent = prefs.getFloat("minMom",   1.0f);
        minLiquidityUsd    = prefs.getFloat("minLiq",   25000f);
        minPairAgeHours    = prefs.getInt("minAge",     0);
        maxPositions       = prefs.getInt("maxPos",     3);
        maxDailyTrades     = prefs.getInt("maxDaily",   10);
        maxScanTokens      = prefs.getInt("maxScanTok", 30);
        autoMode           = prefs.getBoolean("autoMode", false);
        scanIntervalMin    = prefs.getInt("scanMin",    5);
        maxHoldMinutes     = prefs.getInt("maxHold",    15);
        minAlgoScore       = prefs.getInt("minAlgoScore",   55);
        useTrailingStop    = prefs.getBoolean("trailStop",   true);
        trailingStopPct    = prefs.getFloat("trailPct",      1.0f);
        liveMode           = prefs.getBoolean("liveMode",   false);
        liveChainBnb       = prefs.getBoolean("liveBnb",    false);
        liveChainSol       = prefs.getBoolean("liveSol",    false);
        tradeAmountUsd     = prefs.getFloat("tradeAmt",     0f);
        telegramToken      = prefs.getString("tgToken",  "");
        telegramChatId     = prefs.getString("tgChat",   "");
        mlStrategy         = prefs.getString("mlStrat",  "BALANCED");
        mlGeneration       = prefs.getInt("mlGen",      0);
        mlWinRate          = prefs.getFloat("mlWR",     0f);
        totalTrades        = prefs.getInt("totalTrades",0);
        totalWins          = prefs.getInt("totalWins",  0);
        totalPnlUsd        = prefs.getFloat("totalPnl", 0f);
        tradesToday        = prefs.getInt("tradesToday",0);
        lastTradeDay       = prefs.getLong("lastDay",   0L);
        botRunning         = prefs.getBoolean("botRunning", false);
    }

    public void reload() { load(); }

    /** Resets the daily trade counter when the calendar day rolls over,
     *  even if no trade was opened (e.g. bot ran idle past midnight). */
    public void rolloverDayIfNeeded() {
        long today = System.currentTimeMillis() / 86_400_000L;
        if (today != lastTradeDay) {
            tradesToday  = 0;
            lastTradeDay = today;
            save();
        }
    }

    public String getMnemonic()             { return secure.loadMnemonic(); }
    public boolean hasWallet()              { return !bnbAddress.isEmpty(); }
    public boolean hasTelegram()            { return !telegramToken.isEmpty() && !telegramChatId.isEmpty(); }

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
        List<TradeRecord> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("trades", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                TradeRecord r   = new TradeRecord();
                r.id            = o.optString("id", UUID.randomUUID().toString());
                r.tokenName     = o.optString("name");
                r.tokenSymbol   = o.optString("sym");
                r.tokenAddress  = o.optString("addr");
                r.chain         = o.optString("chain");
                r.entryPrice    = o.optDouble("entry");
                r.exitPrice     = o.optDouble("exit");
                r.amountUsd     = o.optDouble("amt", 10.0);
                r.pnlUsd        = o.optDouble("pnl");
                r.pnlPercent    = o.optDouble("pct");
                r.openTimeMs    = o.optLong("openMs");
                r.closeTimeMs   = o.optLong("closeMs");
                r.isWin         = o.optBoolean("win");
                r.isOpen        = o.optBoolean("open");
                r.exitReason    = o.optString("reason");
                r.strategyName  = o.optString("strat");
                r.confidenceScore = o.optInt("conf");
                r.isLive         = o.optBoolean("live");
                r.buyTxHash      = o.optString("buyTx",  "");
                r.sellTxHash     = o.optString("sellTx", "");
                r.entryAlgoScore = o.optInt("algoEntry", 0);
                r.exitAlgoScore  = o.optInt("algoExit",  0);
                r.algoSignal     = o.optString("algoSig", "");
                r.peakPrice      = o.optDouble("peak",   0);
                list.add(r);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void saveHistory(List<TradeRecord> trades) {
        try {
            JSONArray arr = new JSONArray();
            for (TradeRecord r : trades) {
                JSONObject o = new JSONObject();
                o.put("id",    r.id);
                o.put("name",  r.tokenName);
                o.put("sym",   r.tokenSymbol);
                o.put("addr",  r.tokenAddress);
                o.put("chain", r.chain);
                o.put("entry", r.entryPrice);
                o.put("exit",  r.exitPrice);
                o.put("amt",   r.amountUsd);
                o.put("pnl",   r.pnlUsd);
                o.put("pct",   r.pnlPercent);
                o.put("openMs",  r.openTimeMs);
                o.put("closeMs", r.closeTimeMs);
                o.put("win",   r.isWin);
                o.put("open",  r.isOpen);
                o.put("reason",r.exitReason);
                o.put("strat", r.strategyName);
                o.put("conf",  r.confidenceScore);
                o.put("live",     r.isLive);
                o.put("buyTx",    r.buyTxHash);
                o.put("sellTx",   r.sellTxHash);
                o.put("algoEntry",r.entryAlgoScore);
                o.put("algoExit", r.exitAlgoScore);
                o.put("algoSig",  r.algoSignal);
                o.put("peak",     r.peakPrice);
                arr.put(o);
            }
            prefs.edit().putString("trades", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public TradeRecord openPosition(DexCandidate c) {
        TradeRecord r   = new TradeRecord();
        r.id            = UUID.randomUUID().toString();
        r.tokenName     = c.name;
        r.tokenSymbol   = c.symbol;
        r.tokenAddress  = c.tokenAddress;
        r.chain         = c.chain;
        r.entryPrice    = c.priceUsd;
        r.openTimeMs    = System.currentTimeMillis();
        r.isOpen        = true;
        r.strategyName    = mlStrategy;
        r.confidenceScore = c.score;
        r.patterns        = new ArrayList<>(c.patterns);
        r.amountUsd       = liveMode ? tradeAmountUsd : 10.0;
        r.isLive          = liveMode;
        r.peakPrice       = c.priceUsd;
        List<TradeRecord> list = loadHistory();
        list.add(0, r);
        if (list.size() > 500) list = list.subList(0, 500);
        saveHistory(list);
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
                if (r.isWin) totalWins++;
                totalPnlUsd  += r.pnlUsd;
                mlWinRate     = totalTrades > 0 ? (double) totalWins / totalTrades * 100.0 : 0;
                closed = r;
                break;
            }
        }
        saveHistory(list);
        save();
        return closed;
    }

    public List<TradeRecord> getOpenPositions() {
        List<TradeRecord> open = new ArrayList<>();
        for (TradeRecord r : loadHistory())
            if (r.isOpen) open.add(r);
        return open;
    }

    public int openPositionCount() {
        return getOpenPositions().size();
    }
}
