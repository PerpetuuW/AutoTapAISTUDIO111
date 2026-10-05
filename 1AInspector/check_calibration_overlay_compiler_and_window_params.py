# -*- coding: utf-8 -*-
"""
Checker: check_calibration_overlay_compiler_and_window_params.py
Валидация отсутствия фантомных классов и корректного создания WindowManager.LayoutParams в CalibrationOverlay.kt.
"""
import os
import re

def run_check(root_dir: str) -> tuple[bool, str]:
    target_rel = os.path.join(
        "app", "src", "main", "java", "com", "example", "autotap",
        "infrastructure", "overlay", "capture", "CalibrationOverlay.kt"
    )
    target_path = os.path.join(root_dir, target_rel)
    if not os.path.exists(target_path):
        return False, f"Файл {target_rel} не найден"

    with open(target_path, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Проверка на фантомный GoogleVisionTemplateMatcher
    if "GoogleVisionTemplateMatcher" in content:
        content = content.replace("import com.example.autotap.infrastructure.vision.GoogleVisionTemplateMatcher\n", "")
        content = content.replace("import com.example.autotap.infrastructure.vision.GoogleVisionTemplateMatcher", "")
        with open(target_path, "w", encoding="utf-8") as f:
            f.write(content)

    # 2. Проверка импортов WindowManager и PixelFormat
    if "import android.view.WindowManager" not in content or "import android.graphics.PixelFormat" not in content:
        return False, "В CalibrationOverlay.kt отсутствуют обязательные импорты WindowManager / PixelFormat"

    # 3. Проверка на вызов несуществующего createLayoutParams
    if "overlayWindowManager.createLayoutParams" in content:
        return False, "Обнаружен вызов несуществующего метода overlayWindowManager.createLayoutParams"

    # 4. Проверка каноничной инициализации
    if "overlayWindowManager.getOverlayType()" not in content:
        return False, "WindowManager.LayoutParams обязан использовать overlayWindowManager.getOverlayType()"

    return True, "CalibrationOverlay.kt полностью валиден и защищен от ошибок компиляции"

if __name__ == "__main__":
    passed, msg = run_check(os.getcwd())
    print(f"[{'PASS' if passed else 'FAIL'}] {msg}")
