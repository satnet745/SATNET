package org.servalproject.satnet.ui;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.servalproject.R;
import org.servalproject.bitcoin.lightning.LightningPaymentHandler;
import org.servalproject.features.FeatureFlags;
import org.servalproject.satnet.SatnetAuthorizationEngine;
import org.servalproject.satnet.SatnetRoleConflictPolicy;
import org.servalproject.satnet.SatnetRuntimeConfig;
import org.servalproject.satnet.SatnetStartupGate;
import org.servalproject.satnet.qr.QRCodeGenerator;
import org.servalproject.satnet.SatnetRoleManager;
import org.servalproject.voucher.VoucherLedger;
import org.servalproject.voucher.VoucherParticipantSnapshot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal merchant Lightning payment screen.
 */
public class MerchantLightningActivity extends AppCompatActivity {
    private static final long MAX_INVOICE_SATS = Long.MAX_VALUE / 1000L;
    public static final String EXTRA_CONTEXT_VOUCHER_ID = "org.servalproject.satnet.ui.EXTRA_CONTEXT_VOUCHER_ID";

    private EditText amountSatsInput;
    private EditText descriptionInput;
    private TextView invoiceOutput;
    private TextView invoiceMetaOutput;
    private ImageView invoiceQrImage;
    private TextView stageBadgeText;
    private TextView runtimeStatusText;
    private Button generateInvoiceButton;
    private Button copyInvoiceButton;
    private Button shareInvoiceButton;

    private SatnetRoleManager roleManager;
    private VoucherLedger voucherLedger;
    private LightningPaymentHandler paymentHandler;
    private String currentInvoice;
    private String contextVoucherId;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private boolean invoiceGenerationInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            SatnetUiSupport.applySecureWindow(this);
            setContentView(R.layout.activity_merchant_lightning);

