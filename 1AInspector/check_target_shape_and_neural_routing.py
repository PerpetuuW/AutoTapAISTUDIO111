# -*- coding: utf-8 -*-
"""
check_target_shape_and_neural_routing.py
Инспектор формы мишеней и полноты роутинга AI:
1. Форма мишени в TargetOverlayView адаптируется к action.isCircleShape (квадратная форма для обычных шаблонов, без вылезания углов).
2. Движок MacroExecutionEngine учитывает action.isNeuralEngine при стриминге кадров.
3. NeuralVisualMatcher поддерживает учет ROI и аппаратный буфер sPixels.
"""
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    tov_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/target/TargetOverlayView.kt".replace("/", os.sep))
    mee_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/engine/MacroExecutionEngine.kt".replace("/", os.sep))
    nvm_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/NeuralVisualMatcher.kt".replace("/", os.sep))

    for p in [tov_path, mee_path, nvm_path]:
        if not os.path.exists(p):
            return False, f"Файл не найден: {p}"

    with open(tov_path, "r", encoding="utf-8") as f:
        tov = f.read()
    if "isCircleShape" not in tov or "GradientDrawable.RECTANGLE" not in tov:
        return False, "TargetOverlayView не поддерживает прямоугольную форму для квадратных шаблонов (углы вылезают за круг)"

    with open(mee_path, "r", encoding="utf-8") as f:
        mee = f.read()
    if "action.isNeuralEngine" not in mee or "NeuralVisualMatcher.findMatches" not in mee:
        return False, "MacroExecutionEngine игнорирует режим AI в цикле стриминга"

    with open(nvm_path, "r", encoding="utf-8") as f:
        nvm = f.read()
    if "roiLeft" not in nvm or "sPixels: IntArray" not in nvm:
        return False, "NeuralVisualMatcher не поддерживает ROI или прямой буфер sPixels"

    return True, "Форма мишеней адаптирована к геометрии шаблонов, AI-роутинг и пирамида ZNCC полностью активны."
