package org.servalproject.satnet.maps;

import android.graphics.Color;

import java.util.Locale;

public final class SatnetPeerTrustSnapshot {
    public static final SatnetPeerTrustSnapshot EMPTY = new SatnetPeerTrustSnapshot(0, 0, 0, 0, 0, 0, 0L);

    public final int trustedAuditCount;
    public final int cautionAuditCount;
    public final int rotationAlertCount;
    public final int meshEvidenceCount;
    public final int localEvidenceCount;
    public final int auditedVoucherCount;
    public final long latestAuditTime;

    public SatnetPeerTrustSnapshot(int trustedAuditCount,
                                   int cautionAuditCount,
                                   int rotationAlertCount,
                                   int meshEvidenceCount,
                                   int localEvidenceCount,
                                   int auditedVoucherCount,
                                   long latestAuditTime) {
        this.trustedAuditCount = Math.max(0, trustedAuditCount);
        this.cautionAuditCount = Math.max(0, cautionAuditCount);
        this.rotationAlertCount = Math.max(0, rotationAlertCount);
        this.meshEvidenceCount = Math.max(0, meshEvidenceCount);
        this.localEvidenceCount = Math.max(0, localEvidenceCount);
        this.auditedVoucherCount = Math.max(0, auditedVoucherCount);
        this.latestAuditTime = Math.max(0L, latestAuditTime);
    }

    public boolean hasEvidence() {
        return trustedAuditCount > 0
                || cautionAuditCount > 0
                || rotationAlertCount > 0
                || meshEvidenceCount > 0
                || localEvidenceCount > 0
                || auditedVoucherCount > 0
                || latestAuditTime > 0L;
    }

    public int getOverlayColor() {
        if (cautionAuditCount > 0 || rotationAlertCount > 0) {
            return Color.parseColor("#EF6C00");
        }
        if (trustedAuditCount > 0) {
            return Color.parseColor("#2E7D32");
        }
        return Color.parseColor("#546E7A");
    }

    public String getCompactSummary() {
        if (!hasEvidence()) {
            return "No verifier trust evidence";
        }
        return String.format(Locale.US,
                "Trust %d verified · %d caution · %d rotation · %d mesh/%d local · %d vouchers",
                trustedAuditCount,
                cautionAuditCount,
                rotationAlertCount,
                meshEvidenceCount,
                localEvidenceCount,
                auditedVoucherCount);
    }
}

