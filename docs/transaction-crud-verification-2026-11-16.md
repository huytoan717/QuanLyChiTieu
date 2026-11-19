# Chi tiết, sửa và xóa giao dịch

Hoàn thành ngày 16/11/2026 trên mã nguồn hiện tại của QuanLyChiTieu.

## Cách sử dụng

1. Tại Trang chủ hoặc Lịch sử giao dịch, chạm vào một giao dịch.
2. Màn Chi tiết hiển thị loại giao dịch, số tiền, danh mục, ví, thời gian, ghi chú, lựa chọn ngân sách và trạng thái đồng bộ. Chuyển khoản hiển thị cả ví nguồn và ví đích.
3. Chọn **Sửa giao dịch**, cập nhật thông tin và chọn **Lưu thay đổi**. Bản ghi giữ nguyên ID; không tạo giao dịch trùng. Các nhãn ví/danh mục cũ, mã danh mục và ghi chú dài được giữ nguyên khi không chỉnh sửa chúng.
4. Chọn **Xóa giao dịch** để xem hộp xác nhận có số tiền, danh mục và thời gian. Chỉ xóa sau khi xác nhận; chọn Hủy giữ nguyên dữ liệu.

Thay đổi được lưu trên thiết bị trước, sau đó gửi lên Firestore. Khi mất mạng hoặc đồng bộ thất bại, ứng dụng giữ hàng đợi qua lần mở tiếp theo và có nút thử lại. Trang chủ/Lịch sử vẫn hiển thị trạng thái chờ đồng bộ sau khi xóa giao dịch cuối cùng. Các tổng thu/chi và số chi thực tế trong ngân sách, báo cáo được cập nhật từ danh sách giao dịch mới.

SQLite được nâng từ phiên bản 1 lên 2 bằng migration giữ dữ liệu cũ. Phiên bản thay đổi và dấu xóa ngăn phản hồi đồng bộ cũ ghi đè thao tác mới hoặc làm giao dịch đã xóa xuất hiện lại.

## Kết quả xác minh

| Hạng mục | Kết quả |
| --- | --- |
| Build debug và release | Thành công; APK release chưa ký |
| Unit test | 9/9 đạt, không bỏ qua |
| Kiểm thử emulator đầy đủ | 36/36 đạt trên Pixel_10, Android 17/API 37 |
| Ca chọn ví chuyển khoản | Chạy lại riêng 2 lần, đều đạt |
| Kiểm tra giao diện bổ sung | Đạt ở font scale 1.0 và 2.0; kiểm tra nút sửa/xóa/lưu hiển thị hoàn toàn và có thể thao tác |
| Kiểm tra ảnh chụp | Đã xem Chi tiết, cuối trang, hộp xác nhận Xóa, phần đầu/cuối form Sửa; nội dung xuống dòng và cuộn được ở cỡ chữ 200% |
| Lint debug/release | Mỗi biến thể: 0 lỗi, 255 cảnh báo, 6 gợi ý |
| Cấu hình build và thư viện | SHA-256 của hai file build.gradle.kts và gradle/libs.versions.toml trùng bản trước chỉnh sửa |

Các kiểm thử mới bao phủ migration, cập nhật cùng ID, xóa và mở lại cơ sở dữ liệu, phản hồi đồng bộ cũ, sửa/xóa khi yêu cầu trước đang gửi, thử lại sau khởi động, snapshot máy chủ không đầy đủ, đổi màn hình, xoay màn hình, hủy/xác nhận xóa, giữ ghi chú dài và tổng tiền về 0.

Một lượt kiểm thử chọn ví gặp thao tác chạm trong lúc bàn phím đang chuyển động. Log xác nhận sự chồng thời điểm này; kiểm thử đã đóng bàn phím trước khi mở danh sách ví. Sau sửa, cả lượt đầy đủ và hai lượt riêng đều đạt. Bộ kiểm tra ảnh sau đó được bổ sung đợi cửa sổ ổn định, cuộn ngay tới cuối trang và kiểm tra nút hiển thị hoàn toàn; cả hai cỡ chữ đều được chạy lại trên APK ứng dụng đã build thành công.

## Bằng chứng

Các file nằm trong `.codex/transaction-crud-2026-11-16/`:

- `verification-complete.log`: lượt build, unit, Lint và 36 kiểm thử emulator thành công.
- `verification-summary.json`: tổng số kiểm thử, Lint, checksum cấu hình và APK.
- `final-reports/`: bản sao báo cáo XML của lượt đầy đủ.
- `transfer-repeat.log`: hai lượt kiểm tra riêng ca chọn ví.
- `visual-normal-final.log`, `visual-large-final.log`: kết quả kiểm tra giao diện sau khi hoàn thiện bộ chụp ảnh.
- `screenshots-final/`: 10 ảnh PNG nguyên bản từ emulator, tên kết thúc bằng `1.0` hoặc `2.0` theo cỡ chữ.
- `review-report.md`: rà soát mã nguồn độc lập; các phát hiện đã được xử lý.
- `before/`, `before-sha256.json`, `review.diff`: bản lưu nguồn/cấu hình và nội dung thay đổi để xem lại.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `17a3b540114014a2ae676a57029af6c12d41862906b130ebe667cfe70cf76260`.

## Giới hạn xác minh

- Các kiểm thử thao tác dữ liệu dùng SQLite riêng và dịch vụ đồng bộ giả lập. Chưa xác minh trực tiếp quyền Firestore, mạng thật hoặc đồng bộ giữa hai thiết bị; không dùng dữ liệu Firebase của người dùng để thử sửa/xóa.
- Chưa kiểm tra trên thiết bị vật lý hoặc tương tác TalkBack thủ công.
- Lint còn 255 cảnh báo trên toàn dự án; Gradle vẫn phát thông báo API bị deprecated trong cấu hình hiện có. Không nâng AGP hoặc thư viện trong thay đổi này.
- Quản lý ví và tính số dư thực tế của từng ví nằm ngoài phạm vi chức năng này.
