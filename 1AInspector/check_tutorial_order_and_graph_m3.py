# check_tutorial_order_and_graph_m3.py
# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    tut_path = os.path.normpath(os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/tutorial/InteractiveTutorialOverlay.kt"))
    if not os.path.exists(tut_path): return False, "InteractiveTutorialOverlay.kt не найден"

    with open(tut_path, "r", encoding="utf-8") as f: tut_content = f.read()

    expected_order = [
        '"BTN_DRAG"',
        '"BTN_PLAY_VIEW"',
        '"BTN_CAPTURE"',
        '"BTN_RECORD"',
        '"BTN_ADD"',
        '"BTN_TOGGLE_VIEW"',
        '"BTN_CLOSE"',
        '"BTN_GRAPH"',
        '"BTN_SCRIPTS"',
        '"BTN_LOCK"',
        '"BTN_HIDE"',
        '"BTN_CLEAR"',
        '"BTN_LOGS"',
        '"BTN_SETTINGS"',
        '"BTN_TUTORIAL"'
    ]

    last_idx = -1
    for tag in expected_order:
        idx = tut_content.find(tag)
        if idx == -1: return False, f"В туториале отсутствует обязательный шаг {tag}"
        if idx < last_idx: return False, f"Нарушен линейный порядок шагов туториала для {tag}"
        last_idx = idx

    return True, "Линейный порядок туториала подтвержден"

if __name__ == "__main__":
    ok, msg = run_check(os.getcwd()); print(f"Result: {ok} -> {msg}")
