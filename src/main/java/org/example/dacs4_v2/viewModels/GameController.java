package org.example.dacs4_v2.viewModels;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
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
import org.example.dacs4_v2.network.rmi.GoGameServiceImpl;
import org.example.dacs4_v2.network.rmi.IGoGameService;

/**
 * Controller cho màn hình chơi game cờ vây.
 * Chỉ chứa logic UI, logic luật chơi được tách ra GoGameLogic.
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
    private Button btnPass;
    @FXML
    private Button btnSurrender;
    @FXML
    private Button btnExit;

    // ==================== TRẠNG THÁI GAME ====================
    private Game game; // Đối tượng Game hiện tại
    private GoGameLogic gameLogic; // Logic luật cờ vây (tách riêng)
    private String localPlayerId; // ID người chơi local
    private boolean isBlack; // true nếu local player là quân đen
    private boolean viewOnly; // true nếu chỉ xem, không chơi

    // ==================== CÀI ĐẶT VẼ BÀN CỜ ====================
    private double cellSize; // Kích thước mỗi ô
    private double padding; // Khoảng cách viền
    private double boardPixelSize; // Kích thước bàn cờ (pixel)

    // ==================== QUÂN BỊ BẮT ====================
    private int capturedByBlack = 0; // Số quân trắng bị đen bắt
    private int capturedByWhite = 0; // Số quân đen bị trắng bắt

    // ==================== TIMER ====================
    private long blackTimeMs; // Thời gian còn lại của quân đen (ms)
    private long whiteTimeMs; // Thời gian còn lại của quân trắng (ms)
    private long lastTickTime; // Thời điểm tick cuối cùng
    private AnimationTimer gameTimer; // Timer đếm ngược
    private long turnStartTime; // Thời điểm bắt đầu lượt hiện tại

    // ==================== KHỞI TẠO ====================

    /**
     * Khởi tạo màn hình game.
     * Được gọi tự động bởi JavaFX sau khi load FXML.
     */
    @FXML
    public void initialize() {
        // Lấy game từ context
        game = GameContext.getInstance().getCurrentGame();
        viewOnly = GameContext.getInstance().isViewOnly();

        if (game == null) {
            HelloApplication.navigateTo("dashboard.fxml");
            return;
        }

        // Load thời gian từ game (hỗ trợ resume)
        blackTimeMs = game.getBlackTimeMs();
        whiteTimeMs = game.getWhiteTimeMs();

        // Load số quân bị bắt từ game (hỗ trợ resume)
        capturedByBlack = game.getCapturedByBlack();
        capturedByWhite = game.getCapturedByWhite();

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

        // Thiết lập giao diện
        setupHeader();
        setupPlayerPanels();
        setupBoard();

        // Replay các nước đi từ lịch sử (nếu resume game)
        replayMovesFromHistory();

        // Vẽ bàn cờ ban đầu
        drawBoard();
        updateTurnIndicator();
        updateCapturedStones();

        // Thiết lập listener nhận nước đi từ đối thủ
        if (!viewOnly) {
            GameContext.getInstance().setMoveListener(this::onRemoteMoveReceived);
            turnStartTime = System.currentTimeMillis();
            startTimer();
        }

        // Vô hiệu hóa nút nếu chỉ xem
        if (viewOnly) {
            btnPass.setDisable(true);
            btnSurrender.setDisable(true);
        }
    }

    /**
     * Replay các nước đi từ lịch sử game (dùng khi resume).
     */
    private void replayMovesFromHistory() {
        if (game.getMoves() == null || game.getMoves().isEmpty()) {
            return;
        }

        for (Moves m : game.getMoves()) {
            if (m == null || m.getX() < 0 || m.getY() < 0)
                continue; // Bỏ qua pass moves
            int color = "BLACK".equals(m.getPlayer()) ? 1 : 2;
            gameLogic.applyMove(m.getX(), m.getY(), color, false);
        }
    }

    // ==================== THIẾT LẬP GIAO DIỆN ====================

    /**
     * Thiết lập header (tên game, komi).
     */
    private void setupHeader() {
        String gameName = game.getNameGame() != null ? game.getNameGame() : "";
        lblGameName.setText("Game " + game.getGameId() + " - " + gameName);
        lblKomi.setText("Komi: " + game.getKomiAsDouble());
    }

    /**
     * Thiết lập thông tin 2 panel người chơi.
     */
    private void setupPlayerPanels() {
        User hostUser = game.getHostUser();
        User rivalUser = game.getRivalUser();

        // Host chơi quân đen, Rival chơi quân trắng
        User blackPlayer = hostUser;
        User whitePlayer = rivalUser;

        // Thông tin người chơi đen
        if (blackPlayer != null) {
            String name = blackPlayer.getName() != null ? blackPlayer.getName() : "Player 1";
            lblBlackName.setText(name);
            lblBlackAvatar.setText(name.isEmpty() ? "●" : name.substring(0, 1).toUpperCase());
            lblBlackRank.setText("Rank: " + (blackPlayer.getRank() > 0 ? blackPlayer.getRank() : "-"));
        }

        // Thông tin người chơi trắng
        if (whitePlayer != null) {
            String name = whitePlayer.getName() != null ? whitePlayer.getName() : "Player 2";
            lblWhiteName.setText(name);
            lblWhiteAvatar.setText(name.isEmpty() ? "○" : name.substring(0, 1).toUpperCase());
            lblWhiteRank.setText("Rank: " + (whitePlayer.getRank() > 0 ? whitePlayer.getRank() : "-"));
        }

        // Highlight panel của người chơi local
        if (isBlack) {
            panelBlack.setStyle(
                    panelBlack.getStyle() + "-fx-border-color: #3b82f6; -fx-border-width: 3; -fx-border-radius: 0;");
        } else {
            panelWhite.setStyle(
                    panelWhite.getStyle() + "-fx-border-color: #64748b; -fx-border-width: 3; -fx-border-radius: 0;");
        }
    }

    /**
     * Thiết lập canvas bàn cờ.
     */
    private void setupBoard() {
        boardPixelSize = 500;
        padding = 25;
        cellSize = (boardPixelSize - 2 * padding) / (gameLogic.getBoardSize() - 1);

        boardCanvas.setWidth(boardPixelSize);
        boardCanvas.setHeight(boardPixelSize);

        // Thêm handler click chuột
        boardCanvas.setOnMouseClicked(this::onBoardClicked);
    }

    // ==================== VẼ BÀN CỜ ====================

    /**
     * Vẽ toàn bộ bàn cờ: nền gỗ, lưới, star points, quân cờ.
     */
    private void drawBoard() {
        GraphicsContext gc = boardCanvas.getGraphicsContext2D();
        int boardSize = gameLogic.getBoardSize();

        // Xóa canvas
        gc.clearRect(0, 0, boardPixelSize, boardPixelSize);

        // Vẽ nền gỗ
        gc.setFill(Color.web("#dcb35c"));
        gc.fillRect(0, 0, boardPixelSize, boardPixelSize);

        // Vẽ vân gỗ
        gc.setStroke(Color.web("#c9a24d"));
        gc.setLineWidth(0.5);
        for (int i = 0; i < boardPixelSize; i += 15) {
            gc.strokeLine(0, i, boardPixelSize, i + 5);
        }

        // Vẽ lưới
        gc.setStroke(Color.web("#3d2914"));
        gc.setLineWidth(1.0);
        for (int i = 0; i < boardSize; i++) {
            double pos = padding + i * cellSize;
            gc.strokeLine(pos, padding, pos, padding + (boardSize - 1) * cellSize);
            gc.strokeLine(padding, pos, padding + (boardSize - 1) * cellSize, pos);
        }

        // Vẽ star points (hoshi)
        gc.setFill(Color.web("#3d2914"));
        int[] starPoints = gameLogic.getStarPoints();
        for (int i = 0; i < starPoints.length; i += 2) {
            double x = padding + starPoints[i] * cellSize;
            double y = padding + starPoints[i + 1] * cellSize;
            gc.fillOval(x - 4, y - 4, 8, 8);
        }

        // Vẽ quân cờ
        int[][] board = gameLogic.getBoard();
        for (int y = 0; y < boardSize; y++) {
            for (int x = 0; x < boardSize; x++) {
                if (board[x][y] != 0) {
                    drawStone(gc, x, y, board[x][y] == 1);
                }
            }
        }

        // Highlight nước đi cuối
        highlightLastMove(gc);
    }

    /**
     * Vẽ một quân cờ với hiệu ứng gradient và shadow.
     */
    private void drawStone(GraphicsContext gc, int gridX, int gridY, boolean isBlackStone) {
        double x = padding + gridX * cellSize;
        double y = padding + gridY * cellSize;
        double radius = cellSize * 0.45;

        // Bóng đổ
        gc.setFill(Color.rgb(0, 0, 0, 0.3));
        gc.fillOval(x - radius + 2, y - radius + 2, radius * 2, radius * 2);

        // Màu quân với gradient
        if (isBlackStone) {
            RadialGradient gradient = new RadialGradient(
                    0, 0, x - radius * 0.3, y - radius * 0.3, radius * 1.5, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#4a4a4a")),
                    new Stop(1, Color.web("#1a1a1a")));
            gc.setFill(gradient);
        } else {
            RadialGradient gradient = new RadialGradient(
                    0, 0, x - radius * 0.3, y - radius * 0.3, radius * 1.5, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.WHITE),
                    new Stop(1, Color.web("#d4d4d4")));
            gc.setFill(gradient);
        }
        gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);

        // Highlight
        gc.setFill(Color.rgb(255, 255, 255, isBlackStone ? 0.1 : 0.4));
        gc.fillOval(x - radius * 0.6, y - radius * 0.6, radius * 0.5, radius * 0.3);
    }

    /**
     * Highlight nước đi cuối cùng bằng vòng tròn đỏ.
     */
    private void highlightLastMove(GraphicsContext gc) {
        if (game.getMoves() == null || game.getMoves().isEmpty())
            return;

        Moves lastMove = game.getMoves().get(game.getMoves().size() - 1);
        if (lastMove == null || lastMove.getX() < 0)
            return; // Bỏ qua pass

        double x = padding + lastMove.getX() * cellSize;
        double y = padding + lastMove.getY() * cellSize;
        gc.setStroke(Color.RED);
        gc.setLineWidth(2);
        gc.strokeOval(x - cellSize / 4, y - cellSize / 4, cellSize / 2, cellSize / 2);
    }

    // ==================== XỬ LÝ CLICK BÀN CỜ ====================

    /**
     * Xử lý khi người chơi click vào bàn cờ.
     */
    private void onBoardClicked(MouseEvent event) {
        if (game == null || viewOnly)
            return;

        // Chuyển đổi tọa độ chuột sang tọa độ lưới
        int gridX = (int) Math.round((event.getX() - padding) / cellSize);
        int gridY = (int) Math.round((event.getY() - padding) / cellSize);

        // Kiểm tra vị trí hợp lệ
        if (!gameLogic.isValidPosition(gridX, gridY))
            return;

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
            capturedByBlack += gameLogic.getLastCaptureCount();
        } else {
            capturedByWhite += gameLogic.getLastCaptureCount();
        }

        // Tính thời gian suy nghĩ
        long currentTime = System.currentTimeMillis();
        long thinkingTime = currentTime - turnStartTime;
        long myTimeRemaining = isBlack ? blackTimeMs : whiteTimeMs;

        // Tạo move với thông tin thời gian
        int order = game.getMoves() != null ? game.getMoves().size() + 1 : 1;
        String playerColor = isBlack ? "BLACK" : "WHITE";
        Moves move = new Moves(order, playerColor, gridX, gridY, game.getGameId(), myTimeRemaining, thinkingTime);

        // Reset thời điểm cho lượt tiếp theo
        turnStartTime = currentTime;

        // Lưu trạng thái game
        saveGameState(move);

        // Cập nhật giao diện
        drawBoard();
        updateTurnIndicator();
        updateCapturedStones();

        // Gửi nước đi cho đối thủ
        sendMoveToOpponent(move, order);
    }

    /**
     * Lưu trạng thái game vào storage.
     */
    private void saveGameState(Moves move) {
        game.addMove(move);
        game.setBlackTimeMs(blackTimeMs);
        game.setWhiteTimeMs(whiteTimeMs);
        game.setCapturedByBlack(capturedByBlack);
        game.setCapturedByWhite(capturedByWhite);
        GameHistoryStorage.upsert(game);
    }

    /**
     * Gửi nước đi cho đối thủ qua RMI.
     */
    private void sendMoveToOpponent(Moves move, int order) {
        new Thread(() -> {
            try {
                P2PNode node = P2PContext.getInstance().getOrCreateNode();
                String myId = node.getLocalUser() != null ? node.getLocalUser().getUserId() : null;

                User rival = null;
                if (myId != null && myId.equals(game.getUserId())) {
                    rival = game.getRivalUser();
                } else {
                    rival = game.getHostUser();
                }

                if (rival != null) {
                    IGoGameService remote = GoGameServiceImpl.getStub(rival);
                    remote.submitMove(move, order);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "submit-move-thread").start();
    }

    // ==================== NHẬN NƯỚC ĐI TỪ ĐỐI THỦ ====================

    /**
     * Xử lý khi nhận nước đi từ đối thủ qua RMI.
     */
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
        if (opponentTimeRemaining > 0) {
            if (color == 1) {
                blackTimeMs = opponentTimeRemaining;
            } else {
                whiteTimeMs = opponentTimeRemaining;
            }
            game.setBlackTimeMs(blackTimeMs);
            game.setWhiteTimeMs(whiteTimeMs);
        }

        // Reset thời điểm bắt đầu lượt của mình
        turnStartTime = System.currentTimeMillis();

        // Áp dụng nước đi (nếu không phải pass)
        if (mx >= 0 && my >= 0) {
            if (!gameLogic.applyMove(mx, my, color, false)) {
                return;
            }

            // Cập nhật số quân bị bắt
            if (color == 1) {
                capturedByBlack += gameLogic.getLastCaptureCount();
            } else {
                capturedByWhite += gameLogic.getLastCaptureCount();
            }
        }

        // Lưu trạng thái
        game.setCapturedByBlack(capturedByBlack);
        game.setCapturedByWhite(capturedByWhite);
        GameHistoryStorage.upsert(game);

        // Cập nhật giao diện
        Platform.runLater(() -> {
            drawBoard();
            updateTurnIndicator();
            updateCapturedStones();
            lblBlackTime.setText(formatTime(blackTimeMs));
            lblWhiteTime.setText(formatTime(whiteTimeMs));
        });
    }

    // ==================== CẬP NHẬT GIAO DIỆN ====================

    /**
     * Cập nhật chỉ báo lượt đi.
     */
    private void updateTurnIndicator() {
        String currentTurnId = game.getCurrentPlayerId();
        boolean blackTurn = currentTurnId != null && currentTurnId.equals(game.getUserId());

        lblBlackTurn.setVisible(blackTurn);
        lblWhiteTurn.setVisible(!blackTurn);

        if (blackTurn) {
            lblTurnIndicator.setText("Lượt: ĐEN ●");
            lblTurnIndicator.setStyle(
                    "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: #1e293b; -fx-padding: 6 12; -fx-background-radius: 999;");
        } else {
            lblTurnIndicator.setText("Lượt: TRẮNG ○");
            lblTurnIndicator.setStyle(
                    "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-background-color: #e2e8f0; -fx-padding: 6 12; -fx-background-radius: 999;");
        }
    }

    /**
     * Cập nhật số quân bị bắt trên giao diện.
     */
    private void updateCapturedStones() {
        lblBlackCaptured.setText(String.valueOf(capturedByBlack));
        lblWhiteCaptured.setText(String.valueOf(capturedByWhite));
    }

    // ==================== TIMER ====================

    /**
     * Bắt đầu timer đếm ngược.
     */
    private void startTimer() {
        lastTickTime = System.currentTimeMillis();
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
     * Format thời gian từ milliseconds sang mm:ss.
     */
    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // ==================== NÚT ĐIỀU KHIỂN ====================

    /**
     * Xử lý khi nhấn nút Pass.
     */
    @FXML
    private void onPass() {
        if (viewOnly)
            return;

        // Kiểm tra lượt đi
        String currentTurnId = game.getCurrentPlayerId();
        if (localPlayerId == null || !localPlayerId.equals(currentTurnId)) {
            showAlert("Chưa đến lượt", "Vui lòng chờ đến lượt của bạn.");
            return;
        }

        // Tính thời gian suy nghĩ
        long currentTime = System.currentTimeMillis();
        long thinkingTime = currentTime - turnStartTime;
        long myTimeRemaining = isBlack ? blackTimeMs : whiteTimeMs;

        // Tạo pass move (x=-1, y=-1)
        int order = game.getMoves() != null ? game.getMoves().size() + 1 : 1;
        String playerColor = isBlack ? "BLACK" : "WHITE";
        Moves passMove = new Moves(order, playerColor, -1, -1, game.getGameId(), myTimeRemaining, thinkingTime);

        // Reset thời điểm
        turnStartTime = currentTime;

        // Lưu trạng thái
        saveGameState(passMove);

        // Cập nhật giao diện
        updateTurnIndicator();

        // Gửi cho đối thủ
        sendMoveToOpponent(passMove, order);

        showAlert("Pass", "Bạn đã pass lượt này.");
    }

    /**
     * Xử lý khi nhấn nút Đầu hàng.
     */
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
                // Cập nhật trạng thái game
                game.setStatus(GameStatus.FINISHED);
                game.setEndedAt(System.currentTimeMillis());
                game.setBlackTimeMs(blackTimeMs);
                game.setWhiteTimeMs(whiteTimeMs);
                game.setCapturedByBlack(capturedByBlack);
                game.setCapturedByWhite(capturedByWhite);
                GameHistoryStorage.upsert(game);

                // Dừng timer
                if (gameTimer != null) {
                    gameTimer.stop();
                }

                // Thông báo kết quả
                String winner = isBlack ? "TRẮNG" : "ĐEN";
                showAlert("Kết thúc", "Bạn đã đầu hàng. " + winner + " thắng!");

                HelloApplication.navigateTo("rooms.fxml");
            }
        });
    }

    /**
     * Xử lý khi nhấn nút Thoát game.
     */
    @FXML
    private void onExit() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Thoát game");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc chắn muốn thoát không? Game sẽ được lưu.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                // Lưu trạng thái trước khi thoát
                game.setBlackTimeMs(blackTimeMs);
                game.setWhiteTimeMs(whiteTimeMs);
                game.setCapturedByBlack(capturedByBlack);
                game.setCapturedByWhite(capturedByWhite);
                GameHistoryStorage.upsert(game);

                // Dừng timer
                if (gameTimer != null) {
                    gameTimer.stop();
                }

                HelloApplication.navigateTo("dashboard.fxml");
            }
        });
    }

    // ==================== TIỆN ÍCH ====================

    /**
     * Hiển thị dialog thông báo.
     */
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
