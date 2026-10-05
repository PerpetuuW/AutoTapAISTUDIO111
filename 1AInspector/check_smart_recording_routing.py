# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    panel_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "panel", "ControlPanelOverlay.kt")
    if not os.path.exists(panel_file):
        return True, "ControlPanelOverlay.kt не найден"

    with open(panel_file, "r", encoding="utf-8") as f:
        content = f.read()

    idx_smart = content.find("УМНАЯ ЗАПИСЬ")
    if idx_smart == -1:
        return False, "Пункт меню 'УМНАЯ ЗАПИСЬ' не найден в ControlPanelOverlay.kt"

    item_scope = content[idx_smart:idx_smart + 450]

    if "listener.onSmartRecordClicked()" in item_scope:
        return True, "Умная Запись корректно маршрутизирована на onSmartRecordClicked()"

    return False, "В меню showRecordContextMenu вызов onSmartRecordClicked() не обнаружен"
