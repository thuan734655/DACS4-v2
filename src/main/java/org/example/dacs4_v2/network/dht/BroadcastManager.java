package org.example.dacs4_v2.network.dht;

import org.example.dacs4_v2.models.*;
import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.rmi.GoGameServiceImpl;
import org.example.dacs4_v2.network.rmi.IGoGameService;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BroadcastManager {
    private final int MULTICAST_PORT = 9876;
    private  MulticastSocket socket = null;
    private final User localUser;
    private InetAddress group;
    private final BlockingQueue<BroadcastMessage> messageQueue = new LinkedBlockingQueue<>(1000);
    private final ExecutorService workerPool = Executors.newFixedThreadPool(10);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingReplyByRequestId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> clearedByRequestId = new ConcurrentHashMap<>();
    private final int MIN_DELAY_MS = 50;
    private final int MAX_DELAY_MS = 400;

    public BroadcastManager(User user) {
        this.localUser = user;
        try {
            this.group = InetAddress.getByName("239.255.0.1");
            this.socket = new MulticastSocket(null);
            this.socket.setReuseAddress(true);
            this.socket.bind(new InetSocketAddress(MULTICAST_PORT));
            this.socket.setTimeToLive(1);

            NetworkInterface nif = null;
            try {
                if (localUser.getHost() != null && !localUser.getHost().isBlank()) {
                    nif = NetworkInterface.getByInetAddress(InetAddress.getByName(localUser.getHost()));
                }
            } catch (Exception ignored) {}

            if (nif != null) {
                System.out.println(nif + " nif");
                this.socket.setNetworkInterface(nif);
                this.socket.joinGroup(new InetSocketAddress(group, MULTICAST_PORT), nif);
            } else {
                this.socket.joinGroup(group);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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

                    String senderIp = packet.getAddress().getHostAddress();
                    System.out.println("da nhan goi tin");
                    if (senderIp.equals(localUser.getHost())) {
                        System.out.println(" skip");
                        continue;
                    }

                    if (obj instanceof BroadcastMessage) {
                        BroadcastMessage msg = (BroadcastMessage) obj;
                        if (msg.originatorPeerId != null && msg.originatorPeerId.equals(localUser.getUserId())) {
                            continue;
                        }
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
        System.out.println("start worker...");
        for (int i = 0; i < 10; i++) {
            workerPool.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        BroadcastMessage msg = messageQueue.take();
                        handleMulticastLogic(msg);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
    }

    public void SendMessage(BroadcastMessage msg) {
        try{
            byte[] data = serialize(msg);
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, group, MULTICAST_PORT);
            socket.send(datagramPacket);
            System.out.println("gui broadcast");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleMulticastLogic(BroadcastMessage msg) {
        switch (msg.type) {
            case "ASK_ONLINE": {
                System.out.println("goi tin ask");
                handleAskOnline(msg);

                break;
            }
            case "CLEAR_ONLINE": {
                System.out.println("goi tin clear");
                handleClearOnline(msg);
                break;
            }
            case "LOOKUP_PEER": {
                String targetId = (String) msg.payload.get("targetPeerId");
                if (localUser.getUserId().equals(targetId)) {
                    System.out.println("[DHT] Tôi được lookup: " + targetId);
                }
                break;
            }
            case "DISCOVER_ONLINE": {
                System.out.println("su ly broadcast");
                User originConfig = (User) msg.payload.get("originConfig");
                if (originConfig != null) {
                    try {
                        // gan peer nguoi khac
                        P2PContext ctx = P2PContext.getInstance();
                        if (ctx.getNode() != null) {
                            System.out.println("them peer " + originConfig.getName());
                            ctx.getNode().defNeighbor(originConfig);
                        }
                        // gui thong tin cua minh cho peer khac
                        IGoGameService stub = GoGameServiceImpl.getStub(originConfig);
                        stub.onOnlinePeerDiscovered(localUser);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
        }
    }

    private void handleAskOnline(BroadcastMessage msg) {
        System.out.println(msg.payload.get("message") );
        String requestIdRaw = (String) msg.payload.get("requestId");
        String requestId = (requestIdRaw == null || requestIdRaw.isBlank()) ? msg.id : requestIdRaw;
        final String reqId = requestId;

        User originConfigRaw = (User) msg.payload.get("originConfig");
        if (originConfigRaw == null) return;
        final User originConfig = originConfigRaw;

        AtomicBoolean cleared = clearedByRequestId.computeIfAbsent(reqId, k -> new AtomicBoolean(false));
        if (cleared.get()) return;

        ScheduledFuture<?> existing = pendingReplyByRequestId.get(reqId);
        if (existing != null) return;

        int delayMs = computeDelayMs(reqId, originConfig.getUserId(), localUser.getUserId());
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                AtomicBoolean c = clearedByRequestId.computeIfAbsent(reqId, k -> new AtomicBoolean(false));
                if (c.get()) return;

                // gui thong tin cua minh cho peer khac (chi 1 peer thang cuoc se gui)
                IGoGameService stub = GoGameServiceImpl.getStub(originConfig);
                stub.onOnlinePeerDiscovered(localUser);

                // thong bao clear cho cac peer khac trong group
                BroadcastMessage clearMsg = new BroadcastMessage("CLEAR_ONLINE", localUser.getUserId());
                clearMsg.payload.put("requestId", reqId);
                clearMsg.payload.put("winnerUserId", localUser.getUserId());
                clearMsg.payload.put("message" , localUser.getName());
                SendMessage(clearMsg);

                System.out.println("peer thang cuoc la : " + localUser.getName());

                c.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                pendingReplyByRequestId.remove(reqId);
            }
        }, delayMs, TimeUnit.MILLISECONDS);

        ScheduledFuture<?> prev = pendingReplyByRequestId.putIfAbsent(reqId, future);
        if (prev != null) {
            future.cancel(false);
        }
    }

    private void handleClearOnline(BroadcastMessage msg) {
        System.out.println(msg.payload.get("message") );
        String requestId = (String) msg.payload.get("requestId");
        if (requestId == null || requestId.isBlank()) {
            requestId = msg.id;
        }
        if (requestId == null || requestId.isBlank() ) return;

        AtomicBoolean cleared = clearedByRequestId.computeIfAbsent(requestId, k -> new AtomicBoolean(false));
        cleared.set(true);

        System.out.println("clear req khong gui goi tin ve nua");

        ScheduledFuture<?> future = pendingReplyByRequestId.remove(requestId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private int computeDelayMs(String requestId, String originUserId, String receiverUserId) {
        String seed = String.valueOf(requestId) + "|" + String.valueOf(originUserId) + "|" + String.valueOf(receiverUserId);
        int h = seed.hashCode();
        int positive = h & 0x7fffffff;
        int range = (MAX_DELAY_MS - MIN_DELAY_MS) + 1;
        return MIN_DELAY_MS + (positive % range);
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
        workerPool.shutdown();
        scheduler.shutdown();
    }
}