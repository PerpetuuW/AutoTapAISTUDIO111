import os
import sys
import subprocess
import re
import json

# Конфигурация вывода консоли Windows
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

def get_git_root() -> str:
    try:
        res = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            capture_output=True,
            text=True,
            check=True
        )
        return os.path.normpath(res.stdout.strip())
    except Exception:
        return os.path.normpath(os.path.dirname(os.path.abspath(__file__)))

def copy_to_windows_clipboard(text: str) -> bool:
    if sys.platform != "win32":
        return False
    try:
        subprocess.run(
            ["clip"],
            input=text.encode("utf-16le"),
            check=True
        )
        return True
    except Exception:
        return False

def parse_kotlin_signatures(file_path: str, max_lines_per_file: int = 5000) -> list[str]:
    """
    Извлекает Tier-1 сигнатуры (структуру типов, функций и свойств)
    без тел методов и блочной реализации.
    """
    signatures = []
    
    # Регулярные выражения для выявления объявлений верхнего уровня и членов классов
    re_package = re.compile(r"^\s*package\s+([a-zA-Z0-9_.]+)")
    re_decl = re.compile(
        r"^\s*(?:(?:public|protected|private|internal|abstract|open|final|override|sealed|data|inline|external)\s+)*"
        r"(class|interface|object|enum\s+class)\s+([A-Za-z0-9_]+)(?:<[^>]+>)?(?:\s*\([^)]*\))?(?:\s*:\s*[^{]+)?"
    )
    re_fun = re.compile(
        r"^\s*(?:(?:public|protected|private|internal|abstract|open|final|override|inline|suspend|operator)\s+)*"
        r"fun\s+(?:<[^>]+>\s+)?(?:[A-Za-z0-9_<>.]+\.)?([A-Za-z0-9_]+)\s*\(([^)]*)\)(?:\s*:\s*([^{=\n]+))?"
    )
    re_prop = re.compile(
        r"^\s*(?:(?:public|protected|private|internal|abstract|open|final|override|const)\s+)*"
        r"(val|var)\s+([A-Za-z0-9_]+)(?:\s*:\s*([^{=\n]+))?"
    )

    try:
        with open(file_path, "r", encoding="utf-8", errors="replace") as f:
            for line_idx, raw_line in enumerate(f, start=1):
                if line_idx > max_lines_per_file:
                    signatures.append(f"    ... [Лимит {max_lines_per_file} строк исчерпан]")
                    break
                
                line = raw_line.rstrip()
                stripped = line.strip()
                if not stripped or stripped.startswith("//") or stripped.startswith("/*") or stripped.startswith("*"):
                    continue

                pkg_match = re_package.match(stripped)
                if pkg_match:
                    signatures.append(f"  [PACKAGE] {pkg_match.group(1)}")
                    continue

                decl_match = re_decl.match(line)
                if decl_match:
                    kind = decl_match.group(1)
                    name = decl_match.group(2)
                    indent = len(line) - len(line.lstrip())
                    spc = " " * (indent + 2)
                    signatures.append(f"{spc}[L{line_idx:04d}] {kind} {name}")
                    continue

                fun_match = re_fun.match(line)
                if fun_match:
                    fname = fun_match.group(1)
                    params = fun_match.group(2).strip()
                    ret_type = fun_match.group(3).strip() if fun_match.group(3) else "Unit"
                    indent = len(line) - len(line.lstrip())
                    spc = " " * (indent + 2)
                    # Компактное представление параметров
                    if len(params) > 60:
                        params = params[:57] + "..."
                    signatures.append(f"{spc}[L{line_idx:04d}] fun {fname}({params}): {ret_type}")
                    continue

                prop_match = re_prop.match(line)
                if prop_match:
                    kind = prop_match.group(1)
                    pname = prop_match.group(2)
                    ptype = prop_match.group(3).strip() if prop_match.group(3) else "?"
                    indent = len(line) - len(line.lstrip())
                    spc = " " * (indent + 2)
                    signatures.append(f"{spc}[L{line_idx:04d}] {kind} {pname}: {ptype}")
                    continue

    except Exception as ex:
        signatures.append(f"    [ОШИБКА ЧТЕНИЯ: {ex}]")

    return signatures

def scan_manifest(manifest_path: str) -> list[str]:
    results = []
    if not os.path.isfile(manifest_path):
        return ["[AndroidManifest.xml не найден]"]
    
    results.append(f"[MANIFEST] {manifest_path}")
    re_perm = re.compile(r'<uses-permission\s+android:name="([^"]+)"')
    re_service = re.compile(r'<service\s+[^>]*android:name="([^"]+)"')
    re_activity = re.compile(r'<activity\s+[^>]*android:name="([^"]+)"')

    try:
        with open(manifest_path, "r", encoding="utf-8", errors="replace") as f:
            content = f.read()
            perms = re_perm.findall(content)
            services = re_service.findall(content)
            activities = re_activity.findall(content)

            if perms:
                results.append("  Permissions:")
                for p in perms:
                    results.append(f"    - {p}")
            if services:
                results.append("  Services:")
                for s in services:
                    results.append(f"    - {s}")
            if activities:
                results.append("  Activities:")
                for a in activities:
                    results.append(f"    - {a}")
    except Exception as ex:
        results.append(f"  [Ошибка парсинга манифеста: {ex}]")

    return results

