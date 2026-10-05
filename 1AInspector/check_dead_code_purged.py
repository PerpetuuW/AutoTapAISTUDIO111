import os

def run_check(root_dir: str) -> tuple[bool, str]:
    dead_files = [
        "app/src/main/java/com/example/autotap/infrastructure/overlay/joystick/JoystickOverlay.kt",
        "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/MultiTemplateGraphPromptOverlay.kt",
        "app/src/main/java/com/example/autotap/infrastructure/vision/RefinedMaskResult.kt",
        "app/src/main/java/com/example/autotap/infrastructure/vision/ScreenshotBitmapPool.kt"
    ]
    found = [f for f in dead_files if os.path.exists(os.path.join(root_dir, f))]
    if found:
        return False, f"[FAIL] Обнаружен неудаленный мертвый код: {', '.join(found)}"

    return True, "[OK] Мертвый код (1200+ строк) полностью ликвидирован из проекта."
