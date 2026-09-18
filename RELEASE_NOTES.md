# 📢 AllBus v1.0 (Build 1) - Official Release Notes

**Ngày phát hành:** 18/09/2026  
**Phiên bản:** `1.0` (Build `1`)  
**Package:** `com.example.hanoibus`  
**Hệ điều hành hỗ trợ:** Android 8.0 (API 26) trở lên  

---

## 🌟 Tổng quan phiên bản 1.0
**AllBus v1.0** đánh dấu cột mốc phát hành chính thức đầu tiên của ứng dụng tra cứu xe buýt và lộ trình thông minh tại Hà Nội. Phiên bản này được xây dựng trên nền tảng công nghệ hiện đại (Jetpack Compose & Material 3), mang đến trải nghiệm nhanh chóng, trực quan, mượt mà và tích hợp sâu các tiện ích giao thông thực tế.

---

## 🚀 Các tính năng chính (Key Features)

### 1. 🚍 Tra cứu mạng lưới tuyến xe buýt toàn diện
- Dữ liệu đầy đủ hệ thống xe buýt Hà Nội: Xe buýt nội đô, xe buýt ngoại thành, xe buýt nhanh BRT và các tuyến buýt điện (VinBus).
- Cung cấp đầy đủ thông tin: Lộ trình chiều đi (Outbound), lộ trình chiều về (Inbound), thời gian hoạt động trong ngày, giãn cách chuyến và bảng giá vé.
- Tra cứu danh sách toàn bộ trạm dừng trên từng tuyến một cách trực quan.

### 2. 🗺️ Bản đồ tương tác & Hơn 5.000 trạm dừng
- Hiển thị đầy đủ mạng lưới 5.000+ trạm dừng xe buýt trên nền bản đồ OpenStreetMap (OSMDroid) hoạt động mượt mà.
- **Tăng độ zoom khởi tạo (+1.3 levels)**: Giúp người dùng nhìn rõ ngay tên đường, góc phố và vị trí trạm xung quanh mà không cần phải thao tác phóng to thủ công.
- Tự động nhận diện và làm nổi bật các trạm dừng gần vị trí GPS hiện tại của người dùng.

### 3. ⏱️ Theo dõi xe buýt sắp tới điểm theo thời gian thực (Live Tracking)
- Dự báo thời gian thực và khoảng cách xe buýt đang di chuyển đến trạm đón khách.
- **Tối ưu trải nghiệm theo dõi**: Khi bấm chọn một xe buýt ngẫu nhiên tại trạm:
  - Bản đồ vẽ lộ trình di chuyển thực tế của chiếc xe đó.
  - Khung danh sách xe tự động thu gọn xuống dưới cùng kèm nút mũi tên (chevron) để người dùng có thể linh hoạt đóng/mở, loại bỏ hoàn toàn việc bảng thông tin che lấp bản đồ.

### 4. 🧭 Tìm đường thông minh (Multi-modal Trip Planner)
- Thuật toán định tuyến thông minh kết hợp linh hoạt giữa việc đi bộ và đón các tuyến xe buýt phù hợp.
- Hỗ trợ tìm đường từ vị trí hiện tại đến bất kỳ trạm dừng hoặc địa điểm nào tại Hà Nội.
- Phân tách chỉ dẫn chi tiết từng chặng: đoạn đi bộ, điểm đón, tuyến cần đi, điểm trung chuyển và điểm xuống.

### 5. 🧭 La bàn số & Định vị góc nhìn (Compass Mode)
- Tích hợp cảm biến la bàn xoay bản đồ theo thời gian thực tương ứng với hướng nhìn của người dùng ngoài đời thực.
- Nút bấm nhanh để trả bản đồ về hướng chính Bắc hoặc tái định tâm về vị trí hiện tại.

### 6. 🎫 Tích hợp thẻ vé điện tử Giao thông Hà Nội (Virtual Ticket QR)
- Hỗ trợ liên kết và mở nhanh màn hình quét vé QR từ ứng dụng "Thẻ vé giao thông HN".
- Cung cấp nút phím tắt nổi (Floating Action Button) và Tab Thẻ vé chuyên biệt.
- Hỗ trợ công tắc bật/tắt hiển thị nút quét vé linh hoạt trong phần Cài đặt và tự động lưu trạng thái lựa chọn.

### 7. 🎨 Nhận diện thương hiệu mới (App Branding & Icons)
- Bộ icon launcher chính thức với thiết kế hiện đại, biểu tượng xe buýt AllBus nổi bật trên nền xanh nhận diện.
- Hỗ trợ đầy đủ Adaptive Icons (Android 8.0+), Legacy Icons và Round Icons trên mọi độ phân giải màn hình (`mdpi` đến `xxxhdpi`).

---

## 🛠️ Cải tiến & Sửa lỗi (Fixes & Improvements)
- **Fix:** Khắc phục lỗi nút quét vé nhanh vẫn hiển thị ngoài màn hình chính dù đã bị tắt trong Cài đặt.
- **Fix:** Sửa hiện tượng bảng danh sách xe che lấp đường đi của xe buýt khi chọn theo dõi xe tại điểm dừng.
- **Performance:** Tối ưu hóa bộ nhớ và tốc độ tải dữ liệu trạm dừng ngoại tuyến (`hanoi_stations.json`).
- **Clean Architecture:** Đồng bộ hóa phiên bản ứng dụng về chuẩn v1.0 (Build 1) trên toàn bộ hệ thống (`build.gradle.kts` và giao diện người dùng).
