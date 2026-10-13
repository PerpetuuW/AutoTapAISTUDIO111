# -*- coding: utf-8 -*-
import os, sys, re

def run_check(root_dir: str) -> tuple[bool, str]:
    target_dir = os.path.join(root_dir, "app", "src", "main", "java")
    if not os.path.exists(target_dir):
        return False, "[CRITICAL] Директория исхоников не найдена"

    bad_truncation_pattern = re.compile(r'\.take\(\d+\)')
    errors = []

    for root, _, files in os.walk(target_dir):
        for file in files:
            if file.endswith(".kt"):
                full_path = os.path.join(root, file)
                rel_path = os.path.relpath(full_path, root_dir)
                with open(full_path, "r", encoding="utf-8", errors="replace") as f:
                    for idx, line in enumerate(f, 1):
                        # Игнорируем комментарии и логи, проверяем только логику присвоения текста / наименований
                        line_clean = line.split("//")[0]
                        if bad_truncation_pattern.search(line_clean) and ("text" in line_clean or "Title" in line_clean or "Name" in line_clean):
                            errors.append(f"{rel_path}:{idx} -> Обнаружена опасная жесткая обрезка текста: {line.strip()}")

    if errors:
        return False, "\n".join(errors[:5]) + (f"\n...и еще {len(errors)-5} ошибок" if len(errors) > 5 else "")

    return True, "[PASS] Проверка вместимости текста без обрезки по всей верстке успешно пройдена."

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
