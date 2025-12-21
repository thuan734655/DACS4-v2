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

    private HBox predecessorCard;
    private HBox successorCard;
    private Label predecessorAvatarLabel;
    private Label predecessorNameLabel;
    private Label predecessorRankLabel;
    private Label successorAvatarLabel;
    private Label successorNameLabel;
    private Label successorRankLabel;

    private HBox emptyOnlineCard;

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

            ensureNeighborCards();
            applyNeighborToCard(node.getPredecessor(), predecessorAvatarLabel, predecessorNameLabel, predecessorRankLabel);
            applyNeighborToCard(node.getSuccessor(), successorAvatarLabel, successorNameLabel, successorRankLabel);

            P2PContext.getInstance().setNeighborUiUpdater(() -> Platform.runLater(() -> {
                P2PNode n = P2PContext.getInstance().getNode();
                if (n == null) return;
                ensureNeighborCards();
                applyNeighborToCard(n.getPredecessor(), predecessorAvatarLabel, predecessorNameLabel, predecessorRankLabel);
                applyNeighborToCard(n.getSuccessor(), successorAvatarLabel, successorNameLabel, successorRankLabel);
            }));

            P2PContext.getInstance().requestNeighborUiUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        refreshOnlinePlayers();
    }

    private void ensureNeighborCards() {
        if (playersContainer == null) return;

        P2PNode node = P2PContext.getInstance().getNode();
        if (node == null || node.getLocalUser() == null) return;

        User me = node.getLocalUser();
        User pred = node.getPredecessor();
        User succ = node.getSuccessor();
        String myId = me.getUserId();

        boolean predIsMe = pred == null || pred.getUserId() == null || pred.getUserId().equals(myId);
        boolean succIsMe = succ == null || succ.getUserId() == null || succ.getUserId().equals(myId);
        boolean hasAnyoneOnline = !(predIsMe && succIsMe);

        if (!hasAnyoneOnline) {
            if (emptyOnlineCard == null) {
                emptyOnlineCard = new HBox(12);
                emptyOnlineCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 16; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 8, 0.1, 0, 2);");
                Label msg = new Label("Không có ai online");
                msg.setStyle("-fx-text-fill: #6b7280; -fx-font-style: italic;");
                emptyOnlineCard.getChildren().add(msg);
            }
            playersContainer.getChildren().setAll(emptyOnlineCard);
            return;
        }

        if (emptyOnlineCard != null) {
            playersContainer.getChildren().remove(emptyOnlineCard);
        }

        if (predecessorCard == null || predecessorNameLabel == null) {
            predecessorCard = buildNeighborCard(true);
        }

        if (successorCard == null || successorNameLabel == null) {
            successorCard = buildNeighborCard(false);
        }

        boolean samePeer = pred != null && succ != null
                && pred.getUserId() != null && succ.getUserId() != null
                && pred.getUserId().equals(succ.getUserId());

        boolean hasPred = playersContainer.getChildren().contains(predecessorCard);
        boolean hasSucc = playersContainer.getChildren().contains(successorCard);

        if (!hasPred) {
            playersContainer.getChildren().add(0, predecessorCard);
        }

        if (samePeer) {
            playersContainer.getChildren().remove(successorCard);
        } else {
            if (!hasSucc) {
                int index = playersContainer.getChildren().contains(predecessorCard) ? 1 : 0;
                playersContainer.getChildren().add(index, successorCard);
            }
        }
    }

    private HBox buildNeighborCard(boolean predecessor) {
        Label avatar = new Label("?");
        avatar.setStyle("-fx-background-radius: 999; -fx-background-color: linear-gradient(to bottom, #6366f1, #8b5cf6); -fx-text-fill: white; -fx-padding: 10 14;");
        VBox avatarBox = new VBox(4);
        avatarBox.getChildren().add(avatar);

        Label nameLabel = new Label("-");
        nameLabel.setStyle("-fx-font-weight: bold;");
        Label onlineDot = new Label("●");
        onlineDot.setStyle("-fx-text-fill: #22c55e;");
        HBox nameRow = new HBox(6);
        nameRow.getChildren().addAll(nameLabel, onlineDot);

        Label rankLabel = new Label("-");
        rankLabel.setStyle("-fx-text-fill: #6b7280;");

        VBox infoBox = new VBox(4);
        infoBox.getChildren().addAll(nameRow, rankLabel);

        HBox card = new HBox(12);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 16; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 8, 0.1, 0, 2);");
        card.getChildren().addAll(avatarBox, infoBox);

        if (predecessor) {
            predecessorAvatarLabel = avatar;
            predecessorNameLabel = nameLabel;
            predecessorRankLabel = rankLabel;
        } else {
            successorAvatarLabel = avatar;
            successorNameLabel = nameLabel;
            successorRankLabel = rankLabel;
        }
        return card;
    }

    private void applyNeighborToCard(User u, Label avatarLabel, Label nameLabel, Label rankLabel) {
        if (avatarLabel == null || nameLabel == null || rankLabel == null) return;

        if (u == null) {
            avatarLabel.setText("?");
            nameLabel.setText("-");
            rankLabel.setText("-");
            return;
        }

        String name = u.getName() != null ? u.getName() : "Unknown";
        String avatar = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        avatarLabel.setText(avatar);
        nameLabel.setText(name);

        int rank = u.getRank();
        String rankText = rank <= 0 ? "Beginner" : (rank + " \uD83E\uDD47");
        rankLabel.setText(rankText);
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
            P2PContext.getInstance().setNeighborUiUpdater(null);
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
                node.getPeerWhenJoinNet();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "dashboard-online-refresh").start();
    }

    @FXML
    private void onRefreshOnline() {
//        refreshOnlinePlayers();
    }

    private void updatePlayersUI(List<User> players) {
        if (playersContainer == null) return;
        playersContainer.getChildren().clear();

        ensureNeighborCards();

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
