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
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import org.servalproject.R;
import org.servalproject.bitcoin.BitcoinWallet;
import org.servalproject.bitcoin.security.WalletEncryption;
import org.servalproject.satnet.SatnetRuntimeConfig;
import org.servalproject.satnet.SatnetStartupGate;
import org.servalproject.satnet.WalletSessionStore;

import java.util.List;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bitcoin Wallet Setup/Import Screen
 *
 * Setup Options:
 * - Create new wallet (generate 12-word phrase)
 * - Import existing wallet (enter 12-word phrase)
 * - Restore from backup
 */
public class BitcoinWalletSetupActivity extends AppCompatActivity {
    private static final String TAG = "WalletSetup";
    public static final String EXTRA_UNLOCK_ONLY = "unlock_only";

    private RadioGroup setupMode;
    private EditText mnemonicInput;
    private TextView generatedPhrase;
    private EditText pinInput;
    private EditText pinConfirmInput;
    private Button nextButton;
    private Button copyButton;
    private LinearLayout createSection;
    private LinearLayout importSection;
    private TextView stageBadgeText;
    private TextView runtimeStatusText;
    private TextView screenTitleText;
    private TextView screenSubtitleText;
    private TextView pinSectionTitleText;

    private BitcoinWallet wallet;
    private String generatedMnemonic;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private boolean isBusy = false;
    private int mnemonicGenerationToken = 0;
    private boolean unlockOnly = false;
    private String walletId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            SatnetUiSupport.applySecureWindow(this);
            setContentView(R.layout.activity_bitcoin_wallet_setup);

            walletId = getIntent().getStringExtra(BitcoinWalletActivity.EXTRA_WALLET_ID);
            if (walletId == null || walletId.trim().isEmpty()) {
                walletId = BitcoinWalletActivity.DEFAULT_WALLET_ID;
            }
            unlockOnly = getIntent().getBooleanExtra(EXTRA_UNLOCK_ONLY, false);
            wallet = new BitcoinWallet(this, walletId);

            // Bind UI elements
            setupMode = findViewById(R.id.setup_mode);
            createSection = findViewById(R.id.create_section);
            importSection = findViewById(R.id.import_section);
            mnemonicInput = findViewById(R.id.mnemonic_input);
            generatedPhrase = findViewById(R.id.generated_phrase);
            pinInput = findViewById(R.id.pin_input);
            pinConfirmInput = findViewById(R.id.pin_confirm_input);
            nextButton = findViewById(R.id.next_button);
            copyButton = findViewById(R.id.copy_button);
            stageBadgeText = findViewById(R.id.wallet_setup_stage_badge_text);
            runtimeStatusText = findViewById(R.id.wallet_setup_runtime_status_text);
            screenTitleText = findViewById(R.id.wallet_setup_screen_title);
            screenSubtitleText = findViewById(R.id.wallet_setup_screen_subtitle);
            pinSectionTitleText = findViewById(R.id.wallet_pin_section_title);

            if (setupMode == null || createSection == null || importSection == null
                    || mnemonicInput == null || generatedPhrase == null
                    || pinInput == null || pinConfirmInput == null
                    || nextButton == null || copyButton == null
                    || stageBadgeText == null || runtimeStatusText == null
                    || screenTitleText == null || screenSubtitleText == null || pinSectionTitleText == null) {
                throw new IllegalStateException("Wallet setup layout is missing required views");
            }

            // Mode selection listener
            setupMode.setOnCheckedChangeListener((group, checkedId) -> updateUIForMode(checkedId));

            // Copy button for generated phrase
            copyButton.setOnClickListener(v -> copyPhraseToClipboard());

            // Next button
            nextButton.setOnClickListener(v -> proceedWithSetup());

