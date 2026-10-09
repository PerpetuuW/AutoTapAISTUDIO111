#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Иммунитет: Валидация гигиены визуального фидбека (исключение неинформативного текста 'НАЙДЕНО')
и устойчивости геометрии плавающих бейджей/пузырей от деформации и сжатия на границах экрана.
"""
import os

def run_check(root_dir: str = ".") -> tuple[bool, str]:
    vis_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/visualizer/TargetHighlightVisualizer.kt".replace("/", os.sep))
    orch_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt".replace("/", os.sep))
    badge_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/badge/RunningBadgeOverlay.kt".replace("/", os.sep))
    calib_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt".replace("/", os.sep))

    for path in [vis_path, orch_path, badge_path, calib_path]:
        if not os.path.exists(path):
            return False, f"Файл не найден: {path}"

    with open(vis_path, "r", encoding="utf-8") as f:
        vis_src = f.read()

    # 1. Проверка отсутствия неинформативного текста 'НАЙДЕНО' в matchBadge и 'ОТКЛОНЕНО' в rejectBadge
    if 'text = "НАЙДЕНО' in vis_src or 'text = "ОТКЛОНЕНО' in vis_src:
        return False, "TargetHighlightVisualizer.kt содержит неинформативный текст 'НАЙДЕНО' или 'ОТКЛОНЕНО' в бейдже"

    # 2. Проверка устойчивости геометрии пузырей (динамическое измерение и защита от разлома)
    if "matchBadge.measure" not in vis_src or "rejectBadge.measure" not in vis_src:
        return False, "TargetHighlightVisualizer.kt обязан выполнять pre-measure бейджей перед позиционированием для защиты от сплющивания"

    # 3. Проверка однострочности для предотвращения разлома пузыря
    if "isSingleLine = true" not in vis_src or "ellipsize = TextUtils.TruncateAt.END" not in vis_src:
        return False, "TargetHighlightVisualizer.kt должен блокировать вертикальный перенос текста бейджей"

    with open(orch_path, "r", encoding="utf-8") as f:
        orch_src = f.read()

    if '"Найдено:' in orch_src:
        return False, "AutoTapOrchestrator.kt не должен содержать неинформативный префикс 'Найдено:' в Toast"

    with open(badge_path, "r", encoding="utf-8") as f:
        badge_src = f.read()

    if "isSingleLine = true" not in badge_src:
        return False, "RunningBadgeOverlay.kt должен защищать текстовые пузыри от переноса и деформации"

    with open(calib_path, "r", encoding="utf-8") as f:
        calib_src = f.read()

    if "btnDownscaleToggle" in calib_src:
        return False, "CalibrationOverlay.kt не должен содержать ручной тумблер btnDownscaleToggle: сжатие должно быть каскадным"

    return True, "Визуальный фидбек очищен от текстового шума, геометрия плавающих пузырей полностью устойчива к разлому."

if __name__ == "__main__":
    ok, msg = run_check(".")
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
