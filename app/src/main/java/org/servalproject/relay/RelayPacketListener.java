package org.servalproject.relay;

import org.servalproject.servaldna.SubscriberId;

public interface RelayPacketListener {
    void onPacketReceived(RelayPacket packet);
    void onConnectionEstablished(SubscriberId peer);
    void onConnectionLost(SubscriberId peer);
}

