package com.nanu.aitradingbot;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Live on-chain swap execution.
 * SAFETY: Only executes when store.liveMode == true.
 * BNB trades via PancakeSwap V2 on BSC.
 * SOL trades via Jupiter Aggregator on Solana.
 */
public class SwapEngine {
    private static final String TAG = "SwapEngine";

    private static final String BSC_RPC        = "https://bsc-dataseed.binance.org/";
    private static final String PANCAKE_ROUTER  = "0x10ED43C718714eb63d5aA57B78B54704E256024E";
    private static final String WBNB           = "0xbb4CdB9CBd36B01bD1cBaEBF2De08d9173bc095c";
    private static final long   BSC_CHAIN_ID   = 56L;

    private static final String JUPITER_QUOTE  = "https://quote-api.jup.ag/v6/quote";
    private static final String JUPITER_SWAP   = "https://quote-api.jup.ag/v6/swap";
    private static final String SOL_RPC        = "https://api.mainnet-beta.solana.com";
    private static final String WSOL           = "So11111111111111111111111111111111111111112";
    private static final String BINANCE_PRICE  = "https://api.binance.com/api/v3/ticker/price";

    // BIP44 paths
    private static final int[] BNB_PATH = {
        44 | Bip32ECKeyPair.HARDENED_BIT,
        60 | Bip32ECKeyPair.HARDENED_BIT,
         0 | Bip32ECKeyPair.HARDENED_BIT, 0, 0};
    private static final int[] SOL_PATH = {44, 501, 0, 0};

    public interface SwapCallback {
        void onSuccess(String txHash, double executedPrice);
        void onFail(String reason);
    }

    // ─ PUBLIC ─────────────────────────────────────────────────

    public static void buy(DexAppStore store, DexCandidate c, SwapCallback cb) {
        if (!store.liveMode) { cb.onFail("PAPER_MODE"); return; }
        if (store.tradeAmountUsd <= 0) { cb.onFail("Set trade amount in Control"); return; }
        String mnemonic = store.getMnemonic();
        if (mnemonic == null) { cb.onFail("No wallet"); return; }
        new Thread(() -> {
            try {
                if ("bsc".equals(c.chain) && store.liveChainBnb)
                    buyBnb(mnemonic, store, c, cb);
                else if ("solana".equals(c.chain) && store.liveChainSol)
                    buySol(mnemonic, store, c, cb);
                else
                    cb.onFail("Chain not enabled for live trading");
            } catch (Exception e) { cb.onFail("Buy error: " + e.getMessage()); }
        }, "nanu-buy").start();
    }

    public static void sell(DexAppStore store, DexCandidate c,
                            String tokenMint, double tokenAmount, SwapCallback cb) {
        if (!store.liveMode) { cb.onFail("PAPER_MODE"); return; }
        String mnemonic = store.getMnemonic();
        if (mnemonic == null) { cb.onFail("No wallet"); return; }
        new Thread(() -> {
            try {
                if ("bsc".equals(c.chain) && store.liveChainBnb)
                    sellBnb(mnemonic, store, c, tokenMint, tokenAmount, cb);
                else if ("solana".equals(c.chain) && store.liveChainSol)
                    sellSol(mnemonic, store, c, tokenMint, tokenAmount, cb);
                else
                    cb.onFail("Chain not enabled");
            } catch (Exception e) { cb.onFail("Sell error: " + e.getMessage()); }
        }, "nanu-sell").start();
    }

    // ─ BNB / BSC ─────────────────────────────────────────────

    private static void buyBnb(String mnemonic, DexAppStore store,
                                DexCandidate c, SwapCallback cb) throws Exception {
        Credentials creds = bnbCreds(mnemonic);
        double bnbAmt = store.tradeAmountUsd / getPrice("BNBUSDT");
        BigInteger wei = toWei(bnbAmt);
        int nonce = getBscNonce(store.bnbAddress);
        long deadline = System.currentTimeMillis() / 1000 + 300;
        byte[] data = encodeSwapBuy(c.tokenAddress, store.bnbAddress, deadline);
        String raw = signBsc(creds, PANCAKE_ROUTER, wei, data, nonce, 5_000_000_000L, 350_000L);
        String txHash = broadcastBsc(raw);
        Log.i(TAG, "BNB buy: " + txHash);
        cb.onSuccess(txHash, c.priceUsd);
    }

