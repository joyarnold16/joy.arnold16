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
 *
 * Every swap is quoted first and protected with a minimum-output bound
 * derived from store.slippageBps, so a sandwich/MEV bot cannot take the
 * trade at an arbitrary price. Token amounts are read from on-chain
 * balances rather than assumed decimals.
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

    private static final BigInteger MAX_UINT =
        BigInteger.valueOf(2).pow(256).subtract(BigInteger.ONE);

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
                    sellBnb(mnemonic, store, c, tokenMint, cb);
                else if ("solana".equals(c.chain) && store.liveChainSol)
                    sellSol(mnemonic, store, c, tokenMint, cb);
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
        BigInteger wei = toWeiBnb(bnbAmt);

        // Quote expected token output and protect with slippage bound
        String[] path = { WBNB, c.tokenAddress };
        BigInteger expectedOut = getAmountsOut(wei, path);
        BigInteger minOut = applySlippage(expectedOut, store.slippageBps);
        if (minOut.signum() <= 0) { cb.onFail("Quote failed (no liquidity?)"); return; }

        int nonce = getBscNonce(store.bnbAddress);
        long deadline = System.currentTimeMillis() / 1000 + 300;
        byte[] data = encodeSwapBuy(minOut, c.tokenAddress, store.bnbAddress, deadline);
        String raw = signBsc(creds, PANCAKE_ROUTER, wei, data, nonce, 5_000_000_000L, 400_000L);
        String txHash = broadcastBsc(raw);
        Log.i(TAG, "BNB buy: " + txHash + " minOut=" + minOut);
        cb.onSuccess(txHash, c.priceUsd);
    }

    private static void sellBnb(String mnemonic, DexAppStore store, DexCandidate c,
                                 String tokenAddr, SwapCallback cb) throws Exception {
        Credentials creds = bnbCreds(mnemonic);

        // Sell the actual on-chain balance (avoids decimals guesswork / dust)
        BigInteger bal = balanceOf(tokenAddr, store.bnbAddress);
        if (bal.signum() <= 0) { cb.onFail("No token balance to sell"); return; }

        int nonce = getBscNonce(store.bnbAddress);

        // Approve router once if allowance is insufficient, then wait for it
        BigInteger allowed = allowance(tokenAddr, store.bnbAddress, PANCAKE_ROUTER);
        if (allowed.compareTo(bal) < 0) {
            String approveTx = signBsc(creds, tokenAddr, BigInteger.ZERO,
                encodeApprove(PANCAKE_ROUTER, MAX_UINT), nonce, 5_000_000_000L, 100_000L);
            broadcastBsc(approveTx);
            waitForReceipt(approveTx, 45);
            nonce++;
        }

        String[] path = { tokenAddr, WBNB };
        BigInteger expectedOut = getAmountsOut(bal, path);
        BigInteger minOut = applySlippage(expectedOut, store.slippageBps);

        long deadline = System.currentTimeMillis() / 1000 + 300;
        String raw = signBsc(creds, PANCAKE_ROUTER, BigInteger.ZERO,
            encodeSwapSell(tokenAddr, bal, minOut, store.bnbAddress, deadline),
            nonce, 5_000_000_000L, 400_000L);
        String txHash = broadcastBsc(raw);
        Log.i(TAG, "BNB sell: " + txHash + " amount=" + bal + " minOut=" + minOut);
        cb.onSuccess(txHash, c.priceUsd);
    }

    // ─ SOL / Jupiter ───────────────────────────────────────────

    private static void buySol(String mnemonic, DexAppStore store,
                               DexCandidate c, SwapCallback cb) throws Exception {
        byte[] privKey = solPrivKey(mnemonic);
        long lamports  = (long)(store.tradeAmountUsd / getPrice("SOLUSDT") * 1_000_000_000L);
        String quoteUrl = JUPITER_QUOTE + "?inputMint=" + WSOL
            + "&outputMint=" + c.tokenAddress + "&amount=" + lamports
            + "&slippageBps=" + store.slippageBps;
        JSONObject quote = httpGetJson(quoteUrl);
        if (!quote.has("outAmount")) { cb.onFail("Jupiter quote failed"); return; }
        String txHash = jupiterSwap(privKey, store.solAddress, quote);
        Log.i(TAG, "SOL buy: " + txHash);
        cb.onSuccess(txHash, c.priceUsd);
    }

    private static void sellSol(String mnemonic, DexAppStore store, DexCandidate c,
                                String tokenMint, SwapCallback cb) throws Exception {
        byte[] privKey = solPrivKey(mnemonic);

        // Read the real SPL token balance (base units) and sell all of it
        long units = getSplBalance(store.solAddress, tokenMint);
        if (units <= 0) { cb.onFail("No token balance to sell"); return; }

        String quoteUrl = JUPITER_QUOTE + "?inputMint=" + tokenMint
            + "&outputMint=" + WSOL + "&amount=" + units
            + "&slippageBps=" + store.slippageBps;
        JSONObject quote = httpGetJson(quoteUrl);
        if (!quote.has("outAmount")) { cb.onFail("Jupiter quote failed"); return; }
        String txHash = jupiterSwap(privKey, store.solAddress, quote);
        Log.i(TAG, "SOL sell: " + txHash + " units=" + units);
        cb.onSuccess(txHash, c.priceUsd);
    }

    private static String jupiterSwap(byte[] privKey, String userPubkey, JSONObject quote)
            throws Exception {
        JSONObject swapReq = new JSONObject();
        swapReq.put("quoteResponse", quote);
        swapReq.put("userPublicKey", userPubkey);
        swapReq.put("wrapAndUnwrapSol", true);
        JSONObject resp = httpPostJson(JUPITER_SWAP, swapReq);
        byte[] txBytes = android.util.Base64.decode(
            resp.getString("swapTransaction"), android.util.Base64.DEFAULT);
        return broadcastSol(signSol(privKey, txBytes));
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

    /**
     * Signs a Jupiter v6 VersionedTransaction. The serialized form is:
     *   [shortvec sigCount][sigCount * 64-byte signatures][message bytes]
     * Jupiter returns it with the fee-payer signature slot (index 0) zeroed.
     * We sign ONLY the message bytes and write our 64-byte signature into
     * slot 0. (User is the fee payer / first required signer.)
     */
    private static byte[] signSol(byte[] privKey, byte[] txBytes) throws Exception {
        int sigCount = txBytes[0] & 0xFF;          // <128 → single shortvec byte
        int msgStart = 1 + sigCount * 64;
        byte[] message = Arrays.copyOfRange(txBytes, msgStart, txBytes.length);

        net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec spec =
            new net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec(
                privKey,
                net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable.getByName("Ed25519"));
        net.i2p.crypto.eddsa.EdDSAEngine signer = new net.i2p.crypto.eddsa.EdDSAEngine();
        signer.initSign(new net.i2p.crypto.eddsa.EdDSAPrivateKey(spec));
        byte[] sig = signer.signOneShot(message);

        byte[] result = txBytes.clone();
        System.arraycopy(sig, 0, result, 1, 64);   // fill first signature slot
        return result;
    }

    // ─ RPC: BSC ──────────────────────────────────────────────

    private static int getBscNonce(String address) throws Exception {
        JSONObject req = rpc("eth_getTransactionCount",
            new JSONArray().put(address).put("pending"));
        String result = httpPostJson(BSC_RPC, req).getString("result");
        return Integer.parseInt(result.substring(2), 16);
    }

    private static String broadcastBsc(String raw) throws Exception {
        JSONObject req = rpc("eth_sendRawTransaction", new JSONArray().put(raw));
        JSONObject resp = httpPostJson(BSC_RPC, req);
        if (resp.has("error"))
            throw new Exception("RPC: " + resp.getJSONObject("error").optString("message"));
        return resp.getString("result");
    }

    private static String ethCall(String to, String data) throws Exception {
        JSONObject call = new JSONObject().put("to", to).put("data", "0x" + data);
        JSONObject req = rpc("eth_call", new JSONArray().put(call).put("latest"));
        return httpPostJson(BSC_RPC, req).optString("result", "0x");
    }

    /** PancakeSwap router getAmountsOut(amountIn, path) → final output amount. */
    private static BigInteger getAmountsOut(BigInteger amountIn, String[] path) throws Exception {
        StringBuilder d = new StringBuilder("d06ca61f");
        d.append(p32(amountIn));
        d.append(p32(64));                 // offset to path array
        d.append(p32(path.length));
        for (String a : path) d.append(padAddr(a));
        String res = ethCall(PANCAKE_ROUTER, d.toString());
        return lastWord(res);              // last array element = output amount
    }

    private static BigInteger balanceOf(String token, String owner) throws Exception {
        String res = ethCall(token, "70a08231" + padAddr(owner));
        return lastWord(res);
    }

    private static BigInteger allowance(String token, String owner, String spender) throws Exception {
        String res = ethCall(token, "dd62ed3e" + padAddr(owner) + padAddr(spender));
        return lastWord(res);
    }

    private static void waitForReceipt(String txHash, int maxSeconds) {
        for (int i = 0; i < maxSeconds; i += 3) {
            try {
                JSONObject req = rpc("eth_getTransactionReceipt", new JSONArray().put(txHash));
                JSONObject r = httpPostJson(BSC_RPC, req);
                if (!r.isNull("result") && r.opt("result") != null
                        && !(r.opt("result") instanceof String)) return;
            } catch (Exception ignore) {}
            try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
        }
    }

    // ─ RPC: Solana ───────────────────────────────────────────

    private static String broadcastSol(byte[] signed) throws Exception {
        String b64 = android.util.Base64.encodeToString(signed, android.util.Base64.NO_WRAP);
        JSONObject req = rpc("sendTransaction", new JSONArray().put(b64)
            .put(new JSONObject().put("encoding", "base64")));
        JSONObject resp = httpPostJson(SOL_RPC, req);
        if (resp.has("error"))
            throw new Exception("RPC: " + resp.getJSONObject("error").optString("message"));
        return resp.optString("result", "unknown");
    }

    /** Reads the raw SPL token balance (base units) for owner's account of mint. */
    private static long getSplBalance(String owner, String mint) throws Exception {
        JSONObject filter = new JSONObject().put("mint", mint);
        JSONObject cfg    = new JSONObject().put("encoding", "jsonParsed");
        JSONObject req = rpc("getTokenAccountsByOwner",
            new JSONArray().put(owner).put(filter).put(cfg));
        JSONObject resp = httpPostJson(SOL_RPC, req);
        JSONArray accts = resp.getJSONObject("result").getJSONArray("value");
        if (accts.length() == 0) return 0;
        String amt = accts.getJSONObject(0)
            .getJSONObject("account").getJSONObject("data")
            .getJSONObject("parsed").getJSONObject("info")
            .getJSONObject("tokenAmount").getString("amount");
        return Long.parseLong(amt);
    }

    private static JSONObject rpc(String method, JSONArray params) throws Exception {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0"); req.put("id", 1);
        req.put("method", method); req.put("params", params);
        return req;
    }

    private static double getPrice(String symbol) throws Exception {
        return Double.parseDouble(
            httpGetJson(BINANCE_PRICE + "?symbol=" + symbol).getString("price"));
    }

    // ─ ABI ENCODING ───────────────────────────────────────────

    // swapExactETHForTokensSupportingFeeOnTransferTokens(amountOutMin, path, to, deadline)
    private static byte[] encodeSwapBuy(BigInteger minOut, String token, String to, long deadline) {
        return hexBytes("b6f9de95" + p32(minOut) + p32(128) + padAddr(to) + p32(deadline)
            + p32(2) + padAddr(WBNB) + padAddr(token));
    }

    // swapExactTokensForETHSupportingFeeOnTransferTokens(amountIn, amountOutMin, path, to, deadline)
    private static byte[] encodeSwapSell(String token, BigInteger amount, BigInteger minOut,
                                         String to, long deadline) {
        return hexBytes("791ac947" + p32(amount) + p32(minOut) + p32(160)
            + padAddr(to) + p32(deadline) + p32(2) + padAddr(token) + padAddr(WBNB));
    }

    private static byte[] encodeApprove(String spender, BigInteger amount) {
        return hexBytes("095ea7b3" + padAddr(spender) + p32(amount));
    }

    // ─ UTILS ────────────────────────────────────────────────

    private static BigInteger applySlippage(BigInteger amount, int slippageBps) {
        if (amount == null || amount.signum() <= 0) return BigInteger.ZERO;
        int bps = Math.max(0, Math.min(5000, slippageBps)); // cap at 50%
        return amount.multiply(BigInteger.valueOf(10_000 - bps))
                     .divide(BigInteger.valueOf(10_000));
    }

    /** Last 32-byte word of an eth_call result (single uint or final array element). */
    private static BigInteger lastWord(String hex) {
        if (hex == null) return BigInteger.ZERO;
        String s = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (s.length() < 64) return BigInteger.ZERO;
        return new BigInteger(s.substring(s.length() - 64), 16);
    }

    private static BigInteger toWeiBnb(double v) {
        // BNB has 18 decimals: split to keep precision within long range
        return BigInteger.valueOf((long) (v * 1e12)).multiply(BigInteger.valueOf(1_000_000L));
    }

    private static String p32(long v)       { return String.format("%064x", v); }
    private static String p32(BigInteger v) { return String.format("%064x", v); }
    private static String padAddr(String a) {
        String s = a.startsWith("0x") ? a.substring(2) : a;
        return String.format("%64s", s).replace(' ', '0');
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
        InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder(); String ln;
        while ((ln = r.readLine()) != null) sb.append(ln);
        r.close();
        return new JSONObject(sb.toString());
    }
}
