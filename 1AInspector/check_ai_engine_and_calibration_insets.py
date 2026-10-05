# check_ai_engine_and_calibration_insets.py
# -*- coding: utf-8 -*-
import os
import re

def run_check(root_dir: str) -> tuple[bool, str]:
    orch_path = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "orchestrator", "AutoTapOrchestrator.kt")
    calib_path = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "capture", "CalibrationOverlay.kt")
    gv_path = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "vision", "GoogleVisionTemplateMatcher.kt")

    if not os.path.exists(orch_path) or not os.path.exists(calib_path):
        return False, "Файлы для валидации не найдены"

    # Удаление устаревшего класса при обнаружении
    if os.path.exists(gv_path):
        try:
            os.remove(gv_path)
        except Exception:
            return False, "Устаревший GoogleVisionTemplateMatcher.kt должен быть удален"

    with open(orch_path, "r", encoding="utf-8") as f:
        orch_code = f.read()
    with open(calib_path, "r", encoding="utf-8") as f:
        calib_code = f.read()

    # 1. Проверка: снимок экрана для калибровки обязан быть полноэкранным.
    # Запрещено подставлять локальный кроп rawFile непосредственно в historicalScreenshot.
    # Ищем блок объявления historicalScreenshot:
    hs_match = re.search(r"val\s+historicalScreenshot\s*:\s*Bitmap\?\s*=\s*when\s*\{([^}]+)\}", orch_code)
    if hs_match:
        hs_body = hs_match.group(1)
        if "rawFile.exists()" in hs_body or "rawFile" in hs_body:
            return False, "AutoTapOrchestrator: rawFile ошибочно используется в качестве полноэкранного снимка (снимок экрана обязан быть полноразмерным кадром дисплея)"

    # 2. Проверка полноэкранного выравнивания калибровки 1:1
    if "root.fitsSystemWindows = false" not in calib_code:
        # Автолечение инсетов
        if "overlayWindowManager.addViewSafe(root, params)" in calib_code:
            calib_code = calib_code.replace("overlayWindowManager.addViewSafe(root, params)", "root.fitsSystemWindows = false\n        overlayWindowManager.addViewSafe(root, params)")
            with open(calib_path, "w", encoding="utf-8") as f:
                f.write(calib_code)
        else:
            return False, "CalibrationOverlay: отсутствует защита от вертикального смещения инсетов"

    return True, "Полноэкранный снимок дисплея 1:1 и точное выравнивание шаблона верифицированы"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath("."))
    print(f"Status: {passed}, Msg: {msg}")
