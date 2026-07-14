package org.servalproject.features;

import org.servalproject.BuildConfig;

public final class FeatureFlags {
    public static final String STAGE_PILOT = "pilot";
    public static final String STAGE_COUNTRY_BETA = "country-beta";
    public static final String STAGE_GLOBAL = "global";

    private FeatureFlags() {
    }

    public static boolean isLightningEnabled() {
        return BuildConfig.FEATURE_LIGHTNING_ENABLED;
    }

    public static boolean isRelayEnabled() {
        return BuildConfig.FEATURE_RELAY_ENABLED;
    }

    public static boolean isExperimentalRoutingEnabled() {
        return BuildConfig.FEATURE_EXPERIMENTAL_ROUTING_ENABLED;
    }

    public static String getSatnetDeploymentStage() {
        return BuildConfig.SATNET_DEPLOYMENT_STAGE;
    }

    public static String getSatnetSettlementNetwork() {
        return BuildConfig.SATNET_SETTLEMENT_NETWORK;
    }

    public static String getSatnetRelayDirectoryUrl() {
        return BuildConfig.SATNET_RELAY_DIRECTORY_URL;
    }

    public static String getSatnetExchangeRateBaseUrl() {
        return BuildConfig.SATNET_EXCHANGE_RATE_BASE_URL;
    }

    public static boolean isSatnetLiveExchangeRatesEnabled() {
        return BuildConfig.SATNET_LIVE_EXCHANGE_RATES_ENABLED;
    }

    public static String getSatnetExchangeRateProviderUrls() {
        return BuildConfig.SATNET_EXCHANGE_RATE_PROVIDER_URLS;
    }

    public static String getSatnetComplianceProfile() {
        return BuildConfig.SATNET_COMPLIANCE_PROFILE;
    }

    public static boolean isSatnetRemoteKillSwitchEnabled() {
        return BuildConfig.SATNET_REMOTE_KILL_SWITCH_ENABLED;
    }

    public static boolean isSatnetAutoRhizomeApkImportEnabled() {
        return BuildConfig.SATNET_AUTO_RHIZOME_APK_IMPORT_ENABLED;
    }

    public static boolean isSatnetCustodialModeAllowed() {
        return BuildConfig.SATNET_ALLOW_CUSTODIAL_MODE;
    }

    public static boolean isSatnetMandatoryProtocolFeeAllowed() {
        return BuildConfig.SATNET_ALLOW_PROTOCOL_FEES;
    }

    public static boolean isSatnetSurveillanceMonetizationAllowed() {
        return BuildConfig.SATNET_ALLOW_SURVEILLANCE_MONETIZATION;
    }

    public static boolean requiresSatnetTestnetByDefault() {
        return BuildConfig.SATNET_REQUIRE_TESTNET_BY_DEFAULT;
    }

    public static boolean isSatnetMainnetExplicitOverrideEnabled() {
        return BuildConfig.SATNET_MAINNET_EXPLICIT_OVERRIDE;
    }

    public static boolean isSatnetPilotStage() {
        return STAGE_PILOT.equalsIgnoreCase(getSatnetDeploymentStage());
    }

    public static boolean isSatnetCountryBetaStage() {
        return STAGE_COUNTRY_BETA.equalsIgnoreCase(getSatnetDeploymentStage());
    }

    public static boolean isSatnetGlobalStage() {
        return STAGE_GLOBAL.equalsIgnoreCase(getSatnetDeploymentStage());
    }

    public static boolean isSatnetMainnetSettlementEnabled() {
        return "mainnet".equalsIgnoreCase(getSatnetSettlementNetwork());
    }

    public static boolean isSatnetMainnetPermittedByStage() {
        return !isSatnetPilotStage() || isSatnetMainnetExplicitOverrideEnabled();
    }
}

