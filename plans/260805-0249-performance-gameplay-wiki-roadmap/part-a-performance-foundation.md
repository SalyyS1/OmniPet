# Phần A — Nền hiệu năng (Phase 1-3)

Làm trước vì mọi tính năng sau đều chạy trên vòng tick này. Thêm tính năng lên một hệ thống đang rò rỉ sẽ nhân chi phí lên.

---

## Phase 1 — Vòng tick: bỏ cấp phát rác

**Vấn đề đã kiểm chứng.** `nextOwners()` (`PaperPetRuntimeCoordinator.java:200-214`) dựng lại danh sách chủ từ đầu mỗi tick: một `LinkedHashSet` + `List.copyOf` trong `allKnownOwnersLocked()`, rồi một `ArrayList` + một `List.copyOf` nữa — **4 collection mỗi tick, kích thước theo tổng số chủ**, dù budget chỉ chọn 64. `PetActivationService.reconcile` cấp phát thêm ~8 collection mỗi chủ mỗi tick, gồm `List.copyOf(desired)` ở dòng 153 copy lại đúng list mà caller vừa tạo riêng.

### Việc cần làm

1. **Cache danh sách chủ.** Giữ list đã dựng, chỉ dựng lại khi `snapshots` hoặc `cleanupOwners` thay đổi (dirty flag). Bỏ 4 collection/tick tỉ lệ với số người chơi.
2. **Thêm budget theo thời gian.** Hiện chỉ có budget **đếm** (`maximumOwnersPerTick`). Thêm ngưỡng nanosecond: hết thời gian thì `break` vòng chủ — con trỏ round-robin đã đảm bảo tick sau tiếp đúng chỗ. Đây là thứ bảo vệ TPS thật; budget đếm chỉ giới hạn thông lượng.
3. **Tái dùng buffer.** `PaperRuntimeOwnerEngine` cấp phát `ArrayList` + `LinkedHashSet` mỗi chủ mỗi tick → thành field, clear mỗi lượt.
4. **Bỏ copy thừa** ở `PetActivationService:153`.
5. **Chốt mâu thuẫn 64 vs 10** pet/chủ giữa `PaperRuntimeSettings.defaults()` và `OmniPetConfigLoader:182`.

### Cân nhắc (chưa quyết)

`MovementController.step` sinh ~12-18 `RuntimeVector` mỗi lần gọi vì mọi phép `add`/`subtract`/`multiply`/`clampLength` đều trả record mới, và constructor `RuntimeVector` chạy 3 lần `Double.isFinite`. Có thể gộp bằng phép `addScaled` hoặc scratch mutable — **nhưng** `RuntimeVector` bất biến là lý do `MovementController` test được thuần khiết. Chỉ làm nếu Phase 15 đo ra đây là điểm nóng thật; đừng đánh đổi tính test được cho một suy đoán.

### File chính

- `omnipet-paper/.../paper/runtime/PaperPetRuntimeCoordinator.java`
- `omnipet-paper/.../paper/runtime/PaperRuntimeOwnerEngine.java`
- `omnipet-paper/.../paper/runtime/PaperRuntimeSettings.java`
- `omnipet-core/.../core/runtime/PetActivationService.java`

### Nghiệm thu

- Test đếm số lần dựng collection cho 100 pet, trước/sau.
- Test chứng minh budget thời gian dừng vòng lặp và tick sau tiếp **đúng chủ kế tiếp**, không bỏ sót chủ nào.
- `RuntimeFleetBudgetEvidenceTest` không xấu đi. Lưu ý test này lái một class `CentralRuntimePass` định nghĩa trong chính file test, **không** chạy qua coordinator thật — nó chứng minh *hình dạng* (một task, không task per pet), không đo vòng lặp thật.

---

## Phase 2 — ModelEngine: chặn packet mỗi tick

**Vấn đề đã kiểm chứng.** Phase trước đã gate `PaperHeadRenderer` bằng `PaperHeadRendererHandle.displayTransformChanged`. `PaperModelEngineRenderer` **không có** lớp bảo vệ đó: `setInteractionScale` chạy vô điều kiện mỗi tick, và `bindings.scale` là một **lệnh reflective** cũng chạy mỗi tick. `Interaction` width/height là field data-watcher → một metadata packet mỗi pet mỗi tick tới mọi người chơi gần đó. (`driveAnimation` thì đã gate đúng.)

### Việc cần làm

1. **Gate `setInteractionScale` và `bindings.scale`** theo thay đổi thật, theo đúng mẫu `displayTransformChanged` đã có. Thắng lợi packet rõ nhất còn lại.
2. **Bỏ qua `smoothMove` khi pet đứng yên** — cả hai renderer gọi vô điều kiện. Khi `velocity.lengthSquared()` ~0 và yaw không đổi thì bỏ `setVelocity`/`setRotation`. Pet đứng yên là trường hợp phổ biến nhất.
3. **Gộp 3 lần cấp phát `Location`** trong `BukkitPaperHeadRendererBackend` thành một target dùng lại cho `targetChunkLoaded` / `distanceSquared` / `smoothMove`.
4. **Cache `worldId`** trên handle — chỉ đổi khi chủ đổi world, mà việc đó đã buộc cleanup (`PaperPetRuntimeCoordinator.ownerWorldChanged`).

