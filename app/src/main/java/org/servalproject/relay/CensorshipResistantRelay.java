package org.servalproject.relay;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servaldna.SubscriberId;
import org.servalproject.servaldna.keyring.KeyringIdentity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Censorship-Resistant Relay using Tor/I2P networks
 * Enables communication even when:
 * - Internet is censored/filtered
 * - Government blocks specific sites
 * - DPI (Deep Packet Inspection) is active
 * - VPNs are blocked
 *
 * Uses onion routing through Tor or garlic routing through I2P
 * for anonymous, censorship-resistant communication
 */
public class CensorshipResistantRelay {
    private static final String TAG = "CensorshipRelay";

    // Tor SOCKS proxy defaults
    private static final String DEFAULT_TOR_PROXY_HOST = "127.0.0.1";
    private static final int DEFAULT_TOR_PROXY_PORT = 9050;

    // I2P SOCKS proxy defaults
    private static final String DEFAULT_I2P_PROXY_HOST = "127.0.0.1";
    private static final int DEFAULT_I2P_PROXY_PORT = 4447;

    // Relay hidden-service defaults
    private static final String DEFAULT_TOR_RELAY_ONION = "servalrelay.onion";
    private static final String DEFAULT_I2P_RELAY_B32 = "servalrelay.b32.i2p";
    private static final int DEFAULT_RELAY_PORT = 4110;

    // Preference keys
    private static final String PREF_TOR_PROXY_HOST = "relay_tor_proxy_host";
    private static final String PREF_TOR_PROXY_PORT = "relay_tor_proxy_port";
    private static final String PREF_I2P_PROXY_HOST = "relay_i2p_proxy_host";
    private static final String PREF_I2P_PROXY_PORT = "relay_i2p_proxy_port";
    private static final String PREF_TOR_RELAY_HOST = "relay_tor_host";
    private static final String PREF_I2P_RELAY_HOST = "relay_i2p_host";
    private static final String PREF_RELAY_PORT = "relay_port";

    public enum ProxyType {
        TOR,      // Use Tor network (onion routing)
        I2P,      // Use I2P network (garlic routing)
        AUTO      // Try Tor first, fallback to I2P
    }

    private ProxyType proxyType;
    private Socket relaySocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean isConnected = false;
    private SubscriberId localId;
    private RelayPacketListener callback;
    private String torProxyHost = DEFAULT_TOR_PROXY_HOST;
    private int torProxyPort = DEFAULT_TOR_PROXY_PORT;
    private String i2pProxyHost = DEFAULT_I2P_PROXY_HOST;
    private int i2pProxyPort = DEFAULT_I2P_PROXY_PORT;
    private String torRelayHost = DEFAULT_TOR_RELAY_ONION;
    private String i2pRelayHost = DEFAULT_I2P_RELAY_B32;
    private int relayPort = DEFAULT_RELAY_PORT;

    // Active sessions
    private ConcurrentHashMap<SubscriberId, RelaySession> sessions;

    private static class RelaySession {
        SubscriberId peer;
        long sessionId;
        boolean active;

        RelaySession(SubscriberId peer) {
            this.peer = peer;
            this.sessionId = System.currentTimeMillis();
            this.active = true;
        }
    }

    private static CensorshipResistantRelay instance;

    public static synchronized CensorshipResistantRelay getInstance(Context context) {
        if (instance == null) {
            instance = new CensorshipResistantRelay(context);
        }
        return instance;
    }

    private CensorshipResistantRelay(Context context) {
        this.proxyType = ProxyType.AUTO;
        this.sessions = new ConcurrentHashMap<>();
        loadRelayConfiguration(context);
    }

    /**
     * Configure proxy type (Tor, I2P, or Auto)
     */
    public void setProxyType(ProxyType type) {
        this.proxyType = type;
        Log.i(TAG, "Proxy type set to: " + type);
    }

    public void refreshConfiguration(Context context) {
        loadRelayConfiguration(context);
    }

