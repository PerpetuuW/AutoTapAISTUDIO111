# check_kotlin_brackets.py
# -*- coding: utf-8 -*-
import os

def analyze_and_heal_file_brackets(file_path: str) -> tuple[bool, str]:
    with open(file_path, "r", encoding="utf-8", errors="replace") as f: code = f.read()
    chars_with_pos = []; i, n, line_num = 0, len(code), 1
    while i < n:
        if code[i] == "\n": line_num += 1; i += 1; continue
        if i + 1 < n and code[i:i+2] == "//":
            i += 2
            while i < n and code[i] != "\n": i += 1
        elif i + 1 < n and code[i:i+2] == "/*":
            i += 2
            while i + 1 < n and code[i:i+2] != "*/":
                if code[i] == "\n": line_num += 1
                i += 1
            i += 2
        elif code[i] == '"':
            if i + 2 < n and code[i:i+3] == '"""':
                i += 3
                while i + 2 < n and code[i:i+3] != '"""':
                    if code[i] == "\n": line_num += 1
                    i += 1
                i += 3
            else:
                i += 1
                while i < n and code[i] != '"':
                    if code[i] == "\n": line_num += 1
                    if code[i] == "\\" and i + 1 < n: i += 2
                    else: i += 1
                i += 1
        elif code[i] == "'":
            i += 1
            while i < n and code[i] != "'":
                if code[i] == "\n": line_num += 1
                if code[i] == "\\" and i + 1 < n: i += 2
                else: i += 1
            i += 1
        else:
            if code[i] in "{}()[]": chars_with_pos.append((code[i], line_num, i))
            i += 1

    stack = []; pair_map = {")": "(", "}": "{", "]": "["}
    for c, l_num, idx in chars_with_pos:
        if c in "({[": stack.append((c, l_num, idx))
        elif c in ")}]":
            if not stack:
                if c == "}" and l_num >= line_num - 3:
                    healed = code[:idx] + code[idx+1:]
                    with open(file_path, "w", encoding="utf-8", newline="\n") as f: f.write(healed)
                    return True, f"[INFO] Автолечение: удалена лишняя хвостовая скобка на строке {l_num}"
                return False, f"Лишняя закрывающая '{c}' на строке {l_num}"
            top_c, top_l, _ = stack[-1]
            if top_c != pair_map[c]:
                return False, f"Несоответствие скобок на строке {l_num}: закрывается '{c}', ожидалось для '{top_c}' (строка {top_l})"
            stack.pop()

    if stack:
        unclosed = [f"'{c}' на строке {l}" for c, l, _ in stack[-3:]]
        return False, f"Незакрытые скобки: {', '.join(unclosed)} (всего {len(stack)})"
    return True, "OK"

def run_check(root_dir: str) -> tuple[bool, str]:
    kt_dir = os.path.join(root_dir, "app", "src", "main", "java")
    if not os.path.exists(kt_dir): return False, "app/src/main/java не найдена"
    errors = []; healed = []
    for r, _, files in os.walk(kt_dir):
        for f in files:
            if f.endswith(".kt"):
                ok, detail = analyze_and_heal_file_brackets(os.path.join(r, f))
                if not ok: errors.append(f"{f}: {detail}")
                elif detail.startswith("[INFO]"): healed.append(f"{f}: {detail}")
    if errors: return False, "Сбои скобок:\n  " + "\n  ".join(errors)
    if healed: return True, "Автолечение:\n  " + "\n  ".join(healed)
    return True, "Баланс скобок абсолютно корректен"
if __name__ == "__main__":
    ok, msg = run_check(os.getcwd()); print(f"Result: {ok} -> {msg}")
