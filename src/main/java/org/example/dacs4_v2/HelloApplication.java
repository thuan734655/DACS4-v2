package org.example.dacs4_v2;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.P2PNode;

public class HelloApplication extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        // Nếu đã có user.json trong thư mục data thì bỏ qua màn login và vào thẳng màn room/online
        File userFile = new File("data/user.json");
        String startFxml = userFile.exists() ? "online.fxml" : "login.fxml";

        Parent root = loadFXML(startFxml);
        Scene scene = new Scene(root);

        stage.setTitle("Go Game Online");
        stage.setScene(scene);
        stage.show();

        if (userFile.exists()) {
            new Thread(() -> {
                try {
                    P2PNode node = P2PContext.getInstance().getOrCreateNode();
                    node.requestOnlinePeers(1500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, "p2p-bootstrap-thread").start();
        }
    }

    private static Parent loadFXML(String fxmlName) throws IOException {
        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource(fxmlName));
        return loader.load();
    }

    public static void navigateTo(String fxmlName) {
        if (primaryStage == null) {
            return;
        }
        try {
            Parent root = loadFXML(fxmlName);
            Scene scene = primaryStage.getScene();
            if (scene == null) {
                scene = new Scene(root);
                primaryStage.setScene(scene);
            } else {
                scene.setRoot(root);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

