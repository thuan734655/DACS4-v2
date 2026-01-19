package org.example.dacs4_v2.game;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.Moves;

public class GameContext {

    private static final GameContext INSTANCE = new GameContext();

    private Game currentGame;
    private Consumer<Moves> moveListener;
    private boolean viewOnly;

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

    public synchronized void setViewOnly(boolean viewOnly) {
        this.viewOnly = viewOnly;
    }

    public synchronized boolean isViewOnly() {
        return viewOnly;
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

    // ==================== XỬ LÝ THOÁT APP ====================

    // Callback để GameController cung cấp logic thoát game
    private Runnable exitCallback;

    /**
     * Đăng ký callback để xử lý khi app thoát trong khi đang chơi game.
     * GameController sẽ gọi method này khi initialize.
     */
    public synchronized void setExitCallback(Runnable callback) {
        this.exitCallback = callback;
    }

    /**
     * Xử lý khi app bị đóng trong khi đang chơi game.
     * Được gọi từ HelloApplication.onCloseRequest.
     */
    public synchronized void handleAppExit() {
        // Gọi callback nếu có (GameController đã đăng ký)
        if (exitCallback != null) {
            exitCallback.run();
        }

        // Clear context
        currentGame = null;
        moveListener = null;
        exitCallback = null;
        chatListener = null;
    }

    // ==================== CHAT ====================

    // Chat listener: (senderName, message) -> void
    private BiConsumer<String, String> chatListener;

    /**
     * Đăng ký listener để nhận tin nhắn chat.
     */
    public synchronized void setChatListener(BiConsumer<String, String> listener) {
        this.chatListener = listener;
    }

    /**
     * Lấy chat listener.
     */
    public synchronized BiConsumer<String, String> getChatListener() {
        return chatListener;
    }

    // ==================== SCORE RESULT ====================

    // Score result listener: (scoreResult) -> void
    private Consumer<String> scoreResultListener;

    /**
     * Đăng ký listener để nhận kết quả tính điểm từ host.
     */
    public synchronized void setScoreResultListener(Consumer<String> listener) {
        this.scoreResultListener = listener;
    }

    /**
     * Lấy score result listener.
     */
    public synchronized Consumer<String> getScoreResultListener() {
        return scoreResultListener;
    }
}
