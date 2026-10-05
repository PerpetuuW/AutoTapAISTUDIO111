import os

def run_check(root_dir: str) -> tuple[bool, str]:
    path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")
    if not os.path.exists(path):
        return True, "[INFO] GraphEditorOverlay.kt не найден"

    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    expected_import = "import com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay"
    if expected_import not in content:
        return False, f"[FAIL] В GraphEditorOverlay.kt отсутствует обязательный импорт: {expected_import}"

    return True, "[OK] Импорт InteractiveTutorialOverlay в GraphEditorOverlay присутствует."
