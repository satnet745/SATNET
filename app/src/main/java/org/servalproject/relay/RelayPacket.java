package org.servalproject.relay;

import android.util.Base64;

import org.servalproject.servaldna.SubscriberId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Compact line-oriented packet format shared by internet, proxy, SMS, and Rhizome fallbacks.
 */
public class RelayPacket {
    public static final String MAGIC = "SRV1";

    public static final String TYPE_REGISTER = "REGISTER";
    public static final String TYPE_SESSION = "SESSION";
    public static final String TYPE_SESSION_ACK = "SESSION_ACK";
    public static final String TYPE_CALL_INIT = "CALL_INIT";
    public static final String TYPE_CALL_RINGING = "CALL_RINGING";
    public static final String TYPE_CALL_ACCEPT = "CALL_ACCEPT";
    public static final String TYPE_CALL_END = "CALL_END";
    public static final String TYPE_AUDIO = "AUDIO";
    public static final String TYPE_KEEPALIVE = "KEEPALIVE";
    public static final String TYPE_ERROR = "ERROR";
    public static final String TYPE_MISSED_CALL = "MISSED_CALL";

    public String type = "";
    public String from = "";
    public String to = "";
    public String callId = "";
    public int codec = -1;
    public int sampleStart = 0;
    public int sequence = 0;
    public int jitterDelay = 0;
    public int thisDelay = 0;
    public String text = "";
    public byte[] payload;

    public static RelayPacket create(String type, SubscriberId from, SubscriberId to) {
        RelayPacket packet = new RelayPacket();
        packet.type = emptyIfNull(type);
        packet.from = from == null ? "" : from.toHex();
        packet.to = to == null ? "" : to.toHex();
        return packet;
    }

    public SubscriberId getFromSubscriberId() throws SubscriberId.InvalidHexException {
        return from == null || from.length() == 0 ? null : new SubscriberId(from);
    }

    public SubscriberId getToSubscriberId() throws SubscriberId.InvalidHexException {
        return to == null || to.length() == 0 ? null : new SubscriberId(to);
    }

    public String encode() {
        return MAGIC + "|"
                + safe(type) + "|"
                + safe(from) + "|"
                + safe(to) + "|"
                + safe(callId) + "|"
                + codec + "|"
                + sampleStart + "|"
                + sequence + "|"
                + jitterDelay + "|"
                + thisDelay + "|"
                + encodeString(text) + "|"
                + encodeBytes(payload);
    }

    public static RelayPacket decode(String line) throws IOException {
        if (line == null) {
            throw new IOException("Missing relay packet");
        }
        String[] parts = line.split("\\|", -1);
        if (parts.length != 12 || !MAGIC.equals(parts[0])) {
            throw new IOException("Invalid relay packet: " + line);
        }
        RelayPacket packet = new RelayPacket();
        packet.type = parts[1];
        packet.from = parts[2];
        packet.to = parts[3];
        packet.callId = parts[4];
        packet.codec = parseInt(parts[5], -1);
        packet.sampleStart = parseInt(parts[6], 0);
        packet.sequence = parseInt(parts[7], 0);
        packet.jitterDelay = parseInt(parts[8], 0);
        packet.thisDelay = parseInt(parts[9], 0);
        packet.text = decodeString(parts[10]);
        packet.payload = decodeBytes(parts[11]);
        return packet;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String encodeString(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String decodeString(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        return new String(Base64.decode(value, Base64.DEFAULT), StandardCharsets.UTF_8);
    }

    private static String encodeBytes(byte[] value) {
        if (value == null || value.length == 0) {
            return "";
        }
        return Base64.encodeToString(value, Base64.NO_WRAP);
    }

    private static byte[] decodeBytes(String value) {
        if (value == null || value.length() == 0) {
            return null;
        }
        return Base64.decode(value, Base64.DEFAULT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}

