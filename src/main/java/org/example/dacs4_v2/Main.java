package org.example.dacs4_v2;

import org.example.dacs4_v2.models.*;
import org.example.dacs4_v2.network.dht.*;
import org.example.dacs4_v2.network.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        // Cấu hình local
        String name = "Alice";
        String peerId = "A3x9Km2pQz"; // nên random: RandomStringUtils.randomAlphanumeric(10)
        int rmiPort = 1099;

        UserConfig config = new UserConfig("127.0.0.1", rmiPort, "GoGameService");
        User user = new User(peerId, name, config);

        // Khởi tạo DHT
        DHTNode dhtNode = new DHTNode(user);
        BroadcastManager bcast = new BroadcastManager(user, dhtNode);

        // Khởi tạo RMI
        IGoGameService stub = new GoGameServiceImpl(user);
        Registry registry = LocateRegistry.createRegistry(rmiPort);
        registry.rebind("GoGameService", stub);
        System.out.println("✅ RMI ready at rmi://localhost:" + rmiPort + "/GoGameService");

        // Giả lập: broadcast JOIN_DHT
        BroadcastMessage joinMsg = new BroadcastMessage("JOIN_DHT", peerId);
        joinMsg.payload.put("newPeerId", peerId);
        joinMsg.payload.put("newPeerConfig", config);
        bcast.broadcast(joinMsg);

        // CLI đơn giản
        Scanner sc = new Scanner(System.in);
        System.out.println("Nhập 'exit' để thoát");
        while (true) {
            String cmd = sc.nextLine();
            if ("exit".equalsIgnoreCase(cmd)) break;
        }

        bcast.close();
        System.exit(0);
    }
}