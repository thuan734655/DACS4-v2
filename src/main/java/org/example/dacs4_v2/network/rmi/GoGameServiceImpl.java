package org.example.dacs4_v2.network.rmi;

import java.math.BigInteger;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.example.dacs4_v2.HelloApplication;
import org.example.dacs4_v2.game.GameContext;
import org.example.dacs4_v2.models.*;
import org.example.dacs4_v2.network.P2PContext;

public class GoGameServiceImpl extends UnicastRemoteObject implements IGoGameService {
    private final User localUser;
    private final ConcurrentHashMap<String, Game> activeGames = new ConcurrentHashMap<>();
    private final List<Game> gameHistory = new ArrayList<>();

    private static final int KEY_BITS = 160;
    private static final BigInteger KEYSPACE_MOD = BigInteger.ONE.shiftLeft(KEY_BITS);
    private static final int FINGER_TABLE_SIZE = 16;
    private static final int FIX_FINGERS_INTERVAL_MS = 1200;

    private final List<User> fingerTable = new ArrayList<>(FINGER_TABLE_SIZE);
    private final AtomicInteger nextFingerIndex = new AtomicInteger(0);
    private volatile ScheduledExecutorService fingerScheduler;

    public GoGameServiceImpl(User user) throws RemoteException {
        this.localUser = user;
        for (int i = 0; i < FINGER_TABLE_SIZE; i++) {
            fingerTable.add(null);
        }
    }

