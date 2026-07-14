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

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.servalproject.R;
import org.servalproject.bitcoin.BitcoinWallet;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Transaction Preview Dialog - Shows transaction details before sending
 */
public class TransactionPreviewDialog extends Dialog {
    private final Context context;
    private final String recipientAddress;
    private final long amountSats;
    private final long feeRateSatPerVbyte;
    private final String walletId;
    private final String walletSessionToken;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ScrollView contentScroll;
    private TextView transactionDetailsText;
    private Button confirmButton;
    private Button cancelButton;

    public TransactionPreviewDialog(Context context, String recipientAddress, long amountSats,
                                   long feeRateSatPerVbyte, String walletId, String walletSessionToken) {
        super(context);
        this.context = context;
        this.recipientAddress = recipientAddress;
        this.amountSats = amountSats;
        this.feeRateSatPerVbyte = feeRateSatPerVbyte;
        this.walletId = walletId;
        this.walletSessionToken = walletSessionToken;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_transaction_preview);

        contentScroll = findViewById(R.id.transaction_preview_scroll);
        transactionDetailsText = findViewById(R.id.transaction_details_text);
        confirmButton = findViewById(R.id.confirm_button);
        cancelButton = findViewById(R.id.cancel_button);

        cancelButton.setOnClickListener(v -> dismiss());
        confirmButton.setOnClickListener(v -> {
            confirmButton.setEnabled(false);
            confirmButton.setText(R.string.satnet_send_loading);
            confirmTransaction();
        });

        loadTransactionPreview();
    }

    private void loadTransactionPreview() {
        transactionDetailsText.setText(R.string.satnet_send_loading_preview);

        executor.execute(() -> {
            try {
                BitcoinWallet wallet = new BitcoinWallet(context, walletId);

                double amountBTC = amountSats / 100_000_000.0;
                long estimatedFee = 250 * feeRateSatPerVbyte; // Estimated transaction size
                double feeBTC = estimatedFee / 100_000_000.0;
                long totalCost = amountSats + estimatedFee;
                double totalBTC = totalCost / 100_000_000.0;

                String details = String.format(Locale.US,
                        "Transaction Preview\n" +
                        "====================\n\n" +
                        "Recipient Address:\n%s\n\n" +
                        "Amount:\n%.8f BTC (%,d sats)\n\n" +
                        "Fee Rate:\n%d sat/vB\n\n" +
                        "Estimated Fee:\n%.8f BTC (%,d sats)\n\n" +
                        "Total Cost:\n%.8f BTC (%,d sats)\n\n" +
                        "Network:\n%s\n\n" +
                        "Note: This is an estimate. Actual fee may vary based on network conditions.",
                        recipientAddress,
                        amountBTC, amountSats,
                        feeRateSatPerVbyte,
                        feeBTC, estimatedFee,
                        totalBTC, totalCost,
                        wallet.isTestnet() ? "Bitcoin Testnet" : "Bitcoin Mainnet"
                );

                ((android.app.Activity) context).runOnUiThread(() -> {
                    transactionDetailsText.setText(details);
                    confirmButton.setEnabled(true);
                });

            } catch (Exception e) {
                ((android.app.Activity) context).runOnUiThread(() -> {
                    transactionDetailsText.setText("Error loading preview:\n" + e.getMessage());
                    confirmButton.setEnabled(false);
                });
            }
        });
    }

    private void confirmTransaction() {
        PasswordConfirmationDialog passwordDialog = new PasswordConfirmationDialog(
                context,
                password -> {
                    ((android.app.Activity) context).runOnUiThread(this::dismiss);
                }
        );
        passwordDialog.show();
    }

    @Override
    public void dismiss() {
        executor.shutdownNow();
        super.dismiss();
    }
}

