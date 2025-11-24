package org.example.dacs4_v2.viewModels;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import org.example.dacs4_v2.HelloApplication;

public class OnlineController {

    @FXML
    private TextField searchField;

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
