package org.servalproject.relay;

import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servaldna.SubscriberId;
import org.servalproject.servaldna.keyring.KeyringIdentity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internet Relay Client for connecting to faraway devices outside mesh range
 * Enables calls between devices that cannot reach each other via mesh
 */
public class InternetRelayClient {
    private static final String TAG = "InternetRelay";

    // Default relay server configuration
    private static final String DEFAULT_RELAY_HOST = "relay.servalproject.org";
    private static final int DEFAULT_RELAY_PORT = 4110;

    // Connection timeout
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 30000;

    private Socket relayConnection;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread receiveThread;
    private boolean isConnected = false;
    private SubscriberId localId;
    private String relayHost;
    private int relayPort;

    // Callback for receiving relayed data
    private RelayPacketListener callback;

    // Active relay sessions
    private Map<SubscriberId, RelaySession> activeSessions;

    private static class RelaySession {
        SubscriberId remoteId;
        long sessionId;
        long lastActivity;
        boolean isActive;

        RelaySession(SubscriberId remoteId, long sessionId) {
            this.remoteId = remoteId;
            this.sessionId = sessionId;
            this.lastActivity = System.currentTimeMillis();
            this.isActive = true;
        }
    }

    private static InternetRelayClient instance;

    public static synchronized InternetRelayClient getInstance() {
        if (instance == null) {
            instance = new InternetRelayClient();
        }
        return instance;
    }

    private InternetRelayClient() {
        this.relayHost = DEFAULT_RELAY_HOST;
        this.relayPort = DEFAULT_RELAY_PORT;
        this.activeSessions = new ConcurrentHashMap<>();
    }

    /**
     * Configure custom relay server
     */
    public void configureRelay(String host, int port) {
        this.relayHost = host;
        this.relayPort = port;
        Log.i(TAG, "Configured relay: " + host + ":" + port);
    }

    /**
     * Set the local device identity
     */
    public void setLocalIdentity(SubscriberId localId) {
        this.localId = localId;
    }

    /**
     * Set callback for relay events
     */
    public void setCallback(RelayPacketListener callback) {
        this.callback = callback;
    }

    /**
     * Connect to relay server
     */
    public boolean connect() {
        if (isConnected) {
            Log.d(TAG, "Already connected to relay");
            return true;
        }

        try {
            ensureLocalIdentity();
            Log.i(TAG, "Connecting to relay server: " + relayHost + ":" + relayPort);

            relayConnection = new Socket();
            relayConnection.connect(new InetSocketAddress(relayHost, relayPort), CONNECT_TIMEOUT_MS);
            relayConnection.setSoTimeout(READ_TIMEOUT_MS);
            reader = new BufferedReader(new InputStreamReader(relayConnection.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(relayConnection.getOutputStream(), StandardCharsets.UTF_8));

            isConnected = true;

            // Send registration message
            sendRegistration();

            Log.i(TAG, "Connected to relay server");

            // Start receive thread
            startReceiveThread();

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to connect to relay: " + e.getMessage(), e);
            disconnect();
            return false;
        }
    }

    /**
     * Disconnect from relay server
     */
    public void disconnect() {
        isConnected = false;
        activeSessions.clear();

        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }

        closeQuietly(reader);
        closeQuietly(writer);
        if (relayConnection != null) {
            try {
                relayConnection.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing relay connection", e);
            }
            relayConnection = null;
        }
        reader = null;
        writer = null;
        Log.i(TAG, "Disconnected from relay");
    }

