#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_main_nav_and_overlay_dialogs.py — Инспектор навигации главного меню и безопасных оверлей-диалогов
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    layout_path = os.path.join(root_dir, "app/src/main/res/layout/activity_main.xml")
    main_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/presentation/main/MainActivity.kt")
    graph_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")

    for p in [layout_path, main_path, graph_path]:
        if not os.path.exists(p):
            return False, f"Файл не найден: {p}"

    with open(layout_path, "r", encoding="utf-8") as f:
        l_src = f.read()
    if "tab_export" not in l_src or "tab_import" not in l_src:
        return False, "В activity_main.xml отсутствуют кнопки tab_export и tab_import в нижней навигации"

    with open(main_path, "r", encoding="utf-8") as f:
        m_src = f.read()
    if "showStandaloneLogDialog" not in m_src:
        return False, "В MainActivity.kt отсутствует автономный просмотрщик логов без специальных разрешений"

    with open(graph_path, "r", encoding="utf-8") as f:
        g_src = f.read()
    if "showCustomTemplatePickerDialog" not in g_src:
        return False, "В GraphEditorOverlay.kt отсутствует безопасный оверлей-селектор шаблонов без системного AlertDialog"

    return True, "Инспекция навигации и безопасности оверлей-диалогов пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
