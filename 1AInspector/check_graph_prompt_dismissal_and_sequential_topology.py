# -*- coding: utf-8 -*-
"""
check_graph_prompt_dismissal_and_sequential_topology.py
Валидация:
1. Обязательный removeViewSafe(root) при любых действиях в checkAndPromptGraphGeneration.
2. Соединение шаблонов и пауз в хронологическом порядке записи без веерного мультипоиска.
"""

import os
import sys

def run_check(root_dir: str) -> tuple[bool, str]:
    orch_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "orchestrator", "AutoTapOrchestrator.kt")
    migrator_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "core", "math", "LinearToGraphMigrator.kt")

    if not os.path.exists(orch_file) or not os.path.exists(migrator_file):
        return False, "[CRITICAL] AutoTapOrchestrator.kt или LinearToGraphMigrator.kt не найдены"

    with open(orch_file, "r", encoding="utf-8") as f:
        o_code = f.read()

    if 'dismissPrompt()' not in o_code or 'overlayWindowManager.removeViewSafe(root)' not in o_code:
        return False, "[FAIL] В checkAndPromptGraphGeneration отсутствует гарантированное закрытие оверлея removeViewSafe(root)"

    if 'generateMultiTemplateBranchingGraph' in o_code:
        return False, "[FAIL] В prompt генерации графа запрещен веерный мультипоиск, должна использоваться последовательная запись"

    with open(migrator_file, "r", encoding="utf-8") as f:
        m_code = f.read()

    if 'Клик и пауза:' not in m_code or '_match_to_act' not in m_code:
        return False, "[FAIL] В LinearToGraphMigrator.kt отсутствует последовательное соединение сработавших шаблонов с узлами пауз"

    return True, "[PASS] Контракт закрытия промпта графа и хронологической топологии шаблонов с паузами соблюден"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
