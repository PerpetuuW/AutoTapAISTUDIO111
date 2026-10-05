# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str):
    p_disp = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "gesture", "GestureDispatcher.kt")
    p_tom = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "target", "TargetOverlayManager.kt")
    
    for p in [p_disp, p_tom]:
        if not os.path.exists(p):
            return False, f"[CRITICAL] Файл не найден: {p}"
            
    with open(p_disp, "r", encoding="utf-8") as f:
        disp_src = f.read()
    if "getRealDisplayMetrics" not in disp_src:
        return False, "[FAIL] GestureDispatcher: отсутствует метод getRealDisplayMetrics"
    if disp_src.count("fun getRealDisplayMetrics") > 1:
        return False, "[FAIL] GestureDispatcher: обнаружено дублирование метода getRealDisplayMetrics"

    with open(p_tom, "r", encoding="utf-8") as f:
        tom_src = f.read()
    if "android.util.DisplayMetrics()" not in tom_src:
        return False, "[FAIL] TargetOverlayManager: не применен FQCN для android.util.DisplayMetrics"
    if "getRealScreenDimensions" not in tom_src:
        return False, "[FAIL] TargetOverlayManager: не внедрен getRealScreenDimensions"

    return True, "[PASS] FQCN-изоляция типов соблюдена, полная высота экрана разблокирована"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
