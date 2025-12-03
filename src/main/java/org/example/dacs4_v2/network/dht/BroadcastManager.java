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

    // Quản lý cancel & scheduled task
    private final Set<String> cancelledBroadcasts = ConcurrentHashMap.newKeySet();
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Set<String> seenBroadcasts = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public BroadcastManager(User user, DHTNode dhtNode) throws SocketException {
        this.localUser = user;
        this.dhtNode = dhtNode;
        this.socket = new DatagramSocket(BROADCAST_PORT);
        startReceiver();
    }

    // 📥 Nhận gói broadcast/cancel từ mạng
    private void startReceiver() {
        Thread receiver = new Thread(() -> {
            byte[] buffer = new byte[8192];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket.receive(packet);
                    Object obj = deserialize(packet.getData(), packet.getLength());
                    InetAddress senderAddr = packet.getAddress();
                    int senderPort = packet.getPort();

                    if(senderAddr.getHostAddress().equals(localUser.getUserConfig().getHost())) {
                       continue;
                    }
                    if (obj instanceof BroadcastMessage) {
                        handleBroadcast((BroadcastMessage) obj, senderAddr, senderPort);
                    } else if (obj instanceof BroadcastCancel) {
                        handleCancel((BroadcastCancel) obj);
                    }

                } catch (Exception e) {
                    if (!socket.isClosed()) e.printStackTrace();
                }
            }
        });
        receiver.setDaemon(true);
        receiver.start();
    }

    // 📤 Gửi broadcast đến tất cả neighbor (flood with TTL)
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
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, InetAddress.getByName(P2PNode.getLocalIp()),BROADCAST_PORT);
            socket.send(datagramPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 📤 Gửi cancel
    public void broadcastCancel(BroadcastCancel cancel) {
        try {
            byte[] data = serialize(cancel);
            for (UserConfig neighbor : dhtNode.getAllNeighborConfigs()) {
                if (neighbor == null) continue;
                DatagramPacket pkt = new DatagramPacket(
                        data, data.length,
                        InetAddress.getByName(neighbor.getHost()), BROADCAST_PORT
                );
                socket.send(pkt);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleBroadcast(BroadcastMessage msg, InetAddress senderAddr, int senderPort) {
        if (cancelledBroadcasts.contains(msg.id)) return;

        // Giảm TTL và forward tiếp (trước khi xử lý local)
        if (msg.ttl > 1) {
            msg.ttl--;
            broadcastNeighbor(msg); // forward đến neighbor
        }

        // Sinh delay ngẫu nhiên: 10–500ms
        long delayMs = ThreadLocalRandom.current().nextLong(10, 501);

        ScheduledFuture<?> task = scheduler.schedule(() -> {
            if (cancelledBroadcasts.contains(msg.id)) {
                System.out.println("[Bcast] Cancelled: " + msg.id);
                return;
            }

            // → Xử lý logic theo type
            handleBroadcastLogic(msg, senderAddr);

        }, delayMs, TimeUnit.MILLISECONDS);

        scheduledTasks.put(msg.id, task);
    }

    private void handleBroadcastLogic(BroadcastMessage msg, InetAddress senderAddr) {
        switch (msg.type) {
            case "JOIN_DHT": {
                System.out.println(msg.originatorPeerId + "ok");
                String newPeerId = (String) msg.payload.get("newPeerId");
                UserConfig newConfig = (UserConfig) msg.payload.get("newPeerConfig");

                UserConfig succ1 = localUser.getNeighbor(NeighborType.SUCCESSOR_1);
                UserConfig succ2 = localUser.getNeighbor(NeighborType.SUCCESSOR_2);
                UserConfig pred1 = localUser.getNeighbor(NeighborType.PREDECESSOR_1);
                UserConfig pred2 = localUser.getNeighbor(NeighborType.PREDECESSOR_2);

                String myId = localUser.getUserId();
                String succ1Id = (succ1 != null) ? succ1.getUserId() : myId;
                String pred1Id = (pred1 != null) ? pred1.getUserId() : myId;

                boolean iAmPred1 = isBetween(pred1Id, myId, newPeerId);
                boolean iAmSucc1 = isBetween(myId, succ1Id, newPeerId);
                boolean iAmPred2 = pred1 != null && isBetween(
                        (pred2 != null) ? pred2.getUserId() : pred1Id, pred1Id, newPeerId
                );
                boolean iAmSucc2 = succ1 != null && isBetween(
                        succ1Id, (succ2 != null) ? succ2.getUserId() : succ1Id, newPeerId
                );

                if (iAmPred1) {
                    scheduleResponse(msg.id, 50 + rand(20), () -> {
                        try {
                            IGoGameService stub = getRmiStub(newConfig);
                            stub.notifyAsPredecessor1(localUser.getUserConfig(), pred1);
                            broadcastCancel(new BroadcastCancel(msg.id, "RESPONDED", myId));
                        } catch (Exception e) { e.printStackTrace(); }
                    });
                }

                if (iAmSucc1) {
                    scheduleResponse(msg.id, 50 + rand(20), () -> {
                        try {
                            IGoGameService stub = getRmiStub(newConfig);
                            stub.notifyAsSuccessor1(localUser.getUserConfig(), succ1);
                            broadcastCancel(new BroadcastCancel(msg.id, "RESPONDED", myId));
                        } catch (Exception e) { e.printStackTrace(); }
                    });
                }

                if (iAmPred2 && !iAmPred1) {
                    scheduleResponse(msg.id, 150 + rand(30), () -> {
                        try {
                            IGoGameService stub = getRmiStub(newConfig);
                            stub.notifyAsPredecessor2(localUser.getUserConfig());
                        } catch (Exception e) { e.printStackTrace(); }
                    });
                }

                if (iAmSucc2 && !iAmSucc1) {
                    scheduleResponse(msg.id, 150 + rand(30), () -> {
                        try {
                            IGoGameService stub = getRmiStub(newConfig);
                            stub.notifyAsSuccessor2(localUser.getUserConfig());
                        } catch (Exception e) { e.printStackTrace(); }
                    });
                }
                break;
            }
            case "DISCOVER_ONLINE": {
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
                    // Tôi là người cần tìm!
                    System.out.println("[DHT] Tôi được lookup: " + targetId);
                    // Gửi RMI response về originator
                    // real: originatorService.onPeerFound(localUser.getUserConfig());
                    broadcastCancel(new BroadcastCancel(msg.id, "RESPONDED", localUser.getUserId()));
                }
                break;
            }

            case "LOOKUP_GAME": {
                String gameId = (String) msg.payload.get("gameId");
                // Giả sử local có cache game — bạn sẽ implement
                if (hasGame(gameId)) {
                    System.out.println("[DHT] Tôi có game: " + gameId);
                    // Gửi RMI: originatorService.onGameFound(gameSnapshot);
                    broadcastCancel(new BroadcastCancel(msg.id, "RESPONDED", localUser.getUserId()));
                }
                break;
            }
        }
    }

    private boolean isResponsibleFor(String newPeerId) {
        // So sánh peerId dạng string — dùng String.compareTo (vòng đơn giản)
        String myId = localUser.getUserId();
        UserConfig succ1 = localUser.getNeighbor(NeighborType.SUCCESSOR_1);
        String succId = (succ1 != null) ? succ1.getUserId() : myId;

        // Kiểm tra: myId < newPeerId <= succId (trên vòng)
        int cmpMy = myId.compareTo(newPeerId);
        int cmpSucc = newPeerId.compareTo(succId);

        if (myId.compareTo(succId) < 0) { // vòng không bị wrap
            return cmpMy < 0 && cmpSucc <= 0;
        } else { // wrap around (e.g., ZZZ → AAA)
            return cmpMy < 0 || cmpSucc <= 0;
        }
    }

    private boolean hasGame(String gameId) {
        // Giả lập — bạn sẽ thay bằng cache local
        return gameId.equals("123456"); // chỉ peer có game "123456" phản hồi
    }

    private void handleCancel(BroadcastCancel cancel) {
        cancelledBroadcasts.add(cancel.broadcastId);
        ScheduledFuture<?> task = scheduledTasks.remove(cancel.broadcastId);
        if (task != null && !task.isDone()) {
            task.cancel(true);
            System.out.println("[Bcast] Hủy task phản hồi: " + cancel.broadcastId);
        }
        broadcastCancel(cancel);
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
    private boolean isBetween(String a, String b, String x) {
        // So sánh lexic trên vòng: a < x <= b
        if (a.compareTo(b) < 0) {
            // Không wrap: a < b
            return a.compareTo(x) < 0 && x.compareTo(b) <= 0;
        } else {
            // Wrap: a > b → vòng từ a → ... → Z → A → ... → b
            return a.compareTo(x) < 0 || x.compareTo(b) <= 0;
        }
    }

    private void scheduleResponse(String broadcastId, long delayMs, Runnable task) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            if (!cancelledBroadcasts.contains(broadcastId)) {
                task.run();
            } else {
                System.out.println("[Bcast] Bỏ qua phản hồi đã bị cancel: " + broadcastId);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }
    private IGoGameService getRmiStub(UserConfig config) throws Exception {
        String url = "rmi://" + config.getHost() + ":" + config.getPort() + "/" + config.getServiceName();
        return (IGoGameService) java.rmi.Naming.lookup(url);
    }

    private long rand(long bound) {
        return (long) (Math.random() * bound);
    }
    public void close() {
        socket.close();
        scheduler.shutdown();
    }
}