package org.example.dacs4_v2.network.rmi;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.example.dacs4_v2.HelloApplication;
import org.example.dacs4_v2.game.GameContext;
import org.example.dacs4_v2.models.*;
import org.example.dacs4_v2.network.P2PContext;

/**
 * 
 */
public class GoGameServiceImpl extends UnicastRemoteObject implements IGoGameService {
    private final User localUser;
    private final ConcurrentHashMap<String, Game> activeGames = new ConcurrentHashMap<>();
    private final List<Game> gameHistory = new ArrayList<>();

    public GoGameServiceImpl(User user) throws RemoteException {
        this.localUser = user;
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

    @Override
    public void joinRequest(UserConfig requester, String gameId) throws RemoteException {
        Game game = activeGames.get(gameId);
        if (game != null && game.getRivalId() == null || game.getRivalId().isEmpty()) {
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
            ctx.getNode().addOnlinePeer(user);
        }
    }

    @Override
    public UserConfig findPeerById(String targetPeerId, int maxHops) throws RemoteException {
        if (maxHops <= 0 || targetPeerId == null || targetPeerId.isEmpty()) {
            return null;
        }

        String myId = localUser.getUserId();
        if (myId != null && myId.equals(targetPeerId)) {
            return localUser.getUserConfig();
        }

        UserConfig nextHop = selectNextHopFromNeighbors(targetPeerId);
        if (nextHop == null) {
            return null;
        }

        if (nextHop.getUserId() != null && nextHop.getUserId().equals(myId)) {
            return null;
        }

        try {
            IGoGameService stub = getStub(nextHop);
            return stub.findPeerById(targetPeerId, maxHops - 1);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // rmi/GoGameServiceImpl.java
    @Override
    public void notifyAsSuccessor1(UserConfig me, UserConfig nextSuccessor) throws RemoteException {

        System.out.println("→ Đã cập nhật SUCCESSOR_1 & SUCCESSOR_2");
    }

    @Override
    public void notifyAsSuccessor2(UserConfig me) throws RemoteException {

    }

    @Override
    public void notifyAsPredecessor1(UserConfig me, UserConfig prevPredecessor) throws RemoteException {

        System.out.println("→ Đã cập nhật PREDECESSOR_1 & PREDECESSOR_2");
    }

    @Override
    public void notifyAsPredecessor2(UserConfig me) throws RemoteException {

    }

    private IGoGameService getStub(UserConfig config) throws Exception {
        String url = "rmi://" + config.getHost() + ":" + config.getPort() + "/" + config.getServiceName();
        return (IGoGameService) Naming.lookup(url);
    }

    private UserConfig selectNextHopFromNeighbors(String targetPeerId) {
        if (targetPeerId == null || targetPeerId.isEmpty()) return null;

        String myId = localUser.getUserId();
        if (myId == null) return null;

        int bestDistance = Math.abs(myId.compareTo(targetPeerId));
        UserConfig best = null;

        for (NeighborType type : NeighborType.values()) {
            UserConfig c = localUser.getNeighbor(type);
            if (c == null || c.getUserId() == null) continue;
            int dist = Math.abs(c.getUserId().compareTo(targetPeerId));
            if (dist < bestDistance) {
                bestDistance = dist;
                best = c;
            }
        }
        return best;
    }
}