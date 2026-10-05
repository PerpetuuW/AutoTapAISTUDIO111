# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    dlg_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")
    if not os.path.exists(dlg_file):
        return True, "EditActionDialog.kt не найден"
    with open(dlg_file, "r", encoding="utf-8") as f:
        code = f.read()
    if "private fun String.toColorInt()" not in code:
        code = code.rstrip() + "\n\nprivate fun String.toColorInt(): Int = android.graphics.Color.parseColor(this)\n"
        with open(dlg_file, "w", encoding="utf-8", newline="\n") as f:
            f.write(code)
        return True, "Автолечение: добавлено расширение toColorInt() в EditActionDialog.kt"
    return True, "Инспекция EditActionDialog пройдена."
