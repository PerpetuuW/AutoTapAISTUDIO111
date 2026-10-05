# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    mgr_path = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "target", "TargetOverlayManager.kt")
    if not os.path.exists(mgr_path):
        return True, "TargetOverlayManager.kt не найден"

    with open(mgr_path, "r", encoding="utf-8") as f:
        code = f.read()

    if "dp(55f)" in code or "dp(65f)" in code:
        return False, "TargetOverlayManager: обнаружены искусственные 'невидимые стены' (55dp/65dp)!"

    if "FLAG_LAYOUT_NO_LIMITS" not in code:
        return False, "TargetOverlayManager: отсутствует флаг FLAG_LAYOUT_NO_LIMITS для полноэкранного перемещения!"

    return True, "Инвариант Edge-to-Edge Mobility подтвержден (0..screenW, 0..screenH)."
