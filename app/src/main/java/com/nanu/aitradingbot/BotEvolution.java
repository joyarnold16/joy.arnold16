package com.nanu.aitradingbot;

import android.util.Log;
import java.util.List;

public class BotEvolution {
    private static final String TAG = "BotEvolution";
    public  static final int MIN_TRADES = 1;

    private static class S {
        final String name;
        final double minMom, sl, tp, minLiq;
        final int minAge, maxHold;
        S(String n, double mm, double sl, double tp, double liq, int age, int hold) {
            name=n; minMom=mm; this.sl=sl; this.tp=tp; minLiq=liq; minAge=age; maxHold=hold;
        }
    }

    private static final S[] POOL = {
        new S("SCALPER",     2.0, 1.5,  3.0,  25_000, 0,  10),
        new S("BALANCED",    1.0, 5.0, 10.0,  50_000, 2,  60),
        new S("MOMENTUM",    3.5, 3.5,  7.0,  50_000, 1,  30),
        new S("CONSERVATIVE",0.5, 9.0, 20.0, 200_000, 8, 120),
        new S("VOLUME_SPIKE",2.0, 5.0, 12.0,  75_000, 1,  45),
        new S("ACCUMULATION",0.3, 7.0, 22.0, 100_000,12, 180),
        new S("SNIPER",      5.0, 2.0,  4.0,  25_000, 0,   8),
        new S("SAFE_LARGE",  0.5, 8.0, 15.0, 500_000, 8,  90),
        new S("ANTI_DUMP",   1.5, 6.5, 11.0,  75_000, 4,  60),
    };

    public static void evolve(DexAppStore store) {
        List<TradeRecord> history = store.loadHistory();
        int closedCount = 0;
        for (TradeRecord r : history) if (!r.isOpen) closedCount++;
        if (closedCount < MIN_TRADES) return;

        S best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (S s : POOL) {
            double sc = simulate(s, history);
            Log.d(TAG, s.name + " sim=" + sc);
            if (sc > bestScore) { bestScore = sc; best = s; }
        }
        if (best == null) return;

        store.mlStrategy         = best.name;
        store.stopLossPercent    = best.sl;
        store.takeProfitPercent  = best.tp;
        store.minMomentumPercent = best.minMom;
        store.minLiquidityUsd    = best.minLiq;
        store.minPairAgeHours    = best.minAge;
        store.maxHoldMinutes     = best.maxHold;
        store.mlGeneration++;
        store.save();
        Log.i(TAG, "Evolved → " + best.name + " gen" + store.mlGeneration);
    }

    private static double simulate(S s, List<TradeRecord> history) {
        int wins = 0, total = 0;
        double pnl = 0;
        for (TradeRecord r : history) {
            if (r.isOpen || r.entryPrice <= 0) continue;
            total++;
            double pct = r.pnlPercent;
            double sim = pct >= s.tp ? s.tp : (pct <= -s.sl ? -s.sl : pct);
            double p = r.amountUsd * sim / 100.0;
            pnl += p;
            if (p > 0) wins++;
        }
        if (total == 0) return 0;
        return ((double) wins / total) * 60 + (pnl / total) * 40;
    }

    public static String summary(DexAppStore store) {
        return String.format("✨ ML Gen %d: %s %.0f%% WR (%d trades) SL=%.1f%% TP=%.1f%%",
            store.mlGeneration, store.mlStrategy, store.mlWinRate,
            store.totalTrades, store.stopLossPercent, store.takeProfitPercent);
    }
}
