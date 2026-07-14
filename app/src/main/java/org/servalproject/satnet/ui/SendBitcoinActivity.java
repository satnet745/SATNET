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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.servalproject.R;
import org.servalproject.bitcoin.BitcoinWallet;
import org.servalproject.bitcoin.blockchain.EsploraApiClient;
import org.servalproject.bitcoin.security.WalletEncryption;
import org.servalproject.satnet.WalletSessionStore;
import org.servalproject.satnet.SatnetRoleManager;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Send Bitcoin Activity - Direct peer-to-peer Bitcoin transfers
 *
 * Features:
 * - Enter recipient Bitcoin address
 * - Enter amount in BTC or sats
 * - Automatic fee estimation
 * - Fee rate adjustment via slider
 * - Transaction preview
 * - Password confirmation
 */
public class SendBitcoinActivity extends AppCompatActivity {
    private static final String TAG = "SendBitcoin";
    public static final String EXTRA_WALLET_ID = "wallet_id";

    private BitcoinWallet wallet;
    private String walletId;
    private String walletSessionToken;

    private EditText recipientAddressInput;
    private EditText amountInput;
    private TextView amountUnitToggle;
    private SeekBar feeRateSlider;
    private TextView feeRateDisplay;
    private TextView estimatedFeeDisplay;
    private TextView totalCostDisplay;
    private Button estimateFeeButton;
    private Button previewTransactionButton;
    private Button sendButton;

    // Default to satoshis for P2P sends. User can toggle to BTC if they prefer.
    private boolean isAmountInBTC = false;
    private long currentFeeRateSatPerVbyte = 10;
    private List<EsploraApiClient.FeeEstimate> feeEstimates;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            // ========== BANKING AUTHORIZATION GATE ==========
            // Check if current role is authorized for P2P Bitcoin transfers (UI-level authorization)
            // This provides early feedback before wallet operations begin
            SatnetRoleManager roleManager = new SatnetRoleManager(this);
            if (!roleManager.hasCapability(SatnetRoleManager.CAP_BITCOIN_SEND)) {
                Toast.makeText(this, 
                    "Your current role is not authorized for peer-to-peer Bitcoin transfers",
                    Toast.LENGTH_LONG).show();
                android.util.Log.w(TAG, "User attempted to access Send Bitcoin without CAP_BITCOIN_SEND authorization");
                finish();
                return;
            }
            
            // Remember the role that authorized this session (for audit logging)
            int authorizedRole = roleManager.getActiveRole();
            android.util.Log.d(TAG, "P2P Bitcoin send authorized for role: " + authorizedRole);
            // ========== END BANKING AUTHORIZATION GATE ==========

            SatnetUiSupport.applySecureWindow(this);
            setContentView(R.layout.activity_send_bitcoin);

            // Initialize wallet
            walletId = getIntent().getStringExtra(EXTRA_WALLET_ID);
            if (walletId == null) {
                walletId = BitcoinWalletActivity.DEFAULT_WALLET_ID;
            }
            walletSessionToken = getIntent().getStringExtra(WalletSessionStore.EXTRA_SESSION_TOKEN);
            wallet = new BitcoinWallet(this, walletId);

            // Bind UI elements
            recipientAddressInput = SatnetUiSupport.requireView(this, R.id.recipient_address_input, EditText.class, "recipient_address_input");
            amountInput = SatnetUiSupport.requireView(this, R.id.amount_input, EditText.class, "amount_input");
            amountUnitToggle = SatnetUiSupport.requireView(this, R.id.amount_unit_toggle, TextView.class, "amount_unit_toggle");
            feeRateSlider = SatnetUiSupport.requireView(this, R.id.fee_rate_slider, SeekBar.class, "fee_rate_slider");
            feeRateDisplay = SatnetUiSupport.requireView(this, R.id.fee_rate_display, TextView.class, "fee_rate_display");
            estimatedFeeDisplay = SatnetUiSupport.requireView(this, R.id.estimated_fee_display, TextView.class, "estimated_fee_display");
            totalCostDisplay = SatnetUiSupport.requireView(this, R.id.total_cost_display, TextView.class, "total_cost_display");
            estimateFeeButton = SatnetUiSupport.requireView(this, R.id.estimate_fee_button, Button.class, "estimate_fee_button");
            previewTransactionButton = SatnetUiSupport.requireView(this, R.id.preview_transaction_button, Button.class, "preview_transaction_button");
            sendButton = SatnetUiSupport.requireView(this, R.id.send_button, Button.class, "send_button");

