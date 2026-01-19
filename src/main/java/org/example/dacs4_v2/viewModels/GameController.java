package org.example.dacs4_v2.viewModels;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.dacs4_v2.HelloApplication;
import org.example.dacs4_v2.data.GameHistoryStorage;
import org.example.dacs4_v2.game.GameContext;
import org.example.dacs4_v2.game.GoGameLogic;
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.GameStatus;
import org.example.dacs4_v2.models.Moves;
import org.example.dacs4_v2.models.User;
import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.P2PNode;
import org.example.dacs4_v2.viewModels.helpers.*;

/**
 * Controller cho màn hình chơi game cờ vây.
 * Đã được refactor để sử dụng các helper classes.
 */
public class GameController {

    // ==================== FXML COMPONENTS - HEADER ====================
    @FXML
    private Label lblGameName;
    @FXML
    private Label lblKomi;
    @FXML
    private Label lblTurnIndicator;

    // ==================== FXML COMPONENTS - PANEL QUÂN ĐEN ====================
    @FXML
    private VBox panelBlack;
    @FXML
    private Label lblBlackAvatar;
    @FXML
    private Label lblBlackName;
    @FXML
    private Label lblBlackRank;
    @FXML
    private Label lblBlackTime;
    @FXML
    private Label lblBlackCaptured;
    @FXML
    private Label lblBlackTurn;

    // ==================== FXML COMPONENTS - PANEL QUÂN TRẮNG ====================
    @FXML
    private VBox panelWhite;
    @FXML
    private Label lblWhiteAvatar;
    @FXML
    private Label lblWhiteName;
    @FXML
    private Label lblWhiteRank;
    @FXML
    private Label lblWhiteTime;
    @FXML
    private Label lblWhiteCaptured;
    @FXML
    private Label lblWhiteTurn;

    // ==================== FXML COMPONENTS - BÀN CỜ ====================
    @FXML
    private StackPane boardContainer;
    @FXML
    private Canvas boardCanvas;

    // ==================== FXML COMPONENTS - NÚT ĐIỀU KHIỂN ====================
    @FXML
    private Button btnBack;
    @FXML
    private Button btnPass;
    @FXML
    private Button btnSurrender;
    @FXML
    private Button btnExit;
    @FXML
    private Button btnChat;

    // ==================== HELPER CLASSES ====================
    private GameBoardRenderer boardRenderer;
    private GameTimerManager timerManager;
    private GameP2PHandler p2pHandler;
    private GameScoreCalculator scoreCalculator;
    private GameAIHandler aiHandler;
    private GameChatHandler chatHandler;

    // ==================== TRẠNG THÁI GAME ====================
    private Game game;
    private GoGameLogic gameLogic;
    private String localPlayerId;
    private boolean isBlack;
    private boolean viewOnly;
    private boolean isAIGame = false;
    private int consecutivePasses = 0;

    // ==================== KHỞI TẠO ====================

