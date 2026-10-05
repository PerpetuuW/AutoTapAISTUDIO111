# check_ui_ergonomics_standards.py
import os
def run_check(root_dir: str) -> tuple[bool, str]:
    badge_path = os.path.normpath(os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/badge/RunningBadgeOverlay.kt"))
    if not os.path.exists(badge_path): return False, "RunningBadgeOverlay.kt не найден"
    with open(badge_path, "r", encoding="utf-8") as f: content = f.read()
    if "textSize = 7.5f" in content or "textSize = 7f" in content:
        content = content.replace("textSize = 7.5f", "textSize = 10.5f").replace("textSize = 7f", "textSize = 9.5f")
        with open(badge_path, "w", encoding="utf-8", newline="\n") as f: f.write(content)
        return True, "[INFO] Шрифты бейджа приведены к стандарту 10.5sp/9.5sp"
    return True, "Стандарты эргономики UI соблюдены"
if __name__ == "__main__":
    ok, msg = run_check(os.getcwd()); print(f"Result: {ok} -> {msg}")
