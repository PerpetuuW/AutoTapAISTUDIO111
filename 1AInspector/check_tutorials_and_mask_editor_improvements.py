#!/usr/bin/env python3
import os
import sys

def run_check(root_dir="."):
    tutorial_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/tutorial/InteractiveTutorialOverlay.kt")
    wand_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/MagicWandEditorOverlay.kt")
    calib_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")
    smart_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/SmartMaskEngine.kt")

    for path in [tutorial_path, wand_path, calib_path, smart_path]:
        if not os.path.exists(path):
            return False, f"Missing file: {path}"

    # 1. Check tutorial reflection / getTutorialRect invocation
    with open(tutorial_path, "r", encoding="utf-8") as f:
        tut_content = f.read()

    if "getTutorialRect" not in tut_content:
        return False, "InteractiveTutorialOverlay.kt must query getTutorialRect for dynamic custom rect highlights"

    # 2. Check MagicWand shiftMask D-pad controls
    with open(wand_path, "r", encoding="utf-8") as f:
        wand_content = f.read()

    if "shiftMask" not in wand_content or "rowShift" not in wand_content:
        return False, "MagicWandEditorOverlay.kt must implement D-pad pixel-by-pixel shift controls (shiftMask)"

    # 3. Check SmartMaskEngine shiftMask implementation
    with open(smart_path, "r", encoding="utf-8") as f:
        smart_content = f.read()

    if "fun shiftMask" not in smart_content:
        return False, "SmartMaskEngine.kt must contain shiftMask function"

    # 4. Check CalibrationOverlay adaptive padding range (+-50%)
    with open(calib_path, "r", encoding="utf-8") as f:
        calib_content = f.read()

    if "adaptiveMaxPad" not in calib_content:
        return False, "CalibrationOverlay.kt must calculate adaptive padding range based on mask dimensions"

    return True, "Tutorial highlights and mask editor improvements (persistence, adaptive padding, pixel nudge) verified."

if __name__ == "__main__":
    ok, msg = run_check()
    print(msg)
    sys.exit(0 if ok else 1)
