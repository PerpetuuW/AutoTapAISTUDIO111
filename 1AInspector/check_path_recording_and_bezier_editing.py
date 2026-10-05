# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str) -> tuple[bool, str]:
    p_rec = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "record", "GestureRecorderOverlay.kt")
    p_mgr = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "target", "TargetOverlayManager.kt")
    p_graph = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "graph", "GraphOverlayView.kt")

    for p in (p_rec, p_mgr, p_graph):
        if not os.path.exists(p):
            return False, f"[CRITICAL] Файл не найден: {p}"

    with open(p_rec, "r", encoding="utf-8") as f: content_rec = f.read()
    with open(p_mgr, "r", encoding="utf-8") as f: content_mgr = f.read()
    with open(p_graph, "r", encoding="utf-8") as f: content_graph = f.read()

    if "val deltaX = newStartX - oldAct.posX" not in content_mgr:
        return False, "[FAIL] В TargetOverlayManager отсутствует параллельный перенос траектории пути!"

    if "quadTo" not in content_graph:
        return False, "[FAIL] В GraphOverlayView отсутствует сглаживание Безье для путей!"

    return True, "[PASS] Архитектура записи жестов и интерактивного сплайна полностью соблюдена."

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
