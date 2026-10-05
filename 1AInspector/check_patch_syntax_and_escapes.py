# check_patch_syntax_and_escapes.py
import os, re
def run_check(root_dir: str) -> tuple[bool, str]:
    kt_dir = os.path.normpath(os.path.join(root_dir, "app/src/main/java"))
    if not os.path.exists(kt_dir): return False, "app/src/main/java не найдена"
    healed = []
    for root, _, files in os.walk(kt_dir):
        for file in files:
            if not file.endswith(".kt"): continue
            p = os.path.join(root, file)
            with open(p, "r", encoding="utf-8", errors="replace") as f: c = f.read()
            pattern = r'Regex\("([^"\n]*\\[.dwsSnbB][^"\n]*)"\)'
            if re.search(pattern, c):
                new_c = re.sub(pattern, lambda m: f'Regex("""{m.group(1)}""")', c)
                with open(p, "w", encoding="utf-8", newline="\n") as f: f.write(new_c)
                healed.append(file)
    if healed: return True, f"[INFO] Нормализованы Regex-литералы в: {', '.join(healed)}"
    return True, "Строковые литералы Regex валидированы"
if __name__ == "__main__":
    ok, msg = run_check(os.getcwd()); print(f"Result: {ok} -> {msg}")
