package org.servalproject.voucher;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class VoucherAuditRhizomeSyncTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("satnet_vouchers.db");
    }

    @Test
    public void importAuditExportPayloadRoundTripsAuditHistory() throws Exception {
        String voucherId = "voucher-audit-roundtrip";
        try (VoucherLedger sourceLedger = new VoucherLedger(context)) {
            sourceLedger.recordVerifierAudit(voucherId,
                    "record-one",
                    true,
                    true,
                    false,
                    "first audit",
                    "payload_inspection",
                    1000L);
            sourceLedger.recordVerifierAudit(voucherId,
                    "record-two",
                    true,
                    false,
                    true,
                    "second audit",
                    "settlement_verification",
                    2000L);

            String payload = VoucherAuditRhizomeSync.buildAuditExportPayload(
                    sourceLedger.getPendingVerifierAuditExports(),
                    "node-test");

            try (VoucherLedger targetLedger = new VoucherLedger(context)) {
                List<VoucherLedger.VerifierAuditRecord> imported =
                        VoucherAuditRhizomeSync.importAuditExportPayload(targetLedger, payload);
                assertEquals(2, imported.size());

                List<VoucherLedger.VerifierAuditRecord> history = targetLedger.listVerifierAuditRecords(voucherId);
                assertEquals(2, history.size());
                assertEquals("record-one", history.get(0).auditRecordId);
                assertEquals("record-two", history.get(1).auditRecordId);

                VoucherLedger.VerifierAuditRecord latest = targetLedger.getLatestVerifierAuditRecord(voucherId);
                assertNotNull(latest);
                assertEquals("record-two", latest.auditRecordId);
                assertTrue(latest.manifestVerified);
                assertFalse(latest.ledgerMatched);
                assertTrue(latest.rotationDetected);
                assertEquals(VoucherLedger.AUDIT_ORIGIN_MESH, latest.auditOrigin);
                assertEquals("node-test", latest.sourceNode);
                        assertFalse(latest.exportedToMesh);

                VoucherLedger.VerifierAuditSnapshot snapshot = targetLedger.getVerifierAuditSnapshot(voucherId);
                assertNotNull(snapshot);
                assertTrue(snapshot.isManifestVerified());
                assertFalse(snapshot.isLedgerMatched());
                assertTrue(snapshot.isRotationDetected());
                assertEquals("settlement_verification", snapshot.inspectionSource);
            }
        }
    }
}

