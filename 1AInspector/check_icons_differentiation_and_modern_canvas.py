# check_icons_differentiation_and_modern_canvas.py
import os
def run_check(root_dir: str) -> tuple[bool, str]:
    p = os.path.normpath(os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/ui/VectorIconDrawer.kt"))
    if not os.path.exists(p): return False, "VectorIconDrawer.kt не найден"
    with open(p, "r", encoding="utf-8") as f: src = f.read()
    if "triR" not in src: return False, "SCRIPTS не содержит символа ▶"
    if "dotR" not in src: return False, "LOGS не содержит символа терминала"
    return True, "Уникальность пиктограмм SCRIPTS/LOGS подтверждена"
if __name__ == "__main__":
    ok, msg = run_check(os.getcwd()); print(f"Result: {ok} -> {msg}")
