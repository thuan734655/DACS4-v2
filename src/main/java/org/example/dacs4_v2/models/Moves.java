package org.example.dacs4_v2.models;

import java.io.Serializable;

public class Moves implements Serializable {
    private static final long serialVersionUID = 1L;

    private int order;
    private String player;
    private int x;
    private int y;
    private String gameId;

    public Moves(int order, String player, int x, int y, String gameId) {
        this.order = order;
        this.player = player;
        this.x = x;
        this.y = y;
        this.gameId = gameId;
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
}
