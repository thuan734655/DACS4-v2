package org.example.dacs4_v2.models;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;                 // = peerId (10 ký tự ngẫu nhiên)
    private String name;
    private int rank = 1000;
    private UserConfig userConfig;         // cấu hình RMI của chính user này

    private Map<NeighborType, UserConfig> neighbors; // thay vì List<UserConfig>

    // Constructor
    public User(String userId, String name, UserConfig userConfig) {
        this.userId = userId;
        this.name = name;
        this.userConfig = userConfig;
        this.neighbors = new HashMap<>();
        // Khởi tạo rỗng — sẽ được cập nhật khi join DHT
    }

    // ===== Getter & Setter =====
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public UserConfig getUserConfig() { return userConfig; }
    public void setUserConfig(UserConfig userConfig) { this.userConfig = userConfig; }

    public Map<NeighborType, UserConfig> getNeighbors() { return neighbors; }
    public void setNeighbors(Map<NeighborType, UserConfig> neighbors) { this.neighbors = neighbors; }

    // Helper: set neighbor theo loại
    public void setNeighbor(NeighborType type, UserConfig config) {
        if (config != null) {
            neighbors.put(type, config);
        } else {
            neighbors.remove(type);
        }
    }

    // Helper: lấy neighbor — trả về null nếu chưa có
    public UserConfig getNeighbor(NeighborType type) {
        return neighbors.get(type);
    }

    // Helper: kiểm tra đã đủ neighbor chưa (tối thiểu succ1, pred1)
    public boolean hasMinimumNeighbors() {
        return neighbors.containsKey(NeighborType.SUCCESSOR_1) &&
                neighbors.containsKey(NeighborType.PREDECESSOR_1);
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