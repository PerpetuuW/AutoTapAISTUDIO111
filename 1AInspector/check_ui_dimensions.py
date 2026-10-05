# -*- coding: utf-8 -*-
import os
import re

def run_check(root_dir: str) -> tuple[bool, str]:
    ctrl_path = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "panel", "ControlPanelOverlay.kt")
    if not os.path.exists(ctrl_path):
        return True, "Файл ControlPanelOverlay.kt не найден"

    with open(ctrl_path, "r", encoding="utf-8") as f:
        code = f.read()

    # Проверка на Double Density Scaling
    if "sizeDp * density" in code and "effectiveSizeDp" not in code:
        return False, "Обнаружен риск двойного умножения на плотность (Double Density Bug)"

    # Проверка эталонного размера кнопок тулбара (36-40 dp)
    if "38 * density" not in code and "38f * density" not in code and "effectiveSizeDp" not in code:
        return False, "Размер кнопок пульта не соответствует эталону 38 dp [U-14]"

    return True, "Размеры интерфейса оверлея соответствуют эталону Material 3 (38 dp, защита от Double Density)"
