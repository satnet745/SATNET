package org.servalproject.satnet.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.servalproject.R;
import org.servalproject.satnet.SatnetStartupGate;
import org.servalproject.satnet.maps.SatnetMapBookmark;
import org.servalproject.satnet.maps.SatnetMapRhizomeSync;
import org.servalproject.satnet.maps.SecureMapBookmarkStore;

import java.util.ArrayList;
import java.util.List;

public class SatnetMapsInboxActivity extends AppCompatActivity {

    private TextView stageBadgeText;
    private TextView summaryText;
    private TextView selectedBundleText;
    private TextView errorText;
    private TextView previewText;
    private Button reviewButton;
    private Button importButton;
    private Button discardButton;
    private Button refreshButton;
    private EditText passphraseInput;
    private LinearLayout pendingContainer;
    private LinearLayout historyContainer;
    private SecureMapBookmarkStore bookmarkStore;
    private List<SatnetMapRhizomeSync.PendingImport> pendingImports = new ArrayList<>();
    private List<SatnetMapRhizomeSync.ExportHistoryEntry> exportHistory = new ArrayList<>();
    private String selectedPendingBundleId;
    private SatnetMapRhizomeSync.ImportPreview reviewedImportPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            SatnetUiSupport.applySecureWindow(this);
            setContentView(R.layout.activity_satnet_maps_inbox);

