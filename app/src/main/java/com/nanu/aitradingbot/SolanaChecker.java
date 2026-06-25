package com.nanu.aitradingbot;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Tier 3: Solana on-chain safety checks via public RPC.
 * Called synchronously from DexEngine background thread before opening a position.
 * Timeouts are short (5s per call) so a failed RPC doesn't stall the engine.
 */
public class SolanaChecker {
    private static final String TAG = "SolanaChecker";
    private static final String[] RPC_URLS = {
        "https://api.mainnet-beta.solana.com",
        "https://solana-api.projectserum.com"
    };

    public static class Result {
        public boolean mintAuthorityRevoked   = false; // safe when true
        public boolean freezeAuthorityRevoked = false; // safe when true
        public boolean holderConcentrationOk  = false; // top holder <30%
        public double  topHolderPct           = 100;   // % held by largest account
        public int     safetyScore            = 0;     // 0–20 pts added to master score
        public String  note                   = "";
        // Hard-block fields (only set when RPC data was actually received)
        public boolean rpcSucceeded    = false;
        public boolean isHardBlocked   = false;
        public String  hardBlockReason = "";
    }

    public static Result check(String mintAddress) {
        Result r = new Result();
        if (mintAddress == null || mintAddress.isEmpty()) return r;
        try {
            r.rpcSucceeded = parseMintAuthority(mintAddress, r);
        } catch (Exception e) {
            Log.w(TAG, "mintAuthority check failed: " + e.getMessage());
        }
        try {
            parseHolderConcentration(mintAddress, r);
        } catch (Exception e) {
            Log.w(TAG, "holderConcentration check failed: " + e.getMessage());
        }
        // Score: revoked mint authority = 8pts, revoked freeze = 7pts, holder OK = 5pts
        if (r.mintAuthorityRevoked)   r.safetyScore += 8;
        if (r.freezeAuthorityRevoked) r.safetyScore += 7;
        if (r.holderConcentrationOk)  r.safetyScore += 5;
        // Hard block only when RPC confirmed data — fail-open if RPC is down
        if (r.rpcSucceeded) {
            if (!r.mintAuthorityRevoked && !r.freezeAuthorityRevoked) {
                r.isHardBlocked   = true;
                r.hardBlockReason = "Mint + freeze authority both active";
            } else if (!r.mintAuthorityRevoked) {
                r.isHardBlocked   = true;
                r.hardBlockReason = "Mint authority NOT revoked";
            } else if (!r.freezeAuthorityRevoked) {
                r.isHardBlocked   = true;
                r.hardBlockReason = "Freeze authority NOT revoked";
            }
        }
        return r;
    }

    private static boolean parseMintAuthority(String mint, Result r) throws Exception {
        JSONObject body = new JSONObject()
            .put("jsonrpc", "2.0").put("id", 1)
            .put("method", "getAccountInfo")
            .put("params", new JSONArray()
                .put(mint)
                .put(new JSONObject().put("encoding", "jsonParsed")));

        JSONObject resp = rpcCall(body.toString());
        if (resp == null) return false;
        JSONObject result = resp.optJSONObject("result");
        if (result == null) return false;
        JSONObject value = result.optJSONObject("value");
        if (value == null) return false;
        JSONObject data = value.optJSONObject("data");
        if (data == null) return false;
        JSONObject parsed = data.optJSONObject("parsed");
        if (parsed == null) return false;
        JSONObject info = parsed.optJSONObject("info");
        if (info == null) return false;

        String mintAuth   = info.optString("mintAuthority",   "null");
        String freezeAuth = info.optString("freezeAuthority", "null");
        r.mintAuthorityRevoked   = "null".equals(mintAuth)   || mintAuth.trim().isEmpty();
        r.freezeAuthorityRevoked = "null".equals(freezeAuth) || freezeAuth.trim().isEmpty();
        r.note += "Mint:" + (r.mintAuthorityRevoked ? "✓" : "⚠") + " Freeze:" + (r.freezeAuthorityRevoked ? "✓" : "⚠") + " ";
        return true;
    }

    private static void parseHolderConcentration(String mint, Result r) throws Exception {
        JSONObject body = new JSONObject()
            .put("jsonrpc", "2.0").put("id", 2)
            .put("method", "getTokenLargestAccounts")
            .put("params", new JSONArray().put(mint));

        JSONObject resp = rpcCall(body.toString());
        if (resp == null) return;
        JSONObject result = resp.optJSONObject("result");
        if (result == null) return;
        JSONObject outer = result.optJSONObject("value");
        // Some RPC implementations return array directly under "value"
        JSONArray accounts = (outer != null) ? outer.optJSONArray("value") : null;
        if (accounts == null && result.has("value")) {
            try { accounts = result.getJSONArray("value"); } catch (Exception ignored) {}
        }
        if (accounts == null || accounts.length() == 0) return;

        double topHolder = 0, totalSupply = 0;
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject acct = accounts.getJSONObject(i);
            double amount = acct.optDouble("uiAmount", 0);
            totalSupply += amount;
            if (i == 0) topHolder = amount;
        }
        r.topHolderPct = totalSupply > 0 ? topHolder / totalSupply * 100 : 100;
        r.holderConcentrationOk = r.topHolderPct < 30;
        r.note += String.format("Top:%.1f%% ", r.topHolderPct);
    }

    private static JSONObject rpcCall(String bodyStr) {
        for (String endpoint : RPC_URLS) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5_000);
                conn.setReadTimeout(8_000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bodyStr.getBytes(StandardCharsets.UTF_8));
                }
                if (conn.getResponseCode() != 200) continue;
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                return new JSONObject(sb.toString());
            } catch (Exception e) {
                Log.w(TAG, "RPC " + endpoint + " failed: " + e.getMessage());
            }
        }
        return null;
    }
}
