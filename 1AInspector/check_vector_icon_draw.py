# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    rec_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "record", "GestureRecorderOverlay.kt")
    if not os.path.exists(rec_file):
        return True, "GestureRecorderOverlay.kt не найден"

    with open(rec_file, "r", encoding="utf-8") as f:
        content = f.read()

    if "return\n        super.onDraw(canvas)" in content or "return\r\n        super.onDraw(canvas)" in content:
        return False, "Обнаружен блокирующий return перед отрисовкой VectorIconDrawer в GestureRecorderOverlay!"

    return True, "Векторные пиктограммы подтверждения/отмены не заблокированы в onDraw"
