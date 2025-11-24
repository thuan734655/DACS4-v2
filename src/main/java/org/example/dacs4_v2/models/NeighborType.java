package org.example.dacs4_v2.models;

public enum NeighborType {
    SUCCESSOR_1,   // liền sau gần nhất
    SUCCESSOR_2,   // liền sau thứ 2 (fault tolerance)
    PREDECESSOR_1, // liền trước gần nhất
    PREDECESSOR_2  // liền trước thứ 2
}