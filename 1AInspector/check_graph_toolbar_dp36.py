import os

def run_check(root_dir: str) -> tuple[bool, str]:
    path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")
    if not os.path.exists(path):
        return True, "[INFO] GraphEditorOverlay.kt не найден"

    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    if "dp(36)" not in content:
        return False, "[FAIL] В GraphEditorOverlay высота кнопок должна быть dp(36)."

    return True, "[OK] Эргономичная высота dp(36) подтверждена."
