package org.example.dacs4_v2.viewModels;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.dacs4_v2.HelloApplication;
import org.example.dacs4_v2.data.UserStorage;
import org.example.dacs4_v2.models.User;
import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.P2PNode;

import java.util.List;

public class DashboardController {

    @FXML
    private Label lblUserInfo;

    @FXML
    private VBox playersContainer;

    @FXML
    public void initialize() {
        String name = null;
        String peerId = null;

        User user = UserStorage.loadUser();
        if (user != null) {
            name = user.getName();
            peerId = user.getUserId();
        }
        if (name == null || name.isEmpty()) {
            name = "Guest";
        }
        if (peerId == null || peerId.isEmpty()) {
            peerId = "-";
        }
        if (lblUserInfo != null) {
            lblUserInfo.setText(name + " (" + peerId + ")");
        }

        try {
            P2PNode node = P2PContext.getInstance().getOrCreateNode();
            node.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        refreshOnlinePlayers();
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
    private void onStartPlaying() {
        HelloApplication.navigateTo("rooms.fxml");
    }

    @FXML
    private void onChallengeAI() {
        // Sau này điều hướng tới màn chơi với AI riêng nếu cần
    }

    @FXML
    private void onLogout() {
        try {
            P2PNode node = P2PContext.getInstance().getNode();
            if (node != null) {
                node.shutdown();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        HelloApplication.navigateTo("login.fxml");
    }

    private void refreshOnlinePlayers() {
        new Thread(() -> {
            try {
                P2PNode node = P2PContext.getInstance().getOrCreateNode();
                List<User> players = node.requestOnlinePeers(1500);
                Platform.runLater(() -> updatePlayersUI(players));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "dashboard-online-refresh").start();
    }

    @FXML
    private void onRefreshOnline() {
        refreshOnlinePlayers();
    }

    private void updatePlayersUI(List<User> players) {
        if (playersContainer == null) return;
        playersContainer.getChildren().clear();

        if (players == null || players.isEmpty()) {
            HBox emptyCard = new HBox(12);
            emptyCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 16; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 8, 0.1, 0, 2);");

            Label msg = new Label("Không có ai online");
            msg.setStyle("-fx-text-fill: #6b7280; -fx-font-style: italic;");
            emptyCard.getChildren().add(msg);

            playersContainer.getChildren().add(emptyCard);
            return;
        }

        for (User u : players) {
            String name = u.getName() != null ? u.getName() : "Unknown";
            int rank = u.getRank();
            String rankText = rank <= 0 ? "Beginner" : (rank + " \uD83E\uDD47");

            HBox card = new HBox(12);
            card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 16; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 8, 0.1, 0, 2);");

            VBox avatarBox = new VBox(4);
            Label avatar = new Label(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
            avatar.setStyle("-fx-background-radius: 999; -fx-background-color: linear-gradient(to bottom, #6366f1, #8b5cf6); -fx-text-fill: white; -fx-padding: 10 14;");
            avatarBox.getChildren().add(avatar);

            VBox infoBox = new VBox(4);
            HBox nameRow = new HBox(6);
            Label nameLabel = new Label(name);
            nameLabel.setStyle("-fx-font-weight: bold;");
            Label onlineDot = new Label("●");
            onlineDot.setStyle("-fx-text-fill: #22c55e;");
            nameRow.getChildren().addAll(nameLabel, onlineDot);

            Label rankLabel = new Label(rankText);
            rankLabel.setStyle("-fx-text-fill: #6b7280;");

            infoBox.getChildren().addAll(nameRow, rankLabel);

            card.getChildren().addAll(avatarBox, infoBox);
            playersContainer.getChildren().add(card);
        }
    }
}
