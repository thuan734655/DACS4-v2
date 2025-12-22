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
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.GameStatus;
import org.example.dacs4_v2.models.Moves;
import org.example.dacs4_v2.models.User;
import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.P2PNode;
import org.example.dacs4_v2.network.rmi.GoGameServiceImpl;
import org.example.dacs4_v2.network.rmi.IGoGameService;

public class GameController {

    // Header
    @FXML
    private Label lblGameName;
    @FXML
    private Label lblKomi;
    @FXML
    private Label lblTurnIndicator;

    // Black Player Panel
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

    // White Player Panel
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

    // Board
    @FXML
    private StackPane boardContainer;
    @FXML
    private Canvas boardCanvas;

    // Control Buttons
    @FXML
    private Button btnPass;
    @FXML
    private Button btnSurrender;
    @FXML
    private Button btnExit;

    // Game state
    private Game game;
    private String localPlayerId;
    private boolean isBlack;
    private boolean viewOnly;
    private int[][] board;
    private int[][] prevBoard;
    private int boardSize;

    // Board rendering
    private double cellSize;
    private double padding;
    private double boardPixelSize;

    // Captured stones count
    private int capturedByBlack = 0; // Quân trắng bị đen bắt
    private int capturedByWhite = 0; // Quân đen bị trắng bắt

    // Timer
    private long blackTimeMs = 10 * 60 * 1000; // 10 phút
    private long whiteTimeMs = 10 * 60 * 1000;
    private long lastTickTime;
    private AnimationTimer gameTimer;

    @FXML
    public void initialize() {
        game = GameContext.getInstance().getCurrentGame();
        viewOnly = GameContext.getInstance().isViewOnly();

        if (game == null) {
            HelloApplication.navigateTo("dashboard.fxml");
            return;
        }

        boardSize = game.getBoardSize();

        try {
            P2PNode node = P2PContext.getInstance().getOrCreateNode();
            localPlayerId = node.getLocalUser() != null ? node.getLocalUser().getUserId() : null;
        } catch (Exception e) {
            e.printStackTrace();
        }

        isBlack = localPlayerId != null && localPlayerId.equals(game.getUserId());

        // Initialize board state
        board = new int[boardSize][boardSize];
        prevBoard = null;

        // Setup UI
        setupHeader();
        setupPlayerPanels();
        setupBoard();

        // Replay moves from history
        if (game.getMoves() != null && !game.getMoves().isEmpty()) {
            for (Moves m : game.getMoves()) {
                if (m == null)
                    continue;
                int color = "BLACK".equals(m.getPlayer()) ? 1 : 2;
                applyMoveWithRules(m.getX(), m.getY(), color, false);
            }
        }

        // Draw initial board
        drawBoard();
        updateTurnIndicator();
        updateCapturedStones();

        // Setup move listener
        if (!viewOnly) {
            GameContext.getInstance().setMoveListener(this::onRemoteMoveReceived);
            startTimer();
        }

        // Disable controls in view-only mode
        if (viewOnly) {
            btnPass.setDisable(true);
            btnSurrender.setDisable(true);
        }
    }

    private void setupHeader() {
        String gameName = game.getNameGame() != null ? game.getNameGame() : "";
        lblGameName.setText("Game " + game.getGameId() + " - " + gameName);
        lblKomi.setText("Komi: " + game.getKomiAsDouble());
    }

