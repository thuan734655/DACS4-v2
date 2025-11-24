package org.example.dacs4_v2.network.dht;


import java.io.Serializable;

public class BroadcastCancel implements Serializable {
    private static final long serialVersionUID = 1L;

    public String broadcastId;
    public String reason; // "RESPONDED", "TIMEOUT"
    public String responderPeerId;

    public BroadcastCancel() {}

    public BroadcastCancel(String broadcastId, String reason, String responderPeerId) {
        this.broadcastId = broadcastId;
        this.reason = reason;
        this.responderPeerId = responderPeerId;
    }
}