# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str) -> tuple[bool, str]:
    p_orch = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "orchestrator", "AutoTapOrchestrator.kt")
    p_dialog = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "dialog", "EditActionDialog.kt")

    if not os.path.exists(p_orch) or not os.path.exists(p_dialog):
        return False, "[CRITICAL] Файлы исходного кода не найдены."

    with open(p_orch, "r", encoding="utf-8") as f: content_orch = f.read()
    with open(p_dialog, "r", encoding="utf-8") as f: content_dialog = f.read()

    if "wasExecutingBeforeStop" not in content_orch:
        return False, "[FAIL] Предложение графа не привязано к остановке исполнения (wasExecutingBeforeStop)."

    if "mergeTemplateBitmaps" not in content_dialog:
        return False, "[FAIL] В диалоге отсутствует метод mergeTemplateBitmaps."

    return True, "[PASS] Архитектурный контракт остановки выполнения и объединения соблюден."

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
