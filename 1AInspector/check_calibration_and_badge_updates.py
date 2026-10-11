# -*- coding: utf-8 -*-
"""check_calibration_and_badge_updates.py — Проверка согласованности калибровки с поиском, сброса кэша и marquee-бегущей строки в бейдже"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    calib_file = os.path.join(
        root_dir, "app", "src", "main", "java", "com", "example", "autotap",
        "infrastructure", "overlay", "capture", "CalibrationOverlay.kt"
    )
    badge_file = os.path.join(
        root_dir, "app", "src", "main", "java", "com", "example", "autotap",
        "infrastructure", "overlay", "badge", "RunningBadgeOverlay.kt"
    )
    if not os.path.exists(calib_file) or not os.path.exists(badge_file):
        return False, "Файлы не найдены"

    with open(calib_file, "r", encoding="utf-8") as f:
        calib_src = f.read()

    with open(badge_file, "r", encoding="utf-8") as f:
        badge_src = f.read()

    if "similarityPercent = currentSimilarity" not in calib_src:
        return False, "CalibrationOverlay.kt: effectiveActionOverride должен использовать currentSimilarity для точного согласования с поиском"

    if "TemplateMatchingEngine.clearTemplateCache()" not in calib_src:
        return False, "CalibrationOverlay.kt: должен очищать кэш шаблонов при сохранении"

    if "ellipsize = android.text.TextUtils.TruncateAt.MARQUEE" not in badge_src:
        return False, "RunningBadgeOverlay.kt: статус должен использовать бегущую строку (MARQUEE)"

    if "contentWidthDp = 78" not in badge_src:
        return False, "RunningBadgeOverlay.kt: ширина бейджа должна быть компактной (78dp)"

    return True, "[PASS] Согласованность калибровки и компактный marquee-бейджик верифицированы."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
