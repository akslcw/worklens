import tkinter as tk
import datetime
import threading
import requests
import ttkbootstrap as ttk
from ttkbootstrap.constants import *
from collections import defaultdict
from tracker import get_active_window, get_mac_address, upload_data
from config import SERVER_URL, EMPLOYEE_ID, RECORD_INTERVAL

class WorkLensApp:
    def __init__(self, root):
        self.root = root
        self.root.title("WorkLens - 工作统计助手")
        self.root.geometry("600x500")
        self.root.resizable(False, False)

        self.records = defaultdict(int)
        self.running = True

        self._build_ui()
        self._start_tracking()

    def _build_ui(self):
        # 顶部标题
        header = ttk.Frame(self.root, bootstyle="primary")
        header.pack(fill=X)
        ttk.Label(
            header,
            text="WorkLens 工作统计助手",
            font=("微软雅黑", 16, "bold"),
            bootstyle="inverse-primary",
            padding=16
        ).pack(side=LEFT)

        # 日期
        self.date_label = ttk.Label(
            header,
            text=datetime.date.today().strftime("%Y年%m月%d日"),
            font=("微软雅黑", 11),
            bootstyle="inverse-primary",
            padding=16
        )
        self.date_label.pack(side=RIGHT)

        # 统计卡片区
        card_frame = ttk.Frame(self.root, padding=16)
        card_frame.pack(fill=X)

        # 今日使用时长
        total_card = ttk.LabelFrame(card_frame, text="今日总使用时长", padding=12, bootstyle="primary")
        total_card.pack(side=LEFT, expand=True, fill=BOTH, padx=8)
        self.total_label = ttk.Label(total_card, text="0 小时 0 分钟", font=("微软雅黑", 18, "bold"), bootstyle="primary")
        self.total_label.pack()

        # 当前应用
        current_card = ttk.LabelFrame(card_frame, text="当前使用应用", padding=12, bootstyle="info")
        current_card.pack(side=LEFT, expand=True, fill=BOTH, padx=8)
        self.current_label = ttk.Label(current_card, text="—", font=("微软雅黑", 14), bootstyle="info")
        self.current_label.pack()

        # 应用列表
        list_frame = ttk.LabelFrame(self.root, text="应用使用明细", padding=12)
        list_frame.pack(fill=BOTH, expand=True, padx=16, pady=8)

        columns = ("app", "duration")
        self.tree = ttk.Treeview(list_frame, columns=columns, show="headings", height=12)
        self.tree.heading("app", text="应用名称")
        self.tree.heading("duration", text="使用时长")
        self.tree.column("app", width=350)
        self.tree.column("duration", width=150, anchor=CENTER)
        self.tree.pack(fill=BOTH, expand=True)

        # 底部按钮
        btn_frame = ttk.Frame(self.root, padding=12)
        btn_frame.pack(fill=X)
        ttk.Button(
            btn_frame,
            text="立即同步数据",
            bootstyle="success",
            command=self._manual_upload
        ).pack(side=RIGHT, padx=8)

    def _start_tracking(self):
        thread = threading.Thread(target=self._track_loop, daemon=True)
        thread.start()
        self._update_ui()

    def _track_loop(self):
        while self.running:
            app_name, title = get_active_window()
            if app_name and title:
                self.records[(app_name, title)] += RECORD_INTERVAL
            import time
            time.sleep(RECORD_INTERVAL)

    def _update_ui(self):
        # 更新当前应用
        app_name, title = get_active_window()
        if app_name:
            self.current_label.config(text=f"{app_name}")

        # 更新总时长
        total = sum(self.records.values())
        h = total // 3600
        m = (total % 3600) // 60
        self.total_label.config(text=f"{h} 小时 {m} 分钟")

        # 更新应用列表
        self.tree.delete(*self.tree.get_children())
        sorted_records = sorted(self.records.items(), key=lambda x: x[1], reverse=True)
        for (app, title), duration in sorted_records[:20]:
            h = duration // 3600
            m = (duration % 3600) // 60
            s = duration % 60
            time_str = f"{h}h {m}m {s}s" if h > 0 else f"{m}m {s}s"
            self.tree.insert("", END, values=(f"{app}  {title[:30]}", time_str))

        self.root.after(5000, self._update_ui)

    def _manual_upload(self):
        def do_upload():
            upload_data(self.records)
            ttk.dialogs.Messagebox.show_info("同步成功", "数据已成功同步到服务器")
        threading.Thread(target=do_upload, daemon=True).start()


if __name__ == "__main__":
    root = ttk.Window(themename="flatly")
    app = WorkLensApp(root)
    root.mainloop()