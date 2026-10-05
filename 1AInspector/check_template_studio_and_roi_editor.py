# -*- coding: utf-8 -*-
"""
check_template_studio_and_roi_editor.py
Архитектурный инвариант:
1. Кнопка "МАСКА" (ручная доработка маски) находится в CalibrationOverlay.kt.
2. В EditActionDialog.kt для перезахвата кадра размещена кнопка "РЕДАКТИРОВАТЬ".
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    calib_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")
    dialog_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")

    if not os.path.exists(calib_file) or not os.path.exists(dialog_file):
        return False, "[CRITICAL] CalibrationOverlay.kt или EditActionDialog.kt не найдены"

    with open(calib_file, "r", encoding="utf-8") as f:
        calib_content = f.read()
    if 'text = "МАСКА"' not in calib_content or 'MagicWandEditorOverlay' not in calib_content:
        return False, "[FAIL] В CalibrationOverlay.kt должна присутствовать кнопка 'МАСКА' для вызова MagicWandEditorOverlay"

    with open(dialog_file, "r", encoding="utf-8") as f:
        dialog_content = f.read()
    if 'createTextActionButton("РЕДАКТИРОВАТЬ"' not in dialog_content:
        return False, "[FAIL] В EditActionDialog.kt кнопка обязана называться 'РЕДАКТИРОВАТЬ'"

    return True, "[PASS] Кнопка 'МАСКА' локализована в калибровке, а в меню шага размещен 'РЕДАКТИРОВАТЬ'"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    import sys
    sys.exit(0 if passed else 1)
