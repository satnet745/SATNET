/**
 * Copyright (C) 2025 SATNET AFRICA
 *
 * This file is part of SATNET AFRICA (http://satnetafrica.org)
 *
 * SATNET AFRICA is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.servalproject.bitcoin.security;

import android.content.Context;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Wallet Seed Encryption for SATNET AFRICA
 *
 * Features:
 * - AES-256-GCM encryption for seed storage
 * - PBKDF2 key derivation from password
 * - Secure random IV generation
 * - PIN-based fallback encryption
 */
public class WalletEncryption {
    private static final String TAG = "WalletEncryption";

    private static final int PBKDF2_ITERATIONS = 210000;
    private static final int PBKDF2_KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 32;
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static final String ENVELOPE_PREFIX = "v1";

    private final String pin;

    public WalletEncryption() {
        this.pin = null;
    }

    // Backward-compatible API used by current tests.
    public WalletEncryption(Context context, String pin) {
        if (pin == null || pin.length() < 4 || pin.length() > 8) {
            throw new IllegalArgumentException("PIN must be 4-8 characters");
        }
        this.pin = pin;
    }

    public byte[] encrypt(byte[] plaintext, byte[] walletId) throws GeneralSecurityException {
        if (pin == null) {
            throw new GeneralSecurityException("PIN-based constructor required for instance encrypt()");
        }
        EncryptedSeed encryptedSeed = encryptSeed(plaintext, derivePassword(pin, walletId));
        String envelope = ENVELOPE_PREFIX + ":" + encryptedSeed.salt + ":" + encryptedSeed.iv + ":" + encryptedSeed.ciphertext;
        return envelope.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] decrypt(byte[] envelopeBytes, byte[] walletId) throws GeneralSecurityException {
        if (pin == null) {
            throw new GeneralSecurityException("PIN-based constructor required for instance decrypt()");
        }
        String envelope = new String(envelopeBytes, StandardCharsets.UTF_8);
        String[] parts = envelope.split(":", 4);
        if (parts.length != 4 || !ENVELOPE_PREFIX.equals(parts[0])) {
            throw new GeneralSecurityException("Unsupported encrypted payload format");
        }
        EncryptedSeed encryptedSeed = new EncryptedSeed(parts[3], parts[1], parts[2]);
        return decryptSeed(encryptedSeed, derivePassword(pin, walletId));
    }

    public static EncryptedSeed encryptSeed(byte[] seed, String password) throws GeneralSecurityException {
        char[] passwordChars = password == null ? null : password.toCharArray();
        try {
            return encryptSeed(seed, passwordChars);
        } finally {
            clearChars(passwordChars);
        }
    }

