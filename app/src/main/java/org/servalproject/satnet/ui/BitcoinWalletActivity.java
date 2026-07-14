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

import android.app.Activity;
import android.content.Intent;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.servalproject.PeerList;
import org.servalproject.R;
import org.servalproject.bitcoin.BitcoinWallet;
import org.servalproject.bitcoin.security.WalletEncryption;
import org.servalproject.features.FeatureFlags;
import org.servalproject.messages.ShowConversationActivity;
import org.servalproject.satnet.SatnetRoleManager;
import org.servalproject.satnet.SatnetRuntimeConfig;
import org.servalproject.satnet.SatnetStartupGate;
import org.servalproject.satnet.WalletSessionStore;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bitcoin Wallet Screen for SATNET AFRICA users.
 *
 * Features:
 * - Display balance
 * - Receive Bitcoin (address)
 * - Backup recovery phrase
 * - Offline signing
 * - Hidden balance mode
 * - Panic mode
 */
public class BitcoinWalletActivity extends AppCompatActivity {
    private static final String TAG = "BitcoinWallet";
    private static final int REQUEST_PICK_CHAT_PEER = 4101;
    private static final String SECURITY_PREFS = "satnet_wallet_security";
    private static final String KEY_PANIC_MODE_PREFIX = "panic_mode_";
    private static final String KEY_BALANCE_HIDDEN_BEFORE_PANIC_PREFIX = "panic_balance_hidden_";
    public static final String DEFAULT_WALLET_ID = "default";
    public static final String EXTRA_WALLET_ID = "wallet_id";

    private BitcoinWallet wallet;
    private TextView balanceText;
    private TextView addressText;
    private TextView walletStageBadgeText;
    private TextView walletRuntimeStatusText;
    private TextView roleActionSummaryText;
    private Button backupButton;
    private Button hideBalanceButton;
    private Button shareAddressChatButton;
    private Button panicModeButton;
    private Button roleActionButton;
    private Button sendBitcoinButton;

    private boolean balanceHidden = false;
    private boolean panicMode = false;
    private boolean balanceHiddenBeforePanic = false;
    private String walletAddress = "";
    private String walletId;
    private String walletSessionToken;
    private SatnetRoleManager roleManager;
    private SharedPreferences securityPrefs;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private boolean walletLoadInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            SatnetUiSupport.applySecureWindow(this);
            setContentView(R.layout.activity_bitcoin_wallet);

            // Initialize wallet
            walletId = getIntent().getStringExtra(EXTRA_WALLET_ID);
            if (walletId == null) {
                walletId = DEFAULT_WALLET_ID;
            }
            securityPrefs = getSharedPreferences(SECURITY_PREFS, MODE_PRIVATE);
            loadPanicModeState();
            walletSessionToken = getIntent().getStringExtra(WalletSessionStore.EXTRA_SESSION_TOKEN);
            wallet = new BitcoinWallet(this, walletId);
            roleManager = new SatnetRoleManager(this);

            // Bind UI elements
            balanceText = SatnetUiSupport.requireView(this, R.id.balance_text, TextView.class, "balance_text");
            addressText = SatnetUiSupport.requireView(this, R.id.address_text, TextView.class, "address_text");
            SatnetUiSupport.requireView(this, R.id.passphrase_display, TextView.class, "passphrase_display");
            walletStageBadgeText = SatnetUiSupport.requireView(this, R.id.wallet_stage_badge_text, TextView.class, "wallet_stage_badge_text");
            walletRuntimeStatusText = SatnetUiSupport.requireView(this, R.id.wallet_runtime_status_text, TextView.class, "wallet_runtime_status_text");
            roleActionSummaryText = SatnetUiSupport.requireView(this, R.id.role_action_summary_text, TextView.class, "role_action_summary_text");
            backupButton = SatnetUiSupport.requireView(this, R.id.backup_button, Button.class, "backup_button");
            hideBalanceButton = SatnetUiSupport.requireView(this, R.id.hide_balance_button, Button.class, "hide_balance_button");
            shareAddressChatButton = SatnetUiSupport.requireView(this, R.id.share_address_chat_button, Button.class, "share_address_chat_button");
            panicModeButton = SatnetUiSupport.requireView(this, R.id.panic_mode_button, Button.class, "panic_mode_button");
            roleActionButton = SatnetUiSupport.requireView(this, R.id.role_action_button, Button.class, "role_action_button");
            sendBitcoinButton = SatnetUiSupport.requireView(this, R.id.send_bitcoin_button, Button.class, "send_bitcoin_button");

