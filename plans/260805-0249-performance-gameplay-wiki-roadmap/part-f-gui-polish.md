# Phần F — Đánh bóng GUI (Phase 12)

Phase 4 của plan trước đã thống nhất *hạ tầng* GUI (một `MenuLayout`, một `MenuPage`, một `MenuButtonStyle`, colour roles, design guidelines). Phase này là phần **thẩm mỹ** đặt lên nền đó.

Xếp sau Phase 7/9/11 có lý do: lúc đó mới có dữ liệu mới để hiển thị (bond, buff đang chạy, đội hình). Làm trước thì sẽ phải sửa lại giao diện hai lần.

---

## Phase 12 — Đánh bóng GUI

### Việc cần làm

1. **Viền/khung menu.** `MenuStyle` đã cho operator chọn filler; dùng nó để dựng khung thay vì một mặt phẳng kính xám. Menu hiện đọc ra như một cái grid, không như một màn hình được thiết kế.
2. **Thanh tiến trình bằng ký tự** cho EXP / bond / stamina trong lore. Một con số `1250/2000` bắt người đọc tự tính; một thanh thì không.
3. **Icon theo trạng thái rõ ràng hơn.** Pet đang hoạt động / đã khoá / yêu thích phải phân biệt được **ngay từ material**, không phải đọc lore. Hiện phải đọc.
4. **Đầu pet có texture** trong danh sách party và vault. `StudioHeadItems` đã làm đúng việc này cho Studio — tái dùng, đừng viết lại.
5. **Trang tổng quan một pet.** Một màn hình cho một pet: chỉ số, bond, buff đang chạy, kỹ năng. Hiện thông tin pet rải rác giữa vault lore và menu quản lý.

### Bám design guidelines

`docs/design-guidelines.md` (viết ở phase trước) là hợp đồng của phase này:

- Màu là **vai trò** (`GuiColors`), không phải màu thô. Phase trước đã thêm `DESTRUCTIVE`/`DISABLED` để tách "không bấm được" khỏi "không hoàn tác được".
- Lore theo thứ tự: giá trị → `Component.empty()` → dòng hint cuối.
- Tiêu đề theo grammar `OmniPet ▸ <Màn hình>`, resolve từ `MessageKey`.
- Mọi button đi qua `MenuLayout.put`/`putStack`/`bind` — hàm này ghi action và item **cùng một lệnh**, đó là thứ làm nút chết không thể xảy ra.
- Phân trang grid cố định dùng `MenuPage`; màn hình cursor-paged **không** dùng (cursor opaque không có số trang).

### Cạm bẫy

- **Text mới phải vào `MessageKey`** rồi dịch sang `vi.yml` — Phase 7 của plan trước đã có test khẳng định pack tiếng Việt phủ **mọi** khoá và **giữ mọi placeholder**. Thêm khoá mà không dịch sẽ làm test đỏ. Đây là hành vi đúng, không phải trở ngại.
- **Không dùng `Displays.words(enum)`** trong text người chơi thấy — sinh chuỗi từ tên constant, không dịch được. Phase 5 của plan trước đã dọn việc này một lần.
- Menu mới phải vào `MenuStyle.knownMenus()` **và** vào `config.yml`. Test `everyDocumentedButtonNameIsAcceptedByItsMenu` canh một hướng; hướng còn lại là kỷ luật.

### File chính

- `omnipet-paper/.../paper/gui/GuiItems.java`
- `omnipet-paper/.../paper/gui/MenuLayout.java`
- `omnipet-paper/.../paper/gui/player/PlayerPetMenuRenderer.java`
- `omnipet-paper/.../paper/gui/player/PetManagementMenuRenderer.java`
- `omnipet-paper/.../paper/text/MessageKey.java` + `omnipet-paper/src/main/resources/lang/vi.yml`
- `omnipet-paper/.../paper/config/MenuStyle.java` + `config.yml`

### Nghiệm thu

- Test mọi slot có item thì có action, trên mọi màn hình mới.
- Test `vi.yml` vẫn phủ 100% khoá (test đã có sẽ tự bắt).
- Test thanh tiến trình xử lý đúng biên: 0%, 100%, và giá trị vượt trần.
- Không chuỗi tiếng Anh hardcode nào trong renderer mới.
