# Phần D — Buff tạm thời (Phase 8-9)

`PetStatBuff` hiện là `(petInstanceId, statId, modifierType, value)` — **không có field thời hạn nào**. Đây là hạ tầng mới, và là phần **gate** cho món ăn có thời hạn (Phase 6) và synergy đội hình (Phase 11). Làm đúng một lần ở đây thì hai phase kia thành phần thêm vào.

---

## Phase 8 — Cơ chế buff hết hạn

### Việc cần làm

1. **Thêm `expiresAtEpochMillis` (nullable) + `stackMode`** vào `PetStatBuff`. Lưu **wall-clock tuyệt đối**, không lưu tick — tick không tồn tại khi server tắt.
2. **Sweep hết hạn bump revision.** `PaperOwnerBuffCoordinator` đã là reconcile theo revision, nên chỉ cần buff hết hạn làm revision tăng là nó tự rút buff. **Không cần cơ chế mới** — đây là lý do chọn cách này.
3. **Luật xếp chồng rõ ràng**, khai báo trên từng buff:
   - `DURATION` — cộng thời gian, có trần.
   - `INTENSITY` — mạnh hơn theo số stack, có trần stack.
   - `REPLACE_IF_STRONGER` — mạnh đè yếu, thời hạn là tiêu chí phụ.
4. **Luật "pandemic"** cho việc gia hạn cùng nguồn: cộng thêm tối đa **30% thời gian gốc**, trần **130%**. Đây là cách WoW giải quyết cả hai thất bại — restart thuần thì phạt người gia hạn sớm (mất phần còn lại), extend thuần thì cho bank thời gian vô hạn.
5. **Trần cứng**: thời hạn tối đa mỗi buff + số buff đồng thời mỗi chủ. **Đây là đòn chống lạm dụng chính**, quan trọng hơn luật stack.
6. **Quyết định offline: chạy theo wall-clock.** Đơn giản, và không có lỗ hổng đóng băng đồng hồ bằng cách thoát game.
7. **Buff không sống qua `/pet release` hoặc vault-out.** Chặn lỗ hổng bank buff bằng cách xoay pet ra vào vault.

### Cạm bẫy đã biết

Nếu luật có ngưỡng (30%/130%) thì **UI phải hiện ngưỡng đó**. Bài học từ WoW: luật pandemic chỉ chơi được vì có addon hiện thời gian còn lại; không có hiển thị thì người chơi đoán sai và chơi sai. Đây là lý do Phase 9 không phải tuỳ chọn.

### Về MythicLib

MythicLib có modifier theo thời gian (`/ml tempstat add <player> <STAT> 50s`), nhưng `StatModifier` **không có field duration** — thời hạn do tầng command xử lý. Signature công khai không rõ ràng, và API đang được làm lại quanh 1.6/1.7.

**Kết luận: tự sweep phía plugin, không phụ thuộc.** Thiết kế reconcile-theo-revision vốn đã hỗ trợ điều đó, nên đây không phải đánh đổi.

### File chính

- `omnipet-core/.../core/buff/PetStatBuff.java`
- `omnipet-core/.../core/buff/PetStatBuffProjection.java`
- `omnipet-paper/.../paper/buff/PaperOwnerBuffCoordinator.java`

### Nghiệm thu

- Test buff hết hạn tự rút khỏi projection **mà không cần gọi gì thêm** (qua revision bump).
- Test luật pandemic không cho vượt 130% thời gian gốc.
- Test trần số buff đồng thời mỗi chủ.
- Test cycle vault-out/vault-in **không** giữ buff.
- Test buff persist qua restart theo wall-clock (hết hạn lúc offline thì lúc vào lại đã hết).

---

## Phase 9 — Hiển thị buff

**Một buff không thấy được là một buff bị bỏ phí.**

### Việc cần làm

1. **HUD có công tắc**, ba chế độ: boss bar cho buff dài nhất / action-bar gộp / tắt. **Boss bar mặc định TẮT** — theo nghiên cứu đây là kênh dễ gây khó chịu nhất nếu lạm dụng.
2. **Màu leo thang + âm báo** ở vài giây cuối (mẫu BossBarTimer: vàng → đỏ, tick âm ở cuối, âm kết thúc).
3. **Tooltip trong menu quản lý** liệt kê mọi buff đang chạy, thời gian còn lại, và **luật stack đang áp dụng**. Đây là chỗ hiện ngưỡng pandemic.
4. **Cập nhật 5-10 tick/lần**, không mỗi tick. Huỷ task khi disconnect — UltraBar từng ship bug NPE flood đúng vì thiếu điều này.

### Ràng buộc kỹ thuật

- Boss bar màu chỉ có 7 giá trị cố định.
- Placeholder theo từng người chơi cần **một BossBar instance mỗi người** — bar chia sẻ không mang text riêng được.
- Action bar là **một khe duy nhất toàn cục** và đã bị tranh chấp: OmniPet vừa dùng nó ở phase trước cho xác nhận hành động, và các plugin khác (toạ độ, TPS) cũng dùng. Nên chế độ action-bar phải gộp, không được đấu với `FeedbackService`.

### Đi qua `FeedbackService`

Mọi kênh hiển thị đi qua `FeedbackService` như phase trước đã thiết lập, để thừa hưởng rate limit và công tắc `gui.feedback.enabled`. Không mở đường riêng.

### File chính

- `omnipet-paper/.../paper/feedback/FeedbackService.java`
- `omnipet-paper/.../paper/feedback/FeedbackOutput.java` (thêm kênh boss bar)
- `omnipet-paper/.../paper/gui/player/PetManagementMenuRenderer.java`
- `omnipet-paper/.../paper/text/MessageKey.java`

### Nghiệm thu

- Test `gui.feedback.enabled: false` → không boss bar, không action bar.
- Test boss bar tắt mặc định.
- Test task bị huỷ khi người chơi thoát (không leak, không NPE).
- Test tooltip hiện đúng thời gian còn lại và luật stack.
