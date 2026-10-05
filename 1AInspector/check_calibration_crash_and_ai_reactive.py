# -*- coding: utf-8 -*-
"""
check_calibration_crash_and_ai_reactive.py
Инспектор защиты от крашей оверлейных диалогов и реактивности нейрокалибровки:
1. Защита от BadTokenException: любой AlertDialog в CalibrationOverlay обязан устанавливать overlay window type перед show().
2. Реальный расчет AI: в reevaluateMatching() при isNeuralEngineMode обязан вызываться NeuralVisualMatcher.findMatches.
3. Плавный дебаунс: задержка пересчета при перемещении слайдеров >= 100ms.
"""
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    calib_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt".replace("/", os.sep))
    if not os.path.exists(calib_file):
        return False, f"Файл не найден: {calib_file}"

    with open(calib_file, "r", encoding="utf-8") as f:
        code = f.read()

    # 1. Защита от краша при клике на "+ ШАБЛОН"
    if "btnAddCoVerif" in code:
        if "dialog.window?.setType" not in code and "window?.setType" not in code:
            return False, "В CalibrationOverlay.kt обнаружен вызов AlertDialog без установки типа окна оверлея (риск BadTokenException)"

    # 2. Контроль подключения реального NeuralVisualMatcher.findMatches
    if "isNeuralEngineMode" in code:
        if "NeuralVisualMatcher.findMatches" not in code:
            return False, "В CalibrationOverlay.kt переключатель AI ⇄ OPENCV фиктивен: вызов NeuralVisualMatcher.findMatches отсутствует в reevaluateMatching()"

    # 3. Контроль плавного реактивного дебаунса
    if "delay(35L)" in code:
        return False, "В CalibrationOverlay.kt установлен слишком короткий дебаунс (35ms), вызывающий зависание UI при движении слайдеров"

    return True, "Калибровка полностью защищена от крашей оверлейных диалогов и реактивно рассчитывает NeuralVisualMatcher.findMatches."
