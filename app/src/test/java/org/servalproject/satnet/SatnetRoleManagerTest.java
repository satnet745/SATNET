package org.servalproject.satnet;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.features.FeatureFlags;
import org.servalproject.satnet.ui.SatnetRuntimeTestHelper;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SatnetRoleManagerTest {

    private Context context;

    @Before
    public void setUp() {
        ServalBatPhoneApplication app = SatnetRuntimeTestHelper.prepareApp();
        context = SatnetRuntimeTestHelper.resetAppData(app);
    }

    @Test
    public void registerAsAgentInitializesRoleProfilesAndSubjects() {
        SatnetRoleManager roleManager = new SatnetRoleManager(context);
        roleManager.registerAsAgent("Alice Agent", "Kampala");

        assertTrue(roleManager.isRoleRegistered(SatnetRoleManager.ROLE_USER));
        assertTrue(roleManager.isRoleRegistered(SatnetRoleManager.ROLE_AGENT));
        assertEquals(SatnetRoleManager.ROLE_AGENT, roleManager.getActiveRole());

        SatnetRoleManager.RoleProfile userProfile = roleManager.getRoleProfile(SatnetRoleManager.ROLE_USER);
        SatnetRoleManager.RoleProfile agentProfile = roleManager.getRoleProfile(SatnetRoleManager.ROLE_AGENT);
        List<SatnetRoleManager.RoleProfile> profiles = roleManager.getRegisteredRoleProfiles();

        assertNotNull(userProfile);
        assertNotNull(agentProfile);
        assertEquals(2, profiles.size());
        assertNotNull(userProfile.roleSubjectId);
        assertNotNull(agentProfile.roleSubjectId);
        assertNotEquals(userProfile.roleSubjectId, agentProfile.roleSubjectId);
        assertEquals(roleManager.getParticipantRootSubjectId(), userProfile.participantRootSubjectId);
        assertEquals(roleManager.getParticipantRootSubjectId(), agentProfile.participantRootSubjectId);
        assertEquals("Alice Agent", agentProfile.displayName);
        assertEquals("Kampala", agentProfile.descriptor);
    }

    @Test
    public void capabilityChecksPreserveLegacyFeatureMapping() {
        SatnetRoleManager roleManager = new SatnetRoleManager(context);
        roleManager.registerAsMerchant("Field Shop", "Retail");

        assertTrue(roleManager.hasCapability(SatnetRoleManager.CAP_WALLET_VIEW));
        assertTrue(roleManager.hasCapability(SatnetRoleManager.CAP_VOUCHER_REDEEM));
        assertTrue(roleManager.hasFeature("bitcoin_wallet"));
        assertTrue(roleManager.hasFeature("redeem_vouchers"));
        assertEquals(FeatureFlags.isLightningEnabled(), roleManager.canActAsMerchant());
        assertEquals(FeatureFlags.isLightningEnabled(), roleManager.hasFeature("accept_lightning"));
        if (FeatureFlags.isLightningEnabled()) {
            assertTrue(roleManager.hasCapability(SatnetRoleManager.ROLE_MERCHANT,
                    SatnetRoleManager.CAP_MERCHANT_ACCEPT_LIGHTNING));
        } else {
            assertFalse(roleManager.hasCapability(SatnetRoleManager.ROLE_MERCHANT,
                    SatnetRoleManager.CAP_MERCHANT_ACCEPT_LIGHTNING));
        }
    }

    @Test
    public void suspendedMerchantLosesMerchantCapabilityButUserFallbackRemains() {
        SatnetRoleManager roleManager = new SatnetRoleManager(context);
        roleManager.registerAsMerchant("Field Shop", "Retail");
        roleManager.updateRoleStatus(SatnetRoleManager.ROLE_MERCHANT,
                SatnetRoleManager.ROLE_STATUS_SUSPENDED,
                "Manual review required");

        SatnetRoleManager.AuthorizationResult merchantAuthorization =
                roleManager.authorize(SatnetRoleManager.CAP_MERCHANT_ACCEPT_LIGHTNING, "merchant test");
        SatnetRoleManager.AuthorizationResult redeemAuthorization =
                roleManager.authorize(SatnetRoleManager.CAP_VOUCHER_REDEEM, "redeem test");

        assertFalse(roleManager.canActAsMerchant());
        assertFalse(merchantAuthorization.allowed);
        assertEquals("ROLE_SUSPENDED", merchantAuthorization.reasonCode);
        assertTrue(roleManager.hasCapability(SatnetRoleManager.CAP_VOUCHER_REDEEM));
        assertTrue(redeemAuthorization.allowed);
        assertEquals(SatnetRoleManager.ROLE_USER, redeemAuthorization.role);
    }

    @Test
    public void legacyParticipantSubjectMigratesToRootSubject() {
        SharedPreferences prefs = context.getSharedPreferences("satnet_roles", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("participant_subject_id", "legacy-subject-123")
                .putInt("registered_roles", SatnetRoleManager.ROLE_USER)
                .putInt("active_role", SatnetRoleManager.ROLE_USER)
                .commit();

        SatnetRoleManager roleManager = new SatnetRoleManager(context);

        assertEquals("legacy-subject-123", roleManager.getParticipantSubjectId());
        assertEquals("legacy-subject-123", roleManager.getParticipantRootSubjectId());
        assertEquals("legacy-subject-123", roleManager.getRoleProfile(SatnetRoleManager.ROLE_USER).participantRootSubjectId);
    }

    @Test
    public void agentProfilesRequireStepUpOnlyForHighValueAmounts() {
        SatnetRoleManager roleManager = new SatnetRoleManager(context);
        roleManager.registerAsAgent("Alice Agent", "Kampala");

        assertFalse(roleManager.requiresStepUpForAmount(SatnetRoleManager.ROLE_AGENT, 1_000_000L));
        assertTrue(roleManager.requiresStepUpForAmount(SatnetRoleManager.ROLE_AGENT, 1_000_001L));
        assertFalse(roleManager.requiresStepUpForAmount(SatnetRoleManager.ROLE_USER, Long.MAX_VALUE));
    }

    @Test
    public void reregisteringMerchantRefreshesRoleProfilePresentation() {
        SatnetRoleManager roleManager = new SatnetRoleManager(context);
        roleManager.registerAsMerchant("Field Shop", "Retail");
        roleManager.registerAsMerchant("Village Market", "Groceries");

        SatnetRoleManager.RoleProfile merchantProfile = roleManager.getRoleProfile(SatnetRoleManager.ROLE_MERCHANT);

        assertNotNull(merchantProfile);
        assertEquals("Village Market", merchantProfile.displayName);
        assertEquals("Groceries", merchantProfile.descriptor);
    }

    @Test
    public void authorizationForUnknownCapabilityReturnsBackwardCompatibleDenial() {
        SatnetRoleManager roleManager = new SatnetRoleManager(context);
        roleManager.registerAsUser();

        SatnetRoleManager.AuthorizationResult authorizationResult = roleManager.authorize(1 << 20, "unsupported test");

        assertFalse(authorizationResult.allowed);
        assertEquals("CAPABILITY_NOT_GRANTED", authorizationResult.reasonCode);
        assertNull(roleManager.getRoleProfile(SatnetRoleManager.ROLE_AGENT));
    }
}

