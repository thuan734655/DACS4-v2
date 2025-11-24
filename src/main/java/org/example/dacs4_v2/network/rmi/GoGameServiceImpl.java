package org.example.dacs4_v2.network.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.example.dacs4_v2.models.*;


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
        Game game = activeGames.get(move.getOrder() > 0 ? /* suy ra gameId */ "123456" : "123456");
        if (game != null) {
            game.addMove(move);
            System.out.println("[RMI] Nhận nước đi: " + move);
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
    // rmi/GoGameServiceImpl.java
    @Override
    public void notifyAsSuccessor1(UserConfig me, UserConfig nextSuccessor) throws RemoteException {
        System.out.println("[DHT] ✅ Tôi là SUCCESSOR_1 của peer mới: " + me.getUserId());
        localUser.setNeighbor(NeighborType.SUCCESSOR_1, me);
        if (nextSuccessor != null) {
            localUser.setNeighbor(NeighborType.SUCCESSOR_2, nextSuccessor);
        }
        System.out.println("→ Đã cập nhật SUCCESSOR_1 & SUCCESSOR_2");
    }

    @Override
    public void notifyAsSuccessor2(UserConfig me) throws RemoteException {
        System.out.println("[DHT] ✅ Tôi là SUCCESSOR_2 của peer mới: " + me.getUserId());
        localUser.setNeighbor(NeighborType.SUCCESSOR_2, me);
    }

    @Override
    public void notifyAsPredecessor1(UserConfig me, UserConfig prevPredecessor) throws RemoteException {
        System.out.println("[DHT] ✅ Tôi là PREDECESSOR_1 của peer mới: " + me.getUserId());
        localUser.setNeighbor(NeighborType.PREDECESSOR_1, me);
        if (prevPredecessor != null) {
            localUser.setNeighbor(NeighborType.PREDECESSOR_2, prevPredecessor);
        }
        System.out.println("→ Đã cập nhật PREDECESSOR_1 & PREDECESSOR_2");
    }

    @Override
    public void notifyAsPredecessor2(UserConfig me) throws RemoteException {
        System.out.println("[DHT] ✅ Tôi là PREDECESSOR_2 của peer mới: " + me.getUserId());
        localUser.setNeighbor(NeighborType.PREDECESSOR_2, me);
    }
}