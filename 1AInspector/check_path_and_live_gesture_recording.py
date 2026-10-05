import os

def run_check(root_dir: str) -> tuple[bool, str]:
    cp_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/panel/ControlPanelOverlay.kt")
    tom_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/target/TargetOverlayManager.kt")
    rec_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/record/GestureRecorderOverlay.kt")
    
    if os.path.exists(cp_file):
        with open(cp_file, "r", encoding="utf-8") as f:
            content = f.read()
            idx_play = content.find("mainRow.addView(playIconView)")
            idx_cap = content.find("mainRow.addView(btnCapture)")
            idx_rec = content.find("mainRow.addView(btnRecord)")
            idx_add = content.find("mainRow.addView(btnAdd)")
            if not (idx_play < idx_cap < idx_rec < idx_add):
                return False, "[FAIL] Нарушен строгий порядок кнопок пульта: [Play] -> [Шаблон] -> [Запись] -> [+]."
            if '"#38BDF8"' not in content:
                return False, "[FAIL] На пульте отсутствует тонкая синяя обводка #38BDF8."

    if os.path.exists(tom_file):
        with open(tom_file, "r", encoding="utf-8") as f:
            content = f.read()
            if "ActionType.SWIPE || action.type == ActionType.PATH" not in content and "ActionType.SWIPE || newAction.type == ActionType.PATH" not in content:
                return False, "[FAIL] В TargetOverlayManager действие PATH не создает вторую мишень."

    if os.path.exists(rec_file):
        with open(rec_file, "r", encoding="utf-8") as f:
            content = f.read()
            if "Live Motion Streaming" not in content:
                return False, "[FAIL] В GestureRecorderOverlay отсутствует потоковая передача движения в ACTION_MOVE."

    return True, "[OK] Иерархия пульта, параметры действия PATH и потоковая запись жестов подтверждены."
