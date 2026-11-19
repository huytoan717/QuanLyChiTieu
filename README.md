# Quản Lý Chi Tiêu

Dự án Android quản lý chi tiêu, viết bằng Kotlin với giao diện Android Views/XML. Mã nguồn gồm các phần giao dịch, ví, ngân sách và báo cáo; sử dụng SQLite và Firebase.

## Chạy dự án

1. Mở thư mục dự án bằng Android Studio.
2. Cài Android SDK Platform 37. Dự án hỗ trợ thiết bị từ Android API 24.
3. Dùng JDK 21 cho Gradle, theo `gradle/gradle-daemon-jvm.properties`.
4. Thêm ứng dụng Android có package `com.nhom9.quanlychitieu` vào dự án Firebase của bạn, tải `google-services.json` và đặt vào thư mục `app/`. Thiết lập Authentication, Cloud Firestore và quyền truy cập phù hợp với môi trường của bạn.
5. Đồng bộ Gradle và chạy cấu hình `app` trên thiết bị hoặc máy ảo.

`local.properties` chứa đường dẫn Android SDK trên từng máy và có thể được Android Studio tạo tự động. Cấu hình Firebase, tệp ký ứng dụng, cache và bản build được giữ ngoài Git. Các tệp đang có trên máy gốc vẫn được giữ nguyên.

## Build và kiểm thử

Tại thư mục gốc trên Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Kiểm thử giao diện cần thiết bị hoặc máy ảo đang kết nối:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Cấu trúc

- `app/src/main/java/com/nhom9/quanlychitieu/`: giao diện, model và lớp dữ liệu.
- `app/src/main/res/`: layout, chuỗi hiển thị và tài nguyên.
- `app/src/test/`: kiểm thử đơn vị.
- `app/src/androidTest/`: kiểm thử trên thiết bị.
- `docs/`: ghi chú triển khai và kiểm chứng.
- Các script Python ở thư mục gốc: công cụ hỗ trợ tạo báo cáo và kiểm tra.

Các bản báo cáo Word, ảnh chụp phục vụ báo cáo, tệp nén dự án và thư mục `outputs/` là kết quả tạo trên máy, không nằm trong repository mã nguồn.
