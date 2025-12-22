package org.example.dacs4_v2.models;

public enum GameStatus {
    INVITE_SENT,  // ben moi
    INVITE_RECEIVED, // ben nhan
    RECEIVER_ACCEPTED_WAIT_HOST, // ben nhan accept nhung phai cho ben moi xac nhan co choi hay khong
    PLAYING, // ca 2 deu dong y
    DECLINED, 
    CANCELED, // ben nhan ok, ben moi khong ok 
    FINISHED,
    FAILED
}
