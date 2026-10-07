#!/usr/bin/env python3
import os
import sys

def run_check(root_dir="."):
    demo_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/InteractiveRoboticArmDemoDialog.kt")

    if not os.path.exists(demo_path):
        return False, f"Файл {demo_path} не найден!"

    with open(demo_path, "r", encoding="utf-8") as f:
        code = f.read()

    # 1. Проверка наличия живых описаний действий (ДЕЙСТВИЕ:)
    if "currentActionDesc" not in code or "ДЕЙСТВИЕ:" not in code:
        return False, "Демо-режим должен содержать детальное описание текущего микро-действия (ДЕЙСТВИЕ:)!"

    # 2. Проверка наличия практических инструкций (КАК ПРИМЕНЯТЬ:)
    if "practicalHint" not in code or "КАК ПРИМЕНЯТЬ:" not in code:
        return False, "Демо-режим должен содержать практическое руководство (КАК ПРИМЕНЯТЬ:) для каждого этапа!"

    # 3. Проверка наличия 4 стадий
    stages = ["ЭТАП 1/4", "ЭТАП 2/4", "ЭТАП 3/4", "ЭТАП 4/4"]
    for s in stages:
        if s not in code:
            return False, f"В демо-режиме отсутствует этап {s}!"

    # 4. Проверка отрисовки указательной руки
    if "drawClassicIndexPointerGlove" not in code:
        return False, "Демо-режим должен содержать отрисовку указателя тача drawClassicIndexPointerGlove!"

    return True, "[PASS] Проверка четкости, доступности и полноты описания действий в демо-режиме пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(".")
    print(msg)
    if not ok:
        sys.exit(1)