            roleManager = new SatnetRoleManager(this);
            voucherLedger = new VoucherLedger(this);
            paymentHandler = new LightningPaymentHandler();
            contextVoucherId = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_CONTEXT_VOUCHER_ID);

            if (!FeatureFlags.isLightningEnabled()) {
                Toast.makeText(this, R.string.satnet_merchant_disabled_build, Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            if (!roleManager.canActAsMerchant()) {
                Toast.makeText(this, R.string.satnet_merchant_not_authorized, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            amountSatsInput = SatnetUiSupport.requireView(this, R.id.amount_sats_input, EditText.class, "amount_sats_input");
            descriptionInput = SatnetUiSupport.requireView(this, R.id.description_input, EditText.class, "description_input");
            invoiceOutput = SatnetUiSupport.requireView(this, R.id.invoice_output, TextView.class, "invoice_output");
            invoiceMetaOutput = SatnetUiSupport.requireView(this, R.id.invoice_meta_output, TextView.class, "invoice_meta_output");
            invoiceQrImage = SatnetUiSupport.requireView(this, R.id.invoice_qr_image, ImageView.class, "invoice_qr_image");
            stageBadgeText = SatnetUiSupport.requireView(this, R.id.merchant_stage_badge_text, TextView.class, "merchant_stage_badge_text");
            runtimeStatusText = SatnetUiSupport.requireView(this, R.id.merchant_runtime_status_text, TextView.class, "merchant_runtime_status_text");
            generateInvoiceButton = SatnetUiSupport.requireView(this, R.id.generate_invoice_button, Button.class, "generate_invoice_button");
            copyInvoiceButton = SatnetUiSupport.requireView(this, R.id.copy_invoice_button, Button.class, "copy_invoice_button");
            shareInvoiceButton = SatnetUiSupport.requireView(this, R.id.share_invoice_button, Button.class, "share_invoice_button");

            generateInvoiceButton.setOnClickListener(v -> generateInvoice());
            copyInvoiceButton.setOnClickListener(v -> copyInvoice());
            shareInvoiceButton.setOnClickListener(v -> shareInvoice());
            copyInvoiceButton.setEnabled(false);
            shareInvoiceButton.setEnabled(false);
            refreshRuntimeStatus();
            setInvoiceUiEnabled(true);
        } catch (Throwable t) {
            SatnetUiSupport.failInitialization(this, "MerchantLightning", t, getString(R.string.satnet_merchant_init_failed));
        }
    }

    private void generateInvoice() {
        try {
            SatnetStartupGate.Status runtimeStatus = refreshRuntimeStatus();
            if (!runtimeStatus.canUseRoleTools()) {
                Toast.makeText(this, runtimeStatus.getBlockingMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            String amountText = amountSatsInput.getText().toString().trim();
            if (amountText.isEmpty()) {
                Toast.makeText(this, R.string.satnet_merchant_amount_required, Toast.LENGTH_SHORT).show();
                return;
            }

            long amountSats = Long.parseLong(amountText);
            if (amountSats <= 0) {
                Toast.makeText(this, R.string.satnet_merchant_amount_positive, Toast.LENGTH_SHORT).show();
                return;
            }
            if (amountSats > MAX_INVOICE_SATS) {
                Toast.makeText(this, R.string.satnet_merchant_amount_too_large, Toast.LENGTH_SHORT).show();
                return;
            }

            if (contextVoucherId != null && !contextVoucherId.trim().isEmpty()) {
                VoucherParticipantSnapshot participantSnapshot = voucherLedger.getVoucherParticipantSnapshot(contextVoucherId);

                // Use new authorization engine combining role + conflict checks
                SatnetAuthorizationEngine.AuthorizationDecision authDecision = SatnetAuthorizationEngine.authorize(
                        roleManager,
                        SatnetRoleConflictPolicy.ACTION_ACCEPT_MERCHANT_PAYMENT,
                        SatnetRoleManager.ROLE_MERCHANT,
                        participantSnapshot,
                        "Merchant payment acceptance for voucher " + contextVoucherId);

                if (!authDecision.allowed) {
                    // Record the authorization decision for audit trail
                    voucherLedger.updateVoucherPolicyDecision(contextVoucherId,
                            authDecision.reasonCode,
                            authDecision.message);

                    // Record risk event if recommended
                    if (authDecision.shouldRecordRiskEvent()) {
                        voucherLedger.recordRiskEvent(
                                contextVoucherId,
                                roleManager.getParticipantSubjectId(),
                                roleManager.getParticipantRootSubjectId(),
                                SatnetRoleManager.ROLE_MERCHANT,
                                "merchant_payment_denied",
                                authDecision.getRecommendedRiskState() * 5,
                                authDecision.reasonCode,
                                authDecision.message);
                    }

                    // Update risk state if recommended
                    if (authDecision.getRecommendedRiskState() > 0) {
                        voucherLedger.updateVoucherRiskState(
                                contextVoucherId,
                                authDecision.getRecommendedRiskState(),
                                0, null, authDecision.reasonCode, authDecision.message);
                    }

                    Toast.makeText(this,
                            getString(R.string.satnet_merchant_policy_blocked, authDecision.message),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                // Record successful merchant settlement context
                voucherLedger.recordMerchantSettlementContext(
                        contextVoucherId,
                        roleManager.getRoleSubjectId(SatnetRoleManager.ROLE_MERCHANT),
                        roleManager.getParticipantRootSubjectId(),
                        "MERCHANT_SETTLEMENT_ACCEPTED",
                        "Merchant authorized to accept payment for voucher");
            }

            String description = descriptionInput.getText().toString().trim();
            invoiceGenerationInProgress = true;
            setInvoiceUiEnabled(false);
            invoiceOutput.setText(R.string.satnet_merchant_generating_invoice);
            invoiceMetaOutput.setText(R.string.satnet_merchant_preparing_details);

            final long finalAmountSats = amountSats;
            final String finalDescription = description;
            backgroundExecutor.execute(() -> {
                try {
                    String invoice = paymentHandler.generateInvoice(Math.multiplyExact(finalAmountSats, 1000L), finalDescription, 900);
                    Bitmap invoiceQr = QRCodeGenerator.generateLightningInvoiceQR(invoice);
                    final String invoiceMetadata = getString(R.string.satnet_merchant_invoice_metadata,
                            finalAmountSats,
                            finalDescription.isEmpty() ? getString(R.string.satnet_merchant_description_none) : finalDescription);

                    runOnUiThread(() -> {
                        if (isFinishing()) {
                            return;
                        }
                        currentInvoice = invoice;
                        invoiceOutput.setText(currentInvoice);
                        invoiceMetaOutput.setText(invoiceMetadata);
                        invoiceQrImage.setImageBitmap(invoiceQr);
                        invoiceQrImage.setVisibility(android.view.View.VISIBLE);
                        if (contextVoucherId != null && !contextVoucherId.trim().isEmpty()) {
                            Toast.makeText(this,
                                    getString(R.string.satnet_merchant_context_linked, contextVoucherId),
                                    Toast.LENGTH_SHORT).show();
                        }
                        invoiceGenerationInProgress = false;
                        setInvoiceUiEnabled(true);
                        copyInvoiceButton.setEnabled(true);
                        shareInvoiceButton.setEnabled(true);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (isFinishing()) {
                            return;
                        }
                        currentInvoice = null;
                        invoiceGenerationInProgress = false;
                        setInvoiceUiEnabled(true);
                        copyInvoiceButton.setEnabled(false);
                        shareInvoiceButton.setEnabled(false);
                        invoiceQrImage.setImageDrawable(null);
                        invoiceQrImage.setVisibility(android.view.View.GONE);
                        invoiceMetaOutput.setText(R.string.satnet_merchant_invoice_meta_placeholder);
                        invoiceOutput.setText(R.string.satnet_merchant_invoice_placeholder);
                        Toast.makeText(this, getString(R.string.satnet_merchant_generate_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.satnet_merchant_invalid_amount, Toast.LENGTH_SHORT).show();
        } catch (ArithmeticException e) {
            Toast.makeText(this, R.string.satnet_merchant_amount_too_large, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyInvoice() {
        if (invoiceGenerationInProgress) {
            Toast.makeText(this, R.string.satnet_merchant_invoice_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentInvoice == null || currentInvoice.isEmpty()) {
            Toast.makeText(this, R.string.satnet_merchant_generate_first, Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !SatnetUiSupport.copySensitiveText(
                this,
                getString(R.string.satnet_merchant_clipboard_label),
                currentInvoice,
                SatnetUiSupport.CLIPBOARD_CLEAR_DELAY_LONG_MS)) {
            Toast.makeText(this, R.string.satnet_merchant_clipboard_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.satnet_merchant_copied, Toast.LENGTH_SHORT).show();
    }

    private void shareInvoice() {
        if (invoiceGenerationInProgress) {
            Toast.makeText(this, R.string.satnet_merchant_invoice_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentInvoice == null || currentInvoice.isEmpty()) {
            Toast.makeText(this, R.string.satnet_merchant_generate_first, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, invoiceMetaOutput.getText() + "\n\n" + currentInvoice);
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.satnet_merchant_share_chooser)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.satnet_merchant_share_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void setInvoiceUiEnabled(boolean enabled) {
        SatnetStartupGate.Status runtimeStatus = refreshRuntimeStatus();
        boolean interactionReady = enabled && runtimeStatus.canUseRoleTools();
        if (amountSatsInput != null) {
            amountSatsInput.setEnabled(interactionReady);
        }
        if (descriptionInput != null) {
            descriptionInput.setEnabled(interactionReady);
        }
        if (generateInvoiceButton != null) {
            generateInvoiceButton.setEnabled(interactionReady);
            generateInvoiceButton.setText(enabled ? R.string.satnet_merchant_generate_button : R.string.satnet_merchant_generate_button_busy);
        }
    }

    private SatnetStartupGate.Status refreshRuntimeStatus() {
        SatnetStartupGate.Status runtimeStatus = SatnetStartupGate.evaluate(this);
        if (stageBadgeText != null) {
            stageBadgeText.setText(runtimeStatus.stageBadge);
        }
        if (runtimeStatusText != null) {
            runtimeStatusText.setText(getString(
                    R.string.satnet_merchant_runtime_summary,
                    SatnetRuntimeConfig.getRoleSummary(SatnetRoleManager.ROLE_MERCHANT),
                    runtimeStatus.getLocalFirstMessage(getString(R.string.satnet_merchant_capability_label))));
        }
        return runtimeStatus;
    }

    @Override
    protected void onDestroy() {
        backgroundExecutor.shutdownNow();
        if (voucherLedger != null) {
            voucherLedger.close();
        }
        super.onDestroy();
    }
}

