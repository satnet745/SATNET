package org.servalproject.voucher;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.relay.RhizomeRelay;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.rhizome.RhizomeManifest;
import org.servalproject.rhizome.RhizomeManifest_File;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.keyring.KeyringIdentity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public final class VoucherAuditRhizomeSync {
    private static final String TAG = "VoucherAuditRhizomeSync";
    private static final String PREFIX = "voucher-audit-";
    private static final String SCHEMA_VERSION = "satnet-voucher-audit-v1";

    private VoucherAuditRhizomeSync() {
    }

    public static boolean publishPendingAuditRecords(VoucherLedger voucherLedger) {
        if (voucherLedger == null || !RhizomeRelay.isAvailable()) {
            return false;
        }
        List<VoucherLedger.VerifierAuditRecord> pendingRecords = voucherLedger.getPendingVerifierAuditExports();
        if (pendingRecords.isEmpty()) {
            return true;
        }
        File payload = null;
        try {
            KeyringIdentity identity = ServalBatPhoneApplication.context.server.getIdentity();
            if (identity == null || identity.sid == null) {
                return false;
            }
            payload = File.createTempFile(PREFIX, ".json", Rhizome.getTempDirectoryCreated());
            String exportPayload = buildAuditExportPayload(pendingRecords, identity.sid.abbreviation());
            FileWriter writer = new FileWriter(payload);
            writer.write(exportPayload);
            writer.close();

            String name = PREFIX + identity.sid.abbreviation() + "-" + System.currentTimeMillis() + ".json";
            ServalDCommand.rhizomeAddFile(payload, null, null, identity.sid, null, "name=" + name);
            long exportedAt = System.currentTimeMillis();
            for (VoucherLedger.VerifierAuditRecord record : pendingRecords) {
                voucherLedger.markVerifierAuditExported(record.auditRecordId, exportedAt);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to publish verifier audit evidence", e);
            return false;
        } finally {
            if (payload != null) {
                Rhizome.safeDelete(payload);
            }
        }
    }

    public static boolean handleBundle(Context context, RhizomeManifest_File file) {
        if (context == null || file == null) {
            return false;
        }
        try {
            String name = file.getName();
            if (name == null || !name.startsWith(PREFIX)) {
                return false;
            }
            File temp = new File(Rhizome.getTempDirectoryCreated(), file.getManifestId().toHex() + ".voucher_audit");
            ServalDCommand.rhizomeExtractFile(file.getManifestId(), temp);
            BufferedReader reader = new BufferedReader(new FileReader(temp));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            reader.close();
            Rhizome.safeDelete(temp);
            if (builder.length() == 0) {
                return false;
            }
            try (VoucherLedger voucherLedger = new VoucherLedger(context)) {
                importAuditExportPayload(voucherLedger, builder.toString());
            }
            return true;
        } catch (RhizomeManifest.MissingField e) {
            Log.w(TAG, "Voucher audit bundle missing manifest field", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to process verifier audit evidence bundle", e);
            return false;
        }
    }

    public static String buildAuditExportPayload(List<VoucherLedger.VerifierAuditRecord> auditRecords, String sourceNode) throws Exception {
        JSONArray array = new JSONArray();
        if (auditRecords != null) {
            for (VoucherLedger.VerifierAuditRecord record : auditRecords) {
                JSONObject item = new JSONObject();
                item.put("auditRecordId", record.auditRecordId);
                item.put("voucherId", record.voucherId);
                item.put("manifestVerified", record.manifestVerified);
                item.put("ledgerMatched", record.ledgerMatched);
                item.put("rotationDetected", record.rotationDetected);
                item.put("auditTime", record.auditTime);
                item.put("auditMessage", record.auditMessage == null ? "" : record.auditMessage);
                item.put("inspectionSource", record.inspectionSource == null ? "" : record.inspectionSource);
                item.put("auditOrigin", record.auditOrigin == null ? VoucherLedger.AUDIT_ORIGIN_LOCAL : record.auditOrigin);
                item.put("sourceNode", record.sourceNode == null || record.sourceNode.trim().isEmpty()
                        ? (sourceNode == null ? "unknown" : sourceNode)
                        : record.sourceNode);
                item.put("exportedToMesh", record.exportedToMesh);
                item.put("exportedAt", record.exportedAt);
                array.put(item);
            }
        }
        JSONObject payload = new JSONObject();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("sourceNode", sourceNode == null ? "unknown" : sourceNode);
        payload.put("exportedAt", System.currentTimeMillis());
        payload.put("auditRecords", array);
        return payload.toString();
    }

    public static List<VoucherLedger.VerifierAuditRecord> importAuditExportPayload(VoucherLedger voucherLedger, String payload) throws Exception {
        if (voucherLedger == null) {
            return new ArrayList<VoucherLedger.VerifierAuditRecord>();
        }
        JSONObject json = new JSONObject(payload);
        if (!SCHEMA_VERSION.equals(json.optString("schemaVersion", ""))) {
            throw new IllegalArgumentException("Unsupported voucher audit schema version");
        }
        JSONArray array = json.optJSONArray("auditRecords");
        String envelopeSourceNode = json.optString("sourceNode", "unknown");
        ArrayList<VoucherLedger.VerifierAuditRecord> imported = new ArrayList<VoucherLedger.VerifierAuditRecord>();
        if (array == null) {
            return imported;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            String sourceNode = item.optString("sourceNode", envelopeSourceNode);
            VoucherLedger.VerifierAuditRecord record = new VoucherLedger.VerifierAuditRecord(
                    item.optString("auditRecordId", ""),
                    item.optString("voucherId", ""),
                    item.optBoolean("manifestVerified", false),
                    item.optBoolean("ledgerMatched", false),
                    item.optBoolean("rotationDetected", false),
                    item.optLong("auditTime", 0L),
                    item.optString("auditMessage", ""),
                    item.optString("inspectionSource", ""),
                    VoucherLedger.AUDIT_ORIGIN_MESH,
                    sourceNode,
                    item.optBoolean("exportedToMesh", true),
                    item.optLong("exportedAt", json.optLong("exportedAt", 0L)));
            voucherLedger.importVerifierAuditRecord(record);
            imported.add(record);
        }
        return imported;
    }
}

