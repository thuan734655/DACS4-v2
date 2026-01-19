package org.example.dacs4_v2.models;

import java.io.Serializable;

public class Moves implements Serializable {
    private static final long serialVersionUID = 1L;

    private int order;
    // "BLACK" hoặc "WHITE" (dùng để render / xác định màu khi nhận move)
    private String player;
    private int x;
    private int y;
    private String gameId;

    // Thời gian còn lại của người chơi SAU khi đi nước này (milliseconds)
    // Dùng để đồng bộ timer giữa 2 players
    private long playerTimeRemainingMs;

    // Thời gian đã suy nghĩ cho nước đi này (milliseconds)
    private long thinkingTimeMs;

    public Moves(int order, String player, int x, int y, String gameId) {
        this.order = order;
        this.player = player;
        this.x = x;
        this.y = y;
        this.gameId = gameId;
    }

    // Constructor đầy đủ với thời gian
    public Moves(int order, String player, int x, int y, String gameId, long playerTimeRemainingMs,
            long thinkingTimeMs) {
        this.order = order;
        this.player = player;
        this.x = x;
        this.y = y;
        this.gameId = gameId;
        this.playerTimeRemainingMs = playerTimeRemainingMs;
        this.thinkingTimeMs = thinkingTimeMs;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        this.player = player;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public long getPlayerTimeRemainingMs() {
        return playerTimeRemainingMs;
    }

    public void setPlayerTimeRemainingMs(long playerTimeRemainingMs) {
        this.playerTimeRemainingMs = playerTimeRemainingMs;
    }

    public long getThinkingTimeMs() {
        return thinkingTimeMs;
    }

    public void setThinkingTimeMs(long thinkingTimeMs) {
        this.thinkingTimeMs = thinkingTimeMs;
    }
}
