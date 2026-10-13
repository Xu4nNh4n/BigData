---
name: technical-tutorial-creator
description: >-
  Chuẩn mực và hướng dẫn chuyên biệt để tạo ra các tài liệu kỹ thuật, giáo trình Backend (Tutorial/Guide)
  dễ đọc, dễ nhớ, trực quan, không nhàm chán và độ dài vừa vặn (Goldilocks: 200-300 dòng).
---

# 🎓 CHUẨN MỰC THIẾT KẾ GIÁO TRÌNH & TÀI LIỆU KỸ THUẬT (BITE-SIZED TUTORIAL CREATOR)

Tài liệu này định nghĩa bộ quy chuẩn để mọi hướng dẫn, tài liệu kỹ thuật viết trong dự án đều đạt tiêu chuẩn cao nhất về khả năng tiếp thu của người đọc: **Không chán, không ngấy, không học vẹt, nhớ lâu và dễ áp dụng.**

---

## 🎯 5 NGUYÊN TẮC VÀNG (THE GOLDEN RULES)

### 1. Quy tắc Độ dài Vừa Vặn (The Goldilocks Zone: 200 - 300 dòng)
* ❌ **Không viết quá dài (> 500 dòng)**: Gây mỏi mắt, quá tải nhận thức (Cognitive Overload), người đọc lướt qua mà không đọng lại gì.
* ❌ **Không viết quá ngắn (< 80 dòng)**: Mất bối cảnh, thiếu chiều sâu để người đọc tư duy và hiểu nguyên nhân cốt lõi.
* ✅ **Chuẩn mực**: Mỗi file dài từ **200 đến 300 dòng**, đọc hết trong **10 - 15 phút**.

### 2. Sơ đồ Trực quan bằng ASCII Box Text (`│`, `─`, `▼`, `[ ]`)
* Tránh dùng `mermaid` nếu có thể để người đọc không cần cài extension hoặc chuyển tab preview.
* Trình bày trực tiếp dạng text box để đọc được ngay trên mọi IDE và terminal:
  ```text
  [ Client (Frontend) ] ──(Gửi Request)──► [ Controller ] ──(Xử lý)──► [ Service ]
  ```

### 3. Công thức "Bối cảnh - Thảm họa - Cứu cánh" (Problem - Disaster - Solution)
Trước khi giới thiệu bất kỳ công nghệ hay cú pháp nào (`@Transactional`, `traceId`, `JWT`, `DTO`):
1. **Bối cảnh**: Tình huống thực tế gặp phải.
2. **Thảm họa nếu không dùng**: Hậu quả nghiêm trọng gì sẽ xảy ra? (Mất 20 triệu tiền hàng, lộ mật khẩu, sập server).
3. **Cứu cánh**: Công nghệ này giải quyết triệt để vấn đề như thế nào?

### 4. Dùng Ẩn dụ Thực tế (Real-world Metaphors)
* Controller/Service/Repo = *Tiếp tân / Bếp trưởng / Thủ kho*.
* JWT Token = *Vé xem phim (Mua 1 lần, giơ vé vào rạp)*.
* 401 vs 403 = *Chưa có vé vs Có vé thường nhưng đòi vào phòng VIP*.

### 5. Kết bài bằng "Bộ Câu Hỏi Tự Kiểm Tra Tư Duy" (Self-Check Challenge)
* Cuối mỗi tài liệu luôn có 3 - 5 câu hỏi tình huống thực tế kèm gợi ý trả lời để người đọc tự dừng lại 2 phút suy ngẫm và củng cố kiến thức.

---

## 📐 CẤU TRÚC MẪU 1 FILE TÀI LIỆU CHUẨN

```markdown
# 📘 [TÊN CHỦ ĐỀ: TỪ BẢN CHẤT ĐẾN THỰC THI]

> 🎯 **Mục tiêu**: [1 câu nêu rõ người đọc sẽ làm chủ được gì]
> ⏱️ **Thời gian đọc**: ~10 - 15 phút.

---

## 🗺️ 1. BỨC TRANH TỔNG THỂ & SƠ ĐỒ DÒNG CHẢY
[Sơ đồ ASCII text box]

## 💥 2. BÀI TOÁN THỰC TẾ & THẢM HỌA NẾU LÀM SAI
[Tình huống cụ thể + Hậu quả]

## ⚙️ 3. GIẢI PHÁP & CÁCH ÁP DỤNG TRONG CODE DỰ ÁN
[Bảng so sánh / Code ngắn gọn / Chỉ rõ file trong dự án]

## 🚨 4. CÁC LỖI KINH ĐIỂN & CÁCH PHÒNG TRÁNH (NULL-SAFETY, TYPO)
[Mẹo nhận biết và sửa nhanh]

## 🧠 5. BỘ CÂU HỎI TỰ KIỂM TRA TƯ DUY (SELF-CHECK)
[3-5 câu hỏi phỏng vấn / tình huống thực tế]
```
