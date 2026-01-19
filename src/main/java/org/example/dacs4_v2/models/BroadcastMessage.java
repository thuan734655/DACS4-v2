package org.example.dacs4_v2.models;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class BroadcastMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public String id;
    public String type; // "JOIN_DHT", "LOOKUP_PEER", "LOOKUP_GAME", "RECONNECT"
    public Map<String, Object> payload = new HashMap<>();
    public int ttl = 5;
    public String originatorPeerId;
    public long timestamp = System.currentTimeMillis();

    public BroadcastMessage() {}

    public BroadcastMessage(String type, String originatorPeerId) {
        this.id = java.util.UUID.randomUUID().toString();
        this.type = type;
        this.originatorPeerId = originatorPeerId;
    }
}
