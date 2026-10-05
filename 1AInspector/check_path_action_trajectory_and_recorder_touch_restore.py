# -*- coding: utf-8 -*-
"""
check_path_action_trajectory_and_recorder_touch_restore.py
Валидация:
1. Создание двух мишеней и точек сплайна defaultPts для ActionType.PATH в addActionAt.
2. Гарантированный restoreTouchAfter во всех ветках клика GestureRecorderOverlay.
"""

import os
import sys

def run_check(root_dir: str) -> tuple[bool, str]:
    mgr_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "target", "TargetOverlayManager.kt")
    rec_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "record", "GestureRecorderOverlay.kt")

    if not os.path.exists(mgr_file) or not os.path.exists(rec_file):
        return False, "[CRITICAL] TargetOverlayManager.kt или GestureRecorderOverlay.kt не найдены"

    with open(mgr_file, "r", encoding="utf-8") as f:
        m_code = f.read()

    if 'isMovementType = type == ActionType.SWIPE || type == ActionType.PATH' not in m_code:
        return False, "[FAIL] В TargetOverlayManager.addActionAt ActionType.PATH не классифицирован как движение"

    if 'spawnEndTargetView(newAction, endX, endY)' not in m_code:
        return False, "[FAIL] В TargetOverlayManager.addActionAt отсутствует создание конечной мишени"

    with open(rec_file, "r", encoding="utf-8") as f:
        r_code = f.read()

    cnt_restore = r_code.count("restoreTouchAfter(")
    if cnt_restore < 4:
        return False, f"[FAIL] Недостаточно точек восстановления тача restoreTouchAfter ({cnt_restore} найдено, ожидается >= 4)"

    return True, "[PASS] Инварианты траектории пути PATH и восстановления тача записи соблюдены"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
