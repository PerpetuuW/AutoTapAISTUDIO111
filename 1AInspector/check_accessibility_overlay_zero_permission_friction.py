#!/usr/bin/env python3
import os
import sys

def run_check(root_dir="."):
    wm_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/OverlayWindowManager.kt")
    main_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/presentation/main/MainActivity.kt")
    layout_path = os.path.join(root_dir, "app/src/main/res/layout/activity_main.xml")

    if not os.path.exists(wm_path) or not os.path.exists(main_path) or not os.path.exists(layout_path):
        return False, "Не найдены необходимые файлы для проверки оверлея и службы!"

    with open(wm_path, "r", encoding="utf-8") as f:
        wm_code = f.read()

    with open(main_path, "r", encoding="utf-8") as f:
        main_code = f.read()

    with open(layout_path, "r", encoding="utf-8") as f:
        layout_code = f.read()

    # 1. OverlayWindowManager должен использовать TYPE_ACCESSIBILITY_OVERLAY как дефолтный тип
    if "TYPE_ACCESSIBILITY_OVERLAY" not in wm_code:
        return False, "OverlayWindowManager должен использовать TYPE_ACCESSIBILITY_OVERLAY от службы доступности!"

    # 2. В главном меню не должно быть дублирующей кнопки запроса оверлея
    if "btn_perm_overlay" in layout_code:
        return False, "В activity_main.xml обнаружен избыточный блок запроса оверлея btn_perm_overlay!"

    if "showOverlaySafetyDialog" in main_code:
        return False, "В MainActivity обнаружен устаревший метод showOverlaySafetyDialog!"

    return True, "[PASS] Инспекция Zero-Friction оверлея через AccessibilityService пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(".")
    print(msg)
    if not ok:
        sys.exit(1)
