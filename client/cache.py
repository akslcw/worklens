import json
import os
import datetime

CACHE_FILE = "upload_cache.json"

def save_cache(payload):
    cache = load_cache()
    payload['cached_at'] = datetime.datetime.now().isoformat()
    cache.append(payload)
    _atomic_write(cache)

def load_cache():
    if not os.path.exists(CACHE_FILE):
        return []
    try:
        with open(CACHE_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception:
        return []

def remove_item(index):
    cache = load_cache()
    if 0 <= index < len(cache):
        cache.pop(index)
        _atomic_write(cache)

def clear_cache():
    if os.path.exists(CACHE_FILE):
        os.remove(CACHE_FILE)

def _atomic_write(data):
    tmp_file = CACHE_FILE + '.tmp'
    with open(tmp_file, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    os.replace(tmp_file, CACHE_FILE)