package org.servalproject.satnet.maps;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.bitcoin.security.WalletEncryption;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.rhizome.RhizomeManifest;
import org.servalproject.rhizome.RhizomeManifest_File;
import org.servalproject.satnet.SatnetRoleManager;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.keyring.KeyringIdentity;
import org.servalproject.voucher.VoucherIssuerIdentity;
import org.servalproject.voucher.VoucherSignatureAlgorithms;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class SatnetMapRhizomeSync {
    public static final String ACTION_IMPORT_STAGED = "org.servalproject.satnet.maps.IMPORT_STAGED";
    public static final String EXTRA_IMPORT_BUNDLE_ID = "import_bundle_id";
    public static final int SECURITY_MODE_ENCRYPTED = 1;
    public static final int SECURITY_MODE_SIGNED_ENCRYPTED = 2;

    private static final String TAG = "SatnetMapRhizome";
    private static final String FILE_PREFIX = "satnet-maps-bookmarks-";
    private static final String ENVELOPE_SCHEMA = "satnet-maps-rhizome-envelope-v1";
    private static final String PAYLOAD_SCHEMA = "satnet-maps-bookmark-export-v1";
    private static final String INBOX_DIRECTORY = "satnet_maps_inbox";
    private static final String HISTORY_DIRECTORY = "satnet_maps_history";
    private static final String PAYLOAD_SUFFIX = ".mapsbundle";
    private static final String META_SUFFIX = ".meta";
    private static final int MIN_PASSPHRASE_LENGTH = 6;
    private static final String SECURITY_MODE_ENCRYPTED_LABEL = "encrypted";
    private static final String SECURITY_MODE_SIGNED_ENCRYPTED_LABEL = "signed_encrypted";

    private SatnetMapRhizomeSync() {
    }

    public static boolean publishBookmarksToRhizome(Context context,
                                                    List<SatnetMapBookmark> bookmarks,
                                                    String exportLabel,
                                                    String passphrase,
                                                    SatnetRoleManager roleManager) {
        return publishBookmarksToRhizome(context, bookmarks, exportLabel, passphrase, roleManager, SECURITY_MODE_ENCRYPTED);
    }

    public static boolean publishBookmarksToRhizome(Context context,
                                                    List<SatnetMapBookmark> bookmarks,
                                                    String exportLabel,
                                                    String passphrase,
                                                    SatnetRoleManager roleManager,
                                                    int securityMode) {
        if (context == null || bookmarks == null || bookmarks.isEmpty()) {
            return false;
        }
        validatePassphrase(passphrase);

        File payload = null;
        try {
            KeyringIdentity identity = ServalBatPhoneApplication.context.server.getIdentity();
            if (identity == null || identity.sid == null) {
                return false;
            }
            List<SatnetMapBookmark> sanitized = sanitizeBookmarks(bookmarks);
            if (sanitized.isEmpty()) {
                return false;
            }
            payload = File.createTempFile(FILE_PREFIX, ".json", Rhizome.getTempDirectoryCreated());
            String sourceSubjectId = roleManager == null ? null : roleManager.getParticipantSubjectId();
            String roleSummary = roleManager == null ? "" : roleManager.getRegisteredRoleSummary();
            String envelope;
            if (securityMode == SECURITY_MODE_SIGNED_ENCRYPTED) {
                VoucherIssuerIdentity signer = VoucherIssuerIdentity.loadOrCreatePersistent(context);
                envelope = buildSignedEncryptedExportPayload(
                        sanitized,
                        exportLabel,
                        sourceSubjectId,
                        roleSummary,
                        passphrase,
                        signer);
            } else {
                envelope = buildEncryptedExportPayload(
                        sanitized,
                        exportLabel,
                        sourceSubjectId,
                        roleSummary,
                        passphrase);
            }
            writeString(payload, envelope);
            String bundleName = FILE_PREFIX + identity.sid.abbreviation() + "-" + System.currentTimeMillis() + ".json";
            ServalDCommand.rhizomeAddFile(payload, null, null, identity.sid, null, "name=" + bundleName);
            EnvelopeMetadata envelopeMetadata = describeEnvelope(envelope);
            recordExportHistory(context,
                    bundleName,
                    envelopeMetadata.exportLabel,
                    envelopeMetadata.exportedAt,
                    envelopeMetadata.bookmarkCount,
                    envelopeMetadata.securityMode,
                    envelopeMetadata.signerKeyId,
                    envelopeMetadata.roleSummary);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Unable to publish SATNET map bookmarks to Rhizome", e);
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
            if (name == null || !name.startsWith(FILE_PREFIX)) {
                return false;
            }
            File temp = new File(Rhizome.getTempDirectoryCreated(), file.getManifestId().toHex() + PAYLOAD_SUFFIX);
            ServalDCommand.rhizomeExtractFile(file.getManifestId(), temp);
            String payload = readString(temp);
            Rhizome.safeDelete(temp);
            if (payload.trim().isEmpty()) {
                return false;
            }
            stageBundleForReview(context, file.getManifestId().toHex(), name, payload);
            Intent intent = new Intent(ACTION_IMPORT_STAGED)
                    .setPackage(context.getPackageName())
                    .putExtra(EXTRA_IMPORT_BUNDLE_ID, file.getManifestId().toHex());
            context.sendBroadcast(intent);
            return true;
        } catch (RhizomeManifest.MissingField e) {
            Log.w(TAG, "SATNET map bundle missing required manifest field", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to stage SATNET map Rhizome bundle", e);
            return false;
        }
    }

    public static String buildEncryptedExportPayload(List<SatnetMapBookmark> bookmarks,
                                                     String exportLabel,
                                                     String sourceSubjectId,
                                                     String roleSummary,
                                                     String passphrase) throws Exception {
        return buildEncryptedExportPayload(bookmarks, exportLabel, sourceSubjectId, roleSummary, passphrase, null);
    }

    public static String buildSignedEncryptedExportPayload(List<SatnetMapBookmark> bookmarks,
                                                           String exportLabel,
                                                           String sourceSubjectId,
                                                           String roleSummary,
                                                           String passphrase,
                                                           VoucherIssuerIdentity signerIdentity) throws Exception {
        if (signerIdentity == null) {
            throw new IllegalArgumentException("Signing identity is required for signed bookmark bundles");
        }
        return buildEncryptedExportPayload(bookmarks, exportLabel, sourceSubjectId, roleSummary, passphrase, signerIdentity);
    }

    private static String buildEncryptedExportPayload(List<SatnetMapBookmark> bookmarks,
                                                      String exportLabel,
                                                      String sourceSubjectId,
                                                      String roleSummary,
                                                      String passphrase,
                                                      VoucherIssuerIdentity signerIdentity) throws Exception {
        validatePassphrase(passphrase);
        List<SatnetMapBookmark> sanitized = sanitizeBookmarks(bookmarks);
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("At least one valid bookmark is required");
        }
        long exportedAt = System.currentTimeMillis();
        JSONObject payload = buildPayloadObject(sanitized, exportLabel, sourceSubjectId, roleSummary, exportedAt);

        WalletEncryption.EncryptedSeed encrypted = WalletEncryption.encryptSeed(
                payload.toString().getBytes(StandardCharsets.UTF_8),
                passphrase);
        JSONObject envelope = buildEnvelopeObject(
                payload,
                sanitized.size(),
                encrypted,
                signerIdentity == null ? SECURITY_MODE_ENCRYPTED_LABEL : SECURITY_MODE_SIGNED_ENCRYPTED_LABEL);
        if (signerIdentity != null) {
            String signaturePayload = buildSignaturePayload(envelope);
            envelope.put("signerKeyId", signerIdentity.getIssuerKeyId());
            envelope.put("signatureAlgorithm", signerIdentity.getAlgorithm());
            envelope.put("signaturePublicKey", signerIdentity.getEncodedPublicKey());
            envelope.put("signatureValue", VoucherSignatureAlgorithms.sign(
                    signerIdentity.getAlgorithm(),
                    signaturePayload,
                    signerIdentity.getPrivateKey()));
        }
        return envelope.toString();
    }

    public static ImportPreview decryptPayload(String encryptedPayload, String passphrase) throws Exception {
        validatePassphrase(passphrase);
        JSONObject envelope = new JSONObject(encryptedPayload);
        if (!ENVELOPE_SCHEMA.equals(envelope.optString("schemaVersion", ""))) {
            throw new IllegalArgumentException("Unsupported SATNET Maps envelope version");
        }
        SignatureState signatureState = verifySignatureState(envelope);
        WalletEncryption.EncryptedSeed encryptedSeed = new WalletEncryption.EncryptedSeed(
                envelope.getString("ciphertext"),
                envelope.getString("salt"),
                envelope.getString("iv"));
        byte[] plaintext = WalletEncryption.decryptSeed(encryptedSeed, passphrase);
        JSONObject payload = new JSONObject(new String(plaintext, StandardCharsets.UTF_8));
        if (!PAYLOAD_SCHEMA.equals(payload.optString("schemaVersion", ""))) {
            throw new IllegalArgumentException("Unsupported SATNET Maps payload version");
        }
        List<SatnetMapBookmark> bookmarks = new ArrayList<>();
        JSONArray items = payload.optJSONArray("bookmarks");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                SatnetMapBookmark bookmark = new SatnetMapBookmark(
                        item.optString("id", ""),
                        item.optString("label", ""),
                        item.optString("note", ""),
                        item.optDouble("latitude", Double.NaN),
                        item.optDouble("longitude", Double.NaN),
                        item.optLong("createdAtMs", 0L));
                if (bookmark.isValid()) {
                    bookmarks.add(bookmark);
                }
            }
        }
        return new ImportPreview(
                envelope.optString("bundleId", ""),
                envelope.optString("displayName", "SATNET Maps bundle"),
                payload.optString("exportLabel", envelope.optString("exportLabel", "SATNET Maps export")),
                payload.optString("sourceSubjectId", envelope.optString("sourceSubjectId", "unknown")),
                payload.optString("roleSummary", envelope.optString("roleSummary", "No registered roles")),
                payload.optLong("exportedAt", envelope.optLong("exportedAt", 0L)),
                sanitizeBookmarks(bookmarks),
                signatureState.signed,
                signatureState.signatureVerified,
                signatureState.securityMode,
                signatureState.signerKeyId,
                signatureState.signatureAlgorithm);
    }

    public static void stageBundleForReview(Context context, String bundleId, String displayName, String payload) throws Exception {
        File inbox = getInboxDirectory(context);
        if (!inbox.exists() && !inbox.mkdirs()) {
            throw new IllegalStateException("Unable to create SATNET Maps import inbox");
        }
        writeString(new File(inbox, bundleId + PAYLOAD_SUFFIX), payload);
        JSONObject metadata = new JSONObject();
        metadata.put("bundleId", bundleId);
        metadata.put("displayName", safeString(displayName, "SATNET Maps bundle"));
        metadata.put("stagedAt", System.currentTimeMillis());
        EnvelopeMetadata envelopeMetadata = describeEnvelope(payload);
        metadata.put("exportLabel", envelopeMetadata.exportLabel);
        metadata.put("bookmarkCount", envelopeMetadata.bookmarkCount);
        metadata.put("exportedAt", envelopeMetadata.exportedAt);
        metadata.put("securityMode", envelopeMetadata.securityMode);
        metadata.put("signed", envelopeMetadata.signed);
        metadata.put("signerKeyId", safeString(envelopeMetadata.signerKeyId, ""));
        metadata.put("roleSummary", envelopeMetadata.roleSummary);
        writeString(new File(inbox, bundleId + META_SUFFIX), metadata.toString());
    }

    public static List<PendingImport> listPendingImports(Context context) {
        File inbox = getInboxDirectory(context);
        if (!inbox.isDirectory()) {
            return new ArrayList<>();
        }
        ArrayList<PendingImport> imports = new ArrayList<>();
        File[] metaFiles = inbox.listFiles((dir, name) -> name.endsWith(META_SUFFIX));
        if (metaFiles == null) {
            return imports;
        }
        for (File metaFile : metaFiles) {
            try {
                JSONObject metadata = new JSONObject(readString(metaFile));
                String bundleId = metadata.optString("bundleId", "");
                if (bundleId.trim().isEmpty()) {
                    continue;
                }
                File payload = new File(inbox, bundleId + PAYLOAD_SUFFIX);
                if (!payload.exists()) {
                    Rhizome.safeDelete(metaFile);
                    continue;
                }
                imports.add(new PendingImport(
                        bundleId,
                        metadata.optString("displayName", "SATNET Maps bundle"),
                        metadata.optLong("stagedAt", payload.lastModified()),
                        metadata.optString("exportLabel", metadata.optString("displayName", "SATNET Maps bundle")),
                        metadata.optInt("bookmarkCount", 0),
                        metadata.optLong("exportedAt", 0L),
                        metadata.optString("securityMode", SECURITY_MODE_ENCRYPTED_LABEL),
                        metadata.optBoolean("signed", false),
                        metadata.optString("signerKeyId", ""),
                        metadata.optString("roleSummary", "No registered roles"),
                        payload,
                        metaFile));
            } catch (Exception e) {
                Log.w(TAG, "Discarding malformed SATNET Maps import metadata", e);
                Rhizome.safeDelete(metaFile);
            }
        }
        Collections.sort(imports, new Comparator<PendingImport>() {
            @Override
            public int compare(PendingImport left, PendingImport right) {
                return Long.compare(right.stagedAt, left.stagedAt);
            }
        });
        return imports;
    }

    public static ImportPreview previewPendingImport(Context context, String bundleId, String passphrase) throws Exception {
        PendingImport pendingImport = findPendingImport(context, bundleId);
        if (pendingImport == null) {
            throw new IllegalArgumentException("Pending SATNET Maps import not found");
        }
        JSONObject envelope = new JSONObject(readString(pendingImport.payloadFile));
        envelope.put("bundleId", pendingImport.bundleId);
        envelope.put("displayName", pendingImport.displayName);
        return decryptPayload(envelope.toString(), passphrase);
    }

    public static List<SatnetMapBookmark> importPendingBookmarks(Context context,
                                                                 SecureMapBookmarkStore bookmarkStore,
                                                                 String bundleId,
                                                                 String passphrase) throws Exception {
        if (bookmarkStore == null) {
            throw new IllegalArgumentException("Bookmark store is required");
        }
        ImportPreview preview = previewPendingImport(context, bundleId, passphrase);
        for (SatnetMapBookmark bookmark : preview.bookmarks) {
            bookmarkStore.upsertBookmark(bookmark);
        }
        discardPendingImport(context, bundleId);
        return preview.bookmarks;
    }

    public static void discardPendingImport(Context context, String bundleId) {
        PendingImport pendingImport = findPendingImport(context, bundleId);
        if (pendingImport == null) {
            return;
        }
        Rhizome.safeDelete(pendingImport.payloadFile);
        Rhizome.safeDelete(pendingImport.metadataFile);
    }

    public static File getInboxDirectory(Context context) {
        return new File(context.getFilesDir(), INBOX_DIRECTORY);
    }

    public static File getHistoryDirectory(Context context) {
        return new File(context.getFilesDir(), HISTORY_DIRECTORY);
    }

    public static void recordExportHistory(Context context,
                                           String displayName,
                                           String exportLabel,
                                           long exportedAt,
                                           int bookmarkCount,
                                           String securityMode,
                                           String signerKeyId,
                                           String roleSummary) throws Exception {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        File historyDirectory = getHistoryDirectory(context);
        if (!historyDirectory.exists() && !historyDirectory.mkdirs()) {
            throw new IllegalStateException("Unable to create SATNET Maps export history directory");
        }
        JSONObject metadata = new JSONObject();
        metadata.put("historyId", UUID.randomUUID().toString());
        metadata.put("displayName", safeString(displayName, "SATNET Maps bundle"));
        metadata.put("exportLabel", safeString(exportLabel, "SATNET Maps export"));
        metadata.put("exportedAt", exportedAt > 0L ? exportedAt : System.currentTimeMillis());
        metadata.put("bookmarkCount", Math.max(0, bookmarkCount));
        metadata.put("securityMode", normalizeSecurityMode(securityMode));
        metadata.put("signed", SECURITY_MODE_SIGNED_ENCRYPTED_LABEL.equals(normalizeSecurityMode(securityMode)));
        metadata.put("signerKeyId", safeString(signerKeyId, ""));
        metadata.put("roleSummary", safeString(roleSummary, "No registered roles"));
        File metadataFile = new File(historyDirectory,
                metadata.getString("historyId") + META_SUFFIX);
        writeString(metadataFile, metadata.toString());
    }

    public static List<ExportHistoryEntry> listExportHistory(Context context) {
        File historyDirectory = getHistoryDirectory(context);
        if (!historyDirectory.isDirectory()) {
            return new ArrayList<>();
        }
        ArrayList<ExportHistoryEntry> historyEntries = new ArrayList<>();
        File[] metadataFiles = historyDirectory.listFiles((dir, name) -> name.endsWith(META_SUFFIX));
        if (metadataFiles == null) {
            return historyEntries;
        }
        for (File metadataFile : metadataFiles) {
            try {
                JSONObject metadata = new JSONObject(readString(metadataFile));
                historyEntries.add(new ExportHistoryEntry(
                        metadata.optString("historyId", metadataFile.getName()),
                        metadata.optString("displayName", "SATNET Maps bundle"),
                        metadata.optString("exportLabel", "SATNET Maps export"),
                        metadata.optLong("exportedAt", metadataFile.lastModified()),
                        metadata.optInt("bookmarkCount", 0),
                        metadata.optString("securityMode", SECURITY_MODE_ENCRYPTED_LABEL),
                        metadata.optBoolean("signed", false),
                        metadata.optString("signerKeyId", ""),
                        metadata.optString("roleSummary", "No registered roles")));
            } catch (Exception e) {
                Log.w(TAG, "Discarding malformed SATNET Maps export history metadata", e);
                Rhizome.safeDelete(metadataFile);
            }
        }
        Collections.sort(historyEntries, new Comparator<ExportHistoryEntry>() {
            @Override
            public int compare(ExportHistoryEntry left, ExportHistoryEntry right) {
                return Long.compare(right.exportedAt, left.exportedAt);
            }
        });
        return historyEntries;
    }

    private static PendingImport findPendingImport(Context context, String bundleId) {
        if (context == null || TextUtils.isEmpty(bundleId)) {
            return null;
        }
        for (PendingImport pendingImport : listPendingImports(context)) {
            if (bundleId.equals(pendingImport.bundleId)) {
                return pendingImport;
            }
        }
        return null;
    }

    private static List<SatnetMapBookmark> sanitizeBookmarks(List<SatnetMapBookmark> bookmarks) {
        ArrayList<SatnetMapBookmark> sanitized = new ArrayList<>();
        if (bookmarks == null) {
            return sanitized;
        }
        for (SatnetMapBookmark bookmark : bookmarks) {
            if (bookmark == null) {
                continue;
            }
            SatnetMapBookmark copy = bookmark.sanitizedCopy();
            if (copy.isValid()) {
                sanitized.add(copy);
            }
        }
        return sanitized;
    }

    private static void validatePassphrase(String passphrase) {
        if (passphrase == null || passphrase.trim().length() < MIN_PASSPHRASE_LENGTH) {
            throw new IllegalArgumentException("Passphrase must be at least " + MIN_PASSPHRASE_LENGTH + " characters");
        }
    }

    private static JSONObject buildPayloadObject(List<SatnetMapBookmark> bookmarks,
                                                 String exportLabel,
                                                 String sourceSubjectId,
                                                 String roleSummary,
                                                 long exportedAt) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("schemaVersion", PAYLOAD_SCHEMA);
        payload.put("exportLabel", safeString(exportLabel, "SATNET Maps export"));
        payload.put("sourceSubjectId", safeString(sourceSubjectId, "unknown"));
        payload.put("roleSummary", safeString(roleSummary, "No registered roles"));
        payload.put("exportedAt", exportedAt);
        JSONArray bookmarksArray = new JSONArray();
        for (SatnetMapBookmark bookmark : bookmarks) {
            JSONObject item = new JSONObject();
            item.put("id", bookmark.id);
            item.put("label", bookmark.getDisplayLabel());
            item.put("note", bookmark.getDisplayNote());
            item.put("latitude", bookmark.latitude);
            item.put("longitude", bookmark.longitude);
            item.put("createdAtMs", bookmark.createdAtMs);
            bookmarksArray.put(item);
        }
        payload.put("bookmarks", bookmarksArray);
        return payload;
    }

    private static JSONObject buildEnvelopeObject(JSONObject payload,
                                                  int bookmarkCount,
                                                  WalletEncryption.EncryptedSeed encrypted,
                                                  String securityMode) throws Exception {
        JSONObject envelope = new JSONObject();
        envelope.put("schemaVersion", ENVELOPE_SCHEMA);
        envelope.put("securityMode", normalizeSecurityMode(securityMode));
        envelope.put("exportLabel", payload.optString("exportLabel", "SATNET Maps export"));
        envelope.put("sourceSubjectId", payload.optString("sourceSubjectId", "unknown"));
        envelope.put("roleSummary", payload.optString("roleSummary", "No registered roles"));
        envelope.put("exportedAt", payload.optLong("exportedAt", 0L));
        envelope.put("bookmarkCount", Math.max(0, bookmarkCount));
        envelope.put("salt", encrypted.salt);
        envelope.put("iv", encrypted.iv);
        envelope.put("ciphertext", encrypted.ciphertext);
        return envelope;
    }

    private static String buildSignaturePayload(JSONObject envelope) {
        return safeString(envelope.optString("schemaVersion", ""), ENVELOPE_SCHEMA) + "\n"
                + normalizeSecurityMode(envelope.optString("securityMode", SECURITY_MODE_ENCRYPTED_LABEL)) + "\n"
                + safeString(envelope.optString("exportLabel", ""), "SATNET Maps export") + "\n"
                + safeString(envelope.optString("sourceSubjectId", ""), "unknown") + "\n"
                + safeString(envelope.optString("roleSummary", ""), "No registered roles") + "\n"
                + envelope.optLong("exportedAt", 0L) + "\n"
                + envelope.optInt("bookmarkCount", 0) + "\n"
                + envelope.optString("salt", "") + "\n"
                + envelope.optString("iv", "") + "\n"
                + envelope.optString("ciphertext", "");
    }

    private static SignatureState verifySignatureState(JSONObject envelope) throws Exception {
        String securityMode = normalizeSecurityMode(envelope.optString("securityMode", SECURITY_MODE_ENCRYPTED_LABEL));
        String signatureAlgorithm = envelope.optString("signatureAlgorithm", "");
        String signaturePublicKey = envelope.optString("signaturePublicKey", "");
        String signatureValue = envelope.optString("signatureValue", "");
        String signerKeyId = envelope.optString("signerKeyId", "");
        boolean signed = SECURITY_MODE_SIGNED_ENCRYPTED_LABEL.equals(securityMode)
                || !signatureAlgorithm.trim().isEmpty()
                || !signaturePublicKey.trim().isEmpty()
                || !signatureValue.trim().isEmpty();
        if (!signed) {
            return new SignatureState(false, false, securityMode, "", "");
        }
        if (signatureAlgorithm.trim().isEmpty() || signaturePublicKey.trim().isEmpty() || signatureValue.trim().isEmpty()) {
            throw new IllegalArgumentException("Signed SATNET Maps bundle is missing signature metadata");
        }
        PublicKey publicKey = VoucherSignatureAlgorithms.decodePublicKey(signatureAlgorithm, signaturePublicKey);
        boolean verified = VoucherSignatureAlgorithms.verify(
                signatureAlgorithm,
                buildSignaturePayload(envelope),
                signatureValue,
                publicKey);
        if (!verified) {
            throw new IllegalArgumentException("Signed SATNET Maps bundle failed signature verification");
        }
        return new SignatureState(true, true, securityMode, signerKeyId, VoucherSignatureAlgorithms.normalize(signatureAlgorithm));
    }

    private static EnvelopeMetadata describeEnvelope(String payload) {
        try {
            JSONObject envelope = new JSONObject(payload);
            return new EnvelopeMetadata(
                    envelope.optString("exportLabel", "SATNET Maps export"),
                    envelope.optLong("exportedAt", 0L),
                    envelope.optInt("bookmarkCount", 0),
                    normalizeSecurityMode(envelope.optString("securityMode", SECURITY_MODE_ENCRYPTED_LABEL)),
                    !TextUtils.isEmpty(envelope.optString("signatureValue", ""))
                            || SECURITY_MODE_SIGNED_ENCRYPTED_LABEL.equals(normalizeSecurityMode(envelope.optString("securityMode", SECURITY_MODE_ENCRYPTED_LABEL))),
                    envelope.optString("signerKeyId", ""),
                    envelope.optString("roleSummary", "No registered roles"));
        } catch (Exception e) {
            return new EnvelopeMetadata("SATNET Maps export", 0L, 0, SECURITY_MODE_ENCRYPTED_LABEL, false, "", "No registered roles");
        }
    }

    private static String normalizeSecurityMode(String securityMode) {
        String normalized = securityMode == null ? "" : securityMode.trim().toLowerCase(java.util.Locale.US);
        return SECURITY_MODE_SIGNED_ENCRYPTED_LABEL.equals(normalized)
                ? SECURITY_MODE_SIGNED_ENCRYPTED_LABEL
                : SECURITY_MODE_ENCRYPTED_LABEL;
    }

    private static String safeString(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String readString(File file) throws Exception {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } finally {
            reader.close();
        }
        return builder.toString();
    }

    private static void writeString(File file, String value) throws Exception {
        FileOutputStream outputStream = new FileOutputStream(file, false);
        try {
            outputStream.write(value.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } finally {
            outputStream.close();
        }
    }

    public static final class PendingImport {
        public final String bundleId;
        public final String displayName;
        public final long stagedAt;
        public final String exportLabel;
        public final int bookmarkCount;
        public final long exportedAt;
        public final String securityMode;
        public final boolean signed;
        public final String signerKeyId;
        public final String roleSummary;
        final File payloadFile;
        final File metadataFile;

        PendingImport(String bundleId,
                      String displayName,
                      long stagedAt,
                      String exportLabel,
                      int bookmarkCount,
                      long exportedAt,
                      String securityMode,
                      boolean signed,
                      String signerKeyId,
                      String roleSummary,
                      File payloadFile,
                      File metadataFile) {
            this.bundleId = bundleId;
            this.displayName = displayName;
            this.stagedAt = stagedAt;
            this.exportLabel = exportLabel;
            this.bookmarkCount = Math.max(0, bookmarkCount);
            this.exportedAt = exportedAt;
            this.securityMode = normalizeSecurityMode(securityMode);
            this.signed = signed || SECURITY_MODE_SIGNED_ENCRYPTED_LABEL.equals(this.securityMode);
            this.signerKeyId = signerKeyId == null ? "" : signerKeyId;
            this.roleSummary = safeString(roleSummary, "No registered roles");
            this.payloadFile = payloadFile;
            this.metadataFile = metadataFile;
        }
    }

    public static final class ImportPreview {
        public final String bundleId;
        public final String displayName;
        public final String exportLabel;
        public final String sourceSubjectId;
        public final String roleSummary;
        public final long exportedAt;
        public final List<SatnetMapBookmark> bookmarks;
        public final boolean signed;
        public final boolean signatureVerified;
        public final String securityMode;
        public final String signerKeyId;
        public final String signatureAlgorithm;

        ImportPreview(String bundleId,
                      String displayName,
                      String exportLabel,
                      String sourceSubjectId,
                      String roleSummary,
                      long exportedAt,
                      List<SatnetMapBookmark> bookmarks,
                      boolean signed,
                      boolean signatureVerified,
                      String securityMode,
                      String signerKeyId,
                      String signatureAlgorithm) {
            this.bundleId = bundleId;
            this.displayName = displayName;
            this.exportLabel = exportLabel;
            this.sourceSubjectId = sourceSubjectId;
            this.roleSummary = roleSummary;
            this.exportedAt = exportedAt;
            this.bookmarks = bookmarks;
            this.signed = signed;
            this.signatureVerified = signatureVerified;
            this.securityMode = normalizeSecurityMode(securityMode);
            this.signerKeyId = signerKeyId == null ? "" : signerKeyId;
            this.signatureAlgorithm = signatureAlgorithm == null ? "" : signatureAlgorithm;
        }
    }

    public static final class ExportHistoryEntry {
        public final String historyId;
        public final String displayName;
        public final String exportLabel;
        public final long exportedAt;
        public final int bookmarkCount;
        public final String securityMode;
        public final boolean signed;
        public final String signerKeyId;
        public final String roleSummary;

        ExportHistoryEntry(String historyId,
                           String displayName,
                           String exportLabel,
                           long exportedAt,
                           int bookmarkCount,
                           String securityMode,
                           boolean signed,
                           String signerKeyId,
                           String roleSummary) {
            this.historyId = historyId == null ? "" : historyId;
            this.displayName = safeString(displayName, "SATNET Maps bundle");
            this.exportLabel = safeString(exportLabel, "SATNET Maps export");
            this.exportedAt = exportedAt;
            this.bookmarkCount = Math.max(0, bookmarkCount);
            this.securityMode = normalizeSecurityMode(securityMode);
            this.signed = signed || SECURITY_MODE_SIGNED_ENCRYPTED_LABEL.equals(this.securityMode);
            this.signerKeyId = signerKeyId == null ? "" : signerKeyId;
            this.roleSummary = safeString(roleSummary, "No registered roles");
        }
    }

    private static final class EnvelopeMetadata {
        final String exportLabel;
        final long exportedAt;
        final int bookmarkCount;
        final String securityMode;
        final boolean signed;
        final String signerKeyId;
        final String roleSummary;

        EnvelopeMetadata(String exportLabel,
                         long exportedAt,
                         int bookmarkCount,
                         String securityMode,
                         boolean signed,
                         String signerKeyId,
                         String roleSummary) {
            this.exportLabel = safeString(exportLabel, "SATNET Maps export");
            this.exportedAt = exportedAt;
            this.bookmarkCount = Math.max(0, bookmarkCount);
            this.securityMode = normalizeSecurityMode(securityMode);
            this.signed = signed || SECURITY_MODE_SIGNED_ENCRYPTED_LABEL.equals(this.securityMode);
            this.signerKeyId = signerKeyId == null ? "" : signerKeyId;
            this.roleSummary = safeString(roleSummary, "No registered roles");
        }
    }

    private static final class SignatureState {
        final boolean signed;
        final boolean signatureVerified;
        final String securityMode;
        final String signerKeyId;
        final String signatureAlgorithm;

        SignatureState(boolean signed,
                       boolean signatureVerified,
                       String securityMode,
                       String signerKeyId,
                       String signatureAlgorithm) {
            this.signed = signed;
            this.signatureVerified = signatureVerified;
            this.securityMode = normalizeSecurityMode(securityMode);
            this.signerKeyId = signerKeyId == null ? "" : signerKeyId;
            this.signatureAlgorithm = signatureAlgorithm == null ? "" : signatureAlgorithm;
        }
    }
}

