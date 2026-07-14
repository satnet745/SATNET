package org.servalproject.relay;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.servalproject.servaldna.SubscriberId;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
public class SmsRelayClientTest {
    @Test
    public void handleReceivedSmsDispatchesRelayPacketToCallback() throws Exception {
        SmsRelayClient client = SmsRelayClient.getInstance(ApplicationProvider.getApplicationContext());
        final AtomicReference<RelayPacket> received = new AtomicReference<>();
        client.setCallback(new RelayPacketListener() {
            @Override
            public void onPacketReceived(RelayPacket packet) {
                received.set(packet);
            }

            @Override
            public void onConnectionEstablished(SubscriberId peer) {
            }

            @Override
            public void onConnectionLost(SubscriberId peer) {
            }
        });

        SubscriberId from = new SubscriberId(repeat('c'));
        SubscriberId to = new SubscriberId(repeat('d'));
        RelayPacket packet = RelayPacket.create(RelayPacket.TYPE_CALL_INIT, from, to);
        packet.callId = "sms123";
        packet.text = "transport:sms_relay";

        client.handleReceivedSms("+123456789", packet.encode());

        Assert.assertNotNull(received.get());
        Assert.assertEquals(RelayPacket.TYPE_CALL_INIT, received.get().type);
        Assert.assertEquals("sms123", received.get().callId);
        Assert.assertEquals(from.toHex(), received.get().from);
        Assert.assertEquals(to.toHex(), received.get().to);
    }

    private static String repeat(char c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 64; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}

