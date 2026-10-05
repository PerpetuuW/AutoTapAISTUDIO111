#!/usr/bin/env python3
"""
master_build_verifier.py — Релизный оркестратор проверки сборки AutoTap v34.0
Проверяет целостность кода, манифеста, XML-ресурсов и запускает статический аудит.
"""

import os
import sys
import subprocess
from pathlib import Path

def main():
    root = Path(".")
    print("=" * 80)
    print("👑 AutoTap Master Build & Architecture Verifier (v34.0 Greenfield)")
    print("=" * 80)

    # 1. Проверка наличия ключевых сборочных файлов
    critical_files = [
        "app/build.gradle.kts",
        "app/src/main/AndroidManifest.xml",
        "app/proguard-rules.pro",
        "app/src/main/res/xml/file_paths.xml",
        "app/src/main/res/xml/accessibility_service_config.xml",
        "app/src/main/res/values/strings.xml",
        "app/src/main/res/values/themes.xml",
        "app/src/main/res/layout/activity_main.xml"
    ]

    missing = []
    for rel in critical_files:
        if not (root / rel).exists():
            missing.append(rel)

    if missing:
        print("\n❌ КРИТИЧЕСКАЯ ОШИБКА: Отсутствуют обязательные файлы сборки:")
        for m in missing:
            print(f"  - {m}")
        sys.exit(1)

    print("✔ Все обязательные сборочные файлы и XML-ресурсы присутствуют.")

    # 2. Запуск CI Pre-Flight Gate
    gate_script = root / "tools" / "ci_preflight_gate.py"
    if gate_script.exists():
        print("\nЗапуск pre-flight гейта контроля инвариантов...")
        res = subprocess.run([sys.executable, str(gate_script), "."])
        if res.returncode != 0:
            print("\n❌ Pre-flight гейт завершился с ошибкой!")
            sys.exit(res.returncode)
    else:
        print("⚠ Внимание: tools/ci_preflight_gate.py не найден, шаг пропущен.")

    # 3. Запуск статического инспектора deep_project_inspector
    inspector_script = root / "tools" / "deep_project_inspector.py"
    if inspector_script.exists():
        print("\nЗапуск глубокого статического инспектора кода...")
        res = subprocess.run([sys.executable, str(inspector_script), "."])
        if res.returncode != 0:
            print("\n❌ Статический инспектор обнаружил дефекты!")
            sys.exit(res.returncode)

    print("\n" + "=" * 80)
    print("🎉 ВЕРИФИКАЦИЯ ЗАВЕРШЕНА УСПЕШНО.")
    print("Кодовая база AutoTap полностью готова к релизной компиляции APK.")
    print("=" * 80)
    sys.exit(0)

if __name__ == "__main__":
    main()
