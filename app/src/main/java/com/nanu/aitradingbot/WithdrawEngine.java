package com.nanu.aitradingbot;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.MnemonicUtils;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Native-coin withdrawals (sending funds OUT of the bot wallet).
 *  - BNB on BSC: standard 21000-gas ether transfer, signed with web3j.
 *  - SOL on Solana: SystemProgram transfer, hand-serialized + ed25519 signed.
 *
 * SAFETY:
 *  - Only runs when store.liveMode == true (paper mode cannot sign).
 *  - Gas/fee preflight: refuses if the native balance cannot cover
 *    amount + fee, so we never broadcast a doomed transaction.
 *  - All network + signing work happens on a background thread; the
 *    callback is invoked from that thread (caller marshals to UI).
 */
public class WithdrawEngine {
    private static final String TAG = "WithdrawEngine";

    private static final String BSC_RPC      = "https://bsc-dataseed.binance.org/";
    private static final long   BSC_CHAIN_ID = 56L;
    private static final long   BSC_GAS_PRICE = 5_000_000_000L; // 5 gwei
    private static final long   BSC_GAS_LIMIT = 21_000L;        // plain transfer

    private static final String SOL_RPC      = "https://api.mainnet-beta.solana.com";
    private static final long   SOL_FEE_LAMPORTS = 5_000L;      // 1 signature
    private static final long   LAMPORTS_PER_SOL = 1_000_000_000L;

    // BIP44 paths (must match SwapEngine / SecurePrefs)
    private static final int[] BNB_PATH = {
        44 | Bip32ECKeyPair.HARDENED_BIT,
        60 | Bip32ECKeyPair.HARDENED_BIT,
         0 | Bip32ECKeyPair.HARDENED_BIT, 0, 0};
    private static final int[] SOL_PATH = {44, 501, 0, 0};

    public interface WithdrawCallback {
        void onSuccess(String txHash);
        void onFail(String reason);
    }

    // ─ PUBLIC ─────────────────────────────────────────────────

    /** chain = "bnb" or "sol"; amount is in whole coins (BNB / SOL). */
    public static void withdraw(DexAppStore store, String chain,
                                String toAddress, double amount, WithdrawCallback cb) {
        if (!store.liveMode) { cb.onFail("Live mode is off — paper mode cannot sign."); return; }
        if (amount <= 0)     { cb.onFail("Enter an amount greater than 0."); return; }
        String mnemonic = store.getMnemonic();
        if (mnemonic == null || mnemonic.isEmpty()) { cb.onFail("No wallet available."); return; }
        final String to = toAddress == null ? "" : toAddress.trim();
        if (to.isEmpty()) { cb.onFail("Enter a destination address."); return; }

        new Thread(() -> {
            try {
                if ("bnb".equals(chain))      withdrawBnb(mnemonic, store, to, amount, cb);
                else if ("sol".equals(chain)) withdrawSol(mnemonic, store, to, amount, cb);
                else cb.onFail("Unknown chain: " + chain);
            } catch (Exception e) {
                Log.e(TAG, "Withdraw failed", e);
                cb.onFail("Withdraw error: " + e.getMessage());
            }
        }, "nanu-withdraw").start();
    }

    // ─ BNB / BSC ─────────────────────────────────────────────

