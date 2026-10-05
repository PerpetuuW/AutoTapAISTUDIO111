# -*- coding: utf-8 -*-
"""
1AAutomation/git_sync.py
Управление синхронизацией Git и откатом рабочих файлов.
"""

import subprocess
import os

def rollback_files(files: list[str], root_dir: str):
    if not files:
        return
    try:
        subprocess.run(["git", "restore"] + files, cwd=root_dir, check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception:
        try:
            subprocess.run(["git", "checkout", "--"] + files, cwd=root_dir, check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        except Exception:
            pass

def execute_auto_git_sync(root_dir: str, commit_message: str) -> bool:
    try:
        subprocess.run(["git", "add", "-A"], cwd=root_dir, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        diff_check = subprocess.run(["git", "diff", "--staged", "--quiet"], cwd=root_dir)
        if diff_check.returncode == 0:
            return True
        subprocess.run(["git", "commit", "-m", commit_message], cwd=root_dir, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        subprocess.run(["git", "push"], cwd=root_dir, check=False, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        return True
    except Exception as e:
        print(f"[GitSync Warning]: {e}")
        return False
