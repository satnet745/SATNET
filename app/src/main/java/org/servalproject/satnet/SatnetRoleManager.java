/**
 * Copyright (C) 2025 SATNET AFRICA
 *
 * This file is part of SATNET AFRICA (http://satnetafrica.org)
 *
 * SATNET AFRICA is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.servalproject.satnet;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.servalproject.features.FeatureFlags;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SATNET AFRICA Role Manager
 *
 * Keeps track of the currently active role and the set of all roles that
 * have been registered on this device so role creation flows remain safe
 * across repeated launches.
 */
public class SatnetRoleManager {
    private static final String TAG = "SatnetRoleManager";
    private static final String PREFS_NAME = "satnet_roles";
    private static final String KEY_ACTIVE_ROLE = "active_role";
    private static final String KEY_REGISTERED_ROLES = "registered_roles";
    private static final String KEY_PARTICIPANT_SUBJECT_ID = "participant_subject_id";
    private static final String KEY_PARTICIPANT_ROOT_SUBJECT_ID = "participant_root_subject_id";
    private static final String KEY_ROLE_PROFILE_VERSION = "role_profile_version";
    private static final String KEY_ROLE_DATA = "role_data_";
    private static final String KEY_ROLE_SUBJECT_ID = "role_subject_id_";
    private static final String KEY_ROLE_STATUS = "role_status_";
    private static final String KEY_ROLE_RISK_TIER = "role_risk_tier_";
    private static final String KEY_ROLE_REPUTATION_SCORE = "role_reputation_score_";
    private static final String KEY_ROLE_REGISTERED_AT = "role_registered_at_";
    private static final String KEY_ROLE_LAST_REVIEWED_AT = "role_last_reviewed_at_";
    private static final String KEY_ROLE_REGION_SCOPE = "role_region_scope_";
    private static final String KEY_ROLE_DAILY_LIMIT_SATS = "role_daily_limit_sats_";
    private static final String KEY_ROLE_MONTHLY_LIMIT_SATS = "role_monthly_limit_sats_";
    private static final String KEY_ROLE_REQUIRES_STEP_UP = "role_requires_step_up_";
    private static final String KEY_ROLE_DISPLAY_NAME = "role_display_name_";
    private static final String KEY_ROLE_DESCRIPTOR = "role_descriptor_";
    private static final String KEY_ROLE_SUSPENSION_REASON = "role_suspension_reason_";

    private static final int ROLE_PROFILE_VERSION = 1;

    public static final int ROLE_NONE = 0;
    public static final int ROLE_USER = 1;
    public static final int ROLE_AGENT = 2;
    public static final int ROLE_MERCHANT = 4;
    public static final int ROLE_VERIFIER = 8;

    public static final int ROLE_STATUS_ACTIVE = 1;
    public static final int ROLE_STATUS_LIMITED = 2;
    public static final int ROLE_STATUS_SUSPENDED = 3;
    public static final int ROLE_STATUS_REVIEW_REQUIRED = 4;

    public static final int RISK_TIER_LOW = 1;
    public static final int RISK_TIER_MEDIUM = 2;
    public static final int RISK_TIER_HIGH = 3;
    public static final int RISK_TIER_RESTRICTED = 4;

    public static final int CAP_WALLET_VIEW = 1 << 0;
    public static final int CAP_WALLET_BACKUP = 1 << 1;
    public static final int CAP_VOUCHER_REDEEM = 1 << 2;
    public static final int CAP_VOUCHER_ISSUE = 1 << 3;
    public static final int CAP_MERCHANT_ACCEPT_LIGHTNING = 1 << 4;
    public static final int CAP_MERCHANT_SETTLEMENT_VIEW = 1 << 5;
    public static final int CAP_VERIFIER_INSPECT = 1 << 6;
    public static final int CAP_VERIFIER_APPROVE_SETTLEMENT = 1 << 7;
    public static final int CAP_VERIFIER_RESOLVE_DISPUTE = 1 << 8;
    public static final int CAP_ROLE_MANAGE = 1 << 9;
    public static final int CAP_RISK_REVIEW_LOCAL = 1 << 10;
    // BANKING: Secure P2P Bitcoin operations (isolated from voucher/communication systems)
    public static final int CAP_BITCOIN_SEND = 1 << 11;
    public static final int CAP_BITCOIN_RECEIVE = 1 << 12;

