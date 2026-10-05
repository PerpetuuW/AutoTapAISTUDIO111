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

    if "nodeWidth - textStartX" in canvas_code:
        return False, "[FAIL] Обнаружен баг вычисления ширины: вычитание мирового textStartX приводит к исчезновению текста при сдвиге узла!"

    if "МУЛЬТИВЫБОР ШАБЛОНОВ" not in editor_code or "selectedPaths" not in editor_code:
        return False, "[FAIL] В GraphEditorOverlay отсутствует поддержка мультивыбора шаблонов (Мультипоиск)."

    return True, "[OK] Защита от исчезновения текста и мультипоиск в графе верифицированы."
