package org.servalproject.satnet.verifier;

import android.database.Cursor;

import org.servalproject.satnet.SatnetRuntimeConfig;
import org.servalproject.voucher.BitcoinVoucher;
import org.servalproject.voucher.VoucherLedger;
import org.servalproject.voucher.VoucherSecondSignatureManifest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class SettlementVerifier {
	private SettlementVerifier() {
	}

	public static String getVerifierWindowSummary() {
		return SatnetRuntimeConfig.getSettlementWindowHours() + "h verifier release window";
	}

	public static int releaseExpiredSettlementWindows(VoucherLedger voucherLedger, long nowMs) {
		if (voucherLedger == null) {
			return 0;
		}
		Cursor expired = null;
		int released = 0;
		try {
			expired = voucherLedger.getExpiredSettlementWindows(nowMs);
			while (expired.moveToNext()) {
				String voucherId = expired.getString(expired.getColumnIndexOrThrow("voucher_id"));
				voucherLedger.markSettlementVerified(voucherId);
				released++;
			}
		} finally {
			if (expired != null) {
				expired.close();
			}
		}
		return released;
	}

	public static WorkflowMetadataCheck inspectVoucherMetadata(BitcoinVoucher voucher, VoucherLedger voucherLedger) {
		boolean rotationDetected = hasRotation(voucher);
		if (voucher == null) {
			return WorkflowMetadataCheck.failure("Voucher metadata unavailable", null, null, false, false, false);
		}
		VoucherSecondSignatureManifest manifest = voucher.getSecondSignatureManifest();
		if (manifest == null) {
			return WorkflowMetadataCheck.failure("Voucher detached manifest missing", null, null, false, false, rotationDetected);
		}
		VoucherSecondSignatureManifest.ValidationResult manifestValidation = manifest.validateStructure();
		if (!manifestValidation.isValid) {
			return WorkflowMetadataCheck.failure(manifestValidation.message, manifest, null, false, false, rotationDetected);
		}
		if (voucher.getIssuerKeyId() != null && !voucher.getIssuerKeyId().trim().isEmpty()
				&& !voucher.getIssuerKeyId().equalsIgnoreCase(manifest.getPrimaryIssuerKeyId())) {
			return WorkflowMetadataCheck.failure("Voucher issuer key does not match detached manifest", manifest, null, false, false, rotationDetected);
		}
		if (!sameText(voucher.getIssuerKeystoreAlias(), manifest.getIssuerKeystoreAlias())) {
			return WorkflowMetadataCheck.failure("Voucher issuer alias does not match detached manifest", manifest, null, false, false, rotationDetected);
		}
		if (voucher.getIssuerRotationEpoch() != manifest.getRotationEpoch()) {
			return WorkflowMetadataCheck.failure("Voucher rotation epoch does not match detached manifest", manifest, null, false, false, rotationDetected);
		}
		if (!sameText(voucher.getIssuerPreviousKeystoreAlias(), manifest.getPreviousIssuerKeystoreAlias())) {
			return WorkflowMetadataCheck.failure("Voucher previous issuer alias does not match detached manifest", manifest, null, false, false, rotationDetected);
		}
		if (!sameText(voucher.getIssuerRotationReason(), manifest.getRotationReason())) {
			return WorkflowMetadataCheck.failure("Voucher rotation reason does not match detached manifest", manifest, null, false, false, rotationDetected);
		}
		VoucherLedger.VoucherMetadataSnapshot snapshot = voucherLedger == null
				? null
				: voucherLedger.getVoucherMetadataSnapshot(voucher.getVoucherId());
		boolean ledgerMatched = snapshot != null && snapshot.hasPhase3Metadata();
		if (snapshot != null && snapshot.hasPhase3Metadata()) {
			String mismatch = compareSnapshot(voucher, snapshot);
			if (mismatch != null) {
				return WorkflowMetadataCheck.failure(mismatch, manifest, snapshot, true, false, rotationDetected);
			}
		}
		return WorkflowMetadataCheck.success(buildVoucherMetadataSummary(voucher), manifest, snapshot, true, ledgerMatched, rotationDetected);
	}

	public static boolean requiresPayloadInspection(VoucherLedger.VoucherMetadataSnapshot snapshot) {
		return snapshot != null && snapshot.hasPhase3Metadata();
	}

	public static String buildVoucherMetadataSummary(BitcoinVoucher voucher) {
		if (voucher == null) {
			return "Issuer alias: unavailable";
		}
		String alias = valueOrDefault(voucher.getIssuerKeystoreAlias(), "n/a");
		String previousAlias = valueOrDefault(voucher.getIssuerPreviousKeystoreAlias(), "none");
		String rotationReason = valueOrDefault(voucher.getIssuerRotationReason(), "active");
		String publicKeyRef = valueOrDefault(voucher.getSecondSignaturePublicKeyReference(), "n/a");
		String metadataRef = valueOrDefault(voucher.getSecondSignatureMetadataReference(), "n/a");
		String signatureRef = valueOrDefault(voucher.getSecondSignatureReference(), "n/a");
		return "Issuer alias: " + alias
				+ "\nRotation epoch: " + voucher.getIssuerRotationEpoch()
				+ "\nRotation reason: " + rotationReason
				+ "\nPrevious alias: " + previousAlias
				+ "\nDetached public-key ref: " + publicKeyRef
				+ "\nDetached metadata ref: " + metadataRef
				+ "\nDetached signature ref: " + signatureRef;
	}

	public static TrustBadgeState buildTrustBadgeState(WorkflowMetadataCheck check,
			VoucherLedger.VerifierAuditSnapshot auditSnapshot) {
		boolean manifestVerified = auditSnapshot != null && auditSnapshot.hasAudit()
				? auditSnapshot.isManifestVerified()
				: check != null && check.manifestVerified;
		boolean ledgerMatched = auditSnapshot != null && auditSnapshot.hasAudit()
				? auditSnapshot.isLedgerMatched()
				: check != null && check.ledgerMatched;
		boolean rotationDetected = auditSnapshot != null && auditSnapshot.hasAudit()
				? auditSnapshot.isRotationDetected()
				: check != null && check.rotationDetected;
		boolean auditAvailable = auditSnapshot != null && auditSnapshot.hasAudit();
		return new TrustBadgeState(
				manifestVerified ? "Manifest verified" : (auditAvailable ? "Manifest failed" : "Manifest pending"),
				ledgerMatched ? "Ledger matched" : (auditAvailable ? "Ledger mismatch" : "Ledger pending"),
				rotationDetected ? "Rotation detected" : "No rotation detected",
				manifestVerified,
				ledgerMatched,
				rotationDetected);
	}

	public static String buildAuditHistorySummary(List<VoucherLedger.VerifierAuditRecord> auditHistory, int maxRecords) {
		if (auditHistory == null || auditHistory.isEmpty()) {
			return "No verifier audit history available yet.";
		}
		SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
		int limit = maxRecords <= 0 ? auditHistory.size() : Math.min(maxRecords, auditHistory.size());
		StringBuilder builder = new StringBuilder();
		for (int i = auditHistory.size() - 1, rendered = 0; i >= 0 && rendered < limit; i--, rendered++) {
			VoucherLedger.VerifierAuditRecord record = auditHistory.get(i);
			if (builder.length() > 0) {
				builder.append("\n\n");
			}
			builder.append("• ")
					.append(timeFormat.format(new Date(record.auditTime)))
					.append(" · ")
					.append(formatInspectionSource(record.inspectionSource))
					.append(" · ")
					.append(buildAuditProvenance(record))
					.append("\n  Outcome: ")
					.append(buildAuditOutcome(record));
			String auditMessage = valueOrDefault(record.auditMessage, "");
			if (!auditMessage.isEmpty()) {
				builder.append("\n  Note: ").append(auditMessage);
			}
		}
		return builder.toString();
	}

	private static boolean hasRotation(BitcoinVoucher voucher) {
		if (voucher == null) {
			return false;
		}
		return voucher.getIssuerRotationEpoch() > 0L
				|| (voucher.getIssuerPreviousKeystoreAlias() != null && !voucher.getIssuerPreviousKeystoreAlias().trim().isEmpty())
				|| (voucher.getIssuerRotationReason() != null && !"active".equalsIgnoreCase(voucher.getIssuerRotationReason()));
	}

	private static String compareSnapshot(BitcoinVoucher voucher, VoucherLedger.VoucherMetadataSnapshot snapshot) {
		if (!sameText(voucher.getIssuerKeyId(), snapshot.primaryIssuerKeyId)) {
			return "Voucher issuer key does not match stored ledger metadata";
		}
		if (!sameText(voucher.getSecondSignatureManifestJson(), snapshot.secondSignatureManifestJson)) {
			return "Voucher detached manifest does not match stored ledger metadata";
		}
		if (!sameText(voucher.getSecondSignaturePublicKeyReference(), snapshot.secondPublicKeyReference)) {
			return "Voucher detached public-key reference does not match stored ledger metadata";
		}
		if (!sameText(voucher.getSecondSignatureMetadataReference(), snapshot.secondMetadataReference)) {
			return "Voucher detached metadata reference does not match stored ledger metadata";
		}
		if (!sameText(voucher.getSecondSignatureReference(), snapshot.secondSignatureReference)) {
			return "Voucher detached signature reference does not match stored ledger metadata";
		}
		if (!sameText(voucher.getIssuerKeystoreAlias(), snapshot.issuerKeystoreAlias)) {
			return "Voucher issuer alias does not match stored ledger metadata";
		}
		if (voucher.getIssuerRotationEpoch() != snapshot.issuerRotationEpoch) {
			return "Voucher rotation epoch does not match stored ledger metadata";
		}
		if (voucher.getIssuerActivatedAt() != snapshot.issuerActivatedAt) {
			return "Voucher issuer activation time does not match stored ledger metadata";
		}
		if (!sameText(voucher.getIssuerPreviousKeystoreAlias(), snapshot.issuerPreviousKeystoreAlias)) {
			return "Voucher previous alias does not match stored ledger metadata";
		}
		if (!sameText(voucher.getIssuerRotationReason(), snapshot.issuerRotationReason)) {
			return "Voucher rotation reason does not match stored ledger metadata";
		}
		return null;
	}

	private static boolean sameText(String left, String right) {
		return valueOrDefault(left, "").equals(valueOrDefault(right, ""));
	}

	private static String buildAuditOutcome(VoucherLedger.VerifierAuditRecord record) {
		return (record.manifestVerified ? "manifest verified" : "manifest failed")
				+ ", "
				+ (record.ledgerMatched ? "ledger matched" : "ledger mismatch")
				+ ", "
				+ (record.rotationDetected ? "rotation detected" : "no rotation detected");
	}

	private static String buildAuditProvenance(VoucherLedger.VerifierAuditRecord record) {
		String sourceNode = valueOrDefault(record.sourceNode, "");
		if (VoucherLedger.AUDIT_ORIGIN_MESH.equalsIgnoreCase(record.auditOrigin)) {
			return sourceNode.isEmpty() ? "mesh imported" : "mesh imported from " + sourceNode;
		}
		if (record.exportedToMesh) {
			return sourceNode.isEmpty() ? "local device · mesh synced" : "local device · source " + sourceNode + " · mesh synced";
		}
		return sourceNode.isEmpty() ? "local device" : "local device · source " + sourceNode;
	}

	private static String formatInspectionSource(String inspectionSource) {
		String normalized = valueOrDefault(inspectionSource, "inspection");
		return normalized.replace('_', ' ');
	}

	private static String valueOrDefault(String value, String fallback) {
		String normalized = value == null ? "" : value.trim();
		return normalized.isEmpty() ? fallback : normalized;
	}

	public static final class WorkflowMetadataCheck {
		public final boolean isValid;
		public final String message;
		public final String summary;
		public final VoucherSecondSignatureManifest manifest;
		public final VoucherLedger.VoucherMetadataSnapshot ledgerSnapshot;
		public final boolean manifestVerified;
		public final boolean ledgerMatched;
		public final boolean rotationDetected;

		private WorkflowMetadataCheck(boolean isValid,
				String message,
				String summary,
				VoucherSecondSignatureManifest manifest,
				VoucherLedger.VoucherMetadataSnapshot ledgerSnapshot,
				boolean manifestVerified,
				boolean ledgerMatched,
				boolean rotationDetected) {
			this.isValid = isValid;
			this.message = message;
			this.summary = summary;
			this.manifest = manifest;
			this.ledgerSnapshot = ledgerSnapshot;
			this.manifestVerified = manifestVerified;
			this.ledgerMatched = ledgerMatched;
			this.rotationDetected = rotationDetected;
		}

		public static WorkflowMetadataCheck success(String summary,
				VoucherSecondSignatureManifest manifest,
				VoucherLedger.VoucherMetadataSnapshot ledgerSnapshot,
				boolean manifestVerified,
				boolean ledgerMatched,
				boolean rotationDetected) {
			return new WorkflowMetadataCheck(true, "Voucher detached metadata verified", summary, manifest, ledgerSnapshot,
					manifestVerified, ledgerMatched, rotationDetected);
		}

		public static WorkflowMetadataCheck failure(String message,
				VoucherSecondSignatureManifest manifest,
				VoucherLedger.VoucherMetadataSnapshot ledgerSnapshot,
				boolean manifestVerified,
				boolean ledgerMatched,
				boolean rotationDetected) {
			return new WorkflowMetadataCheck(false, message, null, manifest, ledgerSnapshot,
					manifestVerified, ledgerMatched, rotationDetected);
		}
	}

	public static final class TrustBadgeState {
		public final String manifestBadgeText;
		public final String ledgerBadgeText;
		public final String rotationBadgeText;
		public final boolean manifestPositive;
		public final boolean ledgerPositive;
		public final boolean rotationDetected;

		TrustBadgeState(String manifestBadgeText,
				String ledgerBadgeText,
				String rotationBadgeText,
				boolean manifestPositive,
				boolean ledgerPositive,
				boolean rotationDetected) {
			this.manifestBadgeText = manifestBadgeText;
			this.ledgerBadgeText = ledgerBadgeText;
			this.rotationBadgeText = rotationBadgeText;
			this.manifestPositive = manifestPositive;
			this.ledgerPositive = ledgerPositive;
			this.rotationDetected = rotationDetected;
		}
	}
}

