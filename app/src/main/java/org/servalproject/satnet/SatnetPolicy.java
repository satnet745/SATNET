package org.servalproject.satnet;

import org.servalproject.features.FeatureFlags;

public final class SatnetPolicy {
    private SatnetPolicy() {
    }

    public static boolean isCustodyModelCompliant() {
        return !FeatureFlags.isSatnetCustodialModeAllowed();
    }

    public static boolean isMonetizationModelCompliant() {
        return !FeatureFlags.isSatnetMandatoryProtocolFeeAllowed()
                && !FeatureFlags.isSatnetSurveillanceMonetizationAllowed();
    }

    public static boolean isMainnetSettlementPermitted() {
        if (!FeatureFlags.isSatnetMainnetSettlementEnabled()) {
            return true;
        }
        return FeatureFlags.isSatnetMainnetPermittedByStage();
    }

    public static boolean requiresTestnetByDefault() {
        return FeatureFlags.requiresSatnetTestnetByDefault();
    }

    public static boolean isBuildPolicyCompliant() {
        return isCustodyModelCompliant()
                && isMonetizationModelCompliant()
                && isMainnetSettlementPermitted();
    }

    public static String getPolicyViolationReason() {
        if (!isCustodyModelCompliant()) {
            return "Custodial SATNET mode is disabled by policy";
        }
        if (FeatureFlags.isSatnetMandatoryProtocolFeeAllowed()) {
            return "Mandatory protocol fees are disabled by policy";
        }
        if (FeatureFlags.isSatnetSurveillanceMonetizationAllowed()) {
            return "Surveillance monetization is disabled by policy";
        }
        if (!isMainnetSettlementPermitted()) {
            return "Mainnet settlement requires an explicit non-pilot deployment override";
        }
        return null;
    }

    public static void enforceBuildPolicy() {
        String violation = getPolicyViolationReason();
        if (violation != null) {
            throw new IllegalStateException(violation);
        }
    }
}

