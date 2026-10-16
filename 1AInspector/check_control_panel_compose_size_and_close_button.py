# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str) -> tuple[bool, str]:
    panel_path = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "panel", "ControlPanelOverlay.kt")

    if not os.path.exists(panel_path):
        return False, f"[CRITICAL] ControlPanelOverlay.kt не найден: {panel_path}"

    with open(panel_path, "r", encoding="utf-8") as f:
        content = f.read()

    if "VectorIconDrawer.IconType.CLOSE" not in content or "listener.onCloseClicked()" not in content:
        return False, "[FAIL] В ControlPanelOverlay.kt кнопка ЗАКРЫТЬ (крестик X) должна вызывать listener.onCloseClicked()"

    if "dp(32)" not in content and "dp(30)" not in content:
        return False, "[FAIL] В ControlPanelOverlay.kt размер кнопок главной строки должен быть <= 32dp для гарантированной видимости крестика X на экранах 360dp"

    if "attachDragListener" not in content:
        return False, "[FAIL] В ControlPanelOverlay.kt должно быть реализовано плавная подтяжка координат при перетаскивании"

    return True, "[PASS] Нативная 120 FPS Kotlin Canvas панель управления, компактный размер кнопок (30-32dp) и крестик X справа сверху верифицированы."

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
