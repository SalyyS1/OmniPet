# Phần E — Nhiều pet và đội hình (Phase 10-11)

Schema **đã** hỗ trợ nhiều pet: `PlayerState.desiredActivePetIds` là `List<UUID>`, `SlotEntitlement.MAX_SLOT` là 64. Cái thiếu là hình dạng thị giác và một màn hình để quản lý. Đây là lý do phần này là "lộ ra cái đã có", không phải "xây hệ thống mới".

---

## Phase 10 — Đội hình

### Việc cần làm

1. **Mở rộng `MovementPattern`** thêm layout theo đội: `LINE`, `WEDGE`, `RING`, `SCATTER`. Offset tính theo **chỉ số của pet trong đội**, để 3 pet không chồng lên nhau một khối. `MovementController.baseTarget` đã tính offset từ forward/side của chủ nên đây là phần mở rộng, không phải viết lại.
2. **Lệch pha bắt buộc** trên mọi timer bob/hop/idle. Pet đồng bộ hoàn hảo **đọc ra như một lỗi** — có cả mod Skyrim tồn tại chỉ để sửa việc này. `PaperRuntimePetState` đã có lệch pha tất định theo UUID; mở rộng cho mọi hành vi mới.
3. **Giới hạn theo số slot, không theo sức mạnh.** Cấp số pet không được kéo theo cấp số sức mạnh — nếu không thì đội đông luôn thắng và việc chọn pet mất nghĩa.

### Cạm bẫy hiệu năng

Nghiên cứu chỉ ra rõ: chi phí display entity là **tracking, không phải ticking**. Tệ hơn, mỗi entity thêm vào làm chậm `getNearbyEntities`/`getEntity` của **mọi plugin** trên server. Với HEAD renderer, mỗi pet = 3 entity → 100 pet = 300 entity.

Giảm nhẹ nếu cần: `spigot.yml entity-tracking-range.display` và `entities.tracking-range-y.display` của Paper. Chẩn đoán bằng spark, xem stage `Chunk provider tick` → tracker.

**Chưa đưa packet-based entity vào plan** — đó là refactor lớn, và Phase 15 sẽ nói có cần hay không. Không đoán trước.

### File chính

- `omnipet-core/.../core/runtime/MovementPattern.java`
- `omnipet-core/.../core/runtime/MovementController.java`
- `omnipet-core/.../core/runtime/MovementProfile.java`
- `omnipet-paper/.../paper/runtime/PaperRuntimeOwnerSnapshot.java` (chỉ số trong đội)

### Nghiệm thu

- Test offset đội hình **không cho hai pet cùng vị trí** với mọi kích thước đội 1..N.
- Test lệch pha: hai pet cùng definition, cùng thời điểm → pha khác nhau.
- Test đội hình tôn trọng `maxSpeed`/`safetySnapDistance` như pattern cũ.

---

## Phase 11 — GUI đội hình + synergy

### Việc cần làm

1. **Màn hình Active Party.** Đang nằm ở `docs/roadmap.md` dạng deferred. Xem mọi pet đang hoạt động, sắp lại thứ tự, đổi nhanh — một màn hình duy nhất. Hiện hệ thống slot hoạt động **gần như vô hình** với người chơi: họ mua slot nhưng không có chỗ nào thấy nó làm gì.
2. **Vai trò + synergy.** `role` trên `PetDefinition` (TANK/DPS/SUPPORT/UTILITY), `synergies.yml` khai báo cửa sổ số lượng vai trò → buff cho chủ. **Tái dùng `PaperOwnerBuffCoordinator` nguyên vẹn** — nó vốn là reconcile theo revision.
3. **Thông báo synergy khi triệu hồi** — **không phải tuỳ chọn.** Bài học từ plugin synergy của SRPG: hệ thống synergy thất bại trong im lặng. Nếu người chơi không thấy synergy kích hoạt thì với họ nó không tồn tại. Actionbar/toast liệt kê synergy đang có khi đội thay đổi.

### Cạm bẫy quan trọng nhất: buff dự bị

Nghiên cứu tìm ra điều này rất rõ và nó đi ngược trực giác:

> Một buff **phẳng** từ pet để ở kho **đảm bảo cho pet đó một chỗ dự bị vĩnh viễn** — đúng ngược lại mục tiêu khuyến khích đổi pet.

Nếu làm buff dự bị thì **phải có điều kiện** (chỉ trong một biome, chỉ N phút sau khi triệu hồi, chỉ pet trên cấp X) hoặc **suy giảm**. Không thì nó khoá cứng đội hình và làm mọi pet khác thành vô nghĩa.

### Cạm bẫy thứ hai: lạm phát action economy

Càng nhiều pet đồng thời thì mỗi pet phải yếu đi để cân bằng, và kết quả là **không ai cảm thấy mạnh**. Chặn bằng cách giới hạn **độ lớn synergy**, không giới hạn số slot.

### Cosmetic cũng phải dùng được

Server thiên cả hai hướng, nên synergy phải **tắt được** và độ lớn phải cấu hình về 0 mà party UI vẫn hoạt động. Một server cosmetic vẫn cần màn hình đội hình để sắp xếp và đổi pet — đó là giá trị độc lập với việc buff có tồn tại hay không.

Cân nhắc thêm hướng cosmetic cho synergy: thay vì chỉ số, một đội đủ vai trò mở khoá ngoại hình hoặc một hiệu ứng thị giác dùng chung. Khung EULA-an-toàn từ nghiên cứu là giá trị của pet nằm ở ngoại hình/độ hiếm/danh tiếng — sức mạnh là tuỳ chọn của operator.

### Phụ thuộc

**CẦN Phase 8** — synergy là buff, và buff có điều kiện cần cơ chế hết hạn/reconcile của Phase 8. Làm trước sẽ phải viết lại.

**CẦN Phase 10** — không có đội hình thì party UI quản lý một thứ vô hình.

### File chính

- `omnipet-paper/.../paper/gui/player/` (màn hình party mới)
- `omnipet-paper/.../paper/gui/MenuLayout.java` + `MenuPage.java` (hạ tầng đã có từ phase trước)
- `omnipet-core/.../core/buff/PetStatBuffProjection.java`
- `omnipet-paper/.../paper/buff/PaperOwnerBuffCoordinator.java`

### Nghiệm thu

- Test synergy **chỉ** kích hoạt trong cửa sổ vai trò khai báo.
- Test thông báo phát **đúng một lần** khi đội đổi, không mỗi tick.
- Test party UI: mọi slot có item thì có action (không có nút chết) — dùng `MenuLayout` như design guidelines yêu cầu.
- Test đổi thứ tự pet trong party là revision-safe (không mất pet khi hai thao tác đua nhau).
