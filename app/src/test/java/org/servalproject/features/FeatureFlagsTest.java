package org.servalproject.features;

import org.junit.Test;
import org.servalproject.satnet.SatnetRuntimeConfig;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FeatureFlagsTest {

    @Test
    public void lightningIsEnabledByDefaultInProjectBuildConfig() {
        assertTrue(FeatureFlags.isLightningEnabled());
    }

    @Test
    public void relayIsEnabledByDefaultInProjectBuildConfig() {
        assertTrue(FeatureFlags.isRelayEnabled());
    }

    @Test
    public void experimentalRoutingIsEnabledByDefaultInProjectBuildConfig() {
        assertTrue(FeatureFlags.isExperimentalRoutingEnabled());
    }

    @Test
    public void satnetPolicyDefaultsRemainNonCustodialAndAntiRentSeeking() {
        assertFalse(FeatureFlags.isSatnetCustodialModeAllowed());
        assertFalse(FeatureFlags.isSatnetMandatoryProtocolFeeAllowed());
        assertFalse(FeatureFlags.isSatnetSurveillanceMonetizationAllowed());
        assertTrue(FeatureFlags.requiresSatnetTestnetByDefault());
    }

    @Test
    public void pilotStageDoesNotPermitMainnetByDefault() {
        assertTrue(FeatureFlags.isSatnetPilotStage());
        assertFalse(FeatureFlags.isSatnetMainnetSettlementEnabled());
        assertFalse(FeatureFlags.isSatnetMainnetPermittedByStage());
    }

    @Test
    public void liveExchangeRatesAreEnabledByDefault() {
        assertTrue(FeatureFlags.isSatnetLiveExchangeRatesEnabled());
        assertTrue(SatnetRuntimeConfig.allowLiveExchangeRateFetch());
    }

    @Test
    public void exchangeRateProvidersUseConfiguredHttpsMirrors() {
        String providers = FeatureFlags.getSatnetExchangeRateProviderUrls();
        assertTrue(providers.contains("https://api.coinbase.com/"));
        assertTrue(providers.contains("https://api.coingecko.com/"));
        assertFalse(providers.contains(".invalid"));
    }
}

