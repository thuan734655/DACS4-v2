package org.example.dacs4_v2;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.P2PNode;
import org.example.dacs4_v2.network.NetworkRuntimeConfig;

public class HelloApplication extends Application {

    private static Stage primaryStage;
    private static final Map<String, Parent> viewCache = new HashMap<>();

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;

        NetworkRuntimeConfig cfg = NetworkRuntimeConfig.fromArgs(getParameters().getRaw());
        P2PContext.getInstance().setRuntimeConfig(cfg);

        // Nếu đã có user.json trong thư mục data thì bỏ qua màn login
        File userFile = new File("data/user.json");
        String startFxml = userFile.exists() ? "dashboard.fxml" : "login.fxml";

        Parent root = loadFXML(startFxml);
        Scene scene = new Scene(root);

        stage.setTitle("Go Game Online");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            try {
                P2PNode node = P2PContext.getInstance().getNode();
                if (node != null) {
                    node.shutdown();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        stage.show();
    }

    private static Parent loadFXML(String fxmlName) throws IOException {
        Parent cached = viewCache.get(fxmlName);
        if (cached != null) {
            return cached;
        }

        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource(fxmlName));
        Parent root = loader.load();
        viewCache.put(fxmlName, root);
        return root;
    }

    public static void navigateTo(String fxmlName) {
        if (primaryStage == null) {
            return;
        }
        try {
            if ("game.fxml".equals(fxmlName)) {
                viewCache.remove(fxmlName);
            }
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

