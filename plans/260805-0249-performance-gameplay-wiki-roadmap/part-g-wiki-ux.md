# Phần G — Wiki UX (Phase 13-14)

Theme giữ nguyên — nó tốt. Vấn đề nằm ở cơ chế đọc. Khảo sát tìm ra 29 vấn đề cụ thể; dưới đây là những cái thật sự chặn người đọc.

Phase 13-14 **không chung file** với bất kỳ phase Java nào → chạy song song bất cứ lúc nào.

---

## Phase 13 — Sửa lỗi chặn người đọc

Sáu lỗi này phá vòng đọc cơ bản. Sửa trước mọi thứ khác.

### 1. Bấm mục lục làm hỏng URL (`wiki.js:258`)

Mục lục sinh `href="#heading-id"`, nhưng routing dùng `#/page-id` (`wiki.js:56`). Bấm mục lục **ghi đè hash**, nên URL không còn tên trang. Reload hoặc chia sẻ link đó → `readPageFromHash()` trả null → người đọc rơi về trang `install`, không phải mục họ được gửi.

**Sửa:** chặn click, `scrollIntoView`, rồi `history.replaceState('#/' + pageId)` để URL vẫn nêu trang.

**Lưu ý:** đổi format hash sẽ phá link `#/page` đã chia sẻ nếu không nhận cả hai dạng — giữ tương thích.

### 2. Đổi trang không cuộn lên đầu (`wiki.js:263`)

Code đặt `dom.article.scrollTop = 0`, nhưng `.wiki-article` **không phải scroll container** (không có `overflow`/`height` ở `wiki.css:196-203`) — window mới là. Dòng đó là no-op. Đọc hết trang `commands` rồi bấm `config` sẽ rơi vào giữa bài mới.

**Sửa:** `window.scrollTo(0, 0)` và focus `#wiki-article`.

### 3. Re-render mất focus bàn phím (`wiki.js:234`)

`renderNav()` thay `innerHTML` của nav mỗi lần render, kể cả lần render do bấm nav. `<a>` đang focus bị xoá → focus về đầu document. Người dùng bàn phím phải tab lại qua header, search, language switch, và cả nav **mỗi lần đổi trang**.

**Sửa:** không dựng lại nav khi đổi trang — chỉ toggle `active`/`aria-current`; và chuyển focus sang `#wiki-article`.

### 4. Mục lục biến mất trên mobile (`wiki.css:377-381`)

`.wiki-aside { display: none }` dưới 1180px. Tức **mọi điện thoại và tablet mất hẳn công cụ skim** — đúng nơi cần nhất, và trái ngược comment ở `wiki.css:5` nói docs "thường được đọc trên điện thoại". Trang `config` (5 mục) và `commands` (4 mục, bảng dài) thành bức tường chữ.

**Sửa:** gập mục lục thành `<details>` "Trong trang này" phía trên thân bài, thay vì ẩn.

### 5. Dòng quá dài

Thân bài dùng monospace toàn bộ (`styles.css:24`, IBM Plex Mono 16px/1.65). Tính ra: 1400 − 208 − 224 − 64 − 64 − 77 ≈ **763px ≈ 79 ký tự**. Ở breakpoint 1180px, aside bị bỏ và bài **rộng ra** thành ~799px ≈ **83 ký tự**. Mục tiêu là 45-75. Monospace còn giảm ~15% tốc độ đọc văn xuôi.

**Sửa:** `max-width: 68ch` cho thân bài; dùng font proportional cho văn xuôi, giữ monospace cho `code`/`pre`.

### 6. Tương phản dưới chuẩn

Tính trên nền `--paper #f3f0e7`:

| Token | Hex | Tỉ lệ | Kết luận |
|---|---|---|---|
| `--ink` | `#102a2b` | 13.28 | đạt |
| `--ink-soft` | `#2d4a49` | 8.43 | đạt |
| `--signal` | `#e67532` | **2.65** | **không đạt 4.5:1 lẫn 3:1** |
| `--line` | rgba(16,42,43,.22) | **1.55** | **không đạt 3:1 cho viền** |

