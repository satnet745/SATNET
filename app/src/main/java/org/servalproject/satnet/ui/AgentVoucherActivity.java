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
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.servalproject.PeerList;
import org.servalproject.R;
import org.servalproject.messages.ShowConversationActivity;
import org.servalproject.satnet.pricing.ExchangeRateManager;
import org.servalproject.satnet.SatnetRuntimeConfig;
import org.servalproject.satnet.SatnetStartupGate;
import org.servalproject.satnet.qr.QRCodeGenerator;
import org.servalproject.satnet.SatnetRoleManager;
import org.servalproject.voucher.BitcoinVoucher;
import org.servalproject.voucher.VoucherIssuerRotationPolicy;
import org.servalproject.voucher.VoucherLedger;
import org.servalproject.util.FileUriSupport;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;

/**
 * Agent Voucher Issuance Screen.
 *
 * Features for Bitcoin agents:
 * - Generate fixed-denomination vouchers
 * - Display QR codes for customers
 * - Track issued vouchers
 * - View commission earnings
 * - Monitor stake & tier
 */
public class AgentVoucherActivity extends AppCompatActivity {
    private static final String TAG = "AgentVoucherActivity";
    private static final int REQUEST_PICK_CHAT_PEER = 4201;

    private RadioGroup denominationGroup;
    private Spinner expirySpinner;
    private Spinner currencySpinner;
    private ImageView qrDisplay;
    private Button generateButton;
    private Button shareButton;
    private Button shareChatButton;
    private TextView stageBadgeText;
    private TextView runtimeStatusText;
    private TextView exchangeRateStatusText;
    private TextView voucherSummaryText;

    private SatnetRoleManager roleManager;
    private VoucherLedger voucherLedger;
    private ExchangeRateManager exchangeRateManager;
    private BitcoinVoucher currentVoucher;
    private Bitmap currentQrBitmap;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private boolean generationInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            SatnetUiSupport.applySecureWindow(this);
            setContentView(R.layout.activity_agent_voucher);

            roleManager = new SatnetRoleManager(this);
            voucherLedger = new VoucherLedger(this);
            exchangeRateManager = new ExchangeRateManager(this);

