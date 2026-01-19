package org.example.dacs4_v2.viewModels.helpers;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.control.Label;
import org.example.dacs4_v2.models.Game;

/**
 * Helper class quản lý timer đếm ngược cho game.
 */
public class GameTimerManager {

    private final Game game;
    private final Label lblBlackTime;
    private final Label lblWhiteTime;

    private long blackTimeMs;
    private long whiteTimeMs;
    private long lastTickTime;
    private long turnStartTime;
    private AnimationTimer gameTimer;

    public GameTimerManager(Game game, Label lblBlackTime, Label lblWhiteTime) {
        this.game = game;
        this.lblBlackTime = lblBlackTime;
        this.lblWhiteTime = lblWhiteTime;

        // Load thời gian từ game
        this.blackTimeMs = game.getBlackTimeMs();
        this.whiteTimeMs = game.getWhiteTimeMs();
    }

    /**
     * Bắt đầu timer đếm ngược.
     */
    public void startTimer() {
        lastTickTime = System.currentTimeMillis();
        turnStartTime = System.currentTimeMillis();

        gameTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                long currentTime = System.currentTimeMillis();
                long delta = currentTime - lastTickTime;
                lastTickTime = currentTime;

                // Xác định ai đang đi
                String currentTurnId = game.getCurrentPlayerId();
                boolean blackTurn = currentTurnId != null && currentTurnId.equals(game.getUserId());

                // Trừ thời gian của người đang đi
                if (blackTurn) {
                    blackTimeMs -= delta;
                    if (blackTimeMs < 0)
                        blackTimeMs = 0;
                } else {
                    whiteTimeMs -= delta;
                    if (whiteTimeMs < 0)
                        whiteTimeMs = 0;
                }

                // Cập nhật giao diện timer
                Platform.runLater(() -> {
                    lblBlackTime.setText(formatTime(blackTimeMs));
                    lblWhiteTime.setText(formatTime(whiteTimeMs));

                    // Highlight khi thời gian thấp (< 1 phút)
                    if (blackTimeMs < 60000) {
                        lblBlackTime.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: #ef4444;");
                    }
                    if (whiteTimeMs < 60000) {
                        lblWhiteTime.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: #ef4444;");
                    }
                });
            }
        };
        gameTimer.start();
    }

    /**
     * Dừng timer.
     */
    public void stopTimer() {
        if (gameTimer != null) {
            gameTimer.stop();
        }
    }

    /**
     * Format thời gian từ milliseconds sang mm:ss.
     */
    public static String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Reset thời điểm bắt đầu lượt.
     */
    public void resetTurnStartTime() {
        this.turnStartTime = System.currentTimeMillis();
    }

    /**
     * Tính thời gian suy nghĩ từ đầu lượt.
     */
    public long getThinkingTime() {
        return System.currentTimeMillis() - turnStartTime;
    }

    // Getters & Setters
    public long getBlackTimeMs() {
        return blackTimeMs;
    }

    public void setBlackTimeMs(long blackTimeMs) {
        this.blackTimeMs = blackTimeMs;
    }

    public long getWhiteTimeMs() {
        return whiteTimeMs;
    }

    public void setWhiteTimeMs(long whiteTimeMs) {
        this.whiteTimeMs = whiteTimeMs;
    }

    public long getTurnStartTime() {
        return turnStartTime;
    }

    /**
     * Lấy thời gian còn lại của người chơi.
     * 
     * @param isBlack true nếu là quân đen
     */
    public long getTimeRemaining(boolean isBlack) {
        return isBlack ? blackTimeMs : whiteTimeMs;
    }

    /**
     * Cập nhật UI label thời gian.
     */
    public void updateTimeLabels() {
        Platform.runLater(() -> {
            lblBlackTime.setText(formatTime(blackTimeMs));
            lblWhiteTime.setText(formatTime(whiteTimeMs));
        });
    }
}
