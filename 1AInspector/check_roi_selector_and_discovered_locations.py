import os

def run_check(root_dir: str) -> tuple[bool, str]:
    dialog_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")
    orch_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt")
    tme_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt")
    
    if os.path.exists(dialog_file):
        with open(dialog_file, "r", encoding="utf-8") as f:
            content = f.read()
            if "onSelectRoi?.invoke" not in content:
                return False, "[FAIL] В EditActionDialog отсутствует кнопка вызова интерактивного редактора ROI."
                
    if os.path.exists(orch_file):
        with open(orch_file, "r", encoding="utf-8") as f:
            content = f.read()
            if "startRoiSelector" not in content or "showEditDialog(updated)" not in content:
                return False, "[FAIL] В AutoTapOrchestrator отсутствует авто-возврат в диалог шага после выбора ROI."

    if os.path.exists(tme_file):
        with open(tme_file, "r", encoding="utf-8") as f:
            content = f.read()
            if "fun registerDiscoveredLocation" not in content:
                return False, "[FAIL] В TemplateMatchingEngine отсутствует регистрация обнаруженных областей сканирования."

    return True, "[OK] Интерактивный редактор ROI и динамическое обучение областям сканирования подтверждены."
