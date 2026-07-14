package org.servalproject.satnet.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.servalproject.R;
import org.servalproject.satnet.SatnetRoleConflictPolicy;
import org.servalproject.satnet.SatnetRoleManager;
import org.servalproject.satnet.SatnetRuntimeConfig;
import org.servalproject.satnet.SatnetStartupGate;
import org.servalproject.satnet.verifier.SettlementVerifier;
import org.servalproject.voucher.BitcoinVoucher;
import org.servalproject.voucher.VoucherAuditRhizomeSync;
import org.servalproject.voucher.VoucherLedger;
import org.servalproject.voucher.VoucherParticipantSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VerifierDashboardActivity extends AppCompatActivity {
    private TextView stageBadgeText;
    private TextView runtimeStatusText;
    private TextView summaryText;
    private TextView manifestBadgeText;
    private TextView ledgerBadgeText;
    private TextView rotationBadgeText;
    private TextView riskBadgeText;
    private TextView disputeBadgeText;
    private TextView policyStateText;
    private TextView auditHistoryText;
    private EditText voucherIdInput;
    private EditText payloadInput;

    private SatnetRoleManager roleManager;
    private VoucherLedger voucherLedger;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private boolean busy = false;
    private Button refreshButton;
    private Button inspectButton;
    private Button verifyButton;
    private Button releaseExpiredButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            SatnetUiSupport.applySecureWindow(this);
            setContentView(R.layout.activity_verifier_dashboard);

            roleManager = new SatnetRoleManager(this);
            if (!roleManager.canActAsVerifier()) {
                Toast.makeText(this, R.string.satnet_verifier_not_authorized, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            voucherLedger = new VoucherLedger(this);
            stageBadgeText = SatnetUiSupport.requireView(this, R.id.verifier_stage_badge_text, TextView.class, "verifier_stage_badge_text");
            runtimeStatusText = SatnetUiSupport.requireView(this, R.id.verifier_runtime_status_text, TextView.class, "verifier_runtime_status_text");
            summaryText = SatnetUiSupport.requireView(this, R.id.verifier_summary_text, TextView.class, "verifier_summary_text");
            SatnetUiSupport.requireView(this, R.id.verifier_trust_badge_row, android.widget.LinearLayout.class, "verifier_trust_badge_row");
            manifestBadgeText = SatnetUiSupport.requireView(this, R.id.verifier_manifest_badge, TextView.class, "verifier_manifest_badge");
            ledgerBadgeText = SatnetUiSupport.requireView(this, R.id.verifier_ledger_badge, TextView.class, "verifier_ledger_badge");
            rotationBadgeText = SatnetUiSupport.requireView(this, R.id.verifier_rotation_badge, TextView.class, "verifier_rotation_badge");
            riskBadgeText = SatnetUiSupport.requireView(this, R.id.verifier_risk_badge, TextView.class, "verifier_risk_badge");
            disputeBadgeText = SatnetUiSupport.requireView(this, R.id.verifier_dispute_badge, TextView.class, "verifier_dispute_badge");
            policyStateText = SatnetUiSupport.requireView(this, R.id.verifier_policy_state_text, TextView.class, "verifier_policy_state_text");
            auditHistoryText = SatnetUiSupport.requireView(this, R.id.verifier_audit_history_text, TextView.class, "verifier_audit_history_text");
            voucherIdInput = SatnetUiSupport.requireView(this, R.id.verifier_voucher_id_input, EditText.class, "verifier_voucher_id_input");
            payloadInput = SatnetUiSupport.requireView(this, R.id.verifier_payload_input, EditText.class, "verifier_payload_input");
            refreshButton = SatnetUiSupport.requireView(this, R.id.verifier_refresh_button, Button.class, "verifier_refresh_button");
            inspectButton = SatnetUiSupport.requireView(this, R.id.verifier_inspect_button, Button.class, "verifier_inspect_button");
            verifyButton = SatnetUiSupport.requireView(this, R.id.verifier_verify_button, Button.class, "verifier_verify_button");
            releaseExpiredButton = SatnetUiSupport.requireView(this, R.id.verifier_release_expired_button, Button.class, "verifier_release_expired_button");

            refreshButton.setOnClickListener(v -> refreshDashboard());
            inspectButton.setOnClickListener(v -> inspectVoucherPayload());
            verifyButton.setOnClickListener(v -> verifySelectedVoucher());
            releaseExpiredButton.setOnClickListener(v -> releaseExpiredSettlementWindows());

            refreshRuntimeStatus();
            refreshDashboard();
        } catch (Throwable t) {
            SatnetUiSupport.failInitialization(this, "VerifierDashboard", t, getString(R.string.satnet_verifier_init_failed));
        }
    }

    private void refreshDashboard() {
        SatnetStartupGate.Status runtimeStatus = refreshRuntimeStatus();
        if (!runtimeStatus.canUseVerifierTools()) {
            setBusy(false, null);
            if (summaryText != null) {
                summaryText.setText(runtimeStatus.getVerifierBlockingMessage());
            }
            return;
        }
        setBusy(true, getString(R.string.satnet_verifier_loading_dashboard));
        backgroundExecutor.execute(() -> {
            Cursor pending = null;
            try {
                pending = voucherLedger.getPendingSettlementVerification();
                StringBuilder summary = new StringBuilder();
                List<String> pendingIds = new ArrayList<>();
                int count = 0;

                while (pending.moveToNext()) {
                    count++;
                    String voucherId = pending.getString(pending.getColumnIndexOrThrow("voucher_id"));
                    pendingIds.add(voucherId);
                    String agentId = pending.getString(pending.getColumnIndexOrThrow("agent_id"));
                    long denomination = pending.getLong(pending.getColumnIndexOrThrow("denomination"));
                    double exchangeRate = pending.getDouble(pending.getColumnIndexOrThrow("exchange_rate"));
                    String currencyCode = pending.getString(pending.getColumnIndexOrThrow("currency_code"));
                    long issuedTime = pending.getLong(pending.getColumnIndexOrThrow("issued_time"));
                    String issuerAlias = pending.getString(pending.getColumnIndexOrThrow("issuer_keystore_alias"));
                    long rotationEpoch = pending.getLong(pending.getColumnIndexOrThrow("issuer_rotation_epoch"));
                    summary.append("• ").append(voucherId)
                            .append("\n  Agent: ").append(agentId)
                            .append("\n  Amount: ").append(denomination).append(" sats")
                            .append("\n  Rate: ").append(exchangeRate).append(' ').append(currencyCode)
                            .append("\n  Issued: ").append(timeFormat.format(new Date(issuedTime)))
                            .append("\n  Alias: ").append(issuerAlias == null ? "n/a" : issuerAlias)
                            .append("\n  Epoch: ").append(rotationEpoch)
                            .append("\n\n");
                }

                final int finalCount = count;
                final String summaryTextValue = count == 0
                        ? getString(R.string.satnet_verifier_empty_summary, SettlementVerifier.getVerifierWindowSummary())
                        : getString(R.string.satnet_verifier_pending_summary,
                        finalCount,
                        SettlementVerifier.getVerifierWindowSummary(),
                        summary.toString().trim());
                final String preferredVoucherId = resolvePreferredVoucherId(pendingIds.isEmpty() ? null : pendingIds.get(0));
                final List<VoucherLedger.VerifierAuditRecord> auditHistory = preferredVoucherId == null
                        ? new ArrayList<>()
                        : voucherLedger.listVerifierAuditRecords(preferredVoucherId);
                final VoucherParticipantSnapshot participantSnapshot = preferredVoucherId == null
                        ? null
                        : voucherLedger.getVoucherParticipantSnapshot(preferredVoucherId);

                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    summaryText.setText(summaryTextValue);
                    if (finalCount > 0 && (voucherIdInput.getText() == null
                            || voucherIdInput.getText().toString().trim().isEmpty()) && preferredVoucherId != null) {
                        voucherIdInput.setText(preferredVoucherId);
                    }
                    renderAuditHistory(auditHistory, getString(R.string.satnet_verifier_audit_history_placeholder));
                    applyPolicyBadges(participantSnapshot);
                    setBusy(false, null);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    setBusy(false, null);
                    summaryText.setText(R.string.satnet_verifier_dashboard_failed);
                    Toast.makeText(this, getString(R.string.satnet_verifier_dashboard_load_failed_toast, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (pending != null) {
                    pending.close();
                }
            }
        });
    }

    private void inspectVoucherPayload() {
        SatnetStartupGate.Status runtimeStatus = refreshRuntimeStatus();
        if (!runtimeStatus.canUseVerifierTools()) {
            Toast.makeText(this, runtimeStatus.getVerifierBlockingMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (busy) {
            Toast.makeText(this, R.string.satnet_verifier_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        String payload = payloadInput.getText() == null ? "" : payloadInput.getText().toString().trim();
        if (payload.isEmpty()) {
            Toast.makeText(this, R.string.satnet_verifier_payload_required, Toast.LENGTH_SHORT).show();
            return;
        }

        setBusy(true, getString(R.string.satnet_verifier_payload_inspecting));
        backgroundExecutor.execute(() -> {
            try {
                BitcoinVoucher voucher = BitcoinVoucher.parseQRPayload(payload);
                VoucherParticipantSnapshot participantSnapshot = voucherLedger.getVoucherParticipantSnapshot(voucher.getVoucherId());
                SatnetRoleConflictPolicy.ConflictCheck conflictCheck = SatnetRoleConflictPolicy.authorizeAction(
                        roleManager == null ? null : roleManager.getParticipantSubjectId(),
                        roleManager == null ? null : roleManager.getParticipantRootSubjectId(),
                        SatnetRoleConflictPolicy.ACTION_INSPECT_VOUCHER,
                        SatnetRoleManager.ROLE_VERIFIER,
                        participantSnapshot);
                if (!conflictCheck.allowed) {
                    final VoucherParticipantSnapshot blockedSnapshot = participantSnapshot;
                    runOnUiThread(() -> {
                        if (isFinishing()) {
                            return;
                        }
                        applyPolicyBadges(blockedSnapshot);
                        setBusy(false, null);
                        Toast.makeText(this, conflictCheck.message, Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                SettlementVerifier.WorkflowMetadataCheck metadataCheck =
                        SettlementVerifier.inspectVoucherMetadata(voucher, voucherLedger);
                voucherLedger.recordVerifierAudit(voucher.getVoucherId(),
                        metadataCheck.manifestVerified,
                        metadataCheck.ledgerMatched,
                        metadataCheck.rotationDetected,
                        metadataCheck.message,
                        "payload_inspection");
                VoucherAuditRhizomeSync.publishPendingAuditRecords(voucherLedger);
                VoucherLedger.VerifierAuditSnapshot auditSnapshot = voucherLedger.getVerifierAuditSnapshot(voucher.getVoucherId());
                List<VoucherLedger.VerifierAuditRecord> auditHistory = voucherLedger.listVerifierAuditRecords(voucher.getVoucherId());
                final String voucherId = voucher.getVoucherId();
                final String preview = "Voucher payload preview"
                        + "\n\nVoucher ID: " + voucher.getVoucherId()
                        + "\nAgent: " + voucher.getAgentId()
                        + "\nAmount: " + voucher.getDenomination() + " sats"
                        + "\nDirection: " + (voucher.getDirection() == BitcoinVoucher.DIRECTION_SELL
                        ? getString(R.string.satnet_verifier_direction_sell)
                        : getString(R.string.satnet_verifier_direction_buy))
                        + "\nCurrency: " + voucher.getCurrencyCode()
                        + "\nRate: " + voucher.getExchangeRate()
                        + "\nIssued: " + timeFormat.format(new Date(voucher.getIssuedTime()))
                        + "\nExpires: " + timeFormat.format(new Date(voucher.getExpiryTime()))
                        + "\n\n" + (metadataCheck.summary == null ? metadataCheck.message : metadataCheck.summary);
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    voucherIdInput.setText(voucherId);
                    summaryText.setText(preview);
                    applyTrustBadges(metadataCheck, auditSnapshot);
                    applyPolicyBadges(voucherLedger.getVoucherParticipantSnapshot(voucherId));
                    renderAuditHistory(auditHistory, getString(R.string.satnet_verifier_audit_history_placeholder));
                    if (!metadataCheck.isValid) {
                        Toast.makeText(this, "Voucher metadata check failed: " + metadataCheck.message, Toast.LENGTH_LONG).show();
                    }
                    setBusy(false, null);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    setBusy(false, null);
                    Toast.makeText(this, getString(R.string.satnet_verifier_invalid_payload, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void verifySelectedVoucher() {
        SatnetStartupGate.Status runtimeStatus = refreshRuntimeStatus();
        if (!runtimeStatus.canUseVerifierTools()) {
            Toast.makeText(this, runtimeStatus.getVerifierBlockingMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (busy) {
            Toast.makeText(this, R.string.satnet_verifier_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        String voucherId = voucherIdInput.getText() == null ? "" : voucherIdInput.getText().toString().trim();
        if (voucherId.isEmpty()) {
            Toast.makeText(this, R.string.satnet_verifier_verify_id_required, Toast.LENGTH_SHORT).show();
            return;
        }
        String payload = payloadInput.getText() == null ? "" : payloadInput.getText().toString().trim();
        setBusy(true, getString(R.string.satnet_verifier_verifying));
        backgroundExecutor.execute(() -> {
            try {
                if (!isPendingSettlementVoucher(voucherId)) {
                    runOnUiThread(() -> {
                        if (isFinishing()) {
                            return;
                        }
                        setBusy(false, null);
                        Toast.makeText(this, R.string.satnet_verifier_not_pending, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                VoucherParticipantSnapshot participantSnapshot = voucherLedger.getVoucherParticipantSnapshot(voucherId);
                SatnetRoleConflictPolicy.ConflictCheck conflictCheck = SatnetRoleConflictPolicy.authorizeAction(
                        roleManager == null ? null : roleManager.getParticipantSubjectId(),
                        roleManager == null ? null : roleManager.getParticipantRootSubjectId(),
                        SatnetRoleConflictPolicy.ACTION_VERIFY_SETTLEMENT,
                        SatnetRoleManager.ROLE_VERIFIER,
                        participantSnapshot);
                if (!conflictCheck.allowed) {
                    final VoucherParticipantSnapshot blockedSnapshot = participantSnapshot;
                    runOnUiThread(() -> {
                        if (isFinishing()) {
                            return;
                        }
                        applyPolicyBadges(blockedSnapshot);
                        setBusy(false, null);
                        Toast.makeText(this, conflictCheck.message, Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                VoucherLedger.VoucherMetadataSnapshot metadataSnapshot = voucherLedger.getVoucherMetadataSnapshot(voucherId);
                if (SettlementVerifier.requiresPayloadInspection(metadataSnapshot) && payload.isEmpty()) {
                    runOnUiThread(() -> {
                        if (isFinishing()) {
                            return;
                        }
                        setBusy(false, null);
                        Toast.makeText(this,
                                "Inspect the full voucher payload before verifying detached manifest metadata",
                                Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                if (!payload.isEmpty()) {
                    BitcoinVoucher parsedVoucher = BitcoinVoucher.parseQRPayload(payload);
                    if (!voucherId.equals(parsedVoucher.getVoucherId())) {
                        throw new IllegalStateException("Voucher payload does not match the selected voucher ID");
                    }
                    SettlementVerifier.WorkflowMetadataCheck metadataCheck =
                            SettlementVerifier.inspectVoucherMetadata(parsedVoucher, voucherLedger);
                    voucherLedger.recordVerifierAudit(parsedVoucher.getVoucherId(),
                            metadataCheck.manifestVerified,
                            metadataCheck.ledgerMatched,
                            metadataCheck.rotationDetected,
                            metadataCheck.message,
                            "settlement_verification");
                    VoucherAuditRhizomeSync.publishPendingAuditRecords(voucherLedger);
                    if (!metadataCheck.isValid) {
                        throw new IllegalStateException(metadataCheck.message);
                    }
                }
                voucherLedger.markSettlementVerified(voucherId,
                        roleManager == null ? null : roleManager.getParticipantSubjectId(),
                        roleManager == null ? null : roleManager.getParticipantRootSubjectId(),
                        1,
                        1);
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    Toast.makeText(this, getString(R.string.satnet_verifier_verified, voucherId), Toast.LENGTH_SHORT).show();
                    refreshDashboard();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    setBusy(false, null);
                    Toast.makeText(this, getString(R.string.satnet_verifier_verify_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void releaseExpiredSettlementWindows() {
        SatnetStartupGate.Status runtimeStatus = refreshRuntimeStatus();
        if (!runtimeStatus.canUseVerifierTools()) {
            Toast.makeText(this, runtimeStatus.getVerifierBlockingMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (busy) {
            Toast.makeText(this, R.string.satnet_verifier_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true, getString(R.string.satnet_verifier_releasing));
        backgroundExecutor.execute(() -> {
            try {
                final int releasedCount = SettlementVerifier.releaseExpiredSettlementWindows(voucherLedger, System.currentTimeMillis());
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    Toast.makeText(this,
                            releasedCount == 0
                                    ? getString(R.string.satnet_verifier_release_none)
                                    : getString(R.string.satnet_verifier_release_count, releasedCount),
                            Toast.LENGTH_SHORT).show();
                    refreshDashboard();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    setBusy(false, null);
                    Toast.makeText(this, getString(R.string.satnet_verifier_release_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private boolean isPendingSettlementVoucher(String voucherId) {
        try (Cursor pending = voucherLedger.getPendingSettlementVerification()) {
            while (pending.moveToNext()) {
                if (voucherId.equals(pending.getString(pending.getColumnIndexOrThrow("voucher_id")))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void setBusy(boolean busyState, String loadingMessage) {
        busy = busyState;
        SatnetStartupGate.Status runtimeStatus = refreshRuntimeStatus();
        boolean verifierReady = runtimeStatus.canUseVerifierTools();
        if (voucherIdInput != null) {
            voucherIdInput.setEnabled(!busyState && verifierReady);
        }
        if (payloadInput != null) {
            payloadInput.setEnabled(!busyState && verifierReady);
        }
        if (refreshButton != null) {
            refreshButton.setEnabled(!busyState && verifierReady);
        }
        if (inspectButton != null) {
            inspectButton.setEnabled(!busyState && verifierReady);
        }
        if (verifyButton != null) {
            verifyButton.setEnabled(!busyState && verifierReady);
        }
        if (releaseExpiredButton != null) {
            releaseExpiredButton.setEnabled(!busyState && verifierReady);
        }
        if (busyState && summaryText != null && loadingMessage != null) {
            summaryText.setText(loadingMessage);
        }
    }

    private void applyTrustBadges(SettlementVerifier.WorkflowMetadataCheck metadataCheck,
            VoucherLedger.VerifierAuditSnapshot auditSnapshot) {
        SettlementVerifier.TrustBadgeState badgeState =
                SettlementVerifier.buildTrustBadgeState(metadataCheck, auditSnapshot);
        bindBadge(manifestBadgeText, badgeState.manifestBadgeText, badgeState.manifestPositive);
        bindBadge(ledgerBadgeText, badgeState.ledgerBadgeText, badgeState.ledgerPositive);
        bindBadge(rotationBadgeText, badgeState.rotationBadgeText, badgeState.rotationDetected);
        android.view.View badgeRow = findViewById(R.id.verifier_trust_badge_row);
        if (badgeRow != null) {
            badgeRow.setVisibility(android.view.View.VISIBLE);
        }
    }

    private void applyPolicyBadges(VoucherParticipantSnapshot participantSnapshot) {
        if (riskBadgeText == null || disputeBadgeText == null || policyStateText == null) {
            return;
        }
        if (participantSnapshot == null) {
            riskBadgeText.setVisibility(android.view.View.GONE);
            disputeBadgeText.setVisibility(android.view.View.GONE);
            policyStateText.setVisibility(android.view.View.GONE);
            return;
        }
        riskBadgeText.setVisibility(android.view.View.VISIBLE);
        disputeBadgeText.setVisibility(android.view.View.VISIBLE);
        policyStateText.setVisibility(android.view.View.VISIBLE);
        bindBadge(riskBadgeText,
                getRiskBadgeLabel(participantSnapshot.riskState),
                participantSnapshot.riskState <= VoucherLedger.RISK_STATE_NONE);
        bindBadge(disputeBadgeText,
                getDisputeBadgeLabel(participantSnapshot.disputeState),
                participantSnapshot.disputeState == VoucherLedger.DISPUTE_STATUS_NONE
                        || participantSnapshot.disputeState == VoucherLedger.DISPUTE_STATUS_RESOLVED);
        policyStateText.setText(buildPolicyStateSummary(participantSnapshot));
        android.view.View badgeRow = findViewById(R.id.verifier_trust_badge_row);
        if (badgeRow != null) {
            badgeRow.setVisibility(android.view.View.VISIBLE);
        }
    }

    private String buildPolicyStateSummary(VoucherParticipantSnapshot participantSnapshot) {
        String decisionCode = participantSnapshot.decisionCode == null || participantSnapshot.decisionCode.trim().isEmpty()
                ? getString(R.string.satnet_verifier_policy_state_code_unknown)
                : participantSnapshot.decisionCode;
        String decisionMessage = participantSnapshot.decisionMessage == null || participantSnapshot.decisionMessage.trim().isEmpty()
                ? getString(R.string.satnet_verifier_policy_state_message_unknown)
                : participantSnapshot.decisionMessage;
        if ("QUORUM_PENDING".equalsIgnoreCase(participantSnapshot.decisionCode)) {
            decisionMessage = getString(R.string.satnet_verifier_policy_state_quorum_pending,
                    participantSnapshot.achievedQuorum,
                    participantSnapshot.requiredQuorum)
                    + "\n"
                    + decisionMessage;
        }
        String updatedAt = participantSnapshot.decisionAt > 0L
                ? timeFormat.format(new Date(participantSnapshot.decisionAt))
                : getString(R.string.satnet_verifier_runtime_placeholder);
        return getString(R.string.satnet_verifier_policy_state_format,
                decisionCode,
                decisionMessage,
                updatedAt);
    }

    private String getRiskBadgeLabel(int riskState) {
        switch (riskState) {
            case VoucherLedger.RISK_STATE_MONITOR:
                return getString(R.string.satnet_verifier_risk_badge_monitor);
            case VoucherLedger.RISK_STATE_HOLD:
                return getString(R.string.satnet_verifier_risk_badge_hold);
            case VoucherLedger.RISK_STATE_BLOCK:
                return getString(R.string.satnet_verifier_risk_badge_block);
            case VoucherLedger.RISK_STATE_NONE:
            default:
                return getString(R.string.satnet_verifier_risk_badge_clear);
        }
    }

    private String getDisputeBadgeLabel(int disputeState) {
        switch (disputeState) {
            case VoucherLedger.DISPUTE_STATUS_OPEN:
                return getString(R.string.satnet_verifier_dispute_badge_open);
            case VoucherLedger.DISPUTE_STATUS_UNDER_REVIEW:
                return getString(R.string.satnet_verifier_dispute_badge_review);
            case VoucherLedger.DISPUTE_STATUS_RESOLVED:
                return getString(R.string.satnet_verifier_dispute_badge_resolved);
            case VoucherLedger.DISPUTE_STATUS_ESCALATED:
                return getString(R.string.satnet_verifier_dispute_badge_escalated);
            case VoucherLedger.DISPUTE_STATUS_NONE:
            default:
                return getString(R.string.satnet_verifier_dispute_badge_none);
        }
    }

    private void bindBadge(TextView badgeView, String label, boolean positiveState) {
        if (badgeView == null) {
            return;
        }
        badgeView.setText(label);
        badgeView.setTextColor(androidx.core.content.ContextCompat.getColor(this,
                positiveState ? R.color.satnet_accent : R.color.satnet_alert));
    }

    private String resolvePreferredVoucherId(String fallbackVoucherId) {
        String currentVoucherId = voucherIdInput == null || voucherIdInput.getText() == null
                ? ""
                : voucherIdInput.getText().toString().trim();
        return currentVoucherId.isEmpty() ? fallbackVoucherId : currentVoucherId;
    }

    private void renderAuditHistory(List<VoucherLedger.VerifierAuditRecord> auditHistory, String emptyMessage) {
        if (auditHistoryText == null) {
            return;
        }
        auditHistoryText.setText(auditHistory == null || auditHistory.isEmpty()
                ? emptyMessage
                : SettlementVerifier.buildAuditHistorySummary(auditHistory, 3));
    }

    private SatnetStartupGate.Status refreshRuntimeStatus() {
        SatnetStartupGate.Status runtimeStatus = SatnetStartupGate.evaluate(this);
        if (stageBadgeText != null) {
            stageBadgeText.setText(getString(
                    R.string.satnet_verifier_stage_badge,
                    runtimeStatus.stageBadge,
                    SettlementVerifier.getVerifierWindowSummary()));
        }
        if (runtimeStatusText != null) {
            runtimeStatusText.setText(getString(
                    R.string.satnet_verifier_runtime_summary,
                    SatnetRuntimeConfig.getRoleSummary(SatnetRoleManager.ROLE_VERIFIER),
                    runtimeStatus.getVerifierBlockingMessage()));
        }
        return runtimeStatus;
    }

    @Override
    protected void onDestroy() {
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }
}

