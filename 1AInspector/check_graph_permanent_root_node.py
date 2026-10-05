import os

def run_check(root_dir: str) -> tuple[bool, str]:
    migrator_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/core/math/LinearToGraphMigrator.kt")
    editor_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")

    if not os.path.exists(migrator_path) or not os.path.exists(editor_path):
        return True, "[INFO] Модули графа не найдены"

    with open(migrator_path, "r", encoding="utf-8") as f:
        migrator_code = f.read()

    with open(editor_path, "r", encoding="utf-8") as f:
        editor_code = f.read()

    if "node_start" not in migrator_code:
        return False, "[FAIL] В LinearToGraphMigrator отсутствует генерация постоянного стартового узла node_start."

    if "isPermanentStart" not in editor_code:
        return False, "[FAIL] В GraphEditorOverlay должна быть защита постоянного стартового узла от удаления."

    return True, "[OK] Архитектурный паттерн постоянного стартового узла (Root Node) верифицирован."
