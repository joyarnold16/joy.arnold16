package com.nanu.aitradingbot;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQLite persistence layer for Nanu AI Trading Bot.
 * Replaces SharedPreferences JSON for trade history.
 * Settings remain in SharedPreferences (small, frequently read).
 *
 * Tables: trades, rejected_tokens, on_chain_checks, sell_simulations,
 *         daily_stats, bot_errors, strategy_performance, market_snapshots,
 *         tokens, pairs, signals, risk_checks, wallet_events
 */
public class NanuDatabase extends SQLiteOpenHelper {
    private static final String TAG     = "NanuDB";
    private static final String DB_NAME = "nanu_trading.db";
    private static final int    DB_VER  = 2;

    private static volatile NanuDatabase instance;

    public static NanuDatabase get(Context ctx) {
        if (instance == null) {
            synchronized (NanuDatabase.class) {
                if (instance == null)
                    instance = new NanuDatabase(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    private NanuDatabase(Context ctx) {
        super(ctx, DB_NAME, null, DB_VER);
        getWritableDatabase().execSQL("PRAGMA journal_mode=WAL");
    }

    // ─ DDL ──────────────────────────────────────────────────────────────────

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS trades ("
            + "id TEXT PRIMARY KEY, token_name TEXT, token_symbol TEXT, "
            + "token_address TEXT, pair_address TEXT, chain TEXT, "
            + "entry_price REAL, exit_price REAL, amount_usd REAL, "
            + "pnl_usd REAL, pnl_pct REAL, open_ms INTEGER, close_ms INTEGER, "
            + "is_win INTEGER DEFAULT 0, is_open INTEGER DEFAULT 1, "
            + "exit_reason TEXT, strategy TEXT, confidence INTEGER DEFAULT 0, "
            + "is_live INTEGER DEFAULT 0, buy_tx TEXT DEFAULT '', sell_tx TEXT DEFAULT '', "
            + "liq_entry REAL DEFAULT 0, vol_entry REAL DEFAULT 0, "
            + "fdv_entry REAL DEFAULT 0, scam_score INTEGER DEFAULT 0, "
            + "algo_entry INTEGER DEFAULT 0, algo_exit INTEGER DEFAULT 0, "
            + "algo_signal TEXT DEFAULT '', peak_price REAL DEFAULT 0, "
            + "chain_safety INTEGER DEFAULT 0, sell_sim_ok INTEGER DEFAULT 1, "
            + "liq_low REAL DEFAULT 0, trailing_sl REAL DEFAULT 0, created_ms INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS rejected_tokens ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "token_address TEXT, token_symbol TEXT, chain TEXT, "
            + "reject_reason TEXT, score INTEGER DEFAULT 0, "
            + "scam_score INTEGER DEFAULT 0, liquidity_usd REAL DEFAULT 0, "
            + "fdv_ratio REAL DEFAULT 0, rejected_at_ms INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS on_chain_checks ("
            + "token_address TEXT PRIMARY KEY, chain TEXT, "
            + "mint_revoked INTEGER DEFAULT 0, freeze_revoked INTEGER DEFAULT 0, "
            + "is_honeypot INTEGER DEFAULT 0, can_sell INTEGER DEFAULT 1, "
            + "sell_tax_pct REAL DEFAULT 0, buy_tax_pct REAL DEFAULT 0, "
            + "contract_verified INTEGER DEFAULT 0, owner_renounced INTEGER DEFAULT 0, "
            + "lp_burned INTEGER DEFAULT 0, lp_locked INTEGER DEFAULT 0, "
            + "top_holder_pct REAL DEFAULT 0, owner_power_flags TEXT DEFAULT '', "
            + "safety_score INTEGER DEFAULT 0, note TEXT DEFAULT '', "
            + "checked_at_ms INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS sell_simulations ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "token_address TEXT, chain TEXT, "
            + "can_sell INTEGER DEFAULT 0, price_impact_pct REAL DEFAULT 0, "
            + "note TEXT DEFAULT '', checked_at_ms INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS daily_stats ("
            + "day_key INTEGER PRIMARY KEY, "
            + "trades INTEGER DEFAULT 0, wins INTEGER DEFAULT 0, losses INTEGER DEFAULT 0, "
            + "pnl_usd REAL DEFAULT 0, win_rate REAL DEFAULT 0, "
            + "max_loss_streak INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS bot_errors ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "error_type TEXT, message TEXT, occurred_at_ms INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS strategy_performance ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "strategy_name TEXT, generation INTEGER, "
            + "win_rate REAL, avg_pnl_usd REAL, total_trades INTEGER, "
            + "recorded_at_ms INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS market_snapshots ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "token_address TEXT, chain TEXT, price_usd REAL, "
            + "liquidity_usd REAL, volume_24h REAL, buy_pressure REAL, "
            + "fdv_ratio REAL, score INTEGER, snapped_at_ms INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS tokens ("
            + "address TEXT PRIMARY KEY, chain TEXT, name TEXT, symbol TEXT, "
            + "first_seen_ms INTEGER, last_seen_ms INTEGER, "
            + "times_qualified INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS pairs ("
            + "pair_address TEXT PRIMARY KEY, token_address TEXT, chain TEXT, "
            + "dex_name TEXT, quote_token TEXT, "
            + "created_at_ms INTEGER, last_seen_ms INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS signals ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "token_address TEXT, chain TEXT, signal_type TEXT, "
            + "algo_score INTEGER, signaled_at_ms INTEGER, acted_on INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS risk_checks ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "token_address TEXT, chain TEXT, check_result TEXT, "
            + "block_reason TEXT, order_size_usd REAL, "
            + "liq_at_check REAL, checked_at_ms INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS wallet_events ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "chain TEXT, event_type TEXT, tx_hash TEXT, "
            + "amount_usd REAL, occurred_at_ms INTEGER)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2)
            db.execSQL("ALTER TABLE trades ADD COLUMN trailing_sl REAL DEFAULT 0");
    }

    // ─ TRADES ────────────────────────────────────────────────────────────────

    public void upsertTrade(TradeRecord r) {
        try {
            ContentValues v = tradeToValues(r);
            getWritableDatabase().insertWithOnConflict(
                "trades", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) { Log.w(TAG, "upsertTrade: " + e.getMessage()); }
    }

    public void upsertTrades(List<TradeRecord> trades) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (TradeRecord r : trades)
                db.insertWithOnConflict("trades", null, tradeToValues(r),
                    SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public List<TradeRecord> getAllTrades() {
        List<TradeRecord> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "trades", null, null, null, null, null, "open_ms DESC")) {
            while (c.moveToNext()) list.add(cursorToTrade(c));
        } catch (Exception e) { Log.w(TAG, "getAllTrades: " + e.getMessage()); }
        return list;
    }

    public List<TradeRecord> getOpenTrades() {
        List<TradeRecord> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "trades", null, "is_open=1", null, null, null, "open_ms DESC")) {
            while (c.moveToNext()) list.add(cursorToTrade(c));
        } catch (Exception e) { Log.w(TAG, "getOpenTrades: " + e.getMessage()); }
        return list;
    }

    public int countOpenTrades() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM trades WHERE is_open=1", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) { return 0; }
    }

    public double sumOpenExposure(String chain) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT SUM(amount_usd) FROM trades WHERE is_open=1 AND chain=?",
                new String[]{chain})) {
            return c.moveToFirst() && !c.isNull(0) ? c.getDouble(0) : 0;
        } catch (Exception e) { return 0; }
    }

    public boolean isTokenOpen(String tokenAddress) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM trades WHERE is_open=1 AND token_address=?",
                new String[]{tokenAddress})) {
            return c.moveToFirst() && c.getInt(0) > 0;
        } catch (Exception e) { return false; }
    }

    // Migration from SharedPreferences JSON list
    public void migrateFromList(List<TradeRecord> trades) {
        if (trades == null || trades.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (TradeRecord r : trades) {
                if (r.id == null || r.id.isEmpty()) r.id = UUID.randomUUID().toString();
                ContentValues v = tradeToValues(r);
                v.put("created_ms", r.openTimeMs);
                db.insertWithOnConflict("trades", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
            Log.i(TAG, "Migrated " + trades.size() + " trades from SharedPreferences");
        } catch (Exception e) {
            Log.e(TAG, "Migration failed: " + e.getMessage());
        } finally { db.endTransaction(); }
    }

    // ─ REJECTED TOKENS ───────────────────────────────────────────────────────

    public void insertRejected(DexCandidate c, String reason) {
        try {
            ContentValues v = new ContentValues();
            v.put("token_address", c.tokenAddress);
            v.put("token_symbol",  c.symbol);
            v.put("chain",         c.chain);
            v.put("reject_reason", reason);
            v.put("score",         c.score);
            v.put("scam_score",    c.scamRiskScore);
            v.put("liquidity_usd", c.liquidityUsd);
            v.put("fdv_ratio",     c.fdvLiquidityRatio);
            v.put("rejected_at_ms", System.currentTimeMillis());
            getWritableDatabase().insert("rejected_tokens", null, v);
        } catch (Exception e) { Log.w(TAG, "insertRejected: " + e.getMessage()); }
    }

    // ─ ON-CHAIN CACHE ────────────────────────────────────────────────────────

    public void cacheOnChain(DexCandidate c) {
        try {
            ContentValues v = new ContentValues();
            v.put("token_address",    c.tokenAddress);
            v.put("chain",            c.chain);
            v.put("mint_revoked",     c.mintAuthorityRevoked   ? 1 : 0);
            v.put("freeze_revoked",   c.freezeAuthorityRevoked ? 1 : 0);
            v.put("contract_verified",c.contractVerified       ? 1 : 0);
            v.put("owner_renounced",  c.ownerRenounced         ? 1 : 0);
            v.put("lp_burned",        c.lpBurned               ? 1 : 0);
            v.put("lp_locked",        c.lpLocked               ? 1 : 0);
            v.put("owner_power_flags",c.ownerPowerFlags);
            v.put("safety_score",     c.chainSafetyScore);
            v.put("note",             c.onChainNote);
            v.put("checked_at_ms",    System.currentTimeMillis());
            getWritableDatabase().insertWithOnConflict("on_chain_checks", null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) { Log.w(TAG, "cacheOnChain: " + e.getMessage()); }
    }

    // ─ SELL SIMULATIONS ──────────────────────────────────────────────────────

    public void insertSellSim(String addr, String chain, boolean canSell,
                               double impactPct, String note) {
        try {
            ContentValues v = new ContentValues();
            v.put("token_address",    addr);
            v.put("chain",            chain);
            v.put("can_sell",         canSell ? 1 : 0);
            v.put("price_impact_pct", impactPct);
            v.put("note",             note);
            v.put("checked_at_ms",    System.currentTimeMillis());
            getWritableDatabase().insert("sell_simulations", null, v);
        } catch (Exception e) { Log.w(TAG, "insertSellSim: " + e.getMessage()); }
    }

    // ─ DAILY STATS ───────────────────────────────────────────────────────────

    public void upsertDailyStats(long dayKey, int trades, int wins,
                                  double pnlUsd, double winRate, int maxStreak) {
        try {
            ContentValues v = new ContentValues();
            v.put("day_key",         dayKey);
            v.put("trades",          trades);
            v.put("wins",            wins);
            v.put("losses",          trades - wins);
            v.put("pnl_usd",         pnlUsd);
            v.put("win_rate",        winRate);
            v.put("max_loss_streak", maxStreak);
            getWritableDatabase().insertWithOnConflict("daily_stats", null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) { Log.w(TAG, "upsertDailyStats: " + e.getMessage()); }
    }

    // ─ BOT ERRORS ────────────────────────────────────────────────────────────

    public void logError(String type, String msg) {
        try {
            ContentValues v = new ContentValues();
            v.put("error_type",     type);
            v.put("message",        msg);
            v.put("occurred_at_ms", System.currentTimeMillis());
            getWritableDatabase().insert("bot_errors", null, v);
        } catch (Exception ignore) {}
    }

    // ─ STRATEGY PERFORMANCE ──────────────────────────────────────────────────

    public void recordStratPerf(String name, int gen, double winRate,
                                 double avgPnl, int trades) {
        try {
            ContentValues v = new ContentValues();
            v.put("strategy_name",  name);
            v.put("generation",     gen);
            v.put("win_rate",       winRate);
            v.put("avg_pnl_usd",    avgPnl);
            v.put("total_trades",   trades);
            v.put("recorded_at_ms", System.currentTimeMillis());
            getWritableDatabase().insert("strategy_performance", null, v);
        } catch (Exception e) { Log.w(TAG, "recordStratPerf: " + e.getMessage()); }
    }

    // ─ MARKET SNAPSHOTS ──────────────────────────────────────────────────────

    public void insertSnapshot(DexCandidate c) {
        try {
            ContentValues v = new ContentValues();
            v.put("token_address", c.tokenAddress);
            v.put("chain",         c.chain);
            v.put("price_usd",     c.priceUsd);
            v.put("liquidity_usd", c.liquidityUsd);
            v.put("volume_24h",    c.volumeUsd24h);
            v.put("buy_pressure",  c.buyPressure);
            v.put("fdv_ratio",     c.fdvLiquidityRatio);
            v.put("score",         c.score);
            v.put("snapped_at_ms", System.currentTimeMillis());
            getWritableDatabase().insert("market_snapshots", null, v);
        } catch (Exception e) { Log.w(TAG, "insertSnapshot: " + e.getMessage()); }
    }

    // ─ TOKENS & PAIRS ────────────────────────────────────────────────────────

    public void upsertToken(DexCandidate c) {
        try {
            long now = System.currentTimeMillis();
            int rows = getWritableDatabase().update("tokens",
                cv("last_seen_ms", now), "address=?", new String[]{c.tokenAddress});
            if (rows == 0) {
                ContentValues v = new ContentValues();
                v.put("address",        c.tokenAddress);
                v.put("chain",          c.chain);
                v.put("name",           c.name);
                v.put("symbol",         c.symbol);
                v.put("first_seen_ms",  now);
                v.put("last_seen_ms",   now);
                v.put("times_qualified", 1);
                getWritableDatabase().insert("tokens", null, v);
            } else {
                getWritableDatabase().execSQL(
                    "UPDATE tokens SET times_qualified=times_qualified+1 WHERE address=?",
                    new Object[]{c.tokenAddress});
            }
        } catch (Exception e) { Log.w(TAG, "upsertToken: " + e.getMessage()); }
    }

    public void upsertPair(DexCandidate c) {
        if (c.pairAddress == null || c.pairAddress.isEmpty()) return;
        try {
            ContentValues v = new ContentValues();
            v.put("pair_address",  c.pairAddress);
            v.put("token_address", c.tokenAddress);
            v.put("chain",         c.chain);
            v.put("dex_name",      c.dexName);
            v.put("quote_token",   c.quoteToken);
            v.put("created_at_ms", c.pairCreatedAtMs);
            v.put("last_seen_ms",  System.currentTimeMillis());
            getWritableDatabase().insertWithOnConflict("pairs", null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) { Log.w(TAG, "upsertPair: " + e.getMessage()); }
    }

    // ─ SIGNALS ───────────────────────────────────────────────────────────────

    public void insertSignal(DexCandidate c, boolean actedOn) {
        try {
            ContentValues v = new ContentValues();
            v.put("token_address",  c.tokenAddress);
            v.put("chain",          c.chain);
            v.put("signal_type",    c.algoSignal);
            v.put("algo_score",     c.algoScore);
            v.put("signaled_at_ms", System.currentTimeMillis());
            v.put("acted_on",       actedOn ? 1 : 0);
            getWritableDatabase().insert("signals", null, v);
        } catch (Exception e) { Log.w(TAG, "insertSignal: " + e.getMessage()); }
    }

    // ─ RISK CHECKS ───────────────────────────────────────────────────────────

    public void insertRiskCheck(String addr, String chain, String result,
                                 String blockReason, double orderSize, double liq) {
        try {
            ContentValues v = new ContentValues();
            v.put("token_address", addr);
            v.put("chain",         chain);
            v.put("check_result",  result);
            v.put("block_reason",  blockReason);
            v.put("order_size_usd",orderSize);
            v.put("liq_at_check",  liq);
            v.put("checked_at_ms", System.currentTimeMillis());
            getWritableDatabase().insert("risk_checks", null, v);
        } catch (Exception e) { Log.w(TAG, "insertRiskCheck: " + e.getMessage()); }
    }

    // ─ WALLET EVENTS ─────────────────────────────────────────────────────────

    public void insertWalletEvent(String chain, String type, String txHash, double amtUsd) {
        try {
            ContentValues v = new ContentValues();
            v.put("chain",          chain);
            v.put("event_type",     type);
            v.put("tx_hash",        txHash);
            v.put("amount_usd",     amtUsd);
            v.put("occurred_at_ms", System.currentTimeMillis());
            getWritableDatabase().insert("wallet_events", null, v);
        } catch (Exception e) { Log.w(TAG, "insertWalletEvent: " + e.getMessage()); }
    }

    // ─ CONVERSION HELPERS ─────────────────────────────────────────────────────

    private ContentValues tradeToValues(TradeRecord r) {
        ContentValues v = new ContentValues();
        v.put("id",            r.id);
        v.put("token_name",    r.tokenName);
        v.put("token_symbol",  r.tokenSymbol);
        v.put("token_address", r.tokenAddress);
        v.put("pair_address",  r.pairAddress);
        v.put("chain",         r.chain);
        v.put("entry_price",   r.entryPrice);
        v.put("exit_price",    r.exitPrice);
        v.put("amount_usd",    r.amountUsd);
        v.put("pnl_usd",       r.pnlUsd);
        v.put("pnl_pct",       r.pnlPercent);
        v.put("open_ms",       r.openTimeMs);
        v.put("close_ms",      r.closeTimeMs);
        v.put("is_win",        r.isWin   ? 1 : 0);
        v.put("is_open",       r.isOpen  ? 1 : 0);
        v.put("exit_reason",   r.exitReason);
        v.put("strategy",      r.strategyName);
        v.put("confidence",    r.confidenceScore);
        v.put("is_live",       r.isLive  ? 1 : 0);
        v.put("buy_tx",        r.buyTxHash);
        v.put("sell_tx",       r.sellTxHash);
        v.put("liq_entry",     r.liquidityAtEntry);
        v.put("vol_entry",     r.volumeAtEntry);
        v.put("fdv_entry",     r.fdvAtEntry);
        v.put("scam_score",    r.scamScore);
        v.put("algo_entry",    r.entryAlgoScore);
        v.put("algo_exit",     r.exitAlgoScore);
        v.put("algo_signal",   r.algoSignal);
        v.put("peak_price",    r.peakPrice);
        v.put("chain_safety",  r.chainSafetyScore);
        v.put("sell_sim_ok",   r.sellSimOk ? 1 : 0);
        v.put("liq_low",       r.liquidityLow);
        v.put("trailing_sl",   r.trailingSl);
        return v;
    }

    private TradeRecord cursorToTrade(Cursor c) {
        TradeRecord r = new TradeRecord();
        r.id               = s(c, "id");
        r.tokenName        = s(c, "token_name");
        r.tokenSymbol      = s(c, "token_symbol");
        r.tokenAddress     = s(c, "token_address");
        r.pairAddress      = s(c, "pair_address");
        r.chain            = s(c, "chain");
        r.entryPrice       = d(c, "entry_price");
        r.exitPrice        = d(c, "exit_price");
        r.amountUsd        = d(c, "amount_usd");
        r.pnlUsd           = d(c, "pnl_usd");
        r.pnlPercent       = d(c, "pnl_pct");
        r.openTimeMs       = l(c, "open_ms");
        r.closeTimeMs      = l(c, "close_ms");
        r.isWin            = i(c, "is_win")    == 1;
        r.isOpen           = i(c, "is_open")   == 1;
        r.exitReason       = s(c, "exit_reason");
        r.strategyName     = s(c, "strategy");
        r.confidenceScore  = i(c, "confidence");
        r.isLive           = i(c, "is_live")   == 1;
        r.buyTxHash        = s(c, "buy_tx");
        r.sellTxHash       = s(c, "sell_tx");
        r.liquidityAtEntry = d(c, "liq_entry");
        r.volumeAtEntry    = d(c, "vol_entry");
        r.fdvAtEntry       = d(c, "fdv_entry");
        r.scamScore        = i(c, "scam_score");
        r.entryAlgoScore   = i(c, "algo_entry");
        r.exitAlgoScore    = i(c, "algo_exit");
        r.algoSignal       = s(c, "algo_signal");
        r.peakPrice        = d(c, "peak_price");
        r.chainSafetyScore = i(c, "chain_safety");
        r.sellSimOk        = i(c, "sell_sim_ok") == 1;
        r.liquidityLow     = d(c, "liq_low");
        r.trailingSl       = d(c, "trailing_sl");
        return r;
    }

    private ContentValues cv(String k, long v) {
        ContentValues cv = new ContentValues(); cv.put(k, v); return cv;
    }
    private String s(Cursor c, String col) {
        int i = c.getColumnIndex(col); return i >= 0 && !c.isNull(i) ? c.getString(i) : "";
    }
    private double d(Cursor c, String col) {
        int i = c.getColumnIndex(col); return i >= 0 && !c.isNull(i) ? c.getDouble(i) : 0;
    }
    private long l(Cursor c, String col) {
        int i = c.getColumnIndex(col); return i >= 0 && !c.isNull(i) ? c.getLong(i) : 0;
    }
    private int i(Cursor c, String col) {
        int i = c.getColumnIndex(col); return i >= 0 && !c.isNull(i) ? c.getInt(i) : 0;
    }
}
