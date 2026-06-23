package com.nanu.aitradingbot;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecurePrefs {
    private static final String KEYSTORE   = "AndroidKeyStore";
    private static final String KEY_ALIAS  = "nanu_wallet_key";
    private static final String PREFS_NAME = "nanu_secure";
    private static final String KEY_MNEM   = "enc_mnemonic";
    private static final String KEY_IV     = "enc_iv";
    private static final int    GCM_LEN    = 128;

    private final SharedPreferences prefs;

    public SecurePrefs(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        ensureKey();
    }

    private void ensureKey() {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            if (!ks.containsAlias(KEY_ALIAS)) {
                KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
                kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
                kg.generateKey();
            }
        } catch (Exception e) {
            throw new RuntimeException("Keystore init failed", e);
        }
    }

    public void saveMnemonic(String mnemonic) {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            SecretKey key = (SecretKey) ks.getKey(KEY_ALIAS, null);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv  = cipher.getIV();
            byte[] enc = cipher.doFinal(mnemonic.getBytes(StandardCharsets.UTF_8));
            prefs.edit()
                .putString(KEY_MNEM, Base64.encodeToString(enc, Base64.DEFAULT))
                .putString(KEY_IV,   Base64.encodeToString(iv,  Base64.DEFAULT))
                .apply();
        } catch (Exception e) {
            throw new RuntimeException("Save mnemonic failed", e);
        }
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
            byte[] dec = cipher.doFinal(Base64.decode(encB64, Base64.DEFAULT));
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hasMnemonic() {
        return prefs.contains(KEY_MNEM);
    }

    public String deriveBnbAddress(String mnemonic) {
        try {
            wallet.sdk.core.HDWallet w = new wallet.sdk.core.HDWallet(mnemonic, "");
            return w.getAddressForCoin(wallet.sdk.core.CoinType.SMARTCHAIN);
        } catch (Exception e) { return ""; }
    }

    public String deriveSolAddress(String mnemonic) {
        try {
            wallet.sdk.core.HDWallet w = new wallet.sdk.core.HDWallet(mnemonic, "");
            return w.getAddressForCoin(wallet.sdk.core.CoinType.SOLANA);
        } catch (Exception e) { return ""; }
    }

    public static String generateMnemonic() {
        try {
            wallet.sdk.core.HDWallet w = new wallet.sdk.core.HDWallet(128, "");
            return w.mnemonic();
        } catch (Exception e) { return ""; }
    }
}
