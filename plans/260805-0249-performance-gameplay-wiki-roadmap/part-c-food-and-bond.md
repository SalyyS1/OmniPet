# Phần C — Thức ăn và độ thân thiết (Phase 6-7)

Nghiên cứu đưa ra một kết luận rõ và nhất quán: **thức ăn phải là phần thưởng, không phải nghĩa vụ**. Mọi hệ thống hunger có trừng phạt đều bị người chơi mô tả là "thanh máu thứ hai" — giữ pet sống thay vì chơi với pet. Plan này cố ý không làm điều đó.

---

## Phase 6 — Thức ăn cho pet

`CultivationItemActionKind` hiện chỉ có `EXPERIENCE_CANDY` và `BREAKTHROUGH_STONE`. Đường redeem đã journaled và idempotent (`CultivationItemActionService`) — **tái dùng nguyên**, không viết đường mới.

### Việc cần làm

1. **Thêm `PET_FOOD`** vào `CultivationItemActionKind`, đi qua đúng journal đã có. Một lần redeem = một transaction bền vững, giống mọi vật phẩm khác.
2. **`foods.yml`** — mỗi món khai báo material, chỉ số tác động, kiểu (FLAT/RELATIVE), độ mạnh, thời hạn. Mượn hình dạng schema của MCPets `petFood.yml`: `ItemId` / `Type` / `Power` / `Operator` / `Duration` — đã được kiểm chứng thực tế trên plugin khác.
3. **Món ưa thích theo định nghĩa** — mỗi `PetDefinition` nêu một material nó thích, cho gấp đôi hiệu quả. **Cực rẻ, và là tín hiệu "pet này có cá tính" mạnh nhất có thể mua được.**
4. **Thức ăn hồi stamina** — `ProgressionConfig` đã có `maxStamina` và `staminaRegenPerSecond` nhưng **chưa gameplay nào tiêu thụ**. Đây là cách kích hoạt field đang chết thay vì thêm tài nguyên mới.
5. **Chế tạo được** — gate thức ăn sau một hoạt động (nông nghiệp, đi săn), không sau một cái shop. Bài học từ Cobblemon: mọi consumable đi qua trồng berry, tức đi qua thứ người chơi vốn đã làm.

### KHÔNG làm (có chủ đích)

- Không hunger gây tụt chỉ số.
- Không suy giảm khi offline.
- Không pet bỏ đi hoặc chết vì không được cho ăn.
- Không "no 99%" khi vừa cho ăn.

Nghiên cứu nhất quán ở điểm này: những thứ trên biến thú cưng thành nghĩa vụ và tạo áp lực đăng nhập, không tạo gắn bó.

### Nếu muốn có thanh "no"

Làm dạng **satiety cộng thêm, không trừ đi**: 0..100 trong `extensions`, chỉ giảm khi pet **đang được triệu hồi**, đáy là 0 và hậu quả duy nhất là mất một bonus nhỏ (ví dụ +10% EXP nhận được). Không bao giờ ảnh hưởng chỉ số gốc.

### Phụ thuộc

Món **tức thời** (EXP, stamina, bond) làm được ngay. Món **có thời hạn** cần Phase 8 (cơ chế buff hết hạn) — đừng làm trước.

### File chính

- `omnipet-core/.../core/progression/CultivationItemActionKind.java`
- `omnipet-core/.../core/progression/CultivationItemActionService.java`
- `omnipet-paper/.../paper/management/PetCultivationItemController.java`
- `omnipet-paper/.../paper/management/PaperPetConsumableInventory.java`
- `omnipet-core/.../core/domain/PetDefinition.java` (món ưa thích)

### Nghiệm thu

- Test một lần redeem = một transaction, idempotent qua restart (mẫu đã có ở journal test hiện tại).
- Test món ưa thích cho đúng gấp đôi và không áp cho pet khác.
- Test stamina không vượt `maxStamina`.

---

## Phase 7 — Độ thân thiết (bond)

Đối trọng với việc sưu tập: thưởng cho việc **dùng một pet lâu**, không chỉ thưởng cho việc có nhiều pet.

### Việc cần làm

1. **`bond` 0..N** trong `PetInstance.extensions` (map mở, không migration). Tăng khi cho ăn, khi lên cấp, và thụ động khi được triệu hồi.
2. **KHÔNG suy giảm.** Quyết định thiết kế có chủ ý, cùng lý do như trên.
3. **Mốc bond mở khoá** cosmetic, và tuỳ chọn: làm điều kiện tiến hoá (tiền lệ Ragnarok — tiến hoá gate sau độ thân thiết, không chỉ sau cấp).
4. **Hiển thị bond** trong menu quản lý — thanh tiến trình bằng ký tự, đồng bộ với Phase 12.

### Ràng buộc

Bond **không** được mua bằng tiền. Bài học Cobblemon: nén friendship xuống vài giây bằng berry bị chính người chơi mô tả là "đánh đổi không khí lấy tiện lợi". Nếu có món tăng bond thì phải là món **kiếm được**, và tăng vừa phải.

### File chính

- `omnipet-core/.../core/domain/PetInstance.java` (đọc/ghi qua `extensions`)
- `omnipet-core/.../core/progression/RepositoryProgressionService.java`
- `omnipet-paper/.../paper/gui/player/PetManagementMenuRenderer.java`

### Nghiệm thu

- Test bond **không bao giờ giảm** trong bất kỳ đường nào.
- Test cho ăn liên tục không vượt trần.
- Test mốc mở khoá là hàm đơn điệu theo bond.
- Test pet cũ (không có field `bond` trong `extensions`) đọc ra bond 0 chứ không lỗi — đây là tính chất tương thích ngược quan trọng nhất của phase này.
