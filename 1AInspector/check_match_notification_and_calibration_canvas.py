# -*- coding: utf-8 -*-
"""
Checker: check_match_notification_and_calibration_canvas.py
Валидация полноэкранной отрисовки скриншота в CandidatesCanvasView
и сквозной поддержки уведомлений при нахождении шага.
"""
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    calib_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "capture", "CalibrationOverlay.kt")
    action_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "domain", "model", "MacroAction.kt")
    engine_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "engine", "MacroExecutionEngine.kt")
    dialog_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "dialog", "EditActionDialog.kt")

    if not all(os.path.exists(f) for f in [calib_file, action_file, engine_file, dialog_file]):
        return False, "Файлы компонентов калибровки или модели не найдены"

    with open(calib_file, "r", encoding="utf-8") as f:
        c_code = f.read()
    with open(action_file, "r", encoding="utf-8") as f:
        a_code = f.read()
    with open(engine_file, "r", encoding="utf-8") as f:
        e_code = f.read()
    with open(dialog_file, "r", encoding="utf-8") as f:
        d_code = f.read()

    if "canvas.drawBitmap(screenshot" not in c_code:
        return False, "CandidatesCanvasView.onDraw обязан отрисовывать screenshot на весь экран"

    if "val notifyOnMatch: Boolean" not in a_code:
        return False, "MacroAction обязан содержать поле notifyOnMatch"

    if "action.notifyOnMatch" not in e_code:
        return False, "MacroExecutionEngine обязан обрабатывать action.notifyOnMatch"

    if "isNotifyOnMatch" not in d_code:
        return False, "EditActionDialog обязан содержать тумблер isNotifyOnMatch"

    return True, "Полноэкранная отрисовка калибровки и система уведомлений о нахождении шага верифицированы"

if __name__ == "__main__":
    passed, msg = run_check(os.getcwd())
    print(f"[{'PASS' if passed else 'FAIL'}] {msg}")
