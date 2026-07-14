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

package org.servalproject.bitcoin;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for BitcoinWallet
 *
 * Tests BIP39 mnemonic generation, validation, and HD wallet operations
 */
@RunWith(RobolectricTestRunner.class)
public class BitcoinWalletTest {

    private Context context;
    private BitcoinWallet wallet;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        wallet = new BitcoinWallet(context, "test_wallet", true);
    }

    @Test
    public void testMnemonicGeneration() throws Exception {
        List<String> mnemonic = wallet.generateNewMnemonic();
        assertNotNull(mnemonic);
        assertEquals(12, mnemonic.size());
        for (String word : mnemonic) {
            assertNotNull(word);
            assertFalse(word.trim().isEmpty());
        }
    }

    @Test
    public void testMnemonicImportInitializesWallet() throws Exception {
        String validMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon about";

        wallet.importFromMnemonic(validMnemonic, "1234");
        assertTrue(wallet.isInitialized());
        assertNotNull(wallet.getDerivedAddress(0));
    }

    @Test
    public void testInvalidMnemonicRejected() {
        String invalidMnemonic = "invalid invalid invalid invalid invalid invalid " +
                "invalid invalid invalid invalid invalid invalid";

        try {
            wallet.importFromMnemonic(invalidMnemonic, "1234");
            fail("Invalid mnemonic should fail import");
        } catch (Exception expected) {
            assertTrue(true);
        }
    }

    @Test
    public void testDeterministicRecoveryAcrossWalletInstances() throws Exception {
        String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon about";

        BitcoinWallet wallet1 = new BitcoinWallet(context, "wallet_1", true);
        BitcoinWallet wallet2 = new BitcoinWallet(context, "wallet_2", true);

        wallet1.importFromMnemonic(mnemonic, "1234");
        wallet2.importFromMnemonic(mnemonic, "1234");

        assertEquals(wallet1.getDerivedAddress(0), wallet2.getDerivedAddress(0));
    }

    @Test
    public void testIndexedAddressesAreDeterministicAndDistinctAcrossReload() throws Exception {
        String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon about";

        wallet.importFromMnemonic(mnemonic, "1234");

        String firstAddress = wallet.getDerivedAddress(0);
        String secondAddress = wallet.getDerivedAddress(1);
        String thirdAddress = wallet.getDerivedAddress(2);

        assertNotNull(firstAddress);
        assertNotNull(secondAddress);
        assertNotNull(thirdAddress);
        assertNotEquals(firstAddress, secondAddress);
        assertNotEquals(secondAddress, thirdAddress);
        assertNotEquals(firstAddress, thirdAddress);

        BitcoinWallet reopenedWallet = new BitcoinWallet(context, "test_wallet", true);
        reopenedWallet.loadEncryptedSeed("1234");

        assertEquals(firstAddress, reopenedWallet.getDerivedAddress(0));
        assertEquals(secondAddress, reopenedWallet.getDerivedAddress(1));
        assertEquals(thirdAddress, reopenedWallet.getDerivedAddress(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeDerivedAddressIndexRejected() {
        wallet.getDerivedAddress(-1);
    }

    @Test
    public void testWalletWipe() throws Exception {
        String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon about";
        wallet.importFromMnemonic(mnemonic, "1234");
        assertTrue(wallet.isInitialized());

        wallet.wipe();
        assertFalse(wallet.isInitialized());
    }

    @Test
    public void testReloadEncryptedSeedWithPin() throws Exception {
        String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon about";
        wallet.importFromMnemonic(mnemonic, "1234");

        BitcoinWallet reopenedWallet = new BitcoinWallet(context, "test_wallet", true);
        reopenedWallet.loadEncryptedSeed("1234");

        assertTrue(reopenedWallet.isInitialized());
        assertNotNull(reopenedWallet.getDerivedAddress(0));
    }

    @Test
    public void testClearSensitiveMemoryKeepsStoredSeed() throws Exception {
        String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon about";
        wallet.importFromMnemonic(mnemonic, "1234");
        assertTrue(wallet.hasStoredSeed());

        wallet.clearSensitiveMemory();

        assertFalse(wallet.isInitialized());
        assertTrue(wallet.hasStoredSeed());

        BitcoinWallet reopenedWallet = new BitcoinWallet(context, "test_wallet", true);
        reopenedWallet.loadEncryptedSeed("1234");
        assertTrue(reopenedWallet.isInitialized());
    }

    @Test
    public void testWalletDefaultsToTestnetUnderSafeSatnetPolicy() {
        BitcoinWallet defaultWallet = new BitcoinWallet(context, "policy_default_wallet");
        assertTrue(defaultWallet.isTestnet());
    }
}
