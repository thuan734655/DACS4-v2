package org.example.dacs4_v2.viewModels;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import java.util.ArrayList;
import java.util.List;
import org.example.dacs4_v2.HelloApplication;
import org.example.dacs4_v2.game.GameContext;
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.Moves;
import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.P2PNode;
import org.example.dacs4_v2.network.rmi.IGoGameService;

public class GameController {

    @FXML
    private Label lblGameInfo;

    @FXML
    private Label lblPlayerColor;

    @FXML
    private Label lblKomi;

    @FXML
    private GridPane boardGrid;

    @FXML
    private Canvas boardCanvas;

    private Game game;
    private String localPlayerId;
    private boolean isBlack;
    private int[][] board;
    private int[][] prevBoard;
    private int boardSize;

    @FXML
    public void initialize() {
        game = GameContext.getInstance().getCurrentGame();
        if (game == null) {
            // Không có game hiện tại, quay về dashboard
            HelloApplication.navigateTo("dashboard.fxml");
            return;
        }

        boardSize = game.getBoardSize();
        double komi = game.getKomiAsDouble();

        try {
            P2PNode node = P2PContext.getInstance().getOrCreateNode();
            localPlayerId = node.getLocalUser() != null ? node.getLocalUser().getUserId() : null;
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (localPlayerId != null && localPlayerId.equals(game.getUserId())) {
            isBlack = true;
        } else {
            isBlack = false;
        }

        if (lblGameInfo != null) {
            lblGameInfo.setText("Game " + game.getGameId() + " - " + game.getNameGame());
        }
        if (lblPlayerColor != null) {
            lblPlayerColor.setText(isBlack ? "You are BLACK" : "You are WHITE");
        }
        if (lblKomi != null) {
            lblKomi.setText("Komi: " + komi);
        }

        if (boardCanvas != null) {
            drawBoardGrid();
        }

        if (boardGrid != null) {
            boardGrid.getChildren().clear();
            double cellSize = 28;
            if (boardCanvas != null) {
                double size = Math.min(boardCanvas.getWidth(), boardCanvas.getHeight());
                // Dùng cùng step với lưới trên Canvas: size chia cho (boardSize - 1)
                cellSize = size / (boardSize - 1);
            }

            for (int y = 0; y < boardSize; y++) {
                for (int x = 0; x < boardSize; x++) {
                    Button cell = new Button("");
                    cell.setMinSize(cellSize, cellSize);
                    cell.setPrefSize(cellSize, cellSize);
                    cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                    cell.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-font-size: 32px;");
                    final int fx = x;
                    final int fy = y;
                    cell.setOnAction(e -> onCellClicked(fx, fy, cell));
                    boardGrid.add(cell, x, y);
                }
            }
        }

        if (board == null || board.length != boardSize) {
            board = new int[boardSize][boardSize];
        }
        prevBoard = null;
        redrawBoard();

        GameContext.getInstance().setMoveListener(this::onRemoteMoveReceived);
    }

    private void onCellClicked(int x, int y, Button cell) {
        if (game == null) return;
        if (!game.isActive()) return; // chỉ chơi khi ván đang active
        if (game.getUserId() == null || game.getRivalId() == null || game.getRivalId().isEmpty()) return; // cần đủ 2 người chơi
        if (board != null && board[x][y] != 0) return;

        String currentTurnId = game.getCurrentPlayerId();
        if (localPlayerId == null || currentTurnId == null || !localPlayerId.equals(currentTurnId)) {
            return; // chưa tới lượt mình
        }

        int color = isBlack ? 1 : 2;
        if (!applyMoveWithRules(x, y, color, true)) {
            return;
        }

        int order = game.getMoves() != null ? game.getMoves().size() + 1 : 1;
        String playerColor = isBlack ? "BLACK" : "WHITE";
        Moves move = new Moves(order, playerColor, x, y, game.getGameId());

        redrawBoard();

        new Thread(() -> {
            try {
                P2PNode node = P2PContext.getInstance().getOrCreateNode();
                IGoGameService service = node.getLocalService();
                if (service != null) {
                    service.submitMove(move, order);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    cell.setText("");
                });
            }
        }, "submit-move-thread").start();
    }

    private void onRemoteMoveReceived(Moves move) {
        if (move == null || game == null) {
            return;
        }
        if (move.getGameId() == null || !move.getGameId().equals(game.getGameId())) {
            return;
        }

        int mx = move.getX();
        int my = move.getY();
        int color = "BLACK".equals(move.getPlayer()) ? 1 : 2;
        if (!applyMoveWithRules(mx, my, color, false)) {
            return;
        }

        Platform.runLater(this::redrawBoard);
    }

    // Handler cho nút Pass: tạm thời chỉ dùng để đánh giá sống–chết và tô màu nhóm cần vote
    @FXML
    public void onPassClicked() {
        evaluateAndHighlightBoardForScoring();
    }

