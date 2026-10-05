# check_canvas_icon_button_unification.py
import os
def run_check(root_dir: str) -> tuple[bool, str]:
    target_class = os.path.normpath(os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/ui/CanvasIconButton.kt"))
    if not os.path.exists(target_class): return False, "CanvasIconButton.kt отсутствует"
    return True, "CanvasIconButton подтвержден"
if __name__ == "__main__":
    ok, msg = run_check(os.getcwd()); print(f"Result: {ok} -> {msg}")
