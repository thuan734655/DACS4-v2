//package org.example.dacs4_v2;
//
//import org.example.dacs4_v2.models.*;
//import org.example.dacs4_v2.network.dht.*;
//import org.example.dacs4_v2.network.discovery.LanDiscoveryManager;
//import org.example.dacs4_v2.network.rmi.*;
//
//import java.rmi.registry.LocateRegistry;
//import java.rmi.registry.Registry;
//import java.util.List;
//import java.util.Scanner;
//import java.net.*;
//
//public class Main {
//    public static void main(String[] args) throws Exception {
//        // Cấu hình local (demo)
//        String name = "Alice";
//        String peerId = "A3x9Km2pQz";
//        int rmiPort = 1099;
//
//        String hostIp = getLocalIp();
//
//        // Cấu hình RMI cho chính mình với IP thật
//        UserConfig localConfig = new UserConfig(peerId, "GoGameService", rmiPort, hostIp);
//
//        User localUser = new User(peerId, name, localConfig);
//
//        // Khởi tạo RMI service
//        IGoGameService service = new GoGameServiceImpl(localUser);
//        Registry registry = LocateRegistry.createRegistry(rmiPort);
//        registry.rebind("GoGameService", service);
//        System.out.println("✅ RMI ready at rmi://localhost:" + rmiPort + "/GoGameService");
//
//        // Cho phép peer khác trong LAN discover được mình
//        LanDiscoveryManager.startResponder(rmiPort, "GoGameService", peerId);
//
//        // Khởi tạo DHT + BroadcastManager
//        DHTNode dhtNode = new DHTNode(localUser);
//        BroadcastManager bcast = new BroadcastManager(localUser, dhtNode);
//
//        // Thử tìm seed trong LAN
//        List<UserConfig> seeds = LanDiscoveryManager.discoverPeers(1500); // chờ 1.5s
//
//        if (seeds.isEmpty()) {
//            System.out.println("[BOOT] Không tìm thấy peer, tôi là node đầu tiên trong DHT.");
//            // Vòng 1 node: neighbors sẽ được thiết lập khi có node khác join
//        } else {
//            UserConfig seed = seeds.get(0);
//            System.out.println("[BOOT] Tìm được seed trong LAN: " + seed.getUserId() + "@" + seed.getHost() + ":" + seed.getPort());
//
//            // Tạm thởi coi seed là SUCCESSOR_1 để broadcast JOIN_DHT vào overlay của nó
//            localUser.setNeighbor(NeighborType.SUCCESSOR, seed);
//
//            BroadcastMessage joinMsg = new BroadcastMessage("JOIN_DHT", peerId);
//            BroadcastMessage hello = new BroadcastMessage("HELLO", peerId);
//
//            joinMsg.payload.put("newPeerId", peerId);
//            joinMsg.payload.put("newPeerConfig", localConfig);
////            bcast.broadcastNeighbor(joinMsg);
//
//
//            hello.payload.put("newPeerId", peerId);
//            hello.payload.put("newPeerConfig", localConfig);
////            bcast.broadcastNeighbor(hello);
//        }
//
//        // CLI đơn giản
//        Scanner sc = new Scanner(System.in);
//        System.out.println("Nhập 'exit' để thoát");
//        while (true) {
//            String cmd = sc.nextLine();
//            if ("exit".equalsIgnoreCase(cmd)) break;
//        }
//
//        bcast.close();
//        System.exit(0);
//    }
//
//    // Lấy IPv4 của card mạng local, ưu tiên Wi-Fi/wlan, nếu không có thì lấy bất kỳ non-loopback
//    private static String getLocalIp() throws SocketException {
//        String fallback = "127.0.0.1";
//
//        // Ưu tiên interface Wi-Fi
//        for (NetworkInterface ni : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
//            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
//            String name = ni.getDisplayName().toLowerCase();
//            if (!(name.contains("wi-fi") || name.contains("wifi") || name.contains("wlan") || name.contains("wireless"))) {
//                continue;
//            }
//            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
//                InetAddress addr = ia.getAddress();
//                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
//                    return addr.getHostAddress();
//                }
//            }
//        }
//
//        // Nếu không tìm thấy Wi-Fi, lấy bất kỳ IPv4 non-loopback
//        for (NetworkInterface ni : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
//            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
//            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
//                InetAddress addr = ia.getAddress();
//                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
//                    return addr.getHostAddress();
//                }
//            }
//        }
//
//        return fallback;
//    }
//}