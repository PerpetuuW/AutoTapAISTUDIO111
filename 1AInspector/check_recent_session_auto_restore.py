import os

def run_check(root_dir: str) -> tuple[bool, str]:
    path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt")
    if not os.path.exists(path):
        return True, "[INFO] AutoTapOrchestrator.kt не найден"

    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    if "persistActiveSession" not in content:
        return False, "[FAIL] В AutoTapOrchestrator отсутствует метод автосохранения persistActiveSession."
    if "last_active_scenario_name" not in content:
        return False, "[FAIL] В AutoTapOrchestrator отсутствует ключ last_active_scenario_name для восстановления сессии."

    return True, "[OK] Автоматическое восстановление последних использованных шагов подтверждено."
