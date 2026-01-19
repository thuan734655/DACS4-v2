package org.example.dacs4_v2.network;

public class P2PContext {

    private static final P2PContext INSTANCE = new P2PContext();

    private P2PNode node;
    private NetworkRuntimeConfig runtimeConfig;
    private Runnable neighborUiUpdater;

    private P2PContext() {
    }

    public static P2PContext getInstance() {
        return INSTANCE;
    }

    public synchronized P2PNode getNode() {
        return node;
    }

    public synchronized NetworkRuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    public synchronized void setRuntimeConfig(NetworkRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public synchronized void setNeighborUiUpdater(Runnable neighborUiUpdater) {
        this.neighborUiUpdater = neighborUiUpdater;
    }

    public void requestNeighborUiUpdate() {
        Runnable r;
        synchronized (this) {
            r = neighborUiUpdater;
        }
        if (r != null) {
            r.run();
        }
    }

    public synchronized P2PNode getOrCreateNode() {
        if (node == null) {
            node = new P2PNode();
        }
        return node;
    }
}
