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

package org.servalproject.satnet.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import org.servalproject.R;
import org.servalproject.bitcoin.BitcoinWallet;
import org.servalproject.features.FeatureFlags;
import org.servalproject.satnet.SatnetPolicy;
import org.servalproject.satnet.SatnetRoleManager;
import org.servalproject.satnet.SatnetRuntimeConfig;
import org.servalproject.satnet.SatnetStartupGate;

/**
 * SATNET Role Selection Screen
 *
 * Select your primary role:
 * - User (basic wallet holder)
 * - Agent (voucher seller)
 * - Merchant (Lightning payment recipient)
 * - Verifier (community reputation)
 */
public class SatnetRoleSetupActivity extends AppCompatActivity {
    private static final String TAG = "RoleSetup";
    public static final String EXTRA_MANAGE_EXISTING_ROLES = "manage_existing_roles";

    private RadioGroup roleGroup;
    private EditText agentNameInput;
    private EditText agentLocationInput;
    private EditText merchantNameInput;
    private EditText merchantTypeInput;
    private Button nextButton;
    private LinearLayout agentDetails;
    private LinearLayout merchantDetails;
    private TextView stageBadgeText;
    private TextView runtimeStatusText;
    private TextView roleSummaryText;

    private SatnetRoleManager roleManager;
    private RadioButton agentRoleButton;
    private RadioButton merchantRoleButton;
    private RadioButton verifierRoleButton;
    private boolean manageExistingRoles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_satnet_role_setup);

            if (!SatnetPolicy.isBuildPolicyCompliant()) {
                throw new IllegalStateException(SatnetPolicy.getPolicyViolationReason());
            }

            roleManager = new SatnetRoleManager(this);
            manageExistingRoles = getIntent().getBooleanExtra(EXTRA_MANAGE_EXISTING_ROLES, false);

            // Bind UI elements
            roleGroup = findViewById(R.id.role_group);
            agentDetails = findViewById(R.id.agent_details);
            merchantDetails = findViewById(R.id.merchant_details);
            agentNameInput = findViewById(R.id.agent_name_input);
            agentLocationInput = findViewById(R.id.agent_location_input);
            merchantNameInput = findViewById(R.id.merchant_name_input);
            merchantTypeInput = findViewById(R.id.merchant_type_input);
            nextButton = findViewById(R.id.next_button);
            agentRoleButton = findViewById(R.id.role_agent);
            merchantRoleButton = findViewById(R.id.role_merchant);
            verifierRoleButton = findViewById(R.id.role_verifier);
            stageBadgeText = findViewById(R.id.satnet_stage_badge_text);
            runtimeStatusText = findViewById(R.id.satnet_runtime_status_text);
            roleSummaryText = findViewById(R.id.satnet_role_summary_text);

            if (roleGroup == null || agentDetails == null || merchantDetails == null
                    || agentNameInput == null || agentLocationInput == null
                    || merchantNameInput == null || merchantTypeInput == null
                    || nextButton == null || stageBadgeText == null
                    || runtimeStatusText == null || roleSummaryText == null) {
                throw new IllegalStateException("SATNET role setup layout is missing required views");
            }

            // Role selection listener
            roleGroup.setOnCheckedChangeListener((group, checkedId) -> updateUIForRole(checkedId));

            // Next button
            nextButton.setOnClickListener(v -> proceedWithSelectedRole());

            prefillExistingRoleData();
            applyFeatureFlags();
            restoreSelectedRole();
            updateUIForRole(roleGroup.getCheckedRadioButtonId());
            SatnetStartupGate.Status status = refreshRuntimeStatus();
            maybeRouteExistingRole(status);
        } catch (Throwable e) {
            Log.e(TAG, "SATNET role setup failed to initialize", e);
            Toast.makeText(this, "SATNET is not available on this device", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void applyFeatureFlags() {
        if (agentRoleButton != null && roleManager != null && roleManager.isRoleRegistered(SatnetRoleManager.ROLE_AGENT)) {
            agentRoleButton.setText(R.string.satnet_role_agent_registered_label);
        }
        if (!FeatureFlags.isLightningEnabled() && merchantRoleButton != null) {
            merchantRoleButton.setEnabled(false);
            merchantRoleButton.setAlpha(0.5f);
            merchantRoleButton.setText(R.string.satnet_role_merchant_disabled_label);

            if (roleGroup.getCheckedRadioButtonId() == R.id.role_merchant) {
                roleGroup.check(R.id.role_user);
            }
        } else if (merchantRoleButton != null && roleManager != null
                && roleManager.isRoleRegistered(SatnetRoleManager.ROLE_MERCHANT)) {
            merchantRoleButton.setText(R.string.satnet_role_merchant_registered_label);
        }

        if (verifierRoleButton != null && roleManager != null
                && roleManager.isRoleRegistered(SatnetRoleManager.ROLE_VERIFIER)) {
            verifierRoleButton.setText(R.string.satnet_role_verifier_registered_label);
        }
    }

    private void updateUIForRole(int selectedId) {
        // Hide all role-specific inputs
        agentDetails.setVisibility(View.GONE);
        merchantDetails.setVisibility(View.GONE);
        updateRoleSummary(resolveSelectedRole(selectedId));

        // Show relevant inputs based on selection
        if (selectedId == R.id.role_agent) {
            agentDetails.setVisibility(View.VISIBLE);
        } else if (selectedId == R.id.role_merchant) {
            if (!FeatureFlags.isLightningEnabled()) {
                Toast.makeText(this, R.string.satnet_role_merchant_disabled_toast, Toast.LENGTH_SHORT).show();
                roleGroup.check(R.id.role_user);
                return;
            }
            merchantDetails.setVisibility(View.VISIBLE);
        }
    }

    private void proceedWithSelectedRole() {
        try {
            SatnetStartupGate.Status status = refreshRuntimeStatus();
            if (!status.canEnterInteractiveFlows()) {
                Toast.makeText(this, status.getBlockingMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            int selectedId = roleGroup.getCheckedRadioButtonId();
            int selectedRole = resolveSelectedRole(selectedId);

            if (roleManager.isRoleRegistered(selectedRole)) {
                roleManager.switchRole(selectedRole);
                completeRoleSelection();
                return;
            }

            if (selectedId == R.id.role_user) {
                roleManager.registerAsUser();
                completeRoleSelection();

            } else if (selectedId == R.id.role_agent) {
                String agentName = agentNameInput.getText().toString().trim();
                String location = agentLocationInput.getText().toString().trim();

                if (agentName.isEmpty() || location.isEmpty()) {
                    Toast.makeText(this, R.string.satnet_role_fill_all_fields, Toast.LENGTH_SHORT).show();
                    return;
                }

                roleManager.registerAsAgent(agentName, location);
                completeRoleSelection();

            } else if (selectedId == R.id.role_merchant) {
                if (!FeatureFlags.isLightningEnabled()) {
                    Toast.makeText(this, R.string.satnet_role_merchant_disabled_toast, Toast.LENGTH_SHORT).show();
                    return;
                }

                String businessName = merchantNameInput.getText().toString().trim();
                String businessType = merchantTypeInput.getText().toString().trim();

                if (businessName.isEmpty() || businessType.isEmpty()) {
                    Toast.makeText(this, R.string.satnet_role_fill_all_fields, Toast.LENGTH_SHORT).show();
                    return;
                }

                roleManager.registerAsMerchant(businessName, businessType);
                completeRoleSelection();

            } else if (selectedId == R.id.role_verifier) {
                roleManager.registerAsVerifier();
                completeRoleSelection();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private SatnetStartupGate.Status refreshRuntimeStatus() {
        SatnetStartupGate.Status status = SatnetStartupGate.evaluate(this);
        stageBadgeText.setText(status.stageBadge);
        StringBuilder statusText = new StringBuilder(status.startupSummary);
        if (roleManager != null && roleManager.getRegisteredRoles() != SatnetRoleManager.ROLE_NONE) {
            statusText.append("\n\nRegistered roles: ")
                    .append(roleManager.getRegisteredRoleSummary())
                    .append("\nActive role: ")
                    .append(roleManager.getRoleName());
        }
        runtimeStatusText.setText(statusText.toString());
        nextButton.setEnabled(status.canEnterInteractiveFlows());
        updateNextButtonLabel(resolveSelectedRole(roleGroup.getCheckedRadioButtonId()), status.canEnterInteractiveFlows());
        return status;
    }

    private void maybeRouteExistingRole(SatnetStartupGate.Status status) {
        if (manageExistingRoles) {
            return;
        }
        if (!status.canEnterInteractiveFlows()) {
            return;
        }
        if (roleManager == null || roleManager.getActiveRole() == SatnetRoleManager.ROLE_NONE) {
            return;
        }
        startWalletSetupFlow();
    }

    private void restoreSelectedRole() {
        if (roleManager == null || roleGroup == null) {
            return;
        }
        switch (roleManager.getActiveRole()) {
            case SatnetRoleManager.ROLE_AGENT:
                roleGroup.check(R.id.role_agent);
                break;
            case SatnetRoleManager.ROLE_MERCHANT:
                roleGroup.check(R.id.role_merchant);
                break;
            case SatnetRoleManager.ROLE_VERIFIER:
                roleGroup.check(R.id.role_verifier);
                break;
            case SatnetRoleManager.ROLE_USER:
            default:
                roleGroup.check(R.id.role_user);
                break;
        }
    }

    private void prefillExistingRoleData() {
        if (roleManager == null) {
            return;
        }
        if (agentNameInput != null) {
            agentNameInput.setText(roleManager.getAgentName());
        }
        if (agentLocationInput != null) {
            agentLocationInput.setText(roleManager.getAgentLocation());
        }
        if (merchantNameInput != null) {
            merchantNameInput.setText(roleManager.getMerchantName());
        }
        if (merchantTypeInput != null) {
            merchantTypeInput.setText(roleManager.getMerchantType());
        }
    }

    private void startWalletSetupFlow() {
        boolean unlockOnly = false;
        try {
            BitcoinWallet wallet = new BitcoinWallet(this, BitcoinWalletActivity.DEFAULT_WALLET_ID);
            unlockOnly = wallet.hasStoredSeed();
            wallet.clearSensitiveMemory();
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to inspect existing wallet state before routing", e);
        }

        Intent intent = new Intent(this, BitcoinWalletSetupActivity.class);
        intent.putExtra(BitcoinWalletActivity.EXTRA_WALLET_ID, BitcoinWalletActivity.DEFAULT_WALLET_ID);
        intent.putExtra(BitcoinWalletSetupActivity.EXTRA_UNLOCK_ONLY, unlockOnly);
        startActivity(intent);
        finish();
    }

    private void completeRoleSelection() {
        refreshRuntimeStatus();
        if (manageExistingRoles) {
            setResult(RESULT_OK);
            finish();
            return;
        }
        startWalletSetupFlow();
    }

    private int resolveSelectedRole(int selectedId) {
        if (selectedId == R.id.role_agent) {
            return SatnetRoleManager.ROLE_AGENT;
        }
        if (selectedId == R.id.role_merchant) {
            return SatnetRoleManager.ROLE_MERCHANT;
        }
        if (selectedId == R.id.role_verifier) {
            return SatnetRoleManager.ROLE_VERIFIER;
        }
        return SatnetRoleManager.ROLE_USER;
    }

    private void updateRoleSummary(int selectedRole) {
        StringBuilder summary = new StringBuilder(SatnetRuntimeConfig.getRoleSummary(selectedRole));
        if (roleManager != null && roleManager.getRegisteredRoles() != SatnetRoleManager.ROLE_NONE) {
            summary.append("\n\nRegistered roles: ")
                    .append(roleManager.getRegisteredRoleSummary())
                    .append("\nActive role: ")
                    .append(roleManager.getRoleName());
            if (roleManager.isRoleRegistered(selectedRole)) {
                summary.append("\nThis role is already registered and can be re-activated without setup.");
            }
        }
        roleSummaryText.setText(summary.toString());
    }

    private void updateNextButtonLabel(int selectedRole, boolean enabled) {
        if (nextButton == null) {
            return;
        }
        if (!enabled) {
            nextButton.setText(R.string.satnet_role_waiting_for_startup);
            return;
        }
        nextButton.setText(roleManager != null && roleManager.isRoleRegistered(selectedRole)
                ? R.string.satnet_role_continue_existing
                : R.string.satnet_role_register_continue);
    }
}
