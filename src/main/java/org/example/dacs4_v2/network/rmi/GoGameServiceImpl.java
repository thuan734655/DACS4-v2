package org.example.dacs4_v2.network.rmi;

import java.math.BigInteger;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.example.dacs4_v2.HelloApplication;
import org.example.dacs4_v2.data.GameHistoryStorage;
import org.example.dacs4_v2.game.GameContext;
import org.example.dacs4_v2.models.*;
import org.example.dacs4_v2.network.P2PContext;

/**
 * Triển khai interface IGoGameService, cung cấp các phương thức quản lý game và
 * giao tiếp P2P.
 */
public class GoGameServiceImpl extends UnicastRemoteObject implements IGoGameService {
    private final User localUser;
    private final ConcurrentHashMap<String, Game> activeGames = new ConcurrentHashMap<>();
    private final List<Game> gameHistory = new ArrayList<>();

    /**
     * Tìm game để resume: ưu tiên lấy từ cache activeGames, nếu không có thì load
     * từ lịch sử đã lưu.
     *
     * @param gameId ID của game cần tìm
     * @return game tìm thấy, hoặc null nếu không có
     */
    private Game resolveGameForResume(String gameId) {
        if (gameId == null || gameId.isEmpty()) {
            return null;
        }
        Game cached = activeGames.get(gameId);
        if (cached != null) {
            return cached;
        }
        List<Game> history = GameHistoryStorage.loadHistory(0);
        if (history == null) {
            return null;
        }
        for (Game g : history) {
            if (g != null && gameId.equals(g.getGameId())) {
                activeGames.put(gameId, g);
                return g;
            }
        }
        return null;
    }

    /**
     * Tạo bản sao (snapshot) User gọn nhẹ để tránh serialize graph neighbors qua
     * RMI / ghi JSON.
     *
     * @param u user cần snapshot
     * @return user snapshot
     */
    private static User snapshotUser(User u) {
        // Lưu/trao đổi User snapshot (không kèm neighbors) để:
        // - Tránh serialize graph neighbors quá lớn qua RMI/Gson
        // - Tránh vòng tham chiếu khi persist history
        if (u == null) {
            return null;
        }
        return new User(u.getHost(), u.getName(), u.getPort(), u.getRank(), u.getServiceName(), u.getUserId());
    }

    private static final int KEY_BITS = 160;
    private static final BigInteger KEYSPACE_MOD = BigInteger.ONE.shiftLeft(KEY_BITS);
    private static final int FINGER_TABLE_SIZE = 16;
    private static final int FIX_FINGERS_INTERVAL_MS = 1200;

    private final List<User> fingerTable = new ArrayList<>(FINGER_TABLE_SIZE);
    private final AtomicInteger nextFingerIndex = new AtomicInteger(0);
    private volatile ScheduledExecutorService fingerScheduler;

    /**
     * Khởi tạo service RMI tại local, gắn với thông tin localUser.
     *
     * @param user user local
     * @throws RemoteException lỗi RMI
     */
    public GoGameServiceImpl(User user) throws RemoteException {
        this.localUser = user;
        for (int i = 0; i < FINGER_TABLE_SIZE; i++) {
            fingerTable.add(null);
        }
    }

