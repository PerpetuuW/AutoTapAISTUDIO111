# -*- coding: utf-8 -*-
import os
import re

def run_check(root_dir: str) -> tuple[bool, str]:
    kt_dir = os.path.join(root_dir, "app", "src", "main", "java")
    if not os.path.exists(kt_dir):
        return True, "Исходники не найдены"

    errors = []
    for r, d, files in os.walk(kt_dir):
        for f in files:
            if f.endswith(".kt"):
                p = os.path.join(r, f)
                with open(p, "r", encoding="utf-8") as kf:
                    content = kf.read()

                # Проверка: package должен быть раньше первого import
                pkg_match = re.search(r'^package\s+[\w\.]+', content, re.MULTILINE)
                imp_match = re.search(r'^import\s+[\w\.]+', content, re.MULTILINE)

                if pkg_match and imp_match:
                    if imp_match.start() < pkg_match.start():
                        # Автолечение: переносим package в самый верх
                        c_clean = content.replace(pkg_match.group(0), "")
                        c_clean = pkg_match.group(0) + "\n\n" + c_clean.lstrip()
                        with open(p, "w", encoding="utf-8", newline="\n") as kw:
                            kw.write(c_clean)
                        errors.append(f"{f}: package автоматически перенесен в начало файла")

    if errors:
        return True, "Автолечение структуры файлов: " + ", ".join(errors)
    return True, "Синтаксическая структура (package -> import) строго соблюдена."
