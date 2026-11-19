import os
import time
import subprocess

def capture(path):
    print(f"Capturing screenshot to {path}...")
    with open(path, "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f)

def tap(x, y):
    subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)])

os.makedirs("D:/android/QuanLyChiTieu/report_screenshots", exist_ok=True)

# 1. Budget Screen Row
subprocess.run(["adb", "shell", "am", "force-stop", "com.nhom9.quanlychitieu"])
time.sleep(1)
subprocess.run(["adb", "shell", "am", "start", "-n", "com.nhom9.quanlychitieu/.ui.budget.BudgetActivity"])
time.sleep(2)
capture("D:/android/QuanLyChiTieu/report_screenshots/cap_01_budget_row.png")

# 2. Config Dialog Modal
tap(540, 1026) # Open dialog
time.sleep(1.5)
capture("D:/android/QuanLyChiTieu/report_screenshots/cap_02_dialog.png")

# Close dialog
tap(300, 1790)
time.sleep(1)

# 3. Add Transaction Form and trigger Warning Popup
subprocess.run(["adb", "shell", "am", "start", "-n", "com.nhom9.quanlychitieu/.ui.transaction.AddTransactionActivity"])
time.sleep(2)

# Tap btnQuick100k (x=739, y=919) 26 times = 2.600.000 VNĐ
for _ in range(26):
    tap(739, 919)
    time.sleep(0.02)

time.sleep(0.5)

# Tap Save Transaction button (x=540, y=2255)
tap(540, 2255)
time.sleep(1.5)

capture("D:/android/QuanLyChiTieu/report_screenshots/cap_03_warning_popup.png")

print("Cap guard feature screenshots captured successfully!")
