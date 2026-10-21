#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_search_calibration_pipeline_interconnection.py — Чекер связности калибровки,
сохранения метаданных .json и каскадного поиска в TemplateMatchingEngine.
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    calib_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")
    repo_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/storage/TemplateRepositoryImpl.kt")
    tme_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt")

    for p in [calib_path, repo_path, tme_path]:
        if not os.path.exists(p):
            return False, f"Файл не найден: {p}"

    with open(calib_path, "r", encoding="utf-8") as f:
        calib_src = f.read()

    required_calib_keys = [
        "similarityPercent",
        "isShapeOnlyMode",
        "colorDeltaEMode",
        "clickOffsetX",
        "clickOffsetY",
        "useCustomClickOffset",
        "calibratedX",
        "calibratedY"
    ]
    for key in required_calib_keys:
        if key not in calib_src:
            return False, f"В CalibrationOverlay.kt отсутствует сохранение метаданного ключа '{key}'"

    with open(repo_path, "r", encoding="utf-8") as f:
        repo_src = f.read()

    if "additionalSearchLocations" not in repo_src:
        return False, "В TemplateRepositoryImpl.kt отсутствует интеграция ключа 'additionalSearchLocations'"

    with open(tme_path, "r", encoding="utf-8") as f:
        tme_src = f.read()

    required_tme_contracts = [
        "extractFeatures",
        "features.individualThreshold",
        "anchorProbes",
        "evaluateCandidateScore"
    ]
    for contract in required_tme_contracts:
        if contract not in tme_src:
            return False, f"В TemplateMatchingEngine.kt отсутствует обязательный фрагмент '{contract}'"

    return True, "Инспекция связности калибровки и поиска пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
