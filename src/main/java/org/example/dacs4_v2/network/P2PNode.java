package org.example.dacs4_v2.network;

import org.example.dacs4_v2.data.UserStorage;
import org.example.dacs4_v2.models.*;
import org.example.dacs4_v2.network.dht.BroadcastManager;
import org.example.dacs4_v2.network.rmi.GoGameServiceImpl;
import org.example.dacs4_v2.network.rmi.IGoGameService;
import org.example.dacs4_v2.utils.GetIPV4;

import java.math.BigInteger;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.MessageDigest;

public class P2PNode {
    private User localUser;
    private BroadcastManager broadcastManager;
    private IGoGameService service;
    private Registry registry;
    private final Object lock = new Object();

    private volatile User fastestOnlinePeer = null;
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
              localUser.setNeighbor(NeighborType.PREDECESSOR, newPeer);
              System.out.println("my pre peer info: " + localUser.getNeighbor(NeighborType.PREDECESSOR).getName());

              //set phia peer doi phuong
              IGoGameService stubPrev = GoGameServiceImpl.getStub(newPeer);
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
                localUser.setNeighbor(NeighborType.SUCCESSOR, newPeer);
                System.out.println("my pre peer info: " + localUser.getNeighbor(NeighborType.SUCCESSOR).getName());

                //set phia peer doi phuong
                IGoGameService stubPrev = GoGameServiceImpl.getStub(newPeer);
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
    public void getPeerWhenJoinNet() throws Exception {
        synchronized (lock) {
            fastestOnlinePeer = null;
        }

        BroadcastMessage msg = new BroadcastMessage("ASK_ONLINE", localUser.getUserId());
        msg.payload.put("requestId", msg.id);
        msg.payload.put("originConfig", localUser);
        msg.payload.put("message", "goi tin cua peer: " + localUser.getName());
        msg.originatorPeerId = localUser.getUserId();

        System.out.println("bat dau....");
        broadcastManager.SendMessage(msg);
    }

    public void joinDhtNetwork(User entry) throws Exception {
        if (entry == null) {
            // First node: ring of one
            localUser.setNeighbor(NeighborType.PREDECESSOR, localUser);
            localUser.setNeighbor(NeighborType.SUCCESSOR, localUser);
            return;
        }

        insertIntoRingByHash(entry, 64);
    }

    private void insertIntoRingByHash(User entry, int maxHops) throws Exception {
        BigInteger x = hashKey(localUser.getUserId());

        User current = entry;
        for (int hop = 0; hop < maxHops; hop++) {
            IGoGameService stubCurrent = GoGameServiceImpl.getStub(current);
            User succ = stubCurrent.getSuccessor();

            if (succ == null) {
                succ = current;
            }

            BigInteger c = hashKey(current.getUserId());
            BigInteger s = hashKey(succ.getUserId());

            if (between(c, x, s)) {
                // current -> localUser -> succ
                localUser.setNeighbor(NeighborType.PREDECESSOR, current);
                localUser.setNeighbor(NeighborType.SUCCESSOR, succ);

                // Update neighbors on remote peers
                stubCurrent.notifyAsSuccessor(localUser);
                IGoGameService stubSucc = GoGameServiceImpl.getStub(succ);
                stubSucc.notifyAsPredecessor(localUser);
                return;
            }

            // Advance clockwise
            current = succ;
        }

        // TH: 2 node
        localUser.setNeighbor(NeighborType.PREDECESSOR, entry);
        localUser.setNeighbor(NeighborType.SUCCESSOR, entry);
        IGoGameService stubEntry = GoGameServiceImpl.getStub(entry);
        stubEntry.notifyAsSuccessor(localUser);
        stubEntry.notifyAsPredecessor(localUser);

        System.out.println("succ cua peer " + localUser.getName() + " la: " + localUser.getNeighbor(NeighborType.SUCCESSOR) );
        System.out.println("prev cua peer " + localUser.getName() + " la: " + localUser.getNeighbor(NeighborType.PREDECESSOR) );
    }

    private BigInteger hashKey(String userId) throws Exception {
        if (userId == null) {
            return BigInteger.ZERO;
        }
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(userId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new BigInteger(1, digest);
    }

    private boolean between(BigInteger a, BigInteger x, BigInteger b) {
        int ab = a.compareTo(b);
        if (ab < 0) {
            // a < x <= b
            return a.compareTo(x) < 0 && x.compareTo(b) <= 0;
        }
        if (ab > 0) {
            //  x > a OR x <= b
            return x.compareTo(a) > 0 || x.compareTo(b) <= 0;
        }
        // a == b => 1 node
        return true;
    }

    public void firstPeerOnNet(User user) {
        synchronized (lock) {
            if (fastestOnlinePeer != null) return;
            fastestOnlinePeer = user;

            try {
                joinDhtNetwork(fastestOnlinePeer);
            }catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public User getFastestOnlinePeer() {
        return fastestOnlinePeer;
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
