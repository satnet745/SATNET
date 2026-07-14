/**
 * Copyright (C) 2025 SATNET AFRICA
 *
 * This file is part of SATNET AFRICA (http://satnetafrica.org)
 */

package org.servalproject.satnet.integration;

import org.junit.Before;
import org.junit.Test;
import org.servalproject.bitcoin.security.WalletEncryption;

import static org.junit.Assert.*;

/**
 * Integration Test: Encryption Workflow
 *
 * Tests complete encryption scenario: PIN → derive key → encrypt seed → decrypt
 */
public class EncryptionIntegrationTest {

    private WalletEncryption encryption;
    private static final String TEST_PIN = "1234";
    private static final byte[] TEST_WALLET_ID = "user_wallet_001".getBytes();

    @Before
    public void setUp() {
        encryption = new WalletEncryption(null, TEST_PIN);
    }

    @Test
    public void testCompleteEncryptionWorkflow() throws Exception {
        // Step 1: User has a 64-byte seed (typical Bitcoin wallet)
        byte[] originalSeed = new byte[64];
        for (int i = 0; i < 64; i++) {
            originalSeed[i] = (byte)(i % 256);
        }

        // Step 2: Encrypt seed with PIN
        byte[] encryptedSeed = encryption.encrypt(originalSeed, TEST_WALLET_ID);
        assertNotNull("Encrypted seed should exist", encryptedSeed);
        assertNotEquals("Encrypted should differ from plaintext",
                       new String(originalSeed), new String(encryptedSeed));

        // Step 3: Store encrypted seed (simulates local storage)
        // In real app, this would be saved to disk

        // Step 4: Later, user enters PIN to decrypt
        byte[] decryptedSeed = encryption.decrypt(encryptedSeed, TEST_WALLET_ID);

        // Step 5: Verify decrypted matches original
        assertArrayEquals("Decrypted should match original seed", originalSeed, decryptedSeed);

        System.out.println("✓ Complete encryption workflow: seed → encrypt → store → decrypt");
        System.out.println("  Seed size: " + originalSeed.length + " bytes");
        System.out.println("  Encrypted size: " + encryptedSeed.length + " bytes");
        System.out.println("  Decrypted correctly: ✓");
    }

    @Test
    public void testMultipleDevicesWithSamePIN() throws Exception {
        byte[] seed = "master_seed_64_byte_bitcoin_wallet_backup_data_here_123456".getBytes();

        // Device 1: Encrypt
        WalletEncryption device1 = new WalletEncryption(null, TEST_PIN);
        byte[] encrypted1 = device1.encrypt(seed, TEST_WALLET_ID);

        // Device 2: Decrypt (simulates recovery on different device)
        WalletEncryption device2 = new WalletEncryption(null, TEST_PIN);
        byte[] decrypted2 = device2.decrypt(encrypted1, TEST_WALLET_ID);

        // Should recover original seed
        assertArrayEquals("Different device should decrypt same seed", seed, decrypted2);

        System.out.println("✓ Same PIN enables recovery on different device");
        System.out.println("  Encrypted on Device 1");
        System.out.println("  Decrypted on Device 2");
        System.out.println("  PIN-based derivation verified");
    }

    @Test
    public void testTamperingDetection() throws Exception {
        byte[] seed = "sensitive_bitcoin_seed_data_backup".getBytes();

        // Encrypt
        byte[] encrypted = encryption.encrypt(seed, TEST_WALLET_ID);

        // Simulate tampering (flip a bit)
        if (encrypted.length > 10) {
            encrypted[10] ^= 0x01; // Flip 1 bit
        }

        // Try to decrypt tampered data
        try {
            encryption.decrypt(encrypted, TEST_WALLET_ID);
            fail("Tampered encrypted data must fail decryption");
        } catch (Exception expected) {
            // Expected.
        }

        // If no exception, GCM auth should have failed
        // (In production, this would always throw)
        System.out.println("✓ Tampering detection implemented");
        System.out.println("  AES-GCM authenticates ciphertext");
        System.out.println("  Even 1-bit changes detected");
    }

    @Test
    public void testDifferentPINsRequired() {
        // First PIN
        try {
            WalletEncryption enc1 = new WalletEncryption(null, "1111");
            byte[] seed = "test_seed".getBytes();
            byte[] encrypted1 = enc1.encrypt(seed, TEST_WALLET_ID);

            // Wrong PIN for decryption
            WalletEncryption enc2 = new WalletEncryption(null, "2222");
            try {
                enc2.decrypt(encrypted1, TEST_WALLET_ID);
                fail("Different PIN should fail decryption");
            } catch (Exception expected) {
                // Expected.
            }
        } catch (Exception e) {
            fail("Unexpected failure preparing PIN mismatch test: " + e.getMessage());
        }
    }

    @Test
    public void testLargeSeedEncryption() throws Exception {
        // Test with multiple large seeds (full wallet backup)
        byte[] largeSeed = new byte[256]; // 256-byte seed (oversized but valid test)
        for (int i = 0; i < 256; i++) {
            largeSeed[i] = (byte)(i % 256);
        }

        // Encrypt and decrypt
        byte[] encrypted = encryption.encrypt(largeSeed, TEST_WALLET_ID);
        byte[] decrypted = encryption.decrypt(encrypted, TEST_WALLET_ID);

        assertArrayEquals("Large seed should encrypt/decrypt correctly", largeSeed, decrypted);

        System.out.println("✓ Large seeds encrypted/decrypted successfully");
        System.out.println("  Tested with 256-byte seed");
        System.out.println("  AES-256-GCM handles large data");
    }
}

