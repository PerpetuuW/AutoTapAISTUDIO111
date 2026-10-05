# -*- coding: utf-8 -*-
"""
1AAutomation/patch_engine.py
Локальное ядро детерминированной модификации кода, двухканального аудита и иммунитета.
"""
import json
import os
import subprocess
import sys
from datetime import datetime

def copy_to_windows_clipboard(text: str):
    try:
        subprocess.run(["powershell", "-NoProfile", "-Command", "$input | Set-Clipboard"], input=text, text=True, encoding="utf-8", check=True)
        return
    except Exception: pass
    try:
        clip_exe = os.path.join(os.environ.get("SystemRoot", r"C:\Windows"), "System32", "clip.exe")
        proc = subprocess.Popen([clip_exe], stdin=subprocess.PIPE)
        proc.communicate(text.encode("utf-16le"))
    except Exception as e:
        print(f"[INFO] Буфер обмена: {e}")

def get_scope_metadata(lines, target_line_idx):
    scope_class, enclosing_method, members = "Unknown", "Unknown", []
    for i in range(min(target_line_idx, len(lines))):
        line = lines[i].strip()
        if line.startswith("class ") or " class " in line or line.startswith("object "):
            scope_class = line.split("{")[0].strip()
        elif line.startswith("val ") or line.startswith("var "):
            if "=" in line or ":" in line:
                members.append(line.split("=")[0].split("{")[0].strip())
        elif line.startswith("fun ") or " fun " in line:
            enclosing_method = line.split("{")[0].strip()
    return f"[SCOPE] {scope_class}\n[MEMBERS] {', '.join(members[-4:]) if members else 'None'}\n[ENCLOSING] {enclosing_method}"

def read_slice(file_path: str, start_line: int, end_line: int) -> str:
    if not os.path.exists(file_path):
        return f"File not found: {file_path}\n"
    with open(file_path, "r", encoding="utf-8", errors="replace") as f:
        lines = f.readlines()
    start_idx, end_idx = max(0, start_line - 1), min(len(lines), end_line)
    header = get_scope_metadata(lines, start_idx)
    result = [f"\n=== [FILE] {os.path.basename(file_path)} (Lines {start_line}-{end_line}) ===", header, "--- CODE SLICE ---"]
    for idx in range(start_idx, end_idx):
        result.append(f"L{idx+1:04d}: {lines[idx].rstrip()}")
    result.append("------------------\n")
    return "\n".join(result)

def norm_l1(s: str) -> str:
    return " ".join(s.strip().split())

def apply_signature_diff(file_path: str, search_block: str, replace_block: str) -> bool:
    if not os.path.exists(file_path):
        print(f"[ERROR] Файл не найден: {file_path}")
        return False
    with open(file_path, "r", encoding="utf-8") as f:
        file_content = f.read()

    file_lines = file_content.splitlines()
    search_lines = [norm_l1(l) for l in search_block.splitlines() if l.strip()]
    if not search_lines:
        return False

    non_empty_file_lines, file_indices = [], []
    for idx, line in enumerate(file_lines):
        if line.strip():
            non_empty_file_lines.append(norm_l1(line))
            file_indices.append(idx)

    k_len = len(search_lines)
    matches = [(file_indices[i], file_indices[i + k_len - 1]) for i in range(len(non_empty_file_lines) - k_len + 1) if non_empty_file_lines[i:i + k_len] == search_lines]

    if not matches:
        cand_start = [file_indices[i] for i, l in enumerate(non_empty_file_lines) if l == search_lines[0]]
        cand_end = [file_indices[i] for i, l in enumerate(non_empty_file_lines) if l == search_lines[-1]]
        if len(cand_start) == 1 and len(cand_end) == 1 and cand_start[0] < cand_end[0]:
            if cand_end[0] - cand_start[0] <= len(search_lines) + 30:
                matches.append((cand_start[0], cand_end[0]))

    if not matches:
        replace_lines = [norm_l1(l) for l in replace_block.splitlines() if l.strip()]
        rep_k = len(replace_lines)
        if sum(1 for i in range(len(non_empty_file_lines) - rep_k + 1) if non_empty_file_lines[i:i + rep_k] == replace_lines) >= 1:
            print(f"[INFO] Изменения уже применены в {os.path.basename(file_path)}")
            return True
        print(f"[ERROR] Блок поиска не найден в {os.path.basename(file_path)}")
        return False

    if len(matches) > 1:
        print(f"[ERROR] Неоднозначность (Ambiguity) в {os.path.basename(file_path)}")
        return False

    start_match_idx, end_match_idx = matches[0]

    target_indent = ""
    for ch in file_lines[start_match_idx]:
        if ch in (" ", "\t"): target_indent += ch
        else: break

    replace_lines = replace_block.splitlines()
    base_replace_indent = next((line[:len(line)-len(line.lstrip())] for line in replace_lines if line.strip()), "")

    formatted_replace = [target_indent + (line[:len(line)-len(line.lstrip())][len(base_replace_indent):] if line.startswith(base_replace_indent) else "") + line.lstrip() if line.strip() else "" for line in replace_lines]

    new_file_lines = file_lines[:start_match_idx] + formatted_replace + file_lines[end_match_idx + 1:]
    with open(file_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(new_file_lines) + "\n")
    print(f"[SUCCESS] Патч применен: {os.path.basename(file_path)}")
    return True

