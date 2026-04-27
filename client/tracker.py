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

    # 先尝试发送缓存数据
    retry_cache()

    # 发送当前数据
    _do_upload(payload)


def _do_upload(payload):
    """实际发送数据，失败则缓存"""
    from cache import save_cache
    try:
        response = requests.post(
            f"{SERVER_URL}/api/records/upload",
            json=payload,
            timeout=10
        )
        if response.status_code == 200:
            print(f"[{datetime.datetime.now()}] 上报成功，共 {len(payload['records'])} 条记录")
        else:
            print(f"[{datetime.datetime.now()}] 上报失败: {response.status_code}，已缓存")
            save_cache(payload)
    except Exception as e:
        print(f"[{datetime.datetime.now()}] 网络异常: {e}，已缓存")
        save_cache(payload)


def retry_cache():
    """重试发送缓存数据"""
    from cache import load_cache, clear_cache
    cache = load_cache()
    if not cache:
        return
    print(f"[{datetime.datetime.now()}] 发现 {len(cache)} 条缓存数据，尝试重新上报...")
    success_all = True
    for payload in cache:
        payload.pop('cached_at', None)
        try:
            response = requests.post(
                f"{SERVER_URL}/api/records/upload",
                json=payload,
                timeout=10
            )
            if response.status_code != 200:
                success_all = False
        except Exception:
            success_all = False
            break
    if success_all:
        clear_cache()
        print(f"[{datetime.datetime.now()}] 缓存数据上报成功，已清空缓存")
    else:
        print(f"[{datetime.datetime.now()}] 缓存数据上报失败，下次再试")


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