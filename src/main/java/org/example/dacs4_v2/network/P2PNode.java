package org.example.dacs4_v2.network;

import org.example.dacs4_v2.data.UserStorage;
import org.example.dacs4_v2.models.*;
import org.example.dacs4_v2.network.dht.BroadcastManager;
import org.example.dacs4_v2.network.dht.DHTNode;
import org.example.dacs4_v2.network.discovery.LanDiscoveryManager;
import org.example.dacs4_v2.network.rmi.GoGameServiceImpl;
import org.example.dacs4_v2.network.rmi.IGoGameService;

import java.net.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class P2PNode {

    private User localUser;
    private DHTNode dhtNode;
    private BroadcastManager broadcastManager;
    private IGoGameService service;
    private Registry registry;

    private final List<User> onlinePeers = Collections.synchronizedList(new ArrayList<>());

    private boolean started = false;

    public synchronized void startIfNeeded() throws Exception {
        if (started) return;

        User stored = UserStorage.loadUser();
        if (stored == null) {
            throw new IllegalStateException("No local user found. Please login first.");
        }

        String name = stored.getName();
        String peerId = stored.getUserId();
        int rank = stored.getRank();
        int rmiPort = 1099;

        String hostIp = "172.20.10.3";
        UserConfig localConfig = new UserConfig(peerId, "GoGameService", rmiPort, hostIp);

        this.localUser = new User(peerId, name, localConfig);
        this.localUser.setRank(rank);

        // Khởi tạo RMI service
        this.service = new GoGameServiceImpl(localUser);
        this.registry = LocateRegistry.createRegistry(rmiPort);
        registry.rebind("GoGameService", service);

        // Cho phép peer khác trong LAN discover được mình
        LanDiscoveryManager.startResponder(rmiPort, "GoGameService", peerId);

        // Khởi tạo DHT + BroadcastManager
        this.dhtNode = new DHTNode(localUser);
        this.broadcastManager = new BroadcastManager(localUser, dhtNode);

        // Tìm seed trong LAN và JOIN_DHT
        List<UserConfig> seeds = LanDiscoveryManager.discoverPeers(1500);
        if (!seeds.isEmpty()) {
            UserConfig seed = seeds.get(0);
            localUser.setNeighbor(NeighborType.SUCCESSOR_1, seed);

            BroadcastMessage joinMsg = new BroadcastMessage("JOIN_DHT", peerId);
            joinMsg.payload.put("newPeerId", peerId);
            joinMsg.payload.put("newPeerConfig", localConfig);
            broadcastManager.broadcast(joinMsg);
        }

        started = true;
    }

    public void shutdown() {
        try {
            if (broadcastManager != null) {
                broadcastManager.close();
            }
        } catch (Exception ignored) {}
    }

    public User getLocalUser() {
        return localUser;
    }

    public BroadcastManager getBroadcastManager() {
        return broadcastManager;
    }

    public IGoGameService getLocalService() {
        return service;
    }

    public void addOnlinePeer(User user) {
        System.out.println(user + "user");
        if (user == null) return;
        synchronized (onlinePeers) {
            boolean exists = onlinePeers.stream().anyMatch(u -> u.getUserId().equals(user.getUserId()));
            if (!exists) {
                onlinePeers.add(user);
            }
        }
    }

    public List<User> requestOnlinePeers(int timeoutMs) throws Exception {
        startIfNeeded();
        synchronized (onlinePeers) {
            onlinePeers.clear();
        }

        BroadcastMessage msg = new BroadcastMessage("DISCOVER_ONLINE", localUser.getUserId());
        msg.payload.put("originConfig", localUser.getUserConfig());
        broadcastManager.broadcast(msg);

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        synchronized (onlinePeers) {
            return new ArrayList<>(onlinePeers);
        }
    }

    // Lấy IPv4 của card mạng local, ưu tiên Wi-Fi/wlan, nếu không có thì lấy bất kỳ non-loopback
    private static String getLocalIp() throws SocketException {
        String fallback = "127.0.0.1";

        // Ưu tiên interface Wi-Fi
        for (NetworkInterface ni : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
            String name = ni.getDisplayName().toLowerCase();
            if (!(name.contains("wi-fi") || name.contains("wifi") || name.contains("wlan") || name.contains("wireless"))) {
                continue;
            }
            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress addr = ia.getAddress();
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                    return addr.getHostAddress();
                }
            }
        }

        // Nếu không tìm thấy Wi-Fi, lấy bất kỳ IPv4 non-loopback
        for (NetworkInterface ni : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress addr = ia.getAddress();
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                    return addr.getHostAddress();
                }
            }
        }
        System.out.println(fallback + "mang");
        return fallback;
    }
}