    private static void withdrawBnb(String mnemonic, DexAppStore store,
                                    String to, double amountBnb, WithdrawCallback cb) throws Exception {
        if (!to.startsWith("0x") || to.length() != 42) {
            cb.onFail("Invalid BSC address (expected 0x… 42 chars)."); return;
        }
        BigInteger value = toWeiBnb(amountBnb);
        BigInteger fee   = BigInteger.valueOf(BSC_GAS_PRICE).multiply(BigInteger.valueOf(BSC_GAS_LIMIT));
        BigInteger need  = value.add(fee);

        BigInteger balance = getBnbBalance(store.bnbAddress);
        if (balance.compareTo(need) < 0) {
            cb.onFail("Insufficient BNB. Need " + weiToBnb(need) + " BNB (incl. gas), have "
                + weiToBnb(balance) + " BNB.");
            return;
        }

        Credentials creds = Credentials.create(Bip32ECKeyPair.deriveKeyPair(
            Bip32ECKeyPair.generateKeyPair(MnemonicUtils.generateSeed(mnemonic, "")), BNB_PATH));
        int nonce = getBscNonce(store.bnbAddress);
        RawTransaction tx = RawTransaction.createEtherTransaction(
            BigInteger.valueOf(nonce),
            BigInteger.valueOf(BSC_GAS_PRICE),
            BigInteger.valueOf(BSC_GAS_LIMIT),
            to, value);
        byte[] signed = TransactionEncoder.signMessage(tx, BSC_CHAIN_ID, creds);
        String txHash = broadcastBsc(Numeric.toHexString(signed));
        Log.i(TAG, "BNB withdraw " + amountBnb + " → " + to + " : " + txHash);
        cb.onSuccess(txHash);
    }

    // ─ SOL / Solana ──────────────────────────────────────────

    private static void withdrawSol(String mnemonic, DexAppStore store,
                                    String to, double amountSol, WithdrawCallback cb) throws Exception {
        byte[] toPub;
        try { toPub = Base58.decode(to); }
        catch (Exception e) { cb.onFail("Invalid Solana address."); return; }
        if (toPub.length != 32) { cb.onFail("Invalid Solana address (must decode to 32 bytes)."); return; }
        byte[] fromPub;
        try { fromPub = Base58.decode(store.solAddress); }
        catch (Exception e) { cb.onFail("Wallet not ready (no Solana address)."); return; }
        if (fromPub.length != 32) { cb.onFail("Wallet not ready (no Solana address)."); return; }

        long lamports = (long) (amountSol * LAMPORTS_PER_SOL);
        long need     = lamports + SOL_FEE_LAMPORTS;
        long balance  = getSolBalance(store.solAddress);
        if (balance < need) {
            cb.onFail("Insufficient SOL. Need " + (need / (double) LAMPORTS_PER_SOL)
                + " SOL (incl. fee), have " + (balance / (double) LAMPORTS_PER_SOL) + " SOL.");
            return;
        }

        byte[] privKey   = SecurePrefs.slip10(MnemonicUtils.generateSeed(mnemonic, ""), SOL_PATH);
        byte[] blockhash = Base58.decode(getLatestBlockhash());
        if (blockhash.length != 32) { cb.onFail("Bad blockhash from RPC."); return; }

        byte[] message = buildSolTransferMessage(fromPub, toPub, lamports, blockhash);
        byte[] sig     = ed25519Sign(privKey, message);

        // Wire format: shortvec(sigCount=1) + 64-byte signature + message
        byte[] txBytes = new byte[1 + 64 + message.length];
        txBytes[0] = 1;
        System.arraycopy(sig, 0, txBytes, 1, 64);
        System.arraycopy(message, 0, txBytes, 65, message.length);

        String txHash = broadcastSol(txBytes);
        Log.i(TAG, "SOL withdraw " + amountSol + " → " + to + " : " + txHash);
        cb.onSuccess(txHash);
    }

