import time
import subprocess

def tap(x, y):
    subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)])

# 1. Open BudgetActivity
subprocess.run(["adb", "shell", "am", "force-stop", "com.nhom9.quanlychitieu"])
time.sleep(1)
subprocess.run(["adb", "shell", "am", "start", "-n", "com.nhom9.quanlychitieu/.ui.budget.BudgetActivity"])
time.sleep(2)

# 2. Tap btnConfigSpendingCaps (x=540, y=1026) to open dialog
tap(540, 1026)
time.sleep(1.5)

# 3. Focus edtSingleMaxCap (x=500, y=940 in dialog) and type 500000
tap(500, 940)
time.sleep(0.5)
subprocess.run(["adb", "shell", "input", "keyevent", "67", "67", "67", "67", "67", "67", "67", "67", "67"]) # Backspaces
subprocess.run(["adb", "shell", "input", "text", "500000"])
time.sleep(0.5)

# 4. Tap Save Caps button (x=700, y=1480)
tap(700, 1480)
time.sleep(1.5)

# 5. Open AddTransactionActivity
subprocess.run(["adb", "shell", "am", "start", "-n", "com.nhom9.quanlychitieu/.ui.transaction.AddTransactionActivity"])
time.sleep(2)

# Tap btnQuick100k (x=739, y=919) 6 times = 600.000 VNĐ
print("Tapping +100k quick chip 6 times...")
for _ in range(6):
    tap(739, 919)
    time.sleep(0.1)

time.sleep(1)

# Tap Save Transaction button (x=540, y=2255)
print("Tapping Save Transaction button...")
tap(540, 2255)
time.sleep(1.5)

print("Dynamic Spending Caps test completed!")
