package org.example.dacs4_v2.viewModels.helpers;

import javafx.application.Platform;
import org.example.dacs4_v2.ai.AIGameContext;
import org.example.dacs4_v2.game.GoGameLogic;
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.Moves;

/**
 * Helper class xử lý game với AI.
 */
public class GameAIHandler {

    private final Game game;
    private final GoGameLogic gameLogic;
    private final boolean isPlayerBlack;

    /**
     * Interface callback khi AI đánh xong.
     */
    public interface AIResponseCallback {
        void onAIMove(int x, int y, boolean hasCapture, int captureCount);

        void onAIPass();

        void onAIError(String message);
    }

    public GameAIHandler(Game game, GoGameLogic gameLogic, boolean isPlayerBlack) {
        this.game = game;
        this.gameLogic = gameLogic;
        this.isPlayerBlack = isPlayerBlack;
    }

    /**
     * Xử lý phản hồi của AI sau khi người chơi đánh.
     * 
     * @param playerX  tọa độ X của người chơi
     * @param playerY  tọa độ Y của người chơi
     * @param callback callback khi AI đánh xong
     */
    public void handleAIResponse(int playerX, int playerY, AIResponseCallback callback) {
        AIGameContext aiContext = AIGameContext.getInstance();

        // Log nước đi của người chơi
        String playerColorName = isPlayerBlack ? "Đen (Player)" : "Trắng (Player)";
        System.out.println("[AI Game] " + playerColorName + " đánh: (" + playerX + ", " + playerY + ") -> "
                + coordToGo(playerX, playerY));

        // Thông báo nước đi của người chơi cho AI
        aiContext.playPlayerMove(playerX, playerY);

        // Đợi AI đánh trong background thread
        new Thread(() -> {
            try {
                // AI suy nghĩ và đánh
                System.out.println("[AI Game] AI đang suy nghĩ...");
                int[] aiMove = aiContext.getAIMove();

                Platform.runLater(() -> {
                    if (aiMove == null) {
                        System.out.println("[AI Game] AI trả về null");
                        callback.onAIError("AI không thể đánh. Vui lòng thử lại.");
                        return;
                    }

                    if (aiMove[0] == -1 && aiMove[1] == -1) {
                        // AI pass
                        System.out.println("[AI Game] AI PASS");
                        callback.onAIPass();
                        return;
                    }

                    // Log nước đi của AI
                    String aiColorName = isPlayerBlack ? "Trắng (AI)" : "Đen (AI)";
                    System.out.println("[AI Game] " + aiColorName + " đánh: (" + aiMove[0] + ", " + aiMove[1] + ") -> "
                            + coordToGo(aiMove[0], aiMove[1]));

                    // Áp dụng nước đi của AI
                    int aiColor = isPlayerBlack ? 2 : 1; // AI màu ngược với player
                    if (gameLogic.applyMove(aiMove[0], aiMove[1], aiColor, false)) {
                        int captureCount = gameLogic.getLastCaptureCount();

                        // Log số quân bị bắt nếu có
                        if (captureCount > 0) {
                            System.out.println("[AI Game] Bắt được " + captureCount + " quân");
                        }

                        callback.onAIMove(aiMove[0], aiMove[1], captureCount > 0, captureCount);
                    } else {
                        System.out.println("[AI Game] Không thể áp dụng nước đi của AI");
                        callback.onAIError("Không thể áp dụng nước đi của AI.");
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    callback.onAIError("Không thể lấy nước đi từ AI: " + e.getMessage());
                });
            }
        }, "ai-response-thread").start();
    }

    /**
     * Chuyển tọa độ (x, y) sang ký hiệu Go (ví dụ: A1, B2,...).
     * Trong Go, cột được đánh từ A-T (bỏ I), hàng từ 1-19.
     */
    public String coordToGo(int x, int y) {
        // Cột: A-H, J-T (bỏ chữ I vì dễ nhầm với 1)
        char col = (char) ('A' + x);
        if (col >= 'I')
            col++; // Bỏ chữ I

        // Hàng: đánh từ dưới lên, với hàng 1 ở dưới cùng
        int row = game.getBoardSize() - y;

        return String.valueOf(col) + row;
    }

    /**
     * Tạo Moves object cho AI.
     */
    public Moves createAIMove(int x, int y, int order) {
        String aiPlayerColor = isPlayerBlack ? "WHITE" : "BLACK";
        return new Moves(order, aiPlayerColor, x, y, game.getGameId());
    }
}
