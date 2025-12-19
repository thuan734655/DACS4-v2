package org.example.dacs4_v2.network.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.User;
import org.example.dacs4_v2.models.Moves;

public interface IGoGameService extends Remote {
    void inviteToGame(Game game) throws RemoteException;
    void joinRequest(User requester, String gameId) throws RemoteException;
    void submitMove(Moves move, long seqNo) throws RemoteException;
    void moveAck(long seqNo) throws RemoteException;
    void onReconnectRequest(String peerId, String gameId) throws RemoteException;
    void onReconnectOffer(Game gameSnapshot) throws RemoteException;
    void notifyAsSuccessor( User nextSuccessor) throws RemoteException;
    void notifyAsPredecessor( User prevPredecessor) throws RemoteException;
    List<Game> getGameHistory(int limit) throws RemoteException;
    void onOnlinePeerDiscovered(User user) throws RemoteException;
    User findPeerById(String targetPeerId, int maxHops) throws RemoteException;
}