### Rủi ro

`PaperHeadRendererSourceContractTest` khoá nội dung source: **bắt buộc** có `ArmorStand.class`/`ItemDisplay.class`/`Interaction.class`/`setVelocity`/`hardTeleport`, và **cấm** `ModelEngine`/`io.lumine`/`MMOItems`. Không đổi loại entity carrier, không bỏ `setVelocity`.

Giữ nguyên cơ chế velocity-based. Chuyển sang `teleport` + `teleportDuration` sẽ vô hiệu hoá ngữ nghĩa khoảng cách của `PaperHeadMovementPolicy` và các setting gain/max-velocity.

### File chính

- `omnipet-paper/.../paper/render/PaperModelEngineRenderer.java`
- `omnipet-paper/.../paper/render/ModelEngineRendererHandle.java`
- `omnipet-paper/.../paper/render/BukkitPaperHeadRendererBackend.java`

### Nghiệm thu

Test dùng backend giả đếm số lần ghi entity property qua 3 tick không đổi → phải bằng 0. Mẫu đã có: `PaperHeadRendererTest.anUnchangedDisplayTransformIsNotResentEveryTick`.

---

## Phase 3 — Trứng đặt: bỏ I/O main thread

**Phase có lợi nhất trên mỗi giờ công.**

**Vấn đề đã kiểm chứng.** `PlacedEggView.pass()` chạy 20 tick/lần. Mỗi pass:

1. `coordinator.all()` → `store.scanAll()` — **quét toàn bộ thư mục `data/placed-eggs/` và YAML-parse mọi file**, mỗi lần đọc còn kèm `Files.exists` + `isRegularFile` + `size` + `readString`.
2. Mỗi trứng: `coordinator.tick()` → **một `eggs.read()` nữa** (không cache) + `surroundingBlocks()` dựng stream với 10 lần `getRelative` và 10 chuỗi.
3. Mỗi trứng: `store.save()` → `AtomicFileStore.write` với **hai lần fsync**, một bản copy backup, ba temp file.
4. `holograms.show()` ghi lại text mỗi giây → một metadata packet mỗi trứng mỗi giây mỗi người xem.

**100 trứng ≈ 200 lần đọc đĩa + 100 lần ghi fsync kép mỗi giây, tất cả trên tick thread.**

### Việc cần làm

1. **Giữ record trong bộ nhớ**, quét đĩa **một lần** lúc `start()`. Store đã là nơi ghi duy nhất nên `scanAll()` mỗi giây là thừa hoàn toàn.
2. **Cache định nghĩa trứng** — config tĩnh, invalidate khi reload.
3. **Lưu deadline tuyệt đối, flush thưa** (30s / khi ready / khi chunk unload / khi disable) thay vì fsync mỗi giây mỗi trứng.
4. **Kiểm tra điều kiện nhiệt thưa hơn** (5s) — 10 lần tra block × N trứng × 1Hz cho một điều kiện gần như không đổi là quá đắt. Thay stream bằng vòng lặp trên mảng dùng lại.
5. **Chỉ cập nhật hologram khi có người chơi trong tầm nhìn.**

### Rủi ro cần tôn trọng

`PlacedEggStore` và `PlacedEggDurabilityTest` **cố ý** bảo vệ tính bền vững — comment của class nói rõ "mất một file là mất một vật phẩm". Cách giữ nguyên tính chất đó:

- Ảnh vật phẩm vẫn ghi xuống đĩa **ngay lúc đặt**, không đổi.
- Lưu **deadline tuyệt đối** thay vì số đếm giảm dần → sự cố mất tối đa một khoảng flush, và không có gì để tính sai.
- Chỉ **phần đếm ngược** trở thành mất mát được, không phải quyền sở hữu vật phẩm.

`PlacedEggView`/`PlacedEggHolograms` hiện **không có** thread guard (comment nói "phải chạy main thread" nhưng không có gì cưỡng chế). Cân nhắc thêm guard như các lớp khác.

### File chính

- `omnipet-paper/.../paper/incubation/placed/PlacedEggView.java`
- `omnipet-paper/.../paper/incubation/placed/PlacedEggCoordinator.java`
- `omnipet-paper/.../paper/incubation/placed/PlacedEggStore.java`
- `omnipet-paper/.../paper/incubation/placed/PlacedEggHolograms.java`

### Nghiệm thu

- Test đếm số lần đọc/ghi store qua 10 pass với 50 trứng: đọc = 0 sau lần đầu, ghi thưa hơn số pass.
- `PlacedEggDurabilityTest` và `PlacedEggStoreTest` vẫn xanh — nếu phải sửa, sửa có chủ đích và nói rõ tính chất nào đổi.
