package org.servalproject.satnet.ui;

import android.content.Context;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.satnet.SatnetRoleManager;
import org.servalproject.satnet.SatnetRuntimeConfig;
import org.servalproject.satnet.SatnetStartupGate;
import org.servalproject.satnet.verifier.SettlementVerifier;
import org.servalproject.voucher.BitcoinVoucher;
import org.servalproject.voucher.VoucherLedger;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
public class VerifierDashboardActivityTest {

	private Context context;
	private ServalBatPhoneApplication app;

	@Before
	public void setUp() {
		app = SatnetRuntimeTestHelper.prepareApp();
		context = SatnetRuntimeTestHelper.resetAppData(app);
	}

	@Test
	public void readyVerifierStartupShowsVerifierRuntimeDetailsAndPendingVoucher() throws Exception {
		SatnetRuntimeTestHelper.setRuntimeReady(app);
		new SatnetRoleManager(context).registerAsVerifier();
		BitcoinVoucher pendingVoucher = BitcoinVoucher.generateNew(
				"agent-42",
				5_000L,
				24,
				BitcoinVoucher.DIRECTION_SELL,
				67250.5,
				"UGX");
		try (VoucherLedger voucherLedger = new VoucherLedger(context)) {
			voucherLedger.recordBidirectionalVoucher(pendingVoucher);
		}

		try (ActivityController<VerifierDashboardActivity> controller = Robolectric.buildActivity(VerifierDashboardActivity.class)) {
			VerifierDashboardActivity activity = controller.create().start().resume().get();
			TextView stageBadge = activity.findViewById(R.id.verifier_stage_badge_text);
			TextView runtimeStatus = activity.findViewById(R.id.verifier_runtime_status_text);
			TextView summary = activity.findViewById(R.id.verifier_summary_text);
			TextView riskBadge = activity.findViewById(R.id.verifier_risk_badge);
			TextView disputeBadge = activity.findViewById(R.id.verifier_dispute_badge);
			EditText voucherIdInput = activity.findViewById(R.id.verifier_voucher_id_input);

			SatnetRuntimeTestHelper.waitForCondition(() -> summary.getText().toString().contains(pendingVoucher.getVoucherId()));

			assertFalse(activity.isFinishing());
			assertTrue((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
			assertNotNull(summary.getText());
			assertTrue(stageBadge.getText().toString().contains(SatnetStartupGate.evaluate(context).stageBadge));
			assertTrue(stageBadge.getText().toString().contains(SettlementVerifier.getVerifierWindowSummary()));
			assertTrue(runtimeStatus.getText().toString().contains(
					SatnetRuntimeConfig.getRoleSummary(SatnetRoleManager.ROLE_VERIFIER)));
			assertTrue(summary.getText().toString().contains(pendingVoucher.getVoucherId()));
			assertTrue(summary.getText().toString().contains("agent-42"));
			assertTrue(summary.getText().toString().contains("5,000".replace(",", "")) || summary.getText().toString().contains("5000"));
			assertEquals(pendingVoucher.getVoucherId(), voucherIdInput.getText().toString());
			assertEquals("Risk clear", riskBadge.getText().toString());
			assertEquals("No dispute", disputeBadge.getText().toString());
		}
	}

	@Test
	public void verifierWaitsForRhizomeBeforeEnablingActions() {
		SatnetRuntimeTestHelper.setRhizomePending(app);
		new SatnetRoleManager(context).registerAsVerifier();

		try (ActivityController<VerifierDashboardActivity> controller = Robolectric.buildActivity(VerifierDashboardActivity.class)) {
			VerifierDashboardActivity activity = controller.create().start().resume().get();

			TextView summary = activity.findViewById(R.id.verifier_summary_text);
			EditText voucherIdInput = activity.findViewById(R.id.verifier_voucher_id_input);
			EditText payloadInput = activity.findViewById(R.id.verifier_payload_input);
			Button refreshButton = activity.findViewById(R.id.verifier_refresh_button);
			Button inspectButton = activity.findViewById(R.id.verifier_inspect_button);
			Button verifyButton = activity.findViewById(R.id.verifier_verify_button);
			Button releaseButton = activity.findViewById(R.id.verifier_release_expired_button);
			String blockingMessage = SatnetStartupGate.evaluate(context).getVerifierBlockingMessage();

			assertFalse(activity.isFinishing());
			assertTrue(summary.getText().toString().contains(blockingMessage));
			assertFalse(voucherIdInput.isEnabled());
			assertFalse(payloadInput.isEnabled());
			assertFalse(refreshButton.isEnabled());
			assertFalse(inspectButton.isEnabled());
			assertFalse(verifyButton.isEnabled());
			assertFalse(releaseButton.isEnabled());
		}
	}

	@Test
	public void inspectPayloadShowsDetachedManifestAndRotationMetadata() throws Exception {
		SatnetRuntimeTestHelper.setRuntimeReady(app);
		new SatnetRoleManager(context).registerAsVerifier();
		BitcoinVoucher pendingVoucher = BitcoinVoucher.generateNew(
				"agent-55",
				7_000L,
				24,
				BitcoinVoucher.DIRECTION_SELL,
				71234.25,
				"KES");
		try (VoucherLedger voucherLedger = new VoucherLedger(context)) {
			voucherLedger.recordBidirectionalVoucher(pendingVoucher);
		}

		try (ActivityController<VerifierDashboardActivity> controller = Robolectric.buildActivity(VerifierDashboardActivity.class)) {
			VerifierDashboardActivity activity = controller.create().start().resume().get();
			Button inspectButton = activity.findViewById(R.id.verifier_inspect_button);
			EditText payloadInput = activity.findViewById(R.id.verifier_payload_input);
			TextView summary = activity.findViewById(R.id.verifier_summary_text);
			TextView manifestBadge = activity.findViewById(R.id.verifier_manifest_badge);
			TextView ledgerBadge = activity.findViewById(R.id.verifier_ledger_badge);
			TextView rotationBadge = activity.findViewById(R.id.verifier_rotation_badge);
			TextView riskBadge = activity.findViewById(R.id.verifier_risk_badge);
			TextView disputeBadge = activity.findViewById(R.id.verifier_dispute_badge);
			TextView auditHistory = activity.findViewById(R.id.verifier_audit_history_text);
			SatnetRuntimeTestHelper.waitForCondition(inspectButton::isEnabled);
			payloadInput.setText(pendingVoucher.getQRPayload());
			ShadowToast.reset();

			invokeInspectVoucherPayload(activity);

			SatnetRuntimeTestHelper.waitForCondition(() -> summary.getText().toString().contains(pendingVoucher.getSecondSignatureMetadataReference()));
			assertTrue(summary.getText().toString().contains(pendingVoucher.getSecondSignatureMetadataReference()));
			assertTrue(summary.getText().toString().contains(String.valueOf(pendingVoucher.getIssuerRotationEpoch())));
			assertEquals("Manifest verified", manifestBadge.getText().toString());
			assertEquals("Ledger matched", ledgerBadge.getText().toString());
			assertEquals("No rotation detected", rotationBadge.getText().toString());
			assertEquals("Risk clear", riskBadge.getText().toString());
			assertEquals("No dispute", disputeBadge.getText().toString());
			assertTrue(auditHistory.getText().toString().contains("payload inspection"));
			assertTrue(auditHistory.getText().toString().contains("local device"));
			try (VoucherLedger voucherLedger = new VoucherLedger(context)) {
				VoucherLedger.VerifierAuditSnapshot auditSnapshot = voucherLedger.getVerifierAuditSnapshot(pendingVoucher.getVoucherId());
				java.util.List<VoucherLedger.VerifierAuditRecord> auditHistoryRecords = voucherLedger.listVerifierAuditRecords(pendingVoucher.getVoucherId());
				assertNotNull(auditSnapshot);
				assertTrue(auditSnapshot.hasAudit());
				assertEquals("payload_inspection", auditSnapshot.inspectionSource);
				assertTrue(auditSnapshot.isManifestVerified());
				assertTrue(auditSnapshot.isLedgerMatched());
				assertEquals(1, auditHistoryRecords.size());
			}
		}
	}

	@Test
	public void verifierShowsRiskAndDisputeBadgesForHeldVoucher() throws Exception {
		SatnetRuntimeTestHelper.setRuntimeReady(app);
		new SatnetRoleManager(context).registerAsVerifier();
		BitcoinVoucher pendingVoucher = BitcoinVoucher.generateNew(
				"agent-88",
				4_500L,
				24,
				BitcoinVoucher.DIRECTION_SELL,
				60111.0,
				"USD");
		try (VoucherLedger voucherLedger = new VoucherLedger(context)) {
			voucherLedger.recordBidirectionalVoucher(pendingVoucher);
			voucherLedger.openDispute(pendingVoucher.getVoucherId(),
					"user-subject",
					"user-root",
					SatnetRoleManager.ROLE_USER,
					"PRICE_MISMATCH",
					"bundle-ref");
		}

		try (ActivityController<VerifierDashboardActivity> controller = Robolectric.buildActivity(VerifierDashboardActivity.class)) {
			VerifierDashboardActivity activity = controller.create().start().resume().get();
			TextView summary = activity.findViewById(R.id.verifier_summary_text);
			TextView riskBadge = activity.findViewById(R.id.verifier_risk_badge);
			TextView disputeBadge = activity.findViewById(R.id.verifier_dispute_badge);

			SatnetRuntimeTestHelper.waitForCondition(() -> summary.getText().toString().contains(pendingVoucher.getVoucherId()));

			assertEquals("Risk hold", riskBadge.getText().toString());
			assertEquals("Dispute open", disputeBadge.getText().toString());
		}
	}

	@Test
	public void verifierShowsQuorumPendingPolicyStateFromPersistedDecision() throws Exception {
		SatnetRuntimeTestHelper.setRuntimeReady(app);
		new SatnetRoleManager(context).registerAsVerifier();
		BitcoinVoucher pendingVoucher = BitcoinVoucher.generateNew(
				"agent-77",
				5_500L,
				24,
				BitcoinVoucher.DIRECTION_SELL,
				61888.0,
				"KES");
		try (VoucherLedger voucherLedger = new VoucherLedger(context)) {
			voucherLedger.recordBidirectionalVoucher(pendingVoucher);
			voucherLedger.markSettlementVerified(
					pendingVoucher.getVoucherId(),
					"verifier-subject-b",
					"verifier-root-b",
					1,
					2);
		}

		try (ActivityController<VerifierDashboardActivity> controller = Robolectric.buildActivity(VerifierDashboardActivity.class)) {
			VerifierDashboardActivity activity = controller.create().start().resume().get();
			TextView summary = activity.findViewById(R.id.verifier_summary_text);
			TextView policyState = activity.findViewById(R.id.verifier_policy_state_text);
			TextView riskBadge = activity.findViewById(R.id.verifier_risk_badge);
			TextView disputeBadge = activity.findViewById(R.id.verifier_dispute_badge);

			SatnetRuntimeTestHelper.waitForCondition(() -> summary.getText().toString().contains(pendingVoucher.getVoucherId()));

			assertTrue(policyState.getText().toString().contains("Policy: QUORUM_PENDING"));
			assertTrue(policyState.getText().toString().contains("Quorum pending (1/2)"));
			assertTrue(policyState.getText().toString().contains("Verifier quorum pending (1/2)"));
			assertEquals("Risk clear", riskBadge.getText().toString());
			assertEquals("No dispute", disputeBadge.getText().toString());
		}
	}

	@Test
	public void verifierRequiresPayloadInspectionBeforeApprovingPhase3Voucher() throws Exception {
		SatnetRuntimeTestHelper.setRuntimeReady(app);
		new SatnetRoleManager(context).registerAsVerifier();
		BitcoinVoucher pendingVoucher = BitcoinVoucher.generateNew(
				"agent-56",
				8_000L,
				24,
				BitcoinVoucher.DIRECTION_SELL,
				65555.0,
				"USD");
		try (VoucherLedger voucherLedger = new VoucherLedger(context)) {
			voucherLedger.recordBidirectionalVoucher(pendingVoucher);
		}

		try (ActivityController<VerifierDashboardActivity> controller = Robolectric.buildActivity(VerifierDashboardActivity.class)) {
			VerifierDashboardActivity activity = controller.create().start().resume().get();
			Button verifyButton = activity.findViewById(R.id.verifier_verify_button);
			SatnetRuntimeTestHelper.waitForCondition(verifyButton::isEnabled);
			EditText voucherIdInput = activity.findViewById(R.id.verifier_voucher_id_input);
			voucherIdInput.setText(pendingVoucher.getVoucherId());
			ShadowToast.reset();

			invokeVerifySelectedVoucher(activity);

			SatnetRuntimeTestHelper.waitForCondition(() -> ShadowToast.getLatestToast() != null
					&& ShadowToast.getTextOfLatestToast().contains("Inspect the full voucher payload"));
			assertTrue(ShadowToast.getTextOfLatestToast().contains("Inspect the full voucher payload"));
			try (VoucherLedger voucherLedger = new VoucherLedger(context)) {
				try (android.database.Cursor pending = voucherLedger.getPendingSettlementVerification()) {
					assertTrue(pending.moveToFirst());
				}
			}
		}
	}

	@Test
	public void verifierCanApprovePhase3VoucherAfterMatchingPayloadInspection() throws Exception {
		SatnetRuntimeTestHelper.setRuntimeReady(app);
		new SatnetRoleManager(context).registerAsVerifier();
		BitcoinVoucher pendingVoucher = BitcoinVoucher.generateNew(
				"agent-57",
				9_000L,
				24,
				BitcoinVoucher.DIRECTION_SELL,
				64000.0,
				"UGX");
		try (VoucherLedger voucherLedger = new VoucherLedger(context)) {
			voucherLedger.recordBidirectionalVoucher(pendingVoucher);
		}

		try (ActivityController<VerifierDashboardActivity> controller = Robolectric.buildActivity(VerifierDashboardActivity.class)) {
			VerifierDashboardActivity activity = controller.create().start().resume().get();
			Button verifyButton = activity.findViewById(R.id.verifier_verify_button);
			SatnetRuntimeTestHelper.waitForCondition(verifyButton::isEnabled);
			EditText voucherIdInput = activity.findViewById(R.id.verifier_voucher_id_input);
			EditText payloadInput = activity.findViewById(R.id.verifier_payload_input);
			TextView auditHistory = activity.findViewById(R.id.verifier_audit_history_text);
			voucherIdInput.setText(pendingVoucher.getVoucherId());
			payloadInput.setText(pendingVoucher.getQRPayload());
			ShadowToast.reset();

			invokeVerifySelectedVoucher(activity);

			SatnetRuntimeTestHelper.waitForCondition(() -> ShadowToast.getLatestToast() != null
					&& ShadowToast.getTextOfLatestToast().contains(pendingVoucher.getVoucherId()));
			SatnetRuntimeTestHelper.waitForCondition(() -> auditHistory.getText().toString().contains("settlement verification"));
			assertTrue(ShadowToast.getTextOfLatestToast().contains(pendingVoucher.getVoucherId()));
			assertTrue(auditHistory.getText().toString().contains("settlement verification"));
			assertTrue(auditHistory.getText().toString().contains("local device"));
			try (VoucherLedger voucherLedger = new VoucherLedger(context);
				 android.database.Cursor pending = voucherLedger.getPendingSettlementVerification()) {
				java.util.List<VoucherLedger.VerifierAuditRecord> auditHistoryRecords = voucherLedger.listVerifierAuditRecords(pendingVoucher.getVoucherId());
				assertFalse(pending.moveToFirst());
				VoucherLedger.VerifierAuditSnapshot auditSnapshot = voucherLedger.getVerifierAuditSnapshot(pendingVoucher.getVoucherId());
				assertNotNull(auditSnapshot);
				assertEquals("settlement_verification", auditSnapshot.inspectionSource);
				assertTrue(auditSnapshot.isManifestVerified());
				assertTrue(auditSnapshot.isLedgerMatched());
				assertEquals(1, auditHistoryRecords.size());
			}
		}
	}

	@Test
	public void verifierCannotApproveOwnAgentVoucherWhenUserHasMultipleRoles() throws Exception {
		SatnetRuntimeTestHelper.setRuntimeReady(app);
		SatnetRoleManager roleManager = new SatnetRoleManager(context);
		roleManager.registerAsAgent("Field Agent", "Kampala");
		roleManager.registerAsVerifier();
		BitcoinVoucher pendingVoucher = BitcoinVoucher.generateNew(
				roleManager.getAgentName(),
				6_000L,
				24,
				BitcoinVoucher.DIRECTION_SELL,
				66000.0,
				"KES");
		try (VoucherLedger voucherLedger = new VoucherLedger(context)) {
			voucherLedger.recordBidirectionalVoucher(pendingVoucher, roleManager.getParticipantSubjectId());
		}

		try (ActivityController<VerifierDashboardActivity> controller = Robolectric.buildActivity(VerifierDashboardActivity.class)) {
			VerifierDashboardActivity activity = controller.create().start().resume().get();
			Button verifyButton = activity.findViewById(R.id.verifier_verify_button);
			EditText voucherIdInput = activity.findViewById(R.id.verifier_voucher_id_input);
			SatnetRuntimeTestHelper.waitForCondition(verifyButton::isEnabled);
			voucherIdInput.setText(pendingVoucher.getVoucherId());
			ShadowToast.reset();

			invokeVerifySelectedVoucher(activity);

			SatnetRuntimeTestHelper.waitForCondition(() -> ShadowToast.getLatestToast() != null
					&& ShadowToast.getTextOfLatestToast().contains("Conflict of interest"));
			assertTrue(ShadowToast.getTextOfLatestToast().contains("Conflict of interest"));
			try (VoucherLedger voucherLedger = new VoucherLedger(context);
				 android.database.Cursor pending = voucherLedger.getPendingSettlementVerification()) {
				assertTrue(pending.moveToFirst());
			}
		}
	}

	private void invokeInspectVoucherPayload(VerifierDashboardActivity activity) throws Exception {
		Method method = VerifierDashboardActivity.class.getDeclaredMethod("inspectVoucherPayload");
		method.setAccessible(true);
		method.invoke(activity);
	}

	private void invokeVerifySelectedVoucher(VerifierDashboardActivity activity) throws Exception {
		Method method = VerifierDashboardActivity.class.getDeclaredMethod("verifySelectedVoucher");
		method.setAccessible(true);
		method.invoke(activity);
	}

}

