# DHT (Distributed Hash Table) - Chord Algorithm trong DACS4_v2

## 📖 Tổng quan

**DHT (Distributed Hash Table)** là cấu trúc dữ liệu phân tán cho phép lưu trữ và tìm kiếm dữ liệu trong mạng P2P mà không cần server trung tâm. Dự án sử dụng biến thể **Chord-lite** của thuật toán Chord.

### Các thành phần chính

| File | Vai trò |
|------|---------|
| `P2PNode.java` | Quản lý node local, join/leave DHT network |
| `BroadcastManager.java` | Broadcast multicast để tìm peer khi join mạng |
| `GoGameServiceImpl.java` | Chứa logic Chord-lite (finger table, findSuccessor) |

---

## 🔄 Cấu trúc vòng (Ring)

Mỗi node trong mạng được xếp lên một **vòng tròn ảo** dựa trên hash của userId:

```
         ┌─────────────────┐
         │    Peer A       │
         │  (predecessor)  │
         └────────┬────────┘
                  │
         ┌────────▼────────┐
         │    Local Node   │
         │   (bạn đang ở)  │
         └────────┬────────┘
                  │
         ┌────────▼────────┐
         │    Peer B       │
         │   (successor)   │
         └─────────────────┘
```

Mỗi node có:
- **Predecessor**: node trước nó trên vòng
- **Successor**: node sau nó trên vòng
- **Finger Table**: bảng shortcut để tìm kiếm nhanh O(log N)

---

## 📊 Finger Table

### Cấu hình

```java
private static final int KEY_BITS = 160;           // SHA-1 có 160 bit
private static final int FINGER_TABLE_SIZE = 16;   // 16 entry trong finger table
private static final int FIX_FINGERS_INTERVAL_MS = 1200; // Cập nhật mỗi 1.2 giây
```

### Sơ đồ từng bước: Add Peer vào Finger Table

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        fixNextFinger()                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   ① idx = nextFingerIndex++ % 16      // Chọn slot: 0,1,2...15,0,1...  │
│                   │                                                     │
│                   ▼                                                     │
│   ② exponent = idx × 10               // Tính số mũ                    │
│                   │                                                     │
│                   ▼                                                     │
│   ③ start = selfHash + 2^exponent     // Tính vị trí trên vòng        │
│                   │                                                     │
│                   ▼                                                     │
│   ④ resolved = findSuccessorByHash(start)  // Tìm peer chịu trách nhiệm│
│                   │                                                     │
│                   ▼                                                     │
│   ⑤ fingerTable[idx] = resolved       // Lưu vào finger table          │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Ví dụ cụ thể

Giả sử bạn ở `selfHash = 100`:

| Index | Exponent | Start Point | Finger Entry |
|-------|----------|-------------|--------------|
| 0 | 0 | 100 + 2^0 = **101** | successor(101) |
| 1 | 10 | 100 + 2^10 = **1124** | successor(1124) |
| 2 | 20 | 100 + 2^20 = **~1M** | successor(~1M) |
| ... | ... | ... | ... |
| 15 | 150 | 100 + 2^150 | successor của điểm đó |

### Tại sao lấy SUCCESSOR mà không phải peer ngay tại START?

**Vì không có peer nào đứng đúng tại vị trí start!**

- Hash của mỗi peer là **ngẫu nhiên** (SHA-1 của userId)
- Không gian hash = 2^160 (cực lớn)
- Xác suất có peer đúng tại start ≈ 0%

**Giải pháp**: Tìm `successor(start)` = peer GẦN NHẤT có hash ≥ start

```
Vòng: ───A(100)────[132]────B(300)────C(600)────D(900)───
                     ↑
                  start = 132
                  
Không có peer có hash = 132
→ successor(132) = B(300) ← peer đầu tiên có hash ≥ 132
```

---

## 🔍 Hai loại khoảng: (a, b] và (a, b)

### Định nghĩa

| Tên | Ký hiệu | Ý nghĩa |
|-----|---------|---------|
| `inOpenClosedInterval(a, x, b)` | **(a, b]** | a < x **≤** b |
| `inOpenOpenInterval(a, x, b)` | **(a, b)** | a < x **<** b |

