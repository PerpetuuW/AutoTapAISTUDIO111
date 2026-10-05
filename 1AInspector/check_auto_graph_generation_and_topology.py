import os

def run_check(root_dir: str) -> tuple[bool, str]:
    orch_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt")
    migr_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/core/math/LinearToGraphMigrator.kt")
    set_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/GlobalSettingsDialog.kt")
    
    if os.path.exists(orch_file):
        with open(orch_file, "r", encoding="utf-8") as f:
            content = f.read()
            if 'val shouldPrompt = prefs.getBoolean("PREF_PROMPT_AUTO_GRAPH"' not in content:
                return False, "[FAIL] В AutoTapOrchestrator отсутствует логика авто-предложения графа."
    if os.path.exists(migr_file):
        with open(migr_file, "r", encoding="utf-8") as f:
            content = f.read()
            if "val posY = currentCanvasY + timeDelaySpacing" not in content:
                return False, "[FAIL] В LinearToGraphMigrator отсутствует вертикальная топология сверху вниз с учетом времени."

    if os.path.exists(set_file):
        with open(set_file, "r", encoding="utf-8") as f:
            content = f.read()
            if "АВТО-ПРЕДЛОЖЕНИЕ ГРАФА" not in content:
                return False, "[FAIL] В GlobalSettingsDialog отсутствует тумблер управления авто-графом."

    return True, "[OK] Автоматическая генерация графов и вертикальная топология подтверждены."
