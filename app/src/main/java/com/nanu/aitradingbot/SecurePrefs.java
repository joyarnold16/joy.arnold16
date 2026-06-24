package com.nanu.aitradingbot;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.web3j.crypto.*;

public class SecurePrefs {
    private static final String KEYSTORE  = "AndroidKeyStore";
    private static final String KEY_ALIAS = "nanu_wallet_key";
    private static final String PREFS     = "nanu_secure";
    private static final String KEY_MNEM  = "enc_mnemonic";
    private static final String KEY_IV    = "enc_iv";
    private static final int    GCM_LEN   = 128;

    private final SharedPreferences prefs;

    public SecurePrefs(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureKey();
    }

    private void ensureKey() {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            if (!ks.containsAlias(KEY_ALIAS)) {
                KeyGenerator kg = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
                kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
                kg.generateKey();
            }
        } catch (Exception e) { throw new RuntimeException("Keystore init failed", e); }
    }

    public void saveMnemonic(String mnemonic) {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            SecretKey key = (SecretKey) ks.getKey(KEY_ALIAS, null);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] enc = cipher.doFinal(mnemonic.getBytes(StandardCharsets.UTF_8));
            prefs.edit()
                .putString(KEY_MNEM, Base64.encodeToString(enc, Base64.DEFAULT))
                .putString(KEY_IV,   Base64.encodeToString(cipher.getIV(), Base64.DEFAULT))
                .apply();
        } catch (Exception e) { throw new RuntimeException("Save mnemonic failed", e); }
    }

    public String loadMnemonic() {
        try {
            String encB64 = prefs.getString(KEY_MNEM, null);
            String ivB64  = prefs.getString(KEY_IV,   null);
            if (encB64 == null || ivB64 == null) return null;
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            SecretKey key = (SecretKey) ks.getKey(KEY_ALIAS, null);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(GCM_LEN, Base64.decode(ivB64, Base64.DEFAULT)));
            return new String(cipher.doFinal(Base64.decode(encB64, Base64.DEFAULT)),
                StandardCharsets.UTF_8);
        } catch (Exception e) { return null; }
    }

    public boolean hasMnemonic() { return prefs.contains(KEY_MNEM); }

    // BNB = Ethereum BIP44 path m/44'/60'/0'/0/0
    public String deriveBnbAddress(String mnemonic) {
        try {
            byte[] seed = MnemonicUtils.generateSeed(mnemonic, "");
            Bip32ECKeyPair master = Bip32ECKeyPair.generateKeyPair(seed);
            int[] path = {
                44  | Bip32ECKeyPair.HARDENED_BIT,
                60  | Bip32ECKeyPair.HARDENED_BIT,
                0   | Bip32ECKeyPair.HARDENED_BIT,
                0, 0
            };
            return Credentials.create(Bip32ECKeyPair.deriveKeyPair(master, path)).getAddress();
        } catch (Exception e) { return ""; }
    }

    // SOL = SLIP-0010 ed25519 BIP44 path m/44'/501'/0'/0'
    public String deriveSolAddress(String mnemonic) {
        try {
            byte[] seed    = MnemonicUtils.generateSeed(mnemonic, "");
            byte[] privKey = slip10(seed, new int[]{44, 501, 0, 0});
            net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec spec =
                new net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec(
                    privKey,
                    net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable.getByName("Ed25519"));
            net.i2p.crypto.eddsa.EdDSAPrivateKey pk =
                new net.i2p.crypto.eddsa.EdDSAPrivateKey(spec);
            return Base58.encode(pk.getAbyte());
        } catch (Exception e) { return ""; }
    }

    // SLIP-0010 hardened ed25519 derivation
    static byte[] slip10(byte[] seed, int[] path) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
        mac.init(new javax.crypto.spec.SecretKeySpec(
            "ed25519 seed".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        byte[] il_ir = mac.doFinal(seed);
        byte[] key   = Arrays.copyOfRange(il_ir, 0, 32);
        byte[] chain = Arrays.copyOfRange(il_ir, 32, 64);
        for (int idx : path) {
            int h = idx | 0x80000000;
            mac.init(new javax.crypto.spec.SecretKeySpec(chain, "HmacSHA512"));
            byte[] data = new byte[37];
            data[0] = 0;
            System.arraycopy(key, 0, data, 1, 32);
            data[33] = (byte)(h >>> 24); data[34] = (byte)(h >>> 16);
            data[35] = (byte)(h >>> 8);  data[36] = (byte)h;
            il_ir = mac.doFinal(data);
            key   = Arrays.copyOfRange(il_ir, 0, 32);
            chain = Arrays.copyOfRange(il_ir, 32, 64);
        }
        return key;
    }

    public static String generateMnemonic() {
        try {
            byte[] entropy = new byte[16];
            new SecureRandom().nextBytes(entropy);
            return MnemonicUtils.generateMnemonic(entropy);
        } catch (Exception e) { return ""; }
    }
}
