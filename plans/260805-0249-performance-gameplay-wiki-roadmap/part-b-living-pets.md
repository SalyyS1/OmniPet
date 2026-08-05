# Phần B — Pet sống động (Phase 4-5)

Hai phase rẻ nhất trên mỗi đơn vị "cảm giác có sức sống". Nghiên cứu cho thấy đây là thứ người chơi nhận ra nhanh nhất, và tốn ít nhất.

---

## Phase 4 — Trứng rung lắc trước khi nở ✅

Việc bạn nêu trực tiếp. Làm được **ngay sau Phase 3** vì lúc đó vòng trứng mới đủ rẻ để thêm hiệu ứng vào.

### Việc cần làm

1. **Rung theo tiến trình.** Càng gần nở càng lắc mạnh. Dùng `Transformation` của display — kênh này đã dùng cho scale và lean nên không phải mở đường mới. Biên độ là hàm của phần trăm thời gian còn lại.
2. **Nhịp dồn ở cuối** — vài giây trước khi nở lắc rõ rệt kèm particle, phát **qua `FeedbackService`**, không rải `spawnParticle` trực tiếp (nguyên tắc đã thiết lập ở phase trước, và có source-contract test canh).
3. **Dùng `EffectBudget`.** Class này (`core/runtime/EffectBudget.java`) đã có giới hạn per-tick/per-pet/cooldown nhưng **không nơi nào trong production gọi**. Nhiều trứng cạnh nhau không được nhân hiệu ứng lên — đây đúng là việc nó được viết ra để làm.
4. **Hologram nhấn khi sẵn sàng** — đổi màu hoặc thêm dòng, tận dụng `EGG_HOLOGRAM_READY` đã có.
5. **Chỉ rung khi có người trong tầm nhìn** — cùng điều kiện với việc bỏ cập nhật hologram ở Phase 3.

### Ràng buộc

`RuntimeTransform` từ chối scale ≤ 0 hoặc > 64. Nếu rung có thành phần scale (squash) thì phải nằm trong khoảng đó.

### File chính

- `omnipet-paper/.../paper/incubation/placed/PlacedEggView.java`
- `omnipet-paper/.../paper/incubation/placed/PlacedEggHolograms.java`
- `omnipet-core/.../core/runtime/EffectBudget.java` (nối vào production)

### Nghiệm thu

- Test biên độ rung là hàm đơn điệu theo tiến trình và **bằng 0 khi vừa đặt**.
- Test không hiệu ứng nào phát khi không có người xem.
- Test `EffectBudget` thực sự chặn khi vượt hạn — hiện chỉ có test đơn vị cho class, chưa có test đường tích hợp.

---

## Phase 5 — Hành vi nhàn rỗi ✅

Rẻ, và theo nghiên cứu là thứ người chơi để ý nhất. Nguyên tắc rút ra: **vòng lặp cố định bị phát hiện ngay**, cách sửa là ngẫu nhiên hoá trong code chứ không phải thêm animation.

### Đã làm

`IdleBehaviour` (core) giữ toàn bộ quyết định — state machine ACTIVE/ATTENTIVE/RESTING, tính cách roll từ UUID, chọn one-shot, giãn cách có jitter, và yaw quay về chủ. `PaperRuntimePetState` chạy đồng hồ; `RuntimeTransform` mang `IdleBehaviour.Pose` (gộp lại chứ không thêm field lẻ vào port công khai); `MovementGait.REST` + clip `rest`/flourish trong `ModelEngineAnimations` biến nó thành animation.

Gate "không ai xem" đến miễn phí: người xem duy nhất của một pet là chủ nó, và `PaperRuntimeOwnerEngine` đã dọn state khi chủ không hiện diện — nên chủ offline vừa là zero update vừa là không tích luỹ thời gian ngồi. Có test canh.

### Hoãn có chủ ý

- **Phản ứng môi trường** (mưa, nước, chủ bị đánh, chủ lên cấp) — event-driven, không chung file với state machine, và không có gì trong Phase 6-15 chặn nó. Tách ra để phase này không phải chạm listener.
- **Pet nhận biết nhau** — cần vị trí đồng đội, đã ghi là để sau Phase 10.

### Việc cần làm

1. **Ngủ khi chủ đứng yên** (~30s) — pet ngồi/cuộn tròn, chạy một shot animation, tỉnh khi chủ di chuyển. Đây là hành vi được ghi nhận rõ nhất trong nghiên cứu (Petlings).
2. **Nhìn theo chủ khi nhàn rỗi.** Phase 2 của plan trước đã cho pet quay theo hướng đi; đây là phần bù cho lúc đứng yên. Renderer đã điều khiển yaw nên không cần đường mới.
3. **Một shot ngẫu nhiên có jitter** — nhảy, lắc, hít hà. Khoảng cách phải ngẫu nhiên; chu kỳ cố định đọc ra như máy.
4. **Tính cách mỗi pet** — `playfulness`/`curiosity`/`laziness` roll lúc nở, lưu trong `PetInstance.extensions` (map mở, không cần migration), làm lệch việc chọn hành vi và ngưỡng ngủ. Cách rẻ nhất để hai pet cùng model cảm giác khác nhau.
5. **Phản ứng môi trường** — mưa bắt đầu, xuống nước, chủ bị đánh, chủ lên cấp. Event-driven, gần như miễn phí.
6. **Lệch pha bắt buộc** trên mọi timer định kỳ. `PaperRuntimePetState` đã có lệch pha tất định theo UUID cho bob; mở rộng nguyên tắc đó cho hành vi mới.
7. **Tạm dừng khi không ai xem.**

### Cân nhắc: pet nhận biết nhau

Nghiên cứu đánh giá cao việc hai pet nhàn rỗi cạnh nhau chơi với nhau — và đó cũng là lý do thị giác mạnh nhất để dắt nhiều pet. Nhưng nó cần biết vị trí các pet khác trong cùng đội, nên **để sau Phase 10** (đội hình) chứ không làm ở đây.

### Ràng buộc

- Trạng thái hành vi mới bị reset khi chủ đổi world/quit/reload (`PaperRuntimeOwnerEngine.cleanup` xoá `PaperRuntimePetState`). Chấp nhận được — nhưng **không** lưu tiến trình animation vào state bền vững.
- Mọi thứ phải chạy main thread; `states`/`handles` là `LinkedHashMap` chỉ được bảo vệ bằng kỷ luật main-thread.

### File chính

- `omnipet-paper/.../paper/runtime/PaperRuntimePetState.java`
- `omnipet-paper/.../paper/runtime/PaperRuntimeOwnerEngine.java`
- `omnipet-core/.../core/runtime/MovementController.java` (nguồn trạng thái nhàn rỗi)
- `omnipet-paper/.../paper/render/ModelEngineAnimations.java` (clip cho hành vi mới)

### Nghiệm thu

- Test chọn hành vi **tất định theo seed** — để tái lập được, và để test không phập phù.
- Test không tick hành vi nào khi không có người xem.
- Test tính cách roll từ UUID pet là tất định (cùng pet → cùng tính cách qua nhiều lần khởi động).
