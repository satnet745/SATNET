package org.servalproject.satnet.maps;

import java.util.Locale;

public final class SatnetMeshOverlaySnapshot {
    public static final SatnetMeshOverlaySnapshot EMPTY = new SatnetMeshOverlaySnapshot(0, 0, 0, 0, 0d);

    public final int reachablePeerCount;
    public final int directPeerCount;
    public final int relayedPeerCount;
    public final int maxHopCount;
    public final double averageHopCount;

    public SatnetMeshOverlaySnapshot(int reachablePeerCount,
                                     int directPeerCount,
                                     int relayedPeerCount,
                                     int maxHopCount,
                                     double averageHopCount) {
        this.reachablePeerCount = Math.max(0, reachablePeerCount);
        this.directPeerCount = Math.max(0, directPeerCount);
        this.relayedPeerCount = Math.max(0, relayedPeerCount);
        this.maxHopCount = Math.max(0, maxHopCount);
        this.averageHopCount = averageHopCount < 0d ? 0d : averageHopCount;
    }

    public boolean hasCoverage() {
        return reachablePeerCount > 0;
    }

    public String getCompactSummary() {
        if (!hasCoverage()) {
            return "No reachable peers";
        }
        return String.format(Locale.US,
                "%d peers · %d direct · %d relayed · avg %.1f hops",
                reachablePeerCount,
                directPeerCount,
                relayedPeerCount,
                averageHopCount);
    }
}

