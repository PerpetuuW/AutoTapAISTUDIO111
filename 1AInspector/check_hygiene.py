# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    src_dir = os.path.join(root_dir, "app", "src")
    baks = []
    for r, d, files in os.walk(src_dir):
        for f in files:
            if f.endswith(".bak"):
                baks.append(os.path.join(r, f))

    if baks:
        return False, f"Обнаружены неизолированные .bak файлы в src/ ({len(baks)} шт.)"
    return True, "Дерево app/src/ полностью очищено от мусорных файлов"