    private static void sellBnb(String mnemonic, DexAppStore store, DexCandidate c,
                                 String tokenAddr, double tokenAmt, SwapCallback cb) throws Exception {
        Credentials creds = bnbCreds(mnemonic);
        BigInteger tokenWei = toWei(tokenAmt);
        int nonce = getBscNonce(store.bnbAddress);
        long deadline = System.currentTimeMillis() / 1000 + 300;
        // Approve
        String approveTx = signBsc(creds, tokenAddr, BigInteger.ZERO,
            encodeApprove(PANCAKE_ROUTER, tokenWei), nonce, 5_000_000_000L, 100_000L);
        broadcastBsc(approveTx);
        Thread.sleep(4000);
        // Swap
        String raw = signBsc(creds, PANCAKE_ROUTER, BigInteger.ZERO,
            encodeSwapSell(tokenAddr, tokenWei, store.bnbAddress, deadline),
            nonce + 1, 5_000_000_000L, 350_000L);
        String txHash = broadcastBsc(raw);
        Log.i(TAG, "BNB sell: " + txHash);
        cb.onSuccess(txHash, c.priceUsd);
    }

    // ─ SOL / Jupiter ───────────────────────────────────────────

    private static void buySol(String mnemonic, DexAppStore store,
                               DexCandidate c, SwapCallback cb) throws Exception {
        byte[] privKey = solPrivKey(mnemonic);
        long lamports  = (long)(store.tradeAmountUsd / getPrice("SOLUSDT") * 1_000_000_000L);
        String quoteUrl = JUPITER_QUOTE + "?inputMint=" + WSOL
            + "&outputMint=" + c.tokenAddress + "&amount=" + lamports + "&slippageBps=300";
        JSONObject quote = httpGetJson(quoteUrl);
        JSONObject swapReq = new JSONObject();
        swapReq.put("quoteResponse", quote);
        swapReq.put("userPublicKey", store.solAddress);
        swapReq.put("wrapAndUnwrapSol", true);
        JSONObject resp = httpPostJson(JUPITER_SWAP, swapReq);
        byte[] txBytes = android.util.Base64.decode(
            resp.getString("swapTransaction"), android.util.Base64.DEFAULT);
        String txHash = broadcastSol(signSol(privKey, txBytes));
        Log.i(TAG, "SOL buy: " + txHash);
        cb.onSuccess(txHash, c.priceUsd);
    }

    private static void sellSol(String mnemonic, DexAppStore store, DexCandidate c,
                                String tokenMint, double tokenAmt, SwapCallback cb) throws Exception {
        byte[] privKey = solPrivKey(mnemonic);
        long units = (long)(tokenAmt * 1_000_000L);
        String quoteUrl = JUPITER_QUOTE + "?inputMint=" + tokenMint
            + "&outputMint=" + WSOL + "&amount=" + units + "&slippageBps=300";
        JSONObject quote = httpGetJson(quoteUrl);
        JSONObject swapReq = new JSONObject();
        swapReq.put("quoteResponse", quote);
        swapReq.put("userPublicKey", store.solAddress);
        swapReq.put("wrapAndUnwrapSol", true);
        JSONObject resp = httpPostJson(JUPITER_SWAP, swapReq);
        byte[] txBytes = android.util.Base64.decode(
            resp.getString("swapTransaction"), android.util.Base64.DEFAULT);
        String txHash = broadcastSol(signSol(privKey, txBytes));
        Log.i(TAG, "SOL sell: " + txHash);
        cb.onSuccess(txHash, c.priceUsd);
    }

    // ─ KEY DERIVATION ─────────────────────────────────────────

