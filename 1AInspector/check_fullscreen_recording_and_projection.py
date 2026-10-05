# check_fullscreen_recording_and_projection.py
# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    main_path = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "presentation", "main", "MainActivity.kt")
    mps_path = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "projection", "MediaProjectionService.kt")

    if not os.path.exists(main_path) or not os.path.exists(mps_path):
        return False, "Файлы MainActivity или MediaProjectionService не найдены"

    with open(main_path, "r", encoding="utf-8") as f:
        main_code = f.read()
    with open(mps_path, "r", encoding="utf-8") as f:
        mps_code = f.read()

    # 1. Проверка конфигурации захвата всего экрана в Android 14+
    if "createConfigForDefaultDisplay" not in main_code:
        return False, "MainActivity: отсутствует MediaProjectionConfig.createConfigForDefaultDisplay() для Android 14+"

    # 2. Проверка Real Metrics для VirtualDisplay
    if "getRealMetrics" not in mps_code:
        return False, "MediaProjectionService: виртуальный экран использует обрезанные displayMetrics вместо getRealMetrics"

    return True, "Контракт полноэкранного захвата и записи дисплея подтвержден"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath("."))
    print(f"Status: {passed}, Msg: {msg}")
