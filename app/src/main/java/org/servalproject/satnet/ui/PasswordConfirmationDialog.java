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
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.servalproject.R;

/**
 * Password Confirmation Dialog for Bitcoin transactions
 * 
 * Prompts user to enter their wallet password to authorize transaction signing.
 */
class PasswordConfirmationHelper extends AlertDialog {
    private static final String TAG = "PasswordConfirmationHelper";

    interface PasswordCallbackHelper {
        void onPasswordConfirmed(String password);
    }
    
    private EditText passwordInput;
    private PasswordCallbackHelper callback;
    private Context context;
    
    public PasswordConfirmationHelper(Context context, PasswordCallbackHelper callback) {
        super(context);
        this.context = context;
        this.callback = callback;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setTitle(R.string.satnet_send_enter_password);
        
        // Create password input field
        passwordInput = new EditText(context);
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setHint(R.string.satnet_send_password_hint);
        passwordInput.setPadding(16, 16, 16, 16);
        
        setView(passwordInput);
        
        setButton(BUTTON_POSITIVE, context.getString(R.string.satnet_send_confirm), (dialog, which) -> {
            String password = passwordInput.getText().toString();
            
            if (password.isEmpty()) {
                Toast.makeText(context, R.string.satnet_send_password_required, Toast.LENGTH_SHORT).show();
            } else {
                if (callback != null) {
                    callback.onPasswordConfirmed(password);
                }
                dismiss();
            }
        });
        
        setButton(BUTTON_NEGATIVE, context.getString(R.string.satnet_send_cancel), (dialog, which) -> {
            dismiss();
        });
    }
}