            refreshRuntimeStatus();

            // Load wallet data
            loadWallet();

            // Setup click listeners
            backupButton.setOnClickListener(v -> showBackupPhrase());
            hideBalanceButton.setOnClickListener(v -> toggleBalanceVisibility());
            shareAddressChatButton.setOnClickListener(v -> shareAddressInConversation());
            panicModeButton.setOnClickListener(v -> togglePanicMode());
            panicModeButton.setOnLongClickListener(v -> {
                if (walletLoadInProgress) {
                    Toast.makeText(this, R.string.satnet_wallet_loading_toast, Toast.LENGTH_SHORT).show();
                    return true;
                }
                promptPanicWipeConfirmation();
                return true;
            });
            roleActionButton.setOnClickListener(v -> openRoleAction());
            sendBitcoinButton.setOnClickListener(v -> openSendBitcoin());

            // Copy address to clipboard on click
            addressText.setOnClickListener(v -> copyAddressToClipboard());

            configureRoleAction();
            updatePanicModeUi();
        } catch (Throwable t) {
            SatnetUiSupport.failInitialization(this, TAG, t, "Wallet tools are unavailable on this device");
        }
    }

    private void loadWallet() {
        walletLoadInProgress = true;
        setWalletUiEnabled(false);
        balanceText.setText(R.string.satnet_wallet_balance_loading);
        addressText.setText(R.string.satnet_wallet_address_loading);

        backgroundExecutor.execute(() -> {
            try {
                if (!wallet.hasStoredSeed()) {
                    throw new IllegalStateException("Wallet not found, create new wallet");
                }

                char[] walletPin = refreshWalletSessionToken();
                if (walletPin == null || walletPin.length == 0) {
                    runOnUiThread(() -> {
                        if (isFinishing()) {
                            return;
                        }
                        walletLoadInProgress = false;
                        setWalletUiEnabled(true);
                        redirectToUnlockFlow();
                    });
                    return;
                }

                try {
                    wallet.loadEncryptedSeed(walletPin);
                } finally {
                    WalletEncryption.clearChars(walletPin);
                }

                if (!wallet.isInitialized()) {
                    throw new IllegalStateException("Wallet could not be opened");
                }

                final long balanceSats = wallet.getBalanceSats();
                final String address = wallet.getDerivedAddress(0);
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    walletLoadInProgress = false;
                    updateBalanceDisplay(balanceSats);
                    walletAddress = address;
                    updateAddressDisplay();
                    setWalletUiEnabled(true);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    walletLoadInProgress = false;
                    setWalletUiEnabled(true);
                    Toast.makeText(this,
                            getString(R.string.satnet_wallet_open_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void updateBalanceDisplay(long satoshis) {
        if (panicMode) {
            balanceText.setText(R.string.satnet_wallet_zero_balance);
        } else if (balanceHidden) {
            balanceText.setText(R.string.satnet_wallet_hidden_balance);
        } else {
            double btc = satoshis / 100_000_000.0;
            balanceText.setText(String.format(Locale.US, "%.8f BTC (%,d sats)", btc, satoshis));
        }
    }

    private void updateAddressDisplay() {
        if (addressText == null) {
            return;
        }
        if (panicMode) {
            addressText.setText(R.string.satnet_wallet_panic_address_hidden);
            return;
        }
        addressText.setText(walletAddress == null ? "" : walletAddress);
    }

    private void showBackupPhrase() {
        if (panicMode) {
            Toast.makeText(this, R.string.satnet_wallet_panic_blocked_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        // Show modal with 12-word recovery phrase
        // Mark as "verified" once user confirms they saved it
        Toast.makeText(this, R.string.satnet_wallet_backup_notice,
                Toast.LENGTH_LONG).show();

        // TODO: Implement recovery phrase display with verification
    }

    private void toggleBalanceVisibility() {
        if (walletLoadInProgress || !wallet.isInitialized()) {
            Toast.makeText(this, R.string.satnet_wallet_loading_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        if (panicMode) {
            Toast.makeText(this, R.string.satnet_wallet_panic_blocked_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        balanceHidden = !balanceHidden;
        long balance = wallet.getBalanceSats();
        updateBalanceDisplay(balance);

        hideBalanceButton.setText(balanceHidden ? R.string.satnet_wallet_show_balance : R.string.satnet_wallet_hide_balance);
        Toast.makeText(this, balanceHidden ? R.string.satnet_wallet_balance_hidden_notice : R.string.satnet_wallet_balance_visible_notice,
                Toast.LENGTH_SHORT).show();
    }

    private void togglePanicMode() {
        if (walletLoadInProgress) {
            Toast.makeText(this, R.string.satnet_wallet_loading_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        if (panicMode) {
            promptPanicModeDisableConfirmation();
            return;
        }
        promptPanicModeEnableConfirmation();
    }

    private void promptPanicModeEnableConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.satnet_wallet_panic_enable)
                .setMessage(R.string.satnet_wallet_panic_enable_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, which) -> enablePanicMode())
                .show();
    }

    private void enablePanicMode() {
        panicMode = true;
        balanceHiddenBeforePanic = balanceHidden;
        balanceHidden = false;
        persistPanicModeState();
        clearClipboardNow();
        updatePanicModeUi();
        updateAddressDisplay();
        if (wallet != null && wallet.isInitialized()) {
            updateBalanceDisplay(wallet.getBalanceSats());
        }
        Toast.makeText(this, R.string.satnet_wallet_panic_enabled_notice, Toast.LENGTH_SHORT).show();
    }

    private void disablePanicMode() {
        panicMode = false;
        balanceHidden = balanceHiddenBeforePanic;
        persistPanicModeState();
        updatePanicModeUi();
        updateAddressDisplay();
        if (wallet != null && wallet.isInitialized()) {
            updateBalanceDisplay(wallet.getBalanceSats());
        }
        Toast.makeText(this, R.string.satnet_wallet_panic_disabled_notice, Toast.LENGTH_SHORT).show();
    }

    private void promptPanicModeDisableConfirmation() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.satnet_wallet_panic_disable_pin_hint);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.satnet_wallet_panic_disable)
                .setMessage(R.string.satnet_wallet_panic_disable_confirm_message)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        dialog.setOnShowListener(d -> {
            final android.widget.Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                char[] pinChars = null;
                try {
                    String value = input.getText() == null ? "" : input.getText().toString();
                    pinChars = value.toCharArray();
                    if (pinChars.length == 0) {
                        Toast.makeText(this, R.string.satnet_wallet_panic_disable_pin_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    final char[] verificationPin = pinChars;
                    pinChars = null;
                    dialog.dismiss();
                    verifyPinAndDisablePanicMode(verificationPin);
                } finally {
                    WalletEncryption.clearChars(pinChars);
                }
            });
        });

        dialog.show();
    }

    private void verifyPinAndDisablePanicMode(char[] pinChars) {
        backgroundExecutor.execute(() -> {
            try {
                wallet.loadEncryptedSeed(pinChars);
                if (!wallet.isInitialized()) {
                    throw new IllegalStateException("Wallet could not be unlocked");
                }
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    disablePanicMode();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    Toast.makeText(this, R.string.satnet_wallet_panic_disable_pin_failed, Toast.LENGTH_LONG).show();
                });
            } finally {
                WalletEncryption.clearChars(pinChars);
            }
        });
    }

    private void promptPanicWipeConfirmation() {
        if (wallet == null || !wallet.isInitialized()) {
            // Allow wipe even if wallet not initialized locally (attempt to remove stored prefs)
            // Continue to confirmation flow so user explicitly consents
        }

        // Build a confirmation dialog requiring the user to type "WIPE" to confirm
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint(R.string.satnet_wallet_panic_wipe_hint);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.satnet_wallet_panic_button)
                .setMessage(R.string.satnet_wallet_panic_wipe_confirm_message)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, (d, which) -> {
                    // Cancelled
                })
                .setPositiveButton(android.R.string.ok, null)
                .create();

        dialog.setOnShowListener(d -> {
            final android.widget.Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                String vtext = input.getText() == null ? "" : input.getText().toString().trim();
                if (!"WIPE".equalsIgnoreCase(vtext)) {
                    Toast.makeText(this, R.string.satnet_wallet_panic_wipe_mismatch, Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                performPanicWipe();
            });
        });

        dialog.show();
    }

    private void performPanicWipe() {
        // Disable UI while wiping
        setWalletUiEnabled(false);

        backgroundExecutor.execute(() -> {
            try {
                if (wallet != null) {
                    try {
                        wallet.clearQueuedTransactions();
                    } catch (Exception e) {
                        // Non-fatal
                        android.util.Log.w(TAG, "Failed to clear queued transactions during panic wipe: " + e.getMessage());
                    }
                    try {
                        wallet.wipe();
                    } catch (Exception e) {
                        // Non-fatal but log
                        android.util.Log.w(TAG, "Failed to wipe wallet prefs during panic wipe: " + e.getMessage());
                    }
                    wallet.clearSensitiveMemory();
                }

                // Invalidate any active wallet session token to prevent automatic re-use
                try {
                    if (walletSessionToken != null) {
                        WalletSessionStore.invalidate(walletSessionToken);
                        walletSessionToken = null;
                    }
                } catch (Exception e) {
                    android.util.Log.w(TAG, "Failed to invalidate wallet session during panic wipe: " + e.getMessage());
                }
                clearPanicModeState();

                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.satnet_wallet_panic_wiped, Toast.LENGTH_LONG).show();
                    // Redirect to wallet setup/restore flow
                    Intent unlockIntent = new Intent(this, BitcoinWalletSetupActivity.class);
                    unlockIntent.putExtra(EXTRA_WALLET_ID, walletId);
                    startActivity(unlockIntent);
                    finish();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.satnet_wallet_panic_wipe_failed, t.getMessage()), Toast.LENGTH_LONG).show();
                    setWalletUiEnabled(true);
                });
            }
        });
    }

    private void updatePanicModeUi() {
        if (panicModeButton != null) {
            panicModeButton.setText(panicMode ? R.string.satnet_wallet_panic_disable : R.string.satnet_wallet_panic_enable);
        }
        if (hideBalanceButton != null) {
            hideBalanceButton.setText(balanceHidden ? R.string.satnet_wallet_show_balance : R.string.satnet_wallet_hide_balance);
        }
        if (!walletLoadInProgress) {
            setWalletUiEnabled(wallet != null && wallet.isInitialized());
        }
    }

    private void loadPanicModeState() {
        if (securityPrefs == null) {
            return;
        }
        panicMode = securityPrefs.getBoolean(KEY_PANIC_MODE_PREFIX + walletId, false);
        balanceHiddenBeforePanic = securityPrefs.getBoolean(
                KEY_BALANCE_HIDDEN_BEFORE_PANIC_PREFIX + walletId,
                false);
    }

    private void persistPanicModeState() {
        if (securityPrefs == null) {
            return;
        }
        securityPrefs.edit()
                .putBoolean(KEY_PANIC_MODE_PREFIX + walletId, panicMode)
                .putBoolean(KEY_BALANCE_HIDDEN_BEFORE_PANIC_PREFIX + walletId, balanceHiddenBeforePanic)
                .apply();
    }

    private void clearPanicModeState() {
        panicMode = false;
        balanceHiddenBeforePanic = false;
        if (securityPrefs != null) {
            securityPrefs.edit()
                    .remove(KEY_PANIC_MODE_PREFIX + walletId)
                    .remove(KEY_BALANCE_HIDDEN_BEFORE_PANIC_PREFIX + walletId)
                    .apply();
        }
    }

    private void clearClipboardNow() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""));
    }

    private void copyAddressToClipboard() {
        if (walletLoadInProgress) {
            Toast.makeText(this, R.string.satnet_wallet_loading_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        if (panicMode) {
            Toast.makeText(this, R.string.satnet_wallet_panic_blocked_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String address = addressText.getText().toString();
        if (clipboard == null || !SatnetUiSupport.copySensitiveText(
                this,
                getString(R.string.satnet_wallet_address_label),
                address,
                SatnetUiSupport.CLIPBOARD_CLEAR_DELAY_MEDIUM_MS)) {
            Toast.makeText(this, R.string.satnet_wallet_clipboard_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.satnet_wallet_address_copied, Toast.LENGTH_SHORT).show();
    }

    private void shareAddressInConversation() {
        if (walletLoadInProgress || !wallet.isInitialized()) {
            Toast.makeText(this, R.string.satnet_wallet_loading_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        if (panicMode) {
            Toast.makeText(this, R.string.satnet_wallet_panic_blocked_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent pickPeerIntent = new Intent(PeerList.PICK_PEER_INTENT);
        pickPeerIntent.setClass(this, PeerList.class);
        pickPeerIntent.putExtra(PeerList.TITLE, getString(R.string.satnet_wallet_share_chat_picker_title));
        try {
            startActivityForResult(pickPeerIntent, REQUEST_PICK_CHAT_PEER);
        } catch (Exception e) {
            Toast.makeText(this, R.string.satnet_wallet_share_chat_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void configureRoleAction() {
        if (roleActionButton == null) {
            return;
        }

        SatnetStartupGate.Status runtimeStatus = refreshRuntimeStatus();

        if (!runtimeStatus.canUseRoleTools()) {
            roleActionButton.setVisibility(android.view.View.VISIBLE);
            roleActionButton.setEnabled(false);
            roleActionButton.setText(R.string.satnet_wallet_role_action_warming);
            updateRoleActionSummary(R.string.satnet_wallet_role_action_summary_loading);
            return;
        }

        if (roleManager.canActAsVerifier() && !runtimeStatus.canUseVerifierTools()) {
            roleActionButton.setVisibility(android.view.View.VISIBLE);
            roleActionButton.setEnabled(false);
            roleActionButton.setText(R.string.satnet_wallet_role_action_verifier_warming);
            if (roleActionSummaryText != null) {
                roleActionSummaryText.setText(runtimeStatus.getVerifierBlockingMessage());
            }
            return;
        }

        int actionableRole = resolveActionableRole();
        if (actionableRole == SatnetRoleManager.ROLE_AGENT) {
            roleActionButton.setVisibility(android.view.View.VISIBLE);
            roleActionButton.setEnabled(true);
            roleActionButton.setText(R.string.satnet_wallet_role_action_agent);
            updateRoleActionSummary(R.string.satnet_wallet_role_action_summary_agent);
        } else if (actionableRole == SatnetRoleManager.ROLE_MERCHANT) {
            roleActionButton.setVisibility(android.view.View.VISIBLE);
            roleActionButton.setEnabled(FeatureFlags.isLightningEnabled());
            roleActionButton.setText(FeatureFlags.isLightningEnabled()
                    ? R.string.satnet_wallet_role_action_merchant
                    : R.string.satnet_wallet_role_action_merchant_disabled);
            updateRoleActionSummary(R.string.satnet_wallet_role_action_summary_merchant);
        } else if (actionableRole == SatnetRoleManager.ROLE_VERIFIER) {
            roleActionButton.setVisibility(android.view.View.VISIBLE);
            roleActionButton.setEnabled(true);
            roleActionButton.setText(R.string.satnet_wallet_role_action_verifier);
            updateRoleActionSummary(R.string.satnet_wallet_role_action_summary_verifier);
        } else if (actionableRole == SatnetRoleManager.ROLE_USER
                && roleManager.hasCapability(SatnetRoleManager.CAP_VOUCHER_REDEEM)) {
            roleActionButton.setVisibility(android.view.View.VISIBLE);
            roleActionButton.setEnabled(true);
            roleActionButton.setText(R.string.satnet_wallet_role_action_redeem);
            updateRoleActionSummary(R.string.satnet_wallet_role_action_summary_redeem);
        } else {
	            roleActionButton.setVisibility(android.view.View.VISIBLE);
	            roleActionButton.setEnabled(true);
	            roleActionButton.setText(getString(R.string.satnet_wallet_role_action_choose));
	            updateRoleActionSummary(R.string.satnet_wallet_role_action_summary_default);
        }
    }

    private void openRoleAction() {
        if (panicMode) {
            Toast.makeText(this, R.string.satnet_wallet_panic_blocked_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        SatnetStartupGate.Status runtimeStatus = refreshRuntimeStatus();
        if (!runtimeStatus.canUseRoleTools()) {
            Toast.makeText(this, runtimeStatus.getBlockingMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (roleManager.canActAsVerifier() && !runtimeStatus.canUseVerifierTools()) {
            Toast.makeText(this, runtimeStatus.getVerifierBlockingMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (walletLoadInProgress || !wallet.isInitialized()) {
            Toast.makeText(this, R.string.satnet_wallet_loading_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!refreshWalletSessionTokenOnly()) {
            redirectToUnlockFlow();
            return;
        }

        Intent intent;
        int actionableRole = resolveActionableRole();
        if (actionableRole == SatnetRoleManager.ROLE_AGENT) {
            intent = new Intent(this, AgentVoucherActivity.class);
        } else if (actionableRole == SatnetRoleManager.ROLE_MERCHANT) {
            if (!FeatureFlags.isLightningEnabled()) {
                Toast.makeText(this, R.string.satnet_wallet_merchant_disabled_toast, Toast.LENGTH_SHORT).show();
                return;
            }
            intent = new Intent(this, MerchantLightningActivity.class);
        } else if (actionableRole == SatnetRoleManager.ROLE_VERIFIER) {
            intent = new Intent(this, VerifierDashboardActivity.class);
        } else if (actionableRole == SatnetRoleManager.ROLE_USER
                && roleManager.hasCapability(SatnetRoleManager.CAP_VOUCHER_REDEEM)) {
            intent = new Intent(this, VoucherRedemptionActivity.class);
        } else {
            intent = new Intent(this, SatnetRoleSetupActivity.class);
        }

        intent.putExtra(EXTRA_WALLET_ID, walletId);
        if (walletSessionToken != null && !walletSessionToken.isEmpty()) {
            intent.putExtra(WalletSessionStore.EXTRA_SESSION_TOKEN, walletSessionToken);
        }
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.wallet_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_manage_satnet_roles) {
            Intent intent = new Intent(this, SatnetRoleSetupActivity.class);
            intent.putExtra("manage_existing_roles", true);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRuntimeStatus();
        configureRoleAction();
    }

    @Override
    protected void onDestroy() {
        backgroundExecutor.shutdownNow();
        super.onDestroy();
        // Clear only in-memory data during normal lifecycle teardown.
        if (wallet != null) {
            wallet.clearSensitiveMemory();
        }
    }

    private void setWalletUiEnabled(boolean enabled) {
        SatnetStartupGate.Status runtimeStatus = refreshRuntimeStatus();
        if (backupButton != null) {
            backupButton.setEnabled(enabled);
        }
        if (hideBalanceButton != null) {
            hideBalanceButton.setEnabled(enabled);
        }
        if (shareAddressChatButton != null) {
            shareAddressChatButton.setEnabled(enabled);
        }
        if (panicModeButton != null) {
            panicModeButton.setEnabled(enabled);
        }
        if (sendBitcoinButton != null) {
            sendBitcoinButton.setEnabled(enabled);
        }
        if (roleActionButton != null) {
            int actionableRole = resolveActionableRole();
            boolean roleActionAllowed = runtimeStatus.canUseRoleTools() && roleManager != null && (
                    actionableRole == SatnetRoleManager.ROLE_AGENT
                            || (actionableRole == SatnetRoleManager.ROLE_VERIFIER && runtimeStatus.canUseVerifierTools())
                            || actionableRole == SatnetRoleManager.ROLE_USER
                            || (actionableRole == SatnetRoleManager.ROLE_MERCHANT && FeatureFlags.isLightningEnabled()));
            roleActionButton.setEnabled(enabled
                    && roleActionButton.getVisibility() == android.view.View.VISIBLE
                    && roleActionAllowed);
        }
        if (addressText != null) {
            addressText.setEnabled(enabled && !panicMode);
        }
        if (panicMode && enabled) {
            if (backupButton != null) {
                backupButton.setEnabled(false);
            }
            if (hideBalanceButton != null) {
                hideBalanceButton.setEnabled(false);
            }
            if (shareAddressChatButton != null) {
                shareAddressChatButton.setEnabled(false);
            }
            if (sendBitcoinButton != null) {
                sendBitcoinButton.setEnabled(false);
            }
            if (roleActionButton != null) {
                roleActionButton.setEnabled(false);
            }
        }
        configureRoleAction();
        if (panicMode && roleActionButton != null) {
            roleActionButton.setEnabled(false);
        }
    }

    private SatnetStartupGate.Status refreshRuntimeStatus() {
        SatnetStartupGate.Status runtimeStatus = SatnetStartupGate.evaluate(this);
        if (walletStageBadgeText != null) {
            walletStageBadgeText.setText(SatnetRuntimeConfig.getWalletSummary());
        }
        if (walletRuntimeStatusText != null) {
            String statusText = roleManager != null
                    ? SatnetRuntimeConfig.getRoleSummary(roleManager.getActiveRole()) + "\n\n" + runtimeStatus.startupSummary
                    : runtimeStatus.startupSummary;
            walletRuntimeStatusText.setText(statusText);
        }
        return runtimeStatus;
    }

    private void updateRoleActionSummary(int summaryResId) {
        if (roleActionSummaryText != null) {
            roleActionSummaryText.setText(summaryResId);
        }
    }

    private int resolveActionableRole() {
        if (roleManager == null) {
            return SatnetRoleManager.ROLE_NONE;
        }
        int activeRole = roleManager.getActiveRole();
        if (activeRole == SatnetRoleManager.ROLE_AGENT
                && roleManager.hasCapability(SatnetRoleManager.ROLE_AGENT, SatnetRoleManager.CAP_VOUCHER_ISSUE)) {
            return SatnetRoleManager.ROLE_AGENT;
        }
        if (activeRole == SatnetRoleManager.ROLE_MERCHANT
                && roleManager.hasCapability(SatnetRoleManager.ROLE_MERCHANT, SatnetRoleManager.CAP_MERCHANT_ACCEPT_LIGHTNING)) {
            return SatnetRoleManager.ROLE_MERCHANT;
        }
        if (activeRole == SatnetRoleManager.ROLE_VERIFIER
                && roleManager.hasCapability(SatnetRoleManager.ROLE_VERIFIER, SatnetRoleManager.CAP_VERIFIER_INSPECT)) {
            return SatnetRoleManager.ROLE_VERIFIER;
        }
        if (roleManager.hasCapability(SatnetRoleManager.CAP_VOUCHER_REDEEM)) {
            return SatnetRoleManager.ROLE_USER;
        }
        return SatnetRoleManager.ROLE_NONE;
    }

    private char[] refreshWalletSessionToken() {
        WalletSessionStore.SessionAccess sessionAccess = WalletSessionStore.refreshSession(walletSessionToken);
        if (sessionAccess == null) {
            return null;
        }
        try {
            walletSessionToken = sessionAccess.token;
            return sessionAccess.consumePinChars();
        } finally {
            sessionAccess.close();
        }
    }

    private boolean refreshWalletSessionTokenOnly() {
        WalletSessionStore.SessionAccess sessionAccess = WalletSessionStore.refreshSession(walletSessionToken);
        if (sessionAccess == null) {
            return false;
        }
        try {
            walletSessionToken = sessionAccess.token;
            char[] pinChars = sessionAccess.consumePinChars();
            WalletEncryption.clearChars(pinChars);
            return true;
        } finally {
            sessionAccess.close();
        }
    }

    private void redirectToUnlockFlow() {
        Toast.makeText(this, R.string.satnet_wallet_locked_toast, Toast.LENGTH_LONG).show();
        Intent unlockIntent = new Intent(this, BitcoinWalletSetupActivity.class);
        unlockIntent.putExtra(EXTRA_WALLET_ID, walletId);
        unlockIntent.putExtra(BitcoinWalletSetupActivity.EXTRA_UNLOCK_ONLY, true);
        startActivity(unlockIntent);
        finish();
    }

    private void openSendBitcoin() {
        if (walletLoadInProgress || !wallet.isInitialized()) {
            Toast.makeText(this, R.string.satnet_wallet_loading_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        if (panicMode) {
            Toast.makeText(this, R.string.satnet_wallet_panic_blocked_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!refreshWalletSessionTokenOnly()) {
            redirectToUnlockFlow();
            return;
        }

        Intent intent = new Intent(this, SendBitcoinActivity.class);
        intent.putExtra(EXTRA_WALLET_ID, walletId);
        if (walletSessionToken != null && !walletSessionToken.isEmpty()) {
            intent.putExtra(WalletSessionStore.EXTRA_SESSION_TOKEN, walletSessionToken);
        }
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_CHAT_PEER) {
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            Toast.makeText(this, R.string.satnet_wallet_share_chat_cancelled, Toast.LENGTH_SHORT).show();
            return;
        }
        String recipientSid = data.getStringExtra(PeerList.SID);
        if (recipientSid == null || recipientSid.isEmpty()) {
            Toast.makeText(this, R.string.satnet_wallet_share_chat_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent conversationIntent = ShowConversationActivity.createIntent(this, recipientSid);
        conversationIntent.putExtra(ShowConversationActivity.EXTRA_DRAFT_TEXT,
                getString(R.string.satnet_wallet_share_chat_draft, addressText.getText().toString()));
        startActivity(conversationIntent);
        Toast.makeText(this, R.string.satnet_wallet_share_chat_ready, Toast.LENGTH_SHORT).show();
    }
}
