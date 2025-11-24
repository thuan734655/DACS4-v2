package org.example.dacs4_v2.viewModels;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.dacs4_v2.HelloApplication;
import org.example.dacs4_v2.data.UserStorage;
import org.example.dacs4_v2.models.User;
import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.P2PNode;

public class OnlineController {

    @FXML
    private TextField searchField;

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

        refreshOnlinePlayers();
    }

    private void refreshOnlinePlayers() {
        new Thread(() -> {
            try {
                P2PNode node = P2PContext.getInstance().getOrCreateNode();
                java.util.List<User> players = node.requestOnlinePeers(1500);

                Platform.runLater(() -> updatePlayersUI(players));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "online-refresh-thread").start();
    }

    private void updatePlayersUI(java.util.List<User> players) {
        if (playersContainer == null) return;
        playersContainer.getChildren().clear();

        for (User u : players) {
            String name = u.getName() != null ? u.getName() : "Unknown";
            int rank = u.getRank();
            String rankText = rank <= 0 ? "Beginner" : (rank + " Dan");

            HBox card = new HBox(12);
            card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 16; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 8, 0.1, 0, 2);");

            // Avatar
            VBox avatarBox = new VBox(4);
            Label avatar = new Label(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
            avatar.setStyle("-fx-background-radius: 999; -fx-background-color: linear-gradient(to bottom, #6366f1, #8b5cf6); -fx-text-fill: white; -fx-padding: 10 14;");
            avatarBox.getChildren().add(avatar);

            // Thông tin chính
            VBox infoBox = new VBox(4);
            infoBox.setFillWidth(true);

            HBox nameRow = new HBox(6);
            Label nameLabel = new Label(name);
            nameLabel.setStyle("-fx-font-weight: bold;");
            Label onlineDot = new Label("●");
            onlineDot.setStyle("-fx-text-fill: #22c55e;");
            nameRow.getChildren().addAll(nameLabel, onlineDot);

            Label rankLabel = new Label(rankText + " - Online");
            rankLabel.setStyle("-fx-text-fill: #6b7280;");

            Label timeLabel = new Label("active now");
            timeLabel.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 11;");

            infoBox.getChildren().addAll(nameRow, rankLabel, timeLabel);

            // Cột phải: rating + nút
            VBox rightBox = new VBox(8);
            rightBox.setStyle("");
            Label ratingLabel = new Label("-");
            ratingLabel.setStyle("-fx-font-weight: bold;");
            Label ratingText = new Label("rating");
            ratingText.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 11;");

            HBox buttons = new HBox(8);
            Button challengeBtn = new Button("Challenge");
            challengeBtn.setStyle("-fx-background-color: #005b63; -fx-text-fill: white; -fx-background-radius: 999;");
            Button chatBtn = new Button("💬");
            buttons.getChildren().addAll(challengeBtn, chatBtn);

            rightBox.getChildren().addAll(ratingLabel, ratingText, buttons);

            card.getChildren().addAll(avatarBox, infoBox, rightBox);
            playersContainer.getChildren().add(card);
        }
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
    private void onGoOnline() {
        HelloApplication.navigateTo("online.fxml");
    }
}
