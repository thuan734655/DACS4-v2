package org.example.dacs4_v2.network.dht;

import org.example.dacs4_v2.models.*;
import org.example.dacs4_v2.network.P2PNode;
import org.example.dacs4_v2.network.rmi.IGoGameService;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class BroadcastManager {
    private final int BROADCAST_PORT = 9876;
    private final DatagramSocket socket;
    private final User localUser;
    private final DHTNode dhtNode;

    private final Set<String> seenBroadcasts = ConcurrentHashMap.newKeySet();

    private final BlockingQueue<BroadcastMessage> messageQueue = new LinkedBlockingQueue<>(1000);
    private final ExecutorService workerPool = Executors.newFixedThreadPool(10);

    public BroadcastManager(User user, DHTNode dhtNode) throws SocketException {
        this.localUser = user;
        this.dhtNode = dhtNode;
        this.socket = new DatagramSocket(BROADCAST_PORT);
        startReceiver();
        startWorkers();
    }

    // Nhận gói broadcast
    private void startReceiver() {
        Thread receiver = new Thread(() -> {
            byte[] buffer = new byte[8192];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket.receive(packet);
                    Object obj = deserialize(packet.getData(), packet.getLength());
                    InetAddress senderAddr = packet.getAddress();

                    if(senderAddr.getHostAddress().equals(localUser.getUserConfig().getHost())) {
                        System.out.println("trung");

                        continue;
                    }
                    if (obj instanceof BroadcastMessage) {
                        BroadcastMessage msg = (BroadcastMessage) obj;
                        messageQueue.offer(msg);
                    }
                } catch (Exception e) {
                    if (!socket.isClosed()) e.printStackTrace();
                }
            }
        });
        receiver.setDaemon(true);
        receiver.start();
    }

    private void startWorkers() {
        for (int i = 0; i < 10; i++) {
            workerPool.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        BroadcastMessage msg = messageQueue.take();
                        handleBroadcastLogic(msg);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
    }

    // Gửi broadcast đến tất cả neighbor (flood with TTL)
    public void broadcastNeighbor(BroadcastMessage msg) {
        if (msg.ttl <= 0) return;
        if (!seenBroadcasts.add(msg.id)) return; // chống loop

        try {
            byte[] data = serialize(msg);
            for (UserConfig neighbor : dhtNode.getAllNeighborConfigs()) {
                if (neighbor == null) continue;
                try {
                    DatagramPacket pkt = new DatagramPacket(
                            data, data.length,
                            InetAddress.getByName(neighbor.getHost()), BROADCAST_PORT
                    );
                    socket.send(pkt);
                } catch (Exception e) {

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void broadcast(BroadcastMessage msg) {
        try{
            byte[] data = serialize(msg);
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, InetAddress.getByName(P2PNode.getBroadcastIP(P2PNode.getLocalIp())),BROADCAST_PORT);
            socket.send(datagramPacket);
            System.out.println("gui broadcast");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleBroadcastLogic(BroadcastMessage msg) {
        switch (msg.type) {
            case "DISCOVER_ONLINE": {
                System.out.println("su ly broadcast");
                UserConfig originConfig = (UserConfig) msg.payload.get("originConfig");
                if (originConfig != null) {
                    try {
                        IGoGameService stub = getRmiStub(originConfig);
                        stub.onOnlinePeerDiscovered(localUser);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case "LOOKUP_PEER": {
                String targetId = (String) msg.payload.get("targetPeerId");
                if (localUser.getUserId().equals(targetId)) {
                    System.out.println("[DHT] Tôi được lookup: " + targetId);
                }
                break;
            }
        }
    }

    // Serialize/Deserialize helper
    private byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(obj);
        oos.flush();
        return bos.toByteArray();
    }

    private Object deserialize(byte[] data, int len) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data, 0, len);
        ObjectInputStream ois = new ObjectInputStream(bis);
        return ois.readObject();
    }

    private IGoGameService getRmiStub(UserConfig config) throws Exception {
        String url = "rmi://" + config.getHost() + ":" + config.getPort() + "/" + config.getServiceName();
        return (IGoGameService) java.rmi.Naming.lookup(url);
    }

    public void close() {
        socket.close();
        workerPool.shutdown();
    }
}