package org.example.dacs4_v2.viewModels;

import javafx.fxml.FXML;
import org.example.dacs4_v2.HelloApplication;

public class DashboardController {

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
    private void onStartPlaying() {
        HelloApplication.navigateTo("rooms.fxml");
    }

    @FXML
    private void onChallengeAI() {
        // Sau này điều hướng tới màn chơi với AI riêng nếu cần
    }
}
