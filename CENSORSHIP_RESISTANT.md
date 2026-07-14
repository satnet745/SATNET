# Censorship-Resistant Communication

## Overview

When the internet is censored, blocked, or monitored, Serval Mesh provides **multiple fallback mechanisms** to maintain faraway communication outside WiFi range:

1. **SMS/MMS Relay** - Uses cellular SMS (works without internet)
2. **Tor Hidden Services** - Anonymous, censorship-resistant internet
3. **I2P Garlic Routing** - Alternative anonymous network
4. **Store-and-Forward** - Delay-tolerant networking via Rhizome

## Why This Matters

### Censorship Scenarios
- **Government Internet Shutdowns** - Complete internet blackout
- **Deep Packet Inspection (DPI)** - Traffic analysis and blocking
- **Website/Service Blocking** - Specific sites censored
- **VPN Blocking** - VPN protocols detected and blocked
- **Port Blocking** - Specific network ports filtered
- **Throttling** - Selective bandwidth limitation

### Real-World Examples
- Political protests (internet shutdown)
- Natural disasters (infrastructure damage)
- Authoritarian regimes (systematic censorship)
- Corporate/school networks (restrictive filtering)
- War zones (targeted disruption)

---

## Solution 1: SMS Relay

### How It Works
Uses standard cellular SMS messages to relay call signaling and compressed audio when internet is unavailable.

**Advantages:**
- ✅ Works without internet
- ✅ Available on all phones
- ✅ Cellular network is harder to block
- ✅ No special software needed

**Limitations:**
- ⚠️ SMS charges apply (cost per message)
- ⚠️ Very low audio quality (~8 kbps compressed)
- ⚠️ High latency (seconds delay)
- ⚠️ Not suitable for video calls
- ⚠️ SMS can be monitored (not encrypted by carrier)

### Technical Details
- **Audio Codec:** Heavy compression to fit 140 bytes/SMS
- **Signaling:** Text SMS for call setup
- **Data Transfer:** Binary SMS for audio chunks
- **Chunking:** Automatic splitting of large messages
- **Reassembly:** Reorders chunks at destination

### Setup
1. **Configure SMS Relay Number**
   - Settings → Network → SMS Relay
   - Enter relay phone number (any Serval device)
   - Save configuration

2. **Enable SMS Relay**
   - Settings → Network → Enable SMS Relay
   - Grant SMS permissions when prompted

3. **Make Call**
   - Call as normal
   - System uses SMS if internet unavailable
   - You'll see "Using SMS Relay" notification

### Cost Considerations
- Typical: $0.10 - $0.50 per SMS depending on carrier
- Audio call: ~10-20 SMS per minute
- Estimated: $1-10 per minute
- Use only when necessary (emergencies)

---

## Solution 2: Tor Hidden Services

### How It Works
Routes traffic through Tor network's onion routing, making it anonymous and censorship-resistant.

