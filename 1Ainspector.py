#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1AInspector.py — Фасадный раннер кибернетического иммунитета AutoTap (v17.0)
Автоматический аудит кодовой базы, валидация контрактов, кросс-проверка реестра дефектов
и гарантированное немедленное завершение процессов (без зависания в отладчике VS Code).
"""

import os
import sys
import time
import json
import subprocess
import importlib.util

# Безопасная настройка вывода консоли Windows без предупреждений Pylance
if sys.platform == "win32":
    try:
        getattr(sys.stdout, "reconfigure", lambda **kw: None)(encoding="utf-8")
        getattr(sys.stderr, "reconfigure", lambda **kw: None)(encoding="utf-8")
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
        subprocess.run(["clip"], input=text.encode("utf-16le"), check=True)
        return True
    except Exception:
        return False

def verify_defect_ledger(root_dir: str, available_checkers: set) -> tuple[bool, list[str]]:
    ledger_path = os.path.join(root_dir, "1AAutomation", ".defect_ledger.json")
    issues = []
    
    if not os.path.isfile(ledger_path):
        return False, ["Реестр .defect_ledger.json отсутствует на диске!"]

    try:
        with open(ledger_path, "r", encoding="utf-8") as f:
            raw_data = json.load(f)
        
        defects = []
        if isinstance(raw_data, list):
            defects = raw_data
        elif isinstance(raw_data, dict):
            defects = raw_data.get("defects", [])
            if not defects and "id" in raw_data:
                defects = [raw_data]

        for d in defects:
            d_id = d.get("id") or d.get("title") or "UNKNOWN_DEFECT"
            c_file = d.get("checker_file") or d.get("checker") or ""
            
            if not c_file:
                issues.append(f"Дефект [{d_id}] не имеет привязанного чекера (пустое поле checker_file)")
            elif c_file not in available_checkers:
                issues.append(f"Дефект [{d_id}] ссылается на отсутствующий чекер: {c_file}")

    except Exception as ex:
        return False, [f"Ошибка парсинга .defect_ledger.json: {ex}"]

    return len(issues) == 0, issues

def run_all_inspectors():
    start_total_time = time.perf_counter()
    root_dir = get_git_root()
    inspector_dir = os.path.join(root_dir, "1AInspector")

    buffer = []
    buffer.append("================================================================================")
    buffer.append("КОНВЕЙЕР КИБЕРНЕТИЧЕСКОГО ИММУНИТЕТА: 1AInspector (v17.0)")
    buffer.append(f"Корень проекта: {root_dir}")
    buffer.append("================================================================================")

    if not os.path.isdir(inspector_dir):
        msg = f"[КРИТИЧЕСКИЙ СБОЙ] Директория чекеров не найдена: {inspector_dir}"
        print(msg, flush=True)
        os._exit(1)

    checker_files = sorted([f for f in os.listdir(inspector_dir) if f.startswith("check_") and f.endswith(".py")])
    available_set = set(checker_files)

    buffer.append(f"Обнаружено физических модулей проверок: {len(checker_files)}")
    buffer.append("--------------------------------------------------------------------------------")

    passed_count = 0
    failed_count = 0
    crashed_count = 0
    failed_reports = []

    for fname in checker_files:
        fpath = os.path.join(inspector_dir, fname)
        t0 = time.perf_counter()
        
        try:
            spec = importlib.util.spec_from_file_location(fname[:-3], fpath)
            if spec is None or spec.loader is None:
                crashed_count += 1
                failed_reports.append((fname, "CRASH", "Не удалось загрузить спецификацию модуля (spec is None)"))
                buffer.append(f"  [CRASH] {fname:<44} (0.0 ms)")
                continue

            mod = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(mod)

            if not hasattr(mod, "run_check"):
                crashed_count += 1
                failed_reports.append((fname, "CRASH", "Отсутствует обязательная функция контракта run_check(root_dir)"))
                buffer.append(f"  [CRASH] {fname:<44} (0.0 ms)")
                continue

            # Исполнение контракта проверки
            is_ok, status_msg = mod.run_check(root_dir)
            elapsed_ms = (time.perf_counter() - t0) * 1000

            if is_ok:
                passed_count += 1
                buffer.append(f"  [PASS]  {fname:<44} ({elapsed_ms:5.1f} ms)")
            else:
                failed_count += 1
                failed_reports.append((fname, "FAIL", status_msg))
                buffer.append(f"  [FAIL]  {fname:<44} ({elapsed_ms:5.1f} ms)")

        except Exception as ex:
            elapsed_ms = (time.perf_counter() - t0) * 1000
            crashed_count += 1
            failed_reports.append((fname, "CRASH", f"Исключение: {ex}"))
            buffer.append(f"  [CRASH] {fname:<44} ({elapsed_ms:5.1f} ms)")

    # Верификация связности реестра дефектов
    buffer.append("--------------------------------------------------------------------------------")
    buffer.append("ВАЛИДАЦИЯ СВЯЗНОСТИ РЕЕСТРА ДЕФЕКТОВ (.defect_ledger.json):")
    ledger_ok, ledger_issues = verify_defect_ledger(root_dir, available_set)

    if ledger_ok:
        buffer.append("  [PASS]  Все зарегистрированные дефекты имеют физические чекеры на диске.")
    else:
        buffer.append("  [FAIL]  Обнаружены расхождения в реестре дефектов:")
        for issue in ledger_issues:
            buffer.append(f"          ! {issue}")

    # Итоговый блок
    total_elapsed_ms = (time.perf_counter() - start_total_time) * 1000
    buffer.append("================================================================================")
    buffer.append(f"ИТОГИ ИНСПЕКЦИИ: {passed_count} пройдено | {failed_count} провалено | {crashed_count} сбоев | Время: {total_elapsed_ms:.1f} ms")
    buffer.append("================================================================================")

    if failed_reports:
        buffer.append("\nДЕТАЛИЗАЦИЯ ОБНАРУЖЕННЫХ ДЕФЕКТОВ:")
        for fname, tag, desc in failed_reports:
            buffer.append(f"  * [{tag}] {fname}")
            buffer.append(f"    Причина: {desc}")
        buffer.append("================================================================================")

    output_text = "\n".join(buffer)
    print(output_text)
    sys.stdout.flush()

    # Экспорт в системный буфер Windows
    copy_to_windows_clipboard(output_text)

    # Определение кода возврата
    exit_code = 0 if (failed_count == 0 and crashed_count == 0 and ledger_ok) else 1

    # Гарантированное безусловное завершение процесса ОС (без блокировки отладчиком VS Code)
    sys.stderr.flush()
    os._exit(exit_code)

if __name__ == "__main__":
    run_all_inspectors()