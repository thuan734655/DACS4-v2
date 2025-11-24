package org.example.dacs4_v2.network.dht;

import org.example.dacs4_v2.models.NeighborType;
import org.example.dacs4_v2.models.User;
import org.example.dacs4_v2.models.UserConfig;

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
    public void broadcast(BroadcastMessage msg) {
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
                    // ignore unreachable
                }
            }
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

    // 🧠 Xử lý BroadcastMessage — random delay + cancel check
    private void handleBroadcast(BroadcastMessage msg, InetAddress senderAddr, int senderPort) {
        if (cancelledBroadcasts.contains(msg.id)) return;

        // Giảm TTL và forward tiếp (trước khi xử lý local)
        if (msg.ttl > 1) {
            msg.ttl--;
            broadcast(msg); // forward đến neighbor
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
                String newPeerId = (String) msg.payload.get("newPeerId");
                UserConfig newConfig = (UserConfig) msg.payload.get("newPeerConfig");

                // So sánh: newPeerId có nằm giữa mình và successor không?
                if (isResponsibleFor(newPeerId)) {
                    // Mình là predecessor gần nhất → phản hồi qua RMI
                    System.out.println("[DHT] Tôi là predecessor của: " + newPeerId);

                    // Gửi RMI notify (giả lập — bạn sẽ implement trong GoGameServiceImpl)
                    // real code: successorService.notifyPredecessor(localUser.getUserConfig());

                    // Gửi cancel để dừng các peer khác
                    broadcastCancel(new BroadcastCancel(msg.id, "RESPONDED", localUser.getUserId()));
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
        // Forward cancel
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

    public void close() {
        socket.close();
        scheduler.shutdown();
    }
}