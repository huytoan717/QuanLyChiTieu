import time
import subprocess

def tap(x, y):
    subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)])

subprocess.run(["adb", "shell", "am", "force-stop", "com.nhom9.quanlychitieu"])
time.sleep(1)
subprocess.run(["adb", "shell", "am", "start", "-n", "com.nhom9.quanlychitieu/.ui.transaction.AddTransactionActivity"])
time.sleep(2)

# 1. Tap btnTypeExpense (x=213, y=402)
print("Selecting Chi tiêu tab...")
tap(213, 402)
time.sleep(1)

# 2. Tap btnQuick100k (x=739, y=919) 26 times to reach 2.600.000 VNĐ
print("Tapping +100k quick chip 26 times to reach 2.600.000 VNĐ...")
for _ in range(26):
    tap(739, 919)
    time.sleep(0.05)

time.sleep(1)

# 3. Tap Save Transaction button (x=540, y=2255)
print("Tapping Save Transaction button...")
tap(540, 2255)
time.sleep(1.5)

print("Cap guard test execution complete!")
