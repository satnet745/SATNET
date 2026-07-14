package org.servalproject.satnet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WalletSessionStoreTest {

    @Test
    public void refreshSessionRotatesTokenAndInvalidatesOldOne() {
        String token = WalletSessionStore.createSession("1234");

        WalletSessionStore.SessionAccess sessionAccess = WalletSessionStore.refreshSession(token);
        assertNotNull(sessionAccess);
        assertNotNull(sessionAccess.token);
        assertFalse(token.equals(sessionAccess.token));
        assertNull(WalletSessionStore.getPin(token));

        char[] pinChars = sessionAccess.consumePinChars();
        try {
            assertEquals("1234", new String(pinChars));
        } finally {
            org.servalproject.bitcoin.security.WalletEncryption.clearChars(pinChars);
            sessionAccess.close();
        }

        assertEquals("1234", WalletSessionStore.getPin(sessionAccess.token));
        WalletSessionStore.invalidate(sessionAccess.token);
        assertNull(WalletSessionStore.getPin(sessionAccess.token));
    }

    @Test
    public void getPinCharsReturnsIndependentCopy() {
        String token = WalletSessionStore.createSession("5678");

        char[] first = WalletSessionStore.getPinChars(token);
        char[] second = WalletSessionStore.getPinChars(token);
        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first != second);

        try {
            first[0] = '0';
            assertEquals("0678", new String(first));
            assertEquals("5678", new String(second));
            assertEquals("5678", WalletSessionStore.getPin(token));
        } finally {
            org.servalproject.bitcoin.security.WalletEncryption.clearChars(first);
            org.servalproject.bitcoin.security.WalletEncryption.clearChars(second);
            WalletSessionStore.invalidate(token);
        }
    }
}

