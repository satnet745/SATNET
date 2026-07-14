package org.servalproject.relay;

public final class RelayPacketDispatcher {
    public interface Listener {
        void onRelayPacket(RelayPacket packet);
    }

    private static volatile Listener listener;

    private RelayPacketDispatcher() {
    }

    public static void setListener(Listener newListener) {
        listener = newListener;
    }

    public static void dispatch(RelayPacket packet) {
        Listener current = listener;
        if (current != null && packet != null) {
            current.onRelayPacket(packet);
        }
    }
}

