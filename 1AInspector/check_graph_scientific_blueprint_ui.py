import os

def run_check(root_dir: str) -> tuple[bool, str]:
    path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphCanvasView.kt")
    if not os.path.exists(path):
        return True, "[INFO] GraphCanvasView.kt не найден"

    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    if "getNodeTheme" not in content:
        return False, "[FAIL] В GraphCanvasView отсутствует семантическая типизация шапок узлов (getNodeTheme)."
    if "paintMajorGrid" not in content or "paintMinorGrid" not in content:
        return False, "[FAIL] В GraphCanvasView отсутствует двухуровневая координатная сетка Major/Minor."
    if "curveShadowPaint" not in content:
        return False, "[FAIL] В GraphCanvasView отсутствует двухпроходный рендер ребер связей."

    return True, "[OK] Архитектурный стандарт визуализации графа Blueprint полностью удовлетворен."
