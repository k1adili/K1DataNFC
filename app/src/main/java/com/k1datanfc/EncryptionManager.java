package com.k1datanfc;

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

/**
 * AES-256-GCM encryption using Android Keystore.
 * Works on API 23+ with Keystore; falls back to password-based key on lower APIs.
 */
public class EncryptionManager {

    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "K1DataNFC_MasterKey";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String PREFS_NAME = "k1_enc_prefs";
    private static final String PREF_FALLBACK_KEY = "fallback_key";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final Context context;

    public EncryptionManager(Context context) {
        this.context = context.getApplicationContext();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            ensureKeyExists();
        }
    }

    private void ensureKeyExists() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return;
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
                keyGenerator.init(new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
                keyGenerator.generateKey();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private SecretKey getKey() throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        } else {
            return getFallbackKey();
        }
    }

    private SecretKey getFallbackKey() throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedKey = prefs.getString(PREF_FALLBACK_KEY, null);
        if (savedKey == null) {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey key = keyGen.generateKey();
            prefs.edit().putString(PREF_FALLBACK_KEY,
                    Base64.encodeToString(key.getEncoded(), Base64.DEFAULT)).apply();
            return key;
        }
        byte[] keyBytes = Base64.decode(savedKey, Base64.DEFAULT);
        return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            // Combine IV + ciphertext
            byte[] combined = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, GCM_IV_LENGTH, ciphertext.length);
            return Base64.encodeToString(combined, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String decrypt(String encryptedText) {
        try {
            byte[] combined = Base64.decode(encryptedText, Base64.DEFAULT);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(ciphertext);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public byte[] encryptBytes(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(data);
            byte[] combined = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, GCM_IV_LENGTH, ciphertext.length);
            return combined;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public byte[] decryptBytes(byte[] encryptedData) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[encryptedData.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // PORTABLE (password-based) encryption for backups.
    //
    // The Keystore key above never leaves the device, so a ZIP made on one
    // install can't be opened on another (or after the app is reinstalled).
    // For backups we instead derive a key from a user-chosen password with
    // PBKDF2, so the same password can decrypt the backup anywhere.
    // ---------------------------------------------------------------------

    private static final String PBKDF2_ALGO = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH_BITS = 256;

    private SecretKey deriveKeyFromPassword(String password, byte[] salt) throws Exception {
        javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance(PBKDF2_ALGO);
        java.security.spec.KeySpec spec = new javax.crypto.spec.PBEKeySpec(
                password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts data with a key derived from the given password.
     * Output layout: [salt(16)] [iv(12)] [ciphertext+tag]
     * The salt is stored alongside the data (this is standard practice —
     * the salt does not need to be secret, only the password does).
     */
    public byte[] encryptWithPassword(byte[] data, String password) {
        try {
            byte[] salt = new byte[SALT_LENGTH];
            new java.security.SecureRandom().nextBytes(salt);
            SecretKey key = deriveKeyFromPassword(password, salt);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(data);

            byte[] combined = new byte[SALT_LENGTH + GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(salt, 0, combined, 0, SALT_LENGTH);
            System.arraycopy(iv, 0, combined, SALT_LENGTH, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, SALT_LENGTH + GCM_IV_LENGTH, ciphertext.length);
            return combined;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Decrypts data produced by encryptWithPassword(). Returns null if the
     * password is wrong or the data is corrupted (GCM authentication fails).
     */
    public byte[] decryptWithPassword(byte[] encryptedData, String password) {
        try {
            byte[] salt = new byte[SALT_LENGTH];
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[encryptedData.length - SALT_LENGTH - GCM_IV_LENGTH];
            System.arraycopy(encryptedData, 0, salt, 0, SALT_LENGTH);
            System.arraycopy(encryptedData, SALT_LENGTH, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, SALT_LENGTH + GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            SecretKey key = deriveKeyFromPassword(password, salt);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            // Wrong password or corrupted data - this is expected, don't log as error
            return null;
        }
    }
}
