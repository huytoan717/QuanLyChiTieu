# Quản lý ví và số dư thực

Mục tiêu: thêm, sửa, xóa ví; nhập số dư ban đầu; cập nhật số dư theo thu, chi và chuyển khoản; báo cáo Word gồm code và ảnh thao tác, kết quả.

Giữ Kotlin/XML/SQLite và repository hiện có. Không thêm thư viện hoặc mô hình kiến trúc mới. Dùng vòng lặp và hàm nhỏ, tên rõ nghĩa theo yêu cầu của người dùng.

Thiết kế: bảng wallets trong transactions.db phiên bản 3. Wallet.id là khóa cố định (tên gốc để tương thích giao dịch cũ), Wallet.name là tên hiển thị có thể sửa. Giao dịch giữ khóa ví; đổi tên không sửa số tiền hoặc mất liên kết. Ví mặc định và ví cũ được khởi tạo với số dư ban đầu 0. Không dùng số dư hoặc giao dịch mẫu khi khởi tạo tài khoản mới. Cấu hình ví lưu trên thiết bị; cơ chế đồng bộ giao dịch hiện tại giữ nguyên.

Công thức: ban đầu + thu - chi + chuyển vào - chuyển ra, dùng toàn bộ giao dịch chưa xóa; giao dịch loại khỏi ngân sách vẫn ảnh hưởng số dư. Số dư âm được hiển thị đúng. Ví chỉ xóa được khi số dư 0 và không có giao dịch đang tồn tại. Tên rỗng/trùng và tiền không hợp lệ bị từ chối. Mã ví không thay đổi khi đổi tên, không tái sử dụng tên gốc của ví đang tồn tại.

- [x] Kiểm thử số dư không có tiền mẫu, từng ví, chuyển khoản, sửa/xóa và số lớn.
- [x] Nâng cấp SQLite giữ dữ liệu v1/v2; quản lý ví và tính số dư; bổ sung ví vào TransactionSnapshot.
- [x] Màn danh sách ví và form thêm/sửa/xóa; nút mở từ Trang chủ và Tài khoản; giữ dữ liệu nhập khi xoay màn hình.
- [x] Dùng danh sách ví thật trong form giao dịch; cập nhật tên và số dư trên Trang chủ/Tài khoản/Chi tiết/Lịch sử.
- [x] Kiểm thử migration, dữ liệu lưu sau mở lại, hạn chế xóa, tên trùng, sửa/xóa/chuyển khoản, giao diện và cỡ chữ lớn. Build debug/release và lint.
- [x] Chụp flow trên giả lập riêng với cơ sở dữ liệu thử; tạo báo cáo Word đơn giản có code và ảnh kết quả; render và kiểm tra từng trang.

Các file chính: model/WalletBalance.kt, model/DomainModels.kt, data/TransactionStore.kt, data/TransactionRepository.kt, ui/wallet/WalletActivity.kt, ui/wallet/EditWalletActivity.kt, ui/wallet/WalletEditViewModel.kt, các màn giao dịch và Trang chủ/Tài khoản. Kiểm chứng với Gradle testDebugUnitTest, assembleDebug/assembleRelease/lintDebug và instrumentation trên giả lập riêng.

Kết quả xác nhận ngày 16/11/2026: 17 unit test đạt; bộ 44 kiểm thử thiết bị đạt và 1 kiểm thử bố cục ví ở cỡ chữ 200% đạt. Chạy lại riêng flow chụp báo cáo sau khi chờ thông báo tạm biến mất cũng đạt. Debug/release build thành công. Debug Lint có 0 lỗi, 248 cảnh báo và 6 gợi ý còn lại; không tuyên bố đã xử lý các cảnh báo ngoài chức năng ví. Log và kết quả đối chiếu nằm trong `.codex/wallet-feature/`.

Báo cáo `outputs/bao-cao-vi/Bao_cao_Quan_ly_vi_va_So_du_thuc.docx` gồm 5 trang, 5 đoạn code lấy đúng từ mã nguồn và 13 vị trí ảnh chụp app. Đã xuất qua Microsoft Word và xem từng trang PNG. Ví minh họa đổi tên từ Ví sinh hoạt thành Ví chi tiêu, sửa số dư ban đầu từ 1.000.000 lên 2.000.000 VNĐ, chi 200.000 VNĐ, chuyển 300.000 VNĐ và xóa Ví để trống. Tổng cuối 1.800.000 VNĐ.

Phạm vi đã kiểm chứng: SQLite và màn hình trên máy ảo Android API 37.1 riêng, không ghi dữ liệu mẫu vào máy ảo của người dùng. Tên ví và số dư ban đầu lưu cục bộ; không có đồng bộ cấu hình ví hoặc kết nối ngân hàng. Chưa kiểm tra thiết bị vật lý hay thao tác TalkBack trực tiếp.
