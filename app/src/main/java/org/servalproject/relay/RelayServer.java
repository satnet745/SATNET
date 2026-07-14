package org.servalproject.relay;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tiny in-process relay server that forwards RelayPacket lines between registered peers.
 * This is suitable for direct internet relay or for exposure behind a Tor/I2P tunnel.
 */
public class RelayServer extends Thread {
    private static final String TAG = "RelayServer";
    private static final int DEFAULT_START_PORT = 4110;
    private static final int DEFAULT_END_PORT = 4120;

    private final Map<String, ClientConnection> clients = new ConcurrentHashMap<>();
    private final Map<String, Queue<String>> queuedPackets = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private final int port;

    public RelayServer() throws IOException {
        this(DEFAULT_START_PORT, DEFAULT_END_PORT);
    }

    public RelayServer(int startPort, int endPort) throws IOException {
        int candidate = startPort;
        ServerSocket bound = null;
        while (candidate <= endPort) {
            try {
                bound = new ServerSocket(candidate);
                break;
            } catch (IOException e) {
                candidate++;
            }
        }
        if (bound == null) {
            throw new IOException("Unable to bind relay server port");
        }
        this.serverSocket = bound;
        this.port = candidate;
        start();
    }

    public int getPort() {
        return port;
    }

    public int getConnectedClientCount() {
        return clients.size();
    }

    public int getQueuedPacketCount() {
        int total = 0;
        for (Queue<String> queue : queuedPackets.values()) {
            total += queue.size();
        }
        return total;
    }

    public boolean submitPacket(String encodedPacket) {
        if (encodedPacket == null || encodedPacket.trim().isEmpty()) {
            return false;
        }
        try {
            RelayPacket packet = RelayPacket.decode(encodedPacket.trim());
            forward(packet);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Unable to submit relay packet", e);
            return false;
        }
    }

    @Override
    public void interrupt() {
        running = false;
        closeQuietly(serverSocket);
        for (ClientConnection connection : clients.values()) {
            connection.close();
        }
        clients.clear();
        super.interrupt();
    }

    @Override
    public void run() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                new ClientConnection(socket).start();
            } catch (IOException e) {
                if (running) {
                    Log.e(TAG, "Relay accept failed", e);
                }
            }
        }
    }

    private void registerClient(String sid, ClientConnection connection) {
        clients.put(sid, connection);
        Queue<String> queue = queuedPackets.remove(sid);
        if (queue != null) {
            String queued;
            while ((queued = queue.poll()) != null) {
                connection.sendLine(queued);
            }
        }
    }

    private void unregisterClient(String sid, ClientConnection connection) {
        if (sid == null) {
            return;
        }
        ClientConnection current = clients.get(sid);
        if (current == connection) {
            clients.remove(sid);
        }
    }

    private void forward(RelayPacket packet) {
        if (packet.to == null || packet.to.length() == 0) {
            return;
        }
        ClientConnection destination = clients.get(packet.to);
        String encoded = packet.encode();
        if (destination != null) {
            destination.sendLine(encoded);
        } else {
            Queue<String> queue = getOrCreateQueuedPacketBucket(packet.to);
            queue.offer(encoded);
        }
    }

    private Queue<String> getOrCreateQueuedPacketBucket(String subscriberId) {
        Queue<String> queue = queuedPackets.get(subscriberId);
        if (queue != null) {
            return queue;
        }
        synchronized (queuedPackets) {
            queue = queuedPackets.get(subscriberId);
            if (queue == null) {
                queue = new ConcurrentLinkedQueue<>();
                queuedPackets.put(subscriberId, queue);
            }
            return queue;
        }
    }

    private final class ClientConnection extends Thread {
        private final Socket socket;
        private final BufferedReader in;
        private final BufferedWriter out;
        private volatile boolean open = true;
        private String sid;

        private ClientConnection(Socket socket) throws IOException {
            super("RelayClient-" + socket.getPort());
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public void run() {
            try {
                String line;
                while (open && (line = in.readLine()) != null) {
                    RelayPacket packet = RelayPacket.decode(line);
                    if (RelayPacket.TYPE_REGISTER.equals(packet.type)) {
                        sid = packet.from;
                        registerClient(sid, this);
                        continue;
                    }
                    if (RelayPacket.TYPE_SESSION.equals(packet.type)) {
                        RelayPacket ack = RelayPacket.create(RelayPacket.TYPE_SESSION_ACK,
                                packet.getToSubscriberId(), packet.getFromSubscriberId());
                        ack.callId = packet.callId;
                        sendLine(ack.encode());
                    }
                    forward(packet);
                }
            } catch (Exception e) {
                if (open) {
                    Log.w(TAG, "Relay client disconnected", e);
                }
            } finally {
                close();
            }
        }

        private synchronized void sendLine(String line) {
            if (!open) {
                return;
            }
            try {
                out.write(line);
                out.write('\n');
                out.flush();
            } catch (IOException e) {
                Log.w(TAG, "Failed to forward relay line", e);
                close();
            }
        }

        private void close() {
            open = false;
            unregisterClient(sid, this);
            closeQuietly(in);
            closeQuietly(out);
            closeQuietly(socket);
        }
    }

    private void closeQuietly(Closeable closeable) {
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


