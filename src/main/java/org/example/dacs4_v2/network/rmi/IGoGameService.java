package org.example.dacs4_v2.network.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.math.BigInteger;
import java.util.List;
import org.example.dacs4_v2.models.Game;
import org.example.dacs4_v2.models.User;
import org.example.dacs4_v2.models.Moves;

public interface IGoGameService extends Remote {
    void inviteToGame(Game game) throws RemoteException;
    // Receiver -> Host: báo accept/decline cho lời mời (handshake bước 1)
    void onInviteResponse(String gameId, User responder, boolean accepted) throws RemoteException;
    // Host -> Receiver: host quyết định start/cancel sau khi receiver accept (handshake bước 2)
    void onHostDecision(String gameId, boolean start) throws RemoteException;

    void requestResume(String gameId, User requester) throws RemoteException;
    void onResumeResponse(String gameId, User responder, boolean accepted) throws RemoteException;

    void submitMove(Moves move, long seqNo) throws RemoteException;
    void moveAck(long seqNo) throws RemoteException;
    void onReconnectRequest(String peerId, String gameId) throws RemoteException;
    void onReconnectOffer(Game gameSnapshot) throws RemoteException;
    void notifyAsSuccessor(User nextSuccessor) throws RemoteException;
    void notifyAsPredecessor(User prevPredecessor) throws RemoteException;
    User getSuccessor() throws RemoteException;
    User getPredecessor() throws RemoteException;
    List<Game> getGameHistory(int limit) throws RemoteException;
    void onOnlinePeerDiscovered(User user) throws RemoteException;
    User findPeerById(String targetPeerId, int maxHops) throws RemoteException;
    User findSuccessorByHash(BigInteger targetHash, int maxHops) throws RemoteException;
}