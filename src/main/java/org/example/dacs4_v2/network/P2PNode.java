package org.example.dacs4_v2.network;

import org.example.dacs4_v2.data.UserStorage;
import org.example.dacs4_v2.models.*;
import org.example.dacs4_v2.network.dht.BroadcastManager;
import org.example.dacs4_v2.network.rmi.GoGameServiceImpl;
import org.example.dacs4_v2.network.rmi.IGoGameService;
import org.example.dacs4_v2.utils.GetIPV4;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

public class P2PNode {
    private User localUser;
    private BroadcastManager broadcastManager;
    private IGoGameService service;
    private Registry registry;

    private final Set<User> listPeerRes = new HashSet<>();
    private boolean started = false;

    public synchronized void start() throws Exception {
        if (started) return;

        User stored = UserStorage.loadUser();
        if (stored == null) {
            throw new IllegalStateException("No local user found. Please login first.");
        }

        String name = stored.getName();
        String userId = stored.getUserId();
        String serviceName = "GoGameService";
        int rank = stored.getRank();
        int rmiPort = 1099;

        String hostIp = GetIPV4.getLocalIp();
        System.out.println(hostIp);

        this.localUser = new User(hostIp, name,rmiPort,rank,serviceName, userId);
        this.localUser.setRank(rank);

        this.service = new GoGameServiceImpl(localUser);
        this.registry = LocateRegistry.createRegistry(rmiPort);
        registry.rebind(serviceName, service);

        this.broadcastManager = new BroadcastManager(localUser);

        started = true;
    }

    public void defNeighbor(User newPeer) {
            int resultCompare  = localUser.getName().compareTo(newPeer.getName());
            if(resultCompare > 0) { // local user lon hon
                defPrevPeer(newPeer);
            }
            else {
                defSuccPeer(newPeer);
            }
    }
    public void defPrevPeer (User newPeer) {
      try {
          User prevPeer = localUser.getNeighbor(NeighborType.PREDECESSOR);
          if(prevPeer == null) {
              localUser.setNeighbor(NeighborType.PREDECESSOR,prevPeer);
              System.out.println("my pre peer info: " + localUser.getNeighbor(NeighborType.PREDECESSOR).getName());

              //set phia peer doi phuong
              IGoGameService stubPrev = GoGameServiceImpl.getStub(prevPeer);
              stubPrev.notifyAsSuccessor(localUser);
          }
          else {
              int resultCompareID = prevPeer.getUserId().compareTo(newPeer.getUserId());
              switch (resultCompareID) {
                  case -1: {
                      localUser.setNeighbor(NeighborType.PREDECESSOR,newPeer);
                      System.out.println("my pre peer info: " + localUser.getNeighbor(NeighborType.PREDECESSOR).getName());

                      IGoGameService stubPrev = GoGameServiceImpl.getStub(newPeer);
                      stubPrev.notifyAsSuccessor(localUser);
                      break;
                  }
                  case 1: {
                      System.out.println("peer mới " + newPeer.getName() + " nho hon prev peer hien tai");
                      break;
                  }
                  default:
                      System.out.println("2 peer trung nhau");
              }
          }
      }catch (Exception e) {
          e.printStackTrace();
      }
    }
    public void defSuccPeer (User newPeer) {
        try {
            User succPeer = localUser.getNeighbor(NeighborType.SUCCESSOR);
            if(succPeer == null) {
                localUser.setNeighbor(NeighborType.SUCCESSOR, succPeer);
                System.out.println("my pre peer info: " + localUser.getNeighbor(NeighborType.SUCCESSOR).getName());

                //set phia peer doi phuong
                IGoGameService stubPrev = GoGameServiceImpl.getStub(succPeer);
                stubPrev.notifyAsPredecessor(localUser);
            }
            else {
                int resultCompareID = succPeer.getUserId().compareTo(newPeer.getUserId());
                switch (resultCompareID) {
                    case -1: {
                        localUser.setNeighbor(NeighborType.SUCCESSOR,newPeer);
                        System.out.println("my pre peer info: " + localUser.getNeighbor(NeighborType.SUCCESSOR).getName());

                        IGoGameService stubPrev = GoGameServiceImpl.getStub(newPeer);
                        stubPrev.notifyAsPredecessor(localUser);
                        break;
                    }
                    case 1: {
                        System.out.println("peer mới " + newPeer.getName() + " nho hon prev peer hien tai");
                        break;
                    }
                    default:
                        System.out.println("2 peer trung nhau");
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
    public List<User> requestOnlinePeers(int timeoutMs) throws Exception {
        synchronized (listPeerRes) {
            listPeerRes.clear();
        }
        BroadcastMessage msg = new BroadcastMessage("ASK_ONLINE", localUser.getUserId());
        msg.payload.put("requestId", msg.id);
        msg.payload.put("originConfig", localUser);
        System.out.println("bat dau....");
        broadcastManager.SendMessage(msg);

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        synchronized (listPeerRes) {
            return new ArrayList<>(listPeerRes);
        }
    }

    public static String getBroadcastIP(String ip) {
        String [] subIp = ip.split("\\.");
        return subIp[0] + "." + subIp[1] + "."  + subIp[2] + ".255";
    }
    public void shutdown() {
        try {
            if (broadcastManager != null) {
                broadcastManager.close();
                User prevPeer = localUser.getNeighbor(NeighborType.PREDECESSOR);
                User succPeer = localUser.getNeighbor(NeighborType.SUCCESSOR);
                try { // gan lai peer
                    if(prevPeer != null)   {
                        IGoGameService stubPrev = GoGameServiceImpl.getStub(prevPeer);
                        stubPrev.notifyAsPredecessor(succPeer);
                    }
                    if(succPeer != null) {
                        IGoGameService stubSucc = GoGameServiceImpl.getStub(succPeer);
                        stubSucc.notifyAsSuccessor(prevPeer);
                    }
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception ignored) {}
    }

    public User getLocalUser() {
        return localUser;
    }

    public void setLocalUser(User localUser) {
        this.localUser = localUser;
    }

    public IGoGameService getService() {
        return service;
    }

    public void setService(IGoGameService service) {
        this.service = service;
    }
}
