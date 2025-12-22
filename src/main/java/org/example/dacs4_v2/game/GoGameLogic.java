package org.example.dacs4_v2.game;

/**
 * Class chứa logic luật cờ vây.
 * Tách riêng khỏi UI để dễ test và bảo trì.
 */
public class GoGameLogic {

    private int boardSize;
    private int[][] board;
    private int[][] prevBoard;

    // Số quân bị bắt (cập nhật sau mỗi nước đi)
    private int lastCaptureCount = 0;

    /**
     * Khởi tạo logic game với kích thước bàn cờ.
     *
     * @param boardSize kích thước bàn cờ (9, 13, hoặc 19)
     */
    public GoGameLogic(int boardSize) {
        this.boardSize = boardSize;
        this.board = new int[boardSize][boardSize];
        this.prevBoard = null;
    }

    /**
     * Khởi tạo logic game với trạng thái bàn cờ có sẵn (dùng khi resume).
     *
     * @param boardSize         kích thước bàn cờ
     * @param existingBoard     trạng thái bàn cờ hiện tại
     * @param existingPrevBoard trạng thái bàn cờ trước đó (cho luật Ko)
     */
    public GoGameLogic(int boardSize, int[][] existingBoard, int[][] existingPrevBoard) {
        this.boardSize = boardSize;
        this.board = existingBoard != null ? deepCopy(existingBoard) : new int[boardSize][boardSize];
        this.prevBoard = existingPrevBoard != null ? deepCopy(existingPrevBoard) : null;
    }

    /**
     * Lấy trạng thái bàn cờ hiện tại.
     *
     * @return mảng 2D đại diện cho bàn cờ (0=trống, 1=đen, 2=trắng)
     */
    public int[][] getBoard() {
        return board;
    }

    /**
     * Lấy trạng thái bàn cờ trước đó (dùng cho luật Ko).
     *
     * @return mảng 2D của bàn cờ trước
     */
    public int[][] getPrevBoard() {
        return prevBoard;
    }

    /**
     * Lấy kích thước bàn cờ.
     *
     * @return kích thước (9, 13, hoặc 19)
     */
    public int getBoardSize() {
        return boardSize;
    }

    /**
     * Lấy số quân bị bắt trong nước đi gần nhất.
     *
     * @return số quân bị bắt
     */
    public int getLastCaptureCount() {
        return lastCaptureCount;
    }

    /**
     * Lấy giá trị tại một ô trên bàn cờ.
     *
     * @param x tọa độ X
     * @param y tọa độ Y
     * @return 0=trống, 1=đen, 2=trắng, hoặc -1 nếu ngoài phạm vi
     */
    public int getCell(int x, int y) {
        if (x < 0 || x >= boardSize || y < 0 || y >= boardSize) {
            return -1;
        }
        return board[x][y];
    }

    /**
     * Kiểm tra xem một vị trí có hợp lệ để đặt quân không.
     *
     * @param x tọa độ X
     * @param y tọa độ Y
     * @return true nếu ô trống và trong phạm vi
     */
    public boolean isValidPosition(int x, int y) {
        if (x < 0 || x >= boardSize || y < 0 || y >= boardSize) {
            return false;
        }
        return board[x][y] == 0;
    }

