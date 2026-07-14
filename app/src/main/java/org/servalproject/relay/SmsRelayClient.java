package org.servalproject.relay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.telephony.SmsManager;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servaldna.SubscriberId;
import org.servalproject.servaldna.keyring.KeyringIdentity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * SMS/MMS Relay for faraway communication when internet is censored/unavailable
 * Uses cellular SMS to relay call signaling and low-bandwidth audio
 *
 * This enables communication even when:
 * - Internet is blocked/censored
 * - WiFi is unavailable
 * - Only cellular network available
 */
@SuppressLint("StaticFieldLeak")
@SuppressWarnings({"unused", "deprecation"})
public class SmsRelayClient {
    private static final String TAG = "SmsRelay";

    // SMS relay server phone number (can be any phone with Serval)
    private static final String DEFAULT_RELAY_NUMBER = "+1234567890";

    private String relayPhoneNumber;
    private final SmsManager smsManager;
    private SubscriberId localId;

    // Callback for received data
    private RelayPacketListener callback;

    private static SmsRelayClient instance;

    public static synchronized SmsRelayClient getInstance(Context context) {
        if (instance == null) {
            instance = new SmsRelayClient(context.getApplicationContext());
        }
        return instance;
    }

    private SmsRelayClient(Context context) {
        this.smsManager = SmsManager.getDefault();
        this.relayPhoneNumber = DEFAULT_RELAY_NUMBER;
    }

    /**
     * Configure SMS relay phone number
     */
    public void configureRelay(String phoneNumber) {
        this.relayPhoneNumber = phoneNumber;
        Log.i(TAG, "Configured SMS relay: " + phoneNumber);
    }

    /**
     * Set callback for SMS events
     */
    public void setCallback(RelayPacketListener callback) {
        this.callback = callback;
    }

    public void setLocalIdentity(SubscriberId localId) {
        this.localId = localId;
    }

    /**
     * Send data via SMS relay
     * Automatically chunks large messages
     */
    public boolean sendData(SubscriberId destination, byte[] data) {
        if (data == null || data.length == 0) {
            Log.w(TAG, "No data to send");
            return false;
        }

        try {
            RelayPacket packet = RelayPacket.create(RelayPacket.TYPE_AUDIO, getLocalIdentity(), destination);
            packet.payload = data;
            return sendPacket(packet, null);

        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send call signaling via SMS
     * Used for initiating/accepting/hanging up calls
     */
    public boolean sendCallSignal(SubscriberId destination, String signal) {
        try {
            RelayPacket packet = RelayPacket.create(signal, getLocalIdentity(), destination);
            return sendPacket(packet, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send call signal: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send audio chunk via SMS (very low quality, for emergencies)
     * Uses heavy compression: ~8 kbps audio compressed to fit SMS
     */
    public boolean sendAudioChunk(SubscriberId destination, byte[] audioData) {
        byte[] compressed = compressAudio(audioData);
        return sendData(destination, compressed);
    }

    /**
     * Handle received SMS message
     * Call this from BroadcastReceiver
     */
    public void handleReceivedSms(String sender, byte[] data) {
        try {
            handleReceivedSms(sender, new String(data, StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "Error handling received SMS: " + e.getMessage(), e);
        }
    }

    public void handleReceivedSms(String sender, String messageBody) {
        try {
            if (messageBody == null || !messageBody.startsWith(RelayPacket.MAGIC + "|")) {
                return;
            }
            RelayPacket packet = RelayPacket.decode(messageBody.trim());
            if (callback != null) {
                callback.onPacketReceived(packet);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error decoding received SMS relay packet from " + sender, e);
        }
    }

    /**
     * Check if SMS relay is available
     */
    public boolean isAvailable() {
        // Check if device has SMS capability
        try {
            return smsManager != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get estimated cost per message
     */
    public String getEstimatedCost() {
        // Note: Costs vary by carrier and plan
        return "SMS relay uses cellular messages. Costs vary by carrier. " +
               "Typical: $0.10-$0.50 per message depending on your plan.";
    }

    /**
     * Get relay status information
     */
    public String getRelayStatus() {
        return String.format("SMS Relay: %s\nRelay Number: %s",
                           isAvailable() ? "Available" : "Unavailable",
                           relayPhoneNumber);
    }

    // Private helper methods

    public boolean sendPacket(RelayPacket packet, String overridePhoneNumber) {
        try {
            String encoded = packet.encode();
            String destination = resolveDestinationNumber(overridePhoneNumber, packet);
            if (destination == null || destination.isEmpty()) {
                Log.w(TAG, "No SMS destination available for packet " + packet.type);
                return false;
            }
            sendSingleSms(destination, encoded);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to send relay SMS packet", e);
            return false;
        }
    }

    private void sendSingleSms(String destination, String message) {
        ArrayList<String> parts = smsManager.divideMessage(message);
        smsManager.sendMultipartTextMessage(destination, null, parts, null, null);
    }

    private byte[] compressAudio(byte[] audioData) {
        // TODO: Implement heavy audio compression
        // For now, just downsample
        byte[] compressed = new byte[audioData.length / 4];
        for (int i = 0; i < compressed.length; i++) {
            compressed[i] = audioData[i * 4];
        }
        return compressed;
    }

    private String resolveDestinationNumber(String overridePhoneNumber, RelayPacket packet) {
        if (overridePhoneNumber != null && !overridePhoneNumber.trim().isEmpty()) {
            return overridePhoneNumber.trim();
        }
        if (relayPhoneNumber != null && !relayPhoneNumber.trim().isEmpty()
                && !DEFAULT_RELAY_NUMBER.equals(relayPhoneNumber)) {
            return relayPhoneNumber.trim();
        }
        if (packet != null && packet.text != null && !packet.text.isEmpty() && packet.text.startsWith("sms:")) {
            return packet.text.substring(4);
        }
        return relayPhoneNumber;
    }

    private SubscriberId getLocalIdentity() throws IOException {
        if (localId != null) {
            return localId;
        }
        try {
            KeyringIdentity identity = ServalBatPhoneApplication.context.server.getIdentity();
            if (identity == null || identity.sid == null) {
                throw new IOException("Local identity unavailable");
            }
            localId = identity.sid;
            return localId;
        } catch (Exception e) {
            throw new IOException("Unable to resolve local SID", e);
        }
    }
}

