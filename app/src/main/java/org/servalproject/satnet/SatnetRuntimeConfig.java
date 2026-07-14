package org.servalproject.satnet;

import android.content.Context;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.features.FeatureFlags;

public final class SatnetRuntimeConfig {
    private static final long PILOT_MAX_VOUCHER_DENOMINATION_SATS = 10_000L;
    private static final long COUNTRY_BETA_MAX_VOUCHER_DENOMINATION_SATS = 50_000L;
    private static final long GLOBAL_MAX_VOUCHER_DENOMINATION_SATS = 250_000L;

    private SatnetRuntimeConfig() {
    }

    public static String normalizeStage(String stage) {
        if (FeatureFlags.STAGE_COUNTRY_BETA.equalsIgnoreCase(stage)) {
            return FeatureFlags.STAGE_COUNTRY_BETA;
        }
        if (FeatureFlags.STAGE_GLOBAL.equalsIgnoreCase(stage)) {
            return FeatureFlags.STAGE_GLOBAL;
        }
        return FeatureFlags.STAGE_PILOT;
    }

    public static String getStage() {
        return normalizeStage(FeatureFlags.getSatnetDeploymentStage());
    }

    public static String getStageDisplayName() {
        String stage = getStage();
        if (FeatureFlags.STAGE_COUNTRY_BETA.equals(stage)) {
            return getString(R.string.satnet_stage_display_country_beta, "Country Beta");
        }
        if (FeatureFlags.STAGE_GLOBAL.equals(stage)) {
            return getString(R.string.satnet_stage_display_global, "Global");
        }
        return getString(R.string.satnet_stage_display_pilot, "Pilot");
    }

    public static String getStageBadge() {
        return getString(R.string.satnet_stage_badge_format, "SATNET %s", getStageDisplayName());
    }

    public static String getStageBadgeWithNetwork() {
        return getString(R.string.satnet_runtime_badge_with_network, "%s · %s", getStageBadge(), getNetworkDisplayName());
    }

    public static String getNetworkDisplayName() {
        if (SatnetPolicy.isMainnetSettlementPermitted() && FeatureFlags.isSatnetMainnetSettlementEnabled()) {
            return getString(R.string.satnet_network_display_mainnet, "mainnet");
        }
        return getString(R.string.satnet_network_display_testnet, "testnet");
    }

    public static String getStageSummary() {
        String network = getNetworkDisplayName();
        if (FeatureFlags.STAGE_COUNTRY_BETA.equals(getStage())) {
            return getString(
                    R.string.satnet_stage_summary_country_beta,
                    "Country beta rollout active. Field operations are enabled with %s settlement defaults and corridor-specific safeguards.",
                    network);
        }
        if (FeatureFlags.STAGE_GLOBAL.equals(getStage())) {
            return getString(
                    R.string.satnet_stage_summary_global,
                    "Global rollout profile active. Broader merchant and verifier operations are enabled on %s with open, non-custodial policy safeguards.",
                    network);
        }
        return getString(
                R.string.satnet_stage_summary_pilot,
                "Pilot rollout active. Local-first flows stay prioritized, settlement defaults to %s, and high-risk services fail soft instead of crashing.",
                network);
    }

    public static String getRoleSummary(int role) {
        switch (role) {
            case SatnetRoleManager.ROLE_AGENT:
                return getString(
                        R.string.satnet_role_summary_agent,
                        "Agents issue vouchers with a stage cap of %d sats per voucher and offline-first sharing.",
                        getMaxVoucherDenominationSats());
            case SatnetRoleManager.ROLE_MERCHANT:
                return FeatureFlags.isLightningEnabled()
                        ? getString(
                        R.string.satnet_role_summary_merchant_enabled,
                        "Merchants generate Lightning invoices in %s mode with copy/share actions for field payments.",
                        getNetworkDisplayName())
                        : getString(
                        R.string.satnet_role_summary_merchant_disabled,
                        "Merchant tooling is disabled because Lightning support is not enabled in this build.");
            case SatnetRoleManager.ROLE_VERIFIER:
                return getString(
                        R.string.satnet_role_summary_verifier,
                        "Verifiers review SELL settlements with a %d-hour release window in the current rollout stage.",
                        getSettlementWindowHours());
            case SatnetRoleManager.ROLE_USER:
            default:
                return getString(
                        R.string.satnet_role_summary_user,
                        "Users keep a self-custodial Bitcoin wallet with stage-aware routing and voucher redemption.");
        }
    }

