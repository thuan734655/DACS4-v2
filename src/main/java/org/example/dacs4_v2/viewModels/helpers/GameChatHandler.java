package org.example.dacs4_v2.viewModels.helpers;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.Scene;

/**
 * Helper class xử lý chức năng chat trong game.
 */
public class GameChatHandler {

    private Stage chatStage;
    private VBox chatMessagesBox;
    private TextField txtChatInput;
    private ScrollPane chatScrollPane;

    // Callback để gửi tin nhắn
    private ChatSendCallback sendCallback;

    /**
     * Interface callback khi gửi tin nhắn.
     */
    public interface ChatSendCallback {
        void onSendMessage(String message);
    }

    public GameChatHandler() {
    }

    /**
     * Set callback khi gửi tin nhắn.
     */
    public void setSendCallback(ChatSendCallback callback) {
        this.sendCallback = callback;
    }

    /**
     * Toggle mở/đóng chat popup.
     */
    public void toggleChat() {
        if (chatStage == null) {
            createChatPopup();
        }

        if (chatStage.isShowing()) {
            chatStage.hide();
        } else {
            chatStage.show();
        }
    }

    /**
     * Tạo chat popup window.
     */
    private void createChatPopup() {
        chatStage = new Stage();
        chatStage.setTitle("💬 Chat");
        chatStage.initStyle(StageStyle.UTILITY);
        chatStage.setAlwaysOnTop(true);

        // Chat messages
        chatMessagesBox = new VBox(8);
        chatMessagesBox.setStyle("-fx-padding: 8;");

        chatScrollPane = new ScrollPane(chatMessagesBox);
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setPrefHeight(300);
        chatScrollPane.setStyle("-fx-background-color: #f8fafc;");

        // Input area
        txtChatInput = new TextField();
        txtChatInput.setPromptText("Nhập tin nhắn...");
        txtChatInput.setStyle("-fx-background-radius: 20; -fx-padding: 8 12;");
        txtChatInput.setOnAction(e -> onSendMessage());

        Button btnSend = new Button("📤");
        btnSend.setStyle(
                "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 12;");
        btnSend.setOnAction(e -> onSendMessage());

        HBox inputBox = new HBox(8, txtChatInput, btnSend);
        inputBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(txtChatInput, Priority.ALWAYS);

        VBox root = new VBox(10, chatScrollPane, inputBox);
        root.setStyle("-fx-padding: 12; -fx-background-color: white;");
        VBox.setVgrow(chatScrollPane, Priority.ALWAYS);

        chatStage.setScene(new Scene(root, 300, 400));
        chatStage.setX(100);
        chatStage.setY(100);
    }

    /**
     * Xử lý khi gửi tin nhắn.
     */
    private void onSendMessage() {
        if (txtChatInput == null)
            return;

        String message = txtChatInput.getText().trim();
        if (message.isEmpty())
            return;

        // Xóa input
        txtChatInput.clear();

        // Gọi callback để gửi
        if (sendCallback != null) {
            sendCallback.onSendMessage(message);
        }
    }

    /**
     * Thêm tin nhắn vào khung chat.
     */
    public void addMessage(String sender, String message, boolean isMe) {
        if (chatMessagesBox == null)
            return;

        Platform.runLater(() -> {
            VBox msgBox = new VBox(2);
            msgBox.setStyle("-fx-padding: 6 10; -fx-background-radius: 12; " +
                    (isMe ? "-fx-background-color: #3b82f6; -fx-alignment: CENTER_RIGHT;"
                            : "-fx-background-color: #e2e8f0; -fx-alignment: CENTER_LEFT;"));

            Label lblSender = new Label(sender);
            lblSender.setStyle("-fx-font-size: 10; -fx-text-fill: " + (isMe ? "#dbeafe;" : "#64748b;"));

            Label lblMessage = new Label(message);
            lblMessage.setWrapText(true);
            lblMessage.setStyle("-fx-font-size: 13; -fx-text-fill: " + (isMe ? "white;" : "#1e293b;"));

            msgBox.getChildren().addAll(lblSender, lblMessage);
            chatMessagesBox.getChildren().add(msgBox);

            // Auto-scroll xuống cuối
            if (chatScrollPane != null) {
                chatScrollPane.setVvalue(1.0);
            }
        });
    }

    /**
     * Đóng chat popup.
     */
    public void close() {
        if (chatStage != null) {
            chatStage.close();
            chatStage = null;
        }
    }

    /**
     * Kiểm tra chat popup có đang mở không.
     */
    public boolean isOpen() {
        return chatStage != null && chatStage.isShowing();
    }
}