    private static final int DEFAULT_REPUTATION_SCORE = 50;
    private static final long DEFAULT_AGENT_DAILY_LIMIT_SATS = 5_000_000L;
    private static final long DEFAULT_AGENT_MONTHLY_LIMIT_SATS = 100_000_000L;
    private static final long DEFAULT_MERCHANT_DAILY_LIMIT_SATS = 10_000_000L;
    private static final long DEFAULT_MERCHANT_MONTHLY_LIMIT_SATS = 200_000_000L;
    private static final long DEFAULT_AGENT_STEP_UP_THRESHOLD_SATS = 1_000_000L;
    private static final long DEFAULT_MERCHANT_STEP_UP_THRESHOLD_SATS = 2_000_000L;

    private final SharedPreferences prefs;
    private int activeRole;
    private int registeredRoles;

    public SatnetRoleManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        migrateLegacyRoleStateIfNeeded();
        this.registeredRoles = prefs.getInt(KEY_REGISTERED_ROLES, ROLE_NONE);
        ensureParticipantIdentifiers();
        ensureRoleProfiles();
        this.activeRole = sanitizeActiveRole(prefs.getInt(KEY_ACTIVE_ROLE, ROLE_NONE), registeredRoles);
    }

    public void registerAsUser() {
        SatnetPolicy.enforceBuildPolicy();
        Log.d(TAG, "Registering user as ROLE_USER");
        grantRole(ROLE_USER);
        syncRoleProfilePresentation(ROLE_USER);
    }

    public void registerAsAgent(String agentName, String location) {
        SatnetPolicy.enforceBuildPolicy();
        Log.d(TAG, "Registering agent: " + agentName + " in " + location);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_ROLE_DATA + "agent_name", agentName);
        editor.putString(KEY_ROLE_DATA + "agent_location", location);
        editor.putLong(KEY_ROLE_DATA + "agent_registered_time", System.currentTimeMillis());
        editor.apply();

        grantRole(ROLE_AGENT);
        syncRoleProfilePresentation(ROLE_AGENT);
        Log.d(TAG, "Agent registered successfully");
    }

    public void registerAsMerchant(String businessName, String businessType) {
        SatnetPolicy.enforceBuildPolicy();
        if (!FeatureFlags.isLightningEnabled()) {
            throw new UnsupportedOperationException("Merchant registration is disabled in this build");
        }
        Log.d(TAG, "Registering merchant: " + businessName);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_ROLE_DATA + "merchant_name", businessName);
        editor.putString(KEY_ROLE_DATA + "merchant_type", businessType);
        editor.putLong(KEY_ROLE_DATA + "merchant_registered_time", System.currentTimeMillis());
        editor.apply();

        grantRole(ROLE_MERCHANT);
        syncRoleProfilePresentation(ROLE_MERCHANT);
        Log.d(TAG, "Merchant registered successfully");
    }

    public void registerAsVerifier() {
        SatnetPolicy.enforceBuildPolicy();
        Log.d(TAG, "Registering as community verifier");
        grantRole(ROLE_VERIFIER);
        syncRoleProfilePresentation(ROLE_VERIFIER);
    }

    public boolean canActAsAgent() {
        return hasCapability(ROLE_AGENT, CAP_VOUCHER_ISSUE);
    }

    public boolean canActAsMerchant() {
        return hasCapability(ROLE_MERCHANT, CAP_MERCHANT_ACCEPT_LIGHTNING);
    }

    public boolean canActAsVerifier() {
        return hasCapability(ROLE_VERIFIER, CAP_VERIFIER_INSPECT);
    }

    public boolean hasFeature(String featureName) {
        if (!SatnetPolicy.isBuildPolicyCompliant()) {
            return false;
        }
        if (featureName == null || featureName.trim().isEmpty()) {
            return false;
        }
        switch (featureName) {
            case "bitcoin_wallet":
                return hasCapability(CAP_WALLET_VIEW);
            case "bitcoin_send":
                // BANKING: Check if user can send Bitcoin P2P
                return hasCapability(CAP_BITCOIN_SEND);
            case "bitcoin_receive":
                // BANKING: Check if user can receive Bitcoin P2P
                return hasCapability(CAP_BITCOIN_RECEIVE);
            case "issue_vouchers":
                return hasCapability(CAP_VOUCHER_ISSUE);
            case "accept_lightning":
                return hasCapability(CAP_MERCHANT_ACCEPT_LIGHTNING);
            case "mediate_disputes":
                return hasCapability(CAP_VERIFIER_RESOLVE_DISPUTE);
            case "stake_bitcoin":
                return hasCapability(CAP_VOUCHER_ISSUE);
            case "redeem_vouchers":
                return hasCapability(CAP_VOUCHER_REDEEM);
            default:
                return false;
        }
    }

    public String getAgentName() {
        return prefs.getString(KEY_ROLE_DATA + "agent_name", "");
    }

    public String getAgentLocation() {
        return prefs.getString(KEY_ROLE_DATA + "agent_location", "");
    }

    public String getMerchantName() {
        return prefs.getString(KEY_ROLE_DATA + "merchant_name", "");
    }

    public String getMerchantType() {
        return prefs.getString(KEY_ROLE_DATA + "merchant_type", "");
    }

    public int getActiveRole() {
        activeRole = sanitizeActiveRole(activeRole, registeredRoles);
        return activeRole;
    }

    public int getRegisteredRoles() {
        return registeredRoles;
    }

    public String getParticipantRootSubjectId() {
        String rootSubjectId = prefs.getString(KEY_PARTICIPANT_ROOT_SUBJECT_ID, null);
        if (rootSubjectId == null || rootSubjectId.trim().isEmpty()) {
            String legacySubjectId = prefs.getString(KEY_PARTICIPANT_SUBJECT_ID, null);
            rootSubjectId = (legacySubjectId == null || legacySubjectId.trim().isEmpty())
                    ? UUID.randomUUID().toString()
                    : legacySubjectId.trim();
            prefs.edit().putString(KEY_PARTICIPANT_ROOT_SUBJECT_ID, rootSubjectId).apply();
        }
        return rootSubjectId;
    }

    public String getParticipantSubjectId() {
        String subjectId = prefs.getString(KEY_PARTICIPANT_SUBJECT_ID, null);
        if (subjectId == null || subjectId.trim().isEmpty()) {
            subjectId = getParticipantRootSubjectId();
            prefs.edit().putString(KEY_PARTICIPANT_SUBJECT_ID, subjectId).apply();
        }
        return subjectId;
    }

    public String getRoleSubjectId(int role) {
        if (role == ROLE_NONE || !isRoleRegistered(role)) {
            return null;
        }
        String key = keyForRole(KEY_ROLE_SUBJECT_ID, role);
        String roleSubjectId = prefs.getString(key, null);
        if (roleSubjectId == null || roleSubjectId.trim().isEmpty()) {
            roleSubjectId = UUID.randomUUID().toString();
            prefs.edit().putString(key, roleSubjectId).apply();
        }
        return roleSubjectId;
    }

    public boolean isRoleRegistered(int role) {
        return (registeredRoles & role) != 0;
    }

    public RoleProfile getActiveRoleProfile() {
        return getRoleProfile(getActiveRole());
    }

    public RoleProfile getRoleProfile(int role) {
        if (role == ROLE_NONE || !isRoleRegistered(role)) {
            return null;
        }
        ensureRoleProfile(role);
        long registeredAt = prefs.getLong(keyForRole(KEY_ROLE_REGISTERED_AT, role), resolveRegisteredAt(role));
        long lastReviewedAt = prefs.getLong(keyForRole(KEY_ROLE_LAST_REVIEWED_AT, role), registeredAt);
        return new RoleProfile(
                role,
                getRoleSubjectId(role),
                getParticipantRootSubjectId(),
                prefs.getString(keyForRole(KEY_ROLE_DISPLAY_NAME, role), getDefaultRoleDisplayName(role)),
                prefs.getString(keyForRole(KEY_ROLE_DESCRIPTOR, role), getDefaultRoleDescriptor(role)),
                prefs.getString(keyForRole(KEY_ROLE_REGION_SCOPE, role), ""),
                registeredAt,
                lastReviewedAt,
                prefs.getInt(keyForRole(KEY_ROLE_STATUS, role), ROLE_STATUS_ACTIVE),
                prefs.getInt(keyForRole(KEY_ROLE_RISK_TIER, role), RISK_TIER_LOW),
                prefs.getInt(keyForRole(KEY_ROLE_REPUTATION_SCORE, role), DEFAULT_REPUTATION_SCORE),
                prefs.getLong(keyForRole(KEY_ROLE_DAILY_LIMIT_SATS, role), getDefaultDailyLimit(role)),
                prefs.getLong(keyForRole(KEY_ROLE_MONTHLY_LIMIT_SATS, role), getDefaultMonthlyLimit(role)),
                prefs.getBoolean(keyForRole(KEY_ROLE_REQUIRES_STEP_UP, role), getDefaultRequiresStepUp(role)),
                prefs.getString(keyForRole(KEY_ROLE_SUSPENSION_REASON, role), null));
    }

    public List<RoleProfile> getRegisteredRoleProfiles() {
        List<RoleProfile> profiles = new ArrayList<RoleProfile>();
        for (Integer role : getRegisteredRoleList()) {
            RoleProfile profile = getRoleProfile(role.intValue());
            if (profile != null) {
                profiles.add(profile);
            }
        }
        return profiles;
    }

    public List<Integer> getRegisteredRoleList() {
        List<Integer> roles = new ArrayList<Integer>();
        appendRegisteredRole(roles, ROLE_USER);
        appendRegisteredRole(roles, ROLE_AGENT);
        appendRegisteredRole(roles, ROLE_MERCHANT);
        appendRegisteredRole(roles, ROLE_VERIFIER);
        return roles;
    }

    public boolean hasCapability(int capability) {
        for (Integer role : getRegisteredRoleList()) {
            if (hasCapability(role.intValue(), capability)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasCapability(int role, int capability) {
        return authorize(role, capability, null).allowed;
    }

    public AuthorizationResult authorize(int capability) {
        return authorize(capability, null);
    }

    public AuthorizationResult authorize(int capability, String reasonContext) {
        int selectedRole = getActiveRole();
        if (selectedRole != ROLE_NONE && isCapabilityMappedToRole(selectedRole, capability)) {
            AuthorizationResult activeRoleResult = authorize(selectedRole, capability, reasonContext);
            if (activeRoleResult.allowed) {
                return activeRoleResult;
            }
            for (Integer role : getRegisteredRoleList()) {
                if (role.intValue() == selectedRole || !isCapabilityMappedToRole(role.intValue(), capability)) {
                    continue;
                }
                AuthorizationResult fallbackResult = authorize(role.intValue(), capability, reasonContext);
                if (fallbackResult.allowed) {
                    return fallbackResult;
                }
            }
            return activeRoleResult;
        }
        for (Integer role : getRegisteredRoleList()) {
            if (isCapabilityMappedToRole(role.intValue(), capability)) {
                AuthorizationResult authorizationResult = authorize(role.intValue(), capability, reasonContext);
                if (authorizationResult.allowed) {
                    return authorizationResult;
                }
            }
        }
        return AuthorizationResult.deny(ROLE_NONE, capability, "CAPABILITY_NOT_GRANTED",
                buildAuthorizationMessage(reasonContext, "This SATNET device is not authorized for that action."));
    }

    public void updateRoleStatus(int role, int status, String reason) {
        if (!isRoleRegistered(role)) {
            return;
        }
        ensureRoleProfile(role);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(keyForRole(KEY_ROLE_STATUS, role), status);
        editor.putLong(keyForRole(KEY_ROLE_LAST_REVIEWED_AT, role), System.currentTimeMillis());
        if (reason == null || reason.trim().isEmpty()) {
            editor.remove(keyForRole(KEY_ROLE_SUSPENSION_REASON, role));
        } else {
            editor.putString(keyForRole(KEY_ROLE_SUSPENSION_REASON, role), reason.trim());
        }
        editor.apply();
    }

    public void updateRoleRiskTier(int role, int riskTier) {
        if (!isRoleRegistered(role)) {
            return;
        }
        ensureRoleProfile(role);
        prefs.edit()
                .putInt(keyForRole(KEY_ROLE_RISK_TIER, role), riskTier)
                .putLong(keyForRole(KEY_ROLE_LAST_REVIEWED_AT, role), System.currentTimeMillis())
                .apply();
    }

    public void updateRoleReputation(int role, int reputationScore) {
        if (!isRoleRegistered(role)) {
            return;
        }
        ensureRoleProfile(role);
        prefs.edit().putInt(keyForRole(KEY_ROLE_REPUTATION_SCORE, role), reputationScore).apply();
    }

    public void updateRoleLimits(int role, long dailyLimitSats, long monthlyLimitSats) {
        if (!isRoleRegistered(role)) {
            return;
        }
        ensureRoleProfile(role);
        prefs.edit()
                .putLong(keyForRole(KEY_ROLE_DAILY_LIMIT_SATS, role), Math.max(0L, dailyLimitSats))
                .putLong(keyForRole(KEY_ROLE_MONTHLY_LIMIT_SATS, role), Math.max(0L, monthlyLimitSats))
                .apply();
    }

    public boolean requiresStepUpForAmount(int role, long amountSats) {
        RoleProfile profile = getRoleProfile(role);
        if (profile == null || !profile.requiresStepUpForHighValue) {
            return false;
        }
        return amountSats > getDefaultStepUpThreshold(role);
    }

    public String getRoleName() {
        switch (getActiveRole()) {
            case ROLE_USER:
                return "Bitcoin User";
            case ROLE_AGENT:
                return "Voucher Agent";
            case ROLE_MERCHANT:
                return "Merchant";
            case ROLE_VERIFIER:
                return "Community Verifier";
            default:
                return "No Role";
        }
    }

    public String getRegisteredRoleSummary() {
        if (registeredRoles == ROLE_NONE) {
            return "No roles registered yet";
        }
        StringBuilder summary = new StringBuilder();
        appendRoleLabel(summary, ROLE_USER, "Wallet User");
        appendRoleLabel(summary, ROLE_AGENT, "Voucher Agent");
        appendRoleLabel(summary, ROLE_MERCHANT, "Merchant");
        appendRoleLabel(summary, ROLE_VERIFIER, "Community Verifier");
        return summary.toString();
    }

    public void switchRole(int newRole) {
        if (newRole != ROLE_NONE && !isRoleRegistered(newRole)) {
            throw new IllegalStateException("User not registered for role " + newRole);
        }
        setActiveRole(newRole);
        Log.d(TAG, "Switched to role: " + getRoleName());
    }

    public void unregisterRole(int role) {
        registeredRoles = registeredRoles & ~role;
        if (registeredRoles == ROLE_NONE) {
            activeRole = ROLE_NONE;
        } else if (activeRole == role) {
            activeRole = sanitizeActiveRole(ROLE_NONE, registeredRoles);
        }
        persistRoleState();
        Log.d(TAG, "Unregistered from role: " + role);
    }

    private void migrateLegacyRoleStateIfNeeded() {
        if (prefs.contains(KEY_REGISTERED_ROLES)) {
            return;
        }
        int legacyActiveRole = prefs.getInt(KEY_ACTIVE_ROLE, ROLE_NONE);
        int migratedRoles = legacyActiveRole == ROLE_NONE ? ROLE_NONE : ensureWalletBaseRole(legacyActiveRole);
        prefs.edit().putInt(KEY_REGISTERED_ROLES, migratedRoles).apply();
    }

    private void ensureParticipantIdentifiers() {
        String legacySubjectId = prefs.getString(KEY_PARTICIPANT_SUBJECT_ID, null);
        String rootSubjectId = prefs.getString(KEY_PARTICIPANT_ROOT_SUBJECT_ID, null);
        SharedPreferences.Editor editor = prefs.edit();
        if (rootSubjectId == null || rootSubjectId.trim().isEmpty()) {
            rootSubjectId = (legacySubjectId == null || legacySubjectId.trim().isEmpty())
                    ? UUID.randomUUID().toString()
                    : legacySubjectId.trim();
            editor.putString(KEY_PARTICIPANT_ROOT_SUBJECT_ID, rootSubjectId);
        }
        if (legacySubjectId == null || legacySubjectId.trim().isEmpty()) {
            editor.putString(KEY_PARTICIPANT_SUBJECT_ID, rootSubjectId);
        }
        if (!prefs.contains(KEY_ROLE_PROFILE_VERSION)) {
            editor.putInt(KEY_ROLE_PROFILE_VERSION, ROLE_PROFILE_VERSION);
        }
        editor.apply();
    }

    private void grantRole(int role) {
        int normalizedRole = ensureWalletBaseRole(role);
        registeredRoles |= normalizedRole;
        activeRole = role == ROLE_NONE ? sanitizeActiveRole(activeRole, registeredRoles) : role;
        ensureRoleProfiles();
        persistRoleState();
    }

    private void setActiveRole(int role) {
        activeRole = sanitizeActiveRole(role, registeredRoles);
        persistRoleState();
    }

    private void persistRoleState() {
        prefs.edit()
                .putInt(KEY_REGISTERED_ROLES, registeredRoles)
                .putInt(KEY_ACTIVE_ROLE, activeRole)
                .apply();
    }

    private int sanitizeActiveRole(int requestedRole, int availableRoles) {
        if (requestedRole != ROLE_NONE && (availableRoles & requestedRole) != 0) {
            return requestedRole;
        }
        if ((availableRoles & ROLE_AGENT) != 0) {
            return ROLE_AGENT;
        }
        if ((availableRoles & ROLE_MERCHANT) != 0) {
            return ROLE_MERCHANT;
        }
        if ((availableRoles & ROLE_VERIFIER) != 0) {
            return ROLE_VERIFIER;
        }
        if ((availableRoles & ROLE_USER) != 0) {
            return ROLE_USER;
        }
        return ROLE_NONE;
    }

    private int ensureWalletBaseRole(int roleMask) {
        if (roleMask == ROLE_NONE) {
            return ROLE_NONE;
        }
        return roleMask | ROLE_USER;
    }

    private AuthorizationResult authorize(int role, int capability, String reasonContext) {
        if (!SatnetPolicy.isBuildPolicyCompliant()) {
            return AuthorizationResult.deny(role, capability, "POLICY_VIOLATION",
                    buildAuthorizationMessage(reasonContext, SatnetPolicy.getPolicyViolationReason()));
        }
        if (!isRoleRegistered(role)) {
            return AuthorizationResult.deny(role, capability, "ROLE_NOT_REGISTERED",
                    buildAuthorizationMessage(reasonContext, "This role is not registered on this device."));
        }
        if (!isCapabilityMappedToRole(role, capability)) {
            String reasonCode = capability == CAP_MERCHANT_ACCEPT_LIGHTNING && !FeatureFlags.isLightningEnabled()
                    ? "FEATURE_DISABLED"
                    : "CAPABILITY_NOT_GRANTED";
            String message = capability == CAP_MERCHANT_ACCEPT_LIGHTNING && !FeatureFlags.isLightningEnabled()
                    ? "Merchant Lightning tools are disabled in this build."
                    : "This role cannot perform that SATNET action.";
            return AuthorizationResult.deny(role, capability, reasonCode, buildAuthorizationMessage(reasonContext, message));
        }
        RoleProfile profile = getRoleProfile(role);
        if (profile == null) {
            return AuthorizationResult.deny(role, capability, "ROLE_NOT_REGISTERED",
                    buildAuthorizationMessage(reasonContext, "This role is not registered on this device."));
        }
        if (profile.status == ROLE_STATUS_SUSPENDED) {
            return AuthorizationResult.deny(role, capability, "ROLE_SUSPENDED",
                    buildAuthorizationMessage(reasonContext,
                            profile.suspensionReason == null || profile.suspensionReason.trim().isEmpty()
                                    ? "This role is currently suspended."
                                    : profile.suspensionReason));
        }
        if (profile.status == ROLE_STATUS_REVIEW_REQUIRED) {
            return AuthorizationResult.deny(role, capability, "ROLE_REVIEW_REQUIRED",
                    buildAuthorizationMessage(reasonContext, "This role requires review before it can be used."));
        }
        return AuthorizationResult.allow(role, capability);
    }

    private void ensureRoleProfiles() {
        ensureRoleProfile(ROLE_USER);
        ensureRoleProfile(ROLE_AGENT);
        ensureRoleProfile(ROLE_MERCHANT);
        ensureRoleProfile(ROLE_VERIFIER);
    }

    private void syncRoleProfilePresentation(int role) {
        if (!isRoleRegistered(role)) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(keyForRole(KEY_ROLE_DISPLAY_NAME, role), getDefaultRoleDisplayName(role));
        String descriptor = getDefaultRoleDescriptor(role);
        if (descriptor == null || descriptor.trim().isEmpty()) {
            editor.remove(keyForRole(KEY_ROLE_DESCRIPTOR, role));
        } else {
            editor.putString(keyForRole(KEY_ROLE_DESCRIPTOR, role), descriptor.trim());
        }
        editor.apply();
    }

    private void ensureRoleProfile(int role) {
        if (!isRoleRegistered(role)) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        String roleSubjectKey = keyForRole(KEY_ROLE_SUBJECT_ID, role);
        if (!prefs.contains(roleSubjectKey)) {
            editor.putString(roleSubjectKey, UUID.randomUUID().toString());
            changed = true;
        }
        String displayNameKey = keyForRole(KEY_ROLE_DISPLAY_NAME, role);
        if (!prefs.contains(displayNameKey)) {
            editor.putString(displayNameKey, getDefaultRoleDisplayName(role));
            changed = true;
        }
        String descriptorKey = keyForRole(KEY_ROLE_DESCRIPTOR, role);
        if (!prefs.contains(descriptorKey)) {
            String defaultDescriptor = getDefaultRoleDescriptor(role);
            if (defaultDescriptor == null) {
                editor.remove(descriptorKey);
            } else {
                editor.putString(descriptorKey, defaultDescriptor);
            }
            changed = true;
        }
        if (!prefs.contains(keyForRole(KEY_ROLE_STATUS, role))) {
            editor.putInt(keyForRole(KEY_ROLE_STATUS, role), ROLE_STATUS_ACTIVE);
            changed = true;
        }
        if (!prefs.contains(keyForRole(KEY_ROLE_RISK_TIER, role))) {
            editor.putInt(keyForRole(KEY_ROLE_RISK_TIER, role), RISK_TIER_LOW);
            changed = true;
        }
        if (!prefs.contains(keyForRole(KEY_ROLE_REPUTATION_SCORE, role))) {
            editor.putInt(keyForRole(KEY_ROLE_REPUTATION_SCORE, role), DEFAULT_REPUTATION_SCORE);
            changed = true;
        }
        if (!prefs.contains(keyForRole(KEY_ROLE_REGISTERED_AT, role))) {
            long registeredAt = resolveRegisteredAt(role);
            editor.putLong(keyForRole(KEY_ROLE_REGISTERED_AT, role), registeredAt);
            editor.putLong(keyForRole(KEY_ROLE_LAST_REVIEWED_AT, role), registeredAt);
            changed = true;
        } else if (!prefs.contains(keyForRole(KEY_ROLE_LAST_REVIEWED_AT, role))) {
            editor.putLong(keyForRole(KEY_ROLE_LAST_REVIEWED_AT, role), prefs.getLong(keyForRole(KEY_ROLE_REGISTERED_AT, role), System.currentTimeMillis()));
            changed = true;
        }
        if (!prefs.contains(keyForRole(KEY_ROLE_REGION_SCOPE, role))) {
            editor.putString(keyForRole(KEY_ROLE_REGION_SCOPE, role), "");
            changed = true;
        }
        if (!prefs.contains(keyForRole(KEY_ROLE_DAILY_LIMIT_SATS, role))) {
            editor.putLong(keyForRole(KEY_ROLE_DAILY_LIMIT_SATS, role), getDefaultDailyLimit(role));
            changed = true;
        }
        if (!prefs.contains(keyForRole(KEY_ROLE_MONTHLY_LIMIT_SATS, role))) {
            editor.putLong(keyForRole(KEY_ROLE_MONTHLY_LIMIT_SATS, role), getDefaultMonthlyLimit(role));
            changed = true;
        }
        if (!prefs.contains(keyForRole(KEY_ROLE_REQUIRES_STEP_UP, role))) {
            editor.putBoolean(keyForRole(KEY_ROLE_REQUIRES_STEP_UP, role), getDefaultRequiresStepUp(role));
            changed = true;
        }
        if (!prefs.contains(KEY_ROLE_PROFILE_VERSION)) {
            editor.putInt(KEY_ROLE_PROFILE_VERSION, ROLE_PROFILE_VERSION);
            changed = true;
        }
        if (changed) {
            editor.apply();
        }
    }

    private long resolveRegisteredAt(int role) {
        long recordedAt;
        switch (role) {
            case ROLE_AGENT:
                recordedAt = prefs.getLong(KEY_ROLE_DATA + "agent_registered_time", 0L);
                break;
            case ROLE_MERCHANT:
                recordedAt = prefs.getLong(KEY_ROLE_DATA + "merchant_registered_time", 0L);
                break;
            default:
                recordedAt = 0L;
                break;
        }
        return recordedAt > 0L ? recordedAt : System.currentTimeMillis();
    }

    private String keyForRole(String prefix, int role) {
        return prefix + role;
    }

    private void appendRegisteredRole(List<Integer> roles, int role) {
        if (isRoleRegistered(role)) {
            roles.add(Integer.valueOf(role));
        }
    }

    private boolean isCapabilityMappedToRole(int role, int capability) {
        return isCapabilityFlag(capability) && (getCapabilityMaskForRole(role) & capability) != 0;
    }

    private boolean isCapabilityFlag(int capability) {
        return capability != 0 && (capability & (capability - 1)) == 0;
    }

    private int getCapabilityMaskForRole(int role) {
        switch (role) {
            case ROLE_USER:
                // BANKING: All roles inherit P2P Bitcoin permissions; limits are enforced at wallet layer
                return CAP_WALLET_VIEW | CAP_WALLET_BACKUP | CAP_VOUCHER_REDEEM | CAP_ROLE_MANAGE
                     | CAP_BITCOIN_SEND | CAP_BITCOIN_RECEIVE;
            case ROLE_AGENT:
                return getCapabilityMaskForRole(ROLE_USER) | CAP_VOUCHER_ISSUE;
            case ROLE_MERCHANT:
                return getCapabilityMaskForRole(ROLE_USER)
                        | CAP_MERCHANT_SETTLEMENT_VIEW
                        | (FeatureFlags.isLightningEnabled() ? CAP_MERCHANT_ACCEPT_LIGHTNING : 0);
            case ROLE_VERIFIER:
                return getCapabilityMaskForRole(ROLE_USER)
                        | CAP_VERIFIER_INSPECT
                        | CAP_VERIFIER_APPROVE_SETTLEMENT
                        | CAP_VERIFIER_RESOLVE_DISPUTE
                        | CAP_RISK_REVIEW_LOCAL;
            default:
                return 0;
        }
    }

    private long getDefaultDailyLimit(int role) {
        switch (role) {
            case ROLE_AGENT:
                return DEFAULT_AGENT_DAILY_LIMIT_SATS;
            case ROLE_MERCHANT:
                return DEFAULT_MERCHANT_DAILY_LIMIT_SATS;
            default:
                return 0L;
        }
    }

    private long getDefaultMonthlyLimit(int role) {
        switch (role) {
            case ROLE_AGENT:
                return DEFAULT_AGENT_MONTHLY_LIMIT_SATS;
            case ROLE_MERCHANT:
                return DEFAULT_MERCHANT_MONTHLY_LIMIT_SATS;
            default:
                return 0L;
        }
    }

    private boolean getDefaultRequiresStepUp(int role) {
        return role == ROLE_AGENT || role == ROLE_MERCHANT;
    }

    private long getDefaultStepUpThreshold(int role) {
        switch (role) {
            case ROLE_AGENT:
                return DEFAULT_AGENT_STEP_UP_THRESHOLD_SATS;
            case ROLE_MERCHANT:
                return DEFAULT_MERCHANT_STEP_UP_THRESHOLD_SATS;
            default:
                return Long.MAX_VALUE;
        }
    }

    private String getDefaultRoleDisplayName(int role) {
        switch (role) {
            case ROLE_USER:
                return "Wallet User";
            case ROLE_AGENT:
                String agentName = getAgentName();
                return agentName == null || agentName.trim().isEmpty() ? "Voucher Agent" : agentName.trim();
            case ROLE_MERCHANT:
                String merchantName = getMerchantName();
                return merchantName == null || merchantName.trim().isEmpty() ? "Merchant" : merchantName.trim();
            case ROLE_VERIFIER:
                return "Community Verifier";
            default:
                return "No Role";
        }
    }

    private String getDefaultRoleDescriptor(int role) {
        switch (role) {
            case ROLE_AGENT:
                return getAgentLocation();
            case ROLE_MERCHANT:
                return getMerchantType();
            default:
                return null;
        }
    }

    private String buildAuthorizationMessage(String reasonContext, String message) {
        if (reasonContext == null || reasonContext.trim().isEmpty()) {
            return message;
        }
        return reasonContext.trim() + ": " + message;
    }

    private void appendRoleLabel(StringBuilder summary, int role, String label) {
        if (!isRoleRegistered(role)) {
            return;
        }
        if (summary.length() > 0) {
            summary.append(" · ");
        }
        summary.append(label);
    }

    public static final class AuthorizationResult {
        public final boolean allowed;
        public final int role;
        public final int capability;
        public final String reasonCode;
        public final String message;

        private AuthorizationResult(boolean allowed, int role, int capability, String reasonCode, String message) {
            this.allowed = allowed;
            this.role = role;
            this.capability = capability;
            this.reasonCode = reasonCode;
            this.message = message;
        }

        public static AuthorizationResult allow(int role, int capability) {
            return new AuthorizationResult(true, role, capability, null, null);
        }

        public static AuthorizationResult deny(int role, int capability, String reasonCode, String message) {
            return new AuthorizationResult(false, role, capability, reasonCode, message);
        }
    }

    public static final class RoleProfile {
        public final int role;
        public final String roleSubjectId;
        public final String participantRootSubjectId;
        public final String displayName;
        public final String descriptor;
        public final String regionScope;
        public final long registeredAt;
        public final long lastReviewedAt;
        public final int status;
        public final int riskTier;
        public final int reputationScore;
        public final long dailyLimitSats;
        public final long monthlyLimitSats;
        public final boolean requiresStepUpForHighValue;
        public final String suspensionReason;

        private RoleProfile(int role,
                String roleSubjectId,
                String participantRootSubjectId,
                String displayName,
                String descriptor,
                String regionScope,
                long registeredAt,
                long lastReviewedAt,
                int status,
                int riskTier,
                int reputationScore,
                long dailyLimitSats,
                long monthlyLimitSats,
                boolean requiresStepUpForHighValue,
                String suspensionReason) {
            this.role = role;
            this.roleSubjectId = roleSubjectId;
            this.participantRootSubjectId = participantRootSubjectId;
            this.displayName = displayName;
            this.descriptor = descriptor;
            this.regionScope = regionScope;
            this.registeredAt = registeredAt;
            this.lastReviewedAt = lastReviewedAt;
            this.status = status;
            this.riskTier = riskTier;
            this.reputationScore = reputationScore;
            this.dailyLimitSats = dailyLimitSats;
            this.monthlyLimitSats = monthlyLimitSats;
            this.requiresStepUpForHighValue = requiresStepUpForHighValue;
            this.suspensionReason = suspensionReason;
        }
    }
}