**Advantages:**
- ✅ Bypasses censorship
- ✅ Anonymous (can't trace source)
- ✅ Encrypted end-to-end
- ✅ Works in countries blocking VPNs
- ✅ Free (no charges)

**Limitations:**
- ⚠️ Requires Orbot app
- ⚠️ Slower than direct internet (~200-500ms latency)
- ⚠️ Can be blocked if Tor bridges blocked
- ⚠️ Initial connection takes 10-30 seconds

### Technical Details
- **Routing:** 3-hop onion routing (entry → middle → exit)
- **Hidden Service:** Relay runs as .onion address
- **Encryption:** TLS over Tor (double encryption)
- **Protocol:** SOCKS5 proxy to Tor daemon
- **Anonymity:** Source and destination both anonymous

### Setup

**1. Install Orbot**
- Download from F-Droid or Google Play
- Open source, trusted by privacy advocates
- Official Tor Project app for Android

**2. Start Tor**
- Open Orbot
- Tap "Start" button
- Wait for green onion icon (connected)
- Orbot runs in background

**3. Configure Serval**
- Serval automatically detects Orbot
- Settings → Network → Tor Relay (enabled)
- Calls will route through Tor when needed

**4. Make Anonymous Call**
- Call as normal
- System uses Tor if internet censored
- "Connected via Tor (anonymous)" notification

### When Tor is Blocked

If Tor is blocked in your country:

**Use Tor Bridges:**
1. Get bridge addresses from https://bridges.torproject.org
2. Open Orbot → Settings → Bridges
3. Enable "Use Bridges"
4. Enter bridge addresses
5. Reconnect

**Bridge Types:**
- **obfs4** - Most effective, looks like random traffic
- **meek** - Uses CDN to disguise Tor
- **Snowflake** - Uses volunteer proxies

---

## Solution 3: I2P Garlic Routing

### How It Works
Alternative to Tor, uses "garlic routing" for anonymous communication through I2P network.

**Advantages:**
- ✅ Alternative when Tor blocked
- ✅ Fully distributed (no central servers)
- ✅ Better resistance to timing attacks
- ✅ Optimized for hidden services
- ✅ Free (no charges)

**Limitations:**
- ⚠️ Requires I2P Android app
- ⚠️ Slower initial setup (10-15 min for tunnels)
- ⚠️ Smaller network than Tor
- ⚠️ Higher latency than Tor

### Technical Details
- **Routing:** Garlic routing (bundle multiple messages)
- **Hidden Service:** Relay runs as .i2p eepsite
- **Encryption:** End-to-end encrypted tunnels
- **Protocol:** SOCKS5 proxy to I2P router
- **Anonymity:** Source and destination both anonymous

### Setup

**1. Install I2P**
- Download I2P Android from F-Droid
- Larger app (~30MB)
- Official I2P Project app

**2. Start I2P Router**
- Open I2P Android
- Tap "Start Router"
- Wait 10-15 minutes for tunnel building
- First launch takes longer (building address book)

**3. Configure Serval**
- Serval automatically detects I2P
- Settings → Network → I2P Relay (enabled)
- Calls will route through I2P when needed

**4. Make Anonymous Call**
- Call as normal
- System uses I2P if Tor unavailable
- "Connected via I2P (anonymous)" notification

---

## Solution 4: Store-and-Forward (Sneakernet)

### How It Works
Uses Rhizome's store-and-forward capability to deliver messages even with long delays.

**Advantages:**
- ✅ Works with zero connectivity
- ✅ No internet or cellular needed
- ✅ Highly resilient
- ✅ Free (no charges)
- ✅ Can't be blocked

**Limitations:**
- ⚠️ High latency (minutes to hours)
- ⚠️ Not suitable for real-time calls
- ⚠️ Requires intermediate carriers
- ⚠️ Audio-only (no video)

### Technical Details
- **Storage:** Rhizome bundle storage
- **Transfer:** WiFi direct, Bluetooth, or USB
- **Routing:** Epidemic routing (flood to all peers)
- **Persistence:** Bundles stored until delivered
- **Encryption:** End-to-end encrypted bundles

### Use Cases
- **Delayed messaging** when no connectivity
- **Asynchronous voice messages** (like voicemail)
- **Situations where real-time not needed**
- **Maximum censorship resistance**

### How to Use
1. Record voice message (like voicemail)
2. Message stored as Rhizome bundle
3. System automatically syncs to nearby peers
4. Bundles hop peer-to-peer until reaching destination
5. Recipient gets notification when received
6. Can reply same way

---

## Comparison Matrix

| Method | Latency | Quality | Cost | Anonymity | Censorship Resistance |
|--------|---------|---------|------|-----------|----------------------|
| **Direct WiFi** | <50ms | Excellent | Free | No | None |
| **Multi-Hop** | 100ms | Good | Free | No | Low |
| **Internet Relay** | 150ms | Good | Free | No | None |
| **SMS Relay** | 2-5s | Poor | $1-10/min | No | Medium |
| **Tor Relay** | 300ms | Good | Free | Yes | High |
| **I2P Relay** | 500ms | Fair | Free | Yes | High |
| **Sneakernet** | Minutes-Hours | Good | Free | Yes | Maximum |

---

## Route Selection Logic

System automatically selects best available route:

1. **Try Direct** - If peer nearby (highest quality)
2. **Try Multi-Hop** - If peer within 5 hops (good quality)
3. **Try Internet** - If internet available (good quality)
4. **Detect Censorship:**
   - If internet blocked/filtered
   - If VPN doesn't work
   - If relay unreachable
5. **Try Tor** - If Orbot running (anonymous)
6. **Try I2P** - If I2P running (alternative)
7. **Try SMS** - If cellular available (costly but works)
8. **Use Sneakernet** - Last resort (delayed delivery)

User can also force specific route in settings.

---

## Security & Privacy

### End-to-End Encryption
All routes maintain Serval DNA's end-to-end encryption:
- SMS relay: SMS itself not encrypted, but Serval payload is
- Tor relay: Double encrypted (Serval + Tor layers)
- I2P relay: Double encrypted (Serval + I2P layers)
- Sneakernet: Rhizome bundles encrypted

### Metadata Protection
Different routes offer different metadata protection:

| Method | Hides Source IP | Hides Destination IP | Hides Traffic Pattern |
|--------|----------------|---------------------|----------------------|
| SMS | Yes | Yes | No |
| Tor | Yes | Yes (if hidden service) | Yes |
| I2P | Yes | Yes | Yes |
| Sneakernet | Yes | Yes | Yes |

### Threat Models

**Against DPI (Deep Packet Inspection):**
- ✅ Tor (uses obfuscation)
- ✅ I2P (looks like random data)
- ✅ SMS (not IP traffic)
- ✅ Sneakernet (no network traffic)

**Against Internet Shutdown:**
- ❌ Tor (needs internet)
- ❌ I2P (needs internet)
- ✅ SMS (uses cellular)
- ✅ Sneakernet (no connectivity)

**Against Cellular Shutdown:**
- ✅ Tor (uses WiFi/data)
- ✅ I2P (uses WiFi/data)
- ❌ SMS (needs cellular)
- ✅ Sneakernet (no connectivity)

**Against Complete Shutdown (internet + cellular):**
- ❌ Tor
- ❌ I2P
- ❌ SMS
- ✅ Sneakernet (only option)

---

## Best Practices

### For Activists/Journalists

1. **Install All Tools:**
   - Orbot (Tor)
   - I2P Android
   - Keep Serval updated

2. **Test Before Crisis:**
   - Verify Tor works
   - Verify I2P works
   - Practice store-and-forward

3. **Keep Tor/I2P Preconfigured:**
   - Tor and I2P relay settings ship with sensible defaults
   - Anonymous routes are ready when censorship or filtering appears
   - The app still keeps direct, multi-hop, and internet routes first

4. **Have Backup SIM:**
   - Prepaid SIM for SMS relay
   - Register anonymously
   - Keep topped up

5. **Document Setup:**
   - Share instructions with network
   - Everyone needs same tools
   - Practice regularly

### For Emergency Response

1. **Prioritize Reliability:**
   - SMS relay for critical comms
   - Accept higher cost
   - Voice-only (no video)

2. **Use Multiple Routes:**
   - Don't rely on single method
   - Test all options
   - Have contingency plans

3. **Store-and-Forward:**
   - For non-urgent updates
   - Status reports
   - Coordination messages

### For Privacy-Conscious Users

1. **Use Anonymous Relay When Needed:**
   - Tor/I2P are preconfigured for fallback use
   - Keep direct and mesh routes available for normal conditions
   - Switch to anonymous relay when censorship or monitoring is the concern

2. **Avoid SMS:**
   - SMS not anonymous
   - Carrier can see metadata
   - Use only as last resort

3. **Use Sneakernet:**
   - For maximum privacy
   - Accept delays
   - No network exposure

---

## Troubleshooting

### Tor Not Connecting

**Problem:** Orbot installed but Serval can't connect

**Solutions:**
1. Open Orbot, verify it's running (green onion)
2. Settings → Apps → Orbot → Allow VPN
3. If in censored country, configure bridges
4. Try different bridge type (obfs4 → meek)
5. Restart Orbot and Serval

### I2P Slow/Not Working

**Problem:** I2P takes forever or fails

**Solutions:**
1. Wait 15-20 minutes after first start
2. Check I2P console (router status)
3. Verify tunnels are built
4. Ensure sufficient bandwidth
5. Update address book

### SMS Relay Expensive

**Problem:** SMS charges too high

**Solutions:**
1. Use SMS only for signaling
2. Switch to audio after connection
3. Use store-and-forward for non-urgent
4. Find carrier with SMS bundle
5. Consider internet-based alternatives first

### No Route Available

**Problem:** All routes fail

**Solutions:**
1. Check if any connectivity exists
2. Verify apps installed (Orbot/I2P)
3. Try manual route selection
4. Use store-and-forward (always works)
5. Move to area with connectivity

---

## Legal Considerations

### Tor/I2P Legality
- **Legal:** Most countries (USA, EU, Canada, etc.)
- **Restricted:** Some Middle East countries
- **Illegal:** China, Iran, North Korea (using VPNs/proxies)
- **Check local laws** before use

### SMS Relay Legality
- **Generally Legal:** Worldwide
- **Carrier Terms:** May violate ToS if excessive
- **Spam Laws:** Don't use for bulk messaging
- **Emergency Use:** Usually protected

### Encryption Legality
- **Legal:** Most countries
- **Restricted:** Some require key escrow
- **Export Controls:** US has some restrictions
- **Check local laws**

---

## Future Enhancements

Planned improvements:
- ✨ LoRa radio support (even longer range)
- ✨ Satellite link integration (Starlink, Iridium)
- ✨ Mesh radio (900MHz, 2.4GHz)
- ✨ Ham radio integration (licensed operators)
- ✨ Automatic bridge discovery
- ✨ Steganography (hide in images)
- ✨ Traffic shaping (look like HTTPS)

---

**Last Updated:** November 2, 2025  
**Status:** Production Ready  
**Minimum Android:** 4.3 (API 18)