    public void configureRelayEndpoint(String host, int port) {
        if (host == null || host.trim().length() == 0) {
            return;
        }
        if (proxyType == ProxyType.I2P) {
            // no-op, endpoint used directly below via fields
        }
        relayHostOverride = host.trim();
        relayPortOverride = port > 0 ? port : DEFAULT_RELAY_PORT;
    }

    public void setCallback(RelayPacketListener callback) {
        this.callback = callback;
    }

    public void setLocalIdentity(SubscriberId localId) {
        this.localId = localId;
    }

    /**
     * Connect to relay through Tor/I2P
     */
    public boolean connect() {
        if (isConnected) {
            Log.d(TAG, "Already connected");
            return true;
        }

        switch (proxyType) {
            case TOR:
                return connectViaTor();
            case I2P:
                return connectViaI2P();
            case AUTO:
                // Try Tor first, fallback to I2P
                if (connectViaTor()) {
                    return true;
                }
                Log.i(TAG, "Tor unavailable, trying I2P...");
                return connectViaI2P();
            default:
                return false;
        }
    }

    /**
     * Connect through Tor network
     */
    private boolean connectViaTor() {
        try {
            Log.i(TAG, "Connecting via Tor...");
            ensureLocalIdentity();

            // Check if Tor is running
            if (!isTorRunning()) {
                Log.w(TAG, "Tor is not running. Please install Orbot.");
                return false;
            }

            // Create SOCKS proxy for Tor
            Proxy torProxy = new Proxy(Proxy.Type.SOCKS,
                new InetSocketAddress(torProxyHost, torProxyPort));

            // Connect to hidden service
            relaySocket = new Socket(torProxy);
            relaySocket.connect(new InetSocketAddress(getRelayHostFor(ProxyType.TOR), getRelayPort()), 30000);

            inputStream = relaySocket.getInputStream();
            outputStream = relaySocket.getOutputStream();
            reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

            isConnected = true;

            // Send registration
            sendRegistration();
            Log.i(TAG, "Connected via Tor hidden service");

            // Start receive thread
            startReceiveThread();

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to connect via Tor: " + e.getMessage(), e);
            cleanup();
            return false;
        }
    }

    /**
     * Connect through I2P network
     */
    private boolean connectViaI2P() {
        try {
            Log.i(TAG, "Connecting via I2P...");
            ensureLocalIdentity();

            // Check if I2P is running
            if (!isI2PRunning()) {
                Log.w(TAG, "I2P is not running. Please install I2P Android.");
                return false;
            }

            // Create SOCKS proxy for I2P
            Proxy i2pProxy = new Proxy(Proxy.Type.SOCKS,
                new InetSocketAddress(i2pProxyHost, i2pProxyPort));

            // Connect to I2P eepsite
            relaySocket = new Socket(i2pProxy);
            relaySocket.connect(new InetSocketAddress(getRelayHostFor(ProxyType.I2P), getRelayPort()), 60000);

            inputStream = relaySocket.getInputStream();
            outputStream = relaySocket.getOutputStream();
            reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

            isConnected = true;

            // Send registration
            sendRegistration();
            Log.i(TAG, "Connected via I2P eepsite");

            // Start receive thread
            startReceiveThread();

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to connect via I2P: " + e.getMessage(), e);
            cleanup();
            return false;
        }
    }

    /**
     * Disconnect from relay
     */
    public void disconnect() {
        cleanup();
        Log.i(TAG, "Disconnected from censorship-resistant relay");
    }