`--signal` **không** phải trang trí: nó tô `.eyebrow` (`wiki.css:213`) — dấu hiệu duy nhất cho biết "đang ở mục nào" — cộng `.text-link` và `nav a:hover`. `--line` ở 1.55 làm ô search, viền nav, và đường kẻ bảng gần như vô hình.

**Sửa:** đậm signal về ~`#a84a12` (~5.4:1) cho chữ; nâng alpha của `--line` lên ~0.42.

**Đã chốt:** được đổi, và áp cả `index.html` để landing page với wiki cùng một tông.

---

## Phase 14 — Định hướng và tìm kiếm

### 1. Tìm kiếm có trích đoạn (`wiki.js:204-240`)

`matchesQuery` **đã** quét cả nội dung, nhưng UI chỉ render tên trang. Tìm "cursor" → một tên trang, không biết từ đó nằm đâu trong bài 3.4k ký tự, và pane bài vẫn hiện trang cũ không khớp. Không điều hướng bàn phím, không Enter-mở-kết-quả-đầu, không đếm kết quả, không `aria-live`.

**Sửa:** hiện heading khớp + trích ±60 ký tự có tô từ khoá; ↑/↓/Enter; đếm "N kết quả" qua live region.

### 2. Tìm kiếm cả hai ngôn ngữ (`wiki.js:62`)

`text()` chỉ resolve ngôn ngữ **đang chọn**. Người đọc EN tìm từ tiếng Việt (hoặc ngược lại) ra rỗng, và không có gì gợi ý rằng ngôn ngữ kia có nội dung.

**Sửa:** dựng haystack từ cả `en` và `vi`.

### 3. Không có trang định hướng

`state.pageId` mặc định là `wiki.pages[0].id` = `install` (`wiki.js:21`) — wiki mở thẳng vào một trang tác vụ, không có bản đồ. Và **không trang nào link sang trang khác** (0 link `#/` trong `wiki-content.js`), nên chỉ di chuyển được bằng rail.

**Sửa:** thêm trang `overview` với link theo nhóm; thêm link "xem thêm" trong thân bài.

### 4. Rail mất nhãn nhóm (`wiki.js:220-238`)

`renderNav` sắp theo `['start','player','operator','reference']` nhưng render **một `<ul>` phẳng**. Comment lập luận rằng thứ tự tự nói lên nhóm — nó không. Sáu mục không phân cách đọc ra như sáu mục ngang hàng. Tên nhóm chỉ xuất hiện **sau khi** đã chọn trang, dưới dạng `.eyebrow`.

**Sửa:** trả lại `<h2>` nhãn nhóm trong rail — chuỗi đã có sẵn ở `wiki-content.js:28-33,43-48`.

Cùng lúc: summary hiện nằm trong `title=` tooltip (`wiki.js:238`) — tooltip không hiện trên touch, chậm trên desktop, và nhiều screen reader không đọc. Dòng duy nhất nói cho người đọc biết trang có đúng thứ họ cần đang gần như không tới được. **Sửa:** render summary thành dòng thứ hai trong rail, hoặc bỏ.

### 5. Nút trang kế

`.article-foot` (`wiki.js:254`) in một dòng provenance tĩnh. Đọc xong `install` phải quay lại rail và tự đoán. **Sửa:** pager prev/next theo thứ tự nhóm đã có.

### 6. Scroll-spy cho mục lục

`.wiki-toc a` chỉ có `:hover` (`wiki.css:345-353`); không gì theo vị trí cuộn. Trên trang dài, mục lục không cho biết đang ở đâu. **Sửa:** `IntersectionObserver` trên `h2[id]` → `aria-current="location"`.

### 7. Nút copy cho code block (`wiki.js:121`)

Emit `<pre><code>` trơn. **Mọi code block trên site này là một lệnh người đọc phải gõ lại tay** — với một plugin nặng lệnh, đây là affordance giá trị nhất còn thiếu.

### 8. Bảng cuộn được bằng bàn phím (`wiki.js:197`, `wiki.css:279`)

`.table-scroll` có `overflow-x:auto` nhưng **không `tabindex="0"`, không `role`/`aria-label`** — trong khi `index.html:77` làm đúng. Người dùng bàn phím không cuộn ngang được bảng ở `install` và `commands`. `th { white-space: nowrap }` đảm bảo bảng luôn overflow trên điện thoại.

