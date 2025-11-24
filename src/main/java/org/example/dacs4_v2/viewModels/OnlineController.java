package org.example.dacs4_v2.viewModels;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.example.dacs4_v2.HelloApplication;
import org.example.dacs4_v2.data.UserStorage;
import org.example.dacs4_v2.models.User;

public class OnlineController {

    @FXML
    private TextField searchField;

    @FXML
    private Label lblUserInfo;

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
    }

    // Sau này thêm logic hiển thị danh sách peer online từ mạng P2P

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
