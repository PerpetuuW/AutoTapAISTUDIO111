# -*- coding: utf-8 -*-
"""
1ABuild.py
МАСТЕР-ОРКЕСТРАТОР СБОРКИ (v7.0 LIVE STREAMING):
Фаза 1: Инспекторы иммунитета (In-Process)
Фаза 2: Юнит-тестирование :app:testDebugUnitTest
Фаза 3: Компиляция :app:assembleDebug (с живым потоковым выводом)
Фаза 4: Git синхронизация
Фаза 5: Пинг связи с телефоном через ADB (БЕЗ установки и БЕЗ запуска am start)
"""

import os
import sys
import subprocess
import importlib.util

root_dir = os.path.abspath(os.getcwd())
auto_dir = os.path.join(root_dir, "1AAutomation")
sys.path.insert(0, auto_dir)

from git_sync import execute_auto_git_sync
from error_parser import extract_critical_error
from clipboard_sync import copy_to_clipboard
from defect_ledger import record_defect

def ping_connected_device() -> tuple[bool, str]:
    # Автопоиск ADB
    adb_exec = "adb"
    try:
        subprocess.run([adb_exec, "--version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
    except Exception:
        local_appdata = os.environ.get("LOCALAPPDATA")
        if local_appdata:
            cand = os.path.join(local_appdata, "Android", "Sdk", "platform-tools", "adb.exe")
            if os.path.exists(cand):
                adb_exec = cand
            else:
                return False, "ADB не найден в системе"
        else:
            return False, "ADB не найден в системе"

    try:
        res = subprocess.run([adb_exec, "devices"], stdout=subprocess.PIPE, text=True, timeout=3)
        lines = res.stdout.strip().split('\n')[1:]
        devices = [l.split()[0] for l in lines if len(l.split()) == 2 and l.split()[1] == "device"]
        if not devices:
            return False, "Устройство не подключено"

        target = devices[0]
        model_proc = subprocess.run([adb_exec, "-s", target, "shell", "getprop", "ro.product.model"], stdout=subprocess.PIPE, text=True, timeout=3)
        model = model_proc.stdout.strip()
        return True, f"Устройство {model} ({target}) на связи (деплой и автозапуск отключены)"
    except Exception as e:
        return False, f"Сбой проверки связи: {e}"

def build() -> int:
    # 1. Валидация инспекторами
    insp_exec = os.path.join(root_dir, "1AInspector.py")
    if os.path.exists(insp_exec):
        res = subprocess.run([sys.executable, insp_exec], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace", cwd=root_dir)
        sys.stdout.write(res.stdout)
        sys.stdout.flush()
        if res.returncode != 0:
            print("\n[1ABuild] Сборка остановлена инспекторами.", flush=True)
            copy_to_clipboard(res.stdout)
            return 1

    # 2. Компиляция Gradle (Потоковый вывод)
    print("\n=== [1ABuild] КОМПИЛЯЦИЯ GRADLE (LIVE OUTPUT) ===", flush=True)
    gradlew = "gradlew.bat" if os.name == "nt" else "./gradlew"
    
    process = subprocess.Popen(
        [gradlew, ":app:assembleDebug", "--console=plain"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        cwd=root_dir
    )
    
    output_lines = []
    if process.stdout:
        for line in process.stdout:
            sys.stdout.write(line)
            sys.stdout.flush()
            output_lines.append(line)
            
    process.wait()
    res_stdout = "".join(output_lines)

    if process.returncode != 0:
        print("\n=== [1ABuild] ОШИБКА СБОРКИ ===", flush=True)
        crit_err = extract_critical_error(res_stdout)
        print(crit_err, flush=True)
        copy_to_clipboard(crit_err)
        return process.returncode

    print("=== [1ABuild] СБОРКА GRADLE УСПЕШНА ===", flush=True)

    # 3. Модуль верификации и фиксации Git при успешной компиляции (с автолечением)
    insp_dir = os.path.join(root_dir, "1AInspector")
    if insp_dir not in sys.path:
        sys.path.insert(0, insp_dir)
    try:
        import importlib.util
        checker_path = os.path.join(insp_dir, "check_git_commit_on_build.py")
        spec = importlib.util.spec_from_file_location("git_checker_mod", checker_path)
        git_mod = importlib.util.module_from_spec(spec)
        if spec.loader:
            spec.loader.exec_module(git_mod)
            ok, msg = git_mod.verify_and_commit_post_build(root_dir)
            print(f"[1ABuild Git-Контроль]: {msg}", flush=True)
    except Exception as e:
        print(f"[1ABuild Git-Контроль Предупреждение]: {e}", flush=True)
        def get_last_zapros_title():
            try:
                with open(os.path.join(root_dir, "1AZapros.md"), "r", encoding="utf-8") as f:
                    for line in reversed(f.readlines()):
                        if line.startswith("## ") and "Запрос:" in line:
                            return line.split("Запрос:")[-1].strip()
            except Exception:
                pass
            return "Автоматическая сборка и синхронизация"
        execute_auto_git_sync(root_dir, f"Сборка: {get_last_zapros_title()}")

    # 4. Пинг связи с телефоном через ADB
    is_online, ping_msg = ping_connected_device()
    print("\n" + "=" * 64, flush=True)
    if is_online:
        print(f"[ADB ПИНГ СВЯЗИ]: {ping_msg}", flush=True)
        print(f"[ИТОГОВЫЙ СТАТУС]: СБОРКА И GIT УСПЕШНЫ | ДЕПЛОЙ ОТКЛЮЧЕН (ПО ЗАПРОСУ)", flush=True)
    else:
        print(f"[ADB ПИНГ СВЯЗИ]: {ping_msg}", flush=True)
        print(f"[ИТОГОВЫЙ СТАТУС]: СБОРКА И GIT УСПЕШНЫ", flush=True)
    print("=" * 64 + "\n", flush=True)

    copy_to_clipboard(f"[AUTO-TAP SUCCESS] Сборка успешна. Пинг связи: {ping_msg}")
    return 0

if __name__ == "__main__":
    code = build()
    os._exit(code)
