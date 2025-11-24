package org.example.dacs4_v2.network.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.User;
import org.example.dacs4_v2.models.UserConfig;
import org.example.dacs4_v2.models.Moves;

public interface IGoGameService extends Remote {
    // Mời vào phòng
    void inviteToGame(Game game) throws RemoteException;

    // Xin join phòng
    void joinRequest(UserConfig requester, String gameId) throws RemoteException;

    // Gửi nước đi
    void submitMove(Moves move, long seqNo) throws RemoteException;

    // Nhận ACK nước đi
    void moveAck(long seqNo) throws RemoteException;

    // Reconnect
    void onReconnectRequest(String peerId, String gameId) throws RemoteException;
    void onReconnectOffer(Game gameSnapshot) throws RemoteException;
    // Gửi cho peer mới: "Tôi là successor gần nhất của bạn"
    void notifyAsSuccessor1(UserConfig me, UserConfig nextSuccessor) throws RemoteException;

    // Gửi cho peer mới: "Tôi là successor thứ 2 của bạn"
    void notifyAsSuccessor2(UserConfig me) throws RemoteException;

    // Gửi cho peer mới: "Tôi là predecessor gần nhất của bạn"
    void notifyAsPredecessor1(UserConfig me, UserConfig prevPredecessor) throws RemoteException;

    // Gửi cho peer mới: "Tôi là predecessor thứ 2 của bạn"
    void notifyAsPredecessor2(UserConfig me) throws RemoteException;

    // Lấy lịch sử (giới hạn 20 game)
    List<Game> getGameHistory(int limit) throws RemoteException;

    // Thông báo khi discover người chơi online qua broadcast
    void onOnlinePeerDiscovered(User user) throws RemoteException;
}