# check_demo_dialog_4stages_and_zero_friction.py
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    p = os.path.normpath(os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/InteractiveRoboticArmDemoDialog.kt"))
    if not os.path.exists(p):
        return False, "InteractiveRoboticArmDemoDialog.kt не найден"

    with open(p, "r", encoding="utf-8") as f:
        src = f.read()

    required_chips = [
        "1. ИЗОБРАЖЕНИЕ",
        "2. ТЕКСТ (OCR)",
        "3. ЗАПИСЬ ДЕЙСТВИЙ",
        "4. ОБЫЧНЫЕ КЛИКИ"
    ]

    for chip in required_chips:
        if chip not in src:
            return False, f"В Demo dialog отсутствует чип/стадия: {chip}"

    if "КАДРИРОВАНИЕ & ROI" in src or "ДИАЛОГ ШАБЛОНА" in src:
        return False, "В Demo dialog обнаружен технический шум старого 5-шагового демо"

    return True, "Демо-режим полностью соответствует 4-стадийному Zero-Friction стандарту"

if __name__ == "__main__":
    ok, msg = run_check(os.getcwd())
    print(f"Result: {ok} -> {msg}")
