# -*- coding: utf-8 -*-
import os
import re

def run_check(root_dir: str) -> tuple[bool, str]:
    pattern = re.compile(r'val\s+[A-Z]+\s*=\s*[a-zA-Z0-9_\.]+\.[A-Z][a-zA-Z0-9_]*')
    errors = []
    src = os.path.join(root_dir, "app", "src", "main")
    if not os.path.exists(src):
        return True, "Исходники отсутствуют"
    for r, _, files in os.walk(src):
        for file in files:
            if file.endswith(".kt"):
                with open(os.path.join(r, file), "r", encoding="utf-8") as kf:
                    for i, line in enumerate(kf, 1):
                        if pattern.search(line) and "Enum" not in line:
                            errors.append(f"{file}:{i} -> Запрещен локальный Enum-алиас: {line.strip()}")
    if errors:
        return False, "\n".join(errors)
    return True, "Локальные алиасы Enum отсутствуют"
