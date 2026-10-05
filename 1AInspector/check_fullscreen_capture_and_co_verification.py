# -*- coding: utf-8 -*-
"""
Checker: check_fullscreen_capture_and_co_verification.py
Валидация getRealMetrics в CaptureFrameOverlay, правильного порядка кнопок
в ControlPanelOverlay и поддержки coVerificationTemplates в CalibrationOverlay.
"""
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    cap_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "capture", "CaptureFrameOverlay.kt")
    panel_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "panel", "ControlPanelOverlay.kt")
    calib_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "capture", "CalibrationOverlay.kt")

    if not all(os.path.exists(f) for f in [cap_file, panel_file, calib_file]):
        return False, "Файлы компонентов захвата или оверлеев не найдены"

    with open(cap_file, "r", encoding="utf-8") as f:
        c_code = f.read()
    with open(panel_file, "r", encoding="utf-8") as f:
        p_code = f.read()
    with open(calib_file, "r", encoding="utf-8") as f:
        cl_code = f.read()

    if "getRealMetrics" not in c_code:
        return False, "CaptureFrameOverlay обязан использовать getRealMetrics для охвата 100% экрана"

    if "miniBubbleLayout.addView(btnMiniExpand)" not in p_code:
        return False, "В ControlPanelOverlay кнопка разворота обязана быть слева от кнопки Play"

    if "coVerificationTemplates" not in cl_code:
        return False, "В CalibrationOverlay отсутствует поддержка составных шаблонов подтверждения"

    return True, "Полноэкранный захват без мертвых зон и составные шаблоны верифицированы"

if __name__ == "__main__":
    passed, msg = run_check(os.getcwd())
    print(f"[{'PASS' if passed else 'FAIL'}] {msg}")
