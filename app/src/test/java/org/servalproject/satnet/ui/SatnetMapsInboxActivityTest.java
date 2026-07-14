package org.servalproject.satnet.ui;

import android.content.Context;
import android.view.View;
import android.view.WindowManager;
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
import org.servalproject.satnet.maps.SatnetMapBookmark;
import org.servalproject.satnet.maps.SatnetMapRhizomeSync;
import org.servalproject.satnet.maps.SecureMapBookmarkStore;
import org.servalproject.voucher.VoucherIssuerIdentity;

import java.io.File;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SatnetMapsInboxActivityTest {

    private static final String PASSPHRASE = "shared-secret";

    private Context context;

    @Before
    public void setUp() throws Exception {
        ServalBatPhoneApplication app = SatnetRuntimeTestHelper.prepareApp();
        context = SatnetRuntimeTestHelper.resetAppData(app);
        SatnetRuntimeTestHelper.setRuntimeReady(app);
        deleteRecursively(SatnetMapRhizomeSync.getInboxDirectory(context));
        deleteRecursively(SatnetMapRhizomeSync.getHistoryDirectory(context));

        SatnetMapBookmark bookmark = SatnetMapBookmark.create("Clinic Alpha", "Primary cache", 1.2d, 32.8d);
        String envelope = SatnetMapRhizomeSync.buildEncryptedExportPayload(
                Collections.singletonList(bookmark),
                "Field Export",
                "subject-1",
                "Voucher Agent",
                PASSPHRASE);
        SatnetMapRhizomeSync.stageBundleForReview(context, "bundle-1", "maps-export.json", envelope);
        SatnetMapRhizomeSync.recordExportHistory(context,
                "history-export.json",
                "Field Export",
                12345L,
                1,
                "encrypted",
                "",
                "Voucher Agent");
    }

    @Test
    public void inboxShowsCountsAndAllowsDiscard() {
        try (ActivityController<SatnetMapsInboxActivity> controller = Robolectric.buildActivity(SatnetMapsInboxActivity.class)) {
            SatnetMapsInboxActivity activity = controller.create().start().resume().get();

            assertTrue((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);

            TextView summaryText = activity.findViewById(R.id.satnet_maps_inbox_summary_text);
            TextView selectedBundleText = activity.findViewById(R.id.satnet_maps_inbox_selected_bundle_text);
            LinearLayout pendingContainer = activity.findViewById(R.id.satnet_maps_inbox_pending_container);

            assertTrue(summaryText.getText().toString().contains("Pending inbox bundles: 1"));
            assertTrue(summaryText.getText().toString().contains("Local export history entries: 1"));
            assertTrue(selectedBundleText.getText().toString().contains("Selected bundle: maps-export.json"));

            View pendingDetails = activity.findViewById(android.R.id.content).findViewWithTag("pending-details:bundle-1");
            View selectedBadge = activity.findViewById(android.R.id.content).findViewWithTag("selected-badge:bundle-1");
            assertNotNull(pendingDetails);
            assertNotNull(selectedBadge);
            assertTrue(((TextView) pendingDetails).getText().toString().contains("Field Export"));
            assertEquals(1, pendingContainer.getChildCount());

            LinearLayout pendingRow = (LinearLayout) activity.findViewById(android.R.id.content).findViewWithTag("pending-row:bundle-1");
            assertNotNull(pendingRow);
            LinearLayout actions = (LinearLayout) pendingRow.getChildAt(pendingRow.getChildCount() - 1);
            Button discardButton = (Button) actions.getChildAt(1);
            discardButton.performClick();

            assertEquals(0, SatnetMapRhizomeSync.listPendingImports(activity).size());
            assertEquals(1, pendingContainer.getChildCount());
            assertTrue(((TextView) pendingContainer.getChildAt(0)).getText().toString().contains("No SATNET Maps Rhizome bundles are currently staged."));
        }
    }

    @Test
    public void reviewButtonPreviewsAndImportsSelectedBundle() {
        try (ActivityController<SatnetMapsInboxActivity> controller = Robolectric.buildActivity(SatnetMapsInboxActivity.class)) {
            SatnetMapsInboxActivity activity = controller.create().start().resume().get();
            EditText passphraseInput = activity.findViewById(R.id.satnet_maps_inbox_passphrase_input);
            Button reviewButton = activity.findViewById(R.id.satnet_maps_inbox_review_button);
            Button importButton = activity.findViewById(R.id.satnet_maps_inbox_import_button);
            TextView previewText = activity.findViewById(R.id.satnet_maps_inbox_preview_text);

            passphraseInput.setText(PASSPHRASE);
            reviewButton.performClick();

            assertTrue(previewText.getText().toString().contains("Clinic Alpha"));
            assertTrue(importButton.isEnabled());

            importButton.performClick();

            assertEquals(0, SatnetMapRhizomeSync.listPendingImports(activity).size());
            assertEquals(1, new SecureMapBookmarkStore(activity).loadBookmarks().size());
            assertTrue(previewText.getText().toString().contains("Select a staged bundle"));
        }
    }

    @Test
    public void wrongPassphraseShowsInlineErrorText() {
        try (ActivityController<SatnetMapsInboxActivity> controller = Robolectric.buildActivity(SatnetMapsInboxActivity.class)) {
            SatnetMapsInboxActivity activity = controller.create().start().resume().get();
            EditText passphraseInput = activity.findViewById(R.id.satnet_maps_inbox_passphrase_input);
            Button reviewButton = activity.findViewById(R.id.satnet_maps_inbox_review_button);
            TextView errorText = activity.findViewById(R.id.satnet_maps_inbox_error_text);
            TextView previewText = activity.findViewById(R.id.satnet_maps_inbox_preview_text);

            passphraseInput.setText("wrong-passphrase");
            reviewButton.performClick();

            assertEquals(View.VISIBLE, errorText.getVisibility());
            assertTrue(errorText.getText().toString().contains("Unable to decrypt the selected bundle"));
            assertTrue(previewText.getText().toString().contains("Select a staged bundle"));

            passphraseInput.setText(PASSPHRASE);
            reviewButton.performClick();

            assertTrue(errorText.getVisibility() != View.VISIBLE || errorText.getText().toString().isEmpty());
        }
    }

    @Test
    public void signedBundlesShowBadgeStylingInPendingAndHistoryRows() throws Exception {
        SatnetMapBookmark signedBookmark = SatnetMapBookmark.create("Clinic Beta", "Signed cache", 1.5d, 33.1d);
        String signedEnvelope = SatnetMapRhizomeSync.buildSignedEncryptedExportPayload(
                Collections.singletonList(signedBookmark),
                "Signed Export",
                "subject-2",
                "Community Verifier",
                PASSPHRASE,
                VoucherIssuerIdentity.createEphemeral());
        SatnetMapRhizomeSync.stageBundleForReview(context, "bundle-2", "signed-export.json", signedEnvelope);
        SatnetMapRhizomeSync.recordExportHistory(context,
                "signed-history.json",
                "Signed Export",
                23456L,
                1,
                "signed_encrypted",
                "SIGNER123",
                "Community Verifier");

        try (ActivityController<SatnetMapsInboxActivity> controller = Robolectric.buildActivity(SatnetMapsInboxActivity.class)) {
            SatnetMapsInboxActivity activity = controller.create().start().resume().get();
            View root = activity.findViewById(android.R.id.content);
            LinearLayout historyContainer = activity.findViewById(R.id.satnet_maps_inbox_history_container);

            assertNotNull(root.findViewWithTag("signed-badge:pending:bundle-2"));
            assertTrue(((TextView) root.findViewWithTag("signed-badge:pending:bundle-2")).getText().toString().contains("Signed"));

            assertEquals(2, historyContainer.getChildCount());
            LinearLayout newestHistoryRow = (LinearLayout) historyContainer.getChildAt(0);
            TextView newestHistoryDetails = (TextView) newestHistoryRow.getChildAt(newestHistoryRow.getChildCount() - 1);
            assertTrue(newestHistoryDetails.getText().toString().contains("Signed Export"));
            LinearLayout historyBadgeRow = (LinearLayout) newestHistoryRow.getChildAt(0);
            assertTrue(((TextView) historyBadgeRow.getChildAt(0)).getText().toString().contains("Signed"));
        }
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