    private void setupPlayerPanels() {
        User hostUser = game.getHostUser();
        User rivalUser = game.getRivalUser();

        // Determine who is black and who is white
        User blackPlayer = hostUser; // Host plays black (userId)
        User whitePlayer = rivalUser;

        // Black player info
        if (blackPlayer != null) {
            String name = blackPlayer.getName() != null ? blackPlayer.getName() : "Player 1";
            lblBlackName.setText(name);
            lblBlackAvatar.setText(name.isEmpty() ? "●" : name.substring(0, 1).toUpperCase());
            lblBlackRank.setText("Rank: " + (blackPlayer.getRank() > 0 ? blackPlayer.getRank() : "-"));
        }

        // White player info
        if (whitePlayer != null) {
            String name = whitePlayer.getName() != null ? whitePlayer.getName() : "Player 2";
            lblWhiteName.setText(name);
            lblWhiteAvatar.setText(name.isEmpty() ? "○" : name.substring(0, 1).toUpperCase());
            lblWhiteRank.setText("Rank: " + (whitePlayer.getRank() > 0 ? whitePlayer.getRank() : "-"));
        }

        // Highlight local player's panel
        if (isBlack) {
            panelBlack.setStyle(
                    panelBlack.getStyle() + "-fx-border-color: #4ade80; -fx-border-width: 3; -fx-border-radius: 0;");
        } else {
            panelWhite.setStyle(
                    panelWhite.getStyle() + "-fx-border-color: #f472b6; -fx-border-width: 3; -fx-border-radius: 0;");
        }
    }

    private void setupBoard() {
        // Calculate board dimensions
        boardPixelSize = 500;
        padding = 25;
        cellSize = (boardPixelSize - 2 * padding) / (boardSize - 1);

        // Setup canvas size
        boardCanvas.setWidth(boardPixelSize);
        boardCanvas.setHeight(boardPixelSize);

        // Add mouse click handler
        boardCanvas.setOnMouseClicked(this::onBoardClicked);
    }

    private void drawBoard() {
        GraphicsContext gc = boardCanvas.getGraphicsContext2D();

        // Clear canvas
        gc.clearRect(0, 0, boardPixelSize, boardPixelSize);

        // Draw wood background
        gc.setFill(Color.web("#c4a26a"));
        gc.fillRect(0, 0, boardPixelSize, boardPixelSize);

        // Draw wood grain effect (simple lines)
        gc.setStroke(Color.web("#b8956a"));
        gc.setLineWidth(0.5);
        for (int i = 0; i < boardPixelSize; i += 15) {
            gc.strokeLine(0, i, boardPixelSize, i + 5);
        }

        // Draw grid lines
        gc.setStroke(Color.web("#3d2914"));
        gc.setLineWidth(1.0);

        for (int i = 0; i < boardSize; i++) {
            double pos = padding + i * cellSize;
            // Vertical lines
            gc.strokeLine(pos, padding, pos, padding + (boardSize - 1) * cellSize);
            // Horizontal lines
            gc.strokeLine(padding, pos, padding + (boardSize - 1) * cellSize, pos);
        }

        // Draw star points (hoshi)
        gc.setFill(Color.web("#3d2914"));
        int[] starPoints = getStarPoints();
        for (int i = 0; i < starPoints.length; i += 2) {
            double x = padding + starPoints[i] * cellSize;
            double y = padding + starPoints[i + 1] * cellSize;
            gc.fillOval(x - 4, y - 4, 8, 8);
        }

        // Draw stones
        for (int y = 0; y < boardSize; y++) {
            for (int x = 0; x < boardSize; x++) {
                if (board[x][y] != 0) {
                    drawStone(gc, x, y, board[x][y] == 1);
                }
            }
        }

        // Highlight last move
        if (game.getMoves() != null && !game.getMoves().isEmpty()) {
            Moves lastMove = game.getMoves().get(game.getMoves().size() - 1);
            if (lastMove != null) {
                double x = padding + lastMove.getX() * cellSize;
                double y = padding + lastMove.getY() * cellSize;
                gc.setStroke(Color.RED);
                gc.setLineWidth(2);
                gc.strokeOval(x - cellSize / 4, y - cellSize / 4, cellSize / 2, cellSize / 2);
            }
        }
    }

