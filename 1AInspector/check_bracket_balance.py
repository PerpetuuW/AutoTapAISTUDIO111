import os

def run_check(root_dir: str) -> tuple[bool, str]:
    target_dir = os.path.join(root_dir, "app/src/main/java")
    if not os.path.exists(target_dir):
        return True, "[INFO] Исходники Kotlin не найдены"
    
    issues = []
    for root, dirs, files in os.walk(target_dir):
        for file in files:
            if file.endswith(".kt"):
                path = os.path.join(root, file)
                with open(path, "r", encoding="utf-8") as f:
                    content = f.read()
                
                # Строгий лексический парсер: игнорируем скобки внутри строк, символов и комментариев
                open_b, close_b = 0, 0
                in_str, in_char, in_line, in_block, esc = False, False, False, False, False
                i, n = 0, len(content)
                while i < n:
                    c = content[i]
                    if in_line:
                        if c == '\n': in_line = False
                    elif in_block:
                        if c == '*' and i + 1 < n and content[i+1] == '/':
                            in_block = False; i += 1
                    elif in_str:
                        if esc: esc = False
                        elif c == '\\': esc = True
                        elif c == '"': in_str = False
                    elif in_char:
                        if esc: esc = False
                        elif c == '\\': esc = True
                        elif c == "'": in_char = False
                    else:
                        if c == '/' and i + 1 < n and content[i+1] == '/':
                            in_line = True; i += 1
                        elif c == '/' and i + 1 < n and content[i+1] == '*':
                            in_block = True; i += 1
                        elif c == '"': in_str = True
                        elif c == "'": in_char = True
                        elif c == '{': open_b += 1
                        elif c == '}': close_b += 1
                    i += 1

                if open_b != close_b:
                    issues.append(f"{file} (Открыто: {open_b}, Закрыто: {close_b})")
    
    if issues:
        return False, f"[FAIL] Нарушение баланса фигурных скобок:\n" + "\n".join(issues)
    return True, "[OK] Баланс фигурных скобок Kotlin идеален"
