#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_smart_record_mask_and_centering.py — Инспектор автоцентрирования по объекту и генерации прозрачных масок
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    rec_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/record/GestureRecorderOverlay.kt")
    sme_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/SmartMaskEngine.kt")

    if not os.path.isfile(rec_path):
        return False, f"Файл не найден: {rec_path}"
    if not os.path.isfile(sme_path):
        return False, f"Файл не найден: {sme_path}"

    with open(rec_path, "r", encoding="utf-8", errors="replace") as f:
        rec_src = f.read()

    with open(sme_path, "r", encoding="utf-8", errors="replace") as f:
        sme_src = f.read()

    if "val objCenterX = safeX + opt.cropOffsetX + (finalMask.width / 2f)" not in rec_src:
        return False, "Отсутствует расчет центроида объекта objCenterX в GestureRecorderOverlay.kt"

    if "val clickOffX = startPt.x - objCenterX" not in rec_src:
        return False, "Отсутствует расчет относительного оффсета клика clickOffX в GestureRecorderOverlay.kt"

    if "val originX = (anchorX + res.cropOffsetX)" in sme_src:
        return False, "В SmartMaskEngine.kt остался ошибочный сдвиг anchorX в originX"

    if "rawTemplate.copy(Bitmap.Config.ARGB_8888, true)" in sme_src:
        return False, "В SmartMaskEngine.kt остался опасный фолбэк на сырое непрозрачное изображение rawTemplate"

    return True, "Инспекция генерации масок и центрирования объекта пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
