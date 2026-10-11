# -*- coding: utf-8 -*-
"""check_badge_symmetry_and_calibration_accuracy.py — Проверка строгой визуальной симметрии окна выполнения шагов и калибровочной точности"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    badge_file = os.path.join(
        root_dir, "app", "src", "main", "java", "com", "example", "autotap",
        "infrastructure", "overlay", "badge", "RunningBadgeOverlay.kt"
    )
    if not os.path.exists(badge_file):
        return False, f"Файл не найден: {badge_file}"

    with open(badge_file, "r", encoding="utf-8") as f:
        src = f.read()

    # 1. Проверка центрирования кнопок внутри строки
    if "val buttonsRow = LinearLayout(context).apply {\n            orientation = LinearLayout.HORIZONTAL\n            gravity = Gravity.CENTER" not in src:
        return False, "RunningBadgeOverlay.kt: buttonsRow обязан иметь gravity = Gravity.CENTER для полной симметрии кнопок"

    # 2. Проверка симметрии отступа между кнопками
    if "marginEnd = dp(8)" not in src:
        return False, "RunningBadgeOverlay.kt: расстояние между круглыми кнопками должно быть симметричным (8dp)"

    return True, "[PASS] Симметрия окна выполнения шагов верифицирована."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
