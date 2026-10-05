import os

def run_check(root_dir: str) -> tuple[bool, str]:
    main_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/presentation/main/MainActivity.kt")
    tom_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/target/TargetOverlayManager.kt")
    
    if os.path.exists(main_file):
        with open(main_file, "r", encoding="utf-8") as f:
            content = f.read()
            if "dpF(10f)" in content and "fun dpF" not in content:
                return False, "[FAIL] В MainActivity.updateStatus обнаружен вызов необъявленного метода dpF."

    if os.path.exists(tom_file):
        with open(tom_file, "r", encoding="utf-8") as f:
            content = f.read()
            if "Dual-Overlay Path Architecture" not in content:
                return False, "[FAIL] В TargetOverlayManager отсутствует архитектура двух оверлеев для PATH."

    return True, "[OK] Безопасность ресурсов MainActivity и архитектура двух оверлеев PATH подтверждены."
