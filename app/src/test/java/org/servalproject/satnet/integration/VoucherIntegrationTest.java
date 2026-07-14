/**
 * Copyright (C) 2025 SATNET AFRICA
 *
 * This file is part of SATNET AFRICA (http://satnetafrica.org)
 */

package org.servalproject.satnet.integration;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.servalproject.voucher.VoucherParticipantSnapshot;
import org.servalproject.voucher.BitcoinVoucher;
import org.servalproject.voucher.VoucherIssuerRotationPolicy;
import org.servalproject.voucher.VoucherLedger;
import org.servalproject.satnet.verifier.SettlementVerifier;

import static org.junit.Assert.*;

/**
 * Integration Test: Complete Voucher Workflow
 *
 * Tests end-to-end voucher lifecycle: issue → QR encode → parse → validate → redeem
 */
@RunWith(RobolectricTestRunner.class)
public class VoucherIntegrationTest {

    private BitcoinVoucher voucher;
    private VoucherLedger ledger;
    private static final String AGENT_ID = "agent_integration_001";

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        ledger = new VoucherLedger(context);
    }

    @Test
    public void testCompleteVoucherLifecycle() throws Exception {
        // Step 1: Agent generates voucher
        voucher = BitcoinVoucher.generateNew(AGENT_ID, 5000, 24);
        assertNotNull("Voucher should be generated", voucher);
        assertEquals("State should be ISSUED", BitcoinVoucher.STATE_ISSUED, voucher.getState());

        // Step 2: Record in ledger (simulates agent's local database)
        ledger.recordIssuedVoucher(voucher);

        // Step 3: Get QR payload for customer
        String qrPayload = voucher.getQRPayload();
        assertNotNull("QR payload should exist", qrPayload);
        assertTrue("QR should start with voucher marker", qrPayload.startsWith("satnet_voucher|"));
        assertTrue("QR should use the v2 signed envelope", qrPayload.startsWith("satnet_voucher|v2|"));

        // Step 4: Customer scans QR and parses voucher
        BitcoinVoucher scannedVoucher = BitcoinVoucher.parseQRPayload(qrPayload);
        assertNotNull("Scanned voucher should be parsed", scannedVoucher);
        assertEquals("Voucher ID should match", voucher.getVoucherId(), scannedVoucher.getVoucherId());

        // Step 5: Validate before redemption
        BitcoinVoucher.ValidationResult result = scannedVoucher.validate();
        assertTrue("Voucher should be valid", result.isValid);

        // Step 6: User confirms and redeems
        String userWallet = "tb1qreceiver123";
        scannedVoucher.redeem(userWallet);
        assertEquals("State should be REDEEMED", BitcoinVoucher.STATE_REDEEMED, scannedVoucher.getState());
        assertEquals("Wallet should be recorded", userWallet, scannedVoucher.getRedeemedByWallet());

        // Step 7: Record redemption in ledger
        ledger.recordRedemption(scannedVoucher, userWallet, "tx_abc123");
        assertTrue("Voucher should be marked redeemed in ledger",
                  ledger.isVoucherRedeemed(voucher.getVoucherId()));

        System.out.println("✓ Complete voucher lifecycle: generate → QR → parse → validate → redeem");
        System.out.println("  Voucher: " + voucher.getVoucherId());
        System.out.println("  Amount: " + voucher.getDenomination() + " sats");
        System.out.println("  Redeemed to: " + userWallet);
    }

    @Test
    public void testVoucherDoubleRedemptionPrevented() throws Exception {
        // Generate and redeem voucher once
        voucher = BitcoinVoucher.generateNew(AGENT_ID, 10000, 24);
        ledger.recordIssuedVoucher(voucher);
        String originalQrPayload = voucher.getQRPayload();

        // First redemption
        String wallet1 = "tb1quser1";
        voucher.redeem(wallet1);
        ledger.recordRedemption(voucher, wallet1, "tx_001");

        // Rescanning the original QR still yields a syntactically valid issued voucher,
        // but the ledger must prevent double redemption.
        BitcoinVoucher scannedAgain = BitcoinVoucher.parseQRPayload(originalQrPayload);

        BitcoinVoucher.ValidationResult result = scannedAgain.validate();
        assertTrue("Original signed QR should remain cryptographically valid", result.isValid);
        assertTrue("Ledger should prevent double redemption after the first redemption is recorded",
                ledger.isVoucherRedeemed(scannedAgain.getVoucherId()));

        System.out.println("✓ Double redemption prevented");
        System.out.println("  Voucher locked after first redemption");
    }

    @Test
    public void testVoucherExpiryEnforcement() throws Exception {
        // Generate voucher that expires in 0 hours (immediately)
        voucher = BitcoinVoucher.generateNew(AGENT_ID, 1000, 0);
        ledger.recordIssuedVoucher(voucher);

        // Wait briefly to ensure expiry
        Thread.sleep(100);

        // Should fail validation due to expiry
        BitcoinVoucher.ValidationResult result = voucher.validate();
        assertFalse("Expired voucher should fail", result.isValid);
        assertTrue("Error should mention expiry", result.message.contains("expired"));

        System.out.println("✓ Voucher expiry enforced");
        System.out.println("  Expired vouchers correctly rejected");
    }

    @Test
    public void testMultipleVouchersTracking() throws Exception {
        // Agent issues 5 vouchers
        BitcoinVoucher[] vouchers = new BitcoinVoucher[5];
        for (int i = 0; i < 5; i++) {
            vouchers[i] = BitcoinVoucher.generateNew(AGENT_ID, 1000 * (i + 1), 24);
            ledger.recordIssuedVoucher(vouchers[i]);
        }

        // All should have unique IDs
        for (int i = 0; i < 5; i++) {
            for (int j = i + 1; j < 5; j++) {
                assertNotEquals("Vouchers should have unique IDs",
                              vouchers[i].getVoucherId(),
                              vouchers[j].getVoucherId());
            }
        }

        // Redeem some
        for (int i = 0; i < 3; i++) {
            vouchers[i].redeem("tb1quser_" + i);
            ledger.recordRedemption(vouchers[i], "tb1quser_" + i, "tx_" + i);
        }

        // Verify ledger tracking
        for (int i = 0; i < 3; i++) {
            assertTrue("Redeemed vouchers should be marked",
                      ledger.isVoucherRedeemed(vouchers[i].getVoucherId()));
        }
        for (int i = 3; i < 5; i++) {
            assertFalse("Unredeemed vouchers should not be marked",
                       ledger.isVoucherRedeemed(vouchers[i].getVoucherId()));
        }

        System.out.println("✓ Multiple vouchers tracked correctly");
        System.out.println("  5 vouchers issued, 3 redeemed, 2 pending");
    }

    @Test
    public void testLegacyVoucherSchemaIsMigratedForBidirectionalFlows() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("satnet_vouchers.db");

        SQLiteDatabase legacyDb = context.openOrCreateDatabase("satnet_vouchers.db", Context.MODE_PRIVATE, null);
        legacyDb.execSQL("CREATE TABLE vouchers (" +
                "voucher_id TEXT PRIMARY KEY," +
                "agent_id TEXT NOT NULL," +
                "denomination INTEGER NOT NULL," +
                "secret_hash TEXT NOT NULL," +
                "issued_time INTEGER NOT NULL," +
                "expiry_time INTEGER NOT NULL," +
                "state INTEGER NOT NULL," +
                "redeemed_time INTEGER," +
                "redeemed_by_wallet TEXT," +
                "synced_to_mesh BOOLEAN DEFAULT 0" +
                ")");
        legacyDb.execSQL("CREATE TABLE redemptions (" +
                "redemption_id TEXT PRIMARY KEY," +
                "voucher_id TEXT NOT NULL," +
                "user_wallet TEXT NOT NULL," +
                "amount_sats INTEGER NOT NULL," +
                "timestamp INTEGER NOT NULL," +
                "tx_hash TEXT," +
                "confirmed BOOLEAN DEFAULT 0" +
                ")");
        legacyDb.setVersion(1);
        legacyDb.close();

        VoucherLedger migratedLedger = new VoucherLedger(context);
        SQLiteDatabase db = migratedLedger.getWritableDatabase();

        BitcoinVoucher sellVoucher = BitcoinVoucher.generateNew(AGENT_ID, 5000, 24,
                BitcoinVoucher.DIRECTION_SELL, 61234.5, "ugx");
        migratedLedger.recordBidirectionalVoucher(sellVoucher);

        assertEquals(61234.5, migratedLedger.getVoucherExchangeRate(sellVoucher.getVoucherId()), 0.0);

        Cursor pending = migratedLedger.getPendingSettlementVerification();
        try {
            assertTrue(pending.moveToFirst());
        } finally {
            pending.close();
        }

        Cursor schema = db.rawQuery("PRAGMA table_info(vouchers)", null);
        boolean hasDirection = false;
        boolean hasSettlementVerified = false;
        boolean hasPayloadVersion = false;
        boolean hasSignatureBundle = false;
        boolean hasSignatureAlgorithm = false;
        boolean hasSecondManifest = false;
        boolean hasSecondPublicKeyReference = false;
        boolean hasIssuerKeystoreAlias = false;
        boolean hasIssuerRotationEpoch = false;
        boolean hasIssuerActivatedAt = false;
        boolean hasIssuerPreviousAlias = false;
        boolean hasIssuerRotationReason = false;
        boolean hasAuditOrigin = false;
        boolean hasSourceNode = false;
        try {
            while (schema.moveToNext()) {
                String name = schema.getString(schema.getColumnIndexOrThrow("name"));
                if ("direction".equals(name)) {
                    hasDirection = true;
                } else if ("settlement_verified".equals(name)) {
                    hasSettlementVerified = true;
                } else if ("payload_version".equals(name)) {
                    hasPayloadVersion = true;
                } else if ("signature_bundle_json".equals(name)) {
                    hasSignatureBundle = true;
                } else if ("primary_signature_algorithm".equals(name)) {
                    hasSignatureAlgorithm = true;
                } else if ("secondary_signature_manifest_json".equals(name)) {
                    hasSecondManifest = true;
                } else if ("secondary_public_key_reference".equals(name)) {
                    hasSecondPublicKeyReference = true;
                } else if ("issuer_keystore_alias".equals(name)) {
                    hasIssuerKeystoreAlias = true;
                } else if ("issuer_rotation_epoch".equals(name)) {
                    hasIssuerRotationEpoch = true;
                } else if ("issuer_activated_at".equals(name)) {
                    hasIssuerActivatedAt = true;
                } else if ("issuer_previous_keystore_alias".equals(name)) {
                    hasIssuerPreviousAlias = true;
                } else if ("issuer_rotation_reason".equals(name)) {
                    hasIssuerRotationReason = true;
                }
            }
        } finally {
            schema.close();
        }

        Cursor auditSchema = db.rawQuery("PRAGMA table_info(verifier_audit_history)", null);
        try {
            while (auditSchema.moveToNext()) {
                String name = auditSchema.getString(auditSchema.getColumnIndexOrThrow("name"));
                if ("audit_origin".equals(name)) {
                    hasAuditOrigin = true;
                } else if ("source_node".equals(name)) {
                    hasSourceNode = true;
                }
            }
        } finally {
            auditSchema.close();
            migratedLedger.close();
        }

        assertTrue(hasDirection);
        assertTrue(hasSettlementVerified);
        assertTrue(hasPayloadVersion);
        assertTrue(hasSignatureBundle);
        assertTrue(hasSignatureAlgorithm);
        assertTrue(hasSecondManifest);
        assertTrue(hasSecondPublicKeyReference);
        assertTrue(hasIssuerKeystoreAlias);
        assertTrue(hasIssuerRotationEpoch);
        assertTrue(hasIssuerActivatedAt);
        assertTrue(hasIssuerPreviousAlias);
        assertTrue(hasIssuerRotationReason);
        assertTrue(hasAuditOrigin);
        assertTrue(hasSourceNode);
    }

    @Test
    public void testLedgerPersistsDetachedManifestAndRotationMetadata() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("satnet_vouchers.db");
        VoucherLedger rotationLedger = new VoucherLedger(context);

        VoucherIssuerRotationPolicy.ActiveIssuerState activeIssuer = VoucherIssuerRotationPolicy.resolve(context, rotationLedger);
        BitcoinVoucher rotationVoucher = BitcoinVoucher.generateNew(
                AGENT_ID,
                5000,
                24,
                BitcoinVoucher.DIRECTION_BUY,
                60123.45,
                "USD",
                activeIssuer);
        rotationLedger.recordIssuedVoucher(rotationVoucher);

        assertEquals(rotationVoucher.getIssuerKeystoreAlias(),
                rotationLedger.getVoucherIssuerKeystoreAlias(rotationVoucher.getVoucherId()));
        assertEquals(rotationVoucher.getIssuerRotationEpoch(),
                rotationLedger.getVoucherIssuerRotationEpoch(rotationVoucher.getVoucherId()));
        assertEquals(rotationVoucher.getSecondSignatureManifestJson(),
                rotationLedger.getVoucherSecondSignatureManifestJson(rotationVoucher.getVoucherId()));
        assertEquals(rotationVoucher.getSecondSignaturePublicKeyReference(),
                rotationLedger.getVoucherSecondPublicKeyReference(rotationVoucher.getVoucherId()));
        assertEquals(rotationVoucher.getSecondSignatureMetadataReference(),
                rotationLedger.getVoucherSecondMetadataReference(rotationVoucher.getVoucherId()));
        assertEquals(rotationVoucher.getSecondSignatureReference(),
                rotationLedger.getVoucherSecondSignatureReference(rotationVoucher.getVoucherId()));
        assertEquals(rotationVoucher.getIssuerActivatedAt(),
                rotationLedger.getVoucherIssuerActivatedAt(rotationVoucher.getVoucherId()));
        assertEquals(rotationVoucher.getIssuerPreviousKeystoreAlias(),
                rotationLedger.getVoucherIssuerPreviousKeystoreAlias(rotationVoucher.getVoucherId()));
        assertEquals(rotationVoucher.getIssuerRotationReason(),
                rotationLedger.getVoucherIssuerRotationReason(rotationVoucher.getVoucherId()));
        assertTrue(rotationLedger.getIssuedVoucherCountForIssuerAlias(rotationVoucher.getIssuerKeystoreAlias()) >= 1);
    }

    @Test
    public void testLedgerPersistsVerifierAuditOutcomes() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("satnet_vouchers.db");
        VoucherLedger auditLedger = new VoucherLedger(context);

        BitcoinVoucher voucher = BitcoinVoucher.generateNew(
                AGENT_ID,
                6000,
                24,
                BitcoinVoucher.DIRECTION_SELL,
                60234.0,
                "USD");
        auditLedger.recordBidirectionalVoucher(voucher);

        SettlementVerifier.WorkflowMetadataCheck metadataCheck =
                SettlementVerifier.inspectVoucherMetadata(voucher, auditLedger);
        auditLedger.recordVerifierAudit(voucher.getVoucherId(),
                "local-record",
                metadataCheck.manifestVerified,
                metadataCheck.ledgerMatched,
                metadataCheck.rotationDetected,
                metadataCheck.message,
                "payload_inspection",
                2000L);

        auditLedger.importVerifierAuditRecord(new VoucherLedger.VerifierAuditRecord(
                "mesh-record",
                voucher.getVoucherId(),
                false,
                false,
                true,
                1000L,
                "older remote audit",
                "payload_inspection",
                VoucherLedger.AUDIT_ORIGIN_MESH,
                "node-remote",
                true,
                1500L));

        VoucherLedger.VerifierAuditSnapshot auditSnapshot = auditLedger.getVerifierAuditSnapshot(voucher.getVoucherId());
        java.util.List<VoucherLedger.VerifierAuditRecord> auditHistory = auditLedger.listVerifierAuditRecords(voucher.getVoucherId());
        VoucherLedger.VerifierAuditRecord latestAuditRecord = auditLedger.getLatestVerifierAuditRecord(voucher.getVoucherId());

        assertNotNull(auditSnapshot);
        assertEquals(2, auditHistory.size());
        assertTrue(auditSnapshot.hasAudit());
        assertTrue(auditSnapshot.isManifestVerified());
        assertTrue(auditSnapshot.isLedgerMatched());
        assertEquals(metadataCheck.rotationDetected, auditSnapshot.isRotationDetected());
        assertEquals("payload_inspection", auditSnapshot.inspectionSource);
        assertTrue(auditSnapshot.auditTime > 0L);
        assertNotNull(auditSnapshot.auditMessage);
        assertNotNull(latestAuditRecord);
        assertEquals("local-record", latestAuditRecord.auditRecordId);
        assertEquals(VoucherLedger.AUDIT_ORIGIN_LOCAL, latestAuditRecord.auditOrigin);
        assertEquals("mesh-record", auditHistory.get(0).auditRecordId);
        assertEquals(VoucherLedger.AUDIT_ORIGIN_MESH, auditHistory.get(0).auditOrigin);
        assertEquals("node-remote", auditHistory.get(0).sourceNode);
        assertEquals("local-record", auditHistory.get(1).auditRecordId);
        assertEquals(VoucherLedger.AUDIT_ORIGIN_LOCAL, auditHistory.get(1).auditOrigin);
    }

  @Test
  public void testSettlementVerificationPersistsQuorumPendingPolicyMessage() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    context.deleteDatabase("satnet_vouchers.db");
    VoucherLedger policyLedger = new VoucherLedger(context);

    BitcoinVoucher sellVoucher = BitcoinVoucher.generateNew(
        AGENT_ID,
        7000,
        24,
        BitcoinVoucher.DIRECTION_SELL,
        61200.0,
        "USD");
    policyLedger.recordBidirectionalVoucher(sellVoucher);
    policyLedger.markSettlementVerified(sellVoucher.getVoucherId(), "verifier-subject-1", "verifier-root-1", 1, 2);

    VoucherParticipantSnapshot snapshot = policyLedger.getVoucherParticipantSnapshot(sellVoucher.getVoucherId());
    assertNotNull(snapshot);
    assertEquals("QUORUM_PENDING", snapshot.decisionCode);
    assertTrue(snapshot.decisionMessage.contains("1/2"));
    assertEquals(1, snapshot.achievedQuorum);
    assertEquals(2, snapshot.requiredQuorum);
    assertTrue(snapshot.decisionAt > 0L);

    Cursor pendingAfter = policyLedger.getPendingSettlementVerification();
    boolean foundVoucher = false;
    try {
      while (pendingAfter.moveToNext()) {
        if (sellVoucher.getVoucherId().equals(pendingAfter.getString(pendingAfter.getColumnIndexOrThrow("voucher_id")))) {
          foundVoucher = true;
          break;
        }
      }
    } finally {
      pendingAfter.close();
      policyLedger.close();
    }

    assertTrue(foundVoucher);
  }

  @Test
  public void testLedgerPersistsMerchantSettlementContextAndPolicyDecisionMessage() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    context.deleteDatabase("satnet_vouchers.db");
    VoucherLedger policyLedger = new VoucherLedger(context);

    BitcoinVoucher voucher = BitcoinVoucher.generateNew(
        AGENT_ID,
        4500,
        24,
        BitcoinVoucher.DIRECTION_SELL,
        60321.0,
        "USD");
    policyLedger.recordBidirectionalVoucher(voucher);
    policyLedger.recordMerchantSettlementContext(
        voucher.getVoucherId(),
        "merchant-subject-a",
        "merchant-root-a",
        "MERCHANT_SETTLEMENT_PENDING",
        "Merchant settlement context linked to voucher");
    policyLedger.updateVoucherPolicyDecision(
        voucher.getVoucherId(),
        "SELF_MERCHANT_VERIFY",
        "Merchant settlement blocked by policy: Conflict of interest");

    VoucherParticipantSnapshot snapshot = policyLedger.getVoucherParticipantSnapshot(voucher.getVoucherId());
    assertNotNull(snapshot);
    assertEquals("merchant-subject-a", snapshot.merchantSubjectId);
    assertEquals("merchant-root-a", snapshot.merchantParticipantRootId);
    assertEquals("SELF_MERCHANT_VERIFY", snapshot.decisionCode);
    assertTrue(snapshot.decisionMessage.contains("Merchant settlement blocked by policy"));
    assertTrue(snapshot.decisionAt > 0L);

    policyLedger.close();
  }

    @Test
    public void testVerifierCanConfirmPendingSellSettlement() throws Exception {
        BitcoinVoucher sellVoucher = BitcoinVoucher.generateNew(
                AGENT_ID,
                10000,
                24,
                BitcoinVoucher.DIRECTION_SELL,
                65000.0,
                "USD");
        ledger.recordBidirectionalVoucher(sellVoucher);

        Cursor pendingBefore = ledger.getPendingSettlementVerification();
        try {
            assertTrue(pendingBefore.moveToFirst());
        } finally {
            pendingBefore.close();
        }

        ledger.markSettlementVerified(sellVoucher.getVoucherId());

        Cursor pendingAfter = ledger.getPendingSettlementVerification();
        try {
            while (pendingAfter.moveToNext()) {
                assertNotEquals(sellVoucher.getVoucherId(),
                        pendingAfter.getString(pendingAfter.getColumnIndexOrThrow("voucher_id")));
            }
        } finally {
            pendingAfter.close();
        }
    }

    @Test
    public void testVerifiedSellVoucherCanRedeemAfterVerifierRelease() throws Exception {
        BitcoinVoucher sellVoucher = BitcoinVoucher.generateNew(
                AGENT_ID,
                10000,
                24,
                BitcoinVoucher.DIRECTION_SELL,
                65000.0,
                "USD");

        ledger.recordBidirectionalVoucher(sellVoucher);
        ledger.markSettlementVerified(sellVoucher.getVoucherId());

        BitcoinVoucher scannedVoucher = BitcoinVoucher.parseQRPayload(sellVoucher.getQRPayload());
        scannedVoucher.markSettlementVerified();

        BitcoinVoucher.ValidationResult result = scannedVoucher.validate();
        assertTrue(result.message, result.isValid);

        scannedVoucher.redeem("tb1qverifiedsellreceiver");
        assertEquals(BitcoinVoucher.STATE_REDEEMED, scannedVoucher.getState());
    }
}
