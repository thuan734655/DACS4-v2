package org.example.dacs4_v2.ai;

import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.GameStatus;
import org.example.dacs4_v2.models.User;

import java.io.File;

/**
 * Context quản lý game với AI.
 * Singleton pattern để dễ truy cập từ các controller.
 */
public class AIGameContext {

    private static final AIGameContext INSTANCE = new AIGameContext();

    private KataGoClient kataGoClient;
    private Game currentGame;
    private boolean isAIGame = false;
    private boolean playerIsBlack = true; // Người chơi luôn là quân đen (đi trước)

    // Paths đến KataGo (sử dụng absolute path)
    private String katagoPath;
    private String modelPath;
    private String configPath;

    private AIGameContext() {
        // Lấy thư mục project
        String projectDir = System.getProperty("user.dir");
        System.out.println("[AIGame] Project directory: " + projectDir);

        // Tự động detect OS và set path phù hợp
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            katagoPath = projectDir + "/katago/katago.exe";
        } else {
            // Linux hoặc Mac
            katagoPath = projectDir + "/katago/katago";
        }
        modelPath = projectDir + "/katago/model.bin.gz";
        configPath = projectDir + "/katago/gtp.cfg";

        System.out.println("[AIGame] KataGo path: " + katagoPath);
        System.out.println("[AIGame] Model path: " + modelPath);
    }

    public static AIGameContext getInstance() {
        return INSTANCE;
    }

    /**
     * Kiểm tra xem đây có phải game với AI không.
     */
    public boolean isAIGame() {
        return isAIGame;
    }

    /**
     * Kiểm tra KataGo đã sẵn sàng chưa.
     */
    public boolean isKataGoReady() {
        return kataGoClient != null && kataGoClient.isRunning();
    }

    /**
     * Bắt đầu game mới với AI.
     * Tự động khởi động KataGo nếu chưa chạy.
     * 
     * @param boardSize kích thước bàn cờ (9, 13, 19)
     * @param komi      giá trị komi
     * @return Game object mới tạo
     */
    public Game startNewAIGame(int boardSize, double komi) {
        isAIGame = true;

        // Khởi động KataGo nếu chưa chạy
        if (!isKataGoReady()) {
            if (!startKataGo()) {
                System.err.println("[AIGame] Không thể khởi động KataGo!");
                return null;
            }
        }

        // Thiết lập bàn cờ mới
        kataGoClient.setBoardSize(boardSize);
        kataGoClient.setKomi(komi);

        // Tạo Game object
        currentGame = new Game();
        currentGame.setGameId("AI_" + System.currentTimeMillis());
        currentGame.setNameGame("Game với AI");
        currentGame.setBoardSize(boardSize);
        currentGame.setKomi((int) komi);
        currentGame.setStatus(GameStatus.PLAYING);
        currentGame.setStartedAt(System.currentTimeMillis());

        // Thiết lập user (AI là rival)
        User aiUser = new User("KATAGO_AI", "KataGo AI");
        aiUser.setUserId("KATAGO_AI");
        aiUser.setName("KataGo AI");
        aiUser.setRank(9999); // AI rank rất cao
        currentGame.setRivalUser(aiUser);

        return currentGame;
    }

    /**
     * Khởi động KataGo process.
     */
    private boolean startKataGo() {
        // Kiểm tra file tồn tại
        if (!new File(katagoPath).exists()) {
            System.err.println("[AIGame] Không tìm thấy katago.exe tại: " + katagoPath);
            System.err.println("[AIGame] Vui lòng download KataGo và đặt vào thư mục katago/");
            return false;
        }

        if (!new File(modelPath).exists()) {
            System.err.println("[AIGame] Không tìm thấy model tại: " + modelPath);
            return false;
        }

        // Tạo config mặc định nếu chưa có
        createDefaultConfig();

        System.out.println("[AI] Khởi động KataGo...");
        System.out.println("[AI] Path: " + katagoPath);
        System.out.println("[AI] Model: " + modelPath);

        kataGoClient = new KataGoClient();
        boolean started = kataGoClient.start(katagoPath, modelPath, configPath);

        if (started) {
            System.out.println("[AI] KataGo đã khởi động thành công");
        } else {
            System.out.println("[AI] KataGo khởi động thất bại");
        }
        return started;
    }

    /**
     * Tạo file config mặc định cho KataGo.
     */
    private void createDefaultConfig() {
        File configFile = new File(configPath);
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                java.io.FileWriter fw = new java.io.FileWriter(configFile);
                fw.write("# KataGo GTP Config\n");
                fw.write("logSearchInfo = false\n");
                fw.write("logToStderr = false\n");
                fw.write("maxVisits = 100\n"); // Giới hạn để chạy nhanh trên CPU
                fw.write("numSearchThreads = 2\n");
                fw.close();
            } catch (Exception e) {
                // Ignore - KataGo sẽ dùng default
            }
        }
    }

    /**
     * Thông báo nước đi của người chơi cho AI.
     * 
     * @param x tọa độ X
     * @param y tọa độ Y
     */
    public void playPlayerMove(int x, int y) {
        if (!isKataGoReady())
            return;

        String color = playerIsBlack ? "black" : "white";
        System.out.println("[AI] Thông báo nước đi: " + color + " (" + x + ", " + y + ")");
        kataGoClient.playMove(color, x, y);
    }

    /**
     * Thông báo pass của người chơi.
     */
    public void playPlayerPass() {
        if (!isKataGoReady())
            return;

        String color = playerIsBlack ? "black" : "white";
        kataGoClient.playPass(color);
    }

    /**
     * Yêu cầu AI đánh nước tiếp theo.
     * 
     * @return tọa độ [x, y] hoặc [-1, -1] nếu pass
     */
    public int[] getAIMove() {
        if (!isKataGoReady())
            return null;

        String aiColor = playerIsBlack ? "white" : "black";
        System.out.println("[AI] Yêu cầu AI (đ" + aiColor + ") đánh...");
        int[] move = kataGoClient.generateMove(aiColor);
        if (move != null) {
            if (move[0] == -1 && move[1] == -1) {
                System.out.println("[AI] AI quyết định PASS");
            } else {
                System.out.println("[AI] AI đánh: (" + move[0] + ", " + move[1] + ")");
            }
        }
        return move;
    }

    /**
     * Tính điểm cuối game.
     * 
     * @return kết quả dạng "B+2.5" hoặc "W+6.5"
     */
    public String calculateScore() {
        System.out.println("[AI] Tính điểm cuối game...");
        if (!isKataGoReady()) {
            System.out.println("[AI] KataGo không sẵn sàng");
            return "Không thể tính điểm";
        }
        String result = kataGoClient.finalScore();
        System.out.println("[AI] Kết quả: " + result);
        return result;
    }

    /**
     * Kết thúc game với AI.
     */
    public void endAIGame() {
        isAIGame = false;
        currentGame = null;
    }

    /**
     * Dừng KataGo khi thoát app.
     */
    public void shutdown() {
        if (kataGoClient != null) {
            kataGoClient.shutdown();
            kataGoClient = null;
        }
        isAIGame = false;
        currentGame = null;
    }

    /**
     * Lấy game hiện tại.
     */
    public Game getCurrentGame() {
        return currentGame;
    }

    /**
     * Cấu hình đường dẫn KataGo.
     */
    public void setKataGoPaths(String katagoPath, String modelPath, String configPath) {
        this.katagoPath = katagoPath;
        this.modelPath = modelPath;
        this.configPath = configPath;
    }
}
