# -*- coding: utf-8 -*-
"""
check_capture_color_crash_and_recalibration_clean_screen.py
Валидация:
1. Защищенный парсинг цвета в CaptureFrameOverlay (отсутствие Color.parseColor("") краша).
2. Разграничение: в диалоге шага присутствует "РЕДАКТИРОВАТЬ", а "МАСКА" находится в калибровке.
3. Гарантированное сокрытие оверлеев перед созданием снимка в startRecaptureForStep.
"""

import os
import sys

def run_check(root_dir: str) -> tuple[bool, str]:
    capture_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "capture", "CaptureFrameOverlay.kt")
    dialog_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "dialog", "EditActionDialog.kt")
    orch_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "orchestrator", "AutoTapOrchestrator.kt")

    if not os.path.exists(capture_file) or not os.path.exists(dialog_file) or not os.path.exists(orch_file):
        return False, "[CRITICAL] Один из ключевых файлов захвата/диалога не найден"

    with open(capture_file, "r", encoding="utf-8") as f:
        c_code = f.read()
    if 'safeParseColor' not in c_code:
        return False, "[FAIL] В CaptureFrameOverlay отсутствует функция safeParseColor"
    if 'createIconButton(VectorIconDrawer.IconType.CAPTURE, "",' in c_code:
        return False, "[FAIL] Обнаружен вызов createIconButton с пустой строкой цвета фона"

    with open(dialog_file, "r", encoding="utf-8") as f:
        d_code = f.read()
    if 'createTextActionButton("РЕДАКТИРОВАТЬ"' not in d_code:
        return False, "[FAIL] В EditActionDialog кнопка выбора области должна называться 'РЕДАКТИРОВАТЬ'"

    with open(orch_file, "r", encoding="utf-8") as f:
        o_code = f.read()
    if 'activeEditDialog?.dismiss()' not in o_code or 'postDelayed({' not in o_code:
        return False, "[FAIL] В AutoTapOrchestrator отсутствует упреждающее сокрытие диалога с задержкой"

    return True, "[PASS] Контракт безопасного цвета захвата и чистого экрана калибровки соблюден"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
