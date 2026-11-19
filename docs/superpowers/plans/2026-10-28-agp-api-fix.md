# Xử lý cảnh báo API cũ của AGP — 2026-10-28

Đã loại bỏ cảnh báo `Configuration.setVisible(boolean)` bằng cách cập nhật AGP từ `9.4.1` lên `9.5.0-rc02` trong `gradle/libs.versions.toml`. Gradle wrapper vẫn là `9.8.2`. Không thêm cấu hình tắt cảnh báo, Lint baseline hoặc suppression.

Trace trước sửa chỉ tới mã nội bộ AGP, gồm `BasePlugin`, `SourceSetManager` và các cấu hình dependency/test. Chạy `help --warning-mode=fail --no-configuration-cache` với AGP 9.4.1 thất bại vì deprecation; cùng phép kiểm tra với 9.5.0-rc02 thành công. Google ghi nhận bản sửa ở nhánh AGP 9.5 trong [release notes chính thức](https://developer.android.com/build/releases/agp-9-5-0-release-notes).

**AGP 9.5.0-rc02 là release candidate, chưa phải bản stable.** Tại thời điểm kiểm tra, Google Maven vẫn cung cấp 9.4.1 là stable mới nhất; nhánh 9.5 chứa bản sửa. Đã lưu bản sao cấu hình trước cập nhật trong `.codex/agp-api-2026-10-28/before/`.

Lint mới còn báo Firebase BoM 35.0.2 đã có bản mới. Đã cập nhật lên 35.1.0 sau khi đối chiếu [Firebase release notes](https://firebase.google.com/support/release-notes/android) và hai POM chính thức. Trong các thư viện app khai báo trực tiếp, Auth vẫn 25.0.0, Analytics vẫn 24.0.0, Firestore đổi 27.0.1 → 27.1.0. Không tìm thấy lời gọi Firebase/Firestore trong `app/src/main`; chưa kiểm thử dịch vụ Firebase trực tuyến.

## Xác nhận sau cùng

Lệnh chạy từ thư mục dự án:

```powershell
rtk proxy python .codex/agp-api-2026-10-28/run_gradle.py build-final.log :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:lintRelease --warning-mode=fail --no-configuration-cache --rerun-tasks
```

Runner dùng JBR tại `C:\Program Files\Android\Android Studio\jbr` chỉ trong môi trường tiến trình, không thay JAVA_HOME toàn máy. Nó chạy Gradle wrapper với `--console=plain --max-workers=4` và lưu log UTF-8.

- Exit code 0: `BUILD SUCCESSFUL in 1m 24s`; 132/132 tác vụ được thực thi lại.
- Debug APK, release APK chưa ký và APK instrumentation đều build thành công.
- 5 unit test đạt; 0 thất bại, 0 lỗi, 0 bỏ qua.
- Lint debug và release: 0 lỗi, 0 cảnh báo, dùng Lint 9.5.0-rc02.
- Cảnh báo `Configuration.setVisible(boolean)`: baseline có 51 lần xuất hiện khi bật trace; log build cuối có 0.
- SHA-256 của 134 tệp trong `app/src` không đổi. Trong 9 tệp cấu hình/wrapper được đối chiếu, chỉ `gradle/libs.versions.toml` đổi đúng hai dòng phiên bản nêu trên.

Build vẫn có thông tin đóng gói `libdatastore_shared_counter.so` mà không strip symbol; đây không phải cảnh báo API cũ của AGP. Không thêm tùy chọn che thông báo này.

## Bằng chứng và phạm vi

Thư mục `.codex/agp-api-2026-10-28/` lưu `baseline.log`, `after-configuration.log`, `build-final.log`, `configuration.diff`, bản sao XML Lint/unit test trong `reports/`, cùng `verification.json` chứa kích thước, thời gian tạo và SHA-256 các APK. `build.log` là lần build trung gian sau khi chỉ cập nhật AGP; `build-final.log` là kết quả sau cả hai cập nhật.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `b96ccc04dc1aaf9f4aeb431419c650effc9008c3510b7ec8e76190d080c343d8`.

APK release chưa ký: `app/build/outputs/apk/release/app-release-unsigned.apk`, SHA-256 `2527fed7ab61f6c019100dcc98bf05a49b53e76a3410689420dcb59a9ee57f1b`.

Lần này chưa chạy lại instrumentation trên emulator/thiết bị và chưa xác nhận UI bằng mắt. Kết quả instrumentation trước đây không được tính là đã chạy với AGP mới.

Android Studio đang cài là 2026.1.1 (Quail 1), theo `product-info.json`. [Bảng tương thích chính thức](https://developer.android.com/build/releases/about-agp) chỉ liệt kê Quail 1 hỗ trợ tới AGP 9.2; IDE này đã cũ so với cả cấu hình AGP 9.4.1 ban đầu. Muốn Sync AGP 9.5 trong IDE cần Android Studio hỗ trợ 9.5, chẳng hạn [Rabbit 2 RC 2 đi cùng AGP 9.5.0-rc02](https://developer.android.com/studio/releases/fixed-bugs/studio/2026.2.2). Build dòng lệnh đã được xác nhận; chưa cập nhật Android Studio và chưa kiểm tra IDE Sync.
