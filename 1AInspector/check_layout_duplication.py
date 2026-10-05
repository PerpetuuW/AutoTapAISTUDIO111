# -*- coding: utf-8 -*-
import os
import re

def run_check(root_dir: str) -> tuple[bool, str]:
    layout_dir = os.path.join(root_dir, "app", "src", "main", "res", "layout")
    kt_dir = os.path.join(root_dir, "app", "src", "main", "java")

    if not os.path.exists(layout_dir):
        return True, "Макеты не обнаружены"

    xml_files = {f[:-4] for f in os.listdir(layout_dir) if f.endswith(".xml")}
    all_kt_code = ""
    for r, d, files in os.walk(kt_dir):
        for f in files:
            if f.endswith(".kt"):
                with open(os.path.join(r, f), "r", encoding="utf-8") as kf:
                    all_kt_code += kf.read() + "\n"

    warnings = []
    for xml_name in xml_files:
        # Проверяем, упоминается ли макет в коде (R.layout.xxx или xxxBinding)
        has_r_layout = f"R.layout.{xml_name}" in all_kt_code
        binding_name = "".join(part.capitalize() for part in xml_name.split("_")) + "Binding"
        has_binding = binding_name in all_kt_code

        if not has_r_layout and not has_binding:
            # Исключаем системные и элементы списков
            if not xml_name.startswith("item_"):
                warnings.append(f"res/layout/{xml_name}.xml не инфлейтится в коде (сиротский макет)")

    # Это диагностическое предупреждение, не блокирующее сборку
    if warnings:
        return True, f"Диагностика верстки: найдено {len(warnings)} неиспользуемых XML-макетов"
    return True, "Все XML-макеты согласованы с кодом Kotlin"
