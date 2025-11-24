package org.example.dacs4_v2.game;

import java.util.function.Consumer;
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.Moves;

public class GameContext {

    private static final GameContext INSTANCE = new GameContext();

    private Game currentGame;
    private Consumer<Moves> moveListener;

    private GameContext() {
    }

    public static GameContext getInstance() {
        return INSTANCE;
    }

    public synchronized void setCurrentGame(Game game) {
        this.currentGame = game;
    }

    public synchronized Game getCurrentGame() {
        return currentGame;
    }

    public synchronized void setMoveListener(Consumer<Moves> listener) {
        this.moveListener = listener;
    }

    public synchronized void notifyMoveReceived(Moves move) {
        Consumer<Moves> listener = this.moveListener;
        if (listener != null) {
            listener.accept(move);
        }
    }
}
