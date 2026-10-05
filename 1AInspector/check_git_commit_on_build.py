# -*- coding: utf-8 -*-
"""
1AInspector/check_git_commit_on_build.py
Модуль проверки фиксации Git-коммита при успешной компиляции.
Реализует принцип Bounded Auto-Remediation:
Если после успешной компиляции обнаружены незафиксированные файлы — модуль САМ
выполняет git add и git commit с динамическим заголовком из 1AZapros.md.
"""
import os
import sys
import subprocess

def get_last_zapros_title(root_dir: str) -> str:
    try:
        zapros_file = os.path.join(root_dir, "1AZapros.md")
        if os.path.exists(zapros_file):
            with open(zapros_file, "r", encoding="utf-8") as f:
                for line in reversed(f.readlines()):
                    if line.startswith("## ") and "Запрос:" in line:
                        return line.split("Запрос:")[-1].strip()
    except Exception:
        pass
    return "Автоматическая сборка и синхронизация"

def verify_and_commit_post_build(root_dir: str) -> tuple[bool, str]:
    """
    Пост-компиляционная верификация: вызывается строго после успешного завершения Gradle.
    Если рабочее дерево содержит незафиксированные изменения — модуль САМ делает коммит.
    """
    auto_dir = os.path.join(root_dir, "1AAutomation")
    if auto_dir not in sys.path:
        sys.path.insert(0, auto_dir)

    try:
        # Проверяем текущее состояние репозитория
        res = subprocess.run(["git", "status", "--porcelain"], cwd=root_dir, capture_output=True, text=True, timeout=15)
        status_lines = [l for l in res.stdout.strip().splitlines() if not l.strip().endswith(".pyc")]

        if not status_lines:
            return True, "Репозиторий чист. Изменения уже зафиксированы в Git."

        # Автолечение (Auto-Heal): модуль сам выполняет фиксацию
        title = get_last_zapros_title(root_dir)
        commit_message = f"Сборка: {title}"

        from git_sync import execute_auto_git_sync
        success = execute_auto_git_sync(root_dir, commit_message)

        if not success:
            return False, "Автолечение: не удалось выполнить execute_auto_git_sync"

        # Повторная проверка чистоты репозитория
        res_after = subprocess.run(["git", "status", "--porcelain"], cwd=root_dir, capture_output=True, text=True, timeout=15)
        remaining = [l for l in res_after.stdout.strip().splitlines() if not l.strip().endswith(".pyc")]

        if not remaining:
            return True, f"Автолечение успешно: модуль зафиксировал коммит '{commit_message}'"
        else:
            return False, f"Остались незафиксированные файлы: {', '.join(remaining[:3])}"

    except Exception as e:
        return False, f"Сбой модуля верификации Git: {e}"

def run_check(root_dir: str) -> tuple[bool, str]:
    """
    Стандартная проверка 1AInspector (вызывается из 1Ainspector.py на Фазе 1).
    Валидирует наличие вызова verify_and_commit_post_build в 1ABuild.py.
    """
    build_file = os.path.join(root_dir, "1ABuild.py")
    if not os.path.exists(build_file):
        build_file = os.path.join(root_dir, "1Abuild.py")
    if not os.path.exists(build_file):
        return False, f"Файл не найден: {build_file}"

    with open(build_file, "r", encoding="utf-8") as f:
        content = f.read()

    if "check_git_commit_on_build" in content:
        return True, "Контракт верификации Git-коммита при успешной сборке активен в 1ABuild.py"

    return False, "В 1ABuild.py отсутствует вызов модуля верификации Git-коммита (check_git_commit_on_build)"
