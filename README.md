# 🚌 AllBus - Ứng Dụng Tra Cứu Xe Buýt & Lộ Trình Hà Nội

**AllBus** (v1.0 - Build 1) là ứng dụng Android hiện đại hỗ trợ tra cứu lộ trình xe buýt thông minh, theo dõi vị trí xe buýt theo thời gian thực và tích hợp thẻ vé điện tử tại Hà Nội.

---

## ✨ Tính năng nổi bật

- 🚍 **Tra cứu tuyến xe buýt toàn diện**:
  - Danh sách đầy đủ các tuyến xe buýt nội thành, liên tỉnh và buýt nhanh (BRT) tại Hà Nội.
  - Thông tin chi tiết: Thời gian hoạt động, giãn cách chuyến, giá vé, danh sách trạm dừng lượt đi & lượt về.
  
- 🗺️ **Bản đồ toàn bộ trạm dừng (5,000+ điểm)**:
  - Bản đồ tương tác mượt mà tích hợp OpenStreetMap / OSMDroid.
  - Hiển thị vị trí người dùng, trạm dừng lân cận và các tuyến xe đi qua mỗi trạm.
  - Hỗ trợ chế độ la bàn xoay bản đồ theo hướng di chuyển và tự động căn chỉnh góc nhìn.

- 🧭 **Tìm đường thông minh (Trip Planner)**:
  - Thuật toán tìm đường tối ưu kết hợp đi bộ và chuyển tiếp xe buýt.
  - Hiển thị từng chặng di chuyển chi tiết kèm chỉ dẫn trực quan trên bản đồ.

- ⏱️ **Theo dõi xe buýt sắp tới điểm theo thời gian thực**:
  - Dự báo thời gian và khoảng cách xe buýt sắp đến trạm đón khách.
  - Chế độ theo dõi trực quan: Khi chọn một xe, bản đồ tự động thu gọn bảng thông tin để hiển thị trọn vẹn lộ trình di chuyển của xe.

- 🎫 **Tích hợp Thẻ vé ảo Giao thông Hà Nội**:
  - Hỗ trợ mở nhanh mã QR vé xe buýt điện tử từ ứng dụng "Thẻ vé giao thông HN".
  - Nút lối tắt QR tiện lợi và màn hình thẻ vé chuyên biệt.

---

## 🛠️ Công nghệ sử dụng

- **Ngôn ngữ**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) & Material 3
- **Kiến trúc**: MVVM (Model-View-ViewModel), StateFlow, Coroutines
- **Bản đồ**: [OSMDroid](https://github.com/osmdroid/osmdroid) (OpenStreetMap for Android)
- **Networking & JSON**: Retrofit, OkHttp, kotlinx.serialization / Gson
- **Build System**: Gradle Kotlin DSL (`build.gradle.kts`)

---

## 🚀 Cài đặt & Build dự án

### Yêu cầu môi trường
- **Android Studio**: Hedgehog (2023.1.1) hoặc mới hơn
- **JDK**: Java 17
- **Android SDK**:
  - `minSdk`: 26 (Android 8.0 Oreo)
  - `targetSdk`: 34 (Android 14)
  - `compileSdk`: 34

### Các bước thực hiện

1. **Clone repository**:
   ```bash
   git clone https://github.com/anlq295-work/AllBus.git
   cd AllBus
   ```

2. **Mở dự án trong Android Studio**:
   - Chọn **Open** và trỏ đến thư mục `AllBus`.
   - Chờ Gradle đồng bộ (Sync Project with Gradle Files).

3. **Build APK qua dòng lệnh**:
   ```bash
   # Windows
   .\gradlew.bat assembleDebug

   # Linux / macOS
   ./gradlew assembleDebug
   ```
   File APK sau khi build sẽ nằm tại: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📁 Cấu trúc thư mục

```text
AllBus/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── assets/            # Dữ liệu trạm và tuyến xe buýt Hà Nội
│   │       ├── java/.../hanoibus/ # Mã nguồn chính (Data, UI, ViewModel, Map)
│   │       └── res/               # Giao diện XML, icon launcher, chuỗi ngôn ngữ
│   └── build.gradle.kts           # Cấu hình build module app
├── gradle/wrapper/                # Gradle Wrapper
├── build.gradle.kts               # Root build configuration
├── settings.gradle.kts            # Project settings
└── README.md
```

---

## 📄 Bản quyền & Đóng góp
Dự án được phát triển nhằm nâng cao trải nghiệm giao thông công cộng tại Hà Nội. Mọi ý kiến đóng góp và Pull Request luôn được hoan nghênh!
