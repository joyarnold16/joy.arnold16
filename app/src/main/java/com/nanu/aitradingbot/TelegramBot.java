package com.nanu.aitradingbot;

import android.util.Log;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class TelegramBot {
    private static final String TAG = "TelegramBot";
    private static final String BASE = "https://api.telegram.org/bot";

    public static void send(DexAppStore store, String message) {
        if (!store.hasTelegram()) return;
        new Thread(() -> {
            try {
                String urlStr = BASE + store.telegramToken + "/sendMessage";
                String body = "{\"chat_id\":\"" + store.telegramChatId
                    + "\",\"text\":\"" + escapeJson(message)
                    + "\",\"parse_mode\":\"HTML\"}";
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                if (code != 200) Log.w(TAG, "Telegram error: " + code);
            } catch (Exception e) {
                Log.w(TAG, "Telegram send failed: " + e.getMessage());
            }
        }, "nanu-telegram").start();
    }

    public static void notifyOpen(DexAppStore store, TradeRecord r) {
        String mode  = r.isLive ? "🟢 LIVE" : "📜 PAPER";
        String chain = "bsc".equals(r.chain) ? "BNB" : "SOL";

        // Compute effective risk params for this position
        DexEngine.RiskParams rp = DexEngine.effectiveRisk(r.amountUsd, store);
        double slPrice = r.entryPrice * (1 - rp.sl / 100.0);
        double tpPrice = r.entryPrice * (1 + rp.tp / 100.0);

        String signal = r.algoSignal != null && !r.algoSignal.isEmpty() ? r.algoSignal : "SCALP";
        String liqStr = r.liquidityAtEntry > 0
            ? "$" + fmtK(r.liquidityAtEntry) : "unknown";

        send(store, String.format(
            "💀 <b>Nanu AI — Trade Opened</b>\n"
            + "Token: <b>%s (%s)</b>\n"
            + "Chain: %s | Mode: %s | Risk: %s\n"
            + "Entry: $%.8f\n"
            + "TP: $%.8f (+%.1f%%) | SL: $%.8f (-%.1f%%)\n"
            + "Size: $%.2f | Score: %d | Signal: %s\n"
            + "Strategy: %s | Liq: %s",
            r.tokenName, r.tokenSymbol, chain, mode, rp.mode,
            r.entryPrice,
            tpPrice, rp.tp, slPrice, rp.sl,
            r.amountUsd, r.confidenceScore, signal,
            r.strategyName, liqStr));
    }

    public static void notifyClose(DexAppStore store, TradeRecord r) {
        String result  = r.isWin ? "✅ WIN" : "❌ LOSS";
        String pnlStr  = String.format("%+.4f USD (%+.2f%%)", r.pnlUsd, r.pnlPercent);
        String mode    = r.isLive ? "🟢 LIVE" : "📜 PAPER";
        String chain   = "bsc".equals(r.chain) ? "BNB" : "SOL";
        String reason  = r.exitReason != null ? r.exitReason.replace('_', ' ').toUpperCase() : "";
        long   holdMs  = r.holdingTimeMs();
        String holdStr = holdMs > 0
            ? String.format("%dm %ds", holdMs / 60_000, (holdMs % 60_000) / 1_000) : "n/a";

        send(store, String.format(
            "%s <b>Nanu AI — Trade Closed</b>\n"
            + "Token: <b>%s</b> | %s | Mode: %s\n"
            + "Entry: $%.8f → Exit: $%.8f\n"
            + "Held: %s | Reason: %s\n"
            + "P/L: <b>%s</b>\n"
            + "Total P/L: %+.2f USD | WR: %.0f%%",
            result, r.tokenName, chain, mode,
            r.entryPrice, r.exitPrice,
            holdStr, reason,
            pnlStr,
            store.totalPnlUsd, store.mlWinRate));
    }

    public static void notifyQualified(DexAppStore store, DexCandidate c) {
        String chain = "bsc".equals(c.chain) ? "BNB" : "SOL";
        String fdvStr = c.fdv > 0
            ? " | FDV $" + fmtK(c.fdv) : "";
        String ageStr = c.pairAgeHours > 0
            ? String.format(" | Age %.1fh", c.pairAgeHours) : "";
        String onChain = c.onChainNote != null && !c.onChainNote.isEmpty()
            ? "\nOnChain: " + c.onChainNote : "";

        send(store, String.format(
            "🎯 <b>Nanu AI — QUALIFIED Token Found</b>\n"
            + "Token: <b>%s (%s)</b> | Chain: %s\n"
            + "Score: <b>%d</b> | 5m: %+.1f%% | 1h: %+.2f%%\n"
            + "Liq: $%s | Vol: $%s%s%s\n"
            + "Patterns: %s%s",
            c.name, c.symbol, chain, c.score,
            c.priceChange5m, c.priceChange1h,
            fmtK(c.liquidityUsd), fmtK(c.volumeUsd24h), fdvStr, ageStr,
            CandlePatterns.summary(c.patterns), onChain));
    }

    // ─ HELPERS ────────────────────────────────────────────────────

    private static String fmtK(double v) {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
        if (v >= 1_000)     return String.format("%.1fK", v / 1_000);
        return String.format("%.0f", v);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
