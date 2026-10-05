# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str):
    p_orch = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "orchestrator", "AutoTapOrchestrator.kt")
    p_target = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "target", "TargetOverlayManager.kt")
    p_rec = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "record", "GestureRecorderOverlay.kt")
    p_disp = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "gesture", "GestureDispatcher.kt")

    for p in [p_orch, p_target, p_rec, p_disp]:
        if not os.path.exists(p):
            return False, f"[CRITICAL] Файл не найден: {p}"

    with open(p_orch, "r", encoding="utf-8") as f:
        orch_src = f.read()
    if "ActionType.PATH, ActionType.OCR" not in orch_src:
        return False, "[FAIL] AutoTapOrchestrator не открывает showEditDialog для ActionType.PATH"

    with open(p_target, "r", encoding="utf-8") as f:
        target_src = f.read()
    if "spawnWaypointViews(act)" not in target_src:
        return False, "[FAIL] TargetOverlayManager не спавнит точки пути в syncAllViews"

    with open(p_rec, "r", encoding="utf-8") as f:
        rec_src = f.read()
    if "wasStreaming" not in rec_src:
        return False, "[FAIL] В GestureRecorderOverlay отсутствует флаг wasStreaming для защиты от двойного жеста на UP"

    with open(p_disp, "r", encoding="utf-8") as f:
        disp_src = f.read()
    if "continueStroke" not in disp_src:
        return False, "[FAIL] GestureDispatcher не использует continueStroke для непрерывного стриминга"

    return True, "[PASS] Архитектурные контракты записи и редактирования путей полностью соблюдены"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
