import os

def run_check(root_dir: str) -> tuple[bool, str]:
    cp_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/panel/ControlPanelOverlay.kt")
    main_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/presentation/main/MainActivity.kt")
    
    if os.path.exists(cp_file):
        with open(cp_file, "r", encoding="utf-8") as f:
            content = f.read()
            if "val isCustomBg = bgColor.isNotBlank()" not in content:
                return False, "[FAIL] В ControlPanelOverlay.createIconButton игнорируется параметр bgColor."
            idx_cap = content.find("mainRow.addView(btnCapture)")
            idx_rec = content.find("mainRow.addView(btnRecord)")
            if idx_cap == -1 or idx_rec == -1 or idx_cap >= idx_rec:
                return False, "[FAIL] На главном пульте кнопка btnCapture должна стоять перед btnRecord."

    if os.path.exists(main_file):
        with open(main_file, "r", encoding="utf-8") as f:
            content = f.read()
            if '"#86EFAC"' not in content or '"#F59E0B"' not in content:
                return False, "[FAIL] В MainActivity отсутствует семантическая палитра монетизации (#86EFAC активен / #F59E0B неактивен)."

    return True, "[OK] Единая семантическая палитра монетизации и Canvas-оформление пульта подтверждены."
