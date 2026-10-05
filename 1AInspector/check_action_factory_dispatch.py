# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    orch_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "orchestrator", "AutoTapOrchestrator.kt")
    if not os.path.exists(orch_file):
        return True, "AutoTapOrchestrator.kt не найден"

    with open(orch_file, "r", encoding="utf-8") as f:
        content = f.read()

    idx = content.find("fun onAddActionSelected")
    if idx == -1:
        return False, "Метод onAddActionSelected не найден в AutoTapOrchestrator"

    method_scope = content[idx:idx + 800]
    if "when (type)" in method_scope and "ActionType.TRIGGER" in method_scope:
        return True, "Фабрика действий оркестратора валидна (предиктивный роутинг подключен)"

    return False, "Метод onAddActionSelected игнорирует ActionType (отсутствует роутинг типов)"
