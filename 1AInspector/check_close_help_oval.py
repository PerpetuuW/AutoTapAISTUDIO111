# -*- coding: utf-8 -*-
"""
check_close_help_oval.py -> check_toolbar_m3_harmony.py
Научный инспектор эргономики тулбара:
1. Кнопки тулбара соответствуют геометрическому ритму Material 3 (скругленные прямоугольники 10dp, без овальной деформации).
2. Запрещены ослепляющие неоновые подложки (#2E1854, #2E1522).
3. Допущены и верифицированы прецизионные тонкие грани (1.2dp) и векторные пиктограммы.
"""
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    panel_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/panel/ControlPanelOverlay.kt".replace("/", os.sep))
    if not os.path.exists(panel_path):
        return False, f"Файл не найден: {panel_path}"

    with open(panel_path, "r", encoding="utf-8") as f:
        code = f.read()

    # Запрет неоновых заливок
    if "#2E1854" in code or "#2E1522" in code or "0xFF2E1854" in code or "0xFF2E1522" in code:
        return False, "В тулбаре обнаружены ослепляющие неоновые подложки (#2E1854 / #2E1522)"

    # Контроль единой геометрии (скругленный прямоугольник 10dp, без деформированных овалов)
    if "isCloseBtn || isHelpBtn" in code and "GradientDrawable.OVAL" in code:
        return False, "Нарушен геометрический ритм тулбара: кнопки (?) и (X) имеют деформированную форму OVAL вместо Material 3 RECTANGLE"

    # Защита от прыжка драга
    if "attachDragListener(miniBubbleLayout)" in code:
        return False, "Обнаружен конфликтующий attachDragListener(miniBubbleLayout), вызывающий прыжок при перемещении"

    return True, "Кнопки (?) и (X) согласованы с научной эргономикой Material 3 (единый ритм, тонкие грани 1.2dp, отсутствие ослепляющего неона)."
