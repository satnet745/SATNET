package org.servalproject.satnet.pricing;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ExchangeRateManagerTest {

    private ExchangeRateManager exchangeRateManager;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("exchange_rates", Context.MODE_PRIVATE).edit().clear().commit();
        exchangeRateManager = new ExchangeRateManager(context);
    }

    @Test
    public void providerMirrorListIsConfigured() {
        List<String> providerUrls = exchangeRateManager.getProviderUrls();
        assertTrue(providerUrls.size() >= 2);
        for (String providerUrl : providerUrls) {
            assertTrue(providerUrl.startsWith("https://"));
            assertFalse(providerUrl.contains(".invalid"));
        }
    }

    @Test
    public void currencyNormalizationFallsBackToUsd() {
        assertEquals("USD", ExchangeRateManager.normalizeCurrencyCode(null));
        assertEquals("USD", ExchangeRateManager.normalizeCurrencyCode(""));
        assertEquals("KES", ExchangeRateManager.normalizeCurrencyCode("kes"));
        assertEquals("USD", ExchangeRateManager.normalizeCurrencyCode("TOOLONG"));
    }

    @Test
    public void preferredCurrencyCodeIsAlwaysPresent() {
        String preferredCurrencyCode = exchangeRateManager.getPreferredCurrencyCode();
        assertTrue(preferredCurrencyCode != null && preferredCurrencyCode.length() == 3);
    }
}

