/**
 * Copyright (C) 2025 SATNET AFRICA
 *
 * This file is part of SATNET AFRICA (http://satnetafrica.org)
 *
 * SATNET AFRICA is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 */

package org.servalproject.bitcoin.security;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Unit tests for WalletEncryption
 *
 * Tests AES-256-GCM encryption, PIN derivation, and secure memory operations
 */
public class WalletEncryptionTest {

    private WalletEncryption encryption;
    private static final String TEST_PIN = "1234";
    private static final byte[] TEST_WALLET_ID = "test_wallet".getBytes();

    @Before
    public void setUp() throws Exception {
        encryption = new WalletEncryption(null, TEST_PIN);
    }

    @Test
    public void testEncryptionDecryptionRoundTrip() throws Exception {
        byte[] plaintext = "test_seed_data_64_bytes_long_for_bitcoin_wallet_seed_12345".getBytes();

        // Encrypt
        byte[] encrypted = encryption.encrypt(plaintext, TEST_WALLET_ID);

        // Should not be same as plaintext
        assertNotEquals("Encrypted should differ from plaintext",
                       new String(plaintext), new String(encrypted));

        // Decrypt
        byte[] decrypted = encryption.decrypt(encrypted, TEST_WALLET_ID);

        // Should match original
        assertArrayEquals("Decrypted should match plaintext", plaintext, decrypted);

        System.out.println("✓ Encryption/decryption round-trip successful");
    }

    @Test
    public void testInvalidPINRejected() {
        try {
            new WalletEncryption(null, "123"); // Too short
            fail("Should reject PIN < 4 digits");
        } catch (IllegalArgumentException e) {
            System.out.println("✓ Correctly rejected short PIN");
        }

        try {
            new WalletEncryption(null, "123456789"); // Too long
            fail("Should reject PIN > 8 digits");
        } catch (IllegalArgumentException e) {
            System.out.println("✓ Correctly rejected long PIN");
        }
    }

    @Test
    public void testTamperingDetection() throws Exception {
        byte[] plaintext = "original_seed_data".getBytes();

        // Encrypt
        byte[] encrypted = encryption.encrypt(plaintext, TEST_WALLET_ID);

        // Tamper with ciphertext
        if (encrypted.length > 4) {
            encrypted[4] ^= 0xFF; // Flip bits
        }

        // Try to decrypt tampered data
        try {
            encryption.decrypt(encrypted, TEST_WALLET_ID);
            fail("Tampered ciphertext must fail decryption");
        } catch (Exception expected) {
            // Expected: AES-GCM tag verification fails.
        }
    }

    @Test
    public void testDifferentWalletIDsFail() throws Exception {
        byte[] plaintext = "secret_seed".getBytes();
        byte[] walletID1 = "wallet_1".getBytes();
        byte[] walletID2 = "wallet_2".getBytes();

        // Encrypt with wallet ID 1
        byte[] encrypted = encryption.encrypt(plaintext, walletID1);

        // Try to decrypt with wallet ID 2 (different associated data)
        try {
            encryption.decrypt(encrypted, walletID2);
            fail("Decrypt with a different wallet ID must fail");
        } catch (Exception expected) {
            // Expected.
        }
    }

    @Test
    public void testMultipleEncryptionsAreDifferent() throws Exception {
        byte[] plaintext = "same_plaintext".getBytes();

        // Encrypt same plaintext twice
        byte[] encrypted1 = encryption.encrypt(plaintext, TEST_WALLET_ID);
        byte[] encrypted2 = encryption.encrypt(plaintext, TEST_WALLET_ID);

        // Should be different (due to random nonce)
        assertFalse("Same plaintext should encrypt to different ciphertexts",
                Arrays.equals(encrypted1, encrypted2));

        // But both should decrypt to original
        byte[] decrypted1 = encryption.decrypt(encrypted1, TEST_WALLET_ID);
        byte[] decrypted2 = encryption.decrypt(encrypted2, TEST_WALLET_ID);

        assertArrayEquals("Both should decrypt to original", plaintext, decrypted1);
        assertArrayEquals("Both should decrypt to original", plaintext, decrypted2);

        System.out.println("✓ Multiple encryptions are unique (random nonce working)");
    }

    @Test
    public void testLargeDataEncryption() throws Exception {
        // Test with large seed (64 bytes typical)
        byte[] largePlaintext = new byte[64];
        for (int i = 0; i < 64; i++) {
            largePlaintext[i] = (byte)(i % 256);
        }

        // Should handle large data
        byte[] encrypted = encryption.encrypt(largePlaintext, TEST_WALLET_ID);
        byte[] decrypted = encryption.decrypt(encrypted, TEST_WALLET_ID);

        assertArrayEquals("Should handle 64-byte seed", largePlaintext, decrypted);

        System.out.println("✓ Large data (64 bytes) encrypted/decrypted successfully");
    }
}

