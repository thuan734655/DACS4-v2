package org.example.dacs4_v2.viewModels.helpers;

import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.Moves;
import org.example.dacs4_v2.models.User;
import org.example.dacs4_v2.network.P2PContext;
import org.example.dacs4_v2.network.P2PNode;
import org.example.dacs4_v2.network.rmi.GoGameServiceImpl;
import org.example.dacs4_v2.network.rmi.IGoGameService;

/**
 * Helper class xử lý giao tiếp P2P/RMI cho game.
 */
public class GameP2PHandler {

    private final Game game;

    public GameP2PHandler(Game game) {
        this.game = game;
    }

    /**
     * Lấy đối thủ của người chơi hiện tại.
     * 
     * @param localUserId ID người chơi local
     * @return User đối thủ
     */
    public User getRival(String localUserId) {
        if (localUserId != null && localUserId.equals(game.getUserId())) {
            return game.getRivalUser();
        } else {
            return game.getHostUser();
        }
    }

    /**
     * Gửi nước đi cho đối thủ qua RMI.
     */
    public void sendMoveToOpponent(Moves move, int order) {
        new Thread(() -> {
            try {
                P2PNode node = P2PContext.getInstance().getOrCreateNode();
                String myId = node.getLocalUser() != null ? node.getLocalUser().getUserId() : null;

                User rival = getRival(myId);

                // Kiểm tra rival có IP hợp lệ không
                if (rival != null && rival.getHost() != null && !rival.getHost().isEmpty()) {
                    IGoGameService remote = GoGameServiceImpl.getStub(rival);
                    remote.submitMove(move, order);
                } else {
                    System.out.println("[RMI] Không thể gửi nước đi: rival không có IP hợp lệ");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "submit-move-thread").start();
    }

    /**
     * Thông báo cho đối thủ khi thoát/đầu hàng.
     * 
     * @param reason      lý do ("EXIT", "SURRENDER")
     * @param blackTimeMs thời gian còn lại của quân đen
     * @param whiteTimeMs thời gian còn lại của quân trắng
     */
    public void notifyOpponent(String reason, long blackTimeMs, long whiteTimeMs) {
        new Thread(() -> {
            try {
                P2PNode node = P2PContext.getInstance().getOrCreateNode();
                User localUser = node.getLocalUser();
                String myId = localUser != null ? localUser.getUserId() : null;

                User rival = getRival(myId);

                if (rival != null && localUser != null) {
                    IGoGameService remote = GoGameServiceImpl.getStub(rival);
                    remote.notifyGamePaused(game.getGameId(), localUser, reason, blackTimeMs, whiteTimeMs);
                }
            } catch (Exception e) {
                System.out.println("[RMI] Không thể thông báo cho đối thủ: " + e.getMessage());
            }
        }, "notify-opponent-thread").start();
    }

    /**
     * Gửi kết quả tính điểm cho rival qua RMI.
     */
    public void sendScoreResultToRival(String scoreResult) {
        new Thread(() -> {
            try {
                P2PNode node = P2PContext.getInstance().getOrCreateNode();
                String myId = node.getLocalUser() != null ? node.getLocalUser().getUserId() : null;

                User rival = getRival(myId);

                if (rival != null) {
                    IGoGameService remote = GoGameServiceImpl.getStub(rival);
                    remote.sendScoreResult(game.getGameId(), scoreResult);
                    System.out.println("[RMI] Đã gửi kết quả tính điểm cho rival: " + scoreResult);
                }
            } catch (Exception e) {
                System.out.println("[RMI] Không thể gửi kết quả tính điểm: " + e.getMessage());
            }
        }, "send-score-result-thread").start();
    }

    /**
     * Gửi tin nhắn chat cho đối thủ.
     */
    public void sendChatToOpponent(String message) {
        new Thread(() -> {
            try {
                P2PNode node = P2PContext.getInstance().getOrCreateNode();
                String myId = node != null && node.getLocalUser() != null
                        ? node.getLocalUser().getUserId()
                        : null;
                String myName = node != null && node.getLocalUser() != null
                        ? node.getLocalUser().getName()
                        : "Unknown";

                User rival = getRival(myId);

                if (rival != null) {
                    IGoGameService remote = GoGameServiceImpl.getStub(rival);
                    remote.sendChatMessage(game.getGameId(), myName, message);
                }
            } catch (Exception e) {
                System.out.println("[Chat] Không thể gửi tin nhắn: " + e.getMessage());
            }
        }, "chat-send-thread").start();
    }

    /**
     * Thông báo cho đối thủ khi app bị đóng đột ngột (gọi đồng bộ).
     */
    public void notifyOpponentSync(String reason, long blackTimeMs, long whiteTimeMs) {
        try {
            P2PNode node = P2PContext.getInstance().getOrCreateNode();
            User localUser = node.getLocalUser();
            String myId = localUser != null ? localUser.getUserId() : null;

            User rival = getRival(myId);

            if (rival != null && localUser != null) {
                IGoGameService remote = GoGameServiceImpl.getStub(rival);
                remote.notifyGamePaused(game.getGameId(), localUser, reason, blackTimeMs, whiteTimeMs);
            }
        } catch (Exception e) {
            System.out.println("[RMI] Không thể thông báo cho đối thủ khi thoát app: " + e.getMessage());
        }
    }
}
