# Extended Range Calling - Multi-Hop & Internet Relay

## Overview

SATNET now supports **extended range calling** that goes beyond direct Wi-Fi connections. You can now make calls to:
- **Nearby devices** (within Wi-Fi range) - Direct 1-hop connection
- **Intermediate devices** (2-5 hops away) - Through mesh routing
- **Faraway devices** (outside mesh range) - Via internet relay servers
- **Hybrid paths** - Combination of mesh and internet

## Calling Modes

### 1. Direct Calling (Nearby)
**Range:** ~50-100 meters  
**Latency:** < 50ms  
**Requires:** Wi-Fi direct connection

Traditional SATNET calling mode where devices are within direct Wi-Fi range of each other.

### 2. Multi-Hop Mesh Calling (Intermediate)
**Range:** Up to 500 meters (5 hops × 100m each)  
**Latency:** 50-250ms  
**Requires:** Intermediate mesh peers

Calls are routed through multiple intermediate devices to reach faraway peers within the mesh network.

**Example:**
```
Device A → Device B → Device C → Device D
(You)     (Hop 1)    (Hop 2)    (Destination)
```

Each intermediate device forwards your call packets to the next hop until reaching the destination.

### 3. Internet Relay Calling (Faraway)
**Range:** Unlimited (worldwide)  
**Latency:** 100-500ms  
**Requires:** Internet connection on at least one device

When devices cannot reach each other via mesh, they can use an internet relay server as an intermediary.

**Example:**
```
Device A → Internet → Relay Server → Internet → Device Z
(You, has WiFi)                                  (Friend, has mobile data)
```

### 4. Hybrid Calling (Mixed)
**Range:** Unlimited  
**Latency:** Varies  
**Requires:** Mesh + Internet

Combines mesh routing with internet relay for optimal connectivity.

**Example:**
```
Device A → Device B → Internet Gateway → Relay → Device Z
(You)     (Mesh)     (Has internet)              (Faraway friend)
```

## Technical Architecture

### Multi-Hop Routing

**Algorithm:** Breadth-First Search (BFS)  
**Maximum Hops:** 5  
**Route Caching:** 30 seconds  
**Auto-Discovery:** Continuous

The `MultiHopRoutingManager` automatically discovers and maintains routes to all reachable peers:

```java
RouteInfo route = routingManager.findBestRoute(destinationId);
// Returns: DIRECT, MULTI_HOP, INTERNET_RELAY, or HYBRID
```

**Route Selection Priority:**
1. **Direct** - Always preferred if available
2. **Multi-Hop** - Next choice if < 5 hops
3. **Internet Relay** - Used when mesh unavailable
4. **Hybrid** - When destination needs relay but you need mesh to reach internet gateway

### Internet Relay Protocol

**Protocol:** Custom TCP-based relay protocol  
**Default Server:** relay.satnet.app:4110  
**Encryption:** End-to-end (SATNET mesh layer)  
**Authentication:** SATNET Identity (SID)

The `InternetRelayClient` manages connections to relay servers:

```java
relayClient.connect();  // Connect to relay
relayClient.establishSession(peerId);  // Setup session
relayClient.sendData(peerId, audioData);  // Send call data
```

**Relay Features:**
- Automatic reconnection
- Session management
- NAT traversal
- Bandwidth optimization
- Multi-device support

## Configuration

### Enable/Disable Relay

By default, relay is automatically used when needed. To configure:

**Settings → Network → Internet Relay**
- Enable/Disable relay feature
- Configure custom relay server
- View active sessions

### Custom Relay Server

You can run your own relay server or use a community server:

```java
InternetRelayClient.getInstance().configureRelay(
    "my-relay.example.com",  // hostname
    4110                      // port
);
```

### Route Preferences

Configure routing behavior in settings:

- **Prefer Mesh:** Use mesh even if slower than relay
- **Prefer Relay:** Use relay for faster connections
- **Automatic:** Let system choose best route (default)

## Usage

### Making Extended Range Calls

**From User Perspective:**
1. Open SATNET and go to **Call** screen
2. Select contact from peer list
3. System automatically finds best route
4. Call connects via optimal path
5. Route indicator shows connection type

**Route Indicators:**
- 📡 **Direct** - Green indicator, 1 hop
- 🔀 **Multi-Hop** - Yellow indicator, shows hop count
- 🌐 **Relay** - Blue indicator, via internet
- 🔄 **Hybrid** - Purple indicator, mixed path

### Monitoring Call Routes

During active call, view routing information:

**In-Call Menu → Route Info**
- Current route type
- Hop count
- Latency estimate
- Bandwidth usage
- Fallback options

### Route Failover

If the current route fails, system automatically tries alternatives:

1. **Route becomes unavailable**
2. Call quality degrades or connection lost
3. System discovers alternative routes
4. Seamlessly switches to backup route
5. Call continues with minimal interruption

## Bandwidth & Quality

### Bandwidth Requirements

| Route Type | Audio Only | Video + Audio |
|------------|-----------|---------------|
| Direct (1 hop) | 20 kbps | 520 kbps |
| Multi-Hop (2-3 hops) | 25 kbps | 600 kbps |
| Multi-Hop (4-5 hops) | 30 kbps | 700 kbps |
| Internet Relay | 30 kbps | 600 kbps |
| Hybrid | 35 kbps | 700 kbps |