            // Check role
            if (!roleManager.canActAsAgent()) {
                Toast.makeText(this, R.string.satnet_agent_not_authorized, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // Bind UI elements
            denominationGroup = SatnetUiSupport.requireView(this, R.id.denomination_group, RadioGroup.class, "denomination_group");
            expirySpinner = SatnetUiSupport.requireView(this, R.id.expiry_spinner, Spinner.class, "expiry_spinner");
            currencySpinner = SatnetUiSupport.requireView(this, R.id.currency_spinner, Spinner.class, "currency_spinner");
            qrDisplay = SatnetUiSupport.requireView(this, R.id.qr_display, ImageView.class, "qr_display");
            generateButton = SatnetUiSupport.requireView(this, R.id.generate_button, Button.class, "generate_button");
            shareButton = SatnetUiSupport.requireView(this, R.id.share_button, Button.class, "share_button");
            shareChatButton = SatnetUiSupport.requireView(this, R.id.share_chat_button, Button.class, "share_chat_button");
            stageBadgeText = SatnetUiSupport.requireView(this, R.id.agent_stage_badge_text, TextView.class, "agent_stage_badge_text");
            runtimeStatusText = SatnetUiSupport.requireView(this, R.id.agent_runtime_status_text, TextView.class, "agent_runtime_status_text");
            exchangeRateStatusText = SatnetUiSupport.requireView(this, R.id.exchange_rate_status_text, TextView.class, "exchange_rate_status_text");
            voucherSummaryText = SatnetUiSupport.requireView(this, R.id.voucher_summary_text, TextView.class, "voucher_summary_text");

            // Set up expiry spinner
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                    R.array.satnet_expiry_hours, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            expirySpinner.setAdapter(adapter);

            ArrayAdapter<CharSequence> currencyAdapter = ArrayAdapter.createFromResource(this,
                    R.array.satnet_exchange_currencies, android.R.layout.simple_spinner_item);
            currencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            currencySpinner.setAdapter(currencyAdapter);
            preselectPreferredCurrency();
            currencySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                    updateExchangeRateStatus();
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    updateExchangeRateStatus();
                }
            });
            applyStageDenominationPolicy();
            refreshRuntimeStatus();
            updateExchangeRateStatus();
            voucherSummaryText.setText(R.string.satnet_agent_qr_hint);

            // Click listeners
            generateButton.setOnClickListener(v -> generateVoucher());
            shareButton.setOnClickListener(v -> shareVoucher());
            shareChatButton.setOnClickListener(v -> shareVoucherInConversation());
        } catch (Throwable t) {
            SatnetUiSupport.failInitialization(this, "AgentVoucher", t, "Agent voucher tools are unavailable on this device");
        }
    }

    private void generateVoucher() {
        try {
            SatnetStartupGate.Status runtimeStatus = refreshRuntimeStatus();
            if (!runtimeStatus.canUseRoleTools()) {
                Toast.makeText(this, runtimeStatus.getBlockingMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            long denomination = getSelectedDenomination();
            if (denomination == 0) {
                Toast.makeText(this, R.string.satnet_agent_select_denomination, Toast.LENGTH_SHORT).show();
                return;
            }
            if (denomination > SatnetRuntimeConfig.getMaxVoucherDenominationSats()) {
                Toast.makeText(this,
                        getString(R.string.satnet_agent_stage_limit, SatnetRuntimeConfig.getMaxVoucherDenominationSats()),
                        Toast.LENGTH_LONG).show();
                return;
            }

            String expiryText = (String) expirySpinner.getSelectedItem();
            int expiryHours;
            try {
                expiryHours = Integer.parseInt(expiryText);
            } catch (NumberFormatException e) {
                expiryHours = 24;
            }

            generationInProgress = true;
            setGenerationUiEnabled(false);
            if (voucherSummaryText != null) {
                voucherSummaryText.setText(R.string.satnet_agent_voucher_pending);
            }

            final long finalDenomination = denomination;
            final int finalExpiryHours = expiryHours;
            final String finalCurrencyCode = getSelectedCurrencyCode();
            if (!SatnetRuntimeConfig.allowLiveExchangeRateFetch()) {
                generationInProgress = false;
                setGenerationUiEnabled(true);
                Toast.makeText(this, R.string.satnet_agent_live_rates_unavailable, Toast.LENGTH_LONG).show();
                return;
            }

            if (exchangeRateStatusText != null) {
                exchangeRateStatusText.setText(getString(R.string.satnet_agent_refreshing_rate, finalCurrencyCode));
            }
            exchangeRateManager.getExchangeRate(finalCurrencyCode, new ExchangeRateManager.ExchangeRateCallback() {
                @Override
                public void onRateReceived(double rate, String source) {
                    issueVoucherWithRate(finalDenomination, finalExpiryHours, finalCurrencyCode, rate, source);
                }

                @Override
                public void onRateFailed(boolean hasOfflineFallback, String reason) {
                    double fallbackRate = exchangeRateManager.getLastKnownRate(finalCurrencyCode);
                    if (hasOfflineFallback && fallbackRate > 0) {
                        issueVoucherWithRate(finalDenomination, finalExpiryHours, finalCurrencyCode, fallbackRate,
                                exchangeRateManager.getRateSource(finalCurrencyCode) + ":stale_cache");
                        return;
                    }
                    runOnUiThread(() -> {
                        if (isFinishing()) {
                            return;
                        }
                        generationInProgress = false;
                        setGenerationUiEnabled(true);
                        if (exchangeRateStatusText != null) {
                            exchangeRateStatusText.setText(getString(R.string.satnet_agent_rate_failed, reason));
                        }
                        Toast.makeText(AgentVoucherActivity.this,
                                R.string.satnet_agent_rate_failed_toast,
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (Exception e) {
            generationInProgress = false;
            setGenerationUiEnabled(true);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void issueVoucherWithRate(final long denomination, final int expiryHours,
            final String currencyCode, final double exchangeRate, final String source) {
        backgroundExecutor.execute(() -> {
                try {
                    String agentId = roleManager.getAgentName();
                    VoucherIssuerRotationPolicy.ActiveIssuerState activeIssuerState =
                            VoucherIssuerRotationPolicy.resolve(AgentVoucherActivity.this, voucherLedger);
                    BitcoinVoucher voucher = BitcoinVoucher.generateNew(
                            agentId,
                            denomination,
                            expiryHours,
                            BitcoinVoucher.DIRECTION_BUY,
                            exchangeRate,
                            currencyCode,
                            activeIssuerState);
                    voucherLedger.recordIssuedVoucher(voucher);
                    Bitmap qrBitmap = generateQRBitmap(voucher.getQRPayload());
                    if (qrBitmap == null) {
                        throw new IllegalStateException("QR code generation unavailable");
                    }

                    runOnUiThread(() -> {
                        if (isFinishing()) {
                            return;
                        }
                        currentVoucher = voucher;
                        currentQrBitmap = qrBitmap;
                        qrDisplay.setImageBitmap(qrBitmap);
                        generationInProgress = false;
                        setGenerationUiEnabled(true);
                        shareButton.setEnabled(true);
                        if (shareChatButton != null) {
                            shareChatButton.setEnabled(true);
                        }
                        if (voucherSummaryText != null) {
                            StringBuilder summaryBuilder = new StringBuilder(getString(
                                    R.string.satnet_agent_voucher_ready_summary,
                                    denomination,
                                    expiryHours,
                                    currencyCode,
                                    voucher.getNumericCode()));
                            summaryBuilder.append("\nIssuer key epoch ")
                                    .append(voucher.getIssuerRotationEpoch())
                                    .append(" · ")
                                    .append(voucher.getIssuerKeyId());
                            if (voucher.getIssuerRotationReason() != null
                                    && !"active".equalsIgnoreCase(voucher.getIssuerRotationReason())) {
                                summaryBuilder.append("\nRotation: ")
                                        .append(voucher.getIssuerRotationReason());
                                if (voucher.getIssuerPreviousKeystoreAlias() != null
                                        && !voucher.getIssuerPreviousKeystoreAlias().trim().isEmpty()) {
                                    summaryBuilder.append(" from ")
                                            .append(voucher.getIssuerPreviousKeystoreAlias());
                                }
                            }
                            voucherSummaryText.setText(summaryBuilder.toString());
                        }
                        if (exchangeRateStatusText != null) {
                            String aliasLabel = voucher.getIssuerKeystoreAlias() == null ? "n/a" : voucher.getIssuerKeystoreAlias();
                            String rotationSuffix = voucher.getIssuerRotationReason() != null
                                    && !"active".equalsIgnoreCase(voucher.getIssuerRotationReason())
                                    ? " · rotated:" + voucher.getIssuerRotationReason()
                                    : "";
                            exchangeRateStatusText.setText(String.format(Locale.US,
                                    "1 BTC ≈ %,.2f %s · %s · alias %s%s",
                                    exchangeRate,
                                    currencyCode,
                                    source,
                                    aliasLabel,
                                    rotationSuffix));
                        }
                        Toast.makeText(this, getString(R.string.satnet_agent_generated_toast, denomination),
                                Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (isFinishing()) {
                            return;
                        }
                        generationInProgress = false;
                        setGenerationUiEnabled(true);
                        shareButton.setEnabled(currentVoucher != null);
                        if (shareChatButton != null) {
                            shareChatButton.setEnabled(currentVoucher != null);
                        }
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
    }

    private void shareVoucher() {
        if (generationInProgress) {
            Toast.makeText(this, R.string.satnet_agent_generating_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentVoucher == null || currentQrBitmap == null) {
            Toast.makeText(this, R.string.satnet_agent_generate_first, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Save QR bitmap to temporary file
            File tempFile = File.createTempFile("satnet_voucher_qr", ".png", getCacheDir());
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                currentQrBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }

            // Get sharable URI
            android.net.Uri shareUri = FileUriSupport.getSharableUri(this, tempFile);

            // Create share intent for image
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, buildVoucherShareMessage());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, getString(R.string.satnet_agent_share_chooser)));
        } catch (Exception e) {
            Log.e(TAG, "Failed to share voucher QR", e);
            Toast.makeText(this, "Failed to share voucher: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareVoucherInConversation() {
        if (generationInProgress) {
            Toast.makeText(this, R.string.satnet_agent_generating_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentVoucher == null) {
            Toast.makeText(this, R.string.satnet_agent_generate_first, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent pickPeerIntent = new Intent(PeerList.PICK_PEER_INTENT);
        pickPeerIntent.setClass(this, PeerList.class);
        pickPeerIntent.putExtra(PeerList.TITLE, getString(R.string.satnet_agent_share_chat_picker_title));
        try {
            startActivityForResult(pickPeerIntent, REQUEST_PICK_CHAT_PEER);
        } catch (Exception e) {
            Toast.makeText(this, R.string.satnet_agent_share_chat_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private long getSelectedDenomination() {
        int selectedId = denominationGroup.getCheckedRadioButtonId();

        if (selectedId == R.id.denom_1000) return 1000;
        if (selectedId == R.id.denom_5000) return 5000;
        if (selectedId == R.id.denom_10000) return 10000;
        if (selectedId == R.id.denom_50000) return 50000;

        return 0;
    }

    private Bitmap generateQRBitmap(String qrData) {
        if (qrData == null || qrData.trim().isEmpty()) {
            throw new IllegalArgumentException("Voucher QR payload is empty");
        }
        try {
            return QRCodeGenerator.generateQRCode(qrData, 512);
        } catch (Exception e) {
            Log.e(TAG, "Unable to generate voucher QR code. payloadLength=" + qrData.length(), e);
            throw new IllegalStateException("QR code generation unavailable", e);
        }
    }

    private void setGenerationUiEnabled(boolean enabled) {
        SatnetStartupGate.Status runtimeStatus = refreshRuntimeStatus();
        boolean interactionReady = enabled && runtimeStatus.canUseRoleTools();
        if (denominationGroup != null) {
            for (int i = 0; i < denominationGroup.getChildCount(); i++) {
                android.view.View child = denominationGroup.getChildAt(i);
                child.setEnabled(interactionReady && child.getTag() == null);
            }
        }
        if (expirySpinner != null) {
            expirySpinner.setEnabled(interactionReady);
        }
        if (currencySpinner != null) {
            currencySpinner.setEnabled(interactionReady);
        }
        if (generateButton != null) {
            generateButton.setEnabled(interactionReady);
            generateButton.setText(enabled
                    ? R.string.satnet_agent_generate_button
                    : R.string.satnet_agent_generate_button_busy);
        }
        if (shareButton != null && !interactionReady) {
            shareButton.setEnabled(false);
        }
        if (shareChatButton != null && !interactionReady) {
            shareChatButton.setEnabled(false);
        }
    }

    private void applyStageDenominationPolicy() {
        long maxSats = SatnetRuntimeConfig.getMaxVoucherDenominationSats();
        updateDenominationOption(R.id.denom_1000, 1000, maxSats);
        updateDenominationOption(R.id.denom_5000, 5000, maxSats);
        updateDenominationOption(R.id.denom_10000, 10000, maxSats);
        updateDenominationOption(R.id.denom_50000, 50000, maxSats);
    }

    private void updateDenominationOption(int viewId, long denomination, long maxSats) {
        RadioButton button = findViewById(viewId);
        if (button == null) {
            return;
        }
        boolean enabledForStage = denomination <= maxSats;
        button.setEnabled(enabledForStage);
        button.setAlpha(enabledForStage ? 1.0f : 0.5f);
        button.setTag(enabledForStage ? null : "stage-disabled");
        String label = getString(R.string.satnet_agent_denomination_label, denomination);
        if (!enabledForStage) {
            label = getString(R.string.satnet_agent_denomination_unavailable_label,
                    denomination,
                    SatnetRuntimeConfig.getStageDisplayName());
            if (button.isChecked()) {
                denominationGroup.clearCheck();
            }
        }
        button.setText(label);
    }

    private SatnetStartupGate.Status refreshRuntimeStatus() {
        SatnetStartupGate.Status runtimeStatus = SatnetStartupGate.evaluate(this);
        if (stageBadgeText != null) {
            stageBadgeText.setText(runtimeStatus.stageBadge);
        }
        if (runtimeStatusText != null) {
            runtimeStatusText.setText(getString(
                    R.string.satnet_agent_runtime_summary,
                    SatnetRuntimeConfig.getRoleSummary(SatnetRoleManager.ROLE_AGENT),
                    SatnetRuntimeConfig.getExchangeRateModeSummary(),
                    runtimeStatus.getLocalFirstMessage(getString(R.string.satnet_agent_capability_label))));
        }
        return runtimeStatus;
    }

    private void preselectPreferredCurrency() {
        if (currencySpinner == null || currencySpinner.getAdapter() == null) {
            return;
        }
        String preferredCurrency = exchangeRateManager.getPreferredCurrencyCode();
        for (int i = 0; i < currencySpinner.getAdapter().getCount(); i++) {
            Object item = currencySpinner.getAdapter().getItem(i);
            if (item != null && preferredCurrency.equalsIgnoreCase(item.toString())) {
                currencySpinner.setSelection(i);
                return;
            }
        }
    }

    private String getSelectedCurrencyCode() {
        if (currencySpinner == null || currencySpinner.getSelectedItem() == null) {
            return exchangeRateManager.getPreferredCurrencyCode();
        }
        return ExchangeRateManager.normalizeCurrencyCode(currencySpinner.getSelectedItem().toString());
    }

    private void updateExchangeRateStatus() {
        if (exchangeRateStatusText == null || exchangeRateManager == null) {
            return;
        }
        String currencyCode = getSelectedCurrencyCode();
        ExchangeRateManager.RateSnapshot snapshot = exchangeRateManager.getLastKnownRateSnapshot(currencyCode);
        if (snapshot == null) {
            exchangeRateStatusText.setText(getString(R.string.satnet_agent_rate_refresh_prompt, currencyCode));
            return;
        }
        exchangeRateStatusText.setText(getString(
                R.string.satnet_agent_rate_snapshot,
                snapshot.rate,
                currencyCode,
                snapshot.source,
                snapshot.stale ? getString(R.string.satnet_agent_rate_cached_suffix) : ""));
    }

    private String buildVoucherShareMessage() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        return getString(
                R.string.satnet_agent_share_message,
                currentVoucher.getDenomination(),
                String.format(Locale.US, "%,.2f", currentVoucher.getExchangeRate()),
                currentVoucher.getCurrencyCode(),
                currentVoucher.getNumericCode(),
                dateFormat.format(new java.util.Date(currentVoucher.getExpiryTime())));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_CHAT_PEER) {
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            Toast.makeText(this, R.string.satnet_agent_share_chat_cancelled, Toast.LENGTH_SHORT).show();
            return;
        }
        String recipientSid = data.getStringExtra(PeerList.SID);
        if (recipientSid == null || recipientSid.isEmpty()) {
            Toast.makeText(this, R.string.satnet_agent_share_chat_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

            Intent conversationIntent = ShowConversationActivity.createIntent(this, recipientSid);
        conversationIntent.putExtra(ShowConversationActivity.EXTRA_DRAFT_TEXT, buildVoucherShareMessage());
        startActivity(conversationIntent);
        Toast.makeText(this, R.string.satnet_agent_share_chat_ready, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }
}
