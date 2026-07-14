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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.servalproject.R;
import org.servalproject.bitcoin.BitcoinWallet;

import java.util.Locale;

/**
 * Transaction Success Dialog - Shows successful transaction details
 */
public class TransactionSuccessDialog extends Dialog {
    private final String txid;
    private final BitcoinWallet.SendTransactionResult txResult;

    public TransactionSuccessDialog(Context context, String txid, BitcoinWallet.SendTransactionResult txResult) {
        super(context);
        this.txid = txid;
        this.txResult = txResult;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_transaction_success);

        TextView titleText = findViewById(R.id.success_title);
        EditText txidDisplay = findViewById(R.id.txid_display);
        TextView detailsText = findViewById(R.id.transaction_details);
        Button copyTxidButton = findViewById(R.id.copy_txid_button);
        Button closeButton = findViewById(R.id.close_button);

        titleText.setText(R.string.satnet_send_success);

        txidDisplay.setText(txid);
        txidDisplay.setEnabled(false);

        String details = String.format(Locale.US,
                "Transaction ID: %s\n\n" +
                "Amount Sent: %,d sats\n" +
                "Transaction Fee: %,d sats\n" +
                "Change Amount: %,d sats\n" +
                "Total Cost: %,d sats\n\n" +
                "The transaction has been broadcasted to the network.\n" +
                "It may take several minutes for confirmation.",
                txid,
                txResult.sentAmount,
                txResult.fee,
                txResult.changeAmount,
                txResult.getTotalCost()
        );

        detailsText.setText(details);

        copyTxidButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("txid", txid);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), R.string.satnet_send_txid_copied, Toast.LENGTH_SHORT).show();
            }
        });

        closeButton.setOnClickListener(v -> dismiss());
    }
}

/**
 * Password Confirmation Dialog - For transaction signing authorization
 */
class PasswordConfirmationDialog extends Dialog {
    private final Context context;
    private final PasswordCallback callback;

    public interface PasswordCallback {
        void onPasswordConfirmed(String password);
    }

    public PasswordConfirmationDialog(Context context, PasswordCallback callback) {
        super(context);
        this.context = context;
        this.callback = callback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_password_confirmation);

        TextView titleText = findViewById(R.id.password_dialog_title);
        EditText passwordInput = findViewById(R.id.password_input);
        Button confirmButton = findViewById(R.id.confirm_password_button);
        Button cancelButton = findViewById(R.id.cancel_password_button);

        titleText.setText(R.string.satnet_send_enter_password);

        confirmButton.setOnClickListener(v -> {
            String password = passwordInput.getText().toString();
            if (password.isEmpty()) {
                Toast.makeText(context, R.string.satnet_send_password_required, Toast.LENGTH_SHORT).show();
                return;
            }

            callback.onPasswordConfirmed(password);
            dismiss();
        });

        cancelButton.setOnClickListener(v -> dismiss());

        setCanceledOnTouchOutside(false);
    }
}



