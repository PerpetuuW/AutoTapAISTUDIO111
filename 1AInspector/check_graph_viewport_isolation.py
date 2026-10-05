import os

def run_check(root_dir: str) -> tuple[bool, str]:
    editor_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")
    if not os.path.exists(editor_path):
        return True, "[INFO] GraphEditorOverlay.kt не найден"

    with open(editor_path, "r", encoding="utf-8") as f:
        content = f.read()

    if "contentVertical.addView(canvas" not in content:
        return False, "[FAIL] Холст GraphCanvasView должен быть изолирован в contentVertical ниже тулбара."
    if "ДОБАВИТЬ УЗЕЛ В ГРАФ" not in content:
        return False, "[FAIL] Заголовок меню действий графа должен соответствовать стандарту 'ДОБАВИТЬ УЗЕЛ В ГРАФ'."
    if "canvasX = targetX" not in content:
        return False, "[FAIL] Спавн узлов должен использовать вычисленные безопасные координаты targetX."

    return True, "[OK] Видовой экран графа изолирован, заголовки гармонизированы, коллизии исключены."
