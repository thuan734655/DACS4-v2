package org.example.dacs4_v2.viewModels;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.example.dacs4_v2.HelloApplication;
import org.example.dacs4_v2.data.GameHistoryStorage;
import org.example.dacs4_v2.game.GameContext;
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.GameStatus;
import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.P2PNode;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Controller cho màn hình History - hiển thị các game đã kết thúc.
 */
public class HistoryController {

    @FXML
    private FlowPane historyFlow;

    private final List<Game> finishedGames = new ArrayList<>();

    @FXML
    public void initialize() {
        loadFinishedGames();
    }

    /**
     * Tải danh sách game đã kết thúc.
     */
    private void loadFinishedGames() {
        finishedGames.clear();
        List<Game> allGames = GameHistoryStorage.loadHistory(0);

        // Lọc chỉ lấy game đã FINISHED
        for (Game g : allGames) {
            if (g.getStatus() == GameStatus.FINISHED) {
                finishedGames.add(g);
            }
        }

        // Sắp xếp theo thời gian mới nhất
        finishedGames.sort(Comparator.comparingLong(this::getGameEndTime).reversed());

        renderGames();
    }

    private long getGameEndTime(Game g) {
        // Ưu tiên endedAt, fallback về startedAt
        if (g.getEndedAt() > 0) {
            return g.getEndedAt();
        }
        return g.getStartedAt() > 0 ? g.getStartedAt() : System.currentTimeMillis();
    }

    /**
     * Hiển thị danh sách game.
     */
    private void renderGames() {
        historyFlow.getChildren().clear();

        if (finishedGames.isEmpty()) {
            Label emptyLabel = new Label("Chưa có game nào đã kết thúc");
            emptyLabel.setStyle("-fx-font-size: 16; -fx-text-fill: #6b7280;");
            historyFlow.getChildren().add(emptyLabel);
            return;
        }

        String myId = null;
        P2PNode node = P2PContext.getInstance().getOrCreateNode();
        if (node != null && node.getLocalUser() != null) {
            myId = node.getLocalUser().getUserId();
        }

        for (Game g : finishedGames) {
            VBox card = buildGameCard(g, myId);
            historyFlow.getChildren().add(card);
        }
    }

    /**
     * Tạo card hiển thị thông tin game.
     */
    private VBox buildGameCard(Game g, String myId) {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 16; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2); -fx-min-width: 280;");

        // Tên game
        String gameName = g.getNameGame() != null ? g.getNameGame() : "Game #" + g.getGameId();
        Label lblName = new Label(gameName);
        lblName.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        // Thời gian
        String timeStr = formatTime(g.getStartedAt());
        Label lblTime = new Label("🕐 " + timeStr);
        lblTime.setStyle("-fx-font-size: 12; -fx-text-fill: #6b7280;");

        // Người chơi
        String blackPlayer = g.getHostUser() != null ? g.getHostUser().getName() : "Unknown";
        String whitePlayer = g.getRivalUser() != null ? g.getRivalUser().getName() : "Unknown";
        Label lblPlayers = new Label("⚫ " + blackPlayer + " vs ⚪ " + whitePlayer);
        lblPlayers.setStyle("-fx-font-size: 13; -fx-text-fill: #374151;");

        // Kết quả (nếu có)
        String resultText = getGameResult(g, myId);
        Label lblResult = new Label(resultText);
        lblResult.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #059669;");

        // Replay button
        Button btnReplay = new Button("👁 Replay");
        btnReplay.setStyle(
                "-fx-cursor: hand; -fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 8;");
        btnReplay.setOnAction(e -> viewGameReplay(g));

        card.getChildren().addAll(lblName, lblTime, lblPlayers, lblResult, btnReplay);
        return card;
    }

    /**
     * Lấy kết quả game.
     * Ưu tiên sử dụng scoreResult đã lưu, nếu không có thì tính toán.
     */
    private String getGameResult(Game g, String myId) {
        // Ưu tiên dùng scoreResult đã lưu
        if (g.getScoreResult() != null && !g.getScoreResult().isEmpty()) {
            String result = g.getScoreResult();
            // Rút gọn kết quả nếu quá dài
            if (result.contains("\n")) {
                result = result.split("\n")[0];
            }
            return "🏆 " + result;
        }

        // Fallback: tính toán từ số quân bắt
        int blackCaptures = g.getCapturedByBlack();
        int whiteCaptures = g.getCapturedByWhite();
        double komi = g.getKomiAsDouble();

        double blackScore = blackCaptures;
        double whiteScore = whiteCaptures + komi;

        if (blackScore > whiteScore) {
            return "🏆 Black wins +" + (blackScore - whiteScore);
        } else if (whiteScore > blackScore) {
            return "🏆 White wins +" + (whiteScore - blackScore);
        } else {
            return "🤝 Draw";
        }
    }

    /**
     * Xem lại game (replay).
     */
    private void viewGameReplay(Game g) {
        GameContext.getInstance().setCurrentGame(g);
        GameContext.getInstance().setViewOnly(true);
        HelloApplication.navigateTo("game.fxml");
    }

    private String formatTime(long timestamp) {
        if (timestamp <= 0)
            return "Unknown";
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    @FXML
    private void onGoDashboard() {
        HelloApplication.navigateTo("dashboard.fxml");
    }

    @FXML
    private void onGoRooms() {
        HelloApplication.navigateTo("rooms.fxml");
    }

    @FXML
    private void onGoHistory() {
        // Already on History page, do nothing or refresh
        loadFinishedGames();
    }

    @FXML
    private void onLogout() {
        P2PNode node = P2PContext.getInstance().getNode();
        if (node != null) {
            node.shutdown();
        }
        HelloApplication.navigateTo("login.fxml");
    }
}
