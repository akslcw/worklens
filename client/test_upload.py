from collections import defaultdict
from tracker import upload_data

records = defaultdict(int)
records[('idea64.exe', 'WorkLens - IntelliJ IDEA')] = 3600
records[('chrome.exe', 'GitHub - worklens')] = 1800
records[('WeChat.exe', '微信')] = 600

upload_data(records)