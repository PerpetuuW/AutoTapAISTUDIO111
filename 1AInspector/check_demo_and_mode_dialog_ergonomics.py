#!/usr/bin/env python3
import os
import sys

def run_check(root_dir="."):
    demo_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/InteractiveRoboticArmDemoDialog.kt")
    main_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/presentation/main/MainActivity.kt")

    if not os.path.exists(demo_path) or not os.path.exists(main_path):
        return False, "Не найдены необходимые файлы для проверки Демо-режима и диалога выбора режима!"

    with open(demo_path, "r", encoding="utf-8") as f:
        demo_code = f.read()

    with open(main_path, "r", encoding="utf-8") as f:
        main_code = f.read()

    if "drawClassicIndexPointerGlove" not in demo_code:
        return False, "Демо-режим должен использовать метод отрисовки классической указательной руки!"

    if "showMediaProjectionRecommendationDialog" not in main_code:
        return False, "MainActivity должна содержать диалог с рекомендацией стандартного режима и выбором 60 FPS!"

    if "ОБЫЧНОМ режиме" not in main_code:
        return False, "Диалог выбора режима должен пояснять, что ИИ-детекторы работают в ОБЫЧНОМ режиме!"

    return True, "[PASS] Инспекция эргономики Демо-режима и рекомендаций захвата 60 FPS пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(".")
    print(msg)
    if not ok:
        sys.exit(1)
