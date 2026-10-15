# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str) -> tuple[bool, str]:
    mee_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/engine/MacroExecutionEngine.kt")
    mps_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/projection/MediaProjectionService.kt")
    cp_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/panel/ControlPanelComposeOverlay.kt")

    for p in (mee_p, mps_p, cp_p):
        if not os.path.exists(p):
            return False, f"[CRITICAL] Файл не найден: {p}"

    with open(mee_p, "r", encoding="utf-8") as f:
        mee_src = f.read()
    if "!!," in mee_src or "!!)" in mee_src:
        return False, "[FAIL] В MacroExecutionEngine.kt обнаружен небезопасный оператор !!"
    if "action.isNeuralEngine" not in mee_src or "NeuralVisualMatcher.findMatches" not in mee_src:
        return False, "[FAIL] В MacroExecutionEngine.kt отсутствует роутинг action.isNeuralEngine -> NeuralVisualMatcher.findMatches"

    with open(mps_p, "r", encoding="utf-8") as f:
        mps_src = f.read()
    if "currentFrameWidth" not in mps_src or "currentFrameHeight" not in mps_src:
        return False, "[FAIL] В MediaProjectionService.kt отсутствуют геттеры реального размера кадра стриминга"

    with open(cp_p, "r", encoding="utf-8") as f:
        cp_src = f.read()
    if "listener.onGraphClicked()" not in cp_src or "listener.onLogsClicked()" not in cp_src:
        return False, "[FAIL] В ControlPanelComposeOverlay.kt отсутствуют кнопки вызова Графа или Журнала логов"

    return True, "[PASS] Все исправления аудита ветки 1Gemini (стриминг, AI-роутинг, Null-Safety и Compose-пульт) активны."

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
