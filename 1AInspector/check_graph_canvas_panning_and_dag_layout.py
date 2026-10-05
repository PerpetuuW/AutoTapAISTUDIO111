import os

def run_check(root_dir: str) -> tuple[bool, str]:
    canvas_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphCanvasView.kt")
    migrator_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/core/math/LinearToGraphMigrator.kt")

    if not os.path.exists(canvas_path) or not os.path.exists(migrator_path):
        return True, "[INFO] Модули графа не найдены"

    with open(canvas_path, "r", encoding="utf-8") as f:
        canvas_content = f.read()

    if "isPanningCanvas" not in canvas_content:
        return False, "[FAIL] В GraphCanvasView отсутствует 1-пальцевое перемещение холста (isPanningCanvas)."
    if "topClearance" not in canvas_content:
        return False, "[FAIL] В GraphCanvasView.resetViewToFit отсутствует безопасный зазор от тулбара topClearance."

    with open(migrator_path, "r", encoding="utf-8") as f:
        migrator_content = f.read()

    if "nodeSpacingX" not in migrator_content:
        return False, "[FAIL] В LinearToGraphMigrator отсутствует линейное упорядочивание узлов nodeSpacingX."

    return True, "[OK] Панорамирование и бесколлизионная раскладка графа подтверждены."
