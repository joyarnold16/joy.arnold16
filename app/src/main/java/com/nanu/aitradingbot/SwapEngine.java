package com.nanu.aitradingbot;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Live on-chain swap execution.
 * SAFETY: Only executes when store.liveMode == true.
 * BNB trades via PancakeSwap V2 on BSC.
 * SOL trades via Jupiter Aggregator on Solana.
 */
public class SwapEngine {
    private static final String TAG = "SwapEngine";

    private static final String BSC_RPC       = "https://bsc-dataseed.binance.org/";
    private static final String PANCAKE_ROUTER = "0x10ED43C718714eb63d5aA57B78B54704E256024E";
    private static final String WBNB          = "0xbb4CdB9CBd36B01bD1cBaEBF2De08d9173bc095c";
    private static final long   BSC_CHAIN_ID  = 56L;

    private static final String JUPITER_QUOTE = "https://quote-api.jup.ag/v6/quote";
    private static final String JUPITER_SWAP  = "https://quote-api.jup.ag/v6/swap";
    private static final String SOL_RPC       = "https://api.mainnet-beta.solana.com";
    private static final String WSOL          = "So11111111111111111111111111111111111111112";

    private static final String BINANCE_API   = "https://api.binance.com/api/v3/ticker/price";

    public interface SwapCallback {
        void onSuccess(String txHash, double executedPrice);
        void onFail(String reason);
    }

    // ─ PUBLIC API ─────────────────────────────────────────────────────

