package org.servalproject.satnet.maps;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.voucher.VoucherIssuerIdentity;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SatnetMapRhizomeSyncTest {

    private static final String PASSPHRASE = "shared-secret";

    private Context context;
    private SecureMapBookmarkStore store;

    @Before
    public void setUp() {
        ServalBatPhoneApplication app = ApplicationProvider.getApplicationContext();
        ServalBatPhoneApplication.context = app;
        context = resetAppData(app);
        context.getSharedPreferences(SecureMapBookmarkStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("bitcoin_wallet_device_binding", Context.MODE_PRIVATE).edit().clear().commit();
        deleteRecursively(SatnetMapRhizomeSync.getInboxDirectory(context));
        deleteRecursively(SatnetMapRhizomeSync.getHistoryDirectory(context));
        store = new SecureMapBookmarkStore(context);
    }

    @Test
    public void encryptedBundleCanBePreviewedAndImported() throws Exception {
        SatnetMapBookmark bookmark = SatnetMapBookmark.create("Clinic Alpha", "Primary cache", 1.2d, 32.8d);
        String envelope = SatnetMapRhizomeSync.buildEncryptedExportPayload(
                Collections.singletonList(bookmark),
                "Field Export",
                "subject-1",
                "Voucher Agent",
                PASSPHRASE);

        SatnetMapRhizomeSync.stageBundleForReview(context, "bundle-1", "maps-export.json", envelope);

        List<SatnetMapRhizomeSync.PendingImport> pendingImports = SatnetMapRhizomeSync.listPendingImports(context);
        assertEquals(1, pendingImports.size());

        SatnetMapRhizomeSync.ImportPreview preview = SatnetMapRhizomeSync.previewPendingImport(context, "bundle-1", PASSPHRASE);
        assertEquals("Field Export", preview.exportLabel);
        assertEquals(1, preview.bookmarks.size());
        assertEquals("Clinic Alpha", preview.bookmarks.get(0).getDisplayLabel());

        SatnetMapRhizomeSync.importPendingBookmarks(context, store, "bundle-1", PASSPHRASE);

        List<SatnetMapBookmark> imported = store.loadBookmarks();
        assertEquals(1, imported.size());
        assertEquals("Clinic Alpha", imported.get(0).getDisplayLabel());
        assertTrue(SatnetMapRhizomeSync.listPendingImports(context).isEmpty());
    }

    @Test(expected = Exception.class)
    public void wrongPassphraseFailsPreview() throws Exception {
        SatnetMapBookmark bookmark = SatnetMapBookmark.create("Clinic Alpha", "Primary cache", 1.2d, 32.8d);
        String envelope = SatnetMapRhizomeSync.buildEncryptedExportPayload(
                Collections.singletonList(bookmark),
                "Field Export",
                "subject-1",
                "Voucher Agent",
                PASSPHRASE);

        SatnetMapRhizomeSync.stageBundleForReview(context, "bundle-2", "maps-export.json", envelope);
        SatnetMapRhizomeSync.previewPendingImport(context, "bundle-2", "wrong-passphrase");
    }

    @Test
    public void discardPendingImportRemovesStagedFiles() throws Exception {
        SatnetMapBookmark bookmark = SatnetMapBookmark.create("Market", "", 0.5d, 36.5d);
        String envelope = SatnetMapRhizomeSync.buildEncryptedExportPayload(
                Collections.singletonList(bookmark),
                "Field Export",
                "subject-1",
                "Merchant",
                PASSPHRASE);

        SatnetMapRhizomeSync.stageBundleForReview(context, "bundle-3", "maps-export.json", envelope);
        assertFalse(SatnetMapRhizomeSync.listPendingImports(context).isEmpty());

        SatnetMapRhizomeSync.discardPendingImport(context, "bundle-3");

        assertTrue(SatnetMapRhizomeSync.listPendingImports(context).isEmpty());
    }

    @Test
    public void signedBundleCarriesVerifiedSignatureMetadata() throws Exception {
        SatnetMapBookmark bookmark = SatnetMapBookmark.create("Clinic Alpha", "Primary cache", 1.2d, 32.8d);
        VoucherIssuerIdentity signer = VoucherIssuerIdentity.createEphemeral();

        String envelope = SatnetMapRhizomeSync.buildSignedEncryptedExportPayload(
                Collections.singletonList(bookmark),
                "Signed Export",
                "subject-2",
                "Community Verifier",
                PASSPHRASE,
                signer);

        SatnetMapRhizomeSync.stageBundleForReview(context, "bundle-signed", "signed-export.json", envelope);
        SatnetMapRhizomeSync.ImportPreview preview = SatnetMapRhizomeSync.previewPendingImport(context, "bundle-signed", PASSPHRASE);

        assertTrue(preview.signed);
        assertTrue(preview.signatureVerified);
        assertEquals("signed_encrypted", preview.securityMode);
        assertEquals(signer.getIssuerKeyId(), preview.signerKeyId);
        assertEquals(1, preview.bookmarks.size());
    }

    @Test
    public void exportHistoryPersistsSecurityMetadata() throws Exception {
        SatnetMapRhizomeSync.recordExportHistory(
                context,
                "history-export.json",
                "Field Export",
                12345L,
                2,
                "signed_encrypted",
                "ABCD1234",
                "Voucher Agent · Community Verifier");

        List<SatnetMapRhizomeSync.ExportHistoryEntry> historyEntries = SatnetMapRhizomeSync.listExportHistory(context);

        assertEquals(1, historyEntries.size());
        assertEquals("Field Export", historyEntries.get(0).exportLabel);
        assertTrue(historyEntries.get(0).signed);
        assertEquals("ABCD1234", historyEntries.get(0).signerKeyId);
        assertEquals("Voucher Agent · Community Verifier", historyEntries.get(0).roleSummary);
    }

    private Context resetAppData(ServalBatPhoneApplication app) {
        app.getSharedPreferences("satnet_roles", Context.MODE_PRIVATE).edit().clear().commit();
        app.getSharedPreferences("bitcoin_wallet", Context.MODE_PRIVATE).edit().clear().commit();
        app.deleteDatabase("satnet_vouchers.db");
        return app;
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}

