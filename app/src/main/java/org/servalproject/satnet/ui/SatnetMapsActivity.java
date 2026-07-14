package org.servalproject.satnet.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.servalproject.R;
import org.servalproject.permissions.RuntimePermissionGate;
import org.servalproject.satnet.SatnetRoleManager;
import org.servalproject.satnet.SatnetStartupGate;
import org.servalproject.satnet.maps.SatnetMapBookmark;
import org.servalproject.satnet.maps.SatnetMapGridView;
import org.servalproject.satnet.maps.SatnetPeerTrustSnapshot;
import org.servalproject.satnet.maps.SatnetMapRhizomeSync;
import org.servalproject.satnet.maps.SatnetMapRoleOverlay;
import org.servalproject.satnet.maps.SatnetMeshOverlaySnapshot;
import org.servalproject.satnet.maps.SecureMapBookmarkStore;
import org.servalproject.servald.IPeerListListener;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.voucher.VoucherLedger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SatnetMapsActivity extends AppCompatActivity {
    private static final int REQUEST_LOCATION_PERMISSIONS = 4201;
    private static final String[] LOCATION_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private TextView stageBadgeText;
    private TextView runtimeStatusText;
    private TextView privacySummaryText;
    private TextView roleOverlayText;
    private TextView trustOverlayText;
    private TextView meshOverlayText;
    private TextView currentLocationText;
    private TextView rhizomeStatusText;
    private TextView exportSelectionText;
    private TextView importPreviewText;
    private EditText labelInput;
    private EditText noteInput;
    private EditText latitudeInput;
    private EditText longitudeInput;
    private EditText passphraseInput;
    private Button useDeviceLocationButton;
    private Button applyManualButton;
    private Button saveBookmarkButton;
    private Button clearSessionButton;
    private Button clearSavedButton;
    private Button exportFocusedButton;
    private Button exportAllButton;
    private Button exportSelectedButton;
    private Button exportSignedButton;
    private Button previewImportButton;
    private Button importReviewedButton;
    private Button discardImportButton;
    private Button openInboxButton;
    private LinearLayout savedBookmarksContainer;
    private SatnetMapGridView mapGridView;

    private SecureMapBookmarkStore bookmarkStore;
    private SatnetRoleManager roleManager;
    private List<SatnetMapBookmark> savedBookmarks = new ArrayList<>();
    private List<SatnetMapRhizomeSync.PendingImport> pendingImports = new ArrayList<>();
    private TransientMarker currentMarker;
    private String selectedBookmarkId;
    private SatnetMapRhizomeSync.ImportPreview reviewedImportPreview;
    private final Set<String> selectedExportBookmarkIds = new LinkedHashSet<>();
    private boolean peerListenerRegistered = false;
    private boolean importReceiverRegistered = false;

    private final IPeerListListener peerListListener = new IPeerListListener() {
        @Override
        public void peerChanged(Peer p) {
            if (!isFinishing()) {
                runOnUiThread(() -> refreshMeshOverlay());
            }
        }
    };

    private final BroadcastReceiver mapsImportReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (SatnetMapRhizomeSync.ACTION_IMPORT_STAGED.equals(intent.getAction())) {
                refreshPendingImports();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            SatnetUiSupport.applySecureWindow(this);
            setContentView(R.layout.activity_satnet_maps);

            bookmarkStore = new SecureMapBookmarkStore(this);
            roleManager = new SatnetRoleManager(this);

            stageBadgeText = SatnetUiSupport.requireView(this, R.id.satnet_maps_stage_badge_text, TextView.class, "satnet_maps_stage_badge_text");
            runtimeStatusText = SatnetUiSupport.requireView(this, R.id.satnet_maps_runtime_status_text, TextView.class, "satnet_maps_runtime_status_text");
            privacySummaryText = SatnetUiSupport.requireView(this, R.id.satnet_maps_privacy_summary_text, TextView.class, "satnet_maps_privacy_summary_text");
            roleOverlayText = SatnetUiSupport.requireView(this, R.id.satnet_maps_role_overlay_text, TextView.class, "satnet_maps_role_overlay_text");
            trustOverlayText = SatnetUiSupport.requireView(this, R.id.satnet_maps_trust_overlay_text, TextView.class, "satnet_maps_trust_overlay_text");
            meshOverlayText = SatnetUiSupport.requireView(this, R.id.satnet_maps_mesh_overlay_text, TextView.class, "satnet_maps_mesh_overlay_text");
            currentLocationText = SatnetUiSupport.requireView(this, R.id.satnet_maps_current_location_text, TextView.class, "satnet_maps_current_location_text");
            rhizomeStatusText = SatnetUiSupport.requireView(this, R.id.satnet_maps_rhizome_status_text, TextView.class, "satnet_maps_rhizome_status_text");
            exportSelectionText = SatnetUiSupport.requireView(this, R.id.satnet_maps_export_selection_text, TextView.class, "satnet_maps_export_selection_text");
            importPreviewText = SatnetUiSupport.requireView(this, R.id.satnet_maps_import_preview_text, TextView.class, "satnet_maps_import_preview_text");
            labelInput = SatnetUiSupport.requireView(this, R.id.satnet_maps_label_input, EditText.class, "satnet_maps_label_input");
            noteInput = SatnetUiSupport.requireView(this, R.id.satnet_maps_note_input, EditText.class, "satnet_maps_note_input");
            latitudeInput = SatnetUiSupport.requireView(this, R.id.satnet_maps_latitude_input, EditText.class, "satnet_maps_latitude_input");
            longitudeInput = SatnetUiSupport.requireView(this, R.id.satnet_maps_longitude_input, EditText.class, "satnet_maps_longitude_input");
            passphraseInput = SatnetUiSupport.requireView(this, R.id.satnet_maps_passphrase_input, EditText.class, "satnet_maps_passphrase_input");
            useDeviceLocationButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_use_device_location_button, Button.class, "satnet_maps_use_device_location_button");
            applyManualButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_apply_manual_button, Button.class, "satnet_maps_apply_manual_button");
            saveBookmarkButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_save_bookmark_button, Button.class, "satnet_maps_save_bookmark_button");
            clearSessionButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_clear_session_button, Button.class, "satnet_maps_clear_session_button");
            clearSavedButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_clear_saved_button, Button.class, "satnet_maps_clear_saved_button");
            exportFocusedButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_export_focused_button, Button.class, "satnet_maps_export_focused_button");
            exportAllButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_export_all_button, Button.class, "satnet_maps_export_all_button");
            exportSelectedButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_export_selected_button, Button.class, "satnet_maps_export_selected_button");
            exportSignedButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_export_signed_button, Button.class, "satnet_maps_export_signed_button");
            previewImportButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_preview_import_button, Button.class, "satnet_maps_preview_import_button");
            importReviewedButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_import_reviewed_button, Button.class, "satnet_maps_import_reviewed_button");
            discardImportButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_discard_import_button, Button.class, "satnet_maps_discard_import_button");
            openInboxButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_open_inbox_button, Button.class, "satnet_maps_open_inbox_button");
            savedBookmarksContainer = SatnetUiSupport.requireView(this, R.id.satnet_maps_saved_bookmarks_container, LinearLayout.class, "satnet_maps_saved_bookmarks_container");
            mapGridView = SatnetUiSupport.requireView(this, R.id.satnet_maps_grid, SatnetMapGridView.class, "satnet_maps_grid");

            useDeviceLocationButton.setOnClickListener(v -> useDeviceLocationOnce());
            applyManualButton.setOnClickListener(v -> applyManualCoordinates(true));
            saveBookmarkButton.setOnClickListener(v -> saveCurrentBookmark());
            clearSessionButton.setOnClickListener(v -> clearTransientMarker(true));
            clearSavedButton.setOnClickListener(v -> clearSavedBookmarks());
            exportFocusedButton.setOnClickListener(v -> exportFocusedBookmark());
            exportAllButton.setOnClickListener(v -> exportAllBookmarks());
            exportSelectedButton.setOnClickListener(v -> exportSelectedBookmarks(false));
            exportSignedButton.setOnClickListener(v -> exportSelectedBookmarks(true));
            previewImportButton.setOnClickListener(v -> previewLatestImport());
            importReviewedButton.setOnClickListener(v -> importReviewedBookmarks());
            discardImportButton.setOnClickListener(v -> discardLatestImport());
            openInboxButton.setOnClickListener(v -> openInbox());

            mapGridView.setEmptyStateText(getString(R.string.satnet_maps_grid_empty_state));
            refreshRuntimeStatus();
            refreshSavedBookmarks();
            refreshPendingImports();
            refreshMeshOverlay();
            refreshRoleAndTrustOverlays();
            clearTransientMarker(false);
        } catch (Throwable t) {
            SatnetUiSupport.failInitialization(this, "SatnetMaps", t, getString(R.string.satnet_maps_init_failed));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!peerListenerRegistered) {
            PeerListService.addListener(peerListListener);
            peerListenerRegistered = true;
        }
        if (!importReceiverRegistered) {
            ContextCompat.registerReceiver(this, mapsImportReceiver,
                    new IntentFilter(SatnetMapRhizomeSync.ACTION_IMPORT_STAGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            importReceiverRegistered = true;
        }
        refreshMeshOverlay();
        refreshPendingImports();
        refreshRoleAndTrustOverlays();
    }

    @Override
    protected void onPause() {
        if (peerListenerRegistered) {
            PeerListService.removeListener(peerListListener);
            peerListenerRegistered = false;
        }
        if (importReceiverRegistered) {
            unregisterReceiver(mapsImportReceiver);
            importReceiverRegistered = false;
        }
        clearTransientMarker(false);
        super.onPause();
    }

    private void refreshRuntimeStatus() {
        SatnetStartupGate.Status status = SatnetStartupGate.evaluate(this);
        stageBadgeText.setText(status.stageBadge);

        StringBuilder summary = new StringBuilder(status.getLocalFirstMessage(getString(R.string.satnet_maps_capability_label)));
        if (roleManager != null && roleManager.getRegisteredRoles() != SatnetRoleManager.ROLE_NONE) {
            summary.append("\n\n")
                    .append(getString(R.string.satnet_maps_role_context,
                            roleManager.getRegisteredRoleSummary(),
                            roleManager.getRoleName()));
        }
        runtimeStatusText.setText(summary.toString());
    }

    private void refreshPrivacySummary() {
        privacySummaryText.setText(getString(R.string.satnet_maps_privacy_summary, savedBookmarks.size()));
        clearSavedButton.setEnabled(!savedBookmarks.isEmpty());
        exportAllButton.setEnabled(!savedBookmarks.isEmpty());
        refreshExportSelectionSummary();
    }

    private void refreshSavedBookmarks() {
        savedBookmarks = bookmarkStore.loadBookmarks();
        trimExportSelectionToSavedBookmarks();
        if (!hasSelectedBookmark(savedBookmarks, selectedBookmarkId)) {
            selectedBookmarkId = null;
        }
        mapGridView.setBookmarks(savedBookmarks);
        rebuildSavedBookmarksList();
        refreshPrivacySummary();
        refreshRoleAndTrustOverlays();
    }

    private void rebuildSavedBookmarksList() {
        savedBookmarksContainer.removeAllViews();
        if (savedBookmarks.isEmpty()) {
            TextView emptyState = new TextView(this);
            emptyState.setText(R.string.satnet_maps_saved_bookmarks_empty);
            emptyState.setTextColor(ContextCompat.getColor(this, R.color.satnet_text_secondary));
            savedBookmarksContainer.addView(emptyState);
            return;
        }

        for (SatnetMapBookmark bookmark : savedBookmarks) {
            savedBookmarksContainer.addView(buildBookmarkRow(bookmark));
        }
    }

    private View buildBookmarkRow(SatnetMapBookmark bookmark) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackgroundResource(R.drawable.satnet_card_surface);

        LinearLayout.LayoutParams rowLayoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLayoutParams.bottomMargin = dp(8);
        row.setLayoutParams(rowLayoutParams);

        CheckBox exportCheckBox = new CheckBox(this);
        exportCheckBox.setTag("export-checkbox:" + bookmark.id);
        exportCheckBox.setText(getString(R.string.satnet_maps_saved_bookmark_row,
                bookmark.getDisplayLabel(),
                bookmark.getCoordinateSummary(),
                bookmark.getDisplayNote()));
        exportCheckBox.setTextColor(ContextCompat.getColor(this, R.color.satnet_text_primary));
        exportCheckBox.setChecked(selectedExportBookmarkIds.contains(bookmark.id));
        exportCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> setBookmarkExportSelected(bookmark.id, isChecked));
        row.addView(exportCheckBox);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);

        Button viewButton = new Button(this);
        viewButton.setAllCaps(false);
        viewButton.setText(R.string.satnet_maps_view_bookmark_button);
        viewButton.setOnClickListener(v -> focusBookmark(bookmark));
        actions.addView(viewButton);

        Button deleteButton = new Button(this);
        deleteButton.setAllCaps(false);
        deleteButton.setText(R.string.satnet_maps_delete_bookmark_button);
        deleteButton.setOnClickListener(v -> {
            bookmarkStore.deleteBookmark(bookmark.id);
            if (bookmark.id.equals(selectedBookmarkId)) {
                selectedBookmarkId = null;
            }
            refreshSavedBookmarks();
            Toast.makeText(this, R.string.satnet_maps_bookmark_deleted, Toast.LENGTH_SHORT).show();
        });
        actions.addView(deleteButton);

        row.addView(actions);
        return row;
    }

    private void focusBookmark(SatnetMapBookmark bookmark) {
        if (bookmark == null) {
            return;
        }
        selectedBookmarkId = bookmark.id;
        labelInput.setText(bookmark.getDisplayLabel());
        noteInput.setText(bookmark.getDisplayNote());
        latitudeInput.setText(formatCoordinate(bookmark.latitude));
        longitudeInput.setText(formatCoordinate(bookmark.longitude));
        setCurrentMarker(new TransientMarker(bookmark.getDisplayLabel(), bookmark.getDisplayNote(), bookmark.latitude, bookmark.longitude,
                getString(R.string.satnet_maps_source_bookmark)));
        refreshRoleAndTrustOverlays();
        Toast.makeText(this, R.string.satnet_maps_bookmark_focused, Toast.LENGTH_SHORT).show();
    }

    private void useDeviceLocationOnce() {
        if (!hasAnyLocationPermission()) {
            RuntimePermissionGate.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_LOCATION_PERMISSIONS);
            Toast.makeText(this, R.string.satnet_maps_location_permissions_needed, Toast.LENGTH_SHORT).show();
            return;
        }

        Location location = getBestLastKnownLocation();
        if (location == null) {
            Toast.makeText(this, R.string.satnet_maps_location_unavailable, Toast.LENGTH_LONG).show();
            return;
        }

        String defaultLabel = resolveLabel(getString(R.string.satnet_maps_default_device_label));
        String note = location.hasAccuracy()
                ? getString(R.string.satnet_maps_device_note_with_accuracy, location.getProvider(), location.getAccuracy())
                : getString(R.string.satnet_maps_device_note, location.getProvider());

        latitudeInput.setText(formatCoordinate(location.getLatitude()));
        longitudeInput.setText(formatCoordinate(location.getLongitude()));
        if (TextUtils.isEmpty(noteInput.getText())) {
            noteInput.setText(note);
        }
        if (TextUtils.isEmpty(labelInput.getText())) {
            labelInput.setText(defaultLabel);
        }

        setCurrentMarker(new TransientMarker(defaultLabel, note, location.getLatitude(), location.getLongitude(),
                getString(R.string.satnet_maps_source_device)));
        refreshRoleAndTrustOverlays();
        Toast.makeText(this, R.string.satnet_maps_location_loaded, Toast.LENGTH_SHORT).show();
    }

    private void applyManualCoordinates(boolean announceResult) {
        Double latitude = parseLatitude();
        if (latitude == null) {
            Toast.makeText(this, R.string.satnet_maps_invalid_latitude, Toast.LENGTH_SHORT).show();
            return;
        }
        Double longitude = parseLongitude();
        if (longitude == null) {
            Toast.makeText(this, R.string.satnet_maps_invalid_longitude, Toast.LENGTH_SHORT).show();
            return;
        }

        String label = resolveLabel(getString(R.string.satnet_maps_default_manual_label));
        String note = noteInput.getText().toString().trim();
        setCurrentMarker(new TransientMarker(label, note, latitude, longitude,
                getString(R.string.satnet_maps_source_manual)));
        refreshRoleAndTrustOverlays();

        if (announceResult) {
            Toast.makeText(this, R.string.satnet_maps_manual_applied, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveCurrentBookmark() {
        if (currentMarker == null) {
            applyManualCoordinates(false);
        }
        if (currentMarker == null) {
            return;
        }

        SatnetMapBookmark bookmark = SatnetMapBookmark.create(
                currentMarker.label,
                currentMarker.note,
                currentMarker.latitude,
                currentMarker.longitude);
        bookmarkStore.upsertBookmark(bookmark);
        selectedBookmarkId = bookmark.id;
        refreshSavedBookmarks();
        focusBookmark(bookmark);
        Toast.makeText(this, R.string.satnet_maps_bookmark_saved, Toast.LENGTH_SHORT).show();
    }

    private void clearSavedBookmarks() {
        selectedBookmarkId = null;
        bookmarkStore.clearAll();
        refreshSavedBookmarks();
        Toast.makeText(this, R.string.satnet_maps_saved_bookmarks_cleared, Toast.LENGTH_SHORT).show();
    }

    private void exportFocusedBookmark() {
        SatnetMapBookmark exportBookmark = findExportCandidate();
        if (exportBookmark == null) {
            Toast.makeText(this, R.string.satnet_maps_export_nothing_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        publishBookmarksToRhizome(
                java.util.Collections.singletonList(exportBookmark),
                exportBookmark.getDisplayLabel(),
                SatnetMapRhizomeSync.SECURITY_MODE_ENCRYPTED);
    }

    private void exportAllBookmarks() {
        if (savedBookmarks.isEmpty()) {
            Toast.makeText(this, R.string.satnet_maps_export_no_saved, Toast.LENGTH_SHORT).show();
            return;
        }
        publishBookmarksToRhizome(savedBookmarks,
                getString(R.string.satnet_maps_saved_bookmarks_title),
                SatnetMapRhizomeSync.SECURITY_MODE_ENCRYPTED);
    }

    private void exportSelectedBookmarks(boolean signed) {
        List<SatnetMapBookmark> selectedBookmarks = getSelectedBookmarksForExport();
        if (selectedBookmarks.isEmpty()) {
            Toast.makeText(this, R.string.satnet_maps_export_none_checked, Toast.LENGTH_SHORT).show();
            return;
        }
        publishBookmarksToRhizome(
                selectedBookmarks,
                buildSelectionExportLabel(selectedBookmarks),
                signed ? SatnetMapRhizomeSync.SECURITY_MODE_SIGNED_ENCRYPTED : SatnetMapRhizomeSync.SECURITY_MODE_ENCRYPTED);
    }

    private void publishBookmarksToRhizome(List<SatnetMapBookmark> bookmarks, String exportLabel, int securityMode) {
        String passphrase = requirePassphrase();
        if (passphrase == null) {
            return;
        }
        boolean published = SatnetMapRhizomeSync.publishBookmarksToRhizome(this,
                bookmarks,
                exportLabel,
                passphrase,
                roleManager,
                securityMode);
        if (published) {
            Toast.makeText(this,
                    securityMode == SatnetMapRhizomeSync.SECURITY_MODE_SIGNED_ENCRYPTED
                            ? R.string.satnet_maps_export_signed_success
                            : R.string.satnet_maps_export_success,
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.satnet_maps_export_failed, Toast.LENGTH_LONG).show();
        }
        refreshPendingImports();
    }

    private void refreshPendingImports() {
        pendingImports = SatnetMapRhizomeSync.listPendingImports(this);
        int historyCount = SatnetMapRhizomeSync.listExportHistory(this).size();
        int signedPendingCount = 0;
        for (SatnetMapRhizomeSync.PendingImport pendingImport : pendingImports) {
            if (pendingImport.signed) {
                signedPendingCount++;
            }
        }
        if (pendingImports.isEmpty()) {
            rhizomeStatusText.setText(R.string.satnet_maps_pending_import_none);
            previewImportButton.setEnabled(false);
            discardImportButton.setEnabled(false);
        } else {
            SatnetMapRhizomeSync.PendingImport latest = pendingImports.get(0);
            rhizomeStatusText.setText(getString(R.string.satnet_maps_pending_import_status,
                    pendingImports.size(), latest.displayName, signedPendingCount, historyCount));
            previewImportButton.setEnabled(true);
            discardImportButton.setEnabled(true);
        }
        importReviewedButton.setEnabled(reviewedImportPreview != null && !reviewedImportPreview.bookmarks.isEmpty());
    }

    private void previewLatestImport() {
        if (pendingImports.isEmpty()) {
            Toast.makeText(this, R.string.satnet_maps_pending_import_none, Toast.LENGTH_SHORT).show();
            return;
        }
        String passphrase = requirePassphrase();
        if (passphrase == null) {
            return;
        }
        SatnetMapRhizomeSync.PendingImport latest = pendingImports.get(0);
        try {
            reviewedImportPreview = SatnetMapRhizomeSync.previewPendingImport(this, latest.bundleId, passphrase);
            importPreviewText.setText(buildImportPreviewText(reviewedImportPreview));
            importReviewedButton.setEnabled(!reviewedImportPreview.bookmarks.isEmpty());
        } catch (Exception e) {
            reviewedImportPreview = null;
            importPreviewText.setText(R.string.satnet_maps_import_preview_placeholder);
            importReviewedButton.setEnabled(false);
            Toast.makeText(this, R.string.satnet_maps_import_preview_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void importReviewedBookmarks() {
        if (reviewedImportPreview == null || TextUtils.isEmpty(reviewedImportPreview.bundleId)) {
            Toast.makeText(this, R.string.satnet_maps_import_none_reviewed, Toast.LENGTH_SHORT).show();
            return;
        }
        String passphrase = requirePassphrase();
        if (passphrase == null) {
            return;
        }
        try {
            SatnetMapRhizomeSync.importPendingBookmarks(this, bookmarkStore, reviewedImportPreview.bundleId, passphrase);
            reviewedImportPreview = null;
            importPreviewText.setText(R.string.satnet_maps_import_preview_placeholder);
            refreshSavedBookmarks();
            refreshPendingImports();
            Toast.makeText(this, R.string.satnet_maps_import_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.satnet_maps_import_preview_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void discardLatestImport() {
        if (pendingImports.isEmpty()) {
            Toast.makeText(this, R.string.satnet_maps_pending_import_none, Toast.LENGTH_SHORT).show();
            return;
        }
        SatnetMapRhizomeSync.PendingImport latest = pendingImports.get(0);
        SatnetMapRhizomeSync.discardPendingImport(this, latest.bundleId);
        if (reviewedImportPreview != null && latest.bundleId.equals(reviewedImportPreview.bundleId)) {
            reviewedImportPreview = null;
            importPreviewText.setText(R.string.satnet_maps_import_preview_placeholder);
        }
        refreshPendingImports();
        Toast.makeText(this, R.string.satnet_maps_import_discarded, Toast.LENGTH_SHORT).show();
    }

    private String buildImportPreviewText(SatnetMapRhizomeSync.ImportPreview preview) {
        StringBuilder builder = new StringBuilder(getString(R.string.satnet_maps_import_preview_ready,
                preview.displayName,
                preview.exportLabel,
                preview.sourceSubjectId,
                preview.roleSummary,
                preview.bookmarks.size()));
        builder.append("\n")
                .append(getString(R.string.satnet_maps_import_preview_security,
                        getSecurityModeLabel(preview.securityMode),
                        getSignatureStatusLabel(preview)));
        for (SatnetMapBookmark bookmark : preview.bookmarks) {
            builder.append("\n")
                    .append(getString(R.string.satnet_maps_import_preview_bookmark_item,
                            bookmark.getDisplayLabel(),
                            bookmark.getCoordinateSummary()));
        }
        return builder.toString();
    }

    private void clearTransientMarker(boolean announceResult) {
        currentMarker = null;
        mapGridView.clearCurrentMarker();
        currentLocationText.setText(R.string.satnet_maps_current_location_placeholder);
        if (announceResult) {
            Toast.makeText(this, R.string.satnet_maps_session_cleared, Toast.LENGTH_SHORT).show();
        }
    }

    private void setCurrentMarker(TransientMarker marker) {
        currentMarker = marker;
        mapGridView.setCurrentMarker(marker.latitude, marker.longitude, marker.label);

        String note = marker.note == null || marker.note.trim().isEmpty()
                ? getString(R.string.satnet_maps_current_location_no_note)
                : marker.note;
        currentLocationText.setText(getString(R.string.satnet_maps_current_location_summary,
                marker.source,
                marker.label,
                formatCoordinate(marker.latitude),
                formatCoordinate(marker.longitude),
                note));
    }

    private void refreshRoleAndTrustOverlays() {
        VoucherLedger.VerifierTrustSummary trustSummary = loadVerifierTrustSummary();
        refreshRoleOverlay(trustSummary);
        refreshTrustOverlay(trustSummary);
    }

    private void refreshRoleOverlay(VoucherLedger.VerifierTrustSummary trustSummary) {
        SatnetMapRoleOverlay overlay = buildRoleOverlay(trustSummary);
        mapGridView.setRoleOverlay(overlay);
        roleOverlayText.setText(overlay.title + "\n" + overlay.summary);
    }

    private void refreshTrustOverlay(VoucherLedger.VerifierTrustSummary trustSummary) {
        VoucherLedger.VerifierTrustSummary resolvedTrustSummary = trustSummary == null
                ? VoucherLedger.VerifierTrustSummary.EMPTY
                : trustSummary;
        mapGridView.setPeerTrustOverlay(new SatnetPeerTrustSnapshot(
                resolvedTrustSummary.trustedAuditCount,
                resolvedTrustSummary.cautionAuditCount,
                resolvedTrustSummary.rotationAlertCount,
                resolvedTrustSummary.meshEvidenceCount,
                resolvedTrustSummary.localEvidenceCount,
                resolvedTrustSummary.auditedVoucherCount,
                resolvedTrustSummary.latestAuditTime));
        if (!resolvedTrustSummary.hasEvidence()) {
            trustOverlayText.setText(R.string.satnet_maps_trust_overlay_empty);
            return;
        }
        trustOverlayText.setText(getString(R.string.satnet_maps_trust_overlay_summary,
                resolvedTrustSummary.trustedAuditCount,
                resolvedTrustSummary.cautionAuditCount,
                resolvedTrustSummary.rotationAlertCount,
                resolvedTrustSummary.meshEvidenceCount,
                resolvedTrustSummary.localEvidenceCount,
                resolvedTrustSummary.auditedVoucherCount));
    }

    private SatnetMapRoleOverlay buildRoleOverlay(VoucherLedger.VerifierTrustSummary trustSummary) {
        VoucherLedger.VerifierTrustSummary resolvedTrustSummary = trustSummary == null
                ? VoucherLedger.VerifierTrustSummary.EMPTY
                : trustSummary;
        int activeRole = roleManager == null ? SatnetRoleManager.ROLE_USER : roleManager.getActiveRole();
        switch (activeRole) {
            case SatnetRoleManager.ROLE_AGENT:
                return new SatnetMapRoleOverlay(
                        getString(R.string.satnet_maps_role_overlay_agent_title),
                        getString(R.string.satnet_maps_role_overlay_agent_summary,
                                fallbackText(roleManager.getAgentName(), "Agent"),
                                fallbackText(roleManager.getAgentLocation(), "local region")),
                        Color.parseColor("#FF8F00"),
                        0.18f);
            case SatnetRoleManager.ROLE_MERCHANT:
                return new SatnetMapRoleOverlay(
                        getString(R.string.satnet_maps_role_overlay_merchant_title),
                        getString(R.string.satnet_maps_role_overlay_merchant_summary,
                                fallbackText(roleManager.getMerchantName(), "Merchant"),
                                fallbackText(roleManager.getMerchantType(), "field trade")),
                        Color.parseColor("#2E7D32"),
                        0.14f);
            case SatnetRoleManager.ROLE_VERIFIER:
                int verifierAccentColor = resolvedTrustSummary.hasCautionSignals()
                        ? Color.parseColor("#EF6C00")
                        : resolvedTrustSummary.hasStrongTrustSignals()
                        ? Color.parseColor("#2E7D32")
                        : Color.parseColor("#6A1B9A");
                return new SatnetMapRoleOverlay(
                        getString(R.string.satnet_maps_role_overlay_verifier_title),
                        resolvedTrustSummary.hasEvidence()
                                ? getString(R.string.satnet_maps_role_overlay_verifier_summary_rich,
                                resolvedTrustSummary.trustedAuditCount,
                                resolvedTrustSummary.cautionAuditCount,
                                resolvedTrustSummary.meshEvidenceCount)
                                : getString(R.string.satnet_maps_role_overlay_verifier_summary),
                        verifierAccentColor,
                        resolvedTrustSummary.hasEvidence() ? 0.26f : 0.22f);
            case SatnetRoleManager.ROLE_USER:
            default:
                return new SatnetMapRoleOverlay(
                        getString(R.string.satnet_maps_role_overlay_user_title),
                        getString(R.string.satnet_maps_role_overlay_user_summary),
                        Color.parseColor("#1565C0"),
                        0.12f);
        }
    }

    private void refreshMeshOverlay() {
        SatnetMeshOverlaySnapshot snapshot = buildMeshOverlaySnapshot();
        mapGridView.setMeshOverlay(snapshot);
        if (!snapshot.hasCoverage()) {
            meshOverlayText.setText(R.string.satnet_maps_mesh_overlay_empty);
            return;
        }
        meshOverlayText.setText(getString(R.string.satnet_maps_mesh_overlay_summary,
                snapshot.reachablePeerCount,
                snapshot.directPeerCount,
                snapshot.relayedPeerCount,
                snapshot.averageHopCount,
                snapshot.maxHopCount));
    }

    private SatnetMeshOverlaySnapshot buildMeshOverlaySnapshot() {
        int reachable = 0;
        int direct = 0;
        int relayed = 0;
        int maxHop = 0;
        double hopSum = 0d;
        for (Peer peer : PeerListService.peers.values()) {
            if (peer == null || !peer.isReachable()) {
                continue;
            }
            reachable++;
            int hopCount = Math.max(1, peer.getHopCount());
            if (hopCount <= 1) {
                direct++;
            } else {
                relayed++;
            }
            hopSum += hopCount;
            maxHop = Math.max(maxHop, hopCount);
        }
        if (reachable == 0) {
            int lastPeerCount = Math.max(0, PeerListService.getLastPeerCount());
            if (lastPeerCount <= 0) {
                return SatnetMeshOverlaySnapshot.EMPTY;
            }
            return new SatnetMeshOverlaySnapshot(lastPeerCount, lastPeerCount, 0, 1, 1d);
        }
        return new SatnetMeshOverlaySnapshot(reachable, direct, relayed, maxHop, hopSum / reachable);
    }

    private VoucherLedger.VerifierTrustSummary loadVerifierTrustSummary() {
        try (VoucherLedger ledger = new VoucherLedger(this)) {
            return ledger.getVerifierTrustSummary();
        } catch (Exception e) {
            return VoucherLedger.VerifierTrustSummary.EMPTY;
        }
    }

    private SatnetMapBookmark findExportCandidate() {
        if (selectedBookmarkId != null) {
            for (SatnetMapBookmark bookmark : savedBookmarks) {
                if (selectedBookmarkId.equals(bookmark.id)) {
                    return bookmark;
                }
            }
        }
        if (currentMarker != null) {
            return new SatnetMapBookmark("current-marker",
                    currentMarker.label,
                    currentMarker.note,
                    currentMarker.latitude,
                    currentMarker.longitude,
                    System.currentTimeMillis());
        }
        return null;
    }

    private void trimExportSelectionToSavedBookmarks() {
        LinkedHashSet<String> validSelection = new LinkedHashSet<>();
        for (SatnetMapBookmark bookmark : savedBookmarks) {
            if (bookmark != null && selectedExportBookmarkIds.contains(bookmark.id)) {
                validSelection.add(bookmark.id);
            }
        }
        selectedExportBookmarkIds.clear();
        selectedExportBookmarkIds.addAll(validSelection);
    }

    private void setBookmarkExportSelected(String bookmarkId, boolean selected) {
        if (TextUtils.isEmpty(bookmarkId)) {
            return;
        }
        if (selected) {
            selectedExportBookmarkIds.add(bookmarkId);
        } else {
            selectedExportBookmarkIds.remove(bookmarkId);
        }
        refreshExportSelectionSummary();
    }

    private List<SatnetMapBookmark> getSelectedBookmarksForExport() {
        ArrayList<SatnetMapBookmark> selectedBookmarks = new ArrayList<>();
        for (SatnetMapBookmark bookmark : savedBookmarks) {
            if (bookmark != null && selectedExportBookmarkIds.contains(bookmark.id)) {
                selectedBookmarks.add(bookmark);
            }
        }
        return selectedBookmarks;
    }

    private void refreshExportSelectionSummary() {
        int selectedCount = getSelectedBookmarksForExport().size();
        exportSelectionText.setText(selectedCount <= 0
                ? getString(R.string.satnet_maps_export_selection_none)
                : getString(R.string.satnet_maps_export_selection_summary, selectedCount));
        exportSelectedButton.setEnabled(selectedCount > 0);
        exportSignedButton.setEnabled(selectedCount > 0);
    }

    private String buildSelectionExportLabel(List<SatnetMapBookmark> selectedBookmarks) {
        if (selectedBookmarks == null || selectedBookmarks.isEmpty()) {
            return getString(R.string.satnet_maps_saved_bookmarks_title);
        }
        if (selectedBookmarks.size() == 1) {
            return selectedBookmarks.get(0).getDisplayLabel();
        }
        return getString(R.string.satnet_maps_export_selection_label, selectedBookmarks.size());
    }

    private boolean hasSelectedBookmark(List<SatnetMapBookmark> bookmarks, String bookmarkId) {
        if (bookmarks == null || TextUtils.isEmpty(bookmarkId)) {
            return false;
        }
        for (SatnetMapBookmark bookmark : bookmarks) {
            if (bookmark != null && bookmarkId.equals(bookmark.id)) {
                return true;
            }
        }
        return false;
    }

    private String requirePassphrase() {
        String passphrase = passphraseInput.getText().toString().trim();
        if (passphrase.length() < 6) {
            Toast.makeText(this, R.string.satnet_maps_passphrase_required, Toast.LENGTH_SHORT).show();
            return null;
        }
        return passphrase;
    }

    private String fallbackText(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value.trim();
    }

    private String getSecurityModeLabel(String securityMode) {
        return SatnetMapRhizomeSync.SECURITY_MODE_SIGNED_ENCRYPTED == resolveSecurityMode(securityMode)
                ? getString(R.string.satnet_maps_security_mode_signed)
                : getString(R.string.satnet_maps_security_mode_encrypted);
    }

    private String getSignatureStatusLabel(SatnetMapRhizomeSync.ImportPreview preview) {
        if (preview == null || !preview.signed) {
            return getString(R.string.satnet_maps_signature_status_none);
        }
        if (preview.signatureVerified) {
            return getString(R.string.satnet_maps_signature_status_verified,
                    fallbackText(preview.signerKeyId, getString(R.string.satnet_maps_signer_none)),
                    fallbackText(preview.signatureAlgorithm, "signature"));
        }
        return getString(R.string.satnet_maps_signature_status_present);
    }

    private int resolveSecurityMode(String securityMode) {
        return "signed_encrypted".equalsIgnoreCase(securityMode)
                ? SatnetMapRhizomeSync.SECURITY_MODE_SIGNED_ENCRYPTED
                : SatnetMapRhizomeSync.SECURITY_MODE_ENCRYPTED;
    }

    private void openInbox() {
        startActivity(new Intent(this, SatnetMapsInboxActivity.class));
    }

    private boolean hasAnyLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private Location getBestLastKnownLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return null;
        }

        List<String> providers = new ArrayList<>();
        providers.add(LocationManager.GPS_PROVIDER);
        providers.add(LocationManager.NETWORK_PROVIDER);
        providers.add(LocationManager.PASSIVE_PROVIDER);

        Location best = null;
        for (String provider : providers) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        && LocationManager.GPS_PROVIDER.equals(provider)) {
                    continue;
                }
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (candidate == null) {
                    continue;
                }
                if (best == null || isBetterLocation(candidate, best)) {
                    best = candidate;
                }
            } catch (RuntimeException ignored) {
                // Keep the feature local-first even if a provider is absent.
            }
        }
        return best;
    }

    private boolean isBetterLocation(Location candidate, Location best) {
        if (best == null) {
            return true;
        }
        long timeDelta = candidate.getTime() - best.getTime();
        if (timeDelta > 120_000L) {
            return true;
        }
        if (timeDelta < -120_000L) {
            return false;
        }
        float candidateAccuracy = candidate.hasAccuracy() ? candidate.getAccuracy() : Float.MAX_VALUE;
        float bestAccuracy = best.hasAccuracy() ? best.getAccuracy() : Float.MAX_VALUE;
        return candidateAccuracy <= bestAccuracy || timeDelta > 0L;
    }

    private String resolveLabel(String fallback) {
        String label = labelInput.getText().toString().trim();
        return label.isEmpty() ? fallback : label;
    }

    private Double parseLatitude() {
        return parseCoordinate(latitudeInput.getText().toString().trim(), -90.0d, 90.0d);
    }

    private Double parseLongitude() {
        return parseCoordinate(longitudeInput.getText().toString().trim(), -180.0d, 180.0d);
    }

    private Double parseCoordinate(String text, double min, double max) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(text.trim());
            if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
                return null;
            }
            return value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatCoordinate(double value) {
        return String.format(Locale.US, "%.5f", value);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCATION_PERMISSIONS) {
            return;
        }
        if (hasAnyLocationPermission()) {
            useDeviceLocationOnce();
        } else {
            Toast.makeText(this, R.string.satnet_maps_location_permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    private static final class TransientMarker {
        final String label;
        final String note;
        final double latitude;
        final double longitude;
        final String source;

        TransientMarker(String label, String note, double latitude, double longitude, String source) {
            this.label = label;
            this.note = note;
            this.latitude = latitude;
            this.longitude = longitude;
            this.source = source;
        }
    }
}

