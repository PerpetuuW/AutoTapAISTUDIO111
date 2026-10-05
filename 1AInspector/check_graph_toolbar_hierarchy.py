import os

def run_check(root_dir: str) -> tuple[bool, str]:
    path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")
    if not os.path.exists(path):
        return True, "[INFO] GraphEditorOverlay.kt не найден"

    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    if "val titleRow = LinearLayout(context)" not in content:
        return False, "[FAIL] В GraphEditorOverlay отсутствует выделенная строка заголовка titleRow (риск вертикального сжатия текста)."
    if "commandScroll" not in content:
        return False, "[FAIL] В GraphEditorOverlay панель кнопок должна быть обернута в HorizontalScrollView."

    return True, "[OK] Иерархия тулбара редактора графа эргономична."
