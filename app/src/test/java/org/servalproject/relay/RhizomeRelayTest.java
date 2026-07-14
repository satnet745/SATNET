package org.servalproject.relay;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.servalproject.servaldna.SubscriberId;
import java.util.concurrent.atomic.AtomicReference;
@RunWith(RobolectricTestRunner.class)
public class RhizomeRelayTest {
    @Test
    public void dispatchEncodedPacketRoutesThroughDispatcher() throws Exception {
        final AtomicReference<RelayPacket> received = new AtomicReference<>();
        RelayPacketDispatcher.setListener(new RelayPacketDispatcher.Listener() {
            @Override
            public void onRelayPacket(RelayPacket packet) {
                received.set(packet);
            }
        });
        SubscriberId from = new SubscriberId(repeat('e'));
        SubscriberId to = new SubscriberId(repeat('f'));
        RelayPacket packet = RelayPacket.create(RelayPacket.TYPE_MISSED_CALL, from, to);
        packet.callId = "rhiz123";
        packet.text = "transport:sneakernet";
        Assert.assertTrue(RhizomeRelay.dispatchEncodedPacket(packet.encode()));
        Assert.assertNotNull(received.get());
        Assert.assertEquals(RelayPacket.TYPE_MISSED_CALL, received.get().type);
        Assert.assertEquals("rhiz123", received.get().callId);
        Assert.assertEquals("transport:sneakernet", received.get().text);
    }
    private static String repeat(char c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 64; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
