import os

def run_check(root_dir: str) -> tuple[bool, str]:
    path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/tutorial/InteractiveTutorialOverlay.kt")
    if not os.path.exists(path):
        return True, "[INFO] InteractiveTutorialOverlay.kt не найден"

    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    if "TutorialMode.GRAPH" not in content or "БЛОК-УЗЕЛ (NODE)" not in content:
        return False, "[FAIL] В InteractiveTutorialOverlay отсутствует туториал для графа."

    return True, "[OK] Туториал графа внедрен."
