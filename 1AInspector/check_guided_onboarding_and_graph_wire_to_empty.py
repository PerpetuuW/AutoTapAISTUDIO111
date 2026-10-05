# -*- coding: utf-8 -*-
"""
Checker: check_guided_onboarding_and_graph_wire_to_empty.py
Валидация закрытия activeEditDialog, двухкнопочного мини-бабла,
Guided Onboarding при пустом сценарии и механизма Wire-to-Empty в графе.
"""
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    orch_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "orchestrator", "AutoTapOrchestrator.kt")
    panel_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "panel", "ControlPanelOverlay.kt")
    canvas_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "graph", "GraphCanvasView.kt")
    editor_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "graph", "GraphEditorOverlay.kt")
    calib_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "capture", "CalibrationOverlay.kt")

    if not all(os.path.exists(f) for f in [orch_file, panel_file, canvas_file, editor_file, calib_file]):
        return False, "Файлы модулей интерфейса не найдены"

    with open(orch_file, "r", encoding="utf-8") as f:
        o_code = f.read()
    with open(panel_file, "r", encoding="utf-8") as f:
        p_code = f.read()
    with open(canvas_file, "r", encoding="utf-8") as f:
        c_code = f.read()
    with open(editor_file, "r", encoding="utf-8") as f:
        e_code = f.read()
    with open(calib_file, "r", encoding="utf-8") as f:
        cl_code = f.read()

    if "activeEditDialog = null" not in o_code:
        return False, "В AutoTapOrchestrator.kt отсутствует сброс activeEditDialog перед калибровкой"

    if "MINI_PLAY_BTN" not in p_code:
        return False, "В ControlPanelOverlay.kt отсутствует двухкнопочный мини-бабл с кнопкой Play"

    if "onWireToEmptySpace" not in c_code:
        return False, "В GraphCanvasView.kt отсутствует обработка протягивания кабеля в пустоту (Wire-to-Empty)"

    if "showWireToEmptyMenu" not in e_code:
        return False, "В GraphEditorOverlay.kt отсутствует контекстное меню Wire-to-Empty"

    if "roiBar" not in cl_code:
        return False, "В CalibrationOverlay.kt кнопки ROI обязаны быть вынесены в отдельную панель roiBar"

    if "InteractiveTutorialOverlay" not in o_code or "actions.isEmpty()" not in o_code:
        return False, "В AutoTapOrchestrator.kt отсутствует Guided Onboarding туториал при пустом списке шагов"

    return True, "Изоляция окон, двухкнопочный мини-бабл, Guided Onboarding и нодовый редактор Wire-to-Empty верифицированы"

if __name__ == "__main__":
    passed, msg = run_check(os.getcwd())
    print(f"[{'PASS' if passed else 'FAIL'}] {msg}")
