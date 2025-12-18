package org.example.dacs4_v2.models;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String name;
    private int rank = 1000;
    private UserConfig userConfig;
    private int port;
    private String serviceName;
    private String host;

    private Map<NeighborType, User> neighbors;

    // Constructor
    public User(String userId, String name, UserConfig userConfig) {
        this.userId = userId;
        this.name = name;
        this.userConfig = userConfig;
        this.neighbors = new HashMap<>();
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

    public UserConfig getUserConfig() { return userConfig; }
    public void setUserConfig(UserConfig userConfig) { this.userConfig = userConfig; }

    public Map<NeighborType, User> getNeighbors() { return neighbors; }
    public void setNeighbors(Map<NeighborType, User> neighbors) { this.neighbors = neighbors; }

    // Helper: set neighbor theo loại
    public void setNeighbor(NeighborType type, User config) {
        if (config != null) {
            neighbors.put(type, config);
        } else {
            neighbors.remove(type);
        }
    }

    // Helper: lấy neighbor — trả về null nếu chưa có
    public User getNeighbor(NeighborType type) {
        return neighbors.get(type);
    }

    // Helper: kiểm tra đã đủ neighbor chưa (tối thiểu succ1, pred1)
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