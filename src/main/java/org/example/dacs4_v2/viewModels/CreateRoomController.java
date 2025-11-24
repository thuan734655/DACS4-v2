package org.example.dacs4_v2.viewModels;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.User;
import org.example.dacs4_v2.models.UserConfig;
import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.P2PNode;
import org.example.dacs4_v2.network.rmi.IGoGameService;

import java.security.SecureRandom;

public class CreateRoomController {

    @FXML
    private TextField txtGameName;

    @FXML
    private ComboBox<String> cbBoardSize;

    @FXML
    private ComboBox<String> cbKomi;

    @FXML
    private TextField txtOpponentPeerId;

    @FXML
    private Label lblStatus;

    private static final SecureRandom RANDOM = new SecureRandom();

    @FXML
    public void initialize() {
        if (cbBoardSize != null && cbBoardSize.getItems().isEmpty()) {
            cbBoardSize.getItems().addAll("9", "13", "19");
        }
        if (cbKomi != null && cbKomi.getItems().isEmpty()) {
            cbKomi.getItems().addAll("0", "6.5");
        }
        if (cbBoardSize != null && cbBoardSize.getValue() == null) {
            cbBoardSize.setValue("19");
        }
        if (cbKomi != null && cbKomi.getValue() == null) {
            cbKomi.setValue("6.5");
        }
    }

    @FXML
    private void onCreate(ActionEvent event) {
        String name = txtGameName != null ? txtGameName.getText().trim() : "";
        String boardSizeStr = cbBoardSize != null && cbBoardSize.getValue() != null ? cbBoardSize.getValue() : "19";
        String komiStr = cbKomi != null && cbKomi.getValue() != null ? cbKomi.getValue() : "6.5";
        String opponentPeerId = txtOpponentPeerId != null ? txtOpponentPeerId.getText().trim() : "";

        if (name.isEmpty() || opponentPeerId.isEmpty()) {
            setStatus("Name and opponent peerId are required");
            return;
        }

        int boardSize;
        try {
            boardSize = Integer.parseInt(boardSizeStr);
        } catch (NumberFormatException e) {
            setStatus("Invalid board size");
            return;
        }

        int komiInt;
        if ("0".equals(komiStr)) {
            komiInt = 0;
        } else if ("6.5".equals(komiStr)) {
            komiInt = 65;
        } else {
            setStatus("Invalid komi");
            return;
        }

        String gameId = String.format("%06d", RANDOM.nextInt(1_000_000));

        try {
            P2PNode node = P2PContext.getInstance().getOrCreateNode();
            User local = node.getLocalUser();
            if (local == null || local.getUserConfig() == null) {
                setStatus("Local peer not ready");
                return;
            }

            String hostPeerId = local.getUserId();
            String userId = hostPeerId; // local plays black
            String rivalId = opponentPeerId;

            Game game = new Game(gameId, hostPeerId, userId, rivalId, boardSize, komiInt, name);

            // Lookup đối thủ qua DHT
            IGoGameService localService = node.getLocalService();
            if (localService == null) {
                setStatus("Service not ready");
                return;
            }

            UserConfig targetConfig = localService.findPeerById(opponentPeerId, 12);
            if (targetConfig == null) {
                setStatus("Opponent not found or offline");
                return;
            }

            IGoGameService targetStub = node.getLocalService();
            try {
                java.rmi.Naming.rebind("temp://noop", targetStub); // no-op to satisfy compiler
            } catch (Exception ignored) {}

            // Gửi lời mời qua RMI trực tiếp
            String url = "rmi://" + targetConfig.getHost() + ":" + targetConfig.getPort() + "/" + targetConfig.getServiceName();
            IGoGameService remote = (IGoGameService) java.rmi.Naming.lookup(url);
            remote.inviteToGame(game);

            closeWindow();
        } catch (Exception e) {
            e.printStackTrace();
            setStatus("Error: " + e.getMessage());
        }
    }

    @FXML
    private void onCancel(ActionEvent event) {
        closeWindow();
    }

    private void closeWindow() {
        if (txtGameName != null) {
            Stage stage = (Stage) txtGameName.getScene().getWindow();
            if (stage != null) {
                stage.close();
            }
        }
    }

    private void setStatus(String msg) {
        if (lblStatus != null) {
            lblStatus.setText(msg);
        }
    }
}
