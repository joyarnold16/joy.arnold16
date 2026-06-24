package com.nanu.aitradingbot;

public class DexSafetyPolicy {

    public static int score(DexCandidate c, DexAppStore store) {
        int s = 50; // base

        // Momentum
        double h1 = c.priceChange1h;
        if      (h1 > 20) s += 15;
        else if (h1 > 10) s += 12;
        else if (h1 > 5)  s += 8;
        else if (h1 > 2)  s += 4;
        else if (h1 > 0)  s += 1;
        else if (h1 < -5) s -= 10;
        else if (h1 < 0)  s -= 3;

        // Liquidity
        double liq = c.liquidityUsd;
        if      (liq > 500_000) s += 15;
        else if (liq > 100_000) s += 10;
        else if (liq > 50_000)  s += 6;
        else if (liq > 25_000)  s += 2;
        else if (liq < 10_000)  s -= 15;

        // Volume
        double vol = c.volumeUsd24h;
        if      (vol > 1_000_000) s += 10;
        else if (vol > 500_000)   s += 7;
        else if (vol > 100_000)   s += 4;
        else if (vol < 10_000)    s -= 10;

        // Buy pressure
        int total = c.buys24h + c.sells24h;
        double br = total > 0 ? (double) c.buys24h / total : 0.5;
        if (br > 0.7) s += 10;
        else if (br > 0.6) s += 5;
        else if (br < 0.3) s -= 10;
        else if (br < 0.4) s -= 5;

        // Candle pattern bonus/penalty
        s += CandlePatterns.scoreAdj(c.patterns);

        return Math.max(0, s);
    }

    public static String check(DexCandidate c, DexAppStore store) {
        // Hard blocks
        if (c.liquidityUsd < 8_000)
            return "liquidity below $8,000";
        if (c.volumeUsd24h < 10_000)
            return "24h volume below $10,000";
        if (c.liquidityUsd > 0 && c.volumeUsd24h / c.liquidityUsd > 50)
            return "vol/liq ratio " + (int)(c.volumeUsd24h/c.liquidityUsd) + "x (possible wash trading)";
        int total = c.buys24h + c.sells24h;
        if (total < 20)
            return "too little buy/sell activity";
        // Honeypot heuristic: lots of buys but virtually no sells means
        // holders likely cannot sell (sell-tax 100% or blocked transfers).
        if (c.buys24h >= 30 && (double) c.sells24h / total < 0.03)
            return "possible honeypot (buys " + c.buys24h + " / sells " + c.sells24h + ")";
        long ageMs = System.currentTimeMillis() - c.pairCreatedAtMs;
        double ageH = ageMs / 3_600_000.0;
        if (c.pairCreatedAtMs > 0 && ageH < store.minPairAgeHours)
            return "pair is newer than required age";
        if (c.priceChange1h < store.minMomentumPercent)
            return "momentum below minimum (or falling)";
        if (c.liquidityUsd < store.minLiquidityUsd)
            return "liquidity below strategy minimum";
        if (c.priceChange1h < -25)
            return "extreme 1h move (dump)";
        if (CandlePatterns.bearish(c.patterns))
            return "bearish pattern detected";
        return null; // passed
    }

    public static String softWarning(DexCandidate c) {
        StringBuilder sb = new StringBuilder();
        if (c.liquidityUsd < 20_000)
            append(sb, "thin liquidity buffer");
        if (c.volumeUsd24h > 0 && c.liquidityUsd > 0
                && c.volumeUsd24h / c.liquidityUsd > 20)
            append(sb, "vol/liq ratio " + (int)(c.volumeUsd24h/c.liquidityUsd) + "x (possible wash trading)");
        if (Math.abs(c.priceChange1h) > 50)
            append(sb, "extreme 1h move");
        if (Math.abs(c.priceChange24h) > 200)
            append(sb, "extreme 24h move");
        return sb.toString();
    }

    private static void append(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append("; ");
        sb.append(s);
    }
}
