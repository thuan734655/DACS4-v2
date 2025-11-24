package org.example.dacs4_v2.network.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import org.example.dacs4_v2.models.Game;
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

    // Lấy lịch sử (giới hạn 20 game)
    List<Game> getGameHistory(int limit) throws RemoteException;
}