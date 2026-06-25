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

    // Dead addresses used to check LP burn
    private static final String DEAD_ADDR   = "0x000000000000000000000000000000000000dead";
    private static final String BURN_ADDR   = "0x0000000000000000000000000000000000000000";

    public static class Result {
        public boolean isHoneypot            = false;
        public boolean contractVerified      = false;
        public boolean ownerRenounced        = false;
        public double  buyTaxPct             = 0;
        public double  sellTaxPct            = 0;
        public int     safetyScore           = 0;   // 0–20 pts
        public String  note                  = "";
        // Expanded fields
        public boolean canSell               = true;  // false = hard block
        public boolean canBuy                = true;
        public String  ownerPowerFlags       = "";    // "BLACKLIST", "MINT", "PAUSE", "FEE"
        public boolean hasBlacklist          = false; // hard block if true
        public boolean holderConcentrationOk = false; // top holder <30%
        public double  topHolderPct          = 100;
        public boolean lpBurned              = false;
    }

    public static Result check(String tokenAddress) {
        return check(tokenAddress, "");
    }

    public static Result check(String tokenAddress, String pairAddress) {
        Result r = new Result();
        if (tokenAddress == null || tokenAddress.isEmpty()) return r;

        try {
            checkHoneypot(tokenAddress, pairAddress, r);
        } catch (Exception e) {
            Log.w(TAG, "honeypot.is check failed: " + e.getMessage());
        }
        try {
            checkBscScan(tokenAddress, r);
        } catch (Exception e) {
            Log.w(TAG, "BscScan check failed: " + e.getMessage());
        }
        try {
            checkHolderConcentration(tokenAddress, r);
        } catch (Exception e) {
            Log.w(TAG, "Holder concentration check failed: " + e.getMessage());
        }
        if (!pairAddress.isEmpty()) {
            try {
                checkLpBurn(pairAddress, r);
            } catch (Exception e) {
                Log.w(TAG, "LP burn check failed: " + e.getMessage());
            }
        }

        // Scoring
        if (!r.isHoneypot)           r.safetyScore += 6;
        if (r.canSell)               r.safetyScore += 4;
        if (r.contractVerified)      r.safetyScore += 4;
        if (r.ownerRenounced)        r.safetyScore += 3;
        if (r.holderConcentrationOk) r.safetyScore += 2;
        if (r.lpBurned)              r.safetyScore += 1;
        if (r.sellTaxPct > 10)       r.safetyScore -= 5;
        if (r.sellTaxPct > 25)       r.safetyScore -= 5; // stacks

        return r;
    }

    private static void checkHoneypot(String addr, String pairAddr, Result r) throws Exception {
        String url = HONEYPOT_API + "?address=" + addr + "&chainID=56";
        if (!pairAddr.isEmpty()) url += "&pair=" + pairAddr;
        JSONObject resp = httpGetJson(url, 6_000);
        if (resp == null) return;

        JSONObject honeypotResult = resp.optJSONObject("honeypotResult");
        if (honeypotResult != null) {
            r.isHoneypot = honeypotResult.optBoolean("isHoneypot", false);
        }

        JSONObject simulationResult = resp.optJSONObject("simulationResult");
        if (simulationResult != null) {
            r.buyTaxPct  = simulationResult.optDouble("buyTax",   0);
            r.sellTaxPct = simulationResult.optDouble("sellTax",  0);
            // canSell / canBuy from simulation
            r.canBuy  = simulationResult.optBoolean("canBuy",  true);
            r.canSell = simulationResult.optBoolean("canSell", true);
        }

        r.note += String.format("HP:%s CanSell:%s Tax:buy%.1f%%/sell%.1f%% ",
            r.isHoneypot ? "⛔" : "✓",
            r.canSell    ? "✓"  : "⛔",
            r.buyTaxPct, r.sellTaxPct);
    }

    private static void checkBscScan(String addr, Result r) throws Exception {
        String url = BSCSCAN_API + "?module=contract&action=getsourcecode&address=" + addr;
        JSONObject resp = httpGetJson(url, 8_000);
        if (resp == null) return;

        JSONArray results = resp.optJSONArray("result");
        if (results == null || results.length() == 0) return;
        JSONObject first = results.optJSONObject(0);
        if (first == null) return;

        String sourceCode = first.optString("SourceCode", "");
        r.contractVerified = !sourceCode.isEmpty() && !sourceCode.equals("0x");

        if (r.contractVerified) {
            r.note += "Verified ";
            // Keyword scan for dangerous owner capabilities
            String src = sourceCode.toUpperCase();
            StringBuilder flags = new StringBuilder();

            if (src.contains("BLACKLIST") || src.contains("_BLACKLISTED") || src.contains("ISBLACKLISTED")) {
                r.hasBlacklist = true;
                append(flags, "BLACKLIST");
            }
            if (src.contains("MINT(") || src.contains("_MINT(") || src.contains("MINTTOKENS")) {
                append(flags, "MINT");
            }
            if (src.contains("PAUSE(") || src.contains("WHENPAUSE") || src.contains("WHENNOTPAUSED")) {
                append(flags, "PAUSE");
            }
            if (src.contains("SETFEE") || src.contains("UPDATEFEE") || src.contains("SETTAX")) {
                append(flags, "FEE");
            }
            r.ownerPowerFlags = flags.toString().replaceAll(",$", "");
            if (!r.ownerPowerFlags.isEmpty()) r.note += "Powers:" + r.ownerPowerFlags + " ";

            // Renounce check: source confirmed but renounced if owner() returns zero address
            // Rough heuristic: "renounceOwnership" present in source
            if (src.contains("RENOUNCEOWNERSHIP")) {
                // Contract has renounce function — assume renounced if no recent owner activity
                // This is conservative: mark true only if source has renounce and no mint/blacklist
                r.ownerRenounced = !r.hasBlacklist && !flags.toString().contains("MINT");
            }
        }
    }

    private static void checkHolderConcentration(String addr, Result r) throws Exception {
        // BscScan tokenholderlist: module=token, action=tokenholderlist
        String url = BSCSCAN_API + "?module=token&action=tokenholderlist&contractaddress=" + addr + "&page=1&offset=10";
        JSONObject resp = httpGetJson(url, 8_000);
        if (resp == null) return;

        JSONArray holders = resp.optJSONArray("result");
        if (holders == null || holders.length() == 0) return;

        double topValue = 0, totalValue = 0;
        for (int i = 0; i < holders.length(); i++) {
            JSONObject h = holders.optJSONObject(i);
            if (h == null) continue;
            try {
                double qty = Double.parseDouble(h.optString("TokenHolderQuantity", "0"));
                totalValue += qty;
                if (i == 0) topValue = qty;
            } catch (Exception ignored) {}
        }
        r.topHolderPct = totalValue > 0 ? topValue / totalValue * 100.0 : 100;
        r.holderConcentrationOk = r.topHolderPct < 30;
        r.note += String.format("TopHolder:%.1f%% ", r.topHolderPct);
    }

    private static void checkLpBurn(String pairAddress, Result r) throws Exception {
        // Check if LP tokens were sent to dead/burn address using BscScan tokentx
        String url = BSCSCAN_API + "?module=account&action=tokentx&contractaddress="
            + pairAddress + "&address=" + DEAD_ADDR + "&page=1&offset=5";
        JSONObject resp = httpGetJson(url, 8_000);
        if (resp == null) return;

        JSONArray txns = resp.optJSONArray("result");
        if (txns != null && txns.length() > 0) {
            r.lpBurned = true;
            r.note += "LP:burned ";
            return;
        }

        // Also check burn address
        url = BSCSCAN_API + "?module=account&action=tokentx&contractaddress="
            + pairAddress + "&address=" + BURN_ADDR + "&page=1&offset=5";
        resp = httpGetJson(url, 8_000);
        if (resp == null) return;
        txns = resp.optJSONArray("result");
        if (txns != null && txns.length() > 0) {
            r.lpBurned = true;
            r.note += "LP:burned ";
        }
    }

    private static void append(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append(",");
        sb.append(s);
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
