import os

def run_check(root_dir: str) -> tuple[bool, str]:
    path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphCanvasView.kt")
    if not os.path.exists(path):
        return True, "[INFO] GraphCanvasView.kt не найден"

    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    if "private val nodeWidth get() = dpF(280f)" not in content:
        return False, "[FAIL] Нарушение эргономики: nodeWidth должен быть строго 280dp (Unreal Blueprint Style)."

    return True, "[OK] Геометрия узлов графа соответствует стандартам."
