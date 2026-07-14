package org.servalproject.routing;

import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.features.FeatureFlags;
import org.servalproject.relay.CensorshipResistantRelay;
import org.servalproject.relay.InternetRelayClient;
import org.servalproject.relay.SmsRelayClient;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.servald.Peer;
import org.servalproject.servald.PeerListService;
import org.servalproject.servaldna.SubscriberId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Multi-hop routing manager for calls beyond direct Wi-Fi range
 * Enables communication with faraway devices through intermediate peers
 */
public class MultiHopRoutingManager {
    private static final String TAG = "MultiHopRouting";

    // Maximum number of hops allowed for a call
    private static final int MAX_HOPS = 5;

    // Route cache: destination -> list of intermediate peers
    private Map<SubscriberId, RouteInfo> routeCache;

    // Peer reachability status
    private Map<SubscriberId, PeerReachability> reachabilityMap;

    public enum RouteType {
        DIRECT,                    // Direct Wi-Fi connection (1 hop)
        MULTI_HOP,                 // Through intermediate mesh peers (2-5 hops)
        INTERNET_RELAY,            // Through internet relay server
        HYBRID,                    // Combination of mesh + internet
        SMS_RELAY,                 // Through SMS/cellular relay (censorship-resistant)
        TOR_RELAY,                 // Through Tor hidden service (censorship-resistant)
        I2P_RELAY,                 // Through I2P eepsite (censorship-resistant)
        SNEAKERNET                 // Store-and-forward via Rhizome (delay-tolerant)
    }

    public static class RouteInfo {
        public RouteType type;
        public List<SubscriberId> hops;
        public int hopCount;
        public long latencyMs;
        public boolean isActive;
        public long lastUpdated;

