package org.example.dacs4_v2.network.dht;

import org.example.dacs4_v2.models.*;

import java.util.*;

public class DHTNode {
    private final User localUser;

    public DHTNode(User user) {
        this.localUser = user;
    }

    // Cập nhật neighbor (gọi sau khi join thành công)
    public void updateNeighbors(
            UserConfig succ1, UserConfig succ2,
            UserConfig pred1, UserConfig pred2
    ) {
        localUser.setNeighbor(NeighborType.SUCCESSOR_1, succ1);
        localUser.setNeighbor(NeighborType.SUCCESSOR_2, succ2);
        localUser.setNeighbor(NeighborType.PREDECESSOR_1, pred1);
        localUser.setNeighbor(NeighborType.PREDECESSOR_2, pred2);
    }

    // Lấy tất cả neighbor để broadcast
    public List<UserConfig> getAllNeighborConfigs() {
        List<UserConfig> list = new ArrayList<>();
        for (NeighborType type : NeighborType.values()) {
            UserConfig c = localUser.getNeighbor(type);
            if (c != null) list.add(c);
        }
        return list;
    }

    // Gọi định kỳ (mỗi 30s) để đảm bảo ring ổn định
    public void stabilize() {
        // Gửi RMI đến successor1: successor1.getPredecessor()
        // Nếu predecessor của succ1 "gần" mình hơn succ1 → cập nhật succ1
        // → Bạn sẽ implement chi tiết sau
        System.out.println("[DHT] Stabilize called");
    }
}