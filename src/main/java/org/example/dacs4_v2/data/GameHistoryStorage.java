package org.example.dacs4_v2.data;

import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.GameHistory;

import java.util.ArrayList;
import java.util.List;

public class GameHistoryStorage {

    private static final String GAME_HISTORY_FILE = "game_history.json";

    public static synchronized void upsert(Game game) {
        if (game == null || game.getGameId() == null) {
            return;
        }

        GameHistory history = DataStorage.load(GAME_HISTORY_FILE, GameHistory.class);
        if (history == null || history.getGames() == null) {
            history = new GameHistory();
        }

        List<Game> games = history.getGames();
        if (games == null) {
            games = new ArrayList<>();
            history.setGames(games);
        }

        for (int i = 0; i < games.size(); i++) {
            Game g = games.get(i);
            if (g != null && game.getGameId().equals(g.getGameId())) {
                games.set(i, game);
                DataStorage.save(history, GAME_HISTORY_FILE);
                return;
            }
        }

        games.add(0, game);
        DataStorage.save(history, GAME_HISTORY_FILE);
    }

    public static synchronized List<Game> loadHistory(int limit) {
        GameHistory history = DataStorage.load(GAME_HISTORY_FILE, GameHistory.class);
        if (history == null || history.getGames() == null) {
            return new ArrayList<>();
        }
        List<Game> games = history.getGames();
        if (limit <= 0 || games.size() <= limit) {
            return new ArrayList<>(games);
        }
        return new ArrayList<>(games.subList(0, limit));
    }
}
