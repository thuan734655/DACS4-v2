package org.example.dacs4_v2.viewModels;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.control.Button;
import org.example.dacs4_v2.HelloApplication;
import org.example.dacs4_v2.data.GameHistoryStorage;
import org.example.dacs4_v2.game.GameContext;
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.Moves;
import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.P2PNode;
import org.example.dacs4_v2.network.rmi.GoGameServiceImpl;
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

    private Game game;
    private String localPlayerId;
    private boolean isBlack;
    private boolean viewOnly;
    private int[][] board;
    private int[][] prevBoard;
    private int boardSize;

    @FXML
    public void initialize() {
        game = GameContext.getInstance().getCurrentGame();
        viewOnly = GameContext.getInstance().isViewOnly();
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
            if (viewOnly) {
                lblPlayerColor.setText("View mode");
            } else {
                lblPlayerColor.setText(isBlack ? "You are BLACK" : "You are WHITE");
            }
        }
        if (lblKomi != null) {
            lblKomi.setText("Komi: " + komi);
        }

        if (boardGrid != null) {
            boardGrid.getChildren().clear();
            for (int y = 0; y < boardSize; y++) {
                for (int x = 0; x < boardSize; x++) {
                    Button cell = new Button("");
                    cell.setMinSize(28, 28);
                    cell.setPrefSize(28, 28);
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

        // Khi load game từ history, dựng lại trạng thái board dựa trên moves đã lưu.
        if (game.getMoves() != null && !game.getMoves().isEmpty()) {
            board = new int[boardSize][boardSize];
            prevBoard = null;
            for (Moves m : game.getMoves()) {
                if (m == null) {
                    continue;
                }
                int mx = m.getX();
                int my = m.getY();
                int color = "BLACK".equals(m.getPlayer()) ? 1 : 2;
                applyMoveWithRules(mx, my, color, false);
            }
        }
        redrawBoard();

        if (!viewOnly) {
            GameContext.getInstance().setMoveListener(this::onRemoteMoveReceived);
        }
    }

    private void onCellClicked(int x, int y, Button cell) {
        if (game == null) return;
        if (viewOnly) return;
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

        // Persist local trước để history/board luôn nhất quán, kể cả khi gửi RMI lỗi.
        game.addMove(move);
        GameHistoryStorage.upsert(game);

        redrawBoard();

        new Thread(() -> {
            try {
                P2PNode node = P2PContext.getInstance().getOrCreateNode();
                String myId = node.getLocalUser() != null ? node.getLocalUser().getUserId() : null;
                org.example.dacs4_v2.models.User rival = null;
                if (myId != null && myId.equals(game.getUserId())) {
                    rival = game.getRivalUser();
                } else {
                    rival = game.getHostUser();
                }
                if (rival != null) {
                    // Gửi move trực tiếp sang peer đối thủ (remote RMI). Không gọi local service để tránh duplicate.
                    IGoGameService remote = GoGameServiceImpl.getStub(rival);
                    remote.submitMove(move, order);
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
                    btn.setText("●");
                } else if (v == 2) {
                    btn.setText("○");
                } else {
                    btn.setText("");
                }
            }
        }
    }
}
