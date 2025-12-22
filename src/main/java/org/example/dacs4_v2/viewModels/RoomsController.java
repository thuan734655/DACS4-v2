package org.example.dacs4_v2.viewModels;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.dacs4_v2.HelloApplication;
import org.example.dacs4_v2.data.GameHistoryStorage;
import org.example.dacs4_v2.game.GameContext;
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.GameStatus;
import org.example.dacs4_v2.models.User;
import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.P2PNode;
import org.example.dacs4_v2.network.rmi.GoGameServiceImpl;
import org.example.dacs4_v2.network.rmi.IGoGameService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RoomsController {

    @FXML
    private TextField searchField;

    @FXML
    private FlowPane gamesFlow;

    private final List<Game> allGames = new ArrayList<>();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm dd/MM")
            .withZone(ZoneId.systemDefault());

    @FXML
    public void initialize() {
        reloadGames();

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> renderGames());
        }
    }

    private void reloadGames() {
        allGames.clear();
        List<Game> history = GameHistoryStorage.loadHistory(0);
        if (history != null) {
            allGames.addAll(history);
        }
        allGames.sort(Comparator.comparingLong(RoomsController::gameSortTime).reversed());
        renderGames();
    }

    private static long gameSortTime(Game g) {
        if (g == null) {
            return 0;
        }
        if (g.getStartedAt() > 0) {
            return g.getStartedAt();
        }
        if (g.getAcceptedAt() > 0) {
            return g.getAcceptedAt();
        }
        return g.getCreatedAt();
    }

    private void renderGames() {
        if (gamesFlow == null) {
            return;
        }

        gamesFlow.getChildren().clear();

        String query = searchField != null && searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";

        P2PNode node = P2PContext.getInstance().getNode();
        String myId = node != null && node.getLocalUser() != null ? node.getLocalUser().getUserId() : null;

        for (Game g : allGames) {
            if (g == null) {
                continue;
            }

            String name = g.getNameGame() != null ? g.getNameGame() : "-";
            if (!query.isEmpty() && !name.toLowerCase().contains(query) && (g.getGameId() == null || !g.getGameId().toLowerCase().contains(query))) {
                continue;
            }

            gamesFlow.getChildren().add(buildGameCard(g, myId));
        }

        if (gamesFlow.getChildren().isEmpty()) {
            VBox empty = new VBox(8);
            empty.setPrefWidth(680);
            empty.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 16; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 8, 0.1, 0, 2);");
            Label msg = new Label("Không có game nào");
            msg.setStyle("-fx-text-fill: #6b7280; -fx-font-style: italic;");
            empty.getChildren().add(msg);
            gamesFlow.getChildren().add(empty);
        }
    }

    private VBox buildGameCard(Game g, String myId) {
        VBox card = new VBox(12);
        card.setPrefWidth(340);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 16; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 8, 0.1, 0, 2);");

        String displayName = (g.getNameGame() != null && !g.getNameGame().isEmpty()) ? g.getNameGame() : ("Game " + safe(g.getGameId()));
        Label title = new Label(displayName);
        title.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        Label status = new Label(statusText(g.getStatus()));
        status.setStyle(statusStyle(g.getStatus()));

        HBox header = new HBox(8);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getChildren().addAll(title, status);

        long t = gameSortTime(g);
        String timeText = t > 0 ? TIME_FMT.format(Instant.ofEpochMilli(t)) : "-";
        String boardText = g.getBoardSize() > 0 ? (g.getBoardSize() + "x" + g.getBoardSize()) : "-";

        User opp = resolveOpponent(g, myId);
        String oppName = opp != null && opp.getName() != null && !opp.getName().isEmpty() ? opp.getName() : (opp != null ? safe(opp.getUserId()) : "-");

        HBox meta = new HBox(16);
        meta.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12;");
        meta.getChildren().addAll(
                new Label("Time: " + timeText),
                new Label("Board: " + boardText),
                new Label("Opponent: " + oppName)
        );

        Button btnPlay = new Button("Play");
        btnPlay.setMaxWidth(Double.MAX_VALUE);
        btnPlay.setStyle("-fx-background-color: #005b63; -fx-text-fill: white; -fx-background-radius: 999;");

        Button btnView = new Button("View");
        btnView.setMaxWidth(Double.MAX_VALUE);
        btnView.setStyle("-fx-background-color: #9ca3af; -fx-text-fill: white; -fx-background-radius: 999;");

        HBox actions = new HBox(12);
        HBox.setHgrow(btnPlay, Priority.ALWAYS);
        HBox.setHgrow(btnView, Priority.ALWAYS);
        actions.getChildren().addAll(btnPlay, btnView);

        boolean canPlay = g.getStatus() == GameStatus.PLAYING;
        btnPlay.setDisable(!canPlay);
        if (!canPlay) {
            btnPlay.setStyle("-fx-background-color: #cbd5e1; -fx-text-fill: white; -fx-background-radius: 999;");
        }

        btnView.setOnAction(e -> openGameView(g));
        btnPlay.setOnAction(e -> requestPlay(g, myId));

        card.getChildren().addAll(header, meta, actions);
        return card;
    }

    private void openGameView(Game g) {
        GameContext.getInstance().setCurrentGame(g);
        GameContext.getInstance().setViewOnly(true);
        HelloApplication.navigateTo("game.fxml");
    }

    private void requestPlay(Game g, String myId) {
        if (g == null) {
            return;
        }

        if (myId == null || myId.isEmpty()) {
            showInfo("Not ready", "Local peer not ready");
            return;
        }

        User opp = resolveOpponent(g, myId);
        if (opp == null) {
            showInfo("Cannot play", "Opponent info missing");
            return;
        }

        new Thread(() -> {
            try {
                P2PNode node = P2PContext.getInstance().getOrCreateNode();
                User local = node.getLocalUser();
                if (local == null) {
                    Platform.runLater(() -> showInfo("Not ready", "Local peer not ready"));
                    return;
                }
                User requesterSnapshot = snapshotUser(local);
                IGoGameService remote = GoGameServiceImpl.getStub(opp);
                remote.requestResume(g.getGameId(), requesterSnapshot);
                Platform.runLater(() -> showInfo("Resume requested", "Waiting for opponent confirmation..."));
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> showInfo("Resume failed", ex.getMessage()));
            }
        }, "resume-request-thread").start();
    }

    private static User snapshotUser(User u) {
        if (u == null) {
            return null;
        }
        return new User(u.getHost(), u.getName(), u.getPort(), u.getRank(), u.getServiceName(), u.getUserId());
    }

    private static User resolveOpponent(Game g, String myId) {
        if (g == null) {
            return null;
        }
        if (myId == null) {
            return g.getRivalUser() != null ? g.getRivalUser() : g.getHostUser();
        }

        if (myId.equals(g.getUserId()) || myId.equals(g.getHostPeerId())) {
            return g.getRivalUser();
        }
        if (myId.equals(g.getRivalId())) {
            return g.getHostUser();
        }
        return g.getRivalUser() != null ? g.getRivalUser() : g.getHostUser();
    }

    private static String statusText(GameStatus s) {
        if (s == null) {
            return "UNKNOWN";
        }
        return s.name();
    }

    private static String statusStyle(GameStatus s) {
        if (s == GameStatus.PLAYING) {
            return "-fx-background-color: #e0ebff; -fx-text-fill: #2563eb; -fx-padding: 4 10; -fx-background-radius: 999;";
        }
        if (s == GameStatus.FINISHED) {
            return "-fx-background-color: #e1f8e9; -fx-text-fill: #1b9a55; -fx-padding: 4 10; -fx-background-radius: 999;";
        }
        if (s == GameStatus.DECLINED || s == GameStatus.CANCELED || s == GameStatus.FAILED) {
            return "-fx-background-color: #ffe3d1; -fx-text-fill: #d96b2b; -fx-padding: 4 10; -fx-background-radius: 999;";
        }
        return "-fx-background-color: #f3f4f6; -fx-text-fill: #6b7280; -fx-padding: 4 10; -fx-background-radius: 999;";
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg != null ? msg : "", ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private static String safe(String s) {
        return s != null ? s : "";
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

    @FXML
    private void onCreateRoom() {
        try {
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("create_room.fxml"));
            Parent root = loader.load();

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Create Room");
            dialog.setScene(new Scene(root));
            dialog.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