**Note:** Each hop adds ~5-10 kbps overhead for routing headers.

### Quality Expectations

| Route Type | Latency | Quality | Best For |
|------------|---------|---------|----------|
| Direct | < 50ms | Excellent | Normal calls |
| 2-3 Hops | 50-150ms | Good | Extended mesh |
| 4-5 Hops | 150-250ms | Fair | Large mesh networks |
| Relay | 100-500ms | Good | Long distance |
| Hybrid | 150-600ms | Fair-Good | Global reach |

## Implementation Details

### Route Discovery Process

1. **Check Direct Connectivity**
   - Query peer list for direct connections
   - Ping test to verify reachability
   - Measure latency

2. **Search Mesh Network**
   - Broadcast route discovery packets
   - Collect responses from intermediate peers
   - Build routing table with hop counts

3. **Query Relay Server**
   - Check if peer is registered with relay
   - Verify relay connectivity
   - Measure relay latency

4. **Select Best Route**
   - Compare all available routes
   - Consider latency, hop count, bandwidth
   - Cache selected route for reuse

### Packet Routing

**Multi-Hop:**
```
[Source SID | Dest SID | Hop Count | TTL | Payload]
```

Each hop:
- Decrements TTL (Time To Live)
- Increments hop count
- Forwards to next peer
- Updates routing table

**Relay:**
```
[Session ID | Source SID | Dest SID | Data Length | Payload]
```

Relay server:
- Maintains session mappings
- Forwards packets between peers
- Handles NAT traversal
- Manages bandwidth allocation

## Security & Privacy

### End-to-End Encryption

All call data is encrypted end-to-end by the upstream Serval DNA layer, regardless of route:
- Relay servers **cannot** decrypt call content
- Intermediate mesh peers **cannot** decrypt call content
- Only sender and receiver have encryption keys

### Relay Server Trust

**What relay servers can see:**
- Source and destination SIDs (encrypted identities)
- Packet sizes and timing
- Connection metadata

**What relay servers cannot see:**
- Call content (encrypted)
- Actual identities (only SIDs)
- Contact lists or private data

**Best Practices:**
- Run your own relay server for maximum privacy
- Use mesh routes when possible
- Enable relay only when necessary

## Troubleshooting

### Call Won't Connect

**Check:**
1. Is peer online? (Check peer list)
2. Try different route type manually
3. Verify relay server is reachable
4. Check firewall/NAT settings
5. Ensure sufficient bandwidth

### Poor Call Quality

**Possible causes:**
- Too many hops (> 3)
- Congested relay server
- Weak Wi-Fi signals
- Bandwidth limitations

**Solutions:**
- Move closer to reduce hops
- Try different relay server
- Disable video (audio-only uses less bandwidth)
- Switch to better Wi-Fi network

### Route Discovery Fails

**Debug steps:**
1. View routing stats: Settings → Network → Routing Info
2. Check peer reachability status
3. Verify relay connection: Settings → Network → Relay Status
4. Force route rediscovery: In-Call Menu → Refresh Route

## Performance Optimization

### For Developers

**Optimize Route Discovery:**
```java
// Update peer reachability frequently
routingManager.updatePeerReachability(peerId, isDirect, hopCount);

// Invalidate stale routes
routingManager.invalidateRoute(peerId);

// Monitor routing stats
String stats = routingManager.getRoutingStats();
Log.d(TAG, "Routing: " + stats);
```

**Optimize Relay Usage:**
```java
// Check before establishing session
if (relayClient.isPeerReachable(peerId)) {
    relayClient.establishSession(peerId);
}

// Close unused sessions
relayClient.closeSession(peerId);
```

### For Users

- **Keep mesh connected**: More peers = better routing
- **Stable internet**: For relay calls, use Wi-Fi over mobile data
- **Close unused apps**: Free bandwidth for calls
- **Update regularly**: Get latest routing improvements

## Future Enhancements

Planned improvements:
- ✨ Quality of Service (QoS) routing
- ✨ Load balancing across multiple paths
- ✨ Multipath routing for redundancy
- ✨ Geographic routing optimization
- ✨ Mesh topology visualization
- ✨ Predictive route pre-establishment
- ✨ Peer-to-peer relay (no central server)
- ✨ Satellite link support

## Example Scenarios

### Scenario 1: Campus Network
```
Building A ←→ Building B ←→ Building C
(You)         (2 hops)      (Friend)
```
**Route:** Multi-hop mesh (3 hops)  
**Quality:** Excellent

### Scenario 2: City-Wide Network
```
Home ←→ Neighbor ←→ Community Hub → Internet → Relay → Friend (across town with mobile data)
```
**Route:** Hybrid (mesh to internet gateway, then relay)  
**Quality:** Good

### Scenario 3: International Call
```
USA (You, has WiFi) → Internet → Relay Server → Internet → Australia (Friend, has WiFi)
```
**Route:** Internet relay only  
**Quality:** Good (depends on relay location)

---

**Last Updated:** November 2, 2025  
**Feature Status:** Production Ready  
**Minimum Android Version:** 4.3 (API 18)

