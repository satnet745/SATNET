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
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import org.servalproject.R;

import java.util.Collections;

/**
 * QR Code Scanner Activity
 * Integrates with ZXing library for QR code scanning
 */
public class QRScannerActivity extends AppCompatActivity {
    public static final String EXTRA_QR_RESULT = "qr_result";

    private DecoratedBarcodeView barcodeView;

    private final BarcodeCallback barcodeCallback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result == null || result.getText() == null || result.getText().trim().isEmpty()) {
                return;
            }
            Intent intent = new Intent();
            intent.putExtra(EXTRA_QR_RESULT, result.getText());
            setResult(RESULT_OK, intent);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            SatnetUiSupport.applySecureWindow(this);
            setContentView(R.layout.activity_qr_scanner);

            barcodeView = SatnetUiSupport.requireView(this, R.id.barcode_scanner_view, DecoratedBarcodeView.class, "barcode_scanner_view");
            Button cancelButton = SatnetUiSupport.requireView(this, R.id.cancel_scan_button, Button.class, "cancel_scan_button");
            cancelButton.setOnClickListener(v -> cancelScan());

            barcodeView.getBarcodeView().setDecoderFactory(
                    new com.journeyapps.barcodescanner.DefaultDecoderFactory(
                            Collections.singletonList(BarcodeFormat.QR_CODE)));
            barcodeView.decodeSingle(barcodeCallback);
            barcodeView.setStatusText(getString(R.string.satnet_qr_scanner_status));
        } catch (Throwable t) {
            Toast.makeText(this, R.string.satnet_qr_scanner_unavailable, Toast.LENGTH_SHORT).show();
            cancelScan();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (barcodeView != null) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onPause() {
        if (barcodeView != null) {
            barcodeView.pause();
        }
        super.onPause();
    }

    private void cancelScan() {
        setResult(RESULT_CANCELED, new Intent());
        finish();
    }
}