    /**
     * Send data through censorship-resistant relay
     */
    public boolean sendData(SubscriberId destination, byte[] data, int length) {
        if (!isConnected) {
            Log.w(TAG, "Not connected to relay");
            return false;
        }

        try {
            RelayPacket packet = RelayPacket.create(RelayPacket.TYPE_AUDIO, localId, destination);
            packet.payload = new byte[length];
            System.arraycopy(data, 0, packet.payload, 0, length);
            sendPacket(packet);

            Log.d(TAG, "Sent " + length + " bytes via censorship-resistant relay");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to send data: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Establish session with remote peer
     */
    public boolean establishSession(SubscriberId peer) {
        if (!isConnected && !connect()) {
            return false;
        }

        RelaySession session = new RelaySession(peer);
        sessions.put(peer, session);

        // Send session request
        try {
            RelayPacket request = RelayPacket.create(RelayPacket.TYPE_SESSION, localId, peer);
            request.callId = Long.toHexString(session.sessionId);
            sendPacket(request);

            Log.i(TAG, "Established censorship-resistant session with " + peer);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to establish session: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Close session
     */
    public void closeSession(SubscriberId peer) {
        RelaySession session = sessions.remove(peer);
        if (session != null) {
            session.active = false;
            RelayPacket packet = RelayPacket.create(RelayPacket.TYPE_CALL_END, localId, peer);
            sendPacket(packet);
            Log.i(TAG, "Closed session with " + peer);
        }
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return isConnected && relaySocket != null && !relaySocket.isClosed();
    }

    /**
     * Check if Tor is available
     */
    public boolean isTorAvailable() {
        return isTorRunning();
    }

    /**
     * Check if I2P is available
     */
    public boolean isI2PAvailable() {
        return isI2PRunning();
    }

    /**
     * Get relay status
     */
    public String getStatus() {
        String network = "Disconnected";
        if (isConnected) {
            network = (proxyType == ProxyType.TOR) ? "Tor" : "I2P";
        }

        return String.format(Locale.ROOT, "Network: %s\nActive Sessions: %d\nTor Available: %s\nI2P Available: %s",
                           network,
                           sessions.size(),
                           isTorAvailable() ? "Yes" : "No",
                           isI2PAvailable() ? "Yes" : "No");
    }

    /**
     * Get setup instructions
     */
    public String getSetupInstructions() {
        return "CENSORSHIP-RESISTANT RELAY SETUP:\n\n" +
               "For Tor:\n" +
               "1. Install 'Orbot' from F-Droid or Play Store\n" +
               "2. Open Orbot and tap 'Start'\n" +
               "3. Wait for connection (onion icon turns green)\n" +
               "4. Return to Serval and reconnect\n\n" +
               "For I2P:\n" +
               "1. Install 'I2P' from F-Droid\n" +
               "2. Open I2P and start the router\n" +
               "3. Wait 10-15 minutes for tunnels to establish\n" +
               "4. Return to Serval and reconnect\n\n" +
               "Both provide anonymous, censorship-resistant communication.";
    }

    // Private helper methods

    private String relayHostOverride;
    private int relayPortOverride;

    private boolean isTorRunning() {
        try {
            // Try to connect to Tor SOCKS port
            Socket testSocket = new Socket();
            testSocket.connect(new InetSocketAddress(torProxyHost, torProxyPort), 1000);
            testSocket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isI2PRunning() {
        try {
            // Try to connect to I2P SOCKS port
            Socket testSocket = new Socket();
            testSocket.connect(new InetSocketAddress(i2pProxyHost, i2pProxyPort), 1000);
            testSocket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void sendRegistration() throws IOException {
        RelayPacket packet = RelayPacket.create(RelayPacket.TYPE_REGISTER, localId, null);
        writePacket(packet);
    }

    private void startReceiveThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (isConnected && relaySocket != null) {
                    try {
                        String line = reader.readLine();
                        if (line == null) {
                            Log.w(TAG, "Connection closed by relay");
                            break;
                        }
                        processReceivedPacket(line);

                    } catch (Exception e) {
                        Log.e(TAG, "Error in receive thread: " + e.getMessage(), e);
                        break;
                    }
                }

                cleanup();
            }
        }, "CensorshipRelayReceiver").start();
    }

    public boolean sendPacket(RelayPacket packet) {
        try {
            if (!isConnected && !connect()) {
                return false;
            }
            writePacket(packet);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to send censorship-resistant packet", e);
            cleanup();
            return false;
        }
    }

    private void writePacket(RelayPacket packet) throws IOException {
        synchronized (this) {
            if (writer == null) {
                throw new IOException("Relay writer unavailable");
            }
            writer.write(packet.encode());
            writer.write('\n');
            writer.flush();
        }
    }

    private void processReceivedPacket(String line) throws IOException {
        RelayPacket packet = RelayPacket.decode(line);
        if (callback != null) {
            callback.onPacketReceived(packet);
            try {
                SubscriberId peer = packet.getFromSubscriberId();
                if (RelayPacket.TYPE_SESSION_ACK.equals(packet.type) && peer != null) {
                    callback.onConnectionEstablished(peer);
                } else if (RelayPacket.TYPE_CALL_END.equals(packet.type) && peer != null) {
                    callback.onConnectionLost(peer);
                }
            } catch (SubscriberId.InvalidHexException e) {
                Log.w(TAG, "Invalid relay SID received", e);
            }
        }
    }

    private void ensureLocalIdentity() throws IOException {
        if (localId != null) {
            return;
        }
        ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
        if (app == null || app.server == null) {
            throw new IOException("Serval application not ready");
        }
        try {
            KeyringIdentity identity = app.server.getIdentity();
            if (identity == null || identity.sid == null) {
                throw new IOException("Local identity unavailable");
            }
            localId = identity.sid;
        } catch (Exception e) {
            IOException ioe = new IOException("Unable to load local identity");
            ioe.initCause(e);
            throw ioe;
        }
    }

    private String getRelayHostFor(ProxyType type) {
        if (relayHostOverride != null && relayHostOverride.length() > 0) {
            return relayHostOverride;
        }
        return type == ProxyType.I2P ? i2pRelayHost : torRelayHost;
    }

    private int getRelayPort() {
        return relayPortOverride > 0 ? relayPortOverride : relayPort;
    }

    private void loadRelayConfiguration(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        torProxyHost = readHostPreference(prefs, PREF_TOR_PROXY_HOST, DEFAULT_TOR_PROXY_HOST);
        torProxyPort = readPortPreference(prefs, PREF_TOR_PROXY_PORT, DEFAULT_TOR_PROXY_PORT);
        i2pProxyHost = readHostPreference(prefs, PREF_I2P_PROXY_HOST, DEFAULT_I2P_PROXY_HOST);
        i2pProxyPort = readPortPreference(prefs, PREF_I2P_PROXY_PORT, DEFAULT_I2P_PROXY_PORT);
        torRelayHost = readHostPreference(prefs, PREF_TOR_RELAY_HOST, DEFAULT_TOR_RELAY_ONION);
        i2pRelayHost = readHostPreference(prefs, PREF_I2P_RELAY_HOST, DEFAULT_I2P_RELAY_B32);
        relayPort = readPortPreference(prefs, PREF_RELAY_PORT, DEFAULT_RELAY_PORT);
    }

    private String readHostPreference(SharedPreferences prefs, String key, String defaultValue) {
        String value = prefs.getString(key, defaultValue);
        if (value == null) {
            return defaultValue;
        }
        value = value.trim();
        return value.length() > 0 ? value : defaultValue;
    }

    private int readPortPreference(SharedPreferences prefs, String key, int defaultValue) {
        try {
            return Integer.parseInt(prefs.getString(key, Integer.toString(defaultValue)));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void cleanup() {
        isConnected = false;

        try {
            if (inputStream != null) inputStream.close();
        } catch (Exception e) { }

        try {
            if (outputStream != null) outputStream.close();
        } catch (Exception e) { }

        try {
            if (reader != null) reader.close();
        } catch (Exception e) { }

        try {
            if (writer != null) writer.close();
        } catch (Exception e) { }

        try {
            if (relaySocket != null) relaySocket.close();
        } catch (Exception e) { }

        inputStream = null;
        outputStream = null;
        reader = null;
        writer = null;
        relaySocket = null;

        sessions.clear();
    }
}