            bookmarkStore = new SecureMapBookmarkStore(this);
            stageBadgeText = SatnetUiSupport.requireView(this, R.id.satnet_maps_inbox_stage_badge_text, TextView.class, "satnet_maps_inbox_stage_badge_text");
            summaryText = SatnetUiSupport.requireView(this, R.id.satnet_maps_inbox_summary_text, TextView.class, "satnet_maps_inbox_summary_text");
            selectedBundleText = SatnetUiSupport.requireView(this, R.id.satnet_maps_inbox_selected_bundle_text, TextView.class, "satnet_maps_inbox_selected_bundle_text");
            errorText = SatnetUiSupport.requireView(this, R.id.satnet_maps_inbox_error_text, TextView.class, "satnet_maps_inbox_error_text");
            previewText = SatnetUiSupport.requireView(this, R.id.satnet_maps_inbox_preview_text, TextView.class, "satnet_maps_inbox_preview_text");
            passphraseInput = SatnetUiSupport.requireView(this, R.id.satnet_maps_inbox_passphrase_input, EditText.class, "satnet_maps_inbox_passphrase_input");
            reviewButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_inbox_review_button, Button.class, "satnet_maps_inbox_review_button");
            importButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_inbox_import_button, Button.class, "satnet_maps_inbox_import_button");
            discardButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_inbox_discard_button, Button.class, "satnet_maps_inbox_discard_button");
            refreshButton = SatnetUiSupport.requireView(this, R.id.satnet_maps_inbox_refresh_button, Button.class, "satnet_maps_inbox_refresh_button");
            pendingContainer = SatnetUiSupport.requireView(this, R.id.satnet_maps_inbox_pending_container, LinearLayout.class, "satnet_maps_inbox_pending_container");
            historyContainer = SatnetUiSupport.requireView(this, R.id.satnet_maps_inbox_history_container, LinearLayout.class, "satnet_maps_inbox_history_container");

            reviewButton.setOnClickListener(v -> previewSelectedBundle());
            importButton.setOnClickListener(v -> importReviewedBundle());
            discardButton.setOnClickListener(v -> discardSelectedBundle());
            refreshButton.setOnClickListener(v -> refreshInbox());

            refreshInbox();
        } catch (Throwable t) {
            SatnetUiSupport.failInitialization(this, "SatnetMapsInbox", t, getString(R.string.satnet_maps_init_failed));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshInbox();
    }

    private void refreshInbox() {
        SatnetStartupGate.Status status = SatnetStartupGate.evaluate(this);
        stageBadgeText.setText(status.stageBadge);

        pendingImports = SatnetMapRhizomeSync.listPendingImports(this);
        exportHistory = SatnetMapRhizomeSync.listExportHistory(this);
        summaryText.setText(getString(R.string.satnet_maps_inbox_summary, pendingImports.size(), exportHistory.size()));

        SatnetMapRhizomeSync.PendingImport selectedPendingImport = findPendingImport(selectedPendingBundleId);
        if (selectedPendingImport == null) {
            selectedPendingBundleId = pendingImports.isEmpty() ? null : pendingImports.get(0).bundleId;
            selectedPendingImport = findPendingImport(selectedPendingBundleId);
            clearReviewedPreview();
            clearInlineError();
        }

        updateSelectedBundleSummary(selectedPendingImport);
        updateActionState(selectedPendingImport);

        rebuildPendingImports(pendingImports);
        rebuildExportHistory(exportHistory);
    }

    private void rebuildPendingImports(List<SatnetMapRhizomeSync.PendingImport> pendingImports) {
        pendingContainer.removeAllViews();
        if (pendingImports == null || pendingImports.isEmpty()) {
            pendingContainer.addView(buildEmptyStateText(R.string.satnet_maps_inbox_empty));
            return;
        }
        for (SatnetMapRhizomeSync.PendingImport pendingImport : pendingImports) {
            pendingContainer.addView(buildPendingImportRow(pendingImport));
        }
    }

    private void rebuildExportHistory(List<SatnetMapRhizomeSync.ExportHistoryEntry> exportHistory) {
        historyContainer.removeAllViews();
        if (exportHistory == null || exportHistory.isEmpty()) {
            historyContainer.addView(buildEmptyStateText(R.string.satnet_maps_history_empty));
            return;
        }
        for (SatnetMapRhizomeSync.ExportHistoryEntry historyEntry : exportHistory) {
            historyContainer.addView(buildHistoryRow(historyEntry));
        }
    }

    private View buildPendingImportRow(SatnetMapRhizomeSync.PendingImport pendingImport) {
        LinearLayout row = buildCardRow();
        boolean selected = TextUtils.equals(selectedPendingBundleId, pendingImport.bundleId);
        row.setTag("pending-row:" + pendingImport.bundleId);
        row.setBackgroundResource(selected ? R.drawable.satnet_card_surface_selected : R.drawable.satnet_card_surface);
        row.setOnClickListener(v -> selectPendingBundle(pendingImport.bundleId));

        LinearLayout badgeRow = buildBadgeRow();
        if (selected) {
            badgeRow.addView(buildBadge(getString(R.string.satnet_maps_inbox_selected_badge),
                    R.color.satnet_primary,
                    "selected-badge:" + pendingImport.bundleId));
        }
        if (pendingImport.signed) {
            badgeRow.addView(buildBadge(getString(R.string.satnet_maps_inbox_signed_badge),
                    R.color.satnet_accent,
                    "signed-badge:pending:" + pendingImport.bundleId));
        }
        if (badgeRow.getChildCount() > 0) {
            row.addView(badgeRow);
        }

        TextView detailsText = buildDetailsText();
        detailsText.setTag("pending-details:" + pendingImport.bundleId);
        detailsText.setTypeface(Typeface.MONOSPACE, selected ? Typeface.BOLD : Typeface.NORMAL);
        detailsText.setText(buildPendingRowText(pendingImport));
        if (selected) {
            detailsText.setTextColor(ContextCompat.getColor(this, R.color.satnet_text_primary));
        }
        row.addView(detailsText);

        LinearLayout actions = buildActionRow();

        Button selectButton = new Button(this);
        selectButton.setAllCaps(false);
        selectButton.setText(R.string.satnet_maps_inbox_select_button);
        selectButton.setOnClickListener(v -> selectPendingBundle(pendingImport.bundleId));
        actions.addView(selectButton);

        Button discardButton = new Button(this);
        discardButton.setAllCaps(false);
        discardButton.setText(R.string.satnet_maps_inbox_discard_button);
        discardButton.setOnClickListener(v -> {
            if (TextUtils.equals(selectedPendingBundleId, pendingImport.bundleId)) {
                selectedPendingBundleId = null;
                clearReviewedPreview();
            }
            clearInlineError();
            SatnetMapRhizomeSync.discardPendingImport(this, pendingImport.bundleId);
            refreshInbox();
            Toast.makeText(this, R.string.satnet_maps_import_discarded, Toast.LENGTH_SHORT).show();
        });
        actions.addView(discardButton);

        row.addView(actions);
        return row;
    }

    private View buildHistoryRow(SatnetMapRhizomeSync.ExportHistoryEntry historyEntry) {
        LinearLayout row = buildCardRow();
        row.setTag("history-row:" + historyEntry.historyId);

        if (historyEntry.signed) {
            LinearLayout badgeRow = buildBadgeRow();
            badgeRow.addView(buildBadge(getString(R.string.satnet_maps_inbox_signed_badge),
                    R.color.satnet_accent,
                    "signed-badge:history:" + historyEntry.historyId));
            row.addView(badgeRow);
        }

        TextView detailsText = buildDetailsText();
        detailsText.setTag("history-details:" + historyEntry.historyId);
        detailsText.setText(buildHistoryRowText(historyEntry));
        row.addView(detailsText);

        return row;
    }

    private void selectPendingBundle(String bundleId) {
        if (TextUtils.equals(selectedPendingBundleId, bundleId)) {
            updateSelectedBundleSummary(findPendingImport(bundleId));
            updateActionState(findPendingImport(bundleId));
            return;
        }
        selectedPendingBundleId = bundleId;
        clearReviewedPreview();
        clearInlineError();
        updateSelectedBundleSummary(findPendingImport(bundleId));
        updateActionState(findPendingImport(bundleId));
        rebuildPendingImports(pendingImports);
    }

    private void previewSelectedBundle() {
        SatnetMapRhizomeSync.PendingImport pendingImport = findPendingImport(selectedPendingBundleId);
        if (pendingImport == null) {
            Toast.makeText(this, R.string.satnet_maps_pending_import_none, Toast.LENGTH_SHORT).show();
            return;
        }
        String passphrase = requirePassphrase();
        if (passphrase == null) {
            return;
        }
        try {
            reviewedImportPreview = SatnetMapRhizomeSync.previewPendingImport(this, pendingImport.bundleId, passphrase);
            clearInlineError();
            previewText.setText(buildImportPreviewText(reviewedImportPreview));
            importButton.setEnabled(!reviewedImportPreview.bookmarks.isEmpty());
        } catch (Exception e) {
            clearReviewedPreview();
            showInlineError(R.string.satnet_maps_inbox_error_wrong_passphrase);
        }
    }

    private void importReviewedBundle() {
        if (reviewedImportPreview == null || TextUtils.isEmpty(reviewedImportPreview.bundleId)) {
            Toast.makeText(this, R.string.satnet_maps_import_none_reviewed, Toast.LENGTH_SHORT).show();
            return;
        }
        String passphrase = requirePassphrase();
        if (passphrase == null) {
            return;
        }
        try {
            String importedBundleId = reviewedImportPreview.bundleId;
            SatnetMapRhizomeSync.importPendingBookmarks(this, bookmarkStore, importedBundleId, passphrase);
            clearInlineError();
            clearReviewedPreview();
            if (TextUtils.equals(selectedPendingBundleId, importedBundleId)) {
                selectedPendingBundleId = null;
            }
            refreshInbox();
            Toast.makeText(this, R.string.satnet_maps_import_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showInlineError(R.string.satnet_maps_inbox_error_wrong_passphrase);
        }
    }

    private void discardSelectedBundle() {
        SatnetMapRhizomeSync.PendingImport pendingImport = findPendingImport(selectedPendingBundleId);
        if (pendingImport == null) {
            Toast.makeText(this, R.string.satnet_maps_pending_import_none, Toast.LENGTH_SHORT).show();
            return;
        }
        if (reviewedImportPreview != null && TextUtils.equals(reviewedImportPreview.bundleId, pendingImport.bundleId)) {
            clearReviewedPreview();
        }
        clearInlineError();
        selectedPendingBundleId = null;
        SatnetMapRhizomeSync.discardPendingImport(this, pendingImport.bundleId);
        refreshInbox();
        Toast.makeText(this, R.string.satnet_maps_import_discarded, Toast.LENGTH_SHORT).show();
    }

    private void updateSelectedBundleSummary(SatnetMapRhizomeSync.PendingImport pendingImport) {
        if (pendingImport == null) {
            selectedBundleText.setText(R.string.satnet_maps_inbox_selected_placeholder);
            return;
        }
        selectedBundleText.setText(getString(R.string.satnet_maps_inbox_selected_summary,
                pendingImport.displayName,
                pendingImport.exportLabel,
                pendingImport.bookmarkCount,
                getSecurityModeLabel(pendingImport.securityMode),
                getSignerLabel(pendingImport.signed, pendingImport.signerKeyId))
                + buildRoleSuffix(pendingImport.roleSummary));
    }

    private void updateActionState(SatnetMapRhizomeSync.PendingImport selectedPendingImport) {
        boolean hasSelection = selectedPendingImport != null;
        reviewButton.setEnabled(hasSelection);
        discardButton.setEnabled(hasSelection);
        importButton.setEnabled(reviewedImportPreview != null
                && hasSelection
                && TextUtils.equals(reviewedImportPreview.bundleId, selectedPendingImport.bundleId)
                && !reviewedImportPreview.bookmarks.isEmpty());
    }

    private void clearReviewedPreview() {
        reviewedImportPreview = null;
        previewText.setText(R.string.satnet_maps_inbox_preview_placeholder);
        importButton.setEnabled(false);
    }

    private void showInlineError(int stringId) {
        errorText.setVisibility(View.VISIBLE);
        errorText.setText(stringId);
    }

    private void clearInlineError() {
        errorText.setText("");
        errorText.setVisibility(View.GONE);
    }

    private SatnetMapRhizomeSync.PendingImport findPendingImport(String bundleId) {
        if (TextUtils.isEmpty(bundleId)) {
            return null;
        }
        for (SatnetMapRhizomeSync.PendingImport pendingImport : pendingImports) {
            if (TextUtils.equals(bundleId, pendingImport.bundleId)) {
                return pendingImport;
            }
        }
        return null;
    }

    private LinearLayout buildCardRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.satnet_card_surface);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);
        return row;
    }

    private LinearLayout buildBadgeRow() {
        LinearLayout badges = new LinearLayout(this);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        badges.setLayoutParams(params);
        return badges;
    }

    private TextView buildBadge(String text, int colorResId, String tag) {
        TextView badge = new TextView(this);
        badge.setTag(tag);
        badge.setBackgroundResource(R.drawable.satnet_badge_background);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        badge.setText(text);
        badge.setTextColor(ContextCompat.getColor(this, colorResId));
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(6);
        badge.setLayoutParams(params);
        return badge;
    }

    private TextView buildDetailsText() {
        TextView textView = new TextView(this);
        textView.setTextColor(ContextCompat.getColor(this, R.color.satnet_text_primary));
        textView.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        return textView;
    }

    private LinearLayout buildActionRow() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        return actions;
    }

    private TextView buildEmptyStateText(int stringId) {
        TextView emptyState = new TextView(this);
        emptyState.setText(stringId);
        emptyState.setTextColor(ContextCompat.getColor(this, R.color.satnet_text_secondary));
        return emptyState;
    }

    private String buildPendingRowText(SatnetMapRhizomeSync.PendingImport pendingImport) {
        return getString(R.string.satnet_maps_inbox_pending_row,
                pendingImport.displayName,
                pendingImport.exportLabel,
                pendingImport.bookmarkCount,
                getSecurityModeLabel(pendingImport.securityMode),
                getSignerLabel(pendingImport.signed, pendingImport.signerKeyId))
                + buildRoleSuffix(pendingImport.roleSummary);
    }

    private String buildHistoryRowText(SatnetMapRhizomeSync.ExportHistoryEntry historyEntry) {
        return getString(R.string.satnet_maps_history_row,
                historyEntry.displayName,
                historyEntry.exportLabel,
                historyEntry.bookmarkCount,
                getSecurityModeLabel(historyEntry.securityMode),
                getSignerLabel(historyEntry.signed, historyEntry.signerKeyId))
                + buildRoleSuffix(historyEntry.roleSummary);
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

    private String requirePassphrase() {
        String passphrase = passphraseInput.getText().toString().trim();
        if (passphrase.length() < 6) {
            showInlineError(R.string.satnet_maps_passphrase_required);
            return null;
        }
        clearInlineError();
        return passphrase;
    }

    private String fallbackText(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value.trim();
    }

    private String getSecurityModeLabel(String securityMode) {
        return "signed_encrypted".equalsIgnoreCase(securityMode)
                ? getString(R.string.satnet_maps_security_mode_signed)
                : getString(R.string.satnet_maps_security_mode_encrypted);
    }

    private String getSignerLabel(boolean signed, String signerKeyId) {
        if (!signed) {
            return getString(R.string.satnet_maps_signer_none);
        }
        return TextUtils.isEmpty(signerKeyId)
                ? getString(R.string.satnet_maps_signature_status_present)
                : signerKeyId.trim();
    }

    private String buildRoleSuffix(String roleSummary) {
        return TextUtils.isEmpty(roleSummary)
                ? ""
                : "\nRoles: " + roleSummary.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

