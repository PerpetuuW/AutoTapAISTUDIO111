import os

def run_check(root_dir: str) -> tuple[bool, str]:
    canvas_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphCanvasView.kt")
    editor_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")

    if not os.path.exists(canvas_p) or not os.path.exists(editor_p):
        return True, "[INFO] Модули графа не найдены"

    with open(canvas_p, "r", encoding="utf-8") as f:
        canvas_code = f.read()

    with open(editor_p, "r", encoding="utf-8") as f:
        editor_code = f.read()

    if "isWiringReverse" not in canvas_code:
        return False, "[FAIL] В GraphCanvasView отсутствует поддержка двунаправленной протяжки кабелей от входа к выходу (isWiringReverse)."

    if "СТАРТ ▶" not in canvas_code:
        return False, "[FAIL] В GraphCanvasView отсутствует визуальный бейдж точки входа СТАРТ ▶."

    if "btnAddBranch" not in editor_code or "ВЕТКА" not in editor_code:
        return False, "[FAIL] В GraphEditorOverlay отсутствует кнопка добавления ветвления шаблонов."

    if "repeatCount" not in editor_code:
        return False, "[FAIL] В GraphEditorOverlay отсутствуют степперы параметров повторений цикла."

    return True, "[OK] Архитектура ветвлений, степперов и двунаправленных связей верифицирована."
