package org.example.dacs4_v2.models;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class Game implements Serializable {
    private static final long serialVersionUID = 1L;

    // === Cơ bản ===
    private String gameId;          // 6 chữ số, do người tạo sinh
    private String hostPeerId;      // peerId của người tạo room (luôn là "đen" nếu không random)
    private String userId;          // peerId người chơi (đen)
    private String rivalId;         // peerId đối thủ (trắng) — có thể là "AI_KATAGO"
    private String nameGame;        // tên room
    private int boardSize;          // 9, 13, 19
    private int komi;               // 0 hoặc 6.5 (lưu dạng int: 0 hoặc 65 → chia 10 khi hiển thị)

    // === Trạng thái game ===
    private List<Moves> moves;      // lịch sử nước đi (theo order tăng dần)
    private String currentTurn;     // "BLACK", "WHITE", hoặc "AI" (nếu rival là AI)
    private boolean isActive;       // true: đang chơi; false: đã kết thúc hoặc bị hủy
    private long lastMoveTimestamp; // System.currentTimeMillis() của nước đi gần nhất
    private String winner;          // userId, rivalId, "DRAW", hoặc null nếu chưa xong

    // === Thời gian ===
    private Duration userTime;      // thời gian còn lại của userId (đen)
    private Duration rivalTime;     // thời gian còn lại của rivalId (trắng)
    private Duration baseTime;      // thời gian ban đầu (dùng để resume)
    private Duration overtime;      // byo-yomi (nếu có), ví dụ: 30s x 5 lần

    // === Networking & Recovery ===
    private long gameVersion;       // tăng mỗi khi có move mới → dùng để detect conflict/resume
    private boolean isSynchronized; // true nếu cả 2 peer đã đồng bộ state (sau reconnect)

    // Constructor
    public Game(String gameId, String hostPeerId, String userId, String rivalId,
                int boardSize, int komi, String nameGame) {
        this.gameId = gameId;
        this.hostPeerId = hostPeerId;
        this.userId = userId;
        this.rivalId = rivalId;
        this.boardSize = boardSize;
        this.komi = komi;
        this.nameGame = nameGame;
        this.moves = new ArrayList<>();
        this.currentTurn = "BLACK"; // giả sử userId (đen) đi trước
        this.isActive = true;
        this.lastMoveTimestamp = System.currentTimeMillis();
        this.userTime = Duration.ofMinutes(10);
        this.rivalTime = Duration.ofMinutes(10);
        this.baseTime = Duration.ofMinutes(10);
        this.overtime = Duration.ofSeconds(30);
        this.gameVersion = 0;
        this.isSynchronized = true;
    }

    // ===== Getter & Setter (chỉ liệt kê quan trọng) =====
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public String getHostPeerId() { return hostPeerId; }
    public void setHostPeerId(String hostPeerId) { this.hostPeerId = hostPeerId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRivalId() { return rivalId; }
    public void setRivalId(String rivalId) { this.rivalId = rivalId; }

    public int getBoardSize() { return boardSize; }
    public void setBoardSize(int boardSize) { this.boardSize = boardSize; }

    public int getKomi() { return komi; }
    public double getKomiAsDouble() { return komi / 10.0; } // 65 → 6.5
    public void setKomi(int komi) { this.komi = komi; }

    public List<Moves> getMoves() { return moves; }
    public void setMoves(List<Moves> moves) { this.moves = moves; }
    public void addMove(Moves move) {
        this.moves.add(move);
        this.gameVersion++;
        this.lastMoveTimestamp = System.currentTimeMillis();
    }

    public String getCurrentTurn() { return currentTurn; }
    public void setCurrentTurn(String currentTurn) { this.currentTurn = currentTurn; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public long getLastMoveTimestamp() { return lastMoveTimestamp; }

    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }

    public Duration getUserTime() { return userTime; }
    public void setUserTime(Duration userTime) { this.userTime = userTime; }

    public Duration getRivalTime() { return rivalTime; }
    public void setRivalTime(Duration rivalTime) { this.rivalTime = rivalTime; }

    public Duration getBaseTime() { return baseTime; }
    public void setBaseTime(Duration baseTime) { this.baseTime = baseTime; }

    public Duration getOvertime() { return overtime; }
    public void setOvertime(Duration overtime) { this.overtime = overtime; }

    public long getGameVersion() { return gameVersion; }
    public void setGameVersion(long gameVersion) { this.gameVersion = gameVersion; }

    public boolean isSynchronized() { return isSynchronized; }
    public void setSynchronized(boolean synchronizedFlag) { isSynchronized = synchronizedFlag; }

    // Helper: kiểm tra xem peer có tham gia game này không
    public boolean isParticipant(String peerId) {
        return userId.equals(peerId) || rivalId.equals(peerId);
    }

    // Helper: ai đang giữ lượt?
    public String getCurrentPlayerId() {
        if ("BLACK".equals(currentTurn)) return userId;
        if ("WHITE".equals(currentTurn)) return rivalId;
        return null; // AI hoặc lỗi
    }

    @Override
    public String toString() {
        return "Game{" +
                "gameId='" + gameId + '\'' +
                ", players=" + userId + " vs " + rivalId +
                ", moves=" + moves.size() +
                ", turn=" + currentTurn +
                ", active=" + isActive +
                '}';
    }
}