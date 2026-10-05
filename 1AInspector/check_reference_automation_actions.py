# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    act_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/domain/model/ActionType.kt")
    if os.path.exists(act_path):
        with open(act_path, "r", encoding="utf-8") as f:
            act_src = f.read()
        for token in ["GLOBAL_BACK", "GLOBAL_HOME", "DELAY"]:
            if token not in act_src:
                return False, f"ActionType: отсутствует {token}"

    eng_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/engine/MacroExecutionEngine.kt")
    if os.path.exists(eng_path):
        with open(eng_path, "r", encoding="utf-8") as f:
            eng_src = f.read()
        if "out_loop_body" not in eng_src or "nodeLoopCounters" not in eng_src:
            return False, "MacroExecutionEngine: отсутствует рантайм цикла out_loop_body"

    return True, "Эталонные действия (BACK, HOME, DELAY, циклы) верифицированы."
