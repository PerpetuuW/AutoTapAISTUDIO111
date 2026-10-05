# -*- coding: utf-8 -*-
"""
Checker: check_recalibration_raw_preview_and_pipeline.py
Валидация наличия карточки 'СЫРОЙ ЭТАЛОН' в CalibrationOverlay и надежного
запуска перекалибровки в AutoTapOrchestrator с FQCN типами.
"""
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    calib_file = os.path.join(
        root_dir, "app", "src", "main", "java", "com", "example", "autotap",
        "infrastructure", "overlay", "capture", "CalibrationOverlay.kt"
    )
    orch_file = os.path.join(
        root_dir, "app", "src", "main", "java", "com", "example", "autotap",
        "infrastructure", "orchestrator", "AutoTapOrchestrator.kt"
    )

    if not os.path.exists(calib_file):
        return False, "CalibrationOverlay.kt не найден"
    if not os.path.exists(orch_file):
        return False, "AutoTapOrchestrator.kt не найден"

    with open(calib_file, "r", encoding="utf-8") as f:
        c_code = f.read()

    if "previewRawView" not in c_code or "СЫРОЙ ЭТАЛОН" not in c_code:
        return False, "В CalibrationOverlay.kt отсутствует превью оригинального сырого кадра (СЫРОЙ ЭТАЛОН)"

    with open(orch_file, "r", encoding="utf-8") as f:
        o_code = f.read()

    if "android.graphics.Canvas" not in o_code and "Canvas(" in o_code:
        return False, "AutoTapOrchestrator.kt обязан использовать FQCN android.graphics.Canvas"

    return True, "Пайплайн перекалибровки сырого кадра и FQCN типы полностью валидны"

if __name__ == "__main__":
    passed, msg = run_check(os.getcwd())
    print(f"[{'PASS' if passed else 'FAIL'}] {msg}")
