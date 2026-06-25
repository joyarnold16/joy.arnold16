package com.nanu.aitradingbot;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DexMarketClient {
    private static final String TAG = "DexMarket";
    private static final String API = "https://api.dexscreener.com";

    public interface Callback {
        void onResult(List<DexCandidate> candidates);
        void onError(String msg);
    }

    public static void discoverBnb(DexAppStore store, Callback callback) {
        discoverChain("bsc", store, callback);
    }

    public static void discoverSol(DexAppStore store, Callback callback) {
        discoverChain("solana", store, callback);
    }

    private static void discoverChain(String chain, DexAppStore store, Callback callback) {
        new Thread(() -> {
            try {
                Set<String> addresses = new LinkedHashSet<>();
                collectChainAddresses(API + "/token-profiles/latest/v1", chain, addresses, store.maxScanTokens);
                if (addresses.size() < 5)
                    collectChainAddresses(API + "/token-boosts/top/v1", chain, addresses, store.maxScanTokens);
                if (addresses.size() < 5)
                    collectChainAddresses(API + "/token-boosts/active/v1", chain, addresses, store.maxScanTokens);

                if (addresses.isEmpty()) {
                    callback.onError("No " + chain + " tokens found");
                    return;
                }

                List<DexCandidate> all = new ArrayList<>();
                List<String> addrList = new ArrayList<>(addresses);
                for (int i = 0; i < addrList.size(); i += 30) {
                    List<String> batch = addrList.subList(i, Math.min(i + 30, addrList.size()));
                    StringBuilder sb = new StringBuilder();
                    for (String a : batch) { if (sb.length() > 0) sb.append(','); sb.append(a); }
                    try {
                        JSONObject resp = httpGet(API + "/latest/dex/tokens/" + sb);
                        if (resp == null) continue;
                        JSONArray pairs = resp.optJSONArray("pairs");
                        if (pairs == null) continue;
                        for (int j = 0; j < pairs.length(); j++) {
                            DexCandidate c = parsePair(pairs.getJSONObject(j));
                            if (c != null && chain.equals(c.chain)) all.add(c);
                        }
                    } catch (Exception e) { Log.w(TAG, "Batch error: " + e.getMessage()); }
                    Thread.sleep(300);
                }
                scoreAndSort(all, store);
                callback.onResult(all);
            } catch (Exception e) {
                Log.e(TAG, "Discovery error " + chain, e);
                callback.onError(e.getMessage());
            }
        }, "nanu-dex-" + chain).start();
    }

    private static void collectChainAddresses(String url, String chain, Set<String> out, int max) {
        try {
            JSONArray arr = httpGetArray(url);
            if (arr == null) return;
            for (int i = 0; i < arr.length() && out.size() < max; i++) {
                JSONObject item = arr.getJSONObject(i);
                if (!chain.equals(item.optString("chainId", ""))) continue;
                String addr = item.optString("tokenAddress", "");
                if (!addr.isEmpty()) out.add(addr);
            }
        } catch (Exception e) { Log.w(TAG, "collectChainAddresses error: " + e.getMessage()); }
    }

    private static void scoreAndSort(List<DexCandidate> all, DexAppStore store) {
        for (DexCandidate c : all) {
            c.patterns = CandlePatterns.detect(c);
            c.score = DexSafetyPolicy.score(c, store);
            String block = DexSafetyPolicy.check(c, store);
            String warn  = DexSafetyPolicy.softWarning(c);
            if (block != null) {
                c.status = "BLOCKED";
                c.blockReason = block + (warn.isEmpty() ? "" : "; " + warn);
            } else if (c.score >= 60) {
                c.status = "QUALIFIED";
                c.blockReason = warn.isEmpty() ? "Passed all filters" : "Passed filters; caution: " + warn;
            } else {
                c.status = "WATCHING";
                c.blockReason = warn.isEmpty() ? "Score " + c.score + " below 60" : warn;
            }
        }
        all.sort((a, b) -> Integer.compare(b.score, a.score));
    }

    public static void discover(DexAppStore store, Callback callback) {
        new Thread(() -> {
            try {
                Set<String> addresses = new LinkedHashSet<>();
                collectAddresses(API + "/token-profiles/latest/v1", addresses, store.maxScanTokens);
                if (addresses.size() < 5)
                    collectAddresses(API + "/token-boosts/top/v1", addresses, store.maxScanTokens);
                if (addresses.size() < 5)
                    collectAddresses(API + "/token-boosts/active/v1", addresses, store.maxScanTokens);

                if (addresses.isEmpty()) {
                    callback.onError("No token addresses found from DEX Screener");
                    return;
                }

                List<DexCandidate> all = new ArrayList<>();
                List<String> addrList = new ArrayList<>(addresses);

                for (int i = 0; i < addrList.size(); i += 30) {
                    List<String> batch = addrList.subList(i, Math.min(i + 30, addrList.size()));
                    StringBuilder sb = new StringBuilder();
                    for (String a : batch) { if (sb.length() > 0) sb.append(','); sb.append(a); }
                    String url = API + "/latest/dex/tokens/" + sb;
                    try {
                        JSONObject resp = httpGet(url);
                        if (resp == null) continue;
                        JSONArray pairs = resp.optJSONArray("pairs");
                        if (pairs == null) continue;
                        for (int j = 0; j < pairs.length(); j++) {
                            DexCandidate c = parsePair(pairs.getJSONObject(j));
                            if (c != null) all.add(c);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Batch error: " + e.getMessage());
                    }
                    Thread.sleep(300);
                }

                scoreAndSort(all, store);
                callback.onResult(all);

            } catch (Exception e) {
                Log.e(TAG, "Discovery error", e);
                callback.onError(e.getMessage());
            }
        }, "nanu-dex-discovery").start();
    }

    private static void collectAddresses(String url, Set<String> out, int max) {
        try {
            JSONArray arr = httpGetArray(url);
            if (arr == null) return;
            for (int i = 0; i < arr.length() && out.size() < max; i++) {
                JSONObject item = arr.getJSONObject(i);
                String chain = item.optString("chainId", "");
                if (!"bsc".equals(chain) && !"solana".equals(chain)) continue;
                String addr = item.optString("tokenAddress", "");
                if (!addr.isEmpty()) out.add(addr);
            }
        } catch (Exception e) {
            Log.w(TAG, "collectAddresses error: " + e.getMessage());
        }
    }

    // Package-private so DexEngine.fetchCurrentData() can reuse it
    static DexCandidate parsePair(JSONObject pair) {
        try {
            String pairChain = pair.optString("chainId", "");
            if (!"bsc".equals(pairChain) && !"solana".equals(pairChain)) return null;

            JSONObject baseToken = pair.optJSONObject("baseToken");
            if (baseToken == null) return null;

            DexCandidate c = new DexCandidate();
            c.chain        = pairChain;
            c.name         = baseToken.optString("name", "Unknown");
            c.symbol       = baseToken.optString("symbol", "???");
            c.tokenAddress = baseToken.optString("address", "");
            c.pairAddress  = pair.optString("pairAddress", "");
            c.dexName      = pair.optString("dexId", "");
            c.priceUsd     = safeDouble(pair.opt("priceUsd"));
            c.fdv          = pair.optDouble("fdv", 0);
            c.marketCap    = pair.optDouble("marketCap", 0);

            JSONObject quoteToken = pair.optJSONObject("quoteToken");
            if (quoteToken != null) c.quoteToken = quoteToken.optString("symbol", "");

            // Price changes: 5m, 1h, 6h, 24h
            JSONObject priceChange = pair.optJSONObject("priceChange");
            if (priceChange != null) {
                c.priceChange5m  = priceChange.optDouble("m5",  0);
                c.priceChange1h  = priceChange.optDouble("h1",  0);
                c.priceChange6h  = priceChange.optDouble("h6",  0);
                c.priceChange24h = priceChange.optDouble("h24", 0);
            }

            // Volume: 5m, 1h, 6h, 24h
            JSONObject volume = pair.optJSONObject("volume");
            if (volume != null) {
                c.volume5m     = volume.optDouble("m5",  0);
                c.volume1h     = volume.optDouble("h1",  0);
                c.volume6h     = volume.optDouble("h6",  0);
                c.volumeUsd24h = volume.optDouble("h24", 0);
            }

            // Liquidity
            JSONObject liquidity = pair.optJSONObject("liquidity");
            if (liquidity != null)
                c.liquidityUsd = liquidity.optDouble("usd", 0);

            // Transactions: 5m, 1h, 6h, 24h
            JSONObject txns = pair.optJSONObject("txns");
            if (txns != null) {
                JSONObject m5 = txns.optJSONObject("m5");
                if (m5 != null) { c.buys5m = m5.optInt("buys", 0); c.sells5m = m5.optInt("sells", 0); }
                JSONObject h1 = txns.optJSONObject("h1");
                if (h1 != null) { c.buys1h = h1.optInt("buys", 0); c.sells1h = h1.optInt("sells", 0); }
                JSONObject h6 = txns.optJSONObject("h6");
                if (h6 != null) { c.buys6h = h6.optInt("buys", 0); c.sells6h = h6.optInt("sells", 0); }
                JSONObject h24 = txns.optJSONObject("h24");
                if (h24 != null) { c.buys24h = h24.optInt("buys", 0); c.sells24h = h24.optInt("sells", 0); }
            }

            c.pairCreatedAtMs = pair.optLong("pairCreatedAt", 0);
            if (c.pairCreatedAtMs > 0)
                c.pairAgeHours = (System.currentTimeMillis() - c.pairCreatedAtMs) / 3_600_000.0;

            if (c.fdv > 0 && c.liquidityUsd > 0)
                c.fdvLiquidityRatio = c.fdv / c.liquidityUsd;

            if (c.tokenAddress.isEmpty()) return null;
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("User-Agent", "NanuBot/11.0");
        if (conn.getResponseCode() != 200) return null;
        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return new JSONObject(sb.toString());
    }

    private static JSONArray httpGetArray(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("User-Agent", "NanuBot/11.0");
        if (conn.getResponseCode() != 200) return null;
        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        String body = sb.toString().trim();
        if (body.startsWith("[")) return new JSONArray(body);
        JSONObject obj = new JSONObject(body);
        if (obj.has("pairs"))  return obj.getJSONArray("pairs");
        if (obj.has("tokens")) return obj.getJSONArray("tokens");
        return null;
    }

    private static double safeDouble(Object v) {
        if (v == null) return 0;
        try { return Double.parseDouble(v.toString()); }
        catch (Exception e) { return 0; }
    }
}
