import os

def run_check(root_dir: str) -> tuple[bool, str]:
    path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/tutorial/InteractiveTutorialOverlay.kt")
    if not os.path.exists(path):
        return True, "[INFO] Файл не найден"

    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    if "host.getTutorialRect(tagOrKey)" not in content:
        return False, "[FAIL] InteractiveTutorialOverlay не имеет моста getTutorialRect для Canvas-элементов графа."

    return True, "[OK] Мост Canvas <-> View для туториалов внедрен."