        public RouteInfo(RouteType type, List<SubscriberId> hops) {
            this.type = type;
            this.hops = hops;
            this.hopCount = hops.size();
            this.isActive = true;
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    public static class PeerReachability {
        public boolean isDirect;           // Within Wi-Fi range
        public boolean isMultiHop;         // Reachable via mesh
        public boolean isInternetRelay;    // Reachable via internet
        public int hopCount;
        public long lastSeen;

        public PeerReachability() {
            this.isDirect = false;
            this.isMultiHop = false;
            this.isInternetRelay = false;
            this.hopCount = 0;
            this.lastSeen = System.currentTimeMillis();
        }
    }

    private static MultiHopRoutingManager instance;

    public static synchronized MultiHopRoutingManager getInstance() {
        if (instance == null) {
            instance = new MultiHopRoutingManager();
        }
        return instance;
    }

    private MultiHopRoutingManager() {
        routeCache = new HashMap<>();
        reachabilityMap = new HashMap<>();
    }

    /**
     * Find the best route to a destination peer
     */
    public RouteInfo findBestRoute(SubscriberId destination) {
        if (!FeatureFlags.isExperimentalRoutingEnabled()) {
            return null;
        }

        // Check if we have a cached route
        RouteInfo cachedRoute = routeCache.get(destination);
        if (cachedRoute != null && isRouteValid(cachedRoute)) {
            Log.d(TAG, "Using cached route to " + destination + " (" + cachedRoute.type + ")");
            return cachedRoute;
        }

        // Try to find a new route
        RouteInfo newRoute = discoverRoute(destination);
        if (newRoute != null) {
            routeCache.put(destination, newRoute);
            Log.i(TAG, "Found route to " + destination + " via " + newRoute.type +
                      " (" + newRoute.hopCount + " hops)");
        } else {
            Log.w(TAG, "No route found to " + destination);
        }

        return newRoute;
    }

    /**
     * Discover a route to the destination using multiple strategies
     */
    private RouteInfo discoverRoute(SubscriberId destination) {
        if (!FeatureFlags.isExperimentalRoutingEnabled()) {
            return null;
        }

        // Strategy 1: Check direct connectivity (highest priority)
        RouteInfo directRoute = findDirectRoute(destination);
        if (directRoute != null) {
            return directRoute;
        }

        // Strategy 2: Check multi-hop mesh route
        RouteInfo meshRoute = findMultiHopMeshRoute(destination);
        if (meshRoute != null) {
            return meshRoute;
        }

        if (FeatureFlags.isRelayEnabled()) {
            // Strategy 3: Check internet relay route
            RouteInfo relayRoute = findInternetRelayRoute(destination);
            if (relayRoute != null) {
                return relayRoute;
            }

            // Strategy 4: Check hybrid route (mesh + internet)
            RouteInfo hybridRoute = findHybridRoute(destination);
            if (hybridRoute != null) {
                return hybridRoute;
            }

            // Strategy 5: Check SMS relay (cellular, works when internet censored)
            RouteInfo smsRoute = findSmsRelayRoute(destination);
            if (smsRoute != null) {
                return smsRoute;
            }

            // Strategy 6: Check Tor relay (censorship-resistant internet)
            RouteInfo torRoute = findTorRelayRoute(destination);
            if (torRoute != null) {
                return torRoute;
            }

            // Strategy 7: Check I2P relay (censorship-resistant internet)
            RouteInfo i2pRoute = findI2PRelayRoute(destination);
            if (i2pRoute != null) {
                return i2pRoute;
            }
        }

        // Strategy 8: Fallback to sneakernet (store-and-forward via Rhizome)
        RouteInfo sneakernetRoute = findSneakernetRoute(destination);
        return sneakernetRoute;
    }

    /**
     * Find SMS relay route (works when internet is censored)
     */
    private RouteInfo findSmsRelayRoute(SubscriberId destination) {
        // Check if SMS relay is available
        if (isSmsRelayAvailable(destination)) {
            List<SubscriberId> hops = new ArrayList<>();
            hops.add(destination);
            return new RouteInfo(RouteType.SMS_RELAY, hops);
        }
        return null;
    }

    /**
     * Find Tor hidden service relay route
     */
    private RouteInfo findTorRelayRoute(SubscriberId destination) {
        // Check if Tor relay is available
        if (isTorRelayAvailable(destination)) {
            List<SubscriberId> hops = new ArrayList<>();
            hops.add(destination);
            return new RouteInfo(RouteType.TOR_RELAY, hops);
        }
        return null;
    }

    /**
     * Find I2P eepsite relay route
     */
    private RouteInfo findI2PRelayRoute(SubscriberId destination) {
        // Check if I2P relay is available
        if (isI2PRelayAvailable(destination)) {
            List<SubscriberId> hops = new ArrayList<>();
            hops.add(destination);
            return new RouteInfo(RouteType.I2P_RELAY, hops);
        }
        return null;
    }

    /**
     * Find sneakernet route (store-and-forward via Rhizome)
     */
    private RouteInfo findSneakernetRoute(SubscriberId destination) {
        if (!ServalBatPhoneApplication.context.isRhizomeRuntimeReady()) {
            return null;
        }
        List<SubscriberId> hops = new ArrayList<>();
        hops.add(destination);
        RouteInfo route = new RouteInfo(RouteType.SNEAKERNET, hops);
        route.latencyMs = 300000; // 5 minutes typical delay
        return route;
    }

    /**
     * Find direct Wi-Fi route (1 hop)
     */
    private RouteInfo findDirectRoute(SubscriberId destination) {
        PeerReachability reach = reachabilityMap.get(destination);
        if (reach != null && reach.isDirect) {
            List<SubscriberId> hops = new ArrayList<>();
            hops.add(destination);
            return new RouteInfo(RouteType.DIRECT, hops);
        }
        return null;
    }

    /**
     * Find multi-hop mesh route through intermediate peers
     */
    private RouteInfo findMultiHopMeshRoute(SubscriberId destination) {
        // Use breadth-first search to find shortest path
        List<SubscriberId> path = findShortestPath(destination);
        if (path != null && path.size() <= MAX_HOPS) {
            return new RouteInfo(RouteType.MULTI_HOP, path);
        }
        return null;
    }

    /**
     * Find route through internet relay server
     */
    private RouteInfo findInternetRelayRoute(SubscriberId destination) {
        // Check if peer is registered with relay server
        if (isPeerRegisteredWithRelay(destination)) {
            List<SubscriberId> hops = new ArrayList<>();
            hops.add(destination);
            return new RouteInfo(RouteType.INTERNET_RELAY, hops);
        }
        return null;
    }

    /**
     * Find hybrid route (mesh to internet gateway, then relay)
     */
    private RouteInfo findHybridRoute(SubscriberId destination) {
        // Find nearest internet gateway in mesh
        SubscriberId gateway = findNearestInternetGateway();
        if (gateway != null && isPeerRegisteredWithRelay(destination)) {
            List<SubscriberId> hops = new ArrayList<>();
            // Path to gateway
            List<SubscriberId> pathToGateway = findShortestPath(gateway);
            if (pathToGateway != null) {
                hops.addAll(pathToGateway);
                hops.add(destination);
                return new RouteInfo(RouteType.HYBRID, hops);
            }
        }
        return null;
    }

    /**
     * Breadth-first search to find shortest path
     */
    private List<SubscriberId> findShortestPath(SubscriberId destination) {
        Peer peer = PeerListService.peers.get(destination);
        if (peer == null || !peer.isReachable()) {
            return null;
        }
        List<SubscriberId> reversed = new ArrayList<>();
        Set<SubscriberId> seen = new HashSet<>();
        Peer current = peer;
        while (current != null && current.getSubscriberId() != null && seen.add(current.getSubscriberId())) {
            reversed.add(current.getSubscriberId());
            SubscriberId transmitter = current.getTransmitter();
            if (transmitter == null) {
                break;
            }
            current = PeerListService.peers.get(transmitter);
        }
        if (reversed.isEmpty()) {
            return null;
        }
        List<SubscriberId> path = new ArrayList<>();
        for (int i = reversed.size() - 1; i >= 0; i--) {
            path.add(reversed.get(i));
        }
        return path;
    }

    /**
     * Check if route is still valid
     */
    private boolean isRouteValid(RouteInfo route) {
        // Route expires after 30 seconds
        long now = System.currentTimeMillis();
        if (now - route.lastUpdated > 30000) {
            return false;
        }
        return route.isActive;
    }

    /**
     * Update peer reachability information
     */
    public void updatePeerReachability(SubscriberId peerId, boolean isDirect, int hopCount) {
        PeerReachability reach = reachabilityMap.get(peerId);
        if (reach == null) {
            reach = new PeerReachability();
            reachabilityMap.put(peerId, reach);
        }

        reach.isDirect = isDirect;
        reach.isMultiHop = (hopCount > 1 && hopCount <= MAX_HOPS);
        reach.hopCount = hopCount;
        reach.lastSeen = System.currentTimeMillis();

        Log.d(TAG, "Updated reachability for " + peerId + ": direct=" + isDirect +
                  ", hops=" + hopCount);
    }

    /**
     * Mark a peer as reachable via internet relay
     */
    public void markPeerAsRelayReachable(SubscriberId peerId, boolean reachable) {
        PeerReachability reach = reachabilityMap.get(peerId);
        if (reach == null) {
            reach = new PeerReachability();
            reachabilityMap.put(peerId, reach);
        }
        reach.isInternetRelay = reachable;
    }

    /**
     * Invalidate route (when hop becomes unavailable)
     */
    public void invalidateRoute(SubscriberId destination) {
        RouteInfo route = routeCache.get(destination);
        if (route != null) {
            route.isActive = false;
            Log.d(TAG, "Invalidated route to " + destination);
        }
    }

    /**
     * Clear all routes
     */
    public void clearRoutes() {
        routeCache.clear();
        Log.d(TAG, "Cleared all routes");
    }

    /**
     * Get statistics about current routing
     */
    public String getRoutingStats() {
        int directPeers = 0;
        int multiHopPeers = 0;
        int relayPeers = 0;

        for (PeerReachability reach : reachabilityMap.values()) {
            if (reach.isDirect) directPeers++;
            if (reach.isMultiHop) multiHopPeers++;
            if (reach.isInternetRelay) relayPeers++;
        }

        return String.format(Locale.ROOT, "Direct: %d, Multi-hop: %d, Relay: %d, Routes cached: %d",
                           directPeers, multiHopPeers, relayPeers, routeCache.size());
    }

    /**
     * Check if peer is reachable by any means
     */
    public boolean isPeerReachable(SubscriberId peerId) {
        PeerReachability reach = reachabilityMap.get(peerId);
        if (reach == null) {
            return false;
        }
        return reach.isDirect || reach.isMultiHop || reach.isInternetRelay;
    }

    /**
     * Get hop count to peer (returns -1 if unreachable)
     */
    public int getHopCount(SubscriberId peerId) {
        PeerReachability reach = reachabilityMap.get(peerId);
        if (reach == null || !isPeerReachable(peerId)) {
            return -1;
        }
        return reach.hopCount;
    }

    // Helper methods (stubs for now - will integrate with Serval DNA)

    private boolean isPeerRegisteredWithRelay(SubscriberId peerId) {
        if (!FeatureFlags.isRelayEnabled() || peerId == null) {
            return false;
        }
        String host = ServalBatPhoneApplication.context.settings.getString("relay_host", "");
        if (host != null && host.trim().length() > 0) {
            return true;
        }
        return ServalBatPhoneApplication.context.relayServer != null || InternetRelayClient.getInstance().isConnected();
    }

    private SubscriberId findNearestInternetGateway() {
        for (Peer peer : PeerListService.peers.values()) {
            if (peer != null && peer.isReachable() && peer.getHopCount() > 0 && peer.getHopCount() < MAX_HOPS) {
                return peer.getSubscriberId();
            }
        }
        return null;
    }

    private boolean isSmsRelayAvailable(SubscriberId peerId) {
        return SmsRelayClient.getInstance(ServalBatPhoneApplication.context).isAvailable();
    }

    private boolean isTorRelayAvailable(SubscriberId peerId) {
        return CensorshipResistantRelay.getInstance(ServalBatPhoneApplication.context).isTorAvailable();
    }

    private boolean isI2PRelayAvailable(SubscriberId peerId) {
        return CensorshipResistantRelay.getInstance(ServalBatPhoneApplication.context).isI2PAvailable();
    }
}

