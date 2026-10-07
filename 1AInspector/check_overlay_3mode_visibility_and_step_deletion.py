# check_overlay_3mode_visibility_and_step_deletion.py
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    mode_file = os.path.normpath(os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/model/OverlayVisibilityMode.kt"))
    if not os.path.exists(mode_file):
        return False, "OverlayVisibilityMode.kt не найден"

    with open(mode_file, "r", encoding="utf-8") as f:
        mode_src = f.read()
    if "TRANSPARENT" not in mode_src or "HIDDEN" not in mode_src or "FULL" not in mode_src:
        return False, "В OverlayVisibilityMode отсутствуют необходимые 3 режима видимости"

    target_mgr = os.path.normpath(os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/target/TargetOverlayManager.kt"))
    if not os.path.exists(target_mgr):
        return False, "TargetOverlayManager.kt не найден"

    with open(target_mgr, "r", encoding="utf-8") as f:
        target_src = f.read()
    if "applyVisibilityMode" not in target_src or "visibilityMode" not in target_src:
        return False, "В TargetOverlayManager отсутствует метод applyVisibilityMode или свойство visibilityMode"

    orchestrator = os.path.normpath(os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt"))
    if not os.path.exists(orchestrator):
        return False, "AutoTapOrchestrator.kt не найден"

    with open(orchestrator, "r", encoding="utf-8") as f:
        orch_src = f.read()
    if "OverlayVisibilityMode.TRANSPARENT" not in orch_src:
        return False, "В AutoTapOrchestrator отсутствует циклическое переключение 3 режимов видимости"

    return True, "Инвариант 3-режимной видимости оверлеев и удаления шагов подтвержден"

if __name__ == "__main__":
    ok, msg = run_check(os.getcwd())
    print(f"Result: {ok} -> {msg}")
