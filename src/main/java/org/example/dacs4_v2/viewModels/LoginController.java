package org.example.dacs4_v2.viewModels;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import org.example.dacs4_v2.HelloApplication;
import org.example.dacs4_v2.data.UserStorage;
import org.example.dacs4_v2.models.User;
import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.P2PNode;

import java.security.SecureRandom;

public class LoginController {

    @FXML
    private TextField txtName;

    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static String randomPeerId(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            int idx = RANDOM.nextInt(ALPHANUM.length());
            sb.append(ALPHANUM.charAt(idx));
        }
        return sb.toString();
    }

    @FXML
    private void onLogin(ActionEvent event) {
        String name = txtName != null ? txtName.getText().trim() : "";
        if (name.isEmpty()) {
            // đơn giản: nếu rỗng thì không làm gì
            return;
        }
        String userId = randomPeerId(10);
        // Tạo đối tượng User với thông tin tối thiểu, các thông tin khác coi như rỗng
        User user = new User(userId, name);
        UserStorage.saveUser(user);

        new Thread(() -> {
            try {
                P2PNode node = P2PContext.getInstance().getOrCreateNode();
                node.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "p2p-bootstrap-after-login").start();

        HelloApplication.navigateTo("dashboard.fxml");
    }
}