            // Setup listeners
            amountUnitToggle.setOnClickListener(v -> toggleAmountUnit());
            feeRateSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    // Map slider (0-100) to fee rate (1-500 sat/vbyte)
                    currentFeeRateSatPerVbyte = 1 + (long) (progress * 4.99);
                    updateFeeDisplay();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            amountInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateTotalCostDisplay();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            estimateFeeButton.setOnClickListener(v -> estimateFees());
            previewTransactionButton.setOnClickListener(v -> previewTransaction());
            sendButton.setOnClickListener(v -> sendTransaction());

            // Initial display
            updateFeeDisplay();
            estimateFees();

        } catch (Throwable t) {
            SatnetUiSupport.failInitialization(this, TAG, t, "Send Bitcoin is unavailable");
        }
    }

     private void toggleAmountUnit() {
         isAmountInBTC = !isAmountInBTC;
         amountUnitToggle.setText(isAmountInBTC ? "BTC" : "sats");
         amountInput.setHint(isAmountInBTC ? getString(R.string.satnet_send_amount_btc_hint) : getString(R.string.satnet_send_amount_sats_hint));
         // Adjust input type: sats -> integer, BTC -> decimal
         if (isAmountInBTC) {
             amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
         } else {
             amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
         }
         updateTotalCostDisplay();
     }

    private void updateFeeDisplay() {
        feeRateDisplay.setText(String.format(Locale.US, "%d sat/vB", currentFeeRateSatPerVbyte));
        updateTotalCostDisplay();
    }

    private void updateTotalCostDisplay() {
        try {
            String amountStr = amountInput.getText().toString().trim();
            if (amountStr.isEmpty()) {
                totalCostDisplay.setText("");
                estimatedFeeDisplay.setText("");
                return;
            }

            long amountSats = parseAmount(amountStr);
            // Estimate fee: ~250 vbytes for a simple transaction with one input/output
            long estimatedFee = 250 * currentFeeRateSatPerVbyte;
            long totalCost = amountSats + estimatedFee;

            estimatedFeeDisplay.setText(String.format(Locale.US, "Est. Fee: %,d sats", estimatedFee));
            double totalBTC = totalCost / 100_000_000.0;
            totalCostDisplay.setText(String.format(Locale.US, "Total: %.8f BTC (%,d sats)", totalBTC, totalCost));
        } catch (NumberFormatException e) {
            estimatedFeeDisplay.setText("");
            totalCostDisplay.setText("");
        }
    }

    private void estimateFees() {
        estimateFeeButton.setEnabled(false);
        estimateFeeButton.setText(R.string.satnet_send_estimating_fees);

        backgroundExecutor.execute(() -> {
             try {
                 EsploraApiClient apiClient = new EsploraApiClient(wallet.getNetworkParams());
                 feeEstimates = apiClient.getFeeEstimates();

                runOnUiThread(() -> {
                    estimateFeeButton.setEnabled(true);
                    estimateFeeButton.setText(R.string.satnet_send_estimate_fee_button);

                    // Set slider to recommended 6-block target
                    if (feeEstimates != null && !feeEstimates.isEmpty()) {
                        for (EsploraApiClient.FeeEstimate estimate : feeEstimates) {
                            if (estimate.targetBlocks == 6) {
                                // Map fee rate back to slider position (0-100)
                                int sliderPos = (int) ((estimate.satPerVbyte - 1) / 4.99);
                                sliderPos = Math.max(0, Math.min(100, sliderPos));
                                feeRateSlider.setProgress(sliderPos);
                                break;
                            }
                        }
                    }

                    Toast.makeText(SendBitcoinActivity.this, R.string.satnet_send_fees_updated, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    estimateFeeButton.setEnabled(true);
                    estimateFeeButton.setText(R.string.satnet_send_estimate_fee_button);
                    Toast.makeText(SendBitcoinActivity.this,
                            getString(R.string.satnet_send_fee_estimation_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void previewTransaction() {
        try {
            String recipientAddress = recipientAddressInput.getText().toString().trim();
            String amountStr = amountInput.getText().toString().trim();

            if (recipientAddress.isEmpty()) {
                Toast.makeText(this, R.string.satnet_send_enter_recipient, Toast.LENGTH_SHORT).show();
                return;
            }

            if (amountStr.isEmpty()) {
                Toast.makeText(this, R.string.satnet_send_enter_amount, Toast.LENGTH_SHORT).show();
                return;
            }

            long amountSats = parseAmount(amountStr);

            // Show preview dialog
            TransactionPreviewDialog dialog = new TransactionPreviewDialog(
                    this,
                    recipientAddress,
                    amountSats,
                    currentFeeRateSatPerVbyte,
                    walletId,
                    walletSessionToken
            );
            dialog.show();

        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.satnet_send_preview_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void sendTransaction() {
        try {
            String recipientAddress = recipientAddressInput.getText().toString().trim();
            String amountStr = amountInput.getText().toString().trim();

            if (recipientAddress.isEmpty() || amountStr.isEmpty()) {
                Toast.makeText(this, R.string.satnet_send_incomplete_form, Toast.LENGTH_SHORT).show();
                return;
            }

            long amountSats = parseAmount(amountStr);

            // Show password dialog for transaction signing
            PasswordConfirmationDialog dialog = new PasswordConfirmationDialog(
                    this,
                    (password) -> executeTransaction(recipientAddress, amountSats, password)
            );
            dialog.show();

        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.satnet_send_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void executeTransaction(String recipientAddress, long amountSats, String password) {
        sendButton.setEnabled(false);
        sendButton.setText(R.string.satnet_send_broadcasting);

        backgroundExecutor.execute(() -> {
            try {
                char[] passwordChars = password.toCharArray();
                try {
                    // Load wallet
                    if (!wallet.hasStoredSeed()) {
                        throw new IllegalStateException("Wallet not found");
                    }
                    wallet.loadEncryptedSeed(passwordChars);

                    // Create and sign transaction
                    BitcoinWallet.SendTransactionResult txResult =
                            wallet.createAndSignTransaction(recipientAddress, amountSats, currentFeeRateSatPerVbyte, passwordChars);

                    // Broadcast transaction
                    String txid = wallet.broadcastTransaction(txResult.signedTxHex);

                    runOnUiThread(() -> {
                        sendButton.setEnabled(true);
                        sendButton.setText(R.string.satnet_send_button);

                        // Show success
                        TransactionSuccessDialog dialog = new TransactionSuccessDialog(this, txid, txResult);
                        dialog.show();

                        // Clear form
                        recipientAddressInput.setText("");
                        amountInput.setText("");
                    });

                } finally {
                    WalletEncryption.clearChars(passwordChars);
                    wallet.clearSensitiveMemory();
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    sendButton.setEnabled(true);
                    sendButton.setText(R.string.satnet_send_button);
                    Toast.makeText(SendBitcoinActivity.this,
                            getString(R.string.satnet_send_broadcast_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private long parseAmount(String amountStr) throws NumberFormatException {
        // Trim and normalize
        String s = amountStr.trim();
        if (s.isEmpty()) throw new NumberFormatException("Empty amount");

        if (isAmountInBTC) {
            // Parse as decimal BTC and convert to satoshis with rounding to nearest satoshi
            java.math.BigDecimal btc = new java.math.BigDecimal(s);
            java.math.BigDecimal sats = btc.multiply(new java.math.BigDecimal(100_000_000L));
            try {
                return sats.longValueExact();
            } catch (ArithmeticException e) {
                // If not an exact integral number of satoshis, round to nearest satoshi
                return sats.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
            }
        } else {
            // Expect an integer number of satoshis (no decimals)
            if (s.contains(".") || s.contains(",")) {
                throw new NumberFormatException("Amount in sats must be an integer; toggle to BTC to enter fractional amounts");
            }
            return Long.parseLong(s);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.send_bitcoin_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_paste_address) {
            pasteAddressFromClipboard();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void pasteAddressFromClipboard() {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                android.content.ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    String address = clip.getItemAt(0).getText().toString();
                    recipientAddressInput.setText(address);
                    Toast.makeText(this, R.string.satnet_send_address_pasted, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.satnet_send_clipboard_error, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        backgroundExecutor.shutdownNow();
        super.onDestroy();
        if (wallet != null) {
            wallet.clearSensitiveMemory();
        }
    }
}

