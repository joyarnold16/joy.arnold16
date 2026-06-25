package com.nanu.aitradingbot;

public class DexSafetyPolicy {

    /**
     * Master 0–100 safety score.
     * Base 30 + up to 70 from market quality signals.
     * >= 60: QUALIFIED   40-59: WATCHING   <40: implicitly BLOCKED
     */
    public static int score(DexCandidate c, DexAppStore store) {
        int s = 30; // base

        // ── Liquidity (0–15) ─────────────────────────────────────────
        double liq = c.liquidityUsd;
        if      (liq > 500_000) s += 15;
        else if (liq > 100_000) s += 10;
        else if (liq >  50_000) s += 6;
        else if (liq >  25_000) s += 3;
        else if (liq <  10_000) s -= 10;

        // ── Volume 24h (0–10) ────────────────────────────────────────
        double vol = c.volumeUsd24h;
        if      (vol > 1_000_000) s += 10;
        else if (vol >   500_000) s += 7;
        else if (vol >   100_000) s += 4;
        else if (vol <    10_000) s -= 8;

        // ── Buy/sell pressure (-5 to +10) ───────────────────────────
        int total = c.buys24h + c.sells24h;
        double br = total > 0 ? (double) c.buys24h / total : 0.5;
        if      (br > 0.70) s += 10;
        else if (br > 0.60) s += 6;
        else if (br > 0.50) s += 3;
        else if (br < 0.30) s -= 5;
        else if (br < 0.40) s -= 2;

        // ── Price action 1h (-5 to +12) ─────────────────────────────
        double h1 = c.priceChange1h;
        if      (h1 > 20) s += 12;
        else if (h1 > 10) s += 9;
        else if (h1 >  5) s += 7;
        else if (h1 >  2) s += 4;
        else if (h1 >  0) s += 2;
        else if (h1 < -5) s -= 5;
        else if (h1 <  0) s -= 2;

        // ── Pair age (0–8) ───────────────────────────────────────────
        double ageH = c.pairAgeHours;
        if      (ageH > 168) s += 8;  // >7 days
        else if (ageH >  72) s += 6;  // >3 days
        else if (ageH >  24) s += 4;  // >1 day
        else if (ageH >  12) s += 2;
        else if (ageH >   1) s += 1;

        // ── FDV / liquidity safety (–5 to +10) ──────────────────────
        // Huge FDV vs tiny liquidity = most of supply is unlocked/dumpable
        double fdvRatio = c.fdvLiquidityRatio;
        if      (fdvRatio == 0 || fdvRatio <= 5)  s += 10; // tiny FDV or unknown
        else if (fdvRatio <= 20)                   s += 7;
        else if (fdvRatio <= 50)                   s += 4;
        else if (fdvRatio <= 100)                  s += 1;
        else                                       s -= 5;  // >100x FDV/liq = danger

        // ── 5m momentum bonus (0–5) ──────────────────────────────────
        // Short-term acceleration confirms entry signal
        if      (c.priceChange5m > 2)  s += 5;
        else if (c.priceChange5m > 0.5) s += 2;
        else if (c.priceChange5m < -2)  s -= 3;

        // ── Candle pattern adj (–10 to +15) ─────────────────────────
        int patAdj = CandlePatterns.scoreAdj(c.patterns);
        s += Math.max(-10, Math.min(15, patAdj));

        // ── On-chain safety score (0–20) ─────────────────────────────
        // Populated by SolanaChecker / BscChecker before entry
        if (c.chainSafetyScore > 0) s += Math.min(20, c.chainSafetyScore);

        // ── Sell simulation (–10 to +10) ─────────────────────────────
        if (c.sellSimOk) s += 10;
        else             s -= 10;

        return Math.max(0, Math.min(100, s));
    }

    /**
     * Stage band from master score.
     * REJECT (≤40) / WATCH (41-60) / PAPER (61-75) / SMALL_LIVE (76-90) / LIVE (91-100)
     */
    public static String stageBand(int score) {
        if (score >= 91) return "LIVE";
        if (score >= 76) return "SMALL_LIVE";
        if (score >= 61) return "PAPER";
        if (score >= 41) return "WATCH";
        return "REJECT";
    }

    /**
     * Hard-block reasons. Returns null if token passes all hard filters.
     * Any non-null string = candidate is blocked (do not trade).
     */
    public static String check(DexCandidate c, DexAppStore store) {
        if (c.liquidityUsd < 8_000)
            return "liquidity below $8,000";
        if (c.volumeUsd24h < 10_000)
            return "24h volume below $10,000";
        if (c.liquidityUsd > 0 && c.volumeUsd24h / c.liquidityUsd > 50)
            return "vol/liq ratio " + (int)(c.volumeUsd24h / c.liquidityUsd) + "x (wash trading risk)";
        int total = c.buys24h + c.sells24h;
        if (total < 20)
            return "too little buy/sell activity";
        // Honeypot heuristic: many buys but almost no sells
        if (c.buys24h >= 30 && (double) c.sells24h / total < 0.03)
            return "possible honeypot (buys " + c.buys24h + " / sells " + c.sells24h + ")";
        // Extreme pump: +50% in 1h is likely manipulation or already mooned
        if (c.priceChange1h > 50)
            return "extreme 1h pump +" + String.format("%.0f", c.priceChange1h) + "% (likely manipulation)";
        // FDV/liquidity too extreme — most supply can dump on tiny pool
        if (c.fdvLiquidityRatio > 200)
            return "FDV/liq ratio " + (int) c.fdvLiquidityRatio + "x (extreme dump risk)";
        // Pair age gate
        double ageH = c.pairAgeHours;
        if (c.pairCreatedAtMs > 0 && ageH < store.minPairAgeHours)
            return "pair newer than required age (" + String.format("%.1f", ageH) + "h < " + store.minPairAgeHours + "h)";
        // Momentum gate
        if (c.priceChange1h < store.minMomentumPercent)
            return "momentum " + String.format("%.2f", c.priceChange1h) + "% below minimum";
        // Liquidity strategy minimum
        if (c.liquidityUsd < store.minLiquidityUsd)
            return "liquidity below strategy minimum ($" + (int) store.minLiquidityUsd + ")";
        // Extreme dump
        if (c.priceChange1h < -25)
            return "extreme 1h dump " + String.format("%.1f", c.priceChange1h) + "%";
        // Bearish pattern active
        if (CandlePatterns.bearish(c.patterns))
            return "bearish pattern: " + CandlePatterns.summary(c.patterns);
        return null; // passed all hard filters
    }

    /** Non-blocking cautions shown alongside QUALIFIED status. */
    public static String softWarning(DexCandidate c) {
        StringBuilder sb = new StringBuilder();
        if (c.liquidityUsd < 20_000)
            append(sb, "thin liquidity");
        if (c.liquidityUsd > 0 && c.volumeUsd24h / c.liquidityUsd > 20)
            append(sb, "vol/liq " + (int)(c.volumeUsd24h / c.liquidityUsd) + "x");
        if (Math.abs(c.priceChange1h) > 30)
            append(sb, "extreme 1h move");
        if (Math.abs(c.priceChange24h) > 200)
            append(sb, "extreme 24h move");
        if (c.fdvLiquidityRatio > 50 && c.fdvLiquidityRatio <= 200)
            append(sb, "FDV/liq " + (int) c.fdvLiquidityRatio + "x");
        if (c.pairAgeHours > 0 && c.pairAgeHours < 6)
            append(sb, "very new pair " + String.format("%.1f", c.pairAgeHours) + "h");
        return sb.toString();
    }

    private static void append(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append("; ");
        sb.append(s);
    }
}
