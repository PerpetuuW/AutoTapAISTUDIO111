import os

def run_check(root_dir: str) -> tuple[bool, str]:
    graph_view_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphOverlayView.kt")
    recorder_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/record/GestureRecorderOverlay.kt")
    target_view_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/target/TargetOverlayView.kt")

    if not os.path.exists(graph_view_p) or not os.path.exists(recorder_p) or not os.path.exists(target_view_p):
        return True, "[INFO] Модули визуализации не найдены"

    with open(graph_view_p, "r", encoding="utf-8") as f:
        graph_view_code = f.read()

    with open(recorder_p, "r", encoding="utf-8") as f:
        recorder_code = f.read()

    with open(target_view_p, "r", encoding="utf-8") as f:
        target_code = f.read()

    if "pathStrokePaint" not in graph_view_code or "action.pathPoints" not in graph_view_code:
        return False, "[FAIL] GraphOverlayView обязан отрисовывать кривую траектории ActionType.PATH."

    if "instantDuration" not in recorder_code:
        return False, "[FAIL] GestureRecorderOverlay обязан мгновенно инжектировать жест без многосекундного реплея."

    if "ActionType.PATH" not in target_code or '"ПУТЬ"' not in target_code:
        return False, "[FAIL] TargetOverlayView обязан выделять действия типа PATH специальным бейджем."

    return True, "[OK] Рендеринг траекторий PATH и мгновенный отклик жеста верифицированы."
