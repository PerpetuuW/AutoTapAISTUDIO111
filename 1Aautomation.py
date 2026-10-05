#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1Aautomation.py — Автоматический фасад управления развертыванием AutoTap (v17.0)
Автоматическое исполнение без блокирующих пауз и чистое закрытие процессов.
"""

import os
import sys
import subprocess

ROOT_DIR = os.path.abspath(os.path.dirname(__file__))

def main():
    target = sys.argv[1] if len(sys.argv) > 1 else "build"

    if target in ("build", "all", "1"):
        b_path = os.path.join(ROOT_DIR, "1ABuild.py")
        res = subprocess.run([sys.executable, b_path], cwd=ROOT_DIR)
        sys.exit(res.returncode)
    elif target in ("inspect", "check", "2"):
        i_path = os.path.join(ROOT_DIR, "1AInspector.py")
        res = subprocess.run([sys.executable, i_path], cwd=ROOT_DIR)
        sys.exit(res.returncode)
    elif target in ("deploy", "adb", "3"):
        d_path = os.path.join(ROOT_DIR, "1AAutomation", "adb_deploy.py")
        res = subprocess.run([sys.executable, d_path], cwd=ROOT_DIR)
        sys.exit(res.returncode)
    else:
        sys.exit(0)

if __name__ == "__main__":
    main()
