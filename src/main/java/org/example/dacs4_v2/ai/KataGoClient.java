package org.example.dacs4_v2.ai;

import java.io.*;

/**
 * Client giao tiếp với KataGo AI qua GTP (Go Text Protocol).
 * Sử dụng stdin/stdout để gửi lệnh và nhận phản hồi.
 */
public class KataGoClient {

    private Process katagoProcess;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean isRunning = false;

    /**
     * Khởi động KataGo process.
     * 
     * @param katagoPath đường dẫn đến katago.exe
     * @param modelPath  đường dẫn đến model file (.bin.gz)
     * @param configPath đường dẫn đến config file (.cfg)
     * @return true nếu khởi động thành công
     */
    public boolean start(String katagoPath, String modelPath, String configPath) {
        try {
            // Kiểm tra file tồn tại
            File katagoFile = new File(katagoPath);
            File modelFile = new File(modelPath);

            if (!katagoFile.exists()) {
                System.err.println("[KataGo] Không tìm thấy katago.exe: " + katagoPath);
                return false;
            }
            if (!modelFile.exists()) {
                System.err.println("[KataGo] Không tìm thấy model: " + modelPath);
                return false;
            }

            System.out.println("[KataGo] Đang khởi động process...");
            System.out
                    .println("[KataGo] Command: " + katagoPath + " gtp -model " + modelPath + " -config " + configPath);

            // Khởi động process - set working directory là thư mục chứa katago
            ProcessBuilder pb = new ProcessBuilder(
                    katagoPath, "gtp",
                    "-model", modelPath,
                    "-config", configPath);
            pb.directory(katagoFile.getParentFile()); // Set working directory
            pb.redirectErrorStream(true);
            katagoProcess = pb.start();

            reader = new BufferedReader(new InputStreamReader(katagoProcess.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(katagoProcess.getOutputStream()));

            System.out.println("[KataGo] Đợi khởi động (5 giây)...");

            // Đọc output trong khi đợi
            StringBuilder startupOutput = new StringBuilder();
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 5000) {
                if (reader.ready()) {
                    String line = reader.readLine();
                    if (line != null) {
                        startupOutput.append(line).append("\n");
                        System.out.println("[KataGo Output] " + line);
                    }
                }
                Thread.sleep(100);
            }

            // Kiểm tra process còn sống không
            if (!katagoProcess.isAlive()) {
                int exitCode = katagoProcess.exitValue();
                System.err.println("[KataGo] Process đã thoát với exit code: " + exitCode);
                System.err.println("[KataGo] Output: " + startupOutput.toString());
                return false;
            }

            // Test connection
            System.out.println("[KataGo] Test connection với lệnh 'name'...");
            String response = sendCommandDirect("name");
            System.out.println("[KataGo] Response: " + response);

            if (response != null && response.contains("KataGo")) {
                isRunning = true;
                System.out.println("[KataGo] Khởi động thành công!");
                return true;
            }

            System.err.println("[KataGo] Không nhận được response hợp lệ từ KataGo");
            return false;

        } catch (Exception e) {
            System.err.println("[KataGo] Lỗi khởi động: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Kiểm tra KataGo có đang chạy không.
     */
    public boolean isRunning() {
        return isRunning && katagoProcess != null && katagoProcess.isAlive();
    }

    /**
     * Gửi GTP command và nhận response.
     * 
     * @param command lệnh GTP (ví dụ: "genmove black")
     * @return response từ KataGo (không có prefix "= ")
     */
    public synchronized String sendCommand(String command) {
        if (!isRunning()) {
            return null;
        }
        return sendCommandDirect(command);
    }

    /**
     * Gửi GTP command trực tiếp (không kiểm tra isRunning).
     * Dùng trong quá trình khởi động.
     */
    private synchronized String sendCommandDirect(String command) {
        if (writer == null || reader == null) {
            return null;
        }

        try {
            // Gửi command
            writer.write(command + "\n");
            writer.flush();

            // Đọc response
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    break; // GTP response kết thúc bằng dòng trống
                }
                if (response.length() > 0) {
                    response.append("\n");
                }
                response.append(line);
            }

            String result = response.toString().trim();

            // Xử lý prefix "= " hoặc "? "
            if (result.startsWith("= ")) {
                return result.substring(2);
            } else if (result.startsWith("=")) {
                return result.substring(1).trim();
            } else if (result.startsWith("? ")) {
                System.err.println("[KataGo] Error: " + result);
                return null;
            }

            return result;

        } catch (Exception e) {
            System.err.println("[KataGo] Lỗi gửi command: " + e.getMessage());
            return null;
        }
    }

    /**
     * Thiết lập kích thước bàn cờ.
     * 
     * @param size 9, 13, hoặc 19
     */
    public void setBoardSize(int size) {
        sendCommand("boardsize " + size);
        sendCommand("clear_board");
    }

    /**
     * Thiết lập Komi.
     * 
     * @param komi giá trị komi (thường là 6.5 hoặc 7.5)
     */
    public void setKomi(double komi) {
        sendCommand("komi " + komi);
    }

    /**
     * Yêu cầu AI đánh nước tiếp theo.
     * 
     * @param color "black" hoặc "white"
     * @return tọa độ [x, y] (0-indexed) hoặc null nếu pass/resign
     */
    public int[] generateMove(String color) {
        String response = sendCommand("genmove " + color);

        if (response == null || response.isEmpty()) {
            return null;
        }

        response = response.trim().toUpperCase();

        // Kiểm tra pass hoặc resign
        if ("PASS".equals(response) || "RESIGN".equals(response)) {
            return new int[] { -1, -1 }; // -1,-1 = pass
        }

        // Parse tọa độ: "D4" → [3, 3]
        return parseGTPCoords(response);
    }

    /**
     * Thông báo nước đi cho AI.
     * 
     * @param color "black" hoặc "white"
     * @param x     tọa độ X (0-indexed)
     * @param y     tọa độ Y (0-indexed)
     */
    public void playMove(String color, int x, int y) {
        String coords = toGTPCoords(x, y);
        sendCommand("play " + color + " " + coords);
    }

    /**
     * Thông báo pass.
     * 
     * @param color "black" hoặc "white"
     */
    public void playPass(String color) {
        sendCommand("play " + color + " pass");
    }

    /**
     * Tính điểm cuối game.
     * 
     * @return kết quả dạng "B+2.5" hoặc "W+6.5"
     */
    public String finalScore() {
        return sendCommand("final_score");
    }

    /**
     * Undo nước đi gần nhất.
     */
    public void undo() {
        sendCommand("undo");
    }

    /**
     * Reset bàn cờ.
     */
    public void clearBoard() {
        sendCommand("clear_board");
    }

    /**
     * Dừng KataGo process.
     */
    public void shutdown() {
        if (isRunning) {
            try {
                sendCommand("quit");
                Thread.sleep(500);
            } catch (Exception e) {
                // Ignore
            }
        }

        isRunning = false;

        if (katagoProcess != null) {
            katagoProcess.destroyForcibly();
            katagoProcess = null;
        }

        try {
            if (reader != null)
                reader.close();
            if (writer != null)
                writer.close();
        } catch (Exception e) {
            // Ignore
        }

        System.out.println("[KataGo] Đã dừng.");
    }

    // ==================== TIỆN ÍCH ====================

    /**
     * Chuyển tọa độ 0-indexed sang GTP format.
     * Ví dụ: (3, 3) → "D4"
     */
    private String toGTPCoords(int x, int y) {
        // GTP dùng cột A-T (bỏ I), hàng 1-19 từ dưới lên
        char col = (char) ('A' + x);
        if (col >= 'I')
            col++; // Bỏ qua chữ I
        int row = y + 1;
        return "" + col + row;
    }

    /**
     * Chuyển GTP format sang tọa độ 0-indexed.
     * Ví dụ: "D4" → [3, 3]
     */
    private int[] parseGTPCoords(String coords) {
        if (coords == null || coords.length() < 2) {
            return null;
        }

        char colChar = coords.charAt(0);
        int col = colChar - 'A';
        if (colChar > 'I')
            col--; // Bỏ qua chữ I

        int row;
        try {
            row = Integer.parseInt(coords.substring(1)) - 1;
        } catch (NumberFormatException e) {
            return null;
        }

        return new int[] { col, row };
    }
}
