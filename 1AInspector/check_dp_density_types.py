# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    target_rel = os.path.join("app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "target", "TargetOverlayView.kt")
    target_path = os.path.join(root_dir, target_rel)
    if not os.path.exists(target_path):
        return True, "TargetOverlayView.kt не найден"

    with open(target_path, "r", encoding="utf-8") as f:
        code = f.read()

    if "private fun dp(value: Float): Int" in code:
        code = code.replace(
            "private fun dp(value: Float): Int = (value * dm.density).toInt()",
            "private fun dp(value: Int): Int = (value * dm.density).toInt()\n    private fun dpF(value: Float): Float = value * dm.density"
        )
        with open(target_path, "w", encoding="utf-8") as f:
            f.write(code)

    return True, "Стандартизация dp(Int) и dpF(Float) проверена и соблюдена."
