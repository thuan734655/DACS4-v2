package org.example.dacs4_v2.models;

import java.io.Serializable;

public class UserConfig implements Serializable {
    private int port;
    private String serviceName;
    private String host;
    private String userId;

    public UserConfig(String userId, String serviceName, int port, String host) {
        this.userId = userId;
        this.serviceName = serviceName;
        this.port = port;
        this.host = host;
    }

    public String getUserId() {
        return userId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getServiceName() {
        return serviceName;
    }

}
