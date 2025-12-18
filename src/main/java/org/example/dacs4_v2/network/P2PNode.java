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
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class P2PNode {

    private User localUser;
    private DHTNode dhtNode;
    private BroadcastManager broadcastManager;
    private IGoGameService service;
    private Registry registry;

    private final List<User> onlinePeers = Collections.synchronizedList(new ArrayList<>());
    private final TreeSet<User> listPeerRes = new TreeSet<>(  Comparator.comparing(User::getUserId));
    private boolean started = false;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);


    public synchronized void start() throws Exception {
        if (started) return;

        User stored = UserStorage.loadUser();
        if (stored == null) {
            throw new IllegalStateException("No local user found. Please login first.");
        }

        String name = stored.getName();
        String peerId = stored.getUserId();
        int rank = stored.getRank();
        int rmiPort = 1099;

        String hostIp =getLocalIp();
        System.out.println(hostIp);
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
        System.out.println(seeds +"seeds");
        if (!seeds.isEmpty()) {
            UserConfig seed = seeds.get(0);
//            localUser.setNeighbor(NeighborType.SUCCESSOR, seed);

            BroadcastMessage joinMsg = new BroadcastMessage("JOIN_DHT", peerId);
            joinMsg.payload.put("newPeerId", peerId);
            joinMsg.payload.put("newPeerConfig", localConfig);

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
        if (user == null) return;
        synchronized (onlinePeers) {
            boolean exists = onlinePeers.stream().anyMatch(u -> u.getUserId().equals(user.getUserId()));
            if (!exists) {
                onlinePeers.add(user);
                listPeerRes.add(user);
            }
        }
    }

    public void defNeighbor() {
        scheduler.schedule(() -> {
            localUser.setNeighbor(NeighborType.PREDECESSOR, listPeerRes.lower(localUser));
            localUser.setNeighbor(NeighborType.SUCCESSOR, listPeerRes.higher(localUser));

            System.out.println(localUser.getNeighbor(NeighborType.SUCCESSOR) + "succ");
            System.out.println(localUser.getNeighbor(NeighborType.PREDECESSOR )+ "pree");
        }, 3, TimeUnit.SECONDS);
    }
    public List<User> requestOnlinePeers(int timeoutMs) throws Exception {
        start();
        defNeighbor();
        synchronized (onlinePeers) {
            onlinePeers.clear();
        }
        BroadcastMessage msg = new BroadcastMessage("DISCOVER_ONLINE", localUser.getUserId());
        msg.payload.put("originConfig", localUser.getUserConfig());
        System.out.println("bat dau....");
        broadcastManager.broadcast(msg);

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        synchronized (onlinePeers) {
            return new ArrayList<>(onlinePeers);
        }
    }

    public static String getBroadcastIP(String ip) {
        String [] subIp = ip.split("\\.");
        return subIp[0] + "." + subIp[1] + "."  + subIp[2] + ".255";
    }

    // Lấy IPv4 của card mạng local, ưu tiên Wi-Fi/wlan, nếu không có thì lấy bất kỳ non-loopback
    public static String getLocalIp() throws SocketException {
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
