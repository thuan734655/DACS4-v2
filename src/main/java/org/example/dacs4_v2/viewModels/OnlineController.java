package org.example.dacs4_v2.viewModels;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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
            javafx.scene.control.Label label = new javafx.scene.control.Label(u.getName() + " (rank " + u.getRank() + ")");
            playersContainer.getChildren().add(label);
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
