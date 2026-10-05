# check_total_target_hiding.py
import os
def run_check(root_dir: str) -> tuple[bool, str]:
    p = os.path.normpath(os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/target/TargetOverlayView.kt"))
    if not os.path.exists(p): return False, "TargetOverlayView.kt не найден"
    with open(p, "r", encoding="utf-8") as f: src = f.read()
    if "circleContainer.visibility = View.INVISIBLE" not in src:
        return False, "В TargetOverlayView отсутствует полное скрытие круга мишени"
    return True, "Тотальное скрытие меток кнопкой Глаз подтверждено"
if __name__ == "__main__":
    ok, msg = run_check(os.getcwd()); print(f"Result: {ok} -> {msg}")
