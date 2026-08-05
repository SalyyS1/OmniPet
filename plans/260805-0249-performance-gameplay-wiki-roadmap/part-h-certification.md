# Phần H — Đo và chứng nhận runtime (Phase 15)

`docs/roadmap.md` nói rõ: **chưa từng có smoke test trên server thật** — không crash-injection, không process-kill, không live lifecycle, không real inventory-event, không đo hiệu năng, không chứng nhận vendor.

Plan này đổi hành vi mỗi tick ở nhiều chỗ (Phase 1, 2, 3, 4, 5, 10). Nên đây **không phải phase tuỳ chọn**, và cũng là cổng duy nhất cho phép nói bất cứ điều gì về hiệu năng.

---

## Phase 15 — Đo và chứng nhận

### 1. Đo trước/sau trên Paper 1.21 thật

Chạy với 10 / 50 / 100 pet, ghi lại:

- **Tick time** — dùng spark. Xem stage `Chunk provider tick` → tracker stage 1/2 để tách chi phí tracking khỏi chi phí logic.
- **Số entity** — HEAD renderer là 3 entity/pet, nên 100 pet = 300. Xác nhận con số thực tế và ảnh hưởng lên `getNearbyEntities` của plugin khác.
- **Băng thông** — trước/sau Phase 2 (gate packet ModelEngine).

### 2. Kiểm chứng từng phase hiệu năng bằng số

Test đơn vị xanh **không** đủ cho ba phase này. Mỗi phase phải cho ra số đo cải thiện:

| Phase | Đo gì | Kỳ vọng |
|---|---|---|
| 1 | Số object cấp phát / tick với 100 pet | Giảm rõ rệt (bỏ 4 collection/tick + buffer per-owner) |
| 2 | Packet metadata / giây với 100 pet ModelEngine đứng yên | Về gần 0 |
| 3 | Lần đọc/ghi đĩa / giây với 100 trứng đặt | Từ ~200 đọc + 100 fsync kép → gần 0 |

Nếu số không cải thiện thì phase đó **chưa xong**, dù test xanh.

### 3. Boot không có plugin tuỳ chọn

Không ModelEngine, không MythicLib, không MythicMobs, không Vault, không PlayerPoints, không LuckPerms → boot sạch, HEAD renderer hoạt động, `RendererCapabilities.animation()` là false, không exception nào escape.

Đây là điều `paper-plugin.yml` đã hứa (`required: false` cho mọi dependency) nhưng chưa ai xác nhận trên server thật.

### 4. Tắt hết công tắc

- `gui.feedback.enabled: false` → không âm thanh, không action bar, không particle, không boss bar.
- Boss bar off (mặc định).
- `render.maximumLeanDegrees: 0` → pet đứng thẳng.
- `gui.onboarding.firstJoinGreeting: false` → không chào.

Xác nhận **im lặng hoàn toàn**, không chỉ giảm.

### 5. Kiểm chứng gameplay bằng tay

Những thứ không test tự động được:

- Pet quay đúng hướng đi, không giật, **không rung khi đứng yên** (Phase 2 của plan trước).
- Trứng rung mạnh dần và nở đúng lúc (Phase 4).
- Pet ngủ khi chủ đứng yên, tỉnh khi di chuyển (Phase 5).
- Đội hình 5 pet không chồng nhau, không đồng bộ pha (Phase 10).
- Buff hết hạn đúng lúc, HUD hiện đúng thời gian còn lại (Phase 8-9).
- Cho ăn món ưa thích cho gấp đôi, bond tăng (Phase 6-7).

### 6. Kiểm chứng độ bền của Phase 3

Phase 3 đổi mô hình bền vững của trứng đặt, nên phải kiểm bằng tay:

- Đặt trứng, kill process giữa lúc đếm ngược → khởi động lại: trứng còn đó, mất tối đa một khoảng flush.
- Phá khối trứng → nhận lại đúng vật phẩm đã đặt.
- Đặt rồi phá liên tục → **không nhân bản** được.
- Vault đầy lúc trứng ready → trứng chờ, không mất.

### 7. Cập nhật tài liệu

- `docs/roadmap.md` — chỉ đánh dấu shipped những gì đã có bằng chứng. Repo có quy tắc rõ về việc này và nó đã bị vi phạm trước đây (hub và click interaction từng bị liệt là "Not implemented" trong khi đã ship).
- `CHANGELOG.md` — ghi số đo thật, không ghi "nhanh hơn".
- `docs/compatibility.md` — ma trận Paper/vendor đã thử thật.

---

## Nghiệm thu

Phase 15 xong khi:

1. Có bảng số trước/sau cho cả ba phase hiệu năng.
2. Boot sạch không plugin tuỳ chọn, có log chứng minh.
3. Mọi công tắc tắt → im lặng hoàn toàn.
4. Kiểm chứng độ bền trứng đặt qua process-kill.
5. `docs/roadmap.md` chỉ nói những gì đã chứng minh.

## Ghi chú

**Folia vẫn ngoài phạm vi.** Không có region scheduler ở đâu trong code; một task toàn cục đi qua nhiều chủ ở nhiều world là thiết kế trái ngược Folia về bản chất, không phải thứ thêm vào được. `PaperRuntimeScheduler` là seam sạch để làm sau, nhưng đó là refactor riêng của `PaperPetRuntimeCoordinator`. Ghi nhận, không hứa.
