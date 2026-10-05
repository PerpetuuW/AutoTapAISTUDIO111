# check_search_pipeline_and_micro_icon_optimization.py
# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    tme_path = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "vision", "TemplateMatchingEngine.kt")
    if not os.path.exists(tme_path):
        return False, "TemplateMatchingEngine.kt не найден"

    with open(tme_path, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Проверка Zero-Allocation: константа объявлена, а внутри цикла нет создания массива
    if "NEIGHBOR_DX" not in content or "NEIGHBOR_DY" not in content:
        return False, "Отсутствуют статические константы векторов смещений соседей NEIGHBOR_DX/DY"

    if "px + intArrayOf" in content or "py + intArrayOf" in content:
        return False, "Обнаружена динамическая аллокация intArrayOf в цикле сканирования TIER 2"

    # 2. Проверка раннего отсечения фона
    if "k >= features.shapeCount / 3" not in content:
        return False, "Отсутствует оптимизация раннего выхода Early-Rejection Break"

    # 3. Проверка инвариантов микро-иконок
    if "(features.tw / 4).coerceIn(3, 6)" not in content:
        return False, "Нарушен инвариант шага сетки Найквиста для микро-иконок"

    return True, "Zero-Allocation сканирование и оптимизация раннего отсечения полностью верифицированы"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath("."))
    print(f"Status: {passed}, Msg: {msg}")
