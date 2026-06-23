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
        String mode = r.isLive ? "🟢 LIVE" : "📜 PAPER";
        String chain = "bsc".equals(r.chain) ? "BNB" : "SOL";
        send(store, String.format(
            "💀 <b>Nanu AI — Trade Opened</b>\n"
            + "Token: <b>%s (%s)</b>\n"
            + "Chain: %s | Mode: %s\n"
            + "Entry: $%.8f\n"
            + "Size: $%.2f | Score: %d\n"
            + "Strategy: %s | TP: %.1f%% SL: %.1f%%",
            r.tokenName, r.tokenSymbol, chain, mode,
            r.entryPrice, r.amountUsd, r.confidenceScore,
            r.strategyName, store.takeProfitPercent, store.stopLossPercent));
    }

    public static void notifyClose(DexAppStore store, TradeRecord r) {
        String result = r.isWin ? "✅ WIN" : "❌ LOSS";
        String pnlStr = String.format("%+.2f USD (%+.2f%%)", r.pnlUsd, r.pnlPercent);
        String mode = r.isLive ? "🟢 LIVE" : "📜 PAPER";
        send(store, String.format(
            "%s <b>Nanu AI — Trade Closed</b>\n"
            + "Token: <b>%s</b> | Mode: %s\n"
            + "Exit: %s | Reason: %s\n"
            + "P/L: <b>%s</b>\n"
            + "Total P/L: %+.2f USD | WR: %.0f%%",
            result, r.tokenName, mode,
            r.exitReason, r.exitReason, pnlStr,
            store.totalPnlUsd, store.mlWinRate));
    }

    public static void notifyQualified(DexAppStore store, DexCandidate c) {
        String chain = "bsc".equals(c.chain) ? "BNB" : "SOL";
        send(store, String.format(
            "🎯 <b>Nanu AI — QUALIFIED Token Found</b>\n"
            + "Token: <b>%s (%s)</b> | Chain: %s\n"
            + "Score: <b>%d</b> | 1h: %+.2f%%\n"
            + "Liq: $%.0f | Vol: $%.0f\n"
            + "Patterns: %s",
            c.name, c.symbol, chain, c.score,
            c.priceChange1h, c.liquidityUsd, c.volumeUsd24h,
            CandlePatterns.summary(c.patterns)));
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
