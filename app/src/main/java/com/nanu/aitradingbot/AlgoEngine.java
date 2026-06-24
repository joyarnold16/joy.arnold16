package com.nanu.aitradingbot;

/**
 * Algorithmic entry/exit signals for scalping.
 * Derives RSI approximation, MACD-like momentum, volume signals
 * from DEX Screener data — no OHLC candles needed.
 */
public class AlgoEngine {

    public static class Signal {
        public int    score;   // 0–100 algo confidence
        public String type;    // RSI_BOUNCE, MACD_CROSS, VOL_SURGE, MULTI_CONFIRM, WEAK
        public String detail;  // human-readable breakdown

        Signal(int score, String type, String detail) {
            this.score  = Math.max(0, Math.min(100, score));
            this.type   = type;
            this.detail = detail;
        }

        public boolean isGoodEntry(int minScore) { return score >= minScore; }
    }

    // ─ ENTRY SIGNAL ──────────────────────────────────────────────────────

    public static Signal entrySignal(DexCandidate c) {
        int score = 0;
        String type = "SCALP";
        StringBuilder detail = new StringBuilder();

        // ── RSI approximation ──────────────────────────────────────────
        double rsi = approximateRsi(c.priceChange1h, c.priceChange24h);
        if (rsi >= 30 && rsi <= 62) {
            score += 25;
            detail.append("RSI").append((int) rsi).append("✓ ");
            type = "RSI_BOUNCE";
        } else if (rsi > 62 && rsi <= 75) {
            score += 10;
            detail.append("RSI").append((int) rsi).append("~ ");
        } else if (rsi > 75) {
            score -= 15;
            detail.append("RSI").append((int) rsi).append("OB ");  // overbought
        } else {
            score += 12;
            detail.append("RSI").append((int) rsi).append("OS ");  // oversold bounce
            type = "RSI_BOUNCE";
        }

        // ── MACD-like momentum ────────────────────────────────────────
        // 1h momentum stronger than expected from 24h baseline
        double macdSig = c.priceChange1h - (c.priceChange24h / 24.0);
        if (macdSig > 0.3) {
            score += 20;
            detail.append("MACD+").append(String.format("%.1f", macdSig)).append(" ");
            type = "RSI_BOUNCE".equals(type) ? "MULTI_CONFIRM" : "MACD_CROSS";
        } else if (macdSig > 0) {
            score += 8;
            detail.append("MACD+~ ");
        } else if (macdSig < -0.3) {
            score -= 18;
            detail.append("MACD- ");
        }

        // ── Volume confirmation ───────────────────────────────────────
        double volLiq = c.liquidityUsd > 0 ? c.volumeUsd24h / c.liquidityUsd : 0;
        if (volLiq >= 0.4 && volLiq <= 8) {
            score += 18;
            detail.append("VOL✓ ");
            if ("SCALP".equals(type)) type = "VOL_SURGE";
        } else if (volLiq > 8 && volLiq <= 20) {
            score += 5;
            detail.append("VOL~ ");
        } else if (volLiq > 20) {
            score -= 12;
            detail.append("VOL-HIGH ");
        }

        // ── Buy/sell pressure ─────────────────────────────────────────
        int totalTx = c.buys24h + c.sells24h;
        if (totalTx > 10) {
            double buyPct = (double) c.buys24h / totalTx * 100;
            if (buyPct >= 58) {
                score += 18;
                detail.append("BUY").append((int) buyPct).append("% ");
            } else if (buyPct >= 50) {
                score += 8;
                detail.append("BUY").append((int) buyPct).append("% ");
            } else if (buyPct < 42) {
                score -= 18;
                detail.append("SELL").append((int)(100 - buyPct)).append("% ");
            }
        }

        // ── Candlestick pattern bonus ─────────────────────────────────
        int bullPatterns = 0;
        int bearPatterns = 0;
        for (String p : c.patterns) {
            if (isBullish(p)) bullPatterns++;
            else if (isBearish(p)) bearPatterns++;
        }
        if (bullPatterns > 0) {
            score += Math.min(bullPatterns * 12, 20);
            detail.append("BULL×").append(bullPatterns).append(" ");
            if (bullPatterns >= 2) type = "MULTI_CONFIRM";
        }
        if (bearPatterns > 0) {
            score -= Math.min(bearPatterns * 12, 24);
            detail.append("BEAR×").append(bearPatterns).append(" ");
        }

        // ── Safety score contribution ─────────────────────────────────
        if (c.score >= 85) score += 10;
        else if (c.score >= 75) score += 5;
        else if (c.score < 70) score -= 10;

        if (score < 40) type = "WEAK";

        return new Signal(score, type, detail.toString().trim());
    }

    // ─ EXIT SIGNAL (called during monitor loop) ───────────────────────────

    /**
     * Returns 0-100: high = hold on, low = exit now.
     * Uses current token data + P&L percent.
     */
    public static int exitScore(DexCandidate live, double pctChange) {
        int score = 50;

        // Profit/loss bias
        if (pctChange >= 2.0)       score += 20;
        else if (pctChange >= 1.0)  score += 10;
        else if (pctChange < -0.5)  score -= 15;
        else if (pctChange < -1.0)  score -= 25;

        // Bearish pattern during hold → exit
        for (String p : live.patterns) {
            if (isBearish(p)) { score -= 20; break; }
        }

        // RSI overbought → take profit
        double rsi = approximateRsi(live.priceChange1h, live.priceChange24h);
        if (rsi > 78) score -= 20;

        // Sell pressure → exit
        int totalTx = live.buys24h + live.sells24h;
        if (totalTx > 10 && (double) live.sells24h / totalTx > 0.60) score -= 15;

        return Math.max(0, Math.min(100, score));
    }

    // ─ HELPERS ───────────────────────────────────────────────────────────

    /**
     * Approximates RSI (0–100) from 1h and 24h price change data.
     * Each 1% of 1h change moves RSI ~8 points from neutral 50.
     */
    public static double approximateRsi(double change1h, double change24h) {
        double base = 50 + (change1h * 8);
        if (change24h > 5)  base += 6;
        else if (change24h > 0) base += 2;
        else if (change24h < -5) base -= 6;
        else if (change24h < 0)  base -= 2;
        return Math.max(0, Math.min(100, base));
    }

    private static boolean isBullish(String p) {
        return p.contains("BULL") || p.contains("HAMMER") || p.contains("MORNING")
            || p.contains("ACCUMULATION") || p.contains("BREAKOUT_UP")
            || p.contains("MOMENTUM_ACCEL") || p.contains("PUMP_SETUP");
    }

    private static boolean isBearish(String p) {
        return p.contains("BEAR") || p.contains("DISTRIBUTION") || p.contains("SHOOTING")
            || p.contains("EVENING") || p.contains("DEAD_CAT") || p.contains("DUMP")
            || p.contains("BREAKOUT_DN") || p.contains("CLIMAX");
    }
}
