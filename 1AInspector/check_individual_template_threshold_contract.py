#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_individual_template_threshold_contract.py — Чекер применения индивидуальных порогов шаблонов в TIER 0/1/2.
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    tme_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt")

    if not os.path.isfile(tme_path):
        return False, f"Файл не найден: {tme_path}"

    with open(tme_path, "r", encoding="utf-8") as f:
        tme_src = f.read()

    if "individualThreshold" not in tme_src:
        return False, "В TemplateMatchingEngine.kt отсутствует вычитка индивидуального порога individualThreshold"

    if "confidentThreshold" not in tme_src:
        return False, "В TemplateMatchingEngine.kt отсутствует сопоставление confidentThreshold в TIER 0/1/2"

    if "candidate.score >= 0.95f || templates.size == 1" in tme_src:
        return False, "В TemplateMatchingEngine.kt остался блокирующий порог 0.95f, мешающий разблокировке TIER 0 при мультипоиске"

    if "candidate.score >= confidentThreshold" not in tme_src:
        return False, "В TemplateMatchingEngine.kt мгновенный возврат TIER 0/1 должен использовать индивидуальный confidentThreshold"

    return True, "Инспекция использования индивидуальных порогов шаблонов пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
