package org.example.dacs4_v2.network.dht;

import org.example.dacs4_v2.HelloApplication;
import org.example.dacs4_v2.models.*;
import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.P2PNode;
import org.example.dacs4_v2.network.rmi.GoGameServiceImpl;
import org.example.dacs4_v2.network.rmi.IGoGameService;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class BroadcastManager {

    private static final int BROADCAST_PORT = 9876;

    private final DatagramSocket recvSocket;
    private final DatagramSocket sendSocket;
    private final User localUser;

    private final BlockingQueue<BroadcastMessage> messageQueue =
            new LinkedBlockingQueue<>(1000);

    private final ExecutorService workerPool =
            Executors.newFixedThreadPool(4);

    public BroadcastManager(User user) throws Exception {
        this.localUser = user;

        recvSocket = new DatagramSocket(null);
        recvSocket.setReuseAddress(true);
        recvSocket.setBroadcast(true);
        recvSocket.bind(new InetSocketAddress(BROADCAST_PORT));
        sendSocket = new DatagramSocket(
                new InetSocketAddress(
                        InetAddress.getByName(HelloApplication.ip),
                        0
                )
        );
        sendSocket.setBroadcast(true);
        startReceiver();
        startWorkers();
    }

    private void startReceiver() {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[8192];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (!recvSocket.isClosed()) {
                try {
                    recvSocket.receive(packet);

                    String senderIp = packet.getAddress().getHostAddress();
                    if (senderIp.equals(localUser.getHost())) {
                        System.out.println(" skip");
                        continue;
                    }

                    Object obj = deserialize(packet.getData(), packet.getLength());

                    if (obj instanceof BroadcastMessage msg) {
                        messageQueue.offer(msg);
                    }
                } catch (Exception e) {
                    if (!recvSocket.isClosed()) {
                        e.printStackTrace();
                    }
                }
            }
        });

        t.setDaemon(true);
        t.start();
    }

    private void startWorkers() {
        for (int i = 0; i < 4; i++) {
            int idx = i;
            workerPool.submit(() -> {
                while (true) {
                    try {
                        BroadcastMessage msg = messageQueue.take();
                        handleBroadcast(msg);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
        }
    }

    public void broadcast(BroadcastMessage msg) {
        try {
            byte[] data = serialize(msg);

            String bcIp = P2PNode.getBroadcastIP(HelloApplication.ip);
            InetAddress broadcastAddr = InetAddress.getByName(bcIp);

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    broadcastAddr,
                    BROADCAST_PORT
            );

            sendSocket.send(packet);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleBroadcast(BroadcastMessage msg) {
        switch (msg.type) {

            case "DISCOVER_ONLINE" -> {
                User origin = (User) msg.payload.get("originConfig");
                if (origin != null) {
                    try {

                        P2PContext ctx = P2PContext.getInstance();
                        if (ctx.getNode() != null) {
                            System.out.println("them peer " + origin.getName());
                            ctx.getNode().addOnlinePeer(origin);
                        }

                        IGoGameService stub = GoGameServiceImpl.getStub(origin);
                        stub.onOnlinePeerDiscovered(localUser);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            case "LOOKUP_PEER" -> {

            }

            default -> System.out.println("[HANDLE] Unknown type");
        }
    }

    /* ================= UTILS ================= */
    private byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(obj);
        oos.flush();
        return bos.toByteArray();
    }

    private Object deserialize(byte[] data, int len)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data, 0, len);
        ObjectInputStream ois = new ObjectInputStream(bis);
        return ois.readObject();
    }

    public void close() {
        System.out.println("[CLOSE] sockets");
        recvSocket.close();
        sendSocket.close();
        workerPool.shutdownNow();
    }
}
