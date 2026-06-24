package com.nanu.aitradingbot;

import android.util.Log;
import java.util.List;

public class BotEvolution {
    private static final String TAG = "BotEvolution";
    public  static final int MIN_TRADES = 1;

    private static class S {
        final String name;
        final double minMom, sl, tp, minLiq;
        final int minAge, maxHold, minAlgoScore;
        final double trailPct;
        S(String n, double mm, double sl, double tp, double liq, int age, int hold,
          int algoScore, double trail) {
            name=n; minMom=mm; this.sl=sl; this.tp=tp; minLiq=liq;
            minAge=age; maxHold=hold; minAlgoScore=algoScore; trailPct=trail;
        }
    }

    private static final S[] POOL = {
        new S("SCALPER",      2.0, 1.5,  3.0,  25_000, 0,  10, 50, 0.8),
        new S("BALANCED",     1.0, 5.0, 10.0,  50_000, 2,  60, 55, 1.0),
        new S("MOMENTUM",     3.5, 3.5,  7.0,  50_000, 1,  30, 60, 1.2),
        new S("CONSERVATIVE", 0.5, 9.0, 20.0, 200_000, 8, 120, 45, 1.5),
        new S("VOLUME_SPIKE", 2.0, 5.0, 12.0,  75_000, 1,  45, 65, 1.0),
        new S("ACCUMULATION", 0.3, 7.0, 22.0, 100_000,12, 180, 40, 2.0),
        new S("SNIPER",       5.0, 2.0,  4.0,  25_000, 0,   8, 75, 0.5),
        new S("SAFE_LARGE",   0.5, 8.0, 15.0, 500_000, 8,  90, 50, 1.5),
        new S("ANTI_DUMP",    1.5, 6.5, 11.0,  75_000, 4,  60, 60, 1.2),
    };

    private static String lastChangeSummary = "";

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

        String prev = store.mlStrategy;
        double prevSl = store.stopLossPercent;
        double prevTp = store.takeProfitPercent;
        int prevAlgo  = store.minAlgoScore;

        store.mlStrategy         = best.name;
        store.stopLossPercent    = best.sl;
        store.takeProfitPercent  = best.tp;
        store.minMomentumPercent = best.minMom;
        store.minLiquidityUsd    = best.minLiq;
        store.minPairAgeHours    = best.minAge;
        store.maxHoldMinutes     = best.maxHold;
        store.minAlgoScore       = best.minAlgoScore;
        store.trailingStopPct    = best.trailPct;
        store.mlGeneration++;
        store.save();

        StringBuilder sb = new StringBuilder();
        if (!prev.equals(best.name))
            sb.append("Strategy ").append(prev).append("→").append(best.name).append("  ");
        if (Math.abs(prevSl - best.sl) > 0.01)
            sb.append(String.format("SL %.1f→%.1f%%  ", prevSl, best.sl));
        if (Math.abs(prevTp - best.tp) > 0.01)
            sb.append(String.format("TP %.1f→%.1f%%  ", prevTp, best.tp));
        if (prevAlgo != best.minAlgoScore)
            sb.append("AlgoMin ").append(prevAlgo).append("→").append(best.minAlgoScore).append("  ");
        lastChangeSummary = sb.toString().trim();
        if (lastChangeSummary.isEmpty()) lastChangeSummary = "No parameter change (strategy already optimal)";

        Log.i(TAG, "Evolved → " + best.name + " gen" + store.mlGeneration + " | " + lastChangeSummary);
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

    public static void reset(DexAppStore store) {
        store.mlStrategy         = "BALANCED";
        store.stopLossPercent    = 1.5;
        store.takeProfitPercent  = 3.0;
        store.minMomentumPercent = 1.0;
        store.minLiquidityUsd    = 25_000;
        store.minPairAgeHours    = 0;
        store.maxHoldMinutes     = 15;
        store.minAlgoScore       = 55;
        store.trailingStopPct    = 1.0;
        store.mlGeneration       = 0;
        lastChangeSummary        = "Reset to defaults";
        store.save();
    }

    public static String lastChange() {
        return lastChangeSummary.isEmpty() ? "No evolution yet" : lastChangeSummary;
    }

    public static String summary(DexAppStore store) {
        return String.format("ML Gen %d: %s  WR %.0f%%  SL %.1f%%  TP %.1f%%  AlgoMin %d",
            store.mlGeneration, store.mlStrategy, store.mlWinRate,
            store.stopLossPercent, store.takeProfitPercent, store.minAlgoScore);
    }
}
