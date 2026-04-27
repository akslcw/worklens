import json
import os
import datetime

CACHE_FILE = "upload_cache.json"

def save_cache(payload):
    """保存失败的上报数据到本地缓存"""
    cache = load_cache()
    payload['cached_at'] = datetime.datetime.now().isoformat()
    cache.append(payload)
    with open(CACHE_FILE, 'w', encoding='utf-8') as f:
        json.dump(cache, f, ensure_ascii=False, indent=2)

def load_cache():
    """读取本地缓存"""
    if not os.path.exists(CACHE_FILE):
        return []
    with open(CACHE_FILE, 'r', encoding='utf-8') as f:
        return json.load(f)

def clear_cache():
    """清空缓存"""
    if os.path.exists(CACHE_FILE):
        os.remove(CACHE_FILE)