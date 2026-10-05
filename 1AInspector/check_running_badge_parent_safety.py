# check_running_badge_parent_safety.py
import os
def run_check(root_dir: str) -> tuple[bool, str]:
    badge_path = os.path.normpath(os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/badge/RunningBadgeOverlay.kt"))
    if not os.path.exists(badge_path): return False, "RunningBadgeOverlay.kt не найден"
    with open(badge_path, "r", encoding="utf-8") as f: content = f.read()
    if content.count("rootCard.addView(infoContainer)") > 1:
        return False, "Обнаружен повторный вызов rootCard.addView(infoContainer)"
    return True, "Безопасность иерархии RunningBadgeOverlay подтверждена"
if __name__ == "__main__":
    ok, msg = run_check(os.getcwd()); print(f"Result: {ok} -> {msg}")
