# check_overlay_bad_token_guard.py
# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    graph_editor = os.path.normpath(os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt"))
    if not os.path.exists(graph_editor): return False, "GraphEditorOverlay.kt не найден"

    with open(graph_editor, "r", encoding="utf-8") as f: content = f.read()

    if "AlertDialog.Builder(" in content:
        return False, "В GraphEditorOverlay обнаружен запрещенный вызов AlertDialog.Builder (риск BadTokenException)"

    return True, "Оверлей защищен от BadTokenException, используются кастомные оверлейные диалоги"

if __name__ == "__main__":
    ok, msg = run_check(os.getcwd()); print(f"Result: {ok} -> {msg}")
