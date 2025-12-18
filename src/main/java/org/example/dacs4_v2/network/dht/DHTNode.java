package org.example.dacs4_v2.network.dht;

import org.example.dacs4_v2.models.*;

import java.util.*;

public class DHTNode {
    private final User localUser;

    public DHTNode(User user) {
        this.localUser = user;
    }

    // Gọi định kỳ (mỗi 30s) để đảm bảo ring ổn định
    public void stabilize() {
        // Gửi RMI đến successor1: successor1.getPredecessor()
        // Nếu predecessor của succ1 "gần" mình hơn succ1 → cập nhật succ1
        // → Bạn sẽ implement chi tiết sau
        System.out.println("[DHT] Stabilize called");
    }
}