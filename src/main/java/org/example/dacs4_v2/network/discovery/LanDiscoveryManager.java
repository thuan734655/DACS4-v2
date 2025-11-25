package org.example.dacs4_v2.network.discovery;

import org.example.dacs4_v2.models.UserConfig;

import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class LanDiscoveryManager {

    private static final int DISCOVERY_PORT = 9875;
    private static final String DISCOVERY_REQUEST = "DISCOVER_GOGAME";
    private static final String DISCOVERY_RESPONSE = "GOGAME_HERE";

    public static void startResponder(int rmiPort, String serviceName, String userId) throws Exception {
        Thread t = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
                byte[] buf = new byte[256];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                while (!Thread.currentThread().isInterrupted()) {
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    if (DISCOVERY_REQUEST.equals(msg)) {
                        String response = DISCOVERY_RESPONSE + "|" + rmiPort + "|" + serviceName + "|" + userId;
                        byte[] resp = response.getBytes();
                        DatagramPacket respPkt = new DatagramPacket(
                                resp, resp.length,
                                packet.getAddress(), packet.getPort()
                        );
                        socket.send(respPkt);
                    }
                }
            } catch (Exception ignored) {
                // socket đóng hoặc lỗi IO -> thoát thread
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public static List<UserConfig> discoverPeers(int timeoutMs) throws Exception {
        List<UserConfig> results = new ArrayList<>();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            socket.setBroadcast(true);

            byte[] data = DISCOVERY_REQUEST.getBytes();
            DatagramPacket packet = new DatagramPacket(
                    data, data.length,
                    InetAddress.getByName("172.20.10.15"),
                    DISCOVERY_PORT
            );
            socket.send(packet);

            long start = System.currentTimeMillis();
            byte[] buf = new byte[256];
            while (System.currentTimeMillis() - start < timeoutMs) {
                try {
                    DatagramPacket respPkt = new DatagramPacket(buf, buf.length);
                    socket.receive(respPkt);
                    String resp = new String(respPkt.getData(), 0, respPkt.getLength());
                    if (resp.startsWith(DISCOVERY_RESPONSE)) {
                        String[] parts = resp.split("\\|");
                        if (parts.length >= 4) {
                            int rmiPort = Integer.parseInt(parts[1]);
                            String serviceName = parts[2];
                            String userId = parts[3];

                            String host = respPkt.getAddress().getHostAddress();
                            // UserConfig(String userId, String serviceName, int port, String host)
                            UserConfig cfg = new UserConfig(userId, serviceName, rmiPort, host);
                            results.add(cfg);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    break; // hết thời gian chờ
                }
            }
        }

        return results;
    }
}