    /**
     * Establish relay session with remote peer
     */
    public boolean establishSession(SubscriberId remoteId) {
        if (!isConnected) {
            Log.w(TAG, "Not connected to relay server");
            if (!connect()) {
                return false;
            }
        }

        // Check if session already exists
        if (activeSessions.containsKey(remoteId)) {
            Log.d(TAG, "Session already exists with " + remoteId);
            return true;
        }

        try {
            // Send session establishment request
            long sessionId = System.currentTimeMillis();
            RelayPacket request = RelayPacket.create(RelayPacket.TYPE_SESSION, localId, remoteId);
            request.callId = Long.toHexString(sessionId);
            sendPacket(request);

            RelaySession session = new RelaySession(remoteId, sessionId);
            activeSessions.put(remoteId, session);

            Log.i(TAG, "Established relay session with " + remoteId);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to establish session: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send data through relay to remote peer
     */
    public boolean sendData(SubscriberId destination, byte[] data, int length) {
        if (!isConnected) {
            Log.w(TAG, "Not connected to relay");
            return false;
        }

        RelaySession session = activeSessions.get(destination);
        if (session == null || !session.isActive) {
            Log.w(TAG, "No active session with " + destination);
            return false;
        }

        try {
            RelayPacket packet = RelayPacket.create(RelayPacket.TYPE_AUDIO, localId, destination);
            packet.payload = new byte[length];
            System.arraycopy(data, 0, packet.payload, 0, length);
            sendPacket(packet);

            session.lastActivity = System.currentTimeMillis();
            Log.d(TAG, "Sent " + length + " bytes to " + destination + " via relay");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to send data via relay: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Close session with remote peer
     */
    public void closeSession(SubscriberId remoteId) {
        RelaySession session = activeSessions.get(remoteId);
        if (session != null) {
            session.isActive = false;
            activeSessions.remove(remoteId);

            try {
                sendSessionClose(remoteId);
            } catch (Exception e) {
                Log.e(TAG, "Error sending session close", e);
            }

            Log.i(TAG, "Closed relay session with " + remoteId);
        }
    }

    /**
     * Check if connected to relay
     */
    public boolean isConnected() {
        return isConnected && relayConnection != null && relayConnection.isConnected() && !relayConnection.isClosed();
    }

    /**
     * Check if peer is reachable via relay
     */
    public boolean isPeerReachable(SubscriberId peerId) {
        RelaySession session = activeSessions.get(peerId);
        return isConnected() && session != null && session.isActive;
    }

    /**
     * Get active session count
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    public boolean sendPacket(RelayPacket packet) {
        try {
            if (!isConnected && !connect()) {
                return false;
            }
            synchronized (this) {
                if (writer == null) {
                    throw new IOException("Relay writer unavailable");
                }
                writer.write(packet.encode());
                writer.write('\n');
                writer.flush();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to send relay packet: " + e.getMessage(), e);
            disconnect();
            return false;
        }
    }

    // Private helper methods

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
                throw new IOException("Local identity not ready");
            }
            localId = identity.sid;
        } catch (Exception e) {
            IOException ioe = new IOException("Unable to read local identity");
            ioe.initCause(e);
            throw ioe;
        }
    }

    private void sendRegistration() throws IOException {
        if (localId == null) {
            throw new IOException("Local identity not set");
        }

        RelayPacket packet = RelayPacket.create(RelayPacket.TYPE_REGISTER, localId, null);
        synchronized (this) {
            if (writer == null) {
                throw new IOException("Relay writer unavailable");
            }
            writer.write(packet.encode());
            writer.write('\n');
            writer.flush();
        }
        Log.d(TAG, "Sent registration to relay");
    }

    private void sendSessionClose(SubscriberId remoteId) throws IOException {
        RelayPacket packet = RelayPacket.create(RelayPacket.TYPE_CALL_END, localId, remoteId);
        sendPacket(packet);
    }

    private void startReceiveThread() {
        receiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isConnected && relayConnection != null && reader != null) {
                    try {
                        String line = reader.readLine();
                        if (line == null) {
                            Log.w(TAG, "Relay connection closed");
                            disconnect();
                            break;
                        }
                        processRelayData(line);

                    } catch (Exception e) {
                        if (isConnected) {
                            Log.e(TAG, "Error in receive thread: " + e.getMessage(), e);
                        }
                        break;
                    }
                }

                Log.d(TAG, "Receive thread exited");
            }
        }, "RelayReceiveThread");
        receiveThread.start();
    }

    private void processRelayData(String line) throws IOException {
        RelayPacket packet = RelayPacket.decode(line);
        if (callback == null) {
            return;
        }
        callback.onPacketReceived(packet);
        try {
            SubscriberId peer = packet.getFromSubscriberId();
            if (RelayPacket.TYPE_SESSION_ACK.equals(packet.type) && peer != null) {
                callback.onConnectionEstablished(peer);
            } else if (RelayPacket.TYPE_CALL_END.equals(packet.type) && peer != null) {
                callback.onConnectionLost(peer);
            }
        } catch (SubscriberId.InvalidHexException e) {
            Log.w(TAG, "Ignoring packet with invalid SID", e);
        }
    }

    private void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            Log.w(TAG, "Error closing relay resource", e);
        }
    }
}