    /**
     * Serializes a legacy Solana message with a single SystemProgram transfer.
     * Account order (writable signers first): [from, to, systemProgram].
     * Header = {numRequiredSignatures=1, numReadonlySigned=0, numReadonlyUnsigned=1}.
     */
    private static byte[] buildSolTransferMessage(byte[] from, byte[] to,
                                                  long lamports, byte[] blockhash) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Message header
        out.write(1); // numRequiredSignatures
        out.write(0); // numReadonlySignedAccounts
        out.write(1); // numReadonlyUnsignedAccounts (system program is readonly)
        // Account keys (compact-array, 3 entries — count < 128 fits one byte)
        out.write(3);
        out.write(from);          // index 0: fee-payer, signer, writable
        out.write(to);            // index 1: writable
        out.write(new byte[32]);  // index 2: SystemProgram id = 32 zero bytes
        // Recent blockhash
        out.write(blockhash);
        // Instructions (compact-array, 1 entry)
        out.write(1);
        out.write(2);             // programIdIndex → SystemProgram (account index 2)
        out.write(2);             // account index count
        out.write(0);             // from
        out.write(1);             // to
        // Instruction data: [transfer=2 (u32 LE)] + [lamports (u64 LE)] = 12 bytes
        byte[] data = new byte[12];
        data[0] = 2;
        for (int i = 0; i < 8; i++) data[4 + i] = (byte) ((lamports >> (8 * i)) & 0xFF);
        out.write(data.length);   // compact-array length (12)
        out.write(data);
        return out.toByteArray();
    }

    private static byte[] ed25519Sign(byte[] privKey, byte[] message) throws Exception {
        net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec spec =
            new net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec(
                privKey,
                net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable.getByName("Ed25519"));
        net.i2p.crypto.eddsa.EdDSAEngine signer = new net.i2p.crypto.eddsa.EdDSAEngine();
        signer.initSign(new net.i2p.crypto.eddsa.EdDSAPrivateKey(spec));
        return signer.signOneShot(message);
    }

    // ─ RPC ────────────────────────────────────────────────────

    private static BigInteger getBnbBalance(String address) throws Exception {
        JSONObject req = rpc("eth_getBalance", new JSONArray().put(address).put("latest"));
        String result = httpPostJson(BSC_RPC, req).getString("result");
        return new BigInteger(result.substring(2), 16);
    }

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
            throw new Exception(resp.getJSONObject("error").optString("message"));
        return resp.getString("result");
    }

    private static long getSolBalance(String address) throws Exception {
        JSONObject req = rpc("getBalance", new JSONArray().put(address));
        JSONObject resp = httpPostJson(SOL_RPC, req);
        return resp.getJSONObject("result").getLong("value");
    }

    private static String getLatestBlockhash() throws Exception {
        JSONObject req = rpc("getLatestBlockhash", new JSONArray());
        JSONObject resp = httpPostJson(SOL_RPC, req);
        return resp.getJSONObject("result").getJSONObject("value").getString("blockhash");
    }

    private static String broadcastSol(byte[] signed) throws Exception {
        String b64 = android.util.Base64.encodeToString(signed, android.util.Base64.NO_WRAP);
        JSONObject req = rpc("sendTransaction", new JSONArray().put(b64)
            .put(new JSONObject().put("encoding", "base64")));
        JSONObject resp = httpPostJson(SOL_RPC, req);
        if (resp.has("error"))
            throw new Exception(resp.getJSONObject("error").optString("message"));
        return resp.getString("result");
    }

    private static JSONObject rpc(String method, JSONArray params) throws Exception {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0"); req.put("id", 1);
        req.put("method", method); req.put("params", params);
        return req;
    }

    // ─ UTILS ──────────────────────────────────────────────────

    private static BigInteger toWeiBnb(double v) {
        // 18 decimals; split to keep precision within long range
        return BigInteger.valueOf((long) (v * 1e12)).multiply(BigInteger.valueOf(1_000_000L));
    }

    private static String weiToBnb(BigInteger wei) {
        return String.format("%.6f", wei.doubleValue() / 1e18);
    }

    private static JSONObject httpPostJson(String url, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", "NanuBot/12");
        c.setDoOutput(true);
        c.setConnectTimeout(12000); c.setReadTimeout(15000);
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder(); String ln;
        while ((ln = r.readLine()) != null) sb.append(ln);
        r.close();
        return new JSONObject(sb.toString());
    }
}