    public static void buy(DexAppStore store, DexCandidate c, SwapCallback cb) {
        if (!store.liveMode) { cb.onFail("PAPER_MODE"); return; }
        if (store.tradeAmountUsd <= 0) { cb.onFail("Set trade amount in Control tab"); return; }
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

    // ─ BNB / PancakeSwap ───────────────────────────────────────────

    private static void buyBnb(String mnemonic, DexAppStore store,
                                DexCandidate c, SwapCallback cb) throws Exception {
        wallet.sdk.core.HDWallet hw = new wallet.sdk.core.HDWallet(mnemonic, "");
        byte[] privKey = hw.getKeyForCoin(wallet.sdk.core.CoinType.SMARTCHAIN).data();

        double bnbPrice = getPrice("BNBUSDT");
        double bnbAmt   = store.tradeAmountUsd / bnbPrice;
        BigInteger bnbWei = toWei18(bnbAmt);
        int nonce = getBscNonce(store.bnbAddress);
        long deadline = System.currentTimeMillis() / 1000 + 300;

        byte[] data = encodeSwapBuy(c.tokenAddress, store.bnbAddress, deadline);
        String raw  = signBscTx(privKey, PANCAKE_ROUTER, bnbWei, data, nonce,
                                5_000_000_000L, 350_000L);
        String txHash = broadcastBsc(raw);
        Log.i(TAG, "BNB buy tx: " + txHash);
        cb.onSuccess(txHash, c.priceUsd);
    }

    private static void sellBnb(String mnemonic, DexAppStore store, DexCandidate c,
                                 String tokenAddr, double tokenAmt, SwapCallback cb) throws Exception {
        wallet.sdk.core.HDWallet hw = new wallet.sdk.core.HDWallet(mnemonic, "");
        byte[] privKey = hw.getKeyForCoin(wallet.sdk.core.CoinType.SMARTCHAIN).data();

        BigInteger tokenWei = toWei18(tokenAmt);
        int nonce = getBscNonce(store.bnbAddress);
        long deadline = System.currentTimeMillis() / 1000 + 300;

        // Approve first
        byte[] approveData = encodeApprove(PANCAKE_ROUTER, tokenWei);
        String approveTx = signBscTx(privKey, tokenAddr, BigInteger.ZERO, approveData,
                                     nonce, 5_000_000_000L, 100_000L);
        broadcastBsc(approveTx);
        Thread.sleep(4000);

        // Swap token → BNB
        byte[] swapData = encodeSwapSell(tokenAddr, tokenWei, store.bnbAddress, deadline);
        String raw = signBscTx(privKey, PANCAKE_ROUTER, BigInteger.ZERO, swapData,
                               nonce + 1, 5_000_000_000L, 350_000L);
        String txHash = broadcastBsc(raw);
        Log.i(TAG, "BNB sell tx: " + txHash);
        cb.onSuccess(txHash, c.priceUsd);
    }

    // ─ SOL / Jupiter ───────────────────────────────────────────────

    private static void buySol(String mnemonic, DexAppStore store,
                               DexCandidate c, SwapCallback cb) throws Exception {
        wallet.sdk.core.HDWallet hw = new wallet.sdk.core.HDWallet(mnemonic, "");
        byte[] privKey = hw.getKeyForCoin(wallet.sdk.core.CoinType.SOLANA).data();

        double solPrice = getPrice("SOLUSDT");
        double solAmt   = store.tradeAmountUsd / solPrice;
        long lamports   = (long)(solAmt * 1_000_000_000L);

        String quoteUrl = JUPITER_QUOTE + "?inputMint=" + WSOL
            + "&outputMint=" + c.tokenAddress
            + "&amount=" + lamports
            + "&slippageBps=300";
        JSONObject quote = httpGetJson(quoteUrl);

        JSONObject swapReq = new JSONObject();
        swapReq.put("quoteResponse", quote);
        swapReq.put("userPublicKey", store.solAddress);
        swapReq.put("wrapAndUnwrapSol", true);
        JSONObject swapResp = httpPostJson(JUPITER_SWAP, swapReq);

        String txB64 = swapResp.getString("swapTransaction");
        byte[] txBytes = android.util.Base64.decode(txB64, android.util.Base64.DEFAULT);
        byte[] signed  = signSolTx(privKey, txBytes);
        String txHash  = broadcastSol(signed);
        Log.i(TAG, "SOL buy tx: " + txHash);
        cb.onSuccess(txHash, c.priceUsd);
    }

    private static void sellSol(String mnemonic, DexAppStore store, DexCandidate c,
                                String tokenMint, double tokenAmt, SwapCallback cb) throws Exception {
        wallet.sdk.core.HDWallet hw = new wallet.sdk.core.HDWallet(mnemonic, "");
        byte[] privKey = hw.getKeyForCoin(wallet.sdk.core.CoinType.SOLANA).data();

        long tokenUnits = (long)(tokenAmt * 1_000_000L); // 6 decimals

        String quoteUrl = JUPITER_QUOTE + "?inputMint=" + tokenMint
            + "&outputMint=" + WSOL
            + "&amount=" + tokenUnits
            + "&slippageBps=300";
        JSONObject quote = httpGetJson(quoteUrl);

        JSONObject swapReq = new JSONObject();
        swapReq.put("quoteResponse", quote);
        swapReq.put("userPublicKey", store.solAddress);
        swapReq.put("wrapAndUnwrapSol", true);
        JSONObject swapResp = httpPostJson(JUPITER_SWAP, swapReq);

        String txB64 = swapResp.getString("swapTransaction");
        byte[] txBytes = android.util.Base64.decode(txB64, android.util.Base64.DEFAULT);
        byte[] signed  = signSolTx(privKey, txBytes);
        String txHash  = broadcastSol(signed);
        Log.i(TAG, "SOL sell tx: " + txHash);
        cb.onSuccess(txHash, c.priceUsd);
    }

    // ─ SIGNING ──────────────────────────────────────────────────────

    private static String signBscTx(byte[] privKey, String to, BigInteger value,
                                     byte[] data, int nonce, long gasPrice, long gasLimit)
            throws Exception {
        wallet.sdk.proto.Ethereum.Transaction.ContractGeneric generic =
            wallet.sdk.proto.Ethereum.Transaction.ContractGeneric.newBuilder()
                .setAmount(com.google.protobuf.ByteString.copyFrom(
                    value.equals(BigInteger.ZERO) ? new byte[]{0} : value.toByteArray()))
                .setData(com.google.protobuf.ByteString.copyFrom(data))
                .build();

        wallet.sdk.proto.Ethereum.SigningInput input =
            wallet.sdk.proto.Ethereum.SigningInput.newBuilder()
                .setChainId(com.google.protobuf.ByteString.copyFrom(
                    BigInteger.valueOf(BSC_CHAIN_ID).toByteArray()))
                .setNonce(com.google.protobuf.ByteString.copyFrom(
                    BigInteger.valueOf(nonce).toByteArray()))
                .setGasPrice(com.google.protobuf.ByteString.copyFrom(
                    BigInteger.valueOf(gasPrice).toByteArray()))
                .setGasLimit(com.google.protobuf.ByteString.copyFrom(
                    BigInteger.valueOf(gasLimit).toByteArray()))
                .setToAddress(to)
                .setPrivateKey(com.google.protobuf.ByteString.copyFrom(privKey))
                .setTransaction(wallet.sdk.proto.Ethereum.Transaction.newBuilder()
                    .setContractGeneric(generic).build())
                .build();

        wallet.sdk.proto.Ethereum.SigningOutput output =
            (wallet.sdk.proto.Ethereum.SigningOutput)
            wallet.sdk.core.AnySigner.sign(input, wallet.sdk.core.CoinType.SMARTCHAIN,
                wallet.sdk.proto.Ethereum.SigningOutput.parser());
        return "0x" + hex(output.getEncoded().toByteArray());
    }

    private static byte[] signSolTx(byte[] privKey, byte[] txBytes) {
        wallet.sdk.core.PrivateKey pk = new wallet.sdk.core.PrivateKey(privKey);
        byte[] sig = pk.sign(txBytes, wallet.sdk.core.Curve.ED25519);
        byte[] result = txBytes.clone();
        // Inject signature at byte 1 (after compact-u16 sig count)
        if (result.length > 65) System.arraycopy(sig, 0, result, 1, Math.min(64, sig.length));
        return result;
    }

    // ─ RPC ───────────────────────────────────────────────────────────

    private static int getBscNonce(String address) throws Exception {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0"); req.put("id", 1);
        req.put("method", "eth_getTransactionCount");
        req.put("params", new JSONArray().put(address).put("latest"));
        JSONObject resp = httpPostJson(BSC_RPC, req);
        return Integer.parseInt(resp.getString("result").substring(2), 16);
    }

    private static String broadcastBsc(String signedTx) throws Exception {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0"); req.put("id", 1);
        req.put("method", "eth_sendRawTransaction");
        req.put("params", new JSONArray().put(signedTx));
        return httpPostJson(BSC_RPC, req).getString("result");
    }

    private static String broadcastSol(byte[] signed) throws Exception {
        String b64 = android.util.Base64.encodeToString(signed, android.util.Base64.NO_WRAP);
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0"); req.put("id", 1);
        req.put("method", "sendTransaction");
        req.put("params", new JSONArray().put(b64)
            .put(new JSONObject().put("encoding", "base64")));
        return httpPostJson(SOL_RPC, req).optString("result", "unknown");
    }

    private static double getPrice(String symbol) throws Exception {
        JSONObject j = httpGetJson(BINANCE_API + "?symbol=" + symbol);
        return Double.parseDouble(j.getString("price"));
    }

    // ─ ABI ENCODING ───────────────────────────────────────────────

    private static byte[] encodeSwapBuy(String token, String to, long deadline) {
        // swapExactETHForTokensSupportingFeeOnTransferTokens selector
        return hexBytes("b6f9de95"
            + p32(0)             // amountOutMin (0 = max slippage, handled by 3% on DEX)
            + p32(128)           // path[] offset
            + padAddr(to)        // recipient
            + p32(deadline)
            + p32(2)             // path length
            + padAddr(WBNB)
            + padAddr(token));
    }

    private static byte[] encodeSwapSell(String token, BigInteger amount,
                                          String to, long deadline) {
        // swapExactTokensForETHSupportingFeeOnTransferTokens selector
        return hexBytes("791ac947"
            + p32(amount)        // amountIn
            + p32(0)             // amountOutMin
            + p32(160)           // path offset
            + padAddr(to)
            + p32(deadline)
            + p32(2)
            + padAddr(token)
            + padAddr(WBNB));
    }

    private static byte[] encodeApprove(String spender, BigInteger amount) {
        // approve(address,uint256) selector: 095ea7b3
        return hexBytes("095ea7b3" + padAddr(spender) + p32(amount));
    }

    // ─ UTILS ──────────────────────────────────────────────────────────

    private static BigInteger toWei18(double v) {
        return BigInteger.valueOf((long)(v * 1e12)).multiply(BigInteger.valueOf(1_000_000L));
    }

    private static String p32(long v)       { return String.format("%064x", v); }
    private static String p32(BigInteger v) { return String.format("%064x", v); }
    private static String padAddr(String a) {
        String s = a.startsWith("0x") ? a.substring(2) : a;
        return String.format("%64s", s).replace(' ', '0');
    }
    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }
    private static byte[] hexBytes(String h) {
        byte[] d = new byte[h.length() / 2];
        for (int i = 0; i < d.length; i++)
            d[i] = (byte)((Character.digit(h.charAt(i*2),16) << 4)
                        + Character.digit(h.charAt(i*2+1), 16));
        return d;
    }

    private static JSONObject httpGetJson(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(15000);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String ln;
        while ((ln = r.readLine()) != null) sb.append(ln);
        return new JSONObject(sb.toString());
    }

    private static JSONObject httpPostJson(String url, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        c.setConnectTimeout(12000); c.setReadTimeout(15000);
        try (OutputStream os = c.getOutputStream())
            { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String ln;
        while ((ln = r.readLine()) != null) sb.append(ln);
        return new JSONObject(sb.toString());
    }
}