            if (unlockOnly) {
                configureUnlockMode();
            } else {
                // Initialize UI
                updateUIForMode(setupMode.getCheckedRadioButtonId());
            }
            refreshRuntimeStatus();
        } catch (Throwable e) {
            Log.e(TAG, "Wallet setup failed to initialize", e);
            Toast.makeText(this, "Wallet setup is unavailable on this device", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void configureUnlockMode() {
        if (!wallet.hasStoredSeed()) {
            unlockOnly = false;
            updateUIForMode(setupMode.getCheckedRadioButtonId());
            return;
        }

        setTitle(R.string.satnet_activity_label_wallet_unlock);
        if (screenTitleText != null) {
            screenTitleText.setText(R.string.satnet_wallet_unlock_title);
        }
        if (screenSubtitleText != null) {
            screenSubtitleText.setText(R.string.satnet_wallet_unlock_subtitle);
        }
        if (pinSectionTitleText != null) {
            pinSectionTitleText.setText(R.string.satnet_wallet_unlock_pin_title);
        }

        if (setupMode != null) {
            setupMode.setVisibility(View.GONE);
        }
        if (createSection != null) {
            createSection.setVisibility(View.GONE);
        }
        if (importSection != null) {
            importSection.setVisibility(View.GONE);
        }
        if (copyButton != null) {
            copyButton.setVisibility(View.GONE);
            copyButton.setEnabled(false);
        }
        if (generatedPhrase != null) {
            generatedPhrase.setText("");
        }
        if (pinInput != null) {
            pinInput.setHint(R.string.satnet_wallet_setup_pin_unlock_hint);
        }
        if (pinConfirmInput != null) {
            pinConfirmInput.setText("");
            pinConfirmInput.setVisibility(View.GONE);
        }
        if (nextButton != null) {
            nextButton.setText(R.string.satnet_wallet_setup_unlock_button);
        }
        updateBusyState(false);
    }

    private void updateUIForMode(int selectedId) {
        setTitle(R.string.satnet_activity_label_wallet_setup);
        if (screenTitleText != null) {
            screenTitleText.setText(R.string.satnet_wallet_setup_title);
        }
        if (screenSubtitleText != null) {
            screenSubtitleText.setText(R.string.satnet_wallet_setup_subtitle);
        }
        if (pinSectionTitleText != null) {
            pinSectionTitleText.setText(R.string.satnet_wallet_setup_pin_title);
        }

        mnemonicGenerationToken++;
        createSection.setVisibility(View.GONE);
        importSection.setVisibility(View.GONE);

        if (selectedId == R.id.mode_create) {
            createSection.setVisibility(View.VISIBLE);
            nextButton.setText(R.string.satnet_wallet_setup_create_button);
            copyButton.setEnabled(false);
            generatedPhrase.setText(R.string.satnet_wallet_setup_generating_phrase);
            generateMnemonicAsync(mnemonicGenerationToken);

        } else if (selectedId == R.id.mode_import) {
            importSection.setVisibility(View.VISIBLE);
            mnemonicInput.setHint(R.string.satnet_wallet_setup_import_hint);
            nextButton.setText(R.string.satnet_wallet_setup_import_button);
            generatedMnemonic = null;
            copyButton.setEnabled(false);
        }

        // PIN input always visible
        pinInput.setVisibility(View.VISIBLE);
        pinConfirmInput.setVisibility(View.VISIBLE);
        updateBusyState(isBusy);
    }

    private void copyPhraseToClipboard() {
        if (generatedMnemonic == null || generatedMnemonic.trim().isEmpty()) {
            Toast.makeText(this, "Recovery phrase is still being prepared", Toast.LENGTH_SHORT).show();
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.satnet_wallet_clipboard_warning_title)
                .setMessage(R.string.satnet_wallet_clipboard_warning_recovery_phrase)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.satnet_wallet_clipboard_warning_confirm, (dialog, which) -> {
                    boolean copied = SatnetUiSupport.copySensitiveText(
                            this,
                            getString(R.string.satnet_wallet_recovery_phrase_label),
                            generatedMnemonic,
                            SatnetUiSupport.CLIPBOARD_CLEAR_DELAY_SHORT_MS);
                    Toast.makeText(this,
                            copied
                                    ? R.string.satnet_wallet_recovery_phrase_copied
                                    : R.string.satnet_wallet_clipboard_unavailable,
                            Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void proceedWithSetup() {
        char[] pinChars = null;
        char[] pinConfirmChars = null;
        try {
            SatnetStartupGate.Status status = refreshRuntimeStatus();
            if (!status.canEnterInteractiveFlows()) {
                Toast.makeText(this, status.getBlockingMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            pinChars = readInputChars(pinInput);
            pinConfirmChars = readInputChars(pinConfirmInput);

            // Validate PIN
            if (pinChars.length == 0) {
                Toast.makeText(this, unlockOnly ? "Please enter your PIN" : "Please set a PIN", Toast.LENGTH_SHORT).show();
                return;
            }

            if (unlockOnly) {
                runWalletUnlockAsync(pinChars);
                pinChars = null;
                return;
            }

            if (!Arrays.equals(pinChars, pinConfirmChars)) {
                Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            int selectedMode = setupMode.getCheckedRadioButtonId();

            if (selectedMode == R.id.mode_create) {
                // Use generated phrase
                if (generatedMnemonic == null) {
                    Toast.makeText(this, "Recovery phrase is still being generated", Toast.LENGTH_SHORT).show();
                    return;
                }
                runWalletSetupAsync(generatedMnemonic, pinChars);
                pinChars = null;

            } else if (selectedMode == R.id.mode_import) {
                // Use entered phrase
                String mnemonic = mnemonicInput.getText().toString().trim();

                if (mnemonic.isEmpty()) {
                    Toast.makeText(this, "Please enter recovery phrase", Toast.LENGTH_SHORT).show();
                    return;
                }

                runWalletSetupAsync(mnemonic, pinChars);
                pinChars = null;
            }

        } catch (Throwable e) {
            Log.e(TAG, "Wallet setup failed", e);
            String message = e.getMessage();
            Toast.makeText(this,
                    message == null || message.trim().isEmpty() ? "Wallet setup failed" : "Error: " + message,
                    Toast.LENGTH_LONG).show();
        } finally {
            WalletEncryption.clearChars(pinChars);
            WalletEncryption.clearChars(pinConfirmChars);
        }
    }

    private void generateMnemonicAsync(final int generationToken) {
        updateBusyState(true);
        backgroundExecutor.execute(() -> {
            try {
                List<String> mnemonicList = wallet.generateNewMnemonic();
                final String mnemonic = android.text.TextUtils.join(" ", mnemonicList);
                runOnUiThread(() -> {
                    if (isFinishing() || generationToken != mnemonicGenerationToken
                            || setupMode.getCheckedRadioButtonId() != R.id.mode_create) {
                        return;
                    }
                    generatedMnemonic = mnemonic;
                    generatedPhrase.setText(generatedMnemonic);
                    copyButton.setEnabled(true);
                    updateBusyState(false);
                });
            } catch (Throwable e) {
                Log.e(TAG, "Failed to generate mnemonic", e);
                runOnUiThread(() -> {
                    if (isFinishing() || generationToken != mnemonicGenerationToken) {
                        return;
                    }
                    generatedMnemonic = null;
                    generatedPhrase.setText("");
                    copyButton.setEnabled(false);
                    updateBusyState(false);
                    Toast.makeText(this, "Error generating phrase", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void runWalletSetupAsync(final String mnemonic, final char[] pinChars) {
        updateBusyState(true);
        backgroundExecutor.execute(() -> {
            try {
                wallet.importFromMnemonic(mnemonic, pinChars);
                final String sessionToken = WalletSessionStore.createSession(pinChars);
                WalletEncryption.clearChars(pinChars);
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    updateBusyState(false);
                    Toast.makeText(this, "Wallet created successfully", Toast.LENGTH_SHORT).show();
                    openWallet(sessionToken);
                });
            } catch (Throwable e) {
                WalletEncryption.clearChars(pinChars);
                Log.e(TAG, "Wallet setup failed", e);
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    updateBusyState(false);
                    String message = e.getMessage();
                    Toast.makeText(this,
                            message == null || message.trim().isEmpty() ? "Wallet setup failed" : "Error: " + message,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void runWalletUnlockAsync(final char[] pinChars) {
        updateBusyState(true);
        backgroundExecutor.execute(() -> {
            try {
                if (!wallet.hasStoredSeed()) {
                    throw new IllegalStateException("No wallet found. Create or import a wallet first.");
                }
                wallet.loadEncryptedSeed(pinChars);
                if (!wallet.isInitialized()) {
                    throw new IllegalStateException("Wallet could not be unlocked");
                }
                final String sessionToken = WalletSessionStore.createSession(pinChars);
                WalletEncryption.clearChars(pinChars);
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    updateBusyState(false);
                    openWallet(sessionToken);
                });
            } catch (Throwable e) {
                WalletEncryption.clearChars(pinChars);
                Log.e(TAG, "Wallet unlock failed", e);
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    updateBusyState(false);
                    String message = e.getMessage();
                    Toast.makeText(this,
                            message == null || message.trim().isEmpty() ? "Unable to unlock wallet" : "Error: " + message,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openWallet(String sessionToken) {
        Intent intent = new Intent(this, BitcoinWalletActivity.class);
        intent.putExtra(BitcoinWalletActivity.EXTRA_WALLET_ID, walletId);
        intent.putExtra(WalletSessionStore.EXTRA_SESSION_TOKEN, sessionToken);
        startActivity(intent);
        finish();
    }

    private char[] readInputChars(EditText editText) {
        if (editText == null || editText.getText() == null) {
            return new char[0];
        }
        return editText.getText().toString().toCharArray();
    }

    private void updateBusyState(boolean busy) {
        isBusy = busy;
        SatnetStartupGate.Status status = refreshRuntimeStatus();
        boolean runtimeReady = status.canEnterInteractiveFlows();
        boolean createMode = !unlockOnly && setupMode != null && setupMode.getCheckedRadioButtonId() == R.id.mode_create;
        if (setupMode != null) {
            setupMode.setEnabled(!busy && !unlockOnly && runtimeReady);
            for (int i = 0; i < setupMode.getChildCount(); i++) {
                setupMode.getChildAt(i).setEnabled(!busy && !unlockOnly && runtimeReady);
            }
        }
        if (mnemonicInput != null) {
            mnemonicInput.setEnabled(!busy && !unlockOnly && runtimeReady);
        }
        if (pinInput != null) {
            pinInput.setEnabled(!busy && runtimeReady);
        }
        if (pinConfirmInput != null) {
            pinConfirmInput.setEnabled(!busy && !unlockOnly && runtimeReady);
        }
        if (copyButton != null) {
            copyButton.setEnabled(!busy && runtimeReady && createMode && generatedMnemonic != null);
        }
        if (nextButton != null) {
            nextButton.setEnabled(!busy && runtimeReady && (unlockOnly || !createMode || generatedMnemonic != null));
            nextButton.setText(busy
                    ? (unlockOnly ? "Unlocking Wallet…" : (createMode ? "Preparing Wallet…" : "Importing Wallet…"))
                    : (unlockOnly ? "Unlock Wallet" : (createMode ? "Create Wallet" : "Import Wallet")));
        }
    }

    private SatnetStartupGate.Status refreshRuntimeStatus() {
        SatnetStartupGate.Status status = SatnetStartupGate.evaluate(this);
        if (stageBadgeText != null) {
            stageBadgeText.setText(SatnetRuntimeConfig.getWalletSummary());
        }
        if (runtimeStatusText != null) {
            runtimeStatusText.setText(status.startupSummary);
        }
        return status;
    }

    @Override
    protected void onDestroy() {
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }
}
