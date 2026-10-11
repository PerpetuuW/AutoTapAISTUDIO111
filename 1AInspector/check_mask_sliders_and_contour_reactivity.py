# -*- coding: utf-8 -*-
"""check_mask_sliders_and_contour_reactivity.py — Проверка сброса статической маски при движении ползунков охвата/отступа и переключении контура"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    calib_file = os.path.join(
        root_dir, "app", "src", "main", "java", "com", "example", "autotap",
        "infrastructure", "overlay", "capture", "CalibrationOverlay.kt"
    )
    if not os.path.exists(calib_file):
        return False, f"Файл не найден: {calib_file}"

    with open(calib_file, "r", encoding="utf-8") as f:
        src = f.read()

    # 1. Ползунок охвата формы должен сбрасывать customEditedMask для динамического перерасчета
    if "customEditedMask = null\n                    currentShapeExpansion = progress" not in src:
        return False, "CalibrationOverlay.kt: ползунок охвата формы обязан сбрасывать customEditedMask"

    # 2. Ползунок отступа краев должен сбрасывать customEditedMask
    if "customEditedMask = null\n                    currentPaddingOffset = progress - 5" not in src:
        return False, "CalibrationOverlay.kt: ползунок отступа краев обязан сбрасывать customEditedMask"

    # 3. Переключатель режима контура должен сбрасывать customEditedMask
    if "customEditedMask = null\n                isAutoContourMode = !isAutoContourMode" not in src:
        return False, "CalibrationOverlay.kt: кнопка КОНТУР/КАРТИНКА обязана сбрасывать customEditedMask"

    return True, "[PASS] Реактивность ползунков маски и переключателей контура подтверждена."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
