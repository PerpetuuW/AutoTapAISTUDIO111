# -*- coding: utf-8 -*-
import os
import py_compile

def run_check(root_dir: str) -> tuple[bool, str]:
    errors = []
    for f in os.listdir(root_dir):
        if f.startswith("1A") and f.endswith(".py"):
            full_p = os.path.join(root_dir, f)
            try:
                py_compile.compile(full_p, doraise=True)
            except Exception as e:
                errors.append(f"{f}: синтаксическая ошибка Python ({str(e)})")

    if errors:
        return False, "\n".join(errors)
    return True, "Все служебные скрипты 1A*.py синтаксически валидны"