    /**
     * Bắt đầu tác vụ nền Chord-lite (fix-fingers) chạy định kỳ.
     */
    public synchronized void startChordLite() {
        if (fingerScheduler != null) {
            return;
        }
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chord-lite-fix-fingers");
            t.setDaemon(true);
            return t;
        });
        fingerScheduler = scheduler;

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                fixNextFinger();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, FIX_FINGERS_INTERVAL_MS, FIX_FINGERS_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Dừng scheduler Chord-lite.
     */
    public synchronized void stopChordLite() {
        ScheduledExecutorService scheduler = fingerScheduler;
        fingerScheduler = null;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * Tính và cập nhật finger tiếp theo trong finger table bằng
     * findSuccessorByHash.
     *
     * @throws RemoteException lỗi RMI
     */
    private void fixNextFinger() throws RemoteException {
        User succ = localUser.getNeighbor(NeighborType.SUCCESSOR);
        if (succ == null) {
            return;
        }

        BigInteger selfHash = hashUserId(localUser.getUserId());
        int idx = nextFingerIndex.getAndUpdate(i -> (i + 1) % FINGER_TABLE_SIZE);
        int exponent = (idx * KEY_BITS) / FINGER_TABLE_SIZE;

        BigInteger start = selfHash.add(BigInteger.ONE.shiftLeft(exponent)).mod(KEYSPACE_MOD);
        User resolved = findSuccessorByHash(start, 64);
        if (resolved != null) {
            synchronized (fingerTable) {
                fingerTable.set(idx, resolved);
            }
        }
    }

    /**
     * Hash userId vào keyspace của Chord bằng SHA-1.
     *
     * @param userId ID cần hash
     * @return giá trị hash
     */
    private static BigInteger hashUserId(String userId) {
        if (userId == null) {
            return BigInteger.ZERO;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(userId.getBytes(StandardCharsets.UTF_8));
            return new BigInteger(1, digest);
        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }

    /**
     * Kiểm tra x nằm trong khoảng (a, b] trên vòng định danh (identifier ring).
     *
     * @param a đầu khoảng
     * @param x điểm cần kiểm tra
     * @param b cuối khoảng
     * @return true nếu x thuộc (a, b], ngược lại false
     */
    private static boolean inOpenClosedInterval(BigInteger a, BigInteger x, BigInteger b) {
        if (a == null || x == null || b == null) {
            return false;
        }
        int ab = a.compareTo(b);
        if (ab < 0) {
            return a.compareTo(x) < 0 && x.compareTo(b) <= 0;
        }
        if (ab > 0) {
            return x.compareTo(a) > 0 || x.compareTo(b) <= 0;
        }
        return true;
    }

    /**
     * Kiểm tra x nằm trong khoảng (a, b) trên vòng định danh (identifier ring).
     *
     * @param a đầu khoảng
     * @param x điểm cần kiểm tra
     * @param b cuối khoảng
     * @return true nếu x thuộc (a, b), ngược lại false
     */
    private static boolean inOpenOpenInterval(BigInteger a, BigInteger x, BigInteger b) {
        if (a == null || x == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return false;
        }
        int ab = a.compareTo(b);
        if (ab < 0) {
            return a.compareTo(x) < 0 && x.compareTo(b) < 0;
        }
        return x.compareTo(a) > 0 || x.compareTo(b) < 0;
    }

    /**
     * Chọn node gần nhất (closest preceding finger) để route về phía targetHash.
     *
     * @param targetHash hash đích
     * @return node next-hop (finger) phù hợp
     */
    private User closestPrecedingFinger(BigInteger targetHash) {
        BigInteger selfHash = hashUserId(localUser.getUserId());
        synchronized (fingerTable) {
            for (int i = fingerTable.size() - 1; i >= 0; i--) {
                User u = fingerTable.get(i);

                if (u == null) {
                    continue;
                }
                String uid = u.getUserId();
                if (uid == null) {
                    continue;
                }
                BigInteger uh = hashUserId(uid);
                if (inOpenOpenInterval(selfHash, uh, targetHash)) {
                    return u;
                }
            }
        }
        return localUser.getNeighbor(NeighborType.SUCCESSOR);
    }

    /**
     * Đăng ký game outgoing (phía host) vào activeGames để các callback tìm thấy;
     * đồng thời lưu lịch sử.
     *
     * @param game game cần đăng ký
     */
    public void registerOutgoingGame(Game game) {
        // Host tạo game và lưu local ngay lập tức để có record lịch sử (INVITE_SENT) kể
        // cả đối thủ offline.
        if (game == null || game.getGameId() == null) {
            return;
        }
        activeGames.put(game.getGameId(), game);
        GameHistoryStorage.upsert(game);
    }

    @Override
    /**
     * Phía receiver: nhận lời mời (invite), lưu lịch sử, và hiện dialog để người
     * dùng Accept/Decline.
     *
     * @param game game được mời
     * @throws RemoteException lỗi RMI
     */
    public void inviteToGame(Game game) throws RemoteException {
        System.out.println("[RMI] Nhận lời mời vào game: " + (game != null ? game.getGameId() : "null"));
        if (game != null) {
            // Receiver nhận invite -> lưu record + chờ user accept/decline.
            game.setStatus(GameStatus.INVITE_RECEIVED);

            if (game.getCreatedAt() <= 0) {
                game.setCreatedAt(System.currentTimeMillis());
            }
            if (game.getRivalUser() == null) {
                game.setRivalUser(snapshotUser(localUser));
            }

            activeGames.put(game.getGameId(), game);
            GameHistoryStorage.upsert(game);

            Platform.runLater(() -> {
                String title = "Game invite";
                String msg = "Bạn được mời vào game " + game.getGameId();
                if (game.getNameGame() != null && !game.getNameGame().isEmpty()) {
                    msg += " (" + game.getNameGame() + ")";
                }

                Alert alert = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.showAndWait().ifPresent(btn -> {
                    if (btn == ButtonType.OK) {
                        System.out.println("[UI] User accepted invite for game " + game.getGameId());
                        try {
                            // Receiver accept -> callback host (handshake bước 1). Chưa mở game, chờ host
                            // confirm.
                            game.setStatus(GameStatus.RECEIVER_ACCEPTED_WAIT_HOST);
                            game.setAcceptedAt(System.currentTimeMillis());
                            GameHistoryStorage.upsert(game);

                            User host = game.getHostUser();
                            if (host != null) {
                                IGoGameService hostStub = getStub(host);
                                hostStub.onInviteResponse(game.getGameId(), snapshotUser(localUser), true);
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    } else {
                        System.out.println("[UI] User declined invite for game " + game.getGameId());
                        try {
                            // Receiver decline -> callback host để host update history và dừng chờ.
                            game.setStatus(GameStatus.DECLINED);
                            GameHistoryStorage.upsert(game);

                            User host = game.getHostUser();
                            if (host != null) {
                                IGoGameService hostStub = getStub(host);
                                hostStub.onInviteResponse(game.getGameId(), snapshotUser(localUser), false);
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        } finally {
                            activeGames.remove(game.getGameId());
                        }
                    }
                });
            });
        }
    }

    @Override
    /**
     * Phía host: nhận accept/decline từ receiver.
     * Nếu receiver accept thì hỏi host Start/Cancel (bước 2) và notify lại cho
     * receiver.
     *
     * @param gameId    ID của game
     * @param responder peer phản hồi
     * @param accepted  true nếu receiver accept
     * @throws RemoteException lỗi RMI
     */
    public void onInviteResponse(String gameId, User responder, boolean accepted) throws RemoteException {
        Game game = gameId != null ? activeGames.get(gameId) : null;
        if (game == null) {
            return;
        }

        User responderSnapshot = snapshotUser(responder);

        if (!accepted) {
            // Host nhận decline -> đóng flow.
            game.setStatus(GameStatus.DECLINED);
            game.setEndedAt(System.currentTimeMillis());

            if (responderSnapshot != null) {
                game.setRivalUser(responderSnapshot);
            }
            GameHistoryStorage.upsert(game);
            activeGames.remove(gameId);

            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Opponent declined invite for game " + gameId,
                        ButtonType.OK);
                alert.setTitle("Invite declined");
                alert.setHeaderText(null);
                alert.showAndWait();
            });
            return;
        }

        game.setStatus(GameStatus.RECEIVER_ACCEPTED_WAIT_HOST);
        game.setAcceptedAt(System.currentTimeMillis());

        if (responderSnapshot != null) {
            game.setRivalUser(responderSnapshot);
        }
        GameHistoryStorage.upsert(game);

        Platform.runLater(() -> {
            // Host confirm bước 2: start/cancel.
            String msg = "Opponent accepted invite for game " + gameId + ". Start now?";
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
            alert.setTitle("Start game");
            alert.setHeaderText(null);
            alert.showAndWait().ifPresent(btn -> {
                boolean start = btn == ButtonType.OK;
                try {
                    if (start) {
                        // Start: host vào game và thông báo receiver vào game.
                        game.setStatus(GameStatus.PLAYING);
                        game.setStartedAt(System.currentTimeMillis());

                        GameHistoryStorage.upsert(game);
                        GameContext.getInstance().setCurrentGame(game);
                        GameContext.getInstance().setViewOnly(false);
                        HelloApplication.navigateTo("game.fxml");
                    } else {
                        // Cancel: host không chơi -> notify receiver để receiver thoát trạng thái chờ.
                        game.setStatus(GameStatus.CANCELED);
                        game.setEndedAt(System.currentTimeMillis());
                        GameHistoryStorage.upsert(game);
                        activeGames.remove(gameId);
                    }

                    if (responderSnapshot != null) {
                        IGoGameService stub = getStub(responderSnapshot);
                        stub.onHostDecision(gameId, start);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        });
    }

    @Override
    /**
     * Phía receiver: nhận quyết định Start/Cancel từ host và vào game hoặc đóng
     * flow.
     *
     * @param gameId ID của game
     * @param start  true nếu host start game
     * @throws RemoteException lỗi RMI
     */
    public void onHostDecision(String gameId, boolean start) throws RemoteException {
        Game game = gameId != null ? activeGames.get(gameId) : null;
        if (game == null) {
            return;
        }

        if (!start) {
            // Receiver nhận cancel từ host.
            game.setStatus(GameStatus.CANCELED);
            game.setEndedAt(System.currentTimeMillis());

            GameHistoryStorage.upsert(game);
            activeGames.remove(gameId);

            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Host canceled invite for game " + gameId,
                        ButtonType.OK);
                alert.setTitle("Invite canceled");
                alert.setHeaderText(null);
                alert.showAndWait();
            });
            return;
        }

        game.setStatus(GameStatus.PLAYING);
        game.setStartedAt(System.currentTimeMillis());
        GameHistoryStorage.upsert(game);

        Platform.runLater(() -> {
            GameContext.getInstance().setCurrentGame(game);
            GameContext.getInstance().setViewOnly(false);
            HelloApplication.navigateTo("game.fxml");
        });
    }

    @Override
    /**
     * Resume (phía nhận request): hiện dialog accept/decline.
     * Nếu accept thì gửi onResumeResponse(accepted=true) cho peer kia và cả 2 vào
     * game ngay.
     *
     * @param gameId    ID của game
     * @param requester peer gửi yêu cầu resume
     * @throws RemoteException lỗi RMI
     */
    public void requestResume(String gameId, User requester) throws RemoteException {
        Game game = resolveGameForResume(gameId);
        if (game == null) {
            return;
        }

        User requesterSnapshot = snapshotUser(requester);
        Platform.runLater(() -> {
            String msg = "Opponent requests to resume game " + gameId + ". Accept?";
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
            alert.setTitle("Resume game");
            alert.setHeaderText(null);
            alert.showAndWait().ifPresent(btn -> {
                boolean accepted = btn == ButtonType.OK;

                if (accepted) {
                    // Vào game TRƯỚC để đảm bảo bên nhận luôn vào game khi accept
                    GameContext.getInstance().setCurrentGame(game);
                    GameContext.getInstance().setViewOnly(false);
                    HelloApplication.navigateTo("game.fxml");
                }

                // Gửi response qua RMI trong background thread để tránh block UI
                if (requesterSnapshot != null) {
                    final boolean finalAccepted = accepted;
                    new Thread(() -> {
                        try {
                            IGoGameService stub = getStub(requesterSnapshot);
                            stub.onResumeResponse(gameId, snapshotUser(localUser), finalAccepted);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }, "resume-response-thread").start();
                }
            });
        });
    }

    @Override
    /**
     * Resume (phía gửi request): nhận accept/decline.
     * Nếu accept thì vào game ngay (không có bước confirm nào nữa).
     *
     * @param gameId    ID của game
     * @param responder peer phản hồi
     * @param accepted  true nếu peer accept
     * @throws RemoteException lỗi RMI
     */
    public void onResumeResponse(String gameId, User responder, boolean accepted) throws RemoteException {
        System.out.println("accept " + accepted);
        Game game = resolveGameForResume(gameId);
        if (game == null) {
            return;
        }

        if (!accepted) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Opponent declined resume for game " + gameId,
                        ButtonType.OK);
                alert.setTitle("Resume declined");
                alert.setHeaderText(null);
                alert.showAndWait();
            });
            return;
        }

        Platform.runLater(() -> {
            GameContext.getInstance().setCurrentGame(game);
            GameContext.getInstance().setViewOnly(false);
            HelloApplication.navigateTo("game.fxml");
        });
    }

    @Override
    /**
     * Nhận nước đi từ peer remote, append/persist vào game, rồi notify UI thông qua
     * GameContext.
     *
     * @param move  nước đi
     * @param seqNo số thứ tự (seq) của move
     * @throws RemoteException lỗi RMI
     */
    public void submitMove(Moves move, long seqNo) throws RemoteException {
        Game game = null;
        if (move != null && move.getGameId() != null) {
            game = activeGames.get(move.getGameId());
        }
        if (game != null) {
            // Nhận move từ remote -> append vào game + notify UI + persist history.
            game.addMove(move);
            System.out.println("[RMI] Nhận nước đi: " + move);

            GameContext.getInstance().setCurrentGame(game);
            GameContext.getInstance().notifyMoveReceived(move);
            GameHistoryStorage.upsert(game);
            // Gửi ACK
            // clientService.moveAck(seqNo);
        }
    }

    @Override
    /**
     * Nhận ACK cho seqNo của move (hiện tại chỉ log).
     *
     * @param seqNo số thứ tự (seq) của move
     * @throws RemoteException lỗi RMI
     */
    public void moveAck(long seqNo) throws RemoteException {
        System.out.println("[RMI] Nhận ACK move #" + seqNo);
    }

    @Override
    /**
     * Hook reconnect (legacy): dùng để request snapshot game (hiện tại chỉ log).
     *
     * @param peerId ID của peer
     * @param gameId ID của game
     * @throws RemoteException lỗi RMI
     */
    public void onReconnectRequest(String peerId, String gameId) throws RemoteException {
        Game game = activeGames.get(gameId);
        if (game != null && game.isParticipant(peerId)) {
            // Gửi snapshot
            // peerService.onReconnectOffer(cloneGame(game));

            System.out.println("[RMI] Gửi snapshot game " + gameId + " cho " + peerId);
        }
    }

    @Override
    /**
     * Nhận snapshot game từ peer và lưu vào activeGames.
     *
     * @param gameSnapshot snapshot game
     * @throws RemoteException lỗi RMI
     */
    public void onReconnectOffer(Game gameSnapshot) throws RemoteException {
        System.out.println("[RMI] Nhận snapshot game: " + gameSnapshot.getGameId());
        activeGames.put(gameSnapshot.getGameId(), gameSnapshot);
    }

    @Override
    /**
     * Load lịch sử game từ storage.
     *
     * @param limit số lượng tối đa game cần load
     * @return danh sách game lịch sử
     * @throws RemoteException lỗi RMI
     */
    public List<Game> getGameHistory(int limit) throws RemoteException {
        return GameHistoryStorage.loadHistory(limit);
    }

    @Override
    /**
     * Thông báo peer online để node/UI local cập nhật (dashboard/neighbors).
     *
     * @param user peer online
     * @throws RemoteException lỗi RMI
     */
    public void onOnlinePeerDiscovered(User user) throws RemoteException {
        P2PContext ctx = P2PContext.getInstance();
        if (ctx.getNode() != null) {
            ctx.getNode().firstPeerOnNet(user);
        }
    }

    @Override
    /**
     * Tra cứu peer theo peerId qua DHT (có giới hạn số hop).
     *
     * @param targetPeerId ID peer cần tìm
     * @param maxHops      số hop tối đa
     * @return peer tìm thấy, hoặc null nếu không có
     * @throws RemoteException lỗi RMI
     */
    public User findPeerById(String targetPeerId, int maxHops) throws RemoteException {
        if (maxHops <= 0 || targetPeerId == null || targetPeerId.isEmpty()) {
            return null;
        }

        String myId = localUser.getUserId();
        if (myId != null && myId.equals(targetPeerId)) {
            return localUser;
        }

        BigInteger targetHash = hashUserId(targetPeerId);
        User resolved = findSuccessorByHash(targetHash, maxHops);
        if (resolved == null) {
            return null;
        }
        if (resolved.getUserId() != null && resolved.getUserId().equals(targetPeerId)) {
            return resolved;
        }
        return null;
    }

    @Override
    /**
     * Route trong DHT để tìm successor chịu trách nhiệm cho targetHash.
     *
     * @param targetHash hash đích
     * @param maxHops    số hop tối đa
     * @return successor phù hợp, hoặc null nếu không có
     * @throws RemoteException lỗi RMI
     */
    public User findSuccessorByHash(BigInteger targetHash, int maxHops) throws RemoteException {
        if (maxHops <= 0 || targetHash == null) {
            return null;
        }

        BigInteger selfHash = hashUserId(localUser.getUserId());
        if (targetHash.equals(selfHash)) {
            return localUser;
        }
        User succ = localUser.getNeighbor(NeighborType.SUCCESSOR);
        if (succ == null) {
            return localUser;
        }

        String succId = succ.getUserId();
        BigInteger succHash = hashUserId(succId);
        if (inOpenClosedInterval(selfHash, targetHash, succHash)) {
            return succ;
        }

        User nextHop = closestPrecedingFinger(targetHash);
        if (nextHop == null || nextHop.getUserId() == null) {
            return succ;
        }
        if (localUser.getUserId() != null && localUser.getUserId().equals(nextHop.getUserId())) {
            return succ;
        }

        try {
            IGoGameService stub = getStub(nextHop);
            return stub.findSuccessorByHash(targetHash, maxHops - 1);
        } catch (Exception e) {
            e.printStackTrace();
            return succ;
        }
    }

    /**
     * Cập nhật successor local và yêu cầu UI refresh.
     *
     * @param nextSuccessor successor mới
     * @throws RemoteException lỗi RMI
     */
    public void notifyAsSuccessor(User nextSuccessor) throws RemoteException {
        localUser.setNeighbor(NeighborType.SUCCESSOR, nextSuccessor);
        System.out.println("da cap nhat succPeer la: " + nextSuccessor.getName());

        P2PContext ctx = P2PContext.getInstance();
        ctx.requestNeighborUiUpdate();
    }

    /**
     * Cập nhật predecessor local và yêu cầu UI refresh.
     *
     * @param prevPredecessor predecessor mới
     * @throws RemoteException lỗi RMI
     */
    public void notifyAsPredecessor(User prevPredecessor) throws RemoteException {
        localUser.setNeighbor(NeighborType.PREDECESSOR, prevPredecessor);
        System.out.println("da cap nhat prevPeer la: " + prevPredecessor.getName());

        P2PContext ctx = P2PContext.getInstance();
        ctx.requestNeighborUiUpdate();
    }

    @Override
    /**
     * Lấy successor hiện tại.
     *
     * @return successor hiện tại
     * @throws RemoteException lỗi RMI
     */
    public User getSuccessor() throws RemoteException {
        return localUser.getNeighbor(NeighborType.SUCCESSOR);
    }

    @Override
    /**
     * Lấy predecessor hiện tại.
     *
     * @return predecessor hiện tại
     * @throws RemoteException lỗi RMI
     */
    public User getPredecessor() throws RemoteException {
        return localUser.getNeighbor(NeighborType.PREDECESSOR);
    }

    /**
     * Tiện ích tạo RMI stub theo cấu hình peer (host/port/serviceName).
     *
     * @param config cấu hình peer
     * @return RMI stub
     * @throws Exception lỗi bất kỳ
     */
    public static IGoGameService getStub(User config) throws Exception {
        String url = "rmi://" + config.getHost() + ":" + config.getPort() + "/" + config.getServiceName();
        return (IGoGameService) Naming.lookup(url);
    }

    @Override
    /**
     * Xử lý thông báo khi đối thủ thoát/tạm dừng game.
     * Hiển thị dialog cho người chơi còn lại.
     *
     * @param gameId ID của game
     * @param user   người thoát
     * @param reason lý do ("EXIT", "DISCONNECT", "SURRENDER")
     * @throws RemoteException lỗi RMI
     */
    public void notifyGamePaused(String gameId, User user, String reason) throws RemoteException {
        if (gameId == null || user == null)
            return;

        Game game = resolveGameForResume(gameId);
        if (game == null)
            return;

        String userName = user.getName() != null ? user.getName() : "Đối thủ";
        String message;

        switch (reason) {
            case "EXIT":
                message = userName + " đã thoát khỏi trận đấu.\nGame sẽ được tạm dừng và có thể tiếp tục sau.";
                game.setStatus(GameStatus.PAUSED);
                break;
            case "DISCONNECT":
                message = userName + " đã mất kết nối.\nGame sẽ được tạm dừng.";
                game.setStatus(GameStatus.PAUSED);
                break;
            case "SURRENDER":
                message = userName + " đã đầu hàng!\nBạn thắng!";
                game.setStatus(GameStatus.FINISHED);
                game.setEndedAt(System.currentTimeMillis());
                break;
            default:
                message = userName + " đã rời game.";
                game.setStatus(GameStatus.PAUSED);
        }

        // Lưu trạng thái game
        GameHistoryStorage.upsert(game);

        // Hiển thị thông báo
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thông báo");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();

            // Nếu surrender thì chuyển về rooms
            if ("SURRENDER".equals(reason)) {
                HelloApplication.navigateTo("rooms.fxml");
            }
        });
    }
}