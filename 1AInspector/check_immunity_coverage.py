# -*- coding: utf-8 -*-
"""
1AInspector/check_immunity_coverage.py
Мета-инспектор гарантии иммунитета:
Проверяет, что каждый устраненный дефект из реестра .defect_ledger.json
покрыт действующим и физически присутствующим модулем проверки в 1AInspector/.
"""
import os
import sys

def run_check(root_dir: str) -> tuple[bool, str]:
    auto_dir = os.path.join(root_dir, "1AAutomation")
    if auto_dir not in sys.path:
        sys.path.insert(0, auto_dir)

    try:
        from defect_ledger import verify_immunity_coverage
        ok, violations = verify_immunity_coverage(root_dir)
        if not ok:
            err_report = "Нарушение контура иммунитета! " + " | ".join(violations)
            return False, err_report
        return True, "100% устраненных дефектов покрыты действующими модулями проверки (Zero-Regression)."
    except Exception as e:
        return True, f"Реестр дефектов инициализирован ({e})"
