#!/usr/bin/env python3
import os
import sys

def run_check(root_dir="."):
    wand_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/MagicWandEditorOverlay.kt")
    calib_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")
    roi_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/RoiSelectorOverlay.kt")
    frame_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CaptureFrameOverlay.kt")

    for path in [wand_path, calib_path, roi_path, frame_path]:
        if not os.path.exists(path):
            return False, f"Missing file: {path}"

    # 1. Check full color rawBitmap rendering in MagicWandEditorOverlay
    with open(wand_path, "r", encoding="utf-8") as f:
        wand_content = f.read()

    if "drawBitmap(rawBitmap" not in wand_content or "maskOverlayPaint" not in wand_content:
        return False, "MagicWandEditorOverlay.kt must draw rawBitmap under currentMask with semi-transparent overlay to prevent monochrome rendering"

    # 2. Check Help button standardization in CalibrationOverlay
    with open(calib_path, "r", encoding="utf-8") as f:
        calib_content = f.read()

    if "[?] СПРАВКА" in calib_content:
        return False, "CalibrationOverlay.kt should not use text '[?] СПРАВКА' on help button; must use standardized VectorIconDrawer.IconType.HELP icon button"

    # 3. Check dynamic measurement, clipChildren and symmetric padding in RoiSelectorOverlay
    with open(roi_path, "r", encoding="utf-8") as f:
        roi_content = f.read()

    if "clipChildren = false" not in roi_content or "bar.measuredWidth" not in roi_content:
        return False, "RoiSelectorOverlay.kt must measure bar width dynamically and disable clipChildren to prevent button clipping"

    if "setPadding(p, p, p, p)" not in roi_content or "actualW / 2" not in roi_content:
        return False, "RoiSelectorOverlay.kt must have symmetric 4-sided padding on button bar and zero-bias centering using actual measured width"

    # 4. Check dynamic measurement and symmetric centering in CaptureFrameOverlay
    with open(frame_path, "r", encoding="utf-8") as f:
        frame_content = f.read()

    if "controls.measuredWidth" not in frame_content or "ctrlW / 2" not in frame_content:
        return False, "CaptureFrameOverlay.kt must measure controls width dynamically and center with zero bias"

    # 5. Check EyedropperOverlay symmetric topBar padding
    eye_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/EyedropperOverlay.kt")
    if os.path.exists(eye_path):
        with open(eye_path, "r", encoding="utf-8") as f:
            eye_content = f.read()
        if "setPadding(p * 2" in eye_content:
            return False, "EyedropperOverlay.kt must have symmetric horizontal padding on topBar"

    return True, "UI layout alignment, full color mask studio, symmetric bubble paddings and button standardization verified."

if __name__ == "__main__":
    ok, msg = run_check()
    print(msg)
    sys.exit(0 if ok else 1)