def scan_immune_system(root_dir: str) -> list[str]:
    results = []
    ledger_path = os.path.join(root_dir, "1AAutomation", ".defect_ledger.json")
    inspector_dir = os.path.join(root_dir, "1AInspector")

    results.append("================================================================================")
    results.append("КИБЕРНЕТИЧЕСКИЙ ИММУНИТЕТ (1AAutomation / 1AInspector)")
    results.append("================================================================================")

    if os.path.isfile(ledger_path):
        try:
            with open(ledger_path, "r", encoding="utf-8") as f:
                data = json.load(f)
                defects = data if isinstance(data, list) else data.get("defects", [])
                results.append(f"Зарегистрировано дефектов в .defect_ledger.json: {len(defects)}")
                for d in defects:
                    d_id = d.get("id", "UNKNOWN")
                    d_desc = d.get("description", "")
                    d_checker = d.get("checker_file", "")
                    results.append(f"  - [{d_id}] {d_desc} (checker: {d_checker})")
        except Exception as ex:
            results.append(f"[Ошибка чтения реестра дефектов: {ex}]")
    else:
        results.append("[.defect_ledger.json не обнаружен]")

    if os.path.isdir(inspector_dir):
        checkers = [f for f in os.listdir(inspector_dir) if f.startswith("check_") and f.endswith(".py")]
        results.append(f"Физических чекеров в 1AInspector/: {len(checkers)}")
        for c in sorted(checkers):
            results.append(f"  * {c}")
    else:
        results.append("[Директория 1AInspector/ не обнаружена]")

    return results

def main():
    root_dir = get_git_root()
    buffer = []

    buffer.append("================================================================================")
    buffer.append(f"АРХИТЕКТУРНЫЙ АУДИТ ПРОЕКТА: {os.path.basename(root_dir)}")
    buffer.append(f"Корневая директория: {root_dir}")
    buffer.append("================================================================================")

    # 1. Анализ Git
    try:
        git_status = subprocess.run(
            ["git", "status", "--short"],
            cwd=root_dir,
            capture_output=True,
            text=True,
            check=True
        ).stdout.strip()
        buffer.append("\n[GIT STATUS - ТЕКУЩЕЕ СОСТОЯНИЕ РАБОЧЕГО ДЕРЕВА]")
        buffer.append(git_status if git_status else "Рабочее дерево чисто (Clean Working Tree)")
    except Exception as ex:
        buffer.append(f"\n[GIT STATUS] Ошибка вызова: {ex}")

    # 2. Манифесты Android
    buffer.append("\n================================================================================")
    buffer.append("КОМПОНЕНТЫ ANDROID (MANIFEST SCAN)")
    buffer.append("================================================================================")
    manifest_paths = []
    for cur_root, _, files in os.walk(root_dir):
        if "build" in cur_root.split(os.sep):
            continue
        if "AndroidManifest.xml" in files:
            manifest_paths.append(os.path.join(cur_root, "AndroidManifest.xml"))
    
    if manifest_paths:
        for m_path in manifest_paths:
            rel_m = os.path.relpath(m_path, root_dir)
            buffer.extend(scan_manifest(m_path))
    else:
        buffer.append("AndroidManifest.xml не найден в дереве проекта.")

    # 3. Инспекция исходного кода (Kotlin / Java)
    buffer.append("\n================================================================================")
    buffer.append("РЕЕСТР СИГНАТУР ИСХОДНОГО КОДА (TIER-1 SIGNATURE AUDIT)")
    buffer.append("================================================================================")
    
    target_dirs = ["app/src", "src"]
    scanned_files_count = 0

    for cur_root, dirs, files in os.walk(root_dir):
        # Исключаем артефакты сборки и системные каталоги
        parts = cur_root.split(os.sep)
        if any(p in parts for p in ["build", ".git", ".gradle", "1AAutomation", "1AInspector", ".idea"]):
            continue

        kt_files = [f for f in files if f.endswith(".kt") or f.endswith(".java")]
        for f in sorted(kt_files):
            file_abs = os.path.join(cur_root, f)
            rel_file = os.path.relpath(file_abs, root_dir).replace("\\", "/")
            buffer.append(f"\n--- [FILE] {rel_file} ---")
            sigs = parse_kotlin_signatures(file_abs)
            if sigs:
                buffer.extend(sigs)
            else:
                buffer.append("    (Нет публичных сигнатур или пустой файл)")
            scanned_files_count += 1

    buffer.append(f"\nВсего проиндексировано файлов исходного кода: {scanned_files_count}")

    # 4. Система кибернетического иммунитета
    buffer.extend(scan_immune_system(root_dir))

    full_output = "\n".join(buffer)

    # Вывод в терминал
    print(full_output)

    # Экспорт в системный буфер обмена Windows (UTF-16LE)
    copied = copy_to_windows_clipboard(full_output)
    print("\n================================================================================")
    if copied:
        print("[РЕЗУЛЬТАТ] Отчет успешно скопирован в системный буфер обмена Windows (clip).")
    else:
        print("[ИНФО] Экспорт в Windows clip не произведен (не Windows-среда или сбой вызова).")
    print("Zero-Disk Pollution сохранен: промежуточные отчеты на диск не записывались.")
    print("================================================================================")

if __name__ == "__main__":
    main()