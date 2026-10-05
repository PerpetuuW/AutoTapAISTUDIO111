import os

def run_check(root_dir: str) -> tuple[bool, str]:
    path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphCanvasView.kt")
    if not os.path.exists(path):
        return True, "[INFO] GraphCanvasView.kt не найден"

    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Ищем ошибочное использование descent без fontMetrics. или скобок
    if "paintTextTitle.descent)" in content or "paintTextTitle.descent " in content:
        return False, "[FAIL] Обнаружено ошибочное обращение к paint.descent вместо paint.fontMetrics.descent."

    return True, "[OK] Синтаксис работы с FontMetrics корректен."
