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

import android.util.Log;

import org.servalproject.voucher.VoucherParticipantSnapshot;

/**
 * Combined Authorization Engine for SATNET
 *
 * Evaluates both:
 * 1. Role-based capabilities (via SatnetRoleManager)
 * 2. Conflict-of-interest policies (via SatnetRoleConflictPolicy)
 *
 * Designed to provide comprehensive authorization decisions with detailed audit trails.
 */
public final class SatnetAuthorizationEngine {
    private static final String TAG = "SatnetAuthEngine";

    private SatnetAuthorizationEngine() {
    }

    /**
     * Perform full authorization check combining role capabilities and conflict policies
     */
    public static AuthorizationDecision authorize(
            SatnetRoleManager roleManager,
            int actionType,
            int actorRole,
            VoucherParticipantSnapshot participantSnapshot,
            String reasonContext) {

        if (roleManager == null) {
            return AuthorizationDecision.deny("NULL_ROLE_MANAGER", "Role manager not available", null, null);
        }

        // Step 1: Check role-based capability
        SatnetRoleManager.AuthorizationResult roleAuth = roleManager.authorize(
                actionType == SatnetRoleConflictPolicy.ACTION_ISSUE_VOUCHER
                        ? SatnetRoleManager.CAP_VOUCHER_ISSUE
                        : actionType == SatnetRoleConflictPolicy.ACTION_REDEEM_VOUCHER
                        ? SatnetRoleManager.CAP_VOUCHER_REDEEM
                        : actionType == SatnetRoleConflictPolicy.ACTION_ACCEPT_MERCHANT_PAYMENT
                        ? SatnetRoleManager.CAP_MERCHANT_ACCEPT_LIGHTNING
                        : actionType == SatnetRoleConflictPolicy.ACTION_INSPECT_VOUCHER
                        ? SatnetRoleManager.CAP_VERIFIER_INSPECT
                        : actionType == SatnetRoleConflictPolicy.ACTION_VERIFY_SETTLEMENT
                        ? SatnetRoleManager.CAP_VERIFIER_APPROVE_SETTLEMENT
                        : actionType == SatnetRoleConflictPolicy.ACTION_RESOLVE_DISPUTE
                        ? SatnetRoleManager.CAP_VERIFIER_RESOLVE_DISPUTE
                        : 0,
                reasonContext);

        if (!roleAuth.allowed) {
            Log.d(TAG, "Authorization denied at role level: " + roleAuth.reasonCode);
            return AuthorizationDecision.deny(roleAuth.reasonCode, roleAuth.message, roleAuth, null);
        }

        // Step 2: Check conflict-of-interest policies
        SatnetRoleConflictPolicy.ConflictCheck conflictCheck = SatnetRoleConflictPolicy.authorizeAction(
                roleManager,
                actionType,
                actorRole,
                participantSnapshot);

        if (!conflictCheck.allowed) {
            Log.d(TAG, "Authorization denied at conflict policy level: " + conflictCheck.reasonCode);
            return AuthorizationDecision.deny(conflictCheck.reasonCode, conflictCheck.message, roleAuth, conflictCheck);
        }

        Log.d(TAG, "Authorization granted for action: " + actionType);
        return AuthorizationDecision.allow(roleAuth, conflictCheck);
    }

    /**
     * Combined authorization decision result
     */
    public static final class AuthorizationDecision {
        public final boolean allowed;
        public final String reasonCode;
        public final String message;
        public final SatnetRoleManager.AuthorizationResult roleAuthorization;
        public final SatnetRoleConflictPolicy.ConflictCheck conflictCheck;
        public final long decidedAt;

        private AuthorizationDecision(
                boolean allowed,
                String reasonCode,
                String message,
                SatnetRoleManager.AuthorizationResult roleAuthorization,
                SatnetRoleConflictPolicy.ConflictCheck conflictCheck) {
            this.allowed = allowed;
            this.reasonCode = reasonCode;
            this.message = message;
            this.roleAuthorization = roleAuthorization;
            this.conflictCheck = conflictCheck;
            this.decidedAt = System.currentTimeMillis();
        }

        public static AuthorizationDecision allow(
                SatnetRoleManager.AuthorizationResult roleAuth,
                SatnetRoleConflictPolicy.ConflictCheck conflictCheck) {
            return new AuthorizationDecision(true, null, null, roleAuth, conflictCheck);
        }

        public static AuthorizationDecision deny(
                String reasonCode,
                String message,
                SatnetRoleManager.AuthorizationResult roleAuth,
                SatnetRoleConflictPolicy.ConflictCheck conflictCheck) {
            return new AuthorizationDecision(false, reasonCode, message, roleAuth, conflictCheck);
        }

        /**
         * Get the effective recommended risk state from both layers
         */
        public int getRecommendedRiskState() {
            if (conflictCheck != null && conflictCheck.recommendedRiskState > 0) {
                return conflictCheck.recommendedRiskState;
            }
            return 0;
        }

        /**
         * Whether this denial should trigger risk event recording
         */
        public boolean shouldRecordRiskEvent() {
            return !allowed && (conflictCheck == null || conflictCheck.shouldRecordRiskEvent);
        }
    }
}

