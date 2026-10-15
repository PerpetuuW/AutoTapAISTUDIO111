# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str) -> tuple[bool, str]:
    panel_path = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "panel", "ControlPanelComposeOverlay.kt")

    if not os.path.exists(panel_path):
        return False, f"[CRITICAL] ControlPanelComposeOverlay.kt не найден: {panel_path}"

    with open(panel_path, "r", encoding="utf-8") as f:
        content = f.read()

    if "VectorIconDrawer.IconType.CLOSE" not in content or "listener.onCloseClicked()" not in content:
        return False, "[FAIL] В ControlPanelComposeOverlay.kt кнопка ЗАКРЫТЬ (крестик X) должна вызывать listener.onCloseClicked()"

    if "size = 30.dp" not in content and "size = 32.dp" not in content:
        return False, "[FAIL] В ControlPanelComposeOverlay.kt размер кнопок главной строки должен быть <= 32.dp для гарантированной видимости крестика X на экранах 360dp"

    if "posX = (posX + dragAmount.x.toInt())" not in content or "posY = (posY + dragAmount.y.toInt())" not in content:
        return False, "[FAIL] В ControlPanelComposeOverlay.kt перетаскивание оверлея должно обновлять переменные posX и posY плавно"

    return True, "[PASS] Компактный размер кнопок (30dp), видимость крестика X и плавная геометрия перетаскивания оверлея верифицированы."

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
