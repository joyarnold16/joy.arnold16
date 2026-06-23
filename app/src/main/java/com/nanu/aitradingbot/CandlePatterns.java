package com.nanu.aitradingbot;

import java.util.ArrayList;
import java.util.List;

public class CandlePatterns {

    public static List<String> detect(DexCandidate c) {
        List<String> p = new ArrayList<>();
        double h1  = c.priceChange1h;
        double h24 = c.priceChange24h;
        double vol = c.volumeUsd24h;
        double liq = c.liquidityUsd;
        double volLiqRatio = liq > 0 ? vol / liq : 0;
        int buys  = c.buys24h;
        int sells = c.sells24h;
        int total = buys + sells;
        double buyRatio = total > 0 ? (double) buys / total : 0.5;
        long ageMs = System.currentTimeMillis() - c.pairCreatedAtMs;
        double ageH = ageMs / 3_600_000.0;

        // ─ MOMENTUM ──────────────────────────────────────────────
        if (h1 > 5 && h24 > 10)                         p.add("MOMENTUM_ACCEL");
        if (h1 > 10 && buyRatio > 0.65)                 p.add("BREAKOUT_UP");
        if (h1 < -10 && buyRatio < 0.35)                p.add("BREAKOUT_DN");
        if (h1 > 3 && h24 < -20 && buyRatio > 0.6)     p.add("PUMP_SETUP");

        // ─ REVERSALS ───────────────────────────────────────────
        if (h1 > 2 && h24 < -10 && buyRatio > 0.55)    p.add("HAMMER");
        if (h1 < -5 && h24 > 20 && buyRatio < 0.45)    p.add("SHOOTING_STAR");
        if (h1 > 3 && h24 < -30 && buyRatio > 0.6)     p.add("MORNING_STAR");
        if (h1 < -3 && h24 > 30 && buyRatio < 0.4)     p.add("EVENING_STAR");
        if (h1 > 5 && buyRatio > 0.7)                   p.add("ENGULFING_BULL");
        if (h1 < -5 && buyRatio < 0.3)                  p.add("ENGULFING_BEAR");

        // ─ VOLUME ───────────────────────────────────────────────
        if (volLiqRatio > 3 && vol > 100_000)            p.add("VOL_SURGE");
        if (volLiqRatio < 0.2 && total < 50)             p.add("VOL_DRY");
        if (volLiqRatio > 10)                            p.add("WASH_VOL");
        if (volLiqRatio > 5 && Math.abs(h1) > 20)       p.add("CLIMAX");

        // ─ TREND ────────────────────────────────────────────────
        if (h24 > 5 && buyRatio > 0.6 && vol > 50_000)  p.add("ACCUMULATION");
        if (h24 < -5 && buyRatio < 0.4 && vol > 50_000) p.add("DISTRIBUTION");
        if (h1 > 5 && h24 < -40)                        p.add("DEAD_CAT");
        if (h1 > 2 && h24 > 0 && h24 < 15)              p.add("BULL_FLAG");
        if (h1 < -2 && h24 < 0 && h24 > -15)            p.add("BEAR_FLAG");

        return p;
    }

    public static int scoreAdj(List<String> p) {
        int adj = 0;
        for (String s : p) {
            switch (s) {
                case "MOMENTUM_ACCEL":  adj += 12; break;
                case "BREAKOUT_UP":     adj += 10; break;
                case "PUMP_SETUP":      adj += 9;  break;
                case "HAMMER":          adj += 8;  break;
                case "MORNING_STAR":    adj += 8;  break;
                case "ENGULFING_BULL":  adj += 7;  break;
                case "ACCUMULATION":    adj += 6;  break;
                case "BULL_FLAG":       adj += 5;  break;
                case "VOL_SURGE":       adj += 4;  break;
                case "DEAD_CAT":        adj += 3;  break;
                case "VOL_DRY":         adj -= 5;  break;
                case "DISTRIBUTION":    adj -= 6;  break;
                case "BEAR_FLAG":       adj -= 6;  break;
                case "SHOOTING_STAR":   adj -= 7;  break;
                case "ENGULFING_BEAR":  adj -= 8;  break;
                case "EVENING_STAR":    adj -= 8;  break;
                case "BREAKOUT_DN":     adj -= 9;  break;
                case "CLIMAX":          adj -= 5;  break;
                case "WASH_VOL":        adj -= 4;  break;
            }
        }
        return adj;
    }

    public static boolean bullish(List<String> p) {
        for (String s : p)
            if (s.equals("PUMP_SETUP") || s.equals("MOMENTUM_ACCEL")
                || s.equals("BREAKOUT_UP") || s.equals("HAMMER")
                || s.equals("MORNING_STAR") || s.equals("ENGULFING_BULL")
                || s.equals("ACCUMULATION") || s.equals("BULL_FLAG"))
                return true;
        return false;
    }

    public static boolean bearish(List<String> p) {
        for (String s : p)
            if (s.equals("SHOOTING_STAR") || s.equals("ENGULFING_BEAR")
                || s.equals("DISTRIBUTION") || s.equals("EVENING_STAR")
                || s.equals("BREAKOUT_DN") || s.equals("BEAR_FLAG")
                || s.equals("DEAD_CAT"))
                return true;
        return false;
    }

    public static String summary(List<String> p) {
        if (p.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, p.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append(p.get(i).replace('_', ' '));
        }
        if (p.size() > 3) sb.append(" +").append(p.size() - 3);
        return sb.toString();
    }
}
