package org.servalproject.satnet.ui;

import android.content.Context;
import android.content.Intent;
import android.view.WindowManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.bitcoin.BitcoinWallet;
import org.servalproject.satnet.SatnetRoleManager;
import org.servalproject.satnet.SatnetRuntimeConfig;
import org.servalproject.voucher.BitcoinVoucher;
import org.servalproject.voucher.VoucherAuditRhizomeSync;
import org.servalproject.voucher.VoucherLedger;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
public class VoucherRedemptionActivityTest {

    private Context context;
    private ServalBatPhoneApplication app;

    @Before
    public void setUp() {
        app = SatnetRuntimeTestHelper.prepareApp();
        context = SatnetRuntimeTestHelper.resetAppData(app);
    }

    @Test
    public void readyVoucherStartupEnablesVoucherIntakeAndShowsRuntimeSummary() {
        SatnetRuntimeTestHelper.setRuntimeReady(app);
        new SatnetRoleManager(context).registerAsUser();

        try (ActivityController<VoucherRedemptionActivity> controller = Robolectric.buildActivity(
                VoucherRedemptionActivity.class,
                new Intent(context, VoucherRedemptionActivity.class))) {
            VoucherRedemptionActivity activity = controller.create().start().resume().get();

            TextView stageBadge = activity.findViewById(R.id.voucher_stage_badge_text);
            TextView runtimeStatus = activity.findViewById(R.id.voucher_runtime_status_text);
            Button scanButton = activity.findViewById(R.id.scan_button);
            Button manualEntryButton = activity.findViewById(R.id.manual_entry_button);
            Button redeemButton = activity.findViewById(R.id.redeem_button);
            EditText voucherCodeInput = activity.findViewById(R.id.voucher_code_input);
            LinearLayout manualEntryLayout = activity.findViewById(R.id.manual_entry_layout);

            assertFalse(activity.isFinishing());
            assertTrue((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
            assertEquals(SatnetRuntimeConfig.getWalletSummary(), stageBadge.getText().toString());
            assertTrue(runtimeStatus.getText().toString().contains(
                    SatnetRuntimeConfig.getRoleSummary(SatnetRoleManager.ROLE_USER)));
            assertTrue(scanButton.isEnabled());
            assertTrue(manualEntryButton.isEnabled());
            assertTrue(voucherCodeInput.isEnabled());
            assertFalse(redeemButton.isEnabled());
            assertEquals(View.GONE, manualEntryLayout.getVisibility());
        }
    }

    @Test
    public void warmingVoucherStartupDisablesVoucherIntakeUntilStartupCompletes() {
        SatnetRuntimeTestHelper.setRuntimeWarming(app);
        new SatnetRoleManager(context).registerAsUser();

        try (ActivityController<VoucherRedemptionActivity> controller = Robolectric.buildActivity(
                VoucherRedemptionActivity.class,
                new Intent(context, VoucherRedemptionActivity.class))) {
            VoucherRedemptionActivity activity = controller.create().start().resume().get();

            Button scanButton = activity.findViewById(R.id.scan_button);
            Button manualEntryButton = activity.findViewById(R.id.manual_entry_button);
            EditText voucherCodeInput = activity.findViewById(R.id.voucher_code_input);
            TextView runtimeStatus = activity.findViewById(R.id.voucher_runtime_status_text);

            assertFalse(activity.isFinishing());
            assertFalse(scanButton.isEnabled());
            assertFalse(manualEntryButton.isEnabled());
            assertFalse(voucherCodeInput.isEnabled());
            assertEquals(context.getString(R.string.satnet_voucher_scan_button_warming), scanButton.getText().toString());
            assertTrue(runtimeStatus.getText().toString().contains(
                    SatnetRuntimeConfig.getRoleSummary(SatnetRoleManager.ROLE_USER)));
        }
    }

    @Test
    public void validVoucherPayloadDisplaysDetailsAndEnablesRedemption() throws Exception {
        SatnetRuntimeTestHelper.setRuntimeReady(app);
        new SatnetRoleManager(context).registerAsUser();
        BitcoinVoucher voucher = BitcoinVoucher.generateNew("agent-7", 1_000L, 12);
        try (VoucherLedger voucherLedger = new VoucherLedger(context)) {
            voucherLedger.recordIssuedVoucher(voucher);
        }

        try (ActivityController<VoucherRedemptionActivity> controller = Robolectric.buildActivity(
                VoucherRedemptionActivity.class,
                new Intent(context, VoucherRedemptionActivity.class))) {
            VoucherRedemptionActivity activity = controller.create().start().resume().get();

            invokeProcessQrScanResult(activity, voucher.getQRPayload());

            TextView voucherDetails = activity.findViewById(R.id.voucher_details_text);
            TextView manifestBadge = activity.findViewById(R.id.voucher_manifest_badge);
            TextView ledgerBadge = activity.findViewById(R.id.voucher_ledger_badge);
            TextView rotationBadge = activity.findViewById(R.id.voucher_rotation_badge);
            TextView auditHistory = activity.findViewById(R.id.voucher_audit_history_text);
            EditText voucherCodeInput = activity.findViewById(R.id.voucher_code_input);
            Button redeemButton = activity.findViewById(R.id.redeem_button);
            LinearLayout manualEntryLayout = activity.findViewById(R.id.manual_entry_layout);

            SatnetRuntimeTestHelper.waitForCondition(() -> voucherDetails.getText().toString().contains(voucher.getVoucherId()));

            assertEquals(View.VISIBLE, manualEntryLayout.getVisibility());
            assertTrue(voucherDetails.getText().toString().contains(voucher.getVoucherId()));
            assertTrue(voucherDetails.getText().toString().contains("agent-7"));
            assertTrue(voucherDetails.getText().toString().contains(voucher.getIssuerKeystoreAlias()));
            assertTrue(voucherDetails.getText().toString().contains(String.valueOf(voucher.getIssuerRotationEpoch())));
            assertTrue(voucherDetails.getText().toString().contains(voucher.getSecondSignaturePublicKeyReference()));
            assertEquals("Manifest verified", manifestBadge.getText().toString());
            assertEquals("Ledger matched", ledgerBadge.getText().toString());
            assertEquals("No rotation detected", rotationBadge.getText().toString());
            assertTrue(auditHistory.getText().toString().contains("No verifier audit history"));
            assertEquals(voucher.getNumericCode(), voucherCodeInput.getText().toString());
            assertTrue(redeemButton.isEnabled());
        }
    }

    @Test
    public void redemptionShowsPersistedVerifierAuditBadges() throws Exception {
        SatnetRuntimeTestHelper.setRuntimeReady(app);
        new SatnetRoleManager(context).registerAsUser();
        BitcoinVoucher voucher = BitcoinVoucher.generateNew("agent-9", 3_000L, 12);
        try (VoucherLedger voucherLedger = new VoucherLedger(context)) {
            voucherLedger.recordIssuedVoucher(voucher);
            voucherLedger.recordVerifierAudit(voucher.getVoucherId(), true, true, false,
                    "Voucher detached metadata verified", "payload_inspection");
        }

        try (ActivityController<VoucherRedemptionActivity> controller = Robolectric.buildActivity(
                VoucherRedemptionActivity.class,
                new Intent(context, VoucherRedemptionActivity.class))) {
            VoucherRedemptionActivity activity = controller.create().start().resume().get();

            invokeProcessQrScanResult(activity, voucher.getQRPayload());

            TextView manifestBadge = activity.findViewById(R.id.voucher_manifest_badge);
            TextView ledgerBadge = activity.findViewById(R.id.voucher_ledger_badge);
            TextView rotationBadge = activity.findViewById(R.id.voucher_rotation_badge);
            TextView auditHistory = activity.findViewById(R.id.voucher_audit_history_text);

            SatnetRuntimeTestHelper.waitForCondition(() -> manifestBadge.getText().toString().contains("Manifest verified"));

            assertEquals("Manifest verified", manifestBadge.getText().toString());
            assertEquals("Ledger matched", ledgerBadge.getText().toString());
            assertEquals("No rotation detected", rotationBadge.getText().toString());
            assertTrue(auditHistory.getText().toString().contains("payload inspection"));
            assertTrue(auditHistory.getText().toString().contains("local device"));
        }
    }

    @Test
    public void redemptionShowsMeshImportedAuditProvenance() throws Exception {
        SatnetRuntimeTestHelper.setRuntimeReady(app);
        new SatnetRoleManager(context).registerAsUser();
        BitcoinVoucher voucher = BitcoinVoucher.generateNew("agent-10", 4_000L, 12);
        try (VoucherLedger voucherLedger = new VoucherLedger(context)) {
            voucherLedger.recordIssuedVoucher(voucher);
            java.util.List<VoucherLedger.VerifierAuditRecord> exportedRecords = java.util.Collections.singletonList(
                    new VoucherLedger.VerifierAuditRecord(
                            "mesh-record-one",
                            voucher.getVoucherId(),
                            true,
                            true,
                            false,
                            5000L,
                            "mesh verifier evidence",
                            "payload_inspection",
                            VoucherLedger.AUDIT_ORIGIN_LOCAL,
                            "node-test",
                            true,
                            6000L));
            String payload = VoucherAuditRhizomeSync.buildAuditExportPayload(exportedRecords, "node-test");
            VoucherAuditRhizomeSync.importAuditExportPayload(voucherLedger, payload);
        }

        try (ActivityController<VoucherRedemptionActivity> controller = Robolectric.buildActivity(
                VoucherRedemptionActivity.class,
                new Intent(context, VoucherRedemptionActivity.class))) {
            VoucherRedemptionActivity activity = controller.create().start().resume().get();

            invokeProcessQrScanResult(activity, voucher.getQRPayload());

            TextView auditHistory = activity.findViewById(R.id.voucher_audit_history_text);

            SatnetRuntimeTestHelper.waitForCondition(() -> auditHistory.getText().toString().contains("mesh imported from node-test"));

            assertTrue(auditHistory.getText().toString().contains("mesh imported from node-test"));
            assertTrue(auditHistory.getText().toString().contains("payload inspection"));
        }
    }

    @Test
    public void redemptionBlocksVoucherWhenStoredMetadataDoesNotMatchPayload() throws Exception {
        SatnetRuntimeTestHelper.setRuntimeReady(app);
        new SatnetRoleManager(context).registerAsUser();
        BitcoinVoucher voucher = BitcoinVoucher.generateNew("agent-8", 2_000L, 12);
        try (VoucherLedger voucherLedger = new VoucherLedger(context)) {
            voucherLedger.recordIssuedVoucher(voucher);
            voucherLedger.getWritableDatabase().execSQL(
                    "UPDATE vouchers SET issuer_rotation_epoch = issuer_rotation_epoch + 1 WHERE voucher_id = ?",
                    new Object[]{voucher.getVoucherId()});
        }

        try (ActivityController<VoucherRedemptionActivity> controller = Robolectric.buildActivity(
                VoucherRedemptionActivity.class,
                new Intent(context, VoucherRedemptionActivity.class))) {
            VoucherRedemptionActivity activity = controller.create().start().resume().get();

            invokeProcessQrScanResult(activity, voucher.getQRPayload());

            Button redeemButton = activity.findViewById(R.id.redeem_button);
            TextView voucherDetails = activity.findViewById(R.id.voucher_details_text);

            SatnetRuntimeTestHelper.waitForCondition(() -> ShadowToast.getLatestToast() != null);

            assertFalse(redeemButton.isEnabled());
            assertEquals(context.getString(R.string.satnet_voucher_details_placeholder), voucherDetails.getText().toString());
            assertTrue(ShadowToast.getTextOfLatestToast().contains("metadata"));
        }
    }

    @Test
    public void redemptionShowsDetailsButDisablesRedeemWhenVoucherIsOnDisputeHold() throws Exception {
        SatnetRuntimeTestHelper.setRuntimeReady(app);
        new SatnetRoleManager(context).registerAsUser();
        BitcoinVoucher voucher = BitcoinVoucher.generateNew("agent-11", 2_500L, 12);
        try (VoucherLedger voucherLedger = new VoucherLedger(context)) {
            voucherLedger.recordIssuedVoucher(voucher);
            voucherLedger.openDispute(voucher.getVoucherId(),
                    "user-subject",
                    "user-root",
                    SatnetRoleManager.ROLE_USER,
                    "PRICE_MISMATCH",
                    "bundle-ref-2");
        }

        try (ActivityController<VoucherRedemptionActivity> controller = Robolectric.buildActivity(
                VoucherRedemptionActivity.class,
                new Intent(context, VoucherRedemptionActivity.class))) {
            VoucherRedemptionActivity activity = controller.create().start().resume().get();

            invokeProcessQrScanResult(activity, voucher.getQRPayload());

            Button redeemButton = activity.findViewById(R.id.redeem_button);
            TextView voucherDetails = activity.findViewById(R.id.voucher_details_text);

            SatnetRuntimeTestHelper.waitForCondition(() -> ShadowToast.getLatestToast() != null);

            assertTrue(voucherDetails.getText().toString().contains(voucher.getVoucherId()));
            assertTrue(voucherDetails.getText().toString().contains("dispute review"));
            assertFalse(redeemButton.isEnabled());
            assertTrue(ShadowToast.getTextOfLatestToast().contains("Voucher blocked by policy"));
        }
    }

    @Test
    public void redeemWithoutWalletSessionRoutesBackToUnlockFlow() throws Exception {
        SatnetRuntimeTestHelper.setRuntimeReady(app);
        new SatnetRoleManager(context).registerAsUser();

        BitcoinWallet wallet = new BitcoinWallet(context, BitcoinWalletActivity.DEFAULT_WALLET_ID, true);
        wallet.importFromMnemonic(
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
                "1234");
        BitcoinVoucher voucher = BitcoinVoucher.generateNew("agent-7", 1_000L, 12);

        Intent intent = new Intent(context, VoucherRedemptionActivity.class);
        intent.putExtra(BitcoinWalletActivity.EXTRA_WALLET_ID, BitcoinWalletActivity.DEFAULT_WALLET_ID);

        try (ActivityController<VoucherRedemptionActivity> controller = Robolectric.buildActivity(
                VoucherRedemptionActivity.class,
                intent)) {
            VoucherRedemptionActivity activity = controller.create().start().resume().get();

            invokeProcessQrScanResult(activity, voucher.getQRPayload());

            Button redeemButton = activity.findViewById(R.id.redeem_button);
            SatnetRuntimeTestHelper.waitForCondition(redeemButton::isEnabled);

            invokeRedeemVoucher(activity);
            SatnetRuntimeTestHelper.waitForCondition(activity::isFinishing);

            Intent nextIntent = shadowOf(activity).getNextStartedActivity();
            assertNotNull(nextIntent);
            assertNotNull(nextIntent.getComponent());
            assertEquals(BitcoinWalletSetupActivity.class.getName(), nextIntent.getComponent().getClassName());
            assertEquals(BitcoinWalletActivity.DEFAULT_WALLET_ID,
                    nextIntent.getStringExtra(BitcoinWalletActivity.EXTRA_WALLET_ID));
            assertTrue(nextIntent.getBooleanExtra(BitcoinWalletSetupActivity.EXTRA_UNLOCK_ONLY, false));
        }
    }

    private void invokeProcessQrScanResult(VoucherRedemptionActivity activity, String payload) throws Exception {
        Method method = VoucherRedemptionActivity.class.getDeclaredMethod("processQRScanResult", String.class);
        method.setAccessible(true);
        method.invoke(activity, payload);
    }

    private void invokeRedeemVoucher(VoucherRedemptionActivity activity) throws Exception {
        Method method = VoucherRedemptionActivity.class.getDeclaredMethod("redeemVoucher");
        method.setAccessible(true);
        method.invoke(activity);
    }
}

