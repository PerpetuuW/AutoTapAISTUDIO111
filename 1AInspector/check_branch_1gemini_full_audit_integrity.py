# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str) -> tuple[bool, str]:
    mee_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/engine/MacroExecutionEngine.kt")
    mps_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/projection/MediaProjectionService.kt")
    cp_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/panel/ControlPanelComposeOverlay.kt")
    cal_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")
    ge_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")
    sr_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/storage/ScenarioRepositoryImpl.kt")

    for p in (mee_p, mps_p, cp_p, cal_p, ge_p, sr_p):
        if not os.path.exists(p):
            return False, f"[CRITICAL] Файл не найден: {p}"

    with open(mee_p, "r", encoding="utf-8") as f:
        mee_src = f.read()
    if "!!," in mee_src or "!!)" in mee_src:
        return False, "[FAIL] В MacroExecutionEngine.kt обнаружен небезопасный оператор !!"
    if "action.isNeuralEngine" not in mee_src or "NeuralVisualMatcher.findMatches" not in mee_src:
        return False, "[FAIL] В MacroExecutionEngine.kt отсутствует роутинг action.isNeuralEngine -> NeuralVisualMatcher.findMatches"
    if 'meta["calibratedX"] = bestMatchCand.rectLeft' in mee_src:
        return False, "[FAIL] В MacroExecutionEngine.kt calibratedX перезаписывается углом rectLeft вместо центра clickX"

    with open(mps_p, "r", encoding="utf-8") as f:
        mps_src = f.read()
    if "currentFrameWidth" not in mps_src or "currentFrameHeight" not in mps_src:
        return False, "[FAIL] В MediaProjectionService.kt отсутствуют геттеры реального размера кадра стриминга"

    with open(cp_p, "r", encoding="utf-8") as f:
        cp_src = f.read()
    if "listener.onGraphClicked()" not in cp_src or "listener.onLogsClicked()" not in cp_src:
        return False, "[FAIL] В ControlPanelComposeOverlay.kt отсутствуют кнопки вызова Графа или Журнала логов"

    with open(cal_p, "r", encoding="utf-8") as f:
        cal_src = f.read()
    if "card.addView(calibrationContentLayout" not in cal_src:
        return False, "[FAIL] В CalibrationOverlay.kt контейнер calibrationContentLayout не добавлен в карточку"

    with open(ge_p, "r", encoding="utf-8") as f:
        ge_src = f.read()
    if 'standardPorts = listOf("timeout")' in ge_src:
        return False, "[FAIL] В GraphEditorOverlay.kt остался невалидный порт 'timeout' вместо 'out_timeout'"

    with open(sr_p, "r", encoding="utf-8") as f:
        sr_src = f.read()
    if 'put("behavior", trig.behavior.name)' not in sr_src:
        return False, "[FAIL] В ScenarioRepositoryImpl.kt не сохраняется TriggerBehavior для узлов графа"

    return True, "[PASS] Все исправления аудита ветки 1Gemini (поиск, калибровка, эргономика, графы и Null-Safety) активны."

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
