package com.nanu.aitradingbot;

import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Pre-entry sell simulation.
 * BSC: uses honeypot.is canSell flag (already in BscChecker.Result).
 * Solana: calls Jupiter v6 quote API to check if a reverse swap (token→SOL) routes.
 * A failed simulation is SOFT (adjusts score down) except BSC canSell=false which is HARD block.
 */
public class SellSimulator {
    private static final String TAG = "SellSimulator";
    private static final String JUPITER_QUOTE = "https://quote-api.jup.ag/v6/quote";
    // Wrapped SOL mint — the "output" for any Solana sell simulation
    private static final String WSOL_MINT = "So11111111111111111111111111111111111111112";
    // Nominal sell amount in raw token units (1 million micro-units = small but route-detectable)
    private static final long NOMINAL_AMOUNT = 1_000_000L;

    public static class Result {
        public boolean simOk     = true;   // false = hard block (BSC only) or soft warn
        public boolean isHard    = false;  // true = hard block, do not enter
        public double  impactPct = 0;      // estimated price impact %
        public String  note      = "";
    }

    /** BSC: derive from honeypot.is result already fetched by BscChecker. */
    public static Result checkBsc(BscChecker.Result bsc) {
        Result r = new Result();
        if (!bsc.canSell) {
            r.simOk  = false;
            r.isHard = true;
            r.note   = "BSC sell simulation failed (canSell=false)";
        } else {
            r.simOk     = true;
            r.impactPct = bsc.sellTaxPct; // tax ≈ effective impact on sell
            r.note      = String.format("BSC sellTax=%.1f%%", bsc.sellTaxPct);
        }
        return r;
    }

    /**
     * Solana: call Jupiter v6 quote with token→SOL.
     * Returns ok if a valid route exists; soft-fail if no route.
     * Impact > 15% is flagged as a soft warning.
     */
    public static Result checkSolana(String mintAddress, int slippageBps) {
        Result r = new Result();
        if (mintAddress == null || mintAddress.isEmpty()) return r;
        try {
            String url = JUPITER_QUOTE
                + "?inputMint=" + mintAddress
                + "&outputMint=" + WSOL_MINT
                + "&amount=" + NOMINAL_AMOUNT
                + "&slippageBps=" + slippageBps
                + "&onlyDirectRoutes=false";

            JSONObject resp = httpGetJson(url, 6_000);
            if (resp == null) {
                // Jupiter unreachable — soft-pass (RPC down)
                r.simOk = true;
                r.note  = "SOL sim unavailable (Jupiter timeout)";
                return r;
            }

            if (resp.has("error")) {
                r.simOk = false;
                r.note  = "SOL sell sim: no route (" + resp.optString("error") + ")";
                return r;
            }

            // Route exists
            r.simOk = true;
            double inAmt  = resp.optDouble("inAmount",  NOMINAL_AMOUNT);
            double outAmt = resp.optDouble("outAmount", 0);
            // priceImpactPct is returned as a string like "0.12"
            try {
                r.impactPct = Double.parseDouble(resp.optString("priceImpactPct", "0"));
            } catch (Exception ignored) {}
            r.note = String.format("SOL sim: route OK impact=%.2f%%", r.impactPct);
            Log.d(TAG, "Solana sell sim for " + mintAddress + ": " + r.note);
        } catch (Exception e) {
            // Network error = soft-pass
            r.simOk = true;
            r.note  = "SOL sim error (non-blocking): " + e.getMessage();
            Log.w(TAG, r.note);
        }
        return r;
    }

    private static JSONObject httpGetJson(String url, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "NanuBot/11.0");
        int code = conn.getResponseCode();
        if (code == 400) {
            // Jupiter returns 400 with JSON error body for bad routes
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return new JSONObject(sb.toString());
        }
        if (code != 200) return null;
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder(); String ln;
        while ((ln = br.readLine()) != null) sb.append(ln);
        br.close();
        return new JSONObject(sb.toString());
    }
}
