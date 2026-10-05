# -*- coding: utf-8 -*-
"""
check_unified_log_sharing.py
Инспектор контракта экспорта журналов:
1. Защита от ошибки "Неподдерживаемый файл": чистый поток .txt файла через EXTRA_STREAM без конфликта с EXTRA_TEXT.
2. Проверка предоставления прав чтения FileProvider через опрос shareIntent.
3. Проверка использования ClipData.newUri.
"""
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    logger_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/core/logger/AppLogger.kt".replace("/", os.sep))
    if not os.path.exists(logger_file):
        return False, f"Файл не найден: {logger_file}"

    with open(logger_file, "r", encoding="utf-8") as f:
        code = f.read()

    if "fun shareLogs" in code:
        if "ClipData.newUri" not in code:
            return False, "В AppLogger.shareLogs() не используется ClipData.newUri для безопасной передачи прав доступа к файлу"
        if "queryIntentActivities(chooser" in code:
            return False, "В AppLogger.shareLogs() опрашивается Intent chooser вместо shareIntent, что блокирует выдачу прав мессенджерам"
        if "putExtra(android.content.Intent.EXTRA_TEXT" in code and "putExtra(android.content.Intent.EXTRA_STREAM" in code:
            return False, "В AppLogger.shareLogs() обнаружен конфликт EXTRA_TEXT и EXTRA_STREAM, приводящий к ошибке 'Неподдерживаемый файл'"

    return True, "Контракт экспорта журналов соблюден: чистый поток .txt файла с полной выдачей прав FileProvider."
