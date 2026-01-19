package org.example.dacs4_v2.viewModels.helpers;

import org.example.dacs4_v2.ai.AIGameContext;
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.Moves;

/**
 * Helper class xử lý tính điểm cho game.
 */
public class GameScoreCalculator {

    private final Game game;
    private int capturedByBlack;
    private int capturedByWhite;

    public GameScoreCalculator(Game game) {
        this.game = game;
        this.capturedByBlack = game.getCapturedByBlack();
        this.capturedByWhite = game.getCapturedByWhite();
    }

    /**
     * Host dùng AI tính điểm.
     * 
     * @return kết quả tính điểm đã format
     */
    public String calculateScoreAsHost() {
        AIGameContext aiContext = AIGameContext.getInstance();

        System.out.println("[Score] Host đang tính điểm...");
        System.out.println("[Score] KataGo ready: " + aiContext.isKataGoReady());

        String scoreResult;

        // Nếu KataGo chưa sẵn sàng, thử khởi động
        if (!aiContext.isKataGoReady()) {
            System.out.println("[Score] Khởi động KataGo để tính điểm...");
            aiContext.startNewAIGame(game.getBoardSize(), game.getKomiAsDouble());
        }

        if (aiContext.isKataGoReady()) {
            System.out.println("[Score] Đồng bộ bàn cờ với KataGo...");
            // Đồng bộ trạng thái bàn cờ với KataGo trước khi tính điểm
            syncBoardWithKataGo(aiContext);
            System.out.println("[Score] Gọi KataGo tính điểm...");
            String rawResult = aiContext.calculateScore();
            System.out.println("[Score] Kết quả thô từ AI: " + rawResult);
            // Format kết quả cho dễ đọc
            scoreResult = formatAIScoreResult(rawResult);
            System.out.println("[Score] Kết quả đã format: " + scoreResult);
        } else {
            // KataGo không sẵn sàng: tính điểm đơn giản
            System.out.println("[Score] KataGo không khả dụng, dùng tính điểm đơn giản");
            scoreResult = calculateSimpleScoreResult();
        }

        return scoreResult;
    }

    /**
     * Đồng bộ trạng thái bàn cờ với KataGo để tính điểm chính xác.
     */
    public void syncBoardWithKataGo(AIGameContext aiContext) {
        // Khởi động KataGo nếu chưa chạy
        if (!aiContext.isKataGoReady()) {
            aiContext.startNewAIGame(game.getBoardSize(), game.getKomiAsDouble());
        }

        // Replay tất cả nước đi để KataGo đồng bộ trạng thái bàn cờ
        if (game.getMoves() != null) {
            for (Moves move : game.getMoves()) {
                if (move.getX() >= 0 && move.getY() >= 0) {
                    // Thông báo nước đi cho KataGo
                    if ("BLACK".equals(move.getPlayer())) {
                        aiContext.playPlayerMove(move.getX(), move.getY());
                    }
                }
            }
        }
    }

    /**
     * Dùng AI (KataGo) để tính điểm (cho game với AI).
     * 
     * @return kết quả tính điểm đã format
     */
    public String calculateWithAI() {
        AIGameContext aiContext = AIGameContext.getInstance();

        if (!aiContext.isKataGoReady()) {
            return calculateSimpleScoreResult();
        }

        // Gọi KataGo tính điểm
        String rawResult = aiContext.calculateScore();
        // Format kết quả cho dễ đọc
        return formatAIScoreResult(rawResult);
    }

    /**
     * Tính điểm đơn giản (chỉ đếm quân bắt được + komi).
     * 
     * @return chuỗi kết quả
     */
    public String calculateSimpleScoreResult() {
        double blackScore = capturedByBlack;
        double whiteScore = capturedByWhite + game.getKomiAsDouble();

        String winner = blackScore > whiteScore ? "BLACK" : "WHITE";
        double diff = Math.abs(blackScore - whiteScore);

        return winner + " wins by " + diff + " points\n\n" +
                "Black: " + blackScore + " (captured " + capturedByBlack + ")\n" +
                "White: " + whiteScore + " (captured " + capturedByWhite + " + komi " + game.getKomiAsDouble() + ")";
    }

    /**
     * Format kết quả từ AI (VD: "B+20.5" -> "ĐEN thắng 20.5 điểm").
     * 
     * @param rawResult kết quả thô từ KataGo (VD: "B+20.5", "W+6.5", "0")
     * @return kết quả đã format cho người dùng
     */
    public String formatAIScoreResult(String rawResult) {
        if (rawResult == null || rawResult.isEmpty()) {
            return "Unknown";
        }

        rawResult = rawResult.trim();

        // Handle draw result
        if ("0".equals(rawResult) || rawResult.contains("Jigo") || rawResult.contains("Draw")) {
            return "🤝 DRAW\n\nBoth sides have equal points!";
        }

        // Parse kết quả dạng "B+20.5" hoặc "W+6.5"
        String winner;
        double diff = 0;

        try {
            if (rawResult.startsWith("B+") || rawResult.startsWith("b+")) {
                winner = "BLACK";
                diff = Double.parseDouble(rawResult.substring(2));
            } else if (rawResult.startsWith("W+") || rawResult.startsWith("w+")) {
                winner = "WHITE";
                diff = Double.parseDouble(rawResult.substring(2));
            } else if (rawResult.startsWith("B-") || rawResult.startsWith("b-")) {
                winner = "WHITE";
                diff = Double.parseDouble(rawResult.substring(2));
            } else if (rawResult.startsWith("W-") || rawResult.startsWith("w-")) {
                winner = "BLACK";
                diff = Double.parseDouble(rawResult.substring(2));
            } else {
                // Cannot parse, return raw
                return "Result: " + rawResult;
            }
        } catch (NumberFormatException e) {
            return "Result: " + rawResult;
        }

        // Format result
        StringBuilder result = new StringBuilder();
        result.append("🏆 ").append(winner).append(" WINS!\n\n");
        result.append("• Difference: ").append(diff).append(" points\n");
        result.append("• Black captured: ").append(capturedByBlack).append("\n");
        result.append("• White captured: ").append(capturedByWhite).append("\n");
        result.append("• Komi: ").append(game.getKomiAsDouble());

        return result.toString();
    }

    // Getters & Setters
    public int getCapturedByBlack() {
        return capturedByBlack;
    }

    public void setCapturedByBlack(int capturedByBlack) {
        this.capturedByBlack = capturedByBlack;
    }

    public int getCapturedByWhite() {
        return capturedByWhite;
    }

    public void setCapturedByWhite(int capturedByWhite) {
        this.capturedByWhite = capturedByWhite;
    }

    public void addCapturedByBlack(int count) {
        this.capturedByBlack += count;
    }

    public void addCapturedByWhite(int count) {
        this.capturedByWhite += count;
    }
}
