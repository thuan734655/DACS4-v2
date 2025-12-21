package org.example.dacs4_v2.network;

import java.util.List;

public class NetworkRuntimeConfig {
    private final String ip;
    private final Integer multicastPort;
    private final Integer rmiPort;

    public NetworkRuntimeConfig(String ip, Integer multicastPort, Integer rmiPort) {
        this.ip = ip;
        this.multicastPort = multicastPort;
        this.rmiPort = rmiPort;
    }

    public String getIp() {
        return ip;
    }

    public Integer getMulticastPort() {
        return multicastPort;
    }

    public Integer getRmiPort() {
        return rmiPort;
    }

    public static NetworkRuntimeConfig fromArgs(List<String> rawArgs) {
        String ip = null;
        Integer multicastPort = null;
        Integer rmiPort = null;

        if (rawArgs != null) {
            for (String arg : rawArgs) {
                if (arg == null) continue;
                String s = arg.trim();
                if (s.isEmpty()) continue;

                while (s.startsWith("--") || s.startsWith("-")) {
                    s = s.substring(1);
                }

                int eq = s.indexOf('=');
                if (eq <= 0 || eq == s.length() - 1) continue;

                String key = s.substring(0, eq).trim();
                String value = s.substring(eq + 1).trim();
                if (key.isEmpty() || value.isEmpty()) continue;

                if ("nd.ip".equalsIgnoreCase(key)) {
                    ip = value;
                } else if ("bind.port".equalsIgnoreCase(key)) {
                    multicastPort = parseIntOrNull(value);
                } else if ("bind.rmiPort".equalsIgnoreCase(key)) {
                    rmiPort = parseIntOrNull(value);
                }
            }
        }

        return new NetworkRuntimeConfig(ip, multicastPort, rmiPort);
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }
}
