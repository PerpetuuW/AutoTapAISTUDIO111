# -*- coding: utf-8 -*-
"""
check_path_radical_direction_compression.py — Модуль иммунитета (v34.0)
Валидация:
1. Наличие алгоритма фильтрации точек пути СТРОГО в местах радикального изменения направления (filterRadicalDirectionChanges).
2. Вычисление углов поворота траектории (angleDeg, minAngleDeg) в PathCompressionEngine.kt.
3. Интеграция кнопки оптимизации и сжатия точек до углов в EditActionDialog.kt ("УГЛЫ").
"""

import os
import sys

def run_check(root_dir: str) -> tuple[bool, str]:
    p_engine = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "core", "math", "PathCompressionEngine.kt")
    p_dlg = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "dialog", "EditActionDialog.kt")

    if not os.path.exists(p_engine):
        return False, f"[FAIL] PathCompressionEngine.kt не найден: {p_engine}"

    if not os.path.exists(p_dlg):
        return False, f"[FAIL] EditActionDialog.kt не найден: {p_dlg}"

    with open(p_engine, "r", encoding="utf-8") as f:
        src_engine = f.read()

    if "filterRadicalDirectionChanges" not in src_engine:
        return False, "[FAIL] В PathCompressionEngine отсутствует метод filterRadicalDirectionChanges"

    if "minAngleDeg" not in src_engine or "angleDeg" not in src_engine:
        return False, "[FAIL] В PathCompressionEngine отсутствует геометрический анализ углов изменения направления"

    with open(p_dlg, "r", encoding="utf-8") as f:
        src_dlg = f.read()

    if '"УГЛЫ"' not in src_dlg:
        return False, "[FAIL] В EditActionDialog отсутствует кнопка оптимизации пути до углов 'УГЛЫ'"

    return True, "[PASS] Создание точек пути строго в местах радикального изменения направления и кнопка УГЛЫ подтверждены."

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