    @FXML
    public void initialize() {
        // Lấy game từ context
        game = GameContext.getInstance().getCurrentGame();
        viewOnly = GameContext.getInstance().isViewOnly();

        if (game == null) {
            HelloApplication.navigateTo("dashboard.fxml");
            return;
        }

        // Khởi tạo game logic
        int boardSize = game.getBoardSize();
        gameLogic = new GoGameLogic(boardSize);

        // Xác định ID người chơi local
        try {
            P2PNode node = P2PContext.getInstance().getOrCreateNode();
            localPlayerId = node.getLocalUser() != null ? node.getLocalUser().getUserId() : null;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Xác định màu quân của local player
        isBlack = localPlayerId != null && localPlayerId.equals(game.getUserId());

        // Kiểm tra xem đây có phải game với AI không
        isAIGame = org.example.dacs4_v2.ai.AIGameContext.getInstance().isAIGame();

        // Khởi tạo helpers
        initializeHelpers();

        // Thiết lập giao diện
        setupHeader();
        setupPlayerPanels();
        boardRenderer.setupBoard();

        // Replay các nước đi từ lịch sử (nếu resume game)
        replayMovesFromHistory();

        // Vẽ bàn cờ ban đầu
        boardRenderer.drawBoard();
        updateTurnIndicator();
        updateCapturedStones();

        // Thiết lập listener và handler theo loại game
        setupGameMode();

        // Thiết lập click handler cho bàn cờ
        boardCanvas.setOnMouseClicked(this::onBoardClicked);
    }

    private void initializeHelpers() {
        // Board renderer
        boardRenderer = new GameBoardRenderer(boardCanvas, gameLogic, game);

        // Timer (nếu không phải AI game và không phải view only)
        if (!viewOnly && !isAIGame) {
            timerManager = new GameTimerManager(game, lblBlackTime, lblWhiteTime);
        }

        // P2P handler (nếu không phải AI game)
        if (!isAIGame) {
            p2pHandler = new GameP2PHandler(game);
        }

        // Score calculator
        scoreCalculator = new GameScoreCalculator(game);

        // AI handler (nếu là AI game)
        if (isAIGame) {
            aiHandler = new GameAIHandler(game, gameLogic, isBlack);
        }

        // Chat handler (nếu không phải AI game và không phải view only)
        if (!viewOnly && !isAIGame) {
            chatHandler = new GameChatHandler();
            chatHandler.setSendCallback(this::onSendChatMessage);
        }
    }

    private void setupGameMode() {
        if (!viewOnly && !isAIGame) {
            // P2P game
            GameContext.getInstance().setMoveListener(this::onRemoteMoveReceived);
            GameContext.getInstance().setChatListener(this::onChatMessageReceived);
            timerManager.startTimer();
            GameContext.getInstance().setExitCallback(this::onAppExit);
        }

        if (!viewOnly && isAIGame) {
            // AI game - không giới hạn thời gian
            lblBlackTime.setText("∞");
            lblWhiteTime.setText("∞");
            if (btnChat != null) {
                btnChat.setVisible(false);
                btnChat.setManaged(false);
            }
        }

        if (viewOnly) {
            setupViewOnlyMode();
        }
    }

    private void setupViewOnlyMode() {
        btnPass.setVisible(false);
        btnPass.setManaged(false);
        btnSurrender.setVisible(false);
        btnSurrender.setManaged(false);
        btnExit.setVisible(false);
        btnExit.setManaged(false);
        if (btnChat != null) {
            btnChat.setVisible(false);
            btnChat.setManaged(false);
        }
        if (btnBack != null) {
            btnBack.setVisible(true);
            btnBack.setManaged(true);
        }

        // Hiển thị thời gian đã lưu
        if (game.getBlackTimeMs() > 0 || game.getWhiteTimeMs() > 0) {
            lblBlackTime.setText(GameTimerManager.formatTime(game.getBlackTimeMs()));
            lblWhiteTime.setText(GameTimerManager.formatTime(game.getWhiteTimeMs()));
        } else {
            lblBlackTime.setText("--:--");
            lblWhiteTime.setText("--:--");
        }
    }

    // ==================== XỬ LÝ APP EXIT ====================

    private void onAppExit() {
        if (game == null || viewOnly)
            return;

        if (timerManager != null) {
            timerManager.stopTimer();
        }

        game.setBlackTimeMs(timerManager != null ? timerManager.getBlackTimeMs() : 0);
        game.setWhiteTimeMs(timerManager != null ? timerManager.getWhiteTimeMs() : 0);
        game.setCapturedByBlack(scoreCalculator.getCapturedByBlack());
        game.setCapturedByWhite(scoreCalculator.getCapturedByWhite());
        game.setStatus(GameStatus.PAUSED);
        if (!isAIGame) {
            GameHistoryStorage.upsert(game);
        }

        if (p2pHandler != null) {
            p2pHandler.notifyOpponentSync("EXIT",
                    timerManager != null ? timerManager.getBlackTimeMs() : 0,
                    timerManager != null ? timerManager.getWhiteTimeMs() : 0);
        }
    }

    // ==================== REPLAY MOVES ====================

    private void replayMovesFromHistory() {
        if (game.getMoves() == null || game.getMoves().isEmpty()) {
            return;
        }

        for (Moves m : game.getMoves()) {
            if (m == null || m.getX() < 0 || m.getY() < 0)
                continue;
            int color = "BLACK".equals(m.getPlayer()) ? 1 : 2;
            gameLogic.applyMove(m.getX(), m.getY(), color, false);
        }
    }

    // ==================== THIẾT LẬP GIAO DIỆN ====================

    private void setupHeader() {
        String gameName = game.getNameGame() != null ? game.getNameGame() : "";
        lblGameName.setText("Game " + game.getGameId() + " - " + gameName);
        lblKomi.setText("Komi: " + game.getKomiAsDouble());
    }

    private void setupPlayerPanels() {
        User hostUser = game.getHostUser();
        User rivalUser = game.getRivalUser();

        User blackPlayer = hostUser;
        User whitePlayer = rivalUser;

        if (blackPlayer != null) {
            String name = blackPlayer.getName() != null ? blackPlayer.getName() : "Player 1";
            lblBlackName.setText(name);
            lblBlackAvatar.setText(name.isEmpty() ? "●" : name.substring(0, 1).toUpperCase());
            lblBlackRank.setText("Rank: " + (blackPlayer.getRank() > 0 ? blackPlayer.getRank() : "-"));
        }

        if (whitePlayer != null) {
            String name = whitePlayer.getName() != null ? whitePlayer.getName() : "Player 2";
            lblWhiteName.setText(name);
            lblWhiteAvatar.setText(name.isEmpty() ? "○" : name.substring(0, 1).toUpperCase());
            lblWhiteRank.setText("Rank: " + (whitePlayer.getRank() > 0 ? whitePlayer.getRank() : "-"));
        }
    }

    // ==================== XỬ LÝ CLICK BÀN CỜ ====================

    private void onBoardClicked(MouseEvent event) {
        if (game == null || viewOnly)
            return;

        int[] gridPos = boardRenderer.pixelToGrid(event.getX(), event.getY());
        if (gridPos == null)
            return;

        int gridX = gridPos[0];
        int gridY = gridPos[1];

        // Kiểm tra lượt đi
        String currentTurnId = game.getCurrentPlayerId();
        if (localPlayerId == null || currentTurnId == null || !localPlayerId.equals(currentTurnId)) {
            return;
        }

        // Áp dụng nước đi
        int color = isBlack ? 1 : 2;
        if (!gameLogic.applyMove(gridX, gridY, color, true)) {
            return;
        }

        // Cập nhật số quân bị bắt
        if (color == 1) {
            scoreCalculator.addCapturedByBlack(gameLogic.getLastCaptureCount());
        } else {
            scoreCalculator.addCapturedByWhite(gameLogic.getLastCaptureCount());
        }

        // Tạo move
        long thinkingTime = timerManager != null ? timerManager.getThinkingTime() : 0;
        long myTimeRemaining = timerManager != null ? timerManager.getTimeRemaining(isBlack) : 0;
        int order = game.getMoves() != null ? game.getMoves().size() + 1 : 1;
        String playerColor = isBlack ? "BLACK" : "WHITE";
        Moves move = new Moves(order, playerColor, gridX, gridY, game.getGameId(), myTimeRemaining, thinkingTime);

        // Reset timer cho lượt tiếp theo
        if (timerManager != null) {
            timerManager.resetTurnStartTime();
        }

        consecutivePasses = 0;
        saveGameState(move);

        // Cập nhật giao diện
        boardRenderer.drawBoard();
        updateTurnIndicator();
        updateCapturedStones();

        // Xử lý tùy theo loại game
        if (isAIGame) {
            handleAIResponse(gridX, gridY);
        } else {
            p2pHandler.sendMoveToOpponent(move, order);
        }
    }

    // ==================== AI RESPONSE ====================

    private void handleAIResponse(int playerX, int playerY) {
        aiHandler.handleAIResponse(playerX, playerY, new GameAIHandler.AIResponseCallback() {
            @Override
            public void onAIMove(int x, int y, boolean hasCapture, int captureCount) {
                if (hasCapture) {
                    int aiColor = isBlack ? 2 : 1;
                    if (aiColor == 1) {
                        scoreCalculator.addCapturedByBlack(captureCount);
                    } else {
                        scoreCalculator.addCapturedByWhite(captureCount);
                    }
                }

                // Tạo và lưu move của AI
                int aiOrder = game.getMoves() != null ? game.getMoves().size() + 1 : 1;
                Moves aiMoveObj = aiHandler.createAIMove(x, y, aiOrder);
                saveGameState(aiMoveObj);

                // Cập nhật UI
                boardRenderer.drawBoard();
                updateTurnIndicator();
                updateCapturedStones();
            }

            @Override
            public void onAIPass() {
                showAlert("AI Pass", "AI đã pass.");
            }

            @Override
            public void onAIError(String message) {
                showAlert("Lỗi AI", message);
            }
        });
    }

    // ==================== NHẬN NƯỚC ĐI TỪ ĐỐI THỦ ====================

    private void onRemoteMoveReceived(Moves move) {
        if (move == null || game == null)
            return;
        if (move.getGameId() == null || !move.getGameId().equals(game.getGameId()))
            return;

        int mx = move.getX();
        int my = move.getY();
        int color = "BLACK".equals(move.getPlayer()) ? 1 : 2;

        // Cập nhật thời gian của đối thủ
        long opponentTimeRemaining = move.getPlayerTimeRemainingMs();
        if (opponentTimeRemaining > 0 && timerManager != null) {
            if (color == 1) {
                timerManager.setBlackTimeMs(opponentTimeRemaining);
            } else {
                timerManager.setWhiteTimeMs(opponentTimeRemaining);
            }
            game.setBlackTimeMs(timerManager.getBlackTimeMs());
            game.setWhiteTimeMs(timerManager.getWhiteTimeMs());
        }

        if (timerManager != null) {
            timerManager.resetTurnStartTime();
        }

        if (mx >= 0 && my >= 0) {
            consecutivePasses = 0;
            if (!gameLogic.applyMove(mx, my, color, false)) {
                return;
            }

            if (color == 1) {
                scoreCalculator.addCapturedByBlack(gameLogic.getLastCaptureCount());
            } else {
                scoreCalculator.addCapturedByWhite(gameLogic.getLastCaptureCount());
            }
        } else {
            consecutivePasses++;
            if (consecutivePasses >= 2) {
                Platform.runLater(this::onGameEnd);
                return;
            }
        }

        // Lưu trạng thái
        game.setCapturedByBlack(scoreCalculator.getCapturedByBlack());
        game.setCapturedByWhite(scoreCalculator.getCapturedByWhite());
        if (!isAIGame) {
            GameHistoryStorage.upsert(game);
        }

        Platform.runLater(() -> {
            boardRenderer.drawBoard();
            updateTurnIndicator();
            updateCapturedStones();
            if (timerManager != null) {
                timerManager.updateTimeLabels();
            }
        });
    }

    // ==================== CẬP NHẬT GIAO DIỆN ====================

    private void updateTurnIndicator() {
        String currentTurnId = game.getCurrentPlayerId();
        boolean blackTurn = currentTurnId != null && currentTurnId.equals(game.getUserId());

        lblBlackTurn.setVisible(blackTurn);
        lblWhiteTurn.setVisible(!blackTurn);

        if (blackTurn) {
            lblTurnIndicator.setText("Turn: BLACK ●");
            lblTurnIndicator.setStyle(
                    "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: #1e293b; -fx-padding: 6 12; -fx-background-radius: 999;");
        } else {
            lblTurnIndicator.setText("Turn: WHITE ○");
            lblTurnIndicator.setStyle(
                    "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-background-color: #e2e8f0; -fx-padding: 6 12; -fx-background-radius: 999;");
        }
    }

    private void updateCapturedStones() {
        lblBlackCaptured.setText(String.valueOf(scoreCalculator.getCapturedByBlack()));
        lblWhiteCaptured.setText(String.valueOf(scoreCalculator.getCapturedByWhite()));
    }

    // ==================== LƯU TRẠNG THÁI ====================

    private void saveGameState(Moves move) {
        game.addMove(move);
        game.setBlackTimeMs(timerManager != null ? timerManager.getBlackTimeMs() : 0);
        game.setWhiteTimeMs(timerManager != null ? timerManager.getWhiteTimeMs() : 0);
        game.setCapturedByBlack(scoreCalculator.getCapturedByBlack());
        game.setCapturedByWhite(scoreCalculator.getCapturedByWhite());
        if (!isAIGame) {
            GameHistoryStorage.upsert(game);
        }
    }

    // ==================== NÚT ĐIỀU KHIỂN ====================

    @FXML
    private void onPass() {
        if (viewOnly)
            return;

        String currentTurnId = game.getCurrentPlayerId();
        if (localPlayerId == null || !localPlayerId.equals(currentTurnId)) {
            showAlert("Chưa đến lượt", "Vui lòng chờ đến lượt của bạn.");
            return;
        }

        consecutivePasses++;

        long thinkingTime = timerManager != null ? timerManager.getThinkingTime() : 0;
        long myTimeRemaining = timerManager != null ? timerManager.getTimeRemaining(isBlack) : 0;
        int order = game.getMoves() != null ? game.getMoves().size() + 1 : 1;
        String playerColor = isBlack ? "BLACK" : "WHITE";
        Moves passMove = new Moves(order, playerColor, -1, -1, game.getGameId(), myTimeRemaining, thinkingTime);

        if (timerManager != null) {
            timerManager.resetTurnStartTime();
        }

        saveGameState(passMove);
        updateTurnIndicator();

        if (p2pHandler != null) {
            p2pHandler.sendMoveToOpponent(passMove, order);
        }

        if (consecutivePasses >= 2) {
            onGameEnd();
        } else {
            showAlert("Pass", "Bạn đã pass lượt này.");
        }
    }

    @FXML
    private void onSurrender() {
        if (viewOnly)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận đầu hàng");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc chắn muốn đầu hàng không?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                game.setStatus(GameStatus.FINISHED);
                game.setEndedAt(System.currentTimeMillis());
                game.setBlackTimeMs(timerManager != null ? timerManager.getBlackTimeMs() : 0);
                game.setWhiteTimeMs(timerManager != null ? timerManager.getWhiteTimeMs() : 0);
                game.setCapturedByBlack(scoreCalculator.getCapturedByBlack());
                game.setCapturedByWhite(scoreCalculator.getCapturedByWhite());
                if (!isAIGame) {
                    GameHistoryStorage.upsert(game);
                }

                if (p2pHandler != null) {
                    p2pHandler.notifyOpponent("SURRENDER",
                            timerManager != null ? timerManager.getBlackTimeMs() : 0,
                            timerManager != null ? timerManager.getWhiteTimeMs() : 0);
                }

                if (timerManager != null) {
                    timerManager.stopTimer();
                }

                String winner = isBlack ? "WHITE" : "BLACK";
                showAlert("Game Over", "You resigned. " + winner + " wins!");

                HelloApplication.navigateTo("rooms.fxml");
            }
        });
    }

    @FXML
    private void onExit() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Thoát game");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc chắn muốn thoát không? Game sẽ được lưu và đối thủ sẽ được thông báo.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                game.setBlackTimeMs(timerManager != null ? timerManager.getBlackTimeMs() : 0);
                game.setWhiteTimeMs(timerManager != null ? timerManager.getWhiteTimeMs() : 0);
                game.setCapturedByBlack(scoreCalculator.getCapturedByBlack());
                game.setCapturedByWhite(scoreCalculator.getCapturedByWhite());
                game.setStatus(GameStatus.PAUSED);
                if (!isAIGame) {
                    GameHistoryStorage.upsert(game);
                }

                if (p2pHandler != null) {
                    p2pHandler.notifyOpponent("EXIT",
                            timerManager != null ? timerManager.getBlackTimeMs() : 0,
                            timerManager != null ? timerManager.getWhiteTimeMs() : 0);
                }

                if (timerManager != null) {
                    timerManager.stopTimer();
                }

                HelloApplication.navigateTo("dashboard.fxml");
            }
        });
    }

    @FXML
    private void onBack() {
        if (timerManager != null) {
            timerManager.stopTimer();
        }
        if (chatHandler != null) {
            chatHandler.close();
        }
        HelloApplication.navigateTo("history.fxml");
    }

    // ==================== GAME END & SCORING ====================

    private void onGameEnd() {
        if (timerManager != null) {
            timerManager.stopTimer();
        }

        boolean isHost = localPlayerId != null && localPlayerId.equals(game.getHostPeerId());

        if (isAIGame) {
            String result = scoreCalculator.calculateWithAI();
            showGameResult(result);
        } else if (isHost) {
            showAlert("Tính điểm", "Đang tính điểm bằng AI...\nVui lòng đợi.");
            String result = scoreCalculator.calculateScoreAsHost();
            p2pHandler.sendScoreResultToRival(result);
            showGameResult(result);
        } else {
            showAlert("Chờ kết quả", "Đang chờ host tính điểm...\nVui lòng đợi.");
            GameContext.getInstance().setScoreResultListener(scoreResult -> {
                Platform.runLater(() -> showGameResult(scoreResult));
            });
        }
    }

    private void showGameResult(String result) {
        game.setStatus(GameStatus.FINISHED);
        game.setEndedAt(System.currentTimeMillis());
        game.setScoreResult(result);
        game.setCapturedByBlack(scoreCalculator.getCapturedByBlack());
        game.setCapturedByWhite(scoreCalculator.getCapturedByWhite());
        if (!isAIGame) {
            GameHistoryStorage.upsert(game);
            updatePlayerRanks(result);
        }

        Alert resultDialog = new Alert(Alert.AlertType.INFORMATION);
        resultDialog.setTitle("Kết quả");
        resultDialog.setHeaderText(null);
        resultDialog.setContentText(result);
        resultDialog.showAndWait();

        if (chatHandler != null) {
            chatHandler.close();
        }

        HelloApplication.navigateTo("dashboard.fxml");
    }

    private void updatePlayerRanks(String result) {
        try {
            P2PNode node = P2PContext.getInstance().getOrCreateNode();
            User localUser = node != null ? node.getLocalUser() : null;
            if (localUser == null)
                return;

            boolean blackWins = result.startsWith("B+") || result.contains("ĐEN") || result.contains("Đen");
            boolean whiteWins = result.startsWith("W+") || result.contains("TRẮNG") || result.contains("Trắng");

            int rankChange = 0;
            if (blackWins && isBlack) {
                rankChange = 10;
            } else if (whiteWins && !isBlack) {
                rankChange = 10;
            } else if (blackWins || whiteWins) {
                rankChange = -10;
            }

            int oldRank = localUser.getRank();
            int newRank = Math.max(0, oldRank + rankChange);

            localUser.setRank(newRank);
            org.example.dacs4_v2.data.UserStorage.saveUser(localUser);

            System.out.println("[Rank] Cập nhật rank: " + oldRank + " → " + newRank +
                    " (" + (rankChange >= 0 ? "+" : "") + rankChange + ")");
        } catch (Exception e) {
            System.out.println("[Rank] Lỗi cập nhật rank: " + e.getMessage());
        }
    }

    // ==================== CHAT ====================

    @FXML
    private void onToggleChat() {
        if (chatHandler != null) {
            chatHandler.toggleChat();
        }
    }

    private void onSendChatMessage(String message) {
        P2PNode node = P2PContext.getInstance().getOrCreateNode();
        String senderName = node != null && node.getLocalUser() != null
                ? node.getLocalUser().getName()
                : "Bạn";

        chatHandler.addMessage(senderName, message, true);

        if (p2pHandler != null) {
            p2pHandler.sendChatToOpponent(message);
        }
    }

    public void onChatMessageReceived(String senderName, String message) {
        if (chatHandler != null) {
            chatHandler.addMessage(senderName, message, false);
        }
    }

    // ==================== UTILITY ====================

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
