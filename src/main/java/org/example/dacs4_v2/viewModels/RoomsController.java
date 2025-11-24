package org.example.dacs4_v2.viewModels;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import org.example.dacs4_v2.HelloApplication;

public class RoomsController {

    @FXML
    private TextField searchField;

    // Sau này thêm logic load danh sách room từ mạng P2P / RMI

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
