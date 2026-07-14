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

package org.servalproject.satnet.integration;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.servalproject.bitcoin.BitcoinWallet;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class WalletIntegrationTest {

    private Context context;
    private BitcoinWallet wallet;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        wallet = new BitcoinWallet(context, "integration_test", true);
    }

    @Test
    public void testCompleteWalletCreationFlow() throws Exception {
        List<String> mnemonic = wallet.generateNewMnemonic();
        assertNotNull(mnemonic);
        assertEquals(12, mnemonic.size());

        String mnemonicString = String.join(" ", mnemonic);
        wallet.importFromMnemonic(mnemonicString, "5678");
        assertTrue(wallet.isInitialized());

        String walletAddress = wallet.getDerivedAddress(0);
        assertNotNull(walletAddress);
        assertTrue(walletAddress.length() > 20);
    }

    @Test
    public void testDeterministicRecovery() throws Exception {
        String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon about";

        wallet.importFromMnemonic(mnemonic, "1234");
        String address1 = wallet.getDerivedAddress(0);
        String address2 = wallet.getDerivedAddress(1);

        BitcoinWallet wallet2 = new BitcoinWallet(context, "recovery_test", true);
        wallet2.importFromMnemonic(mnemonic, "1234");
        String recoveredAddress1 = wallet2.getDerivedAddress(0);
        String recoveredAddress2 = wallet2.getDerivedAddress(1);

        assertEquals(address1, recoveredAddress1);
        assertEquals(address2, recoveredAddress2);
        assertNotEquals(address1, address2);
    }

    @Test
    public void testWalletSecurityBoundaries() throws Exception {
        String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon about";
        wallet.importFromMnemonic(mnemonic, "4321");
        assertTrue(wallet.isInitialized());

        wallet.wipe();
        assertFalse(wallet.isInitialized());
    }
}
