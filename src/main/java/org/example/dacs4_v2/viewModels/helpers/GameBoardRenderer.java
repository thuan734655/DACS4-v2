package org.example.dacs4_v2.viewModels.helpers;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import org.example.dacs4_v2.game.GoGameLogic;
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.Moves;

/**
 * Helper class để vẽ bàn cờ Go.
 * Chứa logic vẽ nền, lưới, star points và quân cờ.
 */
public class GameBoardRenderer {

    private final Canvas canvas;
    private final GoGameLogic gameLogic;
    private final Game game;

    private double cellSize;
    private double padding;
    private double boardPixelSize;

    public GameBoardRenderer(Canvas canvas, GoGameLogic gameLogic, Game game) {
        this.canvas = canvas;
        this.gameLogic = gameLogic;
        this.game = game;
    }

    /**
     * Thiết lập kích thước bàn cờ.
     */
    public void setupBoard() {
        boardPixelSize = 500;
        padding = 25;
        cellSize = (boardPixelSize - 2 * padding) / (gameLogic.getBoardSize() - 1);

        canvas.setWidth(boardPixelSize);
        canvas.setHeight(boardPixelSize);
    }

    /**
     * Vẽ toàn bộ bàn cờ: nền gỗ, lưới, star points, quân cờ.
     */
    public void drawBoard() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
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
        drawStarPoints(gc);

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
     * Vẽ star points (hoshi) trên bàn cờ.
     */
    private void drawStarPoints(GraphicsContext gc) {
        gc.setFill(Color.web("#3d2914"));
        int[] starPoints = gameLogic.getStarPoints();
        for (int i = 0; i < starPoints.length; i += 2) {
            double x = padding + starPoints[i] * cellSize;
            double y = padding + starPoints[i + 1] * cellSize;
            gc.fillOval(x - 4, y - 4, 8, 8);
        }
    }

    /**
     * Vẽ một quân cờ với hiệu ứng gradient và shadow.
     */
    public void drawStone(GraphicsContext gc, int gridX, int gridY, boolean isBlackStone) {
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
    public void highlightLastMove(GraphicsContext gc) {
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

    /**
     * Chuyển tọa độ pixel sang tọa độ lưới.
     * 
     * @return int[] {gridX, gridY} hoặc null nếu không hợp lệ
     */
    public int[] pixelToGrid(double pixelX, double pixelY) {
        int gridX = (int) Math.round((pixelX - padding) / cellSize);
        int gridY = (int) Math.round((pixelY - padding) / cellSize);

        if (gameLogic.isValidPosition(gridX, gridY)) {
            return new int[] { gridX, gridY };
        }
        return null;
    }

    // Getters
    public double getCellSize() {
        return cellSize;
    }

    public double getPadding() {
        return padding;
    }

    public double getBoardPixelSize() {
        return boardPixelSize;
    }
}