    public synchronized void startChordLite() {
        if (fingerScheduler != null) {
            return;
        }
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chord-lite-fix-fingers");
            t.setDaemon(true);
            return t;
        });
        fingerScheduler = scheduler;

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                fixNextFinger();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, FIX_FINGERS_INTERVAL_MS, FIX_FINGERS_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public synchronized void stopChordLite() {
        ScheduledExecutorService scheduler = fingerScheduler;
        fingerScheduler = null;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void fixNextFinger() throws RemoteException {
        User succ = localUser.getNeighbor(NeighborType.SUCCESSOR);
        if (succ == null) {
            return;
        }

        BigInteger selfHash = hashUserId(localUser.getUserId());
        int idx = nextFingerIndex.getAndUpdate(i -> (i + 1) % FINGER_TABLE_SIZE);
        int exponent = (idx * KEY_BITS) / FINGER_TABLE_SIZE;

        BigInteger start = selfHash.add(BigInteger.ONE.shiftLeft(exponent)).mod(KEYSPACE_MOD);
        User resolved = findSuccessorByHash(start, 64);
        if (resolved != null) {
            synchronized (fingerTable) {
                fingerTable.set(idx, resolved);
            }
        }
    }

    private static BigInteger hashUserId(String userId) {
        if (userId == null) {
            return BigInteger.ZERO;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(userId.getBytes(StandardCharsets.UTF_8));
            return new BigInteger(1, digest);
        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }
    // (a,b] quyet dinh xem dung hay chua
    private static boolean inOpenClosedInterval(BigInteger a, BigInteger x, BigInteger b) {
        if (a == null || x == null || b == null) {
            return false;
        }
        int ab = a.compareTo(b);
        if (ab < 0) {
            return a.compareTo(x) < 0 && x.compareTo(b) <= 0;
        }
        if (ab > 0) {
            return x.compareTo(a) > 0 || x.compareTo(b) <= 0;
        }
        return true;
    }
    // (a,b) quyet dinh xem co tiep tuc duyet nua khong
    private static boolean inOpenOpenInterval(BigInteger a, BigInteger x, BigInteger b) {
        if (a == null || x == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return false;
        }
        int ab = a.compareTo(b);
        if (ab < 0) {
            return a.compareTo(x) < 0 && x.compareTo(b) < 0;
        }
        return x.compareTo(a) > 0 || x.compareTo(b) < 0;
    }

    private User closestPrecedingFinger(BigInteger targetHash) {
        BigInteger selfHash = hashUserId(localUser.getUserId());
        synchronized (fingerTable) {
            for (int i = fingerTable.size() - 1; i >= 0; i--) {
                User u = fingerTable.get(i);
                if (u == null) {
                    continue;
                }
                String uid = u.getUserId();
                if (uid == null) {
                    continue;
                }
                BigInteger uh = hashUserId(uid);
                if (inOpenOpenInterval(selfHash, uh, targetHash)) {
                    return u;
                }
            }
        }
        return localUser.getNeighbor(NeighborType.SUCCESSOR);
    }

    @Override
    public void inviteToGame(Game game) throws RemoteException {
        System.out.println("[RMI] Nhận lời mời vào game: " + game.getGameId());
        // Lưu game, update UI...
        activeGames.put(game.getGameId(), game);

        Platform.runLater(() -> {
            String title = "Game invite";
            String msg = "Bạn được mời vào game " + game.getGameId();
            if (game.getNameGame() != null && !game.getNameGame().isEmpty()) {
                msg += " (" + game.getNameGame() + ")";
            }

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    System.out.println("[UI] User accepted invite for game " + game.getGameId());
                    GameContext.getInstance().setCurrentGame(game);
                    HelloApplication.navigateTo("game.fxml");
                } else {
                    System.out.println("[UI] User declined invite for game " + game.getGameId());
                    activeGames.remove(game.getGameId());
                }
            });
        });
    }

    public void joinRequest(User requester, String gameId) throws RemoteException {
        Game game = activeGames.get(gameId);
        if (game != null && (game.getRivalId() == null || game.getRivalId().isEmpty())) {
            game.setRivalId(requester.getUserId());
            // Gửi game state ban đầu
            // requesterService.onGameStart(game);
            System.out.println("[RMI] Chấp nhận join từ: " + requester.getUserId());
        }
    }

    @Override
    public void submitMove(Moves move, long seqNo) throws RemoteException {
        Game game = null;
        if (move != null && move.getGameId() != null) {
            game = activeGames.get(move.getGameId());
        }
        if (game != null) {
            game.addMove(move);
            System.out.println("[RMI] Nhận nước đi: " + move);
            GameContext.getInstance().setCurrentGame(game);
            GameContext.getInstance().notifyMoveReceived(move);
            // Gửi ACK
            // clientService.moveAck(seqNo);
        }
    }

    @Override
    public void moveAck(long seqNo) throws RemoteException {
        System.out.println("[RMI] Nhận ACK move #" + seqNo);
    }

    @Override
    public void onReconnectRequest(String peerId, String gameId) throws RemoteException {
        Game game = activeGames.get(gameId);
        if (game != null && game.isParticipant(peerId)) {
            // Gửi snapshot
            // peerService.onReconnectOffer(cloneGame(game));
            System.out.println("[RMI] Gửi snapshot game " + gameId + " cho " + peerId);
        }
    }

    @Override
    public void onReconnectOffer(Game gameSnapshot) throws RemoteException {
        System.out.println("[RMI] Nhận snapshot game: " + gameSnapshot.getGameId());
        activeGames.put(gameSnapshot.getGameId(), gameSnapshot);
    }

    @Override
    public List<Game> getGameHistory(int limit) throws RemoteException {
        int n = Math.min(limit, gameHistory.size());
        return new ArrayList<>(gameHistory.subList(0, n));
    }

    @Override
    public void onOnlinePeerDiscovered(User user) throws RemoteException {
        P2PContext ctx = P2PContext.getInstance();
        if (ctx.getNode() != null) {
            ctx.getNode().firstPeerOnNet(user);
        }
    }

    @Override
    public User findPeerById(String targetPeerId, int maxHops) throws RemoteException {
        if (maxHops <= 0 || targetPeerId == null || targetPeerId.isEmpty()) {
            return null;
        }

        String myId = localUser.getUserId();
        if (myId != null && myId.equals(targetPeerId)) {
            return localUser;
        }

        BigInteger targetHash = hashUserId(targetPeerId);
        User resolved = findSuccessorByHash(targetHash, maxHops);
        if (resolved == null) {
            return null;
        }
        if (resolved.getUserId() != null && resolved.getUserId().equals(targetPeerId)) {
            return resolved;
        }
        return null;
    }

    @Override
    public User findSuccessorByHash(BigInteger targetHash, int maxHops) throws RemoteException {
        if (maxHops <= 0 || targetHash == null) {
            return null;
        }

        BigInteger selfHash = hashUserId(localUser.getUserId());
        if (targetHash.equals(selfHash)) {
            return localUser;
        }
        User succ = localUser.getNeighbor(NeighborType.SUCCESSOR);
        if (succ == null) {
            return localUser;
        }

        String succId = succ.getUserId();
        BigInteger succHash = hashUserId(succId);
        if (inOpenClosedInterval(selfHash, targetHash, succHash)) {
            return succ;
        }

        User nextHop = closestPrecedingFinger(targetHash);
        if (nextHop == null || nextHop.getUserId() == null) {
            return succ;
        }
        if (localUser.getUserId() != null && localUser.getUserId().equals(nextHop.getUserId())) {
            return succ;
        }

        try {
            IGoGameService stub = getStub(nextHop);
            return stub.findSuccessorByHash(targetHash, maxHops - 1);
        } catch (Exception e) {
            e.printStackTrace();
            return succ;
        }
    }

    public void notifyAsSuccessor(User nextSuccessor) throws RemoteException {
        localUser.setNeighbor(NeighborType.SUCCESSOR, nextSuccessor);
        System.out.println("da cap nhat succPeer la: " + nextSuccessor.getName());

        P2PContext ctx = P2PContext.getInstance();
        ctx.requestNeighborUiUpdate();
    }

    public void notifyAsPredecessor(User prevPredecessor) throws RemoteException {
        localUser.setNeighbor(NeighborType.PREDECESSOR, prevPredecessor);
        System.out.println("da cap nhat prevPeer la: " + prevPredecessor.getName());

        P2PContext ctx = P2PContext.getInstance();
        ctx.requestNeighborUiUpdate();
    }

    @Override
    public User getSuccessor() throws RemoteException {
        return localUser.getNeighbor(NeighborType.SUCCESSOR);
    }

    @Override
    public User getPredecessor() throws RemoteException {
        return localUser.getNeighbor(NeighborType.PREDECESSOR);
    }

    public static IGoGameService getStub(User config) throws Exception {
        String url = "rmi://" + config.getHost() + ":" + config.getPort() + "/" + config.getServiceName();
        return (IGoGameService) Naming.lookup(url);
    }
//
//    private UserConfig selectNextHopFromNeighbors(String targetPeerId) {
//        if (targetPeerId == null || targetPeerId.isEmpty()) return null;
//
//        String myId = localUser.getUserId();
//        if (myId == null) return null;
//
//        int bestDistance = Math.abs(myId.compareTo(targetPeerId));
//        UserConfig best = null;
//
//        for (NeighborType type : NeighborType.values()) {
//            if (c == null || c.getUserId() == null) continue;
//            int dist = Math.abs(c.getUserId().compareTo(targetPeerId));
//            if (dist < bestDistance) {
//                bestDistance = dist;
//                best = c;
//            }
//        }
//        return best;
//    }
}