# OmniPet — Hiệu năng, Gameplay, và Wiki

**Trạng thái:** chưa bắt đầu · 15 phase · nền tảng: 828 test / 0 fail

## Bối cảnh

Bảy phase trước đã dọn structure, sửa motion, thêm animation ModelEngine, thống nhất GUI, onboarding, particle, và i18n. Plan này là bước tiếp: **làm plugin chạy nhẹ hơn và chơi sâu hơn**.

Khảo sát tìm ra ba vấn đề hiệu năng **đã kiểm chứng bằng đọc code**, không phải suy đoán:

- **Vòng tick cấp phát rác.** `nextOwners()` (`PaperPetRuntimeCoordinator.java:200-214`) cấp phát 4 collection mỗi tick theo *tổng số chủ*, dù budget chỉ chọn 64. Mỗi pet mỗi tick sinh ~20-25 `RuntimeVector` + ~5 `Location`/`Vector`. **100 pet ≈ 2.500 object/tick.**
- **ModelEngine gửi packet mỗi tick.** `PaperHeadRenderer` đã được gate ở phase trước; `PaperModelEngineRenderer` thì **không** — `setInteractionScale` và `bindings.scale` chạy vô điều kiện. `Interaction` width/height là field data-watcher → **một metadata packet mỗi pet mỗi tick tới mọi người gần đó.**
- **Trứng đặt làm I/O nặng trên main thread mỗi giây.** `PlacedEggView.pass()` gọi `scanAll()` quét toàn thư mục + YAML-parse mọi file; rồi mỗi trứng thêm một `eggs.read()`, 10 lần tra block, và một `save()` với **hai lần fsync**. **100 trứng ≈ 200 lần đọc + 100 ghi fsync kép mỗi giây.**

Hai thứ đã có sẵn mà chưa dùng: `EffectBudget` (`core/runtime/`) có giới hạn per-tick/per-pet/cooldown nhưng **không nơi nào trong production gọi**; và `PaperRuntimeSettings.defaults()` nói 64 pet/chủ trong khi đường config nói 10 — mâu thuẫn cần chốt trước khi định cỡ budget.

Schema sẵn sàng hơn tưởng: `PetInstance.extensions` và `ProgressionState.extensions` là map mở → thêm trạng thái **không cần migration**; `desiredActivePetIds` đã là `List` → nhiều pet đã hỗ trợ ở tầng dữ liệu. Nhưng **chưa có** thức ăn/độ thân thiết, **chưa có** buff hết hạn (`PetStatBuff` không có field duration), **chưa có** đội hình.

