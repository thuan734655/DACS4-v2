package org.example.dacs4_v2.viewModels;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
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
