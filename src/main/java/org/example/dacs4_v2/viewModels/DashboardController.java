package org.example.dacs4_v2.viewModels;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.dacs4_v2.HelloApplication;
import org.example.dacs4_v2.data.GameHistoryStorage;
import org.example.dacs4_v2.data.UserStorage;
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.GameStatus;
import org.example.dacs4_v2.models.User;
import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.P2PNode;
import org.example.dacs4_v2.viewModels.CreateRoomController;

import java.util.List;

public class DashboardController {

    @FXML
    private Label lblUserInfo;

    @FXML
    private VBox playersContainer;

    @FXML
    private Label lblTotalGames;

    @FXML
    private Label lblWinRate;

    @FXML
    private Label lblCurrentRank;

    @FXML
    private Label lblOnlineFriends;

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
        // Header hiển thị user hiện tại. Ưu tiên lấy từ node (runtime) nếu đã start.
        updateHeaderUserInfo(UserStorage.loadUser());

        try {
            P2PNode node = P2PContext.getInstance().getOrCreateNode();
            node.start();

            // Sau khi start node, update lại header/stats bằng dữ liệu thật từ node.
            updateHeaderUserInfo(node.getLocalUser());
            updateStats(node);

            ensureNeighborCards();
            applyNeighborToCard(node.getPredecessor(), predecessorAvatarLabel, predecessorNameLabel, predecessorRankLabel);
            applyNeighborToCard(node.getSuccessor(), successorAvatarLabel, successorNameLabel, successorRankLabel);

            P2PContext.getInstance().setNeighborUiUpdater(() -> Platform.runLater(() -> {
                P2PNode n = P2PContext.getInstance().getNode();
                if (n == null) return;
                ensureNeighborCards();
                applyNeighborToCard(n.getPredecessor(), predecessorAvatarLabel, predecessorNameLabel, predecessorRankLabel);
                applyNeighborToCard(n.getSuccessor(), successorAvatarLabel, successorNameLabel, successorRankLabel);

                // Neighbors thay đổi => online count cũng thay đổi.
                updateStats(n);
            }));

            P2PContext.getInstance().requestNeighborUiUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        refreshOnlinePlayers();
    }

    private void updateHeaderUserInfo(User user) {
        String name = user != null ? user.getName() : null;
        String peerId = user != null ? user.getUserId() : null;
        if (name == null || name.isEmpty()) {
            name = "Guest";
        }
        if (peerId == null || peerId.isEmpty()) {
            peerId = "-";
        }
        if (lblUserInfo != null) {
            lblUserInfo.setText(name + " (" + peerId + ")");
        }
    }

    private void updateStats(P2PNode node) {
        // Total games lấy từ lịch sử local (data/game_history.json)
        List<Game> games = GameHistoryStorage.loadHistory(0);
        int total = games != null ? games.size() : 0;
        if (lblTotalGames != null) {
            lblTotalGames.setText(String.valueOf(total));
        }

        // Win rate: hiện chưa có result/winner nên chỉ có thể show "-" hoặc tỉ lệ FINISHED.
        if (lblWinRate != null) {
            int finished = 0;
            if (games != null) {
                for (Game g : games) {
                    if (g != null && g.getStatus() == GameStatus.FINISHED) {
                        finished++;
                    }
                }
            }
            lblWinRate.setText(total <= 0 ? "-" : (finished * 100 / total) + "%");
        }

        // Current rank lấy từ user đang chạy.
        User me = node != null ? node.getLocalUser() : null;
        if (lblCurrentRank != null) {
            int rank = me != null ? me.getRank() : 0;
            lblCurrentRank.setText(rank <= 0 ? "-" : String.valueOf(rank));
        }

        // Online Friends: hiện dự án chưa maintain full list peers online; dùng neighbors hiện tại làm số liệu thật.
        if (lblOnlineFriends != null) {
            int online = countOnlineNeighbors(node);
            lblOnlineFriends.setText(String.valueOf(online));
        }
    }

    private int countOnlineNeighbors(P2PNode node) {
        if (node == null || node.getLocalUser() == null) {
            return 0;
        }
        String myId = node.getLocalUser().getUserId();
        User pred = node.getPredecessor();
        User succ = node.getSuccessor();

        boolean predOk = pred != null && pred.getUserId() != null && !pred.getUserId().equals(myId);
        boolean succOk = succ != null && succ.getUserId() != null && !succ.getUserId().equals(myId);
        if (predOk && succOk && pred.getUserId().equals(succ.getUserId())) {
            return 1;
        }
        return (predOk ? 1 : 0) + (succOk ? 1 : 0);
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

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button inviteBtn = new Button("Invite");
        inviteBtn.setStyle("-fx-background-color: #005b63; -fx-text-fill: white; -fx-background-radius: 999; -fx-padding: 6 14;");
        inviteBtn.setOnAction(e -> onInviteNeighbor(predecessor));

        HBox card = new HBox(12);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 16; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 8, 0.1, 0, 2);");
        card.getChildren().addAll(avatarBox, infoBox, spacer, inviteBtn);

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

    private void onInviteNeighbor(boolean predecessor) {
        try {
            P2PNode node = P2PContext.getInstance().getNode();
            if (node == null) {
                return;
            }
            User me = node.getLocalUser();
            User neighbor = predecessor ? node.getPredecessor() : node.getSuccessor();
            if (me == null || neighbor == null || neighbor.getUserId() == null) {
                return;
            }
            if (me.getUserId() != null && me.getUserId().equals(neighbor.getUserId())) {
                return;
            }
            openCreateRoomDialog(neighbor.getUserId());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void openCreateRoomDialog(String opponentPeerId) {
        try {
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("create_room.fxml"));
            Parent root = loader.load();

            CreateRoomController controller = loader.getController();
            if (controller != null) {
                controller.prefillOpponentPeerId(opponentPeerId, true);
            }

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Invite Game");
            dialog.setScene(new Scene(root));
            dialog.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        // Broadcast ASK_ONLINE để tìm peer entry, từ đó join ring => cập nhật successor/predecessor.
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
        refreshOnlinePlayers();
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
