import time
import datetime
import socket
import uuid
import requests
import win32gui
import win32process
import psutil
from collections import defaultdict
from config import SERVER_URL, EMPLOYEE_ID, RECORD_INTERVAL, UPLOAD_HOUR, UPLOAD_MINUTE


def get_mac_address():
    mac = uuid.UUID(int=uuid.getnode()).hex[-12:]
    return ':'.join(mac[i:i+2] for i in range(0, 12, 2)).upper()


def get_active_window():
    try:
        hwnd = win32gui.GetForegroundWindow()
        title = win32gui.GetWindowText(hwnd)
        _, pid = win32process.GetWindowThreadProcessId(hwnd)
        process = psutil.Process(pid)
        app_name = process.name()
        return app_name, title
    except Exception:
        return None, None


def upload_data(records):
    today = datetime.date.today().strftime("%Y-%m-%d")
    items = [
        {
            "appName": app,
            "windowTitle": title,
            "durationSeconds": duration,
            "recordDate": today
        }
        for (app, title), duration in records.items()
        if duration >= 10
    ]
    payload = {
        "macAddress": get_mac_address(),
        "deviceName": socket.gethostname(),
        "employeeId": EMPLOYEE_ID,
        "records": items
    }
    try:
        response = requests.post(
            f"{SERVER_URL}/api/records/upload",
            json=payload,
            timeout=10
        )
        if response.status_code == 200:
            print(f"[{datetime.datetime.now()}] 上报成功，共 {len(items)} 条记录")
        else:
            print(f"[{datetime.datetime.now()}] 上报失败: {response.status_code}")
    except Exception as e:
        print(f"[{datetime.datetime.now()}] 上报异常: {e}")

def main():
    print(f"[{datetime.datetime.now()}] WorkLens 客户端启动")
    records = defaultdict(int)
    uploaded_today = False

    while True:
        app_name, title = get_active_window()
        if app_name and title:
            records[(app_name, title)] += RECORD_INTERVAL

        now = datetime.datetime.now()

        # 每天到达上报时间自动上报
        if now.hour == UPLOAD_HOUR and now.minute == UPLOAD_MINUTE and not uploaded_today:
            upload_data(records)
            records.clear()
            uploaded_today = True

        # 跨天重置
        if now.hour == 0 and now.minute == 1:
            uploaded_today = False

        time.sleep(RECORD_INTERVAL)


if __name__ == "__main__":
    main()