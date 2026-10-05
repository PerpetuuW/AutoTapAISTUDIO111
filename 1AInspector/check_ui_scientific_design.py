# check_ui_scientific_design.py
# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    panel_path = os.path.normpath(os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/panel/ControlPanelOverlay.kt"))
    if not os.path.exists(panel_path): return False, "ControlPanelOverlay.kt не найден"

    with open(panel_path, "r", encoding="utf-8") as f: panel_content = f.read()
    if 'tag = "SUB_ROW"' not in panel_content: return False, "Секция SUB_ROW не найдена"

    subrow = panel_content.split('tag = "SUB_ROW"')[1].split("rootLinear.addView(subRow)")[0]

    # Проверка, что иконки subRow оформлены в единый активный лазурный цвет #38BDF8
    if '"#38BDF8"' not in subrow:
        return False, "В subRow отсутствует единый лазурный цвет кнопок #38BDF8"

    # Проверка отсутствия старых тусклых или радужных цветов (включая желтый замок и тусклый титан #94A3B8)
    for bad_color in ['"#A78BFA"', '"#FB7185"', '"#60A5FA"', '"#94A3B8"', 'btnLock = createIconButton(VectorIconDrawer.IconType.LOCK, "", "#FBBF24"']:
        if bad_color in subrow:
            return False, f"В subRow обнаружен запрещенный тусклый цвет или цвет светофора: {bad_color}"

    return True, "Единый активный стиль subRow #38BDF8 подтвержден"

if __name__ == "__main__":
    ok, msg = run_check(os.getcwd())
    print(f"Result: {ok} -> {msg}")