    public static EncryptedSeed encryptSeed(byte[] seed, char[] passwordChars) throws GeneralSecurityException {
        if (seed == null || seed.length == 0) {
            throw new GeneralSecurityException("Seed cannot be empty");
        }
        if (passwordChars == null || passwordChars.length == 0) {
            throw new GeneralSecurityException("Password is required for seed encryption");
        }

        byte[] salt = generateRandomBytes(SALT_LENGTH);
        byte[] key = derivePBKDF2(passwordChars, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH / 8);
        byte[] iv = generateRandomBytes(IV_LENGTH);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(seed);
            return new EncryptedSeed(
                    encodeBase64(ciphertext),
                    encodeBase64(salt),
                    encodeBase64(iv)
            );
        } catch (Exception e) {
            Log.e(TAG, "Encryption error", e);
            throw new GeneralSecurityException("Encryption failed", e);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    public static byte[] decryptSeed(EncryptedSeed encryptedSeed, String password) throws GeneralSecurityException {
        char[] passwordChars = password == null ? null : password.toCharArray();
        try {
            return decryptSeed(encryptedSeed, passwordChars);
        } finally {
            clearChars(passwordChars);
        }
    }

    public static byte[] decryptSeed(EncryptedSeed encryptedSeed, char[] passwordChars) throws GeneralSecurityException {
        if (encryptedSeed == null) {
            throw new GeneralSecurityException("Encrypted seed is required");
        }
        if (passwordChars == null || passwordChars.length == 0) {
            throw new GeneralSecurityException("Password is required for seed decryption");
        }

        byte[] salt = decodeBase64(encryptedSeed.salt, "salt");
        byte[] iv = decodeBase64(encryptedSeed.iv, "iv");
        byte[] ciphertext = decodeBase64(encryptedSeed.ciphertext, "ciphertext");
        byte[] key = derivePBKDF2(passwordChars, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH / 8);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            Log.e(TAG, "Decryption error", e);
            throw new GeneralSecurityException("Decryption failed", e);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static String derivePassword(String pin, byte[] walletId) {
        String aad = walletId == null ? "" : encodeBase64(walletId);
        return pin + ":" + aad;
    }

    private static String encodeBase64(byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot encode null value");
        }
        String encoded = tryJavaBase64Encode(value);
        if (encoded == null) {
            encoded = tryAndroidBase64Encode(value);
        }
        if (encoded == null) {
            throw new IllegalStateException("Base64 encode returned null");
        }
        return encoded.trim();
    }

    private static byte[] decodeBase64(String value, String field) throws GeneralSecurityException {
        if (value == null || value.trim().isEmpty()) {
            throw new GeneralSecurityException("Encrypted payload missing " + field);
        }
        byte[] decoded;
        try {
            decoded = tryJavaBase64Decode(value.trim());
            if (decoded == null) {
                decoded = tryAndroidBase64Decode(value.trim());
            }
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Invalid base64 " + field, e);
        }
        if (decoded == null || decoded.length == 0) {
            throw new GeneralSecurityException("Invalid base64 " + field);
        }
        return decoded;
    }

    private static byte[] derivePBKDF2(String password, byte[] salt, int iterations, int keyLengthBytes)
            throws GeneralSecurityException {
        return derivePBKDF2(password == null ? null : password.toCharArray(), salt, iterations, keyLengthBytes);
    }

    private static byte[] derivePBKDF2(char[] passwordChars, byte[] salt, int iterations, int keyLengthBytes)
            throws GeneralSecurityException {
        if (passwordChars == null || passwordChars.length == 0) {
            throw new GeneralSecurityException("Password is required for key derivation");
        }
        PBEKeySpec spec = new PBEKeySpec(passwordChars, salt, iterations, keyLengthBytes * 8);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return skf.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    public static char[] buildCompositeSecret(char[] pinChars, byte[] walletId, byte[] deviceBindingSecret) {
        if (pinChars == null || pinChars.length == 0) {
            throw new IllegalArgumentException("PIN is required for wallet protection");
        }
        char[] walletChars = encodeBase64(walletId == null ? new byte[0] : walletId).toCharArray();
        char[] bindingChars = encodeBase64(deviceBindingSecret == null ? new byte[0] : deviceBindingSecret).toCharArray();
        char[] composite = new char[pinChars.length + walletChars.length + bindingChars.length + 2];
        int offset = 0;
        System.arraycopy(pinChars, 0, composite, offset, pinChars.length);
        offset += pinChars.length;
        composite[offset++] = ':';
        System.arraycopy(walletChars, 0, composite, offset, walletChars.length);
        offset += walletChars.length;
        composite[offset++] = ':';
        System.arraycopy(bindingChars, 0, composite, offset, bindingChars.length);
        clearChars(walletChars);
        clearChars(bindingChars);
        return composite;
    }

    public static void clearChars(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    private static byte[] generateRandomBytes(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    private static String tryJavaBase64Encode(byte[] value) {
        try {
            Class<?> base64Class = Class.forName("java.util.Base64");
            Method getEncoder = base64Class.getMethod("getEncoder");
            Object encoder = getEncoder.invoke(null);
            Method encodeToString = encoder.getClass().getMethod("encodeToString", byte[].class);
            return (String) encodeToString.invoke(encoder, value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] tryJavaBase64Decode(String value) {
        try {
            Class<?> base64Class = Class.forName("java.util.Base64");
            Method getDecoder = base64Class.getMethod("getDecoder");
            Object decoder = getDecoder.invoke(null);
            Method decode = decoder.getClass().getMethod("decode", String.class);
            return (byte[]) decode.invoke(decoder, value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String tryAndroidBase64Encode(byte[] value) {
        try {
            Class<?> base64Class = Class.forName("android.util.Base64");
            int noWrap = base64Class.getField("NO_WRAP").getInt(null);
            Method encodeToString = base64Class.getMethod("encodeToString", byte[].class, int.class);
            return (String) encodeToString.invoke(null, value, noWrap);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] tryAndroidBase64Decode(String value) {
        try {
            Class<?> base64Class = Class.forName("android.util.Base64");
            int defaultFlags = base64Class.getField("DEFAULT").getInt(null);
            Method decode = base64Class.getMethod("decode", String.class, int.class);
            return (byte[]) decode.invoke(null, value, defaultFlags);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static class EncryptedSeed {
        public String ciphertext;
        public String salt;
        public String iv;

        public EncryptedSeed(String ciphertext, String salt, String iv) {
            this.ciphertext = ciphertext;
            this.salt = salt;
            this.iv = iv;
        }
    }
}
