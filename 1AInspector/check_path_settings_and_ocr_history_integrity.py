# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str):
    p_set = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "dialog", "GlobalSettingsDialog.kt")
    p_orch = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "orchestrator", "AutoTapOrchestrator.kt")
    p_rec = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "record", "GestureRecorderOverlay.kt")
    p_ead = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "dialog", "EditActionDialog.kt")

    for p in [p_set, p_orch, p_rec, p_ead]:
        if not os.path.exists(p):
            return False, f"[CRITICAL] Файл не найден: {p}"

    with open(p_set, "r", encoding="utf-8") as f:
        src = f.read()
    if "currentPathDuration: Long = 5000L" not in src:
        return False, "[FAIL] GlobalSettingsDialog: отсутствует настройка currentPathDuration 5000L"

    with open(p_orch, "r", encoding="utf-8") as f:
        src = f.read()
    if "globalPathDurationMs: Long = 5000L" not in src:
        return False, "[FAIL] AutoTapOrchestrator: отсутствует globalPathDurationMs 5000L"

    with open(p_rec, "r", encoding="utf-8") as f:
        src = f.read()
    if "if (!wasStreaming)" in src:
        return False, "[FAIL] GestureRecorderOverlay: блокировка воспроизведения пути на UP не снята"

    with open(p_ead, "r", encoding="utf-8") as f:
        src = f.read()
    if "btnChipGold" in src:
        return False, "[FAIL] EditActionDialog: базовые жесткие чипы OCR не удалены"
    if "recent_queries" not in src:
        return False, "[FAIL] EditActionDialog: адаптивная лента недавних поисков OCR не подключена"

    return True, "[PASS] Контракты длительности пути 5с, стабильной записи и истории OCR полностью соблюдены"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