    private static Credentials bnbCreds(String mnemonic) throws Exception {
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, "");
        Bip32ECKeyPair master = Bip32ECKeyPair.generateKeyPair(seed);
        return Credentials.create(Bip32ECKeyPair.deriveKeyPair(master, BNB_PATH));
    }

    private static byte[] solPrivKey(String mnemonic) throws Exception {
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, "");
        return SecurePrefs.slip10(seed, SOL_PATH);
    }

    // ─ SIGNING ───────────────────────────────────────────────

    private static String signBsc(Credentials creds, String to, BigInteger value,
                                   byte[] data, int nonce, long gasPrice, long gasLimit)
            throws Exception {
        RawTransaction tx = RawTransaction.createTransaction(
            BigInteger.valueOf(nonce),
            BigInteger.valueOf(gasPrice),
            BigInteger.valueOf(gasLimit),
            to, value,
            Numeric.toHexString(data));
        byte[] signed = TransactionEncoder.signMessage(tx, BSC_CHAIN_ID, creds);
        return Numeric.toHexString(signed);
    }

    private static byte[] signSol(byte[] privKey, byte[] txBytes) throws Exception {
        net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec spec =
            new net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec(
                privKey,
                net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable.getByName("Ed25519"));
        net.i2p.crypto.eddsa.EdDSAEngine signer = new net.i2p.crypto.eddsa.EdDSAEngine();
        signer.initSign(new net.i2p.crypto.eddsa.EdDSAPrivateKey(spec));
        byte[] sig = signer.signOneShot(txBytes);
        byte[] result = txBytes.clone();
        if (result.length > 65) System.arraycopy(sig, 0, result, 1, Math.min(64, sig.length));
        return result;
    }

    // ─ RPC ───────────────────────────────────────────────────

    private static int getBscNonce(String address) throws Exception {
        JSONObject req = new JSONObject();
        req.put("jsonrpc","2.0"); req.put("id",1);
        req.put("method","eth_getTransactionCount");
        req.put("params", new JSONArray().put(address).put("latest"));
        String result = httpPostJson(BSC_RPC, req).getString("result");
        return Integer.parseInt(result.substring(2), 16);
    }

    private static String broadcastBsc(String raw) throws Exception {
        JSONObject req = new JSONObject();
        req.put("jsonrpc","2.0"); req.put("id",1);
        req.put("method","eth_sendRawTransaction");
        req.put("params", new JSONArray().put(raw));
        return httpPostJson(BSC_RPC, req).getString("result");
    }

    private static String broadcastSol(byte[] signed) throws Exception {
        String b64 = android.util.Base64.encodeToString(signed, android.util.Base64.NO_WRAP);
        JSONObject req = new JSONObject();
        req.put("jsonrpc","2.0"); req.put("id",1);
        req.put("method","sendTransaction");
        req.put("params", new JSONArray().put(b64)
            .put(new JSONObject().put("encoding","base64")));
        return httpPostJson(SOL_RPC, req).optString("result","unknown");
    }

    private static double getPrice(String symbol) throws Exception {
        return Double.parseDouble(
            httpGetJson(BINANCE_PRICE + "?symbol=" + symbol).getString("price"));
    }

    // ─ ABI ENCODING ───────────────────────────────────────────

    private static byte[] encodeSwapBuy(String token, String to, long deadline) {
        return hexBytes("b6f9de95" + p32(0) + p32(128) + padAddr(to) + p32(deadline)
            + p32(2) + padAddr(WBNB) + padAddr(token));
    }

    private static byte[] encodeSwapSell(String token, BigInteger amount, String to, long deadline) {
        return hexBytes("791ac947" + p32(amount) + p32(0) + p32(160)
            + padAddr(to) + p32(deadline) + p32(2) + padAddr(token) + padAddr(WBNB));
    }

    private static byte[] encodeApprove(String spender, BigInteger amount) {
        return hexBytes("095ea7b3" + padAddr(spender) + p32(amount));
    }

    // ─ UTILS ────────────────────────────────────────────────

    private static BigInteger toWei(double v) {
        return BigInteger.valueOf((long)(v * 1e12)).multiply(BigInteger.valueOf(1_000_000L));
    }

    private static String p32(long v)       { return String.format("%064x", v); }
    private static String p32(BigInteger v) { return String.format("%064x", v); }
    private static String padAddr(String a) {
        String s = a.startsWith("0x") ? a.substring(2) : a;
        return String.format("%64s", s).replace(' ','0');
    }

    private static byte[] hexBytes(String h) {
        byte[] d = new byte[h.length() / 2];
        for (int i = 0; i < d.length; i++)
            d[i] = (byte)((Character.digit(h.charAt(i*2),16) << 4)
                         + Character.digit(h.charAt(i*2+1),16));
        return d;
    }

    private static JSONObject httpGetJson(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(15000);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String ln;
        while ((ln = r.readLine()) != null) sb.append(ln);
        r.close();
        return new JSONObject(sb.toString());
    }

    private static JSONObject httpPostJson(String url, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type","application/json");
        c.setDoOutput(true);
        c.setConnectTimeout(12000); c.setReadTimeout(15000);
        try (OutputStream os = c.getOutputStream())
            { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String ln;
        while ((ln = r.readLine()) != null) sb.append(ln);
        r.close();
        return new JSONObject(sb.toString());
    }
}