    /**
     * Áp dụng nước đi với đầy đủ luật cờ vây.
     * Kiểm tra: vị trí hợp lệ, bắt quân, tự tử, Ko rule.
     *
     * @param x                   tọa độ X
     * @param y                   tọa độ Y
     * @param color               màu quân (1=đen, 2=trắng)
     * @param enforceKoAndSuicide true để kiểm tra luật Ko và tự tử
     * @return true nếu nước đi hợp lệ và đã áp dụng, false nếu không hợp lệ
     */
    public boolean applyMove(int x, int y, int color, boolean enforceKoAndSuicide) {
        // Reset đếm quân bị bắt
        lastCaptureCount = 0;

        // Kiểm tra cơ bản
        if (board == null)
            return false;
        if (x < 0 || x >= boardSize || y < 0 || y >= boardSize)
            return false;
        if (board[x][y] != 0)
            return false;

        // Tạo bản sao tạm thời
        int[][] tmp = deepCopy(board);
        tmp[x][y] = color;
        int oppColor = color == 1 ? 2 : 1;
        boolean anyCapture = false;

        // Kiểm tra và bắt quân đối thủ xung quanh
        int[][] dirs = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] d : dirs) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx < 0 || nx >= boardSize || ny < 0 || ny >= boardSize)
                continue;
            if (tmp[nx][ny] == oppColor) {
                int captured = removeGroupIfNoLiberties(tmp, nx, ny, oppColor);
                if (captured > 0) {
                    anyCapture = true;
                    lastCaptureCount += captured;
                }
            }
        }

        // Kiểm tra luật tự tử: không được đặt quân vào vị trí không có liberties
        int libertiesAfter = countLiberties(tmp, x, y, color);
        if (enforceKoAndSuicide && libertiesAfter == 0 && !anyCapture) {
            return false;
        }

        // Kiểm tra luật Ko: không được lặp lại trạng thái bàn cờ trước đó
        if (enforceKoAndSuicide && prevBoard != null && boardsEqual(tmp, prevBoard)) {
            return false;
        }

        // Áp dụng nước đi
        prevBoard = deepCopy(board);
        board = tmp;
        return true;
    }

    /**
     * Xóa nhóm quân nếu không có liberties.
     *
     * @param state trạng thái bàn cờ
     * @param sx    tọa độ X bắt đầu
     * @param sy    tọa độ Y bắt đầu
     * @param color màu quân cần kiểm tra
     * @return số quân đã xóa (0 nếu nhóm còn liberties)
     */
    private int removeGroupIfNoLiberties(int[][] state, int sx, int sy, int color) {
        boolean[][] visited = new boolean[boardSize][boardSize];
        int[][] stack = new int[boardSize * boardSize][2];
        int top = 0;
        stack[top][0] = sx;
        stack[top][1] = sy;
        visited[sx][sy] = true;
        int groupCount = 0;
        boolean hasLiberty = false;

        int[][] group = new int[boardSize * boardSize][2];
        int[][] dirs = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        // DFS để tìm tất cả quân trong nhóm
        while (top >= 0) {
            int cx = stack[top][0];
            int cy = stack[top][1];
            top--;
            group[groupCount][0] = cx;
            group[groupCount][1] = cy;
            groupCount++;

            for (int[] d : dirs) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                if (nx < 0 || nx >= boardSize || ny < 0 || ny >= boardSize)
                    continue;
                if (state[nx][ny] == 0) {
                    hasLiberty = true;
                } else if (state[nx][ny] == color && !visited[nx][ny]) {
                    visited[nx][ny] = true;
                    top++;
                    stack[top][0] = nx;
                    stack[top][1] = ny;
                }
            }
        }

        // Nếu có liberties thì không xóa
        if (hasLiberty)
            return 0;

        // Xóa tất cả quân trong nhóm
        for (int i = 0; i < groupCount; i++) {
            state[group[i][0]][group[i][1]] = 0;
        }
        return groupCount;
    }

    /**
     * Đếm số liberties của một nhóm quân.
     *
     * @param state trạng thái bàn cờ
     * @param sx    tọa độ X bắt đầu
     * @param sy    tọa độ Y bắt đầu
     * @param color màu quân
     * @return số liberties (ô trống xung quanh nhóm)
     */
    private int countLiberties(int[][] state, int sx, int sy, int color) {
        boolean[][] visited = new boolean[boardSize][boardSize];
        int[][] stack = new int[boardSize * boardSize][2];
        int top = 0;
        stack[top][0] = sx;
        stack[top][1] = sy;
        visited[sx][sy] = true;
        int liberties = 0;

        int[][] dirs = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        while (top >= 0) {
            int cx = stack[top][0];
            int cy = stack[top][1];
            top--;

            for (int[] d : dirs) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                if (nx < 0 || nx >= boardSize || ny < 0 || ny >= boardSize)
                    continue;
                if (state[nx][ny] == 0) {
                    liberties++;
                } else if (state[nx][ny] == color && !visited[nx][ny]) {
                    visited[nx][ny] = true;
                    top++;
                    stack[top][0] = nx;
                    stack[top][1] = ny;
                }
            }
        }
        return liberties;
    }

    /**
     * Tạo bản sao sâu của mảng 2D.
     *
     * @param src mảng nguồn
     * @return bản sao của mảng
     */
    public static int[][] deepCopy(int[][] src) {
        if (src == null)
            return null;
        int[][] dst = new int[src.length][];
        for (int i = 0; i < src.length; i++) {
            dst[i] = new int[src[i].length];
            System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
        }
        return dst;
    }

    /**
     * So sánh hai bàn cờ có giống nhau không.
     *
     * @param a bàn cờ thứ nhất
     * @param b bàn cờ thứ hai
     * @return true nếu giống nhau
     */
    public static boolean boardsEqual(int[][] a, int[][] b) {
        if (a == null || b == null)
            return false;
        if (a.length != b.length)
            return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i].length != b[i].length)
                return false;
            for (int j = 0; j < a[i].length; j++) {
                if (a[i][j] != b[i][j])
                    return false;
            }
        }
        return true;
    }

    /**
     * Lấy các vị trí star points (hoshi) dựa trên kích thước bàn cờ.
     *
     * @return mảng tọa độ [x1, y1, x2, y2, ...]
     */
    public int[] getStarPoints() {
        if (boardSize == 9) {
            return new int[] { 2, 2, 6, 2, 4, 4, 2, 6, 6, 6 };
        } else if (boardSize == 13) {
            return new int[] { 3, 3, 9, 3, 6, 6, 3, 9, 9, 9 };
        } else if (boardSize == 19) {
            return new int[] { 3, 3, 9, 3, 15, 3, 3, 9, 9, 9, 15, 9, 3, 15, 9, 15, 15, 15 };
        }
        return new int[0];
    }
}
