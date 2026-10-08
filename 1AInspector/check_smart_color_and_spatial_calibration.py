#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
import sys

def run_check(root_dir="."):
    color_detector = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/ColorDetector.kt")
    matching_engine = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt")
    calibration_overlay = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")

    for path in [color_detector, matching_engine, calibration_overlay]:
        if not os.path.exists(path):
            return False, f"Файл не найден: {path}"

    with open(color_detector, "r", encoding="utf-8") as f:
        color_code = f.read()
    if "computeDeltaEFast" not in color_code or "colorToLabFast" not in color_code:
        return False, "ColorDetector должен содержать быстрые методы вычисления CIELAB без аллокаций (computeDeltaEFast, colorToLabFast)"

    with open(matching_engine, "r", encoding="utf-8") as f:
        engine_code = f.read()
    if "shapeLabL" not in engine_code or "l1LabL" not in engine_code:
        return False, "TemplateMatchingEngine должен кэшировать Lab координаты формы шаблона (shapeLabL, l1LabL)"
    if "scanPoints.sortBy" not in engine_code:
        return False, "TemplateMatchingEngine должен оптимизировать порядок сканирования с учетом расстояний до выбранных участков (ROI/Anchors)"
    if "spatialFactor" not in engine_code:
        return False, "TemplateMatchingEngine должен применять пространственный весовой фактор к сходимости"

    with open(calibration_overlay, "r", encoding="utf-8") as f:
        calib_code = f.read()
    if "btnSmartColorToggle" not in calib_code or "isSmartColorMode" not in calib_code:
        return False, "CalibrationOverlay должен содержать переключатель и состояние умного цветового режима (isSmartColorMode)"
    if "roiZones" not in calib_code:
        return False, "CalibrationOverlay должен сохранять и загружать выбранные участки (roiZones)"

    return True, "[PASS] Инспекция умного цветового режима и пространственной оптимизации калибровки пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(".")
    print(msg)
    if not ok:
        sys.exit(1)
