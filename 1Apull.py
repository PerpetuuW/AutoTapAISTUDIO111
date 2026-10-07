# -*- coding: utf-8 -*-
"""
1Apull.py
ФОНОВЫЙ АВТО-СИНХРОНИЗАТОР (AUTO-PULL WATCHER)
Запускается на локальной машине разработчика:
  python 1Apull.py

Каждые 5 секунд проверяет появление новых коммитов в ветке 1Gemini на GitHub.
При обнаружении обновлений автоматически выполняет git pull, обновляя проект в Android Studio без необходимости делать pull вручную.
"""

import os
import sys
import time
import subprocess

POLL_INTERVAL_SECONDS = 5
BRANCH = "1Gemini"

def get_remote_head(root_dir: str) -> str:
    try:
        subprocess.run(["git", "fetch", "origin", BRANCH], cwd=root_dir, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        res = subprocess.run(["git", "rev-parse", f"origin/{BRANCH}"], cwd=root_dir, check=True, capture_output=True, text=True)
        return res.stdout.strip()
    except Exception:
        return ""

def get_local_head(root_dir: str) -> str:
    try:
        res = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root_dir, check=True, capture_output=True, text=True)
        return res.stdout.strip()
    except Exception:
        return ""

def main():
    root_dir = os.path.abspath(os.getcwd())
    print("=" * 70)
    print(f"[AutoTap Auto-Pull Daemon] Запущен для ветки: {BRANCH}")
    print(f"Директория: {root_dir}")
    print(f"Интервал проверки: {POLL_INTERVAL_SECONDS} сек. Для остановки нажмите Ctrl+C")
    print("=" * 70)

    while True:
        try:
            local_hash = get_local_head(root_dir)
            remote_hash = get_remote_head(root_dir)

            if remote_hash and local_hash and local_hash != remote_hash:
                print(f"\n[⚡ ОБНАРУЖЕНО ОБНОВЛЕНИЕ ОТ АГЕНТА]: {local_hash[:7]} -> {remote_hash[:7]}")
                pull_res = subprocess.run(["git", "pull", "origin", BRANCH], cwd=root_dir, capture_output=True, text=True)
                if pull_res.returncode == 0:
                    print(f"[✅ УСПЕШНО ОБНОВЛЕНО]:\n{pull_res.stdout.strip()}")
                else:
                    print(f"[⚠️ ОШИБКА ПРИ PULL]:\n{pull_res.stderr.strip()}")
            time.sleep(POLL_INTERVAL_SECONDS)
        except KeyboardInterrupt:
            print("\n[Auto-Pull Daemon] Остановлен пользователем.")
            break
        except Exception as e:
            time.sleep(POLL_INTERVAL_SECONDS)

if __name__ == "__main__":
    main()
