# -*- coding: utf-8 -*-
"""
check_capture_frame_roi_singularity.py
Валидация:
1. Ровно одно объявление designatedRoi в CaptureFrameOverlay.kt.
2. Отсутствие кракозябр кодировки в тексте Toast.
"""

import os
import sys

def run_check(root_dir: str) -> tuple[bool, str]:
    capture_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "capture", "CaptureFrameOverlay.kt")
    if not os.path.exists(capture_file):
        return False, "[CRITICAL] CaptureFrameOverlay.kt не найден"

    with open(capture_file, "r", encoding="utf-8") as f:
        content = f.read()

    cnt = content.count("designatedRoi: Rect?")
    if cnt != 1:
        return False, f"[FAIL] Обнаружено {cnt} объявлений designatedRoi (должно быть ровно 1)"

    if "╨" in content or "╤" in content:
        return False, "[FAIL] В CaptureFrameOverlay.kt обнаружены артефакты поврежденной кодировки"

    return True, "[PASS] Сингулярность designatedRoi и чистота кодировки соблюдены"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
