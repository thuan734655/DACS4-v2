package org.example.dacs4_v2.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Game implements Serializable {
    private static final long serialVersionUID = 1L;

    private String gameId;
    private String hostPeerId;
    private String userId;
    private String rivalId;
    private int boardSize;
    private int komi;
    private String nameGame;

    private GameStatus status;
    private long createdAt;
    private long acceptedAt;
    private long startedAt;
    private long endedAt;

    private User hostUser;
    private User rivalUser;

    private List<Moves> moves = new ArrayList<>();

    public Game() {
    }

    public Game(String gameId, String hostPeerId, String userId, String rivalId, int boardSize, int komi, String nameGame) {
        this.gameId = gameId;
        this.hostPeerId = hostPeerId;
        this.userId = userId;
        this.rivalId = rivalId;
        this.boardSize = boardSize;
        this.komi = komi;
        this.nameGame = nameGame;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getHostPeerId() {
        return hostPeerId;
    }

    public void setHostPeerId(String hostPeerId) {
        this.hostPeerId = hostPeerId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRivalId() {
        return rivalId;
    }

    public void setRivalId(String rivalId) {
        this.rivalId = rivalId;
    }

    public int getBoardSize() {
        return boardSize;
    }

    public void setBoardSize(int boardSize) {
        this.boardSize = boardSize;
    }

    public int getKomi() {
        return komi;
    }

    public void setKomi(int komi) {
        this.komi = komi;
    }

    public double getKomiAsDouble() {
        return komi / 10.0;
    }

    public String getNameGame() {
        return nameGame;
    }

    public void setNameGame(String nameGame) {
        this.nameGame = nameGame;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(long acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public long getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(long endedAt) {
        this.endedAt = endedAt;
    }

    public User getHostUser() {
        return hostUser;
    }

    public void setHostUser(User hostUser) {
        this.hostUser = hostUser;
    }

    public User getRivalUser() {
        return rivalUser;
    }

    public void setRivalUser(User rivalUser) {
        this.rivalUser = rivalUser;
    }

    public List<Moves> getMoves() {
        return moves;
    }

    public void setMoves(List<Moves> moves) {
        this.moves = moves;
    }

    public void addMove(Moves move) {
        if (move == null) {
            return;
        }
        if (moves == null) {
            moves = new ArrayList<>();
        }
        moves.add(move);
    }

    public String getCurrentPlayerId() {
        if (userId == null) {
            return null;
        }
        if (rivalId == null) {
            return userId;
        }
        int n = moves != null ? moves.size() : 0;
        return (n % 2 == 0) ? userId : rivalId;
    }

    public boolean isParticipant(String peerId) {
        if (peerId == null) {
            return false;
        }
        return peerId.equals(userId) || peerId.equals(rivalId) || peerId.equals(hostPeerId);
    }
}
