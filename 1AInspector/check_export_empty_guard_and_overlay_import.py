# check_export_empty_guard_and_overlay_import.py
# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    package_backup = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "storage", "PackageBackupManager.kt")
    scripts_dialog = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "dialog", "ScriptsDialog.kt")
    main_activity = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "presentation", "main", "MainActivity.kt")

    if not os.path.exists(package_backup) or not os.path.exists(scripts_dialog) or not os.path.exists(main_activity):
        return False, "Целевые файлы не найдены"

    with open(package_backup, "r", encoding="utf-8") as f:
        pbm_content = f.read()
    with open(scripts_dialog, "r", encoding="utf-8") as f:
        sd_content = f.read()
    with open(main_activity, "r", encoding="utf-8") as f:
        ma_content = f.read()

    # Проверка 1: Защита от создания пустого архива <= 22 байт
    if "zipFile.length() <= 22L" not in pbm_content:
        return False, "PackageBackupManager: отсутствует проверка на пустой ZIP архив (<= 22 байт)"

    # Проверка 2: Предупреждение о пустом экспорте в оверлее
    if "Нет сценариев и шаблонов для экспорта" not in sd_content:
        return False, "ScriptsDialog: отсутствует валидация и уведомление о пустом экспорте"

    # Проверка 3: Вызов checkIntentForImport в onCreate
    if "checkIntentForImport(intent)" not in ma_content:
        return False, "MainActivity: отсутствует checkIntentForImport в onCreate"

    return True, "Защита пустого экспорта и SAF-трамплин импорта из оверлея валидированы"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath("."))
    print(f"Status: {passed}, Msg: {msg}")
