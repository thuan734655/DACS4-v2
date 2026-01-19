package org.example.dacs4_v2.models;

import java.util.ArrayList;
import java.util.List;

public class GameHistory {
    private List<Game> games = new ArrayList<>();

    public List<Game> getGames() {
        return games;
    }

    public void setGames(List<Game> games) {
        this.games = games;
    }
}
