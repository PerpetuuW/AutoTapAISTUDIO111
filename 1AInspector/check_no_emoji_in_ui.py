# -*- coding: utf-8 -*-
import os
import re

def run_check(root_dir: str) -> tuple[bool, str]:
    target_dir = os.path.join(root_dir, "app/src/main/java")
    emoji_pattern = re.compile(r'[\U00010000-\U0010ffff]|[\u2600-\u27bf]|[\u2300-\u23ff]|[\u2b50-\u2b55]')

    errors = []
    if os.path.exists(target_dir):
        for root, _, files in os.walk(target_dir):
            for file in files:
                if file.endswith(".kt"):
                    full = os.path.join(root, file)
                    with open(full, "r", encoding="utf-8", errors="replace") as f:
                        for idx, line in enumerate(f, 1):
                            if '"' in line and emoji_pattern.search(line):
                                rel = os.path.relpath(full, root_dir)
                                errors.append(f"{rel}:{idx} -> Эмодзи в UI: {line.strip()}")

    if errors:
        return False, "\n".join(errors)
    return True, "Интерфейс приложения полностью очищен от эмодзи во всей кодовой базе"
