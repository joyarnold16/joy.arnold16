package com.nanu.aitradingbot;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Tier 3: BSC on-chain safety checks.
 * Uses honeypot.is API (no key needed) and BscScan public API.
 * Called synchronously from DexEngine background thread before opening a position.
 */
public class BscChecker {
    private static final String TAG = "BscChecker";
    private static final String HONEYPOT_API = "https://api.honeypot.is/v2/IsHoneypot";
    private static final String BSCSCAN_API  = "https://api.bscscan.com/api";

    public static class Result {
        public boolean isHoneypot        = false;
        public boolean contractVerified  = false;
        public boolean ownerRenounced    = false;
        public double  buyTaxPct         = 0;
        public double  sellTaxPct        = 0;
        public int     safetyScore       = 0;   // 0–20 pts
        public String  note              = "";
    }

    public static Result check(String tokenAddress) {
        Result r = new Result();
        if (tokenAddress == null || tokenAddress.isEmpty()) return r;

        try {
            checkHoneypot(tokenAddress, r);
        } catch (Exception e) {
            Log.w(TAG, "honeypot.is check failed: " + e.getMessage());
        }
        try {
            checkBscScan(tokenAddress, r);
        } catch (Exception e) {
            Log.w(TAG, "BscScan check failed: " + e.getMessage());
        }

        // Scoring: not honeypot=10, verified=5, owner renounced=5
        if (!r.isHoneypot)       r.safetyScore += 10;
        if (r.contractVerified)  r.safetyScore += 5;
        if (r.ownerRenounced)    r.safetyScore += 5;
        // Deduct for high taxes
        if (r.sellTaxPct > 10)   r.safetyScore -= 5;
        if (r.sellTaxPct > 25)   r.safetyScore -= 10; // additional deduction

        return r;
    }

    private static void checkHoneypot(String addr, Result r) throws Exception {
        // chainID 56 = BNB Smart Chain
        String url = HONEYPOT_API + "?address=" + addr + "&chainID=56";
        JSONObject resp = httpGetJson(url, 6_000);
        if (resp == null) return;

        JSONObject honeypotResult = resp.optJSONObject("honeypotResult");
        if (honeypotResult != null) {
            r.isHoneypot = honeypotResult.optBoolean("isHoneypot", false);
        }

        JSONObject simulationResult = resp.optJSONObject("simulationResult");
        if (simulationResult != null) {
            r.buyTaxPct  = simulationResult.optDouble("buyTax",  0);
            r.sellTaxPct = simulationResult.optDouble("sellTax", 0);
        }

        r.note += String.format("HP:%s Tax:buy%.1f%%/sell%.1f%% ",
            r.isHoneypot ? "⛔" : "✓", r.buyTaxPct, r.sellTaxPct);
    }

    private static void checkBscScan(String addr, Result r) throws Exception {
        // Contract verification: module=contract, action=getsourcecode
        String url = BSCSCAN_API + "?module=contract&action=getsourcecode&address=" + addr;
        JSONObject resp = httpGetJson(url, 8_000);
        if (resp == null) return;

        JSONArray results = resp.optJSONArray("result");
        if (results == null || results.length() == 0) return;
        JSONObject first = results.optJSONObject(0);
        if (first == null) return;

        String sourceCode = first.optString("SourceCode", "");
        r.contractVerified = !sourceCode.isEmpty() && !sourceCode.equals("0x");

        // Rough check: if ABI is available and doesn't include "owner()" → renounced
        // This is a heuristic — not fully reliable without calling the contract
        String abi = first.optString("ABI", "");
        if (r.contractVerified && !abi.isEmpty() && !abi.equals("Contract source code not verified")) {
            // Source verified; check if owner is renounced via ContractName field
            // Real check would require eth_call to owner(), but that needs a web3 provider
            // For now mark as unconfirmed
            r.note += "Verified ";
        }
    }

    private static JSONObject httpGetJson(String url, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("User-Agent", "NanuBot/11.0");
        if (conn.getResponseCode() != 200) return null;
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return new JSONObject(sb.toString());
    }
}
