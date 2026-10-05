# -*- coding: utf-8 -*-
import os, sys
root_dir = os.path.abspath(os.path.dirname(__file__))
sys.path.insert(0, os.path.join(root_dir, "1AAutomation"))
from patch_engine import (
    apply_signature_diff, create_checker, record_defect_canonical, 
    append_audit_log, commit_and_build
)

print("[MODE: 1APATCH (EXECUTION - GET_INSTANCE PUBLIC ACCESS)]")

diffs = [
    (
        os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "record", "GestureRecorderOverlay.kt"),
        r"""                        lastRecordEventTime = System.currentTimeMillis()
                        val defaultPathDur = com.example.autotap.infrastructure.orchestrator.AutoTapOrchestrator.INSTANCE?.globalPathDurationMs ?: 5000L
                        recordedActions.add(MacroAction(""",
        r"""                        lastRecordEventTime = System.currentTimeMillis()
                        val defaultPathDur = com.example.autotap.infrastructure.orchestrator.AutoTapOrchestrator.getInstance(context).globalPathDurationMs
                        recordedActions.add(MacroAction("""
    )
]

success = True
for target_path, search_b, replace_b in diffs:
    if not apply_signature_diff(target_path, search_b, replace_b):
        success = False
        print(f"[CRITICAL] Сбой сопоставления в файле: {target_path}")
        break

if success:
    checker_code = r"""# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str):
    p_rec = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "record", "GestureRecorderOverlay.kt")
    if not os.path.exists(p_rec):
        return False, f"[CRITICAL] Файл не найден: {p_rec}"
        
    with open(p_rec, "r", encoding="utf-8") as f:
        src = f.read()
    if "AutoTapOrchestrator.INSTANCE" in src:
        return False, "[FAIL] В GestureRecorderOverlay осталось некорректное обращение к приватному свойству INSTANCE"
    if "AutoTapOrchestrator.getInstance(context)" not in src:
        return False, "[FAIL] В GestureRecorderOverlay отсутствует публичный вызов getInstance(context)"

    return True, "[PASS] Доступ к синглтону оркестратора полностью детерминирован"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
"""
    create_checker(root_dir, "check_orchestrator_singleton_access.py", checker_code)
    record_defect_canonical(root_dir, "DEFECT_RECORDER_ORCHESTRATOR_ACCESS", "Доступ к оркестратору через публичный метод getInstance(context) вместо приватного INSTANCE", "GestureRecorderOverlay.kt", "check_orchestrator_singleton_access.py")
    append_audit_log(root_dir, is_feature=False, title="Доступ к оркестратору через getInstance", details="- Заменено некорректное обращение к приватному свойству INSTANCE на AutoTapOrchestrator.getInstance(context).\n- Сборка разблокирована.")
    commit_and_build(root_dir, "fix(compiler): access orchestrator via public getInstance method in recorder overlay")
else:
    print("[CRITICAL] Транзакция отклонена. Выполнен откат.")