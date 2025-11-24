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

    public UserConfig(String host, int port, String serviceName) {
        this.host = host;
        this.port = port;
        this.serviceName = serviceName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
