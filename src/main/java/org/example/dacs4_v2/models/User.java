package org.example.dacs4_v2.models;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String name;
    private int rank = 1000;
    private int port;
    private String serviceName;
    private String host;

    private Map<NeighborType, User> neighbors = new HashMap<>();

    public User(String host, String name, int port, int rank, String serviceName, String userId) {
        this.host = host;
        this.name = name;
        this.port = port;
        this.rank = rank;
        this.serviceName = serviceName;
        this.userId = userId;
    }

    public User(String userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public Map<NeighborType, User> getNeighbors() { return neighbors; }
    public void setNeighbors(Map<NeighborType, User> neighbors) { this.neighbors = neighbors; }

    public void setNeighbor(NeighborType type, User config) {
        if (config != null) {
            neighbors.put(type, config);
        } else {
            neighbors.remove(type);
        }
    }

    public User getNeighbor(NeighborType type) {
        return neighbors.get(type);
    }
    public boolean hasMinimumNeighbors() {
        return neighbors.containsKey(NeighborType.SUCCESSOR) &&
                neighbors.containsKey(NeighborType.PREDECESSOR);
    }

    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", name='" + name + '\'' +
                ", neighbors=" + neighbors.keySet() +
                '}';
    }
}