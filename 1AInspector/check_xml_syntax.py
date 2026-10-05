# -*- coding: utf-8 -*-
import os
import xml.etree.ElementTree as ET

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
            try:
                ET.fromstring(content)
            except Exception as e:
                errors.append(f"{f}: синтаксическая ошибка ({str(e)})")

            if 'android:text="<"' in content or 'android:text=">"' in content:
                errors.append(f"{f}: неэкранированные спецсимволы < или > (требуется &lt; / &gt;)")

    if errors:
        return False, "\n".join(errors)
    return True, "Все XML-макеты синтаксически валидны (ElementTree)"