    public static String getWalletSummary() {
        return getString(
                R.string.satnet_wallet_stage_summary_format,
                "%s · %s · %dh verifier window",
                getStageBadge(),
                getNetworkDisplayName(),
                getSettlementWindowHours());
    }

    public static String getStartupSummary(ServalBatPhoneApplication app) {
        if (app == null) {
            return getStageSummary();
        }
        if (app.getState() != ServalBatPhoneApplication.State.Running) {
            return getString(
                    R.string.satnet_startup_summary_core_waiting,
                    "%s is waiting for core services to finish starting.",
                    getStageBadge());
        }
        if (!app.isStartupTasksComplete()) {
            return getString(
                    R.string.satnet_startup_summary_warming,
                    "%s is available while mesh services finish warming up.",
                    getStageBadge());
        }
        if (!app.isRhizomeRuntimeReady()) {
            return getString(
                    R.string.satnet_startup_summary_rhizome_waiting,
                    "%s wallet flows are ready; mesh audit sync will activate when Rhizome is ready.",
                    getStageBadge());
        }
        return getStageSummary();
    }

    public static String getStartupBlockingCoreMessage(String startupSummary) {
        return getString(
                R.string.satnet_startup_blocking_core,
                "%s Try again once the mesh core has started.",
                startupSummary);
    }

    public static String getStartupBlockingWarmupMessage(String startupSummary) {
        return getString(
                R.string.satnet_startup_blocking_warming,
                "%s SATNET tools will unlock after warm-up finishes.",
                startupSummary);
    }

    public static String getVerifierRhizomeWaitingMessage(String stageBadge) {
        return getString(
                R.string.satnet_startup_blocking_verifier_rhizome,
                "%s verifier tooling is waiting for Rhizome audit sync to come online.",
                stageBadge);
    }

    public static String getLocalFirstMessage(String stageBadge, String capabilityLabel) {
        return getString(
                R.string.satnet_startup_local_first_message,
                "%s is operating in local-first mode. %s will work locally while mesh audit sync finishes warming up.",
                stageBadge,
                capabilityLabel);
    }

    public static long getMaxVoucherDenominationSats() {
        if (FeatureFlags.STAGE_COUNTRY_BETA.equals(getStage())) {
            return COUNTRY_BETA_MAX_VOUCHER_DENOMINATION_SATS;
        }
        if (FeatureFlags.STAGE_GLOBAL.equals(getStage())) {
            return GLOBAL_MAX_VOUCHER_DENOMINATION_SATS;
        }
        return PILOT_MAX_VOUCHER_DENOMINATION_SATS;
    }

    public static int getSettlementWindowHours() {
        if (FeatureFlags.STAGE_COUNTRY_BETA.equals(getStage())) {
            return 48;
        }
        if (FeatureFlags.STAGE_GLOBAL.equals(getStage())) {
            return 72;
        }
        return 24;
    }

    public static long getSettlementWindowMillis() {
        return getSettlementWindowHours() * 60L * 60L * 1000L;
    }

    public static boolean allowLiveExchangeRateFetch() {
        return FeatureFlags.isSatnetLiveExchangeRatesEnabled() && hasConfiguredExchangeRateProviders();
    }

    public static String getExchangeRateModeSummary() {
        return allowLiveExchangeRateFetch()
                ? getString(
                R.string.satnet_exchange_rate_mode_enabled,
                "Automatic live exchange rates are enabled through privacy-preserving HTTPS provider mirrors with cached offline fallback.")
                : getString(
                R.string.satnet_exchange_rate_mode_disabled,
                "Automatic live exchange rates are unavailable because no provider mirrors are configured; cached fallback rates remain available.");
    }

    public static String getVerifierWindowSummary() {
        return getString(
                R.string.satnet_verifier_window_summary,
                "%dh verifier release window",
                getSettlementWindowHours());
    }

    public static boolean hasConfiguredExchangeRateBaseUrl() {
        return hasConfiguredExchangeRateProviders();
    }

    public static boolean hasConfiguredExchangeRateProviders() {
        String providers = FeatureFlags.getSatnetExchangeRateProviderUrls();
        return providers != null
                && !providers.trim().isEmpty()
                && !providers.contains(".invalid");
    }

    private static String getString(int resId, String fallback, Object... args) {
        Context context = ServalBatPhoneApplication.context;
        if (context != null) {
            return args.length == 0 ? context.getString(resId) : context.getString(resId, args);
        }
        return args.length == 0 ? fallback : String.format(java.util.Locale.US, fallback, args);
    }
}