    private void drawStone(GraphicsContext gc, int gridX, int gridY, boolean isBlackStone) {
        double x = padding + gridX * cellSize;
        double y = padding + gridY * cellSize;
        double radius = cellSize * 0.45;

        // Stone shadow
        gc.setFill(Color.rgb(0, 0, 0, 0.3));
        gc.fillOval(x - radius + 2, y - radius + 2, radius * 2, radius * 2);

        // Stone base color
        if (isBlackStone) {
            // Black stone with gradient
            RadialGradient gradient = new RadialGradient(
                    0, 0, x - radius * 0.3, y - radius * 0.3, radius * 1.5, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#4a4a4a")),
                    new Stop(1, Color.web("#1a1a1a")));
            gc.setFill(gradient);
        } else {
            // White stone with gradient
            RadialGradient gradient = new RadialGradient(
                    0, 0, x - radius * 0.3, y - radius * 0.3, radius * 1.5, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.WHITE),
                    new Stop(1, Color.web("#d4d4d4")));
            gc.setFill(gradient);
        }
        gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);

        // Stone highlight
        gc.setFill(Color.rgb(255, 255, 255, isBlackStone ? 0.1 : 0.4));
        gc.fillOval(x - radius * 0.6, y - radius * 0.6, radius * 0.5, radius * 0.3);
    }

    private int[] getStarPoints() {
        if (boardSize == 9) {
            return new int[] { 2, 2, 6, 2, 4, 4, 2, 6, 6, 6 };
        } else if (boardSize == 13) {
            return new int[] { 3, 3, 9, 3, 6, 6, 3, 9, 9, 9 };
        } else if (boardSize == 19) {
            return new int[] { 3, 3, 9, 3, 15, 3, 3, 9, 9, 9, 15, 9, 3, 15, 9, 15, 15, 15 };
        }
        return new int[0];
    }

    private void onBoardClicked(MouseEvent event) {
        if (game == null || viewOnly)
            return;

        // Convert mouse coordinates to grid position
        double mouseX = event.getX();
        double mouseY = event.getY();

        int gridX = (int) Math.round((mouseX - padding) / cellSize);
        int gridY = (int) Math.round((mouseY - padding) / cellSize);

        // Validate grid position
        if (gridX < 0 || gridX >= boardSize || gridY < 0 || gridY >= boardSize)
            return;
        if (board[gridX][gridY] != 0)
            return;

        // Check if it's player's turn
        String currentTurnId = game.getCurrentPlayerId();
        if (localPlayerId == null || currentTurnId == null || !localPlayerId.equals(currentTurnId)) {
            return;
        }

        int color = isBlack ? 1 : 2;
        int capturedBefore = countCaptured(color);

        if (!applyMoveWithRules(gridX, gridY, color, true)) {
            return;
        }

        int capturedAfter = countCaptured(color);
        int newCaptures = capturedAfter - capturedBefore;
        if (color == 1) {
            capturedByBlack += newCaptures;
        } else {
            capturedByWhite += newCaptures;
        }

        // Create move
        int order = game.getMoves() != null ? game.getMoves().size() + 1 : 1;
        String playerColor = isBlack ? "BLACK" : "WHITE";
        Moves move = new Moves(order, playerColor, gridX, gridY, game.getGameId());

        // Persist
        game.addMove(move);
        GameHistoryStorage.upsert(game);

        // Update UI
        drawBoard();
        updateTurnIndicator();
        updateCapturedStones();

        // Send to opponent
        sendMoveToOpponent(move, order);
    }

    private int countCaptured(int color) {
        int count = 0;
        for (int y = 0; y < boardSize; y++) {
            for (int x = 0; x < boardSize; x++) {
                if (board[x][y] == 0)
                    count++;
            }
        }
        return count;
    }

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

    private void onRemoteMoveReceived(Moves move) {
        if (move == null || game == null)
            return;
        if (move.getGameId() == null || !move.getGameId().equals(game.getGameId()))
            return;

        int mx = move.getX();
        int my = move.getY();
        int color = "BLACK".equals(move.getPlayer()) ? 1 : 2;

        int capturedBefore = countCaptured(color);

        if (!applyMoveWithRules(mx, my, color, false)) {
            return;
        }

        int capturedAfter = countCaptured(color);
        int newCaptures = capturedAfter - capturedBefore;
        if (color == 1) {
            capturedByBlack += newCaptures;
        } else {
            capturedByWhite += newCaptures;
        }

        Platform.runLater(() -> {
            drawBoard();
            updateTurnIndicator();
            updateCapturedStones();
        });
    }

    private void updateTurnIndicator() {
        String currentTurnId = game.getCurrentPlayerId();
        boolean blackTurn = currentTurnId != null && currentTurnId.equals(game.getUserId());

        lblBlackTurn.setVisible(blackTurn);
        lblWhiteTurn.setVisible(!blackTurn);

        if (blackTurn) {
            lblTurnIndicator.setText("Lượt: ĐEN ●");
            lblTurnIndicator.setStyle(
                    "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1a1a2e; -fx-background-color: #4ade80; -fx-padding: 6 12; -fx-background-radius: 999;");
        } else {
            lblTurnIndicator.setText("Lượt: TRẮNG ○");
            lblTurnIndicator.setStyle(
                    "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1a1a2e; -fx-background-color: #f472b6; -fx-padding: 6 12; -fx-background-radius: 999;");
        }
    }

    private void updateCapturedStones() {
        lblBlackCaptured.setText(String.valueOf(capturedByBlack));
        lblWhiteCaptured.setText(String.valueOf(capturedByWhite));
    }

    private void startTimer() {
        lastTickTime = System.currentTimeMillis();
        gameTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                long currentTime = System.currentTimeMillis();
                long delta = currentTime - lastTickTime;
                lastTickTime = currentTime;

                String currentTurnId = game.getCurrentPlayerId();
                boolean blackTurn = currentTurnId != null && currentTurnId.equals(game.getUserId());

                if (blackTurn) {
                    blackTimeMs -= delta;
                    if (blackTimeMs < 0)
                        blackTimeMs = 0;
                } else {
                    whiteTimeMs -= delta;
                    if (whiteTimeMs < 0)
                        whiteTimeMs = 0;
                }

                Platform.runLater(() -> {
                    lblBlackTime.setText(formatTime(blackTimeMs));
                    lblWhiteTime.setText(formatTime(whiteTimeMs));

                    // Highlight time when low
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

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // Control button handlers
    @FXML
    private void onPass() {
        if (viewOnly)
            return;

        String currentTurnId = game.getCurrentPlayerId();
        if (localPlayerId == null || !localPlayerId.equals(currentTurnId)) {
            showAlert("Chưa đến lượt", "Vui lòng chờ đến lượt của bạn.");
            return;
        }

        // Create pass move (x=-1, y=-1 indicates pass)
        int order = game.getMoves() != null ? game.getMoves().size() + 1 : 1;
        String playerColor = isBlack ? "BLACK" : "WHITE";
        Moves passMove = new Moves(order, playerColor, -1, -1, game.getGameId());

        game.addMove(passMove);
        GameHistoryStorage.upsert(game);

        updateTurnIndicator();
        sendMoveToOpponent(passMove, order);

        showAlert("Pass", "Bạn đã pass lượt này.");
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
                GameHistoryStorage.upsert(game);

                if (gameTimer != null) {
                    gameTimer.stop();
                }

                String winner = isBlack ? "TRẮNG" : "ĐEN";
                showAlert("Kết thúc", "Bạn đã đầu hàng. " + winner + " thắng!");

                HelloApplication.navigateTo("rooms.fxml");
            }
        });
    }

    @FXML
    private void onExit() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Thoát game");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc chắn muốn thoát không? Game sẽ được lưu.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                GameHistoryStorage.upsert(game);
                if (gameTimer != null) {
                    gameTimer.stop();
                }
                HelloApplication.navigateTo("dashboard.fxml");
            }
        });
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // Go rules implementation
    private boolean applyMoveWithRules(int x, int y, int color, boolean enforceKoAndSuicide) {
        if (board == null)
            return false;
        if (x < 0 || x >= boardSize || y < 0 || y >= boardSize)
            return false;
        if (board[x][y] != 0)
            return false;

        int[][] tmp = deepCopy(board);
        tmp[x][y] = color;
        int oppColor = color == 1 ? 2 : 1;
        boolean anyCapture = false;

        int[][] dirs = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] d : dirs) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx < 0 || nx >= boardSize || ny < 0 || ny >= boardSize)
                continue;
            if (tmp[nx][ny] == oppColor) {
                if (removeGroupIfNoLiberties(tmp, nx, ny, oppColor)) {
                    anyCapture = true;
                }
            }
        }

        int libertiesAfter = countLiberties(tmp, x, y, color);
        if (enforceKoAndSuicide && libertiesAfter == 0 && !anyCapture) {
            return false;
        }

        if (enforceKoAndSuicide && prevBoard != null && boardsEqual(tmp, prevBoard)) {
            return false;
        }

        prevBoard = deepCopy(board);
        board = tmp;
        return true;
    }

    private boolean removeGroupIfNoLiberties(int[][] state, int sx, int sy, int color) {
        boolean[][] visited = new boolean[boardSize][boardSize];
        int[][] stack = new int[boardSize * boardSize][2];
        int top = 0;
        stack[top][0] = sx;
        stack[top][1] = sy;
        visited[sx][sy] = true;
        int groupCount = 0;
        boolean hasLiberty = false;

        int[][] group = new int[boardSize * boardSize][2];
        int[][] dirs = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        while (top >= 0) {
            int cx = stack[top][0];
            int cy = stack[top][1];
            top--;
            group[groupCount][0] = cx;
            group[groupCount][1] = cy;
            groupCount++;

            for (int[] d : dirs) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                if (nx < 0 || nx >= boardSize || ny < 0 || ny >= boardSize)
                    continue;
                if (state[nx][ny] == 0) {
                    hasLiberty = true;
                } else if (state[nx][ny] == color && !visited[nx][ny]) {
                    visited[nx][ny] = true;
                    top++;
                    stack[top][0] = nx;
                    stack[top][1] = ny;
                }
            }
        }

        if (hasLiberty)
            return false;

        for (int i = 0; i < groupCount; i++) {
            state[group[i][0]][group[i][1]] = 0;
        }
        return true;
    }

    private int countLiberties(int[][] state, int sx, int sy, int color) {
        boolean[][] visited = new boolean[boardSize][boardSize];
        int[][] stack = new int[boardSize * boardSize][2];
        int top = 0;
        stack[top][0] = sx;
        stack[top][1] = sy;
        visited[sx][sy] = true;
        int liberties = 0;

        int[][] dirs = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (top >= 0) {
            int cx = stack[top][0];
            int cy = stack[top][1];
            top--;

            for (int[] d : dirs) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                if (nx < 0 || nx >= boardSize || ny < 0 || ny >= boardSize)
                    continue;
                if (state[nx][ny] == 0) {
                    liberties++;
                } else if (state[nx][ny] == color && !visited[nx][ny]) {
                    visited[nx][ny] = true;
                    top++;
                    stack[top][0] = nx;
                    stack[top][1] = ny;
                }
            }
        }
        return liberties;
    }

    private int[][] deepCopy(int[][] src) {
        if (src == null)
            return null;
        int[][] dst = new int[src.length][];
        for (int i = 0; i < src.length; i++) {
            dst[i] = new int[src[i].length];
            System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
        }
        return dst;
    }

    private boolean boardsEqual(int[][] a, int[][] b) {
        if (a == null || b == null)
            return false;
        if (a.length != b.length)
            return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i].length != b[i].length)
                return false;
            for (int j = 0; j < a[i].length; j++) {
                if (a[i][j] != b[i][j])
                    return false;
            }
        }
        return true;
    }
}
