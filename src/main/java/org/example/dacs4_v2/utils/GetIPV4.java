package org.example.dacs4_v2.utils;

import java.net.*;

public class GetIPV4 {
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