    private boolean applyMoveWithRules(int x, int y, int color, boolean enforceKoAndSuicide) {
        if (board == null) {
            return false;
        }
        if (x < 0 || x >= boardSize || y < 0 || y >= boardSize) {
            return false;
        }
        if (board[x][y] != 0) {
            return false;
        }

        int[][] tmp = deepCopy(board);
        tmp[x][y] = color;
        int oppColor = color == 1 ? 2 : 1;
        boolean anyCapture = false;

        int[][] dirs = new int[][] { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
        for (int[] d : dirs) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx < 0 || nx >= boardSize || ny < 0 || ny >= boardSize) {
                continue;
            }
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

        int[][] dirs = new int[][] { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
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
                if (nx < 0 || nx >= boardSize || ny < 0 || ny >= boardSize) {
                    continue;
                }
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

        if (hasLiberty) {
            return false;
        }

        for (int i = 0; i < groupCount; i++) {
            int gx = group[i][0];
            int gy = group[i][1];
            state[gx][gy] = 0;
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

        int[][] dirs = new int[][] { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
        while (top >= 0) {
            int cx = stack[top][0];
            int cy = stack[top][1];
            top--;

            for (int[] d : dirs) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                if (nx < 0 || nx >= boardSize || ny < 0 || ny >= boardSize) {
                    continue;
                }
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
        if (src == null) {
            return null;
        }
        int[][] dst = new int[src.length][];
        for (int i = 0; i < src.length; i++) {
            dst[i] = new int[src[i].length];
            System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
        }
        return dst;
    }

    private boolean boardsEqual(int[][] a, int[][] b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i].length != b[i].length) {
                return false;
            }
            for (int j = 0; j < a[i].length; j++) {
                if (a[i][j] != b[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }

    private void redrawBoard() {
        if (boardGrid == null || board == null) {
            return;
        }
        for (Node node : boardGrid.getChildren()) {
            Integer col = GridPane.getColumnIndex(node);
            Integer row = GridPane.getRowIndex(node);
            int cx = col == null ? 0 : col;
            int cy = row == null ? 0 : row;
            if (cx < 0 || cx >= boardSize || cy < 0 || cy >= boardSize) {
                continue;
            }
            if (node instanceof Button) {
                Button btn = (Button) node;
                int v = board[cx][cy];
                if (v == 1) {
                    // Quân đen – nhỏ hơn, chừa viền để không đè đường lưới
                    btn.setText("");
                    btn.setStyle(
                            "-fx-background-color: radial-gradient(center 50% 50%, radius 50%, #111827, #000000);" +
                            "-fx-background-radius: 999;" +
                            "-fx-background-insets: 4;" +
                            "-fx-padding: 4;" +
                            "-fx-border-color: transparent;"
                    );
                } else if (v == 2) {
                    // Quân trắng – nhỏ hơn, chừa viền
                    btn.setText("");
                    btn.setStyle(
                            "-fx-background-color: radial-gradient(center 50% 50%, radius 50%, #ffffff, #e5e7eb);" +
                            "-fx-background-radius: 999;" +
                            "-fx-background-insets: 4;" +
                            "-fx-padding: 4;" +
                            "-fx-border-color: transparent;"
                    );
                } else {
                    // Ô trống
                    btn.setText("");
                    btn.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0;");
                }
            }
        }
    }

    // ======== ĐÁNH GIÁ SỐNG–CHẾT (dùng sau khi kết thúc ván) ========

    /**
     * Đánh giá sơ bộ sống–chết cho toàn bộ bàn, tô màu những nhóm "chưa rõ"
     * (không có >=2 mắt thật và không đang ở trạng thái chỉ còn 1 khí).
     * Có thể gọi hàm này sau khi hai bên pass/resign để người chơi vote.
     */
    private void evaluateAndHighlightBoardForScoring() {
        if (board == null || boardGrid == null || boardSize <= 0) return;

        boolean[][] visited = new boolean[boardSize][boardSize];

        for (int x = 0; x < boardSize; x++) {
            for (int y = 0; y < boardSize; y++) {
                int color = board[x][y];
                if (color == 0 || visited[x][y]) continue;

                List<int[]> group = collectGroup(board, x, y, color, visited);
                int liberties = countLiberties(board, x, y, color);
                int trueEyes = countTrueEyes(board, group, color);

                boolean alive = trueEyes >= 2;
                boolean clearlyDead = !alive && liberties == 1;

                if (!alive && !clearlyDead) {
                    // Nhóm không chắc chắn → tô màu để người chơi vote
                    highlightGroup(group, Color.web("#facc15", 0.35)); // vàng nhạt
                }
            }
        }
    }

    private List<int[]> collectGroup(int[][] state, int sx, int sy, int color, boolean[][] visited) {
        List<int[]> group = new ArrayList<>();
        int[][] stack = new int[boardSize * boardSize][2];
        int top = 0;
        stack[top][0] = sx;
        stack[top][1] = sy;
        visited[sx][sy] = true;

        int[][] dirs = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (top >= 0) {
            int cx = stack[top][0];
            int cy = stack[top][1];
            top--;
            group.add(new int[]{cx, cy});

            for (int[] d : dirs) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                if (nx < 0 || nx >= boardSize || ny < 0 || ny >= boardSize) {
                    continue;
                }
                if (!visited[nx][ny] && state[nx][ny] == color) {
                    visited[nx][ny] = true;
                    top++;
                    stack[top][0] = nx;
                    stack[top][1] = ny;
                }
            }
        }
        return group;
    }

    private int countTrueEyes(int[][] state, List<int[]> group, int color) {
        boolean[][] eyeVisited = new boolean[boardSize][boardSize];
        int trueEyes = 0;
        int[][] dirs = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] cell : group) {
            int cx = cell[0];
            int cy = cell[1];
            for (int[] d : dirs) {
                int ex = cx + d[0];
                int ey = cy + d[1];
                if (ex < 0 || ex >= boardSize || ey < 0 || ey >= boardSize) continue;
                if (state[ex][ey] != 0 || eyeVisited[ex][ey]) continue;
                eyeVisited[ex][ey] = true;
                if (isEyeOfGroup(state, ex, ey, color) && isTrueEye(state, ex, ey, color)) {
                    trueEyes++;
                }
            }
        }
        return trueEyes;
    }

    // Một ô trống là "mắt" của nhóm nếu 4 hướng đều là quân cùng màu và không ra ngoài biên
    private boolean isEyeOfGroup(int[][] state, int ex, int ey, int color) {
        int[][] dirs = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int nx = ex + d[0];
            int ny = ey + d[1];
            if (nx < 0 || nx >= boardSize || ny < 0 || ny >= boardSize) {
                return false; // mắt phải nằm trong vùng kín
            }
            if (state[nx][ny] != color) {
                return false;
            }
        }
        return true;
    }

    // Phân biệt mắt thật / mắt giả bằng các điểm chéo xung quanh ô trống
    private boolean isTrueEye(int[][] state, int ex, int ey, int color) {
        int opp = (color == 1) ? 2 : 1;
        int enemyDiag = 0;
        int borderDiag = 0;
        int[][] diags = new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] d : diags) {
            int dx = ex + d[0];
            int dy = ey + d[1];
            if (dx < 0 || dx >= boardSize || dy < 0 || dy >= boardSize) {
                borderDiag++;
            } else if (state[dx][dy] == opp) {
                enemyDiag++;
            }
        }
        // Heuristic: nếu có >=2 góc là biên hoặc quân đối phương → mắt giả
        return (enemyDiag + borderDiag) < 2;
    }

    private void highlightGroup(List<int[]> group, Color color) {
        if (boardGrid == null || group == null) return;
        for (int[] cell : group) {
            int gx = cell[0];
            int gy = cell[1];
            for (Node node : boardGrid.getChildren()) {
                Integer col = GridPane.getColumnIndex(node);
                Integer row = GridPane.getRowIndex(node);
                int cx = col == null ? 0 : col;
                int cy = row == null ? 0 : row;
                if (cx == gx && cy == gy && node instanceof Button) {
                    Button btn = (Button) node;
                    String css = String.format("-fx-background-color: rgba(%d,%d,%d,%.2f); -fx-border-color: transparent; -fx-font-size: 32px;",
                            (int) (color.getRed() * 255),
                            (int) (color.getGreen() * 255),
                            (int) (color.getBlue() * 255),
                            color.getOpacity());
                    btn.setStyle(css);
                }
            }
        }
    }

    private void drawBoardGrid() {
        if (boardCanvas == null || boardSize <= 0) {
            return;
        }
        GraphicsContext gc = boardCanvas.getGraphicsContext2D();
        double w = boardCanvas.getWidth();
        double h = boardCanvas.getHeight();

        gc.setFill(Color.web("#d7a86e"));
        gc.fillRect(0, 0, w, h);

        // Không dùng margin, vẽ lưới phủ toàn Canvas để trùng với GridPane overlay
        double size = Math.min(w, h);
        double startX = 0;
        double startY = 0;
        double step = size / (boardSize - 1);

        gc.setStroke(Color.web("#986f3c"));
        gc.setLineWidth(1.0);

        for (int i = 0; i < boardSize; i++) {
            double x = startX + i * step;
            gc.strokeLine(x, startY, x, startY + size);
        }
        for (int j = 0; j < boardSize; j++) {
            double y = startY + j * step;
            gc.strokeLine(startX, y, startX + size, y);
        }

        int[] hoshiCoords;
        if (boardSize == 19) {
            hoshiCoords = new int[]{3, 9, 15};
        } else if (boardSize == 13) {
            hoshiCoords = new int[]{3, 6, 9};
        } else if (boardSize == 9) {
            hoshiCoords = new int[]{2, 4, 6};
        } else {
            return;
        }

        gc.setFill(Color.web("#222222"));
        double r = 3;
        for (int ix : hoshiCoords) {
            for (int iy : hoshiCoords) {
                double cx = startX + ix * step;
                double cy = startY + iy * step;
                gc.fillOval(cx - r, cy - r, r * 2, r * 2);
            }
        }
    }
}
