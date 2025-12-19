package org.example.dacs4_v2.network;

import org.example.dacs4_v2.HelloApplication;
import org.example.dacs4_v2.data.UserStorage;
import org.example.dacs4_v2.models.*;
import org.example.dacs4_v2.network.dht.BroadcastManager;
import org.example.dacs4_v2.network.dht.DHTNode;
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

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    public synchronized void start() throws Exception {
        if (started) return;

        User stored = UserStorage.loadUser();
        if (stored == null) {
            throw new IllegalStateException("No local user found. Please login first.");
        }

        String name = stored.getName();
        String userId = stored.getUserId();
        String serviceName = "GoGameService";
        int rank = stored.getRank();
        int rmiPort = HelloApplication.rmiPort;

        String hostIp = HelloApplication.ip;
        System.out.println(hostIp);

        this.localUser = new User(hostIp, name,rmiPort,rank,serviceName, userId);
        this.localUser.setRank(rank);

        this.service = new GoGameServiceImpl(localUser);
        this.registry = LocateRegistry.createRegistry(rmiPort);
        registry.rebind(serviceName, service);

        this.dhtNode = new DHTNode(localUser);
        this.broadcastManager = new BroadcastManager(localUser);

        started = true;
    }

    public void addOnlinePeer(User user) {
        if (user == null) return;
        synchronized (onlinePeers) {
            boolean exists = onlinePeers.stream().anyMatch(u -> u.getUserId().equals(user.getUserId()));
            if (!exists) {
                onlinePeers.add(user);
                listPeerRes.add(user);
                defNeighbor();
            }
        }
    }

    public void defNeighbor() {
        System.out.println("def");

            try {
                User prevPeer = listPeerRes.lower(localUser);
                User succPeer = listPeerRes.higher(localUser);

                localUser.setNeighbor(NeighborType.PREDECESSOR,prevPeer);
                localUser.setNeighbor(NeighborType.SUCCESSOR,succPeer);
                if(prevPeer != null)   {
                    IGoGameService stubPrev = GoGameServiceImpl.getStub(prevPeer);
                    stubPrev.notifyAsSuccessor(localUser);
                    System.out.println("prev peer info: " + localUser.getNeighbor(NeighborType.PREDECESSOR).getName());
                }
                if(succPeer != null) {
                    IGoGameService stubSucc = GoGameServiceImpl.getStub(succPeer);
                    stubSucc.notifyAsPredecessor(localUser);
                    System.out.println("succ peer info: " + localUser.getNeighbor(NeighborType.SUCCESSOR).getName());
                }

            }catch (Exception e) {
             e.printStackTrace();
         }
    }
    public List<User> requestOnlinePeers(int timeoutMs) throws Exception {
        start();
        synchronized (onlinePeers) {
            onlinePeers.clear();
        }
        BroadcastMessage msg = new BroadcastMessage("DISCOVER_ONLINE", localUser.getUserId());
        msg.payload.put("originConfig", localUser);
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
    public void shutdown() {
        try {
            if (broadcastManager != null) {
                broadcastManager.close();
                User prevPeer = listPeerRes.lower(localUser);
                User succPeer = listPeerRes.higher(localUser);
                try { // gan lai peer
                    if(prevPeer != null)   {
                        IGoGameService stubPrev = GoGameServiceImpl.getStub(prevPeer);
                        stubPrev.notifyAsPredecessor(succPeer);
                    }
                    if(succPeer != null) {
                        IGoGameService stubSucc = GoGameServiceImpl.getStub(succPeer);
                        stubSucc.notifyAsSuccessor(prevPeer);
                    }

                    System.out.println(localUser.getNeighbor(NeighborType.SUCCESSOR) + "succ");
                    System.out.println(localUser.getNeighbor(NeighborType.PREDECESSOR ) +  "pree");
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception ignored) {}
    }

    public User getLocalUser() {
        return localUser;
    }

    public void setLocalUser(User localUser) {
        this.localUser = localUser;
    }

    public IGoGameService getService() {
        return service;
    }

    public void setService(IGoGameService service) {
        this.service = service;
    }
}
