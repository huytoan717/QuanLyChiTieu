import os
import docx
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml import parse_xml
from docx.oxml.ns import nsdecls

def create_report():
    doc = Document()

    # Set margins
    for section in doc.sections:
        section.top_margin = Inches(0.8)
        section.bottom_margin = Inches(0.8)
        section.left_margin = Inches(0.8)
        section.right_margin = Inches(0.8)

    # Styling colors
    INDIGO = RGBColor(99, 102, 241)
    CYAN = RGBColor(56, 189, 248)

    # Title
    p_title = doc.add_paragraph()
    p_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run_title = p_title.add_run("BÁO CÁO TỔNG QUAN KIẾN TRÚC, CHỨC NĂNG NÂNG CAO & LUỒNG GIAO DIỆN (UI/UX)\nỨNG DỤNG QUẢN LÝ CHI TIÊU - AURA FINTECH")
    run_title.font.name = "Arial"
    run_title.font.size = Pt(18)
    run_title.font.bold = True
    run_title.font.color.rgb = INDIGO

    # Subtitle
    p_sub = doc.add_paragraph()
    p_sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run_sub = p_sub.add_run("Hệ Thống Tài Chính Cá Nhân Đa Nền Tảng • Offline-First & Firebase Realtime Synchronization")
    run_sub.font.name = "Arial"
    run_sub.font.size = Pt(11)
    run_sub.font.italic = True
    run_sub.font.color.rgb = CYAN

    doc.add_paragraph()

    # Project Info Table
    table_info = doc.add_table(rows=5, cols=2)
    table_info.alignment = WD_TABLE_ALIGNMENT.CENTER
    info_data = [
        ("Tên Ứng Dụng", "Quản Lý Chi Tiêu (Aura Fintech)"),
        ("Mã Gói (Package Name)", "com.nhom9.quanlychitieu"),
        ("Kiến Trúc Phần Mềm", "Clean Architecture + MVVM + Single Source of Truth Repository"),
        ("Công Nghệ Nền Tảng", "Kotlin, Android SDK 37, AGP 9.2.1, Jetpack ViewModel, LiveData"),
        ("Cơ Sở Dữ Liệu & Cloud", "Firebase Firestore (Cloud Realtime) + SQLite Local Store (Offline-First)")
    ]

    for i, (k, v) in enumerate(info_data):
        row = table_info.rows[i]
        c0, c1 = row.cells[0], row.cells[1]
        c0.width, c1.width = Inches(2.2), Inches(4.5)

        p0 = c0.paragraphs[0]
        r0 = p0.add_run(k)
        r0.font.name, r0.font.bold, r0.font.size = "Arial", True, Pt(10)

        p1 = c1.paragraphs[0]
        r1 = p1.add_run(v)
        r1.font.name, r1.font.size = "Arial", Pt(10)

    doc.add_paragraph()

    # Section 1: Overview Flow
    h1 = doc.add_heading(level=1)
    r_h1 = h1.add_run("1. Sơ Đồ Luồng Giao Diện Tổng Quan (UI/UX Flow Summary)")
    r_h1.font.name, r_h1.font.color.rgb = "Arial", INDIGO

    p_flow_desc = doc.add_paragraph()
    r_fd = p_flow_desc.add_run("Ứng dụng được thiết kế theo phong cách Dark Glassmorphic hiện đại với thanh điều hướng 4 Tab cố định (Trang chủ, Báo cáo, Ngân sách, Tài khoản) và phím nổi FAB (+) để thêm giao dịch nhanh. Luồng người dùng được tối ưu hóa như sau:")
    r_fd.font.name, r_fd.font.size = "Arial", Pt(10.5)

    # Flow Table
    flow_table = doc.add_table(rows=7, cols=3)
    flow_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    flow_headers = ["Màn Hình (Screen)", "Chức Năng Chính (Core Feature)", "Điểm Hướng Đến (Navigation / Flow)"]

    hdr_cells = flow_table.rows[0].cells
    for j, text in enumerate(flow_headers):
        p = hdr_cells[j].paragraphs[0]
        r = p.add_run(text)
        r.font.name, r.font.bold, r.font.size = "Arial", True, Pt(10)
        r.font.color.rgb = RGBColor(255, 255, 255)
        shading = parse_xml(f'<w:shd {nsdecls("w")} w:fill="6366F1"/>')
        hdr_cells[j]._tc.get_or_add_tcPr().append(shading)

    flow_rows = [
        ("Trang Chủ (MainActivity)", "Hiển thị tổng số dư thực tế, 3 giao dịch mới nhất, 4 thẻ danh mục và biểu đồ sóng.", "-> Lịch Sử Giao Dịch, -> Thêm Giao Dịch"),
        ("Lịch Sử Giao Dịch (TransactionHistoryActivity)", "Xem toàn bộ lịch sử thu chi, lọc giao dịch theo 4 chế độ (Tất cả, Chi tiêu, Thu nhập, Chuyển khoản).", "-> Quay lại Trang chủ"),
        ("Thêm Giao Dịch Mới (AddTransactionActivity)", "Nhập số tiền chi/thu/chuyển khoản, phím quick amount, gắn danh mục và Bộ bảo vệ Hạn mức (Cap Guard).", "-> Lưu & Sync Firebase -> Trang chủ"),
        ("Báo Cáo & Thống Kê (ReportActivity)", "Phân tích thu chi qua 3 mốc (Tuần/Tháng/Năm), Biểu đồ Cột, Biểu đồ Donut và Nhận xét AI.", "-> Chuyển đổi mốc thời gian"),
        ("Quản Lý Ngân Sách (BudgetActivity)", "Theo dõi trần chi tiêu, hạn mức 4 nhóm danh mục, Hũ tiết kiệm tích lũy, Cài đặt Hạn mức Bảo vệ.", "-> Thêm/Sửa hạn mức, -> Cấu hình Caps"),
        ("Tài Khoản & Cài Đặt (AccountActivity)", "Hồ sơ cá nhân, tổng tài sản ròng 3 ví, tùy chỉnh bảo mật sinh trắc học và Đăng xuất.", "-> Chỉnh sửa hồ sơ, -> Đăng xuất")
    ]

    for i, row_data in enumerate(flow_rows):
        row_cells = flow_table.rows[i+1].cells
        for j, val in enumerate(row_data):
            p = row_cells[j].paragraphs[0]
            r = p.add_run(val)
            r.font.name, r.font.size = "Arial", Pt(9.5)

    doc.add_paragraph()

    # Section 2: Detailed Screens
    h2 = doc.add_heading(level=1)
    r_h2 = h2.add_run("2. Chi Tiết Các Màn Hình & Hình Ảnh Luồng Thực Tế")
    r_h2.font.name, r_h2.font.color.rgb = "Arial", INDIGO

    screens_detail = [
        {
            "num": "2.1",
            "title": "Màn Hình Trang Chủ (MainActivity)",
            "img": "D:/android/QuanLyChiTieu/report_screenshots/01_main_activity.png",
            "desc": [
                "• Header Cá Nhân: Lời chào cá nhân hóa, Avatar với chấm trạng thái xanh và Nút Chuông thông báo Glass Circle.",
                "• Giao Dịch Gần Đây: Hiển thị tối đa 3 giao dịch mới nhất giúp trang chủ gọn gàng. Bấm 'Xem tất cả >' để mở Lịch Sử.",
                "• Thẻ Số Dư Thực Tế: Tự động tính toán theo công thức Số Dư = Số Dư Gốc + Tổng Thu - Tổng Chi. Cập nhật Badge tăng trưởng (+8.4% / -2.1%) và Biểu đồ sóng Bézier (WaveChartView).",
                "• Lưới 2x2 Danh Mục Chi Tiêu: Cập nhật tổng chi và % tỷ trọng thời gian thực cho 4 nhóm (Di chuyển, Ăn uống, Thuê nhà, Mua sắm)."
            ]
        },
        {
            "num": "2.2",
            "title": "Màn Hình Lịch Sử Giao Dịch (TransactionHistoryActivity)",
            "img": "D:/android/QuanLyChiTieu/report_screenshots/02_history_activity.png",
            "desc": [
                "• Thanh Điều Hướng Header: Nút Quay lại dạng tròn nổi bật với đệm an toàn tai thỏ/cutout, không đè sát mép trên.",
                "• Bộ Lọc 4 Chế Độ: Segment bar lọc dữ liệu tức thì theo [ Tất cả | Chi tiêu | Thu nhập | Chuyển khoản ].",
                "• Thống Kê Số Lượng: Tự động đếm số lượng giao dịch phù hợp với bộ lọc đang chọn.",
                "• Danh Sách Chi Tiết: Hiển thị tên danh mục, số tiền phân màu (Đỏ - Chi, Xanh lá - Thu, Cyan - Chuyển), ví giao dịch, ngày giờ và ghi chú."
            ]
        },
        {
            "num": "2.3",
            "title": "Màn Hình Thêm Giao Dịch Mới (AddTransactionActivity)",
            "img": "D:/android/QuanLyChiTieu/report_screenshots/03_add_transaction.png",
            "desc": [
                "• Chuyển Đổi Loại Giao Dịch: Pill tab chuyển nhanh giữa Chi tiêu, Thu nhập và Chuyển khoản.",
                "• Phím Chọn Nhanh Số Tiền: Cộng dồn tức thì các mức +20k, +50k, +100k, +200k, +500k.",
                "• Lưới 8 Danh Mục Chi Tiêu: Biểu tượng pictograms trực quan cho Ăn uống, Mua sắm, Di chuyển, Hóa đơn, Giải trí, Sức khỏe...",
                "• Tích Hợp Bộ Bảo Vệ Hạn Mức (Cap Guard Engine): Tự động phát cảnh báo khi khoản chi vượt quá 2.000.000 VNĐ / 1 giao dịch hoặc làm tổng chi hôm nay vượt 1.000.000 VNĐ.",
                "• Luồng Lưu Đội Đệm Offline-First: Ghi dữ liệu tức thì vào SQLite local (phản hồi UI 0.01s) và đồng bộ ngầm lên Cloud Firestore."
            ]
        },
        {
            "num": "2.4",
            "title": "Màn Hình Báo Cáo & Thống Kê (ReportActivity)",
            "img": "D:/android/QuanLyChiTieu/report_screenshots/04_report_activity.png",
            "desc": [
                "• Bộ Lọc Mốc Thời Gian: Chuyển đổi linh hoạt giữa [ Tuần | Tháng | Năm ].",
                "• Thẻ Tổng Quan Thu Chi: Hiển thị tổng chi tiêu, tổng thu nhập và tỷ lệ tiết kiệm (%) được tính toán tự động.",
                "• Biểu Đồ Bánh Donut (DonutChartView): Phân tích tỷ trọng chi tiêu của 4 nhóm danh mục chính.",
                "• Biểu Đồ Cột Thu - Chi (BarChartView): So sánh trực quan cột Thu nhập (Cyan) và Chi tiêu (Tím) qua 7 ngày trong tuần, 4 tuần trong tháng hoặc 12 tháng trong năm.",
                "• Thẻ Nhận Xét AI Thông Minh: Tự động phát sinh nhận xét tối ưu tài chính dựa trên dữ liệu thu chi thực tế."
            ]
        },
        {
            "num": "2.5",
            "title": "Màn Hình Quản Lý Ngân Sách (BudgetActivity)",
            "img": "D:/android/QuanLyChiTieu/report_screenshots/05_budget_activity.png",
            "desc": [
                "• Thẻ Tổng Quan Hạn Mức: Hiển thị tổng trần chi tiêu, số tiền còn lại an toàn và mức chi đề xuất mỗi ngày.",
                "• Thẻ Cài Đặt Hạn Mức Bảo Vệ: Tùy chỉnh bật/tắt và cài đặt hạn mức chi tiêu 1 giao dịch và hạn mức chi tiêu ngày.",
                "• Hạn Mức Theo Nhóm: Theo dõi tiến độ chi tiêu của từng nhóm danh mục với màu sắc cảnh báo (An toàn / Cận trần / Vượt trần).",
                "• Quỹ Mục Tiêu Tích Lũy (Hũ Tiết Kiệm): Quản lý hũ Du lịch Nhật Bản, Dự phòng khẩn cấp. Nút '+ Nạp hũ' cộng dồn tiền trực tiếp lên Cloud Firestore.",
                "• BottomSheet Modal: Form tạo thêm ngân sách mới và điều chỉnh hạn mức danh mục linh hoạt."
            ]
        },
        {
            "num": "2.6",
            "title": "Màn Hình Tài Khoản & Cài Đặt (AccountActivity)",
            "img": "D:/android/QuanLyChiTieu/report_screenshots/06_account_activity.png",
            "desc": [
                "• Hồ Sơ Cá Nhân: Ảnh đại diện tròn có viền, tên người dùng 'Toan Nguyen' và nút 'Chỉnh sửa' dạng pill.",
                "• Tổng Tài Sản Ròng: Thẻ tổng hợp số dư ròng từ 3 ví liên kết.",
                "• Danh Sách Ví Liên Kết: Quản lý Visa Platinum, Ví MoMo/ZaloPay và Tiết kiệm tích lũy.",
                "• Cài Đặt & Đăng Xuất: Tùy chỉnh giao diện Dark Glassmorphism, bảo mật sinh trắc học và nút Đăng xuất an toàn."
            ]
        }
    ]

    for item in screens_detail:
        h_screen = doc.add_heading(level=2)
        r_hs = h_screen.add_run(f"{item['num']} {item['title']}")
        r_hs.font.name, r_hs.font.color.rgb = "Arial", CYAN

        if os.path.exists(item['img']):
            p_img = doc.add_paragraph()
            p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
            run_img = p_img.add_run()
            run_img.add_picture(item['img'], width=Inches(2.8))

        for desc_line in item['desc']:
            p_d = doc.add_paragraph()
            r_d = p_d.add_run(desc_line)
            r_d.font.name, r_d.font.size = "Arial", Pt(10)

        doc.add_paragraph()

    # Section 3: Create Transaction Flow
    h3 = doc.add_heading(level=1)
    r_h3 = h3.add_run("3. Chi Tiết Quy Trình & Luồng Nghiệp Vụ Tạo Chi Tiêu Mới (Create Expense Flow)")
    r_h3.font.name, r_h3.font.color.rgb = "Arial", INDIGO

    p_flow_intro = doc.add_paragraph()
    r_fi = p_flow_intro.add_run(
        "Luồng Tạo Chi Tiêu Mới là một trong những luồng nghiệp vụ cốt lõi nhất của ứng dụng, "
        "được tối ưu hóa tối đa để người dùng có thể ghi nhận một khoản chi tiêu chỉ trong 3 bước thao tác cực kỳ nhanh chóng:"
    )
    r_fi.font.name, r_fi.font.size = "Arial", Pt(10.5)

    step_rows = [
        ("Bước 1: Kích hoạt Form", "Bấm phím nổi (+) FAB ở giữa thanh Bottom Navigation từ bất kỳ màn hình nào.", "Mở AddTransactionActivity với animation trượt mượt. Tab 'Chi tiêu' được chọn mặc định."),
        ("Bước 2: Nhập số tiền", "Nhập trực tiếp bàn phím (ví dụ: 150000) hoặc bấm phím chọn nhanh +20k, +50k, +100k, +200k, +500k.", "Tự động định dạng hiển thị tiền tệ dạng 150.000 VNĐ. Xóa thông báo lỗi nếu có."),
        ("Bước 3: Chọn danh mục", "Bấm chọn 1 trong 8 biểu đồ danh mục (Ăn uống, Di chuyển, Mua sắm, Hóa đơn...).", "Đánh dấu selected cho card danh mục, gắn Category ID tương ứng (ví dụ: cat_food)."),
        ("Bước 4: Chọn ví & ghi chú", "Chọn Ví thanh toán (Visa Platinum, MoMo, Tiền mặt) và nhập ghi chú tùy chọn (ví dụ: Cà phê phin).", "Tải cấu hình ví và thời gian hiện tại System.currentTimeMillis()."),
        ("Bước 5: Bấm 'Lưu giao dịch'", "Người dùng chạm nút gradient 'Lưu giao dịch' ở đáy màn hình.", "Kích hoạt TransactionSaveViewModel.save(). Kiểm tra Hạn mức Cap Guard."),
        ("Bước 6: Ghi đệm Local & Sync Cloud", "Hệ thống tự động thực thi trong nền.", "1. Ghi SQLite local store (0.01s). 2. Đẩy tài liệu users/{uid}/transactions/{id} lên Firebase Firestore."),
        ("Bước 7: Tự động nhảy số toàn App", "Form tự đóng finish(), quay về Trang chủ.", "1. Trang chủ nhảy Số dư & 3 giao dịch mới. 2. Ngân sách nhảy % hạn mức. 3. Báo cáo nhảy Biểu đồ Cột & Donut.")
    ]

    step_table = doc.add_table(rows=len(step_rows) + 1, cols=3)
    step_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    step_headers = ["Thứ Tự Bước", "Thao Tác Người Dùng (Action)", "Xử Lý Hệ Thống & Tự Động Hóa (System Execution)"]

    hdr_cells_s = step_table.rows[0].cells
    for j, text in enumerate(step_headers):
        p = hdr_cells_s[j].paragraphs[0]
        r = p.add_run(text)
        r.font.name, r.font.bold, r.font.size = "Arial", True, Pt(10)
        r.font.color.rgb = RGBColor(255, 255, 255)
        shading = parse_xml(f'<w:shd {nsdecls("w")} w:fill="38BDF8"/>')
        hdr_cells_s[j]._tc.get_or_add_tcPr().append(shading)

    for i, row_data in enumerate(step_rows):
        row_cells = step_table.rows[i+1].cells
        for j, val in enumerate(row_data):
            p = row_cells[j].paragraphs[0]
            r = p.add_run(val)
            r.font.name, r.font.size = "Arial", Pt(9.5)

    doc.add_paragraph()

    # Visual Flow Screenshots
    h_seq = doc.add_heading(level=2)
    r_hs2 = h_seq.add_run("Minh Họa Toàn Bộ Chuỗi Hình Ảnh Luồng Tạo Chi Tiêu Mới Từ Bắt Đầu Đến Kết Quả:")
    r_hs2.font.name, r_hs2.font.color.rgb = "Arial", CYAN

    flow_visual_steps = [
        ("Bước 1: Màn hình Trang chủ ban đầu (Kích hoạt bấm phím + FAB)", "D:/android/QuanLyChiTieu/report_screenshots/flow_01_start_home.png", "Người dùng chạm phím nổi (+) FAB màu tím ở trung tâm thanh Bottom Navigation."),
        ("Bước 2: Màn hình Thêm giao dịch (Điền thông tin & chọn danh mục)", "D:/android/QuanLyChiTieu/report_screenshots/flow_02_form_entry.png", "Điền số tiền 150.000 VNĐ, chọn danh mục Ăn uống & Cà phê và chạm 'Lưu giao dịch' ở đáy màn hình."),
        ("Bước 3: Màn hình Lịch sử giao dịch (Xác nhận ghi nhận giao dịch)", "D:/android/QuanLyChiTieu/report_screenshots/flow_03_history_result.png", "Giao dịch Chi tiêu Ăn uống −150.000 VNĐ vừa tạo ngay lập tức xuất hiện nổi bật ở đầu Lịch sử."),
        ("Bước 4: Màn hình Trang chủ tự động nhảy số (Kết quả hoàn tất)", "D:/android/QuanLyChiTieu/report_screenshots/flow_04_home_updated.png", "Số dư hiện tại, Tổng chi tháng và Thẻ danh mục chi tiêu tự động nhảy số và vẽ lại giao diện tức thì.")
    ]

    for title_step, img_p, desc_p in flow_visual_steps:
        p_st = doc.add_paragraph()
        r_st = p_st.add_run(f"• {title_step}:")
        r_st.font.name, r_st.font.bold, r_st.font.size = "Arial", True, Pt(10.5)

        p_sd = doc.add_paragraph()
        r_sd = p_sd.add_run(desc_p)
        r_sd.font.name, r_sd.font.italic, r_sd.font.size = "Arial", True, Pt(9.5)

        if os.path.exists(img_p):
            p_img = doc.add_paragraph()
            p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
            r_img = p_img.add_run()
            r_img.add_picture(img_p, width=Inches(3.2))

        doc.add_paragraph()

    # Section 4: Advanced Cap Guard System
    h4 = doc.add_heading(level=1)
    r_h4 = h4.add_run("4. Chức Năng Nâng Cao: Hệ Thống Bảo Vệ Hạn Mức Ngày & Từng Giao Dịch (Daily & Single Cap Guard System)")
    r_h4.font.name, r_h4.font.color.rgb = "Arial", INDIGO

    p_cap_desc = doc.add_paragraph()
    r_cd = p_cap_desc.add_run(
        "Nhằm giúp người dùng chủ động kiểm soát hành vi chi tiêu ngẫu hứng và tránh bị vung tay quá trớn, "
        "ứng dụng đã tích hợp Bộ máy Kiểm soát Hạn Mức Đa Tầng (Spending Caps Engine) thực thi thời gian thực tại lớp FinanceRepository:\n\n"
        "1. Hạn Mức Tối Đa 1 Giao Dịch (Single-Transaction Cap Guard): Mặc định giới hạn tối đa 2.000.000 VNĐ cho 1 khoản chi. Nếu khoản chi lớn hơn số tiền này, hệ thống sẽ phát tín hiệu cảnh báo gián đoạn.\n"
        "2. Hạn Mức Chi Tiêu Ngày (Daily Cap Guard): Mặc định giới hạn tối đa 1.000.000 VNĐ / ngày. Bộ máy tự động tính [ Tổng chi hôm nay + Khoản chi mới ], nếu vượt trần sẽ cảnh báo mức lạm chi.\n"
        "3. Giao Diện Cấu Hình Hạn Mức Tương Tác: Người dùng bấm '🛡 Cài đặt hạn mức ngày & giao dịch' trên màn hình Ngân sách để mở Modal tùy chỉnh bật/tắt và thay đổi số tiền trần mong muốn.\n"
        "4. Dialog Cảnh Báo Tương Tác: Khi người dùng bấm 'Lưu giao dịch', popup cảnh báo màu đỏ rực xuất hiện với 2 lựa chọn: 'Vẫn Lưu' (chủ động xác nhận chi) hoặc 'Hủy / Sửa Số Tiền' (quay lại cân nhắc)."
    )
    r_cd.font.name, r_cd.font.size = "Arial", Pt(10.5)

    if os.path.exists("D:/android/QuanLyChiTieu/report_screenshots/flow_05_cap_guard_config.png"):
        doc.add_heading(level=2, text="Hình Ảnh Màn Hình Giao Diện Cấu Hình Hạn Mức Bảo Vệ Chi Tiêu (Cap Guard Config Modal):")
        p_img_c = doc.add_paragraph()
        p_img_c.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r_img_c = p_img_c.add_run()
        r_img_c.add_picture("D:/android/QuanLyChiTieu/report_screenshots/flow_05_cap_guard_config.png", width=Inches(3.0))

    doc.add_paragraph()

    # Section 5: Architecture & Sync
    h5 = doc.add_heading(level=1)
    r_h5 = h5.add_run("5. Kiến Trúc Dữ Liệu & Cơ Chế Đồng Bộ Realtime Cloud")
    r_h5.font.name, r_h5.font.color.rgb = "Arial", INDIGO

    p_arch = doc.add_paragraph()
    r_a = p_arch.add_run(
        "Ứng dụng được xây dựng dựa trên nguyên lý Single Source of Truth (FinanceRepository). "
        "Dữ liệu giao dịch, ngân sách, hạn mức bảo vệ và hũ tiết kiệm được quản lý qua kiến trúc 2 lớp đệm kép:\n\n"
        "1. Lớp Đệm Local (SQLite Store): Ghi nhận giao dịch lập tức vào SQLite local store để đảm bảo ứng dụng phản hồi giao diện trong 0.01 giây mà không bị phụ thuộc vào tốc độ mạng.\n"
        "2. Lớp Đồng Bộ Cloud (Firebase Firestore): Tự động phát sóng (Realtime Stream) cập nhật các tài liệu trong bộ sưu tập users/{uid}/transactions, users/{uid}/budgets và users/{uid}/settings/spending_caps. Khi thiết bị có kết nối mạng, mọi thay đổi được đồng bộ tức thì lên Đám mây."
    )
    r_a.font.name, r_a.font.size = "Arial", Pt(10.5)

    # Save Document
    output_filename = "D:/android/QuanLyChiTieu/Bao_Cao_Giao_Dien_Va_Luong_Ung_Dung_Quan_Ly_Chi_Tieu.docx"
    doc.save(output_filename)
    print(f"Report generated successfully with Advanced Cap Guard Section & Modal Image: {output_filename}")

if __name__ == "__main__":
    create_report()