### Xử lý wrap around

Trên vòng tròn, có thể xảy ra `a > b` (khi khoảng đi qua điểm 0):

```java
// Khoảng (a, b] khi a > b (wrap around)
if (ab > 0) {
    return x.compareTo(a) > 0 || x.compareTo(b) <= 0;
}

// Ví dụ: a = 900, b = 100
// Khoảng (900, 100] bao gồm: 901, 902, ..., 999, 0, 1, ..., 100
```

### Khi nào dùng?

| Khoảng | Dùng ở đâu | Mục đích |
|--------|------------|----------|
| **(a, b]** | `findSuccessorByHash()` | Xác định **quyền sở hữu** key |
| **(a, b)** | `closestPrecedingFinger()` | Tìm **bước nhảy** (không bao gồm target) |

**Cách nhớ**:
- **`]` (closed)** = "bao gồm điểm cuối" = **quyền sở hữu**
- **)` (open)** = "không bao gồm điểm cuối" = **tìm predecessor**

---

## 🎯 Thuật toán tìm kiếm

### Hàm `findSuccessorByHash()`

```
              ┌────────────────────────┐
              │  Key nằm giữa mình     │
              │  và successor không?   │
              │  (self, succ]          │
              └────────┬───────────────┘
                       │
           ┌───────────┴───────────┐
           │                       │
         ✅ CÓ                   ❌ KHÔNG
           │                       │
           ▼                       ▼
    ┌──────────────┐    ┌──────────────────┐
    │ Trả về SUCC  │    │ Nhảy qua FINGER  │
    │ (succ sở hữu)│    │ gần target nhất  │
    └──────────────┘    └────────┬─────────┘
                                 │
                                 ▼
                        Lặp lại ở node mới
```

### Hàm `closestPrecedingFinger()`

```java
// Duyệt từ XA → GẦN (finger[15] → finger[0])
for (int i = fingerTable.size() - 1; i >= 0; i--) {
    User u = fingerTable.get(i);
    // Kiểm tra: finger có nằm GIỮA mình và target không?
    if (inOpenOpenInterval(selfHash, uh, targetHash)) {
        return u;  // Chọn finger này để nhảy
    }
}
```

**Luật chọn Finger:**
1. Finger phải NẰM SAU mình (self < finger)
2. Finger phải NẰM TRƯỚC target (finger < target)
3. Chọn finger XA NHẤT thỏa mãn điều kiện trên

---

## ⚡ Hiệu quả

| Không có Finger Table | Có Finger Table |
|---|---|
| Nhảy từng node một | Nhảy gấp đôi mỗi lần |
| **O(N)** - Chậm! | **O(log N)** - Nhanh! |
| 1000 node → ~1000 bước | 1000 node → ~10 bước |

---

## 🔧 Quá trình Join DHT Network

1. Node mới gửi broadcast `ASK_ONLINE` để tìm peer đang online
2. Peer nhanh nhất phản hồi → trở thành "entry point"
3. Node mới tính hash của userId bằng SHA-1
4. Chèn vào đúng vị trí trên vòng dựa trên hash

```java
// Hash userId
private BigInteger hashKey(String userId) {
    MessageDigest md = MessageDigest.getInstance("SHA-1");
    byte[] digest = md.digest(userId.getBytes(UTF_8));
    return new BigInteger(1, digest);  // 160-bit hash
}

// Insert vào ring
private void insertIntoRingByHash(User entry, int maxHops) {
    // Tìm vị trí chèn: x nằm giữa current và successor
    if (between(c, x, s)) {
        localUser.setNeighbor(PREDECESSOR, current);
        localUser.setNeighbor(SUCCESSOR, succ);
        
        // Thông báo các neighbor cập nhật
        stubCurrent.notifyAsSuccessor(localUser);
        stubSucc.notifyAsPredecessor(localUser);
    }
}
```

---

## 📝 Tóm tắt

> **(a, b]** để xác định "ai sở hữu", **(a, b)** để tìm "nhảy đến đâu". 
> Finger table giúp tìm nhanh O(log N) thay vì O(N).
> Successor đảm bảo luôn tìm được peer dù không ai đứng đúng vị trí start.