### 9. Phụ trợ

- **Banner 115KB eager trên mobile** (`wiki.html:47-50`, `wiki.css:111`): chiếm ~20% viewport điện thoại phía trên nav và `h1`. Comment ở `wiki.html:44` khai là trang trí nhưng `alt` lại mô tả → screen reader đọc "quả trứng rồng" trước nội dung. Sửa `alt=""`, và thu nhỏ hoặc bỏ dưới 820px.
- **Header sticky wrap hai dòng trên mobile** (`wiki.css:423`): search chiếm cả một dòng nên header sticky cao gấp đôi, ăn viewport vĩnh viễn.
- **`aria-current` thiếu** trên nav item đang chọn (`wiki.js:235` chỉ đặt `class="active"`).
- **Đổi trang im lặng với screen reader** — không focus move, không live region.
- **Panel build không tới được trên mobile** (nằm trong `.wiki-aside` bị ẩn), trong khi `index.html:48` lại chỉ người đọc sang wiki để xem đúng thứ đó.
- **`languageLabel` là dead string** — `wiki-content.js:27,42` định nghĩa nhưng `wiki.js` không đọc; nhãn `role="group"` ở `wiki.html:34` vẫn tiếng Anh khi ở chế độ VI.
- **`aria-pressed` hardcode EN=true** (`wiki.html:35-36`) — preference VI đã lưu vẫn bị DOM khẳng định English trong khoảnh khắc đầu.

---

## Chốt một nguồn sự thật

Wiki có **6 trang**; `docs/` có **11 file markdown**; và landing page (`index.html:95-103`) trỏ mọi thẻ "Manuals" ra **GitHub raw**, tức dẫn người đọc *ra khỏi* wiki.

Đã có trôi lệch thật:
- `index.html:38,96` nói "schema-v3 player state" trong khi `configuration.md:23` nói "schema 4".
- `wiki-content.js:86,117` nói `/pet admin browse` còn `:518` nói `/petadmin browse`.

**Đã chốt: wiki là nguồn sự thật.** Việc phải làm ở phase này:

1. **Landing page trỏ vào wiki**, không ra GitHub raw. Người đọc theo thẻ "Manuals" phải đến `wiki.html#/…`.
2. **Sửa trôi lệch** — chốt schema 4 và một dạng lệnh duy nhất, rồi sửa mọi nơi nói khác.
3. **Sáu manual chưa có trang wiki** (migration, integrations, compatibility, developer-guide, examples, roadmap): hoặc thêm trang, hoặc wiki phải nói rõ nó chỉ phủ một phần và trỏ tiếp. Im lặng là lựa chọn tệ nhất — người đọc không biết mình đang thiếu gì.
4. **`docs/*.md` ở lại** cho người đọc trong repo, nhưng khi nội dung khác nhau thì **wiki thắng**, và đó là thứ phải ghi rõ ở đầu mỗi file markdown liên quan.

---

## Ràng buộc

Site phải chạy được từ `file://` (`wiki.js:5`). Không được thêm `fetch`, ES module, hay bước build cho phần render lõi. `loadStats()` (`wiki.js:317`) đã im lặng thất bại trên `file://` và để lại dấu gạch ngang từ `wiki.html:60`.

`renderMarkdown` là hand-rolled và **chỉ xử lý `## `** (`wiki.js:125`); `### ` bị nuốt thành đoạn văn. Hiện chưa có `### ` nào được viết nên đây là hạn chế tiềm ẩn — nếu thêm `###` (để mục lục có tầng) thì không được phá danh sách terminator ở `wiki.js:170-176`.

## Nghiệm thu

- Deep-link tới một heading rồi reload → về đúng trang và đúng mục.
- Đổi trang → cuộn lên đầu, focus vào bài, screen reader thông báo.
- Ở 375px: mục lục tới được, search tới được, dòng ≤ 75 ký tự.
- Tìm "cursor" → hiện trích đoạn có ngữ cảnh, điều hướng được bằng bàn phím.
- Kiểm tra tương phản: mọi màu tô chữ ≥ 4.5:1, viền ≥ 3:1.
- Bảng cuộn được chỉ bằng bàn phím.