Wiki: theme tốt, UX không. Nặng nhất — bấm mục lục **ghi đè URL** nên link chia sẻ sai trang; đổi trang **không cuộn lên đầu**; mục lục **biến mất dưới 1180px**; tìm kiếm quét nội dung nhưng chỉ hiện tên trang; `--signal` (#e67532) tương phản **2.65:1** mà đang tô chữ.

**Quyết định đã chốt:** xen kẽ hiệu năng với tính năng; làm cả 4 cụm gameplay; sửa UX wiki giữ theme; Folia ngoài phạm vi.

## Nguyên tắc

- YAGNI/KISS/DRY — lộ ra và sửa cái đã có trước khi thêm hệ thống mới.
- Mở rộng qua `extensions`, không migration.
- Không phá hợp đồng công khai (`PetRendererPort` có 4 implementor + test double).
- **Mỗi phase hiệu năng phải có test đếm được** (object / packet / lần I/O), không chỉ "chạy được".
- Hiệu ứng chỉ tới người thực hiện — nguyên tắc chống griefing đã có.
- File mới < 200 dòng; không đặt số hiệu phase vào code comment.

## Các phase

| # | Phase | Tài liệu |
|---|---|---|
| 1-3 | Nền hiệu năng: cấp phát tick, packet ModelEngine, I/O trứng đặt | [part-a-performance-foundation.md](part-a-performance-foundation.md) |
| 4-5 | Pet sống động: trứng rung lắc, hành vi nhàn rỗi | [part-b-living-pets.md](part-b-living-pets.md) |
| 6-7 | Thức ăn và độ thân thiết | [part-c-food-and-bond.md](part-c-food-and-bond.md) |
| 8-9 | Buff tạm thời: cơ chế hết hạn, hiển thị | [part-d-temporary-buffs.md](part-d-temporary-buffs.md) |
| 10-11 | Nhiều pet: đội hình, GUI party + synergy | [part-e-party-and-formation.md](part-e-party-and-formation.md) |
| 12 | Đánh bóng GUI | [part-f-gui-polish.md](part-f-gui-polish.md) |
| 13-14 | Wiki: lỗi chặn người đọc, định hướng và tìm kiếm | [part-g-wiki-ux.md](part-g-wiki-ux.md) |
| 15 | Đo và chứng nhận runtime | [part-h-certification.md](part-h-certification.md) |

## Thứ tự và phụ thuộc

```
Phase 1  tick allocations      ─┐
Phase 2  ModelEngine packets   ─┼─ nền hiệu năng, độc lập nhau
Phase 3  placed-egg I/O        ─┘
              ↓
Phase 4  trứng rung lắc         ← CẦN Phase 3 (vòng trứng phải rẻ trước)
Phase 5  hành vi nhàn rỗi       ← CẦN Phase 1 (budget thời gian)
              ↓
Phase 6  thức ăn                ← món tức thời làm ngay; món có thời hạn CẦN Phase 8
Phase 7  độ thân thiết          ← CẦN Phase 6 (cho ăn là nguồn bond chính)
              ↓
Phase 8  buff hết hạn           ← GATE cho Phase 6 (món có thời hạn) và Phase 11
Phase 9  hiển thị buff          ← CẦN Phase 8
              ↓
Phase 10 đội hình               ← CẦN Phase 1
Phase 11 GUI party + synergy    ← CẦN Phase 8 (synergy là buff) + Phase 10
              ↓
Phase 12 đánh bóng GUI          ← CẦN Phase 7/9/11 (có dữ liệu mới để hiển thị)

Phase 13 wiki: lỗi chặn        ─┐ song song được với mọi phase Java
Phase 14 wiki: định hướng      ─┘
              ↓
Phase 15 đo và chứng nhận       ← LÀM CUỐI
```

Phase 13-14 không chung file với bất kỳ phase Java nào → chạy song song bất cứ lúc nào.

## Nghiệm thu chung

1. `gradlew build` — hiện 828 test / 0 fail; **không được** xấu đi.
2. Mỗi phase: test hẹp trước, rồi mở rộng.
3. Mỗi phase hiệu năng phải có test đếm được, không chỉ test xanh.
4. Phase 15 là cổng cho mọi tuyên bố về hiệu năng. `docs/roadmap.md` nói rõ **chưa từng có smoke test trên server thật** — không đánh dấu shipped trước khi có số đo.

## Rủi ro chính

| Rủi ro | Giảm thiểu |
|---|---|
| `PaperHeadRendererSourceContractTest` khoá nội dung source HEAD renderer | Phase 2 không đổi loại entity, không bỏ `setVelocity` |
| Đổi độ bền trứng phá điều `PlacedEggDurabilityTest` cố ý bảo vệ | Phase 3: deadline tuyệt đối + ảnh vật phẩm trên đĩa từ lúc đặt; chỉ đếm ngược là mất được |
| Buff bị lạm dụng bằng cách xoay vault | Phase 8: buff không sống qua release/vault-out |
| Buff dự bị phẳng khoá cứng đội hình | Phase 11: chỉ làm khi có điều kiện |
| Đổi `--signal` ảnh hưởng cả `index.html` | Phase 13: xác nhận trước khi đổi màu thương hiệu |
| **Folia** | **Ngoài phạm vi.** Refactor lớn của coordinator, không phải phần thêm vào |

## Câu hỏi mở

1. **`--signal` (#e67532) có được đổi không?** Chỉ đạt 2.65:1 nhưng đang tô chữ. Đổi ảnh hưởng cả landing page. Nếu cố định thì `.eyebrow` cần cách thể hiện khác.
2. **Wiki hay `docs/*.md` là nguồn sự thật?** Không chốt thì Phase 14 không sửa được trôi lệch. Tôi nghiêng về: wiki sinh từ markdown lúc build.
3. **Có mở rộng sang trang bị cho pet không?** Giá trị cao nhưng rủi ro nhân bản vật phẩm — phải tái dùng codec escrow/identity. Hiện chưa đưa vào 15 phase.
4. **Trần số pet hoạt động thực tế?** `defaults()` nói 64, config nói 10. Định cỡ Phase 1 và Phase 10.
5. **Server thiên combat hay cosmetic?** Synergy (Phase 11) và buff (Phase 8) giá trị rất khác giữa hai hướng.
6. **Boss bar mặc định bật hay tắt?** Tôi đề xuất tắt. Legacy `lang.yml` từng có `hud.hatchingBossbar` nên đây từng là thiết kế gốc.
