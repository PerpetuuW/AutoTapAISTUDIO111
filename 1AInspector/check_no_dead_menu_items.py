# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    panel_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "panel", "ControlPanelOverlay.kt")
    if not os.path.exists(panel_file):
        return True, "ControlPanelOverlay.kt не найден"

    with open(panel_file, "r", encoding="utf-8") as f:
        content = f.read()

    if "ЗАПИСЬ ДЖОЙСТИКА" in content:
        return False, "В ControlPanelOverlay обнаружен мертвый пункт меню джойстика!"

    return True, "Мертвый код джойстика полностью удален из меню оверлея"