def record_defect_canonical(root_dir: str, defect_id: str, description: str, target_file: str, checker_file: str):
    auto_dir = os.path.join(root_dir, "1AAutomation")
    ledger_path = os.path.join(auto_dir, ".defect_ledger.json")
    ledger = {"defects": []}
    if os.path.exists(ledger_path):
        with open(ledger_path, "r", encoding="utf-8") as f:
            try:
                data = json.load(f)
                ledger = data if isinstance(data, dict) and "defects" in data else {"defects": []}
            except Exception: pass

    entry = {
        "id": defect_id,
        "description": description,
        "target_file": target_file,
        "checker_module": checker_file,
        "checker_file": checker_file,
        "checker": checker_file,
        "status": "FIXED"
    }
    ledger["defects"] = [d for d in ledger.get("defects", []) if d.get("id") != entry["id"]] + [entry]
    with open(ledger_path, "w", encoding="utf-8") as f:
        json.dump(ledger, f, indent=4, ensure_ascii=False)
    print(f"[SUCCESS] Дефект {defect_id} зафиксирован в реестре (Контракт 0.7.1).")

def create_checker(root_dir: str, checker_filename: str, checker_code: str):
    inspector_dir = os.path.join(root_dir, "1AInspector")
    os.makedirs(inspector_dir, exist_ok=True)
    checker_path = os.path.join(inspector_dir, checker_filename)
    with open(checker_path, "w", encoding="utf-8") as f:
        f.write(checker_code)
    print(f"[SUCCESS] Чекер иммунитета создан: {checker_filename}")

def append_audit_log(root_dir: str, is_feature: bool, title: str, details: str):
    log_name = "1AZaprosFunc.md" if is_feature else "1AZapros.md"
    log_path = os.path.join(root_dir, log_name)
    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    entry = f"\n### [{now_str}] {title}\n{details.strip()}\n"
    with open(log_path, "a", encoding="utf-8") as f:
        f.write(entry)
    print(f"[SUCCESS] Журнал обновлен: {log_name}")


def commit_and_build(root_dir: str, commit_msg: str):
    try:
        subprocess.run(["git", "add", "."], cwd=root_dir, check=True)
        subprocess.run(["git", "commit", "-m", commit_msg], cwd=root_dir, check=True)
        print(f"[INFO] Коммит зафиксирован: {commit_msg}")
        build_script = os.path.join(root_dir, "1ABuild.py")
        if os.path.exists(build_script):
            print("\n[INFO] Запуск конвейера 1ABuild.py...\n")
            res = subprocess.run([sys.executable, build_script], cwd=root_dir)
            if res.returncode == 0:
                print("\n[INFO] Сборка и тесты успешны. Выполняем git push...\n")
                subprocess.run(["git", "push"], cwd=root_dir, check=False)
    except Exception as e:
        print(f"[WARNING] Git/Build: {e}")

class SmartPatcher:
    # Фасад обратной совместимости для декларативных замен
    @staticmethod
    def apply(file_path: str, search: str, replace: str) -> bool:
        return apply_signature_diff(file_path, search, replace)

class TransactionalPatcher:
    # Фасад обратной совместимости для транзакционных пакетов замен
    def __init__(self, root_dir: str = "."):
        self.root_dir = root_dir
        self.operations = []

    def stage(self, file_path: str, search: str, replace: str):
        self.operations.append((file_path, search, replace))

    def commit(self) -> bool:
        for file_path, search, replace in self.operations:
            if not apply_signature_diff(file_path, search, replace):
                return False
        return True
