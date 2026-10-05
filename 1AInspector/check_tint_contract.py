# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    layout_dir = os.path.join(root_dir, "app", "src", "main", "res", "layout")
    if not os.path.exists(layout_dir):
        return True, "Папка layout отсутствует"

    errors = []
    for f in os.listdir(layout_dir):
        if f.endswith(".xml"):
            path = os.path.join(layout_dir, f)
            with open(path, "r", encoding="utf-8") as xf:
                content = xf.read()
            if "android:tint=" in content:
                errors.append(f"{f}: найден запрещенный android:tint (требуется app:tint)")

    if errors:
        return False, "\n".join(errors)
    return True, "Контракт AndroidX app:tint соблюден во всех макетах"
