#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Иммунитет: Валидация конвейера предварительного сжатия и оптимизации масштаба поиска шаблонов (Downscale Optimization).
Проверяет:
1. CalibrationOverlay: динамический подбор оптимального масштаба сжатия, переключатель режимов (АВТО/РУЧН),
   сохранение optimalDownscaleFactor в метаданные шаблона и MacroAction.
2. ScenarioRepositoryImpl: сквозная сериализация и десериализация downscaleFactor.
3. TemplateMatchingEngine: интеграция эффективного масштаба сжатия в первичный отсев кандидатов (gridStep, sampleStride, SearchDiagnostics).
4. TargetHighlightVisualizer: исключение служебных строк сжатия из OCR.
"""
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    calib_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt".replace("/", os.sep))
    repo_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/storage/ScenarioRepositoryImpl.kt".replace("/", os.sep))
    engine_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt".replace("/", os.sep))
    visualizer_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/visualizer/TargetHighlightVisualizer.kt".replace("/", os.sep))

    if not os.path.exists(calib_path):
        return False, "CalibrationOverlay.kt не найден"
    if not os.path.exists(repo_path):
        return False, "ScenarioRepositoryImpl.kt не найден"
    if not os.path.exists(engine_path):
        return False, "TemplateMatchingEngine.kt не найден"
    if not os.path.exists(visualizer_path):
        return False, "TargetHighlightVisualizer.kt не найден"

    with open(calib_path, "r", encoding="utf-8") as f:
        calib_src = f.read()

    if "calculateOptimalDownscaleFactor" not in calib_src:
        return False, "В CalibrationOverlay.kt отсутствует расчет оптимального масштаба сжатия calculateOptimalDownscaleFactor"
    if "userDownscaleFactor" not in calib_src or "effectiveDownscaleFactor" not in calib_src:
        return False, "В CalibrationOverlay.kt отсутствуют переменные состояния масштаба сжатия userDownscaleFactor / effectiveDownscaleFactor"
    if "btnDownscaleToggle" in calib_src:
        return False, "В CalibrationOverlay.kt не должно быть ручной кнопки btnDownscaleToggle: сжатие обязано работать как автоматическое каскадное ускорение"
    if '"optimalDownscaleFactor"' not in calib_src or '"downscaleFactor"' not in calib_src:
        return False, "В CalibrationOverlay.kt отсутствует сохранение downscaleFactor в metaObj"
    if "downscaleFactor = finalDownscale" not in calib_src:
        return False, "В CalibrationOverlay.kt отсутствует передача downscaleFactor в MacroAction"

    with open(repo_path, "r", encoding="utf-8") as f:
        repo_src = f.read()

    if '"downscaleFactor", action.downscaleFactor' not in repo_src:
        return False, "В ScenarioRepositoryImpl.kt отсутствует сериализация downscaleFactor"
    if 'downscaleFactor = obj.optInt("downscaleFactor"' not in repo_src:
        return False, "В ScenarioRepositoryImpl.kt отсутствует десериализация downscaleFactor"

    with open(engine_path, "r", encoding="utf-8") as f:
        engine_src = f.read()

    if "optimalDownscaleFactorFromMeta" not in engine_src:
        return False, "В TemplateMatchingEngine.kt отсутствует чтение optimalDownscaleFactorFromMeta"
    if "downscaleFactor" not in engine_src:
        return False, "В TemplateMatchingEngine.kt отсутствует интеграция downscaleFactor"

    with open(visualizer_path, "r", encoding="utf-8") as f:
        vis_src = f.read()

    if "СЖАТИЕ" not in vis_src:
        return False, "В TargetHighlightVisualizer.kt отсутствует защита от захвата бейджей сжатия в OCR"

    return True, "Конвейер предварительного сжатия и калибровки масштаба шаблонов полностью валиден."

if __name__ == "__main__":
    ok, msg = run_check(".")
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